package dev.kuboext.hypixel;

import dev.sleepclient.mods.QuickPlayMod;
import dev.sleepclient.mods.HypixelAdditionsMod;
import dev.sleepclient.mods.LevelHeadMod;
import dev.sleepclient.mods.StatsOverlayMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Hypixel": Everything for Hypixel: Quick Play, Hypixel Additions, Level Head and the Stats Overlay. */
public class HypixelExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new QuickPlayMod());
		api.addMod(new HypixelAdditionsMod());
		api.addMod(new LevelHeadMod());
		api.addMod(new StatsOverlayMod());
		api.nameDecorator((e, name) -> e instanceof net.minecraft.world.entity.player.Player p ? LevelHeadMod.decorate(p, name) : name);
		ClientTickEvents.END_CLIENT_TICK.register(c -> dev.sleepclient.social.HypixelApi.tick());
	}
}
