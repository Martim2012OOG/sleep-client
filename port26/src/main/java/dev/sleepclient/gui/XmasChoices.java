package dev.sleepclient.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;

/**
 * Christmas World choices: which Christmas songs play (from .minecraft/plegumo-music/christmas) and which Christmas
 * resource packs are turned on with the mod. The songs and packs are YOUR files: nothing is shipped in the jar.
 */
public final class XmasChoices {

	/** Packs that look like Christmas ones (file name or description). */
	private static final Pattern XMAS = Pattern.compile("(?i)christmas|xmas|natal|winter|snow|santa|noel|navidad");

	private static Set<String> offSongs, packsOn, packsByUs;

	private XmasChoices() {
	}

	private static Path file(String n) {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve(n);
	}

	private static Set<String> read(String n, Set<String> def) {
		try {
			Path f = file(n);
			if (Files.exists(f)) {
				Set<String> s = new LinkedHashSet<>();
				for (String l : Files.readAllLines(f)) {
					if (!l.isBlank()) {
						s.add(l.strip());
					}
				}
				return s;
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static void write(String n, Set<String> s) {
		try {
			Files.createDirectories(file(n).getParent());
			Files.write(file(n), s);
		} catch (Exception ignored) {
		}
	}

	// ------------------------------------------------------------------ songs
	public static Path songDir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("plegumo-music").resolve("christmas");
	}

	public static List<Path> songs() {
		List<Path> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(songDir())) {
			s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(ogg|mp3)$")).sorted((a, b) -> pretty(a).compareToIgnoreCase(pretty(b)))
					.forEach(out::add);
		} catch (Exception ignored) {
		}
		return out;
	}

	private static Set<String> offSongs() {
		if (offSongs == null) {
			offSongs = read("xmas-songs-off.txt", new LinkedHashSet<>());
		}
		return offSongs;
	}

	public static boolean songOn(Path p) {
		return !offSongs().contains(p.getFileName().toString());
	}

	public static void setSong(Path p, boolean on) {
		if (on) {
			offSongs().remove(p.getFileName().toString());
		} else {
			offSongs().add(p.getFileName().toString());
		}
		write("xmas-songs-off.txt", offSongs());
	}

	public static void allSongs(boolean on) {
		offSongs().clear();
		if (!on) {
			for (Path p : songs()) {
				offSongs().add(p.getFileName().toString());
			}
		}
		write("xmas-songs-off.txt", offSongs());
	}

	/** "79-bingcrosby-whitechristmas1950avi.ogg" -> "bingcrosby - whitechristmas" (no numbers, no "official audio"...). */
	public static String pretty(Path p) {
		String n = p.getFileName().toString().replaceAll("(?i)\\.(ogg|mp3)$", "").replace('_', ' ');
		n = n.replaceFirst("^\\d+\\s*[-. ]\\s*", "");
		n = n.replaceAll("(?i)(official)?(lyric)?(music)?(audio|video|visualizer)|remastered\\d*|\\d{4}avi|hd$|fireplacevideo.*", "");
		n = n.replace("-", " - ").replaceAll("\\s+", " ").strip();
		return n.isEmpty() ? p.getFileName().toString() : n;
	}

	// ------------------------------------------------------------------ resource packs
	/** The Christmas-looking packs in resourcepacks/ (file packs only). */
	public static List<Pack> packs() {
		List<Pack> out = new ArrayList<>();
		var mgr = Minecraft.getInstance().getResourcePackRepository();
		mgr.reload();
		for (Pack p : mgr.getAvailablePacks()) {
			String id = p.getId();
			if (!id.startsWith("file/")) {
				continue;
			}
			String text = id + " " + p.getTitle().getString() + " " + p.getDescription().getString();
			if (XMAS.matcher(text).find()) {
				out.add(p);
			}
		}
		out.sort((a, b) -> a.getTitle().getString().compareToIgnoreCase(b.getTitle().getString()));
		return out;
	}

	private static Set<String> packsOn() {
		if (packsOn == null) {
			Set<String> def = new LinkedHashSet<>();
			packsOn = read("xmas-packs-on.txt", null);
			if (packsOn == null) {
				// first time: every Christmas pack you have is on (pick in the list which ones you want)
				for (Pack p : packs()) {
					def.add(p.getId());
				}
				packsOn = def;
				write("xmas-packs-on.txt", packsOn);
			}
		}
		return packsOn;
	}

	private static Set<String> byUs() {
		if (packsByUs == null) {
			packsByUs = read("xmas-packs-by-kubo.txt", new LinkedHashSet<>());
		}
		return packsByUs;
	}

	public static boolean packOn(Pack p) {
		return packsOn().contains(p.getId());
	}

	public static void setPack(Pack p, boolean on) {
		if (on) {
			packsOn().add(p.getId());
		} else {
			packsOn().remove(p.getId());
		}
		write("xmas-packs-on.txt", packsOn());
	}

	/**
	 * Turns the chosen Christmas packs on (on top of yours) while Christmas World is on, and turns off again the ones
	 * Sleep turned on when it is off. Packs you turned on yourself are never touched. Reloads only when something changed.
	 */
	public static void apply(boolean modOn) {
		Minecraft mc = Minecraft.getInstance();
		if (dev.sleepclient.compat.Mc.overlay(mc) != null) {
			return;
		}
		var mgr = mc.getResourcePackRepository();
		List<String> enabled = new ArrayList<>(mgr.getSelectedIds());
		boolean changed = false;
		Set<String> want = modOn ? packsOn() : Set.of();
		for (String id : new ArrayList<>(byUs())) {
			if (!want.contains(id)) {
				if (enabled.remove(id)) {
					changed = true;
				}
				byUs().remove(id);
			}
		}
		for (String id : want) {
			if (!enabled.contains(id) && mgr.isAvailable(id)) {
				enabled.add(id); // last = on top
				byUs().add(id);
				changed = true;
			}
		}
		write("xmas-packs-by-kubo.txt", byUs());
		if (changed) {
			mgr.setSelected(enabled);
			mc.options.updateResourcePacks(mgr); // saves the list and reloads (with the normal loading screen)
		}
	}
}
