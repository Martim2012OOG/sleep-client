package dev.sleepclient.mods;

/** The "cinematic camera" of Minecraft's zoom, but all the time: smooth mouse movement. */
public class SmoothCameraMod extends Mod {

	public static SmoothCameraMod instance;

	public SmoothCameraMod() {
		super("Smooth Camera", "smooth_camera", "Smooth, cinematic mouse movement all the time.", Category.VISUAL);
		instance = this;
	}

	/** True only when the player really asked for the smooth camera (this mod is switched on). */
	public static boolean wants() {
		return instance != null && instance.enabled;
	}

	@Override
	public void tick() {
		if (mc.options != null && !mc.options.smoothCamera) {
			mc.options.smoothCamera = true;
		}
	}

	@Override
	protected void onDisable() {
		if (mc.options != null) {
			mc.options.smoothCamera = false;
		}
	}
}
