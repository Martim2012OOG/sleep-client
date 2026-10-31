package dev.solclient.mixin.emote;

import dev.solclient.emote.EmoteFlag;
import dev.solclient.emote.EmoteManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererEmoteMixin {

	/** Marks the render state of the local player while an emote is playing. */
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void sol$flagEmote(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof EmoteFlag flag) {
			boolean local = entity == MinecraftClient.getInstance().player;
			flag.sol$setEmoting(EmoteManager.active() && local);
			flag.sol$setRemoteEmote(local ? null : EmoteManager.remoteFor(entity));
		}
	}
}
