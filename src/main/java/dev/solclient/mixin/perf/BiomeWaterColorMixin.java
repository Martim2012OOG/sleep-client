package dev.solclient.mixin.perf;

import dev.solclient.mods.RealisticLiquidsMod;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Realistic Liquids water colour, applied where the colour is born: both vanilla and Sodium take the water colour of a
 * block from Biome.getWaterColor(), so this works with either renderer (the old fluid-handler hook was ignored by some of them).
 */
@Mixin(Biome.class)
public class BiomeWaterColorMixin {
	@Inject(method = "getWaterColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void sleep$water(CallbackInfoReturnable<Integer> cir) {
		RealisticLiquidsMod.hookHits = Math.min(RealisticLiquidsMod.hookHits + 1, 1_000_000);
		int c = cir.getReturnValueI();
		int t = RealisticLiquidsMod.tint(c & 0xFFFFFF);
		if (t != (c & 0xFFFFFF)) {
			cir.setReturnValue(t);
		}
	}
}
