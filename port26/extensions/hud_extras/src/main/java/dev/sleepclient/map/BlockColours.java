package dev.sleepclient.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

/**
 * The colour of every block, taken from its real texture (the average of the top picture), so every variant has its own
 * colour (all planks, concrete, terracotta, wool, ores...) instead of the ~60 colours of the vanilla paper map. It reads
 * the textures through the resource manager, so a texture pack changes the map too. Blocks with no texture found fall
 * back to the vanilla map colour (the caller does that when this returns -1).
 */
final class BlockColours {

	private static final Map<Block, Integer> CACHE = new HashMap<>();
	private static ResourceManager lastRm;

	private BlockColours() {
	}

	/** Average colour of the block's top texture, or -1. Not tinted: grass and leaves are grey here (the caller tints them). */
	static int of(Block b) {
		ResourceManager rm = Minecraft.getInstance().getResourceManager();
		if (rm != lastRm) {
			lastRm = rm; // a resource pack change: read the textures again
			CACHE.clear();
		}
		Integer c = CACHE.get(b);
		if (c == null) {
			c = compute(rm, b);
			CACHE.put(b, c);
		}
		return c;
	}

	private static int compute(ResourceManager rm, Block b) {
		try {
			Identifier id = BuiltInRegistries.BLOCK.getKey(b);
			String ns = id.getNamespace(), p = id.getPath();
			for (String cand : candidates(p)) {
				int c = average(rm, Identifier.fromNamespaceAndPath(ns, "textures/block/" + cand + ".png"));
				if (c != -1) {
					return c;
				}
			}
		} catch (Throwable ignored) {
		}
		return -1;
	}

	private static final String[] SUFFIX = { "_stairs", "_slab", "_fence_gate", "_fence", "_wall", "_button", "_pressure_plate", "_wall_sign", "_hanging_sign",
			"_wall_hanging_sign", "_sign", "_carpet", "_pane" };

	private static String[] candidates(String p) {
		String base = p;
		boolean cut = false;
		for (String s : SUFFIX) {
			if (p.endsWith(s) && p.length() > s.length()) {
				base = p.substring(0, p.length() - s.length());
				cut = true;
				break;
			}
		}
		if (p.startsWith("potted_")) {
			base = p.substring(7);
			cut = true;
		}
		if (!cut) {
			return new String[] { p + "_top", p, p + "_side", p + "_front", p + "_stage0", p + "_bottom" };
		}
		return new String[] { p + "_top", p, base + "_planks", base, base + "_top", base + "s", base + "_block", base + "_bricks", base + "_side", base + "_wool" };
	}

	/** Average of the opaque pixels of the first frame of a texture, or -1 when there is no such texture. */
	private static int average(ResourceManager rm, Identifier tex) {
		Optional<Resource> r = rm.getResource(tex);
		if (r.isEmpty()) {
			return -1;
		}
		try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
			int w = img.getWidth(), h = Math.min(img.getHeight(), w); // animated textures are frames stacked downwards: first frame only
			long sr = 0, sg = 0, sb = 0, n = 0;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int a = img.getPixel(x, y);
					if ((a >>> 24) < 128) {
						continue;
					}
					sr += (a >> 16) & 255;
					sg += (a >> 8) & 255;
					sb += a & 255;
					n++;
				}
			}
			if (n == 0) {
				return -1;
			}
			return (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n);
		} catch (Exception e) {
			return -1;
		}
	}

	/** Brightness 0..1 of a colour. */
	static float luma(int c) {
		return (((c >> 16) & 255) * 0.30F + ((c >> 8) & 255) * 0.59F + (c & 255) * 0.11F) / 255F;
	}
}
