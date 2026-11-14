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
			Text tag = MusicShare.tagFor(e.getUuid(), e instanceof net.minecraft.entity.player.PlayerEntity pe ? pe.getGameProfile().name() : null, state.squaredDistanceToCamera);
			if (tag != null) {
				// seen in game: a positive offset puts the line UNDER the name (it hung in front of the body), so it goes up with a negative one.
				// With a scoreboard line (the hearts / the sleep score) the name is already a line higher.
				int y = state.playerName != null ? -20 : -11;
				queue.submitLabel(matrices, state.nameLabelPos, y, tag, !state.sneaking, state.light, state.squaredDistanceToCamera, camera);
				String cover = MusicShare.coverFor(e.getUuid(), e instanceof net.minecraft.entity.player.PlayerEntity pe2 ? pe2.getGameProfile().name() : null);
				var tex = cover == null ? null : dev.solclient.social.RemoteTextures.get(cover);
				if (tex != null && camera.orientation != null) {
					// the album cover where the note was: a small square that faces you, at the left end of the line
					float half = mc.textRenderer.getWidth(tag) * 0.5F * 0.025F;
					float size = 0.28F;
					net.minecraft.util.Identifier id = tex.id();
					matrices.push();
					matrices.translate(state.nameLabelPos.x, state.nameLabelPos.y - y * 0.025F, state.nameLabelPos.z);
					matrices.multiply(camera.orientation);
					matrices.translate(-half + 0.04F, 0.27F, 0.0F); // inside the left end of the line, centred on the text
					queue.submitCustom(matrices, net.minecraft.client.render.RenderLayers.entityCutoutNoCull(id), (entry, vc) -> {
						int light = net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;
						int ov = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;
						vc.vertex(entry, 0F, 0F, 0F).color(0xFFFFFFFF).texture(0F, 1F).overlay(ov).light(light).normal(entry, 0F, 0F, 1F);
						vc.vertex(entry, size, 0F, 0F).color(0xFFFFFFFF).texture(1F, 1F).overlay(ov).light(light).normal(entry, 0F, 0F, 1F);
						vc.vertex(entry, size, size, 0F).color(0xFFFFFFFF).texture(1F, 0F).overlay(ov).light(light).normal(entry, 0F, 0F, 1F);
						vc.vertex(entry, 0F, size, 0F).color(0xFFFFFFFF).texture(0F, 0F).overlay(ov).light(light).normal(entry, 0F, 0F, 1F);
					});
					matrices.pop();
				}
			}
		} catch (RuntimeException ignored) {
		}
	}
}
