package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import net.minecraft.core.particles.ParticleTypes;

/** Shows the corners of the chunk you stand in. */
public class ChunkBordersMod extends Mod {

	private int t;

	public ChunkBordersMod() {
		super("Chunk Borders", "chunk_borders", "Shows the edges of the chunk you are in.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null || ++t % 4 != 0) return;
		int cx = ((int) Math.floor(p.getX()) >> 4) << 4, cz = ((int) Math.floor(p.getZ()) >> 4) << 4;
		double y0 = p.getY() - 4;
		for (int i = 0; i <= 16; i += 2) {
			for (double y = y0; y < y0 + 10; y += 2) {
				Fx.spawn(ParticleTypes.ELECTRIC_SPARK, cx + i, y, cz, 0, 0, 0);
				Fx.spawn(ParticleTypes.ELECTRIC_SPARK, cx + i, y, cz + 16, 0, 0, 0);
				Fx.spawn(ParticleTypes.ELECTRIC_SPARK, cx, y, cz + i, 0, 0, 0);
				Fx.spawn(ParticleTypes.ELECTRIC_SPARK, cx + 16, y, cz + i, 0, 0, 0);
			}
		}
	}
}
