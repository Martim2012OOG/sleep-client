package dev.kuboext.friends;

import dev.sleepclient.mods.PartyHudMod;
import dev.sleepclient.mods.FriendPingMod;
import dev.sleepclient.mods.SonarMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "Friends": Party HUD, Pings (mark places for friends) and Sonar. */
public class FriendsExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new PartyHudMod());
		api.addMod(new FriendPingMod());
		api.addMod(new SonarMod());
		api.onFriendPing(FriendPingMod::onIncoming);
		api.onHud(c -> {
			if (FriendPingMod.instance != null && FriendPingMod.instance.enabled) FriendPingMod.instance.draw(c);
		});
	}
}
