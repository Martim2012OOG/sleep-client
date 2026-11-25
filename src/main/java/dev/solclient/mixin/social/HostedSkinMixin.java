package dev.solclient.mixin.social;

import com.mojang.authlib.GameProfile;
import dev.solclient.social.HostedSkins;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real skins for the players of servers that send none (Host World with non-premium accounts allowed). */
@Mixin(PlayerListEntry.class)
public abstract class HostedSkinMixin {

	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true, require = 0)
	private void caos$hostedSkin(CallbackInfoReturnable<SkinTextures> cir) {
		SkinTextures t = HostedSkins.lookup(getProfile());
		if (t != null) {
			cir.setReturnValue(t);
		}
	}
}
