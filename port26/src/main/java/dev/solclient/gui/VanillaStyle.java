package dev.solclient.gui;

import dev.solclient.SolClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Plegumo look for Minecraft's own menus (Select World, Multiplayer, Options...): buttons, lists, text boxes, background. */
public final class VanillaStyle {

	private VanillaStyle() {
	}

	public static boolean on() {
		return SolClient.settings == null || SolClient.settings.restyleMenus.on();
	}

	public static void background(GuiGraphicsExtractor c, int w, int h) {
		if (Season.current() != Season.NONE && (SolClient.settings == null || SolClient.settings.seasonalBackground.on())) {
			Season.background(c, w, h);
		} else {
			c.fill(0, 0, w, h, 0x50080A10);
		}
		dev.solclient.Gfx.gradientV(c, 0, 0, w, h / 3, 0x50000000, 0x00000000);
	}
}
