package dev.solclient.mixin;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class KuboHudHideMixin {

	@Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$titles(CallbackInfo ci) {
		if (dev.solclient.mods.TitlesMod.hidden()) ci.cancel();
	}

	@Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$actionBar(CallbackInfo ci) {
		if (dev.solclient.mods.ActionBarMod.hidden()) ci.cancel();
	}
}
