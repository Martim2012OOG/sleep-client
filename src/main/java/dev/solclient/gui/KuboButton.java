package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.SolClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The buttons of the Right Shift menu (the shop's Buy / Equip / Unequip...), for every other Sleep screen, so they all
 * look the same: with "Button Style: Classic" the raised slate button with its dark bottom edge, otherwise the soft
 * rounded one. Neutral colours (tiles / off) become the slate colour, coloured ones (accent, green, red) stay.
 */
public final class KuboButton {

	private KuboButton() {
	}

	public static boolean classic() {
		return SolClient.settings == null || SolClient.settings.buttonStyle.idx() * 0 == 0;
	}

	private static int slate(int col, boolean hover) {
		if (col == Theme.TILE || col == Theme.OFF_BG || col == Theme.SUB || col == Theme.CARD) {
			return hover ? Gfx.BTN_HOVER : Gfx.BTN_BODY;
		}
		if (col == Theme.TILE_HOVER || col == Theme.OFF_BG_HOVER || col == Theme.CARD_HOVER) {
			return Gfx.BTN_HOVER;
		}
		return hover ? Gfx.blend(col | 0xFF000000, 0xFFFFFFFF, 0.12F) : col | 0xFF000000;
	}

	/**
	 * The background of a neutral button, exactly as the pause / main menu draws it (follows "Button Style"):
	 * a = hover amount 0..1, selected = the open one of a group (pressed in / accent).
	 */
	public static void bg(DrawContext c, int x, int y, int w, int h, float a, boolean selected) {
		int style = SolClient.settings == null ? 0 : SolClient.settings.buttonStyle.idx() * 0;
		int accent = Theme.accent();
		if (selected) {
			if (style == 0) {
				Gfx.button(c, x, y, w, h, false, true);
			} else {
				Gfx.rrect(c, x, y, w, h, style == 1 ? 8 : 4, Gfx.blend(accent | 0xFF000000, 0xFF000000, 0.45F));
				Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, style == 1 ? 7 : 3, Gfx.blend(accent | 0xFF000000, 0xFF000000, 0.3F));
			}
			return;
		}
		switch (style) {
			case 1 -> Gfx.rrectBorder(c, x, y, w, h, 8, Gfx.blend(0xFF16181D, 0xFF20232A, a), Gfx.blend(0x40FFFFFF, accent, a));
			case 2 -> {
				Gfx.rrect(c, x, y, w, h, 4, a > 0.5F ? accent : 0x90FFFFFF);
				Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, 3, Gfx.blend(0xFF0F1116, 0xFF1A1D25, a));
			}
			case 3 -> {
				if (a > 0.01F) {
					Gfx.rrect(c, x, y, w, h, 4, Gfx.withAlpha(0xFFFFFF, Math.round(0x2E * a)));
				}
			}
			default -> Gfx.button(c, x, y, w, h, a > 0.5F, false);
		}
	}

	/** Draws the button (hover = the mouse is on it). */
	public static void draw(DrawContext c, int x, int y, int w, int h, String label, boolean hover, int bg, int bgHover, int textCol) {
		if (classic()) {
			Gfx.buttonBody(c, x, y, w, h, hover ? slate(bgHover, true) : slate(bg, false), false);
			if (textCol == Theme.DIM && (bg == Theme.TILE || bg == Theme.OFF_BG)) {
				textCol = Theme.TEXT;
			}
		} else {
			Gfx.rrect(c, x, y, w, h, 7, hover ? bgHover : bg);
		}
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 9) / 2, textCol);
	}

	/** Main action (accent colour). */
	public static void primary(DrawContext c, int x, int y, int w, int h, String label, boolean hover) {
		draw(c, x, y, w, h, label, hover, Theme.accent(), Theme.accentHover(), 0xFFFFFFFF);
	}

	/** Neutral button. */
	public static void neutral(DrawContext c, int x, int y, int w, int h, String label, boolean hover) {
		draw(c, x, y, w, h, label, hover, Theme.OFF_BG, Theme.OFF_BG_HOVER, Theme.TEXT);
	}

	/** Dangerous action (stop, ban). */
	public static void danger(DrawContext c, int x, int y, int w, int h, String label, boolean hover) {
		draw(c, x, y, w, h, label, hover, 0xFF8E2A2F, 0xFFB3363B, 0xFFFFFFFF);
	}
}
