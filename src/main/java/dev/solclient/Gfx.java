package dev.solclient;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/** Small immediate-mode drawing helpers used by the GUI and the HUD. */
public final class Gfx {

	private static final Map<String, Text> CACHE = new HashMap<>();

	private Gfx() {
	}

	public static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	public static Identifier id(String path) {
		return Identifier.of(SolClient.ID, path);
	}

	// ------------------------------------------------------------------ text
	// ------------------------------------------------------------------ fonts
	/** Settings > Font: the menus / HUD text font. Index into these arrays. */
	public static final String[] FONT_NAMES = {"Poppins", "Inter", "Montserrat", "Nunito", "Rubik", "Quicksand", "Comfortaa", "Fredoka",
			"Outfit", "Lexend", "Baloo", "JetBrains Mono", "Press Start", "Silkscreen", "Minecraft", "Custom"};
	public static final String[] FONT_IDS = {"sol_client:ui", "sol_client:ui_inter", "sol_client:ui_montserrat", "sol_client:ui_nunito",
			"sol_client:ui_rubik", "sol_client:ui_quicksand", "sol_client:ui_comfortaa", "sol_client:ui_fredoka", "sol_client:ui_outfit",
			"sol_client:ui_lexend", "sol_client:ui_baloo", "sol_client:ui_jetbrains", "sol_client:ui_pressstart", "sol_client:ui_silkscreen",
			"minecraft:default", CustomFont.FONT_ID};
	/** Index of the font you import yourself (always the last one, so old saved choices keep working). */
	public static final int CUSTOM_FONT = FONT_IDS.length - 1;
	private static String fontId = FONT_IDS[0];

	public static String fontId() {
		return fontId;
	}

	/** Switches the UI font (clears the text and width caches). */
	public static void setFont(int idx) {
		String id = FONT_IDS[Math.floorMod(idx, FONT_IDS.length)];
		if (id.equals(CustomFont.FONT_ID) && !CustomFont.installed()) {
			id = FONT_IDS[0]; // no imported font yet: Poppins, never empty boxes
		}
		if (!id.equals(fontId)) {
			fontId = id;
			CACHE.clear();
			RAW.clear();
			WIDTHS.clear();
			dev.solclient.chat.Emoji.clearCaches();
		}
	}

	private static Text styled(String s, String font) {
		JsonObject o = new JsonObject();
		o.addProperty("text", s);
		o.addProperty("font", font);
		return TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Text.literal(s));
	}

	/** Text in a given font (used by the font picker previews). Not translated. */
	public static Text inFont(String s, int fontIdx) {
		return styled(s, FONT_IDS[Math.floorMod(fontIdx, FONT_IDS.length)]);
	}

	/** Text using the chosen UI font (Poppins by default), translated to the client language. */
	public static Text ui(String s) {
		s = Lang.t(s); // client language (Settings > Language)
		Text t = CACHE.get(s);
		if (t == null) {
			t = styled(s, fontId);
			if (CACHE.size() > 4000) {
				CACHE.clear();
			}
			CACHE.put(s, t);
		}
		return t;
	}

	private static final Map<String, Text> RAW = new HashMap<>();

	/** Like {@link #ui} but never translated: for things people typed (chat messages, names, text boxes). */
	public static Text raw(String s) {
		Text t = RAW.get(s);
		if (t == null) {
			t = styled(s, fontId);
			if (RAW.size() > 3000) {
				RAW.clear();
			}
			RAW.put(s, t);
		}
		return t;
	}

	public static int twRaw(String s) {
		return mc().textRenderer.getWidth(raw(s));
	}

	public static void textRaw(DrawContext c, String s, int x, int y, int color) {
		c.drawText(mc().textRenderer, raw(s), x, y, color, false);
	}

	/** Text widths are measured a lot every frame in the menus: remember them (refreshed every 30 s, e.g. after a font change). */
	private static final java.util.HashMap<String, Integer> WIDTHS = new java.util.HashMap<>();
	private static long widthsAt;

	public static int tw(String s) {
		long now = System.currentTimeMillis();
		if (langVersion != Lang.version) {
			langVersion = Lang.version;
			WIDTHS.clear();
		}
		if (now - widthsAt > 30_000L || WIDTHS.size() > 6000) {
			WIDTHS.clear();
			widthsAt = now;
		}
		String key = Lang.code() + "\u0001" + s; // widths differ per language (the cache is cleared when the font changes)
		Integer w = WIDTHS.get(key);
		if (w == null) {
			w = mc().textRenderer.getWidth(ui(s));
			WIDTHS.put(key, w);
		}
		return w;
	}

	public static void text(DrawContext c, String s, int x, int y, int color) {
		c.drawText(mc().textRenderer, ui(s), x, y, color, false);
	}

	public static void textC(DrawContext c, String s, int cx, int y, int color) {
		text(c, s, cx - tw(s) / 2, y, color);
	}

	/**
	 * Centred UI text that always stays inside its box (buttons, toggles, strips): when the translation is longer
	 * than maxW (e.g. ON -> LIGADO) it gets a bit smaller, and if that is still not enough it is cut with "..".
	 */
	public static void textFit(DrawContext c, String s, int cx, int maxW, int y, int color) {
		int w = tw(s);
		if (maxW <= 0 || w <= maxW) {
			text(c, s, cx - w / 2, y, color);
			return;
		}
		float sc = Math.max(0.72F, maxW / (float) w);
		Text t = ui(s);
		if (w * sc > maxW) {
			String full = Lang.t(s);
			String cut = full;
			while (cut.length() > 1 && mc().textRenderer.getWidth(raw(cut + "..")) * sc > maxW) {
				cut = cut.substring(0, cut.length() - 1);
			}
			t = raw(cut.strip() + "..");
			w = mc().textRenderer.getWidth(t);
		}
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(cx, y + 4.5F);
		m.scale(sc, sc);
		c.drawText(mc().textRenderer, t, -w / 2, -4, color, false);
		m.popMatrix();
	}

	/** Left-aligned UI text cut with ".." so it never goes past maxW. */
	public static void textClip(DrawContext c, String s, int x, int y, int maxW, int color) {
		if (tw(s) <= maxW) {
			text(c, s, x, y, color);
			return;
		}
		String cut = Lang.t(s);
		while (cut.length() > 1 && twRaw(cut + "..") > maxW) {
			cut = cut.substring(0, cut.length() - 1);
		}
		textRaw(c, cut.strip() + "..", x, y, color);
	}

	/** Width cache is per language; new automatic translations change widths. */
	public static void clearWidths() {
		WIDTHS.clear();
		CACHE.clear();
	}

	private static int langVersion = -1;

	/** Vertically centres a line of UI text inside a box. */
	public static void textMid(DrawContext c, String s, int x, int y, int h, int color) {
		text(c, s, x, y + (h - 9) / 2, color);
	}

	// ---------------------------------------------------------------- shapes
	/** GUI scale factor: how many real screen pixels one GUI pixel is. */
	public static float scaleOverride = 0F;

	/** Multiplies the alpha of rounded shapes (menu opacity setting). Text and icons are not affected. */
	public static float shapeAlpha = 1F;

	private static int fade(int color) {
		if (shapeAlpha >= 0.999F) {
			return color;
		}
		int a = Math.round(((color >>> 24) & 255) * shapeAlpha);
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static float scale() {
		if (scaleOverride > 0F) {
			return scaleOverride;
		}
		return Math.max(1F, (float) mc().getWindow().getScaleFactor());
	}

	public static void fill(DrawContext c, int x1, int y1, int x2, int y2, int color) {
		c.fill(x1, y1, x2, y2, color);
	}

	/** 1 real-pixel hairline outline (crisp at any GUI scale). */
	public static void outline(DrawContext c, int x, int y, int w, int h, int color) {
		c.fill(x, y, x + w, y + 1, color);
		c.fill(x, y + h - 1, x + w, y + h, color);
		c.fill(x, y + 1, x + 1, y + h - 1, color);
		c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	public static void vline(DrawContext c, int x, int y1, int y2, int color) {
		c.fill(x, y1, x + 1, y2, color);
	}

	/** Anti-aliased rounded rectangle, drawn in real screen pixels so corners are smooth. */
	/** A grey placeholder with a soft light running across it, shown while something is loading. */
	public static void skeleton(DrawContext c, int x, int y, int w, int h, int r) {
		rrect(c, x, y, w, h, r, 0xFF1A1C25);
		int band = Math.max(24, w / 3);
		float p = (System.currentTimeMillis() % 1500L) / 1500F;
		int bx = x - band + Math.round((w + band * 2) * p);
		for (int i = 0; i < band; i += 2) {
			int sx = bx + i;
			if (sx < x + r / 2 || sx + 2 > x + w - r / 2) {
				continue;
			}
			float k = 1F - Math.abs(i - band / 2F) / (band / 2F);
			c.fill(sx, y + r / 2, sx + 2, y + h - r / 2, ((int) (0x2A * k) << 24) | 0xFFFFFF);
		}
	}

	/** A loading grid of card-shaped placeholders (with two text lines each) filling x0..x1, y0..y1. */
	public static void skeletonCards(DrawContext c, int x0, int y0, int x1, int y1, int gap, int minW) {
		int inner = x1 - x0;
		int cols = Math.max(1, (inner + gap) / (minW + gap));
		int cw = (inner - (cols - 1) * gap) / cols;
		int ch = cw + 6;
		for (int row = 0; y0 + row * (ch + gap) < y1; row++) {
			for (int col = 0; col < cols; col++) {
				int cx = x0 + col * (cw + gap), cy = y0 + row * (ch + gap);
				skeleton(c, cx, cy, cw, ch, 6);
				skeleton(c, cx + 8, cy + ch - 26, cw * 3 / 5, 7, 3);
				skeleton(c, cx + 8, cy + ch - 14, cw * 2 / 5, 6, 3);
			}
		}
	}

	public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
		rrectF(c, x, y, w, h, r, color);
	}

	/** Largest corner radius for panels / buttons (Settings > Menus > Corner Radius). Circles are not affected. */
	public static float corner(float r) {
		float max = dev.solclient.SolClient.settings == null ? 3F : dev.solclient.SolClient.settings.cornerRadius.f();
		return Math.min(r, max);
	}

	public static void rrectF(DrawContext c, float x, float y, float w, float h, float r, int color) {
		rrectRaw(c, x, y, w, h, corner(r), color);
	}

	/** Rounded rectangle with exactly this radius (not limited by the menu Corner Radius setting). For HUDs with their own roundness option. */
	public static void rrectFree(DrawContext c, float x, float y, float w, float h, float r, int color) {
		rrectRaw(c, x, y, w, h, Math.max(0F, Math.min(r, Math.min(w, h) / 2F)), color);
	}

	private static void rrectRaw(DrawContext c, float x, float y, float w, float h, float r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		color = fade(color);
		float s = scale();
		int px = Math.round(x * s), py = Math.round(y * s);
		int pw = Math.max(1, Math.round(w * s)), ph = Math.max(1, Math.round(h * s));
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(1F / s, 1F / s);
		roundPx(c, px, py, pw, ph, Math.round(r * s), color);
		c.getMatrices().popMatrix();
	}

	/** Rounded rect in real pixels (the matrix must already be scaled by 1/guiScale): one GUI element (see QuadBatch). */
	private static void roundPx(DrawContext c, int px, int py, int pw, int ph, int pr, int color) {
		pr = Math.min(pr, Math.min(pw, ph) / 2);
		if (pr <= 0) {
			c.fill(px, py, px + pw, py + ph, color);
			return;
		}
		QuadBatch b = new QuadBatch();
		roundInto(b, px, py, pw, ph, pr, color);
		b.submit(c);
	}

	/** Per radius: for each corner row, how far in the row is fully covered, and the coverage (0..1) of the pixels before that. */
	private static final float[][][] COVER = new float[257][][];

	private static float[][] cover(int pr) {
		float[][] t = pr < COVER.length ? COVER[pr] : null;
		if (t != null) {
			return t;
		}
		t = new float[pr][];
		for (int i = 0; i < pr; i++) {
			float dy = pr - i - 0.5F;
			int full = pr;
			float[] row = new float[pr];
			for (int x = 0; x < pr; x++) {
				float dx = pr - x - 0.5F;
				float cv = Math.max(0F, Math.min(1F, pr - (float) Math.sqrt(dx * dx + dy * dy) + 0.5F));
				row[x] = cv;
				if (cv >= 0.999F && full == pr) {
					full = x;
				}
			}
			float[] r = new float[full + 1];
			r[0] = full;
			System.arraycopy(row, 0, r, 1, full);
			t[i] = r;
		}
		if (pr < COVER.length) {
			COVER[pr] = t;
		}
		return t;
	}

	/** An anti-aliased rounded rectangle (real pixels) added to a batch: full rows as spans, edge pixels with their coverage. */
	static void roundInto(QuadBatch b, int px, int py, int pw, int ph, int pr, int color) {
		pr = Math.min(pr, Math.min(pw, ph) / 2);
		if (pr <= 0) {
			b.add(px, py, px + pw, py + ph, color);
			return;
		}
		float[][] cv = cover(pr);
		int a0 = (color >>> 24) & 255, rgb = color & 0xFFFFFF;
		for (int i = 0; i < pr; i++) {
			float[] r = cv[i];
			int full = (int) r[0];
			int ty = py + i, by = py + ph - 1 - i;
			b.add(px + full, ty, px + pw - full, ty + 1, color);
			b.add(px + full, by, px + pw - full, by + 1, color);
			for (int x = 0; x < full; x++) {
				int a = Math.round(a0 * r[x + 1]);
				if (a <= 2) {
					continue;
				}
				int col = (a << 24) | rgb;
				b.add(px + x, ty, px + x + 1, ty + 1, col);
				b.add(px + pw - x - 1, ty, px + pw - x, ty + 1, col);
				b.add(px + x, by, px + x + 1, by + 1, col);
				b.add(px + pw - x - 1, by, px + pw - x, by + 1, col);
			}
		}
		if (ph - 2 * pr > 0) {
			b.add(px, py + pr, px + pw, py + ph - pr, color);
		}
	}

	/** Rounded rectangle with a hairline (1 real pixel) border. Inner colour should be opaque. */
	public static void rrectBorder(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
		if (w <= 0 || h <= 0) {
			return;
		}
		float s = scale();
		int px = Math.round(x * s), py = Math.round(y * s);
		int pw = Math.max(2, Math.round(w * s)), ph = Math.max(2, Math.round(h * s));
		int pr = Math.round(corner(r) * s);
		int t = Math.max(2, Math.round(s * 1.0F)); // a real stroke (1 gui unit), not a hairline
		fill = fade(fill);
		border = fade(border);
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(1F / s, 1F / s);
		QuadBatch qb = new QuadBatch(); // border + fill = one GUI element
		if (((fill >>> 24) & 255) >= 250) {
			roundInto(qb, px, py, pw, ph, Math.min(pr, Math.min(pw, ph) / 2), border);
			if (pw - 2 * t > 0 && ph - 2 * t > 0) {
				roundInto(qb, px + t, py + t, pw - 2 * t, ph - 2 * t, Math.max(0, pr - t), fill);
			}
		} else {
			// see-through panel: fill, then only the border ring (no darker border showing through the fill)
			if (pw - 2 * t > 0 && ph - 2 * t > 0) {
				roundInto(qb, px + t, py + t, pw - 2 * t, ph - 2 * t, Math.max(0, pr - t), fill);
			}
			ringPx(qb, px, py, pw, ph, Math.min(pr, Math.min(pw, ph) / 2), border, t);
		}
		qb.submit(c);
		c.getMatrices().popMatrix();
	}

	/** Outline of a rounded rectangle, thickness t (real pixels). */
	private static void ringPx(QuadBatch c, int px, int py, int pw, int ph, int pr, int color, int t) {
		if (pr <= 0) {
			c.add(px, py, px + pw, py + t, color);
			c.add(px, py + ph - t, px + pw, py + ph, color);
			c.add(px, py + t, px + t, py + ph - t, color);
			c.add(px + pw - t, py + t, px + pw, py + ph - t, color);
			return;
		}
		c.add(px + pr, py, px + pw - pr, py + t, color);
		c.add(px + pr, py + ph - t, px + pw - pr, py + ph, color);
		c.add(px, py + pr, px + t, py + ph - pr, color);
		c.add(px + pw - t, py + pr, px + pw, py + ph - pr, color);
		double ri = Math.max(0, pr - t);
		for (int i = 0; i < pr; i++) {
			double dy = pr - i - 0.5;
			int kOut = (int) Math.floor(pr - Math.sqrt(Math.max(0, pr * (double) pr - dy * dy)));
			int kIn;
			if (i < t || Math.abs(dy) >= ri) {
				kIn = pr;
			} else {
				kIn = (int) Math.ceil(pr - Math.sqrt(ri * ri - dy * dy));
			}
			if (kIn <= kOut) {
				kIn = kOut + 1;
			}
			int ty = py + i, by = py + ph - 1 - i;
			c.add(px + kOut, ty, px + kIn, ty + 1, color);
			c.add(px + pw - kIn, ty, px + pw - kOut, ty + 1, color);
			c.add(px + kOut, by, px + kIn, by + 1, color);
			c.add(px + pw - kIn, by, px + pw - kOut, by + 1, color);
		}
	}

	public static void circle(DrawContext c, float cx, float cy, float r, int color) {
		rrectRaw(c, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	/** Small heart icon (size in GUI pixels, centred on cx, cy). */
	public static void heart(DrawContext c, float cx, float cy, float size, int color) {
		float s = scale();
		float u = size * s / 10F;
		float pcx = cx * s, pcy = cy * s;
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(1F / s, 1F / s);
		float rr = 2.9F * u;
		for (int side = -1; side <= 1; side += 2) {
			int bx = Math.round(pcx + side * 2.4F * u - rr), by = Math.round(pcy - 1.9F * u - rr);
			int d = Math.round(rr * 2);
			roundPx(c, bx, by, d, d, d / 2, color);
		}
		int rows = Math.max(1, Math.round(6.4F * u));
		int top = Math.round(pcy - 1.6F * u);
		for (int i = 0; i < rows; i++) {
			float f = i / (float) rows;
			int half = Math.round((1F - f) * 5.1F * u);
			if (half > 0) {
				c.fill(Math.round(pcx) - half, top + i, Math.round(pcx) + half, top + i + 1, color);
			}
		}
		c.getMatrices().popMatrix();
	}

	public static void gradientV(DrawContext c, int x, int y, int w, int h, int top, int bottom) {
		c.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	/** 256x1 white texture whose alpha goes 0 -> 255 left to right (and the mirrored one): fast horizontal gradients. */
	private static Identifier rampId, rampRevId;
	private static boolean rampFailed;

	private static boolean ramps() {
		if (rampId != null) {
			return true;
		}
		if (rampFailed) {
			return false;
		}
		try {
			for (int k = 0; k < 2; k++) {
				net.minecraft.client.texture.NativeImageBackedTexture t = new net.minecraft.client.texture.NativeImageBackedTexture("kubo_ramp" + k, 256, 1, false);
				net.minecraft.client.texture.NativeImage img = t.getImage();
				for (int i = 0; i < 256; i++) {
					int a = k == 0 ? i : 255 - i;
					img.setColorArgb(i, 0, a << 24 | 0xFFFFFF);
				}
				t.upload();
				Identifier id = id("ramp" + k);
				mc().getTextureManager().registerTexture(id, t);
				if (k == 0) {
					rampId = id;
				} else {
					rampRevId = id;
				}
			}
			return true;
		} catch (Throwable e) {
			rampFailed = true;
			rampId = null;
			return false;
		}
	}

	/**
	 * Horizontal gradient. When both colours have the same RGB (vignettes, edge glows) it is 1-2 draws with a stretched
	 * texture instead of one 1-pixel strip per column (that was hundreds of draws every frame on a big screen).
	 */
	public static void gradientH(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int a1 = (c1 >>> 24) & 255, a2 = (c2 >>> 24) & 255;
		if ((c1 & 0xFFFFFF) == (c2 & 0xFFFFFF) && ramps()) {
			int rgb = c1 & 0xFFFFFF;
			int lo = Math.min(a1, a2), diff = Math.abs(a2 - a1);
			if (lo > 0) {
				c.fill(x, y, x + w, y + h, lo << 24 | rgb);
			}
			if (diff > 0) {
				c.drawTexture(RenderPipelines.GUI_TEXTURED, a2 > a1 ? rampId : rampRevId, x, y, 0f, 0f, w, h, 256, 1, 256, 1, diff << 24 | rgb);
			}
			return;
		}
		// different colours: strips, but at most ~64 of them
		int step = Math.max(1, w / 64);
		for (int i = 0; i < w; i += step) {
			int iw = Math.min(step, w - i);
			c.fill(x + i, y, x + i + iw, y + h, blend(c1, c2, w <= 1 ? 0 : (i + iw / 2F) / (float) (w - 1)));
		}
	}

	public static int blend(int a, int b, float p) {
		p = Math.max(0, Math.min(1, p));
		int aa = Math.round(((a >>> 24) & 255) * (1 - p) + ((b >>> 24) & 255) * p);
		int ar = Math.round(((a >> 16) & 255) * (1 - p) + ((b >> 16) & 255) * p);
		int ag = Math.round(((a >> 8) & 255) * (1 - p) + ((b >> 8) & 255) * p);
		int ab = Math.round((a & 255) * (1 - p) + (b & 255) * p);
		return (aa << 24) | (ar << 16) | (ag << 8) | ab;
	}

	public static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0xFFFFFF);
	}

	// -------------------------------------------------------------- textures
	/** Draws a whole texture (texW x texH) scaled to w x h, tinted. */
	public static void tex(DrawContext c, Identifier id, int x, int y, int w, int h, int texW, int texH, int color) {
		c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH, color);
	}

	/** Draws the (u, v, rw, rh) part of a texW x texH texture at x, y scaled to w x h. */
	public static void texUv(DrawContext c, Identifier id, int x, int y, int w, int h, float u, float v, int rw, int rh, int texW, int texH, int color) {
		c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, rw, rh, texW, texH, color);
	}

	public static void sprite(DrawContext c, Identifier id, int x, int y, int w, int h) {
		c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, x, y, w, h);
	}

	// ---------------------------------------------------------------- buttons (Laby-like)
	public static int BTN_BODY = 0xFF323849, BTN_HOVER = 0xFF3A4155, BTN_PRESSED = 0xFF262A36, BTN_EDGE = 0xFF0A0B10;
	/** Text colour of a pressed / selected button (soft blue like the reference). */
	public static int BTN_TEXT_ON = 0xFFB4C6FF;

	/** Raised slate button with a dark outline; pressed = flat, darker, no bottom lip. Hover only lightens (no lines). */
	public static void button(DrawContext c, int x, int y, int w, int h, boolean hover, boolean pressed) {
		buttonBody(c, x, y, w, h, pressed ? BTN_PRESSED : hover ? BTN_HOVER : BTN_BODY, pressed);
	}

	public static void buttonBody(DrawContext c, int x, int y, int w, int h, int body, boolean pressed) {
		if (w < 4 || h < 4) {
			return;
		}
		// the launcher's button: dark outline, light top edge, a darker lip underneath that disappears when pressed
		rrect(c, x, y, w, h, 3, BTN_EDGE);
		if (pressed) {
			rrect(c, x + 1, y + 2, w - 2, h - 3, 2, body);
			c.fill(x + 3, y + 2, x + w - 3, y + 3, 0x22000000);
		} else {
			rrect(c, x + 1, y + 1, w - 2, h - 2, 2, blend(body, 0xFF000000, 0.4F));
			rrect(c, x + 1, y + 1, w - 2, h - 5, 2, body);
			c.fill(x + 3, y + 1, x + w - 3, y + 3, 0x30FFFFFF);
		}
	}

	/** Text colour on the light launcher button. */
	public static final int BTN_LIGHT_TEXT = 0xFF1B2766;

	/** The launcher's white-grey button: dark outline, white top edge, a grey lip underneath (gone when pressed). */
	public static void lightButton(DrawContext c, int x, int y, int w, int h, float hover, boolean pressed) {
		solidButton(c, x, y, w, h, blend(0xFFE6E7ED, 0xFFFFFFFF, 0.5F * hover), 0xFFA9ABBA, 0xC0FFFFFF, pressed);
	}

	/** The launcher's blue button (accent colour): same shape, light top edge, darker lip. */
	public static void blueButton(DrawContext c, int x, int y, int w, int h, int accent, float hover, boolean pressed) {
		int body = blend(accent | 0xFF000000, 0xFFFFFFFF, 0.14F * hover);
		solidButton(c, x, y, w, h, body, blend(accent | 0xFF000000, 0xFF000000, 0.38F), 0x55FFFFFF, pressed);
	}

	private static void solidButton(DrawContext c, int x, int y, int w, int h, int body, int lip, int shine, boolean pressed) {
		if (w < 6 || h < 6) {
			return;
		}
		rrect(c, x, y, w, h, 3, 0xFF07080F);
		int dy = pressed ? 2 : 0;
		rrect(c, x + 1, y + 1 + dy, w - 2, h - 2 - dy, 2, lip);
		rrect(c, x + 1, y + 1 + dy, w - 2, h - 2 - dy - (pressed ? 1 : 3), 2, body);
		c.fill(x + 3, y + 1 + dy, x + w - 3, y + 2 + dy, shine);
	}

	// ---------------------------------------------------------------- coin
	private static final int[] COIN_SPIN = {3, 4, 5, 6, 0, 1, 2, 3};

	/** Pixel coin (front). */
	public static void coin(DrawContext c, int x, int y, int size) {
		texUv(c, id("textures/gui/coin_spin.png"), x, y, size, size, 42, 0, 14, 14, 98, 14, 0xFFFFFFFF);
	}

	/** Pixel coin that spins once every 5 seconds. */
	public static void coinSpinning(DrawContext c, int x, int y, int size) {
		long t = System.currentTimeMillis() % 5000L;
		int step = (int) (t / 85L);
		int f = step < COIN_SPIN.length ? COIN_SPIN[step] : 3;
		texUv(c, id("textures/gui/coin_spin.png"), x, y, size, size, f * 14, 0, 14, 14, 98, 14, 0xFFFFFFFF);
	}
}
