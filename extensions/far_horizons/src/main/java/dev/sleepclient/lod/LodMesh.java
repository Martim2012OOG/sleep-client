package dev.sleepclient.lod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java (no Minecraft classes): turns the {@link LodStore} into coloured quads.
 * The world around the player is split into rings: the closer the ring, the smaller the cells (base, 2x base, 4x base ...),
 * so far away terrain is drawn with very few quads and never lags. Cells are aligned to a world grid, so nothing shimmers
 * while you walk. Quads are grouped in 256-block tiles so the renderer can skip whole tiles that are behind you.
 */
public final class LodMesh {

	public static final class Tile {
		public float cx, cz, r;
		public float[] pos;
		public int[] col;
		public byte[] face;
		public int n;
	}

	public static final class Params {
		public int ox, oz, maxDist, base = 8;
		public float brightness = 1F;
		public int tint = 0xFFFFFFFF;
		/** chunks that are really loaded (vanilla draws those, so the LOD leaves them out). */
		public Set<Long> loaded;
	}

	public final int ox, oz;
	public final Tile[] tiles;
	public final int quads;

	private LodMesh(int ox, int oz, Tile[] tiles, int quads) {
		this.ox = ox;
		this.oz = oz;
		this.tiles = tiles;
		this.quads = quads;
	}

	private static final class TB {
		float cx, cz, maxCell;
		float[] pos = new float[12 * 64];
		int[] col = new int[64];
		byte[] face = new byte[64];
		int n;

		void add(float[] q, int c, int f) {
			if (n == col.length) {
				pos = java.util.Arrays.copyOf(pos, pos.length * 2);
				col = java.util.Arrays.copyOf(col, col.length * 2);
				face = java.util.Arrays.copyOf(face, face.length * 2);
			}
			System.arraycopy(q, 0, pos, n * 12, 12);
			col[n] = c;
			face[n] = (byte) f;
			n++;
		}
	}

	private static final int GRID = 69;
	private static final int MAX_QUADS = 450_000;

	public static LodMesh build(LodStore store, Params p) {
		Map<Long, TB> tiles = new HashMap<>();
		int total = 0;
		float[] q = new float[12];
		int tr = (int) (((p.tint >> 16) & 255) * p.brightness), tg = (int) (((p.tint >> 8) & 255) * p.brightness), tb = (int) ((p.tint & 255) * p.brightness);

		for (int lv = 0; lv < 8; lv++) {
			final int cell = p.base << lv;
			final int outer = (p.base * 32) << lv;
			final int inner = lv == 0 ? 0 : (p.base * 32) << (lv - 1);
			if (inner >= p.maxDist) {
				break;
			}
			final int lim = Math.min(outer, p.maxDist);
			final int gi0 = Math.floorDiv(p.ox, cell) - 34, gj0 = Math.floorDiv(p.oz, cell) - 34;
			float[] H = new float[GRID * GRID];
			int[] C = new int[GRID * GRID];
			boolean[] has = new boolean[GRID * GRID];
			boolean[] wat = new boolean[GRID * GRID];
			boolean[] ring = new boolean[GRID * GRID];
			int[] tmp = new int[6];
			for (int b = 0; b < GRID; b++) {
				for (int a = 0; a < GRID; a++) {
					int i = gi0 + a, j = gj0 + b, idx = b * GRID + a;
					int cxw = i * cell + cell / 2, czw = j * cell + cell / 2;
					int d = Math.max(Math.abs(cxw - p.ox), Math.abs(czw - p.oz));
					ring[idx] = d >= inner && d < lim;
					if (!ring[idx] && (a == 0 || b == 0 || a == GRID - 1 || b == GRID - 1)) {
						continue;
					}
					if (!sampleCell(store, i * cell, j * cell, cell, tmp)) {
						continue;
					}
					has[idx] = true;
					H[idx] = Float.intBitsToFloat(tmp[0]);
					C[idx] = tmp[1];
					wat[idx] = tmp[2] != 0;
				}
			}
			for (int b = 1; b < GRID - 1; b++) {
				for (int a = 1; a < GRID - 1; a++) {
					int idx = b * GRID + a;
					if (!has[idx] || !ring[idx]) {
						continue;
					}
					int i = gi0 + a, j = gj0 + b;
					int x0 = i * cell, z0 = j * cell, x1 = x0 + cell, z1 = z0 + cell;
					int cxw = x0 + cell / 2, czw = z0 + cell / 2;
					if (cell <= 32 && p.loaded != null && p.loaded.contains(LodStore.key(Math.floorDiv(cxw, 16), Math.floorDiv(czw, 16)))) {
						continue;
					}
					if (total > MAX_QUADS) {
						break;
					}
					int c = C[idx];
					int r = Math.min(255, ((c >> 16) & 255) * tr / 255), g = Math.min(255, ((c >> 8) & 255) * tg / 255), bl = Math.min(255, (c & 255) * tb / 255);
					int col = 0xFF000000 | r << 16 | g << 8 | bl;
					float y = top(H[idx], wat[idx]);

					long tk = LodStore.key(Math.floorDiv(cxw, 256), Math.floorDiv(czw, 256));
					TB t = tiles.get(tk);
					if (t == null) {
						t = new TB();
						t.cx = Math.floorDiv(cxw, 256) * 256 + 128 - p.ox;
						t.cz = Math.floorDiv(czw, 256) * 256 + 128 - p.oz;
						tiles.put(tk, t);
					}
					t.maxCell = Math.max(t.maxCell, cell);

					float fx0 = x0 - p.ox, fx1 = x1 - p.ox, fz0 = z0 - p.oz, fz1 = z1 - p.oz;
					// top
					set(q, fx0, y, fz0, fx0, y, fz1, fx1, y, fz1, fx1, y, fz0);
					t.add(q, col, 0);
					total++;
					// walls (only on the sides a player near the centre can see)
					for (int dir = 1; dir <= 4; dir++) {
						int na = a + (dir == 1 ? -1 : dir == 2 ? 1 : 0), nb = b + (dir == 3 ? -1 : dir == 4 ? 1 : 0);
						int nidx = nb * GRID + na;
						boolean visible = switch (dir) {
							case 1 -> p.ox - 24 < x0;
							case 2 -> p.ox + 24 > x1;
							case 3 -> p.oz - 24 < z0;
							default -> p.oz + 24 > z1;
						};
						if (!visible) {
							continue;
						}
						float yn = has[nidx] ? top(H[nidx], wat[nidx]) : y - 16F;
						boolean seam = !ring[nidx];
						float bottom = yn < y ? yn - 1F : y;
						if (seam) {
							bottom = Math.min(bottom, y - 10F);
						}
						if (bottom >= y - 0.01F) {
							continue;
						}
						switch (dir) {
							case 1 -> set(q, fx0, y, fz0, fx0, y, fz1, fx0, bottom, fz1, fx0, bottom, fz0);
							case 2 -> set(q, fx1, y, fz0, fx1, y, fz1, fx1, bottom, fz1, fx1, bottom, fz0);
							case 3 -> set(q, fx0, y, fz0, fx1, y, fz0, fx1, bottom, fz0, fx0, bottom, fz0);
							default -> set(q, fx0, y, fz1, fx1, y, fz1, fx1, bottom, fz1, fx0, bottom, fz1);
						}
						t.add(q, col, dir);
						total++;
					}
				}
			}
		}

		List<Tile> out = new ArrayList<>(tiles.size());
		for (TB t : tiles.values()) {
			Tile o = new Tile();
			o.cx = t.cx;
			o.cz = t.cz;
			o.r = 182F + t.maxCell;
			o.n = t.n;
			o.pos = java.util.Arrays.copyOf(t.pos, t.n * 12);
			o.col = java.util.Arrays.copyOf(t.col, t.n);
			o.face = java.util.Arrays.copyOf(t.face, t.n);
			out.add(o);
		}
		return new LodMesh(p.ox, p.oz, out.toArray(new Tile[0]), total);
	}

	/** y of the top face; a bit under the real terrain so it never fights with vanilla chunks. */
	private static float top(float h, boolean water) {
		return Math.round(h) + 1F - (water ? 0.15F : 0.3F);
	}

	private static void set(float[] q, float... v) {
		System.arraycopy(v, 0, q, 0, 12);
	}

	/** Average of the samples inside the cell. out = {height bits, rgb, water}. */
	private static boolean sampleCell(LodStore store, int x0, int z0, int cell, int[] out) {
		int n = Math.max(1, cell / 4), stride = Math.max(1, n / 8);
		int sx0 = Math.floorDiv(x0, 4), sz0 = Math.floorDiv(z0, 4);
		long sumH = 0, sr = 0, sg = 0, sb = 0;
		int cnt = 0, wat = 0;
		int lcx = Integer.MIN_VALUE, lcz = Integer.MIN_VALUE;
		LodStore.Chunk lc = null;
		for (int dz = 0; dz < n; dz += stride) {
			for (int dx = 0; dx < n; dx += stride) {
				int sx = sx0 + dx, sz = sz0 + dz;
				int cx = Math.floorDiv(sx, 4), cz = Math.floorDiv(sz, 4);
				if (cx != lcx || cz != lcz) {
					lc = store.get(LodStore.key(cx, cz));
					lcx = cx;
					lcz = cz;
				}
				if (lc == null) {
					continue;
				}
				int idx = (sz & 3) * 4 + (sx & 3);
				short h = lc.h[idx];
				if (h == Short.MIN_VALUE) {
					continue;
				}
				int c = lc.rgb[idx];
				sumH += h;
				sr += (c >> 16) & 255;
				sg += (c >> 8) & 255;
				sb += c & 255;
				if (((lc.water >> idx) & 1) != 0) {
					wat++;
				}
				cnt++;
			}
		}
		if (cnt == 0) {
			return false;
		}
		out[0] = Float.floatToIntBits((float) sumH / cnt);
		out[1] = (int) (sr / cnt) << 16 | (int) (sg / cnt) << 8 | (int) (sb / cnt);
		out[2] = wat * 2 > cnt ? 1 : 0;
		return true;
	}
}
