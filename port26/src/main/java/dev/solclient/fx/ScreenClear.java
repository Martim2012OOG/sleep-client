package dev.solclient.fx;

import net.minecraft.client.gui.screens.Screen;

/** The screen (if any) that wants the world left clear behind it (no dark veil, no blur): set by 3D Screens (extension), read by ScreenVeilMixin. */
public final class ScreenClear {

	public static volatile Screen screen;

	private ScreenClear() {
	}
}
