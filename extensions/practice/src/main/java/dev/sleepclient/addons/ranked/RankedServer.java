package dev.sleepclient.addons.ranked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/**
 * The fight, on the host's built-in server: waits for everyone (90 s max), then rounds of "everyone at their spawn with
 * the kit, frozen until GO" (16.6 s for round 1 = the intro, 5 s after), eliminations instead of deaths, first to X
 * rounds (2 for ranked, 1-10 chosen for friendly duels). Every round is played in the next arena of the map, so blocks
 * placed in building kits never carry over; the world itself is a fresh copy for every match.
 * A player who leaves loses the match for their team. The result goes to the site from the host's game.
 */
public final class RankedServer {

	enum Phase {
		IDLE, WAITING, COUNTDOWN, FIGHT, ROUND_END, DONE
	}

	/** Intro length (ticks) = the intro's "FIGHT!" moment (15 s); the clients see the freeze effect and play the intro with it. */
	static final int INTRO_TICKS = 300, ROUND_TICKS = 100;
	/** Slowness level used as the freeze: the client knows from it that the intro (254) or a round countdown (253) runs. */
	static final int INTRO_MARK = 254, ROUND_MARK = 253;
	/** Slowness level while waiting at your spawn for everyone to load: your game answers with {@link RankedReady} once loaded. */
	static final int WAIT_MARK = 252;
	/** Everyone is connected but someone never says "loaded" (very slow PC): start anyway after this long. */
	private static final int LOAD_MAX = 25 * 20;
	/** Luck level given to everyone when the match ends: who won. */
	static final int WIN_MARK_A = 200, WIN_MARK_B = 201, WIN_MARK_NONE = 202;
	private static final int WAIT_MAX = 90 * 20, ROUND_END_TICKS = 60, DONE_TICKS = 200;
	/** World height limit: walls go up to it, and anyone above it is out at once. */
	static final int BOTTOM_Y = -64, TOP_Y = 319;
	/** The arena of the current round (its walls are built a moment after the round starts, once its chunks loaded). */
	private static RankedWorld.Arena cur;
	private static boolean wallsBuilt;

	private static volatile Ranked.Match m;
	private static volatile Phase phase = Phase.IDLE;
	private static int ticks, countdown, round, scoreA, scoreB;
	private static final Set<String> DEAD = new HashSet<>();
	private static final Set<String> READY = java.util.concurrent.ConcurrentHashMap.newKeySet(), PARKED = new HashSet<>();
	private static int allOnlineAt = -1, allReadyAt = -1;
	// battle stats of the sidebar, by lower-case player name
	private static final java.util.Map<String, Integer> HITS = new java.util.HashMap<>(), COMBO = new java.util.HashMap<>(), BEST = new java.util.HashMap<>();
	private static final java.util.Map<String, Double> DAMAGE = new java.util.HashMap<>();
	private static final java.util.Map<Integer, String> SIDEBAR = new java.util.HashMap<>();
	private static int fightTicks;
	private static int curArenaId;

	private RankedServer() {
	}

	static void prepare(Ranked.Match match) {
		m = match;
		phase = Phase.WAITING;
		ticks = 0;
		round = 0;
		scoreA = 0;
		scoreB = 0;
		DEAD.clear();
		READY.clear();
		PLACED.clear();
		PARKED.clear();
		allOnlineAt = -1;
		allReadyAt = -1;
		HITS.clear();
		COMBO.clear();
		BEST.clear();
		DAMAGE.clear();
		SIDEBAR.clear();
		fightTicks = 0;
		curArenaId = match.arena();
		RankedAntiCheat.reset(match.kit());
	}

	/** This player's game finished loading the arena. */
	public static void markReady(ServerPlayerEntity p) {
		READY.add(p.getName().getString().toLowerCase(Locale.ROOT));
	}

	/** The fight started (everyone was there): the site can call the match live. */
	static boolean started() {
		return phase != Phase.IDLE && phase != Phase.WAITING;
	}

	/** True in the arena world while a match runs (no damage outside the fight). */
	public static boolean active(MinecraftServer s) {
		return m != null && phase != Phase.IDLE && isArenaWorld(s);
	}

	/** Blocks the players placed in this match (long BlockPos): the only ones that can be broken or blown up. The map is never touched. */
	private static final java.util.Set<Long> PLACED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** A player used an item on a block: whatever gets placed there (in air or a replaceable block) is theirs to break. */
	public static void notePlace(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction side) {
		var s = world.getServer();
		if (s == null || !active(s)) {
			return;
		}
		if (world.getBlockState(pos).isReplaceable()) {
			PLACED.add(pos.asLong());
		}
		net.minecraft.util.math.BlockPos off = pos.offset(side);
		if (world.getBlockState(off).isReplaceable()) {
			PLACED.add(off.asLong());
		}
	}

	/** Inside an arena match only blocks the players placed may be broken, and only while fighting (not creative: nobody breaks the map). */
	public static boolean canBreak(MinecraftServer s, net.minecraft.util.math.BlockPos pos) {
		if (!active(s)) {
			return true;
		}
		if (phase != Phase.FIGHT || !PLACED.contains(pos.asLong())) {
			return false;
		}
		PLACED.remove(pos.asLong());
		return true;
	}

	/** Explosions: the arena's map blocks stay; only placed blocks may go. */
	public static boolean explosionMayBreak(MinecraftServer s, net.minecraft.util.math.BlockPos pos) {
		if (!active(s)) {
			return true;
		}
		if (PLACED.contains(pos.asLong())) {
			PLACED.remove(pos.asLong());
			return true;
		}
		return false;
	}

	private static boolean isArenaWorld(MinecraftServer s) {
		try {
			var p = s.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
			return p.getFileName() != null && p.getFileName().toString().equals(Ranked.WORLD);
		} catch (Exception e) {
			return false;
		}
	}

	private static void run(MinecraftServer s, String cmd) {
		try {
			s.getCommandManager().parseAndExecute(s.getCommandSource().withSilent(), cmd);
		} catch (Exception ignored) {
			// one command failing must not stop the match
		}
	}

	private static List<String> team(String t) {
		List<String> out = new ArrayList<>();
		Ranked.Match mm = m;
		if (mm != null) {
			for (var p : "A".equals(t) ? mm.teamA() : mm.teamB()) {
				// names go into server commands: only a valid Minecraft name is ever used
				if (p.mcUsername() != null && p.mcUsername().matches("[A-Za-z0-9_]{1,16}")) {
					out.add(p.mcUsername());
				}
			}
		}
		return out;
	}

	private static ServerPlayerEntity player(MinecraftServer s, String name) {
		return s.getPlayerManager().getPlayer(name);
	}

	// ---------------------------------------------------------------- tick
	public static void tick(MinecraftServer s) {
		Ranked.Match mm = m;
		if (mm == null || phase == Phase.IDLE || !isArenaWorld(s)) {
			return;
		}
		ticks++;
		if (ticks % 20 == 0 && phase != Phase.DONE) {
			// every second, for everybody in the match: survival (adventure while waiting), no flying. The host's world may be creative and the host
			// can't be allowed to fly or build from there. It is the game's own call, not a command, so no permission can stop it.
			net.minecraft.world.GameMode want = phase == Phase.WAITING ? net.minecraft.world.GameMode.ADVENTURE : net.minecraft.world.GameMode.SURVIVAL;
			for (ServerPlayerEntity p : allPlayers(s)) {
				try {
					boolean wrong = p.interactionManager.getGameMode() != want && p.interactionManager.getGameMode() != net.minecraft.world.GameMode.SPECTATOR;
					if (wrong) {
						p.changeGameMode(want);
					}
					var ab = p.getAbilities();
					if (!p.isSpectator() && (wrong || ab.allowFlying || ab.flying)) {
						ab.allowFlying = false;
						ab.flying = false;
						ab.invulnerable = false;
						p.sendAbilitiesUpdate();
					}
				} catch (RuntimeException ignored) {
					// next second
				}
			}
		}
		if (phase != Phase.WAITING && ticks % 20 == 0 && phase != Phase.DONE) {
			sidebar(s, mm);
		}
		switch (phase) {
			case WAITING -> waiting(s, mm);
			case COUNTDOWN -> {
				if (!wallsBuilt && (ticks == 10 || ticks == 40)) {
					walls(s, ticks == 40);
				}
				if (--countdown <= 0) {
					phase = Phase.FIGHT;
					run(s, "effect clear @a minecraft:slowness");
					jumpAll(s, "reset");
					if (round > 1) {
						// round 1: the intro on every client shows FIGHT! with its own sound
						run(s, "title @a times 0 20 10");
						run(s, "title @a title {text:\"FIGHT!\",color:\"red\",bold:true}");
						run(s, "execute as @a at @s run playsound minecraft:entity.ender_dragon.growl master @s ~ ~ ~ 0.6 1.4");
					}
				} else if (round > 1 && countdown % 20 == 0 && countdown <= 60) {
					run(s, "title @a title {text:\"" + countdown / 20 + "\",color:\"gold\",bold:true}");
					run(s, "execute as @a at @s run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 1 1.2");
				}
			}
			case FIGHT -> {
				fightTicks++;
				RankedAntiCheat.tick(s, allPlayers(s));
				String caught = RankedAntiCheat.proven();
				if (caught != null && mm.ranked()) {
					// overwhelming evidence only: the cheater's team forfeits a ranked match
					String t = mm.team(caught);
					if (t != null) {
						finish(s, "A".equals(t) ? "B" : "A", "anticheat");
						return;
					}
				}
				// out of the arena: into the void, above the height limit, or somehow past the walls = eliminated at once
				for (String n : allNames()) {
					ServerPlayerEntity p = player(s, n);
					if (p != null && !DEAD.contains(n.toLowerCase(Locale.ROOT)) && outside(p)) {
						eliminate(s, p);
					}
				}
			}
			case ROUND_END -> {
				if (ticks >= ROUND_END_TICKS) {
					if (scoreA >= mm.firstTo() || scoreB >= mm.firstTo()) {
						finish(s, scoreA > scoreB ? "A" : "B", "rounds");
					} else {
						startRound(s, false);
					}
				}
			}
			case DONE -> {
				if (ticks >= DONE_TICKS) {
					phase = Phase.IDLE;
					m = null;
				}
			}
			default -> {
			}
		}
	}

	/** One player at a time: the attribute command refuses "@a" as soon as there are two players. */
	private static void jumpAll(MinecraftServer s, String what) {
		for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
			run(s, "attribute " + p.getUuidAsString() + " minecraft:jump_strength base " + what);
		}
	}

	private static List<ServerPlayerEntity> allPlayers(MinecraftServer s) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		for (String n : allNames()) {
			ServerPlayerEntity p = player(s, n);
			if (p != null && !DEAD.contains(n.toLowerCase(Locale.ROOT))) {
				out.add(p);
			}
		}
		return out;
	}

	private static List<String> allNames() {
		List<String> l = new ArrayList<>(team("A"));
		l.addAll(team("B"));
		return l;
	}

	private static void waiting(MinecraftServer s, Ranked.Match mm) {
		if (ticks == 1) {
			// a calm, fair arena: always day, no weather, no mobs, nothing dropped
			run(s, "gamerule advance_time false");
			run(s, "gamerule advance_weather false");
			run(s, "gamerule spawn_mobs false");
			run(s, "gamerule keep_inventory true");
			run(s, "gamerule show_advancement_messages false");
			run(s, "gamerule show_death_messages false");
			run(s, "time set day");
			run(s, "weather clear");
			run(s, "difficulty normal");
			run(s, "scoreboard objectives add sleep_ranked dummy");
			run(s, "scoreboard objectives modify sleep_ranked displayname {text:\"SLEEP DUELS\",color:\"aqua\",bold:true}");
			run(s, "scoreboard objectives modify sleep_ranked numberformat blank");
			run(s, "scoreboard objectives setdisplay sidebar sleep_ranked");
			RankedWorld.Arena a0 = RankedWorld.arena(mm.arena());
			if (a0 != null) {
				run(s, "forceload add " + (a0.x1() - 2) + " " + (a0.z1() - 2) + " " + (a0.x2() + 2) + " " + (a0.z2() + 2));
			}
		}
		park(s, mm);
		boolean allIn = true;
		boolean anyA = false, anyB = false;
		for (String n : team("A")) {
			boolean in = player(s, n) != null;
			allIn &= in;
			anyA |= in;
		}
		for (String n : team("B")) {
			boolean in = player(s, n) != null;
			allIn &= in;
			anyB |= in;
		}
		// the intro only starts when every game has really loaded the arena (and one second more, so nothing pops in)
		List<String> loading = new ArrayList<>();
		for (String n : allNames()) {
			if (!READY.contains(n.toLowerCase(Locale.ROOT))) {
				loading.add(n);
			}
		}
		if (allIn && allOnlineAt < 0) {
			allOnlineAt = ticks;
		}
		boolean allReady = allIn && loading.isEmpty();
		if (allReady && allReadyAt < 0) {
			allReadyAt = ticks;
		} else if (!allReady) {
			allReadyAt = -1;
		}
		if (ticks % 20 == 0) {
			String what = loading.isEmpty() ? "Everyone is here!" : "Waiting for " + String.join(" & ", loading) + " to load...";
			run(s, "title @a actionbar {text:\"" + what + "  " + Math.max(0, (allIn ? LOAD_MAX - (ticks - allOnlineAt) : WAIT_MAX - ticks) / 20) + "s\",color:\"gray\"}");
		}
		if (allReadyAt >= 0 && ticks - allReadyAt >= 20 || allIn && ticks - allOnlineAt > LOAD_MAX) {
			startRound(s, true);
		} else if (!allIn && ticks > WAIT_MAX) {
			// someone never came: their team loses (nobody gets points if both teams are missing someone)
			finish(s, anyA && !anyB ? "A" : anyB && !anyA ? "B" : "none", "absent");
		}
	}

	private static void startRound(MinecraftServer s, boolean intro) {
		round++;
		DEAD.clear();
		Ranked.Match mm = m;
		// clean the last round: dropped items, crystals, minecarts, arrows, tnt, creepers...
		run(s, "kill @e[type=!minecraft:player]");
		int n = Math.max(1, RankedWorld.arenas().size());
		int id = ((Math.max(1, mm.arena()) - 1 + round - 1) % n) + 1;
		RankedWorld.Arena arena = RankedWorld.arena(id);
		cur = arena;
		curArenaId = id;
		wallsBuilt = false;
		if (arena != null) {
			// load the arena's chunks now; the walls are built a few ticks later (see COUNTDOWN)
			run(s, "forceload add " + (arena.x1() - 2) + " " + (arena.z1() - 2) + " " + (arena.x2() + 2) + " " + (arena.z2() + 2));
		}
		List<String> kit = Ranked.kitLines(mm.kit());
		place(s, team("A"), arena == null ? null : arena.a(), arena == null ? null : arena.a2(), kit);
		place(s, team("B"), arena == null ? null : arena.b(), arena == null ? null : arena.b2(), kit);
		countdown = intro ? INTRO_TICKS : ROUND_TICKS;
		int mark = intro ? INTRO_MARK : ROUND_MARK;
		run(s, "effect give @a minecraft:slowness " + (countdown / 20 + 1) + " " + mark + " true");
		jumpAll(s, "set 0");
		if (!intro) {
			run(s, "title @a times 0 30 10");
			run(s, "title @a title {text:\"ROUND " + round + "\",color:\"white\",bold:true}");
			run(s, "title @a subtitle {text:\"" + scoreA + " - " + scoreB + "  \u2022  first to " + mm.firstTo() + "\",color:\"gray\"}");
		}
		phase = Phase.COUNTDOWN;
		ticks = 0;
		sidebar(s, mm);
	}

	/** Everyone who arrives waits at their own spawn, frozen and safe, so the arena loads around them before the intro. */
	private static void park(MinecraftServer s, Ranked.Match mm) {
		RankedWorld.Arena a = RankedWorld.arena(mm.arena());
		for (String t : new String[] {"A", "B"}) {
			List<String> names = team(t);
			for (int i = 0; i < names.size(); i++) {
				ServerPlayerEntity p = player(s, names.get(i));
				if (p == null) {
					continue;
				}
				String key = names.get(i).toLowerCase(Locale.ROOT), u = p.getUuidAsString();
				if (PARKED.add(key)) {
					RankedWorld.Spot sp = a == null ? null : "A".equals(t) ? (i == 0 ? a.a() : a.a2()) : (i == 0 ? a.b() : a.b2());
					if (sp != null) {
						run(s, "tp " + u + " " + sp.x() + " " + sp.y() + " " + sp.z() + " " + sp.yaw() + " 0");
					}
					run(s, "gamemode adventure " + u);
					run(s, "clear " + u);
					run(s, "attribute " + u + " minecraft:jump_strength base set 0");
				}
				if (ticks % 20 == 1 || p.age < 5) {
					run(s, "effect give " + u + " minecraft:slowness 3 " + WAIT_MARK + " true");
				}
			}
		}
	}

	// ---------------------------------------------------------------- the sidebar: live stats of the battle, like the PvP servers
	private static int sum(java.util.Map<String, Integer> m, List<String> names) {
		int v = 0;
		for (String n : names) {
			v += m.getOrDefault(n.toLowerCase(Locale.ROOT), 0);
		}
		return v;
	}

	private static int max(java.util.Map<String, Integer> m, List<String> names) {
		int v = 0;
		for (String n : names) {
			v = Math.max(v, m.getOrDefault(n.toLowerCase(Locale.ROOT), 0));
		}
		return v;
	}

	private static String stat(String label, String a, String b) {
		return "[{text:\"" + label + " \",color:\"gray\"},{text:\"" + a + "\",color:\"#5CA6FF\",bold:true},{text:\" - \",color:\"dark_gray\"},{text:\"" + b
				+ "\",color:\"#FF5C6C\",bold:true}]";
	}

	private static String info(String label, String value) {
		return "[{text:\"" + label + " \",color:\"gray\"},{text:\"" + value + "\",color:\"white\"}]";
	}

	private static int ping(MinecraftServer s, List<String> names) {
		int total = 0, n = 0;
		for (String name : names) {
			ServerPlayerEntity p = player(s, name);
			if (p != null) {
				total += p.networkHandler.getLatency();
				n++;
			}
		}
		return n == 0 ? 0 : total / n;
	}

	private static String side(String colour, String label, int score) {
		return "[{text:\"\u25A0 \",color:\"" + colour + "\"},{text:\"" + label + " \",color:\"white\"},{text:\"" + score + "\",color:\"" + colour + "\",bold:true}]";
	}

	private static void sidebar(MinecraftServer s, Ranked.Match mm) {
		List<String> a = team("A"), b = team("B");
		double da = 0, db = 0;
		for (String n : a) {
			da += DAMAGE.getOrDefault(n.toLowerCase(Locale.ROOT), 0D);
		}
		for (String n : b) {
			db += DAMAGE.getOrDefault(n.toLowerCase(Locale.ROOT), 0D);
		}
		boolean two = "2v2".equals(mm.mode());
		int secs = fightTicks / 20;
		String blank = "{text:\" \"}";
		List<String> lines = List.of(
				info(mm.ranked() ? "Ranked" : "Friendly", mm.mode()),
				info("Kit:", Ranked.kit(mm.kit()).name()),
				info("Arena:", String.valueOf(curArenaId)),
				info("Round:", Math.max(1, round) + " (first to " + mm.firstTo() + ")"),
				info("Time:", String.format("%02d:%02d", secs / 60, secs % 60)),
				blank,
				side("#5CA6FF", two ? "Blue Team" : a.get(0), scoreA),
				side("#FF5C6C", two ? "Red Team" : b.get(0), scoreB),
				blank,
				stat("Hits", String.valueOf(sum(HITS, a)), String.valueOf(sum(HITS, b))),
				stat("Damage", String.format(Locale.ROOT, "%.1f", da), String.format(Locale.ROOT, "%.1f", db)),
				stat("Combo", String.valueOf(max(BEST, a)), String.valueOf(max(BEST, b))),
				stat("Ping", String.valueOf(ping(s, a)), String.valueOf(ping(s, b))),
				blank,
				"{text:\"sleep client\",color:\"aqua\"}");
		for (int i = 0; i < lines.size(); i++) {
			String l = lines.get(i);
			if (l.equals(SIDEBAR.get(i))) {
				continue;
			}
			String holder = "sl" + i;
			if (!SIDEBAR.containsKey(i)) {
				run(s, "scoreboard players set " + holder + " sleep_ranked " + (lines.size() - i));
			}
			SIDEBAR.put(i, l);
			run(s, "scoreboard players display name " + holder + " sleep_ranked " + l);
		}
	}

	/** Counts a landed hit for the sidebar (hits, damage, best combo: a combo ends when you get hit). */
	public static void afterDamage(LivingEntity e, DamageSource src, float taken) {
		if (e instanceof ServerPlayerEntity hurt) {
			RankedAntiCheat.onDamaged(hurt);
		}
		if (phase != Phase.FIGHT || m == null || taken <= 0 || !(e instanceof ServerPlayerEntity v) || !(src.getAttacker() instanceof ServerPlayerEntity a) || a == v
				|| !isArenaWorld(v.getEntityWorld().getServer())) {
			return;
		}
		String ta = m.team(a.getName().getString()), tv = m.team(v.getName().getString());
		if (ta == null || tv == null || ta.equals(tv)) {
			return;
		}
		RankedAntiCheat.onHit(a, v, src, taken);
		String ka = a.getName().getString().toLowerCase(Locale.ROOT), kv = v.getName().getString().toLowerCase(Locale.ROOT);
		HITS.merge(ka, 1, Integer::sum);
		DAMAGE.merge(ka, (double) taken / 2D, Double::sum); // in hearts
		int c = COMBO.merge(ka, 1, Integer::sum);
		BEST.merge(ka, c, Math::max);
		COMBO.put(kv, 0);
	}

	/**
	 * Invisible barrier walls all around the round's arena, from the bottom of the world to the height limit, so nobody
	 * can leave it (only air is replaced: the map itself is never changed). No roof: above the limit you're out.
	 */
	private static void walls(MinecraftServer s, boolean last) {
		RankedWorld.Arena a = cur;
		if (a == null) {
			wallsBuilt = true;
			return;
		}
		int x1 = a.x1() - 1, z1 = a.z1() - 1, x2 = a.x2() + 1, z2 = a.z2() + 1;
		int mid = (BOTTOM_Y + TOP_Y) / 2;
		boolean ok = true;
		for (int[] ys : new int[][] {{BOTTOM_Y, mid}, {mid + 1, TOP_Y}}) {
			// a fill is limited to 32768 blocks: each side in two halves (bottom / top)
			ok &= fill(s, x1, ys[0], z1, x2, ys[1], z1);
			ok &= fill(s, x1, ys[0], z2, x2, ys[1], z2);
			ok &= fill(s, x1, ys[0], z1, x1, ys[1], z2);
			ok &= fill(s, x2, ys[0], z1, x2, ys[1], z2);
		}
		wallsBuilt = ok || last;
	}

	private static boolean fill(MinecraftServer s, int x1, int y1, int z1, int x2, int y2, int z2) {
		try {
			s.getCommandManager().getDispatcher().execute("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2
					+ " minecraft:barrier replace minecraft:air", s.getCommandSource().withSilent());
			return true;
		} catch (Exception e) {
			return false; // chunks not loaded yet: tried again a bit later
		}
	}

	/** Below the arena, above the height limit, or past the walls. */
	private static boolean outside(ServerPlayerEntity p) {
		if (p.getY() < -100 || p.getY() > TOP_Y + 1) {
			return true;
		}
		RankedWorld.Arena a = cur;
		return a != null && (p.getX() < a.x1() - 3 || p.getX() > a.x2() + 4 || p.getZ() < a.z1() - 3 || p.getZ() > a.z2() + 4);
	}

	private static void place(MinecraftServer s, List<String> names, RankedWorld.Spot first, RankedWorld.Spot second, List<String> kit) {
		PLACED.clear();
		for (int i = 0; i < names.size(); i++) {
			ServerPlayerEntity p = player(s, names.get(i));
			if (p == null) {
				continue;
			}
			String u = p.getUuidAsString();
			RankedWorld.Spot sp = i == 0 ? first : second;
			if (sp != null) {
				run(s, "tp " + u + " " + sp.x() + " " + sp.y() + " " + sp.z() + " " + sp.yaw() + " 0");
			}
			run(s, "gamemode survival " + u);
			run(s, "clear " + u);
			run(s, "effect clear " + u);
			p.setHealth(p.getMaxHealth());
			p.getHungerManager().setFoodLevel(20);
			p.getHungerManager().setSaturationLevel(5F);
			p.extinguish();
			for (String cmd : kit) {
				run(s, "execute as " + u + " run " + cmd);
			}
		}
	}

	// ---------------------------------------------------------------- events (registered once by the extension)
	/** Instead of dying: eliminated for this round. */
	public static boolean allowDeath(LivingEntity e, DamageSource src, float amount) {
		if (!(e instanceof ServerPlayerEntity p) || m == null || phase == Phase.IDLE || !isArenaWorld(p.getEntityWorld().getServer())) {
			return true;
		}
		if (m.team(p.getName().getString()) == null) {
			return true;
		}
		// a Totem of Undying in a hand: let the game run its own totem (pop, 1 heart, the effects) instead of eliminating at once. Void and /kill
		// ignore totems in vanilla too, so those still count as a death here.
		if ((p.getMainHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING) || p.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING))
				&& !src.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		p.setHealth(p.getMaxHealth());
		if (phase == Phase.FIGHT) {
			eliminate(p.getEntityWorld().getServer(), p);
		}
		return false;
	}

	/** No damage before GO / between rounds, and no hitting your teammate. */
	public static boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
		if (!(e instanceof ServerPlayerEntity p) || m == null || phase == Phase.IDLE || !isArenaWorld(p.getEntityWorld().getServer())) {
			return true;
		}
		if (phase != Phase.FIGHT) {
			return false;
		}
		if (src.getAttacker() instanceof ServerPlayerEntity a) {
			String ta = m.team(a.getName().getString()), tp = m.team(p.getName().getString());
			if (ta != null && ta.equals(tp) && a != p) {
				return false;
			}
		}
		return true;
	}

	/** A match player left: their team loses. */
	public static void onLeave(ServerPlayerEntity p) {
		Ranked.Match mm = m;
		if (mm == null || phase == Phase.IDLE || phase == Phase.DONE || !isArenaWorld(p.getEntityWorld().getServer())) {
			return;
		}
		String t = mm.team(p.getName().getString());
		if (t == null) {
			return;
		}
		finish(p.getEntityWorld().getServer(), "A".equals(t) ? "B" : "A", "quit");
	}

	private static void eliminate(MinecraftServer s, ServerPlayerEntity p) {
		String n = p.getName().getString();
		if (!DEAD.add(n.toLowerCase(Locale.ROOT))) {
			return;
		}
		String u = p.getUuidAsString();
		run(s, "gamemode spectator " + u);
		run(s, "execute at " + u + " run particle minecraft:explosion ~ ~1 ~");
		run(s, "execute as @a at @s run playsound minecraft:entity.lightning_bolt.thunder master @s ~ ~ ~ 0.4 1.6");
		run(s, "title " + u + " title {text:\"ELIMINATED\",color:\"red\",bold:true}");
		String t = m.team(n);
		boolean wiped = true;
		for (String o : team(t)) {
			if (!DEAD.contains(o.toLowerCase(Locale.ROOT)) && player(s, o) != null) {
				wiped = false;
			}
		}
		if (wiped) {
			String winner = "A".equals(t) ? "B" : "A";
			if ("A".equals(winner)) {
				scoreA++;
			} else {
				scoreB++;
			}
			run(s, "title @a times 5 40 10");
			run(s, "title @a subtitle {text:\"" + scoreA + " - " + scoreB + "\",color:\"white\"}");
			run(s, "title @a title {text:\"" + String.join(" & ", team(winner)) + " won the round\",color:\"gold\",bold:true}");
			phase = Phase.ROUND_END;
			ticks = 0;
		}
	}

	private static void finish(MinecraftServer s, String winner, String reason) {
		Ranked.Match mm = m;
		if (mm == null || phase == Phase.DONE) {
			return;
		}
		phase = Phase.DONE;
		ticks = 0;
		run(s, "effect clear @a");
		jumpAll(s, "reset");
		// tells every client who won, at once (no need to wait for the site): an invisible Luck effect whose level is the result
		int mark = "A".equals(winner) ? WIN_MARK_A : "B".equals(winner) ? WIN_MARK_B : WIN_MARK_NONE;
		run(s, "effect give @a minecraft:luck 15 " + mark + " true");
		run(s, "gamemode adventure @a");
		if (!"none".equals(winner)) {
			// the clients show the big VICTORY / DEFEAT screen; this line is only a fallback
			run(s, "title @a actionbar {text:\"" + String.join(" & ", team(winner)) + " wins  " + scoreA + " - " + scoreB
					+ (reason.equals("quit") ? " (opponent left)" : reason.equals("absent") ? " (opponent never came)" : "") + "\",color:\"gold\"}");
		}
		sidebar(s, mm);
		String id = mm.id(), score = scoreA + "-" + scoreB;
		com.google.gson.JsonArray flags = RankedAntiCheat.report();
		net.minecraft.client.MinecraftClient.getInstance().execute(() -> Ranked.reportResult(id, winner, reason, score, flags));
	}
}
