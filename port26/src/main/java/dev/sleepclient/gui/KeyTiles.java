package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Keystrokes look for small HUDs (Session Stats, Daily Quests...): flat translucent tiles, small corner radius,
 * a value in white and a tiny label under it. Same default colours as Keystrokes.
 */
public final class KeyTiles {

	public static final int BG = 0x6F000000, TEXT = 0xFFFFFFFF, LABEL = 0xA0FFFFFF;

	private KeyTiles() {
	}

	/** One tile with a value and a small label below it (label may be null). */
	public static void tile(GuiGraphicsExtractor c, float x, float y, float w, float h, float radius, int bg, String value, String label, int text, int labelCol) {
		Gfx.rrectFree(c, x, y, w, h, radius, bg);
		if (label == null || label.isEmpty()) {
			Gfx.textRaw(c, value, Math.round(x + w / 2F - Gfx.twRaw(value) / 2F), Math.round(y + h / 2F - 4), text);
			return;
		}
		int vy = Math.round(y + h / 2F - 8);
		Gfx.textRaw(c, value, Math.round(x + w / 2F - Gfx.twRaw(value) / 2F), vy, text);
		c.pose().pushMatrix();
		float s = 0.6F;
		float lw = Gfx.twRaw(label) * s;
		c.pose().translate(x + w / 2F - lw / 2F, vy + 10.5F);
		c.pose().scale(s, s);
		Gfx.textRaw(c, label, 0, 0, labelCol);
		c.pose().popMatrix();
	}

	/** A wide tile: text on the left, a thin progress line at the bottom (0..1, negative = none). */
	public static void bar(GuiGraphicsExtractor c, float x, float y, float w, float h, float radius, int bg, String left, String right, float progress,
			int text, int fill) {
		Gfx.rrectFree(c, x, y, w, h, radius, bg);
		int ty = Math.round(y + h / 2F - 4 - (progress >= 0 ? 1 : 0));
		Gfx.textRaw(c, left, Math.round(x + 5), ty, text);
		if (right != null && !right.isEmpty()) {
			Gfx.textRaw(c, right, Math.round(x + w - 5 - Gfx.twRaw(right)), ty, Gfx.withAlpha(text, 0xA0));
		}
		if (progress >= 0) {
			float pw = (w - 10) * Math.max(0F, Math.min(1F, progress));
			Gfx.rrectFree(c, x + 5, y + h - 4, w - 10, 1.5F, 0.75F, 0x30FFFFFF);
			if (pw > 0.5F) {
				Gfx.rrectFree(c, x + 5, y + h - 4, pw, 1.5F, 0.75F, fill);
			}
		}
	}
}
