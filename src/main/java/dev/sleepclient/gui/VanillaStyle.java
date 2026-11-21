package dev.sleepclient.gui;

import dev.sleepclient.SleepClient;
import net.minecraft.client.gui.DrawContext;

/** Plegumo look for Minecraft's own menus (Select World, Multiplayer, Options...): buttons, lists, text boxes, background. */
public final class VanillaStyle {

	private VanillaStyle() {
	}

	public static boolean on() {
		return SleepClient.settings == null || SleepClient.settings.restyleMenus.on();
	}

	public static void background(DrawContext c, int w, int h) {
		if (Season.current() != Season.NONE && (SleepClient.settings == null || SleepClient.settings.seasonalBackground.on())) {
			Season.background(c, w, h);
		} else {
			c.fill(0, 0, w, h, 0x50080A10);
		}
		dev.sleepclient.Gfx.gradientV(c, 0, 0, w, h / 3, 0x50000000, 0x00000000);
	}
}
