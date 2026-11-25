package dev.solclient.gui;

import dev.solclient.Gfx;
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

/** A small dotted globe that turns slowly and shows a flight arc from your place to the chosen country. */
public final class GlobeView {

	private static final double RAD = Math.PI / 180D;
	private static double[][] dots; // x, y, z of the land dots
	private static final Map<String, double[]> CENTRE = new HashMap<>();
	private static boolean loaded;

	private double yaw = 0.3, tilt = 20 * RAD;
	private double targetYaw = Double.NaN;
	private long last = System.nanoTime();
	private final double homeLon = -16.9, homeLat = 32.7;

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			var rm = Minecraft.getInstance().getResourceManager();
			try (InputStream in = rm.getResource(Gfx.id("globe_land.png")).orElseThrow().open()) {
				BufferedImage img = ImageIO.read(in);
				int n = 2200;
				double[][] tmp = new double[n][];
				int k = 0;
				double ga = Math.PI * (3 - Math.sqrt(5));
				for (int i = 0; i < n; i++) {
					double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i;
					double lat = Math.asin(y) / RAD, lon = ((Math.atan2(Math.sin(th) * r, Math.cos(th) * r) / RAD));
					int px = (int) Math.min(img.getWidth() - 1, Math.max(0, (lon + 180) / 360 * img.getWidth()));
					int py = (int) Math.min(img.getHeight() - 1, Math.max(0, (90 - lat) / 180 * img.getHeight()));
					if ((img.getRGB(px, py) & 0xFF) > 127) {
						tmp[k++] = vec(lon, lat);
					}
				}
				dots = java.util.Arrays.copyOf(tmp, k);
			}
			try (BufferedReader r = new BufferedReader(new InputStreamReader(
					rm.getResource(Gfx.id("country_centroids.tsv")).orElseThrow().open(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = r.readLine()) != null) {
					String[] p = l.split("\t");
					if (p.length == 3) {
						CENTRE.put(p[0], new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2])});
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Caos] globe: " + e);
			dots = new double[0][];
		}
	}

	private static double[] vec(double lon, double lat) {
		double a = lon * RAD, b = lat * RAD;
		return new double[] {Math.cos(b) * Math.sin(a), Math.sin(b), Math.cos(b) * Math.cos(a)};
	}

	/** Turns the globe towards a country (null = free spin). */
	public void focus(String cc) {
		double[] c = cc == null ? null : CENTRE.get(cc.toLowerCase());
		targetYaw = c == null ? Double.NaN : -c[0] * RAD;
		if (c != null) {
			tilt = tilt + (c[1] * RAD * 0.5 - tilt) * 0.0; // tilt eased in draw()
			wantTilt = c[1] * RAD * 0.6;
		} else {
			wantTilt = 20 * RAD;
		}
	}

	private double wantTilt = 20 * RAD;

	public void draw(GuiGraphicsExtractor c, int x, int y, int size, String cc, boolean on, int accent) {
		load();
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - last) / 1e9);
		last = now;
		if (Double.isNaN(targetYaw)) {
			yaw += dt * 0.25;
		} else {
			double d = Math.atan2(Math.sin(targetYaw - yaw), Math.cos(targetYaw - yaw));
			yaw += d * Math.min(1, dt * 3);
		}
		tilt += (wantTilt - tilt) * Math.min(1, dt * 3);
		double R = size / 2D, cx = x + R, cy = y + R;
		for (int row = 0; row < size; row++) { // a real circle, one line at a time
			double dy = row + 0.5 - R, hw = Math.sqrt(Math.max(0, R * R - dy * dy));
			c.fill((int) Math.round(cx - hw), y + row, (int) Math.round(cx + hw), y + row + 1, 0xFF10141F);
		}
		double cy_ = Math.cos(yaw), sy = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
		int base = accent & 0xFFFFFF;
		for (double[] d : dots) {
			double[] p = rot(d, cy_, sy, ct, st);
			if (p[2] <= 0.02) {
				continue;
			}
			int a = (int) (70 + 150 * p[2]);
			int px = (int) Math.round(cx + p[0] * R * 0.96), py = (int) Math.round(cy - p[1] * R * 0.96);
			c.fill(px, py, px + 1, py + 1, (a << 24) | (on ? base : 0x9AA3B5));
		}
		// route from home to the country
		double[] dest = cc == null ? null : CENTRE.get(cc.toLowerCase());
		if (dest != null) {
			double[] h = vec(homeLon, homeLat), t = vec(dest[0], dest[1]);
			double om = Math.acos(Math.max(-1, Math.min(1, h[0] * t[0] + h[1] * t[1] + h[2] * t[2])));
			if (om > 0.01) {
				int steps = 36;
				double prog = (now / 1e9 * 0.5) % 1.0;
				for (int i = 0; i <= steps; i++) {
					double f = i / (double) steps;
					double s1 = Math.sin((1 - f) * om) / Math.sin(om), s2 = Math.sin(f * om) / Math.sin(om);
					double lift = 1 + 0.22 * Math.sin(Math.PI * f);
					double[] v = {(h[0] * s1 + t[0] * s2) * lift, (h[1] * s1 + t[1] * s2) * lift, (h[2] * s1 + t[2] * s2) * lift};
					double[] p = rot(v, cy_, sy, ct, st);
					if (p[2] <= 0) {
						continue;
					}
					double glow = Math.max(0, 1 - Math.abs(f - prog) * 6);
					int px = (int) Math.round(cx + p[0] * R * 0.96), py = (int) Math.round(cy - p[1] * R * 0.96);
					int col = glow > 0.05 ? Gfx.blend(0xFF000000 | base, 0xFFFFFFFF, (float) glow) : 0xFF000000 | base;
					c.fill(px, py, px + (glow > 0.3 ? 2 : 1), py + (glow > 0.3 ? 2 : 1), (glow > 0.05 ? 0xFF000000 : 0xB4000000) | (col & 0xFFFFFF));
				}
				double[] p = rot(t, cy_, sy, ct, st);
				if (p[2] > 0) {
					int px = (int) Math.round(cx + p[0] * R * 0.96), py = (int) Math.round(cy - p[1] * R * 0.96);
					double pulse = (now / 1e9 * 1.2) % 1.0;
					int rr = 2 + (int) (pulse * 5);
					Gfx.outline(c, px - rr, py - rr, rr * 2 + 1, rr * 2 + 1, ((int) ((1 - pulse) * 150) << 24) | base);
					c.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
				}
			}
		}
		double[] hp = rot(vec(homeLon, homeLat), cy_, sy, ct, st);
		if (hp[2] > 0) {
			int px = (int) Math.round(cx + hp[0] * R * 0.96), py = (int) Math.round(cy - hp[1] * R * 0.96);
			c.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFD83A);
		}
	}

	private static double[] rot(double[] v, double cyw, double syw, double ct, double st) {
		double x = v[0] * cyw + v[2] * syw, z = -v[0] * syw + v[2] * cyw;
		double y = v[1] * ct - z * st, z2 = v[1] * st + z * ct;
		return new double[] {x, y, z2};
	}
}
