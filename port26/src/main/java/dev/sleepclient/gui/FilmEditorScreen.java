package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.fightcam.FilmEdit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Shown when a film has been rendered: add sound effects and an AI voice-over, then press Save. (Skip keeps the film as it is.)
 * The AI voice needs your own keys: one for the language model (Groq / ChatGPT / Gemini / Claude) and one for Fish Audio, plus the
 * link of the Fish voice you want. Keys stay on this computer (config/sleep/film-ai.json).
 */
public final class FilmEditorScreen extends Screen {

	private static final class Field {
		String text;
		final boolean secret;

		Field(String text, boolean secret) {
			this.text = text == null ? "" : text;
			this.secret = secret;
		}
	}

	private record Btn(int x, int y, int w, int h, Runnable run) {
	}

	private record Box(int x, int y, int w, int h, Field f) {
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final FilmEdit.Info info = FilmEdit.last;
	private final FilmEdit.Config cfg = FilmEdit.load();
	private final Field llmKey, fishKey, voice;
	private final List<Btn> buttons = new ArrayList<>();
	private final List<Box> boxes = new ArrayList<>();
	private Field focus;
	private volatile String status = "";
	private volatile boolean busy;
	private List<String> sfxFiles = new ArrayList<>();

	public FilmEditorScreen() {
		super(Component.literal("Film editor"));
		llmKey = new Field(cfg.llmKey, true);
		fishKey = new Field(cfg.fishKey, true);
		voice = new Field(cfg.voice, false);
	}

	@Override
	protected void init() {
		sfxFiles = FilmEdit.sfxFiles();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void store() {
		cfg.llmKey = llmKey.text.trim();
		cfg.fishKey = fishKey.text.trim();
		cfg.voice = voice.text.trim();
		FilmEdit.save(cfg);
	}

	// ------------------------------------------------------------------ widgets
	private void btn(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean on, int onColour, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		Gfx.rrect(c, x, y, w, h, 4, on ? onColour : hov ? 0xFF2F3447 : 0xFF222634);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, 0xFFFFFFFF);
		buttons.add(new Btn(x, y, w, h, run));
	}

	private void toggle(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, String label, boolean on, Runnable flip) {
		btn(c, mx, my, x, y, w, 18, (on ? "ON   " : "OFF  ") + label, on, 0xFF2E8B57, flip);
	}

	private void tabs(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, String[] names, int sel, IntConsumer set) {
		int each = (w - (names.length - 1) * 3) / names.length;
		for (int i = 0; i < names.length; i++) {
			final int k = i;
			btn(c, mx, my, x + i * (each + 3), y, each, 18, names[i], i == sel, 0xFF3B6FD4, () -> set.accept(k));
		}
	}

	private void field(GuiGraphicsExtractor c, int x, int y, int w, String label, Field f, String hint) {
		Gfx.text(c, label, x, y + 5, 0xB0FFFFFF);
		int lw = 78, fx = x + lw, fw = w - lw;
		boolean on = focus == f;
		Gfx.rrect(c, fx, y, fw, 18, 4, on ? 0xFF2A3350 : 0xFF1A1E2B);
		String shown;
		if (f.text.isEmpty()) {
			shown = hint;
		} else if (f.secret && f.text.length() > 4) {
			shown = "*".repeat(Math.min(24, f.text.length() - 4)) + f.text.substring(f.text.length() - 4);
		} else {
			shown = f.text;
		}
		String clip = Gfx.tw(shown) > fw - 8 ? "..." + shown.substring(Math.max(0, shown.length() - 22)) : shown;
		Gfx.text(c, clip + (on && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""), fx + 4, y + 5, f.text.isEmpty() ? 0x60FFFFFF : 0xFFFFFFFF);
		boxes.add(new Box(fx, y, fw, 18, f));
	}

	private String cycle(String cur, int dir) {
		if (sfxFiles.isEmpty()) {
			return "";
		}
		int i = sfxFiles.indexOf(cur);
		i = i < 0 ? (dir > 0 ? 0 : sfxFiles.size() - 1) : i + dir;
		return i < 0 || i >= sfxFiles.size() ? "" : sfxFiles.get(i);
	}

	// ------------------------------------------------------------------ frame
	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		buttons.clear();
		boxes.clear();
		c.fill(0, 0, width, height, 0xD00A0C12);
		int pw = Math.min(width - 12, 560), ph = Math.min(height - 12, 300);
		int px = (width - pw) / 2, py = (height - ph) / 2;
		Gfx.rrect(c, px, py, pw, ph, 8, 0xF0141824);
		String t = info == null || info.file == null ? "" : "   " + info.file.getName() + "  (" + Math.round(info.seconds) + " s)";
		Gfx.text(c, "Edit your film" + t, px + 12, py + 9, 0xFFFFFFFF);

		int colW = (pw - 36) / 2, lx = px + 12, rx = px + 24 + colW, y0 = py + 26, rh = 22;
		// ---- AI voice
		Gfx.text(c, "AI voice-over", lx, y0, 0xFF7C8DFF);
		int y = y0 + 12;
		toggle(c, mx, my, lx, y, colW, "AI commentary", cfg.ai, () -> cfg.ai = !cfg.ai);
		y += rh;
		tabs(c, mx, my, lx, y, colW, FilmEdit.PROVIDERS, cfg.provider, k -> cfg.provider = k);
		y += rh;
		field(c, lx, y, colW, "AI key", llmKey, "paste your " + FilmEdit.PROVIDERS[cfg.provider] + " key");
		y += rh;
		field(c, lx, y, colW, "Fish key", fishKey, "paste your Fish Audio key");
		y += rh;
		field(c, lx, y, colW, "Voice link", voice, "fish.audio/m/... link");
		y += rh;
		tabs(c, mx, my, lx, y, colW, FilmEdit.LANGS, cfg.lang, k -> cfg.lang = k);
		y += rh;
		tabs(c, mx, my, lx, y, colW, FilmEdit.STYLES, cfg.style, k -> cfg.style = k);
		y += rh;
		toggle(c, mx, my, lx, y, colW, "real nicknames in the text", cfg.realNames, () -> cfg.realNames = !cfg.realNames);

		// ---- sound effects
		Gfx.text(c, "Sound effects", rx, y0, 0xFF7CFF9B);
		y = y0 + 12;
		String[] names = {"On every hit", "On the kill", "Slow motion"};
		boolean[] ons = {cfg.sfxHit, cfg.sfxKill, cfg.sfxSlow};
		String[] files = {cfg.hitFile, cfg.killFile, cfg.slowFile};
		for (int i = 0; i < 3; i++) {
			final int k = i;
			toggle(c, mx, my, rx, y, colW, names[i], ons[i], () -> {
				switch (k) {
					case 0 -> cfg.sfxHit = !cfg.sfxHit;
					case 1 -> cfg.sfxKill = !cfg.sfxKill;
					default -> cfg.sfxSlow = !cfg.sfxSlow;
				}
			});
			y += 20;
			String shownFile = files[i] == null || files[i].isEmpty() ? "(no file chosen: click to pick)" : files[i];
			btn(c, mx, my, rx + 10, y, colW - 10, 16, "< " + shownFile + " >", false, 0, () -> {
				String next = cycle(files[k], 1);
				switch (k) {
					case 0 -> cfg.hitFile = next;
					case 1 -> cfg.killFile = next;
					default -> cfg.slowFile = next;
				}
			});
			y += 22;
		}
		btn(c, mx, my, rx, y, colW, 18, "Open the sound-effects folder", false, 0, () -> net.minecraft.util.Util.getPlatform().openFile(FilmEdit.sfxDir()));
		y += 22;
		String hint = sfxFiles.isEmpty() ? "Put your .mp3 / .wav files in that folder (for example a \"fah\" sound), then reopen." : sfxFiles.size() + " sound(s) found.";
		Gfx.text(c, mc.font.plainSubstrByWidth(hint, colW), rx, y, 0x80FFFFFF);

		// ---- bottom
		int by = py + ph - 30;
		btn(c, mx, my, px + 12, by, 120, 22, busy ? "Working..." : "Save video", false, 0, () -> {
			if (!busy) {
				save();
			}
		});
		btn(c, mx, my, px + 138, by, 110, 22, "Skip (keep film)", false, 0, () -> dev.sleepclient.compat.Mc.setScreen(mc, new FilmStudioScreen(info == null ? null : info.file)));
		Gfx.text(c, mc.font.plainSubstrByWidth(status, pw - 270), px + 258, by + 7, status.startsWith("Error") ? 0xFFFF8A80 : 0xFFB9E8C0);
	}

	private void save() {
		store();
		FilmEdit.Info in = info;
		if (in == null || in.file == null) {
			status = "Error: there is no film to edit";
			return;
		}
		busy = true;
		status = "Starting...";
		final FilmEdit.Config c = cfg;
		Thread th = new Thread(() -> {
			try {
				File out = FilmEdit.compose(in, c, s -> status = s);
				mc.execute(() -> dev.sleepclient.compat.Mc.setScreen(mc, new FilmStudioScreen(out)));
			} catch (Exception e) {
				status = "Error: " + e.getMessage();
			} finally {
				busy = false;
			}
		}, "Sleep-Film-Edit");
		th.setDaemon(true);
		th.start();
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() != 0) {
			return true;
		}
		double mx = click.x(), my = click.y();
		focus = null;
		for (Box b : boxes) {
			if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
				focus = b.f();
				return true;
			}
		}
		for (int i = buttons.size() - 1; i >= 0; i--) {
			Btn b = buttons.get(i);
			if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
				b.run().run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (focus != null) {
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (!focus.text.isEmpty()) {
					focus.text = focus.text.substring(0, focus.text.length() - 1);
				}
				return true;
			}
			if (k == GLFW.GLFW_KEY_V && (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
				String clip = GLFW.glfwGetClipboardString(GLFW.glfwGetCurrentContext());
				if (clip != null) {
					focus.text += clip.replaceAll("[\\r\\n\\s]+", "");
				}
				return true;
			}
			if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_ESCAPE) {
				focus = null;
				return true;
			}
			return true;
		}
		if (k == GLFW.GLFW_KEY_ESCAPE) {
			store();
			dev.sleepclient.compat.Mc.setScreen(mc, new FilmStudioScreen(info == null ? null : info.file));
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (focus != null) {
			String s = new String(Character.toChars(input.codepoint()));
			if (!s.isBlank()) {
				focus.text += s;
			}
			return true;
		}
		return false;
	}
}
