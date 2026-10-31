package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Water interaction, all with vanilla particles (client side only):
 * boats leave a V-shaped foam wake, waterfalls get foam + mist where they hit the water, and
 * the player / mobs / items / boats draw ripples when they move on the surface or fall in.
 */
public class WaterEffectsMod extends Mod {

	public static WaterEffectsMod instance;

	private final Option.Choice foam = choice("Foam style", 0, "White foam", "Splash drops", "Bubbles", "Mixed");
	private final Option.Num range = num("Range", 28, 8, 64, 1, " blocks");

	private final Option.Bool boats = bool("Boat wake", true);
	private final Option.Num boatLength = num("Wake length", 6, 2, 14, 1, "");
	private final Option.Num boatWidth = num("Wake width", 60, 20, 100, 5, "%");

	private final Option.Bool falls = bool("Waterfall foam", true);
	private final Option.Num fallFoam = num("Foam amount", 5, 1, 12, 1, "");
	private final Option.Bool fallMist = bool("Waterfall mist", true);

	private final Option.Bool ripples = bool("Ripples (entities)", true);
	private final Option.Bool playerWake = bool("Player wake", true);
	private final Option.Bool entrySplash = bool("Big splash on water entry", true);

	private final Random rnd = new Random();
	private final Map<EntityType<?>, Boolean> boatTypes = new HashMap<>();
	private final Map<Integer, Boolean> wasInWater = new HashMap<>();
	private int tickCount;

	public WaterEffectsMod() {
		super("Water Effects", "water_effects", "Boat wakes, waterfall foam, ripples and splashes.", Category.VISUAL);
		instance = this;
	}

	@Override
	public void tick() {
		var p = mc.player;
		var w = mc.world;
		if (p == null || w == null) {
			return;
		}
		tickCount++;
		double r = range.f();
		double r2 = r * r;

		if (boats.on() || ripples.on() || playerWake.on() || entrySplash.on()) {
			java.util.HashSet<Integer> seen = new java.util.HashSet<>();
			for (Entity e : w.getEntities()) {
				if (e == null || e.squaredDistanceTo(p) > r2) {
					continue;
				}
				seen.add(e.getId());
				entity(e, e == p);
			}
			// forget entities that left the range / the world (so the map does not grow forever)
			if (tickCount % 100 == 0) {
				wasInWater.keySet().retainAll(seen);
			}
		}

		if (falls.on()) {
			waterfalls(p.getX(), p.getY(), p.getZ(), r);
		}
	}

	// ------------------------------------------------------------------ entities
	private boolean isBoat(Entity e) {
		return boatTypes.computeIfAbsent(e.getType(), t -> {
			String id = Registries.ENTITY_TYPE.getId(t).getPath();
			return id.contains("boat") || id.contains("raft");
		});
	}

	private ParticleEffect foamParticle() {
		return switch (foam.idx()) {
			case 1 -> ParticleTypes.SPLASH;
			case 2 -> ParticleTypes.BUBBLE_POP;
			case 3 -> rnd.nextInt(3) == 0 ? ParticleTypes.SPLASH : rnd.nextBoolean() ? ParticleTypes.CLOUD : ParticleTypes.BUBBLE_POP;
			default -> ParticleTypes.CLOUD;
		};
	}

	private void entity(Entity e, boolean self) {
		boolean inWater = e.isTouchingWater();
		Vec3d v = e.getVelocity();
		double hs = v.horizontalLengthSquared();
		boolean was = wasInWater.getOrDefault(e.getId(), inWater);
		wasInWater.put(e.getId(), inWater);

		// 1) big splash when something falls into the water
		if (entrySplash.on() && inWater && !was) {
			double power = Math.min(1.0, Math.abs(v.y) * 1.8 + Math.sqrt(hs));
			if (power > 0.2) {
				int n = (int) (6 + power * 18);
				double y = e.getY() + 0.1;
				Fx.burst(ParticleTypes.SPLASH, e.getX(), y, e.getZ(), n, 0.5 + power * 0.5);
				Fx.ring(ParticleTypes.FISHING, e.getX(), y, e.getZ(), 0.4, 8, 0.05);
				Fx.ring(ParticleTypes.FISHING, e.getX(), y, e.getZ(), 0.9, 12, 0.08);
			}
		}

		if (!inWater || hs < 0.0009) {
			return;
		}
		double speed = Math.sqrt(hs);
		double dx = v.x / speed, dz = v.z / speed; // direction of travel
		double px = -dz, pz = dx; // perpendicular

		// 2) boats: V-shaped foam wake
		if (boats.on() && isBoat(e)) {
			int len = boatLength.i();
			double wid = boatWidth.f() / 100.0;
			int rows = Math.min(len, 2 + (int) (speed * 10));
			for (int k = 0; k < rows; k++) {
				double back = 0.9 + k * 0.45;
				double spread = (0.25 + k * 0.22) * wid;
				double bx = e.getX() - dx * back, bz = e.getZ() - dz * back;
				double y = e.getY() + 0.12;
				// the two arms of the V
				for (int side = -1; side <= 1; side += 2) {
					double off = side * spread + (rnd.nextDouble() - 0.5) * 0.15;
					Fx.spawn(foamParticle(), bx + px * off, y, bz + pz * off, px * side * 0.02, 0.01, pz * side * 0.02);
					if (k < 3) {
						Fx.spawn(ParticleTypes.FISHING, bx + px * off, y, bz + pz * off, 0, 0, 0);
					}
				}
				// foam in the middle, right behind the hull
				if (k < 3) {
					Fx.spawn(foamParticle(), bx + (rnd.nextDouble() - 0.5) * 0.4, y, bz + (rnd.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
				}
			}
			return;
		}

		// 3) player swimming / walking in water: a small wake
		if (self ? playerWake.on() : ripples.on()) {
			if (tickCount % (self ? 2 : 4) != 0) {
				return;
			}
			double y = e.getY() + e.getHeight() * 0.35;
			double back = 0.4 + e.getWidth() * 0.5;
			double wid = 0.25 + e.getWidth() * 0.4;
			for (int side = -1; side <= 1; side += 2) {
				double off = side * wid;
				Fx.spawn(ParticleTypes.FISHING, e.getX() - dx * back + px * off, y, e.getZ() - dz * back + pz * off, 0, 0, 0);
			}
			if (speed > 0.12) {
				Fx.spawn(foamParticle(), e.getX() - dx * back, y, e.getZ() - dz * back, 0, 0.01, 0);
			}
		}
	}

	// ------------------------------------------------------------------ waterfalls
	private boolean water(BlockPos pos) {
		return mc.world.getFluidState(pos).isIn(FluidTags.WATER);
	}

	/** Looks for falling water that lands on water / ground and makes foam and mist there. */
	private void waterfalls(double cx, double cy, double cz, double r) {
		var w = mc.world;
		int samples = 60;
		int foamPerHit = fallFoam.i();
		for (int i = 0; i < samples; i++) {
			int x = (int) Math.floor(cx + (rnd.nextDouble() * 2 - 1) * r);
			int z = (int) Math.floor(cz + (rnd.nextDouble() * 2 - 1) * r);
			int y = (int) Math.floor(cy + (rnd.nextDouble() * 2 - 1) * 14);
			BlockPos pos = new BlockPos(x, y, z);
			var fs = w.getFluidState(pos);
			// the last block of a falling column: flowing water, water above it, and something that stops it below
			if (!fs.isIn(FluidTags.WATER) || fs.isStill() || !water(pos.up())) {
				continue;
			}
			BlockPos below = pos.down();
			boolean landsOnWater = water(below) && w.getFluidState(below).isStill();
			boolean landsOnGround = !water(below) && !w.getBlockState(below).isAir();
			if (!landsOnWater && !landsOnGround) {
				continue;
			}
			double bx = x + 0.5, bz = z + 0.5;
			double by = landsOnWater ? y + 0.05 : y + 0.1;
			for (int k = 0; k < foamPerHit; k++) {
				double ox = (rnd.nextDouble() - 0.5) * 0.9, oz = (rnd.nextDouble() - 0.5) * 0.9;
				Fx.spawn(foamParticle(), bx + ox, by, bz + oz, ox * 0.06, 0.02 + rnd.nextDouble() * 0.05, oz * 0.06);
			}
			if (landsOnWater && rnd.nextInt(2) == 0) {
				Fx.ring(ParticleTypes.FISHING, bx, by, bz, 0.5 + rnd.nextDouble() * 0.5, 6, 0.04);
			}
			if (fallMist.on() && rnd.nextInt(3) == 0) {
				Fx.spawn(ParticleTypes.CLOUD, bx + (rnd.nextDouble() - 0.5), by + 0.4 + rnd.nextDouble(), bz + (rnd.nextDouble() - 0.5), 0, 0.02, 0);
			}
		}
	}
}
