package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.resource.ResourcePackProfile;

/** Christmas World: pick which Christmas songs play, or which Christmas texture packs are used (one, the other or both). */
public class XmasPickerScreen extends UiScreen {

	private final Screen parent;
	private final boolean packs;
	private final TextBox search = new TextBox(32);
	private int scroll;
	private List<Path> songs;
	private List<ResourcePackProfile> packList;
	private boolean packsChanged;

	public XmasPickerScreen(Screen parent, boolean packs) {
		super(packs ? "Christmas packs" : "Christmas songs");
		this.parent = parent;
		this.packs = packs;
		if (packs) {
			packList = XmasChoices.packs();
		} else {
			songs = XmasChoices.songs();
		}
	}

	@Override
	public void close() {
		if (packsChanged) {
			XmasChoices.apply(dev.sleepclient.mods.ChristmasWorldMod.instance != null && dev.sleepclient.mods.ChristmasWorldMod.instance.isEnabled());
		}
		mc.setScreen(parent);
	}

	@Override
	protected void draw(DrawContext c, float delta) {
		Gfx.gradientV(c, 0, 0, width, height, 0x70000000, 0xB0000000);
		int pw = Math.min(460, width - 20), ph = Math.min(320, height - 20);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, Theme.PANEL_UI, BORDER);
		String title = packs ? dev.sleepclient.Lang.tr("Christmas texture packs", "Pacotes de texturas de Natal")
				: dev.sleepclient.Lang.tr("Christmas songs", "Músicas de Natal");
		Gfx.text(c, title, px + 14, py + 12, Theme.TEXT);
		String sub = packs ? dev.sleepclient.Lang.tr("On = used while Christmas World is on. Pick one, the other or both.",
				"Ligado = usado com o Mundo de Natal. Escolhe um, o outro ou os dois.")
				: dev.sleepclient.Lang.tr("On = can play. Files in .minecraft/plegumo-music/christmas", "Ligada = pode tocar. Ficheiros em .minecraft/plegumo-music/christmas");
		Gfx.textFit(c, sub, px + pw / 2, pw - 28, py + 26, Theme.DIM);

		// search + all / none
		int sx = px + 12, sy = py + 42, sw = pw - 24 - 2 * 62;
		Gfx.rrectBorder(c, sx, sy, sw, 20, 7, 0xFF0B0C10, search.isEmpty() ? 0x26FFFFFF : Gfx.withAlpha(Theme.accent(), 0xC0));
		Gfx.tex(c, Theme.icon2("search"), sx + 7, sy + 5, 10, 10, 64, 64, Theme.DIM);
		if (search.isEmpty()) {
			Gfx.text(c, dev.sleepclient.Lang.tr("Search...", "Procurar..."), sx + 22, sy + 6, 0xFF6C7182);
		}
		search.draw(c, sx, sy, sw, 20, sx + 22, sw - 30, !search.isEmpty(), Theme.accent(), Theme.TEXT);
		regionButtons(sx, sy, sw, 20, b -> search.click(mouseX));
		button(c, "all", px + pw - 12 - 2 * 58 - 4, sy, 58, 20, dev.sleepclient.Lang.tr("All", "Todas"), () -> setAll(true));
		button(c, "none", px + pw - 12 - 58, sy, 58, 20, dev.sleepclient.Lang.tr("None", "Nenhuma"), () -> setAll(false));

		// rows
		List<Object> rows = rows();
		int ly0 = sy + 28, ly1 = py + ph - 36, rh = 20;
		int content = rows.size() * rh;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (ly1 - ly0))));
		c.enableScissor(px + 6, ly0, px + pw - 6, ly1);
		int y = ly0 - scroll;
		int on = 0;
		for (Object r : rows) {
			boolean isOn = r instanceof Path p ? XmasChoices.songOn(p) : XmasChoices.packOn((ResourcePackProfile) r);
			if (isOn) {
				on++;
			}
			if (y + rh >= ly0 && y <= ly1) {
				int x = px + 12, w = pw - 24;
				boolean hov = hovered(x, y, w, rh) && mouseY >= ly0 && mouseY < ly1;
				if (hov) {
					Gfx.rrect(c, x, y + 1, w, rh - 2, 6, 0x12FFFFFF);
				}
				String name = r instanceof Path p ? XmasChoices.pretty(p) : ((ResourcePackProfile) r).getDisplayName().getString();
				name = name.replaceAll("§.", "");
				CheckBox.draw(c, "xp" + name, x + 6, y + 5, 10, isOn, hov);
				Gfx.text(c, fit(name, w - 30), x + 24, y + 6, isOn ? Theme.TEXT : Theme.DIM);
				final Object row = r;
				final boolean now = isOn;
				region(x, y, w, rh, () -> toggle(row, !now));
			}
			y += rh;
		}
		c.disableScissor();
		if (rows.isEmpty()) {
			Gfx.textFit(c, packs ? dev.sleepclient.Lang.tr("No Christmas packs in resourcepacks/", "Sem pacotes de Natal em resourcepacks/")
					: dev.sleepclient.Lang.tr("No songs yet: put .ogg / .mp3 in plegumo-music/christmas", "Ainda sem músicas: mete .ogg / .mp3 em plegumo-music/christmas"),
					px + pw / 2, pw - 30, ly0 + 30, Theme.DIM);
		}
		String count = on + " / " + rows.size() + (packs ? dev.sleepclient.Lang.tr(" on", " ligados") : dev.sleepclient.Lang.tr(" on", " ligadas"));
		Gfx.text(c, count, px + 14, py + ph - 24, Theme.DIM);
		button(c, "done", px + pw - 12 - 90, py + ph - 30, 90, 20, dev.sleepclient.Lang.tr("Done", "Concluído"), this::close);
	}

	private List<Object> rows() {
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		List<Object> out = new ArrayList<>();
		if (packs) {
			for (ResourcePackProfile p : packList) {
				if (q.isEmpty() || p.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(q)) {
					out.add(p);
				}
			}
		} else {
			for (Path p : songs) {
				if (q.isEmpty() || XmasChoices.pretty(p).toLowerCase(Locale.ROOT).contains(q)) {
					out.add(p);
				}
			}
		}
		return out;
	}

	private void toggle(Object r, boolean on) {
		if (r instanceof Path p) {
			XmasChoices.setSong(p, on);
		} else {
			XmasChoices.setPack((ResourcePackProfile) r, on);
			packsChanged = true;
		}
	}

	private void setAll(boolean on) {
		if (packs) {
			for (ResourcePackProfile p : packList) {
				XmasChoices.setPack(p, on);
			}
			packsChanged = true;
		} else {
			XmasChoices.allSongs(on);
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, scroll - (int) Math.round(v * 20));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 256) {
			close();
			return true;
		}
		if (search.key(k)) {
			scroll = 0;
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		search.typed(input.codepoint());
		scroll = 0;
		return true;
	}
}
