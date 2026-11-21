package dev.kuboext.building;

import dev.sleepclient.mods.KuboBuilderMod;
import dev.sleepclient.mods.LitematicaHelperMod;
import dev.sleepclient.mods.RulerMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "Building": Schematic, Litematica Helper and Ruler. */
public class BuildingExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new KuboBuilderMod());
		api.addMod(new LitematicaHelperMod());
		api.addMod(new RulerMod());
		api.command("Schematic", "Schematics: load, place, move, build", "kubo_builder",
				() -> MinecraftClient.getInstance().setScreen(new dev.sleepclient.gui.BuilderScreen(MinecraftClient.getInstance().currentScreen)));
	}
}
