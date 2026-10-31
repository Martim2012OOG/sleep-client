package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.SoundLib;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

/**
 * "Pick a sound": every sound of the game and of your resource packs in one list, with a search box and a play
 * button on each row to listen before you pick. Your own .wav files (kubo-sounds folder) are at the top.
 */
public class SoundPickerScreen extends UiScreen {

	private static final int ROW = 20;

	private final Screen parent;
	private final String heading;
	private final String noneLabel;
	private final boolean withSilent;
	private final Consumer<String> onPick;
	private String current;
	private final TextBox search = new TextBox(48);
	private List<String> shown = new ArrayList<>();
	private String lastQuery;
	private String lastSig = "";
	private float scroll;

	public SoundPickerScreen(Screen parent, String heading, String current, String noneLabel, boolean withSilent, Consumer<String> onPick) {
		super("Pick a sound");
		this.parent = parent;
		this.heading = heading;
		this.current = current == null ? "" : current;
		this.noneLabel = noneLabel;
		this.withSilent = withSilent;
		this.onPick = onPick;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Re-filters the list when the search text or the list itself changed. */
	private void refilter() {
		List<String> all = new ArrayList<>();
		if (withSilent) {
			all.add(SoundLib.SILENT); // "Silent" on purpose, at the top
		}
		all.addAll(SoundLib.allSounds());
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		String sig = all.size() + ":" + (all.isEmpty() ? "" : all.get(0));
		if (q.equals(lastQuery) && sig.equals(lastSig)) {
			return;
		}
		lastQuery = q;
		lastSig = sig;
		scroll = 0;
		List<String> out = new ArrayList<>();
		for (String s : all) {
			if (q.isEmpty() || SoundLib.pretty(s).toLowerCase(Locale.ROOT).contains(q) || s.toLowerCase(Locale.ROOT).contains(q)) {
				out.add(s);
			}
		}
		shown = out;
	}

	@Override
	protected void draw(DrawContext c, float delta) {
		refilter();
		c.fill(0, 0, width, height, 0x99000000);
		int w = Math.min(width - 20, 320), h = Math.min(height - 20, 340);
		int x = (width - w) / 2, y = (height - h) / 2;
		Gfx.rrectBorder(c, x, y, w, h, 10, TILE, BORDER);
		Gfx.textC(c, heading, x + w / 2, y + 12, Theme.TEXT);

		// search box
		int sx = x + 12, sy = y + 30, sw = w - 24, sh = 22;
		Gfx.rrectBorder(c, sx, sy, sw, sh, 6, Theme.FIELD, Theme.accent());
		if (search.isEmpty()) {
			Gfx.textMid(c, "Search every sound...", sx + 9, sy, sh, Theme.DIM);
		}
		search.draw(c, sx, sy, sw, sh, sx + 9, sw - 36, true, Theme.accent(), Theme.TEXT);
		if (!search.isEmpty()) {
			boolean hov = hovered(sx + sw - 22, sy, 22, sh);
			Gfx.textC(c, "x", sx + sw - 11, sy + 7, hov ? Theme.TEXT : Theme.DIM);
			region(sx + sw - 22, sy, 22, sh, search::clear);
		}

		// footer
		int bh = 22, by = y + h - 12 - bh;
		int hintY = by - 14;
		int ly = sy + sh + 8, lb = hintY - 4;
		int listH = Math.max(ROW, lb - ly);

		// list
		int n = shown.size();
		float maxScroll = Math.max(0, n * ROW - listH);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int lx = x + 12, lw = w - 24 - (maxScroll > 0 ? 8 : 0);
		Gfx.rrect(c, lx - 2, ly - 2, w - 20, listH + 4, 6, Theme.FIELD);
		c.enableScissor(lx - 2, ly, lx + w - 22, ly + listH);
		int first = Math.max(0, (int) (scroll / ROW));
		for (int i = first; i < n; i++) {
			int ry = ly + i * ROW - (int) scroll;
			if (ry >= ly + listH) {
				break;
			}
			String ref = shown.get(i);
			boolean sel = ref.equals(current);
			boolean hov = hovered(lx, Math.max(ry, ly), lw, Math.min(ry + ROW, ly + listH) - Math.max(ry, ly)) && mouseY >= ly && mouseY < ly + listH;
			if (sel || hov) {
				Gfx.rrect(c, lx, ry + 1, lw, ROW - 2, 5, sel ? Gfx.withAlpha(Theme.accent(), 0x55) : TILE_HOVER);
			}
			boolean playHov = hov && mouseX < lx + 24;
			playIcon(c, lx + 8, ry + 6, 8, playHov ? Theme.accent() : Theme.DIM);
			String label = SoundLib.pretty(ref);
			if (ref.startsWith(SoundLib.CUSTOM)) {
				label = "\u2605 " + label;
			}
			Gfx.textMid(c, fit(label, lw - 34), lx + 28, ry, ROW, sel ? Theme.accent() : Theme.TEXT);

			int vy = Math.max(ry, ly), vh = Math.min(ry + ROW, ly + listH) - vy;
			if (vh > 0) {
				region(lx, vy, lw, vh, () -> choose(ref));
				region(lx, vy, 24, vh, () -> SoundLib.play(ref, 0.8F, 1F)); // registered last: wins over the row
			}
		}
		c.disableScissor();
		if (n == 0) {
			Gfx.textC(c, "No sound found", x + w / 2, ly + listH / 2 - 4, Theme.DIM);
		}
		if (maxScroll > 0) {
			int tx = x + w - 18;
			Gfx.rrect(c, tx, ly, 4, listH, 2, 0x22FFFFFF);
			int th = Math.max(16, Math.round(listH * (listH / (float) (n * ROW))));
			int ty = ly + Math.round((listH - th) * (scroll / maxScroll));
			Gfx.rrect(c, tx, ty, 4, th, 2, Theme.accent());
		}

		// hint + buttons
		boolean noOwn = SoundLib.customFiles().isEmpty();
		Gfx.textC(c, noOwn ? "Own sound: click \"Add own sound\" and drop a .wav in the folder" : n + " sounds  \u2022  \u2605 = your own", x + w / 2, hintY, Theme.DIM);
		int gap = 6, bw = (w - 24 - gap * 2) / 3;
		button(c, "sp_cancel", x + 12, by, bw, bh, "Cancel", this::close);
		button(c, "sp_none", x + 12 + bw + gap, by, bw, bh, noneLabel, () -> choose(""));
		button(c, "sp_add", x + 12 + (bw + gap) * 2, by, bw, bh, "Add own sound", SoundLib::openFolder);
	}

	private void choose(String ref) {
		current = ref;
		onPick.accept(ref);
		close();
	}

	// ------------------------------------------------------------------------------------------ input
	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * ROW * 2;
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 256) {
			close();
			return true;
		}
		search.key(k);
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		int cp = input.codepoint();
		if (cp >= 32 && cp != 127) {
			search.typed(cp);
		}
		return true;
	}

	/** A .wav dragged onto the window while the picker is open: it is added and picked. (No @Override on purpose.) */
	public void onFilesDropped(List<java.nio.file.Path> paths) {
		if (paths == null || paths.isEmpty()) {
			return;
		}
		String ref = SoundLib.importFile(paths.get(0));
		if (ref != null) {
			choose(ref);
		}
	}

	@Override
	public void close() {
		mc.setScreen(parent);
	}
}
