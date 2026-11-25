package dev.kuboext.pvp_extras;

import dev.solclient.mods.PopChamsMod;
import dev.solclient.mods.TierTaggerMod;
import dev.solclient.mods.CobwebTweaksMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Caos extension "PvP Extras": Pop Chams (ghost on totem pop), TierTagger (MCTiers tiers on names) and Cobweb Tweaks. */
public class PvpExtrasExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new PopChamsMod());
		api.addMod(new TierTaggerMod());
		api.addMod(new CobwebTweaksMod());
		api.nameDecorator((e, name) -> e instanceof net.minecraft.world.entity.player.Player p ? TierTaggerMod.decorate(p.getUUID(), name) : name);
		api.tabDecorator((id, name) -> TierTaggerMod.inTab() ? TierTaggerMod.decorate(id, name) : name);
		api.onHud(c -> {
			if (CobwebTweaksMod.instance != null && CobwebTweaksMod.instance.enabled) CobwebTweaksMod.instance.draw(c);
		});
		api.onEntityStatus((e, s) -> {
			if (s == 35) PopChamsMod.onPop(e);
		});
	}
}
