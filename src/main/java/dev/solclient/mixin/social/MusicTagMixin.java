package dev.solclient.mixin.social;

import dev.solclient.music.MusicTagDraw;
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
		// seen in game: a positive offset puts the line UNDER the name, so it goes up with a negative one; with a scoreboard line the name is a line higher
		MusicTagDraw.submit(state, matrices, queue, camera, state.nameLabelPos, state.playerName != null ? -20 : -11);
	}
}
