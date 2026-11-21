package dev.sleepclient.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.cosmetic.CosmeticFeatureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wearing 3D wings with an elytra: the wings ARE the elytra, so the vanilla one is not drawn on top of them. */
@Mixin(WingsLayer.class)
public abstract class ElytraHiddenByWingsMixin {

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$wingsInstead(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidRenderState state, float a, float b,
			CallbackInfo ci) {
		if (CosmeticFeatureRenderer.wingsReplaceElytra(state)) {
			ci.cancel();
		}
	}
}
