package dev.kuboext.hud_extras;

import dev.sleepclient.mods.SpeedometerMod;
import dev.sleepclient.mods.StopwatchMod;
import dev.sleepclient.mods.InventoryHudMod;
import dev.sleepclient.mods.PickupNotificationsMod;
import dev.sleepclient.mods.NewItemMarkMod;
import dev.sleepclient.mods.DeathPointMod;
import dev.sleepclient.mods.MinimapMod;
import dev.sleepclient.mods.CompassBarMod;
import dev.sleepclient.mods.AutoHideHudMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "HUD Extras": Speedometer, Stopwatch, Inventory HUD, Pickup Notifications, New Item Mark, Death Point, Minimap, Compass Bar and Auto-hide HUD. */
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
				dev.sleepclient.map.MapStore.I.flush();
			}
		});
		api.onHudLast(c -> {
			if (NewItemMarkMod.instance != null && NewItemMarkMod.instance.enabled) NewItemMarkMod.instance.draw(c);
		});
	}
}
