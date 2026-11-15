package dev.solclient.fightcam;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fight Replay: like the replay mods PvP creators use.
 * <p>
 * While you play, the last N seconds of the world around you are remembered (every player near you 20 times a
 * second: position, rotation, walking animation, hits, sneaking, armour and items in hand - and every block that
 * changed). Open the replay and you watch it again with a free spectator camera: fly anywhere, slow motion, follow a
 * player, see through their eyes, and record it to a video from any angle.
 * <p>
 * The recorded players are drawn as copies (the real ones are hidden while the replay is open), and the blocks are
 * put back to how they were at that moment, then restored when you close it. It all happens only on your screen.
 */
public final class Replay {

	private static final Minecraft mc = Minecraft.getInstance();
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** One player at one moment. */
	public static final class Snap {
		UUID id;
		double x, y, z;
		float bodyYaw, headYaw, pitch, limbPos, limbSpeed, swing, death;
		int hurt;
		boolean sneaking;
		Pose pose;
		ItemStack[] eq;
	}

	public static final class Frame {
		long tick;
		Snap[] snaps;
	}

	public record BlockChange(long tick, BlockPos pos, BlockState before, BlockState after) {
	}

	/** A player that appears in the replay, and the copy used to draw them. */
	public static final class Actor {
		public final UUID id;
		public final String name;
		final GameProfile profile;
		RemotePlayer copy;
		Object copyWorld;
		final ItemStack[] shown = new ItemStack[6];

		Actor(UUID id, String name, GameProfile profile) {
			this.id = id;
			this.name = name;
			this.profile = profile;
		}
	}

	// ---------------------------------------------------------------- recording (always on while enabled)
	private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();
	private static final ArrayDeque<BlockChange> BLOCKS = new ArrayDeque<>();
	private static final Map<UUID, Actor> ACTORS = new HashMap<>();
	private static final Map<UUID, Snap> LAST = new HashMap<>();
	/** latest real state of every block that changed (to put the world back after the replay) */
	private static final Map<BlockPos, BlockState> LIVE = new HashMap<>();
	private static long tickNo;
	private static int keepSeconds = 60;
	private static double range = 96;
	public static volatile boolean recordingEnabled;
	/** true while WE change blocks (so we do not record our own changes) */
	private static boolean applying;
	private static Object worldRef;

	// ---------------------------------------------------------------- playback
	private static boolean active;
	private static Frame[] play;
	private static BlockChange[] playBlocks;
	private static final Map<BlockPos, List<BlockChange>> BY_POS = new LinkedHashMap<>();
	private static double t; // frame index (fractional)
	public static boolean playing;
	public static float speed = 1F;
	private static long lastNs;
	private static int appliedTick = Integer.MIN_VALUE;
	private static boolean hooked;
	private static String renderError = "";

	// camera
	public static final int FREE = 0, FOLLOW = 1, POV = 2, PATH = 3;
	/** cinematic camera path: {frame, x, y, z, yaw, pitch}, sorted by frame */
	public static final List<double[]> KEYS = new ArrayList<>();
	public static int camMode = FREE;
	public static UUID target;
	public static double camX, camY, camZ;
	public static float camYaw, camPitch;
	public static float orbitYaw, orbitPitch = 20F, orbitDist = 4.5F;
	private static double sx, sy, sz; // smoothed camera position (follow / pov)
	private static float syaw, spitch;

	private Replay() {
	}

	public static void configure(int seconds, double rangeBlocks) {
		keepSeconds = Math.max(10, Math.min(300, seconds));
		range = rangeBlocks;
	}

	public static boolean active() {
		return active;
	}

	public static boolean applying() {
		return applying;
	}

	public static double seconds() {
		return FRAMES.size() / 20.0;
	}

	public static void clear() {
		FRAMES.clear();
		BLOCKS.clear();
		LAST.clear();
		LIVE.clear();
		ACTORS.clear();
	}

	/** Every client tick (only while the FightCam mod is on). */
	public static void tick() {
		if (mc.level == null || mc.player == null) {
			if (active) {
				stop();
			}
			clear();
			worldRef = null;
			return;
		}
		if (worldRef != mc.level) {
			// new world / server: the old replay belongs to another world
			if (active) {
				stop();
			}
			clear();
			worldRef = mc.level;
		}
		if (!recordingEnabled) {
			return;
		}
		tickNo++;
		List<Snap> snaps = new ArrayList<>();
		double r2 = range * range;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p.isSpectator() || (p != mc.player && p.distanceToSqr(mc.player) > r2)) {
				continue;
			}
			UUID id = p.getUUID();
			Snap s = new Snap();
			s.id = id;
			s.x = p.getX();
			s.y = p.getY();
			s.z = p.getZ();
			s.bodyYaw = p.yBodyRot;
			s.headYaw = p.yHeadRot;
			s.pitch = p.getXRot();
			s.limbPos = p.walkAnimation.position();
			s.limbSpeed = p.walkAnimation.speed();
			s.swing = p.getAttackAnim(1F);
			s.hurt = p.hurtTime;
			s.death = p.deathTime;
			s.sneaking = p.isShiftKeyDown();
			s.pose = p.getPose();
			Snap prev = LAST.get(id);
			s.eq = new ItemStack[6];
			for (int i = 0; i < 6; i++) {
				ItemStack now = p.getItemBySlot(SLOTS[i]);
				ItemStack old = prev == null ? null : prev.eq[i];
				s.eq[i] = old != null && ItemStack.matches(old, now) ? old : now.copy();
			}
			LAST.put(id, s);
			snaps.add(s);
			if (!ACTORS.containsKey(id)) {
				ACTORS.put(id, new Actor(id, p.getName().getString(), p.getGameProfile()));
			}
		}
		Frame f = new Frame();
		f.tick = tickNo;
		f.snaps = snaps.toArray(new Snap[0]);
		FRAMES.addLast(f);
		int max = keepSeconds * 20;
		while (FRAMES.size() > max) {
			FRAMES.removeFirst();
		}
		long oldest = FRAMES.isEmpty() ? tickNo : FRAMES.peekFirst().tick;
		while (!BLOCKS.isEmpty() && BLOCKS.peekFirst().tick() < oldest) {
			BLOCKS.removeFirst();
		}
	}

	/** From the World.setBlockState mixin (client world only), before the change happens. */
	public static void onBlockChange(BlockPos pos, BlockState before, BlockState after) {
		if (applying || mc.level == null || before == after) {
			return;
		}
		BlockPos p = pos.immutable();
		LIVE.put(p, after);
		if (!recordingEnabled) {
			return;
		}
		if (mc.player != null && p.distSqr(mc.player.blockPosition()) > range * range) {
			return;
		}
		BLOCKS.addLast(new BlockChange(tickNo, p, before, after));
		if (BLOCKS.size() > 20000) {
			BLOCKS.removeFirst();
		}
	}

	// ---------------------------------------------------------------- open / close
	/** Opens the replay at 'secondsBack' before the end. Returns an error text or null. */
	public static String start(double secondsBack) {
		if (active) {
			return null;
		}
		if (FRAMES.size() < 20) {
			return "Nothing to replay yet (play for a few seconds first)";
		}
		play = FRAMES.toArray(new Frame[0]);
		playBlocks = BLOCKS.toArray(new BlockChange[0]);
		BY_POS.clear();
		for (BlockChange b : playBlocks) {
			BY_POS.computeIfAbsent(b.pos(), k -> new ArrayList<>()).add(b);
		}
		t = Math.max(0, play.length - 1 - secondsBack * 20);
		playing = true;
		speed = 1F;
		appliedTick = Integer.MIN_VALUE;
		lastNs = 0;
		active = true;
		camMode = FREE;
		target = mc.player.getUUID();
		Vec3 eye = mc.player.getEyePosition();
		camX = eye.x;
		camY = eye.y;
		camZ = eye.z;
		camYaw = mc.player.getYRot();
		camPitch = mc.player.getXRot();
		sx = camX;
		sy = camY;
		sz = camZ;
		syaw = camYaw;
		spitch = camPitch;
		KEYS.clear();
		hook();
		return null;
	}

	/** Adds a camera point at the current time (replaces one at the same moment). */
	public static void addKey() {
		double f = t;
		KEYS.removeIf(k -> Math.abs(k[0] - f) < 2);
		KEYS.add(new double[] {f, sx, sy, sz, syaw, spitch});
		KEYS.sort((a, b) -> Double.compare(a[0], b[0]));
	}

	public static void clearKeys() {
		KEYS.clear();
	}

	private static double cr(double p0, double p1, double p2, double p3, double u) {
		double u2 = u * u, u3 = u2 * u;
		return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
	}

	/** Smooth (Catmull-Rom) camera along the points for the current time. */
	private static boolean pathPose() {
		int n = KEYS.size();
		if (n == 0) {
			return false;
		}
		if (n == 1 || t <= KEYS.get(0)[0]) {
			double[] k = KEYS.get(0);
			setPose(k[1], k[2], k[3], (float) k[4], (float) k[5]);
			return true;
		}
		if (t >= KEYS.get(n - 1)[0]) {
			double[] k = KEYS.get(n - 1);
			setPose(k[1], k[2], k[3], (float) k[4], (float) k[5]);
			return true;
		}
		int i = 0;
		while (i < n - 2 && KEYS.get(i + 1)[0] < t) {
			i++;
		}
		double[] a = KEYS.get(Math.max(0, i - 1)), b = KEYS.get(i), c = KEYS.get(i + 1), d = KEYS.get(Math.min(n - 1, i + 2));
		double u = (t - b[0]) / Math.max(1e-6, c[0] - b[0]);
		u = u * u * (3 - 2 * u) * 0.35 + u * 0.65; // a little ease in / out at every point
		// angles: unwrap so the camera never spins the long way round
		double by = b[4], cy = by + Mth.wrapDegrees((float) (c[4] - by));
		double ay = by + Mth.wrapDegrees((float) (a[4] - by)), dy = cy + Mth.wrapDegrees((float) (d[4] - cy));
		setPose(cr(a[1], b[1], c[1], d[1], u), cr(a[2], b[2], c[2], d[2], u), cr(a[3], b[3], c[3], d[3], u), (float) cr(ay, by, cy, dy, u),
				(float) cr(a[5], b[5], c[5], d[5], u));
		return true;
	}

	private static void setPose(double x, double y, double z, float yaw, float pitch) {
		sx = x;
		sy = y;
		sz = z;
		syaw = yaw;
		spitch = Math.max(-90F, Math.min(90F, pitch));
	}

	public static void stop() {
		if (!active) {
			return;
		}
		active = false;
		// put every block back to how the server says it is now
		if (mc.level != null) {
			applying = true;
			try {
				for (BlockPos p : BY_POS.keySet()) {
					BlockState live = LIVE.get(p);
					if (live != null && mc.level.getBlockState(p) != live) {
						mc.level.setBlock(p, live, 18);
					}
				}
			} catch (RuntimeException ignored) {
			} finally {
				applying = false;
			}
		}
		for (Actor a : ACTORS.values()) {
			a.copy = null;
			java.util.Arrays.fill(a.shown, null);
		}
		play = null;
		playBlocks = null;
		BY_POS.clear();
	}

	// ---------------------------------------------------------------- timeline
	public static int length() {
		return play == null ? 0 : play.length;
	}

	public static double time() {
		return t;
	}

	public static void seek(double frame) {
		if (play == null) {
			return;
		}
		t = Math.max(0, Math.min(play.length - 1, frame));
	}

	public static List<Actor> actors() {
		List<Actor> l = new ArrayList<>();
		if (play == null) {
			return l;
		}
		Frame f = play[(int) Math.min(play.length - 1, Math.max(0, t))];
		for (Snap s : f.snaps) {
			Actor a = ACTORS.get(s.id);
			if (a != null) {
				l.add(a);
			}
		}
		return l;
	}

	public static String nameOf(UUID id) {
		Actor a = id == null ? null : ACTORS.get(id);
		return a == null ? "?" : a.name;
	}

	/** Advances the time; called once per rendered frame. */
	public static void advance() {
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0 : Math.min(0.1F, (now - lastNs) / 1e9F);
		lastNs = now;
		if (play == null) {
			return;
		}
		if (playing) {
			t += dt * 20 * speed;
			if (t >= play.length - 1) {
				t = play.length - 1;
				playing = false;
			}
		}
		applyBlocks();
		updateCamera(dt);
	}

	private static Snap find(Frame f, UUID id) {
		for (Snap s : f.snaps) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** Interpolated state of a player at the current time (null if not there). */
	private static Snap at(UUID id) {
		int i = (int) Math.floor(t);
		Frame a = play[Math.max(0, Math.min(play.length - 1, i))];
		Frame b = play[Math.max(0, Math.min(play.length - 1, i + 1))];
		Snap s0 = find(a, id);
		if (s0 == null) {
			return null;
		}
		Snap s1 = find(b, id);
		if (s1 == null) {
			return s0;
		}
		float k = (float) (t - i);
		Snap s = new Snap();
		s.id = id;
		s.x = Mth.lerp(k, s0.x, s1.x);
		s.y = Mth.lerp(k, s0.y, s1.y);
		s.z = Mth.lerp(k, s0.z, s1.z);
		s.bodyYaw = Mth.rotLerp(k, s0.bodyYaw, s1.bodyYaw);
		s.headYaw = Mth.rotLerp(k, s0.headYaw, s1.headYaw);
		s.pitch = Mth.lerp(k, s0.pitch, s1.pitch);
		s.limbPos = Mth.lerp(k, s0.limbPos, s1.limbPos);
		s.limbSpeed = Mth.lerp(k, s0.limbSpeed, s1.limbSpeed);
		s.swing = s1.swing >= s0.swing ? Mth.lerp(k, s0.swing, s1.swing) : (k < 0.5F ? s0.swing : s1.swing);
		s.hurt = k < 0.5F ? s0.hurt : s1.hurt;
		s.death = s0.death > 0 ? Mth.lerp(k, s0.death, s1.death) : 0;
		s.sneaking = k < 0.5F ? s0.sneaking : s1.sneaking;
		s.pose = k < 0.5F ? s0.pose : s1.pose;
		s.eq = k < 0.5F ? s0.eq : s1.eq;
		return s;
	}

	// ---------------------------------------------------------------- blocks
	private static void applyBlocks() {
		if (mc.level == null || BY_POS.isEmpty()) {
			return;
		}
		int frame = (int) Math.floor(t);
		if (frame == appliedTick) {
			return;
		}
		appliedTick = frame;
		long tick = play[Math.max(0, Math.min(play.length - 1, frame))].tick;
		applying = true;
		try {
			for (Map.Entry<BlockPos, List<BlockChange>> e : BY_POS.entrySet()) {
				List<BlockChange> l = e.getValue();
				BlockState want = l.get(0).before();
				for (BlockChange c : l) {
					if (c.tick() <= tick) {
						want = c.after();
					} else {
						break;
					}
				}
				if (mc.level.getBlockState(e.getKey()) != want) {
					mc.level.setBlock(e.getKey(), want, 18); // 2 (tell the renderer) + 16 (no neighbour updates)
				}
			}
		} catch (RuntimeException ignored) {
		} finally {
			applying = false;
		}
	}

	// ---------------------------------------------------------------- camera
	private static void updateCamera(float dt) {
		if (camMode == PATH) {
			if (pathPose()) {
				camX = sx;
				camY = sy;
				camZ = sz;
				camYaw = syaw;
				camPitch = spitch;
				return;
			}
			camMode = FREE; // no points yet
		}
		if (camMode == FREE) {
			sx = camX;
			sy = camY;
			sz = camZ;
			syaw = camYaw;
			spitch = camPitch;
			return;
		}
		Snap s = target == null ? null : at(target);
		if (s == null) {
			return;
		}
		double tx, ty, tz;
		float yaw, pitch;
		if (camMode == POV) {
			tx = s.x;
			ty = s.y + (s.sneaking ? 1.27 : 1.62);
			tz = s.z;
			yaw = s.headYaw;
			pitch = s.pitch;
		} else {
			double fy = s.y + 1.1;
			double yr = Math.toRadians(orbitYaw), pr = Math.toRadians(orbitPitch);
			tx = s.x - Math.sin(yr) * Math.cos(pr) * orbitDist;
			tz = s.z + Math.cos(yr) * Math.cos(pr) * orbitDist;
			ty = fy + Math.sin(pr) * orbitDist;
			double dx = s.x - tx, dy = fy - ty, dz = s.z - tz;
			yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		}
		float k = camMode == POV ? 1F : 1F - (float) Math.exp(-dt / 0.12F);
		sx += (tx - sx) * k;
		sy += (ty - sy) * k;
		sz += (tz - sz) * k;
		syaw += Mth.wrapDegrees(yaw - syaw) * k;
		spitch += (pitch - spitch) * k;
		// switching back to free camera starts from here
		camX = sx;
		camY = sy;
		camZ = sz;
		camYaw = syaw;
		camPitch = spitch;
	}

	/** x, y, z, yaw, pitch for the camera mixin. */
	public static double[] pose() {
		return new double[] {sx, sy, sz, syaw, spitch};
	}

	/** Moves the free camera (blocks), relative to where it looks. */
	public static void fly(double forward, double strafe, double up) {
		double yr = Math.toRadians(camYaw);
		double fx = -Math.sin(yr), fz = Math.cos(yr);
		double rx = -Math.cos(yr), rz = -Math.sin(yr);
		camX += fx * forward + rx * strafe;
		camZ += fz * forward + rz * strafe;
		camY += up;
	}

	// ---------------------------------------------------------------- drawing the recorded players
	private static void hook() {
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
				if (!active || play == null) {
					return;
				}
				try {
					render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState);
				} catch (RuntimeException e) {
					if (renderError.isEmpty()) {
						System.err.println("[Sleep] Replay render: " + e);
						e.printStackTrace();
					}
					renderError = String.valueOf(e);
				}
						} finally {
				dev.solclient.Perf.add("Replay", System.nanoTime() - kuboPerfT0);
			}
		});
		} catch (Throwable e) {
			System.err.println("[Sleep] Replay rendering unavailable: " + e);
		}
	}

	public static String renderError() {
		return renderError;
	}

	private static void render(PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cam) {
		if (mc.level == null) {
			return;
		}
		EntityRenderDispatcher erm = mc.getEntityRenderDispatcher();
		Vec3 cp = cam.pos;
		Frame f = play[(int) Math.max(0, Math.min(play.length - 1, Math.floor(t)))];
		int n = 0;
		for (Snap base : f.snaps) {
			Actor a = ACTORS.get(base.id);
			Snap s = at(base.id);
			if (a == null || s == null) {
				continue;
			}
			if (camMode == POV && base.id.equals(target)) {
				continue; // do not draw the head we are looking out of
			}
			if (a.copy == null || a.copyWorld != mc.level) {
				a.copy = new RemotePlayer(mc.level, a.profile);
				a.copyWorld = mc.level;
				a.copy.setId(-7000 - n);
				java.util.Arrays.fill(a.shown, null);
			}
			n++;
			RemotePlayer e = a.copy;
			e.setPosRaw(s.x, s.y, s.z);
			e.xo = s.x;
			e.yo = s.y;
			e.zo = s.z;
			e.setYRot(s.bodyYaw);
			e.yRotO = s.bodyYaw;
			e.setXRot(s.pitch);
			e.xRotO = s.pitch;
			e.yBodyRot = s.bodyYaw;
			e.yBodyRotO = s.bodyYaw;
			e.yHeadRot = s.headYaw;
			e.yHeadRotO = s.headYaw;
			e.hurtTime = s.hurt;
			e.deathTime = (int) s.death;
			e.setShiftKeyDown(s.sneaking);
			if (s.pose != null) {
				e.setPose(s.pose);
			}
			for (int i = 0; i < 6; i++) {
				if (a.shown[i] != s.eq[i]) {
					a.shown[i] = s.eq[i];
					e.setItemSlot(SLOTS[i], s.eq[i]);
				}
			}
			EntityRenderState st = erm.extractEntity(e, 1F);
			st.x = s.x;
			st.y = s.y;
			st.z = s.z;
			double dx = s.x - cp.x, dy = s.y - cp.y, dz = s.z - cp.z;
			st.distanceToCameraSq = dx * dx + dy * dy + dz * dz;
			if (st instanceof LivingEntityRenderState l) {
				l.bodyRot = s.bodyYaw;
				l.yRot = Mth.wrapDegrees(s.headYaw - s.bodyYaw);
				l.xRot = s.pitch;
				l.walkAnimationPos = s.limbPos;
				l.walkAnimationSpeed = Math.min(1F, s.limbSpeed);
				l.hasRedOverlay = s.hurt > 0;
				l.deathTime = s.death;
			}
			if (st instanceof ArmedEntityRenderState arm) {
				arm.attackTime = s.swing;
			}
			erm.submit(st, cam, dx, dy, dz, matrices, queue);
		}
	}
}
