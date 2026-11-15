package dev.solclient.mixin.extra;

import dev.solclient.fx.ForcedGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the entities in {@link ForcedGlow} show the glowing outline (through walls) on this client. Optional (require = 0). */
@Mixin(Minecraft.class)
public abstract class ForcedGlowMixin {

	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$glow(Entity e, CallbackInfoReturnable<Boolean> cir) {
		if (e != null && ForcedGlow.has(e)) {
			cir.setReturnValue(true);
		}
	}
}
