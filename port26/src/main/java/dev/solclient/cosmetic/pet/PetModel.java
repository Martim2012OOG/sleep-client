package dev.solclient.cosmetic.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Gfx;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * A pet rig (assets/sol_client/pets/&lt;id&gt;.json, made by tools/import_pets.py): bones with a role (head, legs, tail,
 * wings...), a pivot and their quads. Pixels, y up, feet on y = 0, the pet looks towards -z.
 */
public final class PetModel {

	public enum Kind {
		/** four legs: walks, trots, sits, lies down (dogs). */
		WALK,
		/** moves only by hopping, squashes when it lands (plushies, blobs). */
		HOP,
		/** two short legs, rocks from side to side (crewmates). */
		WADDLE,
		/** flaps its wings and flies around you, can land on your shoulder. */
		FLY,
		/** a ball that rolls along the ground (Verity). */
		ROLL,
		/** floats and drifts, goes through walls (ghosts). */
		FLOAT
	}

	public static final class Bone {
		public final String role;
		public final float px, py, pz;
		public final int parent;
		/** x, y, z, u, v for 4 corners per quad. */
		public final float[] verts;
		/** One normal per quad. */
		public final float[] normals;

		Bone(String role, float px, float py, float pz, int parent, float[] verts) {
			this.role = role;
			this.px = px;
			this.py = py;
			this.pz = pz;
			this.parent = parent;
			this.verts = verts;
			int quads = verts.length / 20;
			normals = new float[quads * 3];
			for (int q = 0; q < quads; q++) {
				int o = q * 20;
				// quads can be triangles with a repeated corner: use corners 0, 1, 2
				float ax = verts[o + 5] - verts[o], ay = verts[o + 6] - verts[o + 1], az = verts[o + 7] - verts[o + 2];
				float bx = verts[o + 10] - verts[o], by = verts[o + 11] - verts[o + 1], bz = verts[o + 12] - verts[o + 2];
				float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len < 1e-6F) {
					// corners 0 and 1 coincide (the pole of a ball): the diagonals still give the normal, so the top is not drawn black
					float dx = verts[o + 10] - verts[o], dy = verts[o + 11] - verts[o + 1], dz = verts[o + 12] - verts[o + 2];
					float ex = verts[o + 15] - verts[o + 5], ey = verts[o + 16] - verts[o + 6], ez = verts[o + 17] - verts[o + 7];
					nx = dy * ez - dz * ey;
					ny = dz * ex - dx * ez;
					nz = dx * ey - dy * ex;
					len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
					if (len < 1e-6F) {
						nx = 0;
						ny = 1;
						nz = 0;
						len = 1;
					}
				}
				normals[q * 3] = nx / len;
				normals[q * 3 + 1] = ny / len;
				normals[q * 3 + 2] = nz / len;
			}
		}
	}

	public final String id, name;
	public final Kind kind;
	public final float scale;
	/** Width, height, length in pixels (before scale). */
	public final float sizeX, sizeY, sizeZ;
	public final Identifier texture;
	public final Bone[] bones;
	public final String soundAmbient, soundHappy;
	public final float soundPitch, soundVolume;
	/** Flying pets: flap speed multiplier and hover height above your head. */
	public final float flap, hover;
	public final boolean erratic, glow;
	public final String particles;
	private final Map<String, Integer> byRole = new HashMap<>();

	private PetModel(JsonObject o) {
		id = o.get("id").getAsString();
		name = o.get("name").getAsString();
		kind = Kind.valueOf(o.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT));
		scale = o.get("scale").getAsFloat();
		JsonArray sz = o.getAsJsonArray("size");
		sizeX = sz.get(0).getAsFloat();
		sizeY = sz.get(1).getAsFloat();
		sizeZ = sz.get(2).getAsFloat();
		texture = Gfx.id("textures/cosmetics/" + o.get("texture").getAsString() + ".png");
		JsonObject snd = o.has("sounds") ? o.getAsJsonObject("sounds") : new JsonObject();
		soundAmbient = snd.has("ambient") ? snd.get("ambient").getAsString() : null;
		soundHappy = snd.has("happy") ? snd.get("happy").getAsString() : null;
		soundPitch = snd.has("pitch") ? snd.get("pitch").getAsFloat() : 1F;
		soundVolume = snd.has("volume") ? snd.get("volume").getAsFloat() : 0.5F;
		flap = o.has("flap") ? o.get("flap").getAsFloat() : 1F;
		hover = o.has("hover") ? o.get("hover").getAsFloat() : 1.6F;
		erratic = o.has("erratic") && o.get("erratic").getAsInt() != 0;
		glow = o.has("glow") && o.get("glow").getAsInt() != 0;
		particles = o.has("particles") ? o.get("particles").getAsString() : null;
		JsonArray bs = o.getAsJsonArray("bones");
		List<String> roles = new ArrayList<>();
		for (JsonElement e : bs) {
			roles.add(e.getAsJsonObject().get("role").getAsString());
		}
		bones = new Bone[bs.size()];
		for (int i = 0; i < bs.size(); i++) {
			JsonObject b = bs.get(i).getAsJsonObject();
			JsonArray pv = b.getAsJsonArray("pivot");
			JsonArray q = b.getAsJsonArray("quads");
			float[] v = new float[q.size()];
			for (int k = 0; k < v.length; k++) {
				v[k] = q.get(k).getAsFloat();
			}
			String parent = b.has("parent") && !b.get("parent").isJsonNull() ? b.get("parent").getAsString() : null;
			bones[i] = new Bone(roles.get(i), pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat(),
					parent == null ? -1 : roles.indexOf(parent), v);
			byRole.put(roles.get(i), i);
		}
	}

	public boolean has(String role) {
		return byRole.containsKey(role);
	}

	public boolean hasLegs() {
		return has("leg_fl") || has("leg_bl");
	}

	/** Size of the pet in blocks (its tallest side), for hit boxes and camera checks. */
	public float heightBlocks() {
		return sizeY * scale / 16F;
	}

	public float lengthBlocks() {
		return Math.max(sizeX, sizeZ) * scale / 16F;
	}

	// ---------------------------------------------------------------- loading
	private static final Map<String, Optional<PetModel>> CACHE = new HashMap<>();

	/** The rig with this id, or null (logged once) when it is missing / broken. */
	public static synchronized PetModel get(String id) {
		if (id == null) {
			return null;
		}
		return CACHE.computeIfAbsent(id, PetModel::load).orElse(null);
	}

	private static Optional<PetModel> load(String id) {
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Gfx.id("pets/" + id + ".json"));
			if (res.isEmpty()) {
				System.err.println("[Caos] pet model missing: " + id);
				return Optional.empty();
			}
			try (BufferedReader r = res.get().openAsReader()) {
				return Optional.of(new PetModel(JsonParser.parseReader(r).getAsJsonObject()));
			}
		} catch (Exception e) {
			System.err.println("[Caos] could not load pet " + id + ": " + e);
			return Optional.empty();
		}
	}
}
