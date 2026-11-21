package dev.sleepclient.mixin.cosmetic;

import dev.sleepclient.cosmetic.CosmeticFeatureRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the 3D cosmetics layer to every player renderer (wide and slim). In its own optional mixin config. */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererCosmeticsMixin {

	@SuppressWarnings("unchecked")
	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void plegumo$cosmetics(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
		try {
			((LivingEntityRendererAccessor) this).plegumo$addFeature(
					new CosmeticFeatureRenderer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this, slim));
		} catch (Throwable t) {
			System.err.println("[Sleep] cosmetics layer not added: " + t);
		}
	}
}
