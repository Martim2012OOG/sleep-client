package dev.sleepclient.mixin.extra;

import dev.sleepclient.mods.TweaksPlusMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hit Delay Fix: missing a hit no longer blocks your next clicks for half a second. */
@Mixin(MinecraftClient.class)
public class HitDelayFixMixin {

	@Shadow
	protected int attackCooldown;

	@Inject(method = "doAttack", at = @At("RETURN"), require = 0)
	private void kubo$noMissDelay(CallbackInfoReturnable<Boolean> cir) {
		if (TweaksPlusMod.hitDelayFix()) {
			attackCooldown = 0;
		}
	}
}
