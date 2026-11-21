package dev.sleepclient.mixin.cosmetic;

import dev.sleepclient.cosmetic.SkinPreview;
import net.minecraft.client.gui.render.PlayerSkinGuiElementRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skin previews in menus: gives them the renderer's buffers and draws the 3D cosmetics right after the model. */
@Mixin(PlayerSkinGuiElementRenderer.class)
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
			target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
	private void plegumo$model4(Model model, MatrixStack m, VertexConsumer vc, int light, int overlay) {
		model.render(m, vc, light, overlay);
		SkinPreview.afterModel(model, m, light);
	}

	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
	private void plegumo$model5(Model model, MatrixStack m, VertexConsumer vc, int light, int overlay, int color) {
		model.render(m, vc, light, overlay, color);
		SkinPreview.afterModel(model, m, light);
	}

	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/model/PlayerEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
	private void plegumo$player4(PlayerEntityModel model, MatrixStack m, VertexConsumer vc, int light, int overlay) {
		model.render(m, vc, light, overlay);
		SkinPreview.afterModel(model, m, light);
	}

	@Redirect(method = RENDER, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/model/PlayerEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
	private void plegumo$player5(PlayerEntityModel model, MatrixStack m, VertexConsumer vc, int light, int overlay, int color) {
		model.render(m, vc, light, overlay, color);
		SkinPreview.afterModel(model, m, light);
	}
}
