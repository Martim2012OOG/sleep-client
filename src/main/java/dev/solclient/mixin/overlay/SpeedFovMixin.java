package dev.solclient.mixin.overlay;

import dev.solclient.mods.SpeedFovLimiterMod;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Speed FOV Limiter: clamps the FOV change coming from speed / slowness / sprint. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class SpeedFovMixin {

	@Inject(method = "getFovMultiplier(ZF)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$limitFov(boolean firstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
		float v = cir.getReturnValueF(), l = SpeedFovLimiterMod.limit(v);
		if (l != v) {
			cir.setReturnValue(l);
		}
	}
}
