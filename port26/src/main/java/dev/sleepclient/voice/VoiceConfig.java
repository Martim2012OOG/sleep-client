package dev.sleepclient.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** Voice chat things that are not simple options: chosen devices, per-player volume and mutes (config/plegumo-voice.json). */
public final class VoiceConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static String mic, speaker;
	private static final Map<String, Float> VOLUMES = new HashMap<>();
	private static final Set<String> MUTED = new HashSet<>();
	private static boolean loaded;

	private VoiceConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-voice.json");
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (!Files.exists(file())) {
				return;
			}
			JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
			mic = o.has("mic") ? o.get("mic").getAsString() : null;
			speaker = o.has("speaker") ? o.get("speaker").getAsString() : null;
			if (o.has("volumes")) {
				for (var e : o.getAsJsonObject("volumes").entrySet()) {
					VOLUMES.put(e.getKey(), e.getValue().getAsFloat());
				}
			}
			if (o.has("muted")) {
				o.getAsJsonArray("muted").forEach(e -> MUTED.add(e.getAsString()));
			}
		} catch (Exception e) {
			System.err.println("[Sleep] voice config: " + e);
		}
	}

	private static synchronized void save() {
		JsonObject o = new JsonObject();
		if (mic != null) {
			o.addProperty("mic", mic);
		}
		if (speaker != null) {
			o.addProperty("speaker", speaker);
		}
		JsonObject v = new JsonObject();
		VOLUMES.forEach(v::addProperty);
		o.add("volumes", v);
		com.google.gson.JsonArray m = new com.google.gson.JsonArray();
		MUTED.forEach(m::add);
		o.add("muted", m);
		try {
			Files.writeString(file(), GSON.toJson(o));
		} catch (Exception e) {
			System.err.println("[Sleep] voice config: " + e);
		}
	}

	public static String mic() {
		load();
		return mic;
	}

	public static String speaker() {
		load();
		return speaker;
	}

	public static void setMic(String m) {
		load();
		mic = m;
		save();
	}

	public static void setSpeaker(String s) {
		load();
		speaker = s;
		save();
	}

	/** 0..2 (100% = 1). */
	public static float playerVolume(String uuid) {
		load();
		return VOLUMES.getOrDefault(uuid, 1F);
	}

	public static void setPlayerVolume(String uuid, float v) {
		load();
		VOLUMES.put(uuid, Math.max(0F, Math.min(2F, v)));
		save();
	}

	public static boolean muted(String uuid) {
		load();
		return MUTED.contains(uuid);
	}

	public static void setMuted(String uuid, boolean m) {
		load();
		if (m) {
			MUTED.add(uuid);
		} else {
			MUTED.remove(uuid);
		}
		save();
	}
}
