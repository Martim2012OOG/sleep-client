package dev.kuboext.pvp_extras;

import dev.sleepclient.mods.PopChamsMod;
import dev.sleepclient.mods.TierTaggerMod;
import dev.sleepclient.mods.CobwebTweaksMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "PvP Extras": Pop Chams (ghost on totem pop), TierTagger (MCTiers tiers on names) and Cobweb Tweaks. */
public class PvpExtrasExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new PopChamsMod());
		api.addMod(new TierTaggerMod());
		api.addMod(new CobwebTweaksMod());
		api.nameDecorator((e, name) -> e instanceof net.minecraft.entity.player.PlayerEntity p ? TierTaggerMod.decorate(p.getUuid(), name) : name);
		api.tabDecorator((id, name) -> TierTaggerMod.inTab() ? TierTaggerMod.decorate(id, name) : name);
		api.onHud(c -> {
			if (CobwebTweaksMod.instance != null && CobwebTweaksMod.instance.enabled) CobwebTweaksMod.instance.draw(c);
		});
		api.onEntityStatus((e, s) -> {
			if (s == 35) PopChamsMod.onPop(e);
		});
	}
}
