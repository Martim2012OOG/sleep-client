package dev.solclient.mixin.social;

import dev.solclient.mods.TntTimerMod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Floating timer above lit TNT: turns on the vanilla nametag for it and puts the countdown as its name. */
@Mixin(EntityRenderer.class)
public abstract class TntTimerMixin {

	@Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$tntLabel(Entity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && TntTimerMod.shows(entity, squaredDistance)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$tntName(Entity entity, CallbackInfoReturnable<Text> cir) {
		if (entity instanceof TntEntity t && TntTimerMod.nametagStyle() && t.getFuse() > 0) {
			cir.setReturnValue(TntTimerMod.label(t));
		}
	}
}
