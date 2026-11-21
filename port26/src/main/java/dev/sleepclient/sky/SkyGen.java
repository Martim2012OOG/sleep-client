package dev.sleepclient.sky;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

/**
 * Pure Java (no Minecraft classes): generates the gradient sky (an equirectangular image that is then baked by
 * {@link SkyBake}) and the built-in sprites of the falling layers (petals, snow, leaves, glow, sparks).
 */
public final class SkyGen {

	public static final int SPRITE = 128;
	/** Index = saved value, so new styles are only ever appended (old configs keep working). */
	public static final String[] STYLES = {"Off", "Sakura petals", "Snowflakes", "Leaves", "Glow dots", "Sparks", "Custom texture", "Sakura flowers",
			"Hearts", "Autumn leaves", "Stars", "Confetti", "Bubbles", "Feathers", "Butterflies"};
	public static final int OFF = 0, PETAL = 1, SNOW = 2, LEAF = 3, GLOW = 4, SPARK = 5, CUSTOM = 6, BLOSSOM = 7, HEART = 8, AUTUMN = 9, STAR = 10,
			CONFETTI = 11, BUBBLE = 12, FEATHER = 13, BUTTERFLY = 14;

	/** Flat things that tumble in 3D (the rest always face the camera). */
	public static boolean tumbles(int style) {
		return style == PETAL || style == LEAF || style == CUSTOM || style == BLOSSOM || style == HEART || style == AUTUMN || style == CONFETTI
				|| style == FEATHER;
	}

	/** Things that can lie on the ground (not glows / sparks / stars). */
	public static boolean landable(int style) {
		return (tumbles(style) || style == SNOW) && style != BUTTERFLY;
	}

	/** Things that glow / twinkle. */
	public static boolean twinkles(int style) {
		return style == GLOW || style == SPARK || style == STAR;
	}

	private SkyGen() {
	}

	// ------------------------------------------------------------------ gradient sky
	public record Gradient(int top, int horizon, int ground, boolean sun, int sunColour, float sunAzimuth, float sunHeight, float sunSize, float sunGlow) {
	}

	/** 2:1 equirectangular image. Horizon at the middle row; u = 0.5 looks north (same convention as SkyBake). */
	public static BufferedImage gradient(Gradient g) {
		final int w = 2048, h = 1024;
		int[] px = new int[w * h];
		double saz = Math.toRadians(g.sunAzimuth()), sel = Math.toRadians(g.sunHeight());
		double sx = Math.cos(sel) * Math.sin(saz), sy = Math.sin(sel), sz = -Math.cos(sel) * Math.cos(saz);
		double disc = Math.toRadians(Math.max(0.5F, g.sunSize()));
		double glowRad = disc * (2.0 + 10.0 * g.sunGlow() / 100.0);
		IntStream.range(0, h).parallel().forEach(j -> {
			double lat = (0.5 - (j + 0.5) / h) * Math.PI;
			int base;
			if (lat >= 0) {
				double t = Math.pow(lat / (Math.PI / 2), 0.55);
				base = lerp(g.horizon(), g.top(), t);
			} else {
				double t = Math.pow(-lat / (Math.PI / 2), 0.35);
				base = lerp(g.horizon(), g.ground(), t);
			}
			double cl = Math.cos(lat), sl = Math.sin(lat);
			for (int i = 0; i < w; i++) {
				int c = base;
				if (g.sun()) {
					double lon = ((i + 0.5) / w - 0.5) * 2 * Math.PI;
					double dx = cl * Math.sin(lon), dz = -cl * Math.cos(lon);
					double dot = Math.max(-1, Math.min(1, dx * sx + sl * sy + dz * sz));
					double d = Math.acos(dot);
					if (d < glowRad * 3) {
						double glow = Math.exp(-d / glowRad) * (0.25 + 0.55 * g.sunGlow() / 100.0);
						c = add(c, g.sunColour(), (float) Math.min(1.0, glow));
					}
					if (d < disc) {
						double e = Math.min(1.0, (disc - d) / (disc * 0.12 + 1e-6));
						c = lerp(c, g.sunColour() | 0xFF000000, e);
					}
				}
				px[j * w + i] = 0xFF000000 | (c & 0xFFFFFF);
			}
		});
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, w, h, px, 0, w);
		return img;
	}

	private static int lerp(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static int add(int a, int b, float k) {
		int r = Math.min(255, ((a >> 16) & 255) + Math.round(((b >> 16) & 255) * k));
		int g = Math.min(255, ((a >> 8) & 255) + Math.round(((b >> 8) & 255) * k));
		int bl = Math.min(255, (a & 255) + Math.round((b & 255) * k));
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	// ------------------------------------------------------------------ sprites
	/** ARGB pixels (not premultiplied) of a built-in sprite. Colour is kept even where alpha is 0 so bilinear filtering has no dark fringes. */
	public static int[] sprite(int style) {
		int n = SPRITE;
		int[] out = new int[n * n];
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				double x = (i + 0.5) / n * 2 - 1, y = (j + 0.5) / n * 2 - 1;
				out[j * n + i] = switch (style) {
					case PETAL -> petal(x, y);
					case SNOW -> snow(x, y);
					case LEAF -> leaf(x, y);
					case SPARK -> spark(x, y);
					case BLOSSOM -> blossom(x, y);
					case HEART -> heart(x, y);
					case AUTUMN -> autumn(x, y);
					case STAR -> star(x, y);
					case CONFETTI -> confetti(x, y);
					case BUBBLE -> bubble(x, y);
					case FEATHER -> feather(x, y);
					case BUTTERFLY -> butterfly(x, y);
					default -> glow(x, y);
				};
			}
		}
		return out;
	}

	private static int argb(double a, int rgb) {
		int al = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
		return al << 24 | (rgb & 0xFFFFFF);
	}

	private static double smooth(double v) {
		v = Math.max(0, Math.min(1, v));
		return v * v * (3 - 2 * v);
	}

	private static int petal(double x, double y) {
		// a slightly curved teardrop with a small notch at the top
		double cx = x - 0.18 * y * y;
		double hw = 0.50 * Math.pow(Math.max(0, 1 - (y + 0.05) * (y + 0.05) / 0.86), 0.7);
		double inside = hw <= 0 ? -1 : 1 - Math.abs(cx) / hw;
		double notch = Math.hypot(cx, y + 0.92) < 0.20 ? 1 : 0;
		double a = smooth(inside * 6) * (y > -0.93 && y < 0.93 ? 1 : 0) * (1 - notch);
		double k = Math.min(1, Math.abs(cx) / Math.max(0.05, hw));
		int rgb = lerpRgb(0xFFE3EF, 0xFF9CC2, Math.min(1, 0.5 + 0.5 * (1 - k) + 0.2 * y));
		return argb(a, rgb);
	}

	private static int snow(double x, double y) {
		double r = Math.hypot(x, y);
		double a = Math.max(0, 1 - r / 0.28);
		a = a * a;
		for (int k = 0; k < 3; k++) {
			double ang = Math.PI * k / 3, c = Math.cos(ang), s = Math.sin(ang);
			double along = Math.abs(x * c + y * s), across = Math.abs(-x * s + y * c);
			if (along < 0.92) {
				a = Math.max(a, smooth((0.07 - across) / 0.05) * (1 - along / 1.1));
			}
		}
		a = Math.max(a, Math.max(0, 1 - r / 0.55) * 0.25);
		return argb(a, 0xF4FAFF);
	}

	private static int leaf(double x, double y) {
		double u = (y + 1) / 2; // 0 tip .. 1 stem
		double hw = 0.46 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.max(0, u * 0.92 + 0.04))), 0.8);
		double cx = x - 0.12 * Math.sin(u * Math.PI);
		double inside = hw <= 0 ? -1 : 1 - Math.abs(cx) / hw;
		double a = smooth(inside * 6) * (y > -0.95 && y < 0.95 ? 1 : 0);
		int rgb = lerpRgb(0x86C45C, 0x4E8B34, Math.min(1, Math.abs(cx) / Math.max(0.05, hw) + 0.2 * u));
		if (Math.abs(cx) < 0.025 && y > -0.8) {
			rgb = 0x3C6F28;
		}
		return argb(a, rgb);
	}

	private static int glow(double x, double y) {
		double r = Math.hypot(x, y);
		double a = Math.max(0, 1 - r);
		a = a * a * a;
		double core = Math.max(0, 1 - r / 0.22);
		return argb(Math.min(1, a * 0.85 + core), lerpRgb(0xFFD27A, 0xFFFBE8, core));
	}

	private static int spark(double x, double y) {
		double ax = Math.abs(x), ay = Math.abs(y);
		double a = Math.max(Math.exp(-ax * 16) * Math.max(0, 1 - ay), Math.exp(-ay * 16) * Math.max(0, 1 - ax));
		a = Math.max(a, Math.max(0, 1 - Math.hypot(x, y) / 0.35));
		return argb(Math.min(1, a), lerpRgb(0xFF8A2A, 0xFFF0B0, Math.max(0, 1 - Math.hypot(x, y) / 0.4)));
	}

	/** Five-petal cherry blossom: notched petals, pink getting lighter to the edge, dark centre with stamens. */
	private static int blossom(double x, double y) {
		double r = Math.hypot(x, y);
		double th = Math.atan2(y, x) + Math.PI / 2;
		double seg = 2 * Math.PI / 5;
		double f = th / seg - Math.floor(th / seg);
		double phi = (f - 0.5) * seg; // -36..36 degrees inside one petal
		double prof = Math.cos(Math.min(Math.PI / 2, Math.abs(phi) * 2.25));
		double rmax = 0.95 * Math.pow(Math.max(0, prof), 0.45);
		double an = Math.abs(phi);
		if (an < 0.16) {
			rmax -= 0.17 * (1 - an / 0.16) * (1 - an / 0.16);
		}
		double a = smooth((rmax - r) * 14);
		// colour: deep pink centre -> soft pink -> almost white edge, a faint vein in the middle of each petal
		double t = Math.min(1, r / Math.max(0.2, rmax));
		int rgb = t < 0.28 ? lerpRgb(0xD94F7A, 0xF58FB0, t / 0.28) : lerpRgb(0xF58FB0, 0xFFE6EE, (t - 0.28) / 0.72);
		if (an < 0.025 && r > 0.18 && r < rmax * 0.7) {
			rgb = lerpRgb(rgb, 0xE87CA0, 0.35);
		}
		// stamens: little yellow dots around the centre
		for (int k = 0; k < 5; k++) {
			double ang = k * seg + seg / 2 - Math.PI / 2;
			double sx = Math.cos(ang) * 0.22, sy = Math.sin(ang) * 0.22;
			double d = Math.hypot(x - sx, y - sy);
			if (d < 0.07) {
				rgb = lerpRgb(0xFFE08A, rgb, smooth(d / 0.07));
			}
		}
		if (r < 0.09) {
			rgb = lerpRgb(0xB8365E, rgb, smooth(r / 0.09));
		}
		return argb(a, rgb);
	}

	private static int heart(double x, double y) {
		// two round lobes + a pointed bottom
		double ax = Math.abs(x);
		double lobe = 0.44 - Math.hypot(ax - 0.4, y + 0.28);
		double tri = Math.min(y + 0.3, (0.86 - y) * 0.82 - ax);
		double d = Math.max(lobe, y > -0.3 ? tri * 0.9 : -1);
		double a = smooth(d * 16);
		double shine = Math.max(0, 1 - Math.hypot(x + 0.38, y + 0.36) / 0.22);
		int rgb = lerpRgb(lerpRgb(0xFF4F86, 0xFF9DBE, Math.max(0, -y * 0.5 + 0.3)), 0xFFFFFF, shine * 0.7);
		return argb(a, rgb);
	}

	private static int autumn(double x, double y) {
		// maple-ish leaf: 5 lobes, orange to red, with a stem
		double r = Math.hypot(x, y + 0.1);
		double th = Math.atan2(y + 0.1, x);
		double lobes = 0.62 + 0.24 * Math.pow(Math.abs(Math.cos(th * 2.5)), 3) + 0.1 * Math.abs(Math.cos(th * 7.5));
		double cut = th > Math.PI * 0.30 && th < Math.PI * 0.70 ? 0.55 : 1; // the gap where the stem is
		double a = smooth((lobes * cut - r) * 12);
		boolean stem = Math.abs(x) < 0.03 && y > 0.1 && y < 0.92;
		if (stem) {
			a = 1;
		}
		int rgb = lerpRgb(0xF6A13A, 0xC8341C, Math.min(1, r / 0.8 + 0.15 * x));
		if (stem || Math.abs(x) < 0.018 && y > -0.6) {
			rgb = 0x7A2A12;
		}
		return argb(a, rgb);
	}

	private static int confetti(double x, double y) {
		// a small paper rectangle (white: it takes the layer / random colour)
		double a = smooth((0.32 - Math.abs(x)) * 30) * smooth((0.62 - Math.abs(y)) * 30);
		double shade = 0.93 + 0.07 * Math.signum(x);
		int c = (int) Math.round(255 * shade);
		return argb(a, c << 16 | c << 8 | c);
	}

	private static int bubble(double x, double y) {
		double r = Math.hypot(x, y);
		double ring = Math.exp(-Math.pow((r - 0.78) / 0.07, 2));
		double fill = r < 0.8 ? 0.10 : 0;
		double shine = Math.max(0, 1 - Math.hypot(x + 0.32, y + 0.34) / 0.18);
		double a = Math.min(1, ring * 0.9 + fill + shine * 0.9);
		int rgb = lerpRgb(0xB8E6FF, 0xFFFFFF, Math.max(shine, 0.3 * ring));
		return argb(a, rgb);
	}

	private static int feather(double x, double y) {
		double u = (y + 1) / 2; // 0 tip .. 1 quill
		double hw = 0.30 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.05)), 0.7) * (u < 0.85 ? 1 : 0);
		double cx = x - 0.10 * Math.sin(u * Math.PI);
		double inside = hw <= 0 ? -1 : 1 - Math.abs(cx) / hw;
		double barbs = 0.8 + 0.2 * Math.sin((y * 40) + Math.signum(cx) * 2);
		double a = smooth(inside * 5) * barbs;
		boolean quill = Math.abs(cx) < 0.02 && y > -0.9;
		if (quill) {
			a = 1;
		}
		int rgb = quill ? 0xD8D2C8 : lerpRgb(0xFFFFFF, 0xE8E4F0, Math.abs(cx) / Math.max(0.05, hw));
		return argb(a, rgb);
	}

	private static int butterfly(double x, double y) {
		double ax = Math.abs(x);
		// two upper wings and two smaller lower wings, a thin dark body
		double up = 1 - Math.hypot((ax - 0.42) / 0.42, (y + 0.22) / 0.46);
		double low = 1 - Math.hypot((ax - 0.30) / 0.28, (y - 0.38) / 0.32);
		double wing = Math.max(up, low);
		double a = smooth(wing * 8);
		double body = smooth((0.06 - ax) * 40) * smooth((0.62 - Math.abs(y)) * 20);
		int rgb = lerpRgb(0xFFB040, 0xFF5A8A, Math.min(1, ax * 1.2 + y * 0.3));
		double spot = Math.max(0, 1 - Math.hypot(ax - 0.52, y + 0.3) / 0.1);
		rgb = lerpRgb(rgb, 0xFFFFFF, spot);
		if (wing > 0 && wing < 0.12) {
			rgb = lerpRgb(rgb, 0x2A1A10, 0.7); // dark wing edge
		}
		if (body > 0.5) {
			return argb(1, 0x2A1A10);
		}
		return argb(Math.max(a, body), rgb);
	}

	private static int star(double x, double y) {
		double r = Math.hypot(x, y);
		double th = Math.atan2(y, x) - Math.PI / 2;
		double k = Math.abs(Math.cos(th * 2.5));
		double rmax = 0.32 + 0.5 * Math.pow(k, 6);
		double a = Math.max(smooth((rmax - r) * 10), Math.max(0, 1 - r) * Math.max(0, 1 - r) * 0.35);
		return argb(Math.min(1, a), lerpRgb(0xFFF6C8, 0xFFFFFF, Math.max(0, 1 - r / 0.3)));
	}

	private static int lerpRgb(int a, int b, double t) {
		return lerp(a, b, t) & 0xFFFFFF;
	}

	// ------------------------------------------------------------------ effect textures
	public static final int CLOUD = 128, CLOUD_VARIANTS = 4;

	/** 4 soft cumulus clouds side by side (CLOUD*4 x CLOUD), white with a slightly darker belly. */
	public static int[] clouds() {
		int w = CLOUD * CLOUD_VARIANTS, h = CLOUD;
		int[] out = new int[w * h];
		java.util.Random rnd = new java.util.Random(42);
		for (int v = 0; v < CLOUD_VARIANTS; v++) {
			// a few round puffs on a flat-ish base
			int n = 5 + v;
			double[] bx = new double[n], by = new double[n], br = new double[n];
			for (int i = 0; i < n; i++) {
				double f = (i + 0.5) / n;
				bx[i] = -0.62 + 1.24 * f + (rnd.nextDouble() - 0.5) * 0.12;
				br[i] = 0.18 + 0.2 * Math.sin(Math.PI * f) + rnd.nextDouble() * 0.08;
				by[i] = 0.18 - br[i] * 0.9 + (rnd.nextDouble() - 0.5) * 0.06;
			}
			for (int j = 0; j < h; j++) {
				for (int i = 0; i < CLOUD; i++) {
					double x = (i + 0.5) / CLOUD * 2 - 1, y = (j + 0.5) / h * 2 - 1;
					double d = -1;
					for (int k = 0; k < n; k++) {
						d = Math.max(d, 1 - Math.hypot(x - bx[k], (y - by[k]) * 1.1) / br[k]);
					}
					double base = 1 - Math.max(0, y - 0.22) / 0.06; // flat bottom
					double noise = SkyLook.fbm(x * 4 + v * 10, y * 4, v * 3.3) - 0.5;
					double a = smooth((d + noise * 0.35) * 3.2) * Math.max(0, Math.min(1, base));
					double shade = 1 - 0.28 * smooth((y + 0.1) / 0.4); // darker underneath
					int c = (int) Math.round(255 * shade);
					out[j * w + v * CLOUD + i] = argb(a, c << 16 | c << 8 | Math.min(255, c + 6));
				}
			}
		}
		return out;
	}

	public static final int BEAM_W = 16, BEAM_H = 128;

	/** Vertical light beam (aurora curtain, haze, shooting star tail): bright at the bottom (v = 1), fading to the top. */
	public static int[] beam() {
		int[] out = new int[BEAM_W * BEAM_H];
		for (int j = 0; j < BEAM_H; j++) {
			double v = (j + 0.5) / BEAM_H; // 0 top .. 1 bottom
			double a = Math.pow(v, 2.2) * smooth((1 - v) / 0.08);
			for (int i = 0; i < BEAM_W; i++) {
				double u = (i + 0.5) / BEAM_W;
				double side = Math.sin(Math.PI * u); // soft left/right edges
				out[j * BEAM_W + i] = argb(a * (0.55 + 0.45 * side), 0xFFFFFF);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ user textures
	/** Scales any image to a SPRITE x SPRITE square (keeps the aspect ratio, transparent borders). */
	public static int[] fit(BufferedImage src) {
		int n = SPRITE;
		BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		double k = Math.min(n / (double) src.getWidth(), n / (double) src.getHeight());
		int w = Math.max(1, (int) Math.round(src.getWidth() * k)), h = Math.max(1, (int) Math.round(src.getHeight() * k));
		g.drawImage(src, (n - w) / 2, (n - h) / 2, w, h, null);
		g.dispose();
		return out.getRGB(0, 0, n, n, null, 0, n);
	}
}
