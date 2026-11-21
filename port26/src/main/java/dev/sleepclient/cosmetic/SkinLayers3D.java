package dev.sleepclient.cosmetic;

import dev.sleepclient.mods.SkinLayers3DMod;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * 3D Skin Layers: the second skin layer (hat, jacket, sleeves, pants) becomes real voxels: every visible pixel is a small cube sticking out of
 * the body, with the pixel's colour. The meshes are built once per skin and cached, only players near you get them, and a hard budget of
 * quads per frame keeps crowded places smooth.
 */
public final class SkinLayers3D {

	public static final int HAT = 1, JACKET = 2, LSLEEVE = 4, RSLEEVE = 8, LPANTS = 16, RPANTS = 32;
	/** Group order of a mesh: head(hat), body(jacket), right arm, left arm, right leg, left leg. */
	public static final int HEAD_G = 0, BODY_G = 1, RARM_G = 2, LARM_G = 3, RLEG_G = 4, LLEG_G = 5;

	/** quads * 32 floats: 4 vertices of (x, y, z in pixels; u, v; nx, ny, nz). */
	public record Mesh(float[][] groups) {
		public int quads() {
			int n = 0;
			for (float[] g : groups) {
				n += g.length / 32;
			}
			return n;
		}
	}

	private static final Map<String, Mesh> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Mesh> e) {
			return size() > 90;
		}
	};
	private static float lastFrame = Float.NaN;
	private static int budget;

	private SkinLayers3D() {
	}

	// ------------------------------------------------------------------ deciding what is 3D this frame
	/** Called for every player while the frame is prepared: which layers get the 3D look. */
	public static int maskFor(AvatarRenderState s) {
		SkinLayers3DMod m = SkinLayers3DMod.instance;
		if (m == null || !m.enabled || s.isInvisible || s.isSpectator || s.distanceToCameraSq > m.range() * m.range()) {
			return 0;
		}
		Identifier id = s.skin.body().texturePath();
		Mesh mesh = mesh(id, s.skin.model() == PlayerModelType.SLIM, m.thickness10());
		if (mesh == null) {
			return 0;
		}
		// one shared budget per frame: crowded places keep the nearest-drawn ones in 3D and the rest flat
		float f = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (f != lastFrame) {
			lastFrame = f;
			budget = m.budgetQuads();
		}
		int mask = 0;
		if (m.hat() && s.showHat && s.headEquipment.isEmpty()) {
			mask |= HAT;
		}
		if (m.jacket() && s.showJacket && s.chestEquipment.isEmpty()) {
			mask |= JACKET;
		}
		if (m.sleeves() && s.chestEquipment.isEmpty()) {
			mask |= (s.showLeftSleeve ? LSLEEVE : 0) | (s.showRightSleeve ? RSLEEVE : 0);
		}
		if (m.pants() && s.legsEquipment.isEmpty()) {
			mask |= (s.showLeftPants ? LPANTS : 0) | (s.showRightPants ? RPANTS : 0);
		}
		int cost = 0;
		for (int g = 0; g < 6; g++) {
			if ((mask & bit(g)) != 0) {
				cost += mesh.groups()[g].length / 32;
			}
		}
		if (cost == 0 || cost > budget) {
			return 0;
		}
		budget -= cost;
		return mask;
	}

	public static int bit(int group) {
		return switch (group) {
			case HEAD_G -> HAT;
			case BODY_G -> JACKET;
			case RARM_G -> RSLEEVE;
			case LARM_G -> LSLEEVE;
			case RLEG_G -> RPANTS;
			default -> LPANTS;
		};
	}

	// ------------------------------------------------------------------ meshes
	/** The mesh of this skin (built on first use), or null when its pixels are not known. */
	public static Mesh mesh(Identifier id, boolean slim, int thick10) {
		String key = id + "|" + slim + "|" + thick10;
		synchronized (CACHE) {
			Mesh m = CACHE.get(key);
			if (m != null) {
				return m;
			}
		}
		int[] px = SkinPixels.get(id);
		if (px == null) {
			return null;
		}
		Mesh built = build(px, slim, thick10 / 10F);
		synchronized (CACHE) {
			CACHE.put(key, built);
		}
		return built;
	}

	private static Mesh build(int[] px, boolean slim, float t) {
		int armW = slim ? 3 : 4;
		float[][] g = new float[6][];
		g[HEAD_G] = box(px, -4, -8, -4, 8, 8, 8, 32, 0, t);
		g[BODY_G] = box(px, -4, 0, -2, 8, 12, 4, 16, 32, t);
		g[RARM_G] = box(px, slim ? -2 : -3, -2, -2, armW, 12, 4, 40, 32, t);
		g[LARM_G] = box(px, -1, -2, -2, armW, 12, 4, 48, 48, t);
		g[RLEG_G] = box(px, -2, 0, -2, 4, 12, 4, 0, 32, t);
		g[LLEG_G] = box(px, -2, 0, -2, 4, 12, 4, 0, 48, t);
		return new Mesh(g);
	}

	private static boolean solid(int[] px, int x, int y) {
		return x >= 0 && y >= 0 && x < 64 && y < 64 && (px[y * 64 + x] >>> 24) >= 20;
	}

	/** One box of the second layer as voxels (same faces and texture layout as the game's own cuboid). */
	private static float[] box(int[] px, float x, float y, float z, int sx, int sy, int sz, int u, int v, float t) {
		float f = x, g = y, h = z, i = x + sx, j = y + sy, k = z + sz;
		float[][] V = {{f, g, h}, {i, g, h}, {i, j, h}, {f, j, h}, {f, g, k}, {i, g, k}, {i, j, k}, {f, j, k}};
		float l = u, m = u + sz, n = u + sz + sx, o = u + sz + sx + sx, p = u + sz + sx + sz, q = u + sz + sx + sz + sx;
		float r = v, s = v + sz, tt = v + sz + sy;
		float[] cen = {(f + i) / 2, (g + j) / 2, (h + k) / 2};
		Buf b = new Buf();
		face(b, px, new float[][] {V[5], V[4], V[0], V[1]}, m, r, n, s, cen, t);
		face(b, px, new float[][] {V[2], V[3], V[7], V[6]}, n, s, o, r, cen, t);
		face(b, px, new float[][] {V[0], V[4], V[7], V[3]}, l, s, m, tt, cen, t);
		face(b, px, new float[][] {V[1], V[0], V[3], V[2]}, m, s, n, tt, cen, t);
		face(b, px, new float[][] {V[5], V[1], V[2], V[6]}, n, s, p, tt, cen, t);
		face(b, px, new float[][] {V[4], V[5], V[6], V[7]}, p, s, q, tt, cen, t);
		return b.done();
	}

	private static final class Buf {
		float[] a = new float[4096];
		int n;

		void v(float x, float y, float z, float u, float vv, float nx, float ny, float nz) {
			if (n + 8 > a.length) {
				a = java.util.Arrays.copyOf(a, a.length * 2);
			}
			a[n++] = x;
			a[n++] = y;
			a[n++] = z;
			a[n++] = u;
			a[n++] = vv;
			a[n++] = nx;
			a[n++] = ny;
			a[n++] = nz;
		}

		float[] done() {
			return java.util.Arrays.copyOf(a, n);
		}
	}

	private static float[] lerp(float[][] q, float s, float tv) {
		// vertex k of the quad sits at the texture corners: 0 -> (u2, v1), 1 -> (u1, v1), 2 -> (u1, v2), 3 -> (u2, v2)
		float[] out = new float[3];
		for (int c = 0; c < 3; c++) {
			out[c] = (1 - s) * (1 - tv) * q[1][c] + s * (1 - tv) * q[0][c] + s * tv * q[3][c] + (1 - s) * tv * q[2][c];
		}
		return out;
	}

	private static void face(Buf b, int[] px, float[][] q, float u1, float v1, float u2, float v2, float[] boxCentre, float t) {
		// outward normal of the face
		float[] e1 = {q[0][0] - q[1][0], q[0][1] - q[1][1], q[0][2] - q[1][2]};
		float[] e2 = {q[2][0] - q[1][0], q[2][1] - q[1][1], q[2][2] - q[1][2]};
		float[] nrm = {e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]};
		float len = (float) Math.sqrt(nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]);
		nrm[0] /= len;
		nrm[1] /= len;
		nrm[2] /= len;
		float[] mid = lerp(q, 0.5F, 0.5F);
		if (nrm[0] * (mid[0] - boxCentre[0]) + nrm[1] * (mid[1] - boxCentre[1]) + nrm[2] * (mid[2] - boxCentre[2]) < 0) {
			nrm[0] = -nrm[0];
			nrm[1] = -nrm[1];
			nrm[2] = -nrm[2];
		}
		int ua = (int) Math.min(u1, u2), ub = (int) Math.max(u1, u2), va = (int) Math.min(v1, v2), vb = (int) Math.max(v1, v2);
		float du = u2 - u1, dv = v2 - v1;
		for (int ty = va; ty < vb; ty++) {
			for (int tx = ua; tx < ub; tx++) {
				if (!solid(px, tx, ty)) {
					continue;
				}
				float s0 = (tx - u1) / du, s1 = (tx + 1 - u1) / du, t0 = (ty - v1) / dv, t1 = (ty + 1 - v1) / dv;
				float[] p00 = lerp(q, s0, t0), p10 = lerp(q, s1, t0), p11 = lerp(q, s1, t1), p01 = lerp(q, s0, t1);
				float tu = (tx + 0.5F) / 64F, tv = (ty + 0.5F) / 64F;
				// outer face (winding as the game's quads: 0 -> (s1,t0), 1 -> (s0,t0), 2 -> (s0,t1), 3 -> (s1,t1))
				quad(b, off(p10, nrm, t), off(p00, nrm, t), off(p01, nrm, t), off(p11, nrm, t), tu, tv, nrm);
				// sides where the neighbour pixel (same face) is empty or the face ends
				side(b, px, tx, ty, ua, ub, va, vb, du, dv, p00, p10, p01, p11, nrm, t, tu, tv);
			}
		}
	}

	private static float[] off(float[] p, float[] n, float t) {
		return new float[] {p[0] + n[0] * t, p[1] + n[1] * t, p[2] + n[2] * t};
	}

	private static void quad(Buf b, float[] a, float[] bb, float[] c, float[] d, float u, float v, float[] n) {
		b.v(a[0], a[1], a[2], u, v, n[0], n[1], n[2]);
		b.v(bb[0], bb[1], bb[2], u, v, n[0], n[1], n[2]);
		b.v(c[0], c[1], c[2], u, v, n[0], n[1], n[2]);
		b.v(d[0], d[1], d[2], u, v, n[0], n[1], n[2]);
	}

	/** The four sides of one voxel (inner edge on the body, outer edge at +t). */
	private static void side(Buf b, int[] px, int tx, int ty, int ua, int ub, int va, int vb, float du, float dv, float[] p00, float[] p10, float[] p01, float[] p11,
			float[] nrm, float t, float tu, float tv) {
		// neighbours in texture space: (-1,0) -> edge p00-p01 ... the sign of du / dv decides which world edge it is
		boolean left = !solid(px, tx - 1, ty) || tx - 1 < ua;
		boolean right = !solid(px, tx + 1, ty) || tx + 1 >= ub;
		boolean up = !solid(px, tx, ty - 1) || ty - 1 < va;
		boolean down = !solid(px, tx, ty + 1) || ty + 1 >= vb;
		// edge in "s" direction at the lower texture-u side is p00-p01 when du > 0 (s grows with u), else p10-p11
		float[][] eU0 = du > 0 ? new float[][] {p00, p01} : new float[][] {p10, p11};
		float[][] eU1 = du > 0 ? new float[][] {p10, p11} : new float[][] {p00, p01};
		float[][] eV0 = dv > 0 ? new float[][] {p00, p10} : new float[][] {p01, p11};
		float[][] eV1 = dv > 0 ? new float[][] {p01, p11} : new float[][] {p00, p10};
		if (left) {
			edge(b, eU0[0], eU0[1], p00, p10, p01, p11, nrm, t, tu, tv);
		}
		if (right) {
			edge(b, eU1[0], eU1[1], p00, p10, p01, p11, nrm, t, tu, tv);
		}
		if (up) {
			edge(b, eV0[0], eV0[1], p00, p10, p01, p11, nrm, t, tu, tv);
		}
		if (down) {
			edge(b, eV1[0], eV1[1], p00, p10, p01, p11, nrm, t, tu, tv);
		}
	}

	private static void edge(Buf b, float[] a, float[] c, float[] p00, float[] p10, float[] p01, float[] p11, float[] nrm, float t, float tu, float tv) {
		// side normal: from the voxel's centre to the edge's middle, flattened into the face plane
		float cx = (p00[0] + p10[0] + p01[0] + p11[0]) / 4, cy = (p00[1] + p10[1] + p01[1] + p11[1]) / 4, cz = (p00[2] + p10[2] + p01[2] + p11[2]) / 4;
		float[] sn = {(a[0] + c[0]) / 2 - cx, (a[1] + c[1]) / 2 - cy, (a[2] + c[2]) / 2 - cz};
		float dot = sn[0] * nrm[0] + sn[1] * nrm[1] + sn[2] * nrm[2];
		sn[0] -= nrm[0] * dot;
		sn[1] -= nrm[1] * dot;
		sn[2] -= nrm[2] * dot;
		float l = (float) Math.sqrt(sn[0] * sn[0] + sn[1] * sn[1] + sn[2] * sn[2]);
		if (l < 1e-6F) {
			return;
		}
		sn[0] /= l;
		sn[1] /= l;
		sn[2] /= l;
		float[] ao = off(a, nrm, t), co = off(c, nrm, t);
		// winding so that the quad faces the side normal
		float[] e1 = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
		float[] e2 = {ao[0] - a[0], ao[1] - a[1], ao[2] - a[2]};
		float[] cr = {e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]};
		if (cr[0] * sn[0] + cr[1] * sn[1] + cr[2] * sn[2] >= 0) {
			quad(b, a, c, co, ao, tu, tv, sn);
		} else {
			quad(b, c, a, ao, co, tu, tv, sn);
		}
	}
}
