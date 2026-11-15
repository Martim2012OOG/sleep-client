package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.gui.QuickPlayScreen;
import dev.solclient.option.Option;
import net.minecraft.client.multiplayer.ServerData;

/** Quickly queue Hypixel games. The game list is a built-in curated list of /play commands. */
public class QuickPlayMod extends Mod {

	private final Option.Key menuKey = key("Key", 77);
	private boolean wasDown;

	public QuickPlayMod() {
		super("Quick Play", "quickplay", "Quickly queue any game.", Category.UTILITY);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void tick() {
		boolean down = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(menuKey.code());
		if (down && !wasDown && mc.player != null) {
			ServerData info = mc.getCurrentServer();
			if (info != null && info.ip != null && info.ip.toLowerCase().contains("hypixel.net")) {
				dev.solclient.compat.Mc.setScreen(mc, new QuickPlayScreen());
			}
		}
		wasDown = down;
	}
}
