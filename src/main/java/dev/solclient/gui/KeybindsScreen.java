package dev.solclient.gui;

import dev.solclient.Config;
import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.SolClient;
import dev.solclient.mods.Mod;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/**
 * Keybinds (v99, simpler): in the Right Shift menu's layout. On the left, every action with its key as a little
 * keycap: click the action, press the new key (hold Ctrl / Shift / Alt for a combo), done; the x clears it. On the
 * right, the keyboard (or mouse + numpad) just shows which keys are used: a white dot = used, a red dot = two things on
 * the same key. Click a key to see only what is on it. No lines or labels around the keyboard.
 */
public class KeybindsScreen extends Screen {

	/** One thing a key can do: a Caos mod key or a Minecraft control. */
	static final class Action {
		final String label, group;
		final Option.Key opt;
		final KeyBinding kb;

		Action(String label, String group, Option.Key opt, KeyBinding kb) {
			this.label = label;
			this.group = group;
			this.opt = opt;
			this.kb = kb;
		}

		/** Keyboard code (+ combo bits); mouse buttons are -100 - button; -1 = none. */
		int code() {
			if (opt != null) {
				return opt.code();
			}
			try {
				InputUtil.Key k = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
				if (k.getCode() < 0) {
					return -1;
				}
				return k.getCategory() == InputUtil.Type.MOUSE ? -100 - k.getCode() : k.getCategory() == InputUtil.Type.KEYSYM ? k.getCode() : -1;
			} catch (RuntimeException e) {
				return -1;
			}
		}

		boolean isDefault() {
			return opt != null ? opt.get().equals(opt.def) : kb.isDefault();
		}

		void set(int code) {
			if (opt != null) {
				opt.set(code);
				return;
			}
			InputUtil.Key k = code <= -100 ? InputUtil.Type.MOUSE.createFromCode(-100 - code) : code < 0 ? InputUtil.UNKNOWN_KEY
					: InputUtil.Type.KEYSYM.createFromCode(Keys.base(code));
			kb.setBoundKey(k);
		}

		void reset() {
			if (opt != null) {
				opt.reset();
			} else {
				kb.setBoundKey(kb.getDefaultKey());
			}
		}

		String full() {
			return group + " - " + label;
		}
	}

	private record K(String label, int code, float w) {
	}

	private static final K[][] MAIN = {
			{k("`", 96), k("1", 49), k("2", 50), k("3", 51), k("4", 52), k("5", 53), k("6", 54), k("7", 55), k("8", 56), k("9", 57), k("0", 48), k("-", 45), k("=", 61),
					k("BKSP", 259, 2)},
			{k("TAB", 258, 1.5F), k("Q", 81), k("W", 87), k("E", 69), k("R", 82), k("T", 84), k("Y", 89), k("U", 85), k("I", 73), k("O", 79), k("P", 80), k("[", 91),
					k("]", 93), k("\\", 92, 1.5F)},
			{k("CAPS", 280, 1.75F), k("A", 65), k("S", 83), k("D", 68), k("F", 70), k("G", 71), k("H", 72), k("J", 74), k("K", 75), k("L", 76), k(";", 59), k("'", 39),
					k("ENTER", 257, 2.25F)},
			{k("SHIFT", 340, 2.25F), k("Z", 90), k("X", 88), k("C", 67), k("V", 86), k("B", 66), k("N", 78), k("M", 77), k(",", 44), k(".", 46), k("/", 47),
					k("SHIFT", 344, 2.75F)},
			{k("CTRL", 341, 1.5F), k(mac() ? "CMD" : "WIN", 343, 1.25F), k("ALT", 342, 1.25F), k("SPACE", 32, 6.25F), k("ALT", 346, 1.25F),
					k(mac() ? "CMD" : "WIN", 347, 1.25F), k("CTRL", 345, 1.5F)}};
	private static final K[] FKEYS = {k("ESC", 256), k("F1", 290), k("F2", 291), k("F3", 292), k("F4", 293), k("F5", 294), k("F6", 295), k("F7", 296), k("F8", 297),
			k("F9", 298), k("F10", 299), k("F11", 300), k("F12", 301)};
	private static final K[][] NAV = {{k("INS", 260), k("HOME", 268), k("PGUP", 266)}, {k("DEL", 261), k("END", 269), k("PGDN", 267)}, {}, {k("", -1), k("↑", 265),
			k("", -1)}, {k("←", 263), k("↓", 264), k("→", 262)}};
	private static final K[][] NUMPAD = {{k("NUM", -1), k("/", 331), k("*", 332), k("-", 333)}, {k("7", 327), k("8", 328), k("9", 329), k("+", 334)},
			{k("4", 324), k("5", 325), k("6", 326), k("", -1)}, {k("1", 321), k("2", 322), k("3", 323), k("ENT", 335)}, {k("0", 320, 2), k(".", 330)}};

	private static K k(String l, int c) {
		return new K(l, c, 1F);
	}

	private static K k(String l, int c, float w) {
		return new K(l, c, w);
	}

	private static boolean mac() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	}

	private final Screen parent;
	private final List<Action> actions = new ArrayList<>();
	private final Map<Integer, int[]> keyRects = new HashMap<>();
	private int page; // 0 keyboard, 1 mouse + numpad
	private int filterKey = Integer.MIN_VALUE; // a key clicked on the keyboard: the list shows only what is on it
	private final TextBox search = new TextBox(30);
	private float listScroll;
	private Action listening;
	private long resetAsk;
	private String hint = "";
	private final List<int[]> hits = new ArrayList<>();
	private final List<java.util.function.IntConsumer> acts = new ArrayList<>();
	private int listX, listY0, listY1, listW;

	public KeybindsScreen(Screen parent) {
		super(Text.literal("Keybinds"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		actions.clear();
		for (Mod m : SolClient.MODS) {
			for (Option<?> o : m.options) {
				if (o instanceof Option.Key key) {
					actions.add(new Action(Lang.t(key.name), m == SolClient.settings ? "Caos" : Lang.t(m.name), key, null));
				}
			}
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		for (KeyBinding kb : mc.options.allKeys) {
			actions.add(new Action(Text.translatable(kb.getId()).getString(), "Minecraft", null, kb));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private List<Action> on(int base) {
		List<Action> l = new ArrayList<>();
		for (Action a : actions) {
			int c = a.code();
			if (c != -1 && (c <= -100 ? c == base : Keys.base(c) == base)) {
				l.add(a);
			}
		}
		return l;
	}

	private boolean conflict(List<Action> l) {
		for (int i = 0; i < l.size(); i++) {
			for (int j = i + 1; j < l.size(); j++) {
				if (l.get(i).code() == l.get(j).code()) {
					return true;
				}
			}
		}
		return false;
	}

	private void save() {
		Config.save();
		KeyBinding.updateKeysByCode();
		MinecraftClient.getInstance().options.write();
	}

	private static String keyName(int code) {
		if (code == -1) {
			return "-";
		}
		return code <= -100 ? Lang.tr("Mouse ", "Rato ") + (-100 - code + 1) : Keys.name(code);
	}

	private void hit(int x, int y, int w, int h, java.util.function.IntConsumer r) {
		hits.add(new int[] {x, y, w, h});
		acts.add(r);
	}

	private void hit(int x, int y, int w, int h, Runnable r) {
		hit(x, y, w, h, b -> {
			if (b == 0) {
				r.run();
			}
		});
	}

	// ------------------------------------------------------------------ drawing
	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0x99000000);
		keyRects.clear();
		hits.clear();
		acts.clear();
		int pw = Math.min(width - 16, 720), ph = Math.min(height - 16, 380);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, Theme.PANEL_UI, Theme.CARD_BORDER);

		// ---- left: every action with its key
		int sw = Math.min(220, pw / 3), sx = px + 8, sy = py + 8, sh = ph - 16;
		Gfx.rrect(c, sx, sy, sw, sh, 8, Theme.SUB);
		Gfx.tex(c, Theme.icon2("keybinds"), sx + 10, sy + 10, 14, 14, 64, 64, 0xFFFFFFFF);
		Gfx.text(c, "Keybinds", sx + 30, sy + 13, Theme.TEXT);
		int fy = sy + 32;
		Gfx.rrectBorder(c, sx + 8, fy, sw - 16, 20, 6, Theme.FIELD, Theme.CARD_BORDER);
		search.draw(c, sx + 8, fy, sw - 16, 20, sx + 14, sw - 28, true, Theme.accent(), Theme.TEXT);
		if (search.text().isEmpty()) {
			Gfx.text(c, "Search...", sx + 14, fy + 6, 0xFF5E6272);
		}
		int ly = fy + 26;
		if (filterKey != Integer.MIN_VALUE) {
			String t = Lang.tr("On ", "Na tecla ") + keyName(filterKey);
			Gfx.textClip(c, t, sx + 10, ly + 3, sw - 90, Theme.TEXT);
			String all = "Show all";
			int aw = Gfx.tw(all) + 14;
			KuboButton.neutral(c, sx + sw - 8 - aw, ly - 1, aw, 16, all, in(mx, my, sx + sw - 8 - aw, ly - 1, aw, 16));
			hit(sx + sw - 8 - aw, ly - 1, aw, 16, () -> filterKey = Integer.MIN_VALUE);
			ly += 20;
		}
		drawList(c, mx, my, sx + 4, ly, sw - 8, sy + sh - 6 - ly);

		// ---- right: tabs, keyboard, bottom bar
		int cx = sx + sw + 14, cw = px + pw - 14 - cx, cy = py + 12;
		String[] tabs = {"Keyboard", "Mouse & Numpad"};
		int tx = cx;
		for (int i = 0; i < 2; i++) {
			int w = Gfx.tw(tabs[i]) + 20;
			boolean sel = page == i, hov = in(mx, my, tx, cy, w, 20);
			if (sel) {
				Gfx.rrect(c, tx, cy, w, 20, 4, SlideBg.colour());
			} else if (hov) {
				Gfx.rrect(c, tx, cy, w, 20, 4, 0x0CFFFFFF);
			}
			Gfx.textFit(c, tabs[i], tx + w / 2, w - 6, cy + 6, sel ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
			final int idx = i;
			hit(tx, cy, w, 20, () -> page = idx);
			tx += w + 4;
		}
		int areaY = cy + 30, areaH = py + ph - 44 - areaY;
		if (page == 0) {
			drawKeyboard(c, mx, my, cx, areaY, cw, areaH);
		} else {
			drawMousePage(c, mx, my, cx, areaY, cw, areaH);
		}
		// legend (two dots) + reset
		int by = py + ph - 32;
		Gfx.circle(c, cx + 4, by + 10, 2.5F, 0xFFFFFFFF);
		Gfx.textRaw(c, Lang.tr("used", "usada"), cx + 10, by + 6, Theme.DIM);
		int lx2 = cx + 16 + Gfx.twRaw(Lang.tr("used", "usada"));
		Gfx.circle(c, lx2 + 4, by + 10, 2.5F, 0xFFF87171);
		Gfx.textRaw(c, Lang.tr("two things on one key", "duas coisas na mesma tecla"), lx2 + 10, by + 6, Theme.DIM);
		boolean asking = System.currentTimeMillis() - resetAsk < 3000;
		String rs = asking ? "Click again to reset everything" : "Reset to Default";
		int rw = Math.max(110, Gfx.tw(rs) + 24), rx = px + pw - 14 - rw;
		KuboButton.danger(c, rx, by, rw, 20, rs, in(mx, my, rx, by, rw, 20));
		hit(rx, by, rw, 20, () -> {
			if (System.currentTimeMillis() - resetAsk < 3000) {
				for (Action a : actions) {
					a.reset();
				}
				save();
				resetAsk = 0;
				hint = Lang.tr("Every key is back to default", "Todas as teclas voltaram ao normal");
			} else {
				resetAsk = System.currentTimeMillis();
			}
		});
		if (!hint.isEmpty()) {
			Gfx.textClip(c, hint, cx, by - 14, cw, Theme.DIM);
		}
		// hover on a key: what it does, in one small box
		for (Map.Entry<Integer, int[]> e : keyRects.entrySet()) {
			int[] r = e.getValue();
			if (in(mx, my, r[0], r[1], r[2], r[3])) {
				List<Action> l = on(e.getKey());
				if (!l.isEmpty()) {
					int w = 0;
					for (Action a : l) {
						w = Math.max(w, Gfx.twRaw(a.label) + 12);
					}
					w = Math.min(w, 220);
					int bx = Math.min(width - w - 4, mx + 10), bh = l.size() * 11 + 6, byy = Math.min(height - bh - 4, my + 12);
					Gfx.rrectBorder(c, bx, byy, w, bh, 5, 0xF0101114, Theme.CARD_BORDER);
					int yy = byy + 4;
					for (Action a : l) {
						Gfx.textRaw(c, cut(a.label, w - 12), bx + 6, yy, 0xFFE6E8EE);
						yy += 11;
					}
				}
			}
		}
	}

	/** Actions (grouped by mod), each row: name on the left, its key as a keycap on the right. */
	private void drawList(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		listX = x;
		listW = w;
		listY0 = y;
		listY1 = y + h;
		String q = search.text().toLowerCase(Locale.ROOT);
		List<Action> list = new ArrayList<>();
		for (Action a : actions) {
			if (filterKey != Integer.MIN_VALUE && !on(filterKey).contains(a)) {
				continue;
			}
			if (q.isEmpty() || a.full().toLowerCase(Locale.ROOT).contains(q)) {
				list.add(a);
			}
		}
		int rh = 20;
		// group headers
		int content = 0;
		String lastGroup = null;
		for (Action a : list) {
			if (!a.group.equals(lastGroup)) {
				content += 16;
				lastGroup = a.group;
			}
			content += rh;
		}
		listScroll = Math.max(0, Math.min(Math.max(0, content - h), listScroll));
		c.enableScissor(x, y, x + w, y + h);
		int ry = y - Math.round(listScroll);
		lastGroup = null;
		for (Action a : list) {
			if (!a.group.equals(lastGroup)) {
				lastGroup = a.group;
				if (ry + 16 > y && ry < y + h) {
					Gfx.textRaw(c, cut(a.group.toUpperCase(Locale.ROOT), w - 8), x + 6, ry + 5, 0xFF6B7080);
				}
				ry += 16;
			}
			if (ry + rh > y && ry < y + h) {
				boolean lis = a == listening;
				boolean hov = in(mx, my, x, ry, w, rh) && my >= y && my < y + h;
				if (lis) {
					Gfx.rrect(c, x, ry, w, rh, 4, SlideBg.colour());
				} else if (hov) {
					Gfx.rrect(c, x, ry, w, rh, 4, 0x0CFFFFFF);
				}
				int code = a.code();
				boolean clash = code != -1 && conflict(on(code <= -100 ? code : Keys.base(code)));
				// keycap on the right
				String cap = lis ? Lang.tr("press a key", "carrega numa tecla") : keyName(code);
				int capW = Math.min(w / 2, Gfx.twRaw(cap) + 10), capX = x + w - capW - (lis || code == -1 ? 4 : 20);
				int capCol = lis ? Theme.accent() : code == -1 ? 0xFF1C1E26 : 0xFF2A2D38;
				Gfx.rrect(c, capX, ry + 3, capW, rh - 6, 3, Gfx.blend(capCol, 0xFF000000, 0.45F));
				Gfx.rrect(c, capX, ry + 3, capW, rh - 7, 3, capCol);
				Gfx.textRaw(c, cut(cap, capW - 6), capX + 5, ry + 6, code == -1 && !lis ? 0xFF6B7080 : 0xFFFFFFFF);
				if (clash) {
					Gfx.circle(c, capX - 5, ry + rh / 2F, 2.5F, 0xFFF87171);
				}
				Gfx.textRaw(c, cut(a.label, capX - x - 14), x + 6, ry + 6, lis ? 0xFFFFFFFF : Theme.TEXT);
				// clear (x) when it has a key
				if (!lis && code != -1) {
					int bx = x + w - 16, byy = ry + 4;
					boolean xh = in(mx, my, bx, byy, 12, 12);
					int col = xh ? 0xFFFF8A8A : 0xFF6B7080;
					for (int i = 0; i < 5; i++) {
						c.fill(bx + 3 + i, byy + 3 + i, bx + 4 + i, byy + 4 + i, col);
						c.fill(bx + 7 - i, byy + 3 + i, bx + 8 - i, byy + 4 + i, col);
					}
					if (my >= y && my < y + h) {
						hit(bx, byy, 12, 12, () -> {
							a.set(-1);
							save();
							hint = a.label + ": " + Lang.tr("no key", "sem tecla");
						});
					}
				}
				if (my >= y && my < y + h) {
					final int top = Math.max(ry, y);
					hit(x, top, w - 18, Math.min(rh, y + h - top), b -> {
						if (b == 1) {
							a.reset(); // right click: back to its default key
							save();
							hint = a.label + " → " + keyName(a.code());
						} else {
							listening = listening == a ? null : a;
						}
					});
				}
			}
			ry += rh;
		}
		c.disableScissor();
		if (list.isEmpty()) {
			Gfx.textClip(c, "Nothing on this key", x + 6, y + 6, w - 12, Theme.DIM);
		}
	}

	private int keyColour(int code, boolean hov, boolean sel) {
		int col = 0xFF22242C;
		if (sel) {
			col = Gfx.blend(col, 0xFFFFFFFF, 0.22F);
		} else if (hov) {
			col = Gfx.blend(col, 0xFFFFFFFF, 0.08F);
		}
		return col;
	}

	private void key(DrawContext c, int mx, int my, K key, int x, int y, int w, int h) {
		if (key.code() == -1 && key.label().isEmpty()) {
			return;
		}
		boolean hov = in(mx, my, x, y, w, h), sel = key.code() == filterKey;
		List<Action> l = key.code() == -1 ? List.of() : on(key.code());
		int col = key.code() < 0 && key.code() > -100 ? 0xFF16171D : keyColour(key.code(), hov, sel);
		Gfx.rrect(c, x, y, w, h, 3, Gfx.blend(col, 0xFF000000, 0.5F));
		Gfx.rrect(c, x, y, w, h - 2, 3, col);
		if (sel) {
			Gfx.rrectBorder(c, x - 1, y - 1, w + 2, h + 2, 4, 0x00000000, 0xFFFFFFFF);
		}
		String lbl = key.label();
		Gfx.textRaw(c, lbl, x + w / 2 - Gfx.twRaw(lbl) / 2, y + h / 2 - 5, l.isEmpty() ? 0xFF8C90A0 : 0xFFFFFFFF);
		if (!l.isEmpty()) {
			Gfx.circle(c, x + w - 4, y + 4, 1.6F, conflict(l) ? 0xFFF87171 : 0xFFFFFFFF);
		}
		if (key.code() >= 0 || key.code() <= -100) {
			keyRects.put(key.code(), new int[] {x, y, w, h});
		}
	}

	private void drawKeyboard(DrawContext c, int mx, int my, int ax, int ay, int aw, int ah) {
		float units = 15 + 4;
		int u = Math.max(12, Math.min(28, Math.min((int) ((aw - 8) / units), (ah - 6) / 6)));
		int gap = Math.max(2, u / 10);
		int kbW = Math.round(15 * u), totalW = kbW + Math.round(3.5F * u);
		int x0 = ax + aw / 2 - totalW / 2;
		int rowsH = 6 * u + u / 3;
		int y = ay + Math.max(0, (ah - rowsH) / 2);
		int x = x0;
		for (int i = 0; i < FKEYS.length; i++) {
			key(c, mx, my, FKEYS[i], x, y, u - gap, u - gap);
			x += u + (i == 0 || i == 4 || i == 8 ? u / 2 : 0);
		}
		y += u + u / 3;
		int yMain = y;
		for (K[] row : MAIN) {
			x = x0;
			for (K key : row) {
				int w = Math.round(key.w() * u);
				key(c, mx, my, key, x, y, w - gap, u - gap);
				x += w;
			}
			y += u;
		}
		int nx = x0 + kbW + u / 2;
		int yy = yMain;
		for (K[] row : NAV) {
			x = nx;
			for (K key : row) {
				key(c, mx, my, key, x, yy, u - gap, u - gap);
				x += u;
			}
			yy += u;
		}
	}

	private void drawMousePage(DrawContext c, int mx, int my, int ax, int ay, int aw, int ah) {
		int u = Math.max(14, Math.min(28, ah / 7));
		int nx = ax + aw / 2 - 5 * u - u / 2, ny = ay + ah / 2 - 5 * u / 2;
		Gfx.textRaw(c, "Numpad", nx, ny - 12, Theme.DIM);
		int y = ny;
		for (K[] row : NUMPAD) {
			int x = nx;
			for (K key : row) {
				int w = Math.round(key.w() * u);
				key(c, mx, my, key, x, y, w - 3, u - 3);
				x += w;
			}
			y += u;
		}
		int mxx = ax + aw / 2 + u, myy = ay + ah / 2 - 3 * u;
		int mw = 4 * u, mh = 6 * u;
		Gfx.rrect(c, mxx, myy, mw, mh, mw / 2, 0xFF16171D);
		K[] buttons = {k("L", -100), k("R", -101), k("M", -102), k("4", -103), k("5", -104)};
		key(c, mx, my, buttons[0], mxx + 3, myy + 3, mw / 2 - 5, mh / 3);
		key(c, mx, my, buttons[1], mxx + mw / 2 + 2, myy + 3, mw / 2 - 5, mh / 3);
		key(c, mx, my, buttons[2], mxx + mw / 2 - u / 3, myy + mh / 7, u * 2 / 3, mh / 5);
		key(c, mx, my, buttons[3], mxx - u / 2, myy + mh / 2, u * 2 / 3, u);
		key(c, mx, my, buttons[4], mxx - u / 2, myy + mh / 2 + u + 4, u * 2 / 3, u);
		Gfx.textRaw(c, Lang.tr("Mouse", "Rato"), mxx, myy - 12, Theme.DIM);
	}

	private static String cut(String s, int w) {
		if (Gfx.twRaw(s) <= w) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > w) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (click.button() == 0 && Notify.click(mx, my)) {
			return true;
		}
		// listening and a mouse button pressed outside the list: bind the mouse button (Minecraft controls only)
		if (listening != null && listening.opt == null && !in(mx, my, listX, listY0, listW, listY1 - listY0)) {
			listening.set(-100 - click.button());
			save();
			hint = listening.label + " → " + keyName(listening.code());
			listening = null;
			return true;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			int[] r = hits.get(i);
			if (in(mx, my, r[0], r[1], r[2], r[3])) {
				acts.get(i).accept(click.button());
				return true;
			}
		}
		for (Map.Entry<Integer, int[]> e : keyRects.entrySet()) {
			int[] r = e.getValue();
			if (in(mx, my, r[0], r[1], r[2], r[3])) {
				if (listening != null) {
					bind(e.getKey(), false, false, false);
				} else {
					filterKey = filterKey == e.getKey() ? Integer.MIN_VALUE : e.getKey();
					listScroll = 0;
				}
				return true;
			}
		}
		listening = null;
		return true;
	}

	private void bind(int code, boolean ctrl, boolean shift, boolean alt) {
		Action a = listening;
		if (a == null) {
			return;
		}
		int c = code <= -100 ? code : a.opt != null ? Keys.withMods(code, ctrl, shift, alt) : code;
		a.set(c);
		save();
		hint = a.label + " → " + keyName(a.code());
		listening = null;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (mx < listX + listW) {
			listScroll -= (float) v * 20;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (listening != null) {
			if (k == 256) {
				listening = null; // Esc: cancel
				return true;
			}
			boolean modKey = k == 340 || k == 344 || k == 341 || k == 345 || k == 342 || k == 346 || k == 343 || k == 347;
			if (modKey && listening.opt != null) {
				return true; // wait for the real key of the combo
			}
			bind(k, input.hasCtrlOrCmd(), input.hasShift(), input.hasAlt());
			return true;
		}
		if (k == 256) {
			if (filterKey != Integer.MIN_VALUE) {
				filterKey = Integer.MIN_VALUE;
			} else {
				close();
			}
			return true;
		}
		search.key(k);
		listScroll = 0;
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (listening != null) {
			return true;
		}
		int cp = input.codepoint();
		if (cp >= 32 && cp != 127) {
			search.typed(cp);
			listScroll = 0;
		}
		return true;
	}

	@Override
	public void close() {
		save();
		MinecraftClient.getInstance().setScreen(parent);
	}
}
