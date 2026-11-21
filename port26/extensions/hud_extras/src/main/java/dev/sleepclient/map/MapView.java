package dev.sleepclient.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Paints the map into one texture (every texel is a real screen pixel, so it stays sharp at any size). Round /
 * square / rounded shape, rotation, border and soft edges are all baked into the texture.
 */
public final class MapView {

	/** What to draw. */
	public static final class P {
		public int w, h;
		public boolean cave;
		public double cx, cz;
		/** Texels per block. */
		public float ppb = 2F;
		/** Map rotation in radians (PI = north is up). */
		public float yaw = (float) Math.PI;
		/** 0 circle, 1 square, 2 rounded. */
		public int shape;
		public float border;
		public int borderColour = 0x1B1E27;
		public float bright = 1F;
		public boolean vignette;
		public boolean edge;
		public boolean smooth;
		public int fog = 0xDDE3EC;
		/** Do not redraw more often than this many milliseconds (0 = every time something changed). */
		public int minMs;
		/** Draw the land as a 3D view (tilted: every block has height and you can see its side). Not used for cave slices. */
		public boolean threeD;
		/** How tall the land looks in 3D (1 = normal). */
		public float heightScale = 1F;
		/** Your height, used as the middle of the 3D view when the map does not know the height under the centre yet. */
		public double cy = 64;

		long sig() {
			long h = 1469598103934665603L;
			h = mix(h, w * 31L + this.h);
			h = mix(h, cave ? 1 : 0);
			h = mix(h, Double.doubleToLongBits(cx));
			h = mix(h, Double.doubleToLongBits(cz));
			h = mix(h, Float.floatToIntBits(ppb));
			h = mix(h, Float.floatToIntBits(yaw));
			h = mix(h, shape);
			h = mix(h, Float.floatToIntBits(border));
			h = mix(h, borderColour);
			h = mix(h, Float.floatToIntBits(bright));
			h = mix(h, (vignette ? 1 : 0) | (edge ? 2 : 0) | (smooth ? 4 : 0));
			h = mix(h, fog);
			h = mix(h, (threeD ? 1 : 0));
			h = mix(h, Float.floatToIntBits(heightScale));
			return h;
		}

		private static long mix(long h, long v) {
			return (h ^ v) * 1099511628211L;
		}
	}

	/** 3D view: how much a block of depth (towards you) and a block of height move the picture, in blocks of width. */
	public static final float KY = 0.84F, KH = 0.40F;

	private final Minecraft mc = Minecraft.getInstance();
	private final String name;
	private final Identifier id;
	private DynamicTexture tex;
	private int tw, th;
	private int[] buf;
	private boolean[] known;
	private long lastSig;
	private int lastVersion = -1;
	private long lastMs;
	private MapStore st;
	private boolean cv;
	private long ck = Long.MIN_VALUE;
	private int[] ca;
	/** Height (y of the top block) of the land in the middle of the last 3D picture. */
	private float h0;
	private int[] colA = new int[0], hgA = new int[0], ucA = new int[0], uhA = new int[0];

	public MapView(String name) {
		this.name = "kubo_" + name;
		this.id = Identifier.fromNamespaceAndPath("sleep_client", "map/" + name);
	}

	public Identifier id() {
		return id;
	}

	/** Height of the land under the middle of the last 3D picture (markers use it to sit on the ground). */
	public float h0() {
		return h0;
	}

	public void close() {
		if (tex != null) {
			mc.getTextureManager().release(id);
			tex = null;
			tw = 0;
			th = 0;
		}
	}

	private void ensure(int w, int h) {
		if (tex != null && w == tw && h == th) {
			return;
		}
		close();
		NativeImage img = new NativeImage(w, h, true);
		final String n = name;
		tex = new DynamicTexture(() -> n, img);
		tex.upload();
		mc.getTextureManager().register(id, tex);
		tw = w;
		th = h;
		buf = new int[w * h];
		known = new boolean[w * h];
		lastSig = 0;
		lastVersion = -1;
	}

	private int at(int bx, int bz) {
		long k = MapStore.k(bx >> 4, bz >> 4);
		if (k != ck) {
			ck = k;
			ca = st.chunk(k, cv);
		}
		return ca == null ? 0 : ca[(bz & 15) << 4 | (bx & 15)];
	}

	/** Redraws the texture when something changed. Returns true when it did. */
	public boolean render(MapStore store, P p) {
		boolean fresh = tex == null || p.w != tw || p.h != th;
		long now = System.currentTimeMillis();
		if (!fresh && p.minMs > 0 && now - lastMs < p.minMs) {
			return false;
		}
		ensure(p.w, p.h);
		long sig = p.sig();
		if (sig == lastSig && store.version == lastVersion) {
			return false;
		}
		lastSig = sig;
		lastVersion = store.version;
		lastMs = now;
		st = store;
		cv = p.cave;
		ck = Long.MIN_VALUE;
		ca = null;

		final int w = p.w, h = p.h;
		final float hw = w / 2F, hh = h / 2F;
		final float rx = (float) -Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw), dx = (float) Math.sin(p.yaw), dz = (float) -Math.cos(p.yaw);
		final float inv = 1F / p.ppb;
		final float m = Math.min(w, h);
		final float rr = p.shape == 2 ? m * 0.22F : 0F;
		final int fog = p.fog & 0xFFFFFF;
		final float br = p.bright;

		final boolean three = p.threeD && !p.cave;
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int idx = py * w + px;
				float ox = px + 0.5F - hw, oy = py + 0.5F - hh;
				if (sdf(ox, oy, hw, hh, rr, p.shape) > 1F) {
					buf[idx] = 0;
					known[idx] = true;
					continue;
				}
				if (three) {
					// 3D: start with the "unknown" fog; the land is drawn over it below
					double wx = p.cx + (ox * rx + oy * dx) * inv, wz = p.cz + (ox * rz + oy * dz) * inv;
					int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
					int nz = ((bx * 73856093) ^ (bz * 19349663)) >>> 9 & 7;
					buf[idx] = MapStore.scale(fog, 0.985F + nz * 0.003F) & 0xFFFFFF;
					known[idx] = false;
					continue;
				}
				int rgb;
				boolean kn;
				if (p.smooth) {
					int r = 0, g = 0, b = 0, n = 0;
					for (int s = 0; s < 4; s++) {
						float sx = ox + ((s & 1) == 0 ? -0.25F : 0.25F), sy = oy + ((s & 2) == 0 ? -0.25F : 0.25F);
						int c = sample(p.cx, p.cz, sx, sy, rx, rz, dx, dz, inv, fog);
						if ((c >>> 24) != 0) {
							n++;
						}
						r += (c >> 16) & 255;
						g += (c >> 8) & 255;
						b += c & 255;
					}
					rgb = (r >> 2) << 16 | (g >> 2) << 8 | (b >> 2);
					kn = n > 1;
				} else {
					int c = sample(p.cx, p.cz, ox, oy, rx, rz, dx, dz, inv, fog);
					kn = (c >>> 24) != 0;
					rgb = c & 0xFFFFFF;
				}
				if (br != 1F) {
					rgb = MapStore.scale(rgb, br);
				}
				buf[idx] = rgb;
				known[idx] = kn;
			}
		}

		if (three) {
			terrain3d(p, rx, rz, dx, dz, -1, -1, null);
			if (br != 1F) {
				for (int i = 0; i < buf.length; i++) {
					buf[i] = MapStore.scale(buf[i], br);
				}
			}
		}

		if (p.edge) {
			int e = Math.max(2, Math.round(p.ppb * 0.9F));
			for (int py = 0; py < h; py++) {
				for (int px = 0; px < w; px++) {
					int idx = py * w + px;
					if (!known[idx]) {
						continue;
					}
					boolean near = px - e >= 0 && !known[idx - e] || px + e < w && !known[idx + e] || py - e >= 0 && !known[idx - e * w] || py + e < h && !known[idx + e * w];
					if (near) {
						buf[idx] = MapStore.scale(buf[idx], 0.8F);
					}
				}
			}
		}

		final float vig = 0.16F * m;
		NativeImage img = tex.getPixels();
		if (img == null) {
			return false;
		}
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int idx = py * w + px;
				float ox = px + 0.5F - hw, oy = py + 0.5F - hh;
				float sd = sdf(ox, oy, hw, hh, rr, p.shape);
				float outer = Math.max(0F, Math.min(1F, 0.5F - sd));
				if (outer <= 0F) {
					img.setPixel(px, py, 0);
					continue;
				}
				int rgb = buf[idx];
				if (p.vignette) {
					float e = Math.max(0F, Math.min(1F, (sd + vig) / vig));
					rgb = MapStore.scale(rgb, 1F - 0.30F * e * e);
				}
				if (p.border > 0F) {
					float inner = Math.max(0F, Math.min(1F, 0.5F - (sd + p.border)));
					rgb = MapStore.lerp(p.borderColour & 0xFFFFFF, rgb, inner);
				}
				img.setPixel(px, py, Math.round(outer * 255F) << 24 | (rgb & 0xFFFFFF));
			}
		}
		tex.upload();
		return true;
	}

	// ------------------------------------------------------------------ 3D
	/** Block height of the 3D view at a world point, or MIN_VALUE when the map has no heights there yet. */
	private int heightAt(int bx, int bz) {
		long k = MapStore.k(bx >> 4, bz >> 4);
		if (k != ck) {
			ck = k;
			ca = st.chunk(k, cv);
		}
		if (ca == null || ca.length < 512) {
			return Integer.MIN_VALUE;
		}
		return ca[256 + ((bz & 15) << 4 | (bx & 15))];
	}

	/**
	 * Draws the land as a tilted 3D view. For every column of the picture the land is walked from far to near, and each
	 * block is drawn as its top (lifted by its height) plus its side wall down to the block in front of it, so near
	 * things cover far things. This is the same idea as the old "voxel space" games and costs little.
	 * With pickY >= 0 nothing is drawn: it only finds which block is under the pixel (pickX, pickY) and writes its
	 * world position into pick[0], pick[1].
	 */
	private void terrain3d(P p, float rx, float rz, float dx, float dz, int pickX, int pickY, double[] pick) {
		final int w = p.w, h = p.h;
		final float hw = w / 2F, hh = h / 2F;
		final float ppb = p.ppb;
		final float ky = KY * ppb, kh = KH * p.heightScale * ppb;
		int ch = heightAt((int) Math.floor(p.cx), (int) Math.floor(p.cz));
		if (pick == null || h0 == 0F) {
			h0 = ch == Integer.MIN_VALUE ? (float) Math.floor(p.cy) : ch;
		}
		final float maxUp = 64F, maxDown = 48F;
		final float step = Math.max(0.75F, 1F / ky);
		final float rowPx = step * ky;
		final float vMin = -(hh + maxDown * kh) / ky - 2F;
		final float vMax = (hh + maxUp * kh) / ky + 2F;
		final int rows = Math.max(1, (int) Math.ceil((vMax - vMin) / step) + 1);
		if (colA.length < rows) {
			colA = new int[rows];
			hgA = new int[rows];
			ucA = new int[rows];
			uhA = new int[rows];
		}
		final float inv = 1F / ppb;
		final int x0 = pickX >= 0 ? pickX : 0, x1 = pickX >= 0 ? pickX + 1 : w;
		for (int px = x0; px < x1; px++) {
			float u = (px + 0.5F - hw) * inv;
			for (int i = 0; i < rows; i++) {
				float v = vMin + i * step;
				double wx = p.cx + u * rx + v * dx, wz = p.cz + u * rz + v * dz;
				int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
				long k = MapStore.k(bx >> 4, bz >> 4);
				if (k != ck) {
					ck = k;
					ca = st.chunk(k, cv);
				}
				if (ca == null) {
					colA[i] = 0;
					continue;
				}
				int li = (bz & 15) << 4 | (bx & 15);
				colA[i] = ca[li];
				hgA[i] = ca.length >= 512 ? ca[256 + li] : (int) h0;
				ucA[i] = ca.length >= 1024 ? ca[512 + li] : 0; // under a tree top: leaves thickness << 24 | colour
				uhA[i] = ca.length >= 1024 ? ca[768 + li] : 0;
			}
			for (int i = 0; i < rows; i++) {
				int c = colA[i];
				if (c == 0) {
					continue;
				}
				float v = vMin + i * step;
				float base = hh + (v - step * 0.5F) * ky; // the middle of the picture is the middle of the block
				int hg = hgA[i];
				boolean tree = ucA[i] != 0 && uhA[i] != 0;
				// ---- the ground (for a tree top: the trunk / the ground under the leaves)
				int gh = tree ? uhA[i] : hg;
				float dh = Math.max(-maxDown, Math.min(maxUp, gh - h0));
				float yTop = base - dh * kh;
				int y0 = Math.round(yTop), y1 = Math.max(y0 + 1, Math.round(yTop + rowPx));
				// the wall in front of this block: as tall as the step down to the block in front of it
				int hn = gh;
				if (i + 1 < rows && colA[i + 1] != 0) {
					hn = ucA[i + 1] != 0 && uhA[i + 1] != 0 ? uhA[i + 1] : hgA[i + 1];
				}
				int y2 = y1;
				if (gh > hn) {
					float dn = Math.max(-maxDown, Math.min(maxUp, hn - h0));
					y2 = Math.max(y1, Math.round(base - dn * kh + rowPx)); // down to where the lower block's top starts
				}
				// ---- the leaves: a slab floating above, as thick as the leaves really are
				int c0 = 0, c1 = 0, c2 = 0;
				if (tree) {
					float dc = Math.max(-maxDown, Math.min(maxUp, hg - h0));
					float yc = base - dc * kh;
					c0 = Math.round(yc);
					c1 = Math.max(c0 + 1, Math.round(yc + rowPx));
					c2 = c1 + Math.max(1, Math.round((ucA[i] >>> 24) * kh));
				}
				if (pickY >= 0) {
					if (pickY >= y0 && pickY < y2 || tree && pickY >= c0 && pickY < c2) {
						double wx = p.cx + u * rx + v * dx, wz = p.cz + u * rz + v * dz;
						pick[0] = Math.floor(wx) + 0.5;
						pick[1] = Math.floor(wz) + 0.5;
					}
					continue;
				}
				int top = (tree ? ucA[i] : c) & 0xFFFFFF;
				fillCol(px, y0, y1, top);
				if (y2 > y1) {
					fillCol(px, y1, y2, MapStore.scale(top, 0.66F));
				}
				if (tree) {
					int lc = c & 0xFFFFFF;
					fillCol(px, c0, c1, lc);
					fillCol(px, c1, c2, MapStore.scale(lc, 0.64F));
				}
			}
		}
	}

	private void fillCol(int px, int y0, int y1, int rgb) {
		int a = Math.max(0, y0), b = Math.min(th, y1);
		for (int y = a; y < b; y++) {
			int idx = y * tw + px;
			buf[idx] = rgb;
			known[idx] = true;
		}
	}

	/**
	 * Which block is under pixel (px, py) of the picture made with these settings (3D view), as world x / z of its
	 * middle, or null. Lets the full map put a waypoint where you really click.
	 */
	public double[] pick(MapStore store, P p, int px, int py) {
		if (!(p.threeD && !p.cave) || px < 0 || py < 0 || px >= p.w || py >= p.h) {
			return null;
		}
		st = store;
		cv = false;
		ck = Long.MIN_VALUE;
		ca = null;
		final float rx = (float) -Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw), dx = (float) Math.sin(p.yaw), dz = (float) -Math.cos(p.yaw);
		double[] out = new double[] { Double.NaN, Double.NaN };
		terrain3d(p, rx, rz, dx, dz, px, py, out);
		return Double.isNaN(out[0]) ? null : out;
	}

	/** Colour of the map at an offset (in texels) from the centre; alpha 0 = nothing known there (the fog colour is returned). */
	private int sample(double cx, double cz, float ox, float oy, float rx, float rz, float dx, float dz, float inv, int fog) {
		double wx = cx + (ox * rx + oy * dx) * inv, wz = cz + (ox * rz + oy * dz) * inv;
		int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
		int c = at(bx, bz);
		if (c == 0) {
			int n = ((bx * 73856093) ^ (bz * 19349663)) >>> 9 & 7;
			return MapStore.scale(fog, 0.985F + n * 0.003F) & 0xFFFFFF;
		}
		return c;
	}

	private static float sdf(float ox, float oy, float hw, float hh, float rr, int shape) {
		if (shape == 0) {
			return (float) Math.sqrt(ox * ox + oy * oy) - Math.min(hw, hh);
		}
		float qx = Math.abs(ox) - (hw - rr), qy = Math.abs(oy) - (hh - rr);
		float ax = Math.max(qx, 0F), ay = Math.max(qy, 0F);
		return (float) Math.sqrt(ax * ax + ay * ay) + Math.min(Math.max(qx, qy), 0F) - rr;
	}
}
