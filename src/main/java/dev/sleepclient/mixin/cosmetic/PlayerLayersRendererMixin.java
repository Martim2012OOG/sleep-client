package dev.sleepclient.mixin.cosmetic;

import dev.sleepclient.cosmetic.LayerMask;
import dev.sleepclient.cosmetic.SkinLayers3D;
import dev.sleepclient.cosmetic.SkinLayersFeature;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D Skin Layers: adds the feature that draws the voxels and decides, once per frame and player, which layers are 3D. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerLayersRendererMixin {

	@SuppressWarnings("unchecked")
	@Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V", at = @At("RETURN"), require = 0)
	private void sleep$addLayers(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
		((LivingRendererInvoker) this).sleep$addFeature(new SkinLayersFeature((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this));
	}

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"), require = 0)
	private void sleep$mask(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		int mask = 0;
		try {
			mask = SkinLayers3D.maskFor(state);
		} catch (RuntimeException ignored) {
		}
		((LayerMask) state).sleep$setMask(mask);
	}
}
