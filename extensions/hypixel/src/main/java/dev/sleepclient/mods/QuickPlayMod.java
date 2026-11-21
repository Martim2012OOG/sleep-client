package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.gui.QuickPlayScreen;
import dev.sleepclient.option.Option;
import net.minecraft.client.network.ServerInfo;

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
		boolean down = mc.currentScreen == null && Keys.down(menuKey.code());
		if (down && !wasDown && mc.player != null) {
			ServerInfo info = mc.getCurrentServerEntry();
			if (info != null && info.address != null && info.address.toLowerCase().contains("hypixel.net")) {
				mc.setScreen(new QuickPlayScreen());
			}
		}
		wasDown = down;
	}
}
