package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Hide Armor: don't draw armour on yourself and / or other players (only on your screen). */
public class HideArmorMod extends Mod {

	public static HideArmorMod instance;

	private final Option.Bool mine = bool("My Armour", true);
	private final Option.Bool others = bool("Other Players' Armour", false);

	public HideArmorMod() {
		super("Hide Armor", "hide_armor", "Hides armour on you and / or other players (only on your screen).", Category.VISUAL);
		instance = this;
	}

	/** True when the armour of the player with this entity id should not be drawn. */
	public static boolean hides(int entityId) {
		if (instance == null || !instance.enabled) {
			return false;
		}
		var p = instance.mc.player;
		boolean me = p != null && p.getId() == entityId;
		return me ? instance.mine.on() : instance.others.on();
	}
}
