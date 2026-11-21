package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Any field of view, past the vanilla slider (30 to 150). */
public class FovChangerMod extends Mod {

	public static FovChangerMod instance;
	private final Option.Num fov = num("FOV", 90, 30, 150, 1, "");

	public FovChangerMod() {
		super("FOV Changer", "fov_changer", "Set any field of view.", Category.VISUAL);
		instance = this;
	}

	/** Multiplier applied to the game's FOV (vanilla setting -> chosen value). */
	public static float multiplier() {
		if (instance == null || !instance.enabled) return 1F;
		int base = instance.mc.options.fov().get();
		return instance.fov.f() / Math.max(1, base);
	}
}
