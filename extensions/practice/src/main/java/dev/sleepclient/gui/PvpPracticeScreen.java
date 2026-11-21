package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.addons.pvp.BotDifficulty;
import dev.sleepclient.addons.pvp.KitStore;
import dev.sleepclient.addons.pvp.PracticeArena;
import dev.sleepclient.addons.pvp.PracticeKit;
import dev.sleepclient.addons.pvp.PracticeSession;
import dev.sleepclient.addons.pvp.PracticeWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Setup screen of the PvP Practice add-on: pick kit, arena, bot difficulty and number of bots, then Start. */
public class PvpPracticeScreen extends Screen {

	private final Screen parent;
	private int kitIdx;
	private int arenaIdx;
	private int diffIdx = BotDifficulty.MEDIUM.ordinal();
	private int bots = 1;
	/** built-in + custom kits (refreshed every frame, the editor can change them) */
	private List<PracticeKit> kits = PracticeKit.all();
	private long deleteArmed;

	private record Hit(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Hit> hits = new ArrayList<>();

	public PvpPracticeScreen(Screen parent) {
		super(Text.literal("PvP Practice"));
		this.parent = parent;
		PracticeKit last = PracticeSession.kit;
		if (last != null) {
			select(last.name);
		}
		PracticeArena lastArena = PracticeSession.arena;
		if (lastArena != null) {
			arenaIdx = Math.max(0, PracticeArena.ALL.indexOf(lastArena));
		}
		diffIdx = PracticeSession.difficulty.ordinal();
		bots = PracticeSession.botCount;
	}

	/** Selects the kit with this name (used when the editor saves one). */
	public void select(String kitName) {
		kits = PracticeKit.all();
		for (int i = 0; i < kits.size(); i++) {
			if (kits.get(i).name.equalsIgnoreCase(kitName)) {
				kitIdx = i;
				return;
			}
		}
		kitIdx = 0;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void chip(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, boolean sel, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = sel ? Theme.accent() : hov ? Theme.CARD_HOVER : Theme.CARD;
		Gfx.rrectBorder(c, x, y, w, h, 7, bg, sel ? Theme.accent() : hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
		Gfx.textC(c, label, x + w / 2, y + (h - 8) / 2, sel ? 0xFFFFFFFF : Theme.TEXT);
		hits.add(new Hit(x, y, w, h, run));
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta);
		hits.clear();
		kits = PracticeKit.all();
		kitIdx = Math.max(0, Math.min(kitIdx, kits.size() - 1));
		int kitRows = (kits.size() + 3) / 4, arenaRows = (PracticeArena.ALL.size() + 2) / 3;
		int need = 26 + 11 + kitRows * 24 + 15 + 11 + arenaRows * 24 + 15 + 11 + 20 + 14 + 36;
		int w = Math.min(width - 16, 400), h = Math.min(height - 16, Math.max(300, need));
		int x = (width - w) / 2, y = (height - h) / 2;
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		int ix = x + 14, iw = w - 28;

		Gfx.text(c, "PvP Practice", ix, y + 10, Theme.TEXT);
		String sub = "Private empty world, no server, no one else.";
		Gfx.text(c, sub, ix + Gfx.tw("PvP Practice") + 10, y + 10, Theme.DIM);

		int gap = 4, chh = 20;

		// kits
		int cy = y + 26;
		Gfx.text(c, "Kit", ix, cy, Theme.DIM);
		PracticeKit sel = kits.get(kitIdx);
		// kit editor buttons: New / Edit (a copy, if it is a built-in kit) / Delete (custom kits only)
		int eh = 14, ey = cy - 3;
		int dx = ix + iw - 46;
		if (sel.custom) {
			boolean armed = System.currentTimeMillis() - deleteArmed < 3000;
			chip(c, mx, my, dx, ey, 46, eh, armed ? "Sure?" : "Delete", armed, () -> {
				if (System.currentTimeMillis() - deleteArmed < 3000) {
					KitStore.delete(sel.name);
					kitIdx = 0;
					deleteArmed = 0;
				} else {
					deleteArmed = System.currentTimeMillis();
				}
			});
			dx -= 4;
		}
		dx -= 36;
		chip(c, mx, my, dx, ey, 36, eh, "Edit", false, () -> MinecraftClient.getInstance().setScreen(new KitEditorScreen(this, sel)));
		dx -= 4 + 36;
		chip(c, mx, my, dx, ey, 36, eh, "New", false, () -> MinecraftClient.getInstance().setScreen(new KitEditorScreen(this, null)));
		cy += 11;
		int cols = 4, cw = (iw - gap * (cols - 1)) / cols;
		for (int i = 0; i < kits.size(); i++) {
			final int idx = i;
			int cx = ix + (i % cols) * (cw + gap), ry = cy + (i / cols) * (chh + gap);
			chip(c, mx, my, cx, ry, cw, chh, kits.get(i).name, i == kitIdx, () -> kitIdx = idx);
		}
		int rows = (kits.size() + cols - 1) / cols;
		cy += rows * (chh + gap);
		Gfx.text(c, sel.description, ix, cy, Theme.DIM);
		cy += 15;

		// arenas
		Gfx.text(c, "Arena", ix, cy, Theme.DIM);
		cy += 11;
		int acols = 3, aw = (iw - gap * (acols - 1)) / acols;
		for (int i = 0; i < PracticeArena.ALL.size(); i++) {
			final int idx = i;
			int cx = ix + (i % acols) * (aw + gap), ry = cy + (i / acols) * (chh + gap);
			chip(c, mx, my, cx, ry, aw, chh, PracticeArena.ALL.get(i).name, i == arenaIdx, () -> arenaIdx = idx);
		}
		int arows = (PracticeArena.ALL.size() + acols - 1) / acols;
		cy += arows * (chh + gap);
		Gfx.text(c, PracticeArena.ALL.get(arenaIdx).description, ix, cy, Theme.DIM);
		cy += 15;

		// difficulty (left) + number of bots (right)
		Gfx.text(c, "Bot difficulty", ix, cy, Theme.DIM);
		int bw = 28;
		int botsX = ix + iw - (3 * bw + 2 * gap);
		Gfx.text(c, "Bots", botsX, cy, Theme.DIM);
		cy += 11;
		BotDifficulty[] ds = BotDifficulty.values();
		int dw = 54;
		for (int i = 0; i < ds.length; i++) {
			final int idx = i;
			chip(c, mx, my, ix + i * (dw + gap), cy, dw, chh, ds[i].label, i == diffIdx, () -> diffIdx = idx);
		}
		for (int i = 1; i <= 3; i++) {
			final int n = i;
			chip(c, mx, my, botsX + (i - 1) * (bw + gap), cy, bw, chh, String.valueOf(i), i == bots, () -> bots = n);
		}

		// bottom: back + start
		int by = y + h - 30, bh = 22;
		chip(c, mx, my, ix, by, 70, bh, "Back", false, () -> MinecraftClient.getInstance().setScreen(parent));
		String note = PracticeWorld.exists(PracticeWorld.PVP) ? "Loads your saved practice world." : "First time: press Create New World, only once.";
		Gfx.text(c, note, ix + 78, by + 7, Theme.DIM);
		String start = "Start";
		int sw = 80;
		int sx = ix + iw - sw;
		boolean hov = mx >= sx && mx < sx + sw && my >= by && my < by + bh;
		Gfx.rrect(c, sx, by, sw, bh, 8, hov ? Theme.accentHover() : Theme.accent());
		Gfx.textC(c, start, sx + sw / 2, by + 7, 0xFFFFFFFF);
		hits.add(new Hit(sx, by, sw, bh, this::start));
	}

	private void start() {
		PracticeSession.kit = kits.get(Math.max(0, Math.min(kitIdx, kits.size() - 1)));
		PracticeSession.arena = PracticeArena.ALL.get(arenaIdx);
		PracticeSession.difficulty = BotDifficulty.values()[diffIdx];
		PracticeSession.botCount = bots;
		PracticeWorld.go(this, PracticeWorld.PVP, "PvP Practice", PracticeSession::begin);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		for (Hit hit : new ArrayList<>(hits)) {
			if (click.x() >= hit.x && click.x() < hit.x + hit.w && click.y() >= hit.y && click.y() < hit.y + hit.h) {
				hit.run.run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) {
			MinecraftClient.getInstance().setScreen(parent);
			return true;
		}
		return false;
	}
}
