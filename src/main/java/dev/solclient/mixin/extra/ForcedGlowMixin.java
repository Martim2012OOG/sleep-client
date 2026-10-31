package dev.solclient.mixin.extra;

import dev.solclient.fx.ForcedGlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the entities in {@link ForcedGlow} show the glowing outline (through walls) on this client. Optional (require = 0). */
@Mixin(MinecraftClient.class)
public abstract class ForcedGlowMixin {

	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$glow(Entity e, CallbackInfoReturnable<Boolean> cir) {
		if (e != null && ForcedGlow.has(e.getId())) {
			cir.setReturnValue(true);
		}
	}
}
