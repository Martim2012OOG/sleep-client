package dev.solclient.mixin.overlay;

import dev.solclient.fx.ExplosionGuard;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ranked PvP: explosions (TNT, creepers, crystals...) only break the blocks the players placed, never the map. */
@Mixin(ServerExplosion.class)
public abstract class ExplosionBlocksMixin {

	@Shadow
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), require = 0)
	private void sleep$guard(CallbackInfoReturnable<List<BlockPos>> cir) {
		var g = ExplosionGuard.allow;
		if (g == null) {
			return;
		}
		try {
			cir.getReturnValue().removeIf(p -> !g.test(this.level, p));
		} catch (RuntimeException ignored) {
			// an immutable list: nothing to filter
		}
	}
}
