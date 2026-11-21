package dev.sleepclient.mixin.overlay;

import dev.sleepclient.ext.KuboApi;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells extensions a block was broken (3D Physics turns it into real 3D pieces). Only reads; an extension may replace the flat particles. */
@Mixin(ParticleManager.class)
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
