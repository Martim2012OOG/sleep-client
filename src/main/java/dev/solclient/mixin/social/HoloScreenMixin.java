package dev.solclient.mixin.social;

import dev.solclient.fx.Holo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The 3D GUI keeps the world clear: no dark veil and no blur behind a floating container screen. */
@Mixin(Screen.class)
public abstract class HoloScreenMixin {

	@Inject(method = "renderInGameBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void holo$noVeil(DrawContext ctx, CallbackInfo ci) {
		if (Holo.activeFor((Screen) (Object) this) && Holo.clearWorld()) {
			ci.cancel();
		}
	}

	@Inject(method = "applyBlur(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void holo$noBlur(DrawContext ctx, CallbackInfo ci) {
		if (Holo.activeFor((Screen) (Object) this)) {
			ci.cancel(); // the 3D GUI makes its own split of the GUI pass
		}
	}
}
