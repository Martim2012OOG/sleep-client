package dev.solclient.mixin.overlay;

import dev.solclient.Gfx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own buttons (world list, servers, options...) drawn in the Plegumo style. */
@Mixin(PressableWidget.class)
public abstract class VanillaButtonStyleMixin {

	@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$style(DrawContext c, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on() || dev.solclient.SolClient.settings == null || !dev.solclient.SolClient.settings.restyleButtons.on()) {
			return;
		}
		ClickableWidget w = (ClickableWidget) (Object) this;
		boolean active = w.active;
		Gfx.button(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), active && (w.isHovered() || w.isFocused()), false);
		if (!active) {
			Gfx.rrect(c, w.getX() + 1, w.getY() + 1, w.getWidth() - 2, w.getHeight() - 2, 3, 0x88101218);
		}
		ci.cancel();
	}
}
