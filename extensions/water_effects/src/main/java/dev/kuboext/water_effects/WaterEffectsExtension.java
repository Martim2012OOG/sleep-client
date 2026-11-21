package dev.kuboext.water_effects;

import dev.sleepclient.mods.WaterEffectsMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "Water Effects": Boat wakes, waterfall foam, ripples and splashes. */
public class WaterEffectsExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new WaterEffectsMod());
	}
}
