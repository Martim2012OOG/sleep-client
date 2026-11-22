package dev.sleepclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sleepclient.Lang;
import dev.sleepclient.gui.Notify;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Party: up to 8 friends who play together.
 *
 * - The leader creates the party and invites friends: the invite is a normal friend message that carries a hidden token
 *   ([sleep-party|v1|partyId|leaderUuid|leaderNick]), so it needs no new server.
 * - Everybody who is in the party talks in a private room of the sync Worker (room = sha256 of the secret party id):
 *   heartbeats (who is here, ready, where), the roster (sent by the leader), chat, and the leader's "go" (join this server).
 * - Only the leader's messages count for roster / kick / disband / go (the Worker tells us who sent each message).
 */
public final class Party {

	public static final int MAX = 8;
	private static final long HB_MS = 4_000L, MEMBER_TIMEOUT_MS = 20_000L, LEADER_TIMEOUT_MS = 45_000L, INVITE_TTL_MS = 15L * 60_000L;
	private static final long REPLAY_GUARD_MS = 1_800L;

	public static final class Member {
		public final String uuid;
		public volatile String nick, friendId = "", where = "";
		public volatile boolean ready;
		public volatile long seen;

		Member(String uuid, String nick) {
			this.uuid = uuid;
			this.nick = nick;
			this.seen = System.currentTimeMillis();
		}

		public boolean leader() {
			return uuid.equals(leaderUuid);
		}

		public boolean online() {
			return uuid.equals(myUuid()) || System.currentTimeMillis() - seen < MEMBER_TIMEOUT_MS;
		}
	}

	public static final class Invite {
		public final String partyId, leaderUuid, leaderNick, fromId;
		public final long at;

		Invite(String partyId, String leaderUuid, String leaderNick, String fromId, long at) {
			this.partyId = partyId;
			this.leaderUuid = leaderUuid;
			this.leaderNick = leaderNick;
			this.fromId = fromId;
			this.at = at;
		}
	}

	public record Chat(String nick, String text, long at, boolean system) {
	}

	public record Go(String name, String addr, String from, long at) {
	}

	private static final SecureRandom RNG = new SecureRandom();
	private static volatile boolean active;
	private static volatile String id = "", leaderUuid = "";
	private static volatile long joinedAt, lastHb, leaderSeen, lastGoId;
	private static volatile boolean ready;
	/** Who I invited (site user id -> when): only these may join my party. */
	private static final Map<String, Long> INVITED = new ConcurrentHashMap<>();
	private static final Map<String, Long> DISMISSED = new ConcurrentHashMap<>();
	public static final List<Member> MEMBERS = new CopyOnWriteArrayList<>();
	public static final List<Invite> INVITES = new CopyOnWriteArrayList<>();
	public static final List<Chat> CHAT = new CopyOnWriteArrayList<>();
	/** The leader's last "join this server" that is waiting for me (shown in the party screen). */
	public static volatile Go go;
	/** Set by the member: join automatically (with a 5 s countdown) when the leader starts. */
	public static volatile boolean autoJoin = true;
	/** Set by the leader: take the party along whenever I join a server or host a world. */
	public static volatile boolean bringParty = true;
	private static String lastPlace = "";
	/** Emotes other members played (nick, id): the 3D lobby takes them from here. */
	public static final java.util.concurrent.ConcurrentLinkedQueue<String[]> EMOTES = new java.util.concurrent.ConcurrentLinkedQueue<>();
	/** True while the 3D lobby screen is open (the party then does not open its own join countdown). */
	public static volatile boolean lobbyOpen;
	private static long lastEmoteAt;

	private Party() {
	}

	// ================================================================== state
	public static boolean active() {
		return active;
	}

	public static boolean isLeader() {
		return active && leaderUuid.equals(myUuid());
	}

	public static int size() {
		return MEMBERS.size();
	}

	public static int pendingInvites() {
		return INVITES.size();
	}

	public static boolean ready() {
		return ready;
	}

	public static boolean invited(String friendId) {
		Long t = INVITED.get(friendId);
		return t != null && System.currentTimeMillis() - t < INVITE_TTL_MS;
	}

	public static boolean inParty(Social.User u) {
		if (u == null) {
			return false;
		}
		for (Member m : MEMBERS) {
			if (u.id != null && u.id.equals(m.friendId) || u.mcUsername != null && u.mcUsername.equalsIgnoreCase(m.nick)) {
				return true;
			}
		}
		return false;
	}

	static String myUuid() {
		Social.User me = Social.me;
		String u = me == null ? null : UuidKey.normalize(me.mcUuid);
		if (u == null) {
			MinecraftClient mc = MinecraftClient.getInstance();
			u = mc.getSession().getUuidOrNull() == null ? "" : UuidKey.normalize(mc.getSession().getUuidOrNull());
		}
		return u == null ? "" : u;
	}

	static String myNick() {
		Social.User me = Social.me;
		if (me != null && me.mcUsername != null && !me.mcUsername.isEmpty()) {
			return me.mcUsername;
		}
		return MinecraftClient.getInstance().getSession().getUsername();
	}

	private static String myId() {
		return Social.me == null || Social.me.id == null ? "" : Social.me.id;
	}

	public static String room() {
		return Presence.sha256("sleep-party-v1-" + id);
	}

	// ================================================================== actions
	public static boolean create() {
		if (active) {
			return true;
		}
		if (Social.me == null) {
			Social.say(Lang.tr("Log in to the Sleep site first to use parties", "Entra primeiro no site Sleep para usar parties"), true);
			return false;
		}
		byte[] b = new byte[16];
		RNG.nextBytes(b);
		StringBuilder sb = new StringBuilder();
		for (byte x : b) {
			sb.append(String.format("%02x", x));
		}
		id = sb.toString();
		leaderUuid = myUuid();
		reset(false);
		Member m = new Member(leaderUuid, myNick());
		m.friendId = myId();
		MEMBERS.add(m);
		joinedAt = System.currentTimeMillis();
		leaderSeen = joinedAt;
		lastHb = 0;
		active = true;
		lastPlace = place();
		system(Lang.tr("Party created. Invite your friends!", "Party criada. Convida os teus amigos!"));
		return true;
	}

	public static void invite(Social.User u) {
		if (u == null || u.id == null) {
			return;
		}
		if (!active && !create()) {
			return;
		}
		if (!isLeader()) {
			Social.say(Lang.tr("Only the party leader can invite", "So o lider da party pode convidar"), true);
			return;
		}
		if (inParty(u)) {
			return;
		}
		if (MEMBERS.size() + openInvites() >= MAX) {
			Social.say(Lang.tr("The party is full (8)", "A party esta cheia (8)"), true);
			return;
		}
		INVITED.put(u.id, System.currentTimeMillis());
		Social.sendPartyInvite(u.id, id, leaderUuid, myNick());
	}

	private static int openInvites() {
		int n = 0;
		for (Map.Entry<String, Long> e : INVITED.entrySet()) {
			if (System.currentTimeMillis() - e.getValue() < INVITE_TTL_MS && !inPartyId(e.getKey())) {
				n++;
			}
		}
		return n;
	}

	private static boolean inPartyId(String friendId) {
		for (Member m : MEMBERS) {
			if (friendId.equals(m.friendId)) {
				return true;
			}
		}
		return false;
	}

	public static void cancelInvite(Social.User u) {
		if (u != null && u.id != null) {
			INVITED.remove(u.id);
		}
	}

	public static void accept(Invite inv) {
		if (inv == null) {
			return;
		}
		if (active) {
			leave();
		}
		INVITES.remove(inv);
		DISMISSED.put(inv.partyId, System.currentTimeMillis());
		id = inv.partyId;
		leaderUuid = inv.leaderUuid;
		reset(false);
		Member me = new Member(myUuid(), myNick());
		me.friendId = myId();
		MEMBERS.add(me);
		Member lead = new Member(inv.leaderUuid, inv.leaderNick);
		lead.friendId = inv.fromId == null ? "" : inv.fromId;
		MEMBERS.add(0, lead);
		joinedAt = System.currentTimeMillis();
		leaderSeen = joinedAt;
		lastHb = 0;
		active = true;
		system(Lang.tr("You joined " + inv.leaderNick + "'s party", "Entraste na party de " + inv.leaderNick));
	}

	public static void decline(Invite inv) {
		if (inv != null) {
			INVITES.remove(inv);
			DISMISSED.put(inv.partyId, System.currentTimeMillis());
		}
	}

	/** Leave (a member) or disband (the leader). */
	public static void leave() {
		if (!active) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("c", isLeader() ? "end" : "lv");
		send(o);
		DISMISSED.put(id, System.currentTimeMillis());
		stop();
	}

	private static void stop() {
		active = false;
		id = "";
		leaderUuid = "";
		ready = false;
		go = null;
		MEMBERS.clear();
		INVITED.clear();
		SyncLink.partyLeave();
	}

	private static void reset(boolean keepInvites) {
		MEMBERS.clear();
		CHAT.clear();
		go = null;
		ready = false;
		if (!keepInvites) {
			INVITED.clear();
		}
	}

	public static void kick(Member m) {
		if (!isLeader() || m == null || m.leader()) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("c", "kick");
		o.addProperty("u", m.uuid);
		send(o);
		MEMBERS.remove(m);
		if (!m.friendId.isEmpty()) {
			INVITED.remove(m.friendId);
		}
		system(m.nick + Lang.tr(" was removed", " foi removido"));
	}

	public static void toggleReady() {
		ready = !ready;
		lastHb = 0;
		for (Member m : MEMBERS) {
			if (m.uuid.equals(myUuid())) {
				m.ready = ready;
			}
		}
	}

	public static void chat(String text) {
		if (!active || text == null || text.isBlank()) {
			return;
		}
		String t = text.trim();
		if (t.length() > 200) {
			t = t.substring(0, 200);
		}
		JsonObject o = new JsonObject();
		o.addProperty("c", "chat");
		o.addProperty("t", t);
		send(o);
		addChat(new Chat(myNick(), t, System.currentTimeMillis(), false));
	}

	/** Tells the party which emote you played (the 3D lobby shows it on your character for everybody). */
	public static void sendEmote(String emoteId) {
		long now = System.currentTimeMillis();
		if (!active || emoteId == null || emoteId.length() > 64 || now - lastEmoteAt < 400L) {
			return;
		}
		lastEmoteAt = now;
		JsonObject o = new JsonObject();
		o.addProperty("c", "em");
		o.addProperty("id", emoteId);
		send(o);
	}

	/** The leader sends everybody to a server and goes there too. */
	public static void playTogether(String name, String addr) {
		if (!isLeader() || addr == null || addr.isBlank()) {
			return;
		}
		announce(name, addr);
		lastPlace = addr.toLowerCase(Locale.ROOT);
		ServerShare.join(addr);
	}

	private static void announce(String name, String addr) {
		JsonObject o = new JsonObject();
		o.addProperty("c", "go");
		o.addProperty("g", RNG.nextLong());
		o.addProperty("n", name == null || name.isBlank() ? addr : name);
		o.addProperty("a", addr);
		send(o);
		system(Lang.tr("Taking the party to ", "A levar a party para ") + (name == null || name.isBlank() ? addr : name));
	}

	public static void joinNow(Go g) {
		if (g == null) {
			return;
		}
		go = null;
		lastPlace = g.addr().toLowerCase(Locale.ROOT);
		ServerShare.join(g.addr());
	}

	// ================================================================== network
	private static void send(JsonObject pt) {
		JsonObject kx = new JsonObject();
		kx.add("pt", pt);
		SyncLink.sendParty(kx);
	}

	private static String place() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return "";
		}
		if (HostWorld.hosting()) {
			String a = HostWorld.address();
			return a == null ? "" : a.toLowerCase(Locale.ROOT);
		}
		ServerInfo e = mc.getCurrentServerEntry();
		return e == null || e.address == null || mc.isInSingleplayer() ? "" : e.address.toLowerCase(Locale.ROOT);
	}

	private static String placeName() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (HostWorld.hosting()) {
			return HostWorld.worldName();
		}
		ServerInfo e = mc.getCurrentServerEntry();
		return e == null || e.name == null || e.name.isBlank() ? place() : e.name;
	}

	/** Every client tick (from Social.tick). */
	public static void tick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		INVITES.removeIf(i -> now - i.at > INVITE_TTL_MS);
		if (!active) {
			return;
		}
		if (Social.me == null) {
			return;
		}
		SyncLink.partyEnsure(room());
		boolean open = SyncLink.partyOpen();
		if (open && now - lastHb >= HB_MS) {
			lastHb = now;
			JsonObject o = new JsonObject();
			o.addProperty("c", "hb");
			o.addProperty("f", myId());
			o.addProperty("r", ready);
			String p = place();
			o.addProperty("w", p.isEmpty() ? "" : placeName());
			if (isLeader()) {
				JsonArray ro = new JsonArray();
				for (Member m : MEMBERS) {
					JsonArray e = new JsonArray();
					e.add(m.uuid);
					e.add(m.nick);
					e.add(m.friendId);
					ro.add(e);
				}
				o.add("m", ro);
			}
			send(o);
		}
		if (isLeader()) {
			MEMBERS.removeIf(m -> !m.leader() && now - m.seen > MEMBER_TIMEOUT_MS * 3 && now - joinedAt > MEMBER_TIMEOUT_MS);
			// the leader brings the party along when joining a server / hosting
			String p = place();
			if (!p.equals(lastPlace)) {
				lastPlace = p;
				if (!p.isEmpty() && bringParty && MEMBERS.size() > 1 && open) {
					announce(placeName(), p);
				}
			}
		} else if (now - joinedAt > 15_000L && now - leaderSeen > LEADER_TIMEOUT_MS) {
			Notify.show(Notify.Kind.INFO, Lang.tr("Party ended", "Party terminou"), Lang.tr("The leader left", "O lider saiu"));
			stop();
		}
	}

	/** A message from the party room (main thread). u = the sender's uuid as the Worker verified it. */
	static void onRemote(String u, JsonObject kx) {
		if (!active || !kx.has("pt") || !kx.get("pt").isJsonObject()) {
			return;
		}
		JsonObject o = kx.getAsJsonObject("pt");
		String c = str(o, "c");
		String nick = str(kx, "n");
		long now = System.currentTimeMillis();
		boolean replay = now - SyncLink.partyOpenedAt() < REPLAY_GUARD_MS; // the Worker re-sends everybody's last message to a newcomer
		boolean fromLeader = u.equals(leaderUuid);
		String me = myUuid();
		if (u.equals(me)) {
			return;
		}
		Member m = member(u);
		switch (c) {
			case "hb" -> {
				if (fromLeader) {
					leaderSeen = now;
				}
				if (m == null && isLeader()) {
					String f = str(o, "f");
					Long inv = INVITED.get(f);
					if (inv != null && now - inv < INVITE_TTL_MS && MEMBERS.size() < MAX) {
						m = new Member(u, nick);
						m.friendId = f;
						MEMBERS.add(m);
						INVITED.remove(f);
						system(nick + Lang.tr(" joined the party", " entrou na party"));
						Notify.show(Notify.Kind.FRIEND, nick + Lang.tr(" joined your party", " entrou na tua party"), MEMBERS.size() + "/" + MAX, nick);
					}
				}
				if (m != null) {
					m.seen = now;
					if (!nick.isEmpty()) {
						m.nick = nick;
					}
					m.ready = o.has("r") && o.get("r").getAsBoolean();
					m.where = str(o, "w");
				}
				if (fromLeader && o.has("m") && o.get("m").isJsonArray()) {
					applyRoster(o.getAsJsonArray("m"), now);
				}
			}
			case "lv" -> {
				if (m != null && isLeader()) {
					MEMBERS.remove(m);
					system(m.nick + Lang.tr(" left the party", " saiu da party"));
				}
			}
			case "kick" -> {
				if (fromLeader && !replay && me.equals(str(o, "u"))) {
					Notify.show(Notify.Kind.INFO, Lang.tr("Removed from the party", "Removido da party"), Lang.tr("The leader removed you", "O lider removeu-te"));
					stop();
				}
			}
			case "end" -> {
				if (fromLeader && !replay) {
					Notify.show(Notify.Kind.INFO, Lang.tr("Party disbanded", "Party desfeita"), nick);
					stop();
				}
			}
			case "go" -> {
				if (fromLeader && !replay) {
					long g = o.has("g") ? o.get("g").getAsLong() : 0L;
					String addr = str(o, "a");
					if (g != 0L && g != lastGoId && !addr.isBlank() && addr.length() < 200) {
						lastGoId = g;
						onGo(new Go(str(o, "n"), addr, nick, now));
					}
				}
			}
			case "em" -> {
				String eid = str(o, "id");
				if (m != null && !replay && !eid.isEmpty() && eid.length() <= 64 && EMOTES.size() < 40) {
					EMOTES.add(new String[] {m.nick, eid});
				}
			}
			case "chat" -> {
				if (m != null && !replay) {
					addChat(new Chat(m.nick, str(o, "t"), now, false));
				}
			}
			default -> {
			}
		}
	}

	private static void applyRoster(JsonArray ro, long now) {
		List<String> keep = new ArrayList<>();
		String me = myUuid();
		for (JsonElement e : ro) {
			if (!e.isJsonArray() || e.getAsJsonArray().size() < 2) {
				continue;
			}
			JsonArray a = e.getAsJsonArray();
			String uu = a.get(0).getAsString();
			keep.add(uu);
			Member m = member(uu);
			if (m == null) {
				m = new Member(uu, a.get(1).getAsString());
				m.seen = uu.equals(leaderUuid) ? now : 0L; // not heard from yet
				MEMBERS.add(m);
			}
			if (a.size() > 2 && m.friendId.isEmpty()) {
				m.friendId = a.get(2).getAsString();
			}
		}
		MEMBERS.removeIf(m -> !keep.contains(m.uuid) && !m.uuid.equals(me) && now - joinedAt > 3_000L);
	}

	private static void onGo(Go g) {
		go = g;
		MinecraftClient mc = MinecraftClient.getInstance();
		system(Lang.tr("The leader is going to ", "O lider vai para ") + g.name());
		String here = place();
		if (!here.isEmpty() && here.equalsIgnoreCase(g.addr())) {
			go = null; // already there
			return;
		}
		Notify.clickable(Notify.Kind.PLAY, Lang.tr("Party is joining a server", "A party vai entrar num servidor"),
				g.name() + Lang.tr("  -  click to join", "  -  clica para entrar"), g.from(), () -> joinNow(g));
		if (!lobbyOpen && mc.world == null) {
			openLobby(mc); // the 3D lobby shows the "joining" animation and then connects
		}
	}

	// ================================================================== invites (from friend messages)
	/** A friend message with a party token was seen in the inbox. */
	static void onInviteMessage(Social.User from, String partyId, String leaderUuid2, String leaderNick, long sentAt, boolean announce) {
		long now = System.currentTimeMillis();
		if (from == null || from.id == null || now - sentAt > INVITE_TTL_MS || DISMISSED.containsKey(partyId) || partyId.equals(id) && active) {
			return;
		}
		for (Invite i : INVITES) {
			if (i.partyId.equals(partyId)) {
				return;
			}
		}
		if (Mutes.isMuted(from)) {
			return;
		}
		Invite inv = new Invite(partyId, leaderUuid2, leaderNick.isEmpty() ? from.display() : leaderNick, from.id, sentAt);
		INVITES.add(inv);
		if (announce) {
			Notify.clickable(Notify.Kind.FRIEND, from.display() + Lang.tr(" invited you to a party", " convidou-te para uma party"),
					Lang.tr("Click to join", "Clica para entrar"), from.display(), () -> {
						accept(inv);
						openLobby(MinecraftClient.getInstance());
					});
		}
	}

	/** Opens the 3D lobby (the only party screen). */
	public static void openLobby(MinecraftClient mc) {
		mc.setScreen(new dev.sleepclient.gui.PartyLobbyScreen(mc.currentScreen));
	}

	/** Parses "2024-01-01T10:00:00Z"; "now" when it can not be read. */
	static long parseTime(String iso) {
		if (iso == null) {
			return System.currentTimeMillis();
		}
		try {
			return java.time.Instant.parse(iso).toEpochMilli();
		} catch (RuntimeException e) {
			return System.currentTimeMillis();
		}
	}

	// ================================================================== helpers
	private static Member member(String uuid) {
		for (Member m : MEMBERS) {
			if (m.uuid.equals(uuid)) {
				return m;
			}
		}
		return null;
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
	}

	private static void system(String t) {
		addChat(new Chat("", t, System.currentTimeMillis(), true));
	}

	private static void addChat(Chat c) {
		CHAT.add(c);
		while (CHAT.size() > 60) {
			CHAT.remove(0);
		}
	}
}
