package dev.solclient.mixin;

import dev.solclient.mods.ChristmasWorldMod;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Christmas World: grass and leaves turn frosty white (the colour they are tinted with, so every texture pack works). */
@Mixin(BiomeColors.class)
public class KuboXmasColoursMixin {

	@Inject(method = "getGrassColor", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$frostGrass(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ChristmasWorldMod.frost(cir.getReturnValue()));
	}

	@Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$frostLeaves(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ChristmasWorldMod.frost(cir.getReturnValue()));
	}

	@Inject(method = "getDryFoliageColor", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$frostDry(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ChristmasWorldMod.frost(cir.getReturnValue()));
	}
}
