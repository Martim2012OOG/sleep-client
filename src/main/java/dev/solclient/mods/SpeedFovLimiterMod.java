package dev.solclient.mods;

import dev.solclient.option.Option;

/** Speed FOV Limiter: sets how much Speed, Slowness and sprinting may zoom your view in or out. */
public class SpeedFovLimiterMod extends Mod {

	public static SpeedFovLimiterMod instance;

	private final Option.Num maxUp = num("Max Zoom Out (Speed)", 10, 0, 50, 1, "%");
	private final Option.Num maxDown = num("Max Zoom In (Slowness)", 5, 0, 50, 1, "%");

	public SpeedFovLimiterMod() {
		super("Speed FOV Limiter", "speed_fov_limiter", "Limits how much speed and slowness change your field of view.", Category.VISUAL);
		instance = this;
	}

	public static float limit(float multiplier) {
		if (instance == null || !instance.enabled) {
			return multiplier;
		}
		float hi = 1F + instance.maxUp.f() / 100F, lo = 1F - instance.maxDown.f() / 100F;
		return Math.max(lo, Math.min(hi, multiplier));
	}
}
