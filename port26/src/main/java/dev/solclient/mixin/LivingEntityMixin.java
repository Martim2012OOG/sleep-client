package dev.solclient.mixin;

import dev.solclient.mods.OverlayMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

	/** Only on the client thread, so the integrated server logic is not affected. */
	@Inject(method = "getArrowCount", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$hideStuckArrows(CallbackInfoReturnable<Integer> cir) {
		if (!OverlayMod.stuckArrows() && Minecraft.getInstance().isSameThread()) {
			cir.setReturnValue(0);
		}
	}
}
