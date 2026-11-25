package dev.solclient.mixin.social;

import dev.solclient.social.SolarSkins;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Caos cape / skin, put straight into the player's render state after vanilla filled it. This is what both the
 * world and the 3D preview in the menu draw, so the cape shows everywhere even if getSkin() is not hooked.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererSkinMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$solarSkin(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		try {
			SkinTextures base = state.skinTextures;
			SkinTextures out = (Object) player instanceof net.minecraft.entity.player.PlayerEntity pe ? SolarSkins.apply(pe, base)
					: SolarSkins.apply(((Entity) player).getUuid(), base);
			if (out != base) {
				state.skinTextures = out;
			}
		} catch (RuntimeException ignored) {
		}
	}
}
