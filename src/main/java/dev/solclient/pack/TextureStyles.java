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
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Texture Styles engine. The player picks a community resource pack (Modrinth) PER ITEM / category; this class
 * searches Modrinth, downloads the packs (SHA-1 checked, cached), and builds ONE generated pack
 * (resourcepacks/kubo-styles-HASH.zip) that holds only the files of the chosen categories (plus the models and
 * textures those files point to). The original packs are never changed and nothing is bundled in Sleep.
 */
public final class TextureStyles {

	private TextureStyles() {
	}

	/** A category: id, English name, Portuguese name, Modrinth search words, regex of the files (relative to assets/minecraft/), icon item. */
	public record Cat(String id, String en, String pt, String query, String rx, String item) {
		public ItemStack icon() {
			try {
				return new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", item)));
			} catch (Throwable t) {
				return ItemStack.EMPTY;
			}
		}
	}

	/** One search result. */
	public record Hit(String id, String title, String author, String desc, long downloads, String icon) {
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
	private static final String UA = "Sleep-Client/1.0 (Minecraft client mod)";
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

	/** Searches Modrinth resource packs for this category (blocking: call it from a thread). */
	public static List<Hit> search(Cat cat, String text, int offset, boolean newest) throws IOException, InterruptedException {
		String q = (cat.query() + " " + (text == null ? "" : text)).trim();
		String facets = "[[\"project_type:resourcepack\"],[\"versions:" + gameVersion() + "\"]]";
		String url = API + "/search?query=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&facets="
				+ URLEncoder.encode(facets, StandardCharsets.UTF_8) + "&index=" + (newest ? "newest" : "downloads") + "&limit=40&offset=" + offset;
		JsonObject o = JsonParser.parseString(get(url)).getAsJsonObject();
		List<Hit> out = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("hits")) {
			JsonObject h = e.getAsJsonObject();
			long dl = h.has("downloads") ? h.get("downloads").getAsLong() : 0L;
			out.add(new Hit(str(h, "project_id"), str(h, "title"), str(h, "author"), str(h, "description"), dl, str(h, "icon_url")));
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

	/** Copies what one category needs from one pack into the output. */
	private static int copyCategory(ZipFile z, Cat cat, Map<String, byte[]> out) throws IOException {
		String pre = prefixOf(z) + "assets/minecraft/";
		Pattern rx = Pattern.compile(cat.rx());
		int count = 0;
		List<String> queue = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		var en = z.entries();
		while (en.hasMoreElements()) {
			ZipEntry e = en.nextElement();
			String n = e.getName();
			if (e.isDirectory() || !n.startsWith(pre)) {
				continue;
			}
			String rel = n.substring(pre.length());
			if (rx.matcher(rel).matches()) {
				out.put("assets/minecraft/" + rel, read(z, e));
				seen.add(rel);
				count++;
				if (rel.endsWith(".json")) {
					queue.add(rel);
				}
			}
		}
		// models point to other models and textures: bring those too (only if the pack has them)
		for (int i = 0; i < queue.size() && i < 400; i++) {
			String rel = queue.get(i);
			ZipEntry e = z.getEntry(pre + rel);
			if (e == null) {
				continue;
			}
			Set<String> refs = new HashSet<>();
			try {
				collectRefs(JsonParser.parseString(new String(read(z, e), StandardCharsets.UTF_8)), refs);
			} catch (Exception ex) {
				continue;
			}
			for (String r : refs) {
				for (String cand : new String[] {"models/" + r + ".json", "textures/" + r + ".png", "textures/" + r + ".png.mcmeta"}) {
					if (seen.contains(cand)) {
						continue;
					}
					ZipEntry f = z.getEntry(pre + cand);
					if (f != null) {
						out.put("assets/minecraft/" + cand, read(z, f));
						seen.add(cand);
						count++;
						if (cand.endsWith(".json")) {
							queue.add(cand);
						}
					}
				}
			}
		}
		return count;
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
		Map<String, byte[]> out = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (Map.Entry<String, Choice> e : new ArrayList<>(CHOICES.entrySet())) {
			Cat cat = cat(e.getKey());
			if (cat == null) {
				continue;
			}
			Path p = download(e.getValue());
			try (ZipFile z = new ZipFile(p.toFile())) {
				counts.put(cat.id(), copyCategory(z, cat, out));
			}
		}
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		for (var e : CHOICES.entrySet()) {
			md.update((e.getKey() + e.getValue().sha1() + e.getValue().url()).getBytes(StandardCharsets.UTF_8));
		}
		StringBuilder h = new StringBuilder();
		for (byte b : md.digest()) {
			h.append(String.format("%02x", b));
		}
		String file = "kubo-styles-" + h.substring(0, 10) + ".zip";
		Path dest = rp.resolve(file);
		if (Files.isRegularFile(dest)) {
			return new Built(file, counts); // same choices as before: the pack is already there (and may be in use, so never rewritten)
		}
		try (OutputStream os = Files.newOutputStream(dest); ZipOutputStream zos = new ZipOutputStream(os)) {
			zos.putNextEntry(new ZipEntry("pack.mcmeta"));
			zos.write(CustomFont.mcmeta("Texture Styles").replace("Sleep custom font: Texture Styles", "Sleep Texture Styles").getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			for (var e : out.entrySet()) {
				zos.putNextEntry(new ZipEntry(e.getKey()));
				zos.write(e.getValue());
				zos.closeEntry();
			}
		}
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
		MinecraftClient mc = MinecraftClient.getInstance();
		var pm = mc.getResourcePackManager();
		pm.scanPacks();
		List<String> on = new ArrayList<>(pm.getEnabledIds());
		on.removeIf(id -> id.startsWith("file/kubo-styles-"));
		if (packFileOrNull != null && pm.getIds().contains("file/" + packFileOrNull)) {
			on.add("file/" + packFileOrNull); // last = on top
		}
		pm.setEnabledProfiles(on);
		mc.options.refreshResourcePacks(pm); // saves the list and reloads
	}

	/** Names of the chosen items for the GUI. */
	public static String chosenTitle(String catId) {
		Choice c = CHOICES.get(catId);
		return c == null ? null : c.title();
	}
}
