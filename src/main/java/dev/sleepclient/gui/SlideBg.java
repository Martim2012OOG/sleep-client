package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/**
 * The "selected" highlight shared by tabs, the side menu and view toggles: one soft neutral box behind the selected
 * item (no blue buttons). It used to glide with a spring; now it just sits on the selected item.
 * Call {@link #draw} with the selected item's box before drawing the items' text.
 */
public final class SlideBg {

	private static final class S {
		float x, y, w, h, vx, vy, vw, vh, alpha;
		long last;
		boolean init;
	}

	private static final Map<String, S> STATES = new HashMap<>();

	/** The highlight colour: a neutral light tint (no blue). */
	public static int colour() {
		return Gfx.blend(Theme.TILE_HOVER | 0xFF000000, 0xFFFFFFFF, 0.06F);
	}

	private SlideBg() {
	}

	private static boolean ok(float v, float target, float maxDist) {
		return !Float.isNaN(v) && !Float.isInfinite(v) && Math.abs(v - target) <= maxDist;
	}

	/** Biggest corner radius of a highlight: soft, but a lot squarer than the old pills. */
	private static final float MAX_RADIUS = 4F;

	/** Moves the group's highlight towards (x, y, w, h) and draws it. visible = false fades it out. */
	public static void draw(DrawContext c, String group, float x, float y, float w, float h, float radius, int col, boolean visible) {
		draw(c, group, x, y, w, h, radius, col, visible, 0F);
	}

	/**
	 * Same, for items inside a scrolling list. {@code scrollY} is how far the list is scrolled: the spring runs in
	 * list coordinates (y + scrollY), so scrolling moves the highlight together with its buttons instead of leaving it
	 * behind and sliding back (which looked like the highlight jumping out of place).
	 */
	public static void draw(DrawContext c, String group, float x, float y, float w, float h, float radius, int col, boolean visible, float scrollY) {
		// v91: no more gliding - the selected item simply has the highlight (like the side menu), nothing slides
		if (!visible) {
			return;
		}
		Gfx.rrectFree(c, x, y, Math.max(1F, w), Math.max(1F, h), Gfx.corner(Math.min(radius, MAX_RADIUS)), col);
	}
}
