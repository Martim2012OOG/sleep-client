package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer")
public class EndPortalMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noPortal(CallbackInfo ci) {
		if (OverlayMod.hideEndportals()) {
			ci.cancel();
		}
	}
}
