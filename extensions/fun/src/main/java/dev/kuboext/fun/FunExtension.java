package dev.kuboext.fun;

import dev.solclient.mods.BabyModeMod;
import dev.solclient.mods.DynamicIslandMod;
import dev.solclient.mods.SkinTotemMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Caos extension "Fun": Baby Mode, Dynamic Island and Skin Totem. */
public class FunExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new BabyModeMod());
		api.addMod(new DynamicIslandMod());
		api.addMod(new SkinTotemMod());
		api.entityScale(BabyModeMod::scaleFor);
		api.totemAnimation(SkinTotemMod::replace);
		api.onHudLast(c -> {
			if (SkinTotemMod.instance != null && SkinTotemMod.instance.enabled) SkinTotemMod.instance.draw(c);
		});
	}
}
