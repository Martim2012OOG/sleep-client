package dev.sleepclient.mixin.social;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.music.MusicTagDraw;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Music Share: the line with the note, the song and the headphones icon, ABOVE the name of a player who shares what they listen to. */
@Mixin(AvatarRenderer.class)
public abstract class MusicTagMixin {

	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void sleep$musicTag(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
		if (state.nameTagAttachment == null || state.isInvisible) {
			return;
		}
		// seen in game: a positive offset puts the line UNDER the name, so it goes up with a negative one; with a scoreboard line the name is a line higher
		MusicTagDraw.submit(state, matrices, queue, camera, state.nameTagAttachment, state.scoreText != null ? -20 : -11);
	}
}
