package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.fightcam.ClipToast;
import dev.sleepclient.mods.PhotoModeMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Photo Mode: the camera leaves your head and you move it around you like a photographer. Filters, cinema bars,
 * a rule-of-thirds grid, zoom (scroll), and photos without any interface (saved in your screenshots folder).
 * WASD fly, Space / Shift up / down, drag to look, scroll = zoom, Enter = take the photo, H = hide the controls.
 */
public class PhotoModeScreen extends Screen {

	public static final String[] FILTERS = {"None", "Warm", "Cold", "Pastel", "Night", "Cinematic", "Dreamy", "Vintage", "Noir"};

	private static boolean active;
	private static double cx, cy, cz;
	private static float cyaw, cpitch;
	private static volatile int shotState; // 0 none, 1 hide UI this frame, 2 capture at the end of the frame
	private final Minecraft mc = Minecraft.getInstance();
	private final boolean oldHud;
	private final CameraType oldPerspective;
	private final int oldFov;
	private int filter, fov;
	private boolean bars, grid = true, vignette = true, hideUi;
	private float speed = 4F;
	private long lastNs;
	private boolean looking;
	private double lastMx, lastMy;

	private record Btn(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Btn> buttons = new ArrayList<>();

	public PhotoModeScreen() {
		super(Component.literal("Photo Mode"));
		oldHud = dev.sleepclient.compat.Mc.hideGui(mc);
		oldPerspective = mc.options.getCameraType();
		oldFov = mc.options.fov().get();
		fov = oldFov;
		filter = PhotoModeMod.instance == null ? 0 : PhotoModeMod.instance.defaultFilter();
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getViewVector(1F);
		// start a bit behind you, looking at you
		cx = eye.x - look.x * 3;
		cy = eye.y - look.y * 3 + 0.3;
		cz = eye.z - look.z * 3;
		cyaw = mc.player.getYRot();
		cpitch = mc.player.getXRot();
	}

	public static boolean active() {
		return active;
	}

	public static double[] pose() {
		return new double[] {cx, cy, cz, cyaw, cpitch};
	}

	@Override
	protected void init() {
		active = true;
		dev.sleepclient.compat.Mc.setHideGui(mc, true);
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	@Override
	public void removed() {
		active = false;
		shotState = 0;
		dev.sleepclient.compat.Mc.setHideGui(mc, oldHud);
		mc.options.setCameraType(oldPerspective);
		try {
			mc.options.fov().set(oldFov);
		} catch (RuntimeException ignored) {
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
	}

	// ------------------------------------------------------------------ per frame
	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0 : Math.min(0.1F, (now - lastNs) / 1e9F);
		lastNs = now;
		if (mc.player == null) {
			dev.sleepclient.compat.Mc.setScreen(mc, null);
			return;
		}
		fly(dt);
		drawFilter(c);
		boolean clean = hideUi || shotState != 0;
		if (grid && !clean) {
			int w3 = width / 3, h3 = height / 3;
			c.fill(w3, 0, w3 + 1, height, 0x40FFFFFF);
			c.fill(2 * w3, 0, 2 * w3 + 1, height, 0x40FFFFFF);
			c.fill(0, h3, width, h3 + 1, 0x40FFFFFF);
			c.fill(0, 2 * h3, width, 2 * h3 + 1, 0x40FFFFFF);
		}
		buttons.clear();
		if (!clean) {
			drawUi(c, mouseX, mouseY);
		}
		if (shotState == 1) {
			shotState = 2; // the frame being drawn now has no controls: grab it at the end of the frame
		}
		ClipToast.draw(c);
	}

	/** Called at the very end of every frame (after the menus are drawn). */
	public static void onFrameEnd() {
		if (!active || shotState != 2) {
			return;
		}
		shotState = 0;
		Minecraft mc = Minecraft.getInstance();
		try {
			Screenshot.grab(mc.gameDirectory, dev.sleepclient.compat.Mc.mainTarget(mc), msg -> ClipToast.show("Photo saved", "screenshots folder", null, false));
		} catch (RuntimeException e) {
			ClipToast.show("Photo failed", String.valueOf(e.getMessage()), null, true);
		}
	}

	private void fly(float dt) {
		double sp = speed * dt * (Keys.down(GLFW.GLFW_KEY_LEFT_CONTROL) ? 3 : 1);
		double f = 0, s = 0, u = 0;
		if (Keys.down(GLFW.GLFW_KEY_W)) {
			f += sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_S)) {
			f -= sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_A)) {
			s -= sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_D)) {
			s += sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_SPACE)) {
			u += sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_LEFT_SHIFT)) {
			u -= sp;
		}
		double yr = Math.toRadians(cyaw);
		double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -Math.cos(yr), rz = -Math.sin(yr);
		double nx = cx + fx * f + rx * s, ny = cy + u, nz = cz + fz * f + rz * s;
		// stay within 48 blocks of you
		Vec3 eye = mc.player.getEyePosition();
		double dx = nx - eye.x, dy = ny - eye.y, dz = nz - eye.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d > 48) {
			nx = eye.x + dx / d * 48;
			ny = eye.y + dy / d * 48;
			nz = eye.z + dz / d * 48;
		}
		cx = nx;
		cy = ny;
		cz = nz;
	}

	private void drawFilter(GuiGraphicsExtractor c) {
		int w = width, h = height;
		switch (filter) {
			case 1 -> {
				c.fill(0, 0, w, h, 0x1AFF9A40);
				c.fillGradient(0, h / 2, w, h, 0x00FF7A20, 0x1FFF7A20);
			}
			case 2 -> {
				c.fill(0, 0, w, h, 0x1A4A8CFF);
				c.fillGradient(0, 0, w, h / 2, 0x1F2050C0, 0x002050C0);
			}
			case 3 -> c.fill(0, 0, w, h, 0x24FFD6F0);
			case 4 -> c.fill(0, 0, w, h, 0x4D102040);
			case 5 -> {
				c.fillGradient(0, 0, w, h / 2, 0x281E6A7A, 0x001E6A7A);
				c.fillGradient(0, h / 2, w, h, 0x00FF8A3A, 0x24FF8A3A);
			}
			case 6 -> {
				c.fill(0, 0, w, h, 0x1FFFB8E0);
				c.fillGradient(0, 0, w, h / 2, 0x29FFFFFF, 0x00FFFFFF);
			}
			case 7 -> c.fill(0, 0, w, h, 0x29C9A46A);
			case 8 -> c.fill(0, 0, w, h, 0x59202020);
			default -> {
			}
		}
		if (vignette) {
			int e = Math.max(24, Math.min(w, h) / 3);
			c.fillGradient(0, 0, w, e, 0x80000000, 0x00000000);
			c.fillGradient(0, h - e, w, h, 0x00000000, 0x80000000);
			Gfx.gradientH(c, 0, 0, e, h, 0x80000000, 0x00000000);
			Gfx.gradientH(c, w - e, 0, e, h, 0x00000000, 0x80000000);
		}
		if (bars) {
			int bar = Math.round(h * 0.11F);
			c.fill(0, 0, w, bar, 0xFF000000);
			c.fill(0, h - bar, w, h, 0xFF000000);
		}
	}

	private void button(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean on, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = on ? Gfx.blend(Theme.accent(), 0xFF000000, 0.35F) : hov ? 0xE02A2E3A : 0xD01B1E27;
		Gfx.rrect(c, x, y, w, h, 5, bg);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, 0xFFFFFFFF);
		buttons.add(new Btn(x, y, w, h, run));
	}

	private void drawUi(GuiGraphicsExtractor c, int mx, int my) {
		Gfx.rrect(c, 6, 6, 120, 20, 6, 0xC0101218);
		Gfx.text(c, "PHOTO MODE", 14, 12, 0xFFFFFFFF);
		Gfx.text(c, "WASD fly · drag to look · scroll zoom · Enter = photo · H = hide", 8, 32, 0xA0FFFFFF);
		int y = height - 30, x = 10, bh = 18;
		button(c, mx, my, x, y, 18, bh, "<", false, () -> filter = (filter + FILTERS.length - 1) % FILTERS.length);
		x += 20;
		Gfx.rrect(c, x, y, 70, bh, 5, 0xD01B1E27);
		Gfx.textC(c, FILTERS[filter], x + 35, y + 5, 0xFFFFFFFF);
		x += 72;
		button(c, mx, my, x, y, 18, bh, ">", false, () -> filter = (filter + 1) % FILTERS.length);
		x += 26;
		button(c, mx, my, x, y, 56, bh, "Vignette", vignette, () -> vignette = !vignette);
		x += 58;
		button(c, mx, my, x, y, 40, bh, "Bars", bars, () -> bars = !bars);
		x += 42;
		button(c, mx, my, x, y, 40, bh, "Grid", grid, () -> grid = !grid);
		x += 48;
		Gfx.text(c, "Zoom " + fov + "°", x, y + 5, 0xFFFFFFFF);
		int rx = width - 10 - 40;
		button(c, mx, my, rx, y, 40, bh, "Exit", false, () -> dev.sleepclient.compat.Mc.setScreen(mc, null));
		rx -= 72;
		button(c, mx, my, rx, y, 70, bh, "● Photo", false, this::takePhoto);
	}

	private void takePhoto() {
		shotState = 1;
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (!hideUi) {
			for (int i = buttons.size() - 1; i >= 0; i--) {
				Btn b = buttons.get(i);
				if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
					b.run().run();
					return true;
				}
			}
		}
		looking = true;
		lastMx = mx;
		lastMy = my;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (looking) {
			float ddx = (float) (click.x() - lastMx), ddy = (float) (click.y() - lastMy);
			lastMx = click.x();
			lastMy = click.y();
			cyaw += ddx * 0.4F;
			cpitch = Math.max(-90F, Math.min(90F, cpitch + ddy * 0.4F));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		looking = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		fov = (int) Math.max(30, Math.min(110, fov - Math.signum(v) * 4));
		try {
			mc.options.fov().set(fov);
		} catch (RuntimeException ignored) {
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
			takePhoto();
			return true;
		}
		if (k == GLFW.GLFW_KEY_H) {
			hideUi = !hideUi;
			return true;
		}
		if (PhotoModeMod.instance != null && k == PhotoModeMod.instance.keyCode()) {
			dev.sleepclient.compat.Mc.setScreen(mc, null);
			return true;
		}
		return super.keyPressed(input);
	}
}
