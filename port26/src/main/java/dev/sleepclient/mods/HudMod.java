package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A mod with a single movable HUD element. */
public abstract class HudMod extends Mod {

	public final Option.Num scale = num("Scale", 100, 20, 400, 1, "%");
	// a panel behind any HUD (the one-line HUDs have their own, fuller box options)
	private final Option.Bool panel = bool("Panel Behind", false).showIf(() -> !(this instanceof SimpleHudMod));
	private final Option.Col panelColour = colour("Panel Colour", 0x64000000).showIf(() -> panel.on() && !(this instanceof SimpleHudMod));
	private final Option.Num panelRadius = num("Panel Corner Radius", 4, 0, 16, 0.5F, "").showIf(() -> panel.on() && !(this instanceof SimpleHudMod));
	private final Option.Num panelMargin = num("Panel Margin", 3, 0, 16, 0.5F, "").showIf(() -> panel.on() && !(this instanceof SimpleHudMod));
	private final Option.Bool panelBorder = bool("Panel Border", false).showIf(() -> panel.on() && !(this instanceof SimpleHudMod));
	private final Option.Col panelBorderColour = colour("Panel Border Colour", 0xFF4F86E8).showIf(() -> panel.on() && panelBorder.on() && !(this instanceof SimpleHudMod));
	/** Position as a fraction of the scaled window size. */
	public float hx, hy;
	/** Default position (Reset in the HUD editor). */
	public final float defX, defY;

	protected HudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, Category.HUD);
		this.hx = dx;
		this.hy = dy;
		this.defX = dx;
		this.defY = dy;
	}

	public void resetPosition() {
		hx = defX;
		hy = defY;
		scale.set(100F);
	}

	public abstract int width();

	public abstract int height();

	/** Draws the element with its origin at (0, 0). */
	protected abstract void renderHud(GuiGraphicsExtractor c, boolean edit);

	public boolean visible() {
		return true;
	}

	/** Vertical offset (unscaled) of the bounds relative to the position. */
	public int dy() {
		return 0;
	}

	public float s() {
		return scale.get() / 100F;
	}

	/** Position on screen, kept inside the window (so nothing gets lost off-screen after a resize). */
	public int absX() {
		int sw = mc.getWindow().getGuiScaledWidth();
		int x = Math.round(hx * sw);
		return Math.max(0, Math.min(Math.max(0, sw - boundsW()), x));
	}

	public int absY() {
		int sh = mc.getWindow().getGuiScaledHeight();
		int off = Math.round(dy() * s());
		int y = Math.round(hy * sh);
		return Math.max(-off, Math.min(Math.max(-off, sh - boundsH() - off), y));
	}

	public int boundsX() {
		return absX();
	}

	public int boundsY() {
		return absY() + Math.round(dy() * s());
	}

	public int boundsW() {
		return Math.round(width() * s());
	}

	public int boundsH() {
		return Math.round(height() * s());
	}

	public boolean contains(double mx, double my) {
		return mx >= boundsX() && mx <= boundsX() + boundsW() && my >= boundsY() && my <= boundsY() + boundsH();
	}

	/** Moves the element so its visible box starts at (x, y). */
	public void setBounds(int x, int y) {
		setAbs(x, y - Math.round(dy() * s()));
	}

	public void setAbs(int x, int y) {
		hx = x / (float) mc.getWindow().getGuiScaledWidth();
		hy = y / (float) mc.getWindow().getGuiScaledHeight();
	}

	public void draw(GuiGraphicsExtractor c, boolean edit) {
		if (!edit && !visible()) {
			return;
		}
		float s = s();
		c.pose().pushMatrix();
		c.pose().translate(absX(), absY());
		c.pose().scale(s, s);
		try {
			if (panel.on() && !(this instanceof SimpleHudMod)) {
				float m = panelMargin.get(), r = panelRadius.get(), x0 = -m, y0 = dy() - m, w = width() + 2 * m, h = height() + 2 * m;
				if (panelBorder.on()) {
					dev.sleepclient.Gfx.rrectFree(c, x0 - 1, y0 - 1, w + 2, h + 2, r + 1, panelBorderColour.c());
				}
				dev.sleepclient.Gfx.rrectFree(c, x0, y0, w, h, r, panelColour.c());
			}
			renderHud(c, edit);
		} finally {
			c.pose().popMatrix(); // stays balanced even if the widget throws
		}
	}
}
