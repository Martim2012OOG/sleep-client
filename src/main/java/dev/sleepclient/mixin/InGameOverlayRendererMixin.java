package dev.sleepclient.mixin;

import dev.sleepclient.mods.OverlayMod;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

	@Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private static void sol$noFire(CallbackInfo ci) {
		if (OverlayMod.hideFire()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private static void sol$noWater(CallbackInfo ci) {
		if (dev.sleepclient.mods.NoFluidOverlayMod.on()) {
			ci.cancel();
		}
	}
}
