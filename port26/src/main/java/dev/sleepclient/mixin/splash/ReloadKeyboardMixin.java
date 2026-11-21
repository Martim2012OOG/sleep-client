package dev.sleepclient.mixin.splash;

import dev.sleepclient.FastReload;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Instant pack reload: typing keeps working. */
@Mixin(KeyboardHandler.class)
public abstract class ReloadKeyboardMixin {

	@Redirect(method = "charTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"), require = 0)
	private Overlay sleep$overlay(Minecraft mc) {
		return FastReload.passive() ? null : dev.sleepclient.compat.Mc.overlay(mc);
	}
}
