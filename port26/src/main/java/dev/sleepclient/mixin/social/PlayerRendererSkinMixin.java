package dev.sleepclient.mixin.social;

import dev.sleepclient.social.SolarSkins;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Sleep cape / skin, put straight into the player's render state after vanilla filled it. This is what both the
 * world and the 3D preview in the menu draw, so the cape shows everywhere even if getSkin() is not hooked.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererSkinMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$solarSkin(Avatar player, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
		try {
			PlayerSkin base = state.skin;
			PlayerSkin out = (Object) player instanceof net.minecraft.world.entity.player.Player pe ? SolarSkins.apply(pe, base)
					: SolarSkins.apply(((Entity) player).getUUID(), base);
			if (out != base) {
				state.skin = out;
			}
		} catch (RuntimeException ignored) {
		}
	}
}
