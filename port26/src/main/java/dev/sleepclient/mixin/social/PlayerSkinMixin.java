package dev.sleepclient.mixin.social;

import dev.sleepclient.social.SolarSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sleep capes / skins through getSkin() (heads in the tab list, etc.). On 1.21.11 getSkin() is declared in
 * ClientPlayerLikeEntity, so the renderer hook ({@link PlayerRendererSkinMixin}) is what makes capes show in game.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$solarSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin base = cir.getReturnValue();
		PlayerSkin out = SolarSkins.apply((AbstractClientPlayer) (Object) this, base);
		if (out != base) {
			cir.setReturnValue(out);
		}
	}
}
