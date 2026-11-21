package dev.sleepclient.mixin.social;

import dev.sleepclient.social.KuboRelay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
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

	@Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private static void kubo$hostWorld(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, boolean quickPlay,
			CookieStorage cookies, CallbackInfo ci) {
		String typed = info != null && KuboRelay.isRelayAddress(info.address) ? info.address : null;
		if (typed == null) {
			return;
		}
		try {
			String local = KuboRelay.localFor(typed);
			ServerAddress real = ServerAddress.parse(local);
			if (!real.getAddress().equals(address.getAddress()) || real.getPort() != address.getPort()) {
				ci.cancel();
				ConnectScreen.connect(screen, client, real, info, quickPlay, cookies);
			}
		} catch (Exception e) {
			System.err.println("[Sleep] host world address: " + e);
		}
	}
}
