package dev.sleepclient.mixin.social;

import dev.sleepclient.mods.TransparentPlayersMod;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Transparent Players: uses vanilla's own "invisible but seen" drawing (translucent body). */
@Mixin(AvatarRenderer.class)
public abstract class TransparentPlayersMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$transparent(Avatar player, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
		if (!state.isInvisible && TransparentPlayersMod.applies((Entity) player)) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = false;
		}
	}
}
