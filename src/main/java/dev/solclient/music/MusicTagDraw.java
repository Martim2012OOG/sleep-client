package dev.solclient.music;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Draws the Music Share line (and the cover) above a player: used by the normal name tag and by the fallback for servers with custom name tags. */
public final class MusicTagDraw {

	private MusicTagDraw() {
	}

	/** pos = where the label goes (entity space), y = the line offset in label units (negative = up). */
	public static void submit(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, Vec3d pos, int y) {
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
				queue.submitLabel(matrices, pos, y, tag, !state.sneaking, state.light, state.squaredDistanceToCamera, camera);
				String cover = MusicShare.coverFor(e.getUuid(), e instanceof net.minecraft.entity.player.PlayerEntity pe2 ? pe2.getGameProfile().name() : null);
				var tex = cover == null ? null : dev.solclient.social.RemoteTextures.get(cover);
				if (tex != null && camera.orientation != null) {
					// the album cover where the note was: a small square that faces you, at the left end of the line
					float half = mc.textRenderer.getWidth(tag) * 0.5F * 0.025F;
					float size = 0.28F;
					net.minecraft.util.Identifier id = tex.id();
					matrices.push();
					matrices.translate(pos.x, pos.y, pos.z);
					matrices.multiply(camera.orientation);
					// the line's own offset is in the camera's axes (it turns with the view): the cover must use the same, or it slides when you look up / down
					matrices.translate(-half - 0.08F, 0.27F - y * 0.025F, 0.0F); // inside the left end of the line, centred on the text
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
			// no tag this frame
		}
	}
}
