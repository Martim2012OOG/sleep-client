package dev.sleepclient.mixin.cosmetic;

import dev.sleepclient.cosmetic.LayerMask;
import dev.sleepclient.cosmetic.SkinLayers3D;
import dev.sleepclient.cosmetic.SkinLayersFeature;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D Skin Layers: adds the feature that draws the voxels and decides, once per frame and player, which layers are 3D. */
@Mixin(AvatarRenderer.class)
public abstract class PlayerLayersRendererMixin {

	@SuppressWarnings("unchecked")
	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("RETURN"), require = 0)
	private void sleep$addLayers(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
		((LivingRendererInvoker) this).sleep$addFeature(new SkinLayersFeature((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"), require = 0)
	private void sleep$mask(Avatar player, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
		int mask = 0;
		try {
			mask = SkinLayers3D.maskFor(state);
		} catch (RuntimeException ignored) {
		}
		((LayerMask) state).sleep$setMask(mask);
	}
}
