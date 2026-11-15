package dev.solclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;

/**
 * Small live link between Sleep players on the same server (through the presence room): your health / hunger /
 * position for the Party HUD, and pings ("enemy here", "come here"). Sent about once a second while something
 * needs it, and only to people in that server's room.
 */
public final class KuboLink {

	/** What we know about another Sleep player on this server. */
	public static final class State {
		public String uuid, nick = "", dim = "";
		public float hp = 20, maxHp = 20, food = 20, armor;
		public double x, y, z;
		public long seen;
	}

	/** A ping in the world. */
	public static final class Ping {
		public String from = "", fromUuid = "", dim = "";
		public double x, y, z;
		public int type; // 0 look, 1 enemy, 2 come, 3 danger, 4 loot
		public long at, seq;
		public boolean mine;
	}

	public static final String[] PING_NAMES = {"Look here", "Enemy", "Come here", "Danger", "Loot"};
	public static final int[] PING_COLOURS = {0xFF7DD3FC, 0xFFFF4D5E, 0xFF4ADE80, 0xFFFFB020, 0xFFC084FC};

	public static final Map<String, State> STATES = new ConcurrentHashMap<>();
	public static final List<Ping> PINGS = new ArrayList<>();
	private static final Map<String, Long> LAST_SEQ = new ConcurrentHashMap<>();

	/** Who wants the link running (Party HUD, pings). */
	public static volatile boolean wantParty, wantPings;
	private static Ping outgoing;
	private static String lastBadge = "";
	private static long lastBadgeAt;
	private static long lastSend, seq = System.currentTimeMillis() % 100000;
	private static long lastLobbyAt, lastSentAt, lastRecvAt;
	private static String lastLobbySig = "", lastRecvNick = "";

	private KuboLink() {
	}

	private static String me() {
		var id = MinecraftClient.getInstance().getSession().getUuidOrNull();
		return id == null ? "" : id.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	/** Places a ping where you look (shown to you at once, to the others with the next message). */
	public static void ping(double x, double y, double z, int type) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Ping p = new Ping();
		p.x = x;
		p.y = y;
		p.z = z;
		p.type = type;
		p.at = System.currentTimeMillis();
		p.seq = ++seq;
		p.mine = true;
		p.from = mc.getSession().getUsername();
		p.fromUuid = me();
		p.dim = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
		synchronized (PINGS) {
			PINGS.removeIf(o -> o.mine);
			PINGS.add(p);
		}
		outgoing = p;
		lastSend = 0; // send now
		dev.solclient.stats.KuboStats.add("pings", 1);
	}

	/** My colour choices and position tweaks of the cosmetics I wear (empty when there is nothing to send). */
	private static JsonObject myCosmetics(MinecraftClient mc) {
		try {
			var id = mc.getSession().getUuidOrNull();
			return id == null ? new JsonObject() : dev.solclient.cosmetic.Offsets.mine(dev.solclient.cosmetic.Cosmetics.worn(id));
		} catch (RuntimeException e) {
			return new JsonObject();
		}
	}

	/** Every client tick. */
	public static void tick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		SyncLink.tick(mc);
		synchronized (PINGS) {
			PINGS.removeIf(p -> now - p.at > 9000L);
		}
		STATES.values().removeIf(s -> now - s.seen > 8000L);
		String myBadge = ProfileBadges.myId();
		String myLook = dev.solclient.mods.NameColourMod.encodeMine();
		String myFlag = ProfileBadges.myFlagCode();
		JsonObject myCx = myCosmetics(mc);
		String myDn = dev.solclient.mods.NickHiderMod.publicNick();
		String sig = myDn + "|" + myBadge + "|" + myLook + "|" + ProfileBadges.myColourOn() + "|" + myFlag + "|" + myCx;
		lobbyTick(mc, now, sig, myBadge, myLook, myFlag, myCx, myDn);
		boolean badgeDue = mc.player != null && (!sig.equals(lastBadge) ? now - lastBadgeAt > 1500L
				: (!myDn.isEmpty() || !myBadge.isEmpty() || !myLook.isEmpty() || !myFlag.isEmpty() || myCx.size() > 0)
						&& now - lastBadgeAt > 6_000L);
		if (mc.player == null || mc.world == null || !(wantParty || wantPings && outgoing != null || badgeDue)) {
			return;
		}
		if (outgoing != null && now - outgoing.at > 3500L) {
			outgoing = null; // repeated for a few seconds so nobody misses it, then dropped
		}
		long every = outgoing != null ? 700L : 1500L;
		if (now - lastSend < every) {
			return;
		}
		JsonObject kx = new JsonObject();
		kx.addProperty("n", mc.getSession().getUsername());
		if (wantParty) {
			kx.addProperty("hp", Math.round(mc.player.getHealth() * 10) / 10.0);
			kx.addProperty("mh", Math.round(mc.player.getMaxHealth()));
			kx.addProperty("f", mc.player.getHungerManager().getFoodLevel());
			kx.addProperty("a", mc.player.getArmor());
			kx.addProperty("x", Math.round(mc.player.getX()));
			kx.addProperty("y", Math.round(mc.player.getY()));
			kx.addProperty("z", Math.round(mc.player.getZ()));
			kx.addProperty("d", mc.world.getRegistryKey().getValue().toString());
		}
		kx.addProperty("dn", myDn);
		kx.addProperty("bd", myBadge);
		kx.addProperty("bc", ProfileBadges.myColourOn() ? 1 : 0);
		kx.addProperty("nn", myLook);
		kx.addProperty("fl", myFlag);
		if (myCx.size() > 0) {
			kx.add("cx", myCx);
		}
		if (outgoing != null) {
			JsonArray a = new JsonArray();
			a.add(Math.round(outgoing.x * 10) / 10.0);
			a.add(Math.round(outgoing.y * 10) / 10.0);
			a.add(Math.round(outgoing.z * 10) / 10.0);
			a.add(outgoing.type);
			a.add(outgoing.seq);
			a.add(outgoing.dim);
			kx.add("pg", a);
		}
		boolean viaSync = SyncLink.sendServer(kx);
		if (Presence.sendLink(kx) || viaSync) {
			lastSend = now;
			lastBadge = sig;
			lastBadgeAt = now;
			lastSentAt = now;
		}
	}

	/**
	 * Your public profile (badge, name style, flag, cosmetics) also goes to the global lobby, so players who reached the same
	 * server through another address (another server room) still see your crown, badges and name colour. No position, health or pings.
	 */
	private static void lobbyTick(MinecraftClient mc, long now, String sig, String badge, String look, String flag, JsonObject cx, String dn) {
		if (mc.player == null || mc.world == null || mc.isInSingleplayer()) {
			return;
		}
		boolean changed = !sig.equals(lastLobbySig);
		boolean has = !dn.isEmpty() || !badge.isEmpty() || !look.isEmpty() || !flag.isEmpty() || cx.size() > 0;
		if (!(changed ? now - lastLobbyAt > 1500L : has && now - lastLobbyAt > 6_000L)) {
			return;
		}
		JsonObject kx = new JsonObject();
		kx.addProperty("n", mc.getSession().getUsername());
		kx.addProperty("dn", dn);
		kx.addProperty("bd", badge);
		kx.addProperty("bc", ProfileBadges.myColourOn() ? 1 : 0);
		kx.addProperty("nn", look);
		kx.addProperty("fl", flag);
		if (cx.size() > 0) {
			kx.add("cx", cx);
		}
		boolean lobbyViaSync = SyncLink.sendLobby(kx);
		if (Presence.sendLinkLobby(kx) || lobbyViaSync) {
			lastLobbyAt = now;
			lastLobbySig = sig;
			lastSentAt = now;
		}
	}

	private static String ago(long t) {
		return t == 0 ? "never" : Math.max(0, (System.currentTimeMillis() - t) / 1000L) + " s ago";
	}

	/** One line for the settings: is the badge / name colour sync working, and with whom. */
	public static String diag() {
		String s = "Lobby " + (Presence.connected() ? Presence.lobbyPlayers().size() + " online" : "OFF") + " | Server room "
				+ (Presence.serverRoomOpen() ? Presence.serverPlayers().size() + " players" : "OFF") + " | sent " + ago(lastSentAt)
				+ " | got " + (lastRecvNick.isEmpty() ? "nothing yet" : lastRecvNick + " " + ago(lastRecvAt));
		s = s + " || " + SyncLink.diag() + " || " + dev.solclient.music.AudioStream.diag();
		return Presence.problem == null ? s : s + " | " + Presence.problem;
	}

	/** Sends your badge / name style again right now (the "Check" button). */
	public static void resendNow() {
		lastBadge = "";
		lastLobbySig = "";
		lastBadgeAt = 0;
		lastLobbyAt = 0;
	}

	/** From the presence room (client thread). */
	static void onRemote(String uuid, JsonObject kx) {
		if (uuid == null || uuid.equals(me())) {
			return;
		}
		long now = System.currentTimeMillis();
		String nick = Json.str(kx, "n");
		ProfileBadges.rememberUuid(uuid, nick);
		if (kx.has("mu") || kx.has("mi")) {
			dev.solclient.music.MusicShare.onRemote(uuid, nick, kx); // Music Share: the song above the head, listen-along messages
		}
		if (kx.has("dn") && nick != null) {
			dev.solclient.mods.NickHiderMod.onRemote(nick, Json.str(kx, "dn"));
		}
		if (kx.has("bd") && nick != null) {
			lastRecvAt = now;
			lastRecvNick = nick;
			ProfileBadges.onRemote(nick, Json.str(kx, "bd"));
			ProfileBadges.onRemoteLook(nick, Json.str(kx, "nn"));
			ProfileBadges.onRemoteFlag(nick, Json.str(kx, "fl"));
			if (kx.has("bc")) {
				ProfileBadges.onRemoteColour(nick, kx.get("bc").getAsInt() != 0);
			}
		}
		if (kx.has("cx") && kx.get("cx").isJsonObject()) {
			dev.solclient.cosmetic.Offsets.onRemote(uuid, kx.getAsJsonObject("cx"));
		} else if (kx.has("bd")) {
			dev.solclient.cosmetic.Offsets.onRemote(uuid, null);
		}
		if (kx.has("hp")) {
			State s = STATES.computeIfAbsent(uuid, k -> new State());
			s.uuid = uuid;
			s.nick = nick == null ? s.nick : nick;
			s.hp = (float) dbl(kx, "hp", 20);
			s.maxHp = Math.max(1F, (float) dbl(kx, "mh", 20));
			s.food = (float) dbl(kx, "f", 20);
			s.armor = (float) dbl(kx, "a", 0);
			s.x = dbl(kx, "x", 0);
			s.y = dbl(kx, "y", 0);
			s.z = dbl(kx, "z", 0);
			String d = Json.str(kx, "d");
			s.dim = d == null ? "" : d;
			s.seen = now;
		}
		if (kx.has("pg") && kx.get("pg").isJsonArray()) {
			try {
				JsonArray a = kx.getAsJsonArray("pg");
				long sq = a.get(4).getAsLong();
				Long last = LAST_SEQ.get(uuid);
				if (last != null && last >= sq) {
					return; // already have this one
				}
				LAST_SEQ.put(uuid, sq);
				Ping p = new Ping();
				p.x = a.get(0).getAsDouble();
				p.y = a.get(1).getAsDouble();
				p.z = a.get(2).getAsDouble();
				p.type = Math.max(0, Math.min(PING_NAMES.length - 1, a.get(3).getAsInt()));
				p.seq = sq;
				p.dim = a.size() > 5 ? a.get(5).getAsString() : "";
				p.from = nick == null ? "?" : nick;
				p.fromUuid = uuid;
				p.at = now;
				synchronized (PINGS) {
					PINGS.removeIf(o -> uuid.equals(o.fromUuid));
					PINGS.add(p);
				}
				dev.solclient.ext.KuboApi.friendPing(p);
			} catch (RuntimeException ignored) {
			}
		}
	}

	private static double dbl(JsonObject o, String k, double def) {
		try {
			return o.has(k) ? o.get(k).getAsDouble() : def;
		} catch (RuntimeException e) {
			return def;
		}
	}

	/** True if this account uuid (no dashes) belongs to one of your Sleep friends. */
	public static boolean isFriend(String uuid, String nick) {
		for (Social.User u : Social.friends) {
			if (u.mcUuid != null && u.mcUuid.replace("-", "").equalsIgnoreCase(uuid)) {
				return true;
			}
			if (nick != null && u.mcUsername != null && u.mcUsername.equalsIgnoreCase(nick)) {
				return true;
			}
		}
		return false;
	}
}
