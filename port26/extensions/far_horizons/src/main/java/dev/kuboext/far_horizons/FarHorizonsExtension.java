package dev.kuboext.far_horizons;

import dev.sleepclient.mods.FarHorizonsMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Far Horizons": See terrain far beyond your render distance (like Distant Horizons). Uses more memory. */
public class FarHorizonsExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new FarHorizonsMod());
	}
}
