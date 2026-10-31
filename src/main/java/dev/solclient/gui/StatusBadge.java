package dev.solclient.gui;

import dev.solclient.Gfx;
import net.minecraft.client.gui.DrawContext;

/**
 * Small status mark on the corner of a head, like LabyMod's: a dark frame with a coloured mark inside.
 * Online = green square, Away = yellow moon, Busy = red with a bar, Mobile = green phone, Offline = grey ring.
 */
public final class StatusBadge {

	public enum Status {
		ONLINE(0xFF3BA55D, "Online"), AWAY(0xFFFAA61A, "Away"), BUSY(0xFFED4245, "Busy"), MOBILE(0xFF3BA55D, "On the phone"),
		OFFLINE(0xFF747F8D, "Offline"), INVISIBLE(0xFF747F8D, "Invisible");

		public final int colour;
		public final String label;

		Status(int colour, String label) {
			this.colour = colour;
			this.label = label;
		}
	}

	private StatusBadge() {
	}

	/** Badge whose bottom-right corner is at (right, bottom). size ~ 7-9 for 20-28 px heads. bg = colour around it. */
	public static void draw(DrawContext c, Status s, int right, int bottom, int size, int bg) {
		if (s == Status.MOBILE) {
			// phone: taller than wide
			int w = Math.max(5, size * 2 / 3), h = size + 2, x = right - w, y = bottom - h;
			Gfx.rrect(c, x - 1, y - 1, w + 2, h + 2, 2, bg);
			Gfx.rrect(c, x, y, w, h, 2, s.colour);
			Gfx.rrect(c, x + 1, y + 1, w - 2, h - 4, 1, Gfx.blend(s.colour, 0xFF000000, 0.55F));
			c.fill(x + w / 2, y + h - 2, x + w / 2 + 1, y + h - 1, 0xFFFFFFFF);
			return;
		}
		int x = right - size, y = bottom - size;
		Gfx.rrect(c, x - 1, y - 1, size + 2, size + 2, 3, bg);
		switch (s) {
			case ONLINE -> Gfx.rrect(c, x, y, size, size, 2, s.colour);
			case AWAY -> {
				Gfx.circle(c, x + size / 2F, y + size / 2F, size / 2F, s.colour);
				Gfx.circle(c, x + size * 0.3F, y + size * 0.3F, size * 0.32F, bg); // bite = moon
			}
			case BUSY -> {
				Gfx.rrect(c, x, y, size, size, 2, s.colour);
				int bh = Math.max(1, size / 4);
				c.fill(x + 2, y + size / 2 - bh / 2, x + size - 2, y + size / 2 - bh / 2 + bh, bg | 0xFF000000);
			}
			default -> {
				Gfx.rrect(c, x, y, size, size, 2, s.colour);
				Gfx.rrect(c, x + 2, y + 2, size - 4, size - 4, 1, bg);
			}
		}
	}
}
