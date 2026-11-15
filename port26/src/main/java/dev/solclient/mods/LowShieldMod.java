package dev.solclient.mods;

import dev.solclient.option.Option;

/** Holds your shield lower so it covers less of the screen. */
public class LowShieldMod extends Mod {

	public static LowShieldMod instance;
	private final Option.Num height = num("Shield Height", 0.4F, 0, 1, 0.05F, "");

	public LowShieldMod() {
		super("Low Shield", "low_shield", "Your shield covers less of the screen when blocking.", Category.VISUAL);
		instance = this;
	}

	public static float apply(float equip) {
		float v = (instance == null || !instance.enabled) ? equip : equip + (1F - instance.height.f()) * 0.5F;
		return v + BactroTweaksMod.shieldDown();
	}
}
