package dev.solclient.emote;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/** The emotes bundled in assets/sol_client/emotes (index.json + one json per emote). */
public final class EmoteLibrary {

	public record Info(String id, String name, String author, boolean loop, boolean icon) {

		public Identifier iconId() {
			return Gfx.id("textures/gui/emote/" + id + ".png");
		}
	}

	private static List<Info> all;
	/** Every entry of index.json in order, including the hidden ones: other players' emotes travel as a position in THIS list. */
	private static List<Info> raw;
	private static final Map<String, Animation> CACHE = new HashMap<>();

	private EmoteLibrary() {
	}

	/** True if you can use this emote (bought in the shop; everything while the site sells no emotes). */
	public static boolean owned(String id) {
		java.util.Set<String> o = dev.solclient.social.Social.ownedEmotes();
		return o == null || id != null && o.contains(id.toLowerCase(java.util.Locale.ROOT));
	}

	/** The emotes you own. */
	public static List<Info> mine() {
		List<Info> out = new ArrayList<>();
		for (Info i : all()) {
			if (owned(i.id())) {
				out.add(i);
			}
		}
		return out;
	}

	public static List<Info> all() {
		if (all == null) {
			load();
		}
		return all;
	}

	/** Position of this emote in index.json (what is sent to other players), or -1. */
	public static int rawIndex(Info info) {
		all();
		return raw.indexOf(info);
	}

	/** The emote at this position of index.json, or null when it is out of range or was removed (hidden). */
	public static Info byRawIndex(int index) {
		all();
		if (index < 0 || index >= raw.size()) {
			return null;
		}
		Info i = raw.get(index);
		return all.contains(i) ? i : null;
	}

	private static void load() {
		List<Info> out = new ArrayList<>(), every = new ArrayList<>();
		try {
			JsonElement root = read("emotes/index.json");
			if (root != null && root.isJsonArray()) {
				for (JsonElement el : (JsonArray) root) {
					JsonObject o = el.getAsJsonObject();
					String id = o.get("id").getAsString();
					Info info = new Info(id, o.has("name") ? o.get("name").getAsString() : id,
							o.has("author") ? o.get("author").getAsString() : "",
							o.has("loop") && o.get("loop").getAsBoolean(),
							!o.has("icon") || o.get("icon").getAsBoolean());
					every.add(info);
					if (!(o.has("hidden") && o.get("hidden").getAsBoolean())) { // removed for being unsuitable for young players
						out.add(info);
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] could not read the emote index: " + e);
		}
		all = out;
		raw = every;
	}

	public static Animation animation(Info info) {
		Animation a = CACHE.get(info.id());
		if (a != null) {
			return a;
		}
		try {
			JsonElement root = read("emotes/" + info.id() + ".json");
			if (root != null && root.isJsonObject()) {
				a = Animation.parse(root.getAsJsonObject());
				CACHE.put(info.id(), a);
			}
		} catch (Exception e) {
			System.err.println("[Sleep] could not load emote " + info.id() + ": " + e);
		}
		return a;
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
