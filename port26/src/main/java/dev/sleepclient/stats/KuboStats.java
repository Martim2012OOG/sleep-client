package dev.sleepclient.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Lifetime counters (kills, blocks, distance, play time, clips...) for Session Stats and Achievements. */
public final class KuboStats {

	private static final Map<String, Double> TOTAL = new HashMap<>();
	private static boolean loaded, dirty;
	private static long savedAt;

	private KuboStats() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-stats.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
				for (String k : o.keySet()) {
					if (o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isNumber()) {
						TOTAL.put(k, o.get(k).getAsDouble());
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] stats: " + e);
		}
		touchDay();
	}

	/** Days in a row you played (for the streak achievement). */
	private static void touchDay() {
		long today = LocalDate.now().toEpochDay();
		long last = (long) get("lastDay");
		if (last == today) {
			return;
		}
		set("streak", last == today - 1 ? get("streak") + 1 : 1);
		set("bestStreak", Math.max(get("bestStreak"), get("streak")));
		set("lastDay", today);
	}

	public static double get(String k) {
		load();
		return TOTAL.getOrDefault(k, 0.0);
	}

	public static void set(String k, double v) {
		load();
		TOTAL.put(k, v);
		dirty = true;
	}

	public static void add(String k, double v) {
		set(k, get(k) + v);
		dev.sleepclient.ext.KuboApi.statsChanged();
	}

	public static void max(String k, double v) {
		if (v > get(k)) {
			set(k, v);
			dev.sleepclient.ext.KuboApi.statsChanged();
		}
	}

	/** Saves now and then (and on exit). */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (dirty && now - savedAt > 30_000L) {
			save();
		}
	}

	public static void save() {
		if (!loaded) {
			return;
		}
		savedAt = System.currentTimeMillis();
		dirty = false;
		try {
			JsonObject o = new JsonObject();
			TOTAL.forEach(o::addProperty);
			Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[Sleep] stats: " + e);
		}
	}
}
