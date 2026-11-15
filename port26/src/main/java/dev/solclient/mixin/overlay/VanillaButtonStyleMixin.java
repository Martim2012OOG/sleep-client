package dev.solclient.mixin.overlay;

import dev.solclient.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own buttons (world list, servers, options...) drawn in the Plegumo style. */
@Mixin(AbstractButton.class)
public abstract class VanillaButtonStyleMixin {

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$style(GuiGraphicsExtractor c, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on() || dev.solclient.SolClient.settings == null || !dev.solclient.SolClient.settings.restyleButtons.on()) {
			return;
		}
		AbstractWidget w = (AbstractWidget) (Object) this;
		boolean active = w.active;
		Gfx.button(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), active && (w.isHovered() || w.isFocused()), false);
		if (!active) {
			Gfx.rrect(c, w.getX() + 1, w.getY() + 1, w.getWidth() - 2, w.getHeight() - 2, 3, 0x88101218);
		}
		ci.cancel();
	}
}
