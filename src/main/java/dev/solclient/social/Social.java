package dev.solclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.session.Session;

/**
 * Everything the mod knows from the Solar social backend: login, friends, chat, notifications,
 * coins / shop / wardrobe, capes & skins and the public player lookup used for in-game capes.
 *
 * Threading: network calls run on {@link #IO}; results replace the volatile fields below in one go,
 * so the menu (render thread) can read them at any time without locks.
 */
public final class Social {

	public static final ExecutorService IO = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "plegumo-social");
		t.setDaemon(true);
		return t;
	});

	// ================================================================== models
	public static final class User {
		public String id, name, mcUsername, mcUuid, image, capeUrl, status, activity;
		/** Badges the site gave this account (vip, youtube, twitch, tiktok). */
		public java.util.List<String> badges;

		public String display() {
			if (mcUsername != null && !mcUsername.isEmpty()) {
				return mcUsername;
			}
			return name != null && !name.isEmpty() ? name : "?";
		}

		/** LabyMod-style status: online / away / busy / on the phone (site from a phone) / offline. */
		public dev.solclient.gui.StatusBadge.Status state() {
			String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
			boolean game = Presence.online(this);
			if (s.equals("invisible") || s.equals("offline") && !game) {
				return dev.solclient.gui.StatusBadge.Status.OFFLINE;
			}
			if (s.equals("away") || s.equals("idle")) {
				return dev.solclient.gui.StatusBadge.Status.AWAY;
			}
			if (s.equals("busy") || s.equals("dnd")) {
				return dev.solclient.gui.StatusBadge.Status.BUSY;
			}
			if (game || s.equals("online") || s.equals("in_game")) {
				return dev.solclient.gui.StatusBadge.Status.ONLINE;
			}
			if (s.equals("mobile") || s.equals("web")) {
				return dev.solclient.gui.StatusBadge.Status.MOBILE;
			}
			return dev.solclient.gui.StatusBadge.Status.OFFLINE;
		}

		/** Online = connected to the presence Worker (the site itself never knows; its status is always "unknown"). */
		public boolean online() {
			return Presence.online(this) || status != null && (status.equalsIgnoreCase("online") || status.equalsIgnoreCase("in_game"));
		}

		/** On the same Minecraft server as you (presence Worker server room). */
		public boolean onMyServer() {
			return Presence.onMyServer(this);
		}

		static User parse(JsonObject o) {
			if (o == null) {
				return null;
			}
			User u = new User();
			u.id = Json.str(o, "id", "userId");
			u.name = Json.str(o, "name", "username");
			u.mcUsername = Json.str(o, "mcUsername");
			u.mcUuid = Json.str(o, "mcUuid");
			u.image = Json.str(o, "image");
			u.capeUrl = Json.str(o, "capeUrl");
			u.status = Json.str(o, "status");
			JsonElement bds = o.get("badges");
			if (bds != null && bds.isJsonArray()) {
				u.badges = new ArrayList<>();
				for (JsonElement be : bds.getAsJsonArray()) {
					if (be.isJsonPrimitive()) {
						u.badges.add(be.getAsString().toLowerCase(Locale.ROOT));
					}
				}
			}
			JsonElement act = o.get("activity");
			u.activity = act != null && act.isJsonPrimitive() ? act.getAsString() : null;
			return u;
		}
	}

	/** A friend request (incoming or outgoing). {@code id} is the request id used by PATCH / DELETE /friends/:id. */
	public record Request(String id, User user) {
	}

	/** One emoji reaction on a message: how many people and whether you are one of them. */
	public static final class Reaction {
		public String emoji;
		public int count;
		public boolean mine;
	}

	/** Friend chats keep only the newest messages (the site deletes older ones too). */
	public static final int CHAT_LIMIT = 20;

	public static final class Msg {
		public long id;
		public String senderId, kind, body, createdAt, editedAt;
		public List<Reaction> reactions = List.of();
		public String serverName, address; // invites
		/** Chat bubble style of the sender ("" = default), see dev.solclient.chat.Bubbles. */
		public String bubble = "";
		public boolean mine, pending, failed;
	}

	public static final class Notif {
		public long id;
		public String kind, title, detail;
		public boolean read;
	}

	public static final class Item {
		public String id, name, category, description, badge, imageUrl, modelId;
		/** Cape pictures / video the site sent with the item (null when it only has its shop picture). */
		public CapeMediaLogic.Desc capeMedia;
		public long price, finalPrice, discountPct;
		public boolean owned, equipped, featured;
		/** TEST UNLOCK: a bundled cosmetic added by the client itself (never sold by the site, equipped without asking the server). */
		public boolean local;
		/** Set when the item comes in several colours (the Locker shows a colour picker): the group id. */
		public String variantGroup;
		public List<String> colors = List.of();

		static Item parse(JsonObject o) {
			Item i = new Item();
			i.id = Json.str(o, "id");
			i.name = Json.str(o, "name");
			i.category = Json.str(o, "category");
			i.description = Json.str(o, "description");
			i.badge = Json.str(o, "badge");
			i.imageUrl = Json.str(o, "imageUrl");
			i.modelId = Json.str(o, "modelId", "model");
			i.price = Json.num(o, "price", 0);
			i.finalPrice = Json.num(o, "finalPrice", i.price);
			i.discountPct = Json.num(o, "discountPct", 0);
			i.owned = Json.bool(o, "owned", false);
			i.equipped = Json.bool(o, "equipped", false);
			i.featured = Json.bool(o, "featured", false);
			i.colors = Json.strings(o, "colors");
			i.capeMedia = CapeMedia.parse(Json.obj(o, "capeMedia"));
			if (i.name == null) {
				i.name = i.id == null ? "?" : i.id;
			}
			return i;
		}

		public boolean wearable() {
			return category != null && !category.equalsIgnoreCase("Emotes") && !category.equalsIgnoreCase("Bundles");
		}
	}

	public static final class Upload {
		public long id;
		public String name, kind, imageUrl;
	}

	/** Public lookup result for one player seen in the world. */
	public static final class PlayerInfo {
		public volatile String capeUrl, skinUrl;
		/** Shop cape with panels / pictures / video (null when there is none): see {@link CapeMedia}. */
		public volatile CapeMediaLogic.Desc capeMedia;
		public volatile Map<String, String> cosmetics = Map.of();
		public volatile boolean solar;
		final long fetchedAt;

		PlayerInfo(long fetchedAt) {
			this.fetchedAt = fetchedAt;
		}
	}

	// ================================================================== state
	public static volatile User me;
	/** Login state shown in the menu. */
	public static volatile String status = "Connecting...";
	public static volatile boolean loggingIn;
	private static volatile boolean keyRejected;

	public static volatile List<User> friends = List.of();
	public static volatile List<Request> incoming = List.of();
	public static volatile List<Request> outgoing = List.of();
	public static volatile boolean friendsLoaded;

	public static volatile List<Notif> notifications = List.of();
	public static volatile int unreadNotifications;
	public static volatile Map<String, Integer> unreadByUser = Map.of();
	private static volatile boolean inboxLoaded;
	public static volatile int unreadMessages;

	private static final Map<String, List<Msg>> MESSAGES = new ConcurrentHashMap<>();
	public static volatile String chatUserId;

	public static volatile long coins = -1;
	public static volatile boolean dailyReady;
	public static volatile String dailyNextAt;
	public static volatile long dailyCoins;
	public static volatile List<String> categories = List.of();
	public static volatile List<Item> shop = List.of();
	public static volatile String shopCategory = "Featured";
	public static volatile String shopState = "";

	public static volatile List<Upload> capes = List.of();
	public static volatile List<Upload> skins = List.of();
	public static volatile Long activeCapeId, activeSkinId;
	public static volatile String capesState = "";

	private static final Map<String, Item> CATALOGUE = new ConcurrentHashMap<>();
	private static final Map<UUID, PlayerInfo> PLAYERS = new ConcurrentHashMap<>();
	/** Friends' Minecraft UUIDs to look up (their Plegumo skin for the heads in the friends list). */
	private static final Set<UUID> EXTRA_LOOKUP = ConcurrentHashMap.newKeySet();

	/** Skin URL a friend uploaded to Plegumo (null if none / not known yet; asks for it in the background). */
	public static String friendSkin(User u) {
		if (u == null || u.mcUuid == null) {
			return null;
		}
		UUID id;
		try {
			String h = u.mcUuid.replace("-", "");
			id = h.length() == 32 ? UUID.fromString(h.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")) : UUID.fromString(u.mcUuid);
		} catch (IllegalArgumentException e) {
			return null;
		}
		PlayerInfo p = PLAYERS.get(id);
		if (p == null) {
			EXTRA_LOOKUP.add(id);
			return null;
		}
		return p.skinUrl;
	}

	/** Short feedback line shown in the menu and as an in-game toast. */
	public static volatile String flash;
	public static volatile boolean flashError;
	public static volatile long flashAt;

	// timers (client thread only)
	private static volatile long nextLogin, nextNotif, nextInbox, nextFriends, nextChat, nextLookup, nextCatalogue, nextGroupPoll;
	/** Id of the last message seen in each friend conversation / time of the last one seen in each group (to pop up only new ones). */
	private static final Map<String, String> LAST_MSG = new ConcurrentHashMap<>();
	private static final Map<String, Long> MY_GROUP_SEND = new ConcurrentHashMap<>();
	private static final AtomicInteger busy = new AtomicInteger();
	private static final Set<String> running = ConcurrentHashMap.newKeySet();
	private static final Set<Long> seenNotifs = ConcurrentHashMap.newKeySet();
	private static volatile boolean notifSeeded;
	private static final long startedAt = System.currentTimeMillis();
	public static volatile boolean menuOpen;

	private static final Pattern INVITE = Pattern.compile("\\[ghost-invite\\|v1\\|([^|\\]]*)\\|([^\\]]*)\\]");

	private Social() {
	}

	// ================================================================== lifecycle
	public static void init() {
		SolarApi.loadSettings();
		String fromLauncher = System.getProperty("solar.token");
		if (fromLauncher == null || fromLauncher.isBlank()) {
			fromLauncher = System.getenv("SOLAR_TOKEN");
		}
		if (fromLauncher != null && !fromLauncher.isBlank()) {
			SolarApi.token = fromLauncher.trim();
		}
	}

	/** Called every client tick. Schedules the background jobs. */
	// ---- "X is online" / "X is playing" cards (like the user's notificacao.html)
	private static final Map<String, Boolean> WAS_ONLINE = new java.util.HashMap<>();
	private static final Map<String, String> WAS_DOING = new java.util.HashMap<>();
	private static long friendWatchAt, friendWatchSince;

	private static void friendWatch(long now) {
		if (now - friendWatchAt < 2_000L) {
			return;
		}
		friendWatchAt = now;
		if (friendWatchSince == 0) {
			friendWatchSince = now;
		}
		boolean quiet = now - friendWatchSince < 20_000L; // just started: everybody "comes online" at once, say nothing
		for (User u : friends) {
			if (u.id == null) {
				continue;
			}
			boolean on = u.online();
			Boolean was = WAS_ONLINE.put(u.id, on);
			String doing = u.activity == null ? "" : u.activity.strip();
			String before = WAS_DOING.put(u.id, doing);
			if (quiet || was == null) {
				continue;
			}
			if (Mutes.isMuted(u)) {
				continue; // muted on this client: no "is online" / "is playing" cards
			}
			if (on && !was) {
				dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.ONLINE, u.display() + dev.solclient.Lang.tr(" is online", " ficou online"),
						doing.isEmpty() ? dev.solclient.Lang.tr("In the main menu", "No menu principal") : doing, u.display());
			} else if (on && !doing.isEmpty() && !doing.equals(before)) {
				dev.solclient.gui.Notify.playing(u.display() + dev.solclient.Lang.tr(" is playing", " está a jogar"), doing, u.display());
			}
		}
	}

	// ---- my own status (top bar: click your name). Away by itself after 5 minutes without touching anything.
	public static volatile dev.solclient.gui.StatusBadge.Status myStatus = dev.solclient.gui.StatusBadge.Status.ONLINE;
	private static volatile dev.solclient.gui.StatusBadge.Status sentStatus;
	private static volatile boolean autoAway;
	private static long lastInputAt = System.currentTimeMillis(), statusSentAt;
	private static double lastMx, lastMy;

	public static void setMyStatus(dev.solclient.gui.StatusBadge.Status s) {
		myStatus = s;
		autoAway = false;
		lastInputAt = System.currentTimeMillis();
	}

	/** What friends see. */
	public static dev.solclient.gui.StatusBadge.Status effectiveStatus() {
		return autoAway && myStatus == dev.solclient.gui.StatusBadge.Status.ONLINE ? dev.solclient.gui.StatusBadge.Status.AWAY : myStatus;
	}

	private static void statusTick(MinecraftClient mc, long now) {
		double mx = mc.mouse.getX(), my = mc.mouse.getY();
		boolean moved = Math.abs(mx - lastMx) + Math.abs(my - lastMy) > 0.5 || mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
				|| mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.attackKey.isPressed();
		lastMx = mx;
		lastMy = my;
		if (moved) {
			lastInputAt = now;
			autoAway = false;
		} else if (now - lastInputAt > 300_000L) {
			autoAway = true;
		}
		var st = effectiveStatus();
		if (st != sentStatus || now - statusSentAt > 300_000L) {
			sentStatus = st;
			statusSentAt = now;
			String v = switch (st) {
				case AWAY -> "away";
				case BUSY -> "busy";
				case INVISIBLE -> "invisible";
				default -> "online";
			};
			job("status", () -> {
				JsonObject o = new JsonObject();
				o.addProperty("status", v);
				SolarApi.post("/me/status", o); // see LOVABLE-PROMPT-V100.md; ignored by older sites
			});
		}
	}

	public static void tick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		Presence.tick(mc);
		KuboLive.tick();
		if (me != null) {
			friendWatch(now);
			statusTick(mc, now);
		}
		if (me == null) {
			if (!keyRejected && now >= nextLogin) {
				nextLogin = now + 60_000L;
				job("login", Social::login);
			}
			return;
		}
		if (now >= nextNotif) {
			nextNotif = now + 30_000L;
			job("notif", Social::loadNotifications);
		}
		if (now >= nextInbox) {
			nextInbox = now + 6_000L; // new friend messages and invites pop up quickly
			job("inbox", Social::loadInbox);
		}
		if (groupsSupported && now >= nextGroupPoll) {
			nextGroupPoll = now + 15_000L; // new group messages pop up too (the full group list is only refreshed every 2 minutes)
			job("groups-poll", Social::loadGroups);
		}
		if (now >= nextFriends) {
			nextFriends = now + (menuOpen ? 30_000L : 120_000L);
			job("friends", Social::loadFriends);
			job("groups", Social::loadGroups);
		}
		String chat = chatUserId;
		if (chat != null && menuOpen && now >= nextChat) {
			nextChat = now + 4_000L;
			job("chat", () -> loadChat(chat, false));
		}
		if (now >= nextCatalogue) {
			nextCatalogue = now + 600_000L;
			job("catalogue", Social::loadCatalogue);
		}
		if ((mc.world != null || !EXTRA_LOOKUP.isEmpty()) && now >= nextLookup) {
			nextLookup = now + 5_000L;
			List<String> wanted = new ArrayList<>();
			for (UUID x : EXTRA_LOOKUP) {
				wanted.add(x.toString());
			}
			EXTRA_LOOKUP.clear();
			// you, by your account UUID (on offline-mode servers your in-game UUID is different)
			UUID mine = mc.getSession().getUuidOrNull();
			if (mine != null) {
				PlayerInfo mi = PLAYERS.get(mine);
				if (mi == null || now - mi.fetchedAt > 60_000L) {
					wanted.add(mine.toString());
				}
			}
			for (AbstractClientPlayerEntity p : mc.world == null ? List.<AbstractClientPlayerEntity>of() : mc.world.getPlayers()) {
				// on offline-mode servers the in-game uuid is not the account uuid the site knows;
				// presence gives us the account uuid by matching the game nick
				UUID id = Presence.backendId(p);
				if (id == null) {
					continue;
				}
				PlayerInfo info = PLAYERS.get(id);
				if (info == null || now - info.fetchedAt > 60_000L) {
					wanted.add(id.toString());
					if (wanted.size() >= 100) {
						break;
					}
				}
			}
			if (!wanted.isEmpty()) {
				job("lookup", () -> lookup(wanted));
			}
		}
	}

	/** Menu opened: refresh what it shows right away. */
	public static void onMenuOpened() {
		menuOpen = true;
		nextFriends = 0;
		nextNotif = 0;
		nextInbox = 0;
		if (me == null && !keyRejected) {
			nextLogin = 0;
		}
	}

	public static void onMenuClosed() {
		menuOpen = false;
		// the chat that was open is not open any more: its new messages must pop up like everybody else's
		// (before, messages from the last friend you chatted with never showed up until you opened their chat again)
		chatUserId = null;
	}

	/** Game closing: report play time (short, blocking call). */
	public static void shutdown() {
		if (me == null) {
			return;
		}
		JsonObject b = new JsonObject();
		b.addProperty("seconds", (System.currentTimeMillis() - startedAt) / 1000L);
		SolarApi.send("POST", "/me/activity", b, true, 3);
	}

	private static void job(String name, Runnable r) {
		if (!running.add(name)) {
			return;
		}
		IO.execute(() -> {
			try {
				r.run();
			} catch (Throwable e) {
				System.err.println("[Sleep] social job " + name + " failed: " + e);
			} finally {
				running.remove(name);
			}
		});
	}

	/** Runs a user action in the background (buttons in the menu). */
	private static void action(Runnable r) {
		busy.incrementAndGet();
		IO.execute(() -> {
			try {
				r.run();
			} catch (Throwable e) {
				say("Error: " + e.getClass().getSimpleName(), true);
			} finally {
				busy.decrementAndGet();
			}
		});
	}

	public static boolean busy() {
		return busy.get() > 0;
	}

	public static void say(String text, boolean error) {
		dev.solclient.gui.Notify.show(error ? dev.solclient.gui.Notify.Kind.ERROR : dev.solclient.gui.Notify.Kind.KUBO, text, "");
		flash = text;
		flashError = error;
		flashAt = System.currentTimeMillis();
	}

	private static final Map<String, int[]> ICON_SIZE = new ConcurrentHashMap<>();

	private static int[] sizeOf(Identifier id) {
		return ICON_SIZE.computeIfAbsent(id.toString(), k -> {
			try (java.io.InputStream in = MinecraftClient.getInstance().getResourceManager().getResource(id).get().getInputStream();
					net.minecraft.client.texture.NativeImage img = net.minecraft.client.texture.NativeImage.read(in)) {
				return new int[] {img.getWidth(), img.getHeight()};
			} catch (Exception | Error e) {
				return new int[] {0, 0};
			}
		});
	}

	/** The real picture of an item for the toasts: its bundled thumbnail (cosmetics, kill effects) or the one the site sent. Null = none yet. */
	private static Object[] itemIcon(Item it) {
		try {
			var rm = MinecraftClient.getInstance().getResourceManager();
			if (isKillEffect(it)) {
				Identifier id = dev.solclient.Gfx.id("textures/killfx/thumb/" + killEffectKey(it) + ".png");
				if (rm.getResource(id).isPresent()) {
					int[] sz = sizeOf(id);
					return sz[0] > 0 ? new Object[] {id, sz[0], sz[1]} : null;
				}
			}
			dev.solclient.cosmetic.CosmeticModels.Def d = dev.solclient.cosmetic.Cosmetics.modelFor(it);
			if (d != null) {
				d = dev.solclient.cosmetic.Variants.resolve(d);
				Identifier id = dev.solclient.Gfx.id("textures/cosmetics/thumb/" + d.id() + ".png");
				if (rm.getResource(id).isPresent()) {
					int[] sz = sizeOf(id);
					return sz[0] > 0 ? new Object[] {id, sz[0], sz[1]} : null;
				}
			}
			if (it.imageUrl != null && !it.imageUrl.isBlank()) {
				RemoteTextures.Tex t = RemoteTextures.get(it.imageUrl);
				if (t != null && t.width() > 0) {
					return new Object[] {t.id(), t.width(), t.height()};
				}
			}
		} catch (RuntimeException | Error ignored) {
			// no picture: the normal toast
		}
		return null;
	}

	/** A toast about an item ("Bought X!", "Equipped X"): with the item's own picture as its icon when there is one. */
	public static void sayItem(String text, Item it) {
		Object[] ic = it == null ? null : itemIcon(it);
		if (ic == null) {
			say(text, false);
			return;
		}
		dev.solclient.gui.Notify.item(dev.solclient.gui.Notify.Kind.KUBO, text, "", (Identifier) ic[0], (Integer) ic[1], (Integer) ic[2]);
		flash = text;
		flashError = false;
		flashAt = System.currentTimeMillis();
	}

	/** Shared handling for failed calls. Returns true when the call worked. */
	private static boolean check(SolarApi.Res r, boolean announce) {
		if (r.ok()) {
			return true;
		}
		if (r.status() == 403 && r.error() != null && r.error().toLowerCase(Locale.ROOT).contains("client key")) {
			keyRejected = true;
			logout("Invalid client key (see SolarApi.CLIENT_KEY)");
			return false;
		}
		if (r.status() == 401) {
			logout("Session expired, logging in again...");
			nextLogin = 0;
			return false;
		}
		if (announce) {
			say(r.error() == null ? "Request failed" : r.error(), true);
		}
		return false;
	}

	private static void logout(String why) {
		me = null;
		SolarApi.token = null;
		SolarApi.saveSettings();
		status = why;
	}

	// ================================================================== login
	private static boolean looksLikeMinecraftToken(String t) {
		return t != null && t.length() > 60 && t.startsWith("ey") && t.chars().filter(ch -> ch == '.').count() == 2;
	}

	private static void login() {
		loggingIn = true;
		try {
			Session s = MinecraftClient.getInstance().getSession();
			String name = s.getUsername();

			// 1. saved (or launcher) token still valid and for this Minecraft account?
			if (SolarApi.token != null) {
				SolarApi.Res r = SolarApi.get("/auth/get-session");
				if (r.status() == 403 && r.error() != null && r.error().toLowerCase(Locale.ROOT).contains("client key")) {
					keyRejected = true;
					status = "Invalid client key (see SolarApi.CLIENT_KEY)";
					return;
				}
				User u = r.ok() ? User.parse(Json.obj(r.obj(), "user")) : null;
				if (u != null && (u.mcUsername == null || u.mcUsername.equalsIgnoreCase(name))) {
					loggedIn(u);
					return;
				}
				if (r.status() == 0) {
					status = "Can't reach the Solar servers";
					return;
				}
				SolarApi.token = null;
			}

			// 2. fresh login: Microsoft account -> /auth/minecraft, offline / cracked -> /auth/offline
			String mcToken = s.getAccessToken();
			SolarApi.Res r;
			if (looksLikeMinecraftToken(mcToken)) {
				JsonObject b = new JsonObject();
				b.addProperty("token", mcToken);
				b.addProperty("accessToken", mcToken); // same value, for backends that use this name
				b.addProperty("username", name);
				if (s.getUuidOrNull() != null) {
					b.addProperty("uuid", s.getUuidOrNull().toString().replace("-", ""));
				}
				r = SolarApi.send("POST", "/auth/minecraft", b, false, 15);
			} else {
				JsonObject b = new JsonObject();
				b.addProperty("username", name);
				r = SolarApi.send("POST", "/auth/offline", b, false, 15);
			}
			if (!r.ok()) {
				if (r.status() == 403 && r.error() != null && r.error().toLowerCase(Locale.ROOT).contains("client key")) {
					keyRejected = true;
					status = "Invalid client key (see SolarApi.CLIENT_KEY)";
				} else if (r.status() == 0) {
					status = "Can't reach the Solar servers";
				} else {
					status = "Login failed: " + r.error();
				}
				return;
			}
			String tok = Json.str(Json.obj(r.obj(), "session"), "token");
			User u = User.parse(Json.obj(r.obj(), "user"));
			if (tok == null || u == null) {
				status = "Login failed: bad response";
				return;
			}
			SolarApi.token = tok;
			SolarApi.saveSettings();
			loggedIn(u);
		} finally {
			loggingIn = false;
		}
	}

	private static void loggedIn(User u) {
		User old = me;
		if (old == null || old.id == null || !old.id.equals(u.id)) {
			Presence.reset();
		}
		me = u;
		status = "Online";
		JsonObject b = new JsonObject();
		b.addProperty("launched", true);
		SolarApi.post("/me/activity", b);
		nextNotif = 0;
		nextInbox = 0;
		nextFriends = 0;
		nextCatalogue = 0;
		nextLookup = 0;
		PLAYERS.clear();
		loadWallet();
	}

	/** The Minecraft account was switched (title screen): log in to Solar again with the new one. */
	public static void accountChanged() {
		me = null;
		MY_EQUIP.clear();
		builtinLoaded = false;
		SHOP_CACHE.clear();
		MY_UNEQUIP.clear();
		status = "Connecting...";
		keyRejected = false;
		friends = List.of();
		incoming = List.of();
		outgoing = List.of();
		notifications = List.of();
		unreadNotifications = 0;
		unreadMessages = 0;
		unreadByUser = Map.of();
		coins = -1;
		MESSAGES.clear();
		chatUserId = null;
		PLAYERS.clear();
		Presence.reset();
		nextLogin = 0;
	}

	public static void retryLogin() {
		keyRejected = false;
		status = "Connecting...";
		nextLogin = 0;
	}

	// ================================================================== friends
	private static void loadFriends() {
		SolarApi.Res r = SolarApi.get("/friends");
		if (!check(r, false)) {
			return;
		}
		JsonObject o = r.obj();
		List<User> fr = new ArrayList<>();
		for (JsonElement e : Json.arr(o, "friends")) {
			JsonObject fo = Json.obj(e);
			User u = User.parse(Json.obj(fo, "user") != null ? Json.obj(fo, "user") : fo);
			if (u != null && u.id != null) {
				fr.add(u);
			}
		}
		fr.sort((a, b) -> a.display().compareToIgnoreCase(b.display()));
		fr.sort((a, b) -> Boolean.compare(b.online(), a.online()));
		friends = Collections.unmodifiableList(fr);
		incoming = parseRequests(Json.arr(o, "incoming"));
		outgoing = parseRequests(Json.arr(o, "outgoing"));
		friendsLoaded = true;
	}

	private static List<Request> parseRequests(JsonArray a) {
		List<Request> out = new ArrayList<>();
		for (JsonElement e : a) {
			JsonObject o = Json.obj(e);
			if (o == null) {
				continue;
			}
			// accept both { id: <request id>, user: {...} } and a flat object
			JsonObject uo = Json.obj(o, "user");
			if (uo == null) {
				uo = Json.obj(o, "from");
			}
			if (uo == null) {
				uo = Json.obj(o, "to");
			}
			String reqId = Json.str(o, "requestId", "id");
			User u = User.parse(uo != null ? uo : o);
			if (reqId != null && u != null) {
				out.add(new Request(reqId, u));
			}
		}
		return Collections.unmodifiableList(out);
	}

	public static void addFriend(String query) {
		String q = query.trim();
		if (q.isEmpty() || me == null) {
			return;
		}
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("query", q);
			SolarApi.Res r = SolarApi.post("/friends", b);
			if (check(r, true)) {
				say("Friend request sent to " + q, false);
				loadFriends();
			}
		});
	}

	public static void answerRequest(Request req, boolean accept) {
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("action", accept ? "accept" : "reject");
			SolarApi.Res r = SolarApi.patch("/friends/" + SolarApi.enc(req.id()), b);
			if (check(r, true)) {
				say(accept ? "You are now friends with " + req.user().display() : "Request rejected", false);
				loadFriends();
				loadNotifications();
			}
		});
	}

	/** Cancels an outgoing request (request id). */
	public static void cancelRequest(Request req) {
		action(() -> {
			SolarApi.Res r = SolarApi.delete("/friends/" + SolarApi.enc(req.id()));
			if (check(r, true)) {
				say("Request cancelled", false);
				loadFriends();
			}
		});
	}

	public static void removeFriend(User u) {
		action(() -> {
			SolarApi.Res r = SolarApi.delete("/friends/" + SolarApi.enc(u.id));
			if (check(r, true)) {
				say("Removed " + u.display(), false);
				if (u.id.equals(chatUserId)) {
					chatUserId = null;
				}
				loadFriends();
			}
		});
	}

	public static User friend(String id) {
		if (id == null) {
			return null;
		}
		for (User u : friends) {
			if (id.equals(u.id)) {
				return u;
			}
		}
		return null;
	}

	// ================================================================== chat
	public static List<Msg> messages(String userId) {
		List<Msg> l = MESSAGES.get(userId);
		return l == null ? List.of() : l;
	}

	public static void openChat(String userId) {
		chatUserId = userId;
		nextChat = System.currentTimeMillis() + 4_000L;
		job("chat-open", () -> loadChat(userId, true));
	}

	public static void closeChat() {
		chatUserId = null;
	}

	/** Sender ids seen on messages I sent (the site may use a different id for me than /me returns). */
	private static final Set<String> MY_SENDER_IDS = ConcurrentHashMap.newKeySet();

	private static Boolean bool(JsonObject o, String... keys) {
		for (String k : keys) {
			JsonElement e = o.get(k);
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				return e.getAsBoolean();
			}
		}
		return null;
	}

	/** Reactions as [{emoji,count,mine}] or {"emoji": ["userId", ...]} / {"emoji": count}. */
	private static List<Reaction> parseReactions(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return List.of();
		}
		List<Reaction> out = new ArrayList<>();
		String myId = me == null ? null : me.id;
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				JsonObject r = Json.obj(x);
				if (r == null) {
					continue;
				}
				Reaction rc = new Reaction();
				rc.emoji = Json.str(r, "emoji");
				rc.count = (int) Json.num(r, "count", 1);
				Boolean mine = bool(r, "mine", "byMe");
				rc.mine = mine != null && mine;
				if (rc.emoji != null && rc.count > 0) {
					out.add(rc);
				}
			}
		} else if (e.isJsonObject()) {
			for (String k : e.getAsJsonObject().keySet()) {
				JsonElement v = e.getAsJsonObject().get(k);
				Reaction rc = new Reaction();
				rc.emoji = k;
				if (v.isJsonArray()) {
					rc.count = v.getAsJsonArray().size();
					for (JsonElement u : v.getAsJsonArray()) {
						String uid = u.isJsonPrimitive() ? u.getAsString() : null;
						if (uid != null && (uid.equals(myId) || MY_SENDER_IDS.contains(uid))) {
							rc.mine = true;
						}
					}
				} else if (v.isJsonPrimitive()) {
					rc.count = v.getAsInt();
				}
				if (rc.count > 0) {
					out.add(rc);
				}
			}
		}
		return out;
	}

	private static void replaceMsg(String userId, Msg old, Msg now) {
		synchronized (MESSAGES) {
			List<Msg> l = new ArrayList<>(messages(userId));
			int i = l.indexOf(old);
			if (i >= 0) {
				if (now == null) {
					l.remove(i);
				} else {
					l.set(i, now);
				}
			}
			MESSAGES.put(userId, Collections.unmodifiableList(l));
		}
	}

	private static Msg copy(Msg m) {
		Msg c = new Msg();
		c.id = m.id;
		c.senderId = m.senderId;
		c.kind = m.kind;
		c.body = m.body;
		c.createdAt = m.createdAt;
		c.editedAt = m.editedAt;
		c.reactions = m.reactions;
		c.serverName = m.serverName;
		c.address = m.address;
		c.mine = m.mine;
		c.pending = m.pending;
		c.failed = m.failed;
		return c;
	}

	/** Edits one of your messages (shown at once, then saved on the site). */
	public static void editMessage(String userId, Msg m, String text) {
		if (m == null || !m.mine || m.pending || text == null || text.isBlank() || text.equals(m.body)) {
			return;
		}
		Msg n = copy(m);
		n.body = text;
		n.editedAt = java.time.Instant.now().toString();
		replaceMsg(userId, m, n);
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("body", text);
			SolarApi.Res r = SolarApi.patch("/messages/" + m.id, b);
			if (!r.ok()) {
				replaceMsg(userId, n, m);
				say(r.status() == 404 ? "Editing needs the site update (LOVABLE-PROMPT.md)" : "Could not edit the message", true);
			}
		});
	}

	/** Deletes one of your messages for both of you. */
	public static void deleteMessage(String userId, Msg m) {
		if (m == null || !m.mine) {
			return;
		}
		replaceMsg(userId, m, null);
		if (m.pending || m.failed) {
			return;
		}
		action(() -> {
			SolarApi.Res r = SolarApi.delete("/messages/" + m.id);
			if (!r.ok()) {
				say(r.status() == 404 ? "Deleting needs the site update (LOVABLE-PROMPT.md)" : "Could not delete the message", true);
				loadChat(userId, true);
			}
		});
	}

	/** Adds your reaction (or removes it if you already reacted with that emoji). */
	public static void react(String userId, Msg m, String emoji) {
		if (m == null || m.pending || m.failed || emoji == null) {
			return;
		}
		Msg n = copy(m);
		List<Reaction> rs = new ArrayList<>();
		boolean found = false;
		for (Reaction r : m.reactions) {
			Reaction c = new Reaction();
			c.emoji = r.emoji;
			c.count = r.count;
			c.mine = r.mine;
			if (r.emoji.equals(emoji)) {
				found = true;
				c.mine = !r.mine;
				c.count += c.mine ? 1 : -1;
			}
			if (c.count > 0) {
				rs.add(c);
			}
		}
		if (!found) {
			Reaction c = new Reaction();
			c.emoji = emoji;
			c.count = 1;
			c.mine = true;
			rs.add(c);
		}
		n.reactions = rs;
		replaceMsg(userId, m, n);
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("emoji", emoji);
			SolarApi.Res r = SolarApi.post("/messages/" + m.id + "/reactions", b);
			if (!r.ok()) {
				replaceMsg(userId, n, m);
				say(r.status() == 404 ? "Reactions need the site update (LOVABLE-PROMPT.md)" : "Could not react", true);
			}
		});
	}

	/** partnerId = the friend of this conversation (null if unknown). */
	/** The bubble of messages you sent this session (until the site sends it back with every message). */
	private static final Map<Long, String> MY_BUBBLES = new java.util.concurrent.ConcurrentHashMap<>();

	private static Msg parseMsg(JsonObject o, String partnerId) {
		Msg m = new Msg();
		m.id = Json.num(o, "id", 0);
		m.senderId = Json.str(o, "senderId", "sender_id", "fromUserId", "from_user_id", "fromId", "authorId", "author_id", "from");
		String to = Json.str(o, "recipientId", "recipient_id", "receiverId", "receiver_id", "toUserId", "to_user_id", "toId", "to");
		m.kind = Json.str(o, "kind");
		m.body = Json.str(o, "body", "text", "content");
		m.createdAt = Json.str(o, "createdAt", "created_at");
		m.editedAt = Json.str(o, "editedAt", "edited_at");
		m.reactions = parseReactions(o.get("reactions"));
		String bub = Json.str(o, "bubble", "bubbleStyle", "bubble_style");
		m.bubble = bub == null || bub.isEmpty() ? MY_BUBBLES.getOrDefault(m.id, "") : bub;
		User meNow = me;
		Boolean flag = bool(o, "mine", "isMine", "fromMe", "outgoing", "sentByMe");
		if (flag != null) {
			m.mine = flag;
		} else if (m.senderId != null && (MY_SENDER_IDS.contains(m.senderId) || meNow != null && m.senderId.equals(meNow.id))) {
			m.mine = true;
		} else if (m.senderId != null && partnerId != null) {
			m.mine = !m.senderId.equals(partnerId); // a 1-to-1 chat: not sent by the friend = sent by me
		} else if (to != null && partnerId != null) {
			m.mine = to.equals(partnerId);
		} else {
			m.mine = false;
		}
		JsonObject meta = Json.obj(o, "meta");
		if (meta != null) {
			m.serverName = Json.str(meta, "serverName", "name");
			m.address = Json.str(meta, "address", "serverAddress");
		}
		if (m.body != null) {
			Matcher mt = INVITE.matcher(m.body);
			if (mt.find()) {
				if (m.serverName == null) {
					m.serverName = mt.group(1);
				}
				if (m.address == null) {
					m.address = mt.group(2);
				}
				m.body = (m.body.substring(0, mt.start()) + m.body.substring(mt.end())).trim();
				m.kind = "invite";
			}
		}
		if ("invite".equals(m.kind) && m.address == null) {
			m.kind = "text";
		}
		return m;
	}

	private static void loadChat(String userId, boolean full) {
		// chats are short (last 20 messages): always fetch them all, so edits, deletes and reactions show up too
		String q = isGroup(userId) ? "/groups/" + SolarApi.enc(groupId(userId)) + "/messages?limit=" + CHAT_LIMIT
				: "/messages/" + SolarApi.enc(userId) + "?limit=" + CHAT_LIMIT;
		SolarApi.Res r = SolarApi.get(q);
		if (!check(r, false)) {
			return;
		}
		List<Msg> got = new ArrayList<>();
		for (JsonElement e : Json.arr(r.obj(), "messages")) {
			JsonObject mo = Json.obj(e);
			if (mo != null) {
				got.add(parseMsg(mo, isGroup(userId) ? null : userId));
			}
		}
		boolean newIncoming = false;
		synchronized (MESSAGES) {
			List<Msg> now = MESSAGES.get(userId);
			Set<Long> had = new HashSet<>();
			List<Msg> list = new ArrayList<>(got);
			if (now != null) {
				for (Msg m : now) {
					if (m.pending || m.failed) {
						list.add(m);
					} else {
						had.add(m.id);
					}
				}
			}
			for (Msg m : got) {
				if (now != null && !had.contains(m.id) && !m.mine) {
					newIncoming = true;
				}
			}
			list.sort((a, b) -> a.pending == b.pending ? Long.compare(a.id, b.id) : a.pending ? 1 : -1);
			while (list.size() > CHAT_LIMIT) {
				list.remove(0);
			}
			MESSAGES.put(userId, Collections.unmodifiableList(list));
		}
		Integer unread = unreadByUser.get(userId);
		if (!isGroup(userId) && (newIncoming || (unread != null && unread > 0)) && userId.equals(chatUserId)) {
			JsonObject b = new JsonObject();
			b.addProperty("userId", userId);
			SolarApi.post("/messages/read", b);
			Map<String, Integer> m = new HashMap<>(unreadByUser);
			Integer was = m.remove(userId);
			unreadByUser = Collections.unmodifiableMap(m);
			if (was != null) {
				unreadMessages = Math.max(0, unreadMessages - was);
			}
		}
	}

	public static void sendMessage(String userId, String text) {
		sendRaw(userId, text, text);
		User u = friend(userId);
		if (u != null) {
			KuboLive.typing(u.display(), false); // sent: no more "typing..."
			if (!menuOpen) {
				dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.SENT, dev.solclient.Lang.tr("Message sent", "Mensagem enviada"),
						dev.solclient.Lang.tr("To ", "Para ") + u.display() + ": " + text, u.display());
			}
		}
	}

	/** Sends a failed message again (removes the red "not sent" copy). */
	public static void retryMessage(String userId, Msg m) {
		if (m == null || !m.failed || m.body == null) {
			return;
		}
		synchronized (MESSAGES) {
			List<Msg> l = new ArrayList<>(messages(userId));
			l.remove(m);
			MESSAGES.put(userId, Collections.unmodifiableList(l));
		}
		sendRaw(userId, m.body, m.body);
	}

	/** Sends a server invite for the server you are playing on. */
	public static boolean sendInvite(String userId) {
		if (HostWorld.hosting()) {
			// hosting your own world: invite to it (and let them in if it is Invite Only)
			for (User u : friends) {
				if (userId.equals(u.id)) {
					HostWorld.invite(u);
					return true;
				}
			}
			sendInviteTo(userId, HostWorld.worldName(), HostWorld.address());
			return true;
		}
		var entry = MinecraftClient.getInstance().getCurrentServerEntry();
		if (entry == null || entry.address == null || entry.address.isBlank()) {
			say("Join a server first to invite friends", true);
			return false;
		}
		String name = entry.name == null || entry.name.isBlank() ? entry.address : entry.name;
		name = name.replace("|", "/").replace("]", ")");
		String addr = entry.address.replace("|", "").replace("]", "");
		sendRaw(userId, "Join me on " + name + "![ghost-invite|v1|" + name + "|" + addr + "]", "Invite: " + name);
		return true;
	}

	/** Invite card for any address (used by Host World). */
	public static void sendInviteTo(String userId, String name, String address) {
		String n = (name == null || name.isBlank() ? address : name).replace("|", "/").replace("]", ")");
		String a = address.replace("|", "").replace("]", "");
		sendRaw(userId, "Join me on " + n + "![ghost-invite|v1|" + n + "|" + a + "]", "Invite: " + n);
	}

	private static void sendRaw(String userId, String body, String preview) {
		Msg p = new Msg();
		p.pending = true;
		p.mine = true;
		p.kind = "text";
		p.body = preview;
		p.bubble = dev.solclient.chat.Bubbles.mine();
		synchronized (MESSAGES) {
			List<Msg> l = new ArrayList<>(messages(userId));
			l.add(p);
			MESSAGES.put(userId, Collections.unmodifiableList(l));
		}
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("body", body);
			b.addProperty("bubble", p.bubble); // your bubble style: your friends see it too
			SolarApi.Res r;
			if (isGroup(userId)) {
				MY_GROUP_SEND.put(groupId(userId), System.currentTimeMillis());
				r = SolarApi.post("/groups/" + SolarApi.enc(groupId(userId)) + "/messages", b);
			} else {
				b.addProperty("userId", userId);
				r = SolarApi.post("/messages", b);
			}
			JsonObject mo = r.ok() ? Json.obj(r.obj(), "message") : null;
			if (mo != null && !isGroup(userId)) {
				// a card like "X is online" so you see the message really went out
				String nick = userId;
				for (User fu : friends) {
					if (userId.equals(fu.id)) {
						nick = fu.display();
						break;
					}
				}
				dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.MSG, dev.solclient.Lang.tr("Message sent", "Mensagem enviada"),
						dev.solclient.Lang.tr("to ", "para ") + nick, nick);
			}
			synchronized (MESSAGES) {
				List<Msg> l = new ArrayList<>(messages(userId));
				l.remove(p);
				if (mo != null) {
					Msg real = parseMsg(mo, isGroup(userId) ? null : userId);
					real.mine = true;
					if (real.bubble.isEmpty()) {
						real.bubble = p.bubble; // the site does not store bubbles yet: keep yours on what you sent
					}
					MY_BUBBLES.put(real.id, p.bubble);
					if (real.senderId != null && !real.senderId.equals(userId)) {
						MY_SENDER_IDS.add(real.senderId);
					}
					boolean dup = false;
					for (Msg m : l) {
						if (!m.pending && m.id == real.id) {
							dup = true;
						}
					}
					if (!dup) {
						l.add(real);
					}
				} else {
					p.failed = true;
					p.pending = false;
					p.id = Long.MAX_VALUE;
					l.add(p);
				}
				MESSAGES.put(userId, Collections.unmodifiableList(l));
			}
			if (mo == null) {
				check(r, true);
			}
		});
	}

	private static void loadInbox() {
		SolarApi.Res r = SolarApi.get("/messages");
		if (!check(r, false)) {
			return;
		}
		Map<String, Integer> map = new HashMap<>();
		Map<String, Integer> before = unreadByUser;
		boolean first = !inboxLoaded;
		inboxLoaded = true;
		for (JsonElement e : Json.arr(r.obj(), "conversations")) {
			JsonObject co = Json.obj(e);
			User u = User.parse(Json.obj(co, "user"));
			long n = Json.num(co, "unread", 0);
			JsonObject last = Json.obj(co, "lastMessage");
			String lastId = last == null ? null : Json.str(last, "id", "createdAt", "created_at");
			String prevId = u == null || u.id == null ? null : LAST_MSG.get(u.id);
			if (u != null && u.id != null && lastId != null) {
				LAST_MSG.put(u.id, lastId);
			}
			if (u != null && u.id != null && n > 0) {
				map.put(u.id, (int) n);
				// a new message from this friend: pop-up with their head, like Discord. Only the chat you are looking at right
				// now is left out (the menu is open on it). A new message is spotted by its id as well as by the unread count,
				// so it is not missed when the count did not go up.
				boolean chatOpen = menuOpen && u.id.equals(chatUserId);
				boolean fresh = n > before.getOrDefault(u.id, 0) || (lastId != null && prevId != null && !lastId.equals(prevId));
				if (!first && fresh && !chatOpen) {
					String body = last == null ? null : Json.str(last, "body");
					if (body == null || body.isBlank()) {
						body = dev.solclient.Lang.tr("sent you a message", "enviou-te uma mensagem");
					} else if (body.startsWith("[kubo-wp] ")) {
						body = dev.solclient.Lang.tr("sent you a location", "enviou-te uma localização");
					} else if (dev.solclient.chat.Gifs.isGif(body)) {
						body = "GIF";
					}
					if (!Mutes.isMuted(u)) {
						Matcher inv = INVITE.matcher(last == null ? "" : String.valueOf(Json.str(last, "body")));
						if (inv.find()) {
							// a server invite: "X invited you" with the server, and a click joins it
							final String srv = inv.group(2);
							dev.solclient.gui.Notify.clickable(dev.solclient.gui.Notify.Kind.MSG, u.display() + dev.solclient.Lang.tr(" invited you", " convidou-te"),
									inv.group(1) + dev.solclient.Lang.tr("  -  click to join", "  -  clica para entrar"), u.display(), () -> ServerShare.join(srv));
						} else {
							SocialToast.message(u.display(), body);
						}
					}
				}
			}
		}
		unreadByUser = Collections.unmodifiableMap(map);
		unreadMessages = (int) Json.num(r.obj(), "unreadTotal", map.values().stream().mapToInt(Integer::intValue).sum());
	}

	// ================================================================== notifications
	private static String label(String kind) {
		if (kind == null) {
			return "Notification";
		}
		switch (kind) {
			case "friend_request": return "Friend request";
			case "friend_accept":
			case "friend_accepted": return "Friend request accepted";
			case "message": return "New message";
			case "invite": return "Server invite";
			default:
				String k = kind.replace('_', ' ');
				return k.isEmpty() ? "Notification" : Character.toUpperCase(k.charAt(0)) + k.substring(1);
		}
	}

	private static Notif parseNotif(JsonObject o) {
		Notif n = new Notif();
		n.id = Json.num(o, "id", 0);
		n.kind = Json.str(o, "kind");
		n.read = Json.bool(o, "read", false) || Json.bool(o, "isRead", false) || Json.str(o, "readAt", "read_at") != null || READ_LOCAL.contains(n.id);
		String from = Json.str(o, "fromName", "senderName", "name");
		if (from == null) {
			User u = User.parse(Json.obj(o, "from"));
			if (u == null) {
				u = User.parse(Json.obj(o, "user"));
			}
			if (u != null) {
				from = u.display();
			}
		}
		n.title = label(n.kind) + (from != null && !"?".equals(from) ? " - " + from : "");
		String detail = Json.str(o, "body", "text", "message", "serverName");
		JsonObject meta = Json.obj(o, "meta");
		if (detail == null && meta != null) {
			detail = Json.str(meta, "serverName", "name", "body");
		}
		if (detail != null) {
			Matcher mt = INVITE.matcher(detail);
			if (mt.find()) {
				detail = mt.group(1);
			}
		}
		n.detail = detail;
		return n;
	}

	private static void loadNotifications() {
		SolarApi.Res r = SolarApi.get("/notifications");
		if (!check(r, false)) {
			return;
		}
		List<Notif> list = new ArrayList<>();
		for (JsonElement e : Json.arr(r.obj(), "notifications")) {
			JsonObject no = Json.obj(e);
			if (no != null) {
				list.add(parseNotif(no));
			}
		}
		notifications = Collections.unmodifiableList(list);
		// count from the list itself: notifications you already opened stay read even if the site forgets it
		int unread = (int) list.stream().filter(n -> !n.read).count();
		unreadNotifications = unread;

		// in-game toast for new unread notifications
		Notif newest = null;
		int fresh = 0;
		for (Notif n : list) {
			if (!n.read && seenNotifs.add(n.id)) {
				if ("message".equals(n.kind) || "New message".equals(n.title)) {
					continue; // messages get their own pop-up with the friend's head (loadInbox)
				}
				fresh++;
				if (newest == null) {
					newest = n;
				}
			}
		}
		if (!notifSeeded) {
			notifSeeded = true;
			if (unread > 0) {
				SocialToast.show("Sleep", "You have " + unread + " unread notification" + (unread == 1 ? "" : "s"));
			}
		} else if (newest != null) {
			SocialToast.show(newest.title, fresh > 1 ? "+" + (fresh - 1) + " more" : newest.detail);
			if ("friend_request".equals(newest.kind)) {
				loadFriends();
			}
			if ("message".equals(newest.kind) || "invite".equals(newest.kind)) {
				loadInbox();
			}
		}
	}

	/** Notifications you have seen, remembered on this PC (so they never come back as new). */
	private static final java.util.Set<Long> READ_LOCAL = loadReadLocal();

	private static java.util.Set<Long> loadReadLocal() {
		java.util.Set<Long> s = ConcurrentHashMap.newKeySet();
		try {
			java.nio.file.Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-notifications-read.txt");
			if (java.nio.file.Files.exists(p)) {
				for (String l : java.nio.file.Files.readAllLines(p)) {
					try { s.add(Long.parseLong(l.trim())); } catch (NumberFormatException ignored) { }
				}
			}
		} catch (Exception ignored) {
		}
		return s;
	}

	private static void saveReadLocal() {
		try {
			StringBuilder b = new StringBuilder();
			READ_LOCAL.stream().sorted(java.util.Comparator.reverseOrder()).limit(500).forEach(id -> b.append(id).append('\n'));
			java.nio.file.Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-notifications-read.txt"), b.toString());
		} catch (Exception ignored) {
		}
	}

	public static void markNotificationsRead() {
		if (unreadNotifications == 0) {
			return;
		}
		List<Notif> local = new ArrayList<>();
		for (Notif n : notifications) {
			n.read = true;
			READ_LOCAL.add(n.id);
			local.add(n);
		}
		notifications = Collections.unmodifiableList(local);
		unreadNotifications = 0;
		IO.execute(Social::saveReadLocal);
		action(() -> {
			SolarApi.Res r = SolarApi.post("/notifications/read", new JsonObject());
			if (check(r, false)) {
				List<Notif> list = new ArrayList<>();
				for (Notif n : notifications) {
					n.read = true;
					list.add(n);
				}
				notifications = Collections.unmodifiableList(list);
				unreadNotifications = 0;
			}
		});
	}

	public static void deleteNotification(Notif n) {
		action(() -> {
			SolarApi.Res r = SolarApi.delete("/notifications/" + n.id);
			if (check(r, true)) {
				List<Notif> list = new ArrayList<>(notifications);
				list.remove(n);
				notifications = Collections.unmodifiableList(list);
				if (!n.read) {
					unreadNotifications = Math.max(0, unreadNotifications - 1);
				}
			}
		});
	}

	// ================================================================== coins & shop
	private static void readWallet(JsonObject o) {
		if (o == null) {
			return;
		}
		if (o.has("coins")) {
			coins = Json.num(o, "coins", coins);
		}
		JsonObject d = Json.obj(o, "daily");
		if (d != null) {
			dailyReady = Json.bool(d, "ready", false);
			dailyNextAt = Json.str(d, "nextAt");
			dailyCoins = Json.num(d, "coins", 0);
		}
	}

	public static void loadWallet() {
		SolarApi.Res r = SolarApi.get("/wallet");
		if (check(r, false)) {
			readWallet(r.obj());
		}
	}

	/** Items per category (and "All"): switching section shows them at once, the site is asked in the background. */
	private static final Map<String, List<Item>> SHOP_CACHE = new ConcurrentHashMap<>();
	private static volatile long walletAt, allAt;

	private static List<Item> localFilter(String category) {
		List<Item> all = SHOP_CACHE.get("All");
		if (all == null) {
			return null;
		}
		List<Item> out = new ArrayList<>();
		for (Item it : all) {
			boolean ok = switch (category) {
				case "All" -> true;
				case "Featured" -> it.featured;
				case "Sale" -> it.discountPct > 0;
				default -> it.category != null && it.category.equalsIgnoreCase(category);
			};
			if (ok) {
				out.add(it);
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static volatile long showcaseAskedAt;

	/**
	 * Up to 'max' shop items for the home-screen banner: items of the season first (keyword in the name / badge),
	 * then featured, then the rest. Loads the shop in the background the first time.
	 */
	public static List<Item> showcase(String keyword, int max) {
		List<Item> all = SHOP_CACHE.get("All");
		if (all == null) {
			long t = System.currentTimeMillis();
			if (t - showcaseAskedAt > 60_000L) {
				showcaseAskedAt = t;
				IO.execute(() -> safe(() -> fetchShop("All")));
			}
			return List.of();
		}
		List<Item> out = new ArrayList<>();
		String k = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
		for (int pass = 0; pass < 3 && out.size() < max; pass++) {
			for (Item it : all) {
				if (out.size() >= max || out.contains(it) || it.imageUrl == null) {
					continue;
				}
				String hay = ((it.name == null ? "" : it.name) + " " + (it.badge == null ? "" : it.badge)).toLowerCase(Locale.ROOT);
				boolean ok = pass == 0 ? k != null && hay.contains(k) : pass == 1 ? it.featured : true;
				if (ok) {
					out.add(it);
				}
			}
		}
		return out;
	}

	/**
	 * TEST UNLOCK: every cosmetic bundled in the jar (hats, wings, tails, pets...) shows up as owned in the Locker so it can be tried
	 * without buying it. Turn off with -Dkubo.unlock=0 (or delete this block before shipping to players).
	 */
	public static final boolean UNLOCK_ALL = "1".equals(System.getProperty("kubo.unlock")); // test mode only: everything is bought in the shop now
	private static List<Item> localItems;

	/**
	 * Cosmetics the client draws by itself (no model file): free for everyone and always in the Locker, even when the site does not
	 * sell them or cannot be reached. If the site does sell one, the site's item wins (price, owned, equipped).
	 */
	public static final List<String> BUILTIN_CATEGORIES = List.of("Auras", "Minimes");

	public static boolean builtin(String category) {
		if (category != null) {
			for (String c : BUILTIN_CATEGORIES) {
				if (c.equalsIgnoreCase(category)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean builtinLoaded;

	private static java.nio.file.Path builtinFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("builtin-equip.txt");
	}

	/** The aura / minime you equipped stays on after a restart (the site does not know these, so it is kept on this computer). */
	private static synchronized void loadBuiltinEquip() {
		if (builtinLoaded) {
			return;
		}
		builtinLoaded = true;
		try {
			java.nio.file.Path f = builtinFile();
			if (java.nio.file.Files.exists(f)) {
				for (String l : java.nio.file.Files.readAllLines(f)) {
					int eq = l.indexOf('=');
					if (eq > 0 && builtin(l.substring(0, eq)) && !MY_EQUIP.containsKey(l.substring(0, eq))
							&& dev.solclient.cosmetic.CosmeticModels.find(l.substring(eq + 1).strip()) != null) {
						MY_EQUIP.put(l.substring(0, eq), l.substring(eq + 1).strip());
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] built-in cosmetics: " + e);
		}
	}

	private static synchronized void saveBuiltinEquip() {
		try {
			List<String> lines = new ArrayList<>();
			for (String c : BUILTIN_CATEGORIES) {
				String id = MY_EQUIP.get(c);
				if (id != null && dev.solclient.cosmetic.CosmeticModels.find(id) != null) {
					lines.add(c + "=" + id);
				}
			}
			java.nio.file.Path f = builtinFile();
			java.nio.file.Files.createDirectories(f.getParent());
			java.nio.file.Files.write(f, lines);
		} catch (Exception e) {
			System.err.println("[Sleep] built-in cosmetics: " + e);
		}
	}

	private static synchronized List<Item> localItems() {
		if (localItems == null) {
			List<Item> l = new ArrayList<>();
			for (dev.solclient.cosmetic.CosmeticModels.Def d : dev.solclient.cosmetic.CosmeticModels.list()) {
				if (dev.solclient.cosmetic.CosmeticModels.isExtraVariant(d)) {
					continue; // another colour of an item we already list
				}
				Item it = new Item();
				it.id = d.id();
				it.name = d.groupName() != null ? d.groupName() : d.name();
				it.variantGroup = d.group();
				if (d.group() != null) {
					java.util.List<String> sw = new ArrayList<>();
					for (dev.solclient.cosmetic.CosmeticModels.Def v : dev.solclient.cosmetic.CosmeticModels.variantsOf(d.group())) {
						sw.add(v.swatch());
					}
					it.colors = sw;
				}
				it.category = d.category();
				it.modelId = d.id();
				it.description = d.credit();
				it.owned = true;
				it.local = true;
				it.badge = builtin(d.category()) ? "Free" : "Test";
				l.add(it);
			}
			localItems = l;
		}
		return localItems;
	}

	// ---- presents from the client itself (e.g. the Christmas card): bundled cosmetics that are yours for free, kept in config/kubo/gifts.txt
	private static java.util.Set<String> gifts;

	private static synchronized java.util.Set<String> gifts() {
		if (gifts == null) {
			gifts = new java.util.LinkedHashSet<>();
			try {
				java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("gifts.txt");
				if (java.nio.file.Files.exists(f)) {
					for (String l : java.nio.file.Files.readAllLines(f)) {
						if (!l.isBlank()) {
							gifts.add(l.strip());
						}
					}
				}
			} catch (Exception ignored) {
			}
		}
		return gifts;
	}

	/**
	 * Gives you a bundled cosmetic for free (shows as owned in the Locker). It is local: the site does not know it, so other players
	 * only see it if the client shares it. Returns the item (to equip it), or null if the client has no such cosmetic.
	 */
	public static synchronized Item gift(String id) {
		gifts().add(id);
		try {
			java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("gifts.txt");
			java.nio.file.Files.createDirectories(f.getParent());
			java.nio.file.Files.write(f, gifts());
		} catch (Exception ignored) {
		}
		for (Item it : localItems()) {
			if (id.equals(it.id)) {
				it.badge = "Gift";
				CATALOGUE.put(it.id, it);
				return it;
			}
		}
		return null;
	}

	/** The site's list plus the bundled cosmetics that belong in this section (the site's own copy wins, but it is shown as owned). */
	private static List<Item> withLocal(String category, List<Item> list) {
		if (category == null || category.equalsIgnoreCase("Emotes") || category.equalsIgnoreCase("Capes")) {
			return list;
		}
		loadBuiltinEquip();
		List<Item> out = new ArrayList<>(list);
		java.util.Set<String> have = new java.util.HashSet<>();
		for (Item it : out) {
			if (it.id != null) {
				have.add(it.id);
			}
			if (it.modelId != null) {
				have.add(it.modelId);
			}
		}
		for (Item it : localItems()) {
			if (!UNLOCK_ALL && !gifts().contains(it.id) && !builtin(it.category)) {
				continue; // only the presents you were given (auras and minimes are free for everyone)
			}
			if (!have.contains(it.id) && (category.equals("All") || category.equalsIgnoreCase(it.category))) {
				it.equipped = it.id.equals(MY_EQUIP.get(it.category));
				CATALOGUE.put(it.id, it);
				out.add(it);
			}
		}
		return Collections.unmodifiableList(out);
	}

	public static void loadShop(String category) {
		shopCategory = category;
		List<Item> now = SHOP_CACHE.get(category);
		if (now == null) {
			now = localFilter(category);
		}
		if (now == null || SHOP_CACHE.get(category) == null) {
			now = withLocal(category, now == null ? List.of() : now);
		}
		if (now != null) {
			shop = now;
			shopState = now.isEmpty() ? "Nothing here yet" : "";
		} else {
			shop = List.of(); // never show the previous section's items while loading
			shopState = "Loading...";
		}
		IO.execute(() -> safe(() -> {
			long t = System.currentTimeMillis();
			if (t - walletAt > 30_000L) {
				walletAt = t;
				loadWallet();
			}
			fetchShop(category);
			// the whole shop once in a while, so every other section opens instantly
			if (!category.equals("All") && t - allAt > 60_000L) {
				allAt = t;
				fetchShop("All");
			}
		}));
	}

	private static void fetchShop(String category) {
		SolarApi.Res r = SolarApi.get("/shop?category=" + SolarApi.enc(category));
		if (!check(r, false)) {
			if (category.equals(shopCategory) && shop.isEmpty()) {
				shopState = r.error() == null ? "Could not load the shop" : r.error();
			}
			return;
		}
		JsonObject o = r.obj();
		if (o.has("coins")) {
			coins = Json.num(o, "coins", coins);
		}
		List<String> cats = Json.strings(o, "categories");
		if (!cats.isEmpty()) {
			if (!cats.contains("All")) {
				cats.add(0, "All");
			}
			for (String b : BUILTIN_CATEGORIES) {
				if (cats.stream().noneMatch(b::equalsIgnoreCase)) {
					cats.add(b);
				}
			}
			categories = Collections.unmodifiableList(cats);
		}
		List<Item> items = new ArrayList<>();
		for (JsonElement e : Json.arr(o, "items")) {
			JsonObject io = Json.obj(e);
			if (io != null) {
				items.add(Item.parse(io));
			}
		}
		for (Item it : items) {
			if (it.id != null) {
				CATALOGUE.put(it.id, it);
			}
			if (held(it.category)) {
				// you clicked equip / unequip a moment ago and the site has not caught up: your click is what counts, not its old list
				it.equipped = it.owned && it.id != null && it.id.equals(MY_EQUIP.get(it.category));
			} else {
				rememberEquip(it, it.owned && it.equipped);
			}
		}
		List<Item> list = withLocal(category, Collections.unmodifiableList(items));
		SHOP_CACHE.put(category, list);
		if (category.equals("All")) {
			allAt = System.currentTimeMillis();
		}
		if (category.equals(shopCategory)) {
			shop = list;
			shopState = items.isEmpty() ? "Nothing here yet" : "";
		}
	}

	/** After buying / equipping: forget the cached lists so the next view is fresh. */
	private static void shopChanged() {
		allAt = 0; // the cached lists stay on screen (no flicker) and are refreshed in the background
	}

	private static void safe(Runnable r) {
		try {
			r.run();
		} catch (Throwable e) {
			System.err.println("[Sleep] social: " + e);
		}
	}

	public static void buy(Item it) {
		buy(it, null);
	}

	/** Buys an item; afterBuy runs once the purchase went through (e.g. "wear item after purchase"). */
	public static void buy(Item it, Runnable afterBuy) {
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("itemId", it.id);
			SolarApi.Res r = SolarApi.post("/shop/buy", b);
			if (r.status() == 402) {
				say("Not enough coins", true);
				return;
			}
			if (check(r, true)) {
				coins = Json.num(r.obj(), "coins", coins);
				sayItem("Bought " + it.name + "!", it);
				it.owned = true;
				// every copy of the item (the "All" list, the category list...) is yours now, so equipping works at once
				for (List<Item> l : SHOP_CACHE.values()) {
					for (Item o : l) {
						if (o.id != null && o.id.equals(it.id)) {
							o.owned = true;
						}
					}
				}
				shopChanged();
				refreshOwned();
				if (afterBuy != null) {
					afterBuy.run();
				} else {
					loadShop(shopCategory);
				}
			}
		});
	}

	/** All shop items the client has seen so far (used by the crates' offline preview roll). */
	public static List<Item> catalogue() {
		return new ArrayList<>(CATALOGUE.values());
	}

	/** What the site answered when a crate was opened. {@code demo} = the site has no crates yet (nothing was charged or given). */
	public static final class CrateResult {
		public boolean ok, demo, duplicate;
		public String error, rarity;
		public Item item;
		public long refund, coinsWon;
	}

	/**
	 * Opens a crate: POST /shop/crate/open {crateId}. The site takes the coins and rolls the prize (see LOVABLE-PROMPT-CRATES.md).
	 * A 404 / 405 / 501 means the site does not know crates yet: the answer is then flagged {@code demo} and nothing happens to the account.
	 */
	public static void crateOpen(String crateId, java.util.function.Consumer<CrateResult> done) {
		action(() -> {
			CrateResult out = new CrateResult();
			try {
				JsonObject b = new JsonObject();
				b.addProperty("crateId", crateId);
				SolarApi.Res r = SolarApi.post("/shop/crate/open", b);
				int st = r.status();
				if (st == 404 || st == 405 || st == 501) {
					out.ok = true;
					out.demo = true;
				} else if (st == 402) {
					out.error = "Not enough coins";
				} else if (st >= 200 && st < 300 && r.obj() != null) {
					JsonObject o = r.obj();
					if (o.has("coins")) {
						coins = Json.num(o, "coins", coins);
					}
					JsonObject rw = Json.obj(o, "reward");
					if (rw == null) {
						rw = o;
					}
					JsonObject io = Json.obj(rw, "item");
					if (io != null) {
						out.item = Item.parse(io);
						if (out.item.id != null) {
							CATALOGUE.put(out.item.id, out.item);
						}
					}
					out.rarity = Json.str(rw, "rarity");
					out.duplicate = Json.bool(rw, "duplicate", false);
					out.refund = Json.num(rw, "refund", 0);
					out.coinsWon = Json.num(rw, "coinsWon", 0);
					out.ok = true;
					shopChanged();
					refreshOwned();
				} else {
					out.error = r.error() == null ? "Could not open the crate" : r.error();
				}
			} catch (RuntimeException e) {
				out.error = "Could not open the crate";
			}
			done.accept(out);
		});
	}

	/** Marks {@code it} as worn (or not) in every list we know. The others of its category come off; they are added to {@code off}. */
	private static void setEquippedLocal(Item it, boolean on, List<Item> off) {
		if (on) {
			// one item per category: the others of the same category come off (in every list we know)
			List<Item> known = new ArrayList<>(shop);
			SHOP_CACHE.values().forEach(known::addAll);
			for (Item o : known) {
				if (o != it && o.equipped && o.category != null && o.category.equalsIgnoreCase(it.category) && !(o.id != null && o.id.equals(it.id))) {
					o.equipped = false;
					off.add(o);
				}
			}
		}
		it.equipped = on;
		for (List<Item> l : SHOP_CACHE.values()) {
			for (Item o : l) {
				if (o.id != null && o.id.equals(it.id)) {
					o.equipped = on;
				}
			}
		}
	}

	/** category -> until when (ms) your own click wins over what the site's list still says. */
	private static final Map<String, Long> EQUIP_HOLD = new ConcurrentHashMap<>();

	private static void holdEquip(String category, long ms) {
		if (category != null) {
			EQUIP_HOLD.put(category, System.currentTimeMillis() + ms);
		}
	}

	private static boolean held(String category) {
		Long t = category == null ? null : EQUIP_HOLD.get(category);
		return t != null && System.currentTimeMillis() < t;
	}

	/** Equips / unequips at once on screen, then tells the server in the background (and undoes it if the server says no). */
	public static void equip(Item it, boolean on) {
		final boolean was = it.equipped;
		final List<Item> off = new ArrayList<>();
		holdEquip(it.category, 20_000L);
		setEquippedLocal(it, on, off);
		rememberEquip(it, on);
		dev.solclient.cosmetic.Cosmetics.invalidate();
		shopChanged();
		sayItem(on ? "Equipped " + it.name : "Unequipped " + it.name, it);
		if (it.local && isKillEffect(it)) {
			setLocalKill(on ? killEffectKey(it) : null); // the site sells none: remembered on this computer
			return;
		}
		if (it.local) {
			if (builtin(it.category)) {
				saveBuiltinEquip();
			}
			return; // bundled cosmetic: the site does not know it
		}
		action(() -> {
			SolarApi.Res r;
			try {
				if (on) {
					JsonObject b = new JsonObject();
					b.addProperty("itemId", it.id);
					r = SolarApi.put("/wardrobe/equip", b);
				} else {
					// capes are unequipped by category ("Capes"): a previously worn personal cape shows again
					r = SolarApi.delete("/wardrobe/equip/" + SolarApi.enc("Capes".equalsIgnoreCase(it.category) ? "Capes" : it.id));
				}
			} finally {
				holdEquip(it.category, 3_000L); // the site answers a moment later: until then its old list must not undo your click
			}
			if (check(r, true)) {
				forgetMyLookup();
			} else {
				// the server refused: put everything back the way it was
				setEquippedLocal(it, was, new ArrayList<>());
				for (Item o : off) {
					o.equipped = true;
					for (List<Item> l : SHOP_CACHE.values()) {
						for (Item q : l) {
							if (q.id != null && q.id.equals(o.id)) {
								q.equipped = true;
							}
						}
					}
				}
				shopChanged();
				forgetMyLookup();
				loadShop(shopCategory);
			}
		});
	}

	// ================================================================== capes & skins
	public static void loadCapes() {
		capesState = "Loading...";
		IO.execute(() -> safe(() -> {
			SolarApi.Res r = SolarApi.get("/capes");
			if (!check(r, false)) {
				capesState = r.error() == null ? "Could not load" : r.error();
				return;
			}
			JsonObject o = r.obj();
			capes = parseUploads(Json.arr(o, "capes"), "cape");
			skins = parseUploads(Json.arr(o, "skins"), "skin");
			JsonObject act = Json.obj(o, "active");
			activeCapeId = Json.numOrNull(act, "capeId");
			activeSkinId = Json.numOrNull(act, "skinId");
			capesState = "";
		}));
	}

	private static List<Upload> parseUploads(JsonArray a, String wantKind) {
		List<Upload> out = new ArrayList<>();
		for (JsonElement e : a) {
			JsonObject o = Json.obj(e);
			if (o == null) {
				continue;
			}
			Upload u = new Upload();
			u.id = Json.num(o, "id", 0);
			u.name = Json.str(o, "name");
			u.kind = Json.str(o, "kind");
			u.imageUrl = Json.str(o, "imageUrl");
			if (u.kind != null && !u.kind.equals(wantKind)) {
				continue; // "capes" may also contain skins: keep each list clean
			}
			out.add(u);
		}
		return Collections.unmodifiableList(out);
	}

	/** Sets the active cape / skin. Pass the current value for the one you don't change (omitting it would remove it). */
	public static void setActive(Long capeId, Long skinId) {
		action(() -> {
			JsonObject b = new JsonObject();
			if (capeId == null) {
				b.add("capeId", com.google.gson.JsonNull.INSTANCE);
			} else {
				b.addProperty("capeId", capeId);
			}
			if (skinId == null) {
				b.add("skinId", com.google.gson.JsonNull.INSTANCE);
			} else {
				b.addProperty("skinId", skinId);
			}
			SolarApi.Res r = SolarApi.put("/capes/active", b);
			if (check(r, true)) {
				JsonObject act = Json.obj(r.obj(), "active");
				activeCapeId = act != null ? Json.numOrNull(act, "capeId") : capeId;
				activeSkinId = act != null ? Json.numOrNull(act, "skinId") : skinId;
				say("Saved", false);
				forgetMyLookup();
			}
		});
	}

	public static void deleteUpload(Upload u) {
		action(() -> {
			SolarApi.Res r = SolarApi.delete("/capes/" + u.id);
			if (check(r, true)) {
				say("Deleted " + (u.name == null ? "" : u.name), false);
				forgetMyLookup();
				loadCapes();
			}
		});
	}

	/** Uploads a skin PNG (64x64). Cape uploads were removed: capes come from the shop. */
	public static void upload(Path file) {
		action(() -> {
			try {
				byte[] data = Files.readAllBytes(file);
				if (data.length < 24 || (data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') {
					say("Only PNG files", true);
					return;
				}
				int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
				int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
				String kind;
				if (w == 64 && h == 32) {
					if (!ProfileBadges.canUploadCape()) {
						say("Your own cape is for VIPs: get capes in the shop instead", true);
						return;
					}
					kind = "cape";
				} else if (w == 64 && h == 64) {
					kind = "skin";
				} else {
					say("Skin must be 64x64 (this is " + w + "x" + h + ")", true);
					return;
				}
				String name = file.getFileName().toString().replaceAll("(?i)\\.png$", "");
				if (name.length() > 32) {
					name = name.substring(0, 32);
				}
				JsonObject b = new JsonObject();
				b.addProperty("name", name);
				b.addProperty("kind", kind);
				b.addProperty("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(data));
				SolarApi.Res r = SolarApi.send("POST", "/capes", b, true, 30);
				if (check(r, true)) {
					say("Uploaded " + kind + " \"" + name + "\"", false);
					loadCapes();
				}
			} catch (java.io.IOException e) {
				say("Could not read the file", true);
			}
		});
	}

	/**
	 * Skin changer: uploads a skin to Solar and makes it the active one, so other Plegumo players see it
	 * (used for offline accounts, where Mojang can't be told). Blocking: call off the render thread. Null = OK.
	 */
	public static String uploadSkinAndUse(byte[] png, String name) {
		if (me == null) {
			return "Log in to Solar to share the skin";
		}
		JsonObject b = new JsonObject();
		b.addProperty("name", name.length() > 32 ? name.substring(0, 32) : name);
		b.addProperty("kind", "skin");
		b.addProperty("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
		SolarApi.Res r = SolarApi.send("POST", "/capes", b, true, 30);
		if (!r.ok()) {
			return r.error() == null ? "Solar upload failed (" + r.status() + ")" : r.error();
		}
		Long id = null;
		JsonObject o = r.obj();
		for (String k : new String[]{"skin", "cape", "upload", "item"}) {
			JsonObject in = Json.obj(o, k);
			if (in != null && Json.numOrNull(in, "id") != null) {
				id = Json.numOrNull(in, "id");
			}
		}
		if (id == null) {
			id = Json.numOrNull(o, "id");
		}
		if (id == null) {
			SolarApi.Res l = SolarApi.get("/capes");
			if (l.ok()) {
				for (Upload u : parseUploads(Json.arr(l.obj(), "skins"), "skin")) {
					if (id == null || u.id > id) {
						id = u.id;
					}
				}
			}
		}
		if (id == null) {
			return "Uploaded, but could not activate it";
		}
		JsonObject act = new JsonObject();
		if (activeCapeId == null) {
			act.add("capeId", com.google.gson.JsonNull.INSTANCE);
		} else {
			act.addProperty("capeId", activeCapeId);
		}
		act.addProperty("skinId", id);
		SolarApi.Res ar = SolarApi.put("/capes/active", act);
		if (!ar.ok()) {
			return ar.error() == null ? "Could not activate the skin (" + ar.status() + ")" : ar.error();
		}
		activeSkinId = id;
		forgetMyLookup();
		return null;
	}


	// ================================================================== groups (up to 10 people, 3 groups per person)
	public static final int GROUP_MAX_MEMBERS = 10, GROUPS_PER_USER = 3;

	public static final class Group {
		public String id, name, ownerId, lastBody, lastAt;
		/** Group logo chosen by the owner ("" = the members' heads), see dev.solclient.chat.GroupIcons. */
		public String icon = "";
		public List<User> members = List.of();

		public String key() {
			return "g:" + id;
		}

		public boolean owner() {
			return me != null && me.id != null && me.id.equals(ownerId);
		}

		public User member(String userId) {
			for (User u : members) {
				if (u.id != null && u.id.equals(userId)) {
					return u;
				}
			}
			return null;
		}
	}

	public static volatile List<Group> groups = List.of();
	public static volatile boolean groupsSupported = true;

	public static boolean isGroup(String chatKey) {
		return chatKey != null && chatKey.startsWith("g:");
	}

	public static String groupId(String chatKey) {
		return chatKey.substring(2);
	}

	public static Group group(String chatKey) {
		if (!isGroup(chatKey)) {
			return null;
		}
		String id = groupId(chatKey);
		for (Group g : groups) {
			if (id.equals(g.id)) {
				return g;
			}
		}
		return null;
	}

	private static void loadGroups() {
		SolarApi.Res r = SolarApi.get("/groups");
		if (r.status() == 404) {
			groupsSupported = false;
			return;
		}
		if (!check(r, false)) {
			return;
		}
		groupsSupported = true;
		List<Group> out = new ArrayList<>();
		for (JsonElement e : Json.arr(r.obj(), "groups")) {
			JsonObject o = Json.obj(e);
			if (o == null) {
				continue;
			}
			Group g = new Group();
			g.id = Json.str(o, "id");
			g.name = Json.str(o, "name");
			String gi = Json.str(o, "icon", "logo");
			g.icon = gi != null ? gi : GROUP_ICONS.getOrDefault(g.id, "");
			g.ownerId = Json.str(o, "ownerId", "owner_id");
			JsonObject last = Json.obj(o, "lastMessage");
			if (last != null) {
				g.lastBody = Json.str(last, "body");
				g.lastAt = Json.str(last, "createdAt", "created_at");
			}
			List<User> ms = new ArrayList<>();
			for (JsonElement m : Json.arr(o, "members")) {
				User u = User.parse(Json.obj(m));
				if (u != null) {
					ms.add(u);
				}
			}
			g.members = Collections.unmodifiableList(ms);
			if (g.id != null) {
				out.add(g);
			}
		}
		boolean firstGroups = !groupsLoadedOnce;
		groupsLoadedOnce = true;
		for (Group g : out) {
			String prev = LAST_MSG.put("g:" + g.id, String.valueOf(g.lastAt));
			if (firstGroups || prev == null || String.valueOf(g.lastAt).equals(prev) || g.lastBody == null || g.lastBody.isBlank()) {
				continue;
			}
			Long mine = MY_GROUP_SEND.get(g.id);
			boolean iSent = mine != null && System.currentTimeMillis() - mine < 20_000L; // my own message just sent
			boolean looking = menuOpen && g.key().equals(chatUserId);
			if (!iSent && !looking) {
				String body = g.lastBody.startsWith("[kubo-wp] ") ? dev.solclient.Lang.tr("sent a location", "enviou uma localização")
						: dev.solclient.chat.Gifs.isGif(g.lastBody) ? "GIF" : g.lastBody;
				dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.MSG, dev.solclient.Lang.tr("Message in ", "Mensagem em ") + g.name, body, g.name);
			}
		}
		groups = Collections.unmodifiableList(out);
	}
	private static volatile boolean groupsLoadedOnce;

	public static void refreshGroups() {
		job("groups", Social::loadGroups);
	}

	private static boolean groupResult(SolarApi.Res r, String okMsg) {
		if (r.status() == 404) {
			say("Groups need the site update (LOVABLE-PROMPT.md)", true);
			return false;
		}
		if (!r.ok()) {
			String err = r.obj() == null ? null : Json.str(r.obj(), "error", "message");
			say(err != null ? err : "Something went wrong", true);
			return false;
		}
		if (okMsg != null) {
			say(okMsg, false);
		}
		loadGroups();
		return true;
	}

	public static void createGroup(String name, List<String> memberIds, java.util.function.Consumer<String> opened) {
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("name", name);
			JsonArray a = new JsonArray();
			memberIds.forEach(a::add);
			b.add("memberIds", a);
			SolarApi.Res r = SolarApi.post("/groups", b);
			if (groupResult(r, "Group created")) {
				JsonObject g = Json.obj(r.obj(), "group");
				String id = g == null ? null : Json.str(g, "id");
				if (id != null && opened != null) {
					MinecraftClient.getInstance().execute(() -> opened.accept("g:" + id));
				}
			}
		});
	}

	public static void addToGroup(Group g, String userId) {
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("userId", userId);
			groupResult(SolarApi.post("/groups/" + SolarApi.enc(g.id) + "/members", b), "Added to " + g.name);
		});
	}

	/** Owner: kick someone. Anyone: pass your own id to leave. */
	public static void removeFromGroup(Group g, String userId) {
		boolean leaving = me != null && userId.equals(me.id);
		action(() -> groupResult(SolarApi.delete("/groups/" + SolarApi.enc(g.id) + "/members/" + SolarApi.enc(userId)),
				leaving ? "You left " + g.name : "Removed from " + g.name));
	}

	/** Logos you chose, kept on this computer too (until the site sends "icon" back with the group). */
	private static final Map<String, String> GROUP_ICONS = loadGroupIcons();

	private static Map<String, String> loadGroupIcons() {
		Map<String, String> m = new java.util.concurrent.ConcurrentHashMap<>();
		try {
			java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("group-icons.json");
			if (java.nio.file.Files.exists(f)) {
				JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
				for (var e : o.entrySet()) {
					m.put(e.getKey(), e.getValue().getAsString());
				}
			}
		} catch (Exception ignored) {
			// only a cache
		}
		return m;
	}

	/** The owner picks the group's logo (a preset from GroupIcons, "" = members' heads). */
	public static void setGroupIcon(Group g, String icon) {
		if (g == null || !g.owner()) {
			return;
		}
		g.icon = icon == null ? "" : icon;
		GROUP_ICONS.put(g.id, g.icon);
		action(() -> {
			try {
				JsonObject o = new JsonObject();
				GROUP_ICONS.forEach(o::addProperty);
				java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("group-icons.json");
				java.nio.file.Files.createDirectories(f.getParent());
				java.nio.file.Files.writeString(f, o.toString());
			} catch (Exception ignored) {
				// only a cache
			}
			JsonObject b = new JsonObject();
			b.addProperty("icon", g.icon);
			groupResult(SolarApi.patch("/groups/" + SolarApi.enc(g.id), b), null);
		});
	}

	public static void renameGroup(Group g, String name) {
		action(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("name", name);
			groupResult(SolarApi.patch("/groups/" + SolarApi.enc(g.id), b), null);
		});
	}

	public static void deleteGroup(Group g) {
		action(() -> groupResult(SolarApi.delete("/groups/" + SolarApi.enc(g.id)), "Group deleted"));
	}


	// ================================================================== emotes are bought in the shop
	private static volatile long emotesAskedAt;

	/**
	 * Emote ids you own (shop items of category "Emotes": their modelId, else id). Returns null while unknown or when
	 * the site sells no emotes yet (then every emote stays usable, like before).
	 */
	public static java.util.Set<String> ownedEmotes() {
		List<Item> all = SHOP_CACHE.get("All");
		if (all == null) {
			long t = System.currentTimeMillis();
			if (me != null && t - emotesAskedAt > 60_000L) {
				emotesAskedAt = t;
				IO.execute(() -> safe(() -> fetchShop("All")));
			}
			return null;
		}
		java.util.Set<String> out = new HashSet<>();
		boolean anyEmote = false;
		for (Item it : all) {
			if (it.category != null && it.category.equalsIgnoreCase("Emotes")) {
				String key = (it.modelId != null && !it.modelId.isBlank() ? it.modelId : it.id).toLowerCase(Locale.ROOT);
				anyEmote |= localEmote(key); // a site that still lists other emotes (not ours) counts as "no emotes sold yet"
				if (it.owned) {
					out.add(key);
				}
			}
		}
		return anyEmote ? out : null;
	}

	private static boolean localEmote(String id) {
		for (dev.solclient.emote.EmoteLibrary.Info i : dev.solclient.emote.EmoteLibrary.all()) {
			if (i.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== 3D kill effects are bought in the shop too
	/**
	 * 3D kill effect ids you own (shop items of category "Kill Effects": their modelId, else id). Returns null while
	 * unknown or when the site sells no kill effects yet (then every effect stays usable, like before).
	 */
	public static java.util.Set<String> ownedKillEffects() {
		List<Item> all = SHOP_CACHE.get("All");
		if (all == null) {
			long t = System.currentTimeMillis();
			if (me != null && t - emotesAskedAt > 60_000L) {
				emotesAskedAt = t;
				IO.execute(() -> safe(() -> fetchShop("All")));
			}
			return null;
		}
		java.util.Set<String> out = new HashSet<>();
		boolean any = false;
		for (Item it : all) {
			if (isKillEffect(it)) {
				String key = killEffectKey(it);
				any |= java.util.Arrays.asList(dev.solclient.fx.killfx.KillFxModel.IDS).contains(key);
				if (it.owned) {
					out.add(key);
				}
			}
		}
		return any ? out : null;
	}

	/** Kill effect picked in the Locker while the site sells none (saved in config/kubo-killfx.txt): its id, or null. */
	private static volatile String localKillEffect;
	private static boolean localKillLoaded;
	private static List<Item> localKillItems;

	private static java.nio.file.Path localKillFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-killfx.txt");
	}

	private static String localKill() {
		if (!localKillLoaded) {
			localKillLoaded = true;
			try {
				java.nio.file.Path f = localKillFile();
				if (java.nio.file.Files.exists(f)) {
					String v = java.nio.file.Files.readString(f).trim();
					localKillEffect = v.isEmpty() ? null : v;
				}
			} catch (Exception ignored) {
			}
		}
		return localKillEffect;
	}

	private static void setLocalKill(String id) {
		localKillEffect = id;
		localKillLoaded = true;
		try {
			java.nio.file.Files.writeString(localKillFile(), id == null ? "" : id);
		} catch (Exception e) {
			System.err.println("[Sleep] could not save the kill effect: " + e);
		}
	}

	/** True when the site's shop lists kill effects that the client has. */
	public static boolean sellsKillEffects() {
		List<Item> all = SHOP_CACHE.get("All");
		if (all == null) {
			return false;
		}
		for (Item it : all) {
			if (isKillEffect(it) && java.util.Arrays.asList(dev.solclient.fx.killfx.KillFxModel.IDS).contains(killEffectKey(it))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The kill effects for the Locker: the site's items (owned or not) when it sells them, otherwise all the client's effects as
	 * free local items you can equip.
	 */
	public static List<Item> killEffectItems() {
		if (sellsKillEffects()) {
			List<Item> out = new ArrayList<>();
			for (Item it : SHOP_CACHE.get("All")) {
				if (isKillEffect(it)) {
					out.add(it);
				}
			}
			return out;
		}
		if (localKillItems == null) {
			List<Item> l = new ArrayList<>();
			String[] ids = dev.solclient.fx.killfx.KillFxModel.IDS, names = dev.solclient.fx.killfx.KillFxModel.NAMES;
			for (int i = 0; i < ids.length; i++) {
				Item it = new Item();
				it.id = "killfx:" + ids[i];
				it.modelId = ids[i];
				it.name = i < names.length ? names[i] : ids[i];
				it.category = "Kill Effects";
				it.description = "3D kill effect";
				it.owned = true;
				it.local = true;
				l.add(it);
			}
			localKillItems = l;
		}
		String on = localKill();
		for (Item it : localKillItems) {
			it.equipped = it.modelId.equals(on);
		}
		return localKillItems;
	}

	/** The kill effect you equipped in the Locker / on the website (its id, e.g. "pacman"), or null. */
	public static String equippedKillEffect() {
		List<Item> all = SHOP_CACHE.get("All");
		if (all == null || !sellsKillEffects()) {
			return localKill();
		}
		// the site may spell the category "Kill Effects", "KillEffects", "Kill effects"...
		String mine = null;
		for (Map.Entry<String, String> e : MY_EQUIP.entrySet()) {
			if (e.getKey() != null && e.getKey().replace(" ", "").equalsIgnoreCase("KillEffects")) {
				mine = e.getValue();
			}
		}
		// the one you just equipped wins (even before the shop list says you own it: it was just bought)
		if (mine != null) {
			for (Item it : all) {
				if (isKillEffect(it) && mine.equals(it.id)) {
					return killEffectKey(it);
				}
			}
		}
		for (Item it : all) {
			if (isKillEffect(it) && it.owned && it.equipped) {
				if (MY_UNEQUIP.contains(it.category) && !it.id.equals(mine)) {
					continue;
				}
				return killEffectKey(it);
			}
		}
		return null;
	}

	public static boolean isKillEffect(Item it) {
		return it.category != null && it.category.replace(" ", "").equalsIgnoreCase("KillEffects");
	}

	public static String killEffectKey(Item it) {
		return (it.modelId != null && !it.modelId.isBlank() ? it.modelId : it.id).toLowerCase(Locale.ROOT);
	}

	/** True when the site sells emotes (category "Emotes" exists in the shop). */
	public static boolean sellsEmotes() {
		return ownedEmotes() != null;
	}

	private static List<Item> emoteIndexOf;
	private static Map<String, Item> emoteIndex = Map.of();

	/** The shop item that sells this emote (matched by modelId, id or name), or null. */
	public static Item emoteItem(String emoteId, String name) {
		List<Item> src = SHOP_CACHE.get("Emotes");
		List<Item> all = SHOP_CACHE.get("All");
		List<Item> key = src != null ? src : all;
		if (key == null) {
			return null;
		}
		if (key != emoteIndexOf) {
			Map<String, Item> m = new HashMap<>();
			for (List<Item> l : java.util.Arrays.asList(all, src)) {
				if (l == null) {
					continue;
				}
				for (Item it : l) {
					if (it.category == null || !it.category.equalsIgnoreCase("Emotes")) {
						continue;
					}
					if (it.modelId != null && !it.modelId.isBlank()) {
						m.put("id:" + it.modelId.toLowerCase(Locale.ROOT), it);
					}
					if (it.id != null) {
						m.putIfAbsent("id:" + it.id.toLowerCase(Locale.ROOT), it);
					}
					if (it.name != null) {
						m.putIfAbsent("n:" + it.name.toLowerCase(Locale.ROOT), it);
					}
				}
			}
			emoteIndex = m;
			emoteIndexOf = key;
		}
		Item it = emoteId == null ? null : emoteIndex.get("id:" + emoteId.toLowerCase(Locale.ROOT));
		if (it == null && name != null) {
			it = emoteIndex.get("n:" + name.toLowerCase(Locale.ROOT));
		}
		return it;
	}

	/** Refreshes what you own after buying (so a bought emote shows up at once). */
	public static void refreshOwned() {
		IO.execute(() -> safe(() -> fetchShop("All")));
	}

	// ================================================================== cosmetics worn in game
	/** My equipped items (category -> item id), known from the shop / equip calls: shows at once, before the lookup. */
	private static final Map<String, String> MY_EQUIP = new ConcurrentHashMap<>();
	private static final java.util.Set<String> MY_UNEQUIP = ConcurrentHashMap.newKeySet();

	private static void rememberEquip(Item it, boolean on) {
		if (it == null || it.id == null || it.category == null || !it.wearable()) {
			return;
		}
		String cat = it.category;
		if (on) {
			MY_EQUIP.put(cat, it.id);
			MY_UNEQUIP.remove(cat);
		} else if (it.id.equals(MY_EQUIP.get(cat))) {
			MY_EQUIP.remove(cat);
			MY_UNEQUIP.add(cat);
		} else if (!MY_EQUIP.containsKey(cat)) {
			MY_UNEQUIP.add(cat);
		}
	}

	/** The capes you own (from the whole shop list), A-Z. Load the shop first ({@link #loadShop}) so the list is there. */
	public static List<Item> ownedCapes() {
		Map<String, Item> out = new java.util.LinkedHashMap<>();
		List<Item> all = SHOP_CACHE.get("All");
		List<List<Item>> lists = new ArrayList<>();
		if (all != null) {
			lists.add(all);
		}
		for (Map.Entry<String, List<Item>> e : SHOP_CACHE.entrySet()) {
			if (!e.getKey().equals("All")) {
				lists.add(e.getValue());
			}
		}
		for (List<Item> l : lists) {
			for (Item it : l) {
				if (it.id != null && it.owned && it.category != null && it.category.equalsIgnoreCase("Capes")) {
					out.putIfAbsent(it.id, it);
				}
			}
		}
		List<Item> res = new ArrayList<>(out.values());
		res.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		return res;
	}

	/**
	 * The shop cape a player wears, as the cape picture description (null: nothing, or an old PNG atlas that the
	 * normal cape renderer draws from capeUrl). Your own equipped cape counts at once, before the lookup answers.
	 */
	public static CapeMediaLogic.Desc capeDesc(UUID player) {
		UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
		boolean mine = me != null && me.equals(player);
		if (mine) {
			for (Map.Entry<String, String> e : MY_EQUIP.entrySet()) {
				if ("Capes".equalsIgnoreCase(e.getKey())) {
					CapeMediaLogic.Desc d = CapeMedia.forItem(CATALOGUE.get(e.getValue()));
					if (d != null) {
						return d;
					}
				}
			}
		}
		PlayerInfo p = PLAYERS.get(player);
		if (p == null || !liveUser(player)) {
			return null;
		}
		if (p.capeMedia != null) {
			return p.capeMedia;
		}
		if (p.capeUrl != null) {
			// an uploaded cape in the cape layout: a PNG goes to the normal cape renderer; a GIF / video moves (CapeMedia)
			CapeMediaLogic.Side s = CapeMediaLogic.side(p.capeUrl, null);
			if (s != null) {
				CapeMediaLogic.Kind k = CapeMediaLogic.kind(s);
				if (k == CapeMediaLogic.Kind.GIF || k == CapeMediaLogic.Kind.VIDEO || k == CapeMediaLogic.Kind.WEBP || k == CapeMediaLogic.Kind.JPG) {
					return CapeMediaLogic.make("atlas", s, null);
				}
			}
			return null;
		}
		for (Map.Entry<String, String> e : p.cosmetics.entrySet()) {
			if ("Capes".equalsIgnoreCase(e.getKey()) && !(mine && MY_UNEQUIP.contains(e.getKey()))) {
				return CapeMedia.forItem(CATALOGUE.get(e.getValue()));
			}
		}
		return null;
	}

	/** False for another player in the world who is NOT using the client right now (the site lists everyone who ever made an account, so their things must not show). */
	static boolean liveUser(UUID player) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || player == null || dev.solclient.cosmetic.Cosmetics.inGui) {
			return true;
		}
		net.minecraft.entity.player.PlayerEntity pe = mc.world.getPlayerByUuid(player);
		return pe == null || pe == mc.player || SolarBadge.usesClient(pe);
	}

	public static Item catalogueItem(String id) {
		return id == null ? null : CATALOGUE.get(id);
	}

	/** Item ids a player wears (one per category). Yours come from what you equipped here first. */
	public static java.util.Collection<String> wornItems(UUID player) {
		PlayerInfo p = PLAYERS.get(player);
		Map<String, String> m = new HashMap<>(p == null || !liveUser(player) ? Map.of() : p.cosmetics);
		UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
		if (me != null && me.equals(player)) {
			loadBuiltinEquip();
			for (String c : MY_UNEQUIP) {
				m.remove(c);
			}
			m.putAll(MY_EQUIP);
		}
		return m.values();
	}

	// ================================================================== in-game lookup
	private static void loadCatalogue() {
		SolarApi.Res r = SolarApi.get("/cosmetics");
		if (!check(r, false)) {
			return;
		}
		for (JsonElement e : Json.arr(r.obj(), "items")) {
			JsonObject io = Json.obj(e);
			if (io != null) {
				Item it = Item.parse(io);
				if (it.id != null) {
					CATALOGUE.put(it.id, it);
				}
			}
		}
	}

	private static void lookup(List<String> uuids) {
		JsonObject b = new JsonObject();
		JsonArray a = new JsonArray();
		for (String u : uuids) {
			String k = UuidKey.normalize(u); // the site's keys: lower case, no hyphens
			if (k != null) {
				a.add(k);
			}
		}
		b.add("uuids", a);
		SolarApi.Res r = SolarApi.send("POST", "/players/lookup", b, false, 10);
		long now = System.currentTimeMillis();
		if (r.status() == 429) {
			nextLookup = now + 30_000L;
			return;
		}
		if (!r.ok()) {
			return;
		}
		JsonObject players = Json.obj(r.obj(), "players");
		// answer keys can come in any case / with hyphens: index them by the normalized key
		Map<String, JsonObject> byKey = new HashMap<>();
		if (players != null) {
			for (String k : players.keySet()) {
				String nk = UuidKey.normalize(k);
				JsonObject v = Json.obj(players.get(k));
				if (nk != null && v != null) {
					byKey.put(nk, v);
				}
			}
		}
		for (String id : uuids) {
			UUID uuid;
			try {
				uuid = UUID.fromString(id);
			} catch (IllegalArgumentException e) {
				continue;
			}
			PlayerInfo info = new PlayerInfo(now);
			JsonObject po = byKey.get(UuidKey.normalize(id));
			if (po != null) {
				info.solar = true;
				info.capeUrl = Json.str(po, "capeUrl");
				info.skinUrl = Json.str(po, "skinUrl");
				info.capeMedia = CapeMedia.parse(Json.obj(po, "capeMedia"));
				if (info.capeMedia != null && CapeMediaLogic.custom(info.capeMedia)) {
					info.capeUrl = null; // panels / video are NOT a 64x32 atlas: never hand them to the PNG cape renderer
				} else if (info.capeMedia != null && info.capeUrl == null) {
					info.capeUrl = info.capeMedia.back().url(); // plain PNG atlas
				}
				JsonObject cos = Json.obj(po, "cosmetics");
				if (cos != null) {
					Map<String, String> m = new HashMap<>();
					for (String k : cos.keySet()) {
						String v = Json.str(cos, k);
						if (v != null) {
							m.put(k, v);
						}
					}
					info.cosmetics = Collections.unmodifiableMap(m);
				}
			}
			PLAYERS.put(uuid, info);
		}
	}

	private static void forgetMyLookup() {
		Session s = MinecraftClient.getInstance().getSession();
		UUID id = s.getUuidOrNull();
		if (id != null) {
			PLAYERS.remove(id);
		}
		nextLookup = 0;
	}

	/** Cape texture URL for a player in the world (uploaded active cape, else an equipped shop cape with a picture). */
	/** Public lookup data of a player (null if not looked up yet). */
	public static PlayerInfo info(UUID player) {
		return PLAYERS.get(player);
	}

	public static String capeUrl(UUID player) {
		PlayerInfo p = PLAYERS.get(player);
		if (p == null || !liveUser(player)) {
			return null;
		}
		if (p.capeUrl != null) {
			return p.capeUrl;
		}
		if (p.capeMedia != null) {
			return null; // described by capeMedia (see CapeMedia): the shop picture is only a preview
		}
		String item = p.cosmetics.get("Capes");
		Item it = item == null ? null : CATALOGUE.get(item);
		return it == null ? null : it.imageUrl;
	}

	public static String skinUrl(UUID player) {
		PlayerInfo p = PLAYERS.get(player);
		return p == null ? null : p.skinUrl;
	}

	/** True when the player is a Solar Client member (from the public lookup). */
	public static boolean isSolar(UUID player) {
		PlayerInfo p = PLAYERS.get(player);
		return p != null && p.solar;
	}
}
