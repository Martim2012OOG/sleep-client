package dev.sleepclient.mixin;

import dev.sleepclient.fx.ScaleFlag;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class KuboEntityRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$baby(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof ScaleFlag f) {
			f.kubo$setScale(entity instanceof net.minecraft.world.entity.LivingEntity le ? dev.sleepclient.ext.KuboApi.scaleFor(le) : 1F);
		}
	}
}
