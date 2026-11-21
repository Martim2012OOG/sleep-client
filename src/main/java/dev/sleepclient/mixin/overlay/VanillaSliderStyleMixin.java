package dev.sleepclient.mixin.overlay;

import dev.sleepclient.Gfx;
import dev.sleepclient.SleepClient;
import dev.sleepclient.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own sliders (field of view, volume...) drawn like the Sleep buttons: flat track, coloured fill, raised knob. */
@Mixin(SliderWidget.class)
public abstract class VanillaSliderStyleMixin {

	@Shadow
	protected double value;

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$style(DrawContext c, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!dev.sleepclient.gui.VanillaStyle.on() || SleepClient.settings == null || !SleepClient.settings.restyleButtons.on()) {
			return;
		}
		ClickableWidget w = (ClickableWidget) (Object) this;
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
