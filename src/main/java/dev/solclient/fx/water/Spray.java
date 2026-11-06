package dev.solclient.fx.water;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Sleep's own particles for liquids, kept in flat arrays and drawn in one go (thousands cost almost nothing):
 * <ul>
 * <li>spray: white droplets thrown up by waterfalls and splashes, with gravity; they turn into foam where they land;</li>
 * <li>foam: flat white patches that float on the surface, drift with the current and fade;</li>
 * <li>embers: glowing orange sparks and molten blobs over lava.</li>
 * </ul>
 */
final class Spray {

	static final int SPRAY = 0, FOAM = 1, EMBER = 2, BLOB = 3, BUBBLE = 4, SHAFT = 5;
	private static final int MAX = 6000;

	private final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX];
	private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
	private final float[] age = new float[MAX], life = new float[MAX], size = new float[MAX];
	private final byte[] type = new byte[MAX];
	private final int[] light = new int[MAX];
	private int n;
	private final BlockPos.Mutable bp = new BlockPos.Mutable();

	int count() {
		return n;
	}

	int count(int t) {
		int c = 0;
		for (int i = 0; i < n; i++) {
			if (type[i] == t) {
				c++;
			}
		}
		return c;
	}

	void clear() {
		n = 0;
	}

	void add(int t, double px, double py, double pz, double svx, double svy, double svz, float s, float l, int lightmap) {
		if (n >= MAX) {
			return;
		}
		x[n] = (float) px;
		y[n] = (float) py;
		z[n] = (float) pz;
		vx[n] = (float) svx;
		vy[n] = (float) svy;
		vz[n] = (float) svz;
		age[n] = 0;
		life[n] = l;
		size[n] = s;
		type[n] = (byte) t;
		light[n] = lightmap;
		n++;
	}

	/** A burst of droplets from one point, in a cone upwards. power ~ 0.2 (drip) .. 1.5 (cannonball). */
	void splash(double px, double py, double pz, double dirX, double dirZ, float power, int count, int lightmap) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2, sp = (0.6 + r.nextDouble() * 1.6) * power;
			double up = (1.8 + r.nextDouble() * 3.2) * power;
			add(SPRAY, px + (r.nextDouble() - 0.5) * 0.4, py, pz + (r.nextDouble() - 0.5) * 0.4, Math.cos(a) * sp + dirX * 1.5, up, Math.sin(a) * sp + dirZ * 1.5,
					0.035F + r.nextFloat() * 0.07F * (0.6F + power), 0.6F + r.nextFloat() * 0.9F, lightmap);
		}
		int foams = Math.max(1, count / 6);
		for (int i = 0; i < foams; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * (0.4 + power);
			foam(px + Math.cos(a) * d, py + 0.01, pz + Math.sin(a) * d, 0, 0, 0.25F + r.nextFloat() * 0.35F * (0.5F + power), lightmap);
		}
	}

	void foam(double px, double py, double pz, double fx, double fz, float s, int lightmap) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		add(FOAM, px, py, pz, fx, 0, fz, s, 2.5F + r.nextFloat() * 3.5F, lightmap);
	}

	/** Moves everything by dt seconds. Droplets that fall back into water become foam; foam follows the current. */
	void update(ClientWorld w, float dt) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int i = 0;
		while (i < n) {
			age[i] += dt;
			if (age[i] >= life[i]) {
				remove(i);
				continue;
			}
			switch (type[i]) {
				case SPRAY -> {
					vy[i] -= 9.8F * dt;
					vx[i] *= 1F - 0.6F * dt;
					vz[i] *= 1F - 0.6F * dt;
					float ny = y[i] + vy[i] * dt;
					if (vy[i] < 0) {
						FluidState fs = w.getFluidState(bp.set(x[i], ny, z[i]));
						boolean wet = fs.isIn(FluidTags.WATER) && ny < bp.getY() + fs.getHeight(w, bp);
						if (wet || !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
							if (wet && r.nextInt(5) == 0) {
								foam(x[i], bp.getY() + fs.getHeight(w, bp) + 0.012, z[i], vx[i] * 0.1, vz[i] * 0.1, 0.12F + size[i] * 2F, light[i]);
							}
							remove(i);
							continue;
						}
					}
					x[i] += vx[i] * dt;
					y[i] = ny;
					z[i] += vz[i] * dt;
				}
				case FOAM -> {
					// drift with the current, a little wobble
					FluidState fs = w.getFluidState(bp.set(x[i], y[i] - 0.05, z[i]));
					if (fs.isIn(FluidTags.WATER)) {
						Vec3d v = fs.getVelocity(w, bp);
						vx[i] += ((float) v.x * 1.2F - vx[i]) * Math.min(1F, dt * 1.5F);
						vz[i] += ((float) v.z * 1.2F - vz[i]) * Math.min(1F, dt * 1.5F);
						y[i] += (bp.getY() + fs.getHeight(w, bp) + 0.012F - y[i]) * Math.min(1F, dt * 6F);
					} else if (fs.isEmpty() && w.getFluidState(bp.set(x[i], y[i] - 1.05, z[i])).isEmpty()) {
						remove(i); // the water is gone
						continue;
					}
					x[i] += vx[i] * dt;
					z[i] += vz[i] * dt;
				}
				case EMBER -> {
					vy[i] += 0.6F * dt; // hot air carries it up
					vx[i] += (r.nextFloat() - 0.5F) * dt;
					vz[i] += (r.nextFloat() - 0.5F) * dt;
					x[i] += vx[i] * dt;
					y[i] += vy[i] * dt;
					z[i] += vz[i] * dt;
				}
				case BUBBLE -> {
					vy[i] += (1.4F - vy[i]) * Math.min(1F, dt * 3F);
					vx[i] += (r.nextFloat() - 0.5F) * dt * 2F;
					vz[i] += (r.nextFloat() - 0.5F) * dt * 2F;
					x[i] += vx[i] * dt;
					y[i] += vy[i] * dt;
					z[i] += vz[i] * dt;
					if (!w.getFluidState(bp.set(x[i], y[i], z[i])).isIn(FluidTags.WATER)) {
						remove(i); // popped at the surface
						continue;
					}
				}
				case SHAFT -> {
					// light shafts sway slowly (vy = their length, kept as is)
					x[i] += vx[i] * dt;
					z[i] += vz[i] * dt;
				}
				default -> { // BLOB: a glob of lava that jumps and falls back
					vy[i] -= 9.8F * dt;
					x[i] += vx[i] * dt;
					y[i] += vy[i] * dt;
					z[i] += vz[i] * dt;
					if (vy[i] < 0 && w.getFluidState(bp.set(x[i], y[i], z[i])).isIn(FluidTags.LAVA)) {
						remove(i);
						continue;
					}
				}
			}
			i++;
		}
	}

	private void remove(int i) {
		int last = --n;
		x[i] = x[last];
		y[i] = y[last];
		z[i] = z[last];
		vx[i] = vx[last];
		vy[i] = vy[last];
		vz[i] = vz[last];
		age[i] = age[last];
		life[i] = life[last];
		size[i] = size[last];
		type[i] = type[last];
		light[i] = light[last];
	}

	/** Writes every particle as a quad (camera-relative). right / up: the camera's axes, for droplets that face you. */
	void emit(VertexConsumer vc, Vec3d cam, Vector3f right, Vector3f up, float frameDt) {
		for (int i = 0; i < n; i++) {
			float k = age[i] / life[i];
			float px = (float) (x[i] - cam.x), py = (float) (y[i] - cam.y), pz = (float) (z[i] - cam.z);
			if (px * px + py * py + pz * pz > 96 * 96) {
				continue;
			}
			float s = size[i];
			int col;
			switch (type[i]) {
				case SPRAY -> {
					float a = Math.min(1F, (1F - k) * 2.2F) * 0.85F;
					col = argb(a, 0.93F, 0.96F, 1F);
					billboard(vc, px, py, pz, s, right, up, col, light[i], false, false);
				}
				case FOAM -> {
					float a = Math.min(Math.min(1F, age[i] * 4F), (1F - k) * 1.6F) * 0.8F;
					col = argb(a, 0.95F, 0.97F, 0.98F);
					float g = s * (0.85F + 0.3F * k); // spreads out as it fades
					flat(vc, px, py, pz, g, col, light[i]);
				}
				case BUBBLE -> {
					col = argb(0.55F * Math.min(1F, (1F - k) * 3F), 0.80F, 0.92F, 1F);
					billboard(vc, px, py, pz, s, right, up, col, light[i], false, false);
				}
				case SHAFT -> {
					float a = Math.min(Math.min(1F, age[i] * 0.8F), (1F - k) * 1.5F) * 0.10F;
					col = argb(a, 0.75F, 0.92F, 1F);
					shaft(vc, px, py, pz, s, vy[i], right, col);
				}
				case EMBER -> {
					float a = (1F - k);
					col = argb(a, 1F, 0.45F + 0.3F * (1F - k), 0.08F);
					billboard(vc, px, py, pz, s * (1F - 0.5F * k), right, up, col, 0xF000F0, false, true);
				}
				default -> {
					col = argb(1F, 1F, 0.5F, 0.06F);
					billboard(vc, px, py, pz, s, right, up, col, 0xF000F0, false, true);
				}
			}
		}
	}

	private static void billboard(VertexConsumer vc, float x, float y, float z, float s, Vector3f r, Vector3f u, int col, int light, boolean flat, boolean glow) {
		float rx = r.x * s, ry = r.y * s, rz = r.z * s, ux = u.x * s, uy = u.y * s, uz = u.z * s;
		float gv = glow ? 4F : 0F;
		vc.vertex(x - rx - ux, y - ry - uy, z - rz - uz).color(col).texture(-1F, -1F + gv).light(light);
		vc.vertex(x - rx + ux, y - ry + uy, z - rz + uz).color(col).texture(-1F, 1F + gv).light(light);
		vc.vertex(x + rx + ux, y + ry + uy, z + rz + uz).color(col).texture(1F, 1F + gv).light(light);
		vc.vertex(x + rx - ux, y + ry - uy, z + rz - uz).color(col).texture(1F, -1F + gv).light(light);
	}

	/** A vertical beam hanging down from (x, y, z), turned to the camera around the vertical axis. */
	private static void shaft(VertexConsumer vc, float x, float y, float z, float w, float len, Vector3f r, int col) {
		float hl = (float) Math.sqrt(r.x * r.x + r.z * r.z) + 1e-5F;
		float rx = r.x / hl * w, rz = r.z / hl * w;
		vc.vertex(x - rx, y - len, z - rz).color(col).texture(-5F, -1F + 4F).light(0xF000F0);
		vc.vertex(x - rx, y, z - rz).color(col).texture(-5F, 1F + 4F).light(0xF000F0);
		vc.vertex(x + rx, y, z + rz).color(col).texture(-3F, 1F + 4F).light(0xF000F0);
		vc.vertex(x + rx, y - len, z + rz).color(col).texture(-3F, -1F + 4F).light(0xF000F0);
	}

	private static void flat(VertexConsumer vc, float x, float y, float z, float s, int col, int light) {
		vc.vertex(x - s, y, z - s).color(col).texture(3F, -1F).light(light);
		vc.vertex(x - s, y, z + s).color(col).texture(3F, 1F).light(light);
		vc.vertex(x + s, y, z + s).color(col).texture(5F, 1F).light(light);
		vc.vertex(x + s, y, z - s).color(col).texture(5F, -1F).light(light);
	}

	private static int argb(float a, float r, float g, float b) {
		return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}
}
