package dev.solclient.mixin.social;

import dev.solclient.social.SolarSkins;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sleep capes / skins through getSkin() (heads in the tab list, etc.). On 1.21.11 getSkin() is declared in
 * ClientPlayerLikeEntity, so the renderer hook ({@link PlayerRendererSkinMixin}) is what makes capes show in game.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerSkinMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$solarSkin(CallbackInfoReturnable<SkinTextures> cir) {
		SkinTextures base = cir.getReturnValue();
		SkinTextures out = SolarSkins.apply((AbstractClientPlayerEntity) (Object) this, base);
		if (out != base) {
			cir.setReturnValue(out);
		}
	}
}
