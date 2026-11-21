package dev.sleepclient.mixin.social;

import dev.sleepclient.chatx.BubbleHolder;
import dev.sleepclient.mods.ChatBubblesMod;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat bubbles above players' heads. */
@Mixin(EntityRenderer.class)
public class ChatBubbleRenderMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void plegumo$collectBubbles(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if ((Object) state instanceof BubbleHolder h) {
			h.plegumo$setBubbles(ChatBubblesMod.forEntity(entity));
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void plegumo$drawBubbles(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera,
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
