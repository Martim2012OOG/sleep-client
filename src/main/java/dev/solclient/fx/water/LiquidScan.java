package dev.solclient.fx.water;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Finds the top surfaces of water and lava around the player, a few chunk sections per tick (round robin), so the game never
 * stutters. Sections without any liquid are skipped by looking at their palette only. For every surface block it keeps the
 * height of its four corners (smooth slopes, like vanilla), the current, how much shore is around it, and its light.
 * Waterfall landings are kept too, for the spray.
 */
final class LiquidScan {

	/** One surface block: x, y, z, 4 corner heights, flow x / z, shore, light (packed), lava flag. */
	static final int STRIDE = 12;

	static final class Section {
		float[] quads = new float[0];
		int count;
		/** Waterfall landings: x, y, z (3 floats each). */
		float[] falls = new float[0];
		int fallCount;
		/** Water blocks seen last time (to notice new water: a bucket, a broken dam). */
		java.util.BitSet water;
		long scannedAt;
	}

	final Map<Long, Section> sections = new HashMap<>();
	/** New water that appeared since the last scan of its section (x, y, z), for a splash. */
	final List<double[]> appeared = new ArrayList<>();
	private final List<Long> order = new ArrayList<>();
	private int cursor;
	private int lastCx = Integer.MIN_VALUE, lastCz, lastSy, lastRadius;

	/** Scans the next few sections. radius in blocks. */
	void tick(ClientWorld w, Vec3d cam, int radius, int perTick) {
		int cx = (int) Math.floor(cam.x) >> 4, cz = (int) Math.floor(cam.z) >> 4, sy = (int) Math.floor(cam.y) >> 4;
		int cr = (radius + 15) >> 4;
		if (cx != lastCx || cz != lastCz || sy != lastSy || radius != lastRadius) {
			lastCx = cx;
			lastCz = cz;
			lastSy = sy;
			lastRadius = radius;
			order.clear();
			int minY = w.getBottomSectionCoord(), maxY = w.getTopSectionCoord() - 1;
			// nearest first
			for (int r = 0; r <= cr; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						for (int dy = -2; dy <= 1; dy++) {
							int y = sy + dy;
							if (y >= minY && y <= maxY) {
								order.add(ChunkSectionPos.asLong(cx + dx, y, cz + dz));
							}
						}
					}
				}
			}
			// forget sections that are now too far
			for (Iterator<Long> it = sections.keySet().iterator(); it.hasNext();) {
				long k = it.next();
				if (Math.abs(ChunkSectionPos.unpackX(k) - cx) > cr + 1 || Math.abs(ChunkSectionPos.unpackZ(k) - cz) > cr + 1
						|| Math.abs(ChunkSectionPos.unpackY(k) - sy) > 3) {
					it.remove();
				}
			}
			cursor = 0;
		}
		if (order.isEmpty()) {
			return;
		}
		for (int i = 0; i < perTick; i++) {
			cursor = (cursor + 1) % order.size();
			scan(w, order.get(cursor));
		}
	}

	private void scan(ClientWorld w, long key) {
		int sx = ChunkSectionPos.unpackX(key), sy = ChunkSectionPos.unpackY(key), sz = ChunkSectionPos.unpackZ(key);
		WorldChunk chunk = w.getChunkManager().getChunk(sx, sz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
		if (chunk == null) {
			sections.remove(key);
			return;
		}
		int idx = w.getSectionIndex(sy << 4);
		ChunkSection[] arr = chunk.getSectionArray();
		if (idx < 0 || idx >= arr.length) {
			return;
		}
		ChunkSection sec = arr[idx];
		Section out = sections.get(key);
		if (sec == null || sec.isEmpty() || !sec.getBlockStateContainer().hasAny(s -> !s.getFluidState().isEmpty())) {
			if (out != null) {
				sections.remove(key);
			}
			return;
		}
		if (out == null) {
			out = new Section();
			sections.put(key, out);
		}
		boolean firstScan = out.water == null;
		java.util.BitSet now = new java.util.BitSet(4096);
		float[] q = out.quads.length > 0 ? out.quads : new float[STRIDE * 64];
		int n = 0;
		float[] falls = out.falls.length > 0 ? out.falls : new float[3 * 8];
		int fn = 0;
		BlockPos.Mutable p = new BlockPos.Mutable(), o = new BlockPos.Mutable();
		int bx = sx << 4, by = sy << 4, bz = sz << 4;
		for (int ly = 0; ly < 16; ly++) {
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					FluidState fs = sec.getFluidState(lx, ly, lz);
					if (fs.isEmpty()) {
						continue;
					}
					boolean lava = fs.isIn(FluidTags.LAVA);
					if (!lava && !fs.isIn(FluidTags.WATER)) {
						continue;
					}
					int x = bx + lx, y = by + ly, z = bz + lz;
					if (!lava) {
						now.set((ly << 8) | (lz << 4) | lx);
					}
					p.set(x, y, z);
					// waterfall landing: falling water whose block below is not falling water any more
					if (!lava && fs.contains(FlowableFluid.FALLING) && fs.get(FlowableFluid.FALLING)) {
						FluidState below = w.getFluidState(o.set(x, y - 1, z));
						boolean belowFalls = below.isIn(FluidTags.WATER) && below.contains(FlowableFluid.FALLING) && below.get(FlowableFluid.FALLING);
						if (!belowFalls) {
							if ((fn + 1) * 3 > falls.length) {
								falls = java.util.Arrays.copyOf(falls, falls.length * 2);
							}
							float landY = below.isIn(FluidTags.WATER) ? y - 1 + below.getHeight(w, o) : y;
							falls[fn * 3] = x + 0.5F;
							falls[fn * 3 + 1] = landY;
							falls[fn * 3 + 2] = z + 0.5F;
							fn++;
						}
					}
					// top surface: the block above is not the same liquid
					FluidState up = w.getFluidState(o.set(x, y + 1, z));
					if (lava ? up.isIn(FluidTags.LAVA) : up.isIn(FluidTags.WATER)) {
						continue;
					}
					if (!w.getBlockState(o).isAir() && !w.getBlockState(o).isReplaceable()) {
						continue; // covered by a block: nothing to see
					}
					if ((n + 1) * STRIDE > q.length) {
						q = java.util.Arrays.copyOf(q, q.length * 2);
					}
					int k = n * STRIDE;
					q[k] = x;
					q[k + 1] = y;
					q[k + 2] = z;
					// corner heights exactly like Minecraft's fluid renderer, so our surface lies right on top of its own
					float h = fh(w, x, y, z, lava, o), hn = fh(w, x, y, z - 1, lava, o), hs = fh(w, x, y, z + 1, lava, o);
					float he = fh(w, x + 1, y, z, lava, o), hw = fh(w, x - 1, y, z, lava, o);
					q[k + 3] = calc(w, h, hn, hw, x - 1, y, z - 1, lava, o);
					q[k + 4] = calc(w, h, hn, he, x + 1, y, z - 1, lava, o);
					q[k + 5] = calc(w, h, hs, hw, x - 1, y, z + 1, lava, o);
					q[k + 6] = calc(w, h, hs, he, x + 1, y, z + 1, lava, o);
					Vec3d v = fs.getVelocity(w, p);
					q[k + 7] = (float) v.x;
					q[k + 8] = (float) v.z;
					q[k + 9] = shore(w, x, y, z, lava, o);
					q[k + 10] = Float.intBitsToFloat(WorldRenderer.getLightmapCoordinates(w, o.set(x, y + 1, z)));
					q[k + 11] = lava ? 1 : 0;
					n++;
				}
			}
		}
		// water that was not there before (bucket, broken wall): remember it for a splash
		if (!firstScan && System.currentTimeMillis() - out.scannedAt < 4000L) {
			java.util.BitSet added = (java.util.BitSet) now.clone();
			added.andNot(out.water);
			for (int i = added.nextSetBit(0), c = 0; i >= 0 && c < 6; i = added.nextSetBit(i + 1), c++) {
				appeared.add(new double[] {bx + (i & 15) + 0.5, by + (i >> 8) + 0.9, bz + ((i >> 4) & 15) + 0.5});
			}
		}
		out.water = now;
		out.scannedAt = System.currentTimeMillis();
		out.quads = q;
		out.count = n;
		out.falls = falls;
		out.fallCount = fn;
	}

	/** Minecraft's FluidRenderer.getFluidHeight: 1 under the same liquid, its level / 9, 0 next to a non-solid block, -1 next to a solid one. */
	private static float fh(ClientWorld w, int x, int y, int z, boolean lava, BlockPos.Mutable o) {
		FluidState fs = w.getFluidState(o.set(x, y, z));
		if (lava ? fs.isIn(FluidTags.LAVA) : fs.isIn(FluidTags.WATER)) {
			FluidState up = w.getFluidState(o.set(x, y + 1, z));
			return (lava ? up.isIn(FluidTags.LAVA) : up.isIn(FluidTags.WATER)) ? 1F : fs.getHeight();
		}
		return w.getBlockState(o.set(x, y, z)).isSolid() ? -1F : 0F;
	}

	/** Minecraft's FluidRenderer.calculateFluidHeight (weighted: full blocks count 10x). */
	private static float calc(ClientWorld w, float origin, float ns, float ew, int dx, int y, int dz, boolean lava, BlockPos.Mutable o) {
		if (ew >= 1F || ns >= 1F) {
			return 1F;
		}
		float[] f = new float[2];
		if (ew > 0F || ns > 0F) {
			float d = fh(w, dx, y, dz, lava, o);
			if (d >= 1F) {
				return 1F;
			}
			add(f, d);
		}
		add(f, origin);
		add(f, ew);
		add(f, ns);
		return f[1] == 0 ? origin : f[0] / f[1];
	}

	private static void add(float[] f, float h) {
		if (h >= 0.8F) {
			f[0] += h * 10F;
			f[1] += 10F;
		} else if (h >= 0F) {
			f[0] += h;
			f[1]++;
		}
	}

	/** 0 = open water, 1 = walls on every side (foam gathers at the shore). */
	private static float shore(ClientWorld w, int x, int y, int z, boolean lava, BlockPos.Mutable o) {
		int s = 0;
		for (int i = 0; i < 4; i++) {
			int ax = x + (i == 0 ? 1 : i == 1 ? -1 : 0), az = z + (i == 2 ? 1 : i == 3 ? -1 : 0);
			FluidState fs = w.getFluidState(o.set(ax, y, az));
			if (lava ? !fs.isIn(FluidTags.LAVA) : !fs.isIn(FluidTags.WATER)) {
				s++;
			}
		}
		return s / 4F;
	}
}
