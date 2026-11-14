package dev.solclient.mixin.social;

import dev.solclient.music.MusicShare;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Music Share: the line with the note, the song and the headphones icon, ABOVE the name of a player who shares what they listen to. */
@Mixin(PlayerEntityRenderer.class)
public abstract class MusicTagMixin {

	@Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void sleep$musicTag(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		if (state.nameLabelPos == null || state.invisible) {
			return;
		}
		try {
			var mc = net.minecraft.client.MinecraftClient.getInstance();
			if (mc.world == null) {
				return;
			}
			Entity e = mc.world.getEntityById(state.id);
			if (e == null) {
				return;
			}
			Text tag = MusicShare.tagFor(e.getUuid(), state.squaredDistanceToCamera);
			if (tag != null) {
				// vanilla draws the name at 0 and the scoreboard line under it; positive = higher. With a scoreboard line the name is lifted a line.
				int y = state.playerName != null ? 21 : 11;
				queue.submitLabel(matrices, state.nameLabelPos, y, tag, !state.sneaking, state.light, state.squaredDistanceToCamera, camera);
			}
		} catch (RuntimeException ignored) {
		}
	}
}
