package dev.solclient.mixin.cosmetic;

import dev.solclient.cosmetic.CosmeticFeatureRenderer;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wearing 3D wings with an elytra: the wings ARE the elytra, so the vanilla one is not drawn on top of them. */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraHiddenByWingsMixin {

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$wingsInstead(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityRenderState state, float a, float b,
			CallbackInfo ci) {
		if (CosmeticFeatureRenderer.wingsReplaceElytra(state)) {
			ci.cancel();
		}
	}
}
