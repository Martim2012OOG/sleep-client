package dev.solclient.cosmetic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * How a cosmetic sits on the player, tweaked in the Locker ("Edit"): up/down, left/right and forward/back in 1/16 of a block
 * (max {@value #MAX}), a small turn (max {@value #ROT_MAX} degrees) and an optional colour tint. Limited so a piece can never end
 * up far from the body. Kept on this computer, and sent to the other Sleep players on your server so they see it the same.
 */
public final class Offsets {

	public static final int MAX = 3, ROT_MAX = 20;
	/** Indexes of one entry. */
	public static final int UP = 0, FWD = 1, SIDE = 2, ROT = 3, TINT = 4;
	private static final Gson GSON = new Gson();
	private static final Map<String, int[]> MAP = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, int[]>> REMOTE = new ConcurrentHashMap<>();
	private static final int[] ZERO = new int[5];
	private static boolean loaded;
	private static volatile int version;

	private Offsets() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-cosmetic-offsets.json");
	}

	private static int clamp(int i, int v) {
		return i == TINT ? v : i == ROT ? Math.max(-ROT_MAX, Math.min(ROT_MAX, v)) : Math.max(-MAX, Math.min(MAX, v));
	}

	private static int[] read(JsonArray a) {
		int[] r = new int[5];
		for (int i = 0; i < 5 && i < a.size(); i++) {
			r[i] = clamp(i, a.get(i).getAsInt());
		}
		return r;
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
				if (o != null) {
					o.entrySet().forEach(e -> MAP.put(e.getKey(), read(e.getValue().getAsJsonArray())));
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] cosmetic offsets: " + e);
		}
	}

	/** Colour variants of one item share the same tweak. */
	public static String key(CosmeticModels.Def d) {
		return d.group() != null ? d.group() : d.id();
	}

	/** Yours. */
	public static int[] get(CosmeticModels.Def d) {
		load();
		int[] v = d == null ? null : MAP.get(key(d));
		return v == null ? ZERO : v;
	}

	/** For a player in the world: yours when it is you, the one they sent when it is somebody else. */
	public static int[] get(CosmeticModels.Def d, UUID player) {
		if (d == null) {
			return ZERO;
		}
		UUID me = net.minecraft.client.MinecraftClient.getInstance().getSession().getUuidOrNull();
		if (player == null || player.equals(me)) {
			return get(d);
		}
		Map<String, int[]> r = REMOTE.get(player.toString().replace("-", ""));
		int[] v = r == null ? null : r.get(key(d));
		return v == null ? ZERO : v;
	}

	public static void nudge(CosmeticModels.Def d, int idx, int delta) {
		if (d == null) {
			return;
		}
		load();
		int[] v = MAP.getOrDefault(key(d), ZERO).clone();
		v[idx] = clamp(idx, v[idx] + delta);
		MAP.put(key(d), v);
		save();
	}

	public static void setTint(CosmeticModels.Def d, int argb) {
		if (d == null) {
			return;
		}
		load();
		int[] v = MAP.getOrDefault(key(d), ZERO).clone();
		v[TINT] = argb;
		MAP.put(key(d), v);
		save();
	}

	public static void reset(CosmeticModels.Def d) {
		if (d == null) {
			return;
		}
		load();
		MAP.remove(key(d));
		save();
	}

	public static int version() {
		return version;
	}

	private static void save() {
		version++;
		try {
			JsonObject o = new JsonObject();
			MAP.forEach((k, v) -> o.add(k, arr(v)));
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] could not save the position: " + e);
		}
	}

	private static JsonArray arr(int[] v) {
		JsonArray a = new JsonArray();
		for (int x : v) {
			a.add(x);
		}
		return a;
	}

	// ------------------------------------------------------------------ shared with the other players
	/** What I send: my colour choice of every item with colours that I wear, and the tweak of every item I wear. */
	public static JsonObject mine(java.util.List<CosmeticModels.Def> worn) {
		load();
		JsonObject o = new JsonObject(), v = new JsonObject(), t = new JsonObject();
		for (CosmeticModels.Def d : worn) {
			if (d.group() != null) {
				CosmeticModels.Def c = Variants.chosen(d.group());
				if (c != null) {
					v.addProperty(d.group(), c.id());
				}
			}
			int[] x = get(d);
			boolean any = false;
			for (int i : x) {
				any |= i != 0;
			}
			if (any) {
				t.add(key(d), arr(x));
			}
		}
		if (v.size() > 0) {
			o.add("v", v);
		}
		if (t.size() > 0) {
			o.add("o", t);
		}
		return o;
	}

	/** Somebody else's choices (from the presence room). */
	public static void onRemote(String uuid, JsonObject cx) {
		if (uuid == null) {
			return;
		}
		Map<String, int[]> m = new ConcurrentHashMap<>();
		Map<String, String> vs = new ConcurrentHashMap<>();
		try {
			if (cx != null && cx.has("o")) {
				cx.getAsJsonObject("o").entrySet().forEach(e -> m.put(e.getKey(), read(e.getValue().getAsJsonArray())));
			}
			if (cx != null && cx.has("v")) {
				cx.getAsJsonObject("v").entrySet().forEach(e -> vs.put(e.getKey(), e.getValue().getAsString()));
			}
		} catch (RuntimeException ignored) {
			return;
		}
		REMOTE.put(uuid, m);
		REMOTE_VARIANT.put(uuid, vs);
	}

	private static final Map<String, Map<String, String>> REMOTE_VARIANT = new ConcurrentHashMap<>();

	/** The colour another player picked for this item (or the item itself when we do not know). */
	public static CosmeticModels.Def remoteVariant(UUID player, CosmeticModels.Def d) {
		if (d == null || d.group() == null || player == null) {
			return d;
		}
		Map<String, String> m = REMOTE_VARIANT.get(player.toString().replace("-", ""));
		String id = m == null ? null : m.get(d.group());
		CosmeticModels.Def c = id == null ? null : CosmeticModels.find(id);
		return c != null && d.group().equals(c.group()) ? c : d;
	}
}
