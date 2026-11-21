package dev.sleepclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

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
		var rm = Minecraft.getInstance().getResourceManager();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				rm.getResource(Gfx.id("country_centroids.tsv")).orElseThrow().open(), StandardCharsets.UTF_8))) {
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
			try (InputStream in = rm.getResource(Gfx.id("globe_land.png")).orElseThrow().open()) {
				pending = build(ImageIO.read(in));
			} catch (Exception e) {
				System.err.println("[Sleep] map: " + e);
			}
		}, "Sleep-Map");
		t.setDaemon(true);
		t.start();
	}

	/** Land mask (equirectangular, white = land) -> Mercator texture: dark land, lighter coast, see-through sea. */
	private static NativeImage build(BufferedImage mask) {
		int mw = mask.getWidth(), mh = mask.getHeight();
		float[] m = new float[mw * mh];
		for (int y = 0; y < mh; y++) {
			for (int x = 0; x < mw; x++) {
				m[y * mw + x] = (mask.getRGB(x, y) & 0xFF) / 255F;
			}
		}
		boolean[] land = new boolean[TW * TH];
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
				float a = m[ya * mw + xa] * (1 - tx) + m[ya * mw + xb] * tx;
				float b = m[yb * mw + xa] * (1 - tx) + m[yb * mw + xb] * tx;
				land[py * TW + px] = a * (1 - ty) + b * ty > 0.5F;
			}
		}
		NativeImage img = new NativeImage(TW, TH, true);
		int landCol = 0xFF2B2E38, coastCol = 0xFF5A5F70;
		for (int py = 0; py < TH; py++) {
			for (int px = 0; px < TW; px++) {
				int col = 0;
				if (land[py * TW + px]) {
					boolean coast = false;
					for (int d = 0; d < 4 && !coast; d++) {
						int nx = Math.floorMod(px + (d == 0 ? 1 : d == 1 ? -1 : 0), TW), ny = py + (d == 2 ? 1 : d == 3 ? -1 : 0);
						coast = ny >= 0 && ny < TH && !land[ny * TW + nx];
					}
					col = coast ? coastCol : landCol;
				}
				img.setPixel(px, py, col);
			}
		}
		return img;
	}

	/** A texture with smooth (linear) filtering, so the coast stays soft when zoomed in. */
	private static final class SmoothTexture extends DynamicTexture {
		SmoothTexture(NativeImage img) {
			super(() -> "kubo_private_connection_map", img);
			sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
		}
	}

	private static Identifier texture() {
		if (texId == null && pending != null) {
			SmoothTexture t = new SmoothTexture(pending);
			pending = null;
			t.upload();
			texId = Gfx.id("private_connection_map");
			Minecraft.getInstance().getTextureManager().register(texId, t);
		}
		return texId;
	}

	/**
	 * Draws the map into (x, y, w, h). home / dest: {lon, lat} or null. on = connected (arc + glow in colour).
	 */
	public void draw(GuiGraphicsExtractor c, int x, int y, int w, int h, double[] home, double[] dest, boolean on) {
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
			tspan = Math.max(0.11, Math.min(Math.min(1, 1 / aspect), Math.max(needW, needH / aspect)));
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
		c.disableScissor();
	}
}
