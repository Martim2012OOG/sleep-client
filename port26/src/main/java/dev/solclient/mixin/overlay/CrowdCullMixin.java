package dev.solclient.mixin.overlay;

import dev.solclient.mods.NoRenderMod;
import dev.solclient.mods.PerformanceMod;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crowd Guard (Performance): in a crowd or with thousands of dropped items only the nearest are drawn. Same hook as
 * GroundArrowsMixin (EntityRenderManager.shouldRender, 1.21.9+). Also applies No Render here, because the old
 * NoRenderMixin still targets the pre-1.21.9 class name.
 */
@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderDispatcher")
public class CrowdCullMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$crowd(Entity entity, @Coerce Object frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (PerformanceMod.cull(entity) || NoRenderMod.hide(entity)) {
			cir.setReturnValue(false);
		}
	}
}
