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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** The emotes bundled in assets/sol_client/emotes (index.json + one json per emote). */
public final class EmoteLibrary {

	public record Info(String id, String name, String author, boolean loop, boolean icon) {

		public Identifier iconId() {
			return Gfx.id("textures/gui/emote/" + id + ".png");
		}
	}

	private static List<Info> all;
	/** Emotes the site added (admin zip upload). They are shown after the ones built into the client. */
	private static final List<Info> REMOTE = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final Map<String, String> REMOTE_URL = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<Integer, Info> REMOTE_BY_WIRE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<String, Integer> WIRE_BY_ID = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<String> FETCHING = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static boolean remoteLoaded;
	/** Wire numbers of the site's emotes start here (the built-in ones use their place in index.json, armor stand poses start at 10000). */
	public static final int REMOTE_WIRE_MIN = 1000, REMOTE_WIRE_MAX = 9999;
	/** Every entry of index.json in order, including the hidden ones: other players' emotes travel as a position in THIS list. */
	private static List<Info> raw;
	private static final Map<String, Animation> CACHE = new HashMap<>();
	/** index.json "bodyScale": some bundled files move the whole body in pixels, not blocks (the player flew away). */
	private static final Map<String, Float> BODY_SCALE = new HashMap<>();

	private EmoteLibrary() {
	}

	/** True if you can use this emote (bought in the shop; everything while the site sells no emotes). */
	public static boolean owned(String id) {
		java.util.Set<String> o = dev.solclient.social.Social.ownedEmotes();
		if (unreleasedAllowed()) {
			return true; // owner switch: every emote, bought or not, sold or not
		}
		return o == null || id != null && o.contains(id.toLowerCase(java.util.Locale.ROOT));
	}

	/** The owner's "Use Emotes Not In Store Yet" switch (only works for the owner account). */
	public static boolean unreleasedAllowed() {
		try {
			var set = dev.solclient.SolClient.settings;
			if (set == null || !set.unreleasedEmotes.on()) {
				return false;
			}
			String n = net.minecraft.client.Minecraft.getInstance().getUser().getName();
			var me = dev.solclient.social.Social.me;
			return "marzox_x".equalsIgnoreCase(n) || me != null && "marzox_x".equalsIgnoreCase(me.mcUsername);
		} catch (RuntimeException e) {
			return false;
		}
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
		loadRemoteCache();
		if (REMOTE.isEmpty()) {
			return all;
		}
		List<Info> merged = new ArrayList<>(all);
		merged.addAll(REMOTE);
		return merged;
	}

	// ------------------------------------------------------------------ emotes sent by the site
	private static java.nio.file.Path remoteDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("emote-cache");
	}

	private static String key(String id, String url) {
		return id.replaceAll("[^a-z0-9_]", "") + "-" + Integer.toHexString(url == null ? 0 : url.hashCode());
	}

	/** The site's emotes of the last run (so they are in the wheel and the Locker at once, even before the shop answers). */
	private static synchronized void loadRemoteCache() {
		if (remoteLoaded) {
			return;
		}
		remoteLoaded = true;
		try {
			java.nio.file.Path f = remoteDir().resolve("list.json");
			if (java.nio.file.Files.isRegularFile(f)) {
				for (JsonElement el : JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					addRemote(o.get("id").getAsString(), o.get("name").getAsString(), o.get("loop").getAsBoolean(), o.get("wire").getAsInt(),
							o.get("url").getAsString(), o.has("right") && !o.get("right").isJsonNull() ? o.get("right").getAsString() : null,
							o.has("left") && !o.get("left").isJsonNull() ? o.get("left").getAsString() : null);
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] emote cache: " + e);
		}
	}

	private static void addRemote(String id, String name, boolean loop, int wire, String url, String right, String left) {
		for (Info i : all) {
			if (i.id().equals(id)) {
				return; // built into the client: the client's own file wins
			}
		}
		Info info = new Info(id, name, "", loop, false);
		REMOTE.removeIf(i -> i.id().equals(id));
		REMOTE.add(info);
		REMOTE_URL.put(id, url);
		if (wire >= REMOTE_WIRE_MIN && wire <= REMOTE_WIRE_MAX) {
			REMOTE_BY_WIRE.put(wire, info);
			WIRE_BY_ID.put(id, wire);
		}
		EmoteItems.setRemote(id, right, left);
	}

	/** The shop answered: the emotes the site sells (admin zip upload) become part of the library. */
	public static void refreshRemote(List<dev.solclient.social.Social.Item> items) {
		all();
		JsonArray out = new JsonArray();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (var it : items) {
			if (it.category == null || !it.category.equalsIgnoreCase("Emotes") || it.emoteUrl == null || it.emoteUrl.isBlank()) {
				continue;
			}
			String id = (it.modelId != null && !it.modelId.isBlank() ? it.modelId : it.id).toLowerCase(java.util.Locale.ROOT);
			if (!id.matches("[a-z0-9_]{1,64}")) {
				continue;
			}
			seen.add(id);
			addRemote(id, it.name, it.emoteLoop, it.emoteWire, it.emoteUrl, it.emoteRight, it.emoteLeft);
			CACHE.remove(id + "|" + REMOTE_URL.get(id)); // a new file under the same id is read again (the url changes with every upload)
			JsonObject o = new JsonObject();
			o.addProperty("id", id);
			o.addProperty("name", it.name);
			o.addProperty("loop", it.emoteLoop);
			o.addProperty("wire", it.emoteWire);
			o.addProperty("url", it.emoteUrl);
			o.addProperty("right", it.emoteRight);
			o.addProperty("left", it.emoteLeft);
			out.add(o);
		}
		// emotes the site no longer sells leave the library
		REMOTE.removeIf(i -> !seen.contains(i.id()));
		REMOTE_URL.keySet().removeIf(k -> !seen.contains(k));
		REMOTE_BY_WIRE.values().removeIf(i -> !seen.contains(i.id()));
		WIRE_BY_ID.keySet().removeIf(k -> !seen.contains(k));
		try {
			java.nio.file.Files.createDirectories(remoteDir());
			java.nio.file.Files.writeString(remoteDir().resolve("list.json"), out.toString());
		} catch (Exception e) {
			System.err.println("[Sleep] emote cache: " + e);
		}
	}

	/** True when this emote comes from the site and its file is not downloaded yet. */
	public static boolean isRemote(Info info) {
		return REMOTE_URL.containsKey(info.id()) && !CACHE.containsKey(info.id() + "|" + REMOTE_URL.get(info.id()));
	}

	/** Downloads (or reads from the disk cache) the animation of a site emote, in the background. */
	private static void fetchRemote(Info info) {
		String url = REMOTE_URL.get(info.id());
		if (url == null || !FETCHING.add(info.id())) {
			return;
		}
		dev.solclient.social.Social.IO.execute(() -> {
			try {
				java.nio.file.Path f = remoteDir().resolve(key(info.id(), url) + ".json");
				byte[] data = java.nio.file.Files.isRegularFile(f) ? java.nio.file.Files.readAllBytes(f) : null;
				if (data == null) {
					data = dev.solclient.social.SolarApi.download(url, 4_000_000);
					if (data == null || data.length < 20) {
						return;
					}
				}
				JsonElement root = JsonParser.parseString(new String(data, java.nio.charset.StandardCharsets.UTF_8));
				if (root == null || !root.isJsonObject()) {
					return;
				}
				Animation a = Animation.parse(root.getAsJsonObject());
				if (!java.nio.file.Files.isRegularFile(f)) {
					java.nio.file.Files.createDirectories(remoteDir());
					java.nio.file.Files.write(f, data);
				}
				CACHE.put(info.id() + "|" + url, a);
			} catch (Exception e) {
				System.err.println("[Sleep] could not load emote " + info.id() + ": " + e);
			} finally {
				FETCHING.remove(info.id());
			}
		});
	}

	/** Position of this emote in index.json (what is sent to other players), or -1. */
	public static int rawIndex(Info info) {
		all();
		Integer w = WIRE_BY_ID.get(info.id());
		if (w != null) {
			return w;
		}
		return raw.indexOf(info);
	}

	/** The emote at this position of index.json, or null when it is out of range or was removed (hidden). */
	public static Info byRawIndex(int index) {
		all();
		if (index >= REMOTE_WIRE_MIN && index <= REMOTE_WIRE_MAX) {
			return REMOTE_BY_WIRE.get(index);
		}
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
					if (o.has("bodyScale")) {
						BODY_SCALE.put(id, o.get("bodyScale").getAsFloat());
					}
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
		String rurl = REMOTE_URL.get(info.id());
		if (rurl != null) {
			Animation ra = CACHE.get(info.id() + "|" + rurl);
			if (ra == null) {
				fetchRemote(info); // returns null until it is downloaded: the caller tries again
			}
			return ra;
		}
		Animation a = CACHE.get(info.id());
		if (a != null) {
			return a;
		}
		try {
			JsonElement root = read("emotes/" + info.id() + ".json");
			if (root != null && root.isJsonObject()) {
				a = Animation.parse(root.getAsJsonObject());
				all();
				Float k = BODY_SCALE.get(info.id());
				if (k != null) {
					a.scaleBodyMove(k);
				}
				CACHE.put(info.id(), a);
			}
		} catch (Exception e) {
			System.err.println("[Sleep] could not load emote " + info.id() + ": " + e);
		}
		return a;
	}

	private static JsonElement read(String path) throws Exception {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Gfx.id(path));
		if (res.isEmpty()) {
			return null;
		}
		try (BufferedReader r = res.get().openAsReader()) {
			return JsonParser.parseReader(r);
		}
	}
}
