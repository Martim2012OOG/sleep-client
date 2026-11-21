package dev.sleepclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.sleepclient.Gfx;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * The flat dark map of the Private Connection screen (like a VPN app): the land from globe_land.png drawn once into a
 * Mercator texture with a lighter coast, zoomed and panned smoothly so your place and the server's country both fit,
 * with a gradient arc between them, a small dot at home and a big soft glow on the server's country.
 */
public final class MapView {

	private static final double RAD = Math.PI / 180D;
	/** Latitudes shown by the map (Mercator can't reach the poles). */
	private static final double LAT_TOP = 82, LAT_BOTTOM = -58;
	private static final int TW = 2048;
	private static final int TH;
	private static final double M_TOP = merc(LAT_TOP), M_BOTTOM = merc(LAT_BOTTOM);

	static {
		TH = (int) Math.round(TW * (M_TOP - M_BOTTOM) / (2 * Math.PI));
	}

	private static final Map<String, double[]> CENTRE = new HashMap<>();
	private static volatile NativeImage pending;
	private static Identifier texId;
	private static boolean started;

	/** The view: centre (u, v in 0..1 of the texture) and how much of the width is visible. Eased towards the target. */
	/** Short names shown next to the two points ("You - Portugal", the server's country). Set by the screen before draw(). */
	public String homeLabel = "", destLabel = "";

	private double cu = 0.47, cv = 0.3, span = 0.45;
	private double tu = cu, tv = cv, tspan = span;
	private long last = System.nanoTime();

	private static double merc(double lat) {
		return Math.log(Math.tan(Math.PI / 4 + lat * RAD / 2));
	}

	static double u(double lon) {
		return (lon + 180) / 360D;
	}

	static double v(double lat) {
		lat = Math.max(LAT_BOTTOM, Math.min(LAT_TOP, lat));
		return (M_TOP - merc(lat)) / (M_TOP - M_BOTTOM);
	}

	/** {lon, lat} of a country (lowercase or uppercase code), or null. */
	public static double[] centre(String cc) {
		start();
		return cc == null || cc.isEmpty() ? null : CENTRE.get(cc.toLowerCase(java.util.Locale.ROOT));
	}

	/** Loads the centroids and builds the land texture on a worker thread (once). */
	private static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		var rm = MinecraftClient.getInstance().getResourceManager();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				rm.getResource(Gfx.id("country_centroids.tsv")).orElseThrow().getInputStream(), StandardCharsets.UTF_8))) {
			String l;
			while ((l = r.readLine()) != null) {
				String[] p = l.split("\t");
				if (p.length == 3) {
					CENTRE.put(p[0], new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2])});
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] map centroids: " + e);
		}
		Thread t = new Thread(() -> {
			try (InputStream in = rm.getResource(Gfx.id("globe_land.png")).orElseThrow().getInputStream()) {
				pending = build(ImageIO.read(in));
			} catch (Exception e) {
				System.err.println("[Sleep] map: " + e);
			}
		}, "Sleep-Map");
		t.setDaemon(true);
		t.start();
	}

	/** Land mask (equirectangular, white = land) -> Mercator texture: dark land with a soft, anti-aliased coast, see-through sea. */
	private static NativeImage build(BufferedImage mask) {
		int mw = mask.getWidth(), mh = mask.getHeight();
		float[] m = new float[mw * mh];
		for (int y = 0; y < mh; y++) {
			for (int x = 0; x < mw; x++) {
				m[y * mw + x] = (mask.getRGB(x, y) & 0xFF) / 255F;
			}
		}
		// the mask is only 720 x 360: blur it a little so the coast is a smooth line instead of a staircase when zoomed in
		float[] blur = new float[m.length];
		for (int y = 0; y < mh; y++) {
			for (int x = 0; x < mw; x++) {
				float sum = 0F, wsum = 0F;
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						float wt = (dx == 0 ? 2F : 1F) * (dy == 0 ? 2F : 1F);
						sum += m[Math.max(0, Math.min(mh - 1, y + dy)) * mw + Math.floorMod(x + dx, mw)] * wt;
						wsum += wt;
					}
				}
				blur[y * mw + x] = sum / wsum;
			}
		}
		float[] cov = new float[TW * TH];
		for (int py = 0; py < TH; py++) {
			double mv = M_TOP - (py + 0.5) / TH * (M_TOP - M_BOTTOM);
			double lat = (2 * Math.atan(Math.exp(mv)) - Math.PI / 2) / RAD;
			double fy = (90 - lat) / 180D * mh - 0.5;
			int y0 = (int) Math.floor(fy);
			float ty = (float) (fy - y0);
			int ya = Math.max(0, Math.min(mh - 1, y0)), yb = Math.max(0, Math.min(mh - 1, y0 + 1));
			for (int px = 0; px < TW; px++) {
				double fx = (px + 0.5) / TW * mw - 0.5;
				int x0 = (int) Math.floor(fx);
				float tx = (float) (fx - x0);
				int xa = Math.floorMod(x0, mw), xb = Math.floorMod(x0 + 1, mw);
				float a = blur[ya * mw + xa] * (1 - tx) + blur[ya * mw + xb] * tx;
				float b = blur[yb * mw + xa] * (1 - tx) + blur[yb * mw + xb] * tx;
				float v = a * (1 - ty) + b * ty;
				float t = Math.max(0F, Math.min(1F, (v - 0.36F) / 0.28F)); // smoothstep around the coast
				cov[py * TW + px] = t * t * (3F - 2F * t);
			}
		}
		NativeImage img = new NativeImage(TW, TH, true);
		int landR = 0x2B, landG = 0x2E, landB = 0x38, coastR = 0x5A, coastG = 0x5F, coastB = 0x70;
		for (int py = 0; py < TH; py++) {
			for (int px = 0; px < TW; px++) {
				float a = cov[py * TW + px];
				if (a <= 0.004F) {
					img.setColorArgb(px, py, 0);
					continue;
				}
				float rim = 1F - Math.abs(a * 2F - 1F); // brightest right on the coast line
				float k = rim * 0.85F;
				int r = Math.round(landR + (coastR - landR) * k), g = Math.round(landG + (coastG - landG) * k), bl = Math.round(landB + (coastB - landB) * k);
				img.setColorArgb(px, py, (Math.round(a * 255F) << 24) | (r << 16) | (g << 8) | bl);
			}
		}
		return img;
	}

	/** A texture with smooth (linear) filtering, so the coast stays soft when zoomed in. */
	private static final class SmoothTexture extends NativeImageBackedTexture {
		SmoothTexture(NativeImage img) {
			super(() -> "kubo_private_connection_map", img);
			sampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR);
		}
	}

	private static Identifier texture() {
		if (texId == null && pending != null) {
			SmoothTexture t = new SmoothTexture(pending);
			pending = null;
			t.upload();
			texId = Gfx.id("private_connection_map");
			MinecraftClient.getInstance().getTextureManager().registerTexture(texId, t);
		}
		return texId;
	}

	/**
	 * Draws the map into (x, y, w, h). home / dest: {lon, lat} or null. on = connected (arc + glow in colour).
	 */
	public void draw(DrawContext c, int x, int y, int w, int h, double[] home, double[] dest, boolean on) {
		start();
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - last) / 1e9);
		last = now;
		double aspect = h / (double) Math.max(1, w) * TW / (double) TH; // visible height (in v) per visible width (in u)

		// where to look: both points with some room around them, at least ~40 degrees wide
		double[] a = home != null ? home : dest, b = dest != null ? dest : home;
		if (a != null) {
			double ua = u(a[0]), va = v(a[1]), ub = u(b[0]), vb = v(b[1]);
			double needW = Math.abs(ub - ua) * 1.7, needH = Math.abs(vb - va) * 1.9;
			tspan = Math.max(0.2, Math.min(Math.min(1, 1 / aspect), Math.max(needW, needH / aspect)));
			tu = (ua + ub) / 2;
			tv = (va + vb) / 2 - tspan * aspect * 0.06; // a bit higher: the arc bends up
		}
		double k = Math.min(1, dt * 3.2);
		cu += (tu - cu) * k;
		cv += (tv - cv) * k;
		span += (tspan - span) * k;
		double vh = span * aspect;
		double u0 = Math.max(0, Math.min(1 - span, cu - span / 2)), v0 = Math.max(0, Math.min(Math.max(0, 1 - vh), cv - vh / 2));

		c.enableScissor(x, y, x + w, y + h);
		Gfx.gradientV(c, x, y, w, h, 0xFF16181F, 0xFF101117);
		Identifier tex = texture();
		if (tex != null) {
			int rx = (int) Math.round(u0 * TW), ry = (int) Math.round(v0 * TH);
			int rw = Math.max(1, (int) Math.round(span * TW)), rh = Math.max(1, (int) Math.round(vh * TH));
			Gfx.texUv(c, tex, x, y, w, h, rx, ry, rw, rh, TW, TH, 0xFFFFFFFF);
		}
		// soft colour from the top (like the app when it is protected)
		Gfx.gradientV(c, x, y, w, h / 2, on ? 0x701C7A66 : 0x50000000, 0x00000000);

		double sx = w / span, sy = h / vh;
		float hx = 0, hy = 0, dx = 0, dy = 0;
		if (home != null) {
			hx = (float) (x + (u(home[0]) - u0) * sx);
			hy = (float) (y + (v(home[1]) - v0) * sy);
		}
		if (dest != null) {
			dx = (float) (x + (u(dest[0]) - u0) * sx);
			dy = (float) (y + (v(dest[1]) - v0) * sy);
		}
		double t = now / 1e9;
		int teal = 0xFF2DD4BF, purple = 0xFF8B7CFF;
		if (dest != null) {
			// big soft glow on the server's country
			double pulse = (Math.sin(t * 2.2) + 1) / 2;
			float gr = (float) (Math.min(w, h) * 0.11 + pulse * 2);
			int gc = on ? 0x2DD4BF : 0x8C93A6;
			Gfx.circle(c, dx, dy, gr, Gfx.withAlpha(gc, 0x26));
			Gfx.circle(c, dx, dy, gr * 0.55F, Gfx.withAlpha(gc, 0x30));
		}
		if (home != null && dest != null && Math.hypot(dx - hx, dy - hy) > 6) {
			// arc from the server to home, bending upwards, teal -> purple, with a light travelling along it
			float mx = (hx + dx) / 2, my = (hy + dy) / 2;
			float len = (float) Math.hypot(dx - hx, dy - hy);
			float cx = mx, cy = my - len * 0.35F;
			int steps = Math.max(20, Math.min(90, Math.round(len / 4F))); // fewer dots: each one is many small fills
			double run = (t * 0.45) % 1.0;
			for (int i = 0; i <= steps; i++) {
				float f = i / (float) steps;
				float px = (1 - f) * (1 - f) * dx + 2 * (1 - f) * f * cx + f * f * hx;
				float py = (1 - f) * (1 - f) * dy + 2 * (1 - f) * f * cy + f * f * hy;
				int col = on ? Gfx.blend(teal, purple, f) : Gfx.blend(0xFF6A7082, 0xFF8C93A6, f);
				Gfx.circle(c, px, py, 1.1F, col);
				double g = Math.max(0, 1 - Math.abs(f - run) * 9);
				if (on && g > 0.02) {
					Gfx.circle(c, px, py, 1.1F + (float) g * 1.4F, Gfx.withAlpha(0xFFFFFF, (int) (g * 200)));
				}
			}
		}
		if (dest != null) {
			Gfx.circle(c, dx, dy, 4.5F, 0xFFEFF3F8);
			Gfx.circle(c, dx, dy, 2.8F, on ? 0xFF1FB89A : 0xFF6A7082);
		}
		if (home != null) {
			Gfx.circle(c, hx, hy, 3.6F, 0xFFEFF3F8);
			Gfx.circle(c, hx, hy, 2.2F, purple);
		}
		if (dest != null && !destLabel.isEmpty()) {
			chip(c, dx, dy - 12, destLabel, on ? 0xFF2DD4BF : 0xFF8C93A6, x, x + w, y, y + h);
		}
		if (home != null && !homeLabel.isEmpty()) {
			chip(c, hx, hy + 14, homeLabel, purple, x, x + w, y, y + h);
		}
		c.disableScissor();
	}

	/** A small dark label with a coloured dot, kept inside the map. */
	private static void chip(DrawContext c, float cx, float cy, String s, int dot, int minX, int maxX, int minY, int maxY) {
		int tw = Gfx.tw(s), w = tw + 22, h = 16;
		int x = Math.round(Math.max(minX + 4, Math.min(maxX - w - 4, cx - w / 2F)));
		int y = Math.round(Math.max(minY + 4, Math.min(maxY - h - 4, cy - h / 2F)));
		Gfx.rrectBorder(c, x, y, w, h, 5, 0xE0121319, 0x20FFFFFF);
		Gfx.circle(c, x + 8, y + h / 2F, 2.2F, dot);
		Gfx.text(c, s, x + 15, y + 4, 0xFFE8EBF2);
	}
}
