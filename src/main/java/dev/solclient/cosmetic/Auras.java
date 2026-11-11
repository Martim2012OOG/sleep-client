package dev.solclient.cosmetic;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Auras: small sprites that move around a player (embers rising, snow falling, stars orbiting...). A cosmetic of category
 * "Auras" with "anim": "aura:&lt;style&gt;" in cosmetics/index.json; the sprites are a row of textures/cosmetics/aura_atlas.png
 * (made by tools/gen_auras.py). Everything is computed from the time, nothing is stored or allocated per frame: an aura
 * costs two small quads per sprite.
 */
public final class Auras {

	private static final int ORBIT = 0, RISE = 1, FALL = 2, HELIX = 3, FLICKER = 4, DRIFT = 5;
	/** Sprite sheet: 8 columns x 16 rows of 8x8 sprites. */
	private static final float CELL_U = 1F / 8F, CELL_V = 1F / 16F;
	private static final int FULL_BRIGHT = 0xF000F0;

	private record Style(int row, int count, int motion, float size, float speed, boolean glow) {
	}

	private static final Map<String, Style> STYLES = new HashMap<>();

	static {
		STYLES.put("flame", new Style(0, 14, RISE, 0.17F, 0.55F, true));
		STYLES.put("frost", new Style(1, 14, FALL, 0.15F, 0.28F, true));
		STYLES.put("heart", new Style(2, 9, RISE, 0.16F, 0.3F, true));
		STYLES.put("star", new Style(3, 12, ORBIT, 0.15F, 0.9F, true));
		STYLES.put("note", new Style(4, 8, DRIFT, 0.17F, 0.45F, true));
		STYLES.put("cash", new Style(5, 12, FALL, 0.17F, 0.36F, false));
		STYLES.put("bolt", new Style(6, 9, FLICKER, 0.2F, 1F, true));
		STYLES.put("leaf", new Style(7, 12, FALL, 0.16F, 0.3F, false));
		STYLES.put("bubble", new Style(8, 11, RISE, 0.15F, 0.24F, false));
		STYLES.put("rune", new Style(9, 14, HELIX, 0.15F, 1.4F, true));
		STYLES.put("zzz", new Style(10, 5, RISE, 0.17F, 0.2F, true));
		STYLES.put("skull", new Style(11, 7, ORBIT, 0.18F, 0.6F, true));
	}

	private Auras() {
	}

	public static boolean is(CosmeticModels.Def d) {
		return d != null && d.anim() != null && d.anim().startsWith("aura:");
	}

	private static float hash(int i, int k) {
		float v = (float) Math.sin(i * 12.9898F + k * 78.233F) * 43758.547F;
		return v - (float) Math.floor(v);
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	/**
	 * Draws the aura in the player's model space (blocks; y points down, 0 = the neck, 1.5 = the feet). 'age' in ticks.
	 * 'few' = far away / many players: half of the sprites.
	 */
	public static void emit(CosmeticModels.Def d, MatrixStack.Entry entry, VertexConsumer vc, int light, float age, boolean few) {
		Style s = STYLES.get(d.anim().substring(5));
		if (s == null) {
			return;
		}
		float t = age / 20F;
		int l = s.glow ? FULL_BRIGHT : light;
		int n = few ? (s.count + 1) / 2 : s.count;
		for (int i = 0; i < n; i++) {
			float h0 = hash(i, 1), h1 = hash(i, 2), h2 = hash(i, 3), h3 = hash(i, 4);
			float x, y, z, size = s.size * (0.8F + 0.4F * h2);
			switch (s.motion) {
				case RISE -> {
					float u = frac(t * s.speed * (0.6F + 0.8F * h1) + h0);
					float r = 0.38F + 0.3F * h2, a = h3 * 6.2832F + u * 1.6F;
					x = (float) Math.cos(a) * r;
					z = (float) Math.sin(a) * r;
					y = 1.5F - u * 2.15F;
					size *= (float) Math.sin(Math.PI * u);
				}
				case FALL -> {
					float u = frac(t * s.speed * (0.6F + 0.8F * h1) + h0);
					float r = 0.42F + 0.36F * h2, a = h3 * 6.2832F + (float) Math.sin(t * 1.3F + i) * 0.55F;
					x = (float) Math.cos(a) * r;
					z = (float) Math.sin(a) * r;
					y = -0.75F + u * 2.25F;
					size *= Math.min(1F, (float) Math.sin(Math.PI * u) * 2.5F);
				}
				case HELIX -> {
					float k = i / (float) n;
					float a = t * s.speed + k * 12.566F + (i % 2) * 3.1416F;
					float r = 0.6F + 0.06F * (float) Math.sin(t * 2F + i);
					x = (float) Math.cos(a) * r;
					z = (float) Math.sin(a) * r;
					y = 1.45F - frac(k + t * 0.12F) * 2.1F;
					size *= 0.75F + 0.25F * (float) Math.sin(t * 3F + i * 1.7F);
				}
				case FLICKER -> {
					int step = (int) Math.floor(age / 3F) + i * 7;
					if (hash(step, 5) > 0.45F) {
						continue;
					}
					float a = hash(step, 6) * 6.2832F, r = 0.45F + 0.3F * hash(step, 7);
					x = (float) Math.cos(a) * r;
					z = (float) Math.sin(a) * r;
					y = 1.4F - hash(step, 8) * 2F;
				}
				case DRIFT -> {
					float u = frac(t * s.speed * (0.5F + 0.5F * h1) + h0);
					float a = h3 * 6.2832F, r = 0.5F + 0.35F * u;
					x = (float) Math.cos(a) * r + (float) Math.sin(t * 2.2F + i * 2.1F) * 0.08F;
					z = (float) Math.sin(a) * r;
					y = 0.5F - u * 1.3F - h2 * 0.5F;
					size *= Math.min(1F, (float) Math.sin(Math.PI * u) * 2F);
				}
				default -> {
					float a = t * s.speed * (0.7F + 0.6F * h1) * (i % 2 == 0 ? 1F : -1F) + h0 * 6.2832F;
					float r = 0.55F + 0.22F * h2;
					x = (float) Math.cos(a) * r;
					z = (float) Math.sin(a) * r;
					y = 1.4F - h3 * 1.95F + 0.1F * (float) Math.sin(t * 1.3F + i);
					size *= 0.7F + 0.3F * (float) Math.sin(t * 4F + i * 2.3F);
				}
			}
			if (size < 0.02F) {
				continue;
			}
			float u0 = (i & 7) * CELL_U, v0 = s.row * CELL_V, u1 = u0 + CELL_U, v1 = v0 + CELL_V;
			float hs = size / 2F;
			// two crossed quads: looks like a sprite from every side without knowing where the camera is
			quad(entry, vc, l, x - hs, y - hs, z, x + hs, y + hs, z, u0, v0, u1, v1);
			quad(entry, vc, l, x, y - hs, z - hs, x, y + hs, z + hs, u0, v0, u1, v1);
		}
	}

	private static void quad(MatrixStack.Entry e, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1,
			float u0, float v0, float u1, float v1) {
		int ov = OverlayTexture.DEFAULT_UV;
		vc.vertex(e, x0, y0, z0).color(0xFFFFFFFF).texture(u0, v0).overlay(ov).light(light).normal(e, 0F, -1F, 0F);
		vc.vertex(e, x0, y1, z0).color(0xFFFFFFFF).texture(u0, v1).overlay(ov).light(light).normal(e, 0F, -1F, 0F);
		vc.vertex(e, x1, y1, z1).color(0xFFFFFFFF).texture(u1, v1).overlay(ov).light(light).normal(e, 0F, -1F, 0F);
		vc.vertex(e, x1, y0, z1).color(0xFFFFFFFF).texture(u1, v0).overlay(ov).light(light).normal(e, 0F, -1F, 0F);
	}
}
