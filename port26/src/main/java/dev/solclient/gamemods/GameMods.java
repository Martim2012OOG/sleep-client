package dev.solclient.gamemods;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Your other mods (Sodium, Simple Voice Chat...) with an on/off switch.
 * Java can't unload a mod that is already running, so turning one OFF:
 *  - right away: its key binds stop working and its menus can't be opened;
 *  - the jar becomes "name.jar.disabled", so from the next start it is gone completely (as if not installed).
 * Turning it ON renames it back (it loads again on the next start) and gives its keys back.
 */
public final class GameMods {

	public static final class Entry {
		public String id, name, version, description;
		public Path jar;          // current file (…jar or …jar.disabled)
		public boolean loaded;    // running in this game
		public boolean enabled;   // on disk: not .disabled
		public String iconPath;   // inside the jar
		public List<String> neededBy = List.of();

		public boolean restartNeeded() {
			return loaded != enabled;
		}
	}

	private static final Set<String> HIDDEN = Set.of("minecraft", "java", "fabricloader", "sol_client", "mixinextras", "fabric-api");
	private static final Gson GSON = new Gson();
	private static List<Entry> cache;
	private static long cacheAt;
	/** Jars (real paths) of mods turned off while running: their screens are blocked. */
	private static final Set<Path> OFF_NOW = ConcurrentHashMap.newKeySet();
	private static Map<String, Map<String, String>> savedKeys = new HashMap<>();
	private static boolean started;
	public static volatile String message;

	private GameMods() {
	}

	private static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	private static Path stateFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-game-mods.json");
	}

	// ------------------------------------------------------------------ list
	public static synchronized List<Entry> list() {
		long now = System.currentTimeMillis();
		if (cache != null && now - cacheAt < 3000L) {
			return cache;
		}
		Map<String, Entry> out = new HashMap<>();
		Map<String, List<String>> neededBy = new HashMap<>();
		for (ModContainer mc : FabricLoader.getInstance().getAllMods()) {
			ModMetadata md = mc.getMetadata();
			if (mc.getContainingMod().isPresent() || HIDDEN.contains(md.getId()) || md.getId().startsWith("fabric-") || md.getId().startsWith("kubo_ext_")
					|| "builtin".equals(md.getType())) {
				continue;
			}
			Path jar = null;
			try {
				if (mc.getOrigin().getKind() == ModOrigin.Kind.PATH) {
					for (Path p : mc.getOrigin().getPaths()) {
						if (p.toString().endsWith(".jar")) {
							jar = p;
						}
					}
				}
			} catch (Throwable ignored) {
			}
			if (jar == null) {
				continue;
			}
			Entry e = new Entry();
			e.id = md.getId();
			e.name = md.getName();
			e.version = md.getVersion().getFriendlyString();
			e.description = md.getDescription();
			e.loaded = true;
			e.jar = Files.exists(jar) ? jar : Path.of(jar + ".disabled");
			e.enabled = Files.exists(jar);
			e.iconPath = md.getIconPath(64).orElse(null);
			out.put(e.id, e);
			for (ModDependency d : md.getDependencies()) {
				if (d.getKind() == ModDependency.Kind.DEPENDS) {
					neededBy.computeIfAbsent(d.getModId(), k -> new ArrayList<>()).add(md.getName());
				}
			}
		}
		// jars turned off earlier (not loaded now)
		try (Stream<Path> s = Files.list(modsDir())) {
			s.filter(p -> p.getFileName().toString().endsWith(".jar.disabled")).forEach(p -> {
				Entry e = readJar(p);
				if (e != null && !out.containsKey(e.id) && !e.id.startsWith("kubo_ext_")) {
					out.put(e.id, e);
				}
			});
		} catch (Exception ignored) {
		}
		for (Entry e : out.values()) {
			List<String> nb = new ArrayList<>();
			for (String n : neededBy.getOrDefault(e.id, List.of())) {
				Entry user = null;
				for (Entry o : out.values()) {
					if (o.name.equals(n)) {
						user = o;
					}
				}
				if (user == null || user.enabled) {
					nb.add(n);
				}
			}
			e.neededBy = nb;
		}
		List<Entry> l = new ArrayList<>(out.values());
		l.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		cache = l;
		cacheAt = now;
		return l;
	}

	private static Entry readJar(Path p) {
		try (ZipFile z = new ZipFile(p.toFile())) {
			ZipEntry fm = z.getEntry("fabric.mod.json");
			if (fm == null) {
				return null;
			}
			JsonObject o = JsonParser.parseString(new String(z.getInputStream(fm).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			Entry e = new Entry();
			e.id = str(o, "id", p.getFileName().toString());
			e.name = str(o, "name", e.id);
			e.version = str(o, "version", "");
			e.description = str(o, "description", "");
			JsonElement icon = o.get("icon");
			if (icon != null && icon.isJsonPrimitive()) {
				e.iconPath = icon.getAsString();
			} else if (icon != null && icon.isJsonObject()) {
				for (String k : icon.getAsJsonObject().keySet()) {
					e.iconPath = icon.getAsJsonObject().get(k).getAsString();
				}
			}
			e.jar = p;
			e.loaded = false;
			e.enabled = false;
			return e;
		} catch (Exception ex) {
			return null;
		}
	}

	private static String str(JsonObject o, String k, String def) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}

	/** Icon bytes of a mod (from the running mod or from its jar). */
	public static byte[] icon(Entry e) {
		if (e.iconPath == null) {
			return null;
		}
		try {
			var c = FabricLoader.getInstance().getModContainer(e.id);
			if (c.isPresent() && e.loaded) {
				var p = c.get().findPath(e.iconPath);
				if (p.isPresent()) {
					return Files.readAllBytes(p.get());
				}
			}
			try (ZipFile z = new ZipFile(e.jar.toFile())) {
				ZipEntry ze = z.getEntry(e.iconPath.startsWith("/") ? e.iconPath.substring(1) : e.iconPath);
				return ze == null ? null : z.getInputStream(ze).readAllBytes();
			}
		} catch (Exception ex) {
			return null;
		}
	}

	// ------------------------------------------------------------------ settings (Mod Menu API)
	/** modId -> ConfigScreenFactory (Mod Menu's own interface, used by reflection: no dependency on Mod Menu). */
	private static Map<String, Object> configFactories;

	private static boolean realFactory(Object f) {
		// the default factory of ModMenuApi is a lambda that returns null: it means "this mod has no settings"
		return f != null && !f.getClass().getName().startsWith("com.terraformersmc.modmenu.api.ModMenuApi");
	}

	@SuppressWarnings("unchecked")
	private static synchronized Map<String, Object> factories() {
		if (configFactories != null) {
			return configFactories;
		}
		Map<String, Object> out = new HashMap<>();
		try {
			for (var c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
				try {
					String id = c.getProvider().getMetadata().getId();
					Object api = c.getEntrypoint();
					Object f = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
					if (realFactory(f)) {
						out.putIfAbsent(id, f);
					}
					Object provided = api.getClass().getMethod("getProvidedConfigScreenFactories").invoke(api);
					if (provided instanceof Map<?, ?> m) {
						for (Map.Entry<?, ?> en : m.entrySet()) {
							if (en.getKey() instanceof String k && realFactory(en.getValue())) {
								out.putIfAbsent(k, en.getValue());
							}
						}
					}
				} catch (Throwable ignored) {
					// a mod whose Mod Menu entrypoint fails just has no settings button
				}
			}
		} catch (Throwable ignored) {
		}
		configFactories = out;
		return out;
	}

	/** True when the mod gives Mod Menu a settings screen. */
	public static boolean hasConfig(String modId) {
		return factories().containsKey(modId);
	}

	/** Opens the settings screen the mod gave to Mod Menu; {@code parent} is where Esc / Done goes back to. */
	/** The Mod Menu settings screen of a mod, not opened (the Sleep menu shows it inside its own window). */
	public static Screen createConfig(String modId, Screen parent) {
		Object f = factories().get(modId);
		if (f == null) {
			return null;
		}
		try {
			Class<?> iface = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory", false, f.getClass().getClassLoader());
			Object screen = iface.getMethod("create", Screen.class).invoke(f, parent);
			return screen instanceof Screen sc ? sc : null;
		} catch (Throwable t) {
			System.err.println("[Sleep] Could not create the settings of " + modId + ": " + t);
			return null;
		}
	}

	public static boolean openConfig(String modId, Screen parent) {
		Object f = factories().get(modId);
		if (f == null) {
			return false;
		}
		try {
			Class<?> iface = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory", false, f.getClass().getClassLoader());
			Object screen = iface.getMethod("create", Screen.class).invoke(f, parent);
			if (screen instanceof Screen sc) {
				dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), sc);
				return true;
			}
		} catch (Throwable t) {
			System.err.println("[Sleep] Could not open the settings of " + modId + ": " + t);
		}
		return false;
	}

	// ------------------------------------------------------------------ on / off
	public static synchronized void set(Entry e, boolean on) {
		if (on == e.enabled) {
			return;
		}
		if (!on && !e.neededBy.isEmpty()) {
			message = e.name + " is needed by " + String.join(", ", e.neededBy) + " - turn those off first";
			return;
		}
		Path from = e.jar;
		String fn = from.getFileName().toString();
		Path to = on ? from.resolveSibling(fn.replaceAll("\\.disabled$", "")) : from.resolveSibling(fn + ".disabled");
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception ex) {
			try {
				Files.move(from, to);
			} catch (Exception ex2) {
				// Windows keeps running jars locked: do it when the game closes
				Path f = from, t = to;
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					try {
						Files.move(f, t);
					} catch (Exception ignored) {
					}
				}));
			}
		}
		e.jar = to;
		e.enabled = on;
		Path real = on ? to : from;
		if (e.loaded) {
			if (on) {
				OFF_NOW.remove(real);
				restoreKeys(e.id);
			} else {
				OFF_NOW.add(real);
				unbindKeys(e);
			}
		}
		message = e.restartNeeded() ? (on ? e.name + " will load on the next start" : e.name + " is off (fully removed on the next start)") : null;
		cache = null;
		save();
	}

	// ------------------------------------------------------------------ keys
	private static boolean belongs(KeyMapping kb, Entry e) {
		String id = e.id.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		String name = e.name.toLowerCase(Locale.ROOT).replace(" ", "");
		String hay = (kb.getName() + " " + kb.getCategory()).toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		return hay.contains(id) || hay.contains(name);
	}

	private static void unbindKeys(Entry e) {
		Minecraft mc = Minecraft.getInstance();
		Map<String, String> saved = savedKeys.computeIfAbsent(e.id, k -> new HashMap<>());
		for (KeyMapping kb : mc.options.keyMappings) {
			if (belongs(kb, e) && !kb.isUnbound()) {
				saved.put(kb.getName(), kb.saveString());
				kb.setKey(InputConstants.UNKNOWN);
			}
		}
		KeyMapping.resetMapping();
		mc.options.save();
	}

	private static void restoreKeys(String modId) {
		Map<String, String> saved = savedKeys.remove(modId);
		if (saved == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		for (KeyMapping kb : mc.options.keyMappings) {
			String k = saved.get(kb.getName());
			if (k != null) {
				kb.setKey(InputConstants.getKey(k));
			}
		}
		KeyMapping.resetMapping();
		mc.options.save();
	}

	/** Once per game start: give keys back to mods that were turned on again (they are loaded now). */
	public static void tick() {
		if (started) {
			return;
		}
		started = true;
		load();
		for (String id : new ArrayList<>(savedKeys.keySet())) {
			if (FabricLoader.getInstance().isModLoaded(id)) {
				restoreKeys(id);
			}
		}
		save();
	}

	/** Screens of mods turned off while the game runs can't be opened. */
	public static boolean blocked(Screen s) {
		if (s == null || OFF_NOW.isEmpty()) {
			return false;
		}
		try {
			var src = s.getClass().getProtectionDomain().getCodeSource();
			if (src == null) {
				return false;
			}
			Path p = Path.of(src.getLocation().toURI());
			for (Path off : OFF_NOW) {
				if (p.equals(off) || p.getFileName().equals(off.getFileName())) {
					return true;
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	// ------------------------------------------------------------------ state file
	@SuppressWarnings("unchecked")
	private static void load() {
		try {
			Path f = stateFile();
			if (Files.exists(f)) {
				Map<String, Map<String, String>> m = GSON.fromJson(Files.readString(f), Map.class);
				if (m != null) {
					savedKeys = new HashMap<>();
					m.forEach((k, v) -> savedKeys.put(k, new HashMap<>(v)));
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static void save() {
		try {
			Files.writeString(stateFile(), GSON.toJson(savedKeys));
		} catch (Exception ignored) {
		}
	}

	/** For tiles: mods whose files were found but couldn't be read get skipped; this is just a count helper. */
	public static int count() {
		return list().size();
	}

	static Set<String> ids() {
		Set<String> s = new HashSet<>();
		for (Entry e : list()) {
			s.add(e.id);
		}
		return s;
	}
}
