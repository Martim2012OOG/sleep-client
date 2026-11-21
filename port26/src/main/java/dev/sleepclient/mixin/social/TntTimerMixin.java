package dev.sleepclient.mixin.social;

import dev.sleepclient.mods.TntTimerMod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Floating timer above lit TNT: turns on the vanilla nametag for it and puts the countdown as its name. */
@Mixin(EntityRenderer.class)
public abstract class TntTimerMixin {

	@Inject(method = "shouldShowName", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$tntLabel(Entity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && TntTimerMod.shows(entity, squaredDistance)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$tntName(Entity entity, CallbackInfoReturnable<Component> cir) {
		if (entity instanceof PrimedTnt t && TntTimerMod.nametagStyle() && t.getFuse() > 0) {
			cir.setReturnValue(TntTimerMod.label(t));
		}
	}
}
