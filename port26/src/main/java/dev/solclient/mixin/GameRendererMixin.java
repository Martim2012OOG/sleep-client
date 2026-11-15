package dev.solclient.mixin;

import dev.solclient.mods.ZoomMod;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noTotemPop(net.minecraft.world.item.ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.solclient.ext.KuboApi.replaceTotem(stack) || dev.solclient.mods.TotemAnimationMod.hidden()) {
			ci.cancel();
		}
	}
}
