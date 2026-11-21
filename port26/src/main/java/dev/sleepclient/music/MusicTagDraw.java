package dev.sleepclient.music;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Draws the Music Share line (and the cover) above a player: used by the normal name tag and by the fallback for servers with custom name tags. */
public final class MusicTagDraw {

	private MusicTagDraw() {
	}

	/** pos = where the label goes (entity space), y = the line offset in label units (negative = up). */
	public static void submit(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, Vec3 pos, int y) {
		try {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}
			Entity e = mc.level.getEntity(state.id);
			if (e == null) {
				return;
			}
			Component tag = MusicShare.tagFor(e.getUUID(), e instanceof net.minecraft.world.entity.player.Player pe ? pe.getGameProfile().name() : null, state.distanceToCameraSq);
			if (tag != null) {
				dev.sleepclient.compat.Mc.submitNameTag(queue, matrices, pos, y, tag, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
				String cover = MusicShare.coverFor(e.getUUID(), e instanceof net.minecraft.world.entity.player.Player pe2 ? pe2.getGameProfile().name() : null);
				var tex = cover == null ? null : dev.sleepclient.social.RemoteTextures.get(cover);
				if (tex != null && camera.orientation != null) {
					// the album cover where the note was: a small square that faces you, at the left end of the line
					float half = mc.font.width(tag) * 0.5F * 0.025F;
					float size = 0.28F;
					net.minecraft.resources.Identifier id = tex.id();
					matrices.pushPose();
					matrices.translate(pos.x, pos.y, pos.z);
					matrices.mulPose(camera.orientation);
					// the line's own offset is in the camera's axes (it turns with the view): the cover must use the same, or it slides when you look up / down
					matrices.translate(-half - 0.08F, 0.27F - y * 0.025F, 0.0F); // inside the left end of the line, centred on the text
					queue.submitCustomGeometry(matrices, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(id), (entry, vc) -> {
						int light = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
						int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
						vc.addVertex(entry, 0F, 0F, 0F).setColor(0xFFFFFFFF).setUv(0F, 1F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
						vc.addVertex(entry, size, 0F, 0F).setColor(0xFFFFFFFF).setUv(1F, 1F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
						vc.addVertex(entry, size, size, 0F).setColor(0xFFFFFFFF).setUv(1F, 0F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
						vc.addVertex(entry, 0F, size, 0F).setColor(0xFFFFFFFF).setUv(0F, 0F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
					});
					matrices.popPose();
				}
			}
		} catch (RuntimeException ignored) {
			// no tag this frame
		}
	}
}
