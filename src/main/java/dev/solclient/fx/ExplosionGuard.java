package dev.solclient.fx;

import java.util.function.BiPredicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Lets an extension (Ranked PvP) decide which blocks an explosion may destroy: set to null when nothing is protected. */
public final class ExplosionGuard {

	/** (world, block) -> may this block be destroyed by an explosion? */
	public static volatile BiPredicate<ServerWorld, BlockPos> allow;

	private ExplosionGuard() {
	}
}
