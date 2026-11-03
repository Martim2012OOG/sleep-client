package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/** Decorations that float over the HUD (flying particles, cubes, glyphs). Positions are kept as fractions of the screen. */
public abstract class ScreenFxMod extends Mod {

	public static final List<ScreenFxMod> ALL = new ArrayList<>();
	private static final int MAX = 150;

	protected final Option.Col colour;
	protected final Option.Num amount = num("Amount", 40, 5, MAX, 1, "");
	protected final Option.Num speed = num("Speed", 100, 20, 300, 10, "%");
	protected final Option.Num size = num("Size", 100, 20, 500, 10, "%");
	protected final float[] vx = new float[MAX], vy = new float[MAX];
	private final float[] px = new float[MAX], py = new float[MAX], ph = new float[MAX];
	private boolean seeded;
	private long lastNs;
	private final java.util.Random rnd = new java.util.Random();

	protected ScreenFxMod(String name, String id, String description, int defaultColour) {
		super(name, id, description, Category.VISUAL);
		colour = colour("Colour", defaultColour);
		ALL.add(this);
	}

	/** Called from the HUD every frame. */
	public static void drawAll(DrawContext c) {
		for (ScreenFxMod m : ALL) {
			if (m.enabled) {
				m.draw(c);
			}
		}
	}

	/** Draws one of them: x / y in screen pixels, s = size in pixels, a = 0..1 fade, t = its own slow clock. */
	protected abstract void drawOne(DrawContext c, float x, float y, float s, float a, float t, int argb);

	/** Initial speed (fractions of the screen per second): subclasses can change the motion. */
	protected void motion(int i, java.util.Random r) {
		vx[i] = (r.nextFloat() - 0.5F) * 0.03F;
		vy[i] = -(0.02F + r.nextFloat() * 0.05F);
	}

	private void seed() {
		for (int i = 0; i < MAX; i++) {
			px[i] = rnd.nextFloat();
			py[i] = rnd.nextFloat();
			ph[i] = rnd.nextFloat() * 6.2832F;
			motion(i, rnd);
		}
		seeded = true;
	}

	private void draw(DrawContext c) {
		if (mc.options != null && mc.options.hudHidden || mc.getWindow() == null) {
			return;
		}
		if (!seeded) {
			seed();
		}
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0.016F : Math.min(0.1F, (now - lastNs) / 1e9F);
		lastNs = now;
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		float sp = speed.f() / 100F, sz = size.f() / 100F;
		int n = Math.min(MAX, amount.i());
		for (int i = 0; i < n; i++) {
			px[i] += vx[i] * sp * dt;
			py[i] += vy[i] * sp * dt;
			ph[i] += dt * sp;
			if (py[i] < -0.05F || py[i] > 1.05F || px[i] < -0.05F || px[i] > 1.05F) {
				// out of the screen: comes back from the opposite side
				motion(i, rnd);
				if (vy[i] < 0) {
					py[i] = 1.04F;
					px[i] = rnd.nextFloat();
				} else {
					py[i] = -0.04F;
					px[i] = rnd.nextFloat();
				}
			}
			float a = 0.35F + 0.35F * (float) Math.sin(ph[i] * 2F);
			drawOne(c, px[i] * w, py[i] * h, (2F + (i % 5)) * sz, a, ph[i], colour.c());
		}
	}
}
