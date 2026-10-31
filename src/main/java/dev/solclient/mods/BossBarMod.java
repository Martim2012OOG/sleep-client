package dev.solclient.mods;

import dev.solclient.option.Option;

/** Hide or resize the boss bar. */
public class BossBarMod extends Mod {

	public static BossBarMod instance;
	public final Option.Bool hide = bool("Hide Boss Bar", false);
	public final Option.Num scale = num("Scale", 100, 50, 150, 5, "%");
	public final Option.Num offset = num("Move Down", 0, 0, 100, 2, "px");

	public BossBarMod() {
		super("Boss Bar", "boss_bar", "Hide, resize or move the boss bar.", Category.VISUAL);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
