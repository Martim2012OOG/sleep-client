package dev.solclient.cosmetic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Small position tweak for a cosmetic (up/down and forward/back), in 1/16 of a block, limited to {@value #MAX}
 * so a piece can never end up far from the body. Kept on this computer, one entry per cosmetic (or per colour group).
 */
public final class Offsets {

	public static final int MAX = 3;
	private static final Gson GSON = new Gson();
	private static final Map<String, int[]> MAP = new ConcurrentHashMap<>();
	private static boolean loaded;

	private Offsets() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-cosmetic-offsets.json");
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
					o.entrySet().forEach(e -> {
						JsonArray a = e.getValue().getAsJsonArray();
						MAP.put(e.getKey(), new int[] {clamp(a.get(0).getAsInt()), clamp(a.get(1).getAsInt())});
					});
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] cosmetic offsets: " + e);
		}
	}

	private static int clamp(int v) {
		return Math.max(-MAX, Math.min(MAX, v));
	}

	/** Colour variants of one item share the same tweak. */
	public static String key(CosmeticModels.Def d) {
		return d.group() != null ? d.group() : d.id();
	}

	/** {up, forward} in 1/16 block (up positive = higher, forward positive = away from the body towards the front). */
	public static int[] get(CosmeticModels.Def d) {
		load();
		int[] v = d == null ? null : MAP.get(key(d));
		return v == null ? new int[] {0, 0} : v;
	}

	public static void nudge(CosmeticModels.Def d, int up, int fwd) {
		if (d == null) {
			return;
		}
		load();
		int[] v = get(d);
		MAP.put(key(d), new int[] {clamp(v[0] + up), clamp(v[1] + fwd)});
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

	private static void save() {
		try {
			JsonObject o = new JsonObject();
			MAP.forEach((k, v) -> {
				JsonArray a = new JsonArray();
				a.add(v[0]);
				a.add(v[1]);
				o.add(k, a);
			});
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] could not save the position: " + e);
		}
	}
}
