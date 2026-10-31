package dev.solclient.mixin.overlay;

import dev.solclient.mods.NoHurtCamMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam, layer 3: nothing to tilt while the 3D world is set up (see NoHurtCamMod). */
@Mixin(targets = "net.minecraft.client.render.GameRenderer")
public abstract class HurtCamRenderMixin {

	@Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
	private void kubo$hurtBegin(CallbackInfo ci) {
		NoHurtCamMod.begin();
	}

	@Inject(method = "renderWorld", at = @At("RETURN"), require = 0)
	private void kubo$hurtEnd(CallbackInfo ci) {
		NoHurtCamMod.end();
	}
}
