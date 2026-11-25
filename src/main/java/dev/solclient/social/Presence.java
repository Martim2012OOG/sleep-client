package dev.solclient.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;

/**
 * Connection to the Cloudflare presence Worker (plegumo-presence).
 *
 * Protocol (from the Worker's room.js): WebSocket to /ws/&lt;sha256 hex&gt;, first message
 * {"t":"hello","token":&lt;JWT from POST /issue-token&gt;,"uuid":...,"nick":...}; the room answers
 * {"t":"list","players":[{u,n,c}]} and later {"t":"delta","join":[...],"leave":[uuid]} and {"t":"emote","u","id"}.
 * Plain text "ping" is answered with "pong" (keeps the connection alive, the room drops sockets silent for 90 s).
 *
 * Two rooms are used:
 *  - the LOBBY (fixed name): every Plegumo player is in it while the game is open -> friends online / offline;
 *  - the SERVER room (sha256 of the server address): who is on the same server -> "on your server" + emotes.
 */
public final class Presence {

	/** The Worker deployed with `npx wrangler deploy`. */
	public static final String WORKER = "wss://plegumo-presence.plegumo.workers.dev";

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final String LOBBY = sha256("plegumo-lobby-v1");

	public record Player(String uuid, String nick) {
	}

	/** One WebSocket room. */
	private static final class Room {
		final String name;
		volatile WebSocket ws;
		volatile boolean open, connecting, dead;
		volatile Map<String, Player> players = Map.of();
		volatile long retryAt;
		volatile long backoff = 5_000L;
		volatile long lastPing;
		private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
		private final StringBuilder partial = new StringBuilder();

		Room(String name) {
			this.name = name;
		}

		synchronized void send(String text) {
			WebSocket w = ws;
			if (w == null) {
				return;
			}
			sending = sending.handle((r, e) -> null).thenCompose(x -> w.sendText(text, true));
		}

		synchronized void sendBinary(byte[] data) {
			WebSocket w = ws;
			if (w == null) {
				return;
			}
			// drop audio instead of piling it up when the connection is slow
			if (!sending.isDone() && ++backlog > 6) {
				return;
			}
			sending = sending.handle((r, e) -> null).thenCompose(x -> {
				backlog = 0;
				return w.sendBinary(java.nio.ByteBuffer.wrap(data), true);
			});
		}

		private int backlog;
		/** The Worker answered the voice hello: it supports voice chat. */
		volatile boolean voiceOk;
		/** The Worker said it has voice chat ("voice": true in the list). Old Workers never get voice messages. */
		volatile boolean voiceCapable;
		private final java.io.ByteArrayOutputStream partialBin = new java.io.ByteArrayOutputStream();

		void close() {
			dead = true;
			open = false;
			WebSocket w = ws;
			ws = null;
			players = Map.of();
			if (w != null) {
				try {
					w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
				} catch (RuntimeException ignored) {
					// already closed
				}
			}
		}
	}

	private static volatile Room lobby, server;
	private static volatile String serverKey;

	private static volatile String jwt, jwtNick;
	private static volatile long jwtExpires, nextTokenAt;
	private static volatile boolean tokenBusy;

	/** Status line shown in the menu (null = fine). */
	public static volatile String problem;

	/** Called with (player uuid without dashes, emote index) when someone on your server plays an emote. */
	public static volatile BiConsumer<String, Integer> onEmote;

	private Presence() {
	}

	// ---------------------------------------------------------------- queries for the menu
	private static String norm(String uuid) {
		return uuid == null ? null : uuid.replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static boolean in(Room r, Social.User u) {
		if (r == null || u == null) {
			return false;
		}
		Map<String, Player> p = r.players;
		String id = norm(u.mcUuid);
		if (id != null && p.containsKey(id)) {
			return true;
		}
		if (u.mcUsername != null) {
			for (Player pl : p.values()) {
				if (pl.nick().equalsIgnoreCase(u.mcUsername)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean online(Social.User u) {
		return in(lobby, u) || in(server, u);
	}

	public static boolean onMyServer(Social.User u) {
		return in(server, u);
	}

	/** This player (by uuid) is in your server's presence room, i.e. plays with Plegumo here. */
	public static boolean inServerRoom(UUID id) {
		Room r = server;
		return r != null && id != null && r.players.containsKey(id.toString().replace("-", ""));
	}

	/**
	 * This player is in your server's presence room, matching by account uuid OR by game nick. The nick fallback
	 * matters on offline-mode servers, where a player's in-game uuid differs from their account uuid.
	 */
	public static boolean inServerRoom(UUID id, String nick) {
		Room r = server;
		if (r == null) {
			return false;
		}
		if (id != null && r.players.containsKey(id.toString().replace("-", ""))) {
			return true;
		}
		if (nick != null) {
			for (Player p : r.players.values()) {
				if (p.nick().equalsIgnoreCase(nick)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * This player is online with the client anywhere (the global lobby room), by account uuid or game nick. Used as a
	 * second chance when the two of you reached the same server through different addresses (different server rooms).
	 */
	public static boolean inLobby(UUID id, String nick) {
		Room r = lobby;
		if (r == null) {
			return false;
		}
		if (id != null && r.players.containsKey(id.toString().replace("-", ""))) {
			return true;
		}
		if (nick != null) {
			for (Player p : r.players.values()) {
				if (p.nick().equalsIgnoreCase(nick)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The account uuid (no dashes) of the player with this game nick, if they are in one of your presence rooms.
	 * Bridges the offline-mode gap: presence knows account uuids, the world only knows server-assigned uuids.
	 */
	public static String accountId(String nick) {
		if (nick == null) {
			return null;
		}
		Room[] rooms = { server, lobby };
		for (Room r : rooms) {
			if (r == null) {
				continue;
			}
			for (Player p : r.players.values()) {
				if (p.nick().equalsIgnoreCase(nick)) {
					return p.uuid();
				}
			}
		}
		return null;
	}

	/** UUID from a 32-char hex string without dashes (null if invalid). */
	public static UUID dashed(String noDashes) {
		if (noDashes == null || noDashes.length() != 32) {
			return null;
		}
		try {
			return UUID.fromString(noDashes.substring(0, 8) + "-" + noDashes.substring(8, 12) + "-" + noDashes.substring(12, 16)
					+ "-" + noDashes.substring(16, 20) + "-" + noDashes.substring(20));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The uuid the backend knows for this world player: their account uuid when bridged, else the entity uuid. */
	/** Account uuid of a friend who is online in Caos and plays with this nick, or null. */
	private static UUID friendAccount(String nick) {
		if (nick == null) {
			return null;
		}
		try {
			for (Social.User u : Social.friends) {
				if (u != null && u.online() && nick.equalsIgnoreCase(u.mcUsername) && u.mcUuid != null && !u.mcUuid.isBlank()) {
					return dashed(u.mcUuid.replace("-", "").toLowerCase(java.util.Locale.ROOT));
				}
			}
		} catch (RuntimeException ignored) {
			// list changed while reading
		}
		return null;
	}

	public static UUID backendId(net.minecraft.entity.player.PlayerEntity p) {
		if (p == null) {
			return null;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (p == mc.player) {
			// you: always your account uuid, even on offline-mode / cracked servers (there the in-game uuid is different)
			UUID me = mc.getSession().getUuidOrNull();
			if (me != null) {
				return me;
			}
		}
		// asked several times per player per frame (skin, cape, cosmetics...): remember the answer for a moment
		long now = System.currentTimeMillis();
		Object[] c = BACKEND_CACHE.get(p.getUuid());
		if (c != null && (long) c[0] > now) {
			return (UUID) c[1];
		}
		UUID acc = dashed(accountId(p.getName().getString()));
		if (acc == null) {
			acc = friendAccount(p.getName().getString()); // a friend with this nick: their account, even when presence missed them
		}
		UUID out = acc != null ? acc : p.getUuid();
		if (BACKEND_CACHE.size() > 512) {
			BACKEND_CACHE.clear();
		}
		BACKEND_CACHE.put(p.getUuid(), new Object[] {now + 500L, out});
		return out;
	}

	private static final java.util.Map<UUID, Object[]> BACKEND_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static boolean connected() {
		Room l = lobby;
		return l != null && l.open;
	}

	// ---------------------------------------------------------------- lifecycle
	/** Every client tick (from Social.tick). */
	public static void tick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		if (Social.me == null) {
			disconnectAll();
			return;
		}
		Session s = mc.getSession();
		String nick = s.getUsername();
		UUID uuid = s.getUuidOrNull();
		if (uuid == null) {
			return;
		}

		// token (JWT HS256, 24 h; the site allows 10 per hour): reuse the one saved by the last launch so restarts do not use it up
		if (jwt == null) {
			loadSavedToken(uuid);
		}
		if (jwt == null || now > jwtExpires - 3_600_000L) {
			if (!tokenBusy && now >= nextTokenAt) {
				tokenBusy = true;
				nextTokenAt = now + 360_000L;
				Social.IO.execute(Presence::issueToken);
			}
			if (jwt == null) {
				return;
			}
		}
		if (jwtNick != null && !jwtNick.equalsIgnoreCase(nick)) {
			problem = "Presence: account nick (" + jwtNick + ") is not the game nick";
			return;
		}

		// lobby (not while "Invisible": friends then see you offline)
		if (Social.myStatus == dev.solclient.gui.StatusBadge.Status.INVISIBLE) {
			if (lobby != null) {
				lobby.close();
				lobby = null;
			}
		} else {
			if (lobby == null) {
				lobby = new Room(LOBBY);
			}
			keep(lobby, uuid, nick, now);
		}

		// server room (only in multiplayer)
		String key = null;
		ServerInfo entry = mc.getCurrentServerEntry();
		if (mc.world != null && entry != null && entry.address != null && !mc.isInSingleplayer()) {
			key = sha256(normalizeAddress(entry.address));
		}
		if (key == null ? serverKey != null : !key.equals(serverKey)) {
			Room old = server;
			server = null;
			serverKey = key;
			if (old != null) {
				old.close();
			}
			if (key != null) {
				server = new Room(key);
			}
		}
		if (server != null) {
			keep(server, uuid, nick, now);
		}
	}

	private static void keep(Room r, UUID uuid, String nick, long now) {
		if (r.dead) {
			return;
		}
		if (r.open) {
			if (now - r.lastPing > 30_000L) {
				r.lastPing = now;
				r.send("ping");
			}
			return;
		}
		if (r.connecting || now < r.retryAt) {
			return;
		}
		r.connecting = true;
		String token = jwt;
		String hello = hello(token, uuid, nick);
		HTTP.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(WORKER + "/ws/" + r.name), new Listener(r, hello))
				.whenComplete((ws, err) -> {
					if (err != null) {
						r.connecting = false;
						r.retryAt = System.currentTimeMillis() + r.backoff;
						r.backoff = Math.min(120_000L, r.backoff * 2);
						problem = "Presence: can't reach the Worker";
					}
				});
	}

	private static String hello(String token, UUID uuid, String nick) {
		JsonObject o = new JsonObject();
		o.addProperty("t", "hello");
		o.addProperty("token", token);
		o.addProperty("uuid", uuid.toString().replace("-", ""));
		o.addProperty("nick", nick);
		return o.toString();
	}

	public static void disconnectAll() {
		Room l = lobby, s = server;
		lobby = null;
		server = null;
		serverKey = null;
		if (l != null) {
			l.close();
		}
		if (s != null) {
			s.close();
		}
	}

	/** Forget the token (e.g. after logging in with another account). */
	public static void reset() {
		disconnectAll();
		jwt = null;
		jwtNick = null;
		nextTokenAt = 0;
	}

	private static java.nio.file.Path tokenFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-presence-token.json");
	}

	/** Keeps the token on this computer so the next launch does not ask for a new one (the site only gives 10 per hour). */
	private static void saveToken(String t, long exp) {
		try {
			UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
			JsonObject o = new JsonObject();
			o.addProperty("token", t);
			o.addProperty("exp", exp);
			o.addProperty("nick", jwtNick == null ? "" : jwtNick);
			o.addProperty("uuid", me == null ? "" : me.toString().replace("-", ""));
			java.nio.file.Files.writeString(tokenFile(), o.toString());
		} catch (Exception ignored) {
			// not important
		}
	}

	private static void loadSavedToken(UUID uuid) {
		try {
			if (!java.nio.file.Files.exists(tokenFile())) {
				return;
			}
			JsonObject o = JsonParser.parseString(java.nio.file.Files.readString(tokenFile())).getAsJsonObject();
			long exp = o.get("exp").getAsLong();
			if (!uuid.toString().replace("-", "").equals(o.get("uuid").getAsString()) || exp - System.currentTimeMillis() < 3_600_000L) {
				return;
			}
			jwtNick = o.get("nick").getAsString();
			jwtExpires = exp;
			jwt = o.get("token").getAsString();
			problem = null;
		} catch (Exception ignored) {
			// no saved token: a new one is requested
		}
	}

	private static void issueToken() {
		try {
			SolarApi.Res r = SolarApi.post("/issue-token", new JsonObject());
			if (r.status() == 429) {
				problem = "Presence: too many token requests, trying later";
				// do NOT keep knocking every 6 minutes (that is exactly the limit, so it never frees up): wait a long time
				nextTokenAt = System.currentTimeMillis() + 25L * 60_000L;
				return;
			}
			if (!r.ok()) {
				problem = "Presence: " + (r.error() == null ? "no token" : r.error());
				return;
			}
			String t = Json.str(r.obj(), "token");
			if (t == null) {
				problem = "Presence: bad token response";
				return;
			}
			long exp = System.currentTimeMillis() + 23L * 3_600_000L;
			String at = Json.str(r.obj(), "expiresAt");
			if (at != null) {
				try {
					exp = Instant.parse(at).toEpochMilli();
				} catch (RuntimeException ignored) {
					// keep 23 h
				}
			}
			JsonObject claims = Json.obj(r.obj(), "claims");
			jwtNick = Json.str(claims, "mc_name");
			jwtExpires = exp;
			jwt = t;
			problem = null;
			saveToken(t, exp);
		} finally {
			tokenBusy = false;
		}
	}

	// ---------------------------------------------------------------- voice chat
	/** Receives voice data / states from the server room (implemented by the voice engine). */
	public interface VoiceLink {
		void audio(String uuid, byte[] msg, int offset);

		void state(String uuid, boolean on, boolean muted, boolean deafened);
	}

	public static volatile VoiceLink voice;

	/** True when connected to this server's room and the Worker supports voice (new room.js deployed). */
	public static boolean voiceReady() {
		Room r = server;
		return r != null && r.open && r.voiceOk;
	}

	public static boolean serverRoomOpen() {
		Room r = server;
		return r != null && r.open;
	}

	/** Tells the room our voice state (also asks the Worker for everyone's: the answer proves voice support). */
	public static void sendVoiceState(boolean on, boolean muted, boolean deafened) {
		Room r = server;
		if (r == null || !r.open || !r.voiceCapable) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", "voice");
		o.addProperty("on", on);
		o.addProperty("m", muted);
		o.addProperty("d", deafened);
		r.send(o.toString());
	}

	/** One 20 ms audio frame: [1][seq][adpcm]. */
	public static void sendVoice(byte[] frame) {
		Room r = server;
		if (r != null && r.open && r.voiceOk) {
			r.sendBinary(frame);
		}
	}

	// ---------------------------------------------------------------- emotes
	private static long lastEmote;

	/** Tells the people on your server that you started emote #index (the Worker allows one every 2 s). */
	public static void sendEmote(int index) {
		Room r = server;
		long now = System.currentTimeMillis();
		if (r == null || !r.open || index < 0 || now - lastEmote < 2_100L) {
			return;
		}
		lastEmote = now;
		JsonObject o = new JsonObject();
		o.addProperty("t", "emote");
		o.addProperty("id", index);
		r.send(o.toString());
	}

	// ---------------------------------------------------------------- watch party screens
	private static long lastScreen;

	/** My Watch Party screen (null = removed) for the people on this server. The Worker remembers it for late joiners. */
	public static void sendScreen(JsonObject screen) {
		Room r = server;
		if (r == null || !r.open) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastScreen < 450L) {
			// the Worker allows about two per second: send the latest a bit later
			JsonObject later = screen;
			java.util.concurrent.CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
					.execute(() -> MinecraftClient.getInstance().execute(() -> sendScreen(later)));
			return;
		}
		lastScreen = now;
		JsonObject o = new JsonObject();
		o.addProperty("t", "screen");
		o.add("s", screen == null ? com.google.gson.JsonNull.INSTANCE : screen);
		r.send(o.toString());
	}

	// ---------------------------------------------------------------- Caos link (party, pings)
	private static long lastLink;

	/** Sends Caos data to the people on this server (merged with my watch-party screen, if I have one). */
	public static boolean sendLink(JsonObject kx) {
		Room r = server;
		long now = System.currentTimeMillis();
		if (r == null || !r.open || now - lastLink < 600L || now - lastScreen < 500L) {
			return false;
		}
		lastLink = now;
		lastScreen = now;
		JsonObject s = dev.solclient.video.VideoScreens.currentPayload();
		if (s == null) {
			s = new JsonObject();
		}
		s.add("kx", kx);
		JsonObject o = new JsonObject();
		o.addProperty("t", "screen");
		o.add("s", s);
		r.send(o.toString());
		return true;
	}

	private static long lastLobbyLink;

	/**
	 * Badge / name style / flag / cosmetics for EVERY Caos player online (the global lobby room), not only for the ones whose
	 * server room has the same name. Two friends who reached the same server through different addresses (name vs IP, with or
	 * without a port) sit in different server rooms and never saw each other's crown, badges or name colour. Only public
	 * profile data goes here: never position, health or pings.
	 */
	public static boolean sendLinkLobby(JsonObject kx) {
		Room r = lobby;
		long now = System.currentTimeMillis();
		if (r == null || !r.open || now - lastLobbyLink < 600L) {
			return false;
		}
		lastLobbyLink = now;
		JsonObject s = new JsonObject();
		s.add("kx", kx);
		JsonObject o = new JsonObject();
		o.addProperty("t", "screen");
		o.add("s", s);
		r.send(o.toString());
		return true;
	}

	/** Account uuids (no dashes) and nicks of the Caos players in the global lobby right now. */
	public static Map<String, Player> lobbyPlayers() {
		Room r = lobby;
		return r == null ? Map.of() : r.players;
	}

	/** Account uuids (no dashes) and nicks of the Caos players on this server right now. */
	public static Map<String, Player> serverPlayers() {
		Room r = server;
		return r == null ? Map.of() : r.players;
	}

	// ---------------------------------------------------------------- socket listener
	private static final class Listener implements WebSocket.Listener {
		private final Room room;
		private final String hello;
		private volatile WebSocket mine;

		Listener(Room room, String hello) {
			this.room = room;
			this.hello = hello;
		}

		@Override
		public void onOpen(WebSocket ws) {
			if (room.dead) {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
				return;
			}
			mine = ws;
			room.ws = ws;
			room.send(hello);
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			room.partial.append(data);
			if (last) {
				String msg = room.partial.toString();
				room.partial.setLength(0);
				try {
					handle(msg);
				} catch (RuntimeException e) {
					// ignore bad messages
				}
			}
			ws.request(1);
			return null;
		}

		private void handle(String msg) {
			if (msg.equals("pong")) {
				return;
			}
			JsonObject o = Json.obj(JsonParser.parseString(msg));
			String t = Json.str(o, "t");
			if (t == null) {
				return;
			}
			switch (t) {
				case "list" -> {
					Map<String, Player> m = new HashMap<>();
					for (JsonElement e : Json.arr(o, "players")) {
						add(m, Json.obj(e));
					}
					room.players = Collections.unmodifiableMap(m);
					room.voiceCapable = Json.bool(o, "voice", false);
					room.open = true;
					room.connecting = false;
					room.backoff = 5_000L;
					room.lastPing = System.currentTimeMillis();
					problem = null;
					if (room == server) {
						dev.solclient.video.VideoScreens.resendMine();
					}
				}
				case "screen" -> {
					if (room == lobby && room != server) {
						// the global lobby only carries profile data (badge, name style, flag, cosmetics)
						String lu = norm(Json.str(o, "u"));
						JsonElement lsc = o.get("s");
						if (lu != null && lsc != null && lsc.isJsonObject() && lsc.getAsJsonObject().has("kx")) {
							JsonObject lkx = Json.obj(lsc.getAsJsonObject().get("kx"));
							if (lkx != null) {
								MinecraftClient.getInstance().execute(() -> KuboLink.onRemote(lu, lkx));
							}
						}
					}
					if (room == server) {
						String u = norm(Json.str(o, "u"));
						JsonElement sc = o.get("s");
						// Caos party / ping data rides along with the screen message (works with the current Worker)
						if (sc != null && sc.isJsonObject() && sc.getAsJsonObject().has("kx")) {
							JsonObject kx = Json.obj(sc.getAsJsonObject().get("kx"));
							if (kx != null && u != null) {
								MinecraftClient.getInstance().execute(() -> KuboLink.onRemote(u, kx));
							}
							if (!sc.getAsJsonObject().has("v")) {
								return; // only Caos data, no watch-party screen
							}
						}
						long wnow = Json.num(o, "now", 0);
						MinecraftClient.getInstance().execute(() -> dev.solclient.video.VideoScreens.onRemote(u, sc, wnow));
					}
				}
				case "screens" -> {
					if (room == server || room == lobby) {
						long wnow = Json.num(o, "now", 0);
						for (JsonElement e : Json.arr(o, "list")) {
							JsonObject it = Json.obj(e);
							if (it != null) {
								String u = norm(Json.str(it, "u"));
								JsonElement sc = it.get("s");
								if (u != null && sc != null && sc.isJsonObject() && sc.getAsJsonObject().has("kx")) {
									// someone who was here before me: take his badge / name style now, not at his next message
									JsonObject kx = Json.obj(sc.getAsJsonObject().get("kx"));
									if (kx != null) {
										MinecraftClient.getInstance().execute(() -> KuboLink.onRemote(u, kx));
									}
								}
								if (room == lobby && room != server) {
									continue; // the lobby has no watch-party screens
								}
								if (sc != null && sc.isJsonObject() && !sc.getAsJsonObject().has("v")) {
									continue; // old Caos data only (pings / party): not a screen
								}
								MinecraftClient.getInstance().execute(() -> dev.solclient.video.VideoScreens.onRemote(u, sc, wnow));
							}
						}
					}
				}
				case "delta" -> {
					Map<String, Player> m = new HashMap<>(room.players);
					for (JsonElement e : Json.arr(o, "join")) {
						add(m, Json.obj(e));
					}
					for (String u : Json.strings(o, "leave")) {
						m.remove(norm(u));
					}
					room.players = Collections.unmodifiableMap(m);
				}
				case "vstates" -> {
					room.voiceOk = true;
					VoiceLink l = voice;
					if (l != null && room == server) {
						for (JsonElement e : Json.arr(o, "players")) {
							JsonObject p = Json.obj(e);
							l.state(norm(Json.str(p, "u")), Json.bool(p, "on", false), Json.bool(p, "m", false), Json.bool(p, "d", false));
						}
					}
				}
				case "vstate" -> {
					VoiceLink l = voice;
					if (l != null && room == server) {
						l.state(norm(Json.str(o, "u")), Json.bool(o, "on", false), Json.bool(o, "m", false), Json.bool(o, "d", false));
					}
				}
				case "emote" -> {
					String u = norm(Json.str(o, "u"));
					long id = Json.num(o, "id", -1);
					BiConsumer<String, Integer> cb = onEmote;
					if (u != null && id >= dev.solclient.mods.ArmorStandPosesMod.WIRE && room == server) { // an armor stand pose, not an emote
						int pose = (int) (id - dev.solclient.mods.ArmorStandPosesMod.WIRE);
						MinecraftClient.getInstance().execute(() -> dev.solclient.mods.ArmorStandPosesMod.onRemote(u, pose));
					} else if (u != null && id >= 0 && cb != null && room == server) {
						MinecraftClient.getInstance().execute(() -> cb.accept(u, (int) id));
					}
				}
				default -> {
					// "dm" and anything new: not used (chat goes through the site)
				}
			}
		}

		private void add(Map<String, Player> m, JsonObject p) {
			String u = norm(Json.str(p, "u"));
			String n = Json.str(p, "n");
			if (u != null && n != null) {
				m.put(u, new Player(u, n));
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
			closed(code, reason);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			closed(1006, "error");
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket ws, java.nio.ByteBuffer data, boolean last) {
			byte[] chunk = new byte[data.remaining()];
			data.get(chunk);
			room.partialBin.write(chunk, 0, chunk.length);
			if (last) {
				byte[] msg = room.partialBin.toByteArray();
				room.partialBin.reset();
				VoiceLink l = voice;
				// [1][16 bytes sender uuid][2 bytes seq][audio]
				if (l != null && room == server && msg.length > 19 && msg[0] == 1) {
					StringBuilder u = new StringBuilder(32);
					for (int i = 1; i <= 16; i++) {
						u.append(String.format("%02x", msg[i]));
					}
					l.audio(u.toString(), msg, 17);
				}
			}
			ws.request(1);
			return null;
		}

		private void closed(int code, String reason) {
			if (room.ws != null && room.ws != mine) {
				return; // an older socket of this room closing: the room already has a newer one
			}
			room.open = false;
			room.voiceOk = false;
			room.voiceCapable = false;
			room.connecting = false;
			room.ws = null;
			room.players = Map.of();
			if (room == server || room.dead) {
				dev.solclient.video.VideoScreens.clearRemote();
			}
			if (room.dead) {
				return;
			}
			long now = System.currentTimeMillis();
			switch (code) {
				case 4001 -> {
					// unauthorized: token not accepted (PRESENCE_SECRET different from the site's?) -> new token, slow retry
					problem = "Presence: token refused by the Worker (check PRESENCE_SECRET)";
					jwt = null;
					room.retryAt = now + 120_000L;
				}
				case 4004 -> room.retryAt = now + 600_000L; // same account connected from another game: let it have the spot
				case 4005 -> {
					problem = "Presence: this UUID is in use by another account";
					room.retryAt = now + 120_000L;
				}
				case 4429 -> room.retryAt = now + 30_000L;
				default -> {
					room.retryAt = now + room.backoff;
					room.backoff = Math.min(120_000L, room.backoff * 2);
				}
			}
		}
	}

	// ---------------------------------------------------------------- utils
	/** Same address -> same room for everyone: lower case, no trailing dot, default port 25565. */
	static String normalizeAddress(String address) {
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.endsWith(".")) {
			a = a.substring(0, a.length() - 1);
		}
		if (!a.contains(":")) {
			a = a + ":25565";
		}
		return a;
	}

	static String sha256(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
