package dev.kuboext.far_horizons;

import dev.solclient.mods.FarHorizonsMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Caos extension "Far Horizons": See terrain far beyond your render distance (like Distant Horizons). Uses more memory. */
public class FarHorizonsExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new FarHorizonsMod());
	}
}
