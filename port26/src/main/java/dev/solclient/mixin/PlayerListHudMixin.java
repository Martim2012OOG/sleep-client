package dev.solclient.mixin;

import dev.solclient.mods.TabListMod;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$hideVanillaTab(CallbackInfo ci) {
		if (TabListMod.replacesVanilla()) {
			ci.cancel();
		}
	}
}
