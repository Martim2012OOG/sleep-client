package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;

/** See in the dark: maximum brightness without night vision. */
public class FullbrightMod extends Mod {

	public static FullbrightMod instance;
	private final Option.Num level = num("Brightness", 100, 10, 100, 5, "%");
	private final Option.Key toggleKey = key("Toggle Key", -1);
	private boolean keyDown;

	public FullbrightMod() {
		super("Fullbright", "fullbright", "See everything in caves and at night.", Category.VISUAL);
		instance = this;
	}

	/** Gamma used by the lightmap instead of the video setting (null = vanilla). */
	/** The gamma option of the game (set once by FullbrightGammaCaptureMixin when the options are made). */
	public static volatile Object gammaOption;
	/** options.txt is being written: give the real value. */
	public static volatile boolean saving;

	public static Double gamma() {
		Double boost = BactroTweaksMod.boostGamma();
		if (boost != null && (instance == null || !instance.enabled)) {
			return boost;
		}
		if (instance != null && instance.enabled) {
			return 1.0 + 14.0 * instance.level.f() / 100.0;
		}
		// the old "Fullbright" switch of the Tweaks card
		if (TweaksMod.instance != null && TweaksMod.instance.enabled && TweaksMod.instance.fullbright.on()) {
			return 15.0;
		}
		return null;
	}

	/** The toggle key works even while the mod is off, so it is polled from the tick of the client. */
	public static void pollKey() {
		if (instance == null || instance.toggleKey.code() < 0) {
			return;
		}
		boolean d = dev.solclient.compat.Mc.screen(instance.mc) == null && Keys.down(instance.toggleKey.code());
		if (d && !instance.keyDown) {
			instance.toggle();
			dev.solclient.Config.save();
		}
		instance.keyDown = d;
	}
}
