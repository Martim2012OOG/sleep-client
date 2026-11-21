package dev.sleepclient.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The second line for the profile data (badge, name style, flag, cosmetics, music above the head): a small Worker made only for this
 * (tools/sleep-sync). It carries the same "kx" messages the presence Worker carries, so a player is seen even when that one does not
 * deliver them. Both are used at once; what arrives twice is harmless (it is the same data).
 */
public final class SyncLink {

	/** Where the sync Worker lives (the account it was published on). */
	public static final String URL = "wss://sleep-sync.nobrega-martimrebelo2.workers.dev";

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final String LOBBY_ROOM = Presence.sha256("sleep-sync-lobby-v1");

	/** Last problem, for the "Badge sync status" line. */
	public static volatile String problem;
	private static volatile long lastRecv;
	private static volatile String lastRecvNick = "";

	private static final class Conn {
		final String room;
		volatile WebSocket ws;
		volatile boolean open, connecting;
		volatile long retryAt, lastPing;
		long backoff = 5_000L;
		final StringBuilder partial = new StringBuilder();

		Conn(String room) {
			this.room = room;
		}
	}

	private static Conn lobby, server;
	private static String serverKey;

	private SyncLink() {
	}

	public static boolean serverOpen() {
		Conn c = server;
		return c != null && c.open;
	}

	public static String diag() {
		return "Sync " + (lobby != null && lobby.open ? "lobby ON" : "lobby off") + ", " + (serverOpen() ? "server ON" : "server off")
				+ (lastRecvNick.isEmpty() ? "" : ", got " + lastRecvNick + " " + Math.max(0, (System.currentTimeMillis() - lastRecv) / 1000L) + " s ago")
				+ (problem == null ? "" : " | " + problem);
	}

	/** Every client tick: keeps the lobby socket (and the server one, in multiplayer) connected. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (Social.me == null || SolarApi.token == null || mc.getUser().getProfileId() == null) {
			closeAll();
			return;
		}
		if (Social.myStatus == dev.sleepclient.gui.StatusBadge.Status.INVISIBLE) {
			Conn l = lobby;
			lobby = null;
			close(l);
		} else {
			if (lobby == null) {
				lobby = new Conn(LOBBY_ROOM);
			}
			keep(lobby, mc, now);
		}
		String key = null;
		ServerData entry = mc.getCurrentServer();
		if (mc.level != null && entry != null && entry.ip != null && !mc.isLocalServer()) {
			key = Presence.sha256(Presence.normalizeAddress(entry.ip));
		}
		if (key == null ? serverKey != null : !key.equals(serverKey)) {
			Conn old = server;
			server = null;
			serverKey = key;
			close(old);
			if (key != null) {
				server = new Conn(key);
			}
		}
		if (server != null) {
			keep(server, mc, now);
		}
	}

	private static void keep(Conn c, Minecraft mc, long now) {
		if (c.open) {
			if (now - c.lastPing > 30_000L) {
				c.lastPing = now;
				WebSocket w = c.ws;
				if (w != null) {
					w.sendText("ping", true);
				}
			}
			return;
		}
		if (c.connecting || now < c.retryAt) {
			return;
		}
		c.connecting = true;
		JsonObject hello = new JsonObject();
		hello.addProperty("t", "hello");
		hello.addProperty("token", SolarApi.token);
		hello.addProperty("uuid", mc.getUser().getProfileId().toString().replace("-", ""));
		hello.addProperty("nick", mc.getUser().getName());
		HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(URL + "/ws/" + c.room), new Listener(c, hello.toString()))
				.whenComplete((ws, err) -> {
					if (err != null) {
						c.connecting = false;
						c.retryAt = System.currentTimeMillis() + c.backoff;
						c.backoff = Math.min(120_000L, c.backoff * 2);
						problem = "can't reach the sync Worker";
					}
				});
	}

	private static void close(Conn c) {
		if (c == null) {
			return;
		}
		c.open = false;
		WebSocket w = c.ws;
		c.ws = null;
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (RuntimeException ignored) {
				// already closed
			}
		}
	}

	private static void closeAll() {
		Conn l = lobby, s = server;
		lobby = null;
		server = null;
		serverKey = null;
		close(l);
		close(s);
	}

	private static boolean send(Conn c, JsonObject kx) {
		if (c == null || !c.open || c.ws == null) {
			return false;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", "kx");
		o.add("kx", kx);
		String text = o.toString();
		if (text.length() > 6000) {
			problem = "message too big (" + text.length() + ")";
			return false;
		}
		c.ws.sendText(text, true);
		return true;
	}

	/** Profile + party data for the players on this server. */
	public static boolean sendServer(JsonObject kx) {
		return send(server, kx);
	}

	/** Public profile data for every Sleep player online. */
	public static boolean sendLobby(JsonObject kx) {
		return send(lobby, kx);
	}

	private static final class Listener implements WebSocket.Listener {
		private final Conn c;
		private final String hello;

		Listener(Conn c, String hello) {
			this.c = c;
			this.hello = hello;
		}

		@Override
		public void onOpen(WebSocket ws) {
			c.ws = ws;
			ws.sendText(hello, true);
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			c.partial.append(data);
			if (last) {
				String msg = c.partial.toString();
				c.partial.setLength(0);
				try {
					handle(msg);
				} catch (RuntimeException ignored) {
					// bad message
				}
			}
			ws.request(1);
			return null;
		}

		private void handle(String msg) {
			if (msg.equals("pong")) {
				return;
			}
			JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
			String t = o.has("t") ? o.get("t").getAsString() : "";
			if (t.equals("list")) {
				c.open = true;
				c.connecting = false;
				c.backoff = 5_000L;
				c.lastPing = System.currentTimeMillis();
				problem = null;
				KuboLink.resendNow(); // a fresh connection: send my badge / music again at once
			} else if (t.equals("kx") && o.has("u") && o.has("kx") && o.get("kx").isJsonObject()) {
				String u = o.get("u").getAsString();
				JsonObject kx = o.getAsJsonObject("kx");
				lastRecv = System.currentTimeMillis();
				lastRecvNick = kx.has("n") ? kx.get("n").getAsString() : u;
				Minecraft.getInstance().execute(() -> KuboLink.onRemote(u, kx));
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
			c.open = false;
			c.connecting = false;
			c.ws = null;
			c.retryAt = System.currentTimeMillis() + c.backoff;
			if (code == 4003) {
				problem = "the Worker refused the login";
				c.retryAt = System.currentTimeMillis() + 60_000L;
			}
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			c.open = false;
			c.connecting = false;
			c.ws = null;
			c.retryAt = System.currentTimeMillis() + c.backoff;
			c.backoff = Math.min(120_000L, c.backoff * 2);
		}
	}
}
