package dev.kuboext.far_horizons.mixin;

import dev.solclient.mods.FarHorizonsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Far Horizons: the camera must be able to see as far as the LOD goes. */
/** priority 5000 = applied after Sodium / Iris / other mods, so the last word on the distance is Caos's. */
@Mixin(targets = "net.minecraft.client.render.GameRenderer", priority = 5000)
public abstract class LodFarPlaneMixin {

	@Inject(method = "getFarPlaneDistance", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$lodFar(CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(FarHorizonsMod.farPlane(cir.getReturnValue()));
	}
}
