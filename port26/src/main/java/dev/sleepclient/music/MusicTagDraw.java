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
					// the album cover where the note was: a small square that faces you, at the left end of the line.
					// It uses EXACTLY the label's own transform (the game lifts the label 0.5 up in world space, turns it to the camera and
					// scales it 0.025 per unit), so it stays glued to the text at any distance or angle, also when you look down on the player.
					float tw = mc.font.width(tag);
					final float size = 11F, x0 = -tw / 2F - 3F, y0 = y - 1F;
					net.minecraft.resources.Identifier id = tex.id();
					matrices.pushPose();
					matrices.translate(pos.x, pos.y + 0.5, pos.z);
					matrices.mulPose(camera.orientation);
					matrices.scale(0.025F, -0.025F, 0.025F);
					queue.submitCustomGeometry(matrices, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(id), (entry, vc) -> {
						int light = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
						int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
						vc.addVertex(entry, x0, y0, 0F).setColor(0xFFFFFFFF).setUv(0F, 0F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
						vc.addVertex(entry, x0 + size, y0, 0F).setColor(0xFFFFFFFF).setUv(1F, 0F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
						vc.addVertex(entry, x0 + size, y0 + size, 0F).setColor(0xFFFFFFFF).setUv(1F, 1F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
						vc.addVertex(entry, x0, y0 + size, 0F).setColor(0xFFFFFFFF).setUv(0F, 1F).setOverlay(ov).setLight(light).setNormal(entry, 0F, 0F, 1F);
					});
					matrices.popPose();
				}
			}
		} catch (RuntimeException ignored) {
			// no tag this frame
		}
	}
}
