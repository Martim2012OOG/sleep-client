package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Hide the big totem animation when a Totem of Undying saves you. */
public class TotemAnimationMod extends Mod {

	public static TotemAnimationMod instance;
	private final Option.Bool hide = bool("Hide Animation", true);

	public TotemAnimationMod() {
		super("Totem Animation", "totem_animation", "Hide the big totem pop animation.", Category.VISUAL);
		instance = this;
	}

	public static boolean hidden() {
		return instance != null && instance.enabled && instance.hide.on();
	}
}
