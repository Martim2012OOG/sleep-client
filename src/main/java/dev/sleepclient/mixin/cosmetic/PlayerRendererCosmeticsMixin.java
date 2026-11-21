package dev.sleepclient.mixin.cosmetic;

import dev.sleepclient.cosmetic.CosmeticFeatureRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the 3D cosmetics layer to every player renderer (wide and slim). In its own optional mixin config. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererCosmeticsMixin {

	@SuppressWarnings("unchecked")
	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void plegumo$cosmetics(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
		try {
			((LivingEntityRendererAccessor) this).plegumo$addFeature(
					new CosmeticFeatureRenderer((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this, slim));
		} catch (Throwable t) {
			System.err.println("[Sleep] cosmetics layer not added: " + t);
		}
	}
}
