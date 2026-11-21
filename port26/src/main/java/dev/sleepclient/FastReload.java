package dev.sleepclient;

import net.minecraft.client.Minecraft;

/** While an instant pack reload runs, the game stays playable: the loading overlay no longer takes the mouse and keyboard. */
public final class FastReload {

	/** The loading overlay of the instant reload that is running, or null. */
	public static volatile Object overlay;

	private FastReload() {
	}

	public static boolean passive() {
		Object o = overlay;
		return o != null && dev.sleepclient.compat.Mc.overlay(Minecraft.getInstance()) == o;
	}
}
