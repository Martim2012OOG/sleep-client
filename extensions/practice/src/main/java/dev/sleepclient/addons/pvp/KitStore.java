package dev.sleepclient.addons.pvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.sleepclient.addons.pvp.PracticeKit.Style;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Custom kits made in the kit editor, kept in config/kubo_custom_kits.json. A saved kit is only a map
 * "slot name -> item" (vanilla slot names and item syntax); {@link #build} turns it into a {@link PracticeKit}
 * (commands for the player, equipment + weapons + fighting style for the bot).
 * Everything read from the file is validated, because the strings end up in commands.
 */
public final class KitStore {

	public static final int MAX_KITS = 12;
	/** "AUTO" lets the add-on pick the bot style from the items in the kit. */
	public static final String[] STYLES = {"AUTO", "SWORD", "AXE", "MACE", "SPEAR_MACE", "SWAP"};
	public static final String[] STYLE_LABELS = {"Auto", "Sword", "Axe", "Mace", "Spear Mace", "Attr Swap"};

	/** One saved kit. */
	public static final class Data {
		public String name = "";
		public String style = "AUTO";
		public Map<String, String> slots = new LinkedHashMap<>();

		Data copy() {
			Data d = new Data();
			d.name = name;
			d.style = style;
			d.slots = new LinkedHashMap<>(slots);
			return d;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern SLOT = Pattern.compile("hotbar\\.[0-8]|inventory\\.(\\d|1\\d|2[0-6])|armor\\.(head|chest|legs|feet)|weapon\\.offhand");
	private static final Pattern SPEC = Pattern.compile("[a-z0-9_:.]+(\\[[^\\n\\r]*\\])?( \\d{1,2})?");
	private static final Pattern CMD = Pattern.compile("item replace entity @P (\\S+) with (.+)");
	private static final Pattern DENSITY = Pattern.compile("density:(\\d+)");

	private static List<Data> data;
	private static List<PracticeKit> built;

	private KitStore() {
	}

	// ------------------------------------------------------------------ item spec helpers (shared with the editor)
	public static boolean validSlot(String slot) {
		return slot != null && SLOT.matcher(slot).matches();
	}

	public static boolean validSpec(String spec) {
		return spec != null && spec.length() <= 600 && SPEC.matcher(spec).matches();
	}

	/** "minecraft:mace[...] 1" -> "minecraft:mace" */
	public static String idOf(String spec) {
		return spec.split("[\\[\\s]")[0];
	}

	/** The spec without the trailing amount. */
	public static String bare(String spec) {
		return spec.replaceAll("\\s+\\d+$", "");
	}

	public static int countOf(String spec) {
		Matcher m = Pattern.compile("\\s(\\d+)$").matcher(spec);
		return m.find() ? Integer.parseInt(m.group(1)) : 1;
	}

	public static String withCount(String spec, int n) {
		String b = bare(spec);
		return n <= 1 ? b : b + " " + n;
	}

	// ------------------------------------------------------------------ storage
	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo_custom_kits.json");
	}

	private static synchronized void load() {
		if (data != null) {
			return;
		}
		data = new ArrayList<>();
		Path p = path();
		if (Files.exists(p)) {
			try {
				List<Data> l = GSON.fromJson(Files.readString(p), new TypeToken<List<Data>>() {
				}.getType());
				if (l != null) {
					for (Data d : l) {
						Data ok = sanitize(d);
						if (ok != null && data.size() < MAX_KITS) {
							data.add(ok);
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				System.err.println("[Sleep] Could not read custom kits: " + e);
			}
		}
		rebuild();
	}

	private static Data sanitize(Data d) {
		if (d == null || d.name == null || d.name.isBlank() || d.slots == null) {
			return null;
		}
		Data o = new Data();
		o.name = d.name.trim().length() > 24 ? d.name.trim().substring(0, 24) : d.name.trim();
		o.style = d.style != null && List.of(STYLES).contains(d.style) ? d.style : "AUTO";
		for (Map.Entry<String, String> e : d.slots.entrySet()) {
			if (validSlot(e.getKey()) && validSpec(e.getValue())) {
				o.slots.put(e.getKey(), e.getValue());
			}
		}
		return o.slots.isEmpty() ? null : o;
	}

	private static void rebuild() {
		built = new ArrayList<>();
		for (Data d : data) {
			built.add(build(d));
		}
	}

	private static void write() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(data));
		} catch (IOException e) {
			System.err.println("[Sleep] Could not save custom kits: " + e);
		}
	}

	public static synchronized List<PracticeKit> kits() {
		load();
		return new ArrayList<>(built);
	}

	/** True if a custom kit with this name exists. */
	public static synchronized boolean exists(String name) {
		load();
		return data.stream().anyMatch(d -> d.name.equalsIgnoreCase(name));
	}

	/** Adds the kit, or replaces the custom kit with the same name. False if there is no room / the kit is invalid. */
	public static synchronized boolean save(Data d) {
		load();
		Data ok = sanitize(d);
		if (ok == null) {
			return false;
		}
		for (int i = 0; i < data.size(); i++) {
			if (data.get(i).name.equalsIgnoreCase(ok.name)) {
				data.set(i, ok);
				write();
				rebuild();
				return true;
			}
		}
		if (data.size() >= MAX_KITS) {
			return false;
		}
		data.add(ok);
		write();
		rebuild();
		return true;
	}

	public static synchronized void delete(String name) {
		load();
		if (data.removeIf(d -> d.name.equalsIgnoreCase(name))) {
			write();
			rebuild();
		}
	}

	/** Editable copy of a kit's layout (built-in kits are read back from their commands). */
	public static synchronized Data fromKit(PracticeKit k) {
		load();
		Data d = new Data();
		d.name = k.name;
		if (k.custom) {
			for (Data c : data) {
				if (c.name.equalsIgnoreCase(k.name)) {
					return c.copy();
				}
			}
		}
		d.style = k.style.name();
		for (String cmd : k.player) {
			Matcher m = CMD.matcher(cmd);
			if (m.matches() && validSlot(m.group(1)) && validSpec(m.group(2))) {
				d.slots.put(m.group(1), m.group(2));
			}
		}
		return d;
	}

	// ------------------------------------------------------------------ Data -> PracticeKit
	private static int rank(String id) {
		if (id.contains("netherite")) {
			return 6;
		}
		if (id.contains("diamond")) {
			return 5;
		}
		if (id.contains("iron")) {
			return 4;
		}
		if (id.contains("stone")) {
			return 3;
		}
		if (id.contains("golden")) {
			return 2;
		}
		return 1;
	}

	static PracticeKit build(Data d) {
		List<String> player = new ArrayList<>();
		List<String> bot = new ArrayList<>();
		String sword = null, axe = null, mace = null, spear = null;
		int swordR = 0, axeR = 0;
		for (Map.Entry<String, String> e : d.slots.entrySet()) {
			String slot = e.getKey(), spec = e.getValue();
			String id = idOf(spec), bare = bare(spec);
			player.add("item replace entity @P " + slot + " with " + spec);
			if (slot.startsWith("armor.") || (slot.equals("weapon.offhand") && (id.endsWith("shield") || id.endsWith("totem_of_undying")))) {
				bot.add("item replace entity @B " + slot + " with " + bare);
			}
			if (id.endsWith("_sword") && rank(id) > swordR) {
				sword = bare;
				swordR = rank(id);
			} else if (id.endsWith("_axe") && rank(id) > axeR) {
				axe = bare;
				axeR = rank(id);
			} else if (id.equals("minecraft:mace") && mace == null) {
				mace = bare;
			} else if (id.endsWith("_spear") && spear == null) {
				spear = bare;
			}
		}

		Style style;
		if (!"AUTO".equals(d.style)) {
			style = Style.valueOf(d.style);
		} else if (mace != null && spear != null) {
			style = sword != null && axe != null ? Style.SWAP : Style.SPEAR_MACE;
		} else if (mace != null) {
			style = Style.MACE;
		} else if (axe != null && d.slots.getOrDefault("weapon.offhand", "").contains("shield")) {
			style = Style.AXE;
		} else {
			style = Style.SWORD;
		}
		if (style == Style.SPEAR_MACE && spear == null) {
			style = Style.MACE;
		}
		if (style == Style.MACE && mace == null) {
			style = Style.SWORD;
		}

		// the bot must be able to hold everything its style uses
		if (sword == null) {
			sword = "minecraft:iron_sword";
		}
		if (style == Style.AXE || style == Style.SWAP) {
			axe = axe != null ? axe : "minecraft:iron_axe";
		}
		if (style == Style.SPEAR_MACE || style == Style.SWAP) {
			spear = spear != null ? spear : "minecraft:iron_spear";
		}
		if (style == Style.MACE || style == Style.SPEAR_MACE || style == Style.SWAP) {
			mace = mace != null ? mace : "minecraft:mace";
		}

		int best = Math.max(swordR, axeR);
		float dmg = switch (Math.max(best, 1)) {
			case 6 -> 8F;
			case 5 -> 7F;
			case 4 -> 5.5F;
			case 3 -> 5F;
			default -> 4F;
		};
		if (style == Style.AXE) {
			dmg += 1.5F;
		} else if (style == Style.SWAP) {
			dmg -= 0.5F;
		}
		int density = 0;
		if (mace != null) {
			Matcher m = DENSITY.matcher(mace);
			if (m.find()) {
				density = Math.min(10, Integer.parseInt(m.group(1)));
			}
		}
		int items = d.slots.size();
		return PracticeKit.custom(d.name, "Your kit (" + items + " item" + (items == 1 ? "" : "s") + "). Bot style: "
				+ STYLE_LABELS[List.of(STYLES).indexOf(d.style)] + ".", style, dmg,
				player.toArray(new String[0]), bot.toArray(new String[0]), sword, axe, mace, spear, density);
	}
}
