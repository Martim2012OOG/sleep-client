package dev.solclient.mixin.overlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The old stand-alone "Death Animations +" screen (it opened with the O key) is gone: the Locker has the same thing. */
@Mixin(MinecraftClient.class)
public abstract class DeathAnimScreenBlockMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noOldDeathScreen(Screen screen, CallbackInfo ci) {
		if (screen != null && screen.getClass().getName().equals("com.baom5.deathanimation.client.DeathAnimationsScreen")) {
			ci.cancel();
		}
	}
}
