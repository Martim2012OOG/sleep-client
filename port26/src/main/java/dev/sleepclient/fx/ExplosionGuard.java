package dev.sleepclient.fx;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Lets an extension (Ranked PvP) decide which blocks an explosion may destroy: set to null when nothing is protected. */
public final class ExplosionGuard {

	/** (world, block) -> may this block be destroyed by an explosion? */
	public static volatile BiPredicate<ServerLevel, BlockPos> allow;

	private ExplosionGuard() {
	}
}
