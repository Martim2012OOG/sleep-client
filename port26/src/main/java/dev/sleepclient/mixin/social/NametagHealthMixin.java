package dev.sleepclient.mixin.social;

import dev.sleepclient.mods.NametagHealthMod;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametag Health: puts "13 ❤" in the line vanilla uses for the scoreboard under a player's name, so it is drawn by
 * Minecraft itself, right under the nametag (and only when the nametag is visible).
 */
@Mixin(AvatarRenderer.class)
public abstract class NametagHealthMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$nametagHealth(Avatar player, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
		if (!NametagHealthMod.on() || !((Object) player instanceof LivingEntity le) || le.isInvisible()) {
			return;
		}
		if (state.scoreText != null && NametagHealthMod.keepServerLine()) {
			return; // the server already shows its own line there (often the health too)
		}
		try {
			state.scoreText = NametagHealthMod.line(le);
		} catch (RuntimeException ignored) {
		}
	}
}
