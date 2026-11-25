package dev.solclient.mixin.splash;

import dev.solclient.FastReload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Instant pack reload: clicks, scrolling and looking around keep working. */
@Mixin(Mouse.class)
public abstract class ReloadMouseMixin {

	@Redirect(method = {"onMouseButton", "onMouseScroll", "tick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;"), require = 0)
	private Overlay caos$overlay(MinecraftClient mc) {
		return FastReload.passive() ? null : mc.getOverlay();
	}
}
