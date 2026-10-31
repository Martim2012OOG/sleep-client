package dev.solclient.mixin.social;

import dev.solclient.mods.NametagHealthMod;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametag Health: puts "13 ❤" in the line vanilla uses for the scoreboard under a player's name, so it is drawn by
 * Minecraft itself, right under the nametag (and only when the nametag is visible).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class NametagHealthMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$nametagHealth(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (!NametagHealthMod.on() || !((Object) player instanceof LivingEntity le) || le.isInvisible()) {
			return;
		}
		if (state.playerName != null && NametagHealthMod.keepServerLine()) {
			return; // the server already shows its own line there (often the health too)
		}
		try {
			state.playerName = NametagHealthMod.line(le);
		} catch (RuntimeException ignored) {
		}
	}
}
