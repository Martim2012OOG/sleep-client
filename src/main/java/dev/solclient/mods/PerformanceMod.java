package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;

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
			.tip("Draws the 3D world smaller and stretches it, while menus and text stay sharp. Auto = half on a Retina Mac (same FPS as before, sharp text), full elsewhere.");
	@SuppressWarnings("unused")
	private final Option.Action worldResStatus = action("World Resolution Now", "Refresh", () -> {
	}, () -> dev.solclient.render.WorldScale.status);

	/** How big the world is drawn compared to the screen (1 = full). */
	public static float worldScale(net.minecraft.client.MinecraftClient mc) {
		PerformanceMod m = instance;
		if (m == null || mc.getWindow() == null) {
			return 1F;
		}
		return switch (m.worldRes.idx()) {
			case 1 -> 1F;
			case 2 -> 0.75F;
			case 3 -> 0.5F;
			default -> mc.getWindow().getFramebufferWidth() >= mc.getWindow().getWidth() * 1.5F ? 0.5F : 1F; // Retina: half
		};
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
			int max = MinecraftClient.getInstance().options.getMaxFps().getValue();
			return stream > 0 ? Math.min(max, stream) : max;
		}
		PerformanceMod m = instance;
		if (m == null || !m.enabled || !m.limitUnfocused.on()) {
			return vanilla;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.isWindowFocused()) {
			// AFK in a world (nothing moved for a few minutes): fewer frames, back to normal on the first movement
			if (m.limitAfk.on() && mc.world != null && System.currentTimeMillis() - lastActive > m.afkMinutes.f() * 60_000L) {
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
		MinecraftClient mc = MinecraftClient.getInstance();
		var p = mc.player;
		saveRetina();
		crowd(mc);
		if (p == null) {
			lastActive = System.currentTimeMillis();
			return;
		}
		double mx = mc.mouse.getX(), my = mc.mouse.getY();
		boolean moved = p.getX() != lx || p.getY() != ly || p.getZ() != lz || p.getYaw() != lyaw || p.getPitch() != lpitch || mx != mxp || my != myp
				|| mc.currentScreen != lastScreen || p.handSwinging;
		if (moved) {
			lastActive = System.currentTimeMillis();
		}
		lx = p.getX();
		ly = p.getY();
		lz = p.getZ();
		lyaw = p.getYaw();
		lpitch = p.getPitch();
		mxp = mx;
		myp = my;
		lastScreen = mc.currentScreen;
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
		return m != null && m.enabled && m.crowdGuard.on() && m.tickCull.on() && MinecraftClient.getInstance().isOnThread() && cull(e);
	}

	private void crowd(MinecraftClient mc) {
		if (!crowdGuard.on() || mc.world == null || mc.player == null) {
			if (!hidden.isEmpty()) {
				hidden = new HashSet<>();
			}
			return;
		}
		if (++crowdTick % 4 != 0) {
			return;
		}
		Bucket items = new Bucket(), orbs = new Bucket(), players = new Bucket(), npcs = new Bucket();
		var net = mc.getNetworkHandler();
		Entity self = mc.player, cam = mc.getCameraEntity(), target = mc.targetedEntity;
		for (Entity e : mc.world.getEntities()) {
			if (e == self || e == cam || e == target) {
				continue;
			}
			if (e instanceof ItemEntity) {
				items.add(e.squaredDistanceTo(self), e.getId());
			} else if (e instanceof ExperienceOrbEntity) {
				orbs.add(e.squaredDistanceTo(self), e.getId());
			} else if (e instanceof PlayerEntity p) {
				if (net != null && net.getPlayerListEntry(p.getUuid()) == null) {
					npcs.add(e.squaredDistanceTo(self), e.getId()); // a server NPC (fake player, not in the tab list)
				} else {
					players.add(e.squaredDistanceTo(self), e.getId());
				}
			} else if (!(e instanceof net.minecraft.entity.mob.Monster) && !(e instanceof net.minecraft.entity.projectile.ProjectileEntity)
					&& !e.hasPassengers() && !e.hasVehicle()
					&& (e instanceof net.minecraft.entity.LivingEntity || e instanceof net.minecraft.entity.decoration.DisplayEntity)) {
				npcs.add(e.squaredDistanceTo(self), e.getId()); // armor stands / holograms / villagers / animals
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
