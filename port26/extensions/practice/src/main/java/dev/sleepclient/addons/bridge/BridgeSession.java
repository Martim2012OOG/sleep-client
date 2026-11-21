package dev.sleepclient.addons.bridge;

import dev.sleepclient.addons.ChunkWait;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bridge practice on the add-on's private world: a start platform (green), a goal platform (red) and the void in
 * between. The timer starts when you leave the start platform and stops when you stand on the goal. Falling (or
 * pressing R) sends you back; best times are kept per mode + length in config/kubo_bridge_best.properties.
 * Arena building uses vanilla commands, block counting uses plain block reads, so it depends very little on game internals.
 */
public final class BridgeSession {

	public enum Mode {
		STRAIGHT("Straight", "Horizontal, straight line (classic fast bridge).", 0, 0, 30, true, false),
		DIAGONAL("Diagonal", "Horizontal on the diagonal (45 degrees).", 22, 0, 22, true, false),
		STAIRS_UP("Stairs Up", "Bridge diagonally upwards, one block up per block forward.", 0, 12, 12, true, true),
		STAIRS_DOWN("Stairs Down", "Bridge diagonally downwards.", 0, -12, 12, true, true),
		PILLAR("Pillar", "Vertical: pillar straight up, then step onto the platform.", 5, 15, 0, false, true);

		public final String label;
		public final String description;
		final int dx, dy, dz;
		final boolean scaleH, scaleV;

		Mode(String label, String description, int dx, int dy, int dz, boolean scaleH, boolean scaleV) {
			this.label = label;
			this.description = description;
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
			this.scaleH = scaleH;
			this.scaleV = scaleV;
		}
	}

	public enum Length {
		SHORT("Short", 0.6F),
		MEDIUM("Medium", 1F),
		LONG("Long", 1.5F);

		public final String label;
		final float factor;

		Length(String label, float factor) {
			this.label = label;
			this.factor = factor;
		}
	}

	// ------------------------------------------------------------------ chosen on the screen
	public static volatile Mode mode = Mode.STRAIGHT;
	public static volatile Length length = Length.MEDIUM;

	// ------------------------------------------------------------------ live state, read by the client HUD
	public static volatile boolean active;
	/** 0 = ready on the start platform, 1 = running, 2 = finished. */
	public static volatile int state;
	public static volatile long startNano;
	public static volatile double lastTime = -1;
	public static volatile double best = -1;
	public static volatile int blocks;
	public static volatile int attempts;
	public static volatile int falls;
	public static volatile double speed;
	public static volatile boolean resetRequested;
	public static volatile String title = "";

	// ------------------------------------------------------------------ course geometry
	private static final int FLOOR = 150;
	private static Mode curMode;
	private static Length curLen;
	private static int gx, gy, gz;
	private static int minX, maxX, minZ, maxZ, minY, maxY;

	private static int phase, wait, tickCount, finishWait;
	private static ServerPlayer lastPlayer;
	private static double px, pz;
	private static boolean havePrev;

	private BridgeSession() {
	}

	// ------------------------------------------------------------------ best times
	private static final Properties PROPS = new Properties();
	private static boolean loaded;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo_bridge_best.properties");
	}

	private static void loadBest() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream in = Files.newInputStream(file())) {
			PROPS.load(in);
		} catch (IOException ignored) {
			// no file yet
		}
	}

	private static String key(Mode m, Length l) {
		return m.name() + "_" + l.name();
	}

	/** Best time in seconds for this mode + length, or -1. */
	public static double bestFor(Mode m, Length l) {
		loadBest();
		try {
			return Double.parseDouble(PROPS.getProperty(key(m, l), "-1"));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void saveBest(Mode m, Length l, double seconds) {
		loadBest();
		PROPS.setProperty(key(m, l), String.format(Locale.ROOT, "%.3f", seconds));
		try (OutputStream out = Files.newOutputStream(file())) {
			PROPS.store(out, "Sleep bridge practice best times (seconds)");
		} catch (IOException ignored) {
			// not fatal
		}
	}

	// ------------------------------------------------------------------ lifecycle
	public static void begin(MinecraftServer server) {
		curMode = mode;
		curLen = length;
		float f = curLen.factor;
		int dx = curMode.dx, dy = curMode.dy, dz = curMode.dz;
		if (curMode.scaleH) {
			dx = Math.round(dx * f);
			dz = Math.round(dz * f);
		}
		if (curMode.scaleV) {
			dy = Math.round(dy * f);
		}
		gx = dx;
		gz = dz;
		gy = FLOOR + dy;
		minX = Math.min(-2, gx - 2) - 6;
		maxX = Math.max(2, gx + 2) + 6;
		minZ = Math.min(-2, gz - 2) - 6;
		maxZ = Math.max(2, gz + 2) + 6;
		minY = Math.min(FLOOR, gy) - 8;
		maxY = Math.max(FLOOR, gy) + 8;

		title = curMode.label + " - " + curLen.label;
		best = bestFor(curMode, curLen);
		lastTime = -1;
		blocks = 0;
		attempts = 0;
		falls = 0;
		speed = 0;
		state = 0;
		startNano = 0;
		phase = 0;
		wait = 0;
		tickCount = 0;
		lastPlayer = null;
		havePrev = false;
		resetRequested = false;
		active = true;
	}

	public static void stop() {
		active = false;
		lastPlayer = null;
	}

	private static int run(MinecraftServer s, String cmd) {
		try {
			return s.getCommands().getDispatcher().execute(cmd, s.createCommandSourceStack());
		} catch (Exception e) {
			return 0;
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
			case 0 -> { // load the chunks around the course while flying in spectator
				run(server, "forceload add -16 -16 64 64");
				run(server, "gamemode spectator " + name);
				run(server, "tp " + name + " 0 " + (FLOOR + 6) + " 0");
				wait = 0;
				phase = 1;
			}
			case 1 -> {
				// build only when the chunks are really loaded (a fixed delay failed on slow devices)
				wait++;
				dev.sleepclient.addons.pvp.PracticeWorld.setStatus("Building the course...");
				if (!ChunkWait.ready(server.overworld(), -16, -16, 64, 64, wait)) {
					return;
				}
				buildWorld(server);
				setupPlayer(server, p, name);
				phase = 2;
				dev.sleepclient.addons.pvp.PracticeWorld.markReady();
			}
			default -> running(server, p, name);
		}
	}

	private static void buildWorld(MinecraftServer s) {
		String[] gamerules = {
				"doMobSpawning false", "spawn_mobs false",
				"doDaylightCycle false", "advance_time false",
				"doWeatherCycle false", "advance_weather false",
				"keepInventory true", "keep_inventory true",
				"doImmediateRespawn true", "immediate_respawn true",
				"showDeathMessages false", "show_death_messages false"
		};
		for (String g : gamerules) {
			run(s, "gamerule " + g);
		}
		run(s, "difficulty peaceful");
		run(s, "time set noon");
		run(s, "weather clear");
		run(s, "setworldspawn 0 " + (FLOOR + 1) + " 0");
		// wipe the whole working area (it may hold an older course or terrain), one layer per command
		for (int y = FLOOR - 20; y <= FLOOR + 40; y++) {
			run(s, "fill -12 " + y + " -12 60 " + y + " 60 minecraft:air");
		}
		buildPlatforms(s);
	}

	private static void buildPlatforms(MinecraftServer s) {
		run(s, "fill -2 " + FLOOR + " -2 2 " + FLOOR + " 2 minecraft:lime_concrete");
		run(s, "fill " + (gx - 2) + " " + gy + " " + (gz - 2) + " " + (gx + 2) + " " + gy + " " + (gz + 2) + " minecraft:red_concrete");
	}

	private static void giveBlocks(MinecraftServer s, String name) {
		run(s, "item replace entity " + name + " hotbar.0 with minecraft:white_concrete 64");
		run(s, "item replace entity " + name + " hotbar.1 with minecraft:white_concrete 64");
	}

	private static void setupPlayer(MinecraftServer s, ServerPlayer p, String name) {
		lastPlayer = p;
		run(s, "gamemode survival " + name);
		run(s, "clear " + name);
		run(s, "effect clear " + name);
		run(s, "effect give " + name + " minecraft:resistance infinite 4 true");
		run(s, "effect give " + name + " minecraft:saturation infinite 0 true");
		giveBlocks(s, name);
		teleportToStart(s, name);
		state = 0;
		startNano = 0;
		blocks = 0;
		havePrev = false;
	}

	private static void teleportToStart(MinecraftServer s, String name) {
		float yaw = (float) (Math.atan2(gz, gx) * 57.29577951308232D) - 90F;
		run(s, "tp " + name + " 0.5 " + (FLOOR + 1) + " 0.5 " + String.format(Locale.ROOT, "%.1f", yaw) + " 25");
	}

	/** Back to the start: removes the blocks you placed, rebuilds the platforms, refills blocks, timer ready again. */
	private static void resetCourse(MinecraftServer s, ServerPlayer p, String name) {
		scan(s.overworld(), true);
		buildPlatforms(s);
		giveBlocks(s, name);
		teleportToStart(s, name);
		run(s, "effect give " + name + " minecraft:resistance infinite 4 true");
		state = 0;
		startNano = 0;
		blocks = 0;
		speed = 0;
		havePrev = false;
	}

	private static boolean inPlatform(int x, int y, int z) {
		if (y == FLOOR && Math.abs(x) <= 2 && Math.abs(z) <= 2) {
			return true;
		}
		return y == gy && Math.abs(x - gx) <= 2 && Math.abs(z - gz) <= 2;
	}

	/** Counts the blocks placed outside the two platforms (and removes them when {@code clear}). */
	private static int scan(ServerLevel w, boolean clear) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					if (inPlatform(x, y, z)) {
						continue;
					}
					pos.set(x, y, z);
					BlockState st = w.getBlockState(pos);
					if (!st.isAir()) {
						n++;
						if (clear) {
							w.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		return n;
	}

	private static void running(MinecraftServer server, ServerPlayer p, String name) {
		ServerLevel world = server.overworld();
		tickCount++;

		if (p != lastPlayer) { // respawned / re-created
			setupPlayer(server, p, name);
		}

		double x = p.getX(), y = p.getY(), z = p.getZ();
		if (havePrev) {
			speed = Math.hypot(x - px, z - pz) * 20.0;
		}
		px = x;
		pz = z;
		havePrev = true;

		// manual reset (R key on the client)
		if (resetRequested) {
			resetRequested = false;
			resetCourse(server, p, name);
			return;
		}

		// finished: show the result for a moment, then back to the start
		if (state == 2) {
			if (--finishWait <= 0) {
				resetCourse(server, p, name);
			}
			return;
		}

		// keep the block stacks topped up
		if (tickCount % 10 == 0) {
			if (p.getInventory().getItem(0).getCount() < 24 || p.getInventory().getItem(1).getCount() < 24) {
				giveBlocks(server, name);
			}
		}

		// fell into the void
		double lowest = Math.min(FLOOR, gy) - 8;
		if (y < lowest) {
			if (state == 1) {
				falls++;
			}
			resetCourse(server, p, name);
			return;
		}

		boolean outsideStart = x < -2.0 || x > 3.0 || z < -2.0 || z > 3.0;
		boolean climbed = p.onGround() && y > FLOOR + 1.9;

		if (state == 0) {
			if (outsideStart || climbed) {
				state = 1;
				attempts++;
				startNano = System.nanoTime();
			}
			return;
		}

		// running
		if (tickCount % 5 == 0) {
			blocks = scan(world, false);
		}
		boolean onGoal = p.onGround() && Math.abs(x - (gx + 0.5)) <= 2.5 && Math.abs(z - (gz + 0.5)) <= 2.5 && Math.abs(y - (gy + 1)) < 0.6;
		if (onGoal) {
			double seconds = (System.nanoTime() - startNano) / 1.0E9;
			blocks = scan(world, false);
			lastTime = seconds;
			state = 2;
			finishWait = 40;
			boolean pb = best < 0 || seconds < best;
			if (pb) {
				best = seconds;
				saveBest(curMode, curLen, seconds);
			}
			p.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Finished %s: %.3f s  |  %d blocks%s", title, seconds, blocks, pb ? "  |  NEW BEST!" : "")));
		}
	}
}
