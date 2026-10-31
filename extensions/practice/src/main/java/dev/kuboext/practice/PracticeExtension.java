package dev.kuboext.practice;

import dev.solclient.addons.Addons;
import dev.solclient.addons.bridge.BridgeHud;
import dev.solclient.addons.bridge.BridgePracticeAddon;
import dev.solclient.addons.bridge.BridgeSession;
import dev.solclient.addons.pvp.FightStats;
import dev.solclient.addons.pvp.FightStatsHud;
import dev.solclient.addons.pvp.PracticeSession;
import dev.solclient.addons.pvp.PracticeWorld;
import dev.solclient.addons.pvp.PvpPracticeAddon;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import dev.solclient.gui.PracticeLoadingScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Sleep extension "Practice": the PvP arena with bots, the Kit Editor and Bridge Practice (they used to be built in).
 * Shows up in the menu's Add-ons tab. Fabric events can't be unregistered, so they are hooked once and do nothing
 * while the extension is uninstalled.
 */
public class PracticeExtension implements KuboExtension {

	private static volatile boolean on;
	private static boolean hooked;

	@Override
	public void register(KuboApi api) {
		on = true;
		Addons.register(new PvpPracticeAddon());
		Addons.register(new BridgePracticeAddon());
		if (hooked) {
			return; // reinstalled in the same session: the hooks below are still there
		}
		hooked = true;
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (on) {
				PracticeSession.tick(s);
				BridgeSession.tick(s);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			PracticeSession.stop();
			BridgeSession.stop();
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (on) {
				PracticeWorld.tick(mc);
				BridgeHud.tick(mc);
			}
		});
		api.onHud(c -> {
			if (on) {
				BridgeHud.render(c);
				FightStatsHud.render(c);
			}
		});
		api.onLeftClick(() -> {
			if (on) {
				FightStats.click();
			}
		});
		api.screenFilter(s -> on ? PracticeLoadingScreen.intercept(s) : s);
	}

	@Override
	public void unload() {
		on = false;
		PracticeSession.stop();
		BridgeSession.stop();
		Addons.unregister("pvp_practice");
		Addons.unregister("bridge_practice");
	}
}
