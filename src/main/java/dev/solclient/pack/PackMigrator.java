package dev.solclient.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Brings an old resource pack up to the version of the game that is running.
 *
 * Minecraft moves and renames textures from version to version (blocks/ became block/, armour moved to
 * entity/equipment, animals got variants, files went into sub-folders...). This compares every file of the pack with the
 * game's own asset list and, for every file the game no longer knows, looks for where it lives now:
 *  1. a table of known renames (always checked against the game's own list, so a wrong entry can never do harm);
 *  2. the same file name somewhere else in the same asset type, when the old and the new folder are related
 *     (one inside the other, or the same last folder name).
 * Textures that moved are also fixed inside the pack's own model / blockstate JSON files. pack.mcmeta gets the game's
 * pack format. The original pack is never touched: the result is a new zip next to it.
 *
 * Written from scratch, no third-party code.
 */
public final class PackMigrator {

	private PackMigrator() {
	}

	public static final class Result {
		/** "old/path -> new/path" for every file that was moved. */
		public final List<String> moved = new ArrayList<>();
		/** Files the game does not have (and that could not be matched): left as they are. */
		public final List<String> unmatched = new ArrayList<>();
		/** Files that could be several things: left as they are. */
		public final List<String> ambiguous = new ArrayList<>();
		public final List<String> notes = new ArrayList<>();
		public int unchanged;
		public int jsonFixed;
		public boolean formatUpdated;
		public String formatInfo = "";
		public boolean dryRun;
		public Path output;
		public Path report;
		public String error;
	}

	// ------------------------------------------------------------------------------------------ the game's own assets
	private static Set<String> vanilla;
	private static Map<String, List<String>> vanillaByBase;
	private static JsonObject vanillaMeta;

	private static synchronized void loadVanilla() throws IOException {
		if (vanilla != null) {
			return;
		}
		Set<String> set = new HashSet<>();
		JsonObject meta = null;
		ModContainer mc = FabricLoader.getInstance().getModContainer("minecraft").orElse(null);
		if (mc == null) {
			throw new IOException("Could not find the game files");
		}
		for (Path root : mc.getRootPaths()) {
			Path assets = root.resolve("assets").resolve("minecraft");
			if (Files.isDirectory(assets)) {
				try (Stream<Path> s = Files.walk(assets)) {
					s.filter(Files::isRegularFile).forEach(p -> set.add(assets.relativize(p).toString().replace('\\', '/')));
				}
			}
			Path pm = root.resolve("pack.mcmeta");
			if (meta == null && Files.isRegularFile(pm)) {
				try {
					meta = JsonParser.parseString(Files.readString(pm, StandardCharsets.UTF_8)).getAsJsonObject();
				} catch (Exception ignored) {
				}
			}
		}
		if (set.isEmpty()) {
			throw new IOException("Could not read the game's own assets");
		}
		Map<String, List<String>> by = new HashMap<>();
		for (String p : set) {
			if (p.endsWith(".png") || p.endsWith(".json")) {
				by.computeIfAbsent(topOf(p) + "|" + baseOf(p), k -> new ArrayList<>()).add(p);
			}
		}
		vanillaByBase = by;
		vanillaMeta = meta;
		vanilla = set;
	}

	private static String topOf(String p) {
		int i = p.indexOf('/');
		return i < 0 ? "" : p.substring(0, i);
	}

	private static String baseOf(String p) {
		return p.substring(p.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
	}

	private static String dirOf(String p) {
		int i = p.lastIndexOf('/');
		return i < 0 ? "" : p.substring(0, i);
	}

	// ------------------------------------------------------------------------------------------ the pack
	private interface Source extends AutoCloseable {
		List<String> names() throws IOException;

		InputStream open(String name) throws IOException;

		@Override
		void close() throws IOException;
	}

	private static final class ZipSource implements Source {
		private final ZipFile zip;

		ZipSource(Path p) throws IOException {
			zip = new ZipFile(p.toFile());
		}

		@Override
		public List<String> names() {
			List<String> out = new ArrayList<>();
			var en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				if (!e.isDirectory()) {
					out.add(e.getName().replace('\\', '/'));
				}
			}
			return out;
		}

		@Override
		public InputStream open(String name) throws IOException {
			ZipEntry e = zip.getEntry(name);
			if (e == null) {
				throw new IOException("Missing " + name);
			}
			return zip.getInputStream(e);
		}

		@Override
		public void close() throws IOException {
			zip.close();
		}
	}

	private static final class DirSource implements Source {
		private final Path root;

		DirSource(Path p) {
			root = p;
		}

		@Override
		public List<String> names() throws IOException {
			List<String> out = new ArrayList<>();
			try (Stream<Path> s = Files.walk(root)) {
				s.filter(Files::isRegularFile).forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
			}
			return out;
		}

		@Override
		public InputStream open(String name) throws IOException {
			return Files.newInputStream(root.resolve(name));
		}

		@Override
		public void close() {
		}
	}

	/** True when the path looks like a resource pack (a zip, or a folder with pack.mcmeta / assets inside). */
	public static boolean looksLikePack(Path p) {
		try {
			String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
			if (Files.isDirectory(p)) {
				return Files.exists(p.resolve("pack.mcmeta")) || Files.isDirectory(p.resolve("assets"));
			}
			return Files.isRegularFile(p) && n.endsWith(".zip");
		} catch (Exception e) {
			return false;
		}
	}

	// ------------------------------------------------------------------------------------------ rename knowledge
	private static final Map<String, String> NAMES = new HashMap<>();
	private static final Pattern ARMOR = Pattern.compile("textures/models/armor/([a-z]+)_layer_([12])(_overlay)?\\.png");

	static {
		String[] woodOld = {"oak", "spruce", "birch", "jungle", "acacia", "big_oak"};
		String[] woodNew = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
		for (int i = 0; i < woodOld.length; i++) {
			String o = woodOld[i], n = woodNew[i];
			NAMES.put("planks_" + o, n + "_planks");
			NAMES.put("log_" + o, n + "_log");
			NAMES.put("log_" + o + "_top", n + "_log_top");
			NAMES.put("leaves_" + o, n + "_leaves");
			NAMES.put("sapling_" + o, n + "_sapling");
			NAMES.put("door_" + o + "_upper", n + "_door_top");
			NAMES.put("door_" + o + "_lower", n + "_door_bottom");
			NAMES.put("door_" + o, n + "_door");
			NAMES.put("boat_" + o, n + "_boat");
		}
		String[] colOld = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple",
				"blue", "brown", "green", "red", "black"};
		String[] colNew = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
				"purple", "blue", "brown", "green", "red", "black"};
		for (int i = 0; i < colOld.length; i++) {
			String o = colOld[i], n = colNew[i];
			NAMES.put("wool_colored_" + o, n + "_wool");
			NAMES.put("glass_" + o, n + "_stained_glass");
			NAMES.put("glass_pane_top_" + o, n + "_stained_glass_pane_top");
			NAMES.put("hardened_clay_stained_" + o, n + "_terracotta");
			NAMES.put("dye_powder_" + o, n + "_dye");
		}
		NAMES.put("dye_powder_black", "ink_sac");
		NAMES.put("dye_powder_brown", "cocoa_beans");
		NAMES.put("dye_powder_blue", "lapis_lazuli");
		NAMES.put("dye_powder_white", "bone_meal");
		for (String t : new String[] {"sword", "pickaxe", "axe", "shovel", "hoe"}) {
			NAMES.put("wood_" + t, "wooden_" + t);
			NAMES.put("gold_" + t, "golden_" + t);
		}
		for (String t : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
			NAMES.put("gold_" + t, "golden_" + t);
		}
		for (String d : new String[] {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait"}) {
			NAMES.put("record_" + d, "music_disc_" + d);
		}
		for (String s : new String[] {"granite", "diorite", "andesite"}) {
			NAMES.put("stone_" + s, s);
			NAMES.put("stone_" + s + "_smooth", "polished_" + s);
		}
		String[] pairs = {
				// blocks
				"grass_top", "grass_block_top", "grass_side", "grass_block_side", "grass_side_overlay", "grass_block_side_overlay",
				"grass_side_snowed", "grass_block_snow", "brick", "bricks", "cobblestone_mossy", "mossy_cobblestone",
				"stonebrick", "stone_bricks", "stonebrick_mossy", "mossy_stone_bricks", "stonebrick_cracked", "cracked_stone_bricks",
				"stonebrick_carved", "chiseled_stone_bricks", "sandstone_normal", "sandstone", "sandstone_carved", "chiseled_sandstone",
				"sandstone_smooth", "cut_sandstone", "red_sandstone_normal", "red_sandstone", "ice_packed", "packed_ice",
				"pumpkin_face_off", "carved_pumpkin", "pumpkin_face_on", "jack_o_lantern", "dirt_podzol_top", "podzol_top",
				"dirt_podzol_side", "podzol_side", "nether_brick", "nether_bricks", "red_nether_brick", "red_nether_bricks",
				"end_bricks", "end_stone_bricks", "farmland_wet", "farmland_moist", "waterlily", "lily_pad", "reeds", "sugar_cane",
				"tallgrass", "short_grass", "deadbush", "dead_bush", "double_plant_grass_top", "tall_grass_top",
				"double_plant_grass_bottom", "tall_grass_bottom", "flower_dandelion", "dandelion", "flower_rose", "poppy",
				"mushroom_block_skin_brown", "brown_mushroom_block", "mushroom_block_skin_red", "red_mushroom_block",
				"noteblock", "note_block", "web", "cobweb", "sponge_wet", "wet_sponge", "furnace_front_off", "furnace_front",
				"dispenser_front_horizontal", "dispenser_front", "piston_top_normal", "piston_top", "rail_normal", "rail",
				"rail_golden", "powered_rail", "rail_golden_powered", "powered_rail_on", "rail_detector", "detector_rail",
				"rail_activator", "activator_rail", "prismarine_rough", "prismarine", "prismarine_dark", "dark_prismarine",
				"trapdoor", "oak_trapdoor", "grass_path_top", "dirt_path_top", "grass_path_side", "dirt_path_side",
				"redstone_lamp_off", "redstone_lamp", "quartz_ore", "nether_quartz_ore", "lit_furnace", "furnace_front_on",
				// items
				"apple_golden", "golden_apple", "bow_standby", "bow", "bucket_water", "water_bucket", "bucket_lava", "lava_bucket",
				"bucket_milk", "milk_bucket", "fishing_rod_uncast", "fishing_rod", "carrot_golden", "golden_carrot",
				"melon_speckled", "glistering_melon_slice", "melon", "melon_slice", "potato_baked", "baked_potato",
				"potato_poisonous", "poisonous_potato", "beef_cooked", "cooked_beef", "beef_raw", "beef", "porkchop_cooked",
				"cooked_porkchop", "porkchop_raw", "porkchop", "chicken_cooked", "cooked_chicken", "chicken_raw", "chicken",
				"mutton_cooked", "cooked_mutton", "mutton_raw", "mutton", "rabbit_cooked", "cooked_rabbit", "rabbit_raw", "rabbit",
				"fish_cod_cooked", "cooked_cod", "fish_cod_raw", "cod", "fish_salmon_cooked", "cooked_salmon", "fish_salmon_raw",
				"salmon", "fish_clownfish_raw", "tropical_fish", "fish_pufferfish_raw", "pufferfish", "redstone_dust", "redstone",
				"netherbrick", "nether_brick", "book_enchanted", "enchanted_book", "book_normal", "book", "book_writable",
				"writable_book", "book_written", "written_book", "boat", "oak_boat", "sign", "oak_sign", "door_wood", "oak_door",
				"door_iron", "iron_door", "bed", "red_bed", "fireball", "fire_charge", "fireworks_charge", "firework_star",
				"minecart_normal", "minecart", "minecart_chest", "chest_minecart", "minecart_furnace", "furnace_minecart",
				"minecart_hopper", "hopper_minecart", "minecart_tnt", "tnt_minecart", "minecart_command_block",
				"command_block_minecart", "map_empty", "map", "map_filled", "filled_map", "totem", "totem_of_undying",
				"gold_horse_armor", "golden_horse_armor", "seeds_wheat", "wheat_seeds", "seeds_melon", "melon_seeds",
				"seeds_pumpkin", "pumpkin_seeds", "wheat", "wheat", "item_frame", "item_frame", "speckled_melon", "glistering_melon_slice"};
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			NAMES.putIfAbsent(pairs[i], pairs[i + 1]);
		}
	}

	private static List<String> nameCandidates(String n) {
		List<String> o = new ArrayList<>();
		String t = NAMES.get(n);
		if (t != null) {
			o.add(t);
		}
		return o;
	}

	/** Every place the game could have put this file now (each one is checked against the game's list later). */
	private static List<String> candidates(String rest) {
		List<String> out = new ArrayList<>();
		String d = rest.replace("textures/blocks/", "textures/block/").replace("textures/items/", "textures/item/")
				.replace("models/blocks/", "models/block/").replace("models/items/", "models/item/");
		if (!d.equals(rest)) {
			out.add(d);
		}
		Matcher m = ARMOR.matcher(rest);
		if (m.matches()) {
			String mat = m.group(1);
			if (mat.equals("turtle")) {
				mat = "turtle_scute";
			}
			out.add("textures/entity/equipment/humanoid" + (m.group(2).equals("2") ? "_leggings" : "") + "/" + mat
					+ (m.group(3) != null ? "_overlay" : "") + ".png");
		}
		if (rest.equals("textures/entity/steve.png")) {
			out.add("textures/entity/player/wide/steve.png");
		} else if (rest.equals("textures/entity/alex.png")) {
			out.add("textures/entity/player/slim/alex.png");
		}
		for (String a : new String[] {"cow", "pig", "chicken"}) {
			if (rest.equals("textures/entity/" + a + ".png") || rest.equals("textures/entity/" + a + "/" + a + ".png")) {
				out.add("textures/entity/" + a + "/temperate_" + a + ".png");
			}
		}
		String dir = dirOf(d);
		boolean block = dir.equals("textures/block") || dir.equals("models/block");
		boolean item = dir.equals("textures/item") || dir.equals("models/item");
		if (block || item || dir.equals("blockstates")) {
			String file = d.substring(d.lastIndexOf('/') + 1);
			int dot = file.lastIndexOf('.');
			String stem = dot < 0 ? file : file.substring(0, dot), ext = dot < 0 ? "" : file.substring(dot);
			for (String nn : nameCandidates(stem)) {
				out.add(dir + "/" + nn + ext);
				if (block) {
					out.add(dir.replace("block", "item") + "/" + nn + ext);
				} else if (item) {
					out.add(dir.replace("item", "block") + "/" + nn + ext);
				}
			}
		}
		return out;
	}

	private static boolean related(String oldDir, String newDir) {
		String a = oldDir.replace("/blocks", "/block").replace("/items", "/item");
		if (a.equals(newDir) || a.startsWith(newDir + "/") || newDir.startsWith(a + "/")) {
			return true;
		}
		return a.substring(a.lastIndexOf('/') + 1).equals(newDir.substring(newDir.lastIndexOf('/') + 1));
	}

	private static int common(String a, String b) {
		String[] x = a.split("/"), y = b.split("/");
		int n = 0;
		while (n < x.length && n < y.length && x[x.length - 1 - n].equals(y[y.length - 1 - n])) {
			n++;
		}
		return n;
	}

	/** Where does this file live now? null = unknown. {@code amb[0]} is set when it could be several places. */
	private static String resolve(String rest, boolean[] amb) {
		for (String c : candidates(rest)) {
			if (vanilla.contains(c)) {
				return c;
			}
		}
		List<String> list = vanillaByBase.get(topOf(rest) + "|" + baseOf(rest));
		if (list == null) {
			return null;
		}
		String od = dirOf(rest);
		int best = -1;
		String pick = null;
		boolean tie = false;
		for (String cand : list) {
			if (!related(od, dirOf(cand))) {
				continue;
			}
			int sc = common(od, dirOf(cand));
			if (sc > best) {
				best = sc;
				pick = cand;
				tie = false;
			} else if (sc == best) {
				tie = true;
			}
		}
		if (pick == null) {
			return null;
		}
		if (tie) {
			amb[0] = true;
			return null;
		}
		return pick;
	}

	private static boolean handled(String rest) {
		String top = topOf(rest);
		boolean kind = top.equals("textures") || top.equals("models") || top.equals("blockstates") || top.equals("items")
				|| top.equals("equipment") || top.equals("atlases") || top.equals("particles") || top.equals("font");
		return kind && (rest.endsWith(".png") || rest.endsWith(".json"));
	}

	// ------------------------------------------------------------------------------------------ pack.mcmeta
	private static String describe(JsonObject pack) {
		StringBuilder sb = new StringBuilder();
		for (String k : new String[] {"pack_format", "min_format", "max_format", "supported_formats"}) {
			if (pack.has(k)) {
				sb.append(sb.length() == 0 ? "" : ", ").append(k).append(' ').append(pack.get(k));
			}
		}
		return sb.length() == 0 ? "none" : sb.toString();
	}

	private static String updatedMcmeta(String oldText, Result r) {
		JsonObject root;
		try {
			root = oldText == null ? new JsonObject() : JsonParser.parseString(oldText).getAsJsonObject();
		} catch (Exception e) {
			root = new JsonObject();
		}
		JsonObject pack = root.has("pack") && root.get("pack").isJsonObject() ? root.getAsJsonObject("pack") : new JsonObject();
		JsonObject v = vanillaMeta != null && vanillaMeta.has("pack") && vanillaMeta.get("pack").isJsonObject()
				? vanillaMeta.getAsJsonObject("pack")
				: null;
		if (v == null) {
			r.notes.add("Could not read the game's pack format: pack.mcmeta left as it was");
			return null;
		}
		String before = describe(pack);
		String[] keys = {"pack_format", "min_format", "max_format", "supported_formats"};
		for (String k : keys) {
			pack.remove(k);
		}
		for (String k : keys) {
			if (v.has(k)) {
				pack.add(k, v.get(k).deepCopy());
			}
		}
		if (!pack.has("description")) {
			pack.addProperty("description", "");
		}
		root.add("pack", pack);
		String after = describe(pack);
		r.formatInfo = before + "  >  " + after;
		r.formatUpdated = !before.equals(after);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	// ------------------------------------------------------------------------------------------ the job
	private static String strip(String s, String ext) {
		return s.endsWith(ext) ? s.substring(0, s.length() - ext.length()) : s;
	}

	/** Checks (dryRun) or checks and writes the updated copy next to the pack. Slow: run it off the game thread. */
	public static Result run(Path pack, boolean dryRun) {
		Result r = new Result();
		r.dryRun = dryRun;
		try {
			loadVanilla();
			try (Source src = Files.isDirectory(pack) ? new DirSource(pack) : new ZipSource(pack)) {
				List<String> all = src.names();
				// the pack may sit inside a folder of the zip (Pack/pack.mcmeta): that never works in Minecraft
				String root = "";
				if (!all.contains("pack.mcmeta")) {
					String best = null;
					for (String n : all) {
						if (n.endsWith("/pack.mcmeta") && (best == null || n.length() < best.length())) {
							best = n;
						}
					}
					if (best != null) {
						root = best.substring(0, best.length() - "pack.mcmeta".length());
						r.notes.add("The pack was inside the folder \"" + root + "\": moved to the top level");
					}
				}
				Map<String, String> rel = new LinkedHashMap<>(); // original name -> name without the pack root
				Set<String> relSet = new HashSet<>();
				for (String n : all) {
					if (n.startsWith(root)) {
						rel.put(n, n.substring(root.length()));
						relSet.add(n.substring(root.length()));
					}
				}
				final String rootF = root;

				// 1) decide what moves
				Map<String, String> move = new HashMap<>(); // "textures/a/b.png" -> new "textures/c/b.png" (without assets/minecraft/)
				Set<String> taken = new HashSet<>();
				List<String> unmatchedTex = new ArrayList<>();
				for (String n : rel.values()) {
					if (!n.startsWith("assets/minecraft/")) {
						continue;
					}
					String rest = n.substring("assets/minecraft/".length());
					if (!handled(rest)) {
						continue;
					}
					if (vanilla.contains(rest)) {
						r.unchanged++;
						continue;
					}
					boolean[] amb = new boolean[1];
					String to = resolve(rest, amb);
					if (to != null && !relSet.contains("assets/minecraft/" + to) && taken.add(to)) {
						move.put(rest, to);
						r.moved.add(rest + "  >  " + to);
					} else if (amb[0]) {
						r.ambiguous.add(rest);
					} else if (to == null && rest.startsWith("textures/")) {
						unmatchedTex.add(rest);
					}
				}
				Collections.sort(r.moved);
				Collections.sort(r.ambiguous);
				Collections.sort(unmatchedTex);
				r.unmatched.addAll(unmatchedTex);

				// references inside the pack's own JSON (models, blockstates, items)
				Map<String, String> refs = new LinkedHashMap<>();
				for (Map.Entry<String, String> e : move.entrySet()) {
					String o = e.getKey(), nw = e.getValue();
					if (o.endsWith(".png") && nw.endsWith(".png") && o.startsWith("textures/") && nw.startsWith("textures/")) {
						refs.putIfAbsent(strip(o.substring(9), ".png"), strip(nw.substring(9), ".png"));
					} else if (o.endsWith(".json") && nw.endsWith(".json") && o.startsWith("models/") && nw.startsWith("models/")) {
						refs.putIfAbsent(strip(o.substring(7), ".json"), strip(nw.substring(7), ".json"));
					}
				}

				// pack.mcmeta
				String oldMeta = null;
				for (Map.Entry<String, String> e : rel.entrySet()) {
					if (e.getValue().equals("pack.mcmeta")) {
						try (InputStream in = src.open(e.getKey())) {
							oldMeta = new String(in.readAllBytes(), StandardCharsets.UTF_8);
						}
					}
				}
				String newMeta = updatedMcmeta(oldMeta, r);

				if (dryRun) {
					// count the JSON files that would change
					for (Map.Entry<String, String> e : rel.entrySet()) {
						if (!refs.isEmpty() && e.getValue().endsWith(".json") && e.getValue().startsWith("assets/")) {
							try (InputStream in = src.open(e.getKey())) {
								String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
								if (!fixRefs(s, refs).equals(s)) {
									r.jsonFixed++;
								}
							}
						}
					}
					writeReport(pack, r);
					return r;
				}

				// 2) write the new zip
				String name = pack.getFileName().toString();
				String base = name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
				Path dir = pack.toAbsolutePath().getParent();
				Path out = dir.resolve(base + " (updated).zip");
				for (int i = 2; Files.exists(out); i++) {
					out = dir.resolve(base + " (updated " + i + ").zip");
				}
				Set<String> written = new HashSet<>();
				try (OutputStream os = Files.newOutputStream(out); ZipOutputStream zo = new ZipOutputStream(os)) {
					if (newMeta != null) {
						put(zo, "pack.mcmeta", newMeta.getBytes(StandardCharsets.UTF_8));
						written.add("pack.mcmeta");
					}
					for (Map.Entry<String, String> e : rel.entrySet()) {
						String relName = e.getValue();
						String outName = relName;
						if (relName.equals("pack.mcmeta") && newMeta != null) {
							continue;
						}
						if (relName.startsWith("assets/minecraft/")) {
							String rest = relName.substring("assets/minecraft/".length());
							String mv = move.get(rest);
							if (mv == null && rest.endsWith(".png.mcmeta")) {
								String m2 = move.get(strip(rest, ".mcmeta"));
								if (m2 != null) {
									mv = m2 + ".mcmeta";
								}
							}
							if (mv != null) {
								outName = "assets/minecraft/" + mv;
							}
						}
						if (!written.add(outName)) {
							continue;
						}
						try (InputStream in = src.open(e.getKey())) {
							byte[] data = in.readAllBytes();
							if (!refs.isEmpty() && outName.endsWith(".json") && outName.startsWith("assets/")) {
								String s = new String(data, StandardCharsets.UTF_8);
								String f = fixRefs(s, refs);
								if (!f.equals(s)) {
									data = f.getBytes(StandardCharsets.UTF_8);
									r.jsonFixed++;
								}
							}
							put(zo, outName, data);
						}
					}
					if (newMeta == null && !written.contains("pack.mcmeta")) {
						put(zo, "pack.mcmeta", ("{\"pack\":{\"description\":\"" + base.replace("\"", "'") + "\"}}")
								.getBytes(StandardCharsets.UTF_8));
					}
				}
				r.output = out;
				writeReport(pack, r);
				if (rootF.isEmpty() && r.moved.isEmpty() && !r.formatUpdated) {
					r.notes.add("Nothing needed changing");
				}
				return r;
			}
		} catch (Throwable t) {
			r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
			return r;
		}
	}

	private static void put(ZipOutputStream zo, String name, byte[] data) throws IOException {
		ZipEntry e = new ZipEntry(name);
		zo.putNextEntry(e);
		zo.write(data);
		zo.closeEntry();
	}

	private static String fixRefs(String json, Map<String, String> refs) {
		String s = json;
		for (Map.Entry<String, String> e : refs.entrySet()) {
			s = s.replace("\"" + e.getKey() + "\"", "\"" + e.getValue() + "\"");
			s = s.replace("\"minecraft:" + e.getKey() + "\"", "\"minecraft:" + e.getValue() + "\"");
		}
		return s;
	}

	private static void writeReport(Path pack, Result r) {
		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("kubo-pack-updates");
			Files.createDirectories(dir);
			Path f = dir.resolve(pack.getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_") + ".txt");
			StringBuilder sb = new StringBuilder();
			sb.append(r.dryRun ? "CHECK ONLY (nothing was written)\n" : "UPDATED COPY: " + r.output + "\n");
			sb.append("Pack format: ").append(r.formatInfo.isEmpty() ? "unchanged" : r.formatInfo).append("\n");
			sb.append("Files already correct: ").append(r.unchanged).append("\n");
			sb.append("JSON files fixed: ").append(r.jsonFixed).append("\n\n");
			for (String n : r.notes) {
				sb.append("NOTE: ").append(n).append('\n');
			}
			sb.append("\nMOVED (").append(r.moved.size()).append(")\n");
			r.moved.forEach(s -> sb.append("  ").append(s).append('\n'));
			sb.append("\nAMBIGUOUS, left as they are (").append(r.ambiguous.size()).append(")\n");
			r.ambiguous.forEach(s -> sb.append("  ").append(s).append('\n'));
			sb.append("\nNOT IN THIS VERSION, left as they are (").append(r.unmatched.size()).append(")\n");
			r.unmatched.forEach(s -> sb.append("  ").append(s).append('\n'));
			Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
			r.report = f;
		} catch (Exception ignored) {
		}
	}
}
