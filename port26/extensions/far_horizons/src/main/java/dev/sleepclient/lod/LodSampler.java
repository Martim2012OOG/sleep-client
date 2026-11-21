package dev.sleepclient.lod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reads a chunk (a real one, or a half-generated one from the integrated server) into a {@link LodStore.Chunk}. */
public final class LodSampler {

	private LodSampler() {
	}

	public static LodStore.Chunk sample(Level world, ChunkAccess chunk, boolean worldGen) {
		try {
			return doSample(world, chunk, worldGen);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static LodStore.Chunk doSample(Level world, ChunkAccess chunk, boolean worldGen) {
		Heightmap.Types surf = worldGen ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE;
		Heightmap.Types floor = worldGen ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.OCEAN_FLOOR;
		int sx = chunk.getPos().getMinBlockX(), sz = chunk.getPos().getMinBlockZ();
		int bottom = chunk.getMinY();
		var colors = Minecraft.getInstance().getBlockColors();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		LodStore.Chunk out = new LodStore.Chunk();
		boolean any = false;
		for (int lz = 0; lz < 4; lz++) {
			for (int lx = 0; lx < 4; lx++) {
				int idx = lz * 4 + lx;
				int px = lx * 4 + 2, pz = lz * 4 + 2;
				int x = sx + px, z = sz + pz;
				int y = chunk.getHeight(surf, px, pz) + 1;
				BlockState st = chunk.getBlockState(m.set(x, y, z));
				int guard = 0;
				while (st.isAir() && y > bottom && guard++ < 8) {
					y--;
					st = chunk.getBlockState(m.set(x, y, z));
				}
				if (st.isAir() || y <= bottom) {
					continue;
				}
				int color;
				boolean water = !st.getFluidState().isEmpty();
				if (water) {
					int fy = chunk.getHeight(floor, px, pz) + 1;
					m.set(x, fy, z);
					BlockState fs = chunk.getBlockState(m);
					int g2 = 0;
					while ((fs.isAir() || !fs.getFluidState().isEmpty()) && fy > bottom && g2++ < 48) {
						fy--;
						fs = chunk.getBlockState(m.set(x, fy, z));
					}
					int depth = Math.max(1, y - fy);
					int floorCol = scale(fs.getMapColor(world, m).col, 0.75F);
					m.set(x, y, z);
					int wc = -1;
					try {
						wc = dev.sleepclient.compat.BlockTint.colorAny(Blocks.WATER.defaultBlockState(), world, m, 0);
					} catch (RuntimeException ignored) {
					}
					if (wc == -1) {
						wc = 0x3F76E4;
					}
					color = lerp(floorCol, wc & 0xFFFFFF, Math.min(1F, 0.5F + depth / 20F));
				} else {
					m.set(x, y, z);
					color = st.getMapColor(world, m).col;
					int tint = -1;
					try {
						tint = dev.sleepclient.compat.BlockTint.colorAny(st, world, m, 0);
					} catch (RuntimeException ignored) {
					}
					if (tint != -1) {
						color = scale(tint & 0xFFFFFF, st.is(BlockTags.LEAVES) ? 0.78F : 0.9F);
					}
				}
				out.h[idx] = (short) y;
				out.rgb[idx] = color & 0xFFFFFF;
				if (water) {
					out.water |= 1 << idx;
				}
				any = true;
			}
		}
		return any ? out : null;
	}

	private static int scale(int c, float k) {
		int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
		return r << 16 | g << 8 | b;
	}

	private static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}
}
