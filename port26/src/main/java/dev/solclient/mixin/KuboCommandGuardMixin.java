package dev.solclient.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class KuboCommandGuardMixin {

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$guard(String command, CallbackInfo ci) {
		if (dev.solclient.mods.CommandGuardMod.block(command)) {
			ci.cancel();
		}
	}
}
