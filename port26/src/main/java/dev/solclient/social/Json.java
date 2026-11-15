package dev.solclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Null-safe readers for the backend's JSON (missing / null fields never throw). */
final class Json {

	private Json() {
	}

	static JsonObject obj(JsonElement e) {
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	static JsonObject obj(JsonObject o, String key) {
		return o == null ? null : obj(o.get(key));
	}

	static JsonArray arr(JsonObject o, String key) {
		if (o == null) {
			return new JsonArray();
		}
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	static String str(JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return null;
		}
		if (e.isJsonPrimitive()) {
			return e.getAsString();
		}
		return null;
	}

	/** First non-empty string among the keys. */
	static String str(JsonObject o, String... keys) {
		for (String k : keys) {
			String s = str(o, k);
			if (s != null && !s.isEmpty()) {
				return s;
			}
		}
		return null;
	}

	static long num(JsonObject o, String key, long def) {
		if (o == null) {
			return def;
		}
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
			return def;
		}
		try {
			return Math.round(e.getAsDouble());
		} catch (RuntimeException ex) {
			return def;
		}
	}

	static Long numOrNull(JsonObject o, String key) {
		if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
			return null;
		}
		long v = num(o, key, Long.MIN_VALUE);
		return v == Long.MIN_VALUE ? null : v;
	}

	static boolean bool(JsonObject o, String key, boolean def) {
		if (o == null) {
			return def;
		}
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
			return def;
		}
		try {
			return e.getAsBoolean();
		} catch (RuntimeException ex) {
			return def;
		}
	}

	static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : arr(o, key)) {
			if (e != null && e.isJsonPrimitive()) {
				out.add(e.getAsString());
			}
		}
		return out;
	}
}
