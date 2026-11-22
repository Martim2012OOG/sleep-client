package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.fightcam.FilmEdit;
import java.io.File;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The film editor: the film plays on the left (with the hits, the kill and the slow motion marked on the timeline), sound effects and
 * the AI voice-over are set on the right, and Save mixes them in. With nothing turned on, the film is already saved as it is.
 * The AI voice needs your own keys (a language model + Fish Audio) and the link of the Fish voice; they stay in config/sleep/film-ai.json.
 */
public final class FilmEditorScreen extends UiScreen {

	private static final class Field {
		String text;
		final boolean secret;

		Field(String text, boolean secret) {
			this.text = text == null ? "" : text;
			this.secret = secret;
		}
	}

	private final FilmEdit.Info info = FilmEdit.last;
	private final FilmEdit.Config cfg = FilmEdit.load();
	private final Field llmKey, fishKey, voice;
	private Field focus;
	private int tab; // 0 sound effects, 1 AI voice
	private volatile String status = "";
	private volatile boolean busy;
	private List<String> sfxFiles = List.of();
	private FilmPreview preview;

	public FilmEditorScreen() {
		super("Film editor");
		llmKey = new Field(cfg.llmKey, true);
		fishKey = new Field(cfg.fishKey, true);
		voice = new Field(cfg.voice, false);
		tab = cfg.ai ? 1 : 0;
	}

	@Override
	protected void init() {
		sfxFiles = FilmEdit.sfxFiles();
		if (preview == null && info != null && info.file != null && info.file.isFile()) {
			int pw = 480, ph = info.width > 0 && info.height > 0 ? Math.round(pw * (float) info.height / info.width) & ~1 : 270;
			preview = new FilmPreview(info.file, pw, ph, info.seconds);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		if (preview != null) {
			preview.close();
			preview = null;
		}
	}

	private void store() {
		cfg.llmKey = llmKey.text.trim();
		cfg.fishKey = fishKey.text.trim();
		cfg.voice = voice.text.trim();
		FilmEdit.save(cfg);
	}

	private boolean anyEffect() {
		return cfg.ai || cfg.sfxHit && !cfg.hitFile.isEmpty() || cfg.sfxKill && !cfg.killFile.isEmpty() || cfg.sfxSlow && !cfg.slowFile.isEmpty();
	}

	// ------------------------------------------------------------------ widgets
	private void btn(GuiGraphicsExtractor c, int x, int y, int w, int h, String label, boolean on, int onColour, Runnable run) {
		boolean hov = hovered(x, y, w, h);
		Gfx.rrect(c, x, y, w, h, 4, on ? onColour : hov ? 0xFF2F3447 : 0xFF222634);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, 0xFFFFFFFF);
		region(x, y, w, h, run);
	}

	private void toggle(GuiGraphicsExtractor c, int x, int y, int w, String label, boolean on, Runnable flip) {
		boolean hov = hovered(x, y, w, 18);
		Gfx.rrect(c, x, y, w, 18, 4, hov ? 0xFF2F3447 : 0xFF222634);
		int sx = x + w - 30;
		Gfx.rrect(c, sx, y + 4, 24, 10, 5, on ? 0xFF2E8B57 : 0xFF3A3F52);
		Gfx.circle(c, on ? sx + 18 : sx + 6, y + 9, 4, 0xFFFFFFFF);
		Gfx.text(c, label, x + 7, y + 5, 0xFFFFFFFF);
		region(x, y, w, 18, flip);
	}

	private void tabs(GuiGraphicsExtractor c, int x, int y, int w, String[] names, int sel, IntConsumer set) {
		int each = (w - (names.length - 1) * 3) / names.length;
		for (int i = 0; i < names.length; i++) {
			final int k = i;
			btn(c, x + i * (each + 3), y, each, 18, names[i], i == sel, 0xFF3B6FD4, () -> set.accept(k));
		}
	}

	private void field(GuiGraphicsExtractor c, int x, int y, int w, String label, Field f, String hint) {
		Gfx.text(c, label, x, y + 5, 0xB0FFFFFF);
		int lw = 56, fx = x + lw, fw = w - lw;
		boolean on = focus == f;
		Gfx.rrect(c, fx, y, fw, 18, 4, on ? 0xFF2A3350 : 0xFF1A1E2B);
		String shown;
		if (f.text.isEmpty()) {
			shown = hint;
		} else if (f.secret && f.text.length() > 4) {
			shown = "*".repeat(Math.min(18, f.text.length() - 4)) + f.text.substring(f.text.length() - 4);
		} else {
			shown = f.text;
		}
		String clip = fit(shown, fw - 10);
		Gfx.text(c, clip + (on && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""), fx + 5, y + 5, f.text.isEmpty() ? 0x60FFFFFF : 0xFFFFFFFF);
		region(fx, y, fw, 18, () -> focus = f);
	}

	private String cycle(String cur) {
		if (sfxFiles.isEmpty()) {
			return "";
		}
		int i = sfxFiles.indexOf(cur);
		return i + 1 >= sfxFiles.size() ? (i < 0 ? sfxFiles.get(0) : "") : sfxFiles.get(i + 1);
	}

	private static String clock(double s) {
		int t = (int) Math.max(0, Math.round(s));
		return t / 60 + ":" + String.format("%02d", t % 60);
	}

	// ------------------------------------------------------------------ frame
	@Override
	protected void draw(GuiGraphicsExtractor c, float delta) {
		c.fill(0, 0, width, height, 0xB0070910);
		int pw = Math.min(width - 16, 610), ph = Math.min(height - 16, 372);
		int px = (width - pw) / 2, py = (height - ph) / 2;
		Gfx.rrect(c, px, py, pw, ph, 8, 0xF2121520);
		Gfx.text(c, "Film editor", px + 14, py + 11, 0xFFFFFFFF);
		if (info != null && info.file != null) {
			String n = fit(info.file.getName() + "  (" + clock(info.seconds) + ")", pw - 120);
			Gfx.text(c, n, px + pw - 14 - Gfx.tw(n), py + 11, 0x80FFFFFF);
		}

		// ---- left: the film
		int lx = px + 14, ly = py + 30, lw = Math.min(330, pw / 2 + 20);
		float aspect = info != null && info.width > 0 && info.height > 0 ? (float) info.height / info.width : 0.5625F;
		int vh = Math.min(Math.round(lw * aspect), ph - 120);
		if (preview != null) {
			preview.draw(c, lx, ly, lw, vh);
			region(lx, ly, lw, vh, preview::toggle);
			if (!preview.playing()) {
				playIcon(c, lx + lw / 2 - 8, ly + vh / 2 - 10, 20, 0xCCFFFFFF);
			}
		} else {
			Gfx.rrect(c, lx, ly, lw, vh, 4, 0xFF000000);
			Gfx.textC(c, "No film to show", lx + lw / 2, ly + vh / 2 - 4, 0x80FFFFFF);
		}
		// timeline with the moments of the fight
		int ty = ly + vh + 8;
		double len = preview == null ? 1 : preview.length();
		Gfx.rrect(c, lx, ty, lw, 10, 4, 0xFF1A1E2B);
		if (preview != null) {
			int fillW = (int) Math.round(lw * Math.min(1, preview.position() / len));
			Gfx.rrect(c, lx, ty, Math.max(4, fillW), 10, 4, 0xFF3B6FD4);
			region(lx, ty - 3, lw, 16, () -> {
				double k = Math.max(0, Math.min(1, (mouseX - lx) / (double) lw));
				preview.seek(k * len);
			});
		}
		if (info != null) {
			for (FilmEdit.Ev e : info.events) {
				int mx = lx + (int) Math.round(lw * Math.min(1, e.sec() / Math.max(0.1, info.seconds)));
				int col = e.kind().equals("kill") ? 0xFFFFC94A : e.kind().equals("slow") ? 0xFF6AB7FF : 0xFFFF6B6B;
				c.fill(mx, ty - 2, mx + 2, ty + 12, col);
			}
		}
		int cy = ty + 18;
		btn(c, lx, cy, 62, 18, preview != null && preview.playing() ? "Pause" : "Play", false, 0, () -> {
			if (preview != null) {
				preview.toggle();
			}
		});
		Gfx.text(c, clock(preview == null ? 0 : preview.position()) + " / " + clock(info == null ? 0 : info.seconds), lx + 70, cy + 5, 0xB0FFFFFF);
		btn(c, lx + lw - 118, cy, 118, 18, "Watch with sound", false, 0, () -> {
			if (info != null && info.file != null) {
				net.minecraft.util.Util.getPlatform().openFile(info.file);
			}
		});
		Gfx.text(c, "red = hit   gold = kill   blue = slow motion", lx, cy + 26, 0x70FFFFFF);
		Gfx.text(c, "The preview has no sound. The film is already saved.", lx, cy + 38, 0x70FFFFFF);

		// ---- right: effects
		int rx = lx + lw + 16, rw = px + pw - 14 - rx, y = py + 30;
		tabs(c, rx, y, rw, new String[] {"Sound effects", "AI voice"}, tab, k -> {
			tab = k;
			focus = null;
		});
		y += 26;
		if (tab == 0) {
			String[] names = {"On every hit", "On the kill", "Slow motion"};
			boolean[] ons = {cfg.sfxHit, cfg.sfxKill, cfg.sfxSlow};
			String[] files = {cfg.hitFile, cfg.killFile, cfg.slowFile};
			for (int i = 0; i < 3; i++) {
				final int k = i;
				toggle(c, rx, y, rw, names[i], ons[i], () -> {
					switch (k) {
						case 0 -> cfg.sfxHit = !cfg.sfxHit;
						case 1 -> cfg.sfxKill = !cfg.sfxKill;
						default -> cfg.sfxSlow = !cfg.sfxSlow;
					}
				});
				y += 21;
				String f = files[i] == null || files[i].isEmpty() ? "choose a sound  >" : files[i] + "  >";
				btn(c, rx + 8, y, rw - 8, 16, f, false, 0, () -> {
					String next = cycle(files[k]);
					switch (k) {
						case 0 -> cfg.hitFile = next;
						case 1 -> cfg.killFile = next;
						default -> cfg.slowFile = next;
					}
				});
				y += 22;
			}
			btn(c, rx, y, rw, 18, "Open the sounds folder", false, 0, () -> net.minecraft.util.Util.getPlatform().openFile(FilmEdit.sfxDir()));
			y += 24;
			String hint = sfxFiles.isEmpty() ? "Put your .mp3 / .wav sounds (a \"fah\", a boom...) in that folder, then press > to pick." : sfxFiles.size() + " sound(s) in the folder.";
			Gfx.text(c, fit(hint, rw), rx, y, 0x80FFFFFF);
		} else {
			toggle(c, rx, y, rw, "AI commentary", cfg.ai, () -> cfg.ai = !cfg.ai);
			y += 22;
			tabs(c, rx, y, rw, FilmEdit.PROVIDERS, cfg.provider, k -> cfg.provider = k);
			y += 22;
			field(c, rx, y, rw, "AI key", llmKey, "your " + FilmEdit.PROVIDERS[cfg.provider] + " key");
			y += 22;
			field(c, rx, y, rw, "Fish key", fishKey, "your Fish Audio key");
			y += 22;
			field(c, rx, y, rw, "Voice", voice, "fish.audio/m/... link");
			y += 22;
			tabs(c, rx, y, rw, FilmEdit.LANGS, cfg.lang, k -> cfg.lang = k);
			y += 22;
			tabs(c, rx, y, rw, FilmEdit.STYLES, cfg.style, k -> cfg.style = k);
			y += 22;
			toggle(c, rx, y, rw, "Real nicknames in the text", cfg.realNames, () -> cfg.realNames = !cfg.realNames);
		}

		// ---- bottom
		int by = py + ph - 32;
		boolean fx = anyEffect();
		btn(c, px + 14, by, 150, 22, busy ? "Working..." : fx ? "Save with effects" : "Save (as it is)", fx, 0xFF2E8B57, () -> {
			if (!busy) {
				save();
			}
		});
		btn(c, px + 170, by, 90, 22, "Close", false, 0, this::leave);
		String st = status.isEmpty() ? (fx ? "Mixes the sounds / voice into a new file next to the film." : "Nothing added: the film stays as it is.") : status;
		Gfx.text(c, fit(st, pw - 290), px + 270, by + 7, status.startsWith("Error") ? 0xFFFF8A80 : 0xFFB9E8C0);
	}

	private void leave() {
		store();
		dev.sleepclient.compat.Mc.setScreen(mc, new FilmStudioScreen(info == null ? null : info.file));
	}

	private void save() {
		store();
		FilmEdit.Info in = info;
		if (in == null || in.file == null) {
			status = "Error: there is no film to edit";
			return;
		}
		if (!anyEffect()) {
			leave();
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
	protected boolean clickedNothing(double mx, double my) {
		focus = null;
		return false;
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
			}
			return true;
		}
		if (k == GLFW.GLFW_KEY_ESCAPE) {
			leave();
			return true;
		}
		if (k == GLFW.GLFW_KEY_SPACE && preview != null) {
			preview.toggle();
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
