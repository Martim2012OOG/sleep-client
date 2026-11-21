package dev.sleepclient.mixin.perf;

import dev.sleepclient.mods.WavesMod;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the Realistic Waves mod when a block changes (so shores are rescanned). Does nothing while the mod is off. */
@Mixin(ClientWorld.class)
public class ClientWorldWaveMixin {
	@Inject(method = "handleBlockUpdate", at = @At("TAIL"), require = 0)
	private void sleep$waveBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		WavesMod.blockUpdate(pos, state);
	}
}
