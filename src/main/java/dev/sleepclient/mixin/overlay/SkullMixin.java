package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.OverlayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the placed skull block entity; worn heads use a different (static) method. */
@Mixin(targets = "net.minecraft.client.render.block.entity.SkullBlockEntityRenderer")
public class SkullMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noSkull(CallbackInfo ci) {
		if (OverlayMod.hidePlacedSkulls()) {
			ci.cancel();
		}
	}
}
