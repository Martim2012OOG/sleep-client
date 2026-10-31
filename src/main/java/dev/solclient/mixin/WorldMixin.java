package dev.solclient.mixin;

import dev.solclient.mods.TimeChangerMod;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin {

	/** Best effort: only does something on versions where World#getTimeOfDay still exists. */
	@Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$time(CallbackInfoReturnable<Long> cir) {
		if (TimeChangerMod.instance != null && TimeChangerMod.instance.enabled && ((Object) this) instanceof net.minecraft.client.world.ClientWorld) {
			cir.setReturnValue(TimeChangerMod.override(cir.getReturnValue()));
		}
	}
}
