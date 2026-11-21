package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.PerformanceMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance mod: lower FPS while the window is in the background. */
@Mixin(targets = "com.mojang.blaze3d.platform.FramerateLimitTracker")
public abstract class InactivityFpsMixin {

	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$background(CallbackInfoReturnable<Integer> cir) {
		try {
			int v = cir.getReturnValue();
			int l = PerformanceMod.limit(v);
			if (l != v) {
				cir.setReturnValue(l);
			}
		} catch (Throwable ignored) {
		}
	}
}
