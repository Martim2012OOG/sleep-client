package dev.solclient.addons;

import net.minecraft.client.gui.screen.Screen;

/**
 * One entry of the "Add-ons" tab (left sidebar of the Sleep menu).
 * To add a new one: implement this interface and call {@link Addons#register(Addon)} in {@link Addons#init()}.
 */
public interface Addon {

	/** Unique short id (used for ordering / future settings), e.g. "pvp_practice". */
	String id();

	/** Card title. */
	String name();

	/** One-line description shown on the card. */
	String description();

	/** Small tag on the card (e.g. "PvP", "Training"). */
	default String tag() {
		return "Add-on";
	}

	/** Name of a 64x64 white-on-transparent PNG in assets/sol_client/textures/gui/icon (without ".png"), or "" for none. */
	default String icon() {
		return "";
	}

	/** Opens the add-on. {@code parent} is the screen to come back to. */
	void open(Screen parent);

	/**
	 * A screen drawn inside the Sleep menu's panel (like the mods' settings) instead of opening on its own, or null
	 * to use {@link #open}. Calling {@code client.setScreen(back)} from it closes it again.
	 */
	default Screen embed(Screen back) {
		return null;
	}
}
