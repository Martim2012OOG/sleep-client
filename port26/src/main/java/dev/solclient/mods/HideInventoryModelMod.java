package dev.solclient.mods;

/** Hide Inventory Model: no player model in your inventory screen. */
public class HideInventoryModelMod extends Mod {

	public static HideInventoryModelMod instance;

	public HideInventoryModelMod() {
		super("Hide Inventory Model", "hide_inventory_model", "Hides your player model in the inventory.", Category.VISUAL);
		instance = this;
	}

	public static boolean hides() {
		if (instance == null || !instance.enabled) {
			return false;
		}
		var s = dev.solclient.compat.Mc.screen(instance.mc);
		return s instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
				|| s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
	}
}
