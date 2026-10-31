package dev.solclient.anticheat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Watches the other players your client can see and keeps a "suspicion" score for each one (and from it a trust %). Only what the
 * client can observe is used: how they move, how far they hit, where they look when they hit, how fast they click. It is a guess,
 * never proof: creative / spectator players, lag, knockback, ice, water, elytra, vehicles and teleports are ignored, the points fade
 * with time, and the default strictness is the careful one.
 */
public final class TrustEngine {

	public enum Check {
		FLY("Flying", "Voo"), SPEED("Speed", "Velocidade"), REACH("Reach", "Alcance"), AIM("Aim / kill aura", "Mira / kill aura"), CLICK("Auto clicker", "Auto clicker");

		public final String en, pt;

		Check(String en, String pt) {
			this.en = en;
			this.pt = pt;
		}
	}

	/** Everything we know about one player. Read from the render / command thread, written on the client tick. */
	public static final class Profile {
		public final UUID id;
		public volatile String name;
		/** 0..100 per check. */
		public final double[] pts = new double[Check.values().length];
		public volatile long firstSeenMs, lastSeenMs;
		public volatile int ticks, ping, hits;
		public volatile GameMode mode;
		public volatile boolean community;
		public long lastReportMs;
		public double lastReportScore;
		// movement
		double px, py, pz;
		boolean havePos;
		int hover, fast, rise, lastHurtAge = -100;
		// swings
		int lastSwingAge = -100;
		final ArrayDeque<Integer> swings = new ArrayDeque<>();
		float prevYaw, prevPitch, yawStep, pitchStep, bigTurnAge;

		Profile(UUID id, String name) {
			this.id = id;
			this.name = name;
		}

		public double suspicion() {
			double[] s = pts.clone();
			java.util.Arrays.sort(s);
			double v = 0, w = 1;
			for (int i = s.length - 1; i >= 0; i--) { // the worst check counts fully, the others add less
				v += s[i] * w;
				w *= 0.5;
			}
			return Math.min(100, v);
		}

		public int trust() {
			return (int) Math.round(100 - suspicion());
		}

		public double observedSeconds() {
			return ticks / 20.0;
		}

		/** The checks that are clearly above zero, worst first. */
		public List<Check> flagged() {
			List<Check> out = new ArrayList<>();
			for (Check c : Check.values()) {
				if (pts[c.ordinal()] >= 12) {
					out.add(c);
				}
			}
			out.sort((a, b) -> Double.compare(pts[b.ordinal()], pts[a.ordinal()]));
			return out;
		}
	}

	private static final Map<UUID, Profile> PROFILES = new HashMap<>();
	/** Multiplier of the points added (the Careful / Normal / Strict setting). */
	public static volatile double strictness = 0.7;
	private static final double[] DECAY = {0.9990, 0.9990, 0.9997, 0.9997, 0.9990};

	private TrustEngine() {
	}

	public static synchronized List<Profile> all() {
		return new ArrayList<>(PROFILES.values());
	}

	public static synchronized Profile byName(String name) {
		if (name == null) {
			return null;
		}
		for (Profile p : PROFILES.values()) {
			if (p.name != null && p.name.equalsIgnoreCase(name)) {
				return p;
			}
		}
		return null;
	}

	public static synchronized void clear() {
		PROFILES.clear();
	}

	private static void add(Profile f, Check c, double points) {
		int i = c.ordinal();
		f.pts[i] = Math.min(100, f.pts[i] + points * strictness);
	}

	// ------------------------------------------------------------------ every client tick
	public static synchronized void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) {
			PROFILES.clear();
			return;
		}
		long now = System.currentTimeMillis();
		List<AbstractClientPlayerEntity> players = new ArrayList<>(mc.world.getPlayers());
		for (AbstractClientPlayerEntity p : players) {
			if (p == mc.player || p.isRemoved()) {
				continue;
			}
			Profile f = PROFILES.computeIfAbsent(p.getUuid(), k -> new Profile(k, p.getName().getString()));
			if (f.firstSeenMs == 0) {
				f.firstSeenMs = now;
			}
			f.lastSeenMs = now;
			f.name = p.getName().getString();
			update(mc, f, p, players);
		}
		PROFILES.values().removeIf(f -> now - f.lastSeenMs > 600_000L);
		for (Profile f : PROFILES.values()) {
			for (int i = 0; i < f.pts.length; i++) {
				f.pts[i] *= DECAY[i];
			}
		}
	}

	private static void update(MinecraftClient mc, Profile f, AbstractClientPlayerEntity p, List<AbstractClientPlayerEntity> players) {
		PlayerListEntry le = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
		f.mode = le == null ? null : le.getGameMode();
		f.ping = le == null ? 0 : le.getLatency();
		f.ticks++;
		boolean exempt = f.mode == GameMode.CREATIVE || f.mode == GameMode.SPECTATOR || p.isSpectator() || le == null;
		if (p.hurtTime > 0) {
			f.lastHurtAge = f.ticks;
		}
		boolean laggy = f.ping > 280;
		double dx = p.getX() - f.px, dy = p.getY() - f.py, dz = p.getZ() - f.pz;
		boolean moved = f.havePos;
		if (f.havePos && Math.sqrt(dx * dx + dy * dy + dz * dz) > 8) {
			moved = false; // teleport / respawn: start again
			f.hover = f.fast = f.rise = 0;
		}
		f.px = p.getX();
		f.py = p.getY();
		f.pz = p.getZ();
		boolean first = !f.havePos;
		f.havePos = true;

		// ---- yaw / pitch steps (for the aim check)
		f.yawStep = MathHelper.wrapDegrees(p.getYaw() - f.prevYaw);
		f.pitchStep = p.getPitch() - f.prevPitch;
		f.prevYaw = p.getYaw();
		f.prevPitch = p.getPitch();
		if (Math.abs(f.yawStep) > 70 && !first) {
			f.bigTurnAge = f.ticks;
		}

		if (!exempt && moved && !laggy) {
			double hs = Math.hypot(dx, dz);
			boolean busy = p.hasVehicle() || p.isGliding() || p.isSleeping() || p.isClimbing() || p.isTouchingWater() || p.isInLava() || p.isUsingRiptide()
					|| p.isInsideWall() || f.ticks - f.lastHurtAge < 24 || p.isRemoved();
			boolean floaty = p.hasStatusEffect(StatusEffects.LEVITATION) || p.hasStatusEffect(StatusEffects.SLOW_FALLING) || p.hasStatusEffect(StatusEffects.JUMP_BOOST);
			BlockState under = mc.world.getBlockState(p.getVelocityAffectingPos());
			boolean slippery = under.isIn(BlockTags.ICE) || under.isOf(net.minecraft.block.Blocks.SLIME_BLOCK) || under.isOf(net.minecraft.block.Blocks.HONEY_BLOCK)
					|| under.isOf(net.minecraft.block.Blocks.SOUL_SAND) || under.isOf(net.minecraft.block.Blocks.SOUL_SOIL);
			// ---- flying: hanging in the air without falling, or going up and up
			if (!busy && !floaty && !p.isOnGround()) {
				boolean still = Math.abs(dy) < 0.008;
				f.hover = still && (hs > 0.08 || f.hover > 0) ? f.hover + 1 : still ? f.hover : 0;
				f.rise = dy > 0.04 ? f.rise + 1 : 0;
				if (f.hover >= 12) {
					add(f, Check.FLY, Math.min(6, 1.0 + (f.hover - 12) * 0.35));
				}
				if (f.rise >= 14) {
					add(f, Check.FLY, Math.min(6, 1.0 + (f.rise - 14) * 0.4));
				}
			} else {
				f.hover = Math.max(0, f.hover - 2);
				f.rise = 0;
			}
			// ---- speed: faster than sprint-jumping with their potion effect allows, for several ticks in a row
			var speed = p.getStatusEffect(StatusEffects.SPEED);
			double mult = 1 + (speed == null ? 0 : 0.2 * (speed.getAmplifier() + 1));
			double limit = 0.46 * mult + 0.06;
			if (!busy && !slippery && hs > limit) {
				f.fast++;
				if (f.fast >= 5) {
					add(f, Check.SPEED, Math.min(8, 1.5 + (hs / limit - 1) * 18));
				}
			} else {
				f.fast = Math.max(0, f.fast - 1);
			}
		} else if (exempt) {
			f.hover = f.fast = f.rise = 0;
		}

		// ---- swings (clicking)
		if (p.handSwinging && p.handSwingTicks == 0) {
			f.lastSwingAge = f.ticks;
			f.swings.addLast(f.ticks);
			while (f.swings.size() > 40) {
				f.swings.pollFirst();
			}
			clicks(f);
		}
		// ---- somebody got hit: who hit them, from how far, looking where?
		for (AbstractClientPlayerEntity t : players) {
			if (t.maxHurtTime > 0 && t.hurtTime == t.maxHurtTime) {
				if (t == p || f.lastSwingAge < f.ticks - 3 || p.squaredDistanceTo(t) > 64) {
					continue;
				}
				hit(mc, f, p, t, le, players);
			}
		}
	}

	private static void clicks(Profile f) {
		// swings in the last second (20 ticks): people cannot reach 19-20 clicks a second
		int cps = 0;
		for (int s : f.swings) {
			if (f.ticks - s < 20) {
				cps++;
			}
		}
		if (cps >= 19) {
			add(f, Check.CLICK, 9);
		} else if (cps >= 17) {
			add(f, Check.CLICK, 1.5);
		}
		// a long run of identical gaps (a machine, not a hand)
		if (f.swings.size() >= 30) {
			Integer[] a = f.swings.toArray(new Integer[0]);
			int same = 0, n = 0;
			for (int i = a.length - 1; i > a.length - 30 && i > 0; i--, n++) {
				if (a[i] - a[i - 1] == a[a.length - 1] - a[a.length - 2]) {
					same++;
				}
			}
			if (n > 0 && same >= n - 1 && a[a.length - 1] - a[a.length - 2] <= 3) {
				add(f, Check.CLICK, 1.2);
			}
		}
	}

	private static void hit(MinecraftClient mc, Profile f, AbstractClientPlayerEntity a, AbstractClientPlayerEntity t, PlayerListEntry le,
			List<AbstractClientPlayerEntity> players) {
		// only the attacker who looks most at the target counts (somebody else may have swung at the same time)
		Vec3d eye = a.getEyePos();
		Box box = t.getBoundingBox();
		Vec3d aim = box.getCenter().subtract(eye);
		double len = aim.length();
		if (len < 1e-3) {
			return;
		}
		double angle = Math.toDegrees(Math.acos(MathHelper.clamp(a.getRotationVec(1F).dotProduct(aim.multiply(1 / len)), -1, 1)));
		for (AbstractClientPlayerEntity o : players) {
			if (o != a && o != t && o.handSwinging && o.handSwingTicks <= 2 && o.squaredDistanceTo(t) < 64) {
				Vec3d oa = box.getCenter().subtract(o.getEyePos());
				if (oa.length() > 1e-3 && Math.toDegrees(Math.acos(MathHelper.clamp(o.getRotationVec(1F).dotProduct(oa.normalize()), -1, 1))) < angle) {
					return; // somebody else is the likely attacker
				}
			}
		}
		f.hits++;
		boolean slow = f.ping > 200;
		// distance from the eyes to the closest point of the target's box
		double cx = MathHelper.clamp(eye.x, box.minX, box.maxX), cy = MathHelper.clamp(eye.y, box.minY, box.maxY), cz = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
		double reach = Math.sqrt((eye.x - cx) * (eye.x - cx) + (eye.y - cy) * (eye.y - cy) + (eye.z - cz) * (eye.z - cz));
		if (!slow && reach > 3.65) {
			add(f, Check.REACH, reach > 4.2 ? 26 : reach > 3.9 ? 18 : 10);
		}
		if (!slow && angle > 75 && reach > 0.8) {
			add(f, Check.AIM, 16); // hit somebody who was not even in front of them
		}
		if (!slow && f.ticks - f.bigTurnAge <= 2 && angle < 5 && Math.abs(f.yawStep) < 8) {
			add(f, Check.AIM, 6); // a big instant turn that lands exactly on the target
		}
	}

	// ------------------------------------------------------------------ names for chat
	public static String label(Check c, boolean pt) {
		return pt ? c.pt : c.en;
	}

	public static String lower(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT);
	}
}
