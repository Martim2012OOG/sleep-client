package dev.solclient.mixin.overlay;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.solclient.Gfx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Text boxes (world search, server address...) with the Plegumo look instead of the black box with a white frame. */
@Mixin(TextFieldWidget.class)
public abstract class VanillaTextFieldStyleMixin {

	@Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))
	private void plegumo$field(DrawContext c, RenderPipeline pipeline, Identifier tex, int x, int y, int w, int h) {
		if (!dev.solclient.gui.VanillaStyle.on()) {
			c.drawGuiTexture(pipeline, tex, x, y, w, h);
			return;
		}
		boolean focused = ((ClickableWidget) (Object) this).isFocused();
		Gfx.rrect(c, x, y, w, h, 4, focused ? dev.solclient.gui.Theme.accent() : 0xFF0A0B10);
		Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, 3, 0xFF14161D);
	}
}
