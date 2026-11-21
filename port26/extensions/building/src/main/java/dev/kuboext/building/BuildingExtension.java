package dev.kuboext.building;

import dev.sleepclient.mods.KuboBuilderMod;
import dev.sleepclient.mods.LitematicaHelperMod;
import dev.sleepclient.mods.RulerMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Sleep extension "Building": Sleep Builder (schematics), Litematica Helper and Ruler. */
public class BuildingExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new KuboBuilderMod());
		api.addMod(new LitematicaHelperMod());
		api.addMod(new RulerMod());
		api.command("Sleep Builder", "Schematics: load, place, move, build", "kubo_builder",
				() -> dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), new dev.sleepclient.gui.BuilderScreen(dev.sleepclient.compat.Mc.screen(Minecraft.getInstance()))));
	}
}
