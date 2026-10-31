package dev.solclient.mixin.extra;

import dev.solclient.mods.BactroTweaksMod;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.BlindnessEffectFogModifier;
import net.minecraft.client.render.fog.DarknessEffectFogModifier;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.PowderSnowFogModifier;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Bactro Tweaks switch each kind of fog off. Optional (require = 0). */
@Mixin({BlindnessEffectFogModifier.class, DarknessEffectFogModifier.class, LavaFogModifier.class, WaterFogModifier.class, PowderSnowFogModifier.class, AtmosphericFogModifier.class})
public abstract class FogOffMixin {

	@Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$fogOff(CameraSubmersionType type, Entity e, CallbackInfoReturnable<Boolean> cir) {
		if (BactroTweaksMod.fogOff(this)) {
			cir.setReturnValue(false);
		}
	}
}
