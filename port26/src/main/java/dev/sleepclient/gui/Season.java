package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.SleepClient;
import java.time.LocalDate;
import java.time.Month;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Seasonal decorations for the home screen: a pumpkin next to the logo in October (Halloween, until 2 November),
 * a Santa hat on the logo and falling snow at Christmas (1 December - 6 January).
 * Settings > Menus > Seasonal Decorations: Automatic / Off / Halloween / Christmas.
 */
final class Season {

	static final int NONE = 0, HALLOWEEN = 1, CHRISTMAS = 2;

	private static final int FLAKES = 70;
	private static final float[] FX = new float[FLAKES], FY = new float[FLAKES], FS = new float[FLAKES], FV = new float[FLAKES], FP = new float[FLAKES];
	private static boolean seeded;
	private static long last;

	private Season() {
	}

	static int current() {
		int mode = SleepClient.settings == null ? 0 : SleepClient.settings.seasonal.idx();
		if (mode == 1) {
			return NONE;
		}
		if (mode == 2) {
			return HALLOWEEN;
		}
		if (mode == 3) {
			return CHRISTMAS;
		}
		// Automatic: Christmas from the end of October until the end of December. Halloween only when picked by hand.
		LocalDate d = LocalDate.now();
		if ((d.getMonth() == Month.OCTOBER && d.getDayOfMonth() >= 28) || d.getMonth() == Month.NOVEMBER || d.getMonth() == Month.DECEMBER) {
			return CHRISTMAS;
		}
		return NONE;
	}

	/** Pumpkin / hat around the logo drawn at (x, y) with size s. */
	static void decorateLogo(GuiGraphicsExtractor c, int x, int y, int s) {
		int season = current();
		if (season == HALLOWEEN) {
			// small pumpkin held at the logo's little paw (bottom-right)
			int ps = Math.round(s * 0.27F);
			Gfx.tex(c, Gfx.id("textures/gui/season/pumpkin.png"), x + Math.round(s * 0.66F), y + Math.round(s * 0.66F), ps, ps, 128, 128, 0xFFFFFFFF);
		} else if (season == CHRISTMAS) {
			// the original Santa hat, turned a little and sitting on the cube's head (not floating over the wings)
			Gfx.tex(c, Gfx.id("textures/gui/season/santa_hat2.png"), x + Math.round(s * 91F / 256F), y + Math.round(s * 26F / 256F),
					Math.round(s * 143F / 256F), Math.round(s * 102F / 256F), 152, 109, 0xFFFFFFFF);
		}
	}

	/** Falling snow without mouse interaction (other screens). */
	static void snow(GuiGraphicsExtractor c, int w, int h) {
		snow(c, w, h, -9999, -9999);
	}

	// ---- the snow on the main menu: every flake has a position and a speed; the mouse blows them away, a drag grabs one (throw it,
	// or pull it out of the window, and it is gone for a while); the ones that land make the pile on the floor grow
	private static final float[] PX = new float[FLAKES], PY = new float[FLAKES], VX = new float[FLAKES], VY = new float[FLAKES], DEAD = new float[FLAKES];
	private static int sw, sh, grab = -1;
	private static float gvx, gvy;
	private static final java.util.Random RNG = new java.util.Random();

	private static void respawn(int i, int w) {
		PX[i] = RNG.nextFloat() * w;
		PY[i] = -10F - RNG.nextFloat() * 20F;
		VX[i] = 0F;
		VY[i] = 0F;
		DEAD[i] = -1F;
	}

	/** Pressing on a flake picks it up. True when one was taken. */
	static boolean snowGrab(double mx, double my) {
		if (current() != CHRISTMAS || !seeded) {
			return false;
		}
		int best = -1;
		float bd = Float.MAX_VALUE;
		for (int i = 0; i < FLAKES; i++) {
			if (DEAD[i] >= 0F) {
				continue;
			}
			float cx = PX[i] + FS[i] / 2F, cy = PY[i] + FS[i] / 2F;
			float d = (float) Math.hypot(cx - mx, cy - my);
			if (d < Math.max(9F, FS[i] + 4F) && d < bd) {
				bd = d;
				best = i;
			}
		}
		if (best < 0) {
			return false;
		}
		grab = best;
		gvx = 0F;
		gvy = 0F;
		return true;
	}

	static boolean snowHeld() {
		return grab >= 0;
	}

	/** The mouse moved while holding a flake: outside the window it is deleted. */
	static void snowDrag(double mx, double my) {
		if (grab < 0) {
			return;
		}
		if (mx < 1 || my < 1 || mx > sw - 1 || my > sh - 1) {
			DEAD[grab] = 25F; // pulled off the screen: deleted (a new one falls in much later)
			grab = -1;
		}
	}

	static void snowRelease() {
		if (grab >= 0) {
			VX[grab] = Math.max(-1600F, Math.min(1600F, gvx));
			VY[grab] = Math.max(-1600F, Math.min(1600F, gvy));
			grab = -1;
		}
	}

	static void snow(GuiGraphicsExtractor c, int w, int h, int mx, int my) {
		if (current() != CHRISTMAS) {
			return;
		}
		long now = System.currentTimeMillis();
		float dt = last == 0 ? 0F : Math.min(0.05F, (now - last) / 1000F);
		last = now;
		if (!seeded) {
			for (int i = 0; i < FLAKES; i++) {
				FS[i] = 3F + RNG.nextFloat() * 5F;
				FV[i] = 0.04F + RNG.nextFloat() * 0.07F;
				FP[i] = RNG.nextFloat() * 6.28F;
				respawn(i, w);
				PY[i] = RNG.nextFloat() * h;
			}
			seeded = true;
		}
		if (sw != w || sh != h) {
			if (sw > 0 && sh > 0) {
				for (int i = 0; i < FLAKES; i++) {
					PX[i] *= (float) w / sw;
					PY[i] *= (float) h / sh;
				}
			}
			sw = w;
			sh = h;
		}
		double t = now / 1000.0;
		float relax = 1F - (float) Math.exp(-dt * 2.2F);
		for (int i = 0; i < FLAKES; i++) {
			if (DEAD[i] >= 0F) {
				DEAD[i] -= dt;
				if (DEAD[i] < 0F) {
					respawn(i, w);
				}
				continue;
			}
			if (i == grab) {
				float k = 1F - (float) Math.exp(-dt * 40F);
				float nx = PX[i] + (mx - FS[i] / 2F - PX[i]) * k, ny = PY[i] + (my - FS[i] / 2F - PY[i]) * k;
				if (dt > 0F) {
					gvx += ((nx - PX[i]) / dt - gvx) * 0.35F;
					gvy += ((ny - PY[i]) / dt - gvy) * 0.35F;
				}
				PX[i] = nx;
				PY[i] = ny;
				continue;
			}
			float fall = FV[i] * h * (0.6F + FS[i] / 8F);
			float tvx = (float) Math.sin(t * 0.8 + FP[i]) * 12F;
			VX[i] += (tvx - VX[i]) * relax;
			VY[i] += (fall - VY[i]) * relax;
			float dx = PX[i] + FS[i] / 2F - mx, dy = PY[i] + FS[i] / 2F - my;
			float d2 = dx * dx + dy * dy;
			if (grab < 0 && d2 < 2500F && d2 > 0.01F) { // the mouse blows flakes away
				float d = (float) Math.sqrt(d2), f = (1F - d / 50F) * 1400F * dt / d;
				VX[i] += dx * f;
				VY[i] += dy * f;
			}
			PX[i] += VX[i] * dt;
			PY[i] += VY[i] * dt;
			if (PY[i] >= h - crest((int) Math.max(0F, Math.min(w - 1F, PX[i]))) - 1F) { // landed: the pile grows
				level = Math.min(LEVEL_MAX, level + 0.012F);
				respawn(i, w);
			} else if (PX[i] < -24F || PX[i] > w + 24F || PY[i] < -60F) { // thrown off the screen: deleted
				DEAD[i] = 25F;
			}
		}
		for (int i = 0; i < FLAKES; i++) {
			if (DEAD[i] >= 0F) {
				continue;
			}
			int s = Math.round(FS[i]) + (i == grab ? 2 : 0);
			int a = i == grab ? 255 : 0x60 + Math.round(FS[i] * 16);
			Gfx.tex(c, Gfx.id("textures/gui/season/snowflake.png"), Math.round(PX[i]), Math.round(PY[i]), s, s, 16, 16, (Math.min(255, a) << 24) | 0xFFFFFF);
		}
	}

	/** True when a season is on and its wallpaper should be used (it wins over the intro video / custom sky). */
	static boolean active() {
		return current() != NONE && (SleepClient.settings == null || SleepClient.settings.seasonalBackground.on());
	}

	/** Full-screen seasonal wallpaper (Christmas tree / Halloween pumpkins), cropped to fill the screen. */
	static void background(GuiGraphicsExtractor c, int w, int h) {
		int s = current();
		if (s == NONE || SleepClient.settings != null && !SleepClient.settings.seasonalBackground.on()) {
			return;
		}
		String file = s == CHRISTMAS ? "bg_christmas2.png" : "bg_halloween2.png";
		int tw = s == CHRISTMAS ? 1280 : 1472, th = s == CHRISTMAS ? 720 : 864;
		// a slow "camera" drift so the picture feels alive
		double t = System.currentTimeMillis() / 1000.0;
		float zoom = 1.06F + 0.02F * (float) Math.sin(t * 0.12);
		float screen = (float) w / h, img = (float) tw / th;
		float uw = tw / zoom, vh = th / zoom;
		if (screen > uw / vh) {
			vh = uw / screen;
		} else {
			uw = vh * screen;
		}
		float u = (tw - uw) / 2 + (float) Math.sin(t * 0.07) * (tw - uw) * 0.35F, v = (th - vh) / 2 + (float) Math.cos(t * 0.05) * (th - vh) * 0.25F;
		Gfx.texUv(c, Gfx.id("textures/gui/season/" + file), 0, 0, w, h, u, v, Math.round(uw), Math.round(vh), tw, th, 0xFFFFFFFF);
		// a little darker so the buttons stay readable
		c.fill(0, 0, w, h, 0x26000000);
		Gfx.gradientV(c, 0, h * 2 / 3, w, h - h * 2 / 3, 0x00000000, 0x70000000);
	}

	// ------------------------------------------------------------------ animated decorations
	private static final int BATS = 7, LEAVES = 16, GIFTS = 5;
	private static final float[][] BAT = new float[BATS][5], LEAF = new float[LEAVES][5];
	private static boolean decoSeeded;
	private static long lastDeco, nextFlash, flashAt;

	private static void seedDeco() {
		if (decoSeeded) {
			return;
		}
		decoSeeded = true;
		Random r = new Random(7);
		for (float[] b : BAT) {
			b[0] = r.nextFloat(); // x 0..1
			b[1] = 0.08F + r.nextFloat() * 0.45F; // y
			b[2] = 0.035F + r.nextFloat() * 0.05F; // speed
			b[3] = 12 + r.nextFloat() * 14; // size
			b[4] = r.nextFloat() * 6.28F; // phase
		}
		for (float[] l : LEAF) {
			l[0] = r.nextFloat();
			l[1] = r.nextFloat();
			l[2] = 0.03F + r.nextFloat() * 0.05F;
			l[3] = 6 + r.nextFloat() * 6;
			l[4] = r.nextFloat() * 6.28F;
		}
	}

	/** Moving decorations on top of the wallpaper (under the buttons): bats, ghosts, leaves, webs / lights, gifts, snow. */
	static void overlay(GuiGraphicsExtractor c, int w, int h) {
		int s = current();
		if (s == NONE) {
			return;
		}
		seedDeco();
		long now = System.currentTimeMillis();
		float dt = lastDeco == 0 ? 0 : Math.min(0.1F, (now - lastDeco) / 1000F);
		lastDeco = now;
		double t = now / 1000.0;
		if (s == HALLOWEEN) {
			halloween(c, w, h, dt, t, now);
		} else {
			christmas(c, w, h, t);
		}
	}

	private static void halloween(GuiGraphicsExtractor c, int w, int h, float dt, double t, long now) {
		var m = c.pose();
		// drifting purple fog near the ground
		for (int i = 0; i < 3; i++) {
			float fx = (float) ((t * (8 + i * 5) + i * 300) % (w + 400)) - 200;
			Gfx.tex(c, Gfx.id("textures/gui/season/glow.png"), Math.round(fx), h - 120 + i * 18, 420, 150, 64, 64, 0x30A070FF);
		}
		// warm flicker of the pumpkins
		float fl = 0.5F + 0.25F * (float) Math.sin(t * 9) + 0.25F * (float) Math.sin(t * 23.0 + 1.3);
		Gfx.gradientV(c, 0, h / 2, w, h / 2, 0x00000000, ((int) (22 + 18 * fl) << 24) | 0xFF7A1A);
		// cobwebs in the top corners
		int cs = Math.max(60, Math.min(120, h / 4));
		Gfx.tex(c, Gfx.id("textures/gui/season/cobweb.png"), 0, 0, cs, cs, 128, 128, 0xB0FFFFFF);
		m.pushMatrix();
		m.translate(w, 0);
		m.rotate((float) Math.toRadians(90));
		Gfx.tex(c, Gfx.id("textures/gui/season/cobweb.png"), 0, 0, cs, cs, 128, 128, 0xB0FFFFFF);
		m.popMatrix();
		// a little spider going up and down on its thread
		float sy = 30 + (float) (Math.sin(t * 0.6) * 0.5 + 0.5) * cs * 0.9F;
		c.fill(w - cs / 3, 0, w - cs / 3 + 1, Math.round(sy), 0x90FFFFFF);
		Gfx.circle(c, w - cs / 3F + 0.5F, sy + 3, 3.5F, 0xFF101015);
		// bats
		for (float[] b : BAT) {
			b[0] += b[2] * dt;
			if (b[0] > 1.1F) {
				b[0] = -0.1F;
				b[1] = 0.08F + (float) Math.random() * 0.45F;
			}
			float bx = b[0] * w, by = b[1] * h + (float) Math.sin(t * 2 + b[4]) * 14;
			int frame = ((int) (t * 8 + b[4] * 3)) % 2;
			int sz = Math.round(b[3]);
			Gfx.texUv(c, Gfx.id("textures/gui/season/bat.png"), Math.round(bx), Math.round(by), sz, sz, frame * 16, 0, 16, 16, 32, 16, 0xFFFFFFFF);
		}
		// two ghosts floating
		for (int i = 0; i < 2; i++) {
			float gx = (float) ((t * (6 + i * 4) + i * w * 0.6) % (w + 80)) - 40;
			float gy = h * (0.35F + i * 0.2F) + (float) Math.sin(t * 1.3 + i * 2) * 10;
			int a = (int) (90 + 60 * Math.sin(t * 0.8 + i));
			Gfx.tex(c, Gfx.id("textures/gui/season/ghost.png"), Math.round(gx), Math.round(gy), 28, 28, 16, 16, (a << 24) | 0xFFFFFF);
		}
		// falling leaves
		for (float[] l : LEAF) {
			l[1] += l[2] * dt;
			if (l[1] > 1.05F) {
				l[1] = -0.05F;
				l[0] = (float) Math.random();
			}
			float lx = l[0] * w + (float) Math.sin(t * 1.1 + l[4]) * 22, ly = l[1] * h;
			m.pushMatrix();
			m.translate(lx, ly);
			m.rotate((float) (t * 1.5 + l[4]));
			int ls = Math.round(l[3]);
			Gfx.tex(c, Gfx.id("textures/gui/season/leaf.png"), -ls / 2, -ls / 2, ls, ls, 8, 8, 0xE0FFFFFF);
			m.popMatrix();
		}
		// lightning now and then
		if (nextFlash == 0) {
			nextFlash = now + 9000;
		}
		if (now > nextFlash) {
			flashAt = now;
			nextFlash = now + 9000 + (long) (Math.random() * 9000);
		}
		long fa = now - flashAt;
		if (fa < 450) {
			float k = fa < 80 ? 1 : fa < 160 ? 0.3F : fa < 230 ? 0.8F : (450 - fa) / 220F * 0.5F;
			c.fill(0, 0, w, h, ((int) (k * 110) << 24) | 0xD8E0FF);
		}
	}

	private static final int[] BULBS = {0xFFFF3B3B, 0xFFFFD23B, 0xFF3BFF6E, 0xFF3BB4FF, 0xFFFF5BD8};

	private static void christmas(GuiGraphicsExtractor c, int w, int h, double t) {
		// twinkling stars in the sky
		Random r = new Random(11);
		for (int i = 0; i < 40; i++) {
			float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.45F;
			float k = (float) (0.5 + 0.5 * Math.sin(t * (1.5 + r.nextFloat() * 2) + i));
			Gfx.tex(c, Gfx.id("textures/gui/season/glow.png"), Math.round(x - 4), Math.round(y - 4), 8, 8, 64, 64, ((int) (60 + 150 * k) << 24) | 0xFFFFFF);
		}
		// string lights hanging across the top (two loops)
		int loops = 2;
		for (int L = 0; L < loops; L++) {
			float x0 = w * L / (float) loops, x1 = w * (L + 1) / (float) loops, sag = Math.max(18, h / 14F);
			int prevX = -1, prevY = -1;
			for (int i = 0; i <= 40; i++) {
				float k = i / 40F;
				int x = Math.round(x0 + (x1 - x0) * k), y = Math.round(4 + sag * 4 * k * (1 - k));
				if (prevX >= 0) {
					int steps = Math.max(1, Math.abs(x - prevX));
					for (int sidx = 0; sidx < steps; sidx++) {
						int xx = prevX + (x - prevX) * sidx / steps, yy = prevY + (y - prevY) * sidx / steps;
						c.fill(xx, yy, xx + 1, yy + 1, 0xFF1C2A1C);
					}
				}
				prevX = x;
				prevY = y;
			}
			int n = Math.max(6, Math.round((x1 - x0) / 26));
			for (int i = 1; i < n; i++) {
				float k = i / (float) n;
				float x = x0 + (x1 - x0) * k, y = 4 + sag * 4 * k * (1 - k);
				int col = BULBS[(i + L * 3) % BULBS.length];
				float tw = (float) (0.55 + 0.45 * Math.sin(t * 3 + i * 1.7 + L));
				Gfx.tex(c, Gfx.id("textures/gui/season/glow.png"), Math.round(x - 11), Math.round(y - 5), 22, 22, 64, 64,
						((int) (90 * tw) << 24) | (col & 0xFFFFFF));
				c.fill(Math.round(x) - 1, Math.round(y), Math.round(x) + 2, Math.round(y) + 2, 0xFF2A3A2A);
				Gfx.rrectFree(c, x - 2, y + 2, 4, 6, 2, Gfx.blend(col, 0xFF000000, 0.4F - 0.4F * tw));
			}
		}
		// snow piled up along the bottom
		snowPile(c, w, h);
		// (the presents that used to pile up in the bottom-left corner are gone)
	}

	// ------------------------------------------------------------------ snow along the bottom
	private static final int COL = 2;
	/** How deep the snow is (grows very slowly, then simply stays: no sudden melting). */
	private static float level = 6F;
	private static long pileAt;
	private static final float LEVEL_MAX = 20F;

	/** A soft layer of snow with gentle dunes: bright crest, a pale body and a cool shade at the base, with a few glints. */
	private static void snowPile(GuiGraphicsExtractor c, int w, int h) {
		long now = System.currentTimeMillis();
		float dt = pileAt == 0 ? 0F : Math.min(0.1F, (now - pileAt) / 1000F);
		pileAt = now;
		
		double t = now / 1000.0;
		int n = w / COL + 1;
		for (int i = 0; i < n; i++) {
			int x = i * COL;
			float hh = crest(x);
			int ih = (int) hh;
			float frac = hh - ih;
			int top = h - ih - 1;
			if (frac > 0.05F) {
				c.fill(x, top - 1, x + COL, top, Gfx.withAlpha(0xFFFFFF, Math.round(frac * 235F))); // soft, anti-aliased edge
			}
			c.fill(x, top, x + COL, top + 2, 0xFFFFFFFF); // lit crest
			c.fill(x, top + 2, x + COL, h - 6, 0xFFEDF3FC); // body
			c.fill(x, h - 6, x + COL, h, 0xFFC9D8EE); // cool shade at the base
		}
		for (int k = 0; k < 6; k++) { // a few glints on the crest
			int gx = (int) ((Math.sin(k * 91.7 + Math.floor(t * 0.6 + k)) * 0.5 + 0.5) * w);
			float tw = (float) (0.5 + 0.5 * Math.sin(t * 3 + k * 1.7));
			int gy = h - (int) crest(gx) - 2;
			c.fill(gx, gy, gx + 1, gy + 1, Gfx.withAlpha(0xFFFFFF, Math.round(200F * tw)));
		}
	}

	/** Height of the snow at a screen x: slow low-frequency dunes, never a flat bar. */
	private static float crest(int x) {
		double shape = 0.62 + 0.20 * Math.sin(x * 0.0105 + 1.3) + 0.12 * Math.sin(x * 0.0273 + 0.4) + 0.06 * Math.sin(x * 0.071 + 2.1);
		return (float) (level * shape * 1.25);
	}
}
