package dev.sleepclient.mixin.social;

import dev.sleepclient.mods.TransparentPlayersMod;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Transparent Players: uses vanilla's own "invisible but seen" drawing (translucent body). */
@Mixin(PlayerEntityRenderer.class)
public abstract class TransparentPlayersMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$transparent(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (!state.invisible && TransparentPlayersMod.applies((Entity) player)) {
			state.invisible = true;
			state.invisibleToPlayer = false;
		}
	}
}
