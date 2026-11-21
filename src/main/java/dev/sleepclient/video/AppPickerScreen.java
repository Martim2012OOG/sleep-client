package dev.sleepclient.video;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.gui.KuboButton;
import dev.sleepclient.gui.SlideBg;
import dev.sleepclient.gui.TextBox;
import dev.sleepclient.gui.Theme;
import dev.sleepclient.gui.UiScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Identifier;

/**
 * Open Apps (v102): every app of this computer in a grid of tiles (icon + name), the open ones first. Type to search,
 * click one: it appears on a panel in front of you, live, and you can use it (click on it, scroll, type).
 * Right Shift menu look.
 */
public class AppPickerScreen extends UiScreen {

	private final Screen parent;
	private volatile List<AppMirror.Source> list;
	private float scroll;
	private final TextBox search = new TextBox(40);
	private int tab; // 0 open, 1 installed

	public AppPickerScreen(Screen parent) {
		super("Open Apps");
		this.parent = parent;
		reload();
	}

	private void reload() {
		list = null;
		Thread t = new Thread(() -> list = AppMirror.list(), "kubo-app-list");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void draw(DrawContext c, float delta) {
		c.fill(0, 0, width, height, 0x88000000);
		int pw = Math.min(520, width - 20), ph = Math.min(320, height - 24);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 4, Theme.PANEL_UI, Theme.CARD_BORDER);

		// side: Open now / Installed + help
		int sw = 128, sx = px + 8, sy = py + 8, sh = ph - 16;
		Gfx.rrect(c, sx, sy, sw, sh, 3, Theme.SUB);
		Gfx.text(c, "Open Apps", sx + 10, sy + 12, Theme.TEXT);
		String[] tabs = {"Open now", "Installed"};
		List<AppMirror.Source> l = list;
		for (int i = 0; i < 2; i++) {
			int iy = sy + 32 + i * 28;
			boolean cur = tab == i, hov = hovered(sx + 4, iy, sw - 8, 24);
			if (cur) {
				Gfx.rrect(c, sx + 4, iy, sw - 8, 24, 3, SlideBg.colour());
			} else if (hov) {
				Gfx.rrect(c, sx + 4, iy, sw - 8, 24, 3, 0x0CFFFFFF);
			}
			Gfx.textMid(c, tabs[i], sx + 12, iy, 24, cur ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
			if (l != null) {
				int n = 0;
				for (AppMirror.Source s : l) {
					n += s.installed() == (i == 1) ? 1 : 0;
				}
				String ns = String.valueOf(n);
				Gfx.textRaw(c, ns, sx + sw - 12 - Gfx.twRaw(ns), iy + 8, 0xFF6B7080);
			}
			final int idx = i;
			region(sx + 4, iy, sw - 8, 24, () -> {
				tab = idx;
				scroll = 0;
			});
		}
		String help = AppMirror.MAC ? Lang.tr("Mac: allow the launcher in Privacy > Screen Recording and Accessibility",
				"Mac: dá ao launcher Gravação do ecrã e Acessibilidade (Privacidade)") : Lang.tr("Click on the panel to use the app", "Clica no painel para usar a app");
		drawWrapped(c, help, sx + 10, sy + sh - 58, sw - 20, 0xFF6B7080);
		String rl = "Refresh";
		KuboButton.neutral(c, sx + 8, sy + sh - 26, sw - 16, 18, rl, hovered(sx + 8, sy + sh - 26, sw - 16, 18));
		region(sx + 8, sy + sh - 26, sw - 16, 18, this::reload);

		// right: search + grid of tiles
		int cx = sx + sw + 12, cw = px + pw - 12 - cx, cy = py + 12;
		Gfx.rrectBorder(c, cx, cy, cw, 22, 3, Theme.FIELD, Theme.CARD_BORDER);
		search.draw(c, cx, cy, cw, 22, cx + 8, cw - 16, true, Theme.accent(), Theme.TEXT);
		if (search.isEmpty()) {
			Gfx.text(c, "Search apps...", cx + 8, cy + 7, 0xFF5E6272);
		}
		int gy = cy + 30, gh = py + ph - 12 - gy;
		if (l == null) {
			Gfx.textC(c, Lang.tr("Looking for apps...", "A procurar apps..."), cx + cw / 2, gy + gh / 2 - 4, Theme.DIM);
			return;
		}
		String q = search.text().toLowerCase(Locale.ROOT).trim();
		List<AppMirror.Source> shown = new ArrayList<>();
		for (AppMirror.Source s : l) {
			if (s.installed() == (tab == 1) && (q.isEmpty() || s.label().toLowerCase(Locale.ROOT).contains(q))) {
				shown.add(s);
			}
		}
		if (shown.isEmpty()) {
			String msg = !AppMirror.problem.isEmpty() ? AppMirror.problem : Lang.tr("Nothing here", "Nada aqui");
			drawWrapped(c, msg, cx + 4, gy + 6, cw - 8, 0xFFFFD27A);
			return;
		}
		int tile = 74, gap = 6, cols = Math.max(1, (cw + gap) / (tile + gap));
		int tw = (cw - (cols - 1) * gap) / cols, th = 70;
		int rows = (shown.size() + cols - 1) / cols, total = rows * (th + gap);
		scroll = Math.max(0, Math.min(Math.max(0, total - gh), scroll));
		c.enableScissor(cx - 2, gy, cx + cw + 2, gy + gh);
		for (int i = 0; i < shown.size(); i++) {
			AppMirror.Source s = shown.get(i);
			int tx = cx + (i % cols) * (tw + gap), ty = gy + (i / cols) * (th + gap) - Math.round(scroll);
			if (ty + th < gy || ty > gy + gh) {
				continue;
			}
			boolean hov = hovered(tx, ty, tw, th) && mouseY >= gy && mouseY < gy + gh;
			Gfx.rrectBorder(c, tx, ty, tw, th, 3, hov ? Theme.TILE_HOVER : Theme.TILE, hov ? 0xFFB6BAC8 : Theme.CARD_BORDER);
			Identifier icon = AppIcons.get(s);
			int is = 32, ix = tx + tw / 2 - is / 2, iy = ty + 8;
			if (icon != null) {
				Gfx.tex(c, icon, ix, iy, is, is, 64, 64, 0xFFFFFFFF);
			} else {
				Gfx.rrect(c, ix, iy, is, is, 3, 0xFF2A2D36);
				String ini = s.label().isEmpty() ? "?" : s.label().substring(0, 1).toUpperCase(Locale.ROOT);
				Gfx.textRaw(c, ini, ix + is / 2 - Gfx.twRaw(ini) / 2, iy + is / 2 - 4, Theme.TEXT);
			}
			String name = s.app() != null && !s.installed() ? s.app() : s.label();
			Gfx.textRaw(c, fitRaw(name, tw - 8), tx + tw / 2 - Math.min(Gfx.twRaw(name), tw - 8) / 2, ty + th - 22, Theme.TEXT);
			if (!s.installed() && !s.label().equals(name)) {
				String sub = s.label().startsWith(name + " - ") ? s.label().substring(name.length() + 3) : "";
				Gfx.textRaw(c, fitRaw(sub, tw - 8), tx + tw / 2 - Math.min(Gfx.twRaw(sub), tw - 8) / 2, ty + th - 12, 0xFF6B7080);
			}
			if (hov) {
				tooltip = s.label();
			}
			if (mouseY >= gy && mouseY < gy + gh) {
				region(tx, ty, tw, th, () -> {
					if (!dev.sleepclient.mods.BrowserMod.on() && dev.sleepclient.mods.BrowserMod.instance != null) {
						dev.sleepclient.mods.BrowserMod.instance.setEnabled(true);
					}
					Panels.createApp(s);
					mc.setScreen(null);
				});
			}
		}
		c.disableScissor();
	}

	private static String fitRaw(String s, int w) {
		if (Gfx.twRaw(s) <= w) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > w) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	private void drawWrapped(DrawContext c, String s, int x, int y, int w, int col) {
		StringBuilder line = new StringBuilder();
		int yy = y;
		for (String word : s.split(" ")) {
			String t = line.length() == 0 ? word : line + " " + word;
			if (Gfx.twRaw(t) > w && line.length() > 0) {
				Gfx.textRaw(c, line.toString(), x, yy, col);
				yy += 10;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(t);
			}
		}
		if (line.length() > 0) {
			Gfx.textRaw(c, line.toString(), x, yy, col);
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * 24;
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		search.typed(input.codepoint());
		scroll = 0;
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) {
			close();
			return true;
		}
		search.key(input.key());
		scroll = 0;
		return true;
	}

	@Override
	public void close() {
		mc.setScreen(parent);
	}
}
