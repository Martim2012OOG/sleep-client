package dev.solclient.mixin;

import dev.solclient.mods.TimeChangerMod;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class WorldMixin {

	/** Best effort: only does something on versions where World#getTimeOfDay still exists. */
	@Inject(method = "getOverworldClockTime", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$time(CallbackInfoReturnable<Long> cir) {
		if (TimeChangerMod.instance != null && TimeChangerMod.instance.enabled && ((Object) this) instanceof net.minecraft.client.multiplayer.ClientLevel) {
			cir.setReturnValue(TimeChangerMod.override(cir.getReturnValue()));
		}
	}
}
