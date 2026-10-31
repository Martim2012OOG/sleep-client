package dev.solclient.gui;

import dev.solclient.Gfx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/**
 * The checkbox from the user's Radix + motion component:
 * <ul>
 * <li>a small rounded square, muted when off, the theme colour when on (the colour fades over 0.5 s);</li>
 * <li>the tick draws itself along its path (0.2 s, starting 0.2 s after you tick it) and un-draws in 0.2 s;</li>
 * <li>it grows a little (1.05) under the mouse.</li>
 * </ul>
 * Tick path: M4.5 12.75 l6 6 9-13.5 in a 24 x 24 box, stroke 3.5, drawn at 14/20 of the box size.
 */
public final class CheckBox {

	private static final class S {
		boolean on;
		long changedAt;
		float colour, hover;
		long last;
	}

	private static final Map<String, S> STATES = new HashMap<>();
	private static final float[][] PATH = {{4.5F, 12.75F}, {10.5F, 18.75F}, {19.5F, 5.25F}};

	private CheckBox() {
	}

	/** Draws the box with its top-left at (x, y), size x size GUI pixels. */
	public static void draw(DrawContext c, String key, float x, float y, float size, boolean on, boolean hovered) {
		S s = STATES.computeIfAbsent(key, k -> {
			S n = new S();
			n.on = on;
			n.colour = on ? 1F : 0F;
			n.changedAt = 0L; // first frame: already in place, no animation
			return n;
		});
		long now = System.currentTimeMillis();
		if (s.on != on) {
			s.on = on;
			s.changedAt = now;
		}
		float dt = s.last == 0 ? 0F : Math.min(0.1F, (now - s.last) / 1000F);
		s.last = now;
		// colour: 0.5 s ease, hover: quick
		s.colour += ((on ? 1F : 0F) - s.colour) * (1F - (float) Math.exp(-dt * 20F));
		s.hover += ((hovered ? 1F : 0F) - s.hover) * (1F - (float) Math.exp(-dt * 18F));

		// tick progress: checked -> wait 0.2 s, draw in 0.2 s; unchecked -> undraw in 0.2 s
		float p;
		if (s.changedAt == 0L) {
			p = on ? 1F : 0F;
		} else {
			float t = (now - s.changedAt) / 1000F;
			p = on ? clamp01((t - 0.03F) / 0.12F) : 1F - clamp01(t / 0.1F); // the tick follows the click right away
		}
		p = ease(p);

		float scale = 1F + 0.05F * s.hover;
		float sz = size * scale, ox = x - (sz - size) / 2F, oy = y - (sz - size) / 2F;
		int off = 0xFF2A2D35, accent = Theme.accent() | 0xFF000000;
		int bg = Gfx.blend(off, accent, s.colour);
		Gfx.rrectFree(c, ox, oy, sz, sz, Math.max(1.5F, sz * 0.18F), bg);
		if (s.colour < 0.5F && s.hover > 0.01F) {
			Gfx.rrectFree(c, ox, oy, sz, sz, Math.max(1.5F, sz * 0.18F), Gfx.withAlpha(0xFFFFFF, Math.round(0x14 * s.hover)));
		}
		if (p <= 0.001F) {
			return;
		}
		// the tick: 14/20 of the box, centred; stroke 3.5 / 24 of that
		float iconSz = sz * 0.7F, ix = ox + (sz - iconSz) / 2F, iy = oy + (sz - iconSz) / 2F;
		float k = iconSz / 24F, stroke = Math.max(1.1F, 3.5F * k);
		// white tick, or a dark one when the accent is light (Eclipse theme's near-white accent)
		int r = (accent >> 16) & 255, g = (accent >> 8) & 255, b = accent & 255;
		int tick = r * 299 + g * 587 + b * 114 > 175000 ? 0xFF141418 : 0xFFFFFFFF;
		int alpha = Math.round(255 * Math.min(1F, p * 3F)); // opacity rises with the path
		tick = (alpha << 24) | (tick & 0xFFFFFF);
		float len1 = dist(PATH[0], PATH[1]), len2 = dist(PATH[1], PATH[2]), total = len1 + len2;
		float want = total * p;
		stroke(c, ix, iy, k, PATH[0], PATH[1], Math.min(1F, want / len1), stroke, tick);
		if (want > len1) {
			stroke(c, ix, iy, k, PATH[1], PATH[2], (want - len1) / len2, stroke, tick);
		}
	}

	/** A round-capped line from a towards b, 'part' of the way, stamped with small circles. */
	private static void stroke(DrawContext c, float ox, float oy, float k, float[] a, float[] b, float part, float width, int col) {
		float ax = ox + a[0] * k, ay = oy + a[1] * k;
		float bx = ox + (a[0] + (b[0] - a[0]) * part) * k, by = oy + (a[1] + (b[1] - a[1]) * part) * k;
		float len = (float) Math.hypot(bx - ax, by - ay);
		int steps = Math.max(1, (int) Math.ceil(len / Math.max(0.35F, width * 0.3F)));
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			Gfx.circle(c, ax + (bx - ax) * t, ay + (by - ay) * t, width / 2F, col);
		}
	}

	private static float dist(float[] a, float[] b) {
		return (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
	}

	private static float clamp01(float v) {
		return Math.max(0F, Math.min(1F, v));
	}

	private static float ease(float t) {
		return t * t * (3F - 2F * t);
	}
}
