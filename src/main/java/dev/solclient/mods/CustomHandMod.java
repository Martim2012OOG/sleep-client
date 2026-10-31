package dev.solclient.mods;

import dev.solclient.option.Option;

/** Move and resize the item in your hand. */
public class CustomHandMod extends Mod {

	public static CustomHandMod instance;
	public final Option.Num x = num("X", 0, -1, 1, 0.05F, "");
	public final Option.Num y = num("Y", 0, -1, 1, 0.05F, "");
	public final Option.Num z = num("Z", 0, -1, 1, 0.05F, "");
	public final Option.Num size = num("Size", 1, 0.3F, 1.5F, 0.05F, "x");

	public CustomHandMod() {
		super("Custom Hand", "custom_hand", "Move and resize the item in your hand.", Category.VISUAL);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
