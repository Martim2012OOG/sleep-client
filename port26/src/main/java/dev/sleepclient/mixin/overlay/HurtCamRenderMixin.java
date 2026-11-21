package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.NoHurtCamMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam, layer 3: nothing to tilt while the 3D world is set up (see NoHurtCamMod). */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer")
public abstract class HurtCamRenderMixin {

	@Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
	private void kubo$hurtBegin(CallbackInfo ci) {
		NoHurtCamMod.begin();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
	private void kubo$hurtEnd(CallbackInfo ci) {
		NoHurtCamMod.end();
	}
}
