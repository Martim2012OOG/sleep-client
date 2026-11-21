package dev.sleepclient.addons.ranked;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Anti-cheat of the duels, on the server of the match, so it only trusts what the server itself knows.
 * Reach is measured against where the target really was during the attacker's ping, with the range of what the attacker
 * was holding (spears and attribute swaps are legal). Also: hits without looking at the target, fake lag / blink,
 * speed, flying, and no knockback. Kits that launch you (mace, elytra, crystals, carts...) skip the movement checks.
 * Every check adds points that fade with time, so one laggy moment proves nothing. The points go with the match result
 * for review; only overwhelming reach evidence ends a ranked match.
 */
final class RankedAntiCheat {

	private static final Set<String> LAUNCH_KITS = Set.of("mace", "spear", "spear_elytra", "vanilla_ht", "vanilla_lt", "cart_ht", "cart_lt", "creeper");
	private static final int HISTORY = 16;
	private static final double REACH_TOLERANCE = 0.35;

	private static final class Data {
		final ArrayDeque<Vec3d> feet = new ArrayDeque<>();
		final ArrayDeque<Double> ranges = new ArrayDeque<>();
		final Map<String, Double> points = new HashMap<>();
		final Map<String, Integer> samples = new HashMap<>();
		double maxExcess, excessSum;
		int excessN;
		Vec3d last;
		double lastStep;
		int still, sinceBurst = 999, sinceHurt = 999, risingTicks, airStill, ghostGround;
		Vec3d windowStart;
		int windowTicks;
		// knockback check: where the player was when hit, and how long ago
		Vec3d hitAt;
		int hitAge = -1;
	}

	private static final Map<String, Data> ALL = new HashMap<>();
	private static boolean launchKit;
	private static long lastTickNs;
	private static int hitchTicks;

	private RankedAntiCheat() {
	}

	static void reset(String kit) {
		ALL.clear();
		launchKit = LAUNCH_KITS.contains(kit);
		lastTickNs = 0;
		hitchTicks = 0;
	}

	private static String key(ServerPlayerEntity p) {
		return p.getName().getString().toLowerCase(Locale.ROOT);
	}

	private static Data of(ServerPlayerEntity p) {
		return ALL.computeIfAbsent(key(p), k -> new Data());
	}

	private static void add(Data d, String check, double pts) {
		d.points.merge(check, pts, Double::sum);
		d.samples.merge(check, 1, Integer::sum);
	}

	/** How far this weapon reaches (spears reach further than the normal 3 blocks). */
	private static double rangeOf(ServerPlayerEntity p) {
		double r = p.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
		ItemStack st = p.getMainHandStack();
		var c = st.get(DataComponentTypes.ATTACK_RANGE);
		if (c != null) {
			r = Math.max(r, c.maxRange() + c.hitboxMargin());
		}
		return r;
	}

	static void tick(MinecraftServer s, List<ServerPlayerEntity> players) {
		long now = System.nanoTime();
		if (lastTickNs != 0 && now - lastTickNs > 90_000_000L) {
			hitchTicks = 12; // the server itself stuttered: nothing about movement means anything for a moment
		}
		lastTickNs = now;
		if (hitchTicks > 0) {
			hitchTicks--;
		}
		for (ServerPlayerEntity p : players) {
			Data d = of(p);
			Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
			d.feet.addLast(pos);
			if (d.feet.size() > HISTORY) {
				d.feet.removeFirst();
			}
			d.ranges.addLast(rangeOf(p));
			if (d.ranges.size() > 6) {
				d.ranges.removeFirst(); // the last moments: a weapon swapped just before the hit counts
			}
			d.sinceBurst++;
			d.sinceHurt++;
			d.points.replaceAll((k, v) -> Math.max(0, v - 0.004)); // about 8 points fade in 100 s
			knockback(p, d, pos);
			movement(p, d, pos);
			d.last = pos;
		}
	}

	private static boolean exempt(ServerPlayerEntity p) {
		return p.isGliding() || p.isUsingRiptide() || p.hasVehicle() || p.getAbilities().flying || p.isSpectator() || p.isCreative();
	}

	private static void movement(ServerPlayerEntity p, Data d, Vec3d pos) {
		if (d.last == null || hitchTicks > 0) {
			d.still = 0;
			d.windowStart = null;
			return;
		}
		double step = Math.hypot(pos.x - d.last.x, pos.z - d.last.z);
		boolean teleported = step > 8 || Math.abs(pos.y - d.last.y) > 8; // ender pearl / the round's teleport
		if (teleported) {
			d.still = 0;
			d.windowStart = null;
			d.risingTicks = 0;
			d.lastStep = 0;
			return;
		}
		// fake lag / blink: frozen on the server while moving, then a jump with everything at once
		if (step < 1e-4 && Math.abs(pos.y - d.last.y) < 1e-4) {
			if (d.lastStep > 0.06 || d.still > 0) {
				d.still++;
			}
		} else {
			if (d.still >= 6 && step > 0.9 && step <= 8) {
				add(d, "fakelag", 1.0);
				d.sinceBurst = 0;
			}
			d.still = 0;
			d.lastStep = step;
		}
		if (launchKit || exempt(p) || d.sinceHurt < 60 || p.isTouchingWater() || p.isInLava() || p.isClimbing()
				|| p.hasStatusEffect(StatusEffects.LEVITATION) || p.hasStatusEffect(StatusEffects.SLOW_FALLING) || p.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
			d.windowStart = null;
			d.risingTicks = 0;
			d.airStill = 0;
			return;
		}
		// speed, over a full second (so a single step proves nothing)
		if (d.windowStart == null) {
			d.windowStart = pos;
			d.windowTicks = 0;
		}
		if (++d.windowTicks >= 20) {
			double dist = Math.hypot(pos.x - d.windowStart.x, pos.z - d.windowStart.z);
			var sp = p.getStatusEffect(StatusEffects.SPEED);
			double max = 8.6 * (1 + (sp == null ? 0 : (sp.getAmplifier() + 1) * .2)) + 1.8;
			BlockState under = p.getEntityWorld().getBlockState(BlockPos.ofFloored(pos.x, pos.y - 0.5, pos.z));
			if (dist > max && under.getBlock().getSlipperiness() <= 0.6F) {
				add(d, "speed", Math.min(3, 1 + (dist - max) / 3));
			}
			d.windowStart = pos;
			d.windowTicks = 0;
		}
		// flying: going up for too long, floating still, or saying "on the ground" over nothing
		double dy = pos.y - d.last.y;
		boolean air = p.getEntityWorld().isSpaceEmpty(p, p.getBoundingBox().offset(0, -0.08, 0));
		d.risingTicks = dy > 0.01 && air ? d.risingTicks + 1 : 0;
		if (d.risingTicks > 14) {
			add(d, "fly", 1.5);
			d.risingTicks = 0;
		}
		d.airStill = air && Math.abs(dy) < 0.003 ? d.airStill + 1 : 0;
		if (d.airStill > 12) {
			add(d, "fly", 1.5);
			d.airStill = 0;
		}
		d.ghostGround = air && p.isOnGround() ? d.ghostGround + 1 : 0;
		if (d.ghostGround > 12) {
			add(d, "fly", 1.0);
			d.ghostGround = 0;
		}
	}

	private static void knockback(ServerPlayerEntity p, Data d, Vec3d pos) {
		if (d.hitAge < 0) {
			return;
		}
		if (++d.hitAge >= 6) {
			d.hitAge = -1;
			Box room = p.getBoundingBox().expand(0.5, 0, 0.5);
			if (d.hitAt != null && pos.distanceTo(d.hitAt) < 0.02 && hitchTicks == 0 && p.getEntityWorld().isSpaceEmpty(p, room)) {
				add(d, "knockback", 1.0);
			}
		}
	}

	static void onDamaged(ServerPlayerEntity p) {
		of(p).sinceHurt = 0;
	}

	private static double distanceToBox(Vec3d eye, Vec3d feet) {
		double minX = feet.x - .3, maxX = feet.x + .3, minY = feet.y, maxY = feet.y + 1.8, minZ = feet.z - .3, maxZ = feet.z + .3;
		double dx = Math.max(Math.max(minX - eye.x, 0), eye.x - maxX);
		double dy = Math.max(Math.max(minY - eye.y, 0), eye.y - maxY);
		double dz = Math.max(Math.max(minZ - eye.z, 0), eye.z - maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** A melee hit landed: checks how far it was and whether the attacker was looking at the target. */
	static void onHit(ServerPlayerEntity a, ServerPlayerEntity v, DamageSource src, float taken) {
		if (src.getSource() != a) {
			return; // arrows, explosions, crystals...
		}
		Data da = of(a), dv = of(v);
		if (da.sinceBurst <= 3) {
			add(da, "fakelag", 0.8); // hit right after coming back from a freeze: the classic fake-lag advantage
		}
		if (taken > 0 && v.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE) < 0.5) {
			dv.hitAt = new Vec3d(v.getX(), v.getY(), v.getZ());
			dv.hitAge = 0;
		}
		if (hitchTicks > 0) {
			return;
		}
		// closest the target was during the attacker's ping (never punishes lag), from the attacker's recent positions
		int lag = Math.min(HISTORY - 1, a.networkHandler.getLatency() / 50 + 2);
		double eyeH = a.getStandingEyeHeight(), best = Double.MAX_VALUE;
		Vec3d[] tf = dv.feet.toArray(new Vec3d[0]);
		Vec3d[] af = da.feet.toArray(new Vec3d[0]);
		if (tf.length == 0 || af.length == 0) {
			return;
		}
		for (int i = Math.max(0, tf.length - 1 - lag); i < tf.length; i++) {
			for (int j = Math.max(0, af.length - 3); j < af.length; j++) {
				best = Math.min(best, distanceToBox(af[j].add(0, eyeH, 0), tf[i]));
			}
		}
		double allowed = da.ranges.stream().mapToDouble(Double::doubleValue).max().orElse(3.0) + REACH_TOLERANCE;
		double excess = best - allowed;
		if (excess > 0) {
			add(da, "reach", 1 + Math.min(3, excess * 2));
			da.maxExcess = Math.max(da.maxExcess, excess);
			da.excessSum += excess;
			da.excessN++;
		} else {
			da.points.computeIfPresent("reach", (k, p) -> Math.max(0, p - 0.25));
		}
		// hit without looking at them (kill aura). Swords sweep, maces splash and spears pierce: those are legal
		ItemStack held = a.getMainHandStack();
		if (best >= 1.5 && !held.isIn(ItemTags.SWORDS) && !held.isOf(Items.MACE) && held.get(DataComponentTypes.KINETIC_WEAPON) == null) {
			Vec3d eye = a.getEyePos(), to = new Vec3d(v.getX(), v.getY() + 0.9, v.getZ()).subtract(eye).normalize();
			if (a.getRotationVector().dotProduct(to) < 0.35) { // about 70 degrees off
				add(da, "aim", 1.5);
			}
		}
	}

	/** Name of a player with overwhelming reach evidence, or null. */
	static String proven() {
		for (var e : ALL.entrySet()) {
			Data d = e.getValue();
			if (d.points.getOrDefault("reach", 0D) >= 14 && d.excessN >= 8 && d.excessSum / d.excessN >= 0.5) {
				return e.getKey();
			}
		}
		return null;
	}

	/** What goes with the match result: everything that looked wrong, for review. */
	static JsonArray report() {
		JsonArray out = new JsonArray();
		for (var e : ALL.entrySet()) {
			Data d = e.getValue();
			for (var c : d.samples.entrySet()) {
				if (d.points.getOrDefault(c.getKey(), 0D) < 2) {
					continue;
				}
				JsonObject o = new JsonObject();
				o.addProperty("player", e.getKey());
				o.addProperty("check", c.getKey());
				o.addProperty("points", Math.round(d.points.get(c.getKey()) * 10) / 10.0);
				o.addProperty("samples", c.getValue());
				if ("reach".equals(c.getKey())) {
					o.addProperty("maxExtraBlocks", Math.round(d.maxExcess * 100) / 100.0);
				}
				out.add(o);
			}
		}
		return out;
	}
}
