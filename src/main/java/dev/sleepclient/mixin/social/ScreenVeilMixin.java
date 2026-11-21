package dev.sleepclient.mixin.social;

import dev.sleepclient.fx.ScreenClear;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D Screens, "Keep The World Clear": no dark veil and no blur behind the floating panel. */
@Mixin(Screen.class)
public abstract class ScreenVeilMixin {

	@Inject(method = "renderInGameBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$noVeil(DrawContext ctx, CallbackInfo ci) {
		if (ScreenClear.screen == (Object) this) {
			ci.cancel();
		}
	}

	@Inject(method = "applyBlur(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$noBlur(DrawContext ctx, CallbackInfo ci) {
		if (ScreenClear.screen == (Object) this) {
			ci.cancel();
		}
	}
}
