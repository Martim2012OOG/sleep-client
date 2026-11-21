package dev.sleepclient.pack;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny software 3D renderer for the Texture Styles pictures (runs on a background thread, no OpenGL). It builds the
 * real Minecraft shapes from a pack's texture - the end crystal on its bedrock base, blocks as cubes, shulker boxes, the
 * ender chest, armour on a body, shields, elytra wings, crossed planes for cobwebs and fire - and items as 1-pixel thick
 * extruded sprites like in your hand. Drawn back to front with alpha blending, 2x supersampled so the edges are smooth.
 */
public final class StylePreview3D {

	private StylePreview3D() {
	}

	/** A texture: ARGB pixels. */
	public record Tex(int[] px, int w, int h, float k) {
		public Tex(int[] px, int w, int h) {
			this(px, w, h, 1F);
		}

		/** u, v in texture units (times k for HD entity textures, whose nets are written for 64 px). */
		int at(float u, float v) {
			int x = Math.min(w - 1, Math.max(0, (int) Math.floor(u * k))), y = Math.min(h - 1, Math.max(0, (int) Math.floor(v * k)));
			return px[y * w + x];
		}

		/** The first frame of an animated strip (taller than wide, height a multiple of the width). */
		Tex firstFrame() {
			if (h > w && h % w == 0) {
				int[] p = new int[w * w];
				System.arraycopy(px, 0, p, 0, w * w);
				return new Tex(p, w, w);
			}
			return this;
		}
	}

	/** A finished picture. */
	public record Pic(int[] px, int w, int h) {
	}

	// ---------------------------------------------------------------- scene
	private static final class Quad {
		final float[] p = new float[12]; // 4 corners x,y,z (model space, y up)
		final float[] uv = new float[8];
		Tex tex;
		int colour; // used when tex == null
		float depth, shade;
		final float[] s = new float[12]; // screen x, y, z
	}

	private final List<Quad> quads = new ArrayList<>();
	private float yaw = -35, pitch = 25, roll;

	private StylePreview3D view(float yawDeg, float pitchDeg) {
		yaw = yawDeg;
		pitch = pitchDeg;
		return this;
	}

	private void quad(float[] a, float[] b, float[] c, float[] d, Tex t, float u0, float v0, float u1, float v1, int colour) {
		Quad q = new Quad();
		float[][] cs = {a, b, c, d};
		for (int i = 0; i < 4; i++) {
			q.p[i * 3] = cs[i][0];
			q.p[i * 3 + 1] = cs[i][1];
			q.p[i * 3 + 2] = cs[i][2];
		}
		// a = top-left, b = top-right, c = bottom-right, d = bottom-left of the texture region
		q.uv[0] = u0;
		q.uv[1] = v0;
		q.uv[2] = u1;
		q.uv[3] = v0;
		q.uv[4] = u1;
		q.uv[5] = v1;
		q.uv[6] = u0;
		q.uv[7] = v1;
		q.tex = t;
		q.colour = colour;
		quads.add(q);
	}

	/** Transform applied to the next shapes (rotation in degrees around the given centre, then a move). */
	private float[] xf = null;

	private float[] P(float x, float y, float z) {
		if (xf == null) {
			return new float[]{x, y, z};
		}
		// xf: cx, cy, cz, rx, ry, rz (degrees), tx, ty, tz, scale
		float sc = xf[9];
		x = (x - xf[0]) * sc;
		y = (y - xf[1]) * sc;
		z = (z - xf[2]) * sc;
		float[] r = rot(x, y, z, xf[3], xf[4], xf[5]);
		return new float[]{r[0] + xf[0] + xf[6], r[1] + xf[1] + xf[7], r[2] + xf[2] + xf[8]};
	}

	private static float[] rot(float x, float y, float z, float rxD, float ryD, float rzD) {
		double rx = Math.toRadians(rxD), ry = Math.toRadians(ryD), rz = Math.toRadians(rzD);
		// Z, then X, then Y
		double x1 = x * Math.cos(rz) - y * Math.sin(rz), y1 = x * Math.sin(rz) + y * Math.cos(rz), z1 = z;
		double y2 = y1 * Math.cos(rx) - z1 * Math.sin(rx), z2 = y1 * Math.sin(rx) + z1 * Math.cos(rx), x2 = x1;
		double x3 = x2 * Math.cos(ry) + z2 * Math.sin(ry), z3 = -x2 * Math.sin(ry) + z2 * Math.cos(ry);
		return new float[]{(float) x3, (float) y2, (float) z3};
	}

	/**
	 * A Minecraft "box UV" cuboid: x0..x0+dx, y0..y0+dy, z0..z0+dz (y up, +z towards the viewer), texture offset u, v.
	 * Front (+z) = north part of the net, right (+x) = east part, top = up part.
	 */
	private void box(float x0, float y0, float z0, float dx, float dy, float dz, Tex t, float u, float v) {
		float x1 = x0 + dx, y1 = y0 + dy, z1 = z0 + dz;
		// up
		quad(P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1), t, u + dz, v, u + dz + dx, v + dz, 0);
		// down
		quad(P(x0, y0, z1), P(x1, y0, z1), P(x1, y0, z0), P(x0, y0, z0), t, u + dz + dx, v, u + dz + dx * 2, v + dz, 0);
		// front (+z)
		quad(P(x0, y1, z1), P(x1, y1, z1), P(x1, y0, z1), P(x0, y0, z1), t, u + dz, v + dz, u + dz + dx, v + dz + dy, 0);
		// right (+x)
		quad(P(x1, y1, z1), P(x1, y1, z0), P(x1, y0, z0), P(x1, y0, z1), t, u + dz + dx, v + dz, u + dz * 2 + dx, v + dz + dy, 0);
		// back (-z)
		quad(P(x1, y1, z0), P(x0, y1, z0), P(x0, y0, z0), P(x1, y0, z0), t, u + dz * 2 + dx, v + dz, u + dz * 2 + dx * 2, v + dz + dy, 0);
		// left (-x)
		quad(P(x0, y1, z0), P(x0, y1, z1), P(x0, y0, z1), P(x0, y0, z0), t, u, v + dz, u + dz, v + dz + dy, 0);
	}

	/** A block: a 16x16x16 cube, 'top' on the top and bottom, 'side' around. */
	private void block(Tex side, Tex top) {
		float a = 0, b = 16;
		quad(P(a, b, a), P(b, b, a), P(b, b, b), P(a, b, b), top, 0, 0, top.w, top.h, 0);
		quad(P(a, a, b), P(b, a, b), P(b, a, a), P(a, a, a), top, 0, 0, top.w, top.h, 0);
		quad(P(a, b, b), P(b, b, b), P(b, a, b), P(a, a, b), side, 0, 0, side.w, side.h, 0);
		quad(P(b, b, b), P(b, b, a), P(b, a, a), P(b, a, b), side, 0, 0, side.w, side.h, 0);
		quad(P(b, b, a), P(a, b, a), P(a, a, a), P(b, a, a), side, 0, 0, side.w, side.h, 0);
		quad(P(a, b, a), P(a, b, b), P(a, a, b), P(a, a, a), side, 0, 0, side.w, side.h, 0);
	}

	/** Two crossed planes (cobweb, fire, flowers...). */
	private void cross(Tex t) {
		quad(P(0, 16, 0), P(16, 16, 16), P(16, 0, 16), P(0, 0, 0), t, 0, 0, t.w, t.h, 0);
		quad(P(16, 16, 0), P(0, 16, 16), P(0, 0, 16), P(16, 0, 0), t, 0, 0, t.w, t.h, 0);
	}

	/** An item sprite pushed out to 1 pixel of thickness, like an item in your hand. */
	private void extrude(Tex t) {
		int w = t.w, h = t.h;
		float s = 16F / Math.max(w, h), th = 1F;
		float z0 = -th / 2, z1 = th / 2;
		quad(P(0, 16, z1), P(16, 16, z1), P(16, 16 - h * s, z1), P(0, 16 - h * s, z1), t, 0, 0, w, h, 0);
		quad(P(16, 16, z0), P(0, 16, z0), P(0, 16 - h * s, z0), P(16, 16 - h * s, z0), t, w, 0, 0, h, 0);
		// the edges: one small face per pixel that has an empty neighbour
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = t.px[y * w + x];
				if ((c >>> 24) < 128) {
					continue;
				}
				float px0 = x * s, px1 = (x + 1) * s, py1 = 16 - y * s, py0 = 16 - (y + 1) * s;
				int dark = darker(c, 0.82F);
				if (y == 0 || (t.px[(y - 1) * w + x] >>> 24) < 128) {
					quad(P(px0, py1, z0), P(px1, py1, z0), P(px1, py1, z1), P(px0, py1, z1), null, 0, 0, 0, 0, c);
				}
				if (y == h - 1 || (t.px[(y + 1) * w + x] >>> 24) < 128) {
					quad(P(px0, py0, z1), P(px1, py0, z1), P(px1, py0, z0), P(px0, py0, z0), null, 0, 0, 0, 0, dark);
				}
				if (x == w - 1 || (t.px[y * w + x + 1] >>> 24) < 128) {
					quad(P(px1, py1, z1), P(px1, py1, z0), P(px1, py0, z0), P(px1, py0, z1), null, 0, 0, 0, 0, dark);
				}
				if (x == 0 || (t.px[y * w + x - 1] >>> 24) < 128) {
					quad(P(px0, py1, z0), P(px0, py1, z1), P(px0, py0, z1), P(px0, py0, z0), null, 0, 0, 0, 0, dark);
				}
			}
		}
	}

	private static int darker(int c, float f) {
		int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
		return (c & 0xFF000000) | r << 16 | g << 8 | b;
	}

	// ---------------------------------------------------------------- render
	private Pic render(int size) {
		int ss = size * 2;
		float[] L = norm(-0.35F, 0.85F, 0.55F);
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (Quad q : quads) {
			for (int i = 0; i < 4; i++) {
				float[] r = rot(q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2], 0, yaw, 0);
				float[] r2 = rot(r[0], r[1], r[2], pitch, 0, roll);
				q.s[i * 3] = r2[0];
				q.s[i * 3 + 1] = -r2[1];
				q.s[i * 3 + 2] = r2[2];
				minX = Math.min(minX, r2[0]);
				maxX = Math.max(maxX, r2[0]);
				minY = Math.min(minY, -r2[1]);
				maxY = Math.max(maxY, -r2[1]);
			}
			// face normal (in view space) -> brightness; Minecraft-like: top light, sides a bit darker
			float ax = q.s[3] - q.s[0], ay = -(q.s[4] - q.s[1]), az = q.s[5] - q.s[2];
			float bx = q.s[9] - q.s[0], by = -(q.s[10] - q.s[1]), bz = q.s[11] - q.s[2];
			float[] n = norm(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
			float d = Math.abs(n[0] * L[0] + n[1] * L[1] + n[2] * L[2]);
			q.shade = 0.62F + 0.38F * d;
			q.depth = (q.s[2] + q.s[5] + q.s[8] + q.s[11]) / 4F;
		}
		float span = Math.max(maxX - minX, maxY - minY);
		if (span <= 0) {
			return null;
		}
		float scale = ss * 0.86F / span, ox = ss / 2F - (minX + maxX) / 2F * scale, oy = ss / 2F - (minY + maxY) / 2F * scale;
		quads.sort((a, b) -> Float.compare(a.depth, b.depth)); // far (small z = away from viewer) first
		float[] acc = new float[ss * ss * 4]; // premultiplied r, g, b, a
		for (Quad q : quads) {
			float[] sx = new float[4], sy = new float[4];
			for (int i = 0; i < 4; i++) {
				sx[i] = q.s[i * 3] * scale + ox;
				sy[i] = q.s[i * 3 + 1] * scale + oy;
			}
			tri(acc, ss, q, sx, sy, 0, 1, 2);
			tri(acc, ss, q, sx, sy, 0, 2, 3);
		}
		int[] out = new int[size * size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float r = 0, g = 0, b = 0, a = 0;
				for (int k = 0; k < 4; k++) {
					int i = ((y * 2 + (k >> 1)) * ss + x * 2 + (k & 1)) * 4;
					r += acc[i];
					g += acc[i + 1];
					b += acc[i + 2];
					a += acc[i + 3];
				}
				a /= 4;
				if (a <= 0.003F) {
					continue;
				}
				r = r / 4 / a;
				g = g / 4 / a;
				b = b / 4 / a;
				out[y * size + x] = clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
			}
		}
		return new Pic(out, size, size);
	}

	private static int clamp(float f) {
		return Math.max(0, Math.min(255, Math.round(f * 255)));
	}

	private static float[] norm(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return l < 1e-6F ? new float[]{0, 1, 0} : new float[]{x / l, y / l, z / l};
	}

	private static void tri(float[] acc, int ss, Quad q, float[] sx, float[] sy, int i0, int i1, int i2) {
		float x0 = sx[i0], y0 = sy[i0], x1 = sx[i1], y1 = sy[i1], x2 = sx[i2], y2 = sy[i2];
		float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
		if (Math.abs(area) < 1e-4F) {
			return;
		}
		int bx0 = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2)))), bx1 = Math.min(ss - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
		int by0 = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2)))), by1 = Math.min(ss - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
		float u0 = q.uv[i0 * 2], v0 = q.uv[i0 * 2 + 1], u1 = q.uv[i1 * 2], v1 = q.uv[i1 * 2 + 1], u2 = q.uv[i2 * 2], v2 = q.uv[i2 * 2 + 1];
		for (int y = by0; y <= by1; y++) {
			for (int x = bx0; x <= bx1; x++) {
				float px = x + 0.5F, py = y + 0.5F;
				float w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area;
				float w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area;
				float w2 = 1 - w0 - w1;
				if (w0 < -1e-4F || w1 < -1e-4F || w2 < -1e-4F) {
					continue;
				}
				int c;
				if (q.tex != null) {
					float u = w0 * u0 + w1 * u1 + w2 * u2, v = w0 * v0 + w1 * v1 + w2 * v2;
					c = q.tex.at(u, v);
				} else {
					c = q.colour;
				}
				float a = (c >>> 24) / 255F;
				if (a < 0.04F) {
					continue;
				}
				float r = ((c >> 16) & 0xFF) / 255F * q.shade, g = ((c >> 8) & 0xFF) / 255F * q.shade, b = (c & 0xFF) / 255F * q.shade;
				int i = (y * ss + x) * 4;
				float k = 1 - a;
				acc[i] = r * a + acc[i] * k;
				acc[i + 1] = g * a + acc[i + 1] * k;
				acc[i + 2] = b * a + acc[i + 2] * k;
				acc[i + 3] = a + acc[i + 3] * k;
			}
		}
	}

	// ---------------------------------------------------------------- what to build
	/** Flat picture (GUI sprites, particles, glint): the first frame, scaled up without blur. */
	public static Pic flat(Tex t, int size) {
		t = t.firstFrame();
		int[] out = new int[size * size];
		float s = Math.min(size * 0.86F / t.w, size * 0.86F / t.h);
		int w = Math.round(t.w * s), h = Math.round(t.h * s), x0 = (size - w) / 2, y0 = (size - h) / 2;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				out[(y0 + y) * size + x0 + x] = t.at(x / s, y / s);
			}
		}
		return new Pic(out, size, size);
	}

	/**
	 * Builds the 3D picture for a texture of a pack. 'path' is relative to assets/minecraft/ (for example
	 * textures/entity/end_crystal/end_crystal.png); 'top' is the top texture of a block, when there is one.
	 */
	public static Pic of(String path, Tex t, Tex top, int size) {
		String n = path.substring(path.lastIndexOf('/') + 1);
		StylePreview3D r = new StylePreview3D();
		float k = t.w / 64F; // HD entity textures: same net, more pixels
		if (path.startsWith("textures/entity/end_crystal/") && n.startsWith("end_crystal") && t.w == t.h * 2) {
			// bedrock base, then the two glass frames and the core, standing on a corner
			Tex s = scaled(t, k);
			r.box(-6, 0, -6, 12, 4, 12, s, 0, 16);
			float[] cubes = {1F, 0.875F, 0.766F};
			float[] us = {0, 0, 32};
			for (int i = 0; i < 3; i++) {
				r.xf = new float[]{0, 12, 0, 35.26F + i * 12, 45 + i * 25, 0, 0, 0, 0, cubes[i] * 1.5F};
				r.box(-4, 8, -4, 8, 8, 8, s, us[i], 0);
			}
			r.xf = null;
			return r.view(-30, 18).render(size);
		}
		if (path.startsWith("textures/entity/shulker/")) {
			Tex s = scaled(t, k);
			r.box(-8, 0, -8, 16, 8, 16, s, 0, 28);
			r.box(-8, 4, -8, 16, 12, 16, s, 0, 0);
			return r.view(-35, 26).render(size);
		}
		if (path.startsWith("textures/entity/chest/")) {
			Tex s = scaled(t, k);
			r.box(-7, 0, -7, 14, 10, 14, s, 0, 19);
			r.box(-7, 9, -7, 14, 5, 14, s, 0, 0);
			r.box(-1, 7, 7, 2, 4, 1, s, 0, 0);
			return r.view(-35, 24).render(size);
		}
		if (path.startsWith("textures/entity/equipment/humanoid")) {
			Tex s = scaled(t, t.w / 64F);
			boolean legs = path.contains("leggings");
			if (legs) {
				r.box(-4, 12, -2, 8, 12, 4, s, 16, 16);
				r.box(-4.1F, 0, -2.1F, 4, 12, 4.2F, s, 0, 16);
				r.box(0.1F, 0, -2.1F, 4, 12, 4.2F, s, 0, 16);
			} else {
				r.box(-4.5F, 24, -4.5F, 9, 9, 9, s, 0, 0); // helmet (a bit bigger than the head)
				r.box(-4.5F, 12, -2.5F, 9, 12, 5, s, 16, 16); // chestplate
				r.box(-8.5F, 12, -2.5F, 4, 12, 5, s, 40, 16); // arms
				r.box(4.5F, 12, -2.5F, 4, 12, 5, s, 40, 16);
				r.box(-4.1F, 0, -2.2F, 4, 12, 4.4F, s, 0, 16); // legs + boots
				r.box(0.1F, 0, -2.2F, 4, 12, 4.4F, s, 0, 16);
			}
			return r.view(-30, 12).render(size);
		}
		if (path.startsWith("textures/entity/shield")) {
			Tex s = scaled(t, k);
			r.box(-6, -11, -1, 12, 22, 1, s, 0, 0);
			return r.view(-30, 10).render(size);
		}
		if (path.startsWith("textures/entity/elytra") || path.startsWith("textures/entity/equipment/wings/")) {
			Tex s = scaled(t, k);
			r.xf = new float[]{0, 20, 0, 20, -20, -22, 0, 0, 0, 1};
			r.box(0, 0, 0, 10, 20, 2, s, 22, 0);
			r.xf = new float[]{0, 20, 0, 20, 20, 22, 0, 0, 0, 1};
			r.box(-10, 0, 0, 10, 20, 2, s, 22, 0);
			r.xf = null;
			return r.view(-25, 15).render(size);
		}
		if (path.startsWith("textures/block/")) {
			Tex f = t.firstFrame();
			if (n.startsWith("cobweb") || n.contains("fire")) {
				r.cross(f);
				return r.view(-30, 15).render(size);
			}
			r.block(f, top == null ? f : top.firstFrame());
			return r.view(-35, 28).render(size);
		}
		if (path.startsWith("textures/item/")) {
			r.extrude(t.firstFrame());
			return r.view(-28, 14).render(size);
		}
		return flat(t, size);
	}

	/** Entity nets are made for 64 px wide textures; an HD one (128, 256...) uses the same net, sampled k times further. */
	private static Tex scaled(Tex t, float k) {
		return Math.abs(k - 1) < 1e-3F || k <= 0 ? t : new Tex(t.px(), t.w(), t.h(), k);
	}
}
