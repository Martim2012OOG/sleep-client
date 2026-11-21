package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.mods.WaypointsMod;
import dev.sleepclient.mods.WaypointsMod.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Waypoint editor: your waypoints on the left, a live preview of the selected one in the middle and its look on the right
 * (shape, colour, size, opacity, outline, what to show, when to hide). Everything saves the moment you change it.
 */
public class WaypointEditorScreen extends Screen {

	private final Screen parent;
	private Point sel;
	private float listScroll;
	private boolean renaming;
	private String hint = "";
	private final List<int[]> hits = new ArrayList<>();
	private final List<Runnable> acts = new ArrayList<>();
	private DoubleConsumer slider;
	private int sliderX, sliderW;
	/** The whole editor is drawn in a virtual size and scaled down to fit small windows / big GUI scales. */
	private float k = 1F;

	public WaypointEditorScreen(Screen parent) {
		super(Component.literal("Waypoints"));
		this.parent = parent;
		List<Point> l = list();
		sel = l.isEmpty() ? null : l.get(0);
	}

	private static List<Point> list() {
		return WaypointsMod.instance == null ? List.of() : WaypointsMod.instance.pointsHere();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void hit(int x, int y, int w, int h, Runnable r) {
		hits.add(new int[] {x, y, w, h});
		acts.add(r);
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x99000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		hits.clear();
		acts.clear();
		extractBackground(c, mouseX, mouseY, delta);
		// fit: the editor needs about 780 x 440 units; on a smaller screen everything is scaled down together
		k = Math.max(0.4F, Math.min(1F, Math.min((width - 12) / 780F, (height - 12) / 440F)));
		int vw = Math.round(width / k), vh = Math.round(height / k);
		int mx = Math.round(mouseX / k), my = Math.round(mouseY / k);
		c.pose().pushMatrix();
		c.pose().scale(k, k);
		try {
			int pw = Math.min(780, vw - 12), ph = Math.min(440, vh - 12);
			int x = (vw - pw) / 2, y = (vh - ph) / 2;
			Gfx.rrectBorder(c, x, y, pw, ph, 6, Theme.PANEL_UI, Theme.PANEL_BORDER);
			Gfx.text(c, "Waypoints", x + 16, y + 14, Theme.TEXT);
			Gfx.text(c, hint.isEmpty() ? "Changes save on their own" : hint, x + 16 + Gfx.tw("Waypoints") + 12, y + 14, Theme.DIM);
			button(c, mx, my, x + pw - 40, y + 8, 28, 22, "x", false, this::onClose);

			int top = y + 40, bot = y + ph - 14;
			int lw = Math.min(200, pw / 4), rw = Math.min(236, pw * 3 / 10);
			int lx = x + 14, cx = lx + lw + 12, rx = x + pw - rw - 14, cw = rx - cx - 12;
			drawList(c, mx, my, lx, top, lw, bot - top);
			drawPreview(c, cx, top, Math.max(60, cw), bot - top);
			drawProps(c, mx, my, rx, top, rw, bot - top);
		} finally {
			c.pose().popMatrix();
		}
	}

	// ------------------------------------------------------------------ list
	private void drawList(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h) {
		Gfx.rrectBorder(c, x, y, w, h, 4, Theme.CARD, Theme.CARD_BORDER);
		button(c, mx, my, x + 8, y + 8, w - 16, 24, "+  Add here", true, () -> {
			if (WaypointsMod.instance != null) {
				Point p = WaypointsMod.instance.addHere();
				if (p != null) {
					sel = p;
					renaming = false;
				}
			}
		});
		List<Point> pts = list();
		int ly0 = y + 40, ly1 = y + h - 6, rowH = 30;
		float maxScroll = Math.max(0, pts.size() * rowH - (ly1 - ly0));
		listScroll = Math.max(0, Math.min(maxScroll, listScroll));
		if (pts.isEmpty()) {
			Gfx.textFit(c, "No waypoints here yet", x + w / 2, w - 16, y + h / 2 - 4, Theme.DIM);
		}
		c.enableScissor(x + 2, ly0, x + w - 2, ly1);
		var tr = Minecraft.getInstance().player;
		for (int i = 0; i < pts.size(); i++) {
			Point p = pts.get(i);
			int ry = ly0 + i * rowH - Math.round(listScroll);
			if (ry + rowH < ly0 || ry > ly1) {
				continue;
			}
			boolean hov = in(mx, my, x + 6, ry, w - 12, rowH - 3) && my >= ly0 && my < ly1;
			boolean on = p == sel;
			Gfx.rrect(c, x + 6, ry, w - 12, rowH - 3, 4, on ? 0xFF2A2D38 : hov ? Theme.TILE_HOVER : Theme.TILE);
			Gfx.circle(c, x + 18, ry + (rowH - 3) / 2F, 5, p.color | 0xFF000000);
			String d = tr == null ? "" : Math.round(Math.sqrt(tr.distanceToSqr(p.x, p.y, p.z))) + "m";
			Gfx.textClip(c, p.name, x + 30, ry + 4, w - 30 - 48, p.visible ? Theme.TEXT : Theme.DIM);
			Gfx.text(c, d, x + 30, ry + 14, Theme.DIM);
			// eye: show / hide
			int ex = x + w - 34;
			Gfx.rrect(c, ex, ry + 5, 20, 16, 4, p.visible ? 0xFF2B5FA8 : 0xFF2A2C35);
			Gfx.textC(c, p.visible ? "on" : "off", ex + 10, ry + 9, 0xFFFFFFFF);
			if (my >= ly0 && my < ly1) {
				hit(ex, ry + 5, 20, 16, () -> {
					p.visible = !p.visible;
					WaypointsMod.instance.saveNow();
				});
				hit(x + 6, ry, w - 44, rowH - 3, () -> {
					sel = p;
					renaming = false;
				});
			}
		}
		c.disableScissor();
	}

	// ------------------------------------------------------------------ preview
	private void drawPreview(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		Gfx.rrectBorder(c, x, y, w, h, 4, Theme.CARD, Theme.CARD_BORDER);
		Gfx.text(c, "Preview", x + 10, y + 8, Theme.DIM);
		int ax = x + 10, ay = y + 24, aw = w - 20, ah = h - 34;
		// a little checkerboard so see-through markers read well
		for (int cy = 0; cy < ah; cy += 12) {
			for (int cxx = 0; cxx < aw; cxx += 12) {
				c.fill(ax + cxx, ay + cy, Math.min(ax + aw, ax + cxx + 12), Math.min(ay + ah, ay + cy + 12), ((cxx / 12 + cy / 12) & 1) == 0 ? 0xFF1B1D24 : 0xFF16181E);
			}
		}
		if (sel == null) {
			Gfx.textFit(c, "Pick or add a waypoint", ax + aw / 2, aw - 10, ay + ah / 2 - 4, Theme.DIM);
			return;
		}
		var m = c.pose();
		m.pushMatrix();
		m.translate(ax + aw / 2F, ay + ah / 2F + 10);
		float sc = 2.4F * sel.size / 100F;
		m.scale(sc, sc);
		WaypointsMod.shape(c, sel.shape, sel.color, sel.outline, sel.opacity);
		WaypointsMod.label(c, Minecraft.getInstance().font, sel, 42, -19);
		m.popMatrix();
		Gfx.textC(c, "x " + (int) Math.floor(sel.x) + "   y " + (int) Math.floor(sel.y) + "   z " + (int) Math.floor(sel.z), ax + aw / 2, ay + ah - 14, Theme.DIM);
	}

	// ------------------------------------------------------------------ properties
	private void drawProps(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h) {
		Gfx.rrectBorder(c, x, y, w, h, 4, Theme.CARD, Theme.CARD_BORDER);
		if (sel == null) {
			return;
		}
		int ix = x + 10, iw = w - 20, yy = y + 10;
		// name
		Gfx.text(c, "Name", ix, yy, Theme.DIM);
		yy += 12;
		boolean hovName = in(mx, my, ix, yy, iw, 22);
		Gfx.rrectBorder(c, ix, yy, iw, 22, 4, Theme.FIELD, renaming ? Theme.accent() : hovName ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
		Gfx.textClip(c, sel.name + (renaming && (System.currentTimeMillis() / 500 & 1) == 0 ? "|" : ""), ix + 7, yy + 7, iw - 14, Theme.TEXT);
		hit(ix, yy, iw, 22, () -> renaming = true);
		yy += 30;
		// shape
		Gfx.text(c, "Shape", ix, yy, Theme.DIM);
		yy += 12;
		int sw = (iw - 4 * 4) / 5;
		for (int i = 0; i < 5; i++) {
			int sx = ix + i * (sw + 4);
			final int shape = i;
			boolean on = sel.shape == i;
			Gfx.rrectBorder(c, sx, yy, sw, 26, 4, on ? 0xFF2A2D38 : Theme.TILE, on ? Theme.accent() : Theme.CARD_BORDER);
			c.pose().pushMatrix();
			c.pose().translate(sx + sw / 2F, yy + 13);
			c.pose().scale(0.8F, 0.8F);
			WaypointsMod.shape(c, i, sel.color, sel.outline, 100);
			c.pose().popMatrix();
			hit(sx, yy, sw, 26, () -> change(() -> sel.shape = shape));
		}
		yy += 34;
		// colour
		Gfx.text(c, "Colour", ix, yy, Theme.DIM);
		yy += 12;
		int[] cols = WaypointsMod.COLOURS;
		int cs = (iw - (cols.length - 1) * 4) / cols.length;
		for (int i = 0; i < cols.length; i++) {
			int sx = ix + i * (cs + 4);
			int col = cols[i];
			Gfx.rrectBorder(c, sx, yy, cs, cs, 4, col | 0xFF000000, (sel.color & 0xFFFFFF) == (col & 0xFFFFFF) ? 0xFFFFFFFF : Theme.CARD_BORDER);
			hit(sx, yy, cs, cs, () -> change(() -> sel.color = col));
		}
		yy += cs + 10;
		yy = slider(c, mx, my, "Size", sel.size, 50, 200, ix, yy, iw, v -> sel.size = (int) v);
		yy = slider(c, mx, my, "Opacity", sel.opacity, 20, 100, ix, yy, iw, v -> sel.opacity = (int) v);
		yy = toggle(c, mx, my, "Outline", sel.outline, ix, yy, iw, () -> sel.outline = !sel.outline);
		yy = toggle(c, mx, my, "Show name", sel.showName, ix, yy, iw, () -> sel.showName = !sel.showName);
		yy = toggle(c, mx, my, "Show distance", sel.showDist, ix, yy, iw, () -> sel.showDist = !sel.showDist);
		yy = toggle(c, mx, my, "Hide when near", sel.hideNear, ix, yy, iw, () -> sel.hideNear = !sel.hideNear);
		yy = toggle(c, mx, my, "Only when facing it", sel.onlyFacing, ix, yy, iw, () -> sel.onlyFacing = !sel.onlyFacing);
		int by = y + h - 34;
		int half = (iw - 6) / 2;
		button(c, mx, my, ix, by, half, 24, "Same look for all", false, () -> {
			for (Point p : list()) {
				p.shape = sel.shape;
				p.size = sel.size;
				p.opacity = sel.opacity;
				p.outline = sel.outline;
				p.showName = sel.showName;
				p.showDist = sel.showDist;
				p.hideNear = sel.hideNear;
				p.onlyFacing = sel.onlyFacing;
			}
			WaypointsMod.instance.saveNow();
			hint = "Look copied to every waypoint here";
		});
		button(c, mx, my, ix + half + 6, by, half, 24, "Delete", false, () -> {
			WaypointsMod.instance.remove(sel);
			List<Point> l = list();
			sel = l.isEmpty() ? null : l.get(0);
			renaming = false;
		});
	}

	private void change(Runnable r) {
		r.run();
		WaypointsMod.instance.saveNow();
	}

	private int toggle(GuiGraphicsExtractor c, int mx, int my, String label, boolean on, int x, int y, int w, Runnable flip) {
		Gfx.text(c, label, x, y + 3, Theme.TEXT);
		int tx = x + w - 28;
		boolean hov = in(mx, my, x, y, w, 18);
		Gfx.rrect(c, tx, y + 2, 28, 14, 7, on ? Theme.accent() : hov ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
		Gfx.circle(c, on ? tx + 21 : tx + 7, y + 9, 5, 0xFFFFFFFF);
		hit(x, y, w, 18, () -> change(flip));
		return y + 20;
	}

	private int slider(GuiGraphicsExtractor c, int mx, int my, String label, int value, int min, int max, int x, int y, int w, DoubleConsumer set) {
		Gfx.text(c, label, x, y, Theme.TEXT);
		String v = value + "%";
		Gfx.text(c, v, x + w - Gfx.tw(v), y, Theme.DIM);
		int ty = y + 14;
		Gfx.rrect(c, x, ty, w, 4, 2, Theme.OFF_BG);
		int fw = Math.round(w * (value - min) / (float) (max - min));
		Gfx.rrect(c, x, ty, Math.max(4, fw), 4, 2, Theme.accent());
		Gfx.circle(c, x + fw, ty + 2, 5, 0xFFFFFFFF);
		hits.add(new int[] {x - 4, ty - 6, w + 8, 16});
		acts.add(() -> {
			slider = v2 -> {
				set.accept(Math.round(min + (max - min) * Math.max(0, Math.min(1, v2))));
				WaypointsMod.instance.saveNow();
			};
			sliderX = x;
			sliderW = w;
			slider.accept((mx - x) / (double) w);
		});
		return y + 26;
	}

	private void button(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Runnable r) {
		boolean hov = in(mx, my, x, y, w, h);
		Gfx.rrect(c, x, y, w, h, 4, primary ? (hov ? Theme.accentHover() : Theme.accent()) : hov ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
		Gfx.textFit(c, label, x + w / 2, w - 8, y + (h - 8) / 2, 0xFFFFFFFF);
		hit(x, y, w, h, r);
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x() / k, my = click.y() / k;
		renaming = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			int[] r = hits.get(i);
			if (in(mx, my, r[0], r[1], r[2], r[3])) {
				acts.get(i).run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (slider != null) {
			slider.accept((click.x() / k - sliderX) / (double) sliderW);
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		slider = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		listScroll -= (float) v * 24 / k;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (renaming && sel != null) {
			if (k == 259 && !sel.name.isEmpty()) {
				sel.name = sel.name.substring(0, sel.name.length() - 1);
				WaypointsMod.instance.saveNow();
				return true;
			}
			if (k == 257 || k == 256) {
				renaming = false;
				return true;
			}
			return true;
		}
		if (k == 256) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (renaming && sel != null) {
			int cp = input.codepoint();
			if (cp >= 32 && cp != 127 && sel.name.length() < 28) {
				sel.name += new String(Character.toChars(cp));
				WaypointsMod.instance.saveNow();
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), parent);
	}
}
