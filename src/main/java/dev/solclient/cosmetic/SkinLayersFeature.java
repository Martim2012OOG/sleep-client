package dev.solclient.cosmetic;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Identifier;

/** Draws the 3D skin layers on the animated model of a player (runs inside the player's own transform, like the cape or the armour). */
public final class SkinLayersFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

	public SkinLayersFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) {
		super(ctx);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float yaw, float pitch) {
		int mask = ((LayerMask) state).sleep$getMask();
		if (mask == 0) {
			return;
		}
		Identifier id = state.skinTextures.body().texturePath();
		var mesh = SkinLayers3D.mesh(id, state.skinTextures.model() == PlayerSkinType.SLIM, dev.solclient.mods.SkinLayers3DMod.instance.thickness10());
		if (mesh == null) {
			return;
		}
		PlayerEntityModel model = getContextModel();
		model.setAngles(state); // the pose of this frame (the same call the model makes when it is drawn)
		ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		int overlay = state.hurt || state.deathTime > 0 ? LivingEntityRenderer.getOverlay(state, 0F) : OverlayTexture.DEFAULT_UV;
		for (int g = 0; g < 6; g++) {
			if ((mask & SkinLayers3D.bit(g)) == 0) {
				continue;
			}
			float[] data = mesh.groups()[g];
			if (data.length == 0) {
				continue;
			}
			matrices.push();
			parts[g].applyTransform(matrices);
			queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(id), (e, vc) -> emit(vc, e, data, light, overlay));
			matrices.pop();
		}
	}

	private static void emit(VertexConsumer vc, MatrixStack.Entry e, float[] d, int light, int overlay) {
		for (int i = 0; i + 7 < d.length; i += 8) {
			vc.vertex(e, d[i] / 16F, d[i + 1] / 16F, d[i + 2] / 16F).color(-1).texture(d[i + 3], d[i + 4]).overlay(overlay).light(light)
					.normal(e, d[i + 5], d[i + 6], d[i + 7]);
		}
	}
}
