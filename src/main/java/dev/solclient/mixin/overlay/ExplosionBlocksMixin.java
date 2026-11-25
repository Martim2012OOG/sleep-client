package dev.solclient.mixin.overlay;

import dev.solclient.fx.ExplosionGuard;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ranked PvP: explosions (TNT, creepers, crystals...) only break the blocks the players placed, never the map. */
@Mixin(ExplosionImpl.class)
public abstract class ExplosionBlocksMixin {

	@Shadow
	private ServerWorld world;

	@Inject(method = "getBlocksToDestroy", at = @At("RETURN"), require = 0)
	private void caos$guard(CallbackInfoReturnable<List<BlockPos>> cir) {
		var g = ExplosionGuard.allow;
		if (g == null) {
			return;
		}
		try {
			cir.getReturnValue().removeIf(p -> !g.test(this.world, p));
		} catch (RuntimeException ignored) {
			// an immutable list: nothing to filter
		}
	}
}
