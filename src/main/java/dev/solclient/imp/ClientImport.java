package dev.solclient.imp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Config;
import dev.solclient.SolClient;
import dev.solclient.mods.Mod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Import settings from other clients (Settings > Setup & Reset). It finds the other client on this computer and brings over what can be read
 * safely: which mods were on (Feather / Dawn, whose profile files are read for real), and the game's own options of that client: the key
 * bindings, the mouse sensitivity and the field of view. Your Caos settings for everything else stay as they are.
 */
public final class ClientImport {

	public record Profile(String name, Path file, Path options) {
	}

	public record Found(String id, String label, String icon, List<Profile> profiles, String note) {
	}

	private static final boolean WIN = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

	/** Feather's mod key -> the id of the same mod in Caos. */
	private static final Map<String, String> FEATHER = new LinkedHashMap<>();

	static {
		String[][] m = {{"fps", "fps"}, {"cps", "cps"}, {"ping", "ping"}, {"keystrokes", "keystrokes"}, {"coordinates", "coordinates"},
				{"armorStatus", "armour"}, {"potionEffects", "potion_effects"}, {"toggleSprint", "toggle_sprint"}, {"zoom", "zoom"},
				{"fovChanger", "fov_changer"}, {"motionBlur", "motion_blur"}, {"hitbox", "hitboxes"}, {"scoreboard", "scoreboard"},
				{"tablist", "tab_list"}, {"waypoints", "waypoints"}, {"time", "clock"}, {"stopwatch1", "stopwatch"}, {"packdisplay", "pack_display"},
				{"nickHider", "nick_hider"}, {"bossBar", "boss_bar"}, {"itemPhysic", "item_physics"}, {"brightness", "fullbright"},
				{"reachDisplay", "reach_display"}, {"comboDisplay", "combo_counter"}, {"itemCounter", "item_counter"}, {"speedMeter", "speedometer"},
				{"timeChanger", "time_changer"}, {"weatherchanger", "weather"}, {"tps", "tps_display"}, {"direction", "direction"},
				{"serverAddress", "server_address"}, {"crosshair", "crosshair"}, {"autohidehud", "auto_hide_hud"}, {"reconnect", "auto_reconnect"},
				{"damageIndicator", "damage_numbers"}, {"nametags", "name_tag_style"}, {"perspective", "freelook"}, {"snaplook", "taplook"},
				{"tnttimer", "tnt_timer"}, {"lightleveloverlay", "light_level"}, {"systemresources", "memory"}, {"playtime", "session_time"},
				{"totem", "totem_animation"}, {"voice", "voice_chat"}, {"discordRP", "discord"}};
		for (String[] p : m) {
			FEATHER.put(p[0], p[1]);
		}
	}

	private ClientImport() {
	}

	private static Path home() {
		return Paths.get(System.getProperty("user.home"));
	}

	private static Path appData() {
		String a = System.getenv("APPDATA");
		return a != null ? Paths.get(a) : home().resolve("AppData").resolve("Roaming");
	}

	/** Where other launchers keep their Minecraft folder (the default one plus anything their own settings say). */
	private static List<Path> minecraftDirs() {
		List<Path> out = new ArrayList<>();
		Path def = WIN ? appData().resolve(".minecraft") : MAC ? home().resolve("Library/Application Support/minecraft") : home().resolve(".minecraft");
		out.add(def);
		for (Path dir : featherAppDirs()) {
			try {
				Path s = dir.resolve("settings.json");
				if (Files.isRegularFile(s)) {
					JsonObject o = JsonParser.parseString(Files.readString(s, StandardCharsets.UTF_8)).getAsJsonObject();
					if (o.has("mcPath") && !o.get("mcPath").isJsonNull()) {
						Path p = Paths.get(o.get("mcPath").getAsString());
						if (!out.contains(p)) {
							out.add(p);
						}
					}
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}
		return out;
	}

	private static List<Path> featherAppDirs() {
		List<Path> l = new ArrayList<>();
		if (MAC) {
			Path b = home().resolve("Library/Application Support");
			l.add(b.resolve("feather"));
			l.add(b.resolve("dawn"));
		} else if (WIN) {
			Path b = appData();
			l.add(b.resolve(".feather"));
			l.add(b.resolve("feather"));
			l.add(b.resolve("dawn"));
			l.add(b.resolve("Dawn"));
		} else {
			l.add(home().resolve(".config/feather"));
			l.add(home().resolve(".feather"));
			l.add(home().resolve(".config/dawn"));
		}
		return l;
	}

	// ------------------------------------------------------------------ finding the clients
	public static List<Found> detect() {
		List<Found> out = new ArrayList<>();
		out.add(lunar());
		out.add(badlion());
		out.add(feather());
		return out;
	}

	private static Found feather() {
		List<Profile> profiles = new ArrayList<>();
		for (Path mc : minecraftDirs()) {
			for (String sub : new String[] {"feather", "dawn"}) {
				Path dir = mc.resolve(sub).resolve("configuration").resolve("profiles");
				Path opts = mc.resolve(sub).resolve("mc_options_v2.txt");
				if (!Files.isDirectory(dir)) {
					continue;
				}
				try (Stream<Path> s = Files.list(dir)) {
					s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().forEach(f -> {
						String n = f.getFileName().toString();
						profiles.add(new Profile(n.substring(0, n.length() - 5), f, Files.isRegularFile(opts) ? opts : null));
					});
				} catch (IOException ignored) {
				}
			}
		}
		return new Found("feather", "Feather / Dawn", "import_feather", profiles, "");
	}

	private static Found lunar() {
		List<Profile> profiles = new ArrayList<>();
		Path root = home().resolve(".lunarclient");
		boolean there = Files.isDirectory(root);
		if (there) {
			try (Stream<Path> s = Files.walk(root, 8)) {
				s.filter(f -> f.getFileName().toString().equals("options.txt") && Files.isRegularFile(f)).sorted().forEach(f -> {
					Path rel = root.relativize(f.getParent());
					profiles.add(new Profile(rel.toString().isEmpty() ? "options" : rel.toString().replace('\\', '/'), null, f));
				});
			} catch (IOException ignored) {
			}
		}
		return new Found("lunar", "Lunar Client", "import_lunar", profiles, there && profiles.isEmpty() ? "Lunar found, no key settings to read" : "");
	}

	private static Found badlion() {
		List<Profile> profiles = new ArrayList<>();
		boolean there = Files.isDirectory(appData().resolve("Badlion Client")) || Files.isDirectory(Paths.get("/Applications/Badlion Client.app"))
				|| Files.isDirectory(home().resolve("Library/Application Support/Badlion Client")) || Files.isDirectory(home().resolve(".config/Badlion Client"));
		for (Path mc : minecraftDirs()) {
			Path o = mc.resolve("options.txt");
			if (there && Files.isRegularFile(o)) {
				profiles.add(new Profile("Minecraft options (" + mc.getFileName() + ")", null, o));
			}
		}
		return new Found("badlion", "Badlion Client", "import_badlion", profiles, "");
	}

	// ------------------------------------------------------------------ importing
	/** Imports one profile; returns what happened (for the screen). Client thread. */
	public static String run(Found f, Profile p) {
		StringBuilder sb = new StringBuilder();
		int mods = 0;
		try {
			if (p.file() != null) {
				mods = featherMods(p.file());
				sb.append(mods).append(" mods set like in ").append(f.label()).append(".\n");
			}
			if (p.options() != null) {
				sb.append(options(p.options()));
			}
			if (mods > 0) {
				Config.save();
			}
		} catch (IOException | RuntimeException e) {
			return "Could not import: " + e.getMessage();
		}
		if (sb.length() == 0) {
			sb.append("Nothing to import from this profile.");
		}
		return sb.toString().strip();
	}

	private static int featherMods(Path file) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		Map<String, Mod> byId = new HashMap<>();
		for (Mod m : SolClient.MODS) {
			byId.put(m.id, m);
		}
		int n = 0;
		for (Map.Entry<String, String> e : FEATHER.entrySet()) {
			JsonElement g = root.get(e.getKey());
			Mod m = byId.get(e.getValue());
			if (g == null || !g.isJsonObject() || m == null || !g.getAsJsonObject().has("enabled")) {
				continue;
			}
			boolean on = "true".equalsIgnoreCase(g.getAsJsonObject().get("enabled").getAsString());
			if (m.enabled != on) {
				m.toggle();
			}
			n++;
		}
		return n;
	}

	/** Key bindings, mouse sensitivity and field of view from an options.txt style file. */
	private static String options(Path file) throws IOException {
		MinecraftClient mc = MinecraftClient.getInstance();
		Map<String, String> kv = new HashMap<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			int c = line.indexOf(':');
			if (c > 0) {
				kv.put(line.substring(0, c), line.substring(c + 1).trim());
			}
		}
		int keys = 0;
		for (KeyBinding kb : mc.options.allKeys) {
			String v = kv.get("key_" + kb.getId());
			if (v == null || v.isEmpty()) {
				continue;
			}
			try {
				InputUtil.Key k = InputUtil.fromTranslationKey(v.replace("\"", ""));
				if (!k.getTranslationKey().equals(kb.getBoundKeyTranslationKey())) {
					kb.setBoundKey(k);
					keys++;
				}
			} catch (RuntimeException ignored) {
			}
		}
		StringBuilder sb = new StringBuilder();
		try {
			if (kv.containsKey("mouseSensitivity")) {
				mc.options.getMouseSensitivity().setValue(Math.max(0, Math.min(1, Double.parseDouble(kv.get("mouseSensitivity")))));
				sb.append("Mouse sensitivity. ");
			}
			if (kv.containsKey("fov")) {
				double f = Double.parseDouble(kv.get("fov"));
				mc.options.getFov().setValue((int) Math.max(30, Math.min(110, Math.round(70 + 40 * f))));
				sb.append("Field of view. ");
			}
		} catch (RuntimeException ignored) {
		}
		KeyBinding.updateKeysByCode();
		mc.options.write();
		return keys + " keys changed. " + sb;
	}
}
