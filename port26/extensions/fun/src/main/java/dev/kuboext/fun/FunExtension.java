package dev.kuboext.fun;

import dev.sleepclient.mods.BabyModeMod;
import dev.sleepclient.mods.DynamicIslandMod;
import dev.sleepclient.mods.SkinTotemMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Fun": Baby Mode, Dynamic Island and Skin Totem. */
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
