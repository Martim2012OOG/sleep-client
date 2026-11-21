package dev.sleepclient.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/** Draws the 3D skin layers on the animated model of a player (runs inside the player's own transform, like the cape or the armour). */
public final class SkinLayersFeature extends RenderLayer<AvatarRenderState, PlayerModel> {

	public SkinLayersFeature(RenderLayerParent<AvatarRenderState, PlayerModel> ctx) {
		super(ctx);
	}

	@Override
	public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float yaw, float pitch) {
		int mask = ((LayerMask) state).sleep$getMask();
		if (mask == 0) {
			return;
		}
		Identifier id = state.skin.body().texturePath();
		var mesh = SkinLayers3D.mesh(id, state.skin.model() == PlayerModelType.SLIM, dev.sleepclient.mods.SkinLayers3DMod.instance.thickness10());
		if (mesh == null) {
			return;
		}
		PlayerModel model = getParentModel();
		model.setupAnim(state); // the pose of this frame (the same call the model makes when it is drawn)
		ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		int overlay = state.hasRedOverlay || state.deathTime > 0 ? LivingEntityRenderer.getOverlayCoords(state, 0F) : OverlayTexture.NO_OVERLAY;
		for (int g = 0; g < 6; g++) {
			if ((mask & SkinLayers3D.bit(g)) == 0) {
				continue;
			}
			float[] data = mesh.groups()[g];
			if (data.length == 0) {
				continue;
			}
			matrices.pushPose();
			parts[g].translateAndRotate(matrices);
			queue.submitCustomGeometry(matrices, RenderTypes.entityCutout(id), (e, vc) -> emit(vc, e, data, light, overlay));
			matrices.popPose();
		}
	}

	private static void emit(VertexConsumer vc, PoseStack.Pose e, float[] d, int light, int overlay) {
		for (int i = 0; i + 7 < d.length; i += 8) {
			vc.addVertex(e, d[i] / 16F, d[i + 1] / 16F, d[i + 2] / 16F).setColor(-1).setUv(d[i + 3], d[i + 4]).setOverlay(overlay).setLight(light)
					.setNormal(e, d[i + 5], d[i + 6], d[i + 7]);
		}
	}
}
