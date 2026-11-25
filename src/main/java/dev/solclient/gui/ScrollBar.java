package dev.solclient.gui;

import dev.solclient.Gfx;
import net.minecraft.client.gui.DrawContext;

/** The scrollbar used everywhere in Caos: a slim rounded thumb on a faint track. */
public final class ScrollBar {

	private ScrollBar() {
	}

	/** x = left of the bar (7 px wide), scroll / max = current and maximum scroll in pixels. */
	public static void draw(DrawContext c, int x, int y0, int y1, int contentH, float scroll, float max) {
		int trackH = y1 - y0;
		if (max <= 0 || trackH <= 0) {
			return;
		}
		int barH = Math.max(20, trackH * trackH / Math.max(trackH, contentH));
		int barY = y0 + Math.round((trackH - barH) * Math.max(0F, Math.min(1F, scroll / max)));
		// slim rounded pill on a faint track
		Gfx.rrectF(c, x + 2, y0, 3, trackH, 1.5F, 0x12FFFFFF);
		Gfx.rrectF(c, x + 2, barY, 3, barH, 1.5F, Gfx.withAlpha(Theme.TEXT, 0x70));
	}
}
