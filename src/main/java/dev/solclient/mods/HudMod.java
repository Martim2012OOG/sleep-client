package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.client.gui.DrawContext;

/** A mod with a single movable HUD element. */
public abstract class HudMod extends Mod {

	public final Option.Num scale = num("Scale", 100, 50, 150, 1, "%");
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
	protected abstract void renderHud(DrawContext c, boolean edit);

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
		int sw = mc.getWindow().getScaledWidth();
		int x = Math.round(hx * sw);
		return Math.max(0, Math.min(Math.max(0, sw - boundsW()), x));
	}

	public int absY() {
		int sh = mc.getWindow().getScaledHeight();
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
		hx = x / (float) mc.getWindow().getScaledWidth();
		hy = y / (float) mc.getWindow().getScaledHeight();
	}

	public void draw(DrawContext c, boolean edit) {
		if (!edit && !visible()) {
			return;
		}
		float s = s();
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(absX(), absY());
		c.getMatrices().scale(s, s);
		renderHud(c, edit);
		c.getMatrices().popMatrix();
	}
}
