package dev.solclient.sky;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

/**
 * Pure Java (no Minecraft classes): turns a photo into a 3x2 atlas of cube faces.
 * The cube has the same geometry as the vanilla End sky, so the baked faces line up with {@link #FACES}.
 */
public final class SkyBake {

	public static final int FACE = 1024;
	public static final int W = FACE * 3, H = FACE * 2;
	public static final float R = 100F;
	private static final int MAX_SRC = 4096;

	/** FACES[f][k] = corner k (x,y,z) of face f. k=0 uv(0,0), k=1 uv(0,1), k=2 uv(1,1), k=3 uv(1,0). Same layout as vanilla's end sky quad. */
	public static final float[][][] FACES = new float[6][4][];

	static {
		float[][] base = {{-R, -R, -R}, {-R, -R, R}, {R, -R, R}, {R, -R, -R}};
		int[] rx = {0, 90, -90, 180, 0, 0};
		int[] rz = {0, 0, 0, 0, 90, -90};
		for (int f = 0; f < 6; f++) {
			for (int k = 0; k < 4; k++) {
				float[] v = base[k].clone();
				rotX(v, rx[f]);
				rotZ(v, rz[f]);
				FACES[f][k] = v;
			}
		}
	}

	private static void rotX(float[] v, int deg) {
		int c = (int) Math.round(Math.cos(Math.toRadians(deg))), s = (int) Math.round(Math.sin(Math.toRadians(deg)));
		float y = v[1] * c - v[2] * s, z = v[1] * s + v[2] * c;
		v[1] = y;
		v[2] = z;
	}

	private static void rotZ(float[] v, int deg) {
		int c = (int) Math.round(Math.cos(Math.toRadians(deg))), s = (int) Math.round(Math.sin(Math.toRadians(deg)));
		float x = v[0] * c - v[1] * s, y = v[0] * s + v[1] * c;
		v[0] = x;
		v[1] = y;
	}

	public record Source(int[] px, int w, int h) {
	}

	public static Source source(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		double k = Math.min(1.0, MAX_SRC / (double) Math.max(w, h));
		if (k < 1.0) {
			int nw = Math.max(1, (int) Math.round(w * k)), nh = Math.max(1, (int) Math.round(h * k));
			BufferedImage s = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = s.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, 0, 0, nw, nh, null);
			g.dispose();
			img = s;
			w = nw;
			h = nh;
		}
		return new Source(img.getRGB(0, 0, w, h, null, 0, w), w, h);
	}

	/** mode 0: equirectangular 360 panorama, mode 1: the photo on each of the 4 sides (top/bottom get the edge colours). */
	public static int[] bake(Source s, int mode) {
		int[] out = new int[W * H];
		int top = rowAverage(s, 0), bottom = rowAverage(s, s.h - 1);
		IntStream.range(0, 6 * FACE).parallel().forEach(idx -> {
			int f = idx / FACE, j = idx % FACE;
			float[] p0 = FACES[f][0], p1 = FACES[f][1], p3 = FACES[f][3];
			int col = f % 3, row = f / 3;
			float b = (j + 0.5F) / FACE;
			int base = (row * FACE + j) * W + col * FACE;
			for (int i = 0; i < FACE; i++) {
				float a = (i + 0.5F) / FACE;
				float x = p0[0] + a * (p3[0] - p0[0]) + b * (p1[0] - p0[0]);
				float y = p0[1] + a * (p3[1] - p0[1]) + b * (p1[1] - p0[1]);
				float z = p0[2] + a * (p3[2] - p0[2]) + b * (p1[2] - p0[2]);
				out[base + i] = mode == 0 ? panorama(s, x, y, z) : sides(s, x, y, z, top, bottom);
			}
		});
		return out;
	}

	private static int panorama(Source s, float x, float y, float z) {
		double len = Math.sqrt(x * x + y * y + z * z);
		double lon = Math.atan2(x, -z), lat = Math.asin(y / len);
		float u = (float) (0.5 + lon / (2 * Math.PI)), v = (float) (0.5 - lat / Math.PI);
		return sample(s, u, v, true);
	}

	private static int sides(Source s, float x, float y, float z, int top, int bottom) {
		float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
		if (ay >= ax && ay >= az) {
			return y > 0 ? top : bottom;
		}
		float t, h;
		if (az >= ax) {
			t = (-Math.signum(z) * x) / az;
			h = y / az;
		} else {
			t = (Math.signum(x) * z) / ax;
			h = y / ax;
		}
		return sample(s, 0.5F + 0.5F * t, 0.5F - 0.5F * h, false);
	}

	private static int rowAverage(Source s, int y) {
		long r = 0, g = 0, b = 0;
		for (int x = 0; x < s.w; x++) {
			int c = s.px[y * s.w + x];
			r += (c >> 16) & 255;
			g += (c >> 8) & 255;
			b += c & 255;
		}
		return 0xFF000000 | (int) (r / s.w) << 16 | (int) (g / s.w) << 8 | (int) (b / s.w);
	}

	private static int sample(Source s, float u, float v, boolean wrapU) {
		float x = u * s.w - 0.5F, y = v * s.h - 0.5F;
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
		float fx = x - x0, fy = y - y0;
		int c00 = fetch(s, x0, y0, wrapU), c10 = fetch(s, x0 + 1, y0, wrapU);
		int c01 = fetch(s, x0, y0 + 1, wrapU), c11 = fetch(s, x0 + 1, y0 + 1, wrapU);
		int res = 0xFF000000;
		for (int sh = 0; sh <= 16; sh += 8) {
			float top = ((c00 >> sh) & 255) * (1 - fx) + ((c10 >> sh) & 255) * fx;
			float bot = ((c01 >> sh) & 255) * (1 - fx) + ((c11 >> sh) & 255) * fx;
			int ch = Math.round(top * (1 - fy) + bot * fy);
			res |= Math.max(0, Math.min(255, ch)) << sh;
		}
		return res;
	}

	private static int fetch(Source s, int x, int y, boolean wrapU) {
		x = wrapU ? Math.floorMod(x, s.w) : Math.max(0, Math.min(s.w - 1, x));
		y = Math.max(0, Math.min(s.h - 1, y));
		return s.px[y * s.w + x];
	}
}
