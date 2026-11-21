package dev.sleepclient.mixin;

import dev.sleepclient.mods.ChristmasWorldMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Christmas World: it snows in every biome (only what you see: only on the render thread, never on the game's server). */
@Mixin(Biome.class)
public class KuboXmasMixin {

	@Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$snowEverywhere(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
		if (ChristmasWorldMod.snowing() && net.minecraft.client.Minecraft.getInstance().isSameThread()) {
			cir.setReturnValue(Biome.Precipitation.SNOW);
		}
	}
}
