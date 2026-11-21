package dev.sleepclient.mixin;

import dev.sleepclient.mods.ZoomMod;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noTotemPop(net.minecraft.world.item.ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.sleepclient.ext.KuboApi.replaceTotem(stack) || dev.sleepclient.mods.TotemAnimationMod.hidden()) {
			ci.cancel();
		}
	}
}
