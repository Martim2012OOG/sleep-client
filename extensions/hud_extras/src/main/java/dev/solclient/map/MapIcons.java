package dev.solclient.map;

import dev.solclient.Gfx;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Everything drawn on top of the map: heads of players and mobs, your arrow, waypoint badges. */
public final class MapIcons {

	private MapIcons() {
	}

	private static final MinecraftClient mc = MinecraftClient.getInstance();

	/** Where the front of a mob's head is in its texture (measured on a texture of rw x rh pixels). */
	private record Spec(String[] tex, int rw, int rh, int u, int v, int w, int h) {
	}

	private static final Map<String, Spec> SPECS = new HashMap<>();

	private static void spec(String id, int rw, int rh, int u, int v, int w, int h, String... tex) {
		SPECS.put(id, new Spec(tex, rw, rh, u, v, w, h));
	}

	static {
		spec("zombie", 64, 64, 8, 8, 8, 8, "textures/entity/zombie/zombie.png");
		spec("husk", 64, 64, 8, 8, 8, 8, "textures/entity/zombie/husk.png");
		spec("drowned", 64, 64, 8, 8, 8, 8, "textures/entity/zombie/drowned.png");
		spec("skeleton", 64, 32, 8, 8, 8, 8, "textures/entity/skeleton/skeleton.png");
		spec("stray", 64, 32, 8, 8, 8, 8, "textures/entity/skeleton/stray.png");
		spec("bogged", 64, 32, 8, 8, 8, 8, "textures/entity/skeleton/bogged.png");
		spec("wither_skeleton", 64, 32, 8, 8, 8, 8, "textures/entity/skeleton/wither_skeleton.png");
		spec("creeper", 64, 32, 8, 8, 8, 8, "textures/entity/creeper/creeper.png");
		spec("enderman", 64, 32, 8, 8, 8, 8, "textures/entity/enderman/enderman.png");
		spec("spider", 64, 32, 40, 12, 8, 8, "textures/entity/spider/spider.png");
		spec("cave_spider", 64, 32, 40, 12, 8, 8, "textures/entity/spider/cave_spider.png");
		spec("blaze", 64, 32, 8, 8, 8, 8, "textures/entity/blaze.png");
		spec("ghast", 64, 32, 16, 16, 16, 16, "textures/entity/ghast/ghast.png");
		spec("villager", 64, 64, 8, 8, 8, 10, "textures/entity/villager/villager.png");
		spec("wandering_trader", 64, 64, 8, 8, 8, 10, "textures/entity/wandering_trader.png");
		spec("pillager", 64, 64, 8, 8, 8, 10, "textures/entity/illager/pillager.png");
		spec("vindicator", 64, 64, 8, 8, 8, 10, "textures/entity/illager/vindicator.png");
		spec("evoker", 64, 64, 8, 8, 8, 10, "textures/entity/illager/evoker.png");
		spec("witch", 64, 128, 8, 8, 8, 10, "textures/entity/witch.png");
		spec("iron_golem", 128, 128, 8, 8, 8, 10, "textures/entity/iron_golem/iron_golem.png");
		spec("snow_golem", 64, 64, 8, 8, 8, 8, "textures/entity/snow_golem.png");
		spec("pig", 64, 64, 8, 8, 8, 8, "textures/entity/pig/pig_temperate.png", "textures/entity/pig/pig.png");
		spec("cow", 64, 64, 6, 6, 8, 8, "textures/entity/cow/cow_temperate.png", "textures/entity/cow/cow.png");
		spec("mooshroom", 64, 64, 6, 6, 8, 8, "textures/entity/cow/red_mooshroom.png", "textures/entity/cow/mooshroom.png");
		spec("sheep", 64, 32, 8, 8, 6, 6, "textures/entity/sheep/sheep.png");
		spec("chicken", 64, 32, 3, 3, 4, 6, "textures/entity/chicken/chicken_temperate.png", "textures/entity/chicken.png");
		spec("wolf", 64, 32, 4, 4, 6, 6, "textures/entity/wolf/wolf.png");
		spec("rabbit", 64, 32, 37, 5, 5, 4, "textures/entity/rabbit/brown.png");
		spec("fox", 48, 32, 7, 11, 8, 6, "textures/entity/fox/fox.png");
		spec("polar_bear", 128, 64, 7, 7, 7, 7, "textures/entity/bear/polarbear.png");
		spec("piglin", 64, 64, 8, 8, 10, 8, "textures/entity/piglin/piglin.png");
		spec("piglin_brute", 64, 64, 8, 8, 10, 8, "textures/entity/piglin/piglin_brute.png");
		spec("zombified_piglin", 64, 64, 8, 8, 10, 8, "textures/entity/piglin/zombified_piglin.png");
		// cats and dogs (every coat / variant is tried until one exists in the game or your resource pack)
		spec("wolf", 64, 32, 4, 4, 6, 6, "textures/entity/wolf/wolf.png", "textures/entity/wolf/wolf_tame.png", "textures/entity/wolf/wolf_pale.png",
				"textures/entity/wolf/pale.png", "textures/entity/wolf/wolf_ashen.png", "textures/entity/wolf/wolf_woods.png");
		String[] cats = { "tabby", "all_black", "black", "red", "siamese", "british_shorthair", "calico", "persian", "ragdoll", "white", "jellie" };
		String[] catTex = new String[cats.length];
		for (int i = 0; i < cats.length; i++) {
			catTex[i] = "textures/entity/cat/" + cats[i] + ".png";
		}
		spec("cat", 64, 32, 5, 5, 5, 4, catTex);
		spec("ocelot", 64, 32, 5, 5, 5, 4, "textures/entity/cat/ocelot.png");
		spec("llama", 128, 64, 9, 9, 4, 4, "textures/entity/llama/brown.png", "textures/entity/llama/creamy.png", "textures/entity/llama/gray.png",
				"textures/entity/llama/white.png");
		spec("trader_llama", 128, 64, 9, 9, 4, 4, "textures/entity/llama/brown.png", "textures/entity/llama/creamy.png", "textures/entity/llama/gray.png");
		spec("bat", 64, 64, 6, 6, 6, 6, "textures/entity/bat.png", "textures/entity/bat/bat.png");
		spec("parrot", 32, 32, 4, 4, 2, 3, "textures/entity/parrot/parrot_red_blue.png", "textures/entity/parrot/parrot_green.png",
				"textures/entity/parrot/parrot_blue.png");
		spec("squid", 64, 32, 12, 12, 12, 16, "textures/entity/squid/squid.png", "textures/entity/squid.png");
		spec("glow_squid", 64, 32, 12, 12, 12, 16, "textures/entity/squid/glow_squid.png");
		spec("bee", 64, 64, 10, 10, 7, 7, "textures/entity/bee/bee.png");
		spec("guardian", 64, 64, 16, 16, 12, 12, "textures/entity/guardian.png", "textures/entity/guardian/guardian.png");
		spec("elder_guardian", 64, 64, 16, 16, 12, 12, "textures/entity/guardian_elder.png", "textures/entity/guardian/guardian_elder.png");
		spec("warden", 128, 128, 10, 10, 16, 16, "textures/entity/warden/warden.png");
		spec("vex", 64, 64, 8, 8, 8, 10, "textures/entity/illager/vex.png");
		spec("zombie_villager", 64, 64, 8, 8, 8, 10, "textures/entity/zombie_villager/zombie_villager.png");
		spec("parched", 64, 32, 8, 8, 8, 8, "textures/entity/skeleton/parched.png");
		spec("hoglin", 128, 64, 80, 20, 14, 6, "textures/entity/hoglin/hoglin.png");
		spec("zoglin", 128, 64, 80, 20, 14, 6, "textures/entity/hoglin/zoglin.png");
		spec("ravager", 128, 128, 16, 16, 16, 20, "textures/entity/illager/ravager.png");
		spec("phantom", 64, 64, 5, 5, 7, 3, "textures/entity/phantom.png");
		spec("panda", 64, 64, 9, 15, 13, 10, "textures/entity/panda/panda.png");
		spec("dolphin", 64, 64, 6, 6, 8, 7, "textures/entity/dolphin.png");
		spec("allay", 32, 32, 5, 5, 5, 5, "textures/entity/allay/allay.png");
		spec("turtle", 128, 64, 9, 6, 6, 5, "textures/entity/turtle/big_sea_turtle.png");
		spec("frog", 48, 48, 9, 9, 7, 3, "textures/entity/frog/temperate_frog.png", "textures/entity/frog/cold_frog.png", "textures/entity/frog/warm_frog.png");
		spec("axolotl", 64, 64, 5, 6, 8, 5, "textures/entity/axolotl/axolotl_lucy.png", "textures/entity/axolotl/axolotl_wild.png");
		spec("slime", 64, 32, 8, 8, 8, 8, "textures/entity/slime/slime.png");
		spec("magma_cube", 64, 32, 8, 8, 8, 8, "textures/entity/slime/magmacube.png");
	}

	/** Colours for the little drawn face of mobs we do not have a head for (so they still show a face, never an egg). */
	private static final int[] FACE_COLOURS = { 0xC9A27A, 0x8FB874, 0x7FA3D6, 0xD68F8F, 0xB59AD6, 0xD6C46F, 0x7FCBC3, 0xB8B8B8, 0xD69B5E, 0x9AA86B };
	private static final Map<String, Integer> FACE_FIXED = new HashMap<>();

	static {
		FACE_FIXED.put("slime", 0x6FBF5E);
		FACE_FIXED.put("magma_cube", 0xB5481A);
		FACE_FIXED.put("horse", 0xA0724A);
		FACE_FIXED.put("donkey", 0x8B7B6B);
		FACE_FIXED.put("mule", 0x7A5C45);
		FACE_FIXED.put("skeleton_horse", 0xD9D9CF);
		FACE_FIXED.put("zombie_horse", 0x6F8F5F);
		FACE_FIXED.put("camel", 0xD9B26B);
		FACE_FIXED.put("sniffer", 0xB5483A);
		FACE_FIXED.put("goat", 0xE8E4DA);
		FACE_FIXED.put("strider", 0xB8403A);
		FACE_FIXED.put("shulker", 0x9B6FB0);
		FACE_FIXED.put("silverfish", 0x8C8C8C);
		FACE_FIXED.put("endermite", 0x3B2A4D);
		FACE_FIXED.put("breeze", 0x8CB4E6);
		FACE_FIXED.put("creaking", 0x6B5A4A);
		FACE_FIXED.put("cod", 0xB89B6E);
		FACE_FIXED.put("salmon", 0xC76B5A);
		FACE_FIXED.put("tropical_fish", 0xF29B3D);
		FACE_FIXED.put("pufferfish", 0xE6C24D);
		FACE_FIXED.put("tadpole", 0x4A3F36);
		FACE_FIXED.put("armadillo", 0xB07F6A);
		FACE_FIXED.put("nautilus", 0xE8B88A);
		FACE_FIXED.put("happy_ghast", 0xF2F2F2);
	}

	private static final class Head {
		Identifier tex;
		int tw, th;
		float u, v, w, h;
		int faceColour;
	}

	private static final Map<EntityType<?>, Head> HEADS = new HashMap<>();
	private static long clearedAt;

	private static Head resolve(EntityType<?> type) {
		Head h = new Head();
		Identifier eid = Registries.ENTITY_TYPE.getId(type);
		Spec sp = SPECS.get(eid.getPath());
		if (sp != null) {
			for (String path : sp.tex()) {
				Identifier id = Identifier.of("minecraft", path);
				try {
					var res = mc.getResourceManager().getResource(id);
					if (res.isPresent()) {
						try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
							h.tex = id;
							h.tw = img.getWidth();
							h.th = img.getHeight();
						}
						float kx = h.tw / (float) sp.rw(), ky = h.th / (float) sp.rh();
						h.u = sp.u() * kx;
						h.v = sp.v() * ky;
						h.w = sp.w() * kx;
						h.h = sp.h() * ky;
						return h;
					}
				} catch (Exception ignored) {
				}
			}
		}
		Integer fixed = FACE_FIXED.get(eid.getPath());
		h.faceColour = fixed != null ? fixed : FACE_COLOURS[Math.floorMod(eid.getPath().hashCode(), FACE_COLOURS.length)];
		return h;
	}

	/** The head of a mob (from its own texture, so resource packs change it too); a spawn egg when we don't know its head. */
	public static void mobHead(DrawContext c, Entity e, int x, int y, int s) {
		long now = System.currentTimeMillis();
		if (now - clearedAt > 60000L) {
			HEADS.clear();
			clearedAt = now;
		}
		Head h = HEADS.computeIfAbsent(e.getType(), MapIcons::resolve);
		if (h.tex != null) {
			c.drawTexture(RenderPipelines.GUI_TEXTURED, h.tex, x, y, h.u, h.v, s, s, Math.max(1, Math.round(h.w)), Math.max(1, Math.round(h.h)), h.tw, h.th, 0xFFFFFFFF);
		} else {
			face(c, x, y, s, h.faceColour);
		}
	}

	/** A small drawn face (two eyes and a mouth) for a mob whose real head we do not know. */
	private static void face(DrawContext c, int x, int y, int s, int base) {
		int dark = MapStore.scale(base, 0.62F);
		int light = MapStore.scale(base, 1.18F);
		c.fill(x, y, x + s, y + s, 0xFF000000 | base);
		c.fill(x, y, x + s, y + Math.max(1, s / 5), 0xFF000000 | dark);
		c.fill(x, y + s - Math.max(1, s / 6), x + s, y + s, 0xFF000000 | MapStore.scale(base, 0.85F));
		int e = Math.max(1, s / 5), ey = y + s * 2 / 5;
		int lx = x + s / 5, rx = x + s - s / 5 - e;
		c.fill(lx, ey, lx + e, ey + e, 0xFFFFFFFF);
		c.fill(rx, ey, rx + e, ey + e, 0xFFFFFFFF);
		int pp = Math.max(1, e / 2);
		c.fill(lx + e - pp, ey + e - pp, lx + e, ey + e, 0xFF14141C);
		c.fill(rx + e - pp, ey + e - pp, rx + e, ey + e, 0xFF14141C);
		int my = y + s * 7 / 10;
		c.fill(x + s * 3 / 10, my, x + s * 7 / 10, my + Math.max(1, s / 8), 0xFF000000 | dark);
		c.fill(x, y, x + s, y + 1, 0xFF000000 | light);
	}

	public interface Painter {
		void draw(int x, int y, int size);
	}

	/** A framed icon centred on (cx, cy). style: 0 square frame, 1 round frame, 2 no frame. */
	public static void icon(DrawContext c, int style, float cx, float cy, int s, int ring, Painter p) {
		int hs = style == 1 ? Math.max(4, Math.round(s * 0.76F)) : s;
		int x = Math.round(cx - hs / 2F), y = Math.round(cy - hs / 2F);
		if (style == 0) {
			c.fill(x - 2, y - 2, x + hs + 2, y + hs + 2, 0xB0000000);
			c.fill(x - 1, y - 1, x + hs + 1, y + hs + 1, ring | 0xFF000000);
		} else if (style == 1) {
			float mx = x + hs / 2F, my = y + hs / 2F;
			Gfx.circle(c, mx, my, s * 0.5F + 2.6F, 0xB0000000);
			Gfx.circle(c, mx, my, s * 0.5F + 1.5F, ring | 0xFF000000);
		} else {
			c.fill(x - 1, y - 1, x + hs + 1, y + hs + 1, 0x90000000);
		}
		p.draw(x, y, hs);
	}

	/** An arrow pointing up when ang = 0; fill is the right half, accent the left half. */
	public static void arrow(DrawContext c, float cx, float cy, float ang, float size, int fill, int accent) {
		var ms = c.getMatrices();
		ms.pushMatrix();
		ms.translate(cx, cy);
		ms.rotate(ang);
		float k = size / 14F;
		ms.scale(k, k);
		int[][] o = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { -1, -1 }, { 1, 1 }, { -1, 1 }, { 1, -1 } };
		for (int[] d : o) {
			shape(c, d[0], d[1], 0xFF0A0B10, 0);
		}
		shape(c, 0, 0, fill | 0xFF000000, 0);
		shape(c, 0, 0, accent | 0xFF000000, -1);
		ms.popMatrix();
	}

	private static void shape(DrawContext c, int ox, int oy, int col, int half) {
		for (int y = -7; y <= 6; y++) {
			int hw = (int) Math.round((y + 7) / 13.0 * 5.5);
			int cut = y >= 1 ? (int) Math.round((y - 1) / 5.0 * 3) : -1;
			int l1 = -hw, r1 = cut < 0 ? hw + 1 : -cut;
			if (half == -1) {
				r1 = Math.min(r1, 0);
			}
			if (r1 > l1) {
				c.fill(ox + l1, oy + y, ox + r1, oy + y + 1, col);
			}
			if (cut >= 0 && half != -1) {
				c.fill(ox + cut + 1, oy + y, ox + hw + 1, oy + y + 1, col);
			}
		}
	}

	public static void dot(DrawContext c, float cx, float cy, float r, int col) {
		Gfx.circle(c, cx, cy, r + 1.2F, 0xFF0A0B10);
		Gfx.circle(c, cx, cy, r, col | 0xFF000000);
	}

	/** Round waypoint badge with initials. */
	public static void badge(DrawContext c, float cx, float cy, float r, int col, String label) {
		Gfx.circle(c, cx, cy, r + 1.4F, 0xFF0A0B10);
		Gfx.circle(c, cx, cy, r, col | 0xFF000000);
		if (label != null && !label.isEmpty()) {
			int w = mc.textRenderer.getWidth(label);
			float k = Math.min(1F, r * 1.55F / Math.max(w, 6));
			var ms = c.getMatrices();
			ms.pushMatrix();
			ms.translate(cx, cy);
			ms.scale(k, k);
			c.drawText(mc.textRenderer, label, -w / 2, -4, 0xFFFFFFFF, false);
			ms.popMatrix();
		}
	}
}
