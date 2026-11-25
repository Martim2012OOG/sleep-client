package dev.solclient.mixin.social;

import dev.solclient.social.KuboRelay;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla refuses "host.endlock.net/Name" as a server address (the Join / Done button stays grey). Relay addresses are
 * valid for Caos: the connection itself is swapped for the local tunnel by HostWorldConnectMixin.
 */
@Mixin(ServerAddress.class)
public abstract class ServerAddressValidMixin {

	@Inject(method = "isValid(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private static void kubo$relayValid(String address, CallbackInfoReturnable<Boolean> cir) {
		if (KuboRelay.isRelayAddress(address)) {
			cir.setReturnValue(true);
		}
	}
}
