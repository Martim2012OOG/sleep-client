package dev.solclient.mixin.splash;

import dev.solclient.FastReload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Instant pack reload: the key bindings (inventory, chat, moving...) keep working. The overlay itself still ticks and finishes the reload. */
@Mixin(Minecraft.class)
public abstract class ReloadInputMixin {

	@Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;overlay:Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 2), require = 0)
	private Overlay caos$tickInput(Minecraft mc) {
		return FastReload.passive() ? null : dev.solclient.compat.Mc.overlay(mc);
	}

	@Redirect(method = "handleKeybinds", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;overlay:Lnet/minecraft/client/gui/screens/Overlay;"), require = 0)
	private Overlay caos$keys(Minecraft mc) {
		return FastReload.passive() ? null : dev.solclient.compat.Mc.overlay(mc);
	}
}
