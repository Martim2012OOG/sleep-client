package dev.solclient.gui;

import dev.solclient.Config;
import dev.solclient.Gfx;
import dev.solclient.SolClient;
import dev.solclient.mods.HudMod;
import dev.solclient.mods.Mod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD editor.
 *  - drag an element to move it; it snaps to the screen edges / centre and to the other elements (hold Shift = no snap);
 *  - drag the corner handle or scroll over it to resize; arrow keys nudge the selected one (Shift = 10 px);
 *  - toolbar on the selected element: smaller / bigger, settings, reset, hide;
 *  - HUD elements are turned on / off in the mods menu (no big list over the screen);
 *  - bottom bar: Done, grid, snapping, reset all.
 */
public class MoveHudsScreen extends Screen {

	private static final int SNAP = 4;
	private static boolean grid = false, snapping = true;

	private record Btn(int x, int y, int w, int h, Runnable run) {
	}

	private final boolean fromMenu;
	private final List<Btn> buttons = new ArrayList<>();
	private HudMod selected, dragging, resizing;
	private int offX, offY;
	private float resizeStartScale;
	private double resizeStartX;
	private Integer guideX, guideY;
	private int mx, my;

	public MoveHudsScreen(boolean fromMenu) {
		super(Component.literal("Edit HUD"));
		this.fromMenu = fromMenu;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void leave() {
		Config.save();
		SolClient.armed = false;
		dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), fromMenu ? new SolMenuScreen() : null);
	}

	private static List<HudMod> huds() {
		List<HudMod> l = new ArrayList<>();
		for (Mod m : SolClient.MODS) {
			if (m instanceof HudMod h) {
				l.add(h);
			}
		}
		return l;
	}

	private static List<HudMod> active() {
		List<HudMod> l = new ArrayList<>();
		for (HudMod h : huds()) {
			if (h.enabled) {
				l.add(h);
			}
		}
		return l;
	}

	/** Scale of the editor's own controls (toolbar, bottom bar): they stay small even with a big GUI scale. */
	private float k = 1F;

	private float uiScale() {
		double f = Minecraft.getInstance().getWindow().getGuiScale();
		return (float) Math.max(0.5, Math.min(1.0, 2.0 / Math.max(1.0, f)));
	}

	private void begin(GuiGraphicsExtractor c) {
		k = uiScale();
		c.pose().pushMatrix();
		c.pose().scale(k, k);
	}

	private void end(GuiGraphicsExtractor c) {
		c.pose().popMatrix();
		k = 1F;
	}

	/** Laby-like button; on = pressed look (toggles that are active). Coordinates are in the scaled space. */
	private void btn(GuiGraphicsExtractor c, int x, int y, int w, int h, String label, boolean on, Runnable run) {
		float smx = mx / k, smy = my / k;
		boolean hov = smx >= x && smx < x + w && smy >= y && smy < y + h;
		Gfx.button(c, x, y, w, h, hov, on);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 9) / 2 + (on ? 1 : 0), on ? Gfx.BTN_TEXT_ON : 0xFFFFFFFF);
		buttons.add(new Btn(Math.round(x * k), Math.round(y * k), Math.round(w * k), Math.round(h * k), run));
	}

	// ------------------------------------------------------------------ render
	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		buttons.clear();
		mx = mouseX;
		my = mouseY;
		int accent = Theme.accent();
		c.fill(0, 0, width, height, 0x66000000);
		if (grid) {
			for (int x = 0; x < width; x += 10) {
				c.fill(x, 0, x + 1, height, 0x0FFFFFFF);
			}
			for (int y = 0; y < height; y += 10) {
				c.fill(0, y, width, y + 1, 0x0FFFFFFF);
			}
		}
		// centre lines (faint) and active snap guides (accent)
		c.fill(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
		c.fill(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);
		if (dragging != null && guideX != null) {
			c.fill(guideX, 0, guideX + 1, height, accent);
		}
		if (dragging != null && guideY != null) {
			c.fill(0, guideY, width, guideY + 1, accent);
		}

		HudMod hover = null;
		for (HudMod h : active()) {
			if (h.contains(mouseX, mouseY)) {
				hover = h;
			}
		}
		for (HudMod h : active()) {
			h.draw(c, true);
			int bx = h.boundsX(), by = h.boundsY(), bw = Math.max(6, h.boundsW()), bh = Math.max(6, h.boundsH());
			boolean sel = h == selected;
			boolean hov = h == hover || h == dragging;
			c.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, sel ? 0x18FFFFFF : hov ? 0x10FFFFFF : 0x00000000);
			dashed(c, bx - 1, by - 1, bw + 2, bh + 2, sel ? accent : hov ? 0xCCFFFFFF : 0x55FFFFFF);
			if (sel) {
				// resize handle (bottom-right)
				Gfx.rrect(c, bx + bw - 3, by + bh - 3, 7, 7, 2, accent);
			}
			if (hov && !sel) {
				label(c, h.name, bx, by - 13);
			}
		}
		if (selected != null && selected.enabled) {
			begin(c);
			toolbar(c, selected);
			end(c);
		}
		begin(c);
		bottomBar(c);
		end(c);
	}

	private void label(GuiGraphicsExtractor c, String s, int x, int y) {
		int w = Gfx.tw(s) + 10;
		y = Math.max(2, y);
		Gfx.rrect(c, x, y, w, 12, 4, 0xE0101114);
		Gfx.text(c, s, x + 5, y + 2, 0xFFFFFFFF);
	}

	private static void dashed(GuiGraphicsExtractor c, int x, int y, int w, int h, int col) {
		for (int i = 0; i < w; i += 4) {
			c.fill(x + i, y, x + Math.min(w, i + 2), y + 1, col);
			c.fill(x + i, y + h - 1, x + Math.min(w, i + 2), y + h, col);
		}
		for (int i = 0; i < h; i += 4) {
			c.fill(x, y + i, x + 1, y + Math.min(h, i + 2), col);
			c.fill(x + w - 1, y + i, x + w, y + Math.min(h, i + 2), col);
		}
	}

	private void toolbar(GuiGraphicsExtractor c, HudMod h) {
		String title = h.name + "  " + Math.round(h.scale.get()) + "%";
		int tw = Gfx.tw(title) + 12;
		int bw = 18, gap = 3;
		int total = tw + 5 * (bw + gap) + 6;
		int W = Math.round(width / k), H = Math.round(height / k);
		int x = Math.max(2, Math.min(W - total - 2, Math.round(h.boundsX() / k)));
		int y = Math.round((h.boundsY() + h.boundsH()) / k) + 6;
		if (y + 22 > H - 30) {
			y = Math.round(h.boundsY() / k) - 26;
		}
		Gfx.rrectBorder(c, x, y, total, 22, 4, 0xF0101114, 0xFF0A0B10);
		Gfx.text(c, title, x + 7, y + 7, 0xFFFFFFFF);
		int bx = x + tw;
		btn(c, bx, y + 2, bw, bw, "-", false, () -> h.scale.set(h.scale.get() - 5F));
		bx += bw + gap;
		btn(c, bx, y + 2, bw, bw, "+", false, () -> h.scale.set(h.scale.get() + 5F));
		bx += bw + gap;
		btn(c, bx, y + 2, bw, bw, "", false, () -> {
			Config.save();
			SolMenuScreen menu = new SolMenuScreen();
			dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), menu);
			menu.openSettingsFor(h);
		});
		Gfx.tex(c, Theme.icon2("settings_small"), bx + 3, y + 5, 12, 12, 64, 64, 0xFFFFFFFF);
		bx += bw + gap;
		btn(c, bx, y + 2, bw, bw, "R", false, h::resetPosition);
		bx += bw + gap;
		btn(c, bx, y + 2, bw, bw, "x", false, () -> {
			h.enabled = false;
			selected = null;
		});
		int smx = Math.round(mx / k), smy = Math.round(my / k);
		if (smx >= bx - 4 * (bw + gap) && smx < bx + bw && smy >= y && smy < y + 22) {
			int i = (smx - (x + tw)) / (bw + gap);
			String[] tips = {"Smaller", "Bigger", "Settings", "Reset position & size", "Hide (turn off)"};
			if (i >= 0 && i < tips.length) {
				label(c, tips[i], smx + 8, smy + 12);
			}
		}
	}

	private void bottomBar(GuiGraphicsExtractor c) {
		int W = Math.round(width / k), H = Math.round(height / k);
		int bw = 56, bh = 18, gap = 4;
		int total = 4 * bw + 3 * gap;
		int x = W / 2 - total / 2, y = H - bh - 6;
		btn(c, x, y, bw, bh, "Grid", grid, () -> grid = !grid);
		btn(c, x + (bw + gap), y, bw, bh, "Snap", snapping, () -> snapping = !snapping);
		btn(c, x + 2 * (bw + gap), y, bw, bh, "Reset", false, () -> {
			for (HudMod h : huds()) {
				h.resetPosition();
			}
		});
		btn(c, x + 3 * (bw + gap), y, bw, bh, "Done", false, this::leave);
		if (selected == null && dragging == null) {
			String hint = "Drag to move  •  Scroll to resize";
			Gfx.textC(c, hint, W / 2, y - 11, 0x88FFFFFF);
		}
	}

	// ------------------------------------------------------------------ snapping
	private void snapMove(HudMod h, int nx, int ny, boolean free) {
		int w = h.boundsW(), hh = h.boundsH();
		guideX = null;
		guideY = null;
		if (snapping && !free) {
			List<int[]> xs = new ArrayList<>(), ys = new ArrayList<>();
			// targets: screen edges (2 px margin) and centre
			xs.add(new int[]{2, 2});
			xs.add(new int[]{width - 2, width - 2});
			xs.add(new int[]{width / 2, width / 2});
			ys.add(new int[]{2, 2});
			ys.add(new int[]{height - 2, height - 2});
			ys.add(new int[]{height / 2, height / 2});
			for (HudMod o : active()) {
				if (o == h) {
					continue;
				}
				int ox = o.boundsX(), oy = o.boundsY(), ow = o.boundsW(), oh = o.boundsH();
				xs.add(new int[]{ox, ox});
				xs.add(new int[]{ox + ow, ox + ow});
				xs.add(new int[]{ox + ow / 2, ox + ow / 2});
				xs.add(new int[]{ox + ow + 2, ox + ow + 2}); // right next to it
				xs.add(new int[]{ox - 2, ox - 2});
				ys.add(new int[]{oy, oy});
				ys.add(new int[]{oy + oh, oy + oh});
				ys.add(new int[]{oy + oh / 2, oy + oh / 2});
				ys.add(new int[]{oy + oh + 2, oy + oh + 2});
				ys.add(new int[]{oy - 2, oy - 2});
			}
			int best = SNAP + 1;
			int[] mine = {nx, nx + w / 2, nx + w};
			for (int[] t : xs) {
				for (int k = 0; k < 3; k++) {
					int d = Math.abs(mine[k] - t[0]);
					if (d < best) {
						best = d;
						guideX = t[0];
						nx = t[0] - (k == 0 ? 0 : k == 1 ? w / 2 : w);
					}
				}
			}
			best = SNAP + 1;
			int[] mineY = {ny, ny + hh / 2, ny + hh};
			for (int[] t : ys) {
				for (int k = 0; k < 3; k++) {
					int d = Math.abs(mineY[k] - t[0]);
					if (d < best) {
						best = d;
						guideY = t[0];
						ny = t[0] - (k == 0 ? 0 : k == 1 ? hh / 2 : hh);
					}
				}
			}
		}
		nx = Math.max(0, Math.min(width - Math.max(4, w), nx));
		ny = Math.max(0, Math.min(height - Math.max(4, hh), ny));
		h.setBounds(nx, ny);
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double x = click.x(), y = click.y();
		for (int i = buttons.size() - 1; i >= 0; i--) {
			Btn b = buttons.get(i);
			if (x >= b.x && x < b.x + b.w && y >= b.y && y < b.y + b.h) {
				if (click.button() == 0) {
					b.run.run();
				}
				return true;
			}
		}
		if (click.button() != 0) {
			return true;
		}
		// resize handle of the selected element
		if (selected != null && selected.enabled) {
			int hx = selected.boundsX() + selected.boundsW(), hy = selected.boundsY() + selected.boundsH();
			if (Math.abs(x - hx) <= 5 && Math.abs(y - hy) <= 5) {
				resizing = selected;
				resizeStartScale = selected.scale.get();
				resizeStartX = x;
				return true;
			}
		}
		List<HudMod> a = active();
		for (int i = a.size() - 1; i >= 0; i--) {
			HudMod h = a.get(i);
			if (h.contains(x, y)) {
				dragging = h;
				selected = h;
				offX = (int) x - h.boundsX();
				offY = (int) y - h.boundsY();
				return true;
			}
		}
		selected = null;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (resizing != null) {
			float base = Math.max(8, resizing.width());
			float sc = resizeStartScale + (float) ((click.x() - resizeStartX) / base * 100.0);
			resizing.scale.set(sc);
			return true;
		}
		if (dragging != null) {
			boolean free = (dev.solclient.Keys.down(340) || dev.solclient.Keys.down(344));
			snapMove(dragging, (int) click.x() - offX, (int) click.y() - offY, free);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		dragging = null;
		resizing = null;
		guideX = null;
		guideY = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double hAmount, double vAmount) {
		List<HudMod> a = active();
		for (int i = a.size() - 1; i >= 0; i--) {
			HudMod h = a.get(i);
			if (h.contains(x, y)) {
				h.scale.set(h.scale.get() + (float) Math.signum(vAmount) * 5F);
				selected = h;
				break;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (k == 256 || k == SolClient.settings.editHudKey.code() || k == SolClient.settings.modsKey.code()) {
			leave();
			return true;
		}
		if (selected != null && k >= 262 && k <= 265) {
			int step = (dev.solclient.Keys.down(340) || dev.solclient.Keys.down(344)) ? 10 : 1;
			int x = selected.boundsX(), y = selected.boundsY();
			switch (k) {
				case 262 -> x += step;
				case 263 -> x -= step;
				case 264 -> y += step;
				default -> y -= step;
			}
			x = Math.max(0, Math.min(width - selected.boundsW(), x));
			y = Math.max(0, Math.min(height - selected.boundsH(), y));
			selected.setBounds(x, y);
			return true;
		}
		if (selected != null && (k == 261 || k == 259)) { // Delete / Backspace: hide
			selected.enabled = false;
			selected = null;
			return true;
		}
		return false;
	}

	@Override
	public void removed() {
		Config.save();
	}
}
