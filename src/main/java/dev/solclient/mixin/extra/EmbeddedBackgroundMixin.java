package dev.solclient.mixin.extra;

import dev.solclient.gui.EmbedFlag;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No blur / dark layer / panorama from a settings screen that is shown inside the Caos menu. */
@Mixin(Screen.class)
public abstract class EmbeddedBackgroundMixin {

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$noBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (EmbedFlag.rendering) {
			ci.cancel();
		}
	}
}
