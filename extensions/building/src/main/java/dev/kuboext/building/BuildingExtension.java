package dev.kuboext.building;

import dev.solclient.mods.KuboBuilderMod;
import dev.solclient.mods.LitematicaHelperMod;
import dev.solclient.mods.RulerMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Caos extension "Building": Caos Builder (schematics), Litematica Helper and Ruler. */
public class BuildingExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new KuboBuilderMod());
		api.addMod(new LitematicaHelperMod());
		api.addMod(new RulerMod());
		api.command("Caos Builder", "Schematics: load, place, move, build", "kubo_builder",
				() -> MinecraftClient.getInstance().setScreen(new dev.solclient.gui.BuilderScreen(MinecraftClient.getInstance().currentScreen)));
	}
}
