package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Hide the big titles servers put on your screen. */
public class TitlesMod extends Mod {

	public static TitlesMod instance;
	private final Option.Bool hide = bool("Hide Titles", true);

	public TitlesMod() {
		super("Titles", "titles", "Hide the big server titles on your screen.", Category.VISUAL);
		instance = this;
	}

	public static boolean hidden() {
		return instance != null && instance.enabled && instance.hide.on();
	}
}
