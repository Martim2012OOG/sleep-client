package dev.sleepclient.mixin.overlay;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.sleepclient.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Text boxes (world search, server address...) with the Plegumo look instead of the black box with a white frame. */
@Mixin(EditBox.class)
public abstract class VanillaTextFieldStyleMixin {

	@Redirect(method = "extractWidgetRenderState", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void plegumo$field(GuiGraphicsExtractor c, RenderPipeline pipeline, Identifier tex, int x, int y, int w, int h) {
		if (!dev.sleepclient.gui.VanillaStyle.on()) {
			c.blitSprite(pipeline, tex, x, y, w, h);
			return;
		}
		boolean focused = ((AbstractWidget) (Object) this).isFocused();
		Gfx.rrect(c, x, y, w, h, 4, focused ? dev.sleepclient.gui.Theme.accent() : 0xFF0A0B10);
		Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, 3, 0xFF14161D);
	}
}
