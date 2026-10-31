package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.GameRenderer")
public class ViewBobbingMixin {

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noBob(CallbackInfo ci) {
		if (OverlayMod.minimalBobbing()) {
			ci.cancel();
		}
	}
}
