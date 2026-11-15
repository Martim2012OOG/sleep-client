package dev.solclient.mixin.overlay;

import dev.solclient.ext.KuboApi;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells extensions a block was broken (3D Physics turns it into real 3D pieces). Only reads; an extension may replace the flat particles. */
@Mixin(ParticleEngine.class)
public abstract class BlockBreakHookMixin {

	@Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$blockBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
		try {
			if (KuboApi.blockBreak(pos, state)) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
