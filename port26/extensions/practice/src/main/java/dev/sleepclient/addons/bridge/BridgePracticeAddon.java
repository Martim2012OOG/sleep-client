package dev.sleepclient.addons.bridge;

import dev.sleepclient.addons.Addon;
import dev.sleepclient.gui.BridgePracticeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Bridging practice: straight, diagonal, stairs up / down, pillar, with a timer and best times. */
public class BridgePracticeAddon implements Addon {

	@Override
	public String id() {
		return "bridge_practice";
	}

	@Override
	public String name() {
		return "Bridge Practice";
	}

	@Override
	public String description() {
		return "Practise bridging: straight, diagonal, stairs, pillar. Timer and best times.";
	}

	@Override
	public String icon() {
		return "addon_bridge";
	}

	@Override
	public String tag() {
		return "Training";
	}

	@Override
	public void open(Screen parent) {
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), new BridgePracticeScreen(parent));
	}
}
