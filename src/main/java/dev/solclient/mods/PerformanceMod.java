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

	// ------------------------------------------------------------------ Crowd Guard
	/** Ids of the entities that are NOT drawn right now (rebuilt 5 times a second; new entities are drawn until then). */
	private static volatile Set<Integer> hidden = new HashSet<>();
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
		Bucket items = new Bucket(), orbs = new Bucket(), players = new Bucket();
		Entity self = mc.player, cam = mc.getCameraEntity(), target = mc.targetedEntity;
		for (Entity e : mc.world.getEntities()) {
			if (e == self || e == cam || e == target) {
				continue;
			}
			if (e instanceof ItemEntity) {
				items.add(e.squaredDistanceTo(self), e.getId());
			} else if (e instanceof ExperienceOrbEntity) {
				orbs.add(e.squaredDistanceTo(self), e.getId());
			} else if (e instanceof PlayerEntity) {
				players.add(e.squaredDistanceTo(self), e.getId());
			}
		}
		Set<Integer> out = new HashSet<>();
		double id = itemDistance.f(), pd = playerDistance.f();
		pick(items, Math.round(maxItems.f()), id * id, 4, out);
		pick(orbs, Math.round(maxXp.f()), id * id, 4, out);
		pick(players, Math.round(maxPlayers.f()), pd * pd, 64, out);
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
