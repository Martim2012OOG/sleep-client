package dev.solclient.addons.pvp;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * Arenas of the PvP Practice world. Every arena is built with vanilla /fill commands in the sky (floor at y-1,
 * players stand at y), inside invisible barrier walls and a barrier ceiling so nobody falls out or flies away.
 * To add an arena: add an {@code arena(...)} call in the static block.
 */
public final class PracticeArena {

	/** Half-size / height cleared before every build, so nothing of a previous (bigger) arena is left behind. */
	private static final int MAX_R = 31;
	private static final int MAX_H = 50;
	/** Rows cleared per /fill: (2*MAX_R+1)^2 * 8 = 31752 blocks, under the 32768 limit of one fill command. */
	private static final int CLEAR_ROWS = 8;

	@FunctionalInterface
	private interface Builder {
		void build(MinecraftServer s, int y);
	}

	public static final List<PracticeArena> ALL = new ArrayList<>();

	public final String name;
	public final String description;
	/** Half-size of the floor. */
	public final int radius;
	/** Free height above the floor (ceiling is a barrier at y + height). */
	public final int height;
	/** Distance from the centre at which the player (-z) and the bots (+z) start. */
	public final int spawnZ;
	private final Builder builder;

	private PracticeArena(String name, String description, int radius, int height, int spawnZ, Builder builder) {
		this.name = name;
		this.description = description;
		this.radius = radius;
		this.height = height;
		this.spawnZ = spawnZ;
		this.builder = builder;
	}

	private static void arena(String name, String desc, int radius, int height, int spawnZ, Builder b) {
		ALL.add(new PracticeArena(name, desc, radius, height, spawnZ, b));
	}

	// ------------------------------------------------------------------ build
	public void build(MinecraftServer s) {
		int y = PracticeSession.ARENA_Y;
		// clear in slabs (a fill command is limited to 32768 blocks)
		for (int y0 = y - 6; y0 <= y + MAX_H; y0 += CLEAR_ROWS) {
			fill(s, -MAX_R, y0, -MAX_R, MAX_R, Math.min(y0 + CLEAR_ROWS - 1, y + MAX_H), MAX_R, "minecraft:air");
		}
		// invisible shell: four walls + ceiling
		int w = radius + 1, top = y + height;
		fill(s, -w, y, -w, w, top, -w, "minecraft:barrier");
		fill(s, -w, y, w, w, top, w, "minecraft:barrier");
		fill(s, -w, y, -w, -w, top, w, "minecraft:barrier");
		fill(s, w, y, -w, w, top, w, "minecraft:barrier");
		fill(s, -w, top, -w, w, top, w, "minecraft:barrier");
		builder.build(s, y);
	}

	private static void fill(MinecraftServer s, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		PracticeSession.run(s, "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block);
	}

	private static void floor(MinecraftServer s, int y, int r, String floor, String border) {
		fill(s, -r, y - 1, -r, r, y - 1, r, floor);
		fill(s, -r, y - 1, -r, r, y - 1, -r, border);
		fill(s, -r, y - 1, r, r, y - 1, r, border);
		fill(s, -r, y - 1, -r, -r, y - 1, r, border);
		fill(s, r, y - 1, -r, r, y - 1, r, border);
	}

	/** Concentric bands (3 blocks wide, alternating a / b) painted over the floor, from the border inwards. */
	private static void rings(MinecraftServer s, int y, int r, String a, String b) {
		String[] c = {a, b};
		int n = 0;
		for (int i = 3; r - i >= 4; i += 3, n++) {
			fill(s, -(r - i), y - 1, -(r - i), r - i, y - 1, r - i, c[n % 2]);
		}
	}

	/** Floating-island look: the underside shrinks layer by layer below the floor (stays above the cleared depth). */
	private static void underside(MinecraftServer s, int y, int r, String block) {
		fill(s, -(r - 1), y - 2, -(r - 1), r - 1, y - 2, r - 1, block);
		fill(s, -(r - 3), y - 3, -(r - 3), r - 3, y - 3, r - 3, block);
		fill(s, -(r - 6), y - 4, -(r - 6), r - 6, y - 4, r - 6, block);
		fill(s, -(r - 10), y - 5, -(r - 10), r - 10, y - 5, r - 10, block);
	}

	/**
	 * Visible low wall around the floor edge, tall posts with a lamp on top at the four corners and shorter ones in the
	 * middle of every side. Purely decoration (the invisible barrier is what keeps people in).
	 */
	private static void rim(MinecraftServer s, int y, int r, String wall, String post, String lamp) {
		fill(s, -r, y, -r, r, y, -r, wall);
		fill(s, -r, y, r, r, y, r, wall);
		fill(s, -r, y, -r, -r, y, r, wall);
		fill(s, r, y, -r, r, y, r, wall);
		for (int sx : new int[] {-r, r}) {
			for (int sz : new int[] {-r, r}) {
				fill(s, sx, y, sz, sx, y + 4, sz, post);
				fill(s, sx, y + 5, sz, sx, y + 5, sz, lamp);
			}
		}
		for (int[] m : new int[][] {{0, -r}, {0, r}, {-r, 0}, {r, 0}}) {
			fill(s, m[0], y, m[1], m[0], y + 2, m[1], post);
			fill(s, m[0], y + 3, m[1], m[0], y + 3, m[1], lamp);
		}
	}

	/** 2x2 pillar whose lowest corner is (x, z). */
	private static void pillar(MinecraftServer s, int y, int x, int z, int h, String block) {
		fill(s, x, y, z, x + 1, y + h - 1, z + 1, block);
	}

	/** 2x2 pillar with a cap block on top (and a lamp above the cap when {@code lamp} is not null). */
	private static void pillar(MinecraftServer s, int y, int x, int z, int h, String block, String cap, String lamp) {
		pillar(s, y, x, z, h, block);
		fill(s, x, y + h, z, x + 1, y + h, z + 1, cap);
		if (lamp != null) {
			fill(s, x, y + h + 1, z, x + 1, y + h + 1, z + 1, lamp);
		}
	}

	// ------------------------------------------------------------------ arenas
	// Every radius is <= 30 so the arena + its barrier shell stay inside the 4x4 chunks that PracticeSession loads
	// (-32..31). Obstacles never touch the spawn points (x -6..6 on z = -spawnZ for you, z = +spawnZ for the bots).
	static {
		arena("Classic", "Big open quartz arena with a glowing rim. Nothing in the way.", 28, 16, 20, (s, y) -> {
			floor(s, y, 28, "minecraft:polished_andesite", "minecraft:sea_lantern");
			rings(s, y, 28, "minecraft:smooth_quartz", "minecraft:polished_andesite");
			underside(s, y, 28, "minecraft:smooth_stone");
			rim(s, y, 28, "minecraft:stone_brick_wall", "minecraft:quartz_pillar", "minecraft:sea_lantern");
		});

		arena("Pillars", "Stone pillars to hide behind and climb for the high ground.", 30, 28, 20, (s, y) -> {
			floor(s, y, 30, "minecraft:smooth_stone", "minecraft:sea_lantern");
			rings(s, y, 30, "minecraft:stone", "minecraft:smooth_stone");
			underside(s, y, 30, "minecraft:mossy_stone_bricks");
			rim(s, y, 30, "minecraft:stone_brick_wall", "minecraft:chiseled_stone_bricks", "minecraft:sea_lantern");
			String b = "minecraft:stone_bricks", cap = "minecraft:chiseled_stone_bricks", lamp = "minecraft:lantern";
			pillar(s, y, -14, -11, 8, b, cap, lamp);
			pillar(s, y, 13, -11, 8, b, cap, lamp);
			pillar(s, y, -14, 10, 8, b, cap, lamp);
			pillar(s, y, 13, 10, 8, b, cap, lamp);
			pillar(s, y, 0, 0, 14, b, cap, lamp);
			pillar(s, y, -24, 0, 5, b, cap, lamp);
			pillar(s, y, 23, 0, 5, b, cap, lamp);
		});

		arena("Mace Tower", "Tall room with a big central tower. Room to launch and smash.", 22, 48, 14, (s, y) -> {
			floor(s, y, 22, "minecraft:polished_blackstone", "minecraft:crying_obsidian");
			rings(s, y, 22, "minecraft:blackstone", "minecraft:polished_blackstone");
			underside(s, y, 22, "minecraft:blackstone");
			rim(s, y, 22, "minecraft:blackstone_wall", "minecraft:polished_blackstone_bricks", "minecraft:ochre_froglight");
			// stepped base + tower + gold crown
			fill(s, -4, y, -4, 4, y, 4, "minecraft:polished_blackstone_bricks");
			fill(s, -2, y + 1, -2, 2, y + 1, 2, "minecraft:polished_blackstone_bricks");
			fill(s, -1, y + 2, -1, 1, y + 31, 1, "minecraft:polished_blackstone_bricks");
			fill(s, -3, y + 32, -3, 3, y + 32, 3, "minecraft:gilded_blackstone");
			fill(s, -3, y + 33, -3, 3, y + 33, 3, "minecraft:chiseled_polished_blackstone");
			fill(s, -2, y + 33, -2, 2, y + 33, 2, "minecraft:gilded_blackstone");
			// low pillars at the sides to launch from
			pillar(s, y, -14, 0, 6, "minecraft:polished_blackstone_bricks", "minecraft:gilded_blackstone", null);
			pillar(s, y, 13, 0, 6, "minecraft:polished_blackstone_bricks", "minecraft:gilded_blackstone", null);
		});

		arena("Cage", "Small cage. No room to run, pure close combat.", 10, 16, 6, (s, y) -> {
			floor(s, y, 10, "minecraft:polished_blackstone_bricks", "minecraft:crying_obsidian");
			rings(s, y, 10, "minecraft:blackstone", "minecraft:polished_blackstone_bricks");
			underside(s, y, 10, "minecraft:deepslate_bricks");
			String bars = "minecraft:iron_bars";
			fill(s, -11, y, -11, 11, y + 4, -11, bars);
			fill(s, -11, y, 11, 11, y + 4, 11, bars);
			fill(s, -11, y, -11, -11, y + 4, 11, bars);
			fill(s, 11, y, -11, 11, y + 4, 11, bars);
			// stone corner posts with a lamp so the cage is not just bars
			for (int sx : new int[] {-11, 11}) {
				for (int sz : new int[] {-11, 11}) {
					fill(s, sx, y, sz, sx, y + 6, sz, "minecraft:polished_blackstone_bricks");
					fill(s, sx, y + 7, sz, sx, y + 7, sz, "minecraft:sea_lantern");
				}
			}
		});

		arena("Colosseum", "Stepped centre platform and four corner towers.", 30, 24, 22, (s, y) -> {
			floor(s, y, 30, "minecraft:deepslate_tiles", "minecraft:glowstone");
			rings(s, y, 30, "minecraft:polished_deepslate", "minecraft:deepslate_tiles");
			underside(s, y, 30, "minecraft:cobbled_deepslate");
			rim(s, y, 30, "minecraft:deepslate_brick_wall", "minecraft:chiseled_deepslate", "minecraft:glowstone");
			fill(s, -9, y, -9, 9, y, 9, "minecraft:polished_deepslate");
			fill(s, -6, y + 1, -6, 6, y + 1, 6, "minecraft:deepslate_bricks");
			fill(s, -3, y + 2, -3, 3, y + 2, 3, "minecraft:chiseled_deepslate");
			String b = "minecraft:deepslate_bricks";
			pillar(s, y, -24, -24, 9, b, "minecraft:chiseled_deepslate", "minecraft:soul_lantern");
			pillar(s, y, 23, -24, 9, b, "minecraft:chiseled_deepslate", "minecraft:soul_lantern");
			pillar(s, y, -24, 23, 9, b, "minecraft:chiseled_deepslate", "minecraft:soul_lantern");
			pillar(s, y, 23, 23, 9, b, "minecraft:chiseled_deepslate", "minecraft:soul_lantern");
		});

		arena("Nether Ruins", "Low broken walls and ruined pillars to fight around.", 28, 20, 20, (s, y) -> {
			floor(s, y, 28, "minecraft:blackstone", "minecraft:shroomlight");
			rings(s, y, 28, "minecraft:polished_blackstone", "minecraft:blackstone");
			underside(s, y, 28, "minecraft:netherrack");
			rim(s, y, 28, "minecraft:nether_brick_wall", "minecraft:chiseled_nether_bricks", "minecraft:shroomlight");
			String b = "minecraft:nether_bricks";
			fill(s, -17, y, -8, -8, y, -8, b);
			fill(s, 8, y, 8, 17, y, 8, b);
			fill(s, -17, y, 10, -17, y, 17, b);
			fill(s, 17, y, -17, 17, y, -10, b);
			fill(s, -8, y, 0, -8, y, 4, b);
			fill(s, 8, y, -4, 8, y, 0, b);
			String c = "minecraft:cracked_nether_bricks";
			pillar(s, y, -12, -1, 3, c);
			pillar(s, y, 11, -1, 3, c);
			pillar(s, y, -21, -16, 5, c);
			pillar(s, y, 20, 15, 5, c);
			pillar(s, y, -21, 15, 4, c);
			pillar(s, y, 20, -16, 4, c);
		});
	}
}
