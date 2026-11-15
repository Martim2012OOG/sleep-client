package dev.solclient.mixin.overlay;

import dev.solclient.mods.LowFireMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Moves the first person fire overlay up/down (vanilla translates it by -0.3 on Y). */
@Mixin(targets = "net.minecraft.client.renderer.ScreenEffectRenderer")
public class FireHeightMixin {

	@ModifyArgs(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), require = 0)
	private static void sol$fireF(Args args) {
		float y = args.get(1);
		if (Math.abs(y + 0.3F) < 0.001F) {
			args.set(1, (float) LowFireMod.fireOverlayY(y));
		}
	}

	@ModifyArgs(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V"), require = 0)
	private static void sol$fireD(Args args) {
		double y = args.get(1);
		if (Math.abs(y + 0.3D) < 0.001D) {
			args.set(1, LowFireMod.fireOverlayY(y));
		}
	}
}
