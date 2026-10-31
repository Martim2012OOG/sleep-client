package dev.kuboext.hypixel;

import dev.solclient.mods.QuickPlayMod;
import dev.solclient.mods.HypixelAdditionsMod;
import dev.solclient.mods.LevelHeadMod;
import dev.solclient.mods.StatsOverlayMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "Hypixel": Everything for Hypixel: Quick Play, Hypixel Additions, Level Head and the Stats Overlay. */
public class HypixelExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new QuickPlayMod());
		api.addMod(new HypixelAdditionsMod());
		api.addMod(new LevelHeadMod());
		api.addMod(new StatsOverlayMod());
		api.nameDecorator((e, name) -> e instanceof net.minecraft.entity.player.PlayerEntity p ? LevelHeadMod.decorate(p, name) : name);
		ClientTickEvents.END_CLIENT_TICK.register(c -> dev.solclient.social.HypixelApi.tick());
	}
}
