package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/**
 * The slider used everywhere in Sleep (like the shadcn / Radix one): a rounded track, a filled range and a round thumb
 * with a little cube as the thumb. It slides smoothly: while you drag, the thumb follows the mouse exactly (no jumping from step to step),
 * and when the value changes any other way it glides there. A small bubble shows the value while dragging.
 */
public final class SmoothSlider {

	private static final class State {
		float vis = -1F, raw;
		long last;
		float grow;
	}

	private static final Map<Object, State> STATES = new HashMap<>();

	private SmoothSlider() {
	}

	/** Mouse position on the track while dragging (0..1, not snapped to the option's steps). */
	public static void dragTo(Object key, float raw01) {
		state(key).raw = Math.max(0F, Math.min(1F, raw01));
	}

	private static State state(Object key) {
		return STATES.computeIfAbsent(key, k -> new State());
	}

	/**
	 * Draws a slider. x, w: the track; cy: its vertical centre. value01: the real value. dragging: the mouse is holding
	 * this slider. hover: the mouse is over it. tip: text for the bubble (null = none).
	 */
	public static void draw(DrawContext c, Object key, float x, float cy, float w, float value01, boolean dragging, boolean hover, String tip) {
		State s = state(key);
		long now = System.nanoTime();
		float dt = s.last == 0 ? 0F : Math.min(0.1F, (now - s.last) / 1e9F);
		s.last = now;
		float v = Math.max(0F, Math.min(1F, value01));
		if (s.vis < 0F) {
			s.vis = v;
		}
		if (dragging) {
			s.vis = s.raw;
		} else {
			s.vis += (v - s.vis) * (1F - (float) Math.exp(-dt * 16F));
			if (Math.abs(v - s.vis) < 0.0005F) {
				s.vis = v;
			}
		}
		s.grow += ((dragging || hover ? 1F : 0F) - s.grow) * (1F - (float) Math.exp(-dt * 14F));

		int track = Gfx.blend(Theme.OFF_BG_HOVER, 0xFF000000, 0.1F);
		int primary = Theme.accent() | 0xFF000000;
		float th = 5F;
		Gfx.rrectFree(c, x, cy - th / 2F, w, th, th / 2F, track);
		float fx = w * s.vis;
		if (fx > 0.5F) {
			Gfx.rrectFree(c, x, cy - th / 2F, fx, th, th / 2F, primary);
		}
		float tx = x + fx;
		// focus ring while held / hovered, then the thumb: background inside a 2px primary border
		// the thumb is a little cube: a 2px primary frame around the background, with a square ring when held / hovered
		float half = 5.5F;
		if (s.grow > 0.02F) {
			float g = half + 3F * s.grow;
			Gfx.rrectFree(c, tx - g, cy - g, g * 2, g * 2, 2.5F, Gfx.withAlpha(primary, Math.round(0x40 * s.grow)));
		}
		Gfx.rrectFree(c, tx - half, cy - half, half * 2, half * 2, 1.5F, primary);
		Gfx.rrectFree(c, tx - half + 2F, cy - half + 2F, half * 2 - 4F, half * 2 - 4F, 0.5F, Theme.PANEL | 0xFF000000);
		if (dragging && tip != null && !tip.isEmpty()) {
			int tw = Gfx.tw(tip) + 10;
			float bx = tx - tw / 2F, by = cy - 23F;
			Gfx.rrectFree(c, bx, by, tw, 14, 4F, 0xF0101114);
			Gfx.rrectFree(c, tx - 2.5F, by + 13F, 5F, 2F, 1F, 0xF0101114);
			Gfx.text(c, tip, Math.round(bx + 5), Math.round(by + 3), 0xFFFFFFFF);
		}
	}
}
