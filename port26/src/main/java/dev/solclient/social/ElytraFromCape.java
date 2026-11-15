package dev.solclient.social;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Elytra textures made from a cape. Minecraft draws the elytra with the cape texture when there is no elytra texture,
 * and a cape is a full, flat rectangle there, so the wings looked like a green tent / a box. This makes a real
 * elytra texture instead: the shape (transparent edges) and the feather shading come from the vanilla elytra
 * (or the resource pack's), the colours from the cape. Render thread only.
 */
public final class ElytraFromCape {

	private static final Identifier VANILLA = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");
	private static NativeImage vanilla;
	private static boolean vanillaFailed;
	private static double meanLum = -1;

	private ElytraFromCape() {
	}

	/**
	 * True when the elytra part of the cape texture (22,0)-(46,22) of 64x32 is painted with a wing shape: mostly opaque
	 * but with transparent pixels around it (a plain filled rectangle is not elytra art).
	 */
	static boolean hasElytraArt(NativeImage cape) {
		int w = cape.getWidth(), h = cape.getHeight();
		double k = w / 64.0;
		int x0 = (int) Math.round(22 * k), x1 = Math.min(w, (int) Math.round(46 * k)), y1 = Math.min(h, (int) Math.round(22 * k));
		int step = Math.max(1, (int) Math.round(k / 2));
		int opaque = 0, clear = 0;
		for (int y = 0; y < y1; y += step) {
			for (int x = x0; x < x1; x += step) {
				if ((cape.getPixel(x, y) >>> 24) > 40) {
					opaque++;
				} else {
					clear++;
				}
			}
		}
		int total = opaque + clear;
		return total > 0 && opaque > total / 4 && clear > total / 10;
	}

	/** Forget the cached vanilla elytra (resource packs changed). */
	public static void reset() {
		if (vanilla != null) {
			vanilla.close();
		}
		vanilla = null;
		vanillaFailed = false;
		meanLum = -1;
	}

	private static NativeImage vanilla() {
		if (vanilla != null || vanillaFailed) {
			return vanilla;
		}
		try {
			Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(VANILLA);
			if (r.isPresent()) {
				try (InputStream in = r.get().open()) {
					vanilla = NativeImage.read(in);
				}
			}
		} catch (Throwable t) {
			System.err.println("[Sleep] vanilla elytra texture: " + t);
		}
		vanillaFailed = vanilla == null;
		return vanilla;
	}

	private static double lum(int argb) {
		return 0.299 * (argb >> 16 & 255) + 0.587 * (argb >> 8 & 255) + 0.114 * (argb & 255);
	}

	/**
	 * A new elytra texture (same size as the cape) or null when it can't be made (odd cape size, no vanilla
	 * texture). The caller owns the image.
	 */
	public static NativeImage make(NativeImage cape) {
		// a cape made in the cape + elytra template (like the shop capes) already has its own elytra art: keep it
		if (hasElytraArt(cape)) {
			NativeImage copy = new NativeImage(cape.getWidth(), cape.getHeight(), true);
			copy.copyFrom(cape);
			return copy;
		}
		NativeImage v = vanilla();
		if (cape == null || v == null) {
			return null;
		}
		int w = cape.getWidth(), h = cape.getHeight();
		if (w < 64 || h * 2 != w || v.getWidth() < 64 || v.getHeight() * 2 != v.getWidth()) {
			return null;
		}
		double k = w / 64.0, vk = v.getWidth() / 64.0;
		// average brightness of the vanilla wings: the shading is relative to it
		if (meanLum < 0) {
			double sum = 0;
			int n = 0;
			for (int y = 0; y < (int) (22 * vk); y++) {
				for (int x = (int) (22 * vk); x < (int) (46 * vk); x++) {
					int p = v.getPixel(x, y);
					if ((p >>> 24) > 16) {
						sum += lum(p);
						n++;
					}
				}
			}
			meanLum = n == 0 ? 128 : Math.max(20, sum / n);
		}
		// does the cape already have its own elytra design? (more than a quarter of that part opaque)
		int opaque = 0, total = 0;
		for (int y = 0; y < (int) (22 * k); y += Math.max(1, (int) k)) {
			for (int x = (int) (22 * k); x < (int) (46 * k); x += Math.max(1, (int) k)) {
				total++;
				if ((cape.getPixel(x, y) >>> 24) > 32) {
					opaque++;
				}
			}
		}
		boolean ownDesign = total > 0 && opaque > total / 4;
		NativeImage out = new NativeImage(w, h, true);
		for (int y = 0; y < (int) (22 * k); y++) {
			for (int x = (int) (22 * k); x < (int) (46 * k) && x < w; x++) {
				double u = (x - 22 * k) / k, t = y / k; // wing coordinates in the 64 x 32 grid (0..24, 0..22)
				int vp = v.getPixel(Math.min(v.getWidth() - 1, (int) ((22 + u) * vk)), Math.min(v.getHeight() - 1, (int) (t * vk)));
				int va = vp >>> 24;
				if (va == 0) {
					continue;
				}
				int src;
				if (ownDesign) {
					src = cape.getPixel(x, y);
				} else {
					// the outside of the cape (1,1)-(11,17) stretched over the wings
					int sx = (int) ((1 + u / 24.0 * 10.0) * k), sy = (int) ((1 + t / 22.0 * 16.0) * k);
					src = cape.getPixel(Math.min(w - 1, sx), Math.min(h - 1, sy));
				}
				if ((src >>> 24) < 16) {
					src = vp; // a hole in the cape: keep the vanilla feather there
				}
				double s = Math.max(0.45, Math.min(1.45, lum(vp) / meanLum));
				int r = (int) Math.min(255, (src >> 16 & 255) * s), g = (int) Math.min(255, (src >> 8 & 255) * s), b = (int) Math.min(255, (src & 255) * s);
				int a = Math.min(va, Math.max(src >>> 24, 160));
				out.setPixel(x, y, a << 24 | r << 16 | g << 8 | b);
			}
		}
		return out;
	}
}
