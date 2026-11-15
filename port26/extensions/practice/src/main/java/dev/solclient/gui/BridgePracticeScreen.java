package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.addons.bridge.BridgeSession;
import dev.solclient.addons.pvp.PracticeWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Setup screen of the Bridge Practice add-on: pick the bridge type and length, then Start. */
public class BridgePracticeScreen extends Screen {

	private final Screen parent;
	private int modeIdx;
	private int lenIdx;

	private record Hit(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Hit> hits = new ArrayList<>();

	public BridgePracticeScreen(Screen parent) {
		super(Component.literal("Bridge Practice"));
		this.parent = parent;
		modeIdx = BridgeSession.mode.ordinal();
		lenIdx = BridgeSession.length.ordinal();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void chip(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean sel, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = sel ? Theme.accent() : hov ? Theme.CARD_HOVER : Theme.CARD;
		Gfx.rrectBorder(c, x, y, w, h, 7, bg, sel ? Theme.accent() : hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
		Gfx.textC(c, label, x + w / 2, y + (h - 8) / 2, sel ? 0xFFFFFFFF : Theme.TEXT);
		hits.add(new Hit(x, y, w, h, run));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		super.extractRenderState(c, mx, my, delta);
		hits.clear();
		int w = Math.min(width - 16, 400), h = Math.min(height - 16, 260);
		int x = (width - w) / 2, y = (height - h) / 2;
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		int ix = x + 14, iw = w - 28;

		Gfx.text(c, "Bridge Practice", ix, y + 12, Theme.TEXT);
		Gfx.text(c, "Private empty world with a timer.", ix + Gfx.tw("Bridge Practice") + 10, y + 12, Theme.DIM);

		int gap = 6, chh = 22, cy = y + 32;
		Gfx.text(c, "Bridge type", ix, cy, Theme.DIM);
		cy += 12;
		BridgeSession.Mode[] modes = BridgeSession.Mode.values();
		int cols = 3, cw = (iw - gap * (cols - 1)) / cols;
		for (int i = 0; i < modes.length; i++) {
			final int idx = i;
			chip(c, mx, my, ix + (i % cols) * (cw + gap), cy + (i / cols) * (chh + gap), cw, chh, modes[i].label, i == modeIdx, () -> modeIdx = idx);
		}
		int rows = (modes.length + cols - 1) / cols;
		cy += rows * (chh + gap);
		Gfx.text(c, modes[modeIdx].description, ix, cy, Theme.DIM);
		cy += 20;

		Gfx.text(c, "Length", ix, cy, Theme.DIM);
		cy += 12;
		BridgeSession.Length[] lens = BridgeSession.Length.values();
		int lw = (iw - gap * (lens.length - 1)) / lens.length;
		for (int i = 0; i < lens.length; i++) {
			final int idx = i;
			chip(c, mx, my, ix + i * (lw + gap), cy, lw, chh, lens[i].label, i == lenIdx, () -> lenIdx = idx);
		}
		cy += chh + 12;

		double best = BridgeSession.bestFor(modes[modeIdx], lens[lenIdx]);
		Gfx.text(c, "Your best: " + (best < 0 ? "--" : String.format(Locale.ROOT, "%.3f s", best)), ix, cy, 0xFF55FFFF);
		Gfx.text(c, "R in game = back to the start", ix + iw - Gfx.tw("R in game = back to the start"), cy, Theme.DIM);

		int by = y + h - 34, bh = 24;
		chip(c, mx, my, ix, by, 70, bh, "Back", false, () -> dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), parent));
		String note = PracticeWorld.exists(PracticeWorld.BRIDGE) ? "Loads your saved bridge world." : "First time: press Create New World, only once.";
		Gfx.text(c, note, ix + 78, by + 8, Theme.DIM);
		int sw = 80, sx = ix + iw - sw;
		boolean hov = mx >= sx && mx < sx + sw && my >= by && my < by + bh;
		Gfx.rrect(c, sx, by, sw, bh, 8, hov ? Theme.accentHover() : Theme.accent());
		Gfx.textC(c, "Start", sx + sw / 2, by + 8, 0xFFFFFFFF);
		hits.add(new Hit(sx, by, sw, bh, this::start));
	}

	private void start() {
		BridgeSession.mode = BridgeSession.Mode.values()[modeIdx];
		BridgeSession.length = BridgeSession.Length.values()[lenIdx];
		PracticeWorld.go(this, PracticeWorld.BRIDGE, "Bridge Practice", BridgeSession::begin);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		for (Hit hit : new ArrayList<>(hits)) {
			if (click.x() >= hit.x && click.x() < hit.x + hit.w && click.y() >= hit.y && click.y() < hit.y + hit.h) {
				hit.run.run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == 256) {
			dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), parent);
			return true;
		}
		return false;
	}
}
