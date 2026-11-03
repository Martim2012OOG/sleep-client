package dev.solclient.gui;

import dev.solclient.Gfx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/**
 * The "neon" checkbox from the user's component:
 * <ul>
 * <li>a dark rounded box with a green border (darker green when off, bright when on, with a soft glow);</li>
 * <li>the tick draws itself along its path and pops a little (1.1) when ticked;</li>
 * <li>when you tick it: 12 particles fly out, 3 rings pulse and 4 sparks flash (0.6 s);</li>
 * <li>while it is on, four thin lights run along its border (one lap every 2 s).</li>
 * </ul>
 * Everything is scaled from the original 30 px box to the size you ask for.
 */
public final class CheckBox {

	private static final class S {
		boolean on;
		long changedAt;
		float colour, hover;
		long last;
	}

	private static final Map<String, S> STATES = new HashMap<>();
	/** The tick path of the component: M3,12.5 l7,7 L21,5 in a 24 x 24 box. */
	private static final float[][] PATH = {{3F, 12.5F}, {10F, 19.5F}, {21F, 5F}};
	private static final float[] PX = {25, -25, 25, -25, 35, -35, 0, 0, 20, -20, 30, -30};
	private static final float[] PY = {-25, -25, 25, 25, 0, 0, 35, -35, -30, 30, 20, -20};
	private static final int PRIMARY = 0xFF00FFAA, PRIMARY_DARK = 0xFF00CC88;

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
		s.colour += ((on ? 1F : 0F) - s.colour) * (1F - (float) Math.exp(-dt * 14F));
		s.hover += ((hovered ? 1F : 0F) - s.hover) * (1F - (float) Math.exp(-dt * 18F));

		float k = size / 30F;
		float bw = Math.max(1F, size * 0.085F);
		float rad = Math.max(1.5F, size * 0.14F);
		float t = s.changedAt == 0L ? 99F : (now - s.changedAt) / 1000F;

		// soft glow around the box (the blur of the component)
		if (s.colour > 0.02F) {
			for (int i = 3; i >= 1; i--) {
				float g = i * k * 1.7F;
				Gfx.rrectFree(c, x - g, y - g, size + 2 * g, size + 2 * g, rad + g, Gfx.withAlpha(0x00FFAA, Math.round(8 * s.colour)));
			}
		}
		// border + body (black with a hint of green when on)
		int border = Gfx.blend(Gfx.blend(PRIMARY_DARK, PRIMARY, s.hover * 0.45F), PRIMARY, s.colour);
		int body = Gfx.blend(0xFF0A0C10, 0xFF082A22, s.colour);
		Gfx.rrectFree(c, x, y, size, size, rad, border);
		Gfx.rrectFree(c, x + bw, y + bw, size - 2 * bw, size - 2 * bw, Math.max(0.5F, rad - bw), body);

		// the tick draws itself
		float p;
		if (s.changedAt == 0L) {
			p = on ? 1F : 0F;
		} else {
			p = on ? clamp01(t / 0.35F) : 1F - clamp01(t / 0.18F);
		}
		p = ease(p);
		if (p > 0.001F) {
			float icon = size * 0.8F * (1F + 0.1F * p);
			float ox = x + (size - icon) / 2F, oy = y + (size - icon) / 2F;
			float kk = icon / 24F, stroke = Math.max(1.1F, 3F * kk);
			float len1 = dist(PATH[0], PATH[1]), len2 = dist(PATH[1], PATH[2]), want = (len1 + len2) * p;
			stroke(c, ox, oy, kk, PATH[0], PATH[1], Math.min(1F, want / len1), stroke, PRIMARY);
			if (want > len1) {
				stroke(c, ox, oy, kk, PATH[1], PATH[2], (want - len1) / len2, stroke, PRIMARY);
			}
		}

		// four lights running along the border while it is on
		if (s.colour > 0.5F) {
			float ph = (now % 2000L) / 2000F;
			int col = Gfx.withAlpha(PRIMARY, Math.round(230 * s.colour));
			float len = size * 0.34F, th = Math.max(1F, k), in = bw * 0.6F;
			for (int e = 0; e < 4; e++) {
				float a = ph * (size + len) - len, b = a + len; // runs from before the edge to past it
				float lo = Math.max(0F, a), hi = Math.min(size, b);
				if (hi <= lo) {
					continue;
				}
				switch (e) {
					case 0 -> Gfx.rrectFree(c, x + lo, y + in, hi - lo, th, 0F, col); // top, left to right
					case 1 -> Gfx.rrectFree(c, x + size - in - th, y + lo, th, hi - lo, 0F, col); // right, top to bottom
					case 2 -> Gfx.rrectFree(c, x + size - hi, y + size - in - th, hi - lo, th, 0F, col); // bottom, right to left
					default -> Gfx.rrectFree(c, x + in, y + size - hi, th, hi - lo, 0F, col); // left, bottom to top
				}
			}
		}

		// the burst when you tick it: particles, rings, sparks (0.6 s)
		if (on && t < 0.75F) {
			float cx = x + size / 2F, cy = y + size / 2F;
			float q = clamp01(t / 0.6F);
			float fade = q < 0.2F ? q / 0.2F : 1F - (q - 0.2F) / 0.8F;
			for (int i = 0; i < 12; i++) {
				float pr = Math.max(0.7F, 2F * k) * (1F - q);
				int a = Math.round(255 * clamp01(fade));
				if (pr > 0.05F && a > 4) {
					Gfx.circle(c, cx + PX[i] * k * q, cy + PY[i] * k * q, pr * 1.8F, Gfx.withAlpha(PRIMARY, a / 4));
					Gfx.circle(c, cx + PX[i] * k * q, cy + PY[i] * k * q, pr, Gfx.withAlpha(PRIMARY, a));
				}
			}
			for (int i = 0; i < 3; i++) {
				float rq = clamp01((t - i * 0.1F) / 0.6F);
				if (rq <= 0F || rq >= 1F) {
					continue;
				}
				float r = (size / 2F + 20F * k) * 2F * rq;
				int a = Math.round(200 * (1F - rq));
				int dots = Math.max(16, Math.min(48, (int) (r * 1.6F)));
				for (int d = 0; d < dots; d++) {
					double ang = d * 2 * Math.PI / dots;
					Gfx.circle(c, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, Math.max(0.55F, k * 0.7F), Gfx.withAlpha(PRIMARY, a));
				}
			}
			for (int i = 0; i < 4; i++) {
				double ang = Math.toRadians(i * 90);
				float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
				float off = 30F * k * q, len = 20F * k * (1F - q);
				int a = Math.round(255 * (1F - q));
				int steps = Math.max(2, (int) (len / Math.max(0.5F, k * 0.6F)));
				for (int j = 0; j <= steps && len > 0.2F; j++) {
					float u = j / (float) steps; // brighter at the start, fading to nothing like the gradient
					Gfx.circle(c, cx + dx * (off + len * u), cy + dy * (off + len * u), Math.max(0.5F, k * 0.55F),
							Gfx.withAlpha(PRIMARY, Math.round(a * (1F - u))));
				}
			}
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
