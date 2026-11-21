package dev.sleepclient.addons.bridge;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** The timer panel (best time, your time, blocks...) shown on the right while bridge practice is active, plus the R reset key. */
public final class BridgeHud {

	private static boolean rArmed = true;

	private BridgeHud() {
	}

	private static String fmt(double seconds) {
		return seconds < 0 ? "--" : String.format(Locale.ROOT, "%.3f", seconds);
	}

	/** Client tick: R sends you back to the start. */
	public static void tick(MinecraftClient mc) {
		if (!BridgeSession.active) {
			return;
		}
		boolean down = Keys.down(82); // GLFW_KEY_R
		if (!down) {
			rArmed = true;
		} else if (rArmed && mc.currentScreen == null && mc.player != null) {
			rArmed = false;
			BridgeSession.resetRequested = true;
		}
	}

	public static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!BridgeSession.active || mc.player == null) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		int w = 128, h = 112;
		int x = sw - w - 6, y = sh / 2 - h / 2;
		Gfx.rrect(c, x, y, w, h, 6, 0xB00E0F13);

		int state = BridgeSession.state;
		double current;
		if (state == 1) {
			current = (System.nanoTime() - BridgeSession.startNano) / 1.0E9;
		} else if (state == 2) {
			current = BridgeSession.lastTime;
		} else {
			current = 0;
		}

		int tx = x + 8, ty = y + 7;
		Gfx.text(c, "Bridge Practice", tx, ty, 0xFFFFAA00);
		Gfx.text(c, BridgeSession.title, tx, ty + 11, 0xFF8C90A0);
		Gfx.text(c, "Best Time: " + fmt(BridgeSession.best), tx, ty + 26, 0xFF55FFFF);
		Gfx.text(c, "Your Time: " + fmt(current), tx, ty + 37, state == 2 ? 0xFFFFFF55 : 0xFF55FF55);
		Gfx.text(c, "Blocks: " + BridgeSession.blocks, tx, ty + 48, 0xFFFFFFFF);
		Gfx.text(c, String.format(Locale.ROOT, "Speed: %.2f m/s", BridgeSession.speed), tx, ty + 59, 0xFFFFFFFF);
		Gfx.text(c, "Attempts: " + BridgeSession.attempts + "  Falls: " + BridgeSession.falls, tx, ty + 70, 0xFFAAAAAA);
		Gfx.text(c, "R = reset", tx, ty + 85, 0xFF8C90A0);
	}
}
