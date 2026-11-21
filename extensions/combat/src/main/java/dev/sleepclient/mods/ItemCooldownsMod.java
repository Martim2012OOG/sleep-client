package dev.sleepclient.mods;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Cooldowns of ender pearls, shields, chorus fruit, wind charges and more. */
public class ItemCooldownsMod extends TextHudMod {

	private static final net.minecraft.item.Item[] WATCH = {Items.ENDER_PEARL, Items.SHIELD, Items.CHORUS_FRUIT, Items.WIND_CHARGE, Items.GOAT_HORN, Items.TRIDENT};

	public ItemCooldownsMod() {
		super("Item Cooldowns", "item_cooldowns", "Cooldowns of pearls, shields and more.", 0.01F, 0.80F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "Ender Pearl 40%" : null;
		var cd = mc.player.getItemCooldownManager();
		StringBuilder b = new StringBuilder();
		for (var it : WATCH) {
			ItemStack st = new ItemStack(it);
			if (cd.isCoolingDown(st)) {
				if (b.length() > 0) b.append("  ");
				b.append(st.getName().getString()).append(' ').append(Math.round(cd.getCooldownProgress(st, 0F) * 100)).append('%');
			}
		}
		return b.length() == 0 ? (edit ? "Ender Pearl 40%" : null) : b.toString();
	}
}
