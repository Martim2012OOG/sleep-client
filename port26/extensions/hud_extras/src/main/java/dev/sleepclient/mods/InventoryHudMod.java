package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Your main inventory (the 27 slots above the hotbar) always on screen. */
public class InventoryHudMod extends HudMod {

	private final Option.Bool background = bool("Background", true);
	private final Option.Col bgColour = colour("Background Colour", 0x80000000);
	private final Option.Bool slots = bool("Slot Outlines", true);
	private final Option.Bool hideEmpty = bool("Hide When Empty", true);

	public InventoryHudMod() {
		super("Inventory HUD", "inventory_hud", "Shows your inventory on screen at all times.", 0.5F, 0.80F);
	}

	@Override
	public int width() {
		return 9 * 18 + 4;
	}

	@Override
	public int height() {
		return 3 * 18 + 4;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		boolean any = false;
		if (mc.player != null) {
			var inv = mc.player.getInventory();
			for (int i = 9; i < 36; i++) {
				if (!inv.getItem(i).isEmpty()) {
					any = true;
					break;
				}
			}
		}
		if (!edit && hideEmpty.on() && !any) {
			return;
		}
		if (background.on()) {
			c.fill(0, 0, width(), height(), bgColour.c());
		}
		for (int i = 0; i < 27; i++) {
			int x = 2 + (i % 9) * 18, y = 2 + (i / 9) * 18;
			if (slots.on()) {
				c.fill(x, y, x + 18, y + 1, 0x30FFFFFF);
				c.fill(x, y, x + 1, y + 18, 0x30FFFFFF);
			}
			ItemStack st = mc.player != null ? mc.player.getInventory().getItem(9 + i) : ItemStack.EMPTY;
			if (edit && !any && (i == 0 || i == 4 || i == 13 || i == 26)) {
				st = new ItemStack(i == 0 ? Items.DIAMOND_SWORD : i == 4 ? Items.GOLDEN_APPLE : i == 13 ? Items.COBBLESTONE : Items.ENDER_PEARL, i == 13 ? 64 : 1);
			}
			if (!st.isEmpty()) {
				c.item(st, x + 1, y + 1);
				c.itemDecorations(mc.font, st, x + 1, y + 1);
			}
		}
	}
}
