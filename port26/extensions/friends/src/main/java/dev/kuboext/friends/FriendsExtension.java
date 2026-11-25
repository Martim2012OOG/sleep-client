package dev.kuboext.friends;

import dev.solclient.mods.PartyHudMod;
import dev.solclient.mods.FriendPingMod;
import dev.solclient.mods.SonarMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Caos extension "Friends": Party HUD, Pings (mark places for friends) and Sonar. */
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
