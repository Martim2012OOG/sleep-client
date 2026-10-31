package dev.solclient.gui;

import dev.solclient.Gfx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Real pictures behind the settings previews (no drawn terrain): a clear Minecraft sky with moving clouds, a cherry
 * grove with falling petals, an autumn garden and a night sky. Animated ones are sprite sheets that cross-fade
 * between frames, so they move smoothly.
 */
public final class Backdrops {

	public record Anim(String name, int fw, int fh, int cols, int frames, int frameMs, int texW, int texH) {
		Identifier id() {
			return Gfx.id("textures/gui/backdrop/" + name + ".png");
		}
	}

	public static final Anim CLEAR = new Anim("sky_clear", 400, 240, 6, 42, 400, 2400, 1680);
	public static final Anim SAKURA = new Anim("sky_sakura", 400, 225, 5, 20, 100, 2000, 900);
	public static final Anim AUTUMN = new Anim("sky_autumn", 640, 360, 1, 1, 0, 640, 360);
	public static final Anim NIGHT = new Anim("sky_night", 640, 360, 1, 1, 0, 640, 360);

	private Backdrops() {
	}

	/** Draws the picture covering x, y, w, h (cropped in the middle, like a "cover" background). */
	public static void draw(DrawContext c, Anim a, int x, int y, int w, int h) {
		draw(c, a, x, y, w, h, 0xFF);
	}

	public static void draw(DrawContext c, Anim a, int x, int y, int w, int h, int alpha) {
		if (w <= 0 || h <= 0) {
			return;
		}
		// the part of one frame that has the box's shape
		float boxK = w / (float) h, frameK = a.fw / (float) a.fh;
		int rw = a.fw, rh = a.fh;
		if (boxK > frameK) {
			rh = Math.max(1, Math.round(a.fw / boxK));
		} else {
			rw = Math.max(1, Math.round(a.fh * boxK));
		}
		int ox = (a.fw - rw) / 2, oy = (a.fh - rh) / 2;
		int col = (alpha & 255) << 24 | 0xFFFFFF;
		if (a.frames <= 1) {
			Gfx.texUv(c, a.id(), x, y, w, h, ox, oy, rw, rh, a.texW, a.texH, col);
			return;
		}
		long t = System.currentTimeMillis();
		long total = (long) a.frames * a.frameMs;
		float pos = (t % total) / (float) a.frameMs;
		int f0 = (int) pos, f1 = (f0 + 1) % a.frames;
		float mix = pos - f0;
		frame(c, a, f0, x, y, w, h, ox, oy, rw, rh, col);
		int a1 = Math.round((alpha & 255) * mix);
		if (a1 > 2) {
			frame(c, a, f1, x, y, w, h, ox, oy, rw, rh, a1 << 24 | 0xFFFFFF);
		}
	}

	private static void frame(DrawContext c, Anim a, int f, int x, int y, int w, int h, int ox, int oy, int rw, int rh, int col) {
		int fx = (f % a.cols) * a.fw, fy = (f / a.cols) * a.fh;
		Gfx.texUv(c, a.id(), x, y, w, h, fx + ox, fy + oy, rw, rh, a.texW, a.texH, col);
	}
}
