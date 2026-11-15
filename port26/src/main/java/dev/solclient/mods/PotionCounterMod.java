package dev.solclient.mods;

import net.minecraft.world.item.Items;

/** How many potions (drinkable, splash and lingering) are in your inventory. */
public class PotionCounterMod extends TextHudMod {

	public PotionCounterMod() {
		super("Potion Counter", "potion_counter", "Counts the potions in your inventory.", 0.02F, 0.65F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) {
			return edit ? "Potions: 12" : null;
		}
		var inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var st = inv.getItem(i);
			if (st.is(Items.POTION) || st.is(Items.SPLASH_POTION) || st.is(Items.LINGERING_POTION)) {
				n += st.getCount();
			}
		}
		return "Potions: " + n;
	}
}
