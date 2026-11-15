package dev.solclient.addons.ranked;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.solclient.Lang;
import dev.solclient.social.Social;
import dev.solclient.social.SolarApi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Ranked PvP (like the tier lists of PvP servers: HT1 ... LT5) and duels with friends, 1v1 or 2v2, in the Sleep arena map.
 *
 * The Sleep site is the referee: it keeps the queue, makes the matches, picks the host (the first player who queued),
 * keeps the ELO / tiers and the rankings. The host's game opens the arena world and hosts it (Host World + relay); the
 * others join it. During a match the host cannot kick, ban or use commands; if the host leaves, the host's team loses
 * (the site stops getting the host's heartbeat). The fight itself runs in {@link RankedServer} on the host's game.
 */
public final class Ranked {

	/**
	 * Game modes (kits) with a tier list, in the order of the menu. id = what the site uses and the kit file
	 * (kubo_practice/kits/<id>.mcfunction); icon = the item shown for it.
	 */
	public record Kit(String id, String name, String icon) {
	}

	public static final List<Kit> KITS = List.of(new Kit("sword", "Sword", "diamond_sword"), new Kit("axe", "Axe", "diamond_axe"),
			new Kit("mace", "Mace", "mace"), new Kit("pot", "Pot", "splash_potion"), new Kit("netherite_pot", "Neth Pot", "netherite_helmet"),
			new Kit("uhc", "UHC", "golden_apple"), new Kit("shieldless_uhc", "Shieldless UHC", "lava_bucket"), new Kit("smp", "SMP", "ender_pearl"),
			new Kit("diamond_smp", "Diamond SMP", "diamond_chestplate"), new Kit("legacy_diamond_smp", "Legacy SMP", "diamond_helmet"),
			new Kit("moneysmp", "Money SMP", "emerald"), new Kit("vanilla_ht", "Crystal HT", "end_crystal"), new Kit("vanilla_lt", "Crystal LT", "respawn_anchor"),
			new Kit("cart_ht", "Cart HT", "tnt_minecart"), new Kit("cart_lt", "Cart LT", "minecart"), new Kit("creeper", "Creeper", "creeper_spawn_egg"),
			new Kit("bow", "Bow", "bow"), new Kit("spear", "Spear", "netherite_spear"), new Kit("spear_elytra", "Spear Elytra", "elytra"));

	private static final java.util.Map<String, List<String>> KIT_LINES = new java.util.concurrent.ConcurrentHashMap<>();

	/** The kit's commands (from kubo_practice/kits/<id>.mcfunction), written for "@s". */
	public static List<String> kitLines(String id) {
		return KIT_LINES.computeIfAbsent(id, k -> {
			List<String> out = new java.util.ArrayList<>();
			try (var in = Ranked.class.getResourceAsStream("/kubo_practice/kits/" + k + ".mcfunction")) {
				if (in != null) {
					for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
						l = l.strip();
						if (!l.isEmpty() && !l.startsWith("#")) {
							out.add(l);
						}
					}
				}
			} catch (Exception e) {
				System.err.println("[Sleep] kit " + k + ": " + e);
			}
			return out;
		});
	}

	public static Kit kit(String id) {
		for (Kit k : KITS) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return KITS.get(0);
	}

	public static final String WORLD = "Sleep Ranked Arenas";

	// ---------------------------------------------------------------- state from the site
	public record Player(String userId, String name, String mcUsername, String mcUuid, int elo, String tier) {
	}

	public record Match(String id, boolean ranked, String mode, String kit, int arena, String hostUserId, String hostName, String address,
			String status, List<Player> teamA, List<Player> teamB, String winner, int myChange, int firstTo) {

		public boolean has(String mcName) {
			return team(mcName) != null;
		}

		/** "A" / "B" / null. */
		public String team(String mcName) {
			for (Player p : teamA) {
				if (p.mcUsername.equalsIgnoreCase(mcName)) {
					return "A";
				}
			}
			for (Player p : teamB) {
				if (p.mcUsername.equalsIgnoreCase(mcName)) {
					return "B";
				}
			}
			return null;
		}

		public Set<String> names() {
			Set<String> s = new HashSet<>();
			for (Player p : teamA) {
				s.add(p.mcUsername.toLowerCase(Locale.ROOT));
			}
			for (Player p : teamB) {
				s.add(p.mcUsername.toLowerCase(Locale.ROOT));
			}
			return s;
		}
	}

	public record Invite(String id, String fromName, String mode, String kit, String kind, int firstTo) {
	}

	public record Rating(String kit, String mode, int elo, String tier, int wins, int losses) {
	}

	public record RankRow(int position, String name, String mcUuid, int elo, String tier, int wins, int losses) {
	}

	public static volatile String queueState = "idle"; // idle | searching | waiting_teammate
	public static volatile String queueMode = "1v1", queueKit = "sword";
	public static volatile long queuedAt;
	public static volatile Match match;
	public static volatile List<Invite> invites = List.of();
	public static volatile List<Rating> myRatings = List.of();
	public static volatile List<RankRow> rankings = List.of();
	public static volatile String rankingsKey = "";
	public static volatile String status = "";
	/** The site does not know /pvp yet (404): the menu says so. */
	public static volatile boolean siteMissing;

	private static volatile long lastPoll, lastHeartbeat;
	private static volatile boolean polling;
	/** Match ids this game already started hosting / joining / showed the end of. */
	private static String hostingFor, joiningFor, endedFor, liveSentFor;
	private static long worldOpenedAt;

	private Ranked() {
	}

	public static String me() {
		return Minecraft.getInstance().getUser().getName();
	}

	public static boolean inMatch() {
		Match m = match;
		return m != null && ("hosting".equals(m.status) || "live".equals(m.status)) && m.has(me());
	}

	public static boolean iAmHost() {
		Match m = match;
		return m != null && Social.me != null && Social.me.id != null && Social.me.id.equals(m.hostUserId);
	}

	// ---------------------------------------------------------------- tiers
	/** ELO -> tier (HT1 is the best, LT5 the lowest), like the PvP tier lists. */
	public static String tierOf(int elo) {
		String[] t = {"LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "LT2", "HT2", "LT1", "HT1"};
		int i = Math.max(0, Math.min(9, (elo - 1100) / 100));
		return t[i];
	}

	public static int tierColour(String tier) {
		if (tier == null) {
			return 0xFF8E8E8E;
		}
		return switch (tier) {
			case "HT1" -> 0xFFFFD23C;
			case "LT1" -> 0xFFFFB347;
			case "HT2" -> 0xFFF2F4F8;
			case "LT2" -> 0xFFC2C7D0;
			case "HT3" -> 0xFFE0904F;
			case "LT3" -> 0xFFB06A3B;
			case "HT4" -> 0xFF5DADE2;
			case "LT4" -> 0xFF3A7BBF;
			case "HT5" -> 0xFF9AA0AA;
			default -> 0xFF6E737C;
		};
	}

	public static Rating rating(String kit, String mode) {
		for (Rating r : myRatings) {
			if (r.kit.equals(kit) && r.mode.equals(mode)) {
				return r;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- actions (menu)
	public static void queue(String mode, String kit, String teammateId) {
		JsonObject b = new JsonObject();
		b.addProperty("mode", mode);
		b.addProperty("kit", kit);
		b.addProperty("ranked", true);
		if (teammateId != null) {
			b.addProperty("teammateId", teammateId);
		}
		queueMode = mode;
		queueKit = kit;
		queueState = "searching";
		queuedAt = System.currentTimeMillis();
		async(() -> {
			SolarApi.Res r = SolarApi.call("POST", "/pvp/queue", b);
			if (!r.ok()) {
				queueState = "idle";
				fail(r, Lang.tr("Could not join the queue", "Não deu para entrar na fila"));
			}
			poll(true);
		});
	}

	public static void leaveQueue() {
		queueState = "idle";
		async(() -> {
			SolarApi.call("DELETE", "/pvp/queue", null);
			poll(true);
		});
	}

	/** A duel with friends (not ranked): 1v1 = one opponent; 2v2 = a teammate and two opponents. */
	public static void challenge(String mode, String kit, String teammateId, List<String> opponentIds, int firstTo) {
		JsonObject b = new JsonObject();
		b.addProperty("mode", mode);
		b.addProperty("kit", kit);
		b.addProperty("firstTo", Math.max(1, Math.min(10, firstTo)));
		if (teammateId != null) {
			b.addProperty("teammateId", teammateId);
		}
		JsonArray a = new JsonArray();
		opponentIds.forEach(a::add);
		b.add("opponentIds", a);
		async(() -> {
			SolarApi.Res r = SolarApi.call("POST", "/pvp/duels", b);
			if (r.ok()) {
				Social.say(Lang.tr("Challenge sent!", "Desafio enviado!"), false);
			} else {
				fail(r, Lang.tr("Could not send the challenge", "Não deu para enviar o desafio"));
			}
			poll(true);
		});
	}

	public static void answer(Invite inv, boolean accept) {
		async(() -> {
			String path = "team".equals(inv.kind) ? "/pvp/queue/" + SolarApi.enc(inv.id) + (accept ? "/accept" : "/decline")
					: "/pvp/duels/" + SolarApi.enc(inv.id) + (accept ? "/accept" : "/decline");
			SolarApi.Res r = SolarApi.call("POST", path, new JsonObject());
			if (!r.ok()) {
				fail(r, Lang.tr("Could not answer", "Não deu para responder"));
			}
			poll(true);
		});
	}

	public static void loadRankings(String kit, String mode) {
		rankingsKey = kit + "/" + mode;
		async(() -> {
			SolarApi.Res r = SolarApi.call("GET", "/pvp/rankings?kit=" + kit + "&mode=" + mode + "&limit=50", null);
			List<RankRow> out = new ArrayList<>();
			if (r.ok()) {
				for (JsonElement e : arr(r.obj(), "players")) {
					JsonObject o = e.getAsJsonObject();
					int elo = num(o, "elo");
					out.add(new RankRow(num(o, "position"), str(o, "mcUsername", str(o, "name", "?")), str(o, "mcUuid", ""), elo, str(o, "tier", tierOf(elo)),
							num(o, "wins"), num(o, "losses")));
				}
			} else if (r.status() == 404) {
				siteMissing = true;
			}
			if ((kit + "/" + mode).equals(rankingsKey)) {
				rankings = out;
			}
		});
	}

	// ---------------------------------------------------------------- polling the site
	/** Every client tick: polls the site (fast while queued / in a match), starts hosting / joining, heartbeats. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		boolean busy = !"idle".equals(queueState) || match != null && !"finished".equals(match.status) && !"cancelled".equals(match.status);
		if (now - lastPoll > (busy ? 2000L : 20000L) && Social.me != null) {
			lastPoll = now;
			async(() -> poll(false));
		}
		Match m = match;
		if (m == null || !m.has(me())) {
			return;
		}
		if ("hosting".equals(m.status) || "live".equals(m.status)) {
			if (iAmHost()) {
				hostTick(mc, m, now);
			} else {
				joinTick(mc, m);
			}
		}
		if (("finished".equals(m.status) || "cancelled".equals(m.status)) && !m.id.equals(endedFor)) {
			endedFor = m.id;
			RankedFx.showEnd(m);
		}
	}

	private static void hostTick(Minecraft mc, Match m, long now) {
		if (!m.id.equals(hostingFor)) {
			// 1) open the arena world (installing it the first time)
			hostingFor = m.id;
			liveSentFor = null;
			dev.solclient.social.HostWorld.matchPlayers = m.names();
			RankedServer.prepare(m);
			worldOpenedAt = now;
			RankedWorld.open(mc);
			return;
		}
		// 2) once in the world: host it, then tell the site the address
		if (mc.getSingleplayerServer() != null && mc.player != null) {
			if (!dev.solclient.social.HostWorld.hosting() && now - worldOpenedAt > 2000L) {
				dev.solclient.social.HostWorld.host();
			}
			String addr = dev.solclient.social.HostWorld.hosting() && dev.solclient.social.KuboRelay.hostConnected() ? dev.solclient.social.KuboRelay.hostAddress() : null;
			if (addr != null && (m.address == null || !m.address.equalsIgnoreCase(addr))) {
				JsonObject b = new JsonObject();
				b.addProperty("address", addr);
				async(() -> SolarApi.call("POST", "/pvp/matches/" + SolarApi.enc(m.id) + "/address", b));
				match = withAddress(m, addr);
			}
			// heartbeat: when it stops (the host left / closed the game) the site gives the win to the other team
			if (now - lastHeartbeat > 5000L) {
				lastHeartbeat = now;
				async(() -> SolarApi.call("POST", "/pvp/matches/" + SolarApi.enc(m.id) + "/heartbeat", new JsonObject()));
			}
			if (RankedServer.started() && !m.id.equals(liveSentFor)) {
				liveSentFor = m.id;
				async(() -> SolarApi.call("POST", "/pvp/matches/" + SolarApi.enc(m.id) + "/live", new JsonObject()));
			}
		}
	}

	private static long joinTryAt;
	private static int joinTries;
	private static String triesFor;

	/**
	 * Takes you to the match's server. If the connection fails (a mod mismatch, the host's server not ready yet, a hiccup) it is tried again
	 * every 12 s, up to 6 times, instead of leaving you "in a match" while sitting in the menu.
	 */
	private static void joinTick(Minecraft mc, Match m) {
		if (m.address == null || m.id.equals(ignoredMatch)) {
			return;
		}
		if (dev.solclient.compat.Mc.screen(mc) instanceof net.minecraft.client.gui.screens.ConnectScreen) {
			joinTryAt = System.currentTimeMillis();
			return;
		}
		if (!m.id.equals(triesFor)) {
			triesFor = m.id;
			joinTries = 0;
			joinTryAt = 0;
		}
		var cur = mc.getCurrentServer();
		if (mc.level != null && cur != null && m.address.equalsIgnoreCase(cur.ip)) {
			joiningFor = m.id; // in the match
			joinTries = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (m.id.equals(joiningFor) && (now - joinTryAt < 12_000L || joinTries >= 6)) {
			return;
		}
		joiningFor = m.id;
		joinTryAt = now;
		joinTries++;
		String addr = m.address;
		mc.execute(() -> {
			if (mc.level != null) {
				quit(mc, false); // leave the world / server you were in
			}
			var info = new net.minecraft.client.multiplayer.ServerData("Sleep " + (m.ranked ? "Ranked" : "Duel"), addr, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
			net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
					net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(addr), info, false, null);
		});
	}

	/** The match this game left on purpose (/leave or the button): it is no longer shown as running. */
	private static volatile String ignoredMatch;

	/** /leave (only while a match runs) and the menu button: leave the match = lose it. The host leaving also ends it (the site sees the heartbeat stop). */
	public static void leaveMatch(Minecraft mc) {
		Match m = match;
		if (m == null) {
			return;
		}
		final String id = m.id;
		ignoredMatch = id;
		match = null;
		joiningFor = id;
		async(() -> {
			SolarApi.call("POST", "/pvp/matches/" + SolarApi.enc(id) + "/leave", new JsonObject());
			poll(true);
		});
		mc.execute(() -> {
			if (mc.level != null) {
				quit(mc, true);
			}
			if (mc.player != null) {
				dev.solclient.compat.Mc.chat(mc).addClientSystemMessage(net.minecraft.network.chat.Component.literal("[Sleep] You left the match."));
			}
		});
	}

	private static Match withAddress(Match m, String addr) {
		return new Match(m.id, m.ranked, m.mode, m.kit, m.arena, m.hostUserId, m.hostName, addr, m.status, m.teamA, m.teamB, m.winner, m.myChange, m.firstTo);
	}

	/** The host's game reports how the match ended (rounds, or a player who left). */
	public static void reportResult(String matchId, String winner, String reason, String score, com.google.gson.JsonArray flags) {
		JsonObject b = new JsonObject();
		b.add("anticheat", flags == null ? new com.google.gson.JsonArray() : flags);
		b.addProperty("winner", winner);
		b.addProperty("reason", reason);
		b.addProperty("score", score);
		async(() -> {
			SolarApi.call("POST", "/pvp/matches/" + SolarApi.enc(matchId) + "/result", b);
			poll(true);
		});
	}

	/** Everyone also tells the site who won, so a host can't simply report a win they didn't get. */
	public static void confirmResult(String matchId, String winner) {
		JsonObject b = new JsonObject();
		b.addProperty("winner", winner);
		async(() -> SolarApi.call("POST", "/pvp/matches/" + SolarApi.enc(matchId) + "/confirm", b));
	}

	/**
	 * Leaves the world / server like the pause menu does (stops hosting, saves, and always ends on a real screen; the
	 * old way left the game stuck on "Saving world"). {@code toTitle}: open the title screen afterwards.
	 */
	public static void quit(Minecraft mc, boolean toTitle) {
		try {
			if (dev.solclient.social.HostWorld.hosting()) {
				dev.solclient.social.HostWorld.stop();
			}
		} catch (Throwable ignored) {
			// still leave
		}
		boolean sp = mc.isLocalServer();
		if (mc.level != null) {
			mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
		if (sp) {
			mc.disconnectWithSavingScreen();
		} else {
			mc.disconnectWithProgressScreen();
		}
		if (toTitle) {
			dev.solclient.compat.Mc.setScreen(mc, new net.minecraft.client.gui.screens.TitleScreen());
		}
	}

	/** The match is over for this game: stop the match rules, leave the arena after the end screen. */
	public static void afterMatch() {
		dev.solclient.social.HostWorld.matchPlayers = null;
		hostingFor = null;
		joiningFor = null;
	}

	private static synchronized void poll(boolean force) {
		if (polling && !force) {
			return;
		}
		polling = true;
		try {
			SolarApi.Res r = SolarApi.call("GET", "/pvp/state", null);
			if (r.status() == 404) {
				siteMissing = true;
				return;
			}
			if (!r.ok()) {
				return;
			}
			siteMissing = false;
			JsonObject o = r.obj();
			JsonObject q = obj(o, "queue");
			if (q == null) {
				queueState = "idle";
			} else {
				queueState = str(q, "state", "searching");
				queueMode = str(q, "mode", queueMode);
				queueKit = str(q, "kit", queueKit);
			}
			List<Invite> inv = new ArrayList<>();
			for (JsonElement e : arr(o, "invites")) {
				JsonObject i = e.getAsJsonObject();
				inv.add(new Invite(str(i, "id", ""), str(i, "fromName", "?"), str(i, "mode", "1v1"), str(i, "kit", "sword"), str(i, "kind", "duel"),
						Math.max(1, i.has("firstTo") ? num(i, "firstTo") : 2)));
			}
			invites = inv;
			List<Rating> rs = new ArrayList<>();
			for (JsonElement e : arr(o, "ratings")) {
				JsonObject i = e.getAsJsonObject();
				int elo = num(i, "elo");
				rs.add(new Rating(str(i, "kit", "sword"), str(i, "mode", "1v1"), elo, str(i, "tier", tierOf(elo)), num(i, "wins"), num(i, "losses")));
			}
			myRatings = rs;
			JsonObject mo = obj(o, "match");
			Match old = match;
			match = mo == null ? (old != null && ("finished".equals(old.status) || "cancelled".equals(old.status)) ? old : null) : parseMatch(mo);
			if (match != null && match.id.equals(ignoredMatch) && !"finished".equals(match.status) && !"cancelled".equals(match.status)) {
				match = null; // you left this one: it does not hold you in "In a match" any more
			}
			if (old == null && match != null && "hosting".equals(match.status)) {
				queueState = "idle";
				net.minecraft.client.Minecraft.getInstance().execute(() -> RankedFx.found(match));
			}
		} catch (Exception e) {
			status = String.valueOf(e.getMessage());
		} finally {
			polling = false;
		}
	}

	private static Match parseMatch(JsonObject o) {
		JsonObject teams = obj(o, "teams");
		List<Player> a = players(teams == null ? null : teams.get("A")), b = players(teams == null ? null : teams.get("B"));
		JsonObject ch = obj(o, "eloChange");
		int mine = 0;
		if (ch != null && Social.me != null && Social.me.id != null && ch.has(Social.me.id)) {
			mine = ch.get(Social.me.id).getAsInt();
		}
		return new Match(str(o, "id", ""), !o.has("ranked") || o.get("ranked").getAsBoolean(), str(o, "mode", "1v1"), str(o, "kit", "sword"),
				Math.max(1, Math.min(15, num(o, "arena"))), str(o, "hostUserId", ""), str(o, "hostName", ""), o.has("address") && !o.get("address").isJsonNull() ? o.get("address").getAsString() : null,
				str(o, "status", "hosting"), a, b, o.has("winner") && !o.get("winner").isJsonNull() ? o.get("winner").getAsString() : null, mine,
				Math.max(1, Math.min(10, o.has("firstTo") ? num(o, "firstTo") : 2)));
	}

	private static List<Player> players(JsonElement e) {
		List<Player> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				JsonObject p = x.getAsJsonObject();
				int elo = num(p, "elo");
				out.add(new Player(str(p, "userId", ""), str(p, "name", "?"), str(p, "mcUsername", str(p, "name", "?")), str(p, "mcUuid", ""), elo, str(p, "tier", tierOf(elo))));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- helpers
	private static void async(Runnable r) {
		Thread t = new Thread(() -> {
			try {
				r.run();
			} catch (Throwable e) {
				status = String.valueOf(e.getMessage());
			}
		}, "sleep-ranked");
		t.setDaemon(true);
		t.start();
	}

	private static void fail(SolarApi.Res r, String what) {
		if (r.status() == 404) {
			siteMissing = true;
			Social.say(Lang.tr("Ranked PvP is not on the Sleep site yet.", "O Ranked PvP ainda não está no site do Sleep."), true);
			return;
		}
		String err = r.error();
		JsonObject o = r.ok() ? null : r.body() != null && r.body().isJsonObject() ? r.body().getAsJsonObject() : null;
		if (o != null && o.has("error")) {
			err = o.get("error").getAsString();
		}
		Social.say(what + (err == null || err.isBlank() ? "" : ": " + err), true);
	}

	static JsonObject obj(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
	}

	static JsonArray arr(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}

	static String str(JsonObject o, String k, String def) {
		return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
	}

	static int num(JsonObject o, String k) {
		try {
			return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0;
		} catch (Exception e) {
			return 0;
		}
	}
}
