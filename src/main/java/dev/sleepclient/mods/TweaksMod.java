package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

public class TweaksMod extends Mod {

	public static TweaksMod instance;

	public final Option.Bool fullbright = bool("Fullbright", false);
	public final Option.Bool arabicNumerals = bool("Arabic Numerals", false);

	public TweaksMod() {
		super("Tweaks", "tweaks", "Various game tweaks.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean arabic() {
		return instance != null && instance.enabled && instance.arabicNumerals.on();
	}
}
