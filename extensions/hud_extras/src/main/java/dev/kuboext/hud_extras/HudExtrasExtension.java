package dev.kuboext.hud_extras;

import dev.solclient.mods.SpeedometerMod;
import dev.solclient.mods.StopwatchMod;
import dev.solclient.mods.InventoryHudMod;
import dev.solclient.mods.PickupNotificationsMod;
import dev.solclient.mods.NewItemMarkMod;
import dev.solclient.mods.DeathPointMod;
import dev.solclient.mods.MinimapMod;
import dev.solclient.mods.CompassBarMod;
import dev.solclient.mods.AutoHideHudMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Caos extension "HUD Extras": Speedometer, Stopwatch, Inventory HUD, Pickup Notifications, New Item Mark, Death Point, Minimap, Compass Bar and Auto-hide HUD. */
public class HudExtrasExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new SpeedometerMod());
		api.addMod(new StopwatchMod());
		api.addMod(new InventoryHudMod());
		api.addMod(new PickupNotificationsMod());
		api.addMod(new NewItemMarkMod());
		api.addMod(new DeathPointMod());
		api.addMod(new MinimapMod());
		api.addMod(new CompassBarMod());
		api.addMod(new AutoHideHudMod());
		// the minimap is a normal movable HUD mod now; this only saves the explored map when you leave a world
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.world == null) {
				dev.solclient.map.MapStore.I.flush();
			}
		});
		api.onHudLast(c -> {
			if (NewItemMarkMod.instance != null && NewItemMarkMod.instance.enabled) NewItemMarkMod.instance.draw(c);
		});
	}
}
