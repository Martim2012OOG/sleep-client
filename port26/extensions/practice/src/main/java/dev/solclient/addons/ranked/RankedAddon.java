package dev.solclient.addons.ranked;

import dev.solclient.Lang;
import dev.solclient.addons.Addon;
import dev.solclient.gui.RankedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Add-ons > Ranked PvP: tiers (HT1 ... LT5), ranked 1v1 / 2v2, duels with friends and the rankings. */
public class RankedAddon implements Addon {

	@Override
	public String id() {
		return "ranked_pvp";
	}

	@Override
	public String name() {
		return "Ranked PvP";
	}

	@Override
	public String description() {
		return Lang.tr("Ranked 1v1 / 2v2 with tiers (HT1 to LT5), duels with friends and the rankings. 15 arenas.",
				"Ranked 1v1 / 2v2 com tiers (HT1 a LT5), duelos com amigos e os rankings. 15 arenas.");
	}

	@Override
	public String tag() {
		return "PvP";
	}

	@Override
	public String icon() {
		return "pvp_group";
	}

	@Override
	public Screen embed(Screen back) {
		return new RankedScreen(back, true);
	}

	@Override
	public void open(Screen parent) {
		dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), new RankedScreen(parent));
	}
}
