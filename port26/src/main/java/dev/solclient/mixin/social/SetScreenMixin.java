package dev.solclient.mixin.social;

import dev.solclient.gui.Menus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens the Plegumo home / pause screens instead of the vanilla ones (no flicker). */
@Mixin(Minecraft.class)
public abstract class SetScreenMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$menus(Screen screen, CallbackInfo ci) {
		if (dev.solclient.gamemods.GameMods.blocked(screen)) {
			ci.cancel(); // menu of a mod you turned off
			return;
		}
		Screen loadingScreen = dev.solclient.ext.KuboApi.filterScreen(screen);
		if (loadingScreen != screen) {
			ci.cancel(); // an extension swaps it (e.g. the Practice loading screen)
			dev.solclient.compat.Mc.setScreen((Minecraft) (Object) this, loadingScreen);
			return;
		}
		Screen r = Menus.replace(screen);
		if (r != screen) {
			ci.cancel();
			dev.solclient.compat.Mc.setScreen((Minecraft) (Object) this, r);
		}
	}
}
