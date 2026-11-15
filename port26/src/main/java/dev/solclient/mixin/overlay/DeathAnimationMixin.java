package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entities no longer tip over when they die. */
@Mixin(targets = "net.minecraft.client.renderer.entity.LivingEntityRenderer")
public class DeathAnimationMixin {

	@Inject(method = "getFlipDegrees", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$noDeathTilt(CallbackInfoReturnable<Float> cir) {
		if (OverlayMod.disableDeathAnimation()) {
			cir.setReturnValue(0F);
		}
	}
}
