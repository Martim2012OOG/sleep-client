package dev.solclient.fx;

/** Small colour helpers for the rainbow / gradient effects (fishing line, tripwire, mace effects). */
public final class Rainbow {

	private Rainbow() {
	}

	/** A fully bright rainbow colour (opaque ARGB). hue is any number: 0..1 is one full turn of the rainbow. */
	public static int hue(float hue) {
		return hsv(hue, 0.85F, 1F);
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float r, g, b;
		float f = h * 6F;
		int i = (int) Math.floor(f);
		float ff = f - i;
		float p = v * (1F - s), q = v * (1F - s * ff), t = v * (1F - s * (1F - ff));
		switch (i % 6) {
			case 0 -> {
				r = v;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = v;
				b = p;
			}
			case 2 -> {
				r = p;
				g = v;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = v;
			}
			case 4 -> {
				r = t;
				g = p;
				b = v;
			}
			default -> {
				r = v;
				g = p;
				b = q;
			}
		}
		return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	/** Mix of two ARGB colours (t = 0 gives a, t = 1 gives b). */
	public static int mix(int a, int b, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8
				| Math.round(ab + (bb - ab) * t);
	}

	/** Same colour with another alpha (0..1 multiplies the colour's own alpha). */
	public static int alpha(int argb, float k) {
		int a = Math.round(((argb >>> 24) & 255) * Math.max(0F, Math.min(1F, k)));
		return a << 24 | (argb & 0xFFFFFF);
	}

	/** Seconds since start, for animations that should not depend on the game tick. */
	public static float seconds() {
		return (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000F;
	}
}
