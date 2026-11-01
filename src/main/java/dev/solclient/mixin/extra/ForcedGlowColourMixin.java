package dev.solclient.mixin.extra;

import dev.solclient.fx.ForcedGlow;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The colour of the forced glowing outline. Optional (require = 0). */
@Mixin(Entity.class)
public abstract class ForcedGlowColourMixin {

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$colour(CallbackInfoReturnable<Integer> cir) {
		Integer c = ForcedGlow.colour((Entity) (Object) this);
		if (c != null) {
			cir.setReturnValue(c);
		}
	}
}
