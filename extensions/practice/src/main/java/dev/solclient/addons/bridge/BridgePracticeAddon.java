package dev.solclient.addons.bridge;

import dev.solclient.addons.Addon;
import dev.solclient.gui.BridgePracticeScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

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
		MinecraftClient.getInstance().setScreen(new BridgePracticeScreen(parent));
	}
}
