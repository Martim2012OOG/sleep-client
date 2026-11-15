package dev.solclient.mixin.perf;

import dev.solclient.mods.WavesMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the Realistic Waves mod when a block changes (so shores are rescanned). Does nothing while the mod is off. */
@Mixin(ClientLevel.class)
public class ClientWorldWaveMixin {
	@Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"), require = 0)
	private void sleep$waveBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		WavesMod.blockUpdate(pos, state);
	}
}
