package dev.sleepclient.mixin.extra;

import dev.sleepclient.gui.EmbedFlag;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No blur / dark layer / panorama from a settings screen that is shown inside the Sleep menu. */
@Mixin(Screen.class)
public abstract class EmbeddedBackgroundMixin {

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$noBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (EmbedFlag.rendering) {
			ci.cancel();
		}
	}
}
