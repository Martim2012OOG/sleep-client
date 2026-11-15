package dev.solclient.tiktok;

import dev.solclient.Gfx;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Full-screen effects for gifts (confetti, fireworks, hearts, coins, lightning, stars, rainbow glow, shockwave,
 * sakura, embers).
 *
 * Built to be free: every particle lives in fixed arrays (no object per particle, nothing allocated while drawing),
 * the number of particles has a hard cap, and drawing is only plain filled rectangles (the cheapest thing the HUD can
 * draw). When nothing is playing, {@link #draw} returns on the first line.
 */
public final class TikFx {

	public static final String[] NAMES = {"Confetti", "Fireworks", "Hearts", "Coin Rain", "Lightning", "Starfall", "Rainbow Glow",
			"Shockwave", "Sakura", "Embers"};
	public static final int CONFETTI = 0, FIREWORKS = 1, HEARTS = 2, COINS = 3, LIGHTNING = 4, STARFALL = 5, RAINBOW = 6,
			SHOCKWAVE = 7, SAKURA = 8, EMBERS = 9;

	private static final int MAXP = 1600;
	private static final int K_RECT = 0, K_SPARK = 1, K_HEART = 2, K_COIN = 3, K_STAR = 4, K_PETAL = 5, K_EMBER = 6, K_ROCKET = 7;

	private static final int[] PAL = {0xFF5252, 0xFFB142, 0xFFEE58, 0x66FF99, 0x4DD0FF, 0x7C7CFF, 0xFF6BD6, 0xFFFFFF};
	private static final int[] PAL_HEART = {0xFF3B6B, 0xFF5C8A, 0xFF8FB1, 0xFF2E63, 0xFFFFFF};
	private static final int[] PAL_PETAL = {0xFFB7D5, 0xFF9EC4, 0xFFD6E8, 0xFFC2DA};
	private static final int[] PAL_EMBER = {0xFF7A1A, 0xFFB020, 0xFF4500, 0xFFD86B};
	private static final int[] PAL_STAR = {0xFFF59E, 0xFFFFFF, 0xBFE9FF, 0xFFD166};
	private static final float[] DRAG = {1.3F, 0.9F, 0F, 0.6F, 0F, 0F, 0.3F, 0F};
	private static final Identifier COIN = Gfx.id("textures/gui/coin_spin.png");

	// particles (structure of arrays)
	private final float[] x = new float[MAXP], y = new float[MAXP], vx = new float[MAXP], vy = new float[MAXP];
	private final float[] g = new float[MAXP], life = new float[MAXP], max = new float[MAXP], size = new float[MAXP];
	private final float[] rot = new float[MAXP], vr = new float[MAXP];
	private final int[] col = new int[MAXP];
	private final byte[] kind = new byte[MAXP];
	private int n;
	private int cap = 500;

	// emitters (things that keep spawning for a few seconds)
	private static final int ME = 10;
	private final int[] eType = new int[ME];
	private final float[] eT = new float[ME], eDur = new float[ME], eInt = new float[ME], eAcc = new float[ME];
	private int eN;

	// lightning
	private static final int BP = 26;
	private final float[][] bx = new float[3][BP], by = new float[3][BP];
	private int bolts;
	private float boltT;
	private float flash2At = -1F;

	// shockwave rings (negative age = waiting)
	private final float[] ring = new float[4];
	private int ringN;
	private float ringInt = 1F;

	private float flash, glowT, glowDur, glowInt = 1F;
	private float clock;
	private long last;
	private int w = 480, h = 270;
	private float load = 1F;
	private final Random rnd = new Random();

	/** Hard limit of particles on screen (the "Max particles" option). */
	public void setCap(int c) {
		cap = Math.max(50, Math.min(MAXP, c));
	}

	/** 1 = normal, less = spawn fewer particles (used when the game's FPS is low). */
	public void setLoad(float l) {
		load = Math.max(0.2F, Math.min(1F, l));
	}

	public boolean active() {
		return n > 0 || eN > 0 || bolts > 0 || ringN > 0 || flash > 0.01F || glowT > 0F;
	}

	public void clear() {
		n = 0;
		eN = 0;
		bolts = 0;
		ringN = 0;
		flash = 0;
		glowT = 0;
	}

	public void flash(float amount) {
		flash = Math.max(flash, Math.min(1F, amount));
	}

	/** Starts an effect. intensity 1 = normal, 2 = twice as much; seconds = how long it keeps going. */
	public void start(int type, float intensity, float seconds) {
		intensity = Math.max(0.3F, Math.min(3F, intensity));
		seconds = Math.max(0.8F, Math.min(15F, seconds));
		switch (type) {
			case CONFETTI -> {
				cannons(Math.round(70 * intensity));
				emitter(type, seconds, intensity);
			}
			case LIGHTNING -> strike(intensity);
			case SHOCKWAVE -> {
				ringN = Math.min(ring.length, 2 + Math.round(intensity));
				for (int i = 0; i < ringN; i++) {
					ring[i] = -0.18F * i;
				}
				ringInt = intensity;
				flash(0.35F);
			}
			case RAINBOW -> {
				glowT = Math.max(glowT, seconds);
				glowDur = Math.max(glowDur, seconds);
				glowInt = Math.min(1.4F, 0.7F + intensity * 0.3F);
			}
			default -> emitter(type, seconds, intensity);
		}
	}

	// ------------------------------------------------------------------ drawing
	public void draw(GuiGraphicsExtractor c, int sw, int sh) {
		if (!active()) {
			last = 0L;
			return;
		}
		w = sw;
		h = sh;
		long now = System.nanoTime();
		float dt = last == 0L ? 0.016F : Math.min(0.05F, (now - last) / 1_000_000_000F);
		last = now;
		clock += dt;
		update(dt);
		drawBackdrops(c, dt);
		drawParticles(c);
		drawLightning(c, dt);
		drawRings(c, dt);
		if (flash > 0.01F) {
			c.fill(0, 0, w, h, ((int) (flash * 150F) << 24) | 0xFFFFFF);
			flash -= dt * 2.4F;
		}
	}

	// ------------------------------------------------------------------ spawning
	private boolean add(int k, float px, float py, float pvx, float pvy, float grav, float lf, float sz, int color) {
		if (n >= cap) {
			return false;
		}
		int i = n++;
		x[i] = px;
		y[i] = py;
		vx[i] = pvx;
		vy[i] = pvy;
		g[i] = grav;
		life[i] = lf;
		max[i] = lf;
		size[i] = sz;
		col[i] = color;
		kind[i] = (byte) k;
		rot[i] = rnd.nextFloat() * 6.28F;
		vr[i] = 2F + rnd.nextFloat() * 6F;
		return true;
	}

	private void remove(int i) {
		int l = --n;
		if (i != l) {
			x[i] = x[l];
			y[i] = y[l];
			vx[i] = vx[l];
			vy[i] = vy[l];
			g[i] = g[l];
			life[i] = life[l];
			max[i] = max[l];
			size[i] = size[l];
			rot[i] = rot[l];
			vr[i] = vr[l];
			col[i] = col[l];
			kind[i] = kind[l];
		}
	}

	private float rf(float a, float b) {
		return a + rnd.nextFloat() * (b - a);
	}

	private static int pick(Random r, int[] pal) {
		return pal[r.nextInt(pal.length)];
	}

	private void emitter(int type, float seconds, float intensity) {
		int i = eN < ME ? eN++ : 0;
		eType[i] = type;
		eT[i] = 0F;
		eDur[i] = seconds;
		eInt[i] = intensity;
		eAcc[i] = 0F;
	}

	/** Two confetti cannons from the bottom corners. */
	private void cannons(int count) {
		for (int i = 0; i < count; i++) {
			boolean left = (i & 1) == 0;
			float ang = (float) Math.toRadians(rf(52F, 86F));
			float sp = rf(260F, 470F);
			float dx = (float) Math.cos(ang) * sp, dy = -(float) Math.sin(ang) * sp;
			if (!add(K_RECT, left ? rf(0, 14) : w - rf(0, 14), h + 4, left ? dx : -dx, dy, 420F, rf(2.2F, 3.4F), rf(4F, 7F), pick(rnd, PAL))) {
				return;
			}
		}
	}

	private void strike(float intensity) {
		bolts = intensity > 1.5F ? 3 : 2;
		for (int b = 0; b < bolts; b++) {
			float px = w * (0.15F + 0.7F * rnd.nextFloat());
			for (int k = 0; k < BP; k++) {
				bx[b][k] = px;
				by[b][k] = k * (h / (float) (BP - 1));
				px += (rnd.nextFloat() - 0.5F) * 30F;
				px = Math.max(8F, Math.min(w - 8F, px));
			}
		}
		boltT = 0.5F;
		flash(0.95F);
		flash2At = 0.34F; // a second, weaker flash right after the first (looks like real lightning)
	}

	private void explode(float px, float py, int color) {
		int count = Math.round(34 + rnd.nextInt(26) * Math.min(1.6F, load + 0.3F));
		boolean sphereRing = rnd.nextInt(3) == 0;
		float base = rf(70F, 120F);
		int second = rnd.nextInt(3) == 0 ? pick(rnd, PAL) : color;
		for (int i = 0; i < count; i++) {
			float a = (float) (Math.PI * 2 * i / count) + rnd.nextFloat() * 0.1F;
			float sp = sphereRing ? base : base * rf(0.35F, 1F);
			if (!add(K_SPARK, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 75F, rf(0.9F, 1.6F), rf(2F, 3.4F), i % 5 == 0 ? second : color)) {
				return;
			}
		}
		flash(0.12F);
	}

	// ------------------------------------------------------------------ simulation
	private void update(float dt) {
		for (int i = 0; i < eN; ) {
			eT[i] += dt;
			if (eT[i] >= eDur[i]) {
				eType[i] = eType[eN - 1];
				eT[i] = eT[eN - 1];
				eDur[i] = eDur[eN - 1];
				eInt[i] = eInt[eN - 1];
				eAcc[i] = eAcc[eN - 1];
				eN--;
				continue;
			}
			float rate = switch (eType[i]) {
				case CONFETTI -> 45F;
				case FIREWORKS -> 2.6F;
				case HEARTS -> 22F;
				case COINS -> 26F;
				case STARFALL -> 30F;
				case SAKURA -> 34F;
				case EMBERS -> 70F;
				default -> 0F;
			};
			// fade the emitter out in its last second so effects end softly
			float tail = Math.min(1F, (eDur[i] - eT[i]) / 1F);
			eAcc[i] += rate * eInt[i] * load * tail * dt;
			while (eAcc[i] >= 1F) {
				eAcc[i] -= 1F;
				spawn(eType[i]);
			}
			i++;
		}
		if (flash2At >= 0F) {
			flash2At -= dt;
			if (flash2At < 0F) {
				flash(0.6F);
				flash2At = -1F;
			}
		}
		if (boltT > 0F) {
			boltT -= dt;
			if (boltT <= 0F) {
				bolts = 0;
			}
		}
		if (glowT > 0F) {
			glowT -= dt;
			if (glowT <= 0F) {
				glowT = 0F;
				glowDur = 0F;
			}
		}
		for (int i = 0; i < n; ) {
			life[i] -= dt;
			if (life[i] <= 0F || y[i] > h + 30F || y[i] < -60F || x[i] < -60F || x[i] > w + 60F && kind[i] != K_STAR) {
				if (kind[i] == K_ROCKET && life[i] <= 0F) {
					float ex = x[i], ey = y[i];
					int c = col[i];
					remove(i);
					explode(ex, ey, c);
					continue;
				}
				remove(i);
				continue;
			}
			int k = kind[i];
			float d = DRAG[k];
			vy[i] += g[i] * dt;
			if (d > 0F) {
				float f = Math.max(0F, 1F - d * dt);
				vx[i] *= f;
				vy[i] *= f;
			}
			rot[i] += vr[i] * dt;
			x[i] += vx[i] * dt;
			y[i] += vy[i] * dt;
			if (k == K_HEART) {
				x[i] += (float) Math.sin(rot[i] * 0.6F) * 22F * dt;
			} else if (k == K_PETAL) {
				x[i] += (float) Math.sin(rot[i] * 0.5F) * 26F * dt;
			} else if (k == K_ROCKET) {
				add(K_SPARK, x[i], y[i] + 3F, rf(-8F, 8F), rf(10F, 30F), 0F, 0.35F, 2F, 0xFFC36B);
			} else if (k == K_STAR && rnd.nextInt(5) == 0) {
				add(K_SPARK, x[i], y[i], rf(-10F, 10F), rf(-10F, 10F), 0F, 0.4F, 2F, 0xFFF59E);
			}
			i++;
		}
	}

	private void spawn(int type) {
		switch (type) {
			case CONFETTI -> add(K_RECT, rf(0, w), -6F, rf(-30F, 30F), rf(30F, 90F), 90F, rf(3F, 5F), rf(4F, 7F), pick(rnd, PAL));
			case FIREWORKS -> {
				float target = h * rf(0.42F, 0.78F);
				float grav = 140F;
				float v0 = (float) Math.sqrt(2F * grav * target);
				add(K_ROCKET, w * rf(0.12F, 0.88F), h, rf(-14F, 14F), -v0, grav, v0 / grav, 3F, pick(rnd, PAL));
			}
			case HEARTS -> add(K_HEART, rf(0, w), h + 8F, 0F, -rf(55F, 125F), 0F, rf(3F, 5.5F), rf(6F, 14F), pick(rnd, PAL_HEART));
			case COINS -> add(K_COIN, rf(0, w), -14F, rf(-12F, 12F), rf(70F, 140F), 120F, rf(2.6F, 4F), rf(10F, 15F), 0xFFFFFFFF);
			case STARFALL -> add(K_STAR, rf(w * 0.2F, w * 1.1F), -8F, -rf(90F, 160F), rf(120F, 190F), 0F, rf(1.4F, 2.4F), rf(3F, 6F), pick(rnd, PAL_STAR));
			case SAKURA -> add(K_PETAL, rf(0, w * 1.1F), -8F, -rf(15F, 55F), rf(28F, 70F), 0F, rf(5F, 9F), rf(5F, 9F), pick(rnd, PAL_PETAL));
			case EMBERS -> add(K_EMBER, rf(0, w), h + 4F, rf(-16F, 16F), -rf(40F, 120F), -8F, rf(1.5F, 3.2F), rf(2F, 3.6F), pick(rnd, PAL_EMBER));
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ drawing parts
	private void drawBackdrops(GuiGraphicsExtractor c, float dt) {
		if (glowT > 0F) {
			float a = Math.min(1F, Math.min((glowDur - glowT) / 0.35F, glowT / 0.8F)) * glowInt;
			a = Math.max(0F, Math.min(1F, a));
			int e = Math.max(18, Math.min(w, h) / 6);
			int segX = 14, segY = 8;
			for (int i = 0; i < segX; i++) {
				int rgb = java.awt.Color.HSBtoRGB((clock * 0.45F + i / (float) segX) % 1F, 0.85F, 1F) & 0xFFFFFF;
				int x0 = i * w / segX, x1 = (i + 1) * w / segX;
				int top = ((int) (a * 190F) << 24) | rgb;
				c.fillGradient(x0, 0, x1, e, top, rgb);
				int rgb2 = java.awt.Color.HSBtoRGB((clock * 0.45F + 0.5F + i / (float) segX) % 1F, 0.85F, 1F) & 0xFFFFFF;
				c.fillGradient(x0, h - e, x1, h, rgb2, ((int) (a * 190F) << 24) | rgb2);
			}
			for (int i = 0; i < segY; i++) {
				int rgbL = java.awt.Color.HSBtoRGB((clock * 0.45F + 0.25F + i / (float) segY) % 1F, 0.85F, 1F) & 0xFFFFFF;
				int y0 = i * h / segY, y1 = (i + 1) * h / segY;
				for (int s = 0; s < 6; s++) {
					int al = (int) (a * 170F * (1F - s / 6F));
					int px = s * e / 6, pw = (s + 1) * e / 6;
					int colr = (al << 24) | rgbL;
					c.fill(px, y0, pw, y1, colr);
					c.fill(w - pw, y0, w - px, y1, colr);
				}
			}
		}
		for (int i = 0; i < eN; i++) {
			if (eType[i] == EMBERS) {
				float a = Math.min(1F, Math.min(eT[i] / 0.5F, (eDur[i] - eT[i]) / 1F));
				int e = Math.max(30, h / 4);
				c.fillGradient(0, h - e, w, h, 0x00FF5A1A, ((int) (a * 120F) << 24) | 0xFF5A1A);
			}
		}
	}

	private void drawParticles(GuiGraphicsExtractor c) {
		for (int i = 0; i < n; i++) {
			float fade = Math.min(0.6F, max[i] * 0.4F);
			float a = Math.min(1F, life[i] / fade);
			int k = kind[i];
			if (k == K_STAR || k == K_EMBER) {
				a *= 0.65F + 0.35F * (float) Math.sin(rot[i] * 1.7F);
			}
			int al = (int) (a * 255F);
			if (al < 6) {
				continue;
			}
			int cc = (al << 24) | (col[i] & 0xFFFFFF);
			int px = (int) x[i], py = (int) y[i];
			switch (k) {
				case K_RECT -> {
					int ww = Math.max(1, (int) (size[i] * Math.abs(Math.cos(rot[i]))));
					int hh = Math.max(2, (int) (size[i] * 0.55F));
					c.fill(px - ww / 2, py, px - ww / 2 + ww, py + hh, cc);
				}
				case K_SPARK -> {
					int s = Math.max(1, (int) size[i]);
					c.fill(px, py, px + s, py + s, cc);
				}
				case K_HEART -> Gfx.heart(c, x[i], y[i], size[i], cc);
				case K_COIN -> {
					int s = Math.max(6, (int) size[i]);
					int frame = ((int) rot[i]) % 7;
					Gfx.texUv(c, COIN, px, py, s, s, frame * 14, 0, 14, 14, 98, 14, (al << 24) | 0xFFFFFF);
				}
				case K_STAR -> {
					int s = Math.max(2, (int) size[i]);
					c.fill(px - s, py, px + s + 1, py + 1, cc);
					c.fill(px, py - s, px + 1, py + s + 1, cc);
					c.fill(px - 1, py - 1, px + 2, py + 2, (al << 24) | 0xFFFFFF);
				}
				case K_PETAL -> {
					int ww = Math.max(1, (int) (size[i] * (0.45F + 0.55F * Math.abs(Math.cos(rot[i])))));
					int hh = Math.max(2, (int) (size[i] * 0.6F));
					c.fill(px - ww / 2, py, px - ww / 2 + ww, py + hh, cc);
				}
				case K_EMBER -> {
					int s = Math.max(1, (int) size[i]);
					c.fill(px, py, px + s, py + s, cc);
				}
				default -> c.fill(px, py, px + 2, py + 5, (al << 24) | 0xFFFFFF); // rocket
			}
		}
	}

	private void drawLightning(GuiGraphicsExtractor c, float dt) {
		if (bolts <= 0) {
			return;
		}
		float a = Math.max(0F, Math.min(1F, boltT / 0.35F));
		boolean flick = ((int) (clock * 40F)) % 3 != 0;
		if (!flick) {
			a *= 0.55F;
		}
		for (int b = 0; b < bolts; b++) {
			for (int k = 0; k < BP - 1; k++) {
				line(c, bx[b][k], by[b][k], bx[b][k + 1], by[b][k + 1], 5, 2, ((int) (a * 70F) << 24) | 0x6FA8FF);
				line(c, bx[b][k], by[b][k], bx[b][k + 1], by[b][k + 1], 2, 1, ((int) (a * 255F) << 24) | 0xF2F8FF);
			}
		}
	}

	/** Thick line made of small squares. step = pixels between squares. */
	private static void line(GuiGraphicsExtractor c, float x0, float y0, float x1, float y1, int thick, int step, int color) {
		float dx = x1 - x0, dy = y1 - y0;
		int steps = Math.max(1, (int) (Math.max(Math.abs(dx), Math.abs(dy)) / step));
		int off = thick / 2;
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			int px = (int) (x0 + dx * t) - off, py = (int) (y0 + dy * t) - off;
			c.fill(px, py, px + thick, py + thick, color);
		}
	}

	private void drawRings(GuiGraphicsExtractor c, float dt) {
		if (ringN <= 0) {
			return;
		}
		float cx = w / 2F, cy = h / 2F;
		float maxR = (float) Math.hypot(w, h) * 0.55F;
		boolean any = false;
		for (int i = 0; i < ringN; i++) {
			ring[i] += dt;
			float age = ring[i];
			if (age < 0F) {
				any = true;
				continue;
			}
			float p = age / 1.1F;
			if (p >= 1F) {
				continue;
			}
			any = true;
			float r = (1F - (1F - p) * (1F - p)) * maxR;
			float a = (1F - p) * (1F - p);
			int pts = Math.max(32, Math.min(220, (int) (r * 0.9F)));
			int al = (int) (Math.min(1F, a * ringInt) * 230F);
			int thick = p < 0.4F ? 3 : 2;
			for (int j = 0; j < pts; j++) {
				double ang = Math.PI * 2 * j / pts;
				int px = (int) (cx + Math.cos(ang) * r), py = (int) (cy + Math.sin(ang) * r);
				c.fill(px, py, px + thick, py + thick, (al << 24) | 0xBFEFFF);
				if (r > 6F) {
					int qx = (int) (cx + Math.cos(ang) * (r - 5F)), qy = (int) (cy + Math.sin(ang) * (r - 5F));
					c.fill(qx, qy, qx + 2, qy + 2, ((al / 3) << 24) | 0x7FD0FF);
				}
			}
		}
		if (!any) {
			ringN = 0;
		}
	}
}
