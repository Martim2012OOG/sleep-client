package dev.sleepclient.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class KuboHudHideMixin {

	@Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$titles(CallbackInfo ci) {
		if (dev.sleepclient.mods.TitlesMod.hidden()) ci.cancel();
	}

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$actionBar(CallbackInfo ci) {
		if (dev.sleepclient.mods.ActionBarMod.hidden()) ci.cancel();
	}
}
