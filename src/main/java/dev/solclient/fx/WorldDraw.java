package dev.solclient.fx;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Small helpers to draw glowing lines, boxes and markers in the world from a WorldRenderEvents hook (camera-relative,
 * same technique as the floating browser panels). Lines are thin quads turned towards the camera, so they have a real
 * thickness and look clean at any distance.
 */
public final class WorldDraw {

	public static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
	private static final int LIGHT = 0xF000F0;

	private WorldDraw() {
	}

	/** Collects segments, then draws them all in one go. */
	public static final class Lines {
		private float[] buf = new float[224];
		private int[] cols = new int[64];
		private int n; // floats used (7 per line)
		private int cn; // colours used (2 per line)
		private final Vec3d cam;

		public Lines(Vec3d cam) {
			this.cam = cam;
		}

		/** A line from a to b (world coordinates), width in blocks, colour ARGB (each end can have its own). */
		public void line(double ax, double ay, double az, double bx, double by, double bz, float width, int colA, int colB) {
			if (n + 7 > buf.length) {
				buf = java.util.Arrays.copyOf(buf, buf.length * 2);
			}
			if (cn + 2 > cols.length) {
				cols = java.util.Arrays.copyOf(cols, cols.length * 2);
			}
			buf[n++] = (float) (ax - cam.x);
			buf[n++] = (float) (ay - cam.y);
			buf[n++] = (float) (az - cam.z);
			buf[n++] = (float) (bx - cam.x);
			buf[n++] = (float) (by - cam.y);
			buf[n++] = (float) (bz - cam.z);
			buf[n++] = width;
			cols[cn++] = colA;
			cols[cn++] = colB;
		}

		public void line(double ax, double ay, double az, double bx, double by, double bz, float width, int col) {
			line(ax, ay, az, bx, by, bz, width, col, col);
		}

		/** The 12 edges of a box. */
		public void box(double x0, double y0, double z0, double x1, double y1, double z1, float width, int col) {
			line(x0, y0, z0, x1, y0, z0, width, col);
			line(x0, y0, z1, x1, y0, z1, width, col);
			line(x0, y0, z0, x0, y0, z1, width, col);
			line(x1, y0, z0, x1, y0, z1, width, col);
			line(x0, y1, z0, x1, y1, z0, width, col);
			line(x0, y1, z1, x1, y1, z1, width, col);
			line(x0, y1, z0, x0, y1, z1, width, col);
			line(x1, y1, z0, x1, y1, z1, width, col);
			line(x0, y0, z0, x0, y1, z0, width, col);
			line(x1, y0, z0, x1, y1, z0, width, col);
			line(x0, y0, z1, x0, y1, z1, width, col);
			line(x1, y0, z1, x1, y1, z1, width, col);
		}

		public boolean isEmpty() {
			return n == 0;
		}

		/** Sends everything to the renderer. */
		public void draw(MatrixStack m, OrderedRenderCommandQueue q) {
			if (n == 0) {
				return;
			}
			final float[] b = buf;
			final int[] cl = cols;
			final int count = n;
			q.submitCustom(m, RenderLayers.entityTranslucent(WHITE), (e, vc) -> {
				for (int i = 0, c = 0; i < count; i += 7, c += 2) {
					seg(vc, e, b[i], b[i + 1], b[i + 2], b[i + 3], b[i + 4], b[i + 5], b[i + 6], cl[c], cl[c + 1]);
				}
			});
		}
	}

	private static void seg(VertexConsumer vc, MatrixStack.Entry e, float ax, float ay, float az, float bx, float by, float bz, float w, int ca,
			int cb) {
		// side = direction x (vector to the camera), so the quad faces the camera
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float mx = (ax + bx) * 0.5F, my = (ay + by) * 0.5F, mz = (az + bz) * 0.5F;
		float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
		float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (l < 1e-6F) {
			return;
		}
		float h = w * 0.5F / l;
		sx *= h;
		sy *= h;
		sz *= h;
		float nx = -mx, ny = -my, nz = -mz;
		float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6F;
		nx /= nl;
		ny /= nl;
		nz /= nl;
		// this order makes the front face point at the camera (visible even if back faces are culled)
		v(vc, e, ax + sx, ay + sy, az + sz, ca, nx, ny, nz);
		v(vc, e, ax - sx, ay - sy, az - sz, ca, nx, ny, nz);
		v(vc, e, bx - sx, by - sy, bz - sz, cb, nx, ny, nz);
		v(vc, e, bx + sx, by + sy, bz + sz, cb, nx, ny, nz);
	}

	private static void v(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, int col, float nx, float ny, float nz) {
		vc.vertex(e, x, y, z).color(col).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(LIGHT).normal(e, nx, ny, nz);
	}
}
