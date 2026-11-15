package dev.solclient.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;

/**
 * The pixels (ARGB, 64x64) of the skins we have seen, needed to turn the outer skin layers into 3D. Downloaded skins are captured when the game
 * registers them; skins that are dynamic textures or resources (Steve, Alex, hosted skins) are read from their texture on demand.
 */
public final class SkinPixels {

	private static final Map<Identifier, int[]> MAP = new ConcurrentHashMap<>();
	private static final Map<Identifier, Long> MISS = new ConcurrentHashMap<>();

	private SkinPixels() {
	}

	private static int[] copy(NativeImage img) {
		if (img == null || img.getWidth() != 64 || img.getHeight() != 64) {
			return null;
		}
		int[] px = new int[64 * 64];
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				px[y * 64 + x] = img.getPixel(x, y);
			}
		}
		return px;
	}

	/** Called when a downloaded skin is registered (possibly on another thread). */
	public static void put(Identifier id, NativeImage img) {
		try {
			int[] px = copy(img);
			if (px != null && id != null) {
				if (MAP.size() > 300) {
					MAP.clear();
				}
				MAP.put(id, px);
			}
		} catch (RuntimeException ignored) {
		}
	}

	/** The skin's pixels, or null (not known yet / not a 64x64 skin). Render thread. */
	public static int[] get(Identifier id) {
		if (id == null) {
			return null;
		}
		int[] px = MAP.get(id);
		if (px != null) {
			return px;
		}
		Long miss = MISS.get(id);
		long now = System.currentTimeMillis();
		if (miss != null && now - miss < 4000L) {
			return null;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			AbstractTexture t = mc.getTextureManager().getTexture(id);
			if (t instanceof DynamicTexture n) {
				px = copy(n.getPixels());
			}
			if (px == null) {
				try (TextureContents c = TextureContents.load(mc.getResourceManager(), id)) {
					px = copy(c.image());
				}
			}
		} catch (Throwable ignored) {
			px = null;
		}
		if (px != null) {
			MAP.put(id, px);
		} else {
			MISS.put(id, now);
		}
		return px;
	}
}
