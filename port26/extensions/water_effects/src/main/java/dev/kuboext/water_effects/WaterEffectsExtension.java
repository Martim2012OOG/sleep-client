package dev.kuboext.water_effects;

import dev.solclient.mods.WaterEffectsMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Caos extension "Water Effects": Boat wakes, waterfall foam, ripples and splashes. */
public class WaterEffectsExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new WaterEffectsMod());
	}
}
