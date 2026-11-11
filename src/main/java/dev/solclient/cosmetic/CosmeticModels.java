package dev.solclient.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Gfx;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/**
 * 3D cosmetics bundled in the jar (assets/sol_client/cosmetics): index.json lists every item (id, name, category,
 * geometry, texture, animation), each geometry is a small binary file made from the Sketchfab models
 * (converted to Minecraft's player model space: one piece per body part it follows).
 */
public final class CosmeticModels {

	/** What a piece is attached to. */
	public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, PET = 6, WING_LEFT = 7, WING_RIGHT = 8;

	/** One piece: triangles as x, y, z, u, v per vertex (pixels, relative to the part's pivot) plus one normal per triangle. */
	public record Part(int attach, float[] verts, float[] normals) {
		public int triangles() {
			return normals.length / 3;
		}
	}

	public record Def(String id, String name, String category, String geometry, String texture, String anim, String credit, int texW, int texH,
			String pet, String group, String groupName, String variant, String swatch) {
		public Identifier textureId() {
			return Gfx.id("textures/cosmetics/" + texture + ".png");
		}

		/** Pets that walk / fly around in the world (assets/sol_client/pets/&lt;pet&gt;.json) instead of sitting on your shoulder. */
		public boolean livePet() {
			return pet != null;
		}
	}

	private static Map<String, Def> defs;
	private static final Map<String, List<Part>> GEOMETRY = new HashMap<>();

	private CosmeticModels() {
	}

	public static synchronized Map<String, Def> all() {
		if (defs == null) {
			defs = new LinkedHashMap<>();
			try {
				JsonElement root = read("cosmetics/index.json");
				if (root != null && root.isJsonArray()) {
					for (JsonElement e : (JsonArray) root) {
						JsonObject o = e.getAsJsonObject();
						Def d = new Def(o.get("id").getAsString(), o.get("name").getAsString(), o.get("category").getAsString(),
								o.get("geometry").getAsString(), o.get("texture").getAsString(),
								o.has("anim") ? o.get("anim").getAsString() : null, o.has("credit") ? o.get("credit").getAsString() : "",
								o.has("textureSize") ? o.getAsJsonArray("textureSize").get(0).getAsInt() : 64,
								o.has("textureSize") ? o.getAsJsonArray("textureSize").get(1).getAsInt() : 64,
								o.has("pet") ? o.get("pet").getAsString() : null,
								o.has("group") ? o.get("group").getAsString() : null, o.has("groupName") ? o.get("groupName").getAsString() : null,
								o.has("variant") ? o.get("variant").getAsString() : null, o.has("swatch") ? o.get("swatch").getAsString() : null);
						defs.put(d.id(), d);
					}
				}
			} catch (Exception e) {
				System.err.println("[Sleep] could not read the cosmetics index: " + e);
			}
		}
		return defs;
	}

	/** Every colour of a group (index order); the first one is the group's main item. */
	public static List<Def> variantsOf(String group) {
		List<Def> out = new ArrayList<>();
		if (group != null) {
			for (Def d : all().values()) {
				if (group.equals(d.group())) {
					out.add(d);
				}
			}
		}
		return out;
	}

	/** True for the extra colours of a group: they are not items of their own (the group's main item has a colour picker). */
	public static boolean isExtraVariant(Def d) {
		if (d.group() == null) {
			return false;
		}
		List<Def> v = variantsOf(d.group());
		return !v.isEmpty() && v.get(0) != d;
	}

	public static Collection<Def> list() {
		return Collections.unmodifiableCollection(all().values());
	}

	/** The model for a shop item: by its model id / item id, or by its name turned into an id ("Pirate Hat" -> pirate_hat). */
	public static Def find(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		Map<String, Def> m = all();
		Def d = m.get(key);
		if (d == null) {
			d = m.get(slug(key));
		}
		if (d == null) {
			// a site item that only carries the display name ("Inferno Aura"): match the model by its name
			d = byName().get(slug(key));
		}
		return d;
	}

	private static Map<String, Def> byName;

	private static synchronized Map<String, Def> byName() {
		if (byName == null) {
			Map<String, Def> n = new HashMap<>();
			for (Def d : all().values()) {
				n.putIfAbsent(slug(d.groupName() != null ? d.groupName() : d.name()), d);
				n.putIfAbsent(slug(d.name()), d);
			}
			byName = n;
		}
		return byName;
	}

	public static String slug(String s) {
		return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	public static synchronized List<Part> geometry(Def d) {
		List<Part> g = GEOMETRY.get(d.geometry());
		if (g != null) {
			return g;
		}
		g = new ArrayList<>();
		try {
			Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(Gfx.id("cosmetics/" + d.geometry() + ".bin"));
			if (res.isPresent()) {
				try (InputStream raw = res.get().getInputStream(); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
					if (in.readInt() != 0x504C4347) {
						throw new IllegalStateException("bad file");
					}
					in.readInt(); // version
					int parts = in.readInt();
					for (int p = 0; p < parts; p++) {
						int attach = in.readByte();
						int tris = in.readInt();
						float[] v = new float[tris * 15];
						for (int i = 0; i < v.length; i++) {
							v[i] = in.readFloat();
						}
						float[] n = new float[tris * 3];
						for (int t = 0; t < tris; t++) {
							int o = t * 15;
							float ax = v[o + 5] - v[o], ay = v[o + 6] - v[o + 1], az = v[o + 7] - v[o + 2];
							float bx = v[o + 10] - v[o], by = v[o + 11] - v[o + 1], bz = v[o + 12] - v[o + 2];
							float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
							float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
							if (len < 1e-6F) {
								len = 1;
							}
							n[t * 3] = nx / len;
							n[t * 3 + 1] = ny / len;
							n[t * 3 + 2] = nz / len;
						}
						g.add(new Part(attach, v, n));
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] could not load cosmetic " + d.geometry() + ": " + e);
		}
		GEOMETRY.put(d.geometry(), g);
		return g;
	}

	private static JsonElement read(String path) throws Exception {
		Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(Gfx.id(path));
		if (res.isEmpty()) {
			return null;
		}
		try (BufferedReader r = res.get().getReader()) {
			return JsonParser.parseReader(r);
		}
	}
}
