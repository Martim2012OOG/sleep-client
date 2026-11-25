package dev.solclient.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.CustomFont;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Texture Styles engine. The player picks a community resource pack (Modrinth) PER ITEM / category; this class
 * searches Modrinth, downloads the packs (SHA-1 checked, cached), and builds ONE generated pack
 * (resourcepacks/kubo-styles-HASH.zip) that holds only the files of the chosen categories (plus the models and
 * textures those files point to). The original packs are never changed and nothing is bundled in Caos.
 */
public final class TextureStyles {

	private TextureStyles() {
	}

	/** A category: id, English name, Portuguese name, Modrinth search words, regex of the files (relative to assets/minecraft/), icon item. */
	public record Cat(String id, String en, String pt, String query, String rx, String item) {
		public ItemStack icon() {
			try {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", item)));
			} catch (Throwable t) {
				return ItemStack.EMPTY;
			}
		}
	}

	/** One search result. */
	public record Hit(String id, String title, String author, String desc, long downloads, String icon, String latest) {
	}

	/** What the player chose for a category. */
	public record Choice(String project, String title, String author, String url, String sha1, String file) {
	}

	private static String item(String names) {
		return "(textures/item|models/item|items)/(" + names + ")(_\\d+)?\\.(png|png\\.mcmeta|json)";
	}

	private static String block(String names) {
		return "textures/block/(" + names + ")\\.(png|png\\.mcmeta)";
	}

	/** Same order as the list in the GUI. */
	public static final List<Cat> CATS = List.of(
			new Cat("crystal", "Crystal", "Cristal", "crystal", "textures/entity/end_crystal/.*|" + item("end_crystal"), "end_crystal"),
			new Cat("anchor", "Anchor", "Âncora", "anchor", "textures/block/respawn_anchor_.*|" + item("respawn_anchor"), "respawn_anchor"),
			new Cat("obsidian", "Obsidian", "Obsidiana", "obsidian", block("obsidian|crying_obsidian"), "obsidian"),
			new Cat("totem", "Totem", "Totem", "totem", item("totem_of_undying"), "totem_of_undying"),
			new Cat("pearl", "Pearl", "Pérola", "pearl", item("ender_pearl"), "ender_pearl"),
			new Cat("gapple", "Gapple", "Maçã dourada", "gapple golden apple", item("golden_apple|enchanted_golden_apple"), "golden_apple"),
			new Cat("sword", "Sword", "Espada", "sword", item("[a-z]+_sword"), "diamond_sword"),
			new Cat("axe", "Axe", "Machado", "axe", item("[a-z]+_axe"), "diamond_axe"),
			new Cat("mace", "Mace", "Maça", "mace", item("mace"), "mace"),
			new Cat("armor", "Armor", "Armadura", "armor", "textures/entity/equipment/humanoid(_leggings)?/diamond\\.png|" + item("diamond_(helmet|chestplate|leggings|boots)"), "diamond_chestplate"),
			new Cat("netherite_armor", "Netherite Armor", "Armadura de netherite", "netherite armor",
					"textures/entity/equipment/humanoid(_leggings)?/netherite\\.png|" + item("netherite_(helmet|chestplate|leggings|boots)"), "netherite_chestplate"),
			new Cat("shield", "Shield", "Escudo", "shield", "textures/entity/shield.*|" + item("shield"), "shield"),
			new Cat("bow", "Bow", "Arco", "bow", item("bow|bow_pulling"), "bow"),
			new Cat("crossbow", "Crossbow", "Besta", "crossbow", item("crossbow|crossbow_pulling|crossbow_arrow|crossbow_firework"), "crossbow"),
			new Cat("pickaxe", "Pickaxe", "Picareta", "pickaxe", item("[a-z]+_pickaxe"), "diamond_pickaxe"),
			new Cat("shovel", "Shovel", "Pá", "shovel tools", item("[a-z]+_shovel"), "diamond_shovel"),
			new Cat("trident", "Trident", "Tridente", "trident", "textures/entity/trident.*|" + item("trident"), "trident"),
			new Cat("elytra", "Elytra", "Élitros", "elytra", "textures/entity/equipment/wings/elytra\\.png|textures/entity/elytra\\.png|" + item("elytra"), "elytra"),
			new Cat("xp_bottle", "XP Bottle", "Garrafa de XP", "xp bottle experience", item("experience_bottle"), "experience_bottle"),
			new Cat("glowstone", "Glowstone", "Glowstone", "glowstone", block("glowstone"), "glowstone"),
			new Cat("cobweb", "Cobweb", "Teia", "cobweb", block("cobweb") + "|" + item("cobweb"), "cobweb"),
			new Cat("explosions", "Explosions", "Explosões", "explosion", "textures/particle/(explosion|sonic_boom|flash).*", "tnt"),
			new Cat("glint", "Glint", "Brilho", "glint enchant", "textures/misc/enchanted_glint_(item|entity)\\.png(\\.mcmeta)?", "enchanted_book"),
			new Cat("potions", "Potions", "Poções", "potion", item("potion|splash_potion|lingering_potion"), "potion"),
			new Cat("wind_charge", "Wind Charge", "Carga de vento", "wind charge", "textures/entity/wind_charge.*|" + item("wind_charge"), "wind_charge"),
			new Cat("hit_effects", "Hit Effects", "Efeitos de hit", "hit effects particles", "textures/particle/(crit|damage|sweep|generic|enchanted_hit).*", "iron_sword"),
			new Cat("hearts", "Hearts", "Corações", "hearts", "textures/gui/sprites/hud/heart/.*", "apple"),
			new Cat("crosshair", "Crosshair", "Mira", "crosshair", "textures/gui/sprites/hud/crosshair.*", "spyglass"),
			new Cat("hotbar", "Hotbar", "Hotbar", "hotbar", "textures/gui/sprites/hud/hotbar.*|textures/gui/sprites/hud/hotbar_.*", "chest"),
			new Cat("fire", "Fire", "Fogo", "fire", "textures/block/(soul_)?fire_(0|1)\\.png(\\.mcmeta)?|textures/block/(soul_)?campfire_fire\\.png(\\.mcmeta)?", "flint_and_steel"),
			new Cat("inventory", "Inventory", "Inventário", "inventory gui", "textures/gui/container/inventory\\.png|textures/gui/sprites/container/slot.*|textures/gui/sprites/container/inventory/.*", "crafting_table"),
			new Cat("ender_chest", "Ender Chest", "Baú do fim", "ender chest", "textures/entity/chest/ender.*|" + item("ender_chest"), "ender_chest"),
			new Cat("shulker", "Shulker", "Shulker", "shulker", "textures/entity/shulker/.*", "shulker_box"),
			new Cat("tnt", "TNT", "TNT", "tnt", block("tnt_side|tnt_top|tnt_bottom") + "|" + item("tnt_minecart"), "tnt"),
			new Cat("fishing_rod", "Fishing Rod", "Cana de pesca", "fishing rod", item("fishing_rod|fishing_rod_cast") + "|textures/entity/fishing_hook\\.png", "fishing_rod"),
			new Cat("food", "Food", "Comida", "food", item("cooked_beef|cooked_porkchop|bread|golden_carrot|apple|steak"), "cooked_beef"));

	public static Cat cat(String id) {
		for (Cat c : CATS) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ http
	private static final String API = "https://api.modrinth.com/v2";
	private static final String UA = "Caos-Client/1.0 (Minecraft client mod)";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(12)).build();

	private static String gameVersion() {
		try {
			return FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString())
					.orElse("1.21.11");
		} catch (Throwable t) {
			return "1.21.11";
		}
	}

	private static String get(String url) throws IOException, InterruptedException {
		HttpRequest rq = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", UA)
				.header("Accept", "application/json").GET().build();
		HttpResponse<String> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (r.statusCode() < 200 || r.statusCode() >= 300) {
			throw new IOException("HTTP " + r.statusCode());
		}
		return r.body();
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static Path searchFile(Cat cat, int offset) {
		return cacheDir().resolve("search").resolve(cat.id() + "_" + offset + ".json");
	}

	/**
	 * Searches Modrinth resource packs for this category (blocking: call it from a thread). The plain list of a category (no search
	 * words, most downloaded first) is kept on disk for 6 hours, so opening a category again shows it at once.
	 */
	public static List<Hit> search(Cat cat, String text, int offset, boolean newest) throws IOException, InterruptedException {
		boolean plain = (text == null || text.isBlank()) && !newest;
		String body = null;
		Path cf = searchFile(cat, offset);
		if (plain) {
			try {
				if (Files.isRegularFile(cf) && System.currentTimeMillis() - Files.getLastModifiedTime(cf).toMillis() < 6L * 3600 * 1000) {
					body = Files.readString(cf, StandardCharsets.UTF_8);
				}
			} catch (Exception ignored) {
				body = null;
			}
		}
		boolean fresh = body == null;
		if (fresh) {
			String q = (cat.query() + " " + (text == null ? "" : text)).trim();
			String facets = "[[\"project_type:resourcepack\"],[\"versions:" + gameVersion() + "\"]]";
			body = get(API + "/search?query=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&facets="
					+ URLEncoder.encode(facets, StandardCharsets.UTF_8) + "&index=" + (newest ? "newest" : "downloads") + "&limit=40&offset=" + offset);
		}
		List<Hit> out = new ArrayList<>();
		try {
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			for (JsonElement e : o.getAsJsonArray("hits")) {
				JsonObject h = e.getAsJsonObject();
				long dl = h.has("downloads") ? h.get("downloads").getAsLong() : 0L;
				out.add(new Hit(str(h, "project_id"), str(h, "title"), str(h, "author"), str(h, "description"), dl, str(h, "icon_url"),
						str(h, "latest_version")));
			}
		} catch (RuntimeException broken) {
			if (!fresh) { // a broken cache file: ask Modrinth
				try {
					Files.deleteIfExists(cf);
				} catch (IOException ignored) {
					// asked again anyway
				}
				return search(cat, text, offset, newest);
			}
			throw new IOException(broken);
		}
		if (fresh && plain) {
			try {
				Files.createDirectories(cf.getParent());
				Files.writeString(cf, body, StandardCharsets.UTF_8);
			} catch (IOException ignored) {
				// no cache this time
			}
		}
		return out;
	}

	private static Choice choiceOf(Hit h, JsonObject version) {
		JsonArray files = version.getAsJsonArray("files");
		if (files == null || files.isEmpty()) {
			return null;
		}
		JsonObject pick = files.get(0).getAsJsonObject();
		for (JsonElement f : files) {
			if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
				pick = f.getAsJsonObject();
			}
		}
		String sha = pick.has("hashes") && pick.getAsJsonObject("hashes").has("sha1") ? str(pick.getAsJsonObject("hashes"), "sha1") : "";
		return new Choice(h.id(), h.title(), h.author(), str(pick, "url"), sha, str(pick, "filename"));
	}

	/**
	 * The download file of MANY packs with ONE request (their newest version), instead of one request per pack. Blocking.
	 * Packs it could not find are simply missing from the result (the caller then asks for those one by one).
	 */
	public static Map<String, Choice> resolveAll(List<Hit> hits) {
		Map<String, Choice> out = new java.util.HashMap<>();
		Map<String, Hit> byVersion = new java.util.HashMap<>();
		StringBuilder ids = new StringBuilder("[");
		for (Hit h : hits) {
			if (h.latest() != null && !h.latest().isBlank() && byVersion.putIfAbsent(h.latest(), h) == null) {
				ids.append(ids.length() > 1 ? "," : "").append('"').append(h.latest()).append('"');
			}
		}
		if (byVersion.isEmpty()) {
			return out;
		}
		try {
			JsonArray arr = JsonParser.parseString(get(API + "/versions?ids=" + URLEncoder.encode(ids.append("]").toString(), StandardCharsets.UTF_8)))
					.getAsJsonArray();
			for (JsonElement e : arr) {
				JsonObject v = e.getAsJsonObject();
				Hit h = byVersion.get(str(v, "id"));
				Choice c = h == null ? null : choiceOf(h, v);
				if (c != null && !c.url().isBlank()) {
					out.put(h.id(), c);
				}
			}
		} catch (Exception e) {
			// one by one, as before
		}
		return out;
	}

	/** Finds the file of the newest version for this game version (blocking). */
	public static Choice resolve(Hit h) throws IOException, InterruptedException {
		String gv = gameVersion();
		String url = API + "/project/" + h.id() + "/version?game_versions=" + URLEncoder.encode("[\"" + gv + "\"]", StandardCharsets.UTF_8);
		JsonArray versions = JsonParser.parseString(get(url)).getAsJsonArray();
		if (versions.isEmpty()) {
			versions = JsonParser.parseString(get(API + "/project/" + h.id() + "/version")).getAsJsonArray();
		}
		if (versions.isEmpty()) {
			throw new IOException("No version");
		}
		JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
		JsonObject pick = files.get(0).getAsJsonObject();
		for (JsonElement f : files) {
			if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
				pick = f.getAsJsonObject();
			}
		}
		String sha = pick.has("hashes") && pick.getAsJsonObject("hashes").has("sha1") ? str(pick.getAsJsonObject("hashes"), "sha1") : "";
		return new Choice(h.id(), h.title(), h.author(), str(pick, "url"), sha, str(pick, "filename"));
	}

	// ------------------------------------------------------------------ storage
	private static Path cfg() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-texture-styles.json");
	}

	private static Path cacheDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-styles-cache");
	}

	/** category id -> choice (insertion order). */
	public static final Map<String, Choice> CHOICES = new LinkedHashMap<>();
	private static boolean loaded;

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.isRegularFile(cfg())) {
				JsonObject o = JsonParser.parseString(Files.readString(cfg(), StandardCharsets.UTF_8)).getAsJsonObject();
				for (String k : o.keySet()) {
					JsonObject c = o.getAsJsonObject(k);
					CHOICES.put(k, new Choice(str(c, "project"), str(c, "title"), str(c, "author"), str(c, "url"), str(c, "sha1"), str(c, "file")));
				}
			}
		} catch (Exception ignored) {
			// broken file: start empty
		}
	}

	public static synchronized void save() {
		try {
			JsonObject o = new JsonObject();
			for (Map.Entry<String, Choice> e : CHOICES.entrySet()) {
				JsonObject c = new JsonObject();
				Choice v = e.getValue();
				c.addProperty("project", v.project());
				c.addProperty("title", v.title());
				c.addProperty("author", v.author());
				c.addProperty("url", v.url());
				c.addProperty("sha1", v.sha1());
				c.addProperty("file", v.file());
				o.add(e.getKey(), c);
			}
			Files.createDirectories(cfg().getParent());
			Files.writeString(cfg(), o.toString(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// best effort
		}
	}

	private static String sha1(Path p) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		try (InputStream in = Files.newInputStream(p)) {
			byte[] b = new byte[65536];
			int n;
			while ((n = in.read(b)) > 0) {
				md.update(b, 0, n);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (byte x : md.digest()) {
			sb.append(String.format("%02x", x));
		}
		return sb.toString();
	}

	/** Downloads (or finds in the cache) the pack of a choice; checks its SHA-1 (blocking). */
	public static Path download(Choice c) throws Exception {
		Files.createDirectories(cacheDir());
		String name = (c.sha1().isEmpty() ? Integer.toHexString(c.url().hashCode()) : c.sha1()) + ".zip";
		Path dest = cacheDir().resolve(name);
		if (Files.isRegularFile(dest) && Files.size(dest) > 0) {
			return dest;
		}
		Path tmp = cacheDir().resolve(name + ".part");
		HttpRequest rq = HttpRequest.newBuilder(URI.create(c.url())).timeout(Duration.ofMinutes(3)).header("User-Agent", UA).GET().build();
		HttpResponse<Path> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofFile(tmp));
		if (r.statusCode() < 200 || r.statusCode() >= 300) {
			Files.deleteIfExists(tmp);
			throw new IOException("HTTP " + r.statusCode());
		}
		if (!c.sha1().isEmpty() && !c.sha1().equalsIgnoreCase(sha1(tmp))) {
			Files.deleteIfExists(tmp);
			throw new IOException("SHA-1 does not match");
		}
		Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
		return dest;
	}

	// ------------------------------------------------------------------ build
	private static final Pattern NS_PATH = Pattern.compile("^(?:minecraft:)?([a-z0-9_./-]+)$");

	/** Reads the "minecraft" assets of a zip (a pack that has its assets inside a sub folder is handled). */
	private static String prefixOf(ZipFile z) {
		var en = z.entries();
		while (en.hasMoreElements()) {
			String n = en.nextElement().getName();
			int i = n.indexOf("assets/minecraft/");
			if (i >= 0) {
				return n.substring(0, i);
			}
		}
		return "";
	}

	private static void collectRefs(JsonElement e, Set<String> out) {
		if (e.isJsonObject()) {
			for (var en : e.getAsJsonObject().entrySet()) {
				collectRefs(en.getValue(), out);
			}
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				collectRefs(x, out);
			}
		} else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			String s = e.getAsString();
			if (s.startsWith("#") || s.startsWith("builtin/") || s.startsWith("minecraft:builtin/") || s.contains(":") && !s.startsWith("minecraft:")) {
				return;
			}
			var m = NS_PATH.matcher(s);
			if (m.matches()) {
				out.add(m.group(1));
			}
		}
	}

	private static byte[] read(ZipFile z, ZipEntry e) throws IOException {
		try (InputStream in = z.getInputStream(e)) {
			return in.readAllBytes();
		}
	}

	private static final java.util.concurrent.ExecutorService FETCH = java.util.concurrent.Executors.newFixedThreadPool(10, r -> {
		Thread t = new Thread(r, "kubo-styles-fetch");
		t.setDaemon(true);
		return t;
	});

	/** Reads several files of a pack at the same time (a pack on the web answers one small request per file). */
	private static Map<String, byte[]> readAll(Src src, List<String> names) {
		Map<String, byte[]> got = new java.util.concurrent.ConcurrentHashMap<>();
		List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
		for (String n : names) {
			jobs.add(FETCH.submit(() -> {
				try {
					byte[] b = src.read(n);
					if (b != null) {
						got.put(n, b);
					}
				} catch (Exception ignored) {
					// that file is left out
				}
			}));
		}
		for (var j : jobs) {
			try {
				j.get();
			} catch (Exception ignored) {
				// same
			}
		}
		return got;
	}

	/** Copies what one category needs from one pack (on disk or on the web) into the output. */
	private static int copyCategory(Src src, Cat cat, Map<String, byte[]> out) throws IOException {
		Set<String> all = new HashSet<>();
		String pre = null;
		for (String n : src.names()) {
			all.add(n);
			if (pre == null) {
				int i = n.indexOf("assets/minecraft/");
				if (i >= 0) {
					pre = n.substring(0, i) + "assets/minecraft/";
				}
			}
		}
		if (pre == null) {
			return 0;
		}
		Pattern rx = Pattern.compile(cat.rx());
		Set<String> seen = new HashSet<>();
		List<String> wave = new ArrayList<>();
		for (String n : all) {
			if (n.startsWith(pre) && rx.matcher(n.substring(pre.length())).matches() && seen.add(n.substring(pre.length()))) {
				wave.add(n);
			}
		}
		int count = 0;
		// models point to other models and textures: bring those too (only if the pack has them), a few rounds deep
		for (int round = 0; round < 6 && !wave.isEmpty() && count < 600; round++) {
			Map<String, byte[]> got = readAll(src, wave);
			List<String> next = new ArrayList<>();
			for (var e : got.entrySet()) {
				String rel = e.getKey().substring(pre.length());
				out.put("assets/minecraft/" + rel, e.getValue());
				count++;
				if (!rel.endsWith(".json")) {
					continue;
				}
				Set<String> refs = new HashSet<>();
				try {
					collectRefs(JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8)), refs);
				} catch (Exception ex) {
					continue;
				}
				for (String r : refs) {
					for (String cand : new String[] {"models/" + r + ".json", "textures/" + r + ".png", "textures/" + r + ".png.mcmeta"}) {
						if (all.contains(pre + cand) && seen.add(cand)) {
							next.add(pre + cand);
						}
					}
				}
			}
			wave = next;
		}
		return count;
	}

	/** Packs on the web that were already opened (their table of contents is kept, so the next read asks only for the file). */
	private static final Map<String, RemoteZip> REMOTE = java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, RemoteZip> e) {
			return size() > 96;
		}
	});

	private static RemoteZip remote(String url) throws IOException, InterruptedException {
		RemoteZip z = REMOTE.get(url);
		if (z == null) {
			z = RemoteZip.open(url, UA);
			REMOTE.put(url, z);
		}
		return z;
	}

	/** The files a category takes from a pack, kept while the game runs: changing one item does not fetch the others again. */
	private static final Map<String, Map<String, byte[]>> EXTRACTED = new java.util.concurrent.ConcurrentHashMap<>();

	private static Map<String, byte[]> extract(Cat cat, Choice c) throws Exception {
		String key = cat.id() + "|" + (c.sha1().isEmpty() ? c.url() : c.sha1());
		Map<String, byte[]> m = EXTRACTED.get(key);
		if (m != null) {
			return m;
		}
		m = new LinkedHashMap<>();
		Path local = cacheDir().resolve((c.sha1().isEmpty() ? Integer.toHexString(c.url().hashCode()) : c.sha1()) + ".zip");
		boolean done = false;
		if (!(Files.isRegularFile(local) && Files.size(local) > 0)) {
			try {
				copyCategory(remote(c.url()), cat, m); // only this item's files are downloaded, not the whole pack
				done = !m.isEmpty();
			} catch (IOException noRanges) {
				m.clear();
			}
		}
		if (!done) {
			try (ZipFile z = new ZipFile(download(c).toFile())) {
				copyCategory(new ZipSrc(z), cat, m);
			}
		}
		if (EXTRACTED.size() > 80) {
			EXTRACTED.clear();
		}
		EXTRACTED.put(key, m);
		return m;
	}

	/** The picture of the category's main item inside a pack (e.g. the totem texture), or null when the pack has none. */
	public static byte[] previewPng(Path zip, Cat cat) throws IOException {
		try (ZipFile z = new ZipFile(zip.toFile())) {
			String pre = prefixOf(z) + "assets/minecraft/";
			Pattern rx = Pattern.compile(cat.rx());
			ZipEntry best = null;
			int bestScore = -1;
			var en = z.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				String n = e.getName();
				if (e.isDirectory() || !n.startsWith(pre) || !n.endsWith(".png")) {
					continue;
				}
				String rel = n.substring(pre.length());
				if (!rx.matcher(rel).matches()) {
					continue;
				}
				String base = rel.substring(rel.lastIndexOf('/') + 1);
				int score = 1;
				if (base.equals(cat.item() + ".png")) {
					score = 100;
				} else if (base.startsWith(cat.item())) {
					score = 50;
				} else if (base.contains(cat.item())) {
					score = 20;
				}
				if (rel.startsWith("textures/item/") || rel.startsWith("textures/block/")) {
					score += 5;
				}
				if (score > bestScore) {
					best = e;
					bestScore = score;
				}
			}
			if (best == null || best.getSize() > 2_000_000) {
				return null;
			}
			return read(z, best);
		}
	}

	// ------------------------------------------------------------------ 3D pictures (fast: only the needed bytes)
	/** Files of a zip, wherever it is (on disk or on the web). */
	interface Src {
		Iterable<String> names();

		long size(String name);

		byte[] read(String name) throws IOException;
	}

	private record ZipSrc(ZipFile z) implements Src {
		@Override
		public Iterable<String> names() {
			List<String> l = new ArrayList<>();
			var en = z.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				if (!e.isDirectory()) {
					l.add(e.getName());
				}
			}
			return l;
		}

		@Override
		public long size(String name) {
			ZipEntry e = z.getEntry(name);
			return e == null ? -1 : e.getSize();
		}

		@Override
		public byte[] read(String name) throws IOException {
			ZipEntry e = z.getEntry(name);
			return e == null ? null : TextureStyles.read(z, e);
		}
	}

	/** Shapes that are drawn in 3D from an entity texture (preferred over the flat item picture). */
	private static boolean model3d(String rel) {
		return rel.startsWith("textures/entity/end_crystal/") || rel.startsWith("textures/entity/shulker/") || rel.startsWith("textures/entity/chest/")
				|| rel.startsWith("textures/entity/equipment/humanoid") || rel.startsWith("textures/entity/shield")
				|| rel.startsWith("textures/entity/equipment/wings/") || rel.startsWith("textures/entity/elytra");
	}

	/** Picks the texture that shows the category best and draws it (3D when it has a shape). Null when the pack has none. */
	static StylePreview3D.Pic preview(Src src, Cat cat, int size) throws IOException {
		String pre = null;
		for (String n : src.names()) {
			int i = n.indexOf("assets/minecraft/");
			if (i >= 0) {
				pre = n.substring(0, i) + "assets/minecraft/";
				break;
			}
		}
		if (pre == null) {
			return null;
		}
		Pattern rx = Pattern.compile(cat.rx());
		String best = null;
		int bestScore = -1;
		Set<String> all = new HashSet<>();
		for (String n : src.names()) {
			all.add(n);
			if (!n.startsWith(pre) || !n.endsWith(".png")) {
				continue;
			}
			String rel = n.substring(pre.length());
			if (!rx.matcher(rel).matches() || rel.contains("/models/") || src.size(n) > 2_000_000) {
				continue;
			}
			String base = rel.substring(rel.lastIndexOf('/') + 1);
			int score = 1;
			if (base.equals(cat.item() + ".png")) {
				score = 100;
			} else if (base.startsWith(cat.item())) {
				score = 50;
			} else if (base.contains(cat.item())) {
				score = 20;
			}
			if (rel.startsWith("textures/item/") || rel.startsWith("textures/block/")) {
				score += 5;
			}
			if (model3d(rel)) {
				score += 150;
				if (base.equals("shulker.png") || base.equals("ender.png") || base.equals("end_crystal.png") || base.equals("shield_base_nopattern.png")) {
					score += 10;
				}
			}
			if (base.contains("_side") && !base.contains("_side0") && rel.startsWith("textures/block/respawn_anchor")) {
				score -= 3; // the empty anchor
			}
			if (base.contains("_top") || base.contains("_bottom")) {
				score -= 2; // a block's side shows it better
			}
			if (score > bestScore) {
				best = n;
				bestScore = score;
			}
		}
		if (best == null) {
			return null;
		}
		StylePreview3D.Tex t = decode(src.read(best));
		if (t == null) {
			return null;
		}
		String rel = best.substring(pre.length());
		StylePreview3D.Tex top = null;
		if (rel.startsWith("textures/block/")) {
			String dir = best.substring(0, best.lastIndexOf('/') + 1), b = best.substring(best.lastIndexOf('/') + 1, best.length() - 4);
			String stem = b.replaceAll("_side\\d*$", "");
			for (String c : new String[]{dir + stem + "_top.png", dir + b + "_top.png"}) {
				if (all.contains(c)) {
					top = decode(src.read(c));
					break;
				}
			}
		}
		return StylePreview3D.of(rel, t, top, size);
	}

	private static StylePreview3D.Tex decode(byte[] png) {
		if (png == null) {
			return null;
		}
		try (com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(png)) {
			int w = img.getWidth(), h = img.getHeight();
			if (w <= 0 || h <= 0 || (long) w * h > 4_000_000L) {
				return null;
			}
			int[] px = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					px[y * w + x] = img.getPixel(x, y);
				}
			}
			return new StylePreview3D.Tex(px, w, h);
		} catch (Exception e) {
			return null;
		}
	}

	/** A finished picture plus the file it came from (null pic = this pack has no texture for the category). */
	public record Preview(Choice choice, StylePreview3D.Pic pic) {
	}

	private static Path previewFile(String project, Cat cat) {
		return cacheDir().resolve("previews").resolve(project.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + cat.id() + ".json");
	}

	/** The picture of a pack for a category: from the disk cache (instant), else read straight from Modrinth. Blocking. */
	public static Preview preview(Hit h, Cat cat, int size) throws Exception {
		return preview(h, cat, size, null);
	}

	/** Same; 'known' = the pack's file when it was already looked up (see {@link #resolveAll}), so no extra request is made. */
	public static Preview preview(Hit h, Cat cat, int size, Choice known) throws Exception {
		Path f = previewFile(h.id(), cat);
		try {
			if (Files.isRegularFile(f) && System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() < 14L * 24 * 3600 * 1000) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				Choice c = new Choice(str(o, "project"), str(o, "title"), str(o, "author"), str(o, "url"), str(o, "sha1"), str(o, "file"));
				if (o.has("none")) {
					return new Preview(c, null);
				}
				int w = o.get("w").getAsInt(), hh = o.get("h").getAsInt();
				if (w == size) {
					byte[] z = java.util.Base64.getDecoder().decode(str(o, "px"));
					java.util.zip.Inflater inf = new java.util.zip.Inflater();
					inf.setInput(z);
					byte[] raw = new byte[w * hh * 4];
					int n = 0;
					while (n < raw.length && !inf.finished()) {
						int k = inf.inflate(raw, n, raw.length - n);
						if (k == 0 && inf.needsInput()) {
							break;
						}
						n += k;
					}
					inf.end();
					if (n == raw.length) {
						int[] px = new int[w * hh];
						java.nio.ByteBuffer.wrap(raw).asIntBuffer().get(px);
						return new Preview(c, new StylePreview3D.Pic(px, w, hh));
					}
				}
			}
		} catch (Exception ignored) {
			// broken cache file: make it again
		}
		Choice c = known != null ? known : resolve(h);
		StylePreview3D.Pic pic;
		Path local = cacheDir().resolve((c.sha1().isEmpty() ? Integer.toHexString(c.url().hashCode()) : c.sha1()) + ".zip");
		if (Files.isRegularFile(local) && Files.size(local) > 0) {
			try (ZipFile z = new ZipFile(local.toFile())) {
				pic = preview(new ZipSrc(z), cat, size);
			}
		} else {
			StylePreview3D.Pic p2;
			try {
				p2 = preview(remote(c.url()), cat, size); // only the table of contents + one or two textures
			} catch (IOException noRanges) {
				try (ZipFile z = new ZipFile(download(c).toFile())) { // server without ranges: whole file, as before
					p2 = preview(new ZipSrc(z), cat, size);
				}
			}
			pic = p2;
		}
		try {
			JsonObject o = new JsonObject();
			o.addProperty("project", c.project());
			o.addProperty("title", c.title());
			o.addProperty("author", c.author());
			o.addProperty("url", c.url());
			o.addProperty("sha1", c.sha1());
			o.addProperty("file", c.file());
			if (pic == null) {
				o.addProperty("none", true);
			} else {
				java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(pic.px().length * 4);
				bb.asIntBuffer().put(pic.px());
				java.util.zip.Deflater d = new java.util.zip.Deflater(6);
				d.setInput(bb.array());
				d.finish();
				java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				while (!d.finished()) {
					bo.write(buf, 0, d.deflate(buf));
				}
				d.end();
				o.addProperty("w", pic.w());
				o.addProperty("h", pic.h());
				o.addProperty("px", java.util.Base64.getEncoder().encodeToString(bo.toByteArray()));
			}
			Files.createDirectories(f.getParent());
			Files.writeString(f, o.toString(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			// no cache this time
		}
		return new Preview(c, pic);
	}

	/** Result of {@link #buildPack()}. */
	public record Built(String packFile, Map<String, Integer> files) {
	}

	/** Builds the single generated pack from all choices (blocking). Returns null when there are no choices. */
	public static Built buildPack() throws Exception {
		Path rp = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
		Files.createDirectories(rp);
		if (CHOICES.isEmpty()) {
			return null;
		}
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.update("fmt2".getBytes(StandardCharsets.UTF_8)); // packs made before this had a pack.mcmeta the game refused: new name = made again
		for (var e : new ArrayList<>(CHOICES.entrySet())) {
			md.update((e.getKey() + e.getValue().sha1() + e.getValue().url()).getBytes(StandardCharsets.UTF_8));
		}
		StringBuilder h = new StringBuilder();
		for (byte b : md.digest()) {
			h.append(String.format("%02x", b));
		}
		String file = "kubo-styles-" + h.substring(0, 10) + ".zip";
		Path dest = rp.resolve(file);
		Map<String, Integer> counts = new LinkedHashMap<>();
		if (Files.isRegularFile(dest) && Files.size(dest) > 0) {
			return new Built(file, counts); // same choices as before: the pack is already there (and may be in use, so never rewritten)
		}
		Map<String, byte[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, Choice> e : new ArrayList<>(CHOICES.entrySet())) {
			Cat cat = cat(e.getKey());
			if (cat == null) {
				continue;
			}
			Map<String, byte[]> files = extract(cat, e.getValue());
			out.putAll(files);
			counts.put(cat.id(), files.size());
		}
		Path tmp = rp.resolve(file + ".part");
		try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zos = new ZipOutputStream(os)) {
			zos.setLevel(1); // fast to write; the game reads it from this computer
			zos.putNextEntry(new ZipEntry("pack.mcmeta"));
			zos.write(CustomFont.mcmeta("Texture Styles").replace("Caos custom font: Texture Styles", "Caos Texture Styles").getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			for (var e : out.entrySet()) {
				zos.putNextEntry(new ZipEntry(e.getKey()));
				zos.write(e.getValue());
				zos.closeEntry();
			}
		}
		Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
		return new Built(file, counts);
	}

	/** Deletes generated packs (kubo-styles-*.zip) from resourcepacks/, except {@code keep}. A pack still in use (Windows locks it) stays until next time. */
	public static void removeGenerated(String keep) {
		try (var s = Files.list(FabricLoader.getInstance().getGameDir().resolve("resourcepacks"))) {
			for (Path p : (Iterable<Path>) s::iterator) {
				String n = p.getFileName().toString();
				if (n.startsWith("kubo-styles-") && n.endsWith(".zip") && !n.equals(keep)) {
					try {
						Files.deleteIfExists(p);
					} catch (IOException locked) {
						// in use: removed on a later run
					}
				}
			}
		} catch (Exception ignored) {
			// nothing to remove
		}
	}

	/** Makes the game see / use the generated pack and reloads. Call on the game thread. */
	public static void activate(String packFileOrNull) {
		Minecraft mc = Minecraft.getInstance();
		var pm = mc.getResourcePackRepository();
		pm.reload();
		List<String> on = new ArrayList<>(pm.getSelectedIds());
		on.removeIf(id -> id.startsWith("file/kubo-styles-"));
		if (packFileOrNull != null && pm.getAvailableIds().contains("file/" + packFileOrNull)) {
			on.add("file/" + packFileOrNull); // last = on top
		}
		pm.setSelected(on);
		mc.options.updateResourcePacks(pm); // saves the list and reloads
	}

	/** Names of the chosen items for the GUI. */
	public static String chosenTitle(String catId) {
		Choice c = CHOICES.get(catId);
		return c == null ? null : c.title();
	}
}
