package dev.sleepclient.mixin.social;

import dev.sleepclient.gui.Menus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens the Plegumo home / pause screens instead of the vanilla ones (no flicker). */
@Mixin(MinecraftClient.class)
public abstract class SetScreenMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$menus(Screen screen, CallbackInfo ci) {
		if (dev.sleepclient.gamemods.GameMods.blocked(screen)) {
			ci.cancel(); // menu of a mod you turned off
			return;
		}
		Screen loadingScreen = dev.sleepclient.ext.KuboApi.filterScreen(screen);
		if (loadingScreen != screen) {
			ci.cancel(); // an extension swaps it (e.g. the Practice loading screen)
			((MinecraftClient) (Object) this).setScreen(loadingScreen);
			return;
		}
		Screen r = Menus.replace(screen);
		if (r != screen) {
			ci.cancel();
			((MinecraftClient) (Object) this).setScreen(r);
		}
	}
}
