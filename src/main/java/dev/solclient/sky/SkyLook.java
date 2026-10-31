package dev.solclient.sky;

import java.util.stream.IntStream;

/**
 * Pure Java (no Minecraft classes): paints the generated sky straight onto the 6 faces of the sky cube.
 * Gradient (top / horizon / ground), a sun with a glow, a moon with a halo and craters, stars that are always in the same
 * place, and a milky way with a coloured nebula. Everything above the horizon only.
 * The atlas has the same 3x2 layout as {@link SkyBake#bake} but any face size (smaller = faster to re-paint, which the
 * time-of-day mode does every few seconds).
 */
public final class SkyLook {

	private SkyLook() {
	}

	public static final class Params {
		public int top = 0xFF3B2A5C, horizon = 0xFFF0A890, ground = 0xFF4A3550;
		public boolean sun = true;
		public int sunColour = 0xFFFFE2B8;
		/** unit vector towards the sun (x east, y up, z south) */
		public double sx, sy = 0.1, sz = -1;
		public float sunSize = 6F, sunGlow = 70F;
		public boolean moon;
		public int moonColour = 0xFFE8ECF5;
		public double mx, my = 0.5, mz = 1;
		public float moonSize = 5F, moonGlow = 40F;
		/** 0..100 */
		public float stars, milky;
		public int nebula = 0xFF8A5CFF;
		/** 0..1: how much of the night sky (stars, milky way) shows */
		public float night;
		/** 0..100: extra horizon glow (haze) */
		public float haze;
		/** Square pixel sun and moon like Minecraft's (false = round). */
		public boolean blocky = true;
		/** 0..100: how thick the sky looks (0 = clear and deep, 100 = hazy, the horizon colour fills it). */
		public float density = 50F;
	}

	/** Unit direction for an azimuth (0 = north, 90 = east) and a height above the horizon, in degrees. */
	public static double[] dir(double azimuthDeg, double heightDeg) {
		double a = Math.toRadians(azimuthDeg), e = Math.toRadians(heightDeg);
		return new double[] {Math.cos(e) * Math.sin(a), Math.sin(e), -Math.cos(e) * Math.cos(a)};
	}

	public static int[] bake(Params p, int face) {
		int w = face * 3, h = face * 2;
		int[] out = new int[w * h];
		IntStream.range(0, 6 * face).parallel().forEach(idx -> {
			int f = idx / face, j = idx % face;
			float[] p0 = SkyBake.FACES[f][0], p1 = SkyBake.FACES[f][1], p3 = SkyBake.FACES[f][3];
			int col = f % 3, row = f / 3;
			float b = (j + 0.5F) / face;
			int base = (row * face + j) * w + col * face;
			for (int i = 0; i < face; i++) {
				float a = (i + 0.5F) / face;
				float x = p0[0] + a * (p3[0] - p0[0]) + b * (p1[0] - p0[0]);
				float y = p0[1] + a * (p3[1] - p0[1]) + b * (p1[1] - p0[1]);
				float z = p0[2] + a * (p3[2] - p0[2]) + b * (p1[2] - p0[2]);
				double len = Math.sqrt(x * x + y * y + z * z);
				out[base + i] = colour(p, x / len, y / len, z / len);
			}
		});
		return out;
	}

	/** Colour of the sky in direction (x, y, z) (unit vector). */
	public static int colour(Params p, double x, double y, double z) {
		double lat = Math.asin(Math.max(-1, Math.min(1, y)));
		double r, g, b;
		int c0, c1;
		double t;
		if (lat >= 0) {
			t = Math.pow(lat / (Math.PI / 2), 0.25 + 0.006 * p.density);
			c0 = p.horizon;
			c1 = p.top;
		} else {
			t = Math.pow(-lat / (Math.PI / 2), 0.35);
			c0 = p.horizon;
			c1 = p.ground;
		}
		r = lerp(ch(c0, 16), ch(c1, 16), t);
		g = lerp(ch(c0, 8), ch(c1, 8), t);
		b = lerp(ch(c0, 0), ch(c1, 0), t);
		// soft bright band right at the horizon (haze)
		if (p.haze > 0) {
			double hz = Math.exp(-Math.abs(y) / (0.04 + 0.0006 * p.density)) * p.haze / 100.0 * 0.45 * (0.5 + p.density / 100.0);
			r += (ch(p.horizon, 16) * 1.25 + 30 - r) * hz;
			g += (ch(p.horizon, 8) * 1.25 + 30 - g) * hz;
			b += (ch(p.horizon, 0) * 1.25 + 30 - b) * hz;
		}
		double above = smooth((y + 0.02) / 0.06); // celestial things only above the horizon
		if (above > 0) {
			double lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0;
			double dark = Math.max(0, 1 - lum * 1.6); // stars fade where the sky is bright
			// ---- milky way + nebula
			if (p.milky > 0 && p.night > 0) {
				double nx = 0.30, ny = 0.62, nz = 0.72;
				double d = x * nx + y * ny + z * nz;
				double band = Math.exp(-(d * d) / (0.22 * 0.22));
				if (band > 0.01) {
					double n = fbm(x * 3.1, y * 3.1, z * 3.1);
					double fine = fbm(x * 11 + 7, y * 11, z * 11 - 3);
					double core = Math.exp(-(d * d) / (0.07 * 0.07));
					double clump = Math.pow(Math.max(0, n - 0.25) / 0.75, 1.6); // patchy clouds of stars
					double dust = 1 - 0.65 * smooth((fbm(x * 6 - 2, y * 6 + 5, z * 6) - 0.45) / 0.2) * core; // dark dust lane
					double k = (band * (0.12 + 1.1 * clump) * (0.6 + 0.4 * fine) + core * 0.5 * clump) * dust * p.milky / 100.0 * p.night * above * dark;
					double mixW = Math.min(1, core * 0.8 + fine * 0.2);
					r += (ch(p.nebula, 16) * (1 - mixW) + 235 * mixW) * k;
					g += (ch(p.nebula, 8) * (1 - mixW) + 235 * mixW) * k;
					b += (ch(p.nebula, 0) * (1 - mixW) + 245 * mixW) * k;
				}
			}
			// ---- stars
			if (p.stars > 0 && p.night > 0) {
				double s = star(x, y, z, p.stars / 100.0) * p.night * above * dark;
				r += 255 * s;
				g += 250 * s;
				b += 240 * s;
			}
			// ---- moon
			if (p.moon && p.blocky) {
				double[] m = square(x, y, z, p.mx, p.my, p.mz, p.moonSize);
				if (m != null) {
					double gl = Math.exp(-m[2] / (Math.toRadians(Math.max(0.5, p.moonSize)) * (0.35 + 1.4 * p.moonGlow / 100.0)))
							* (0.08 + 0.25 * p.moonGlow / 100.0) * above;
					r += ch(p.moonColour, 16) * gl;
					g += ch(p.moonColour, 8) * gl;
					b += ch(p.moonColour, 0) * gl;
					if (m[3] > 0) {
						double k = moonPix((int) m[0], (int) m[1]);
						r = lerp(r, ch(p.moonColour, 16) * k, above);
						g = lerp(g, ch(p.moonColour, 8) * k, above);
						b = lerp(b, ch(p.moonColour, 0) * k, above);
					}
				}
			} else if (p.moon) {
				double dot = Math.max(-1, Math.min(1, x * p.mx + y * p.my + z * p.mz));
				double ang = Math.acos(dot);
				double disc = Math.toRadians(Math.max(0.5, p.moonSize));
				double glowR = disc * (1.5 + 6.0 * p.moonGlow / 100.0);
				if (ang < glowR * 3) {
					double gl = Math.exp(-ang / glowR) * (0.15 + 0.4 * p.moonGlow / 100.0) * above;
					r += ch(p.moonColour, 16) * gl;
					g += ch(p.moonColour, 8) * gl;
					b += ch(p.moonColour, 0) * gl;
				}
				if (ang < disc) {
					double e = Math.min(1.0, (disc - ang) / (disc * 0.08 + 1e-6)) * above;
					double crater = 0.82 + 0.18 * fbm(x * 90, y * 90, z * 90);
					r = lerp(r, ch(p.moonColour, 16) * crater, e);
					g = lerp(g, ch(p.moonColour, 8) * crater, e);
					b = lerp(b, ch(p.moonColour, 0) * crater, e);
				}
			}
			// ---- sun
			if (p.sun && p.blocky) {
				double[] m = square(x, y, z, p.sx, p.sy, p.sz, p.sunSize);
				if (m != null) {
					double gl = Math.exp(-m[2] / (Math.toRadians(Math.max(0.5, p.sunSize)) * (0.35 + 1.6 * p.sunGlow / 100.0)))
							* (0.12 + 0.38 * p.sunGlow / 100.0) * above;
					r += ch(p.sunColour, 16) * gl;
					g += ch(p.sunColour, 8) * gl;
					b += ch(p.sunColour, 0) * gl;
					if (m[3] > 0) {
						int[] c = sunPix((int) m[0], (int) m[1], p.sunColour);
						r = lerp(r, c[0], above);
						g = lerp(g, c[1], above);
						b = lerp(b, c[2], above);
					}
				}
			} else if (p.sun) {
				double dot = Math.max(-1, Math.min(1, x * p.sx + y * p.sy + z * p.sz));
				double ang = Math.acos(dot);
				double disc = Math.toRadians(Math.max(0.5, p.sunSize));
				double glowR = disc * (2.0 + 10.0 * p.sunGlow / 100.0);
				if (ang < glowR * 4) {
					double gl = Math.exp(-ang / glowR) * (0.25 + 0.55 * p.sunGlow / 100.0) * above;
					r += ch(p.sunColour, 16) * gl;
					g += ch(p.sunColour, 8) * gl;
					b += ch(p.sunColour, 0) * gl;
				}
				if (ang < disc) {
					double e = Math.min(1.0, (disc - ang) / (disc * 0.12 + 1e-6)) * above;
					r = lerp(r, ch(p.sunColour, 16), e);
					g = lerp(g, ch(p.sunColour, 8), e);
					b = lerp(b, ch(p.sunColour, 0), e);
				}
			}
		}
		return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	// ------------------------------------------------------------------ square pixel sun / moon (like Minecraft's)
	/**
	 * Where direction (x, y, z) falls on a square centred on direction (cx, cy, cz) that is sizeDeg wide:
	 * {pixel x 0..15, pixel y 0..15, distance for the glow (radians, square-ish), 1 if inside the square else 0},
	 * or null when it is on the other side of the sky.
	 */
	private static double[] square(double x, double y, double z, double cx, double cy, double cz, float sizeDeg) {
		double dot = x * cx + y * cy + z * cz;
		if (dot <= 0.05) {
			return null;
		}
		// basis on the sky around the centre: e1 along the horizon, e2 "up"
		double ux = 0, uy = 1, uz = 0;
		if (Math.abs(cy) > 0.99) {
			ux = 1;
			uy = 0;
		}
		double e1x = uy * cz - uz * cy, e1y = uz * cx - ux * cz, e1z = ux * cy - uy * cx;
		double l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
		e1x /= l1;
		e1y /= l1;
		e1z /= l1;
		double e2x = cy * e1z - cz * e1y, e2y = cz * e1x - cx * e1z, e2z = cx * e1y - cy * e1x;
		double u = (x * e1x + y * e1y + z * e1z) / dot, v = (x * e2x + y * e2y + z * e2z) / dot;
		double half = Math.tan(Math.toRadians(Math.max(0.5, sizeDeg)) / 2);
		double au = Math.abs(u), av = Math.abs(v);
		double dist = Math.max(0, 0.6 * Math.max(au, av) + 0.4 * Math.sqrt(u * u + v * v) - half);
		boolean in = au < half && av < half;
		int px = (int) Math.floor((u / half * 0.5 + 0.5) * 16), py = (int) Math.floor((0.5 - v / half * 0.5) * 16);
		return new double[] {Math.max(0, Math.min(15, px)), Math.max(0, Math.min(15, py)), dist, in ? 1 : 0};
	}

	/** Sun pixels: a warm rim, the sun colour, a pale centre with small corner marks. */
	private static int[] sunPix(int px, int py, int col) {
		double r = ch(col, 16), g = ch(col, 8), b = ch(col, 0);
		boolean rim = px == 0 || py == 0 || px == 15 || py == 15;
		boolean inner = px >= 3 && px <= 12 && py >= 3 && py <= 12;
		boolean mark = inner && ((px <= 4 || px >= 11) && (py == 3 || py == 12) || (py <= 4 || py >= 11) && (px == 3 || px == 12));
		if (rim) {
			return new int[] {clamp(r * 0.95 + 10), clamp(g * 0.78), clamp(b * 0.45)};
		}
		if (mark) {
			return new int[] {clamp(r), clamp(g * 0.9), clamp(b * 0.7)};
		}
		double k = inner ? 0.55 : 0.0;
		return new int[] {clamp(lerp(r, 255, k)), clamp(lerp(g, 252, k)), clamp(lerp(b, 225, k))};
	}

	/** Moon pixels: pale with a few craters (fixed pixel pattern). */
	private static final String[] MOON = {
			"................", ".......##.......", "..##...##.......", "..##........##..", "............##..", ".....###........",
			".....###........", ".....###....#...", "...........###..", "..#.........#...", ".###............", "..#.....##......",
			"........##......", "..............#.", ".....#..........", "................"};

	private static double moonPix(int px, int py) {
		if (px == 0 || py == 0 || px == 15 || py == 15) {
			return 0.86;
		}
		return MOON[py].charAt(px) == '#' ? 0.74 : 1.0;
	}

	// ------------------------------------------------------------------ stars (fixed in the sky, independent of resolution)
	private static final int COLS = 1100, ROWS = 560;

	private static double star(double x, double y, double z, double amount) {
		double lon = Math.atan2(x, -z); // -pi..pi
		double u = (lon / (2 * Math.PI) + 0.5) * COLS;
		double v = (y * 0.5 + 0.5) * ROWS; // equal-area rows
		int cu = (int) Math.floor(u), cv = (int) Math.floor(v);
		double best = 0;
		double density = 0.004 + 0.026 * amount;
		for (int dv = -1; dv <= 1; dv++) {
			int rv = cv + dv;
			if (rv < 0 || rv >= ROWS) {
				continue;
			}
			for (int du = -1; du <= 1; du++) {
				int ru = Math.floorMod(cu + du, COLS);
				long hsh = hash(ru, rv);
				double pick = (hsh & 0xFFFF) / 65535.0;
				if (pick > density) {
					continue;
				}
				double jx = ((hsh >>> 16) & 0xFFFF) / 65535.0, jy = ((hsh >>> 32) & 0xFFFF) / 65535.0;
				double bright = Math.pow(((hsh >>> 48) & 0xFFFF) / 65535.0, 3);
				double slon = ((ru + jx) / COLS - 0.5) * 2 * Math.PI;
				double sy = ((rv + jy) / ROWS) * 2 - 1;
				double sc = Math.sqrt(Math.max(0, 1 - sy * sy));
				double sxx = sc * Math.sin(slon), szz = -sc * Math.cos(slon);
				double dx = x - sxx, dy = y - sy, dz = z - szz;
				double d2 = dx * dx + dy * dy + dz * dz; // chord^2 ~ angle^2
				double sig = Math.toRadians(0.07 + 0.11 * bright);
				double s = (0.35 + 0.65 * bright) * Math.exp(-d2 / (sig * sig));
				if (s > best) {
					best = s;
				}
			}
		}
		return Math.min(1.0, best * 1.4);
	}

	private static long hash(int a, int b) {
		long h = a * 0x9E3779B97F4A7C15L ^ (b + 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return h;
	}

	// ------------------------------------------------------------------ noise
	private static double vnoise(double x, double y, double z) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
		double fx = x - ix, fy = y - iy, fz = z - iz;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		double c000 = h3(ix, iy, iz), c100 = h3(ix + 1, iy, iz), c010 = h3(ix, iy + 1, iz), c110 = h3(ix + 1, iy + 1, iz);
		double c001 = h3(ix, iy, iz + 1), c101 = h3(ix + 1, iy, iz + 1), c011 = h3(ix, iy + 1, iz + 1), c111 = h3(ix + 1, iy + 1, iz + 1);
		double x00 = lerp(c000, c100, fx), x10 = lerp(c010, c110, fx), x01 = lerp(c001, c101, fx), x11 = lerp(c011, c111, fx);
		return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
	}

	private static double h3(int x, int y, int z) {
		long h = hash(x * 73856093 ^ z * 83492791, y * 19349663 + z);
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	static double fbm(double x, double y, double z) {
		double s = 0, a = 0.5, n = 0;
		for (int o = 0; o < 4; o++) {
			s += vnoise(x, y, z) * a;
			n += a;
			x *= 2.03;
			y *= 2.03;
			z *= 2.03;
			a *= 0.5;
		}
		return s / n;
	}

	// ------------------------------------------------------------------ helpers
	private static double ch(int c, int sh) {
		return (c >> sh) & 255;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * Math.max(0, Math.min(1, t));
	}

	private static double smooth(double v) {
		v = Math.max(0, Math.min(1, v));
		return v * v * (3 - 2 * v);
	}

	private static int clamp(double v) {
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}

	/** Blends two colours (ARGB) by t (0..1). */
	public static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		return 0xFF000000 | clamp(lerp(ch(a, 16), ch(b, 16), t)) << 16 | clamp(lerp(ch(a, 8), ch(b, 8), t)) << 8 | clamp(lerp(ch(a, 0), ch(b, 0), t));
	}
}
