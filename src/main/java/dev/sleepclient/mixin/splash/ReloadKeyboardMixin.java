package dev.sleepclient.mixin.splash;

import dev.sleepclient.FastReload;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Instant pack reload: typing keeps working. */
@Mixin(Keyboard.class)
public abstract class ReloadKeyboardMixin {

	@Redirect(method = "onChar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;"), require = 0)
	private Overlay sleep$overlay(MinecraftClient mc) {
		return FastReload.passive() ? null : mc.getOverlay();
	}
}
