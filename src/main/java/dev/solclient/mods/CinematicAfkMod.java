package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Cinematic AFK Camera: when you stop playing (or when you press the key) the camera leaves your head and films you
 * like a movie: orbit, drone, portrait, crane, side sweep, or all of them one after the other. Any movement, key,
 * click, mouse move or hit brings the normal camera back.
 *
 * <p>It only moves the camera on your screen (client side). Nothing is sent to the server.
 */
public class CinematicAfkMod extends Mod {

	public static CinematicAfkMod instance;
	/** Set by the mouse mixins: any mouse movement / scroll wakes the player up. */
	public static volatile long pokeNs;

	private static final long GRACE_NS = 600_000_000L;
	private static final int STYLES = 5;

	// ---- activation
	private final Option.Bool autoStart;
	private final Option.Num delay;
	private final Option.Key toggleKey;
	private final Option.Bool stopHurt;
	private final Option.Bool ignoreHeld;
	// ---- camera
	private final Option.Choice style;
	private final Option.Num styleEvery;
	private final Option.Choice direction;
	private final Option.Num speed, distance, height, smooth, lens;
	private final Option.Bool avoidWalls;
	// ---- screen
	private final Option.Bool hideHud, bars;
	private final Option.Num barSize;
	private final Option.Col barColour;

	private boolean active, prevToggle, hidHud;
	private boolean hudWas;
	private Perspective previous = Perspective.FIRST_PERSON;
	private long lastInputNs, startedNs, tickNs, lastPoseNs, lastBlendNs, seenPoke;
	private World lastWorld;
	private float blend;
	private double t, theta0;
	private Vec3d pos, focus;
	private final boolean[] prevKeys = new boolean[32];

	public CinematicAfkMod() {
		super("Cinematic AFK Camera", "cinematic_afk",
				"When you are AFK (or press a key) the camera films you like a movie. Any move brings it back.", Category.VISUAL);
		instance = this;

		header("Activation");
		autoStart = bool("Auto Start When AFK", true);
		delay = num("AFK Delay", 30, 5, 600, 5, " s");
		toggleKey = key("Toggle Key", -1);
		stopHurt = bool("Stop When Hurt", true);
		ignoreHeld = bool("Ignore Held Keys", true);

		header("Camera");
		style = choice("Camera Style", 0, "Orbit", "Drone", "Portrait", "Crane", "Side Sweep", "Cycle All");
		styleEvery = num("Change Style Every", 20, 5, 120, 5, " s");
		direction = choice("Direction", 0, "Clockwise", "Counter-clockwise");
		speed = num("Speed", 100, 20, 300, 10, "%");
		distance = num("Distance", 100, 40, 250, 5, "%");
		height = num("Height", 0.6F, -1, 4, 0.1F, " blocks");
		smooth = num("Smoothness", 60, 0, 95, 5, "%");
		lens = num("Lens Zoom", 100, 60, 130, 5, "%");
		avoidWalls = bool("Avoid Walls", true);

		header("Screen");
		hideHud = bool("Hide HUD", true);
		bars = bool("Cinema Bars", true);
		barSize = num("Bars Size", 11, 4, 20, 1, "%");
		barColour = colour("Bars Colour", 0xFF000000);

		styleEvery.visibleIf = () -> style.idx() == STYLES;
		barSize.visibleIf = bars::on;
		barColour.visibleIf = bars::on;
		delay.visibleIf = autoStart::on;
	}

	@Override
	public boolean enabledByDefault() {
		return false; // it takes the camera: the player turns it on
	}

	// ------------------------------------------------------------------ life cycle
	@Override
	protected void onDisable() {
		if (active) {
			stop(true);
		}
		blend = 0F;
	}

	/** Called by the mouse mixins. */
	public static void poke() {
		pokeNs = System.nanoTime();
	}

	public static void pokeLook(double dx, double dy) {
		if (Math.abs(dx) + Math.abs(dy) > 1e-4) {
			pokeNs = System.nanoTime();
		}
	}

	public static boolean camActive() {
		CinematicAfkMod m = instance;
		return m != null && m.enabled && m.active && m.mc.player != null && m.mc.world != null && !FreelookMod.active;
	}

	// ------------------------------------------------------------------ ticking
	@Override
	public void tick() {
		long now = System.nanoTime();
		tickNs = now;
		advanceBlend();
		if (mc.player == null || mc.world == null) {
			if (active) {
				stop(false);
			}
			lastInputNs = now;
			return;
		}
		if (active && mc.world != lastWorld) {
			stop(false); // you changed world while it was filming you
		}
		lastWorld = mc.world;

		boolean tg = mc.currentScreen == null && Keys.down(toggleKey.code());
		if (tg && !prevToggle) {
			if (active) {
				stop(true);
				lastInputNs = now;
			} else if (!blocked()) {
				start(now);
			}
		}
		prevToggle = tg;

		if (blocked()) {
			if (active) {
				stop(true);
			}
			lastInputNs = now;
			pollInput(); // keeps the key states fresh
			seenPoke = pokeNs;
			return;
		}

		boolean input = pollInput();
		long p = pokeNs;
		if (p != seenPoke) {
			seenPoke = p;
			input = true;
		}
		boolean grace = active && now - startedNs < GRACE_NS;
		if (input && !grace) {
			lastInputNs = now;
			if (active) {
				stop(true);
				return;
			}
		}

		if (active) {
			if (mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
				stop(false); // you pressed the perspective key yourself
			} else if (hidHud && !mc.options.hudHidden) {
				stop(true); // you pressed F1
			} else if (stopHurt.on() && mc.player.hurtTime > 0) {
				stop(true);
				lastInputNs = now;
			}
		} else if (autoStart.on() && now - lastInputNs >= (long) (delay.f() * 1e9)) {
			start(now);
		}
	}

	@Override
	public void frame() {
		advanceBlend();
	}

	/** Other cameras / screens that must not be mixed with this one. */
	private boolean blocked() {
		if (mc.currentScreen != null || mc.getOverlay() != null || mc.player == null || !mc.player.isAlive() || mc.player.isSleeping()) {
			return true;
		}
		return FreelookMod.active || dev.solclient.gui.PhotoModeScreen.active() || dev.solclient.fightcam.Replay.active()
				|| FightCamMod.camActive();
	}

	/** @return true when the player pressed something new (a key that was not held before). */
	private boolean pollInput() {
		boolean any = false;
		var o = mc.options;
		KeyBinding[] ks = {o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey, o.useKey,
				o.dropKey, o.swapHandsKey, o.pickItemKey, o.inventoryKey};
		int n = 0;
		for (KeyBinding k : ks) {
			any |= track(n++, k.isPressed());
		}
		for (KeyBinding k : o.hotbarKeys) {
			if (n < prevKeys.length) {
				any |= track(n++, k.isPressed());
			}
		}
		return any;
	}

	private boolean track(int i, boolean down) {
		boolean fresh = down && (!ignoreHeld.on() || !prevKeys[i]);
		prevKeys[i] = down;
		return fresh;
	}

	private void start(long now) {
		active = true;
		startedNs = now;
		lastInputNs = now;
		pos = null;
		focus = null;
		lastPoseNs = 0;
		t = 0;
		theta0 = Math.toRadians(mc.player.getYaw()) + Math.PI; // starts behind you
		previous = mc.options.getPerspective();
		mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		if (hideHud.on()) {
			hudWas = mc.options.hudHidden;
			mc.options.hudHidden = true;
			hidHud = true;
		}
	}

	private void stop(boolean restorePerspective) {
		if (!active) {
			return;
		}
		active = false;
		pos = null;
		focus = null;
		if (restorePerspective) {
			mc.options.setPerspective(previous);
		}
		if (hidHud) {
			mc.options.hudHidden = hudWas;
			hidHud = false;
		}
	}

	private void advanceBlend() {
		long now = System.nanoTime();
		double dt = lastBlendNs == 0 ? 0 : Math.min(0.1, (now - lastBlendNs) / 1e9);
		lastBlendNs = now;
		float target = active ? 1F : 0F;
		float step = (float) (dt / 0.9);
		blend = target > blend ? Math.min(target, blend + step) : Math.max(target, blend - step);
	}

	// ------------------------------------------------------------------ camera
	/** @return x, y, z, yaw, pitch of the camera for this frame. */
	public static double[] pose() {
		CinematicAfkMod m = instance;
		return m == null || !m.active || m.mc.player == null ? null : m.computePose();
	}

	/** The FOV is eased in and out with the cinema bars. */
	public static float fovMultiplier() {
		CinematicAfkMod m = instance;
		if (m == null || !m.enabled || m.blend <= 0.001F) {
			return 1F;
		}
		float e = m.blend * m.blend * (3F - 2F * m.blend);
		return 1F + (m.lens.f() / 100F - 1F) * e;
	}

	private int currentStyle() {
		int s = style.idx();
		if (s < STYLES) {
			return s;
		}
		double el = (System.nanoTime() - startedNs) / 1e9;
		return (int) (el / Math.max(5F, styleEvery.f())) % STYLES;
	}

	private double[] computePose() {
		PlayerEntity me = mc.player;
		long now = System.nanoTime();
		double dt = lastPoseNs == 0 ? 0.016 : Math.min(0.25, (now - lastPoseNs) / 1e9);
		lastPoseNs = now;
		float frac = (float) Math.max(0, Math.min(1, (now - tickNs) / 5e7));
		double el = (now - startedNs) / 1e9;
		t += dt * speed.f() / 100.0;
		double base = 4.5 * distance.f() / 100.0;
		double dir = direction.idx() == 0 ? 1 : -1;
		double yaw = Math.toRadians(me.getYaw());
		double h = me.getHeight();
		Vec3d feet = me.getLerpedPos(frac);

		double fy = 0.6; // where on your body the camera looks (0 = feet, 1 = head top)
		double theta, r, yo;
		switch (currentStyle()) {
			case 1: // drone: floats around you, drifting
				theta = theta0 + dir * (0.9 * Math.sin(0.18 * t) + 0.04 * t);
				r = base * (1 + 0.22 * Math.sin(0.27 * t + 1));
				yo = height.f() + 0.5 + 0.9 * Math.sin(0.21 * t + 2);
				break;
			case 2: // portrait: in front of you, slowly pushing in and out
				fy = 0.88;
				theta = yaw + dir * 0.35 * Math.sin(0.22 * t);
				r = base * (0.62 + 0.18 * Math.sin(0.17 * t));
				yo = height.f() * 0.5 + 0.15 + 0.1 * Math.sin(0.3 * t);
				break;
			case 3: { // crane: rises and falls while it turns
				double lift = 0.5 - 0.5 * Math.cos(0.12 * t);
				fy = 0.55;
				theta = theta0 + dir * 0.25 * t;
				r = base * (1 + 0.35 * lift);
				yo = height.f() + 0.3 + 2.8 * lift;
				break;
			}
			case 4: // side sweep: from one side of you to the other
				fy = 0.7;
				theta = yaw + dir * (Math.PI / 2 + 0.9 * Math.sin(0.2 * t));
				r = base * 0.9;
				yo = height.f() + 0.3;
				break;
			default: // orbit
				theta = theta0 + dir * 0.35 * t;
				r = base;
				yo = height.f() + 0.4;
		}
		Vec3d fp = feet.add(0, h * fy, 0);
		Vec3d want = fp.add(-Math.sin(theta) * r, yo, Math.cos(theta) * r);

		if (avoidWalls.on()) {
			try {
				BlockHitResult hr = mc.world.raycast(new RaycastContext(fp, want, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, me));
				if (hr != null && hr.getType() != HitResult.Type.MISS) {
					Vec3d d = want.subtract(fp);
					double l = d.length();
					Vec3d back = l > 1e-4 ? d.multiply(0.3 / l) : Vec3d.ZERO;
					want = hr.getPos().subtract(back);
				}
			} catch (RuntimeException ignored) {
				// keep the wanted position
			}
		}

		if (pos == null) {
			// the camera starts in your head and pulls out
			Vec3d eye = feet.add(0, h * 0.9, 0);
			pos = eye;
			focus = eye.add(Vec3d.fromPolar(me.getPitch(), me.getYaw()).multiply(2));
		}
		double tau = (0.02 + smooth.f() / 100.0 * 0.55) * (1 + 1.5 * Math.max(0, 1 - el / 3.0));
		double k = 1 - Math.exp(-dt / Math.max(0.02, tau));
		pos = pos.add(want.subtract(pos).multiply(k));
		focus = focus.add(fp.subtract(focus).multiply(k));

		Vec3d v = focus.subtract(pos);
		double hz = Math.sqrt(v.x * v.x + v.z * v.z);
		double yawDeg = Math.toDegrees(Math.atan2(-v.x, v.z));
		double pitchDeg = -Math.toDegrees(Math.atan2(v.y, hz));
		return new double[] {pos.x, pos.y, pos.z, yawDeg, pitchDeg};
	}

	// ------------------------------------------------------------------ screen
	/** Drawn from SolClient.renderHud, before the "HUD hidden" check, so the bars show with the HUD hidden. */
	public static void drawOverlay(DrawContext c) {
		CinematicAfkMod m = instance;
		if (m != null && m.enabled) {
			m.drawBars(c);
		}
	}

	private void drawBars(DrawContext c) {
		advanceBlend();
		if (blend <= 0.001F || mc.player == null || mc.getWindow() == null) {
			return;
		}
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		float e = blend * blend * (3F - 2F * blend);
		int bh = 0;
		if (bars.on()) {
			bh = Math.round(h * barSize.f() / 100F * e);
			if (bh > 0) {
				int col = barColour.c();
				Gfx.fill(c, 0, 0, w, bh, col);
				Gfx.fill(c, 0, h - bh, w, h, col);
			}
		}
	}
}
