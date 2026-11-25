package dev.solclient.mixin.splash;

import dev.solclient.FastReload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Instant pack reload: the key bindings (inventory, chat, moving...) keep working. The overlay itself still ticks and finishes the reload. */
@Mixin(MinecraftClient.class)
public abstract class ReloadInputMixin {

	@Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;overlay:Lnet/minecraft/client/gui/screen/Overlay;", ordinal = 2), require = 0)
	private Overlay caos$tickInput(MinecraftClient mc) {
		return FastReload.passive() ? null : mc.getOverlay();
	}

	@Redirect(method = "handleInputEvents", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;overlay:Lnet/minecraft/client/gui/screen/Overlay;"), require = 0)
	private Overlay caos$keys(MinecraftClient mc) {
		return FastReload.passive() ? null : mc.getOverlay();
	}
}
