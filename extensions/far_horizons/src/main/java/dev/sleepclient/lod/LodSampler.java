package dev.sleepclient.lod;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Reads a chunk (a real one, or a half-generated one from the integrated server) into a {@link LodStore.Chunk}. */
public final class LodSampler {

	private LodSampler() {
	}

	public static LodStore.Chunk sample(World world, Chunk chunk, boolean worldGen) {
		try {
			return doSample(world, chunk, worldGen);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static LodStore.Chunk doSample(World world, Chunk chunk, boolean worldGen) {
		Heightmap.Type surf = worldGen ? Heightmap.Type.WORLD_SURFACE_WG : Heightmap.Type.WORLD_SURFACE;
		Heightmap.Type floor = worldGen ? Heightmap.Type.OCEAN_FLOOR_WG : Heightmap.Type.OCEAN_FLOOR;
		int sx = chunk.getPos().getStartX(), sz = chunk.getPos().getStartZ();
		int bottom = chunk.getBottomY();
		var colors = MinecraftClient.getInstance().getBlockColors();
		BlockPos.Mutable m = new BlockPos.Mutable();
		LodStore.Chunk out = new LodStore.Chunk();
		boolean any = false;
		for (int lz = 0; lz < 4; lz++) {
			for (int lx = 0; lx < 4; lx++) {
				int idx = lz * 4 + lx;
				int px = lx * 4 + 2, pz = lz * 4 + 2;
				int x = sx + px, z = sz + pz;
				int y = chunk.sampleHeightmap(surf, px, pz) + 1;
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
					int fy = chunk.sampleHeightmap(floor, px, pz) + 1;
					m.set(x, fy, z);
					BlockState fs = chunk.getBlockState(m);
					int g2 = 0;
					while ((fs.isAir() || !fs.getFluidState().isEmpty()) && fy > bottom && g2++ < 48) {
						fy--;
						fs = chunk.getBlockState(m.set(x, fy, z));
					}
					int depth = Math.max(1, y - fy);
					int floorCol = scale(fs.getMapColor(world, m).color, 0.75F);
					m.set(x, y, z);
					int wc = -1;
					try {
						wc = colors.getColor(Blocks.WATER.getDefaultState(), world, m, 0);
					} catch (RuntimeException ignored) {
					}
					if (wc == -1) {
						wc = 0x3F76E4;
					}
					color = lerp(floorCol, wc & 0xFFFFFF, Math.min(1F, 0.5F + depth / 20F));
				} else {
					m.set(x, y, z);
					color = st.getMapColor(world, m).color;
					int tint = -1;
					try {
						tint = colors.getColor(st, world, m, 0);
					} catch (RuntimeException ignored) {
					}
					if (tint != -1) {
						color = scale(tint & 0xFFFFFF, st.isIn(BlockTags.LEAVES) ? 0.78F : 0.9F);
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
