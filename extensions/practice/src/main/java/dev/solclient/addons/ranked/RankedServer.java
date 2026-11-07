package dev.solclient.addons.ranked;

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

	/** Intro length (ticks) = the intro page's "GO" moment; the clients see the freeze effect and play the intro with it. */
	static final int INTRO_TICKS = 333, ROUND_TICKS = 100;
	/** Slowness level used as the freeze: the client knows from it that the intro (254) or a round countdown (253) runs. */
	static final int INTRO_MARK = 254, ROUND_MARK = 253;
	private static final int WAIT_MAX = 90 * 20, ROUND_END_TICKS = 60, DONE_TICKS = 200;

	private static volatile Ranked.Match m;
	private static volatile Phase phase = Phase.IDLE;
	private static int ticks, countdown, round, scoreA, scoreB;
	private static final Set<String> DEAD = new HashSet<>();

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
	}

	/** The fight started (everyone was there): the site can call the match live. */
	static boolean started() {
		return phase != Phase.IDLE && phase != Phase.WAITING;
	}

	/** True in the arena world while a match runs (no damage outside the fight). */
	public static boolean active(MinecraftServer s) {
		return m != null && phase != Phase.IDLE && isArenaWorld(s);
	}

	/** Blocks can be broken only while fighting (building kits); never during the intro or between rounds. */
	public static boolean canBreak(MinecraftServer s) {
		return !active(s) || phase == Phase.FIGHT;
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
				out.add(p.mcUsername());
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
		switch (phase) {
			case WAITING -> waiting(s, mm);
			case COUNTDOWN -> {
				if (--countdown <= 0) {
					phase = Phase.FIGHT;
					run(s, "effect clear @a minecraft:slowness");
					run(s, "attribute @a minecraft:jump_strength base reset");
					run(s, "title @a times 0 20 10");
					run(s, "title @a title {text:\"FIGHT!\",color:\"red\",bold:true}");
					run(s, "execute as @a at @s run playsound minecraft:entity.ender_dragon.growl master @s ~ ~ ~ 0.6 1.4");
				} else if (round > 1 && countdown % 20 == 0 && countdown <= 60) {
					run(s, "title @a title {text:\"" + countdown / 20 + "\",color:\"gold\",bold:true}");
					run(s, "execute as @a at @s run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 1 1.2");
				}
			}
			case FIGHT -> {
				// someone fell out of the arena (into the void): eliminated
				for (String n : allNames()) {
					ServerPlayerEntity p = player(s, n);
					if (p != null && !DEAD.contains(n.toLowerCase(Locale.ROOT)) && p.getY() < -100) {
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

	private static List<String> allNames() {
		List<String> l = new ArrayList<>(team("A"));
		l.addAll(team("B"));
		return l;
	}

	private static void waiting(MinecraftServer s, Ranked.Match mm) {
		if (ticks == 1) {
			// a calm, fair arena: always day, no weather, no mobs, nothing dropped
			run(s, "gamerule doDaylightCycle false");
			run(s, "gamerule doWeatherCycle false");
			run(s, "gamerule doMobSpawning false");
			run(s, "gamerule keepInventory true");
			run(s, "gamerule announceAdvancements false");
			run(s, "gamerule showDeathMessages false");
			run(s, "time set day");
			run(s, "weather clear");
			run(s, "difficulty normal");
			run(s, "scoreboard objectives add sleep_ranked dummy");
		}
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
		if (ticks % 20 == 0) {
			run(s, "title @a actionbar {text:\"Waiting for players... " + Math.max(0, (WAIT_MAX - ticks) / 20) + "s\",color:\"gray\"}");
			run(s, "gamemode adventure @a");
		}
		if (allIn) {
			startRound(s, true);
		} else if (ticks > WAIT_MAX) {
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
		List<String> kit = Ranked.kitLines(mm.kit());
		place(s, team("A"), arena == null ? null : arena.a(), arena == null ? null : arena.a2(), kit);
		place(s, team("B"), arena == null ? null : arena.b(), arena == null ? null : arena.b2(), kit);
		countdown = intro ? INTRO_TICKS : ROUND_TICKS;
		int mark = intro ? INTRO_MARK : ROUND_MARK;
		run(s, "effect give @a minecraft:slowness " + (countdown / 20 + 1) + " " + mark + " true");
		run(s, "attribute @a minecraft:jump_strength base set 0");
		if (!intro) {
			run(s, "title @a times 0 30 10");
			run(s, "title @a title {text:\"ROUND " + round + "\",color:\"white\",bold:true}");
			run(s, "title @a subtitle {text:\"" + scoreA + " - " + scoreB + "  \u2022  first to " + mm.firstTo() + "\",color:\"gray\"}");
		}
		phase = Phase.COUNTDOWN;
		ticks = 0;
	}

	private static void place(MinecraftServer s, List<String> names, RankedWorld.Spot first, RankedWorld.Spot second, List<String> kit) {
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
		run(s, "attribute @a minecraft:jump_strength base reset");
		run(s, "gamemode adventure @a");
		if (!"none".equals(winner)) {
			run(s, "title @a times 10 80 20");
			run(s, "title @a subtitle {text:\"" + scoreA + " - " + scoreB + (reason.equals("quit") ? " (opponent left)" : reason.equals("absent") ? " (opponent never came)" : "") + "\",color:\"white\"}");
			run(s, "title @a title {text:\"" + String.join(" & ", team(winner)) + " WINS!\",color:\"gold\",bold:true}");
			run(s, "execute as @a at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1");
		}
		String id = mm.id(), score = scoreA + "-" + scoreB;
		net.minecraft.client.MinecraftClient.getInstance().execute(() -> Ranked.reportResult(id, winner, reason, score));
	}
}
