package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Stretches the picture to another aspect ratio (4:3 stretched, 16:10, ultra-wide...). */
public class AspectRatioMod extends Mod {

	public static AspectRatioMod instance;
	private static final float[] RATIOS = {4F / 3F, 5F / 4F, 16F / 10F, 16F / 9F, 21F / 9F, 32F / 9F};
	private final Option.Choice preset = choice("Ratio", 0, "4:3", "5:4", "16:10", "16:9", "21:9", "32:9", "Custom");
	private final Option.Num custom = num("Custom ratio", 1.78F, 0.5F, 4F, 0.01F, "");

	public AspectRatioMod() {
		super("Aspect Ratio", "aspect_ratio", "Change the aspect ratio of the game picture (stretched 4:3, ultra-wide...).", Category.VISUAL);
		instance = this;
	}

	/** The aspect ratio the camera uses; 'real' when the mod is off. */
	public static float apply(float real) {
		if (instance == null || !instance.enabled) {
			return real;
		}
		int i = instance.preset.idx();
		return i >= 0 && i < RATIOS.length ? RATIOS[i] : instance.custom.f();
	}
}
