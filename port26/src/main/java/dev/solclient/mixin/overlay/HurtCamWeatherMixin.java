package dev.solclient.mixin.overlay;

import dev.solclient.mods.NoHurtCamMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam: skip the damage tilt (Yarn name "tiltViewWhenHurt", Mojang name "bobHurt"). */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer")
public abstract class HurtCamWeatherMixin {

	// no target arguments on purpose: the handler is valid whatever the exact signature is
	@Inject(method = {"bobHurt", "bobHurt"}, at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$noHurtCam(CallbackInfo ci) {
		if (NoHurtCamMod.active()) {
			ci.cancel();
		}
	}
}
