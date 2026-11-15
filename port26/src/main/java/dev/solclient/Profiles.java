package dev.solclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod profiles (like Lunar's Default / UHC / Arena PvP): each profile is a full copy of every mod's settings
 * and HUD positions in config/plegumo-profiles/&lt;name&gt;.json. Loading one replaces the current settings.
 */
public final class Profiles {

	private Profiles() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-profiles");
	}

	public static boolean valid(String name) {
		return name != null && name.matches("[A-Za-z0-9 _-]{1,24}") && !name.isBlank();
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir())) {
			return out;
		}
		try (Stream<Path> s = Files.list(dir())) {
			s.filter(p -> p.getFileName().toString().endsWith(".json"))
					.map(p -> p.getFileName().toString().replaceAll("\\.json$", ""))
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.forEach(out::add);
		} catch (Exception e) {
			System.err.println("[Sleep] profiles: " + e);
		}
		return out;
	}

	public static String active() {
		try {
			Path p = dir().resolve("active.txt");
			return Files.exists(p) ? Files.readString(p).trim() : "Default";
		} catch (Exception e) {
			return "Default";
		}
	}

	private static void setActive(String name) {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("active.txt"), name);
		} catch (Exception ignored) {
			// not important
		}
	}

	/** Saves the current settings as a profile (overwrites one with the same name). */
	public static boolean save(String name) {
		if (!valid(name)) {
			return false;
		}
		Config.saveTo(dir().resolve(name.trim() + ".json"));
		setActive(name.trim());
		return true;
	}

	/** Loads a profile: the current settings are first kept in the active profile so nothing is lost. */
	public static void load(String name) {
		Path p = dir().resolve(name + ".json");
		if (!Files.exists(p)) {
			return;
		}
		String cur = active();
		if (valid(cur) && !cur.equals(name)) {
			Config.saveTo(dir().resolve(cur + ".json"));
		}
		Config.loadFrom(p, SolClient.MODS);
		Config.save();
		setActive(name);
	}

	public static void delete(String name) {
		try {
			Files.deleteIfExists(dir().resolve(name + ".json"));
		} catch (Exception ignored) {
			// ignore
		}
	}
}
