package dev.solclient.mixin.extra;

import dev.solclient.mods.BactroTweaksMod;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.PowderedSnowFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Bactro Tweaks switch each kind of fog off. Optional (require = 0). */
@Mixin({BlindnessFogEnvironment.class, DarknessFogEnvironment.class, LavaFogEnvironment.class, WaterFogEnvironment.class, PowderedSnowFogEnvironment.class, AtmosphericFogEnvironment.class})
public abstract class FogOffMixin {

	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$fogOff(FogType type, Entity e, CallbackInfoReturnable<Boolean> cir) {
		if (BactroTweaksMod.fogOff(this)) {
			cir.setReturnValue(false);
		}
	}
}
