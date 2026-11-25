package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import java.util.Arrays;

/**
 * Light performance helpers (on by default, nothing visual changes while you play):
 * - lower FPS while the game window is in the background (saves CPU / GPU / battery, the game stays cool);
 * - 3D cosmetics of far-away players are skipped (you can't see them anyway);
 * - Crowd Guard: with hundreds of players or thousands of dropped items / XP orbs only the nearest ones are DRAWN
 *   (the rest stay in the world, nothing is deleted and nothing is sent to the server). Rendering only.
 */
public class PerformanceMod extends Mod {

	public static PerformanceMod instance;

	public final Option.Bool limitUnfocused = bool("Lower FPS In Background", true);
	public final Option.Num unfocusedFps = num("Background FPS", 15, 5, 60, 5, "");
	public final Option.Bool limitAfk = bool("Lower FPS When Idle", false);
	public final Option.Num afkMinutes = num("AFK After", 5, 1, 30, 1, " min");
	public final Option.Num afkFps = num("AFK FPS", 30, 5, 60, 5, "");
	public final Option.Num cosmeticDistance = num("3D Cosmetics Distance", 32, 8, 96, 4, " blocks");

	public final Option.Bool crowdGuard = bool("Crowd Guard (crowded servers / many items)", true);
	public final Option.Bool tickCull = bool("Skip Far Items In The Game Tick (big FPS gain)", true).tip("Items far away are not moved on your computer: thousands of dropped items no longer slow the game down.");
	public final Option.Num maxItems = num("Max Dropped Items Drawn", 48, 8, 512, 8, "");
	public final Option.Num itemDistance = num("Dropped Items Distance", 24, 8, 64, 4, " blocks");
	public final Option.Num maxXp = num("Max XP Orbs Drawn", 24, 4, 256, 4, "");
	public final Option.Num maxPlayers = num("Max Players Drawn", 32, 8, 128, 4, "");
	public final Option.Num playerDistance = num("Players Draw Distance", 64, 24, 160, 8, " blocks");
	/** Mac: full Retina resolution (sharp, about 4x slower to draw). Read from a file at start, before the window exists. */
	// (new name and file: the old "fast" choice drew everything, text included, at half resolution = pixelated text; everyone starts sharp again)
	/** Auto = on a Retina Mac the world is drawn at half size (old fast-mode FPS) while menus and text stay sharp. */
	public final Option.Choice worldRes = choice("World Resolution", 0, "Auto", "100%", "75%", "50%")
			.tip("Draws the 3D world smaller and stretches it, while menus and text stay sharp. Auto = half on a Retina Mac, and full sharpness while you look at signs, holograms or maps; full elsewhere.");
	@SuppressWarnings("unused")
	private final Option.Action worldResStatus = action("World Resolution Now", "Refresh", () -> {
	}, () -> dev.solclient.render.WorldScale.status);

	/** How big the world is drawn compared to the screen (1 = full). */
	public static float worldScale(net.minecraft.client.Minecraft mc) {
		PerformanceMod m = instance;
		if (m == null || mc.getWindow() == null) {
			return 1F;
		}
		return switch (m.worldRes.idx()) {
			case 1 -> 1F;
			case 2 -> 0.75F;
			case 3 -> 0.5F;
			default -> mc.getWindow().getWidth() >= mc.getWindow().getScreenWidth() * 1.5F && !reading(mc) ? 0.5F : 1F; // Retina: half, full when reading
		};
	}

	private static long readCheckAt, readingUntil;

	/** True while you're looking at something with words on it nearby: signs, holograms, maps. Auto then draws the world at full sharpness. */
	private static boolean reading(net.minecraft.client.Minecraft mc) {
		long now = System.currentTimeMillis();
		if (now >= readCheckAt) {
			readCheckAt = now + 200L;
			try {
				if (lookingAtText(mc)) {
					readingUntil = now + 1500L; // stays sharp a moment, so it doesn't flicker
				}
			} catch (RuntimeException ignored) {
				// only a sharpness hint
			}
		}
		return now < readingUntil;
	}

	private static final double READ_RANGE = 40, READ_CONE = 0.88; // about 28 degrees around where you look

	private static boolean inView(net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 look, double x, double y, double z) {
		net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(x - eye.x, y - eye.y, z - eye.z);
		double len = d.length();
		return len < READ_RANGE && len > 0.01 && d.dot(look) / len > READ_CONE;
	}

	private static boolean lookingAtText(net.minecraft.client.Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return false;
		}
		net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
		int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4, r = 3;
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				if (!mc.level.getChunkSource().hasChunk(x, z)) {
					continue;
				}
				for (var be : mc.level.getChunk(x, z).getBlockEntities().values()) {
					if (be instanceof net.minecraft.world.level.block.entity.SignBlockEntity || be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity) {
						var p = be.getBlockPos();
						if (inView(eye, look, p.getX() + .5, p.getY() + .5, p.getZ() + .5)) {
							return true;
						}
					}
				}
			}
		}
		for (var e : mc.level.entitiesForRendering()) {
			if ((e instanceof net.minecraft.world.entity.Display.TextDisplay || e instanceof net.minecraft.world.entity.decoration.ItemFrame)
					&& inView(eye, look, e.getX(), e.getY(), e.getZ())) {
				return true;
			}
		}
		return false;
	}

	public final Option.Bool sharpRetina = bool("Mac: Sharp Text and Menus (Retina)", true)
			.tip("On = sharp text and menus. Off = the whole game, text included, is drawn at half resolution: more FPS on a Mac, but blurry / pixelated text. Restart the game after changing it.");
	public final Option.Bool crowdMode = bool("Crowd Mode (lobbies full of NPCs)", true)
			.tip("When many NPCs, holograms or animals are around you, only the nearest are drawn. Real players and monsters are never hidden.");
	public final Option.Num crowdAbove = num("Crowd Mode Starts With", 25, 10, 120, 5, " NPCs");
	public final Option.Num crowdMax = num("Max NPCs Drawn In A Crowd", 30, 8, 150, 2, "");
	public final Option.Num crowdDistance = num("NPCs Distance In A Crowd", 20, 8, 64, 2, " blocks");

	public final Option.Action maxFps = action("Max FPS Mode", "Apply", () -> dev.solclient.gui.Presets.performance(0),
			() -> "Uncapped FPS, no vsync, no clouds, less particles / shadows / distance");
	public final Option.Action prettyMode = action("Best Looks Mode (smooth, sharp far away)", "Apply", () -> {
		try {
			var o = net.minecraft.client.Minecraft.getInstance().options;
			o.ambientOcclusion().set(true); // smooth lighting: soft shadows in corners
			o.mipmapLevels().set(4); // far textures stay smooth instead of pixelated / shimmering
			o.biomeBlendRadius().set(3);
			o.entityShadows().set(true);
			o.save();
			dev.solclient.compat.Mc.allChanged(net.minecraft.client.Minecraft.getInstance());
		} catch (Throwable t) {
			System.err.println("[Caos] best looks mode: " + t);
		}
	}, () -> "Smooth lighting, 4 mipmap levels, biome blending and entity shadows (costs very little with Sodium). Does not change render distance.");

	public PerformanceMod() {
		super("Performance", "performance", "Saves resources: lower FPS in the background and skips far-away cosmetics.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	/** Called by the vanilla FPS limiter: our cap while the window is not focused. */
	public static int limit(int vanilla) {
		// streaming (TikTok Live): the game never slows down in the background, optionally locked to a steady FPS
		int stream = dev.solclient.ext.KuboApi.streamFpsCap();
		if (stream >= 0) {
			int max = Minecraft.getInstance().options.framerateLimit().get();
			return stream > 0 ? Math.min(max, stream) : max;
		}
		PerformanceMod m = instance;
		if (m == null || !m.enabled || !m.limitUnfocused.on()) {
			return vanilla;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.isWindowActive()) {
			// AFK in a world (nothing moved for a few minutes): fewer frames, back to normal on the first movement
			if (m.limitAfk.on() && mc.level != null && System.currentTimeMillis() - lastActive > m.afkMinutes.f() * 60_000L) {
				return Math.min(vanilla, Math.max(5, Math.round(m.afkFps.f())));
			}
			return vanilla;
		}
		return Math.min(vanilla, Math.max(5, Math.round(m.unfocusedFps.f())));
	}

	private static volatile long lastActive = System.currentTimeMillis();
	private double lx, ly, lz, mxp, myp;
	private float lyaw, lpitch;
	private Object lastScreen;

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		saveRetina();
		crowd(mc);
		if (p == null) {
			lastActive = System.currentTimeMillis();
			return;
		}
		double mx = mc.mouseHandler.xpos(), my = mc.mouseHandler.ypos();
		boolean moved = p.getX() != lx || p.getY() != ly || p.getZ() != lz || p.getYRot() != lyaw || p.getXRot() != lpitch || mx != mxp || my != myp
				|| dev.solclient.compat.Mc.screen(mc) != lastScreen || p.swinging;
		if (moved) {
			lastActive = System.currentTimeMillis();
		}
		lx = p.getX();
		ly = p.getY();
		lz = p.getZ();
		lyaw = p.getYRot();
		lpitch = p.getXRot();
		mxp = mx;
		myp = my;
		lastScreen = dev.solclient.compat.Mc.screen(mc);
	}

	/** Max squared distance (blocks²) for drawing 3D cosmetics. */
	public static double cosmeticDistanceSq() {
		PerformanceMod m = instance;
		double d = m == null || !m.enabled ? 128 : m.cosmeticDistance.f();
		return d * d;
	}

	private static java.nio.file.Path retinaFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-retina-v2.txt");
	}

	/** Read at start-up (before the settings are loaded): true = keep the full Retina resolution. */
	public static boolean sharpRetinaSaved() {
		try {
			java.nio.file.Path f = retinaFile();
			// sharp unless you chose the fast (lower resolution) mode
			return !java.nio.file.Files.exists(f) || !java.nio.file.Files.readString(f).trim().equals("fast");
		} catch (Exception e) {
			return true;
		}
	}

	private Boolean lastSharp;

	/** Keeps the file in step with the option (checked every tick, written only when it changes). */
	private void saveRetina() {
		boolean on = sharpRetina.on();
		if (lastSharp == null || lastSharp != on) {
			if (lastSharp != null || sharpRetinaSaved() != on) {
				try {
					java.nio.file.Files.writeString(retinaFile(), on ? "sharp" : "fast");
				} catch (Exception ignored) {
				}
			}
			lastSharp = on;
		}
	}

	// ------------------------------------------------------------------ Crowd Guard
	/** Ids of the entities that are NOT drawn right now (rebuilt 5 times a second; new entities are drawn until then). */
	private static volatile Set<Integer> hidden = new HashSet<>();
	/** True while Crowd Mode is thinning out the NPCs around you. */
	public static volatile boolean crowded;
	private int crowdTick;

	private static final class Bucket {
		double[] d = new double[256];
		int[] id = new int[256];
		int n;

		void add(double dist, int entityId) {
			if (n == d.length) {
				d = Arrays.copyOf(d, n * 2);
				id = Arrays.copyOf(id, n * 2);
			}
			d[n] = dist;
			id[n++] = entityId;
		}
	}

	/** Render thread: true when this entity should not be drawn this frame (too many near you / too far). */
	public static boolean cull(Entity e) {
		PerformanceMod m = instance;
		return m != null && m.enabled && m.crowdGuard.on() && e != null && !hidden.isEmpty() && hidden.contains(e.getId());
	}

	/** Client tick of a dropped item: skipped for the hidden ones. Never on the server thread (singleplayer), so the world is untouched. */
	public static boolean cullTick(Entity e) {
		PerformanceMod m = instance;
		return m != null && m.enabled && m.crowdGuard.on() && m.tickCull.on() && Minecraft.getInstance().isSameThread() && cull(e);
	}

	private void crowd(Minecraft mc) {
		if (!crowdGuard.on() || mc.level == null || mc.player == null) {
			if (!hidden.isEmpty()) {
				hidden = new HashSet<>();
			}
			return;
		}
		if (++crowdTick % 4 != 0) {
			return;
		}
		Bucket items = new Bucket(), orbs = new Bucket(), players = new Bucket(), npcs = new Bucket();
		var net = mc.getConnection();
		Entity self = mc.player, cam = mc.getCameraEntity(), target = mc.crosshairPickEntity;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == self || e == cam || e == target) {
				continue;
			}
			if (e instanceof ItemEntity) {
				items.add(e.distanceToSqr(self), e.getId());
			} else if (e instanceof ExperienceOrb) {
				orbs.add(e.distanceToSqr(self), e.getId());
			} else if (e instanceof Player p) {
				if (net != null && net.getPlayerInfo(p.getUUID()) == null) {
					npcs.add(e.distanceToSqr(self), e.getId()); // a server NPC (fake player, not in the tab list)
				} else {
					players.add(e.distanceToSqr(self), e.getId());
				}
			} else if (!(e instanceof net.minecraft.world.entity.monster.Enemy) && !(e instanceof net.minecraft.world.entity.projectile.Projectile)
					&& !e.isVehicle() && !e.isPassenger()
					&& (e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.Display)) {
				npcs.add(e.distanceToSqr(self), e.getId()); // armor stands / holograms / villagers / animals
			}
		}
		Set<Integer> out = new HashSet<>();
		double id = itemDistance.f(), pd = playerDistance.f();
		pick(items, Math.round(maxItems.f()), id * id, 4, out);
		pick(orbs, Math.round(maxXp.f()), id * id, 4, out);
		pick(players, Math.round(maxPlayers.f()), pd * pd, 64, out);
		// Crowd Mode: a lobby full of NPCs, holograms and animals only draws the nearest ones
		if (crowdMode.on()) {
			double near = 48 * 48;
			int around = 0;
			for (int i = 0; i < npcs.n; i++) {
				if (npcs.d[i] < near) {
					around++;
				}
			}
			crowded = around >= Math.round(crowdAbove.f());
			if (crowded) {
				double cd = crowdDistance.f();
				pick(npcs, Math.round(crowdMax.f()), cd * cd, 36, out);
			}
		} else {
			crowded = false;
		}
		hidden = out;
	}

	/** Hides everything farther than maxSq, and everything beyond the nearest {@code max} (anything closer than alwaysSq stays). */
	private static void pick(Bucket b, int max, double maxSq, double alwaysSq, Set<Integer> out) {
		if (b.n == 0) {
			return;
		}
		double kth = Double.MAX_VALUE;
		if (b.n > max) {
			double[] c = Arrays.copyOf(b.d, b.n);
			Arrays.sort(c);
			kth = c[Math.max(0, max - 1)];
		}
		for (int i = 0; i < b.n; i++) {
			if (b.d[i] > alwaysSq && (b.d[i] > kth || b.d[i] > maxSq)) {
				out.add(b.id[i]);
			}
		}
	}
}
