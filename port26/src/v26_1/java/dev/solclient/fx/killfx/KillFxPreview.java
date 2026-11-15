package dev.solclient.fx.killfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Pre-visualizacao do kill effect nos menus (cartao do Locker). Versao 26.1: usa o MultiBufferSource. */
public final class KillFxPreview {
	private KillFxPreview() {
	}

	/**
	 * Draws the effect inside a menu preview (the Locker card). 'ms' is the player-model space of the preview (blocks, y down, 0 = the neck),
	 * t = seconds since the preview started. Same bones and animation as in the world; the stand-in carries 'skin'.
	 */
	public static void draw(String id, float t, PoseStack ms, net.minecraft.client.renderer.MultiBufferSource p, int light, Identifier skin) {
		KillFxModel m = KillFxModel.get(id);
		if (m == null || p == null) {
			return;
		}
		// t < 0: a still frame (-t = how far into the effect), for the cards the mouse is not on
		float tt = t < 0F ? m.length * Math.min(1F, -t) : m.length <= 0F ? 0F : t % (m.length + 0.8F);
		tt = Math.min(tt, m.length);
		RenderType layer = RenderTypes.entityCutout(m.texture);
		RenderType skinLayer = skin == null ? null : RenderTypes.entityTranslucent(skin);
		ms.pushPose();
		ms.translate(0F, 1.5F, 0F); // the feet
		ms.scale(-1F / 16F, -1F / 16F, 1F / 16F); // back to world orientation, in model pixels
		for (int i = 0; i < m.bones.length; i++) {
			KillFxModel.Bone b = m.bones[i];
			if (b.verts.length == 0 || KillFx.hidden(m, i) || b.skin && skinLayer == null) {
				continue;
			}
			ms.pushPose();
			KillFx.apply(m, i, tt, ms, 0);
			KillFx.emit(b, ms.last(), p.getBuffer(b.skin ? skinLayer : layer), light);
			ms.popPose();
		}
		ms.popPose();
	}
}
