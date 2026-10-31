package dev.solclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;

/**
 * Live link to friends (v99), through the EndLock Worker (tools/kubo-relay, path /live): one socket per game, with
 * your Minecraft name proven by Mojang. Carries the small real-time things the website can't:
 *  - "typing..." in the friend chat (like Discord);
 *  - voice group invites and updates.
 */
public final class KuboLive {

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static volatile WebSocket ws;
	private static volatile boolean open, connecting;
	private static volatile long retryAt, backoff = 5_000L, lastPing;
	private static CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
	/** friend name (lower case) -> typing until (ms). */
	private static final Map<String, Long> TYPING = new ConcurrentHashMap<>();
	private static volatile String typingTo;
	private static volatile long typingSentAt, lastKeyAt;

	private KuboLive() {
	}

	public static boolean connected() {
		return open;
	}

	/** Every client tick (from Social.tick). */
	public static void tick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (Social.me == null) {
			if (ws != null) {
				close();
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (open) {
			if (now - lastPing > 25_000L) {
				lastPing = now;
				send("ping");
			}
			// stop "typing" if nothing was typed for 4 s
			if (typingTo != null && now - lastKeyAt > 4_000L) {
				typing(typingTo, false);
			}
			return;
		}
		if (connecting || now < retryAt || mc.getSession().getUuidOrNull() == null) {
			return;
		}
		connecting = true;
		new Thread(KuboLive::connect, "kubo-live-connect").start();
	}

	private static void connect() {
		try {
			String sid = KuboRelay.mojangJoinIfOriginal(); // null = not a Microsoft account: the claim key proves the nick instead
			String name = MinecraftClient.getInstance().getSession().getUsername();
			String url = KuboRelay.relay() + "/live?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&sid=" + (sid == null ? "" : sid)
					+ "&ck=" + KuboRelay.claimKey();
			HTTP.newWebSocketBuilder().buildAsync(URI.create(url), new Listener()).whenComplete((w, err) -> {
				if (err != null) {
					failed();
				}
			});
		} catch (Exception e) {
			failed();
		}
	}

	private static void failed() {
		connecting = false;
		open = false;
		retryAt = System.currentTimeMillis() + backoff;
		backoff = Math.min(120_000L, backoff * 2);
	}

	public static void close() {
		WebSocket w = ws;
		ws = null;
		open = false;
		connecting = false;
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (RuntimeException ignored) {
			}
		}
	}

	private static synchronized void send(String text) {
		WebSocket w = ws;
		if (w == null) {
			return;
		}
		sending = sending.handle((r, e) -> null).thenCompose(x -> w.sendText(text, true));
	}

	/** @return true when the message was handed to the live socket (false = not connected / bad name: nothing was sent). */
	private static boolean sendTo(String to, JsonObject o) {
		if (!open || to == null || !to.matches("[A-Za-z0-9_]{3,16}")) {
			return false;
		}
		o.addProperty("to", to);
		send(o.toString());
		return true;
	}

	// ------------------------------------------------------------------ typing
	/** Call on every key typed in a friend chat box (sends at most every 2.5 s). on = false when sent / cleared. */
	public static void typing(String friendName, boolean on) {
		long now = System.currentTimeMillis();
		if (on) {
			lastKeyAt = now;
			if (friendName.equals(typingTo) && now - typingSentAt < 2_500L) {
				return; // sent a moment ago (the friend's side keeps it for 6 s)
			}
			if (typingTo != null && !friendName.equals(typingTo)) {
				typing(typingTo, false);
			}
			typingTo = friendName;
			typingSentAt = now;
		} else {
			if (typingTo == null) {
				return;
			}
			typingTo = null;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", "typing");
		o.addProperty("on", on);
		sendTo(friendName, o);
	}

	/** True while this friend is typing to you. */
	public static boolean isTyping(String friendName) {
		if (friendName == null) {
			return false;
		}
		Long until = TYPING.get(friendName.toLowerCase(Locale.ROOT));
		return until != null && until > System.currentTimeMillis();
	}

	/** A message from them arrived: they stopped typing. */
	public static void stoppedTyping(String friendName) {
		if (friendName != null) {
			TYPING.remove(friendName.toLowerCase(Locale.ROOT));
		}
	}

	// ------------------------------------------------------------------ voice groups
	public static boolean voiceGroup(String type, String to, String id, String old, String name, List<String> members) {
		JsonObject o = new JsonObject();
		o.addProperty("t", type);
		o.addProperty("id", id);
		if (old != null) {
			o.addProperty("old", old);
		}
		o.addProperty("name", name);
		JsonArray a = new JsonArray();
		members.forEach(a::add);
		o.add("members", a);
		return sendTo(to, o);
	}

	public static void voiceLeave(String to, String id) {
		JsonObject o = new JsonObject();
		o.addProperty("t", "vleave");
		o.addProperty("id", id);
		sendTo(to, o);
	}

	// ------------------------------------------------------------------ socket
	private static final class Listener implements WebSocket.Listener {
		private final StringBuilder text = new StringBuilder();
		private WebSocket mine;

		@Override
		public void onOpen(WebSocket w) {
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
			String from = o.has("from") ? o.get("from").getAsString() : null;
			switch (t) {
				case "ok" -> {
					open = true;
					connecting = false;
					backoff = 5_000L;
					lastPing = System.currentTimeMillis();
				}
				case "typing" -> {
					if (from != null) {
						if (o.has("on") && o.get("on").getAsBoolean()) {
							TYPING.put(from.toLowerCase(Locale.ROOT), System.currentTimeMillis() + 6_000L);
						} else {
							TYPING.remove(from.toLowerCase(Locale.ROOT));
						}
					}
				}
				case "vinvite", "vgroup" -> {
					List<String> members = new ArrayList<>();
					if (o.has("members")) {
						for (JsonElement e : o.getAsJsonArray("members")) {
							members.add(e.getAsString());
						}
					}
					String id = o.get("id").getAsString(), name = o.get("name").getAsString();
					String old = o.has("old") ? o.get("old").getAsString() : "";
					MinecraftClient.getInstance().execute(() -> {
						if (t.equals("vinvite")) {
							dev.solclient.voice.VoiceGroups.onInvite(from, id, name, members);
						} else {
							dev.solclient.voice.VoiceGroups.onUpdate(from, id, old, name, members);
						}
					});
				}
				case "vleave" -> {
					String id = o.get("id").getAsString();
					MinecraftClient.getInstance().execute(() -> dev.solclient.voice.VoiceGroups.onLeft(from, id));
				}
				default -> {
				}
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
			ended();
			return null;
		}

		@Override
		public void onError(WebSocket w, Throwable error) {
			ended();
		}

		private void ended() {
			if (ws != mine && ws != null) {
				return;
			}
			ws = null;
			failed();
		}
	}
}
