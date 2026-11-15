package dev.solclient.mixin.social;

import com.mojang.authlib.GameProfile;
import dev.solclient.social.HostedSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real skins for the players of servers that send none (Host World with non-premium accounts allowed). */
@Mixin(PlayerInfo.class)
public abstract class HostedSkinMixin {

	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$hostedSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin t = HostedSkins.lookup(getProfile());
		if (t != null) {
			cir.setReturnValue(t);
		}
	}
}
