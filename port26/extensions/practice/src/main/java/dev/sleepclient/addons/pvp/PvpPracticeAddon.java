package dev.sleepclient.addons.pvp;

import dev.sleepclient.gui.PvpPracticeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import dev.sleepclient.addons.Addon;

/** Private empty world with kits to practise PvP against bots (Easy to Extreme). */
public class PvpPracticeAddon implements Addon {

	@Override
	public String id() {
		return "pvp_practice";
	}

	@Override
	public String name() {
		return "PvP Practice";
	}

	@Override
	public String description() {
		return "Private empty world with kits. Fight bots from Easy to Extreme.";
	}

	@Override
	public String icon() {
		return "addon_mace";
	}

	@Override
	public String tag() {
		return "PvP";
	}

	@Override
	public void open(Screen parent) {
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), new PvpPracticeScreen(parent));
	}
}
