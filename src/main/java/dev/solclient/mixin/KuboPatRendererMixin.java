package dev.solclient.mixin;

import dev.solclient.fx.PatSquish;
import dev.solclient.mods.PatPatMod;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PatPat: the patted animal squishes down a little under the hand (from its feet, so it stays on the ground). */
@Mixin(LivingEntityRenderer.class)
public class KuboPatRendererMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$patState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof PatSquish p) {
			p.kubo$setPat(PatPatMod.squish(entity));
		}
	}

	@Inject(method = "setupTransforms", at = @At("TAIL"), require = 0)
	private void kubo$patSquish(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
		if ((Object) state instanceof PatSquish p && p.kubo$pat() > 0.001F) {
			float k = p.kubo$pat();
			matrices.scale(1F + 0.12F * k, 1F - 0.2F * k, 1F + 0.12F * k);
		}
	}
}
