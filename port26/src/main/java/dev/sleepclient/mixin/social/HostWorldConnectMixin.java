package dev.sleepclient.mixin.social;

import dev.sleepclient.social.KuboRelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "host.endlock.net/Name" works anywhere you type a server (server list, Direct Connect): Sleep swaps it for its small
 * local tunnel to the Sleep relay, and the server list keeps showing the nice address.
 */
@Mixin(ConnectScreen.class)
public abstract class HostWorldConnectMixin {

	@Inject(method = "startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private static void kubo$hostWorld(Screen screen, Minecraft client, ServerAddress address, ServerData info, boolean quickPlay,
			TransferState cookies, CallbackInfo ci) {
		String typed = info != null && KuboRelay.isRelayAddress(info.ip) ? info.ip : null;
		if (typed == null) {
			return;
		}
		try {
			String local = KuboRelay.localFor(typed);
			ServerAddress real = ServerAddress.parseString(local);
			if (!real.getHost().equals(address.getHost()) || real.getPort() != address.getPort()) {
				ci.cancel();
				ConnectScreen.startConnecting(screen, client, real, info, quickPlay, cookies);
			}
		} catch (Exception e) {
			System.err.println("[Sleep] host world address: " + e);
		}
	}
}
