package dev.sleepclient.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleepclient.cosmetic.SkinPreview;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skin previews in menus: gives them the renderer's buffers and draws the 3D cosmetics right after the model. */
@Mixin(GuiSkinRenderer.class)
public abstract class PlayerSkinRendererMixin {

	private static final String RENDER = "render(Lnet/minecraft/client/gui/render/state/special/PlayerSkinGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V";

	@Inject(method = RENDER, at = @At("HEAD"), require = 0)
	private void plegumo$begin(CallbackInfo ci) {
		SkinPreview.provider = ((SpecialGuiElementRendererAccessor) (Object) this).plegumo$vertexConsumers();
	}

	@Inject(method = RENDER, at = @At("RETURN"), require = 0)
	private void plegumo$end(CallbackInfo ci) {
		SkinPreview.provider = null;
	}

	// the model may be drawn through Model or PlayerEntityModel, with or without a colour: cover every form
	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
	private void plegumo$model4(Model model, PoseStack m, VertexConsumer vc, int light, int overlay) {
		model.renderToBuffer(m, vc, light, overlay);
		SkinPreview.afterModel(model, m, light);
	}

	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void plegumo$model5(Model model, PoseStack m, VertexConsumer vc, int light, int overlay, int color) {
		model.renderToBuffer(m, vc, light, overlay, color);
		SkinPreview.afterModel(model, m, light);
	}

	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/player/PlayerModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
	private void plegumo$player4(PlayerModel model, PoseStack m, VertexConsumer vc, int light, int overlay) {
		model.renderToBuffer(m, vc, light, overlay);
		SkinPreview.afterModel(model, m, light);
	}

	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/player/PlayerModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void plegumo$player5(PlayerModel model, PoseStack m, VertexConsumer vc, int light, int overlay, int color) {
		model.renderToBuffer(m, vc, light, overlay, color);
		SkinPreview.afterModel(model, m, light);
	}
}
