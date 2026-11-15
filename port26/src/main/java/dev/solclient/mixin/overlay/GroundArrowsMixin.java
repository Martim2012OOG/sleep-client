package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderDispatcher")
public class GroundArrowsMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$groundArrows(Entity entity, @Coerce Object frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (OverlayMod.hideGroundArrow(entity)) {
			cir.setReturnValue(false);
		}
	}
}
