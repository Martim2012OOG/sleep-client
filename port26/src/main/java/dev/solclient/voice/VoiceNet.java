package dev.solclient.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The voice connection (v99). Voice used to ride on the presence Worker, which only worked on a real multiplayer
 * server and only if that Worker had the voice update: on a Host World the host had no room at all, so nobody heard
 * anybody. Now it has its own small room on the EndLock Worker (tools/kubo-relay, path /voice):
 *  - in a voice GROUP you are in the group's room wherever each of you is playing (no distance);
 *  - otherwise you are in the room of the server you are on (proximity, like before). A hosted world and the friends
 *    who joined it through host.endlock.net/<name> share the same room.
 * Your name is proven with Mojang like Host World does, so nobody can talk as you.
 */
public final class VoiceNet {

	public record Member(String uuid, String name, boolean on, boolean muted, boolean deafened) {
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private static volatile WebSocket ws;
	private static volatile String room, wantedRoom;
	private static volatile boolean open, connecting;
	private static volatile long retryAt, backoff = 3_000L, lastPing;
	private static volatile String you;
	private static volatile Map<String, Member> members = Map.of();
	/** Why it is not connected (null = fine or not needed). */
	public static volatile String problem;
	private static CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
	private static int backlog;
	/** True when the last connection went in with a Mojang proof (Microsoft account). */
	private static volatile boolean sentSid;

	private VoiceNet() {
	}

	public static boolean ready() {
		return open && ws != null;
	}

	/** True while talking in a group (no distance: everybody in it is heard). */
	public static boolean groupMode() {
		String r = room;
		return r != null && r.startsWith("grp-");
	}

	public static Map<String, Member> members() {
		return members;
	}

	public static String you() {
		return you;
	}

	/** The room this client should be in now, or null. */
	public static String roomNow() {
		String g = VoiceGroups.activeId();
		if (g != null) {
			return "grp-" + g;
		}
		Minecraft mc = Minecraft.getInstance();
		String address = null;
		ServerData entry = mc.getCurrentServer();
		if (mc.level != null && entry != null && entry.ip != null && !mc.isLocalServer()) {
			address = entry.ip;
		} else if (mc.level != null && mc.isLocalServer() && dev.solclient.social.HostWorld.hosting()) {
			address = dev.solclient.social.KuboRelay.hostAddress(); // the address your friends typed
		}
		if (address == null) {
			return null;
		}
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.endsWith(".")) {
			a = a.substring(0, a.length() - 1);
		}
		if (!a.contains(":")) {
			a += ":25565";
		}
		return "srv-" + sha256(a).substring(0, 40);
	}

	/** Every client tick while voice chat is on. */
	public static void tick() {
		String want = roomNow();
		wantedRoom = want;
		long now = System.currentTimeMillis();
		if (want == null ? room != null : !want.equals(room)) {
			close();
			room = want;
			backoff = 3_000L;
			retryAt = 0;
		}
		if (want == null) {
			problem = null;
			return;
		}
		if (open) {
			if (now - lastPing > 25_000L) {
				lastPing = now;
				sendText("ping");
			}
			return;
		}
		if (connecting || now < retryAt) {
			return;
		}
		connecting = true;
		final String target = want;
		new Thread(() -> connect(target), "kubo-voice-connect").start();
	}

	private static void connect(String target) {
		try {
			// Microsoft account: proven with Mojang. Any other account (cracked): goes in with its claim key, so voice works for everybody.
			String sid = dev.solclient.social.KuboRelay.mojangJoinIfOriginal();
			sentSid = sid != null;
			String name = Minecraft.getInstance().getUser().getName();
			String url = dev.solclient.social.KuboRelay.relay() + "/voice?room=" + URLEncoder.encode(target, StandardCharsets.UTF_8) + "&name="
					+ URLEncoder.encode(name, StandardCharsets.UTF_8) + "&sid=" + (sid == null ? "" : sid) + "&ck=" + dev.solclient.social.KuboRelay.claimKey();
			HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(url), new Listener(target)).whenComplete((w, err) -> {
				if (err != null) {
					failed(explain(err));
				}
			});
		} catch (Exception e) {
			failed(e.getMessage() == null ? e.toString() : e.getMessage());
		}
	}

	/**
	 * Turns a failed WebSocket handshake into a short, useful line. A handshake that answers with a normal web page
	 * (HTTP 200 / 302 / 404 ...) instead of switching to a WebSocket means the EndLock Worker on the server is still the
	 * OLD version, without the /voice room: it has to be published again (tools/kubo-relay/Publicar.command).
	 */
	static String explain(Throwable err) {
		Throwable t = err;
		while (t != null) {
			if (t instanceof java.net.http.WebSocketHandshakeException he) {
				int code = he.getResponse() == null ? 0 : he.getResponse().statusCode();
				return dev.solclient.Lang.tr("Voice server is out of date (HTTP " + code + "): run Publicar.command again",
						"Servidor de voz desatualizado (HTTP " + code + "): corre o Publicar.command outra vez");
			}
			t = t.getCause();
		}
		return dev.solclient.Lang.tr("Can't reach the voice server (" + rootMessage(err) + ")",
				"Não consigo ligar ao servidor de voz (" + rootMessage(err) + ")");
	}

	/** Why the voice server said no (close code 4003). */
	static String refused(String reason) {
		if (reason != null && reason.contains("claimed")) {
			return dev.solclient.Lang.tr("This nick is already used by someone else in Caos (it frees up after 30 days without use)",
					"Este nick já está a ser usado por outra pessoa no Caos (fica livre depois de 30 dias sem uso)");
		}
		// a Worker that is not the new one refuses everybody it can't verify with Mojang: tell the player to publish it again
		if (!workerIsCurrent()) {
			return dev.solclient.Lang.tr("Voice server is out of date: run Publicar.command again",
					"Servidor de voz desatualizado: corre o Publicar.command outra vez");
		}
		String why = dev.solclient.social.KuboRelay.joinProblem;
		return dev.solclient.Lang.tr("Voice server refused your name" + (why == null ? "" : " (Mojang: " + why + ")"),
				"O servidor de voz recusou o teu nome" + (why == null ? "" : " (Mojang: " + why + ")"));
	}

	/** The Worker online answers /kubo-version (added in v118). An older one answers with a web page or a redirect. */
	private static boolean workerIsCurrent() {
		try {
			String base = dev.solclient.social.KuboRelay.relay().replaceFirst("^ws", "http");
			var res = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build().send(
					java.net.http.HttpRequest.newBuilder(URI.create(base + "/kubo-version")).timeout(Duration.ofSeconds(4)).GET().build(),
					java.net.http.HttpResponse.BodyHandlers.ofString());
			return res.statusCode() == 200 && res.body().startsWith("kubo-relay");
		} catch (Exception e) {
			return true; // can't tell (offline?): don't blame the Worker
		}
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) {
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}

	private static void failed(String why) {
		connecting = false;
		open = false;
		problem = why;
		retryAt = System.currentTimeMillis() + backoff;
		backoff = Math.min(60_000L, backoff * 2);
	}

	public static void close() {
		WebSocket w = ws;
		ws = null;
		open = false;
		connecting = false;
		members = Map.of();
		you = null;
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (RuntimeException ignored) {
			}
		}
	}

	// ------------------------------------------------------------------ send
	private static synchronized void sendText(String text) {
		WebSocket w = ws;
		if (w == null) {
			return;
		}
		sending = sending.handle((r, e) -> null).thenCompose(x -> w.sendText(text, true));
	}

	public static void sendState(boolean on, boolean muted, boolean deafened) {
		if (!open) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", "state");
		o.addProperty("on", on);
		o.addProperty("m", muted);
		o.addProperty("d", deafened);
		sendText(o.toString());
	}

	/** One 20 ms frame: [1][seq hi][seq lo][adpcm]. Dropped instead of piling up when the connection is slow. */
	public static synchronized void sendAudio(byte[] frame) {
		WebSocket w = ws;
		if (w == null || !open) {
			return;
		}
		if (!sending.isDone() && ++backlog > 6) {
			return;
		}
		sending = sending.handle((r, e) -> null).thenCompose(x -> {
			backlog = 0;
			return w.sendBinary(ByteBuffer.wrap(frame), true);
		});
	}

	// ------------------------------------------------------------------ receive
	private static final class Listener implements WebSocket.Listener {
		private final String target;
		private final StringBuilder text = new StringBuilder();
		private final java.io.ByteArrayOutputStream bin = new java.io.ByteArrayOutputStream();
		private WebSocket mine;

		Listener(String target) {
			this.target = target;
		}

		@Override
		public void onOpen(WebSocket w) {
			if (!target.equals(wantedRoom)) {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "moved");
				connecting = false;
				return;
			}
			mine = w;
			ws = w;
			w.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
			text.append(data);
			if (last) {
				String msg = text.toString();
				text.setLength(0);
				try {
					handle(msg);
				} catch (RuntimeException ignored) {
				}
			}
			w.request(1);
			return null;
		}

		private void handle(String msg) {
			if (msg.equals("pong")) {
				return;
			}
			JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
			String t = o.has("t") ? o.get("t").getAsString() : "";
			switch (t) {
				case "hello" -> {
					you = str(o, "you");
					Map<String, Member> m = new LinkedHashMap<>();
					for (JsonElement e : o.getAsJsonArray("members")) {
						Member mm = member(e.getAsJsonObject());
						m.put(mm.uuid(), mm);
					}
					members = Collections.unmodifiableMap(m);
					open = true;
					connecting = false;
					backoff = 3_000L;
					problem = null;
					lastPing = System.currentTimeMillis();
					for (Member mm : m.values()) {
						VoiceEngine.INSTANCE.state(mm.uuid(), mm.on(), mm.muted(), mm.deafened());
					}
					VoiceChatMod mod = VoiceChatMod.instance;
					if (mod != null) {
						sendState(true, mod.muted.on(), mod.deafened.on());
					}
				}
				case "join", "state" -> {
					Member mm = member(o);
					Map<String, Member> m = new LinkedHashMap<>(members);
					boolean isNew = !m.containsKey(mm.uuid());
					m.put(mm.uuid(), mm);
					members = Collections.unmodifiableMap(m);
					VoiceEngine.INSTANCE.state(mm.uuid(), mm.on(), mm.muted(), mm.deafened());
					if (isNew && "join".equals(t) && groupMode()) {
						dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.VOICE, mm.name() + dev.solclient.Lang.tr(" joined the voice group", " entrou no grupo de voz"),
								VoiceGroups.activeName(), mm.name());
					}
				}
				case "leave" -> {
					String u = str(o, "u");
					Map<String, Member> m = new LinkedHashMap<>(members);
					Member gone = m.remove(u);
					members = Collections.unmodifiableMap(m);
					VoiceEngine.INSTANCE.state(u, false, false, false);
					if (gone != null && groupMode()) {
						dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.VOICE, gone.name() + dev.solclient.Lang.tr(" left the voice group", " saiu do grupo de voz"),
								VoiceGroups.activeName(), gone.name());
					}
				}
				default -> {
				}
			}
		}

		private static Member member(JsonObject p) {
			return new Member(str(p, "u"), str(p, "n"), bool(p, "on"), bool(p, "m"), bool(p, "d"));
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
			byte[] chunk = new byte[data.remaining()];
			data.get(chunk);
			bin.write(chunk, 0, chunk.length);
			if (last) {
				byte[] msg = bin.toByteArray();
				bin.reset();
				// [1][16 bytes sender uuid][2 bytes seq][audio]
				if (msg.length > 19 && msg[0] == 1) {
					StringBuilder u = new StringBuilder(32);
					for (int i = 1; i <= 16; i++) {
						u.append(String.format("%02x", msg[i]));
					}
					VoiceEngine.INSTANCE.audio(u.toString(), msg, 17);
				}
			}
			w.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
			ended(code == 4003 ? refused(reason) : code == 4000 ? "Voice opened somewhere else" : null);
			return null;
		}

		@Override
		public void onError(WebSocket w, Throwable error) {
			ended("Voice connection lost");
		}

		private void ended(String why) {
			if (ws != mine && ws != null) {
				return;
			}
			ws = null;
			open = false;
			members = Map.of();
			failed(why == null ? "Reconnecting..." : why);
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static boolean bool(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && !e.isJsonNull() && e.getAsBoolean();
	}

	static String sha256(String s) {
		try {
			byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Names of the people in the room right now (for the screens), you first. */
	public static List<Member> list() {
		List<Member> out = new ArrayList<>(members.values());
		String me = you;
		out.sort((a, b) -> Boolean.compare(b.uuid().equals(me), a.uuid().equals(me)));
		return out;
	}
}
