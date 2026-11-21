package dev.kuboext.better_f3;

import dev.sleepclient.mods.BetterF3Mod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Better F3": A clean, readable F3 screen with only what matters. */
public class BetterF3Extension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new BetterF3Mod());
		api.onF3Hud(c -> {
			if (BetterF3Mod.active()) BetterF3Mod.instance.draw(c);
		});
		api.replacesF3(BetterF3Mod::replacesVanilla);
	}
}
