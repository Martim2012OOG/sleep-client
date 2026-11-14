package dev.solclient.fx;

import dev.solclient.mods.HologramScreensMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Matrix4f;

/**
 * State of the experimental 3D GUI: builds the perspective transform of the screen that is open, hands it to the GUI renderer and maps
 * the mouse back onto the tilted panel. Everything is guarded: if something is off, the screen just draws normally.
 */
public final class Holo {

	/** True from the moment a container screen starts drawing until the GUI pass of that frame is done. */
	public static volatile boolean pending;
	private static Screen current;
	private static Screen last;
	private static long openedAt;
	private static final Matrix4f G = new Matrix4f();
	private static boolean valid;
	private static float pivX, pivY;

	private Holo() {
	}

	public static boolean wanted(Screen s) {
		HologramScreensMod m = HologramScreensMod.instance;
		return m != null && m.enabled && m.appliesTo(s);
	}

	public static boolean activeFor(Screen s) {
		return pending && current == s;
	}

	private static float ease(float t) {
		t = Math.max(0F, Math.min(1F, t));
		return 1F - (float) Math.pow(1F - t, 3);
	}

	/** Called when the screen starts drawing (render, HEAD). Builds this frame's transform and marks the split of the GUI pass. */
	public static void begin(DrawContext ctx, Screen s, int px, int py, int pw, int ph) {
		HologramScreensMod m = HologramScreensMod.instance;
		if (m == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = System.currentTimeMillis();
		if (s != last) {
			last = s;
			openedAt = now;
			valid = false;
		}
		float p = ease((now - openedAt) / 340F);
		float t = now / 1000F;
		var w = mc.getWindow();
		float W = w.getScaledWidth(), H = w.getScaledHeight();
		pivX = px + pw / 2F;
		pivY = py + ph / 2F;
		float mxn = 0F;
		try {
			mxn = (float) (mc.mouse.getX() / Math.max(1, w.getWidth())) - 0.5F;
		} catch (RuntimeException ignored) {
		}
		float yaw = (float) Math.toRadians(m.angleDeg() * p + (m.floating() ? Math.sin(t * 0.9F) * 1.4F : 0F) + mxn * 5F);
		float pitch = (float) Math.toRadians(-3.5F * p + (m.floating() ? Math.sin(t * 1.3F + 1F) * 0.8F : 0F));
		float bob = m.floating() ? (float) Math.sin(t * 1.4F) * 2.2F : 0F;
		float sc = 0.9F + 0.1F * p; // pops in
		Matrix4f persp = new Matrix4f().m23(-1F / m.distance());
		G.identity().translation(pivX, pivY + bob, 0F).mul(persp).scale(sc, sc, sc).rotateY(yaw).rotateX(pitch).translate(-pivX, -pivY, 0F);
		valid = true;
		current = s;
		pending = true;
		// split the GUI pass here: the HUD (already drawn) stays flat, everything from now on is the floating panel
		ctx.applyBlur();
		if (m.glow()) {
			int a = Math.round(70 * p);
			for (int i = 0; i < 5; i++) {
				int al = Math.max(0, a - i * 12);
				int o = 2 + i * 2;
				ctx.fill(px - o, py - o, px + pw + o, py - o + 2, al << 24 | 0x6FD8FF);
				ctx.fill(px - o, py + ph + o - 2, px + pw + o, py + ph + o, al << 24 | 0x6FD8FF);
				ctx.fill(px - o, py - o, px - o + 2, py + ph + o, al << 24 | 0x6FD8FF);
				ctx.fill(px + pw + o - 2, py - o, px + pw + o, py + ph + o, al << 24 | 0x6FD8FF);
			}
		}
	}

	public static boolean valid() {
		return valid;
	}

	/** The transform of this frame as a matrix for the GUI projection (gui units in, gui units out, with perspective). */
	public static Matrix4f matrix() {
		return G;
	}

	public static boolean clearWorld() {
		HologramScreensMod m = HologramScreensMod.instance;
		return m != null && m.clearWorld();
	}

	/** Where the point (sx, sy) of the screen lands on the flat panel (the other way round of the picture); null = leave it as it is. */
	public static double[] unproject(double sx, double sy) {
		if (!valid) {
			return null;
		}
		Matrix4f g = G;
		double a = g.m00() - sx * g.m03(), b = g.m10() - sx * g.m13(), e = sx * g.m33() - g.m30();
		double c = g.m01() - sy * g.m03(), d = g.m11() - sy * g.m13(), f = sy * g.m33() - g.m31();
		double det = a * d - b * c;
		if (Math.abs(det) < 1e-9) {
			return null;
		}
		return new double[] {(e * d - b * f) / det, (a * f - e * c) / det};
	}

	/** The real mouse position in gui units, mapped onto the panel (used by the render pass: hover, tooltips). */
	public static double[] mouseOnPanel() {
		MinecraftClient mc = MinecraftClient.getInstance();
		var w = mc.getWindow();
		double sx = mc.mouse.getX() * w.getScaledWidth() / Math.max(1, w.getWidth());
		double sy = mc.mouse.getY() * w.getScaledHeight() / Math.max(1, w.getHeight());
		return unproject(sx, sy);
	}
}
