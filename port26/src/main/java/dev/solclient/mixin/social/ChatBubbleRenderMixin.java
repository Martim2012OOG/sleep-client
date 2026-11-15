package dev.solclient.mixin.social;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.chatx.BubbleHolder;
import dev.solclient.mods.ChatBubblesMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat bubbles above players' heads. */
@Mixin(EntityRenderer.class)
public class ChatBubbleRenderMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void plegumo$collectBubbles(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof BubbleHolder h) {
			h.plegumo$setBubbles(ChatBubblesMod.forEntity(entity));
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void plegumo$drawBubbles(EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera,
			CallbackInfo ci) {
		if ((Object) state instanceof BubbleHolder h && h.plegumo$bubbles() != null) {
			try {
				ChatBubblesMod.render(h.plegumo$bubbles(), state, matrices, queue, camera);
			} catch (RuntimeException ignored) {
				// never break entity rendering because of a bubble
			}
		}
	}
}
