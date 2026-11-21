package dev.sleepclient.mixin;

import dev.sleepclient.mods.OverlayMod;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {

	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0)
	private static void sol$noFire(CallbackInfo ci) {
		if (OverlayMod.hideFire()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true, require = 0)
	private static void sol$noWater(CallbackInfo ci) {
		if (dev.sleepclient.mods.NoFluidOverlayMod.on()) {
			ci.cancel();
		}
	}
}
