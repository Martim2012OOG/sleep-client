package dev.sleepclient.mixin.perf;

import dev.sleepclient.fx.killfx.KillFx;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3D kill effects: while a creature eats a dead player, the real body (which would tip over) is not drawn; the effect
 * draws a standing copy with the same skin instead. Only drawing is skipped: the entity itself is never touched.
 */
@Mixin(EntityRenderer.class)
public class KillFxHideMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$hideEaten(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (KillFx.hides(entity)) {
			cir.setReturnValue(false);
		}
	}
}
