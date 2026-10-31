package dev.solclient.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class KuboCommandGuardMixin {

	@Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$guard(String command, CallbackInfo ci) {
		if (dev.solclient.mods.CommandGuardMod.block(command)) {
			ci.cancel();
		}
	}
}
