package dev.sleepclient.mods;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A little star on hotbar slots that just got new items. */
public class NewItemMarkMod extends Mod {

	public static NewItemMarkMod instance;
	private final int[] last = new int[9];
	private final long[] newUntil = new long[9];
	private boolean ready;

	public NewItemMarkMod() {
		super("New Item Mark", "new_item_mark", "Marks hotbar slots that just got new items.", Category.HUD);
		instance = this;
	}

	@Override
	public void tick() {
		if (mc.player == null) { ready = false; return; }
		var inv = mc.player.getInventory();
		long now = System.currentTimeMillis();
		for (int i = 0; i < 9; i++) {
			int n = inv.getItem(i).getCount();
			if (ready && n > last[i]) newUntil[i] = now + 8000;
			if (n == 0) newUntil[i] = 0;
			last[i] = n;
		}
		int sel = inv.getSelectedSlot();
		if (sel >= 0 && sel < 9) newUntil[sel] = Math.min(newUntil[sel], now + 600); // seen it
		ready = true;
	}

	public void draw(GuiGraphicsExtractor c) {
		if (mc.player == null || dev.sleepclient.compat.Mc.hideGui(mc)) return;
		long now = System.currentTimeMillis();
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
		for (int i = 0; i < 9; i++) {
			if (newUntil[i] > now) {
				int x = w / 2 - 91 + i * 20 + 2, y = h - 21;
				c.text(mc.font, "\u2605", x, y - 1, 0xFFFFD43B);
			}
		}
	}
}
