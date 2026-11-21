package dev.sleepclient.gui;

import dev.sleepclient.cosmetic.CosmeticModels;
import dev.sleepclient.social.RemoteTextures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * Draws a player skin as a real 3D model in a GUI, without a world or an entity: every visible face is a textured
 * rectangle drawn through an affine 2D matrix, sorted back to front (orthographic camera, slightly above).
 * - bust: head, torso and arms (home screen); full: the whole body with legs (Locker preview);
 * - optional 3D cosmetics (hats, wings, robe, pets) drawn on the model, so the shop shows items on your own skin.
 * Positive yaw = looking to the screen's left.
 */
public final class SkinModel3D {

	private SkinModel3D() {
	}

	/** One drawable face: texture region (u, v, w, h) of a texture, mapped to the screen by an affine matrix. */
	private record Face(double depth, Identifier tex, int texW, int texH, float u, float v, int w, int h, Matrix3x2f m) {
	}

	/** View transform: yaw around the vertical axis (about a pivot), then the camera pitch. */
	private static final class View {
		final double cy, sy, cp, sp, px, py, pz;

		View(double yawDeg, double pitchDeg, double px, double py, double pz) {
			cy = Math.cos(Math.toRadians(yawDeg));
			sy = Math.sin(Math.toRadians(yawDeg));
			cp = Math.cos(Math.toRadians(pitchDeg));
			sp = Math.sin(Math.toRadians(pitchDeg));
			this.px = px;
			this.py = py;
			this.pz = pz;
		}

		/** Point -> {screenX, screenY, depth}. y points down, +z towards the viewer. */
		double[] point(double x, double y, double z) {
			x -= px;
			y -= py;
			z -= pz;
			double x1 = x * cy - z * sy, z1 = x * sy + z * cy;
			double y2 = y * cp + z1 * sp, z2 = -y * sp + z1 * cp;
			return new double[]{x1 + px, y2 + py, z2 + pz};
		}

		double[] dir(double x, double y, double z) {
			double x1 = x * cy - z * sy, z1 = x * sy + z * cy;
			return new double[]{x1, y * cp + z1 * sp, -y * sp + z1 * cp};
		}
	}

	/** Screen placement shared by all faces of one draw call. */
	private record Place(float cx, float top, float px) {
		float sx(double x) {
			return (float) (cx + x * px);
		}

		float sy(double y) {
			return (float) (top + (y + 8) * px);
		}
	}

	// ---------------------------------------------------------------- skin cubes
	private static void cube(List<Face> out, View view, Place pl, RemoteTextures.Tex tex, double x0, double y0, double z0, int w, int h, int d,
			int U, int V, double inflate) {
		double x1 = x0 + w + inflate, y1 = y0 + h + inflate, z1 = z0 + d + inflate;
		x0 -= inflate;
		y0 -= inflate;
		z0 -= inflate;
		double W = x1 - x0, H = y1 - y0, D = z1 - z0;
		// front (+z), player's right side (-x), left side (+x), back (-z), top (-y), bottom (+y)
		skinFace(out, view, pl, tex, new double[]{x0, y0, z1}, new double[]{W, 0, 0}, new double[]{0, H, 0}, new double[]{0, 0, 1}, U + d, V + d, w, h);
		skinFace(out, view, pl, tex, new double[]{x0, y0, z0}, new double[]{0, 0, D}, new double[]{0, H, 0}, new double[]{-1, 0, 0}, U, V + d, d, h);
		skinFace(out, view, pl, tex, new double[]{x1, y0, z1}, new double[]{0, 0, -D}, new double[]{0, H, 0}, new double[]{1, 0, 0}, U + d + w, V + d, d, h);
		skinFace(out, view, pl, tex, new double[]{x1, y0, z0}, new double[]{-W, 0, 0}, new double[]{0, H, 0}, new double[]{0, 0, -1}, U + 2 * d + w, V + d, w, h);
		skinFace(out, view, pl, tex, new double[]{x0, y0, z0}, new double[]{W, 0, 0}, new double[]{0, 0, D}, new double[]{0, -1, 0}, U + d, V, w, d);
		skinFace(out, view, pl, tex, new double[]{x0, y1, z1}, new double[]{W, 0, 0}, new double[]{0, 0, -D}, new double[]{0, 1, 0}, U + d + w, V, w, d);
	}

	private static void skinFace(List<Face> out, View view, Place pl, RemoteTextures.Tex tex, double[] o, double[] u, double[] v, double[] n,
			int tu, int tv, int tw, int th) {
		double[] nn = view.dir(n[0], n[1], n[2]);
		if (nn[2] <= 1e-4) {
			return; // facing away
		}
		double[] po = view.point(o[0], o[1], o[2]);
		double[] pu = view.dir(u[0], u[1], u[2]);
		double[] pv = view.dir(v[0], v[1], v[2]);
		double depth = po[2] + (pu[2] + pv[2]) / 2;
		int k = Math.max(1, tex.width() / 64);
		Matrix3x2f m = new Matrix3x2f(
				(float) (pu[0] / tw * pl.px()), (float) (pu[1] / tw * pl.px()),
				(float) (pv[0] / th * pl.px()), (float) (pv[1] / th * pl.px()),
				pl.sx(po[0]), pl.sy(po[1]));
		// the region is drawn in k-times larger local units for HD skins: scale back
		if (k > 1) {
			m.scale(1F / k, 1F / k);
		}
		out.add(new Face(depth, tex.id(), tex.width(), tex.height(), tu * k, tv * k, tw * k, th * k, m));
	}

	// ---------------------------------------------------------------- cosmetics
	/** A cosmetic piece turned into quads (pairs of triangles), with their texture-space rectangles. */
	private record Quad(double[] p, float u0, float v0, int w, int h, double[] uvToPos, double[] n) {
	}

	private static final Map<String, List<List<Quad>>> QUADS = new ConcurrentHashMap<>();

	/** Pairs the triangles of each piece into textured parallelograms (cube faces), once per model. */
	private static List<List<Quad>> quads(CosmeticModels.Def d, int texW, int texH) {
		return QUADS.computeIfAbsent(d.geometry() + "@" + texW + "x" + texH, k -> {
			List<List<Quad>> parts = new ArrayList<>();
			for (CosmeticModels.Part part : CosmeticModels.geometry(d)) {
				List<Quad> qs = new ArrayList<>();
				float[] v = part.verts();
				int tris = part.triangles();
				for (int t = 0; t + 1 < tris; t++) {
					Quad q = pair(v, t * 15, (t + 1) * 15, texW, texH, part.normals(), t);
					if (q != null) {
						qs.add(q);
						t++;
					}
				}
				parts.add(qs);
			}
			return parts;
		});
	}

	/** Two triangles a-b-c and a-c-d (or any order sharing an edge) -> parallelogram with an affine uv -> position map. */
	private static Quad pair(float[] v, int o1, int o2, int texW, int texH, float[] normals, int t) {
		double[][] P = new double[6][];
		double[][] T = new double[6][];
		for (int i = 0; i < 3; i++) {
			P[i] = new double[]{v[o1 + i * 5], v[o1 + i * 5 + 1], v[o1 + i * 5 + 2]};
			T[i] = new double[]{v[o1 + i * 5 + 3] * texW, v[o1 + i * 5 + 4] * texH};
			P[3 + i] = new double[]{v[o2 + i * 5], v[o2 + i * 5 + 1], v[o2 + i * 5 + 2]};
			T[3 + i] = new double[]{v[o2 + i * 5 + 3] * texW, v[o2 + i * 5 + 4] * texH};
		}
		// the corner of the second triangle that is not in the first one
		int extra = -1, shared = 0;
		for (int j = 3; j < 6; j++) {
			boolean found = false;
			for (int i = 0; i < 3; i++) {
				if (same(P[i], P[j])) {
					found = true;
				}
			}
			if (found) {
				shared++;
			} else {
				extra = j;
			}
		}
		if (shared != 2 || extra < 0) {
			return null;
		}
		// the corner of the first triangle that is not shared = opposite to "extra"; its two neighbours span the face
		int lone = -1;
		for (int i = 0; i < 3; i++) {
			boolean found = false;
			for (int j = 3; j < 6; j++) {
				if (same(P[i], P[j])) {
					found = true;
				}
			}
			if (!found) {
				lone = i;
			}
		}
		if (lone < 0) {
			return null;
		}
		int a = lone, b = (lone + 1) % 3, c = (lone + 2) % 3;
		// affine map uv -> position from corner a and edges a->b, a->c
		double du1 = T[b][0] - T[a][0], dv1 = T[b][1] - T[a][1], du2 = T[c][0] - T[a][0], dv2 = T[c][1] - T[a][1];
		double det = du1 * dv2 - du2 * dv1;
		if (Math.abs(det) < 1e-6) {
			return null;
		}
		double[] e1 = {P[b][0] - P[a][0], P[b][1] - P[a][1], P[b][2] - P[a][2]};
		double[] e2 = {P[c][0] - P[a][0], P[c][1] - P[a][1], P[c][2] - P[a][2]};
		// position = Pa + Mu * (u - ua) + Mv * (v - va)
		double[] mu = new double[3], mv = new double[3];
		for (int i = 0; i < 3; i++) {
			mu[i] = (e1[i] * dv2 - e2[i] * dv1) / det;
			mv[i] = (-e1[i] * du2 + e2[i] * du1) / det;
		}
		double umin = Math.min(Math.min(T[a][0], T[b][0]), Math.min(T[c][0], T[extra][0]));
		double vmin = Math.min(Math.min(T[a][1], T[b][1]), Math.min(T[c][1], T[extra][1]));
		double umax = Math.max(Math.max(T[a][0], T[b][0]), Math.max(T[c][0], T[extra][0]));
		double vmax = Math.max(Math.max(T[a][1], T[b][1]), Math.max(T[c][1], T[extra][1]));
		int w = (int) Math.max(1, Math.round(umax - umin)), h = (int) Math.max(1, Math.round(vmax - vmin));
		// origin of the texture rectangle in model space
		double[] origin = new double[3];
		for (int i = 0; i < 3; i++) {
			origin[i] = P[a][i] + mu[i] * (umin - T[a][0]) + mv[i] * (vmin - T[a][1]);
		}
		double[] n = {normals[t * 3], normals[t * 3 + 1], normals[t * 3 + 2]};
		return new Quad(origin, (float) umin, (float) vmin, w, h, new double[]{mu[0], mu[1], mu[2], mv[0], mv[1], mv[2]}, n);
	}

	private static boolean same(double[] a, double[] b) {
		return Math.abs(a[0] - b[0]) < 1e-3 && Math.abs(a[1] - b[1]) < 1e-3 && Math.abs(a[2] - b[2]) < 1e-3;
	}

	/** Pivot of each body part in this model's space (y down; z towards the viewer = the player's front). */
	private static double[] pivot(int attach) {
		return switch (attach) {
			case CosmeticModels.RIGHT_ARM -> new double[]{-5, 2, 0};
			case CosmeticModels.LEFT_ARM -> new double[]{5, 2, 0};
			case CosmeticModels.RIGHT_LEG -> new double[]{-1.9, 12, 0};
			case CosmeticModels.LEFT_LEG -> new double[]{1.9, 12, 0};
			case CosmeticModels.PET -> new double[]{-13, -1, -1};
			default -> new double[]{0, 0, 0};
		};
	}

	private static void cosmetic(List<Face> out, CosmeticModels.Def d, View head, View body, Place pl, boolean full) {
		Identifier tex = d.textureId();
		int[] size = {d.texW(), d.texH()};
		List<CosmeticModels.Part> parts = CosmeticModels.geometry(d);
		List<List<Quad>> qs = quads(d, size[0], size[1]);
		int total = 0;
		for (List<Quad> l : qs) {
			total += l.size();
		}
		boolean cull = total > 600; // big closed models (wings): skip the faces turned away
		for (int p = 0; p < parts.size() && p < qs.size(); p++) {
			int attach = parts.get(p).attach();
			if (!full && (attach == CosmeticModels.RIGHT_LEG || attach == CosmeticModels.LEFT_LEG)) {
				continue;
			}
			View view = attach == CosmeticModels.HEAD ? head : body;
			double[] pv = pivot(attach);
			for (Quad q : qs.get(p)) {
				// model space (front = -z) -> this view (front = +z): flip z
				double[] n = view.dir(q.n()[0], q.n()[1], -q.n()[2]);
				if (cull && n[2] < -1e-3) {
					continue;
				}
				double[] o = view.point(q.p()[0] + pv[0], q.p()[1] + pv[1], -(q.p()[2]) + pv[2]);
				double[] a = view.dir(q.uvToPos()[0], q.uvToPos()[1], -q.uvToPos()[2]);
				double[] b = view.dir(q.uvToPos()[3], q.uvToPos()[4], -q.uvToPos()[5]);
				double depth = o[2] + (a[2] * q.w() + b[2] * q.h()) / 2 + n[2] * 0.01;
				Matrix3x2f m = new Matrix3x2f((float) (a[0] * pl.px()), (float) (a[1] * pl.px()), (float) (b[0] * pl.px()), (float) (b[1] * pl.px()),
						pl.sx(o[0]), pl.sy(o[1]));
				out.add(new Face(depth, tex, size[0], size[1], q.u0(), q.v0(), q.w(), q.h(), m));
			}
		}
	}

	// ---------------------------------------------------------------- drawing
	/** The bust (home screen): about 20 * px tall. */
	static void draw(GuiGraphicsExtractor c, RemoteTextures.Tex tex, float cx, float top, float px, double bodyYaw, double headYaw, double pitch) {
		draw(c, tex, cx, top, px, bodyYaw, headYaw, pitch, false, List.of());
	}

	/**
	 * Full model: with full = true the whole body (about 32 * px tall, top = head top), plus the given cosmetics.
	 */
	static void draw(GuiGraphicsExtractor c, RemoteTextures.Tex tex, float cx, float top, float px, double bodyYaw, double headYaw, double pitch,
			boolean full, List<CosmeticModels.Def> cosmetics) {
		boolean legacy = tex.height() * 2 == tex.width();
		List<Face> faces = new ArrayList<>();
		Place pl = new Place(cx, top, px);
		View body = new View(bodyYaw, pitch, 0, 6, 0);
		View head = new View(headYaw, pitch, 0, 0, 0); // head turns around the neck

		cube(faces, head, pl, tex, -4, -8, -4, 8, 8, 8, 0, 0, 0);
		cube(faces, head, pl, tex, -4, -8, -4, 8, 8, 8, 32, 0, 0.5);

		cube(faces, body, pl, tex, -4, 0, -2, 8, 12, 4, 16, 16, 0);
		cube(faces, body, pl, tex, -8, 0, -2, 4, 12, 4, 40, 16, 0);
		cube(faces, body, pl, tex, 4, 0, -2, 4, 12, 4, legacy ? 40 : 32, legacy ? 16 : 48, 0);
		if (full) {
			cube(faces, body, pl, tex, -4, 12, -2, 4, 12, 4, 0, 16, 0);
			cube(faces, body, pl, tex, 0, 12, -2, 4, 12, 4, legacy ? 0 : 16, legacy ? 16 : 48, 0);
		}
		if (!legacy) {
			cube(faces, body, pl, tex, -4, 0, -2, 8, 12, 4, 16, 32, 0.25);
			cube(faces, body, pl, tex, -8, 0, -2, 4, 12, 4, 40, 32, 0.25);
			cube(faces, body, pl, tex, 4, 0, -2, 4, 12, 4, 48, 48, 0.25);
			if (full) {
				cube(faces, body, pl, tex, -4, 12, -2, 4, 12, 4, 0, 32, 0.25);
				cube(faces, body, pl, tex, 0, 12, -2, 4, 12, 4, 0, 48, 0.25);
			}
		}
		for (CosmeticModels.Def d : cosmetics) {
			try {
				cosmetic(faces, d, head, body, pl, full);
			} catch (RuntimeException ignored) {
			}
		}
		faces.sort((a, b) -> Double.compare(a.depth(), b.depth()));

		for (Face f : faces) {
			c.pose().pushMatrix();
			c.pose().mul(f.m());
			c.blit(RenderPipelines.GUI_TEXTURED, f.tex(), 0, 0, f.u(), f.v(), f.w(), f.h(), f.w(), f.h(), f.texW(), f.texH(), 0xFFFFFFFF);
			c.pose().popMatrix();
		}
	}
}
