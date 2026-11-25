package dev.solclient.gui;

import dev.solclient.Config;
import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.SolClient;
import dev.solclient.mods.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * First start: a short setup in the Right Shift menu's look (same panel, cards, buttons and checkboxes).
 * 1 language, 2 performance (Performance / Balanced / Quality), 3 theme + transparency, 4 HUD (Minimal / PvP / Everything),
 * 5 extras. The theme shows live; the rest changes on "Finish". Every choice is also in Caos Settings afterwards
 * (Setup, Theme & Colours, Menus, Players), and the whole setup can be run again from there.
 */
public class OnboardingScreen extends UiScreen {

	private final Screen next;
	private int step;
	private int lang = Lang.idx(), perf = 1, hud = 1, theme, glass;
	/** ESC menu: 0 Caos (simple), 1 Classic + Caos, 2 Minecraft's own. Mods screen: 0 Caos, 1 Mod Menu. */
	private int pause = 0, modsScr;
	private boolean intro, seasonal, music, animatedCapes, badge;
	private static final int STEPS = 6;
	private static final int[] GLASS = {0, 20, 40, 60};

	public OnboardingScreen(Screen next) {
		super("Caos setup");
		this.next = next;
		var s = SolClient.settings;
		intro = CinematicIntroMod.instance != null && CinematicIntroMod.instance.enabled;
		seasonal = s == null || s.seasonalBackground.on();
		music = s == null || s.seasonalMusic.on();
		animatedCapes = s == null || s.animatedCapes.on();
		badge = s == null || s.nameBadge.on();
		if (s != null) {
			perf = s.perfPreset.idx();
			hud = s.hudPreset.idx();
			pause = !s.customPause.on() ? 2 : s.pauseStyle.idx();
			modsScr = s.modsScreen.idx();
			theme = s.uiTheme.idx();
			int g = s.uiTransparency.i();
			for (int i = 0; i < GLASS.length; i++) {
				if (Math.abs(GLASS[i] - g) < Math.abs(GLASS[glass] - g)) {
					glass = i;
				}
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/** The setup's own texts in the language being picked (other languages: translated automatically). */
	private String tr(String en, String pt) {
		return lang == 1 ? pt : lang == 0 ? en : dev.solclient.AutoTranslate.get(Lang.CODES[lang], en);
	}

	/** Every language with its flag, in two columns. */
	private void languages(GuiGraphicsExtractor c, int x, int y, int w) {
		int n = Lang.NAMES.length, colW = (w - 6) / 2, rh = 22;
		for (int i = 0; i < n; i++) {
			int rx = x + (i % 2) * (colW + 6), ry = y + (i / 2) * (rh + 3);
			boolean on = i == lang, hov = hovered(rx, ry, colW, rh);
			float a = anim("obl" + i, hov);
			Gfx.rrect(c, rx, ry, colW, rh, 4, on ? Theme.ROW : Gfx.blend(Theme.PANEL_UI | 0xFF000000, Theme.ROW, a));
			CheckBox.draw(c, "obl" + i, rx + 6, ry + 5.5F, 11, on, hov);
			Gfx.tex(c, Gfx.id("textures/gui/flag/" + Lang.FLAGS[i] + ".png"), rx + 22, ry + 5, 16, 12, 64, 48, 0xFFFFFFFF);
			Gfx.textRaw(c, Lang.NAMES[i], rx + 42, ry + 7, on ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, a));
			final int idx = i;
			region(rx, ry, colW, rh, () -> {
				lang = idx;
				applyLanguage(); // the setup itself switches language right away
			});
		}
	}

	// ------------------------------------------------------------------ frame
	/**
	 * Same layout as the Right Shift menu: a side list of the steps on the left (the current one highlighted, the
	 * finished ones ticked) and the step's choices on the right as plain rows, then Back / Next at the bottom right.
	 */
	@Override
	protected void draw(GuiGraphicsExtractor c, float delta) {
		c.fill(0, 0, width, height, 0x99000000);
		int pw = Math.min(460, width - 24), ph = Math.min(260, height - 24);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, Theme.PANEL_UI, Theme.CARD_BORDER);

		// ---- side list (like the menu's left bar)
		int sw = 128, sx = px + 8, sy = py + 8;
		Gfx.rrect(c, sx, sy, sw, ph - 16, 8, Theme.SUB);
		Gfx.tex(c, Theme.icon2("sol_client"), sx + 10, sy + 10, 14, 14, 64, 64, 0xFFFFFFFF);
		Gfx.text(c, tr("Caos setup", "Configurar o Caos"), sx + 30, sy + 13, Theme.TEXT);
		String[] steps = {tr("Language", "Língua"), tr("Performance", "Desempenho"), tr("Theme", "Tema"), "HUD", tr("Menus", "Menus"), tr("Extras", "Extras")};
		String[] icons = {"language", "performance", "colour_saturation", "hud", "screen_tweaks", "settings_small"};
		for (int i = 0; i < STEPS; i++) {
			int iy = sy + 34 + i * 28;
			boolean cur = i == step, done = i < step;
			if (cur) {
				Gfx.rrect(c, sx + 4, iy, sw - 8, 24, 4, SlideBg.colour());
			}
			int col = cur ? 0xFFFFFFFF : done ? Theme.TEXT : Theme.DIM;
			Gfx.tex(c, Theme.icon2(icons[i]), sx + 12, iy + 5, 14, 14, 64, 64, col);
			Gfx.textMid(c, steps[i], sx + 32, iy, 24, col);
			if (done) {
				CheckBox.draw(c, "obstep" + i, sx + sw - 20, iy + 6.5F, 11, true, false);
			}
			final int to = i;
			if (i <= step) {
				region(sx + 4, iy, sw - 8, 24, () -> step = to);
			}
		}
		String skip = tr("Skip setup", "Saltar");
		int skw = Gfx.tw(skip) + 12, sky = py + ph - 30;
		boolean skh = hovered(sx + 4, sky, skw, 16);
		Gfx.text(c, skip, sx + 10, sky + 4, skh ? Theme.TEXT : Theme.DIM);
		region(sx + 4, sky, skw, 16, () -> {
			applyLanguage();
			if (SolClient.settings != null) {
				SolClient.settings.onboarded.set(true);
			}
			Config.save();
			dev.solclient.compat.Mc.setScreen(mc, next);
		});

		// ---- the step
		int cx = sx + sw + 14, cw = px + pw - 14 - cx, cy = py + 16;
		String[] titles = {tr("Language", "Língua"), tr("Performance", "Desempenho"), tr("Theme", "Tema"), tr("What's on your screen", "O que aparece no ecrã"),
				tr("Esc menu", "Menu do Esc"), tr("Extras", "Extras")};
		String[] subs = {tr("You can change it later in Settings.", "Podes mudar depois nas Definições."),
				tr("Pick what matters more on this computer.", "Escolhe o que importa mais neste computador."),
				tr("This menu changes as you pick. Settings > Theme & Colours.", "Este menu muda enquanto escolhes. Definições > Tema e Cores."),
				tr("You can turn each one on or off later.", "Depois podes ligar ou desligar cada um."),
				tr("Settings > Menus changes this later.", "Definições > Menus muda isto depois."),
				tr("Turn off what you don't want.", "Desliga o que não queres.")};
		Gfx.text(c, titles[step], cx, cy, Theme.TEXT);
		Gfx.textRaw(c, subs[step], cx, cy + 13, Theme.DIM);
		int ry = cy + 32;
		switch (step) {
			case 0 -> languages(c, cx, ry - 4, cw);
			case 1 -> choices(c, cx, ry, cw, new String[] {tr("Performance", "Desempenho"), tr("Balanced", "Equilibrado"), tr("Quality", "Qualidade")},
					new String[] {tr("most FPS, fewer effects", "mais FPS, menos efeitos"), tr("recommended", "recomendado"),
							tr("every effect, strong PC", "todos os efeitos, PC forte")},
					perf, i -> perf = i);
			case 2 -> themeStep(c, cx, ry, cw);
			case 3 -> choices(c, cx, ry, cw, new String[] {tr("Minimal", "Mínimo"), "PvP", tr("Everything", "Tudo")},
					new String[] {tr("FPS + armour", "FPS + armadura"), tr("FPS, CPS, keys, armour, potions...", "FPS, CPS, teclas, armadura, poções..."),
							tr("PvP + coords, clock, biome", "PvP + coordenadas, relógio, bioma")},
					hud, i -> hud = i);
			case 4 -> menusStep(c, cx, ry, cw);
			default -> extras(c, cx, ry, cw);
		}

		// ---- Back / Next (bottom right)
		int by = py + ph - 32, bh = 20;
		String nl = step == STEPS - 1 ? tr("Finish", "Concluir") : tr("Next", "Seguinte");
		int nw = Math.max(72, Gfx.tw(nl) + 24), nx = px + pw - 14 - nw;
		KuboButton.primary(c, nx, by, nw, bh, nl, hovered(nx, by, nw, bh));
		region(nx, by, nw, bh, () -> {
			if (step == 0) {
				applyLanguage();
			}
			if (step < STEPS - 1) {
				step++;
			} else {
				finish();
			}
		});
		if (step > 0) {
			String bl = tr("Back", "Voltar");
			int bw = Math.max(60, Gfx.tw(bl) + 20), bx = nx - 6 - bw;
			KuboButton.neutral(c, bx, by, bw, bh, bl, hovered(bx, by, bw, bh));
			region(bx, by, bw, bh, () -> step--);
		}
	}

	/** One choice per row, like the settings rows: a tick box, the name, a short note on the right. */
	private void choices(GuiGraphicsExtractor c, int x, int y, int w, String[] names, String[] notes, int sel, java.util.function.IntConsumer pick) {
		for (int i = 0; i < names.length; i++) {
			int ry = y + i * 30;
			boolean on = i == sel, hov = hovered(x, ry, w, 26);
			float a = anim("obc" + step + "_" + i, hov);
			Gfx.rrect(c, x, ry, w, 26, 4, on ? Theme.ROW : Gfx.blend(Theme.PANEL_UI | 0xFF000000, Theme.ROW, a));
			CheckBox.draw(c, "obc" + step + "_" + i, x + 8, ry + 7.5F, 11, on, hov);
			Gfx.textMid(c, names[i], x + 26, ry, 26, on ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, a));
			if (!notes[i].isEmpty()) {
				Gfx.textRaw(c, notes[i], x + w - 10 - Gfx.twRaw(notes[i]), ry + 9, 0xFF6B7080);
			}
			final int idx = i;
			region(x, ry, w, 26, () -> pick.accept(idx));
		}
	}

	/** Themes as rows (3 colour squares on the right), then the transparency as 4 segments. Applied right away. */
	private void themeStep(GuiGraphicsExtractor c, int x, int y, int w) {
		String[] names = {"Caos (Original)", "Eclipse", "Graphite", "AMOLED"};
		String[] notes = {"", tr("see-through, grey", "transparente, cinzento"), tr("warm grey", "cinzento quente"), tr("pure black", "preto puro")};
		for (int i = 0; i < names.length; i++) {
			int ry = y + i * 28;
			boolean on = i == theme, hov = hovered(x, ry, w, 24);
			float a = anim("obt" + i, hov);
			Gfx.rrect(c, x, ry, w, 24, 4, on ? Theme.ROW : Gfx.blend(Theme.PANEL_UI | 0xFF000000, Theme.ROW, a));
			CheckBox.draw(c, "obt" + i, x + 8, ry + 6.5F, 11, on, hov);
			Gfx.textMid(c, names[i], x + 26, ry, 24, on ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, a));
			int[] sw = Presets.swatch(i);
			int sx = x + w - 10 - 3 * 12;
			for (int k = 0; k < 3; k++) {
				Gfx.rrectBorder(c, sx + k * 12, ry + 7, 10, 10, 2, sw[k] | 0xFF000000, 0xFF3A3D49);
			}
			if (!notes[i].isEmpty()) {
				Gfx.textRaw(c, notes[i], sx - 8 - Gfx.twRaw(notes[i]), ry + 8, 0xFF6B7080);
			}
			final int idx = i;
			region(x, ry, w, 24, () -> {
				theme = idx;
				if (SolClient.settings != null) {
					SolClient.settings.uiTheme.set(idx); // shows at once
				}
			});
		}
		int ty = y + names.length * 28 + 4;
		Gfx.textMid(c, tr("Transparency", "Transparência"), x, ty, 20, Theme.TEXT);
		int segX = x + 96, segW = w - 96, gap = 2;
		Gfx.rrect(c, segX, ty, segW, 20, 6, Theme.ROW);
		float tw = (segW - 6 - gap * (GLASS.length - 1)) / (float) GLASS.length;
		for (int i = 0; i < GLASS.length; i++) {
			int tx = Math.round(segX + 3 + i * (tw + gap));
			boolean on = i == glass, hov = hovered(tx, ty + 3, Math.round(tw), 14);
			if (on) {
				Gfx.rrect(c, tx, ty + 3, Math.round(tw), 14, 4, SlideBg.colour());
			}
			Gfx.textC(c, GLASS[i] + "%", Math.round(tx + tw / 2F), ty + 6, on ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
			final int idx = i;
			region(tx, ty + 3, Math.round(tw), 14, () -> {
				glass = idx;
				if (SolClient.settings != null) {
					SolClient.settings.uiTransparency.set((float) GLASS[idx]);
					Theme.refresh();
				}
			});
		}
	}

	/** One compact row (tick box, name, note on the right) for the Menus step. */
	private void menuRow(GuiGraphicsExtractor c, String key, int x, int y, int w, String name, String note, boolean on, Runnable pick) {
		boolean hov = hovered(x, y, w, 22);
		float a = anim("obm" + key, hov);
		Gfx.rrect(c, x, y, w, 22, 4, on ? Theme.ROW : Gfx.blend(Theme.PANEL_UI | 0xFF000000, Theme.ROW, a));
		CheckBox.draw(c, "obm" + key, x + 8, y + 5.5F, 11, on, hov);
		Gfx.textMid(c, name, x + 26, y, 22, on ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, a));
		if (!note.isEmpty()) {
			Gfx.textRaw(c, note, x + w - 10 - Gfx.twRaw(note), y + 7, 0xFF6B7080);
		}
		region(x, y, w, 22, pick);
	}

	/** Esc menu (3 looks) and, if Mod Menu is installed, which mods screen the mods list opens. */
	private void menusStep(GuiGraphicsExtractor c, int x, int y, int w) {
		String[] names = {"Caos", tr("Classic + Caos", "Clássico + Caos"), tr("Minecraft's own", "O do Minecraft")};
		String[] notes = {tr("simple, centred", "simples, ao centro"), tr("your player + Essential / Mod Menu buttons", "o teu jogador + botões do Essential / Mod Menu"),
				tr("no changes", "sem alterações")};
		for (int i = 0; i < names.length; i++) {
			final int idx = i;
			menuRow(c, "p" + i, x, y + i * 24, w, names[i], notes[i], pause == i, () -> pause = idx);
		}
		if (ModMenuLink.present()) {
			int my = y + names.length * 24 + 8;
			Gfx.textRaw(c, tr("Mods list", "Lista de mods"), x + 4, my, Theme.DIM);
			String[] mn = {tr("Caos's own", "A do Caos"), "Mod Menu"};
			String[] mt = {tr("on/off switches, settings", "ligar/desligar, definições"), tr("the original one", "o original")};
			for (int i = 0; i < mn.length; i++) {
				final int idx = i;
				menuRow(c, "m" + i, x, my + 12 + i * 24, w, mn[i], mt[i], modsScr == i, () -> modsScr = idx);
			}
		}
	}

	private void extras(GuiGraphicsExtractor c, int x0, int y, int w0) {
		String[][] rows = {
				{tr("Cinematic intro and home video", "Intro cinematográfica e vídeo no início"), "intro"},
				{tr("Christmas / Halloween decorations", "Decorações de Natal / Halloween"), "seasonal"},
				{tr("Menu music", "Música no menu"), "music"},
				{tr("Animated capes (GIFs / videos)", "Capas animadas (GIFs / vídeos)"), "capes"},
				{tr("Show the Caos logo next to names", "Mostrar o logo do Caos ao lado dos nomes"), "badge"}};
		int x = x0, w = w0, yy = y;
		for (String[] r : rows) {
			boolean on = switch (r[1]) {
				case "intro" -> intro;
				case "seasonal" -> seasonal;
				case "music" -> music;
				case "capes" -> animatedCapes;
				default -> badge;
			};
			boolean hov = hovered(x, yy, w, 24);
			float a = anim("obx" + r[1], hov);
			if (a > 0.01F) {
				Gfx.rrect(c, x, yy, w, 24, 4, Gfx.withAlpha(0xFFFFFF, Math.round(0x0A * a)));
			}
			CheckBox.draw(c, "obx" + r[1], x + 6, yy + 6.5F, 11, on, hov);
			Gfx.textMid(c, r[0], x + 24, yy, 24, on ? Theme.TEXT : Theme.DIM);
			region(x, yy, w, 24, () -> {
				switch (r[1]) {
					case "intro" -> intro = !intro;
					case "seasonal" -> seasonal = !seasonal;
					case "music" -> music = !music;
					case "capes" -> animatedCapes = !animatedCapes;
					default -> badge = !badge;
				}
			});
			yy += 26;
		}
	}

	private static List<String> wrap(String s, int maxW) {
		List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String w : s.split(" ")) {
			String t = line.length() == 0 ? w : line + " " + w;
			if (Gfx.twRaw(t) > maxW && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(w);
			} else {
				line = new StringBuilder(t);
			}
			if (out.size() == 4) {
				break;
			}
		}
		if (line.length() > 0 && out.size() < 5) {
			out.add(line.toString());
		}
		return out;
	}

	// ------------------------------------------------------------------ apply
	private void applyLanguage() {
		var s = SolClient.settings;
		if (s != null && s.language.idx() != lang) {
			s.language.set(lang);
		}
	}

	static void set(Class<? extends Mod> cls, boolean on) {
		for (Mod m : SolClient.MODS) {
			if (cls.isInstance(m) && !m.locked && m.enabled != on) {
				m.setEnabled(on);
			}
		}
	}

	private void finish() {
		var s = SolClient.settings;
		applyLanguage();
		// performance + HUD: the same code as Settings > Setup, and the choice is kept there
		if (s != null) {
			s.perfPreset.set(perf);
			s.hudPreset.set(hud);
		} else {
			Presets.performance(perf);
			Presets.hud(hud);
		}
		if (s != null) {
			s.customPause.set(pause != 2);
			if (pause != 2) {
				s.pauseStyle.set(pause);
			}
			s.modsScreen.set(modsScr);
		}
		// extras
		set(CinematicIntroMod.class, intro);
		if (s != null) {
			s.seasonalBackground.set(seasonal);
			s.seasonalMusic.set(music);
			s.animatedCapes.set(animatedCapes);
			s.nameBadge.set(badge);
			s.onboarded.set(true);
		}
		Config.save();
		dev.solclient.compat.Mc.setScreen(mc, next);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == 256) {
			if (step > 0) {
				step--;
			}
			return true;
		}
		return super.keyPressed(input);
	}
}
