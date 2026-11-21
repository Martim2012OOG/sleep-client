package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.TpsDisplayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TPS Display: notes when the server's world time packet arrives. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener")
public abstract class TpsMixin {

	@Inject(method = "handleSetTime", at = @At("HEAD"), require = 0)
	private void kubo$tps(CallbackInfo ci) {
		TpsDisplayMod.packet();
	}
}
