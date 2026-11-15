package dev.solclient.mixin.overlay;

import dev.solclient.Gfx;
import dev.solclient.SolClient;
import dev.solclient.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own sliders (field of view, volume...) drawn like the Sleep buttons: flat track, coloured fill, raised knob. */
@Mixin(AbstractSliderButton.class)
public abstract class VanillaSliderStyleMixin {

	@Shadow
	protected double value;

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$style(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on() || SolClient.settings == null || !SolClient.settings.restyleButtons.on()) {
			return;
		}
		AbstractWidget w = (AbstractWidget) (Object) this;
		int x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
		boolean hot = w.active && (w.isHovered() || w.isFocused());
		double v = Math.max(0D, Math.min(1D, value));
		Gfx.buttonBody(c, x, y, bw, bh, Gfx.BTN_PRESSED, true);
		int fillW = (int) Math.round((bw - 6) * v);
		if (fillW > 0) {
			Gfx.rrect(c, x + 3, y + 4, fillW, bh - 7, 2, Gfx.blend(Theme.accent() | 0xFF000000, 0xFF000000, 0.35F));
		}
		int kw = 8, kx = x + 1 + (int) Math.round((bw - 2 - kw) * v);
		Gfx.buttonBody(c, kx, y, kw, bh, hot ? Gfx.BTN_HOVER : Gfx.BTN_BODY, false);
		String msg = w.getMessage().getString();
		Gfx.textFit(c, msg, x + bw / 2, bw - 18, y + (bh - 9) / 2, w.active ? 0xFFFFFFFF : 0xFF9A9CA8);
		ci.cancel();
	}
}
