package dev.solclient.mixin;

import dev.solclient.mods.HitColourMod;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void sol$created(CallbackInfo ci) {
		HitColourMod.onCreate((Object) this);
	}
}
