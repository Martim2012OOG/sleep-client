package dev.solclient.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.solclient.Gfx;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * The moving sky effects drawn in the world around you: soft clouds, an aurora, shooting stars and a coloured haze
 * on the horizon. They are attached to the camera like the sky (so they always look far away) but are real 3D quads,
 * so you can fly under the clouds and the haze softly hides far terrain.
 */
public final class SkyEffects {

	/** Everything the effects need, filled from the mod options every frame. */
	public static final class Settings {
		public boolean clouds;
		public int cloudCount = 60, cloudColour = 0xFFFFFFFF;
		public float cloudHeight = 170, cloudSize = 100, cloudOpacity = 85, cloudSpeed = 30, windDir = 60;
		public boolean aurora, auroraAlways;
		public int auroraA = 0xFF3CFFB4, auroraB = 0xFF8A5CFF;
		public float auroraStrength = 70, auroraHeight = 45, auroraSpeed = 40, auroraDir = 0;
		public boolean shooting;
		public float shootingRate = 40;
		public boolean haze;
		public int hazeColour = 0xFFF0A890;
		public float hazeStrength = 45;
		/** 0..1 how dark (night) it is now, and a colour the clouds take at sunset */
		public float night, sunset;
		public int sunsetColour = 0xFFFF9A6A;
		public boolean rainbow;
		public float rainbowStrength = 55, rainbowDir = 180;
		public boolean sunRays;
		public float sunRayStrength = 40;
		public int sunColour = 0xFFFFE2B8;
		/** unit vector towards the sun */
		public double sx, sy = 0.3, sz = -1;
		/** 0.25..1, lowered by the auto performance mode */
		public float quality = 1F;
	}

	private static final MinecraftClient mc = MinecraftClient.getInstance();
	private static final int LIGHT = 0xF000F0;

	private static Tex cloudTex, beamTex;
	private static boolean texFailed;
	private static final Random RND = new Random();

	// clouds (world x / z, offset y, size, variant, phase)
	private static int cn;
	private static double[] clx = new double[0], clz = new double[0];
	private static float[] cly = new float[0], cls = new float[0], clp = new float[0];
	private static int[] clv = new int[0];
	private static boolean cloudsPlaced;
	private static double lastCx, lastCz;


	private static final List<float[]> STARS = new ArrayList<>(); // x, y, z (relative), dx, dy, dz, age, life

	private SkyEffects() {
	}

	/** Bilinear texture made from pixels. */
	private static final class Tex extends NativeImageBackedTexture {
		final Identifier id;

		Tex(String name, int w, int h, int[] px) {
			super(name, w, h, false);
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
			NativeImage img = getImage();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					img.setColorArgb(x, y, px[y * w + x]);
				}
			}
			upload();
			id = Gfx.id("sky_fx_" + name);
			mc.getTextureManager().registerTexture(id, this);
		}
	}

	private static boolean textures() {
		if (texFailed) {
			return false;
		}
		if (cloudTex == null) {
			try {
				cloudTex = new Tex("clouds", SkyGen.CLOUD * SkyGen.CLOUD_VARIANTS, SkyGen.CLOUD, SkyGen.clouds());
				beamTex = new Tex("beam", SkyGen.BEAM_W, SkyGen.BEAM_H, SkyGen.beam());
			} catch (Throwable t) {
				texFailed = true;
				System.err.println("[Caos] sky effects textures: " + t);
				return false;
			}
		}
		return true;
	}

	private static void v(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float vv, int col, float nx, float ny, float nz) {
		vc.vertex(e, x, y, z).color(col).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV).light(LIGHT).normal(e, nx, ny, nz);
	}

	/** A quad a-b-c-d (camera relative) whose front is turned towards the camera. Colours per corner. */
	private static void quad(VertexConsumer vc, MatrixStack.Entry e, float[] a, float[] b, float[] c, float[] d, float u0, float u1, float v0, float v1,
			int ca, int cb, int cc, int cd) {
		float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		float wx = d[0] - a[0], wy = d[1] - a[1], wz = d[2] - a[2];
		float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
		float mx = (a[0] + c[0]) / 2, my = (a[1] + c[1]) / 2, mz = (a[2] + c[2]) / 2;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6F;
		nx /= len;
		ny /= len;
		nz /= len;
		if (nx * mx + ny * my + nz * mz > 0) {
			// facing away: draw it the other way round
			v(vc, e, a[0], a[1], a[2], u0, v1, ca, -nx, -ny, -nz);
			v(vc, e, d[0], d[1], d[2], u0, v0, cd, -nx, -ny, -nz);
			v(vc, e, c[0], c[1], c[2], u1, v0, cc, -nx, -ny, -nz);
			v(vc, e, b[0], b[1], b[2], u1, v1, cb, -nx, -ny, -nz);
		} else {
			v(vc, e, a[0], a[1], a[2], u0, v1, ca, nx, ny, nz);
			v(vc, e, b[0], b[1], b[2], u1, v1, cb, nx, ny, nz);
			v(vc, e, c[0], c[1], c[2], u1, v0, cc, nx, ny, nz);
			v(vc, e, d[0], d[1], d[2], u0, v0, cd, nx, ny, nz);
		}
	}

	private static int alpha(int rgb, float a) {
		return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24 | (rgb & 0xFFFFFF);
	}

	private static float viewDist() {
		try {
			return Math.max(64F, mc.options.getClampedViewDistance() * 16F);
		} catch (RuntimeException e) {
			return 160F;
		}
	}

	// ------------------------------------------------------------------ per frame
	public static void render(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam, Vector3f right, Vector3f up, float t, float dt, Settings s) {
		if (!(s.clouds || s.aurora || s.shooting || s.haze || s.rainbow || s.sunRays) || !textures()) {
			return;
		}
		float far = viewDist();
		if (s.haze) {
			haze(m, q, s, far);
		}
		if (s.aurora) {
			aurora(m, q, s, t, far);
		}
		if (s.rainbow) {
			rainbow(m, q, s, far);
		}
		if (s.sunRays) {
			sunRays(m, q, s, t, far);
		}
		if (s.shooting) {
			shooting(m, q, s, dt, far, right, up);
		}
		if (s.clouds) {
			clouds(m, q, cam, right, up, t, dt, s, far);
		}
	}

	// ------------------------------------------------------------------ haze
	private static void haze(MatrixStack m, OrderedRenderCommandQueue q, Settings s, float far) {
		final float r = far * 0.82F;
		final int segs = 48;
		final float a = s.hazeStrength / 100F;
		final int col = s.hazeColour;
		q.submitCustom(m, RenderLayers.entityTranslucent(beamTex.id), (e, vc) -> {
			for (int i = 0; i < segs; i++) {
				double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
				float x0 = (float) Math.sin(a0) * r, z0 = (float) -Math.cos(a0) * r, x1 = (float) Math.sin(a1) * r, z1 = (float) -Math.cos(a1) * r;
				float bottom = -r * 0.35F, top = r * 0.28F;
				int cb = alpha(col, a), ct = alpha(col, 0);
				quad(vc, e, new float[] {x0, bottom, z0}, new float[] {x1, bottom, z1}, new float[] {x1, top, z1}, new float[] {x0, top, z0}, 0.5F, 0.5F, 0F,
						1F, cb, cb, ct, ct);
			}
		});
	}

	// ------------------------------------------------------------------ aurora
	private static void aurora(MatrixStack m, OrderedRenderCommandQueue q, Settings s, float t, float far) {
		float vis = s.auroraAlways ? 1F : s.night;
		if (vis < 0.02F) {
			return;
		}
		final float strength = s.auroraStrength / 100F * vis;
		final float dist = Math.min(110F, far * 0.7F);
		final float speed = 0.2F + s.auroraSpeed / 100F * 1.2F;
		final int segs = Math.max(16, Math.round(44 * s.quality));
		final int ca = s.auroraA, cb = s.auroraB;
		final double centre = Math.toRadians(s.auroraDir);
		final float baseH = s.auroraHeight;
		q.submitCustom(m, RenderLayers.entityTranslucent(beamTex.id), (e, vc) -> {
			for (int c = 0; c < 3; c++) {
				float co = c * 1.7F;
				float[] prevB = null, prevT = null;
				int prevBc = 0, prevTc = 0;
				float prevU = 0;
				for (int i = 0; i <= segs; i++) {
					float sgm = i / (float) segs;
					double phi = centre + (sgm - 0.5) * Math.toRadians(150) + (c - 1) * 0.25;
					double rr = dist * (0.85 + 0.12 * Math.sin(sgm * 5 + t * 0.15 * speed + co) + c * 0.08);
					float y0 = baseH + 6F * (float) Math.sin(sgm * 3 + t * 0.2 * speed + co);
					float hgt = (24F + 18F * c) * (0.6F + 0.4F * (float) Math.sin(sgm * 7 - t * 0.5 * speed + co * 2));
					float x = (float) (Math.sin(phi) * rr), z = (float) (-Math.cos(phi) * rr);
					// light ripples running along the curtain
					float pulse = (float) Math.pow(0.5 + 0.5 * Math.sin(sgm * 11 - t * 1.3 * speed + co * 3), 1.6);
					float edge = (float) Math.sin(Math.PI * sgm);
					float a = strength * (0.25F + 0.75F * pulse) * edge * (c == 0 ? 1F : 0.65F);
					int colB = alpha(c == 2 ? cb : ca, a), colT = alpha(cb, a * 0.55F);
					float[] bt = {x, y0, z}, tp = {x, y0 + hgt, z};
					if (prevB != null) {
						quad(vc, e, prevB, bt, tp, prevT, prevU, sgm, 0F, 1F, prevBc, colB, colT, prevTc);
					}
					prevB = bt;
					prevT = tp;
					prevBc = colB;
					prevTc = colT;
					prevU = sgm;
				}
			}
		});
	}

	// ------------------------------------------------------------------ rainbow
	private static final int[] BOW = {0xFF3030, 0xFF8A20, 0xFFE040, 0x40E060, 0x3090FF, 0x5040E0, 0xA040E0};

	private static void rainbow(MatrixStack m, OrderedRenderCommandQueue q, Settings s, float far) {
		float vis = (1F - s.night) * s.rainbowStrength / 100F;
		if (vis < 0.02F) {
			return;
		}
		final float d = Math.min(150F, far * 0.8F);
		double az = Math.toRadians(s.rainbowDir);
		final float hx = (float) Math.sin(az), hz = (float) -Math.cos(az); // where it stands
		final float rx = -hz, rz = hx; // its left-right direction
		final float r = d * 0.62F, band = r * 0.022F;
		final int segs = Math.max(20, Math.round(48 * s.quality));
		final float a0 = vis * 0.55F;
		q.submitCustom(m, RenderLayers.entityTranslucent(beamTex.id), (e, vc) -> {
			float cx = hx * d, cy = -d * 0.12F, cz = hz * d;
			for (int b = 0; b < BOW.length; b++) {
				float r0 = r + (BOW.length / 2F - b - 1) * band, r1 = r0 + band;
				float soft = b == 0 || b == BOW.length - 1 ? 0.6F : 1F;
				for (int i = 0; i < segs; i++) {
					double t0 = Math.PI * i / segs, t1 = Math.PI * (i + 1) / segs;
					float e0 = (float) Math.pow(Math.sin(t0), 0.6), e1 = (float) Math.pow(Math.sin(t1), 0.6); // fades into the ground
					float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
					float[] p0 = {cx + rx * c0 * r0, cy + s0 * r0, cz + rz * c0 * r0};
					float[] p1 = {cx + rx * c1 * r0, cy + s1 * r0, cz + rz * c1 * r0};
					float[] p2 = {cx + rx * c1 * r1, cy + s1 * r1, cz + rz * c1 * r1};
					float[] p3 = {cx + rx * c0 * r1, cy + s0 * r1, cz + rz * c0 * r1};
					int k0 = alpha(BOW[b], a0 * soft * e0), k1 = alpha(BOW[b], a0 * soft * e1);
					quad(vc, e, p0, p1, p2, p3, 0.5F, 0.5F, 0.9F, 0.9F, k0, k1, k1, k0);
				}
			}
		});
	}

	// ------------------------------------------------------------------ sun rays
	private static void sunRays(MatrixStack m, OrderedRenderCommandQueue q, Settings s, float t, float far) {
		float up = (float) SkyFx.smoothstep(-0.05, 0.12, s.sy);
		float vis = up * (1F - s.night) * s.sunRayStrength / 100F;
		if (vis < 0.02F) {
			return;
		}
		final float d = Math.min(140F, far * 0.75F);
		final float sx = (float) s.sx * d, sy = (float) s.sy * d, sz = (float) s.sz * d;
		// two directions across the sun disc
		float ax = (float) -s.sz, az = (float) s.sx;
		float al = (float) Math.sqrt(ax * ax + az * az) + 1e-6F;
		ax /= al;
		az /= al;
		float bx = (float) (s.sy * az), by = (float) (s.sz * ax - s.sx * az), bz = (float) (-s.sy * ax);
		float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz) + 1e-6F;
		bx /= bl;
		by /= bl;
		bz /= bl;
		final float fax = ax, faz = az, fbx = bx, fby = by, fbz = bz;
		final int rays = 12;
		final int col = s.sunColour;
		q.submitCustom(m, RenderLayers.entityTranslucent(beamTex.id), (e, vc) -> {
			for (int i = 0; i < rays; i++) {
				double ang = i * Math.PI * 2 / rays + t * 0.02 + Math.sin(i * 7.3) * 0.2;
				float len = d * (0.35F + 0.25F * (float) Math.abs(Math.sin(i * 3.1 + t * 0.15)));
				float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
				float dx = fax * c + fbx * sn, dy = fby * sn, dz = faz * c + fbz * sn;
				// side direction for the width
				float wx = -fax * sn + fbx * c, wy = fby * c, wz = -faz * sn + fbz * c;
				float w0 = d * 0.012F, w1 = d * 0.06F;
				float[] p0 = {sx - wx * w0, sy - wy * w0, sz - wz * w0};
				float[] p1 = {sx + wx * w0, sy + wy * w0, sz + wz * w0};
				float ex = sx + dx * len, ey = sy + dy * len, ez = sz + dz * len;
				float[] p2 = {ex + wx * w1, ey + wy * w1, ez + wz * w1};
				float[] p3 = {ex - wx * w1, ey - wy * w1, ez - wz * w1};
				float a = vis * (0.25F + 0.2F * (float) Math.abs(Math.sin(i * 1.7 + t * 0.3)));
				int k0 = alpha(col, a), k1 = alpha(col, 0F);
				quad(vc, e, p0, p1, p2, p3, 0F, 1F, 0F, 1F, k0, k0, k1, k1);
			}
		});
	}

	// ------------------------------------------------------------------ shooting stars
	private static void shooting(MatrixStack m, OrderedRenderCommandQueue q, Settings s, float dt, float far, Vector3f right, Vector3f up) {
		float vis = Math.max(s.night, 0F);
		if (vis > 0.05F && STARS.size() < 4 && RND.nextFloat() < dt * s.shootingRate / 100F * 0.6F * vis) {
			double az = RND.nextDouble() * Math.PI * 2, el = Math.toRadians(25 + RND.nextDouble() * 40);
			double d = Math.min(120, far * 0.75);
			float x = (float) (Math.cos(el) * Math.sin(az) * d), y = (float) (Math.sin(el) * d), z = (float) (-Math.cos(el) * Math.cos(az) * d);
			double ta = RND.nextDouble() * Math.PI * 2;
			// direction: across the sky and a bit downwards
			float dx = (float) Math.cos(ta), dz = (float) Math.sin(ta), dy = -0.35F - RND.nextFloat() * 0.3F;
			float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			STARS.add(new float[] {x, y, z, dx / l, dy / l, dz / l, 0F, 0.6F + RND.nextFloat() * 0.5F});
		}
		if (STARS.isEmpty()) {
			return;
		}
		for (int i = STARS.size() - 1; i >= 0; i--) {
			float[] st = STARS.get(i);
			st[6] += dt;
			float sp = 70F;
			st[0] += st[3] * sp * dt;
			st[1] += st[4] * sp * dt;
			st[2] += st[5] * sp * dt;
			if (st[6] > st[7]) {
				STARS.remove(i);
			}
		}
		final List<float[]> list = new ArrayList<>(STARS);
		q.submitCustom(m, RenderLayers.entityTranslucent(beamTex.id), (e, vc) -> {
			for (float[] st : list) {
				float k = st[6] / st[7];
				float a = (float) Math.sin(Math.PI * Math.min(1, k)) * 0.95F;
				float len = 10F, w = 0.35F;
				float hx = st[0], hy = st[1], hz = st[2];
				float tx = hx - st[3] * len, ty = hy - st[4] * len, tz = hz - st[5] * len;
				// width direction: perpendicular to the streak and to the view
				float cx = st[4] * hz - st[5] * hy, cy = st[5] * hx - st[3] * hz, cz = st[3] * hy - st[4] * hx;
				float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz) + 1e-6F;
				cx = cx / cl * w;
				cy = cy / cl * w;
				cz = cz / cl * w;
				int head = alpha(0xFFFFFF, a), tail = alpha(0xFFFFFF, 0);
				// v = 1 (bright) at the head, 0 at the tail
				quad(vc, e, new float[] {hx - cx, hy - cy, hz - cz}, new float[] {hx + cx, hy + cy, hz + cz}, new float[] {tx + cx, ty + cy, tz + cz},
						new float[] {tx - cx, ty - cy, tz - cz}, 0F, 1F, 0F, 1F, head, head, tail, tail);
			}
		});
	}

	// ------------------------------------------------------------------ clouds
	private static void clouds(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam, Vector3f right, Vector3f up, float t, float dt, Settings s, float far) {
		final float r = Math.min(220F, far * 0.9F);
		int want = Math.max(4, Math.round(s.cloudCount * s.quality));
		if (want != cn) {
			int old = cn;
			clx = java.util.Arrays.copyOf(clx, want);
			clz = java.util.Arrays.copyOf(clz, want);
			cly = java.util.Arrays.copyOf(cly, want);
			cls = java.util.Arrays.copyOf(cls, want);
			clp = java.util.Arrays.copyOf(clp, want);
			clv = java.util.Arrays.copyOf(clv, want);
			for (int i = old; i < want; i++) {
				spawnCloud(i, cam, r);
			}
			cn = want;
		}
		if (!cloudsPlaced || Math.abs(cam.x - lastCx) > r * 2 || Math.abs(cam.z - lastCz) > r * 2) {
			for (int i = 0; i < cn; i++) {
				spawnCloud(i, cam, r);
			}
			cloudsPlaced = true;
		}
		lastCx = cam.x;
		lastCz = cam.z;
		double wr = Math.toRadians(s.windDir);
		float sp = s.cloudSpeed / 100F * 4F;
		double wx = Math.sin(wr) * sp, wz = -Math.cos(wr) * sp;
		for (int i = 0; i < cn; i++) {
			clx[i] += wx * dt;
			clz[i] += wz * dt;
			double dx = clx[i] - cam.x, dz = clz[i] - cam.z;
			if (dx > r) {
				clx[i] -= 2 * r;
			} else if (dx < -r) {
				clx[i] += 2 * r;
			}
			if (dz > r) {
				clz[i] -= 2 * r;
			} else if (dz < -r) {
				clz[i] += 2 * r;
			}
		}
		// far first
		Integer[] order = new Integer[cn];
		float[] dist = new float[cn];
		for (int i = 0; i < cn; i++) {
			order[i] = i;
			double dx = clx[i] - cam.x, dy = s.cloudHeight + cly[i] - cam.y, dz = clz[i] - cam.z;
			dist[i] = (float) (dx * dx + dy * dy + dz * dz);
		}
		java.util.Arrays.sort(order, (a, b) -> Float.compare(dist[b], dist[a]));
		int tint = SkyLook.mix(s.cloudColour, s.sunsetColour, s.sunset * 0.7);
		tint = SkyLook.mix(tint, 0xFF2A3350, s.night * 0.75);
		final int col = tint;
		final float op = s.cloudOpacity / 100F;
		final float size = s.cloudSize / 100F;
		final float height = s.cloudHeight;
		final float vw = 1F / SkyGen.CLOUD_VARIANTS;
		q.submitCustom(m, RenderLayers.entityTranslucent(cloudTex.id), (e, vc) -> {
			for (Integer oi : order) {
				int i = oi;
				float x = (float) (clx[i] - cam.x), y = (float) (height + cly[i] - cam.y), z = (float) (clz[i] - cam.z);
				float d = (float) Math.sqrt(x * x + z * z);
				float a = op * (1F - (float) SkyFx.smoothstep(r * 0.7, r, d)) * Math.min(1F, (float) Math.abs(y) / 6F + 0.3F);
				if (a < 0.02F) {
					continue;
				}
				float h = cls[i] * size;
				float hx = h * 1.0F, hy = h * 1.0F;
				float[] p0 = {x - right.x * hx - up.x * hy, y - right.y * hx - up.y * hy, z - right.z * hx - up.z * hy};
				float[] p1 = {x + right.x * hx - up.x * hy, y + right.y * hx - up.y * hy, z + right.z * hx - up.z * hy};
				float[] p2 = {x + right.x * hx + up.x * hy, y + right.y * hx + up.y * hy, z + right.z * hx + up.z * hy};
				float[] p3 = {x - right.x * hx + up.x * hy, y - right.y * hx + up.y * hy, z - right.z * hx + up.z * hy};
				int c = alpha(col, a);
				float u0 = clv[i] * vw, u1 = u0 + vw;
				quad(vc, e, p0, p1, p2, p3, u0, u1, 0F, 1F, c, c, c, c);
			}
		});
	}

	private static void spawnCloud(int i, Vec3d cam, float r) {
		clx[i] = cam.x + (RND.nextDouble() * 2 - 1) * r;
		clz[i] = cam.z + (RND.nextDouble() * 2 - 1) * r;
		cly[i] = (RND.nextFloat() - 0.5F) * 16F;
		cls[i] = 10F + RND.nextFloat() * RND.nextFloat() * 22F;
		clp[i] = RND.nextFloat();
		clv[i] = RND.nextInt(SkyGen.CLOUD_VARIANTS);
	}
}
