package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.world.item.ItemStack;

/** Choose where the enchantment shine shows. */
public class EnchantmentGlintMod extends Mod {

	public static EnchantmentGlintMod instance;
	private final Option.Choice show = choice("Show Glint On", 1, "Everything", "Items Only", "Armor Only", "Nothing");

	public EnchantmentGlintMod() {
		super("Enchantment Glint", "enchantment_glint", "Choose where the enchanted shine shows.", Category.VISUAL);
		instance = this;
	}

	public static boolean allowed(ItemStack st) {
		if (instance == null || !instance.enabled) return true;
		int m = instance.show.idx();
		if (m == 0) return true;
		if (m == 3) return false;
		boolean armor = st.has(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		return m == 1 ? !armor : armor;
	}
}
