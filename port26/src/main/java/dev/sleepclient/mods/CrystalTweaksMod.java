package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** End crystals: calmer bounce (the crystal no longer floats up and down). */
public class CrystalTweaksMod extends Mod {

	public static CrystalTweaksMod instance;
	/** The crystal's resting height when it does not bounce (the middle of its normal bounce). */
	private static final float FLAT = -1.1F;
	private final Option.Num bounce = num("Bounce", 0, 0, 100, 5, "%");

	public CrystalTweaksMod() {
		super("Crystal Tweaks", "crystal_tweaks", "End crystals bounce less (or not at all), easier to hit and to read in fights.", Category.VISUAL);
		instance = this;
	}

	/** The crystal's height offset: 'normal' scaled towards the flat height. */
	public static float yOffset(float normal) {
		if (instance == null || !instance.enabled) {
			return normal;
		}
		return FLAT + (normal - FLAT) * instance.bounce.f() / 100F;
	}
}
