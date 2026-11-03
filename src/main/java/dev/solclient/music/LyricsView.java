package dev.solclient.music;

import dev.solclient.Gfx;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;

/**
 * Draws the lyrics like the Neonlyrics mod: (1) a dark capsule at the top with the current line, the part being sung
 * highlighted; (2) the same line big, letter by letter, as a flat sheet of text turned and tilted in 3D (every LETTER is projected on its own,
 * so the far side is smaller and the near side bigger, with thick letters). Each line has its own pose and can sit in the middle,
 * on the left or on the right of the screen. It is locked to the screen and only sways a little when you turn.
 */
public final class LyricsView {

	/** Settings, filled by the mod every frame. */
	public static final class Cfg {
		public boolean bar = true, world = true, onlyPlaying = true;
		/** handwritten font (like Neonlyrics) for the big words; false = the normal game font in italics */
		public boolean script = true;
		/** the font the player imported (Custom Font), or null */
		public StyleSpriteSource fontOverride;
		/** size multiplier, height of the lines above the horizon in degrees, sync offset in ms */
		public float size = 1F, height = 18F, offsetMs;
		public int highlight = 0xFF8B5CF6, text = 0xFFFFFFFF;
		/** 0 = one colour (text), 1 = my palette, one colour per word in turn, 2 = rainbow */
		public int colourMode;
		/** opacity of everything (0.1..1) and size of the top bar (separate from the size of the big words) */
		public float opacity = 1F, barSize = 1F;
		/** the palette used when colourMode == 1 (first entry is the text colour) */
		public int[] palette = {0xFFFFFFFF, 0xFFFF6FB5, 0xFF7DD3FC, 0xFFFDE047};
		/** how many entries of the palette are used (2..4) */
		public int paletteSize = 4;
		/** 3D look of the big words (0..1) and how much they sway when the camera turns (0..1) */
		public float depth = 0.7F, sway = 0.6F;
		/** where the lines go: 0 centre, 1 left, 2 right, 3 left and right in turn, 4 random */
		public int position;
		/** how far from the middle of the screen the left / right lines are (fraction of the screen width) */
		public float spread = 0.24F;
		/** capital letters, strength of the glow (0..1.5), entrance of the big words (0 letters pop, 1 fade, 2 rise, 3 zoom) */
		public boolean upper;
		public float glow = 1F;
		public int lAnim;
		/** gradient colours (colourMode 3), how many are used, and how fast they slide along (0 = still) */
		public int[] grad = {0xFF8B5CF6, 0xFFEC4899};
		public float gradFlow;
		/** "in front of me" mode: how far ahead a line appears (blocks), how far away it fades out (blocks), how many old lines stay behind */
		public boolean front;
		public float frontDist = 8F, frontFade = 24F;
		public int frontKeep = 3;
	}

	/** Where a line was placed when it started: side of the screen and its own 3D pose (each line leans differently, like the reference). */
	private static final class Anchor {
		int line = -2;
		int version;
		int mode = -1;
		long leftAt; // when it stopped being the current line (0 = still current)
		int side; // -1 left, 0 centre, 1 right
		float yaw, pitch, roll; // degrees
		float dy; // vertical shift (fraction of the screen height)
	}

	/** Handwriting font, from assets/sol_client/font/lyrics.json (Nothing You Could Do, SIL OFL). */
	private static final StyleSpriteSource FONT = new StyleSpriteSource.Font(Identifier.of("sol_client", "lyrics"));
	private static Anchor cur = new Anchor(), old = new Anchor();
	/** camera sway: how far the sheet has been "dragged" by turning (screen px) and the last camera angles */
	private static float swayX, swayY, lastYaw, lastPitch;
	private static long lastFrame;
	private static boolean haveAngles;

	private LyricsView() {
	}

	/** Called once per frame: turning the camera pushes the sheet a little (like it has weight), then it springs back to the centre. */
	private static void updateSway(MinecraftClient mc, Cfg cfg, long now, int sw) {
		float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
		float dt = lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1000F);
		lastFrame = now;
		if (haveAngles) {
			float dy = yaw - lastYaw;
			dy -= 360F * Math.round(dy / 360F);
			float dp = pitch - lastPitch;
			swayX -= dy * 1.5F * cfg.sway;
			swayY -= dp * 1.5F * cfg.sway;
		}
		haveAngles = true;
		lastYaw = yaw;
		lastPitch = pitch;
		float lim = swayLimit(sw);
		float decay = (float) Math.exp(-dt * 7.5F);
		swayX = Math.max(-lim, Math.min(lim, swayX * decay));
		swayY = Math.max(-lim * 0.7F, Math.min(lim * 0.7F, swayY * decay));
	}

	/** the sheet never moves further than this from its place, so it can never get left behind */
	private static float swayLimit(int sw) {
		return Math.max(6F, sw * 0.045F);
	}

	public static void draw(DrawContext c, Cfg cfg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || !MusicHub.hasTrack || (cfg.onlyPlaying && !MusicHub.playing)) {
			return;
		}
		List<Lyrics.Line> lines = Lyrics.lines;
		if (lines.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		long pos = MusicHub.progressMs + (MusicHub.playing ? now - MusicHub.fetchedAt : 0) + Math.round(cfg.offsetMs);
		updateSway(mc, cfg, now, mc.getWindow().getScaledWidth());
		int idx = Lyrics.indexAt(lines, pos);
		if (idx < 0) {
			return;
		}
		Lyrics.Line line = lines.get(idx);
		// the line is over a bit after its last word (instrumental gap): let it fade instead of staying for ages
		boolean over = !line.blank() && pos > line.chunks().get(line.chunks().size() - 1).end() + 1800;
		if (line.blank() || over) {
			if (cur.line != -2) {
				leave(now);
			}
			if (cfg.world) {
				drawGhost(c, mc, cfg, lines, pos, now);
			}
			return;
		}

		// new line (or new lyrics): the old one starts fading, the new one gets a place in the sky
		if (cur.line != idx || cur.version != Lyrics.version || cur.mode != cfg.position) {
			if (cur.line != -2 && cur.version == Lyrics.version) {
				leave(now);
			}
			cur = place(idx, cfg);
		}

		if (cfg.bar) {
			drawBar(c, mc, cfg, line, pos);
		}
		if (cfg.world) {
			drawGhost(c, mc, cfg, lines, pos, now);
			drawWorld(c, mc, cfg, line, cur, pos, now, 1F);
		}
	}

	private static void leave(long now) {
		cur.leftAt = now;
		old = cur;
		cur = new Anchor();
	}

	/** Gives the line its side of the screen and its own pose. Same line = same pose (seeded), so it never jumps. */
	private static Anchor place(int idx, Cfg cfg) {
		Anchor a = new Anchor();
		a.line = idx;
		a.version = Lyrics.version;
		a.mode = cfg.position;
		java.util.Random r = new java.util.Random(idx * 7919L + a.version * 104729L + 17L);
		switch (cfg.position) {
			case 1: a.side = -1; break;
			case 2: a.side = 1; break;
			case 3: a.side = idx % 2 == 0 ? -1 : 1; break;
			case 4: a.side = r.nextInt(3) - 1; break;
			default: a.side = 0;
		}
		float mag = 12F + r.nextFloat() * 14F; // 12..26 degrees: clearly turned, not just a little
		// on the left the right edge goes away (the sheet faces the middle), on the right the opposite
		a.yaw = a.side != 0 ? -a.side * mag : (r.nextBoolean() ? mag : -mag);
		a.pitch = 8F + r.nextFloat() * 16F;
		a.roll = (r.nextFloat() * 2F - 1F) * 8F;
		a.dy = a.side == 0 ? 0F : (r.nextFloat() * 2F - 1F) * 0.07F;
		return a;
	}

	private static void drawGhost(DrawContext c, MinecraftClient mc, Cfg cfg, List<Lyrics.Line> lines, long pos, long now) {
		if (old.line < 0 || old.leftAt == 0 || old.version != Lyrics.version) {
			return;
		}
		float k = 1F - (now - old.leftAt) / 700F;
		if (k <= 0F || old.line >= lines.size()) {
			old.line = -2;
			return;
		}
		drawWorld(c, mc, cfg, lines.get(old.line), old, pos, now, k * k);
	}

	// ------------------------------------------------------------------ floating text in the world
	private static void drawWorld(DrawContext c, MinecraftClient mc, Cfg cfg, Lyrics.Line line, Anchor a, long pos, long now, float fade) {
		if (a.line < 0 || line.blank()) {
			return;
		}
		var tr = mc.textRenderer;
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		float spread = Math.max(0.05F, Math.min(0.40F, cfg.spread));
		float cx = sw / 2F + a.side * sw * spread;
		float cy = sh * Math.max(0.14F, 0.5F - cfg.height / 80F) + a.dy * sh;
		List<Lyrics.Chunk> ch = line.chunks();
		int n = ch.size();
		int current = currentChunk(ch, pos);

		// widths of every chunk and where the rows break (the widest row decides the size)
		float space = tr.getWidth(styled(" ", cfg));
		float[] cw = new float[n];
		float tot = 0F;
		for (int i = 0; i < n; i++) {
			cw[i] = tr.getWidth(styled(ch.get(i).text(), cfg));
			tot += cw[i] + space;
		}
		tot -= space;
		int wantRows = n <= 2 ? 1 : (n <= 6 ? 2 : 3);
		int[] rowOf = new int[n];
		float cum = 0F;
		int rows = 1;
		for (int i = 0; i < n; i++) {
			int r = Math.min(wantRows - 1, (int) ((cum + cw[i] / 2F) / (tot / wantRows + 0.001F)));
			rowOf[i] = Math.max(i == 0 ? 0 : rowOf[i - 1], r);
			rows = Math.max(rows, rowOf[i] + 1);
			cum += cw[i] + space;
		}
		float base = (cfg.script ? 2.0F : 1.4F) * cfg.size;
		float avail = a.side == 0 ? sw * 0.82F : 2F * Math.min(cx, sw - cx) - sw * 0.04F;
		float gapU = space * (cfg.script ? 1.6F : 1F);
		float[] rowW = new float[rows];
		for (int i = 0; i < n; i++) {
			rowW[rowOf[i]] += cw[i] + gapU;
		}
		float widest = 1F;
		for (int r = 0; r < rows; r++) {
			widest = Math.max(widest, rowW[r] - gapU);
		}
		if (widest * base > avail) {
			base *= avail / (widest * base); // long line: shrink it, never run off the screen
		}
		float[] x = new float[n];
		Arrays.fill(rowW, 0F);
		for (int i = 0; i < n; i++) {
			int r = rowOf[i];
			x[i] = rowW[r];
			rowW[r] += (cw[i] + gapU) * base;
		}
		float rowH = 11F * base * 1.35F;
		double time = now / 1000.0;

		// the sheet: this line's own yaw / pitch / roll, breathing slowly, pushed a little by camera turns
		float depth = Math.max(0F, Math.min(1F, cfg.depth));
		float lim = swayLimit(sw);
		float sx = swayX / lim, sy = swayY / (lim * 0.7F);
		float yaw = (float) Math.toRadians(depth * (a.yaw + 3F * Math.sin(time * 0.7)) + sx * 12F * depth);
		float pitch = (float) Math.toRadians(depth * (a.pitch + 2F * Math.sin(time * 0.55 + 1.0)) + sy * 8F * depth);
		float roll = (float) Math.toRadians(a.roll * depth);
		float focal = Math.max(140F, Math.min(sw, sh * 1.7F) * 0.5F); // close camera = strong perspective
		int layers = Math.round(5F * depth);
		float[] pr = new float[6];

		for (int i = 0; i < n; i++) {
			Lyrics.Chunk chunk = ch.get(i);
			long since = pos - chunk.start();
			if (since < 0) {
				continue; // not sung yet: appears when it is sung
			}
			int r = rowOf[i];
			boolean sung = i == current;
			float rw = rowW[r] - gapU * base;
			float rowShift = rows > 1 ? (r % 2 == 1 ? 10F : -6F) * base : 0F;
			float u0 = -rw / 2F + x[i] + rowShift;
			float vRow = (r - (rows - 1) / 2F) * rowH;
			String text = chunk.text();
			int base0 = wordColour(cfg, i, n, time);
			int col = Gfx.blend(base0, 0xFFFFFFFF, sung ? (cfg.colourMode == 0 ? 0.4F : 0.25F) : 0F);
			for (int g = 0; g < text.length(); g++) {
				char chr = text.charAt(g);
				if (chr == ' ') {
					continue;
				}
				// letters pop in one after the other inside the word
				float inK = Math.max(0F, Math.min(1F, (since - g * 22F) / 260F));
				if (inK <= 0F) {
					continue;
				}
				inK = 1F - (1F - inK) * (1F - inK) * (1F - inK);
				Text t = styled(String.valueOf(chr), cfg);
				float raw = tr.getWidth(t);
				float off = tr.getWidth(styled(text.substring(0, g), cfg)) * base;
				float gw = raw * base;
				float cu = u0 + off + gw / 2F; // where this LETTER is on the sheet: every letter is projected on its own
				float ur = rw <= 1F ? 0F : (cu + rw / 2F - rowShift) / (rw / 2F) - 1F; // -1..1 along the row
				float u = cu + (a.side == 0 ? 0F : -a.side * 26F * base) * (1F - inK);
				float v = vRow + ur * ur * 4F * base + (1F - inK) * 8F * base + (float) Math.sin(time * 1.6 + (i * 3 + g) * 0.5) * 1.2F * base;
				Persp.project(u, v, yaw, pitch, roll, focal, pr);
				float near = Persp.scale(pr);
				float farFade = 1F - Math.min(0.35F, Math.max(0F, 1F - near) * 1.2F * depth);
				float alpha = inK * fade * (sung ? 1F : 0.82F) * cfg.opacity * farFade;
				float sc = sung ? 1.08F : 1F;
				float tilt = (float) Math.toRadians(ur * 2F);

				var m = c.getMatrices();
				m.pushMatrix();
				m.translate(cx + swayX + pr[0], cy + swayY + pr[1]);
				m.mul(new Matrix3x2f(pr[2], pr[3], pr[4], pr[5], 0F, 0F)); // this letter's own perspective stretch
				m.rotate(tilt);
				m.scale(base * sc, base * sc);
				m.translate(-raw / 2F, -4F);
				// thickness: dark copies pushed toward the vanishing point (the middle of the screen), so it looks like a solid slab
				if (layers > 0) {
					float ox = Math.max(-1F, Math.min(1F, (sw / 2F - cx - pr[0]) / (sw * 0.25F))) * 0.55F;
					float oy = Math.max(-1F, Math.min(1F, (sh / 2F - cy - pr[1]) / (sh * 0.25F))) * 0.4F + 0.55F;
					int dark = Gfx.blend(base0, 0xFF000000, 0.72F);
					for (int l = layers; l >= 1; l--) {
						int a8 = alphaOf(dark, alpha * (0.7F - 0.07F * l));
						if (((a8 >>> 24) & 255) < 6) {
							continue;
						}
						m.pushMatrix();
						m.translate(ox * l, oy * l);
						c.drawText(tr, t, 0, 0, a8, false);
						m.popMatrix();
					}
				}
				if (sung) {
					int gl = alphaOf(cfg.colourMode == 0 ? cfg.highlight : base0, (cfg.script ? 0.35F : 0.22F) * cfg.glow * alpha);
					c.drawText(tr, t, -1, 1, gl, false);
					c.drawText(tr, t, 1, -1, gl, false);
				}
				int a8 = alphaOf(col, alpha);
				if (((a8 >>> 24) & 255) >= 8) {
					c.drawText(tr, t, 0, 0, a8, !cfg.script);
				}
				m.popMatrix();
			}
		}
	}

	// ------------------------------------------------------------------ top capsule
	/** Flat dark pill, small round icon on the left, the line, three dots on the right. No glow, no outline. */
	private static void drawBar(DrawContext c, MinecraftClient mc, Cfg cfg, Lyrics.Line line, long pos) {
		List<Lyrics.Chunk> ch = line.chunks();
		int current = currentChunk(ch, pos);
		int sw = mc.getWindow().getScaledWidth();
		int full = mc.textRenderer.getWidth(line.text());
		float maxW = sw * 0.62F;
		float s = Math.min(cfg.barSize, maxW / Math.max(1, full + 52));
		float op = cfg.opacity;
		int padL = 22, padR = 24;
		float bw = (full + padL + padR) * s;
		float bh = 16F * Math.max(0.75F, s);
		float bx = (sw - bw) / 2F, by = 5F;

		Gfx.rrectFree(c, bx, by, bw, bh, bh / 2F, alphaOf(0xD90B0B10, op));
		// small round icon
		Gfx.circle(c, bx + 9.5F * s, by + bh / 2F, 4.6F * s, alphaOf(0xFF4A4A55, op));
		Gfx.circle(c, bx + 9.5F * s, by + bh / 2F, 1.6F * s, alphaOf(0xFF0B0B10, op));

		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(bx + padL * s, by + bh / 2F - 4F * s);
		m.scale(s, s);
		float xx = 0;
		for (int i = 0; i < ch.size(); i++) {
			String word = ch.get(i).text();
			int wd = mc.textRenderer.getWidth(word);
			if (i == current) {
				int chip = cfg.colourMode == 0 ? cfg.highlight : wordColour(cfg, i, ch.size(), System.currentTimeMillis() / 1000.0);
				Gfx.rrectFree(c, xx - 1.5F, -1.5F, wd + 3, 11, 3F, alphaOf(chip, op));
				c.drawText(mc.textRenderer, word, Math.round(xx), 0, alphaOf(0xFFFFFFFF, op), false);
			} else {
				c.drawText(mc.textRenderer, word, Math.round(xx), 0, alphaOf(i < current ? 0xFFFFFFFF : 0x80FFFFFF, op), false);
			}
			xx += mc.textRenderer.getWidth(word + " ");
		}
		m.popMatrix();

		// three small dots on the right
		for (int i = 0; i < 3; i++) {
			Gfx.circle(c, bx + bw - 14F * s + i * 3.4F * s, by + bh / 2F, 0.9F * s, alphaOf(0xB3FFFFFF, op));
		}
	}

	// ------------------------------------------------------------------ helpers
	static int currentChunk(List<Lyrics.Chunk> ch, long pos) {
		int r = -1;
		for (int i = 0; i < ch.size(); i++) {
			if (ch.get(i).start() <= pos) {
				r = i;
			}
		}
		return r;
	}

	/** Colour of the i-th word of a line, according to the colour mode. */
	/** Colour of word i out of n (colourMode 3 = gradient along the line). */
	static int wordColour(Cfg cfg, int i, int n, double time) {
		if (cfg.colourMode == 3) {
			return Gradients.at(cfg.grad, n <= 1 ? 0.5F : i / (float) (n - 1), cfg.gradFlow, time);
		}
		return wordColour(cfg, i, time);
	}

	static int wordColour(Cfg cfg, int i, double time) {
		switch (cfg.colourMode) {
			case 1:
				return cfg.palette[Math.floorMod(i, Math.max(2, Math.min(cfg.palette.length, cfg.paletteSize)))] | 0xFF000000;
			case 2:
				return hsv((float) ((i * 0.13 + time * 0.12) % 1.0), 0.55F, 1F);
			default:
				return cfg.text;
		}
	}

	private static int hsv(float h, float sat, float v) {
		float f = (h - (float) Math.floor(h)) * 6F;
		int k = (int) f;
		float fr = f - k, p = v * (1F - sat), q = v * (1F - sat * fr), t = v * (1F - sat * (1F - fr));
		float r, g, b;
		switch (k) {
			case 0: r = v; g = t; b = p; break;
			case 1: r = q; g = v; b = p; break;
			case 2: r = p; g = v; b = t; break;
			case 3: r = p; g = q; b = v; break;
			case 4: r = t; g = p; b = v; break;
			default: r = v; g = p; b = q; break;
		}
		return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	static Text styled(String s, Cfg cfg) {
		if (cfg.upper) {
			s = s.toUpperCase(java.util.Locale.ROOT);
		}
		return Text.literal(s).setStyle(cfg.fontOverride != null ? Style.EMPTY.withFont(cfg.fontOverride) : cfg.script ? Style.EMPTY.withFont(FONT) : Style.EMPTY.withItalic(true));
	}

	static int alphaOf(int color, float a) {
		int ca = (color >>> 24) & 255;
		return Math.round(ca * Math.max(0F, Math.min(1F, a))) << 24 | (color & 0xFFFFFF);
	}
}
