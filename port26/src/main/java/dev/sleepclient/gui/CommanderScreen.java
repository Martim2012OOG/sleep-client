package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.SleepClient;
import dev.sleepclient.mods.Mod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Commander (Ctrl / Cmd + K): one search box for the whole client. Type a few letters of a mod or an action, use the
 * arrows and press Enter to open it (Ctrl / Cmd + Enter switches a mod on / off). Things you used last show first.
 */
public class CommanderScreen extends Screen {

	private record Entry(String title, String sub, Identifier icon, Mod mod, Runnable run) {
	}

	private static final List<String> RECENT = new ArrayList<>();

	private final Screen parent;
	private final TextBox box = new TextBox(40);
	private List<Entry> all;
	private List<Entry> shown = List.of();
	private String lastQuery;
	private int sel;
	private float scroll, anim;
	private long openedAt;
	private int px, py, pw, rowsY;
	private static final int ROW = 26, MAX_ROWS = 8;

	public CommanderScreen(Screen parent) {
		super(Component.literal("Commander"));
		this.parent = parent;
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (!(dev.sleepclient.compat.Mc.screen(mc) instanceof CommanderScreen)) {
			dev.sleepclient.compat.Mc.setScreen(mc, new CommanderScreen(dev.sleepclient.compat.Mc.screen(mc)));
		}
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		if (all == null) {
			all = build();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// --------------------------------------------------------------- entries
	private List<Entry> build() {
		List<Entry> l = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		l.add(new Entry("Mods", "Open the mods menu", Theme.icon2("mods"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, new SolMenuScreen())));
		l.add(new Entry("Edit HUD", "Move and resize the HUD", Theme.icon2("hud"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, new MoveHudsScreen(false))));
		l.add(new Entry("Minigames", "Snake, 2048, Sleep Bird, Breakout, Stack and Tetris", Theme.icon2("minigames"), null,
				() -> dev.sleepclient.compat.Mc.setScreen(mc, new MinigamesScreen(parent))));
		l.add(new Entry("Friends & Chat", "Talk with your friends", Theme.icon2("chat"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_MODS))));
		l.add(new Entry("Shop", "Capes, emotes and cosmetics", Theme.icon2("shop"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_SHOP))));
		l.add(new Entry("Capes & Skins", "Your capes and skins", Theme.icon2("nav_capes"), null,
				() -> dev.sleepclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_CAPES))));
		l.add(new Entry("Notifications", "See your notifications", Theme.icon2("bell"), null,
				() -> dev.sleepclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_NOTIFICATIONS))));
		l.add(new Entry("Keybinds", "Every key on a keyboard: change, combos, reset", Theme.icon2("keybinds"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, new KeybindsScreen(parent))));
		l.add(new Entry("Host World", "Open your world to friends and invite them", Theme.icon2("host_world"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, new HostWorldScreen(parent))));
		l.add(new Entry("Skin Editor", "Paint your skin and wear it", Theme.icon2("skin_editor"), null, () -> dev.sleepclient.compat.Mc.setScreen(mc, new SkinEditorScreen(parent))));
		l.add(new Entry("Extensions", "Install more mods (Hypixel, stats, particles...)", Theme.icon2("extensions"), null,
				() -> dev.sleepclient.compat.Mc.setScreen(mc, SolMenuScreen.openExtensions())));
		for (dev.sleepclient.ext.KuboApi.Command cmd : dev.sleepclient.ext.KuboApi.COMMANDS) {
			l.add(new Entry(cmd.title(), cmd.sub(), Theme.icon2(cmd.icon()), null, cmd.run()));
		}
		l.add(new Entry("Font", "Choose the letters of the client", Theme.icon2("font"), null, () -> openSettings(SleepClient.settings)));
		l.add(new Entry("Language", "English, Português, Español, Français, Deutsch...", Theme.icon2("language"), null, () -> openSettings(SleepClient.settings)));
		l.add(new Entry("Emote Wheel", "Play an emote", Theme.icon2("players"), null, () -> {
			if (mc.player != null) {
				dev.sleepclient.compat.Mc.setScreen(mc, new EmoteWheelScreen());
			}
		}));
		if (dev.sleepclient.mods.ClipEventsMod.instance != null) {
			l.add(new Entry("Clip that", "Save the last seconds (Clip Events)", Theme.icon2("clip_events"), null, () -> {
				dev.sleepclient.compat.Mc.setScreen(mc, null);
				dev.sleepclient.mods.ClipEventsMod.instance.trigger("Commander");
			}));
		}
		if (dev.sleepclient.mods.FightCamMod.instance != null) {
			l.add(new Entry("Replay", "Watch your last fight from any angle", Theme.icon2("replay"), null, () -> {
				dev.sleepclient.compat.Mc.setScreen(mc, null);
				dev.sleepclient.mods.FightCamMod.instance.openReplay();
			}));
		}
		l.add(new Entry("Photo Mode", "Free camera, filters and screenshots", Theme.icon2("photo_mode"), null, () -> {
			if (mc.player != null) {
				dev.sleepclient.compat.Mc.setScreen(mc, new PhotoModeScreen());
			}
		}));
		for (Mod m : SleepClient.MODS) {
			if (m == SleepClient.settings) {
				l.add(new Entry("Sleep Settings", "Theme, font, language, keys", Theme.icon2("settings"), m, null));
				continue;
			}
			Identifier ic = Theme.icon(m.id);
			l.add(new Entry(m.name, m.description, ic != null ? ic : Theme.icon2("mods"), m, null));
		}
		return l;
	}

	private static void openSettings(Mod m) {
		SolMenuScreen s = new SolMenuScreen();
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), s);
		s.openSettingsFor(m);
	}

	private static int score(Entry e, String q) {
		if (q.isEmpty()) {
			int r = RECENT.indexOf(e.title());
			return r >= 0 ? 1000 - r : e.mod() == null ? 100 : 0;
		}
		String t = e.title().toLowerCase(Locale.ROOT), tp = Lang.t(e.title()).toLowerCase(Locale.ROOT);
		String d = (e.sub() == null ? "" : e.sub() + " " + Lang.t(e.sub())).toLowerCase(Locale.ROOT);
		int bonus = RECENT.contains(e.title()) ? 5 : 0;
		if (t.equals(q) || tp.equals(q)) {
			return 500 + bonus;
		}
		if (t.startsWith(q) || tp.startsWith(q)) {
			return 400 + bonus - t.length();
		}
		for (String w : (t + " " + tp).split("[ &/-]+")) {
			if (w.startsWith(q)) {
				return 300 + bonus - t.length();
			}
		}
		if (t.contains(q) || tp.contains(q)) {
			return 200 + bonus;
		}
		if (d.contains(q)) {
			return 100 + bonus;
		}
		// letters in order ("tgh" finds "Target HUD")
		int i = 0;
		for (char ch : t.toCharArray()) {
			if (i < q.length() && ch == q.charAt(i)) {
				i++;
			}
		}
		return i == q.length() && q.length() >= 2 ? 50 + bonus : -1;
	}

	private void refilter() {
		String q = box.text().trim().toLowerCase(Locale.ROOT);
		if (q.equals(lastQuery)) {
			return;
		}
		lastQuery = q;
		List<Entry> l = new ArrayList<>();
		List<Integer> sc = new ArrayList<>();
		for (Entry e : all) {
			int s = score(e, q);
			if (s >= 0) {
				int at = 0;
				while (at < l.size() && sc.get(at) >= s) {
					at++;
				}
				l.add(at, e);
				sc.add(at, s);
			}
		}
		shown = l;
		sel = 0;
		scroll = 0;
	}

	private void activate(Entry e, boolean toggle) {
		RECENT.remove(e.title());
		RECENT.add(0, e.title());
		while (RECENT.size() > 6) {
			RECENT.remove(RECENT.size() - 1);
		}
		if (e.mod() != null) {
			if (toggle && !e.mod().locked) {
				e.mod().toggle();
				dev.sleepclient.Config.save();
				return; // stay open so you can switch more
			}
			openSettings(e.mod());
			return;
		}
		e.run().run();
	}

	// ---------------------------------------------------------------- render
	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		if (parent != null && !(parent instanceof CommanderScreen)) { // never stack a Commander on a Commander
			try {
				parent.extractRenderState(c, -1, -1, delta);
			} catch (RuntimeException ignored) {
			}
		}
		refilter();
		float k = Math.min(1F, (System.currentTimeMillis() - openedAt) / 160F);
		anim = 1 - (1 - k) * (1 - k) * (1 - k);
		c.fill(0, 0, width, height, Gfx.withAlpha(0x05060A, Math.round(150 * anim)));
		int accent = Theme.accent();
		pw = Math.min(440, width - 32);
		px = (width - pw) / 2;
		py = Math.max(16, height / 6) + Math.round((1 - anim) * -8);
		int rows = Math.min(MAX_ROWS, Math.max(1, shown.size()));
		int ph = 40 + rows * ROW + 22;
		Gfx.rrectBorder(c, px - 1, py - 1, pw + 2, ph + 2, 13, 0x66000000, 0x00000000);
		Gfx.rrectBorder(c, px, py, pw, ph, 12, Theme.PANEL_UI, Theme.PANEL_BORDER);

		// search field
		int fx = px + 10, fy = py + 9, fw = pw - 20;
		Gfx.rrectBorder(c, fx, fy, fw, 24, 8, Theme.FIELD, Gfx.withAlpha(accent, 0xAA));
		Gfx.tex(c, Theme.icon2("search"), fx + 6, fy + 5, 14, 14, 64, 64, Theme.DIM);
		if (box.text().isEmpty()) {
			Gfx.textMid(c, "Search mods and actions...", fx + 26, fy, 24, 0xFF5E6272);
		}
		box.draw(c, fx + 26, fy, fw - 70, 24, fx + 26, fw - 74, true, accent, Theme.TEXT);
		String kb = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "⌘K" : "Ctrl K";
		int kw = Gfx.twRaw(kb) + 10;
		Gfx.rrect(c, fx + fw - kw - 5, fy + 5, kw, 14, 4, Theme.OFF_BG);
		Gfx.textRaw(c, kb, fx + fw - kw, fy + 8, Theme.DIM);

		// results
		rowsY = py + 40;
		if (shown.isEmpty()) {
			Gfx.textFit(c, "Nothing found", px + pw / 2, pw - 6, rowsY + 9, Theme.DIM);
		}
		int maxScroll = Math.max(0, shown.size() - MAX_ROWS);
		if (sel < scroll) {
			scroll = sel;
		} else if (sel >= scroll + MAX_ROWS) {
			scroll = sel - MAX_ROWS + 1;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int first = Math.round(scroll);
		for (int r = 0; r < rows && first + r < shown.size(); r++) {
			int i = first + r;
			Entry e = shown.get(i);
			int ry = rowsY + r * ROW, rx = px + 6, rw = pw - 12;
			boolean hov = mx >= rx && mx < rx + rw && my >= ry && my < ry + ROW;
			if (hov && (mx != lastMx || my != lastMy)) {
				sel = i;
			}
			boolean s = sel == i;
			if (s) {
				Gfx.rrect(c, rx, ry + 1, rw, ROW - 2, 7, Gfx.blend(accent, Theme.PANEL_UI, 0.8F));
				Gfx.rrect(c, rx, ry + 6, 2, ROW - 12, 1, accent);
			}
			Gfx.rrect(c, rx + 6, ry + 4, 18, 18, 5, s ? Gfx.withAlpha(accent, 0xCC) : Theme.OFF_BG);
			if (e.icon() != null) {
				Gfx.tex(c, e.icon(), rx + 8, ry + 6, 14, 14, 64, 64, 0xFFFFFFFF);
			}
			int tx = rx + 32;
			String title = e.title();
			Gfx.text(c, title, tx, ry + 4, s ? 0xFFFFFFFF : Theme.TEXT);
			int right = rx + rw - 8;
			if (e.mod() != null && e.mod() != SleepClient.settings) {
				boolean on = e.mod().enabled;
				String st = on ? "ON" : "OFF";
				int sw = Gfx.tw(st) + 12;
				Gfx.rrect(c, right - sw, ry + 7, sw, 12, 6, on ? Gfx.withAlpha(accent, 0xDD) : Theme.OFF_BG_HOVER);
				Gfx.textC(c, st, right - sw / 2, ry + 9, on ? 0xFFFFFFFF : Theme.DIM);
				right -= sw + 6;
			} else {
				Gfx.textRaw(c, "↵", right - Gfx.twRaw("↵"), ry + 9, s ? 0xFFFFFFFF : 0xFF5E6272);
				right -= 16;
			}
			String sub = e.sub() == null ? "" : Lang.t(e.sub());
			int avail = right - tx - 4;
			if (avail > 30 && !sub.isEmpty()) {
				Gfx.textRaw(c, cut(sub, avail), tx, ry + 14, s ? 0xFFB9BDC9 : 0xFF6B7080);
			}
		}
		if (shown.size() > MAX_ROWS) {
			int trackH = MAX_ROWS * ROW, barH = Math.max(12, trackH * MAX_ROWS / shown.size());
			int barY = rowsY + Math.round((trackH - barH) * (scroll / Math.max(1F, maxScroll)));
			Gfx.rrect(c, px + pw - 5, barY, 3, barH, 1, 0x55FFFFFF);
		}

		// footer
		int fy2 = py + ph - 18;
		c.fill(px + 10, fy2 - 3, px + pw - 10, fy2 - 2, Theme.PANEL_BORDER);
		String hint = Lang.tr("↑↓ move   ↵ open   Ctrl+↵ on/off   Esc close",
				"↑↓ navegar   ↵ abrir   Ctrl+↵ ligar/desligar   Esc fechar");
		Gfx.textRaw(c, cut(hint, pw - 20), px + 10, fy2 + 2, 0xFF6B7080);
		String count = shown.size() + "";
		Gfx.textRaw(c, count, px + pw - 10 - Gfx.twRaw(count), fy2 + 2, 0xFF4A4E5C);
		lastMx = mx;
		lastMy = my;
	}

	private int lastMx = -1, lastMy = -1;

	private static String cut(String s, int w) {
		if (Gfx.twRaw(s) <= w) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "...") > w) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	// ----------------------------------------------------------------- input
	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (k == 256) {
			onClose();
			return true;
		}
		if (k == 264) { // down
			if (!shown.isEmpty()) {
				sel = (sel + 1) % shown.size();
			}
			return true;
		}
		if (k == 265) { // up
			if (!shown.isEmpty()) {
				sel = Math.floorMod(sel - 1, shown.size());
			}
			return true;
		}
		if (k == 258) { // tab = down too
			if (!shown.isEmpty()) {
				sel = (sel + (input.hasShiftDown() ? shown.size() - 1 : 1)) % shown.size();
			}
			return true;
		}
		if (k == 257 || k == 335) {
			if (sel >= 0 && sel < shown.size()) {
				activate(shown.get(sel), input.hasControlDownWithQuirk());
			}
			return true;
		}
		if (k == 75 && input.hasControlDownWithQuirk()) {
			onClose();
			return true;
		}
		box.key(k);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		int cp = input.codepoint();
		if (cp >= 32 && cp != 127) {
			box.typed(cp);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (mx < px || mx > px + pw || my < py || my > py + 40 + MAX_ROWS * ROW + 22) {
			onClose();
			return true;
		}
		int r = (int) Math.floor((my - rowsY) / ROW);
		int i = Math.round(scroll) + r;
		if (my >= rowsY && r >= 0 && r < MAX_ROWS && i < shown.size()) {
			activate(shown.get(i), click.button() == 1);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (!shown.isEmpty()) {
			sel = Math.max(0, Math.min(shown.size() - 1, sel - (int) Math.signum(v)));
		}
		return true;
	}

	@Override
	public void onClose() {
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), parent);
	}
}
