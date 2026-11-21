package dev.sleepclient.addons;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * Waits until the chunks of an area are really loaded before an add-on builds in them.
 * A fixed delay is not enough: on slow devices (phones, low-end PCs) generating the chunks takes longer, the
 * /fill commands then fail silently and the player ends up in an empty sky with nothing to stand on.
 */
public final class ChunkWait {

	/** After this many ticks of waiting, missing chunks are generated on the spot (one per tick) so it can never hang. */
	private static final int FORCE_AFTER = 100;

	private ChunkWait() {
	}

	/**
	 * @param waited ticks spent waiting so far
	 * @return true when every chunk touching the block area (x1,z1)-(x2,z2) is fully loaded
	 */
	public static boolean ready(ServerWorld w, int x1, int z1, int x2, int z2, int waited) {
		boolean allLoaded = true;
		boolean forcedOne = false;
		for (int cx = Math.min(x1, x2) >> 4; cx <= Math.max(x1, x2) >> 4; cx++) {
			for (int cz = Math.min(z1, z2) >> 4; cz <= Math.max(z1, z2) >> 4; cz++) {
				if (w.getChunk(cx, cz, ChunkStatus.FULL, false) != null) {
					continue;
				}
				allLoaded = false;
				if (waited >= FORCE_AFTER && !forcedOne) {
					forcedOne = true;
					w.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
		}
		return allLoaded;
	}
}
