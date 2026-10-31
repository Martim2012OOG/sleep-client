package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import net.minecraft.util.math.MathHelper;

public class ZoomMod extends Mod {

	public static ZoomMod instance;

	private final Option.Key key = key("Zoom Key", 67);
	private final Option.Key zoomOutKey = key("Zoom Out", 45);
	private final Option.Key zoomInKey = key("Zoom In", 61);
	private final Option.Bool cinematic = bool("Cinematic", true);
	private final Option.Bool reduceSensitivity = bool("Reduce Sensitivity", false);
	private final Option.Bool scrolling = bool("Scroll-to-Zoom", true);
	private final Option.Bool smooth = bool("Smooth Animation", true);
	private final Option.Num factor = num("Factor", 4, 2, 32, 1, "x");

	private float currentFactor = 1;
	private float animatedFactor = 1;
	private long lastFrame = System.nanoTime();
	private boolean active;
	private double lastSensitivity;
	/** True only while Zoom itself switched the cinematic camera on (so it never leaves it stuck). */
	private boolean zoomSetSmooth;
	private boolean middleWasDown;

	public ZoomMod() {
		super("Zoom", "zoom", "Zoom in when pressing a button.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	protected void onDisable() {
		if (active) {
			stop();
		}
		animatedFactor = 1;
		currentFactor = 1;
	}

	private void start() {
		active = true;
		lastSensitivity = mc.options.getMouseSensitivity().getValue();
		resetFactor();
		zoomSetSmooth = cinematic.on();
		if (cinematic.on()) {
			mc.options.smoothCameraEnabled = true;
		}
	}

	private void stop() {
		active = false;
		setFactor(1);
		mc.options.getMouseSensitivity().setValue(lastSensitivity);
		releaseSmooth();
	}

	/** Gives the camera back: smooth only if the Smooth Camera mod is on, never because of an old zoom. */
	private void releaseSmooth() {
		if (zoomSetSmooth || cinematic.on()) {
			mc.options.smoothCameraEnabled = SmoothCameraMod.wants();
		}
		zoomSetSmooth = false;
	}

	@Override
	public void tick() {
		if (active && (mc.player == null || mc.world == null)) {
			stop(); // left the world while zooming: do not keep the smooth camera
		}
		if (!active && zoomSetSmooth) {
			releaseSmooth();
		}
		boolean down = mc.currentScreen == null && Keys.down(key.code());
		if (down && !active) {
			start();
		} else if (!down && active) {
			stop();
		}
		if (active) {
			if (Keys.down(zoomOutKey.code())) {
				zoom(false);
			} else if (Keys.down(zoomInKey.code())) {
				zoom(true);
			}
			boolean middle = Keys.mouseDown(2);
			if (middle && !middleWasDown && scrolling.on()) {
				resetFactor();
			}
			middleWasDown = middle;
		}
	}

	@Override
	public void frame() {
		long now = System.nanoTime();
		float dt = (now - lastFrame) / 1_000_000_000F;
		lastFrame = now;
		if (smooth.on()) {
			float t = 1F - (float) Math.pow(0.0005, dt);
			animatedFactor += (currentFactor - animatedFactor) * MathHelper.clamp(t, 0F, 1F);
		} else {
			animatedFactor = currentFactor;
		}
	}

	/** Multiplier applied to the field of view (see GameRendererMixin). */
	public static float fovMultiplier() {
		if (instance == null || !instance.enabled) {
			return 1F;
		}
		return instance.animatedFactor;
	}

	/** Called from MouseMixin; returns true to swallow the scroll. */
	public static boolean onScroll(double vertical) {
		if (instance == null || !instance.enabled || !instance.active || !instance.scrolling.on()) {
			return false;
		}
		if (vertical < 0) {
			instance.zoom(false);
		} else if (vertical > 0) {
			instance.zoom(true);
		}
		return true;
	}

	private void resetFactor() {
		setFactor(1 / factor.f());
	}

	private void setFactor(float f) {
		currentFactor = f;
		if (reduceSensitivity.on() && active) {
			mc.options.getMouseSensitivity().setValue(lastSensitivity * currentFactor);
		}
	}

	private void zoom(boolean in) {
		float div = 1 / currentFactor;
		float changed = in ? div + 1F : div - 1F;
		setFactor(MathHelper.clamp(1 / Math.max(changed, 2F), 0F, 0.5F));
	}
}
