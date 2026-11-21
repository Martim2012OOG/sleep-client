package dev.sleepclient.mixin.splash;

import dev.sleepclient.FastReload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Instant pack reload: clicks, scrolling and looking around keep working. */
@Mixin(MouseHandler.class)
public abstract class ReloadMouseMixin {

	@Redirect(method = {"onButton", "onScroll", "handleAccumulatedMovement"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"), require = 0)
	private Overlay sleep$overlay(Minecraft mc) {
		return FastReload.passive() ? null : dev.sleepclient.compat.Mc.overlay(mc);
	}
}
