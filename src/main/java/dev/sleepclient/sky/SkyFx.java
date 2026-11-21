package dev.sleepclient.sky;

import java.util.Random;

/**
 * Pure Java (no Minecraft classes): one layer of things that fall (or rise) through the WORLD around you.
 * <p>
 * Every particle has a real world position. They live in a box around the camera that moves with you: when one
 * leaves the box on one side it comes back on the other side (wrap), so there are always particles all around you,
 * in front, behind, above and below, and they stay still in the world when you turn your head (true 3D, not a
 * screen overlay). Near the edge of the box (and very close to your face) they fade out, so nothing pops.
 */
public final class SkyFx {

	private SkyFx() {
	}

	/** Settings of one layer, filled from the mod options every frame. */
	public static final class Params {
		public int style;
		public int amount;
		/** size in 1/100 blocks (25 = a quarter of a block) */
		public float size = 30F;
		/** -100..100, positive falls, negative rises */
		public float speed = 25F;
		public float sway = 40F, spin = 40F, opacity = 90F;
		/** wind strength -100..100 and direction in degrees (0 = towards north / -Z) */
		public float wind = 0F, windDir = 0F;
		/** 0..100: how different the particles are in size */
		public float sizeVariation = 60F;
		/** half size of the box around the camera, in blocks */
		public float radius = 24F;
		/** land on the ground and stay there 'stay' seconds before fading away */
		public boolean landing;
		public float stay = 4F;
		/** extra wind from gusts right now (0 = calm, 1 = double wind), set by the mod every frame */
		public float gust;
		/** where they live: 0 everywhere, 1 near the ground (around your feet), 2 high up */
		public int band;
		/** push particles away from the player */
		public boolean react;
		public double px, py, pz, pvx, pvz;
	}

	public static final class Layer {
		public int n;
		/** world position */
		public double[] x = new double[0], y = new double[0], z = new double[0];
		/** per particle: size multiplier, speed multiplier, phase, rotation angle, spin direction, tumble axis */
		public float[] scale = new float[0], speedMul = new float[0], phase = new float[0], rot = new float[0], spinDir = new float[0];
		public float[] ax = new float[0], ay = new float[0], az = new float[0];
		/** 0..1 fade used by "only outdoors" (eases towards the target) */
		public float[] vis = new float[0];
		public boolean[] covered = new boolean[0];
		/** pushes (blocks/s) that fade out; land: seconds on the ground (-1 = flying); ground: top of the ground below (NaN = unknown) */
		public float[] vx = new float[0], vy = new float[0], vz = new float[0], land = new float[0], ground = new float[0];
		/** 0..1 random per particle (used for random colours) */
		public float[] hue = new float[0];
		private final Random rnd = new Random();
		private boolean placed;
		private double lastCx, lastCy, lastCz;

		private static float[] grow(float[] a, int n) {
			float[] b = new float[n];
			System.arraycopy(a, 0, b, 0, Math.min(a.length, n));
			return b;
		}

		private static double[] grow(double[] a, int n) {
			double[] b = new double[n];
			System.arraycopy(a, 0, b, 0, Math.min(a.length, n));
			return b;
		}

		private void resize(int count, Params p, double cx, double cy, double cz) {
			if (count == n) {
				return;
			}
			int keep = Math.min(n, count);
			x = grow(x, count);
			y = grow(y, count);
			z = grow(z, count);
			scale = grow(scale, count);
			speedMul = grow(speedMul, count);
			phase = grow(phase, count);
			rot = grow(rot, count);
			spinDir = grow(spinDir, count);
			ax = grow(ax, count);
			ay = grow(ay, count);
			az = grow(az, count);
			vis = grow(vis, count);
			vx = grow(vx, count);
			vy = grow(vy, count);
			vz = grow(vz, count);
			land = grow(land, count);
			ground = grow(ground, count);
			hue = grow(hue, count);
			boolean[] nc = new boolean[count];
			System.arraycopy(covered, 0, nc, 0, Math.min(covered.length, count));
			covered = nc;
			for (int i = keep; i < count; i++) {
				spawn(i, p, cx, cy, cz);
			}
			n = count;
		}

		private void spawn(int i, Params p, double cx, double cy, double cz) {
			float r = p.radius, rv = vertical(p);
			x[i] = cx + (rnd.nextDouble() * 2 - 1) * r;
			y[i] = cy + (rnd.nextDouble() * 2 - 1) * rv;
			z[i] = cz + (rnd.nextDouble() * 2 - 1) * r;
			float v = p.sizeVariation / 100F;
			// most particles small, a few big ones (looks much more natural than uniform sizes)
			float g = rnd.nextFloat();
			scale[i] = 1F + v * (g * g * 1.6F - 0.55F);
			speedMul[i] = 0.75F + rnd.nextFloat() * 0.5F;
			phase[i] = rnd.nextFloat() * 6.2831855F;
			rot[i] = rnd.nextFloat() * 6.2831855F;
			spinDir[i] = rnd.nextBoolean() ? 1F : -1F;
			// random tumble axis (normalised)
			float a1 = rnd.nextFloat() * 6.2831855F, a2 = (float) Math.acos(rnd.nextFloat() * 2 - 1);
			ax[i] = (float) (Math.sin(a2) * Math.cos(a1));
			ay[i] = (float) Math.cos(a2);
			az[i] = (float) (Math.sin(a2) * Math.sin(a1));
			vis[i] = 1F;
			vx[i] = vy[i] = vz[i] = 0F;
			land[i] = -1F;
			ground[i] = Float.NaN;
			hue[i] = rnd.nextFloat();
		}

		/** Half height of the box: a bit flatter than wide, most of what you see is around the horizon. */
		public static float vertical(Params p) {
			return switch (p.band) {
				case 1 -> 2.6F;
				case 2 -> Math.max(4F, p.radius * 0.35F);
				default -> Math.max(6F, p.radius * 0.7F);
			};
		}

		/** Centre of the box above / below the camera. */
		public static double centreOffset(Params p) {
			return switch (p.band) {
				case 1 -> -0.4; // camera is ~1.6 above your feet: from the ground to a bit over your head
				case 2 -> 8 + Math.max(4F, p.radius * 0.35F);
				default -> 0;
			};
		}

		private static double wrap(double v, double c, double half) {
			// Inside the box: return v EXACTLY as it was. The old version recomputed it with floating point maths, which
			// changed the last bits of almost any value, and the caller read that as "left the box" and teleported the
			// particle to a random place (constant jumping / flickering).
			double lo = c - half;
			double span = half * 2;
			if (v >= lo && v < lo + span) {
				return v;
			}
			double d = v - lo;
			d -= Math.floor(d / span) * span;
			double r = lo + d;
			return r >= lo + span ? lo : r;
		}

		/**
		 * Moves every particle. dt in seconds, (cx, cy, cz) = camera position.
		 * Wind, sway and falling are in blocks per second.
		 */
		public void step(Params p, float dt, float t, double cx, double cy, double cz) {
			cy += centreOffset(p); // the box can sit low (near the ground) or high up
			resize(Math.max(0, p.amount), p, cx, cy, cz);
			if (!placed || Math.abs(cx - lastCx) > p.radius * 4 || Math.abs(cz - lastCz) > p.radius * 4 || Math.abs(cy - lastCy) > p.radius * 4) {
				// first frame or teleport: scatter everything around the new position
				for (int i = 0; i < n; i++) {
					spawn(i, p, cx, cy, cz);
				}
				placed = true;
			}
			lastCx = cx;
			lastCy = cy;
			lastCz = cz;
			float fall = p.speed / 100F * 3.2F;
			double wr = Math.toRadians(p.windDir);
			float wind = p.wind / 100F * 4F;
			float wx = (float) (Math.sin(wr) * wind), wz = (float) (-Math.cos(wr) * wind);
			float sway = p.sway / 100F * 0.9F;
			float spin = p.spin / 100F * 2.6F;
			float r = p.radius, rv = vertical(p);
			float gw = 1F + p.gust;
			wx *= gw;
			wz *= gw;
			float decay = (float) Math.exp(-dt * 2.2);
			double pspeed = Math.sqrt(p.pvx * p.pvx + p.pvz * p.pvz) / Math.max(dt, 1e-3);
			for (int i = 0; i < n; i++) {
				// on the ground: stay, then fade and come back from the top
				if (land[i] >= 0F) {
					land[i] += dt;
					if (land[i] > p.stay) {
						land[i] = -1F;
						x[i] = cx + (rnd.nextDouble() * 2 - 1) * r;
						z[i] = cz + (rnd.nextDouble() * 2 - 1) * r;
						y[i] = cy + rv * (0.6 + 0.4 * rnd.nextDouble());
						ground[i] = Float.NaN;
						vis[i] = 0F;
					}
					continue;
				}
				float k = speedMul[i];
				double ph = phase[i];
				// gentle swirl: each particle drifts in its own little loop
				double sx = Math.sin(t * (0.6 + 0.5 * k) + ph) * sway;
				double sz = Math.cos(t * (0.5 + 0.4 * k) + ph * 1.7) * sway;
				double sy = Math.sin(t * 1.3 + ph * 2.1) * sway * 0.25;
				if (p.react) {
					double dx = x[i] - p.px, dy = y[i] - (p.py + 1.0), dz = z[i] - p.pz;
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 < 6.25 && d2 > 1e-4) {
						double d = Math.sqrt(d2);
						double push = (0.8 + Math.min(12.0, pspeed) * 0.9) * (1 - d / 2.5) * dt * 6;
						vx[i] += (float) (dx / d * push);
						vy[i] += (float) (Math.abs(dy / d) * push * 0.5 + push * 0.2);
						vz[i] += (float) (dz / d * push);
					}
				}
				vx[i] *= decay;
				vy[i] *= decay;
				vz[i] *= decay;
				double oldY = y[i];
				x[i] += (wx * k + sx + vx[i]) * dt;
				z[i] += (wz * k + sz + vz[i]) * dt;
				y[i] += (-fall * k + sy + vy[i]) * dt;
				rot[i] += spin * spinDir[i] * (0.5F + 0.5F * k) * dt;
				if (p.landing && fall > 0 && !Float.isNaN(ground[i]) && y[i] <= ground[i] + 0.03 && oldY > ground[i] - 0.6) {
					y[i] = ground[i] + 0.03;
					land[i] = 0F;
					continue;
				}
				double ox = x[i], oy = y[i], oz = z[i];
				x[i] = wrap(x[i], cx, r);
				y[i] = wrap(y[i], cy, rv);
				z[i] = wrap(z[i], cz, r);
				if (oy != y[i]) {
					// came back in at the top (or bottom): new random column so the pattern never repeats
					x[i] = cx + (rnd.nextDouble() * 2 - 1) * r;
					z[i] = cz + (rnd.nextDouble() * 2 - 1) * r;
					ground[i] = Float.NaN;
					vis[i] = covered[i] ? 0F : vis[i];
				} else if (ox != x[i] || oz != z[i]) {
					y[i] = cy + (rnd.nextDouble() * 2 - 1) * rv;
					ground[i] = Float.NaN;
				}
				float target = covered[i] ? 0F : 1F;
				vis[i] += (target - vis[i]) * Math.min(1F, dt * 4F);
			}
		}

		/** Throws the particles near (x, y, z) outwards (a hit, an explosion...). */
		public void burst(double bx, double by, double bz, double radius, double strength) {
			for (int i = 0; i < n; i++) {
				double dx = x[i] - bx, dy = y[i] - by, dz = z[i] - bz;
				double d2 = dx * dx + dy * dy + dz * dz;
				if (d2 < radius * radius && d2 > 1e-4) {
					double d = Math.sqrt(d2);
					double f = strength * (1 - d / radius);
					if (land[i] >= 0F) {
						land[i] = -1F; // kicked up from the ground
					}
					vx[i] += (float) (dx / d * f);
					vy[i] += (float) (dy / d * f + f * 0.4);
					vz[i] += (float) (dz / d * f);
				}
			}
		}

		/** 0..1 how visible a particle at this offset from the camera is (fades at the box edge and near your face). */
		public static float fade(double dx, double dy, double dz, Params p) {
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double r = p.radius;
			float far = (float) (1 - smoothstep(r * 0.62, r * 0.98, d));
			float near = (float) smoothstep(1.2, 4.0, d); // up close they fade out instead of becoming huge see-through quads
			return far * near;
		}

		public float twinkle(int i, float t) {
			return 0.5F + 0.5F * (0.5F + 0.5F * (float) Math.sin(t * 2.4 + phase[i] * 3));
		}
	}

	public static double smoothstep(double a, double b, double v) {
		double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}
}
