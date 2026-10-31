package dev.solclient.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * What the map knows about the world: one 16x16 block of colours per chunk (1 block = 1 pixel), with the sun
 * shading already baked in. The surface is remembered on disk per world and dimension, so places you have
 * been to stay on the map. Cave slices are only kept in memory (they are rebuilt from the chunks around you).
 */
public final class MapStore {

	public static final MapStore I = new MapStore();
	private static final int MAGIC = 0x4B4D4150;
	/** Newer file format: every chunk also keeps the height of each block (for the 3D map). */
	private static final int MAGIC2 = 0x4B4D4132;
	/** Newest: every chunk has its own length (256 / 512 / 1024: 1024 = also what is under the trees). */
	private static final int MAGIC3 = 0x4B4D4133;

	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final Map<Long, int[]> surf = new HashMap<>();
	private final Map<Long, Integer> surfAt = new HashMap<>();
	private final Map<Long, int[]> cave = new HashMap<>();
	private final Map<Long, Integer> caveAt = new HashMap<>();
	private String key = "";
	private int tick;
	private int caveY = Integer.MIN_VALUE;
	private boolean dirty;
	private long lastSave;
	private static int[] ORDER;
	private static int orderR = -1;

	/** Goes up every time any colour changes (the map views redraw when it does). */
	public int version;

	private MapStore() {
	}

	public static long k(int cx, int cz) {
		return ((long) cx & 0xFFFFFFFFL) << 32 | ((long) cz & 0xFFFFFFFFL);
	}

	/**
	 * The colours (z * 16 + x) of a chunk, or null when unknown. 0 = unknown block. A surface chunk has 512 numbers:
	 * 256 colours, then the 256 heights (y of the top block) used by the 3D map. Chunks saved by older versions only
	 * have the 256 colours (they get their heights the next time you walk near them). A cave chunk has 256.
	 */
	public int[] chunk(long k, boolean caveView) {
		return caveView ? cave.get(k) : surf.get(k);
	}

	public int chunks() {
		return surf.size();
	}

	// ------------------------------------------------------------------ scanning
	public void update(World w, double px, double pz, int radius, int budget, boolean wantCave, int sliceY) {
		tick++;
		String wk = worldKey() + "_" + w.getRegistryKey().getValue().toString().replace(':', '_');
		if (!wk.equals(key)) {
			switchTo(wk);
		}
		boolean ceiling = w.getDimension().hasCeiling();
		boolean needCave = wantCave || ceiling;
		if (needCave && sliceY != caveY) {
			caveY = sliceY;
			caveAt.clear();
			version++;
		}
		int pcx = ((int) Math.floor(px)) >> 4, pcz = ((int) Math.floor(pz)) >> 4;
		int[] ord = order(radius);
		int done = 0;
		for (int i = 0; i + 1 < ord.length && done < budget; i += 2) {
			int dx = ord[i], dz = ord[i + 1];
			int cx = pcx + dx, cz = pcz + dz;
			long ck = k(cx, cz);
			int d = Math.max(Math.abs(dx), Math.abs(dz));
			int maxAge = d <= 2 ? 30 : d <= 5 ? 120 : 500;
			Integer a1 = ceiling ? null : surfAt.get(ck);
			boolean stale1 = !ceiling && (a1 == null || tick - a1 > maxAge);
			Integer a2 = needCave ? caveAt.get(ck) : null;
			boolean stale2 = needCave && (a2 == null || tick - a2 > maxAge);
			if (!stale1 && !stale2) {
				continue;
			}
			Chunk ch = w.getChunk(cx, cz, ChunkStatus.FULL, false);
			if (ch == null) {
				continue;
			}
			if (stale1) {
				int[] arr = scanSurface(w, ch, cx, cz);
				surfAt.put(ck, tick);
				if (arr != null) {
					int[] old = surf.get(ck);
					if (old == null || !Arrays.equals(old, arr)) {
						surf.put(ck, arr);
						dirty = true;
						version++;
					}
				}
			}
			if (stale2) {
				int[] arr = scanCave(w, ch, cx, cz, caveY);
				caveAt.put(ck, tick);
				if (arr != null) {
					int[] old = cave.get(ck);
					if (old == null || !Arrays.equals(old, arr)) {
						cave.put(ck, arr);
						version++;
					}
				}
			}
			done++;
		}
		if (dirty && System.currentTimeMillis() - lastSave > 60000L) {
			flush();
		}
	}

	private static int[] order(int r) {
		if (r != orderR || ORDER == null) {
			List<int[]> l = new ArrayList<>();
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (dx * dx + dz * dz <= r * r + r) {
						l.add(new int[] { dx, dz });
					}
				}
			}
			l.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
			ORDER = new int[l.size() * 2];
			for (int i = 0; i < l.size(); i++) {
				ORDER[i * 2] = l.get(i)[0];
				ORDER[i * 2 + 1] = l.get(i)[1];
			}
			orderR = r;
		}
		return ORDER;
	}

	private int[] scanSurface(World w, Chunk ch, int cx, int cz) {
		try {
			return doSurface(w, ch, cx, cz);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private int[] doSurface(World w, Chunk ch, int cx, int cz) {
		Heightmap.Type surfT = Heightmap.Type.WORLD_SURFACE, floorT = Heightmap.Type.OCEAN_FLOOR;
		int sx = cx << 4, sz = cz << 4, bottom = ch.getBottomY();
		var colors = mc.getBlockColors();
		BlockPos.Mutable m = new BlockPos.Mutable();
		// heights with a one block border, so the shading also works on the edge of a chunk
		Chunk[] nb = new Chunk[9];
		for (int oz = -1; oz <= 1; oz++) {
			for (int ox = -1; ox <= 1; ox++) {
				nb[(oz + 1) * 3 + ox + 1] = ox == 0 && oz == 0 ? ch : w.getChunk(cx + ox, cz + oz, ChunkStatus.FULL, false);
			}
		}
		int[] hs = new int[18 * 18];
		for (int lz = -1; lz <= 16; lz++) {
			for (int lx = -1; lx <= 16; lx++) {
				int ox = lx < 0 ? -1 : lx > 15 ? 1 : 0, oz = lz < 0 ? -1 : lz > 15 ? 1 : 0;
				Chunk src = nb[(oz + 1) * 3 + ox + 1];
				hs[(lz + 1) * 18 + lx + 1] = src == null ? Integer.MIN_VALUE : src.sampleHeightmap(surfT, lx & 15, lz & 15);
			}
		}
		for (int lz = -1; lz <= 16; lz++) {
			for (int lx = -1; lx <= 16; lx++) {
				int i = (lz + 1) * 18 + lx + 1;
				if (hs[i] == Integer.MIN_VALUE) {
					hs[i] = hs[(Math.max(0, Math.min(15, lz)) + 1) * 18 + Math.max(0, Math.min(15, lx)) + 1];
				}
			}
		}
		int[] out = new int[512];
		int[] big = null; // 1024 numbers when the chunk has trees
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = sx + lx, z = sz + lz, hi = (lz + 1) * 18 + lx + 1;
				int y = hs[hi] + 1;
				BlockState st = ch.getBlockState(m.set(x, y, z));
				int g = 0;
				while (st.isAir() && y > bottom && g++ < 8) {
					y--;
					st = ch.getBlockState(m.set(x, y, z));
				}
				if (st.isAir()) {
					continue;
				}
				int noise = ((x * 73856093) ^ (z * 19349663)) >>> 9 & 31;
				boolean fluid = !st.getFluidState().isEmpty();
				int rgb;
				float shade;
				if (fluid) {
					boolean lava = st.getFluidState().isIn(FluidTags.LAVA);
					if (lava) {
						rgb = 0xFF8A1E;
						shade = 0.92F + noise * 0.004F;
					} else {
						int fy = ch.sampleHeightmap(floorT, lx, lz) + 1;
						BlockState fs = ch.getBlockState(m.set(x, fy, z));
						int g2 = 0;
						while ((fs.isAir() || !fs.getFluidState().isEmpty()) && fy > bottom && g2++ < 64) {
							fy--;
							fs = ch.getBlockState(m.set(x, fy, z));
						}
						int depth = Math.max(1, y - fy);
						int floorCol = scale(blockRgb(w, fs, m), 0.8F);
						m.set(x, y, z);
						int wc = -1;
						try {
							wc = colors.getColor(Blocks.WATER.getDefaultState(), w, m, 0);
						} catch (RuntimeException ignored) {
						}
						if (wc == -1) {
							wc = 0x3F76E4;
						}
						rgb = lerp(floorCol, wc & 0xFFFFFF, Math.min(0.93F, 0.42F + depth * 0.06F));
						shade = (1F - Math.min(0.38F, depth * 0.012F)) * (0.985F + noise * 0.001F);
					}
				} else {
					rgb = blockRgb(w, st, m.set(x, y, z));
					int h0 = hs[hi];
					float slope = Math.max(-6F, Math.min(6F, (h0 - hs[hi - 18]) + (h0 - hs[hi - 1])));
					shade = 1F + slope * 0.07F;
					int occ = 0;
					if (hs[hi - 18] - h0 >= 2) occ++;
					if (hs[hi - 1] - h0 >= 2) occ++;
					if (hs[hi + 18] - h0 >= 2) occ++;
					if (hs[hi + 1] - h0 >= 2) occ++;
					shade -= occ * 0.04F;
					int drop = Math.max(0, h0 - hs[hi + 18]) + Math.max(0, h0 - hs[hi + 1]);
					if (drop >= 4) {
						shade += 0.05F;
					}
					shade *= 0.955F + noise * 0.003F;
					shade *= 1F + Math.max(-0.08F, Math.min(0.1F, (h0 - 64) * 0.0015F));
				}
				out[lz * 16 + lx] = 0xFF000000 | scale(rgb, shade);
				out[256 + lz * 16 + lx] = y; // the height of this block, for the 3D map
				if (!fluid && st.isIn(BlockTags.LEAVES)) {
					// a tree top: remember what is under the leaves (trunk or ground) and how thick the leaves are
					int yb = y;
					while (yb - 1 > bottom && ch.getBlockState(m.set(x, yb - 1, z)).isIn(BlockTags.LEAVES) && y - yb < 40) {
						yb--;
					}
					int yu = yb - 1;
					BlockState su = null;
					while (yu > bottom && yb - yu < 64) {
						su = ch.getBlockState(m.set(x, yu, z));
						if (!su.isAir()) {
							break;
						}
						yu--;
					}
					if (su != null && !su.isAir() && yu > bottom) {
						if (big == null) {
							big = new int[1024];
						}
						int ur = su.getFluidState().isEmpty() ? blockRgb(w, su, m.set(x, yu, z)) : 0x3F76E4;
						int thick = Math.max(1, Math.min(255, y - yb + 1));
						big[512 + lz * 16 + lx] = thick << 24 | scale(ur, 0.92F);
						big[768 + lz * 16 + lx] = yu;
					}
				}
			}
		}
		if (big != null) {
			System.arraycopy(out, 0, big, 0, 512);
			return big;
		}
		return out;
	}

	/** The colour of a surface block: its real texture (tinted for grass / leaves), or the vanilla map colour when it has none. */
	private int blockRgb(World w, BlockState st, BlockPos pos) {
		int tint = -1;
		try {
			tint = mc.getBlockColors().getColor(st, w, pos, 0);
		} catch (RuntimeException ignored) {
		}
		int tex = BlockColours.of(st.getBlock());
		if (tex == -1) {
			int rgb = st.getMapColor(w, pos).color;
			if (tint != -1) {
				rgb = scale(tint & 0xFFFFFF, st.isIn(BlockTags.LEAVES) ? 0.82F : 0.94F);
			}
			return rgb;
		}
		if (tint != -1) {
			// the texture is grey: the biome colour gives the hue, the texture its brightness
			float k = Math.max(0.72F, Math.min(1.2F, BlockColours.luma(tex) / 0.58F));
			return scale(tint & 0xFFFFFF, (st.isIn(BlockTags.LEAVES) ? 0.80F : 0.93F) * k);
		}
		return tex;
	}

	private int[] scanCave(World w, Chunk ch, int cx, int cz, int sliceY) {
		try {
			int sx = cx << 4, sz = cz << 4;
			int bottom = ch.getBottomY(), top = bottom + ch.getHeight() - 1;
			int sy = Math.max(bottom + 1, Math.min(top - 1, sliceY));
			BlockPos.Mutable m = new BlockPos.Mutable();
			boolean[] solid = new boolean[256];
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					m.set(sx + lx, sy, sz + lz);
					BlockState st = ch.getBlockState(m);
					solid[lz * 16 + lx] = !st.isAir() && st.getFluidState().isEmpty() && st.isSolidBlock(w, m);
				}
			}
			int[] out = new int[256];
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					int idx = lz * 16 + lx, x = sx + lx, z = sz + lz;
					int noise = ((x * 73856093) ^ (z * 19349663)) >>> 9 & 15;
					float nf = 0.94F + noise * 0.008F;
					int rgb;
					if (solid[idx]) {
						boolean rim = lx > 0 && !solid[idx - 1] || lx < 15 && !solid[idx + 1] || lz > 0 && !solid[idx - 16] || lz < 15 && !solid[idx + 16];
						m.set(x, sy, z);
						int base = ch.getBlockState(m).getMapColor(w, m).color;
						rgb = scale(base, rim ? 0.62F : 0.15F);
					} else {
						rgb = 0x07070B;
						for (int d = 0; d < 48; d++) {
							int y = sy - d;
							if (y <= bottom) {
								break;
							}
							m.set(x, y, z);
							BlockState fs = ch.getBlockState(m);
							if (!fs.getFluidState().isEmpty()) {
								rgb = fs.getFluidState().isIn(FluidTags.LAVA) ? 0xFF8A1E : scale(0x3D6BDB, Math.max(0.5F, 1F - d * 0.012F));
								break;
							}
							if (!fs.isAir() && fs.isSolidBlock(w, m)) {
								rgb = scale(fs.getMapColor(w, m).color, Math.max(0.45F, 1F - d * 0.016F));
								break;
							}
						}
					}
					out[idx] = 0xFF000000 | scale(rgb, nf);
				}
			}
			return out;
		} catch (RuntimeException e) {
			return null;
		}
	}

	static int scale(int c, float k) {
		int r = Math.max(0, Math.min(255, (int) (((c >> 16) & 255) * k))), g = Math.max(0, Math.min(255, (int) (((c >> 8) & 255) * k))), b = Math.max(0, Math.min(255, (int) ((c & 255) * k)));
		return r << 16 | g << 8 | b;
	}

	static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	// ------------------------------------------------------------------ disk
	private String worldKey() {
		try {
			if (mc.isInSingleplayer() && mc.getServer() != null) {
				return "sp_" + mc.getServer().getSaveProperties().getLevelName();
			}
			if (mc.getCurrentServerEntry() != null) {
				return "mp_" + mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
			}
		} catch (RuntimeException ignored) {
		}
		return "unknown";
	}

	private static Path file(String k) {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-map").resolve(k.replaceAll("[^a-zA-Z0-9._-]", "_") + ".bin");
	}

	private void switchTo(String wk) {
		flush();
		surf.clear();
		surfAt.clear();
		cave.clear();
		caveAt.clear();
		caveY = Integer.MIN_VALUE;
		key = wk;
		version++;
		Path f = file(wk);
		Thread t = new Thread(() -> {
			Map<Long, int[]> got = new HashMap<>();
			try {
				if (!Files.exists(f)) {
					return;
				}
				try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
					int magic = in.readInt();
					if (magic != MAGIC && magic != MAGIC2 && magic != MAGIC3) {
						return;
					}
					int per = magic == MAGIC2 ? 512 : 256;
					int n = in.readInt();
					for (int i = 0; i < n; i++) {
						long ck = in.readLong();
						int len = magic == MAGIC3 ? in.readInt() : per;
						if (len < 0 || len > 1024) {
							return;
						}
						int[] a = new int[len];
						for (int j = 0; j < len; j++) {
							a[j] = in.readInt();
						}
						got.put(ck, a);
					}
				}
			} catch (Exception ignored) {
			}
			if (got.isEmpty()) {
				return;
			}
			mc.execute(() -> {
				if (wk.equals(key)) {
					for (Map.Entry<Long, int[]> e : got.entrySet()) {
						surf.putIfAbsent(e.getKey(), e.getValue());
					}
					version++;
				}
			});
		}, "kubo-map-load");
		t.setDaemon(true);
		t.start();
	}

	/** Writes the explored surface to disk (in the background). */
	public void flush() {
		if (!dirty || key.isEmpty() || surf.isEmpty()) {
			return;
		}
		dirty = false;
		lastSave = System.currentTimeMillis();
		final Path f = file(key);
		final long[] keys = new long[surf.size()];
		final int[][] vals = new int[surf.size()][];
		int i = 0;
		for (Map.Entry<Long, int[]> e : surf.entrySet()) {
			keys[i] = e.getKey();
			vals[i] = e.getValue();
			i++;
		}
		Thread t = new Thread(() -> {
			try {
				Files.createDirectories(f.getParent());
				Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
				try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
					out.writeInt(MAGIC3);
					out.writeInt(keys.length);
					for (int n = 0; n < keys.length; n++) {
						out.writeLong(keys[n]);
						out.writeInt(vals[n].length);
						for (int j = 0; j < vals[n].length; j++) {
							out.writeInt(vals[n][j]);
						}
					}
				}
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				System.err.println("[Sleep] could not save the map: " + e);
			}
		}, "kubo-map-save");
		t.setDaemon(true);
		t.start();
	}
}
