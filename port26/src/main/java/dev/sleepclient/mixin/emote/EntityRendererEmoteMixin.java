package dev.sleepclient.mixin.emote;

import dev.sleepclient.emote.EmoteFlag;
import dev.sleepclient.emote.EmoteManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererEmoteMixin {

	/** Marks the render state of the local player while an emote is playing. */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void sol$flagEmote(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof EmoteFlag flag) {
			boolean local = entity == Minecraft.getInstance().player;
			flag.sol$setEmoting(EmoteManager.active() && local);
			flag.sol$setRemoteEmote(local ? null : EmoteManager.remoteFor(entity));
		}
	}
}
