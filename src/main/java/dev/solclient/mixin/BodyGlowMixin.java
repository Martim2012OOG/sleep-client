package dev.solclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Body Glow: outline around nearby players. */
@Mixin(MinecraftClient.class)
public class BodyGlowMixin {

	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (dev.solclient.mods.BodyGlowMod.glows(entity) || dev.solclient.mods.DurabilityOverlayMod.wants(entity)) {
			cir.setReturnValue(true);
		}
	}
}
