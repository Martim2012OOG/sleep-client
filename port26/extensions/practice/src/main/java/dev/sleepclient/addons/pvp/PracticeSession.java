package dev.sleepclient.addons.pvp;

import dev.sleepclient.addons.ChunkWait;
import dev.sleepclient.addons.pvp.PracticeKit.Style;
import dev.sleepclient.addons.pvp.PracticeKit.Weapon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runs on the practice world's (integrated) server: builds the chosen arena in the sky, gives the kit and drives
 * the bots. Almost everything is done with vanilla commands (kits, arena, damage, weapon swaps), so it depends
 * very little on game internals. Bots are vanilla Mannequins (player-shaped) steered by the small AI below.
 *
 * What the bots can do (depends on the kit's {@link Style} and the difficulty):
 *  - SWORD / AXE: chase, strafe, crit jumps, heal; the axe bot gets bonus damage while you block.
 *  - MACE: wind-charge launch, dive onto you, smash damage from the real mace formula (capped), sword as backup.
 *  - SPEAR_MACE: spear lunge (charge damage from closing speed) -> launch -> mace smash -> back to the spear.
 *  - SWAP: attribute swaps: lunge swap (spear for one tick), axe swap against your shield, swap hits that keep
 *    the sword's attack rhythm, mace launches.
 * All the combat numbers are in the constants below, tweak them to rebalance.
 */
public final class PracticeSession {

	/** Chosen on the PvP Practice screen. */
	public static volatile boolean requested;
	public static volatile PracticeKit kit;
	public static volatile PracticeArena arena;
	public static volatile BotDifficulty difficulty = BotDifficulty.MEDIUM;
	public static volatile int botCount = 1;

	private static final String TAG = "kubo_bot";
	/** players stand here, the floor is at ARENA_Y - 1 */
	static final int ARENA_Y = 220;
	private static final Random RND = new Random();

	// ---- tunable combat numbers (approximations of the real weapons, not exact vanilla values)
	private static final float SPEAR_JAB = 4.5F;
	private static final float SPEAR_REACH = 4.2F;
	private static final float SPEAR_MIN_REACH = 1.4F;
	private static final float SPEAR_CHARGE_BASE = 4F;
	/** extra charge damage per block/tick of closing speed */
	private static final float SPEAR_CHARGE_PER_SPEED = 18F;
	private static final double LUNGE_SPEED = 0.78;
	private static final int LUNGE_TICKS = 7;
	private static final float MACE_BASE = 6F;
	private static final float MACE_SMASH_CAP = 12F;
	private static final double LAUNCH_VY = 1.1;
	/**
	 * Bot hits go through the player's armor (Protection IV diamond/netherite cuts ~85-90%), so the kit numbers
	 * would end up under 1 damage. This brings the hits back to something you can feel. Lower it to make bots softer.
	 */
	private static final float ARMOR_COMP = 2.4F;

	private static final class Bot {
		int cd = 20;
		int knock;
		int strafeDir = 1;
		int strafeTime;
		int heals;
		int healCd;
		/** human tricks: drank the buff potions, ticks until the next pearl / golden apple / splash potion */
		boolean buffed;
		int pearlCd = 120, gapCd, splashCd = 200;
		float lastHp = 20F;
		/** what the bot has in its hand right now */
		Weapon held;
		int swapLock;
		int swapBack;
		/** 0 = no combo, 1 = in the air after a wind-charge launch */
		int combo;
		int comboT;
		int comboCd = 40;
		int lungeCd = 20;
		int lungeTicks;
		boolean spearHit;
		double peakY;
		/** health shown above the head last time (the name is only rewritten when it changes) */
		float shownHp = -1F;
	}

	private static boolean active;
	private static int phase;
	private static int wait;
	private static int hudTimer;
	private static int kills, deaths;
	private static int pendingSpawns, spawnTimer, scanTimer;
	/** arena build attempts in this load, and the timer that checks the floor is still there */
	private static int buildTries, floorTimer;
	/** round flow: bots left in this round, pause before the next one, and the player stats used for the fight stats */
	private static final int ROUND_PAUSE = 60, ROUND_PAUSE_WIN = 100;
	private static int botsLeft, roundWait;
	private static boolean resetPlayer;
	private static float lastPlayerHp = 20F, playerFall;
	private static ServerPlayer lastPlayer;
	private static PracticeKit curKit;
	private static PracticeArena curArena;
	private static BotDifficulty curDiff;
	private static int curCount;
	private static final Map<UUID, Bot> BOTS = new HashMap<>();

	private PracticeSession() {
	}

	// ------------------------------------------------------------------ lifecycle
	public static void begin(MinecraftServer server) {
		requested = false;
		curKit = kit != null ? kit : PracticeKit.ALL.get(0);
		curArena = arena != null ? arena : PracticeArena.ALL.get(0);
		curDiff = difficulty;
		curCount = Math.max(1, Math.min(3, botCount));
		active = true;
		phase = 0;
		wait = 0;
		buildTries = 0;
		floorTimer = 0;
		kills = 0;
		deaths = 0;
		lastPlayer = null;
		pendingSpawns = 0;
		botsLeft = 0;
		roundWait = 0;
		resetPlayer = false;
		lastPlayerHp = 20F;
		playerFall = 0F;
		FightStats.reset();
		BOTS.clear();
	}

	public static void stop() {
		active = false;
		BOTS.clear();
		lastPlayer = null;
		FightStats.stop();
	}

	/** True while a practice session is running (read by the client HUD). */
	public static boolean isActive() {
		return active;
	}

	static int run(MinecraftServer s, String cmd) {
		try {
			return s.getCommands().getDispatcher().execute(cmd, s.createCommandSourceStack());
		} catch (Exception e) {
			return 0;
		}
	}

	private static void runAll(MinecraftServer s, String[] cmds, String player, String botId) {
		for (String c : cmds) {
			String cmd = c;
			if (player != null) {
				cmd = cmd.replace("@P", player);
			}
			if (botId != null) {
				cmd = cmd.replace("@B", botId);
			}
			run(s, cmd);
		}
	}

	// ------------------------------------------------------------------ tick (server thread, end of tick)
	public static void tick(MinecraftServer server) {
		if (!active) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayer p = players.get(0);
		String name = p.getName().getString();

		switch (phase) {
			case 0 -> { // spectator (no falling) at the arena spot while its chunks load
				run(server, "forceload add -32 -32 31 31");
				run(server, "gamemode spectator " + name);
				run(server, "tp " + name + " 0 " + (ARENA_Y + 1) + " 0");
				run(server, "setworldspawn 0 " + ARENA_Y + " 0");
				wait = 0;
				buildTries = 0;
				phase = 1;
			}
			case 1 -> {
				// build only when the chunks are really loaded (a fixed delay failed on slow devices)
				wait++;
				PracticeWorld.setStatus("Preparing the arena...");
				if (wait % 10 == 1) {
					p.sendOverlayMessage(Component.literal("\u00a7ePreparing the arena..."));
				}
				if (!ChunkWait.ready(server.overworld(), -32, -32, 31, 31, wait)) {
					return;
				}
				applyRules(server);
				curArena.build(server);
				if (floorMissing(server)) {
					if (++buildTries >= 5) {
						active = false;
						PracticeWorld.markReady(); // nothing more to wait for: let the player see the message
						run(server, "gamemode creative " + name);
						p.sendSystemMessage(Component.literal("\u00a7cCould not build the arena. Leave the world and try again."));
					}
					return;
				}
				wait = 5;
				phase = 2;
			}
			case 2 -> {
				if (--wait <= 0) {
					run(server, "kill @e[tag=" + TAG + "]");
					setupPlayer(server, p, name);
					pendingSpawns = curCount;
					botsLeft = curCount;
					spawnTimer = 0;
					FightStats.startRound();
					phase = 3;
					PracticeWorld.markReady();
				}
			}
			default -> running(server, p, name);
		}
	}

	/** True when the arena floor is not there (build failed, or chunks were not loaded). */
	private static boolean floorMissing(MinecraftServer s) {
		return s.overworld().getBlockState(new BlockPos(0, ARENA_Y - 1, 0)).isAir();
	}

	private static void applyRules(MinecraftServer s) {
		String[] gamerules = {
				"doMobSpawning false", "spawn_mobs false",
				"doDaylightCycle false", "advance_time false",
				"doWeatherCycle false", "advance_weather false",
				"keepInventory true", "keep_inventory true",
				"doImmediateRespawn true", "immediate_respawn true",
				"showDeathMessages false", "show_death_messages false",
				"naturalRegeneration false", "natural_health_regeneration false"
		};
		for (String g : gamerules) {
			run(s, "gamerule " + g);
		}
		run(s, "difficulty normal");
		run(s, "time set noon");
		run(s, "weather clear");
	}

	private static void setupPlayer(MinecraftServer s, ServerPlayer p, String name) {
		lastPlayer = p;
		run(s, "clear " + name);
		run(s, "effect clear " + name);
		run(s, "effect give " + name + " minecraft:saturation infinite 0 true");
		run(s, "gamemode adventure " + name);
		run(s, "tp " + name + " 0 " + ARENA_Y + " -" + curArena.spawnZ + " 0 0");
		run(s, "effect give " + name + " minecraft:instant_health 1 10 true");
		lastPlayerHp = 20F;
		playerFall = 0F;
		runAll(s, curKit.player, name, null);
		for (Bot b : BOTS.values()) {
			b.cd = 30;
			b.heals = curDiff.heals;
		}
	}

	private static void running(MinecraftServer server, ServerPlayer p, String name) {
		var world = server.overworld();

		// safety net: if the floor ever disappears (or the player fell out), rebuild the arena instead of leaving them lost
		if (++floorTimer >= 20) {
			floorTimer = 0;
			if (floorMissing(server) || p.getY() < ARENA_Y - 10) {
				run(server, "kill @e[tag=" + TAG + "]");
				BOTS.clear();
				pendingSpawns = 0;
				botsLeft = 0;
				roundWait = 0;
				resetPlayer = false;
				phase = 0;
				return;
			}
		}

		// the player died and respawned: the round is lost, new kit, bots are replaced when the next round starts
		if (p != lastPlayer) {
			if (lastPlayer != null) {
				deaths++;
				FightStats.took(Math.max(0F, lastPlayerHp));
				finishRound(p, false);
				run(server, "kill @e[tag=" + TAG + "]");
				BOTS.clear();
				pendingSpawns = 0;
				resetPlayer = false;
				roundWait = ROUND_PAUSE;
			}
			setupPlayer(server, p, name);
			return;
		}

		// pause between two rounds, then everything starts again
		if (roundWait > 0) {
			if (--roundWait > 0) {
				return;
			}
			if (resetPlayer) {
				setupPlayer(server, p, name);
				resetPlayer = false;
			}
			run(server, "kill @e[tag=" + TAG + "]");
			BOTS.clear();
			botsLeft = curCount;
			pendingSpawns = curCount;
			spawnTimer = 0;
			FightStats.startRound();
		}

		// spawn queued bots one at a time
		if (pendingSpawns > 0 && --spawnTimer <= 0) {
			spawnBot(server, pendingSpawns);
			pendingSpawns--;
			spawnTimer = 20;
		}
		// find + equip newly summoned bots
		if (--scanTimer <= 0) {
			scanTimer = 5;
			AABB box = new AABB(-33, ARENA_Y - 5, -33, 33, ARENA_Y + 99, 33);
			for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, box, x -> x.entityTags().contains(TAG))) {
				if (BOTS.containsKey(e.getUUID()) || !e.isAlive()) {
					continue;
				}
				if (BOTS.size() >= curCount) {
					e.discard(); // never more bots than the ones you asked for
					continue;
				}
				Bot b = new Bot();
				b.heals = curDiff.heals;
				b.lastHp = e.getHealth();
				b.peakY = e.getY();
				b.comboCd = 30 + RND.nextInt(40);
				BOTS.put(e.getUUID(), b);
				runAll(server, curKit.bot, null, e.getStringUUID());
				equip(server, b, e.getStringUUID(), curKit.rest(), true);
			}
		}

		// bots: AI + death handling
		List<UUID> dead = new ArrayList<>();
		for (Map.Entry<UUID, Bot> en : BOTS.entrySet()) {
			Entity e = world.getEntity(en.getKey());
			if (!(e instanceof LivingEntity bot) || !bot.isAlive()) {
				dead.add(en.getKey());
				continue;
			}
			think(server, bot, en.getValue(), p, name);
		}
		for (UUID id : dead) {
			Bot gone = BOTS.remove(id);
			if (gone != null && gone.lastHp > 0) {
				FightStats.hit(gone.lastHp, isSmash(p)); // the killing blow (the bot is gone before its AI can see it)
			}
			kills++;
			botsLeft--;
		}
		if (!dead.isEmpty() && botsLeft <= 0 && pendingSpawns == 0) {
			finishRound(p, true);
			resetPlayer = true;
			roundWait = ROUND_PAUSE_WIN;
		}

		// fight stats: damage taken (the bots' hits) and the fall distance used to spot mace smashes
		FightStats.tick();
		float php = p.getHealth() + p.getAbsorptionAmount();
		if (php < lastPlayerHp - 0.01F) {
			FightStats.took(lastPlayerHp - php);
		}
		lastPlayerHp = php;
		playerFall = (float) p.fallDistance;

		if (++hudTimer >= 10) {
			hudTimer = 0;
			p.sendOverlayMessage(Component.literal("PvP Practice  |  " + curKit.name + " vs " + curDiff.label + "  |  " + curArena.name
					+ "  |  You " + kills + " - " + deaths + " Bots"));
		}
	}

	/** A mace hit while falling = smash. The fall distance is the one of the previous tick (the hit resets it). */
	private static boolean isSmash(ServerPlayer p) {
		return p.getMainHandItem().is(Items.MACE) && Math.max(playerFall, (float) p.fallDistance) > 1.5F;
	}

	private static void finishRound(ServerPlayer p, boolean won) {
		FightStats.Round r = FightStats.end(won);
		if (r == null) {
			return;
		}
		String col = won ? "\u00a7a" : "\u00a7c";
		p.sendSystemMessage(Component.literal("\u00a78----- \u00a76Round " + r.number() + " " + col + (won ? "Won" : "Lost") + " \u00a78-----"));
		p.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "\u00a77Damage dealt \u00a7f%.1f \u00a78| \u00a77taken \u00a7f%.1f", r.dealt(), r.taken())));
		p.sendSystemMessage(Component.literal("\u00a77Accuracy \u00a7f" + r.accuracy() + "% \u00a78(" + r.hits() + "/" + r.swings() + ")"));
		p.sendSystemMessage(Component.literal("\u00a77Combos \u00a7f" + r.combos() + " \u00a78(best " + r.bestCombo() + ") \u00a78| \u00a77Mace smashes \u00a7f" + r.smashes()));
	}

	private static void spawnBot(MinecraftServer s, int index) {
		double x = (index - 1) * 3 - (curCount - 1) * 1.5;
		String label = "Bot [" + curDiff.label + "]";
		String pos = x + " " + ARENA_Y + " " + curArena.spawnZ;
		String[] variants = {
				"summon minecraft:mannequin " + pos + " {Tags:[\"" + TAG + "\"],CustomName:\"" + label + "\",CustomNameVisible:1b,hide_description:1b,immovable:0b,profile:{name:\"Bot\"}}",
				"summon minecraft:mannequin " + pos + " {Tags:[\"" + TAG + "\"],CustomName:\"" + label + "\",CustomNameVisible:1b}",
				"summon minecraft:husk " + pos + " {Tags:[\"" + TAG + "\"],NoAI:1b,Silent:1b,CustomName:\"" + label + "\",CustomNameVisible:1b}"
		};
		var world = s.overworld();
		AABB box = new AABB(-33, ARENA_Y - 5, -33, 33, ARENA_Y + 99, 33);
		for (String v : variants) {
			int before = world.getEntitiesOfClass(LivingEntity.class, box, en -> en.entityTags().contains(TAG)).size();
			run(s, v);
			int after = world.getEntitiesOfClass(LivingEntity.class, box, en -> en.entityTags().contains(TAG)).size();
			if (after > before) {
				return; // one bot is there, do not try the fallbacks
			}
		}
	}

	// ------------------------------------------------------------------ weapon swaps
	private static void equip(MinecraftServer s, Bot st, String id, Weapon w, boolean force) {
		String item = curKit.item(w);
		if (item == null || (!force && st.held == w)) {
			return;
		}
		run(s, "item replace entity " + id + " weapon.mainhand with " + item);
		st.held = w;
	}

	private static void equip(MinecraftServer s, Bot st, String id, Weapon w) {
		equip(s, st, id, w, false);
	}

	private static void endCombo(MinecraftServer s, Bot st, String id) {
		st.combo = 0;
		st.comboT = 0;
		equip(s, st, id, curKit.rest());
	}

	/** Mace smash: 6 base + 4/block for the first 3 blocks, 2/block for the next 5, 1/block after, + density 0.5/block. */
	private static float maceDamage(double fall, int density) {
		double bonus = Math.min(fall, 3) * 4 + Math.max(0, Math.min(fall - 3, 5)) * 2 + Math.max(0, fall - 8);
		bonus += density * 0.5 * fall;
		return (float) (MACE_BASE + bonus);
	}

	private static void hurt(MinecraftServer s, String targetName, LivingEntity by, float dmg) {
		run(s, "damage " + targetName + " " + String.format(Locale.ROOT, "%.1f", dmg * ARMOR_COMP) + " minecraft:mob_attack by " + by.getStringUUID());
	}

	/** Name above the bot: "Bot [Medium]  17.5 hearts" in green / yellow / red, rewritten only when the health changes. */
	private static void showHealth(LivingEntity bot, Bot st, float hp) {
		float shown = Math.round(hp * 10F) / 10F;
		if (shown == st.shownHp) {
			return;
		}
		st.shownHp = shown;
		String col = hp > 13F ? "\u00a7a" : hp > 6F ? "\u00a7e" : "\u00a7c";
		bot.setCustomName(Component.literal("\u00a7fBot [" + curDiff.label + "]  " + col + String.format(Locale.ROOT, "%.1f", shown) + " \u2764"));
		bot.setCustomNameVisible(true);
	}

	// ------------------------------------------------------------------ the bot brain

	/**
	 * What a real player does besides swinging: drinks speed / strength before the fight, eats a golden apple when low, throws an ender
	 * pearl to close a gap, and splashes a slowness / weakness potion on you in a fight. Harder bots do more of it. Everything is shown
	 * (sound, particles, a line in the chat) so you can react.
	 */
	private static void humanTricks(MinecraftServer server, LivingEntity bot, Bot st, String id, String targetName, ServerPlayer target, double dist,
			double fx, double fz, float hp, boolean ground) {
		BotDifficulty d = curDiff;
		if (d == BotDifficulty.EASY) {
			return;
		}
		if (st.pearlCd > 0) {
			st.pearlCd--;
		}
		if (st.gapCd > 0) {
			st.gapCd--;
		}
		if (st.splashCd > 0) {
			st.splashCd--;
		}
		if (!st.buffed && dist < 18) {
			st.buffed = true;
			run(server, "effect give " + id + " minecraft:speed 60 " + (d == BotDifficulty.EXTREME ? 1 : 0) + " true");
			if (d == BotDifficulty.HARD || d == BotDifficulty.EXTREME) {
				run(server, "effect give " + id + " minecraft:strength 60 0 true");
			}
			run(server, "execute at " + id + " run particle minecraft:witch ~ ~1 ~ 0.3 0.4 0.3 0.02 12");
			run(server, "execute at " + id + " run playsound minecraft:entity.generic.drink hostile @a ~ ~ ~ 1 1");
		}
		// a golden apple when it is hurt
		if (hp < 9F && st.gapCd <= 0 && d != BotDifficulty.MEDIUM) {
			st.gapCd = 500;
			run(server, "effect give " + id + " minecraft:absorption 120 1 true");
			run(server, "effect give " + id + " minecraft:regeneration 6 1 true");
			bot.swing(InteractionHand.OFF_HAND);
			run(server, "execute at " + id + " run particle minecraft:heart ~ ~1.6 ~ 0.3 0.3 0.3 0.01 6");
			run(server, "execute at " + id + " run playsound minecraft:entity.generic.eat hostile @a ~ ~ ~ 1 1");
		}
		// an ender pearl to get back to you when you are far away
		if ((d == BotDifficulty.HARD || d == BotDifficulty.EXTREME) && dist > 14 && ground && st.pearlCd <= 0) {
			st.pearlCd = 320 + RND.nextInt(120);
			double tx = target.getX() - fx * 3.5, tz = target.getZ() - fz * 3.5;
			run(server, "execute at " + id + " run playsound minecraft:entity.ender_pearl.throw hostile @a ~ ~ ~ 1 1");
			run(server, "execute at " + id + " run particle minecraft:portal ~ ~1 ~ 0.4 0.6 0.4 0.4 30");
			run(server, "tp " + id + " " + String.format(Locale.ROOT, "%.2f %.2f %.2f", tx, target.getY(), tz));
			run(server, "execute at " + id + " run particle minecraft:portal ~ ~1 ~ 0.4 0.6 0.4 0.4 30");
			run(server, "execute at " + id + " run playsound minecraft:entity.player.teleport hostile @a ~ ~ ~ 1 1");
		}
		// a splash potion on you (slowness or weakness) from a few blocks
		if (d == BotDifficulty.EXTREME || d == BotDifficulty.HARD && RND.nextInt(3) == 0) {
			if (st.splashCd <= 0 && dist > 3 && dist < 8) {
				st.splashCd = 260 + RND.nextInt(120);
				String fxn = RND.nextBoolean() ? "slowness" : "weakness";
				run(server, "effect give " + targetName + " minecraft:" + fxn + " 4 0 true");
				run(server, "execute at " + targetName + " run particle minecraft:effect ~ ~1 ~ 0.5 0.6 0.5 0.1 30");
				run(server, "execute at " + targetName + " run playsound minecraft:entity.splash_potion.break hostile @a ~ ~ ~ 1 1");
			}
		}
	}
	private static void think(MinecraftServer server, LivingEntity bot, Bot st, ServerPlayer target, String targetName) {
		BotDifficulty d = curDiff;
		PracticeKit k = curKit;
		Style style = k.style;
		String id = bot.getStringUUID();
		if (st.held == null) {
			st.held = k.rest();
		}

		double dx = target.getX() - bot.getX();
		double dz = target.getZ() - bot.getZ();
		double dy = target.getY() - bot.getY();
		double dist = Math.sqrt(dx * dx + dz * dz);
		double fx = dx / Math.max(dist, 1.0E-4), fz = dz / Math.max(dist, 1.0E-4);

		float yaw = (float) (Mth.atan2(dz, dx) * 57.29577951308232D) - 90F;
		bot.setYRot(yaw);
		bot.setYHeadRot(yaw);
		bot.setYBodyRot(yaw);
		bot.setXRot(0F);

		// got hit: let the knockback play out instead of overriding the velocity
		float hp = bot.getHealth();
		showHealth(bot, st, hp);
		if (hp < st.lastHp - 0.01F) {
			st.knock = 7;
			FightStats.hit(st.lastHp - hp, isSmash(target));
		}
		st.lastHp = hp;
		if (st.healCd > 0) {
			st.healCd--;
		}
		if (st.cd > 0) {
			st.cd--;
		}
		if (st.comboCd > 0) {
			st.comboCd--;
		}
		if (st.lungeCd > 0) {
			st.lungeCd--;
		}
		if (st.swapLock > 0) {
			st.swapLock--;
		}
		if (st.swapBack > 0 && --st.swapBack == 0) {
			equip(server, st, id, k.rest());
		}

		// how far the bot has fallen since it left the ground / peaked (for mace smashes)
		boolean ground = bot.onGround();
		if (ground) {
			st.peakY = bot.getY();
		} else if (bot.getY() > st.peakY) {
			st.peakY = bot.getY();
		}
		double fall = st.peakY - bot.getY();

		boolean alive = target.isAlive() && !target.isSpectator();
		boolean airStyle = style == Style.MACE || style == Style.SPEAR_MACE || style == Style.SWAP;
		boolean spearStyle = style == Style.SPEAR_MACE || style == Style.SWAP;
		boolean blocking = target.isBlocking();
		Vec3 v = bot.getDeltaMovement();

		if (st.knock > 0) {
			st.knock--;
			if (st.combo > 0) {
				endCombo(server, st, id);
			}
			st.lungeTicks = 0;
		} else if (alive) {
			double mx = 0, mz = 0;
			double vy = v.y;
			double reachNow = st.held == Weapon.SPEAR ? SPEAR_REACH : d.reach;

			if (st.combo > 0) {
				// diving: steer onto the target, gravity does the rest
				st.comboT++;
				double hs = Math.min(0.34, Math.max(0.08, dist * 0.14));
				if (dist > 0.8) {
					mx = fx * hs;
					mz = fz * hs;
				}
				if (ground && st.comboT > 4) {
					endCombo(server, st, id);
				}
			} else if (st.lungeTicks > 0) {
				// the lunge propels the bot toward the target
				st.lungeTicks--;
				mx = fx * LUNGE_SPEED;
				mz = fz * LUNGE_SPEED;
				// charge damage grows with closing speed (spear-mace kit; the swap kit hits with its own weapon after)
				if (!st.spearHit && st.held == Weapon.SPEAR && dist <= SPEAR_REACH + 0.1 && dist >= 1.0 && Math.abs(dy) < 2.2) {
					st.spearHit = true;
					bot.swing(InteractionHand.MAIN_HAND);
					if (RND.nextFloat() < d.accuracy) {
						double closing = Math.sqrt(v.x * v.x + v.z * v.z);
						hurt(server, targetName, bot, (SPEAR_CHARGE_BASE + SPEAR_CHARGE_PER_SPEED * (float) closing) * d.dmgMul);
					}
				}
			} else {
				// spear lunge: open the distance with the spear, one tick swap for the swap kit
				boolean canLunge = style == Style.SPEAR_MACE || (style == Style.SWAP && d.swaps);
				if (canLunge && ground && st.lungeCd <= 0 && dist >= 3.2 && dist <= 9 && Math.abs(dy) < 2.5) {
					equip(server, st, id, Weapon.SPEAR);
					st.lungeTicks = LUNGE_TICKS;
					st.lungeCd = 50 + RND.nextInt(25);
					st.spearHit = style == Style.SWAP;
					if (style == Style.SWAP) {
						st.swapBack = 2;
					}
					mx = fx * LUNGE_SPEED;
					mz = fz * LUNGE_SPEED;
				} else if (airStyle && d.comboCd > 0 && ground && st.comboCd <= 0 && dist > 2.0 && dist < 6.5 && Math.abs(dy) < 2.5
						&& (style != Style.SPEAR_MACE || st.lungeCd > 0 || dist < 3.2)) {
					// wind-charge launch, then dive with the mace
					equip(server, st, id, Weapon.MACE);
					vy = LAUNCH_VY + RND.nextDouble() * 0.15;
					mx = fx * 0.16;
					mz = fz * 0.16;
					st.combo = 1;
					st.comboT = 0;
					st.comboCd = d.comboCd + RND.nextInt(20) + (style == Style.SWAP ? 25 : 0);
					st.peakY = bot.getY();
					run(server, "execute at " + id + " run particle minecraft:gust ~ ~ ~");
					run(server, "execute at " + id + " run playsound minecraft:entity.wind_charge.wind_burst hostile @a ~ ~ ~ 1 1");
				} else {
					// normal chase / strafe
					if (dist > reachNow - 0.5) {
						mx = fx * d.speed;
						mz = fz * d.speed;
					} else if (st.held == Weapon.SPEAR && dist < SPEAR_MIN_REACH + 0.2) {
						mx = -fx * d.speed * 0.8; // too close for the spear: back off
						mz = -fz * d.speed * 0.8;
					} else if (d == BotDifficulty.EXTREME && dist < 1.3) {
						mx = -fx * d.speed * 0.6;
						mz = -fz * d.speed * 0.6;
					}
					if (d.strafe && dist < 5.5) {
						if (--st.strafeTime <= 0) {
							st.strafeDir = -st.strafeDir;
							st.strafeTime = 10 + RND.nextInt(25);
						}
						mx += -fz * st.strafeDir * d.speed * 0.7;
						mz += fx * st.strafeDir * d.speed * 0.7;
					}
					boolean critJump = d.crits && dist < 3.6 && st.cd <= 2 && RND.nextInt(3) == 0
							&& st.held != Weapon.SPEAR && st.held != Weapon.MACE;
					boolean blocked = bot.horizontalCollision && dist > 1.5;
					if (ground && (critJump || blocked || (dy > 0.6 && dist < 2.5))) {
						vy = 0.42;
					}
				}
			}
			bot.setDeltaMovement(mx, vy, mz);
		}

		// heal when low
		if (st.heals > 0 && hp < 7F && st.healCd <= 0) {
			st.heals--;
			st.healCd = 50;
			run(server, "effect give " + id + " minecraft:instant_health 1 1");
		}
		if (!alive) {
			return;
		}
		humanTricks(server, bot, st, id, targetName, target, dist, fx, fz, hp, ground);

		// mace smash: falling, close enough, has fallen far enough
		if (st.combo > 0 && st.held == Weapon.MACE && fall >= 1.5 && bot.getDeltaMovement().y < -0.05
				&& dist <= d.reach + 0.5 && Math.abs(dy) < 2.6) {
			bot.swing(InteractionHand.MAIN_HAND);
			if (RND.nextFloat() < d.accuracy) {
				hurt(server, targetName, bot, Math.min(maceDamage(fall, k.density), MACE_SMASH_CAP) * d.dmgMul);
			}
			st.cd = d.cooldown + 10;
			endCombo(server, st, id);
			return;
		}

		// normal attack (jab / sword / axe / mace poke)
		if (st.combo > 0 || st.lungeTicks > 0 || st.cd > 0) {
			return;
		}
		Weapon use = st.held;
		boolean swapHit = false;
		if (style == Style.SWAP && d.swaps && st.swapLock <= 0) {
			// attribute swap: axe against a shield, sword otherwise
			Weapon want = blocking && k.axe != null ? Weapon.AXE : Weapon.SWORD;
			if (want != st.held) {
				equip(server, st, id, want);
				st.swapLock = 3;
				swapHit = true;
				use = want;
			}
		} else if (style == Style.AXE && st.held != Weapon.AXE) {
			equip(server, st, id, Weapon.AXE);
			use = Weapon.AXE;
		}
		double reachUse = use == Weapon.SPEAR ? SPEAR_REACH : d.reach;
		double minReach = use == Weapon.SPEAR ? SPEAR_MIN_REACH : 0;
		if (dist > reachUse || dist < minReach || Math.abs(dy) >= 2.2) {
			return;
		}
		int cd = swapHit ? Math.max(5, d.cooldown * 55 / 100) : d.cooldown;
		if (use == Weapon.MACE) {
			cd += 12;
		} else if (use == Weapon.SPEAR) {
			cd += 6;
		}
		st.cd = cd + RND.nextInt(3);
		bot.swing(InteractionHand.MAIN_HAND);
		if (RND.nextFloat() < d.accuracy) {
			float base = switch (use) {
				case SWORD -> k.botDamage;
				case AXE -> k.botDamage * (style == Style.SWAP ? 1.15F : 1F);
				case MACE -> MACE_BASE;
				case SPEAR -> SPEAR_JAB;
			};
			boolean crit = d.crits && !bot.onGround() && bot.getDeltaMovement().y < 0;
			float dmg = base * d.dmgMul * (crit ? 1.5F : 1F);
			if (blocking && use == Weapon.AXE) {
				dmg *= 1.7F; // an axe disables the shield: approximated as bonus damage
			}
			hurt(server, targetName, bot, dmg);
		}
	}
}
