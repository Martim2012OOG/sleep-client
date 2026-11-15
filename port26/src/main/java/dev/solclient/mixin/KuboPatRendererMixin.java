package dev.solclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.fx.PatSquish;
import dev.solclient.mods.PatPatMod;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PatPat: the patted animal squishes down a little under the hand (from its feet, so it stays on the ground). */
@Mixin(LivingEntityRenderer.class)
public class KuboPatRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$patState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof PatSquish p) {
			p.kubo$setPat(PatPatMod.squish(entity));
		}
	}

	@Inject(method = "setupRotations", at = @At("TAIL"), require = 0)
	private void kubo$patSquish(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
		if ((Object) state instanceof PatSquish p && p.kubo$pat() > 0.001F) {
			float k = p.kubo$pat();
			matrices.scale(1F + 0.12F * k, 1F - 0.2F * k, 1F + 0.12F * k);
		}
	}
}
