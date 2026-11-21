package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;

/**
 * Explosion Debris: when something blows up, the blocks that really disappear fly out as chunky, spinning pieces of
 * the same blocks (with their own texture), tumble through the air, bounce and shrink away. Everything is one small
 * pool of cubes drawn in a single batch, with hard limits and an automatic quality drop when the FPS is low, so
 * it does not lag.
 */
public class ExplosionDebrisMod extends Mod {

	public static ExplosionDebrisMod instance;

	private final Option.Num intensity = num("Amount", 100, 20, 300, 10, "%");
	private final Option.Num power = num("Blast Strength", 100, 40, 220, 10, "%");
	private final Option.Num pieceSize = num("Piece Size", 100, 50, 200, 10, "%");
	private final Option.Num lifetime = num("Lifetime", 5, 2, 12, 1, " s");
	private final Option.Bool spin = bool("Spin", true);
	private final Option.Bool bounce = bool("Bounce On Blocks", true);
	private final Option.Num maxPieces = num("Max Pieces", 600, 100, 1500, 50, "");
	private final Option.Bool autoQuality = bool("Auto Quality (keeps FPS)", true);
	private final Option.Num range = num("Draw Distance", 64, 24, 128, 8, " blocks");

	private static final int R = 8; // blocks around the explosion that are watched
	private static final float GRAVITY = 0.05F;
	private static boolean hooked;
	private final Random rnd = new Random();
	/** 3D Physics uses this same pool of cubes for broken blocks, even when Explosion Debris itself is off. */
	public boolean external;

	// ---------------------------------------------------------------- the pieces (struct of arrays: no garbage, no objects)
	private static final int CAP = 1500;
	private int n;
	private final float[] x = new float[CAP], y = new float[CAP], z = new float[CAP];
	private final float[] px = new float[CAP], py = new float[CAP], pz = new float[CAP];
	private final float[] vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
	private final float[] ax = new float[CAP], ay = new float[CAP], az = new float[CAP];
	private final float[] sx = new float[CAP], sy = new float[CAP], sz = new float[CAP];
	private final float[] size = new float[CAP];
	/** Shape of the chunk: its half-size on each axis is size * as*, so pieces are broken slabs and wedges, not tidy cubes. */
	private final float[] asx = new float[CAP], asy = new float[CAP], asz = new float[CAP];
	private final float[] u0 = new float[CAP], u1 = new float[CAP], v0 = new float[CAP], v1 = new float[CAP];
	private final int[] tint = new int[CAP], age = new int[CAP], life = new int[CAP];
	private final float[] light = new float[CAP];
	private final boolean[] still = new boolean[CAP];

	/** What was around an explosion when it happened, to see which blocks really disappeared. */
	private static final class Watch {
		Vec3d at;
		int age;
		int count;
		long[] pos;
		BlockState[] state;
		boolean[] done;
	}

	private final List<Watch> watches = new ArrayList<>();
	private final java.util.Map<BlockState, float[]> uvCache = new java.util.HashMap<>();

	public ExplosionDebrisMod() {
		super("Explosion Debris", "explosion_debris", "Blocks that blow up fly out as spinning pieces of the same blocks. Light on FPS.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				if (instance != null && (instance.enabled || instance.external) && instance.n > 0) {
					long t0 = System.nanoTime();
					try {
						instance.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
					instance.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Explosion Debris unavailable: " + t);
		}
	}

	@Override
	protected void onDisable() {
		n = 0;
		watches.clear();
	}

	// ---------------------------------------------------------------- an explosion happened
	public void onExplosion(Vec3d at) {
		var w = mc.world;
		if (w == null) {
			return;
		}
		long t0 = System.nanoTime();
		if (watches.size() >= 6) {
			watches.remove(0);
		}
		int cx = (int) Math.floor(at.x), cy = (int) Math.floor(at.y), cz = (int) Math.floor(at.z);
		int cap = (2 * R + 1) * (2 * R + 1) * (2 * R + 1);
		long[] pos = new long[cap];
		BlockState[] st = new BlockState[cap];
		int k = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dy = -R; dy <= R; dy++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int dx = -R; dx <= R; dx++) {
					if (dx * dx + dy * dy + dz * dz > R * R) {
						continue;
					}
					m.set(cx + dx, cy + dy, cz + dz);
					BlockState s = w.getBlockState(m);
					if (s.isAir() || !s.getFluidState().isEmpty()) {
						continue;
					}
					pos[k] = m.asLong();
					st[k] = s;
					k++;
				}
			}
		}
		Watch wa = new Watch();
		wa.at = at;
		wa.count = k;
		wa.pos = pos;
		wa.state = st;
		wa.done = new boolean[k];
		watches.add(wa);
		addCost(System.nanoTime() - t0);
	}

	// ---------------------------------------------------------------- every tick
	private float quality() {
		if (!autoQuality.on()) {
			return 1F;
		}
		int fps = mc.getCurrentFps();
		return fps <= 0 ? 1F : fps < 25 ? 0.25F : fps < 40 ? 0.5F : fps < 55 ? 0.8F : 1F;
	}

	@Override
	public void tick() {
		var w = mc.world;
		if (w == null || mc.player == null) {
			n = 0;
			watches.clear();
			return;
		}
		long t0 = System.nanoTime();
		// which watched blocks are gone now?
		for (int i = watches.size() - 1; i >= 0; i--) {
			Watch wa = watches.get(i);
			wa.age++;
			if (wa.age > 40) {
				watches.remove(i);
				continue;
			}
			if (wa.age % 2 != 0) {
				continue;
			}
			spawnFrom(w, wa);
		}
		step(w);
		addCost(System.nanoTime() - t0);
	}

	private void spawnFrom(net.minecraft.world.World w, Watch wa) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int[] gone = new int[wa.count];
		int g = 0;
		for (int i = 0; i < wa.count; i++) {
			if (wa.done[i]) {
				continue;
			}
			m.set(BlockPos.fromLong(wa.pos[i]));
			BlockState now = w.getBlockState(m);
			if (now != wa.state[i] && (now.isAir() || !now.getFluidState().isEmpty())) {
				wa.done[i] = true;
				gone[g++] = i;
			}
		}
		if (g == 0) {
			return;
		}
		// not too many per explosion: the nearest to the blast are kept first, the rest are thinned out
		float q = quality();
		int budget = Math.max(12, Math.round(150F * intensity.f() / 100F * q));
		float keep = g <= budget ? 1F : budget / (float) g;
		float pw = power.f() / 100F;
		float psz = pieceSize.f() / 100F;
		for (int j = 0; j < g && n < Math.min(CAP, maxPieces.i()); j++) {
			if (keep < 1F && rnd.nextFloat() > keep) {
				continue;
			}
			int i = gone[j];
			BlockPos bp = BlockPos.fromLong(wa.pos[i]);
			double bx = bp.getX() + 0.5, by = bp.getY() + 0.5, bz = bp.getZ() + 0.5;
			double dx = bx - wa.at.x, dy = by - wa.at.y, dz = bz - wa.at.z;
			double d = Math.max(0.3, Math.sqrt(dx * dx + dy * dy + dz * dz));
			float fall = (float) Math.max(0.35, 1.0 - d / (R + 3.0));
			float horiz = (0.18F + rnd.nextFloat() * 0.42F) * pw * (0.6F + fall);
			int k = n++;
			x[k] = px[k] = (float) (bx + (rnd.nextFloat() - 0.5F) * 0.5F);
			y[k] = py[k] = (float) (by + (rnd.nextFloat() - 0.5F) * 0.5F);
			z[k] = pz[k] = (float) (bz + (rnd.nextFloat() - 0.5F) * 0.5F);
			vx[k] = (float) (dx / d) * horiz + (rnd.nextFloat() - 0.5F) * 0.12F;
			vz[k] = (float) (dz / d) * horiz + (rnd.nextFloat() - 0.5F) * 0.12F;
			vy[k] = (0.30F + rnd.nextFloat() * 0.50F + (float) (dy / d) * 0.25F) * pw * (0.7F + fall * 0.5F);
			ax[k] = rnd.nextFloat() * 6.28F;
			ay[k] = rnd.nextFloat() * 6.28F;
			az[k] = rnd.nextFloat() * 6.28F;
			boolean sp = spin.on();
			sx[k] = sp ? (rnd.nextFloat() - 0.5F) * 0.7F : 0F;
			sy[k] = sp ? (rnd.nextFloat() - 0.5F) * 0.7F : 0F;
			sz[k] = sp ? (rnd.nextFloat() - 0.5F) * 0.7F : 0F;
			size[k] = (0.55F + rnd.nextFloat() * 0.55F) * psz; // big chunks of the block
			shape(k);
			// a random corner piece of the block's texture, so the pieces look like chunks of it
			float[] uv = uv(wa.state[i]);
			float du = uv[1] - uv[0], dv = uv[3] - uv[2];
			float win = rnd.nextBoolean() ? 0.5F : 0.75F;
			float ou = rnd.nextFloat() * (1F - win), ov = rnd.nextFloat() * (1F - win);
			u0[k] = uv[0] + du * ou;
			u1[k] = uv[0] + du * (ou + win);
			v0[k] = uv[2] + dv * ov;
			v1[k] = uv[2] + dv * (ov + win);
			int c = -1;
			try {
				c = mc.getBlockColors().getColor(wa.state[i], w, bp, 0);
			} catch (RuntimeException ignored) {
			}
			tint[k] = c == -1 ? 0xFFFFFF : c & 0xFFFFFF;
			age[k] = 0;
			life[k] = Math.round((lifetime.f() * 20F) * (0.7F + rnd.nextFloat() * 0.5F) * (q < 1F ? 0.6F + 0.4F * q : 1F));
			light[k] = lightAt(w, bp);
			still[k] = false;
		}
	}


	/** Number of pieces alive (3D Physics keeps its own budget on top of this). */
	public int pieces() {
		return n;
	}

	/**
	 * 3D Physics: a broken block falls apart into a few small spinning cubes of the same block. Same pool, same single
	 * batch, same hard limits as the explosion pieces, so it costs almost nothing.
	 */
	public void spawnBreak(BlockPos bp, BlockState st, int count, float speed, float sizeMul, int lifeTicks, boolean spinOn, int limit) {
		var w = mc.world;
		if (w == null || st == null) {
			return;
		}
		float[] uv = uv(st);
		float du = uv[1] - uv[0], dv = uv[3] - uv[2];
		int c = -1;
		try {
			c = mc.getBlockColors().getColor(st, w, bp, 0);
		} catch (RuntimeException ignored) {
		}
		float lt = lightAt(w, bp);
		double ex = 0, ez = 0;
		if (mc.player != null) {
			ex = bp.getX() + 0.5 - mc.player.getX();
			ez = bp.getZ() + 0.5 - mc.player.getZ();
			double d = Math.max(0.2, Math.sqrt(ex * ex + ez * ez));
			ex /= d;
			ez /= d;
		}
		for (int j = 0; j < count && n < Math.min(CAP, limit); j++) {
			int k = n++;
			x[k] = px[k] = bp.getX() + 0.15F + rnd.nextFloat() * 0.7F;
			y[k] = py[k] = bp.getY() + 0.15F + rnd.nextFloat() * 0.7F;
			z[k] = pz[k] = bp.getZ() + 0.15F + rnd.nextFloat() * 0.7F;
			vx[k] = ((float) ex * 0.05F + (rnd.nextFloat() - 0.5F) * 0.22F) * speed;
			vz[k] = ((float) ez * 0.05F + (rnd.nextFloat() - 0.5F) * 0.22F) * speed;
			vy[k] = (0.12F + rnd.nextFloat() * 0.26F) * speed;
			ax[k] = rnd.nextFloat() * 6.28F;
			ay[k] = rnd.nextFloat() * 6.28F;
			az[k] = rnd.nextFloat() * 6.28F;
			sx[k] = spinOn ? (rnd.nextFloat() - 0.5F) * 0.6F : 0F;
			sy[k] = spinOn ? (rnd.nextFloat() - 0.5F) * 0.6F : 0F;
			sz[k] = spinOn ? (rnd.nextFloat() - 0.5F) * 0.6F : 0F;
			size[k] = (0.38F + rnd.nextFloat() * 0.34F) * sizeMul; // a few big broken chunks
			shape(k);
			float win = rnd.nextBoolean() ? 0.5F : 0.75F;
			float ou = rnd.nextFloat() * (1F - win), ov = rnd.nextFloat() * (1F - win);
			u0[k] = uv[0] + du * ou;
			u1[k] = uv[0] + du * (ou + win);
			v0[k] = uv[2] + dv * ov;
			v1[k] = uv[2] + dv * (ov + win);
			tint[k] = c == -1 ? 0xFFFFFF : c & 0xFFFFFF;
			age[k] = 0;
			life[k] = Math.max(20, Math.round(lifeTicks * (0.75F + rnd.nextFloat() * 0.4F)));
			light[k] = lt;
			still[k] = false;
		}
	}

	private void shape(int k) {
		asx[k] = 0.6F + rnd.nextFloat() * 0.8F;
		asy[k] = 0.45F + rnd.nextFloat() * 0.75F;
		asz[k] = 0.6F + rnd.nextFloat() * 0.8F;
	}

	private float lightAt(net.minecraft.world.World w, BlockPos p) {
		try {
			int b = w.getLightLevel(LightType.BLOCK, p), s = w.getLightLevel(LightType.SKY, p) - w.getAmbientDarkness();
			return 0.3F + 0.7F * Math.max(0, Math.max(b, s)) / 15F;
		} catch (RuntimeException e) {
			return 1F;
		}
	}

	private float[] uv(BlockState s) {
		return uvCache.computeIfAbsent(s, k -> {
			try {
				Sprite sp = mc.getBlockRenderManager().getModel(k).particleSprite();
				return new float[] {sp.getMinU(), sp.getMaxU(), sp.getMinV(), sp.getMaxV()};
			} catch (RuntimeException e) {
				return new float[] {0, 1, 0, 1};
			}
		});
	}

	private boolean solid(net.minecraft.world.World w, BlockPos.Mutable m, float X, float Y, float Z) {
		m.set((int) Math.floor(X), (int) Math.floor(Y), (int) Math.floor(Z));
		BlockState s = w.getBlockState(m);
		return !s.isAir() && !s.getCollisionShape(w, m).isEmpty();
	}

	private void step(net.minecraft.world.World w) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		boolean bnc = bounce.on();
		for (int i = 0; i < n; i++) {
			if (++age[i] >= life[i]) {
				remove(i--);
				continue;
			}
			px[i] = x[i];
			py[i] = y[i];
			pz[i] = z[i];
			ax[i] += sx[i];
			ay[i] += sy[i];
			az[i] += sz[i];
			if (still[i]) {
				continue;
			}
			vy[i] -= GRAVITY;
			vx[i] *= 0.985F;
			vz[i] *= 0.985F;
			vy[i] *= 0.985F;
			float h = size[i] * 0.5F;
			// one axis at a time, so a piece slides along walls and lands on floors
			float ny = y[i] + vy[i];
			if (bnc && solid(w, m, x[i], vy[i] < 0 ? ny - h : ny + h, z[i])) {
				if (vy[i] < 0 && Math.abs(vy[i]) < 0.09F) {
					still[i] = true;
					vx[i] = vy[i] = vz[i] = 0;
					sx[i] = sy[i] = sz[i] = 0;
					y[i] = (float) Math.floor(ny - h) + 1F + h;
					continue;
				}
				vy[i] = -vy[i] * 0.38F;
				vx[i] *= 0.7F;
				vz[i] *= 0.7F;
				sx[i] *= 0.7F;
				sy[i] *= 0.7F;
				sz[i] *= 0.7F;
			} else {
				y[i] = ny;
			}
			float nx = x[i] + vx[i];
			if (bnc && solid(w, m, nx + Math.signum(vx[i]) * h, y[i], z[i])) {
				vx[i] = -vx[i] * 0.4F;
			} else {
				x[i] = nx;
			}
			float nz = z[i] + vz[i];
			if (bnc && solid(w, m, x[i], y[i], nz + Math.signum(vz[i]) * h)) {
				vz[i] = -vz[i] * 0.4F;
			} else {
				z[i] = nz;
			}
			if (y[i] < w.getBottomY() - 8) {
				remove(i--);
			}
		}
	}

	private void remove(int i) {
		int l = --n;
		if (i == l) {
			return;
		}
		x[i] = x[l]; y[i] = y[l]; z[i] = z[l];
		px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l];
		vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
		ax[i] = ax[l]; ay[i] = ay[l]; az[i] = az[l];
		sx[i] = sx[l]; sy[i] = sy[l]; sz[i] = sz[l];
		size[i] = size[l];
		asx[i] = asx[l]; asy[i] = asy[l]; asz[i] = asz[l];
		u0[i] = u0[l]; u1[i] = u1[l]; v0[i] = v0[l]; v1[i] = v1[l];
		tint[i] = tint[l]; age[i] = age[l]; life[i] = life[l];
		light[i] = light[l];
		still[i] = still[l];
	}

	// ---------------------------------------------------------------- drawing (one batch for all pieces)
	private static final int F = 16; // floats per piece sent to the renderer

	private void render(MatrixStack ms, OrderedRenderCommandQueue q, Vec3d cam) {
		if (mc.world == null) {
			return;
		}
		float pt = mc.getRenderTickCounter().getTickProgress(true);
		double max = range.f(), max2 = max * max;
		float[] data = new float[n * F];
		int c = 0;
		for (int i = 0; i < n; i++) {
			float ix = px[i] + (x[i] - px[i]) * pt, iy = py[i] + (y[i] - py[i]) * pt, iz = pz[i] + (z[i] - pz[i]) * pt;
			double dx = ix - cam.x, dy = iy - cam.y, dz = iz - cam.z;
			if (dx * dx + dy * dy + dz * dz > max2) {
				continue;
			}
			int left = life[i] - age[i];
			float shrink = left >= 16 ? 1F : Math.max(0F, left / 16F);
			int o = c++ * F;
			data[o] = (float) dx;
			data[o + 1] = (float) dy;
			data[o + 2] = (float) dz;
			data[o + 3] = size[i] * shrink * 0.5F;
			data[o + 4] = ax[i] + sx[i] * pt;
			data[o + 5] = ay[i] + sy[i] * pt;
			data[o + 6] = az[i] + sz[i] * pt;
			data[o + 7] = u0[i];
			data[o + 8] = u1[i];
			data[o + 9] = v0[i];
			data[o + 10] = v1[i];
			data[o + 11] = Float.intBitsToFloat(tint[i]);
			data[o + 12] = light[i];
			data[o + 13] = asx[i];
			data[o + 14] = asy[i];
			data[o + 15] = asz[i];
		}
		if (c == 0) {
			return;
		}
		final int count = c;
		q.submitCustom(ms, RenderLayers.entityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE), (entry, vc) -> {
			Matrix4f mat = entry.getPositionMatrix();
			float[] cx = new float[8], cy = new float[8], cz = new float[8];
			for (int i = 0; i < count; i++) {
				int o = i * F;
				float h = data[o + 3];
				if (h <= 0.001F) {
					continue;
				}
				float hx = h * data[o + 13], hy = h * data[o + 14], hz = h * data[o + 15];
				float sa = (float) Math.sin(data[o + 4]), ca = (float) Math.cos(data[o + 4]);
				float sb = (float) Math.sin(data[o + 5]), cb = (float) Math.cos(data[o + 5]);
				float sc = (float) Math.sin(data[o + 6]), cc = (float) Math.cos(data[o + 6]);
				// rotation = Rz * Ry * Rx
				float m00 = cb * cc, m01 = sa * sb * cc - ca * sc, m02 = ca * sb * cc + sa * sc;
				float m10 = cb * sc, m11 = sa * sb * sc + ca * cc, m12 = ca * sb * sc - sa * cc;
				float m20 = -sb, m21 = sa * cb, m22 = ca * cb;
				for (int k = 0; k < 8; k++) {
					float lx = (k & 1) == 0 ? -hx : hx, ly = (k & 2) == 0 ? -hy : hy, lz = (k & 4) == 0 ? -hz : hz;
					cx[k] = data[o] + m00 * lx + m01 * ly + m02 * lz;
					cy[k] = data[o + 1] + m10 * lx + m11 * ly + m12 * lz;
					cz[k] = data[o + 2] + m20 * lx + m21 * ly + m22 * lz;
				}
				int rgb = Float.floatToRawIntBits(data[o + 11]);
				float lt = data[o + 12];
				float uA = data[o + 7], uB = data[o + 8], vA = data[o + 9], vB = data[o + 10];
				// corner index = x | y << 1 | z << 2 (bit set = +); same vertex order as the block faces Minecraft draws
				face(vc, mat, entry, cx, cy, cz, 2, 6, 7, 3, m01, m11, m21, 1.00F * lt, rgb, uA, uB, vA, vB); // +y
				face(vc, mat, entry, cx, cy, cz, 0, 1, 5, 4, -m01, -m11, -m21, 0.50F * lt, rgb, uA, uB, vA, vB); // -y
				face(vc, mat, entry, cx, cy, cz, 1, 3, 7, 5, m00, m10, m20, 0.70F * lt, rgb, uA, uB, vA, vB); // +x
				face(vc, mat, entry, cx, cy, cz, 0, 4, 6, 2, -m00, -m10, -m20, 0.70F * lt, rgb, uA, uB, vA, vB); // -x
				face(vc, mat, entry, cx, cy, cz, 4, 5, 7, 6, m02, m12, m22, 0.85F * lt, rgb, uA, uB, vA, vB); // +z
				face(vc, mat, entry, cx, cy, cz, 0, 2, 3, 1, -m02, -m12, -m22, 0.85F * lt, rgb, uA, uB, vA, vB); // -z
			}
		});
	}

	private static void face(net.minecraft.client.render.VertexConsumer vc, Matrix4f m, MatrixStack.Entry e, float[] cx, float[] cy, float[] cz, int a, int b, int c, int d,
			float nx, float ny, float nz, float shade, int rgb, float uA, float uB, float vA, float vB) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 255) * shade)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * shade)), bl = Math.min(255, Math.round((rgb & 255) * shade));
		int col = 0xFF000000 | r << 16 | g << 8 | bl;
		vert(vc, m, e, cx[a], cy[a], cz[a], uA, vB, col, nx, ny, nz);
		vert(vc, m, e, cx[b], cy[b], cz[b], uA, vA, col, nx, ny, nz);
		vert(vc, m, e, cx[c], cy[c], cz[c], uB, vA, col, nx, ny, nz);
		vert(vc, m, e, cx[d], cy[d], cz[d], uB, vB, col, nx, ny, nz);
	}

	private static void vert(net.minecraft.client.render.VertexConsumer vc, Matrix4f m, MatrixStack.Entry e, float x, float y, float z, float u, float v, int col, float nx, float ny, float nz) {
		vc.vertex(m, x, y, z).color(col).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, nx, ny, nz);
	}
}
