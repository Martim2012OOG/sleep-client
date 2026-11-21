package dev.sleepclient.mixin;

import dev.sleepclient.mods.ChristmasWorldMod;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Christmas World: grass and leaves turn frosty white (the colour they are tinted with, so every texture pack works). */
@Mixin(BiomeColors.class)
public class KuboXmasColoursMixin {

	@Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$frostGrass(BlockAndTintGetter world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ChristmasWorldMod.frost(cir.getReturnValue()));
	}

	@Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$frostLeaves(BlockAndTintGetter world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ChristmasWorldMod.frost(cir.getReturnValue()));
	}

	@Inject(method = "getAverageDryFoliageColor", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$frostDry(BlockAndTintGetter world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ChristmasWorldMod.frost(cir.getReturnValue()));
	}
}
