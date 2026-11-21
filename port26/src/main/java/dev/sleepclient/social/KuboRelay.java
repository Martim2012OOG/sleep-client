package dev.sleepclient.social;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Sleep Relay client: Host World for friends anywhere, with no router setup and no same Wi-Fi (like Lunar).
 *
 * Host: one WebSocket to the relay Worker; every friend connection arrives as "open id", and its bytes are
 * forwarded to the world's LAN port on this computer.
 * Friend: Sleep opens a tiny server on 127.0.0.1, Minecraft connects to it, and each connection becomes a WebSocket
 * to the relay. The relay only passes bytes along: Minecraft's own login and encryption still run on top.
 * Addresses look like "kubo:AB12CD34".
 */
public final class KuboRelay {

	/** Old style "kubo:CODE" (still understood). */
	public static final String PREFIX = "kubo:";
	/** The relay runs on the owner's own domain (Cloudflare Worker "kubo-relay"). Addresses: host.endlock.net/<name>. */
	public static final String DEFAULT = "wss://host.endlock.net";
	public static final String POWERED_BY = "Powered by EndLock.net";
	private static final java.util.regex.Pattern ADDRESS = java.util.regex.Pattern.compile("(?i)^(?:wss?://|https?://)?([a-z0-9-]+(?:\\.[a-z0-9-]+)+(?::\\d{1,5})?)/([a-z0-9_]{3,16})/?$");
	private static final java.util.regex.Pattern CONTROL = java.util.regex.Pattern.compile("\"t\"\\s*:\\s*\"(open|close)\"\\s*,\\s*\"id\"\\s*:\\s*(\\d+)");
	private static final SecureRandom RNG = new SecureRandom();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private KuboRelay() {
	}

	/** "host.endlock.net/Marzox" (or the old "kubo:..."). */
	public static boolean isRelayAddress(String a) {
		if (a == null) {
			return false;
		}
		a = a.trim();
		return a.regionMatches(true, 0, PREFIX, 0, PREFIX.length()) || ADDRESS.matcher(a).matches();
	}

	/** wss://host/join?room=name for a relay address. */
	private static String joinUrl(String a) {
		a = a.trim();
		java.util.regex.Matcher m = ADDRESS.matcher(a);
		if (m.matches()) {
			String host = m.group(1).toLowerCase(java.util.Locale.ROOT);
			boolean local = host.startsWith("127.") || host.startsWith("localhost");
			return (local ? "ws://" : "wss://") + host + "/join?room=" + m.group(2).toLowerCase(java.util.Locale.ROOT);
		}
		return relay() + "/join?room=" + a.substring(PREFIX.length()).trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** Key for the local tunnel of an address. */
	public static String code(String address) {
		return address.trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** host.endlock.net (from the relay setting). */
	private static String relayHost() {
		String r = relay();
		int i = r.indexOf("://");
		return i >= 0 ? r.substring(i + 3) : r;
	}

	public static String relay() {
		String r = dev.sleepclient.mods.HostWorldMod.instance == null ? "" : dev.sleepclient.mods.HostWorldMod.instance.relay.get().trim();
		if (r.isEmpty()) {
			r = DEFAULT;
		}
		if (r.startsWith("https://")) {
			r = "wss://" + r.substring(8);
		} else if (r.startsWith("http://")) {
			r = "ws://" + r.substring(7);
		} else if (!r.contains("://")) {
			r = "wss://" + r;
		}
		while (r.endsWith("/")) {
			r = r.substring(0, r.length() - 1);
		}
		return r;
	}

	/**
	 * Proves to the relay that this Minecraft name is really yours: the same "join" call Minecraft makes when you
	 * enter any online server (Mojang's session server); the relay then asks Mojang "hasJoined". Returns the server id.
	 */
	private static long lastJoinAt, joinBlockedUntil;

	public static synchronized String mojangJoin() throws Exception {
		var mc = net.minecraft.client.Minecraft.getInstance();
		long now = System.currentTimeMillis();
		// Mojang rate-limits its session server per account. Our background proofs (live link, voice) must never eat that
		// budget: the player's own "join server" (ranked queue, hops between servers) then comes back as "Invalid session".
		if (now < joinBlockedUntil) {
			throw new IllegalStateException("Minecraft login skipped (cooling down)");
		}
		if (mc.getConnection() != null && mc.player == null) {
			throw new IllegalStateException("Minecraft login skipped (you are joining a server)");
		}
		if (now - lastJoinAt < 20_000L) {
			throw new IllegalStateException("Minecraft login skipped (just done)");
		}
		lastJoinAt = now;
		var session = mc.getUser();
		java.util.UUID id = session.getProfileId();
		String token = session.getAccessToken();
		if (id == null || token == null || token.isBlank() || token.length() < 20) {
			throw new IllegalStateException("needs an original Minecraft account");
		}
		byte[] b = new byte[16];
		RNG.nextBytes(b);
		String sid = new java.math.BigInteger(1, b).toString(16);
		String body = "{\"accessToken\":\"" + token + "\",\"selectedProfile\":\"" + id.toString().replace("-", "") + "\",\"serverId\":\"" + sid + "\"}";
		var res = HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
				.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() / 100 != 2) {
			joinBlockedUntil = System.currentTimeMillis() + 10 * 60_000L; // stop poking Mojang for 10 min
			throw new IllegalStateException("Minecraft login refused (" + res.statusCode() + "), restart the game");
		}
		return sid;
	}

	/**
	 * For voice and the live link: the Mojang proof when this is an original Microsoft account (throws if Mojang
	 * refuses, so a real account never silently becomes a "cracked" one), or null for any other account. Those go in
	 * with {@link #claimKey()} instead, so voice works for everybody.
	 */
	public static String mojangJoinIfOriginal() throws Exception {
		joinProblem = null;
		if (!"Microsoft".equals(dev.sleepclient.account.Accounts.currentType())) {
			return null;
		}
		try {
			return mojangJoin();
		} catch (Exception e) {
			// v118: Mojang said no (expired token, launcher quirk, network...). Voice and friends must still work: go in with
			// the claim key like any other account instead of failing with "can't verify your Microsoft account".
			joinProblem = e.getMessage() == null ? e.toString() : e.getMessage();
			System.err.println("[Sleep] Mojang login proof failed, using the claim key instead: " + joinProblem);
			return null;
		}
	}

	/** Why the last Mojang proof failed (null = it worked or was not needed). Only for the messages of the voice screen. */
	public static volatile String joinProblem;

	/**
	 * Secret that keeps your name yours when Mojang can't prove it (cracked accounts). Made once and kept in
	 * config/kubo/claim-key.txt: the first client to use a name owns it, nobody else can talk as you.
	 */
	public static synchronized String claimKey() {
		if (claimKey != null) {
			return claimKey;
		}
		java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("claim-key.txt");
		try {
			if (java.nio.file.Files.exists(f)) {
				String k = java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8).trim();
				if (k.length() >= 16) {
					return claimKey = k;
				}
			}
		} catch (Exception ignored) {
		}
		byte[] b = new byte[24];
		RNG.nextBytes(b);
		String k = new java.math.BigInteger(1, b).toString(16);
		try {
			java.nio.file.Files.createDirectories(f.getParent());
			java.nio.file.Files.writeString(f, k, java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[Sleep] could not save claim-key.txt: " + e);
		}
		return claimKey = k;
	}

	private static String claimKey;

	/** Sends one WebSocket message at a time (java's WebSocket does not allow two at once). */
	private static final class Sender {
		private final WebSocket ws;

		Sender(WebSocket ws) {
			this.ws = ws;
		}

		synchronized void binary(ByteBuffer b) throws Exception {
			ws.sendBinary(b, true).get(30, TimeUnit.SECONDS);
		}

		synchronized void text(String s) {
			try {
				ws.sendText(s, true).get(10, TimeUnit.SECONDS);
			} catch (Exception ignored) {
			}
		}
	}

	/** Collects a binary message that may arrive in pieces. */
	private abstract static class Collector implements WebSocket.Listener {
		private ByteBuffer acc = ByteBuffer.allocate(64 * 1024);
		private final StringBuilder text = new StringBuilder();

		@Override
		public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
			if (acc.remaining() < data.remaining()) {
				ByteBuffer n = ByteBuffer.allocate(Math.max(acc.capacity() * 2, acc.position() + data.remaining()));
				acc.flip();
				n.put(acc);
				acc = n;
			}
			acc.put(data);
			if (last) {
				acc.flip();
				byte[] msg = new byte[acc.remaining()];
				acc.get(msg);
				acc.clear();
				try {
					binary(msg);
				} catch (Exception e) {
					failed(e);
				}
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			text.append(data);
			if (last) {
				String t = text.toString();
				text.setLength(0);
				text(t);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
			closed(code, reason);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			closed(-1, String.valueOf(error.getMessage()));
		}

		abstract void binary(byte[] msg) throws Exception;

		void text(String t) {
		}

		abstract void closed(int code, String reason);

		void failed(Exception e) {
		}
	}

	// ================================================================== host
	private static volatile Host host;

	public static final class Host {
		final String room;
		final int localPort;
		volatile boolean running = true, connected;
		volatile String error;
		private volatile Sender sender;
		private volatile WebSocket socket;
		private final Map<Integer, Socket> conns = new ConcurrentHashMap<>();

		Host(int localPort, String name) {
			this.localPort = localPort;
			this.room = name.toLowerCase(java.util.Locale.ROOT);
		}

		void loop() {
			int fails = 0;
			while (running) {
				CompletableFuture<Void> done = new CompletableFuture<>();
				try {
					// the Mojang proof when it works; the claim key as well, so a launcher whose Mojang login fails can still host
					String sid = mojangJoinIfOriginal();
					String q = "/host?room=" + room + (sid != null ? "&sid=" + sid : "") + "&ck=" + claimKey();
					WebSocket ws = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
							.buildAsync(URI.create(relay() + q), new Collector() {
								@Override
								void binary(byte[] msg) throws Exception {
									if (msg.length < 4) {
										return;
									}
									int id = ByteBuffer.wrap(msg, 0, 4).getInt();
									Socket s = conns.get(id);
									if (s != null) {
										try {
											s.getOutputStream().write(msg, 4, msg.length - 4);
										} catch (Exception e) {
											drop(id, true);
										}
									}
								}

								@Override
								void text(String t) {
									if (t.contains("\"ok\"")) {
										connected = true; // the relay confirmed with Mojang that the name is ours
										HostWorld.relayChanged();
										return;
									}
									java.util.regex.Matcher m = CONTROL.matcher(t);
									if (m.find()) {
										int id = Integer.parseInt(m.group(2));
										if (m.group(1).equals("open")) {
											open(id);
										} else {
											drop(id, false);
										}
									}
								}

								@Override
								void closed(int code, String reason) {
									if (code == 4003) {
										error = "the relay could not confirm your name (update the relay: tools/kubo-relay/Publicar.command)";
									} else if (code == 4000 && reason != null && reason.contains("another")) {
										error = "you are hosting from another place";
									}
									System.err.println("[Sleep] relay host closed: " + code + " " + reason);
									done.complete(null);
								}
							}).get(15, TimeUnit.SECONDS);
					sender = new Sender(ws);
					socket = ws;
					error = null;
					sender.text(HostWorld.infoJson());
					while (running && !done.isDone()) {
						try {
							done.get(15, TimeUnit.SECONDS);
						} catch (java.util.concurrent.TimeoutException t) {
							sender.text(HostWorld.infoJson()); // keeps the connection alive + the page on host.endlock.net up to date
						}
					}
					if (!running) {
						ws.sendClose(WebSocket.NORMAL_CLOSURE, "stop");
					}
				} catch (Exception e) {
					error = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
					System.err.println("[Sleep] relay host: " + error);
				}
				if (connected) {
					fails = 0;
				}
				connected = false;
				closeAll();
				HostWorld.relayChanged();
				if (!running) {
					break;
				}
				fails++;
				try {
					Thread.sleep(Math.min(30_000L, 1500L * fails));
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		/** Connects to the world right away (on the relay thread) so no byte that arrives next is lost. */
		private void open(int id) {
			Socket s;
			try {
				s = new Socket(InetAddress.getLoopbackAddress(), localPort);
				s.setTcpNoDelay(true);
			} catch (Exception e) {
				if (sender != null) {
					sender.text("{\"t\":\"close\",\"id\":" + id + "}");
				}
				return;
			}
			conns.put(id, s);
			Thread t = new Thread(() -> {
				try {
					InputStream in = s.getInputStream();
					byte[] buf = new byte[16 * 1024];
					int n;
					while ((n = in.read(buf)) > 0) {
						ByteBuffer b = ByteBuffer.allocate(n + 4);
						b.putInt(id).put(buf, 0, n).flip();
						sender.binary(b);
					}
				} catch (Exception ignored) {
				}
				drop(id, true);
			}, "Sleep relay host " + id);
			t.setDaemon(true);
			t.start();
		}

		private void drop(int id, boolean tellRelay) {
			Socket s = conns.remove(id);
			if (s != null) {
				try {
					s.close();
				} catch (Exception ignored) {
				}
				if (tellRelay && sender != null) {
					sender.text("{\"t\":\"close\",\"id\":" + id + "}");
				}
			}
		}

		private void closeAll() {
			for (Integer id : conns.keySet()) {
				drop(id, false);
			}
		}
	}

	/** Starts relaying the world's LAN port. Returns the address friends use ("host.endlock.net/<name>"). */
	public static synchronized String startHost(int localPort, String name) {
		stopHost();
		Host h = new Host(localPort, name);
		host = h;
		Thread t = new Thread(h::loop, "Sleep relay host");
		t.setDaemon(true);
		t.start();
		return relayHost() + "/" + name;
	}

	public static synchronized void stopHost() {
		Host h = host;
		host = null;
		if (h != null) {
			h.running = false;
			h.closeAll();
			WebSocket ws = h.socket;
			if (ws != null) {
				// close now, so friends see at once that you stopped (and host.endlock.net/<name> says so)
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "stop").orTimeout(3, TimeUnit.SECONDS).whenComplete((r, e) -> ws.abort());
			}
		}
	}

	public static boolean hostConnected() {
		Host h = host;
		return h != null && h.connected;
	}

	public static String hostAddress() {
		Host h = host;
		return h == null ? null : relayHost() + "/" + net.minecraft.client.Minecraft.getInstance().getUser().getName();
	}

	public static String hostError() {
		Host h = host;
		return h == null ? null : h.error;
	}

	// ================================================================== friend
	private static final Map<String, ServerSocket> LOCAL = new ConcurrentHashMap<>();

	/** Local address Minecraft connects to for a relay code (a tiny server on this computer that tunnels to it). */
	public static synchronized String localFor(String address) throws Exception {
		String code = code(address);
		ServerSocket ss = LOCAL.get(code);
		if (ss == null || ss.isClosed()) {
			ServerSocket srv = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
			LOCAL.put(code, srv);
			Thread t = new Thread(() -> {
				while (!srv.isClosed()) {
					try {
						Socket s = srv.accept();
						Thread c = new Thread(() -> tunnel(address, s), "Sleep relay join");
						c.setDaemon(true);
						c.start();
					} catch (Exception e) {
						break;
					}
				}
			}, "Sleep relay local " + code);
			t.setDaemon(true);
			t.start();
			ss = srv;
		}
		return "127.0.0.1:" + ss.getLocalPort();
	}

	private static void tunnel(String address, Socket s) {
		try (s) {
			s.setTcpNoDelay(true);
			OutputStream out = s.getOutputStream();
			CompletableFuture<Void> done = new CompletableFuture<>();
			boolean[] gotData = {false};
			WebSocket ws = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
					.buildAsync(URI.create(joinUrl(address)), new Collector() {
						@Override
						void binary(byte[] msg) throws Exception {
							gotData[0] = true;
							out.write(msg);
						}

						@Override
						void closed(int c, String reason) {
							if (c == 4004) {
								Social.say(address + dev.sleepclient.Lang.tr(" is not hosting right now", " não está a hospedar agora"), true);
							} else if (!gotData[0]) {
								Social.say(dev.sleepclient.Lang.tr("Could not reach the world: ", "Não deu para chegar ao mundo: ") + reason, true);
							}
							System.err.println("[Sleep] relay join " + address + " closed: " + c + " " + reason);
							done.complete(null);
							try {
								s.close();
							} catch (Exception ignored) {
							}
						}
					}).get(15, TimeUnit.SECONDS);
			Sender sender = new Sender(ws);
			InputStream in = s.getInputStream();
			byte[] buf = new byte[16 * 1024];
			int n;
			while (!done.isDone() && (n = in.read(buf)) > 0) {
				sender.binary(ByteBuffer.wrap(buf, 0, n));
			}
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(3, TimeUnit.SECONDS);
			} catch (Exception ignored) {
			}
		} catch (Exception e) {
			System.err.println("[Sleep] relay join " + address + ": " + e);
			Social.say(dev.sleepclient.Lang.tr("Could not reach the Sleep relay: ", "Não deu para chegar ao relay do Sleep: ") + e.getMessage(), true);
		}
	}
}
