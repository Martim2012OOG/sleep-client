package dev.solclient.mods;

import dev.solclient.option.Option;

/** Lowers the first person fire overlay so it covers less of the screen. */
public class LowFireMod extends Mod {

	public static LowFireMod instance;
	private final Option.Num height = num("Fire Height", 0.5F, 0, 2, 0.05F, "");

	public LowFireMod() {
		super("Low Fire", "low_fire", "The fire overlay covers less of the screen when you are burning.", Category.VISUAL);
		instance = this;
	}

	/** Used by FireHeightMixin: vertical offset of the first person fire overlay. */
	public static double fireOverlayY(double vanilla) {
		double v = (instance == null || !instance.enabled) ? vanilla : vanilla + (instance.height.f() - 1F) * 0.7D;
		return v + BactroTweaksMod.fireY();
	}
}
