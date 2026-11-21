package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.CrystalTweaksMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crystal Tweaks: the up-and-down bounce of end crystals. */
@Mixin(targets = "net.minecraft.client.renderer.entity.EndCrystalRenderer")
public abstract class CrystalBounceMixin {

	@Inject(method = "getY", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$bounce(float age, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(CrystalTweaksMod.yOffset(cir.getReturnValueF()));
	}
}
