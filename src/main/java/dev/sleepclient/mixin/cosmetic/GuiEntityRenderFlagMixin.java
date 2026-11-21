package dev.sleepclient.mixin.cosmetic;

import net.minecraft.client.gui.render.EntityGuiElementRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks "drawing an entity inside a menu": the Locker try-on applies only there, never to you in the world. */
@Mixin(EntityGuiElementRenderer.class)
public abstract class GuiEntityRenderFlagMixin {

	private static final String RENDER = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V";

	@Inject(method = RENDER, at = @At("HEAD"), require = 0)
	private void plegumo$begin(CallbackInfo ci) {
		dev.sleepclient.cosmetic.Cosmetics.inGui = true;
	}

	@Inject(method = RENDER, at = @At("RETURN"), require = 0)
	private void plegumo$end(CallbackInfo ci) {
		dev.sleepclient.cosmetic.Cosmetics.inGui = false;
	}
}
