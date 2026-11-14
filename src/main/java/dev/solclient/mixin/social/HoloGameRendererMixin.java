package dev.solclient.mixin.social;

import dev.solclient.fx.Holo;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The floating GUI splits the GUI pass with the "blur" marker but must not really blur the world. */
@Mixin(GameRenderer.class)
public abstract class HoloGameRendererMixin {

	@Inject(method = "renderBlur()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void holo$noBlur(CallbackInfo ci) {
		if (Holo.pending) {
			ci.cancel();
		}
	}
}
