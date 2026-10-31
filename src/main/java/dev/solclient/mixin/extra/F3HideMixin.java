package dev.solclient.mixin.extra;

import dev.solclient.ext.KuboApi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Minecraft's wall of F3 text while an extension draws its own F3 (Better F3). */
@Mixin(DebugHud.class)
public class F3HideMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$hideF3(DrawContext c, CallbackInfo ci) {
		if (KuboApi.f3Replaced()) {
			ci.cancel();
		}
	}
}
