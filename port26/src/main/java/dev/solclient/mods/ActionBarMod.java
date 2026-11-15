package dev.solclient.mods;

import dev.solclient.option.Option;

/** Hide the text above your hotbar (action bar). */
public class ActionBarMod extends Mod {

	public static ActionBarMod instance;
	private final Option.Bool hide = bool("Hide Action Bar", true);

	public ActionBarMod() {
		super("Action Bar", "action_bar", "Hide the text above your hotbar.", Category.VISUAL);
		instance = this;
	}

	public static boolean hidden() {
		return instance != null && instance.enabled && instance.hide.on();
	}
}
