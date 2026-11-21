package dev.sleepclient.fx.killfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.Gfx;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * A 3D kill effect made in Blockbench (assets/sleep_client/killfx/&lt;id&gt;.json, written by tools/killfx/bbmodel_to_killfx.py).
 * Units: model pixels (16 = 1 block), y up, the victim stands at the origin and the viewer looks from +Z.
 * Rotations are already in radians and in the bone's own direction (the converter undoes Blockbench's sign flips).
 */
public final class KillFxModel {

	public static final class Bone {
		public final String name;
		public final float px, py, pz, restX, restY, restZ;
		public final int parent;
		/** Only exists in Blockbench / the preview: never drawn in game. */
		public final boolean preview;
		/** The dead player's stand-in: drawn with the victim's own skin (64x64 skin UVs). */
		public final boolean skin;
		/** x, y, z, u, v for 4 corners per quad. */
		public final float[] verts;
		public final float[] normals;
		/** Keyframes {time, x, y, z, interpolation(0 linear, 1 catmull-rom, 2 step)}, or null. */
		float[][] rot, pos, scale;

		Bone(JsonObject b) {
			name = b.get("name").getAsString();
			JsonArray p = b.getAsJsonArray("pivot");
			px = p.get(0).getAsFloat();
			py = p.get(1).getAsFloat();
			pz = p.get(2).getAsFloat();
			JsonArray r = b.has("rest") ? b.getAsJsonArray("rest") : null;
			restX = r == null ? 0 : r.get(0).getAsFloat();
			restY = r == null ? 0 : r.get(1).getAsFloat();
			restZ = r == null ? 0 : r.get(2).getAsFloat();
			parent = b.get("parent").getAsInt();
			preview = b.has("preview") && b.get("preview").getAsBoolean();
			skin = b.has("skin") && b.get("skin").getAsBoolean();
			JsonArray q = b.getAsJsonArray("quads");
			verts = new float[q.size()];
			for (int i = 0; i < verts.length; i++) {
				verts[i] = q.get(i).getAsFloat();
			}
			int quads = verts.length / 20;
			normals = new float[quads * 3];
			for (int k = 0; k < quads; k++) {
				int o = k * 20;
				float ax = verts[o + 5] - verts[o], ay = verts[o + 6] - verts[o + 1], az = verts[o + 7] - verts[o + 2];
				float bx = verts[o + 10] - verts[o], by = verts[o + 11] - verts[o + 1], bz = verts[o + 12] - verts[o + 2];
				float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len < 1e-6F) {
					len = 1;
				}
				// bent towards the sky so the sides keep their bright colours under Minecraft's entity lighting
				nx /= len;
				ny = ny / len + 0.6F;
				nz /= len;
				len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				normals[k * 3] = nx / len;
				normals[k * 3 + 1] = ny / len;
				normals[k * 3 + 2] = nz / len;
			}
		}
	}

	public final String id, name;
	public final float length;
	public final Identifier texture;
	public final Bone[] bones;
	public final float[] eventTimes;
	public final String[] events;

	private KillFxModel(JsonObject o) {
		id = o.get("id").getAsString();
		name = o.get("name").getAsString();
		length = o.get("length").getAsFloat();
		texture = Gfx.id("textures/killfx/" + o.get("texture").getAsString() + ".png");
		JsonArray bs = o.getAsJsonArray("bones");
		bones = new Bone[bs.size()];
		for (int i = 0; i < bones.length; i++) {
			bones[i] = new Bone(bs.get(i).getAsJsonObject());
		}
		for (JsonElement e : o.getAsJsonArray("tracks")) {
			JsonObject t = e.getAsJsonObject();
			int bi = t.get("bone").getAsInt();
			if (bi < 0 || bi >= bones.length) {
				continue;
			}
			JsonArray ks = t.getAsJsonArray("keys");
			float[][] keys = new float[ks.size()][5];
			for (int k = 0; k < keys.length; k++) {
				JsonArray a = ks.get(k).getAsJsonArray();
				for (int j = 0; j < 5; j++) {
					keys[k][j] = a.get(j).getAsFloat();
				}
			}
			switch (t.get("ch").getAsString()) {
				case "r" -> bones[bi].rot = keys;
				case "p" -> bones[bi].pos = keys;
				case "s" -> bones[bi].scale = keys;
				default -> {
				}
			}
		}
		JsonArray ev = o.getAsJsonArray("events");
		eventTimes = new float[ev.size()];
		events = new String[ev.size()];
		for (int i = 0; i < events.length; i++) {
			JsonArray a = ev.get(i).getAsJsonArray();
			eventTimes[i] = a.get(0).getAsFloat();
			events[i] = a.get(1).getAsString();
		}
	}

	/** Value of a track at time t into 'out' (x, y, z), or 'def' when the bone has no such track. Same maths as the preview. */
	static void sample(float[][] keys, float t, float def, float[] out) {
		if (keys == null || keys.length == 0) {
			out[0] = out[1] = out[2] = def;
			return;
		}
		int n = keys.length;
		if (t <= keys[0][0]) {
			copy(keys[0], out);
			return;
		}
		if (t >= keys[n - 1][0]) {
			copy(keys[n - 1], out);
			return;
		}
		int i = 0;
		while (i < n - 2 && keys[i + 1][0] < t) {
			i++;
		}
		float[] a = keys[i], b = keys[i + 1];
		if (a[4] == 2) {
			copy(a, out);
			return;
		}
		float u = (t - a[0]) / Math.max(1e-6F, b[0] - a[0]);
		boolean cat = a[4] == 1 || b[4] == 1;
		float[] p = keys[Math.max(0, i - 1)], q = keys[Math.min(n - 1, i + 2)];
		for (int k = 1; k < 4; k++) {
			if (cat) {
				float p0 = p[k], p1 = a[k], p2 = b[k], p3 = q[k], u2 = u * u, u3 = u2 * u;
				out[k - 1] = 0.5F * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
			} else {
				out[k - 1] = a[k] + (b[k] - a[k]) * u;
			}
		}
	}

	private static void copy(float[] k, float[] out) {
		out[0] = k[1];
		out[1] = k[2];
		out[2] = k[3];
	}

	// ---------------------------------------------------------------- loading
	public static final String[] IDS = {"pacman", "shark", "mimic", "frog", "flytrap", "ufo", "blackhole", "rocket", "whale", "dragon", "worm", "snake",
			"croc", "trex", "toilet", "hand", "kraken", "pig", "ghost", "eagle", "piano", "tornado", "gift", "cannon",
			"thunder", "anvil", "grave", "balloons", "ice", "glove", "portal", "swords", "bed"};
	/** Display names, same order as IDS. */
	public static final String[] NAMES = {"Pac-Man", "Shark", "Mimic Chest", "Giant Frog", "Flytrap", "UFO", "Black Hole", "Firework Rocket", "Whale",
			"Dragon", "Giant Worm", "Snake", "Crocodile", "T-Rex", "Toilet Flush", "Giant Hand", "Kraken", "Hungry Pig", "Ghost", "Eagle", "Piano Drop",
			"Tornado", "Surprise Gift", "Cannon", "Thunder Strike", "Anvil Drop", "Rest in Peace", "Balloon Ride", "Ice Shatter", "Knockout", "Void Portal",
			"Sword Rain", "Sweet Dreams"};
	private static final Map<String, Optional<KillFxModel>> CACHE = new HashMap<>();

	public static synchronized KillFxModel get(String id) {
		if (id == null) {
			return null;
		}
		return CACHE.computeIfAbsent(id, KillFxModel::load).orElse(null);
	}

	/** Forget loaded effects (after a resource reload, so edited files are picked up). */
	public static synchronized void clear() {
		CACHE.clear();
	}

	/** Why the last effect could not be loaded (shown in the Kill Effect menu). */
	public static volatile String lastError = "";

	private static Optional<KillFxModel> load(String id) {
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Gfx.id("killfx/" + id + ".json"));
			if (res.isEmpty()) {
				System.err.println("[Sleep] kill effect missing: " + id);
				lastError = "file killfx/" + id + ".json not found";
				return Optional.empty();
			}
			try (BufferedReader r = res.get().openAsReader()) {
				return Optional.of(new KillFxModel(JsonParser.parseReader(r).getAsJsonObject()));
			}
		} catch (Exception e) {
			System.err.println("[Sleep] could not load kill effect " + id + ": " + e);
			lastError = String.valueOf(e);
			return Optional.empty();
		}
	}
}
