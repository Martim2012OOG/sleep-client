package dev.solclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.social.Social;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Your own font for the menus and HUD (Settings > Font > "Custom").
 *
 * <p>Minecraft only loads fonts from resource packs, so importing a font means: keep a copy in {@code kubo-fonts/},
 * write a tiny resource pack ({@code resourcepacks/kubo-font-<time>.zip}) that holds the font, turn that pack on and
 * reload. Nothing here touches the game's internals: only the same pack calls the Sleep pack updater already uses.
 */
public final class CustomFont {

	/** The font id the pack defines (assets/sol_client/font/ui_custom.json). */
	public static final String FONT_ID = "sol_client:ui_custom";
	private static final String PACK_PREFIX = "kubo-font-";
	private static final long MAX_BYTES = 16L * 1024 * 1024;
	private static final String FONT_JSON = "{\"providers\":["
			+ "{\"type\":\"ttf\",\"file\":\"sol_client:custom_font.ttf\",\"size\":9.0,\"oversample\":8.0,\"shift\":[0.0,0.0],\"skip\":\"\"},"
			+ "{\"type\":\"reference\",\"id\":\"sol_client:ui\"}]}";

	private static boolean cachedInstalled;
	private static long cachedAt;
	private static String status = "";
	private static long statusAt;
	private static String activeName;

	private CustomFont() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** The folder where your font files live (next to the mods folder). */
	public static Path folder() {
		return mc().gameDirectory.toPath().resolve("kubo-fonts");
	}

	/** True while the font pack is switched on (checked at most once a second). */
	public static boolean installed() {
		long now = System.currentTimeMillis();
		if (now - cachedAt > 1000L) {
			cachedAt = now;
			boolean ok = false;
			try {
				for (String id : mc().getResourcePackRepository().getSelectedIds()) {
					if (id.startsWith("file/" + PACK_PREFIX)) {
						ok = true;
						break;
					}
				}
			} catch (RuntimeException ignored) {
				// the pack manager is not ready yet
			}
			cachedInstalled = ok;
		}
		return cachedInstalled;
	}

	/** What the settings row shows next to its button. */
	public static String statusText() {
		if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 7000L) {
			return status;
		}
		if (installed()) {
			String n = activeName();
			return n.isEmpty() ? "Custom font on" : "In use: " + n;
		}
		return "";
	}

	private static String activeName() {
		if (activeName == null) {
			try {
				Path f = folder().resolve("active.txt");
				activeName = Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8).trim() : "";
			} catch (IOException | RuntimeException e) {
				activeName = "";
			}
		}
		return activeName;
	}

	private static void say(String text, boolean error) {
		status = text;
		statusAt = System.currentTimeMillis();
		try {
			Social.say(text, error);
		} catch (RuntimeException ignored) {
			// the notification is only a bonus: the settings row shows the same text
		}
	}

	public static void openFolder() {
		try {
			Path d = folder();
			Files.createDirectories(d);
			net.minecraft.util.Util.getPlatform().openFile(d.toFile());
		} catch (IOException | RuntimeException e) {
			say("Could not open the folder: " + e.getMessage(), true);
		}
	}

	/** Uses the newest .ttf / .otf file that is in the kubo-fonts folder. */
	public static void useFolder() {
		try {
			Path dir = folder();
			Files.createDirectories(dir);
			List<Path> found;
			try (Stream<Path> s = Files.list(dir)) {
				found = s.filter(p -> {
					String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
					return Files.isRegularFile(p) && (n.endsWith(".ttf") || n.endsWith(".otf"));
				}).toList();
			}
			Path best = null;
			long bestTime = Long.MIN_VALUE;
			for (Path p : found) {
				long t = Files.getLastModifiedTime(p).toMillis();
				if (t > bestTime) {
					bestTime = t;
					best = p;
				}
			}
			if (best == null) {
				openFolder();
				say("Put a .ttf file in the folder, then click Apply", false);
				return;
			}
			importFile(best);
		} catch (IOException | RuntimeException e) {
			say("Could not read the folder: " + e.getMessage(), true);
		}
	}

	/** Imports one .ttf / .otf file and starts using it. Returns false (and says why) when it can't be used. */
	public static boolean importFile(Path src) {
		try {
			String fn = src.getFileName().toString();
			String low = fn.toLowerCase(Locale.ROOT);
			if (!(low.endsWith(".ttf") || low.endsWith(".otf"))) {
				say("That is not a font file (.ttf or .otf)", true);
				return false;
			}
			long size = Files.size(src);
			if (size < 1000L || size > MAX_BYTES) {
				say("The font file is too small or too big (max 16 MB)", true);
				return false;
			}
			byte[] data = Files.readAllBytes(src);
			if (!looksLikeFont(data)) {
				say("That file is not a valid font", true);
				return false;
			}
			String base = fn.substring(0, fn.length() - 4);
			Path dir = folder();
			Files.createDirectories(dir);
			Path copy = dir.resolve(fn);
			if (!copy.toAbsolutePath().normalize().equals(src.toAbsolutePath().normalize())) {
				Files.write(copy, data); // keep a copy so "Apply" works later
			}
			Files.writeString(dir.resolve("active.txt"), base, StandardCharsets.UTF_8);
			activeName = base;
			buildAndActivate(data, base);
			return true;
		} catch (IOException | RuntimeException e) {
			say("Could not import the font: " + e.getMessage(), true);
			return false;
		}
	}

	/** Switches back to the built-in fonts and turns the font pack off. */
	public static void remove() {
		Minecraft mc = mc();
		mc.execute(() -> {
			try {
				PackRepository pm = mc.getResourcePackRepository();
				pm.reload();
				List<String> on = new ArrayList<>(pm.getSelectedIds());
				boolean had = on.removeIf(s -> s.startsWith("file/" + PACK_PREFIX));
				if (SolClient.settings != null) {
					SolClient.settings.font.set(0);
				}
				Gfx.setFont(0);
				Config.save();
				cachedAt = 0L;
				if (had) {
					pm.setSelected(on);
					mc.options.updateResourcePacks(pm);
				}
				say("Back to the normal fonts", false);
			} catch (Throwable t) {
				say("Could not turn the font off: " + t.getMessage(), true);
			}
		});
	}

	// ------------------------------------------------------------------------------------------ the pack
	private static void buildAndActivate(byte[] ttf, String displayName) throws IOException {
		Minecraft mc = mc();
		Path packs = mc.gameDirectory.toPath().resolve("resourcepacks");
		Files.createDirectories(packs);
		cleanOldPacks(packs);
		String fileName = PACK_PREFIX + System.currentTimeMillis() + ".zip";
		Path tmp = packs.resolve(fileName + ".part");
		Path zip = packs.resolve(fileName);
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(tmp))) {
			put(z, "pack.mcmeta", mcmeta(displayName).getBytes(StandardCharsets.UTF_8));
			put(z, "assets/sol_client/font/ui_custom.json", FONT_JSON.getBytes(StandardCharsets.UTF_8));
			put(z, "assets/sol_client/font/custom_font.ttf", ttf);
		}
		Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
		final String id = "file/" + fileName;
		mc.execute(() -> {
			try {
				PackRepository pm = mc.getResourcePackRepository();
				pm.reload(); // the new zip shows up without restarting
				if (!pm.getAvailableIds().contains(id)) {
					say("The game did not find the font pack", true);
					return;
				}
				List<String> on = new ArrayList<>(pm.getSelectedIds());
				on.removeIf(s -> s.startsWith("file/" + PACK_PREFIX)); // the older font pack is replaced
				on.add(id);
				pm.setSelected(on);
				cachedAt = 0L;
				cachedInstalled = true;
				if (SolClient.settings != null) {
					SolClient.settings.font.set(Gfx.CUSTOM_FONT);
				}
				Gfx.setFont(Gfx.CUSTOM_FONT);
				Config.save();
				say("Font imported: " + displayName, false);
				mc.options.updateResourcePacks(pm); // saves the options and reloads the resources
			} catch (Throwable t) {
				say("Could not turn the font on: " + t.getMessage(), true);
			}
		});
	}

	/** Deletes older font packs that are not in use any more (a pack that is in use can't be deleted on Windows). */
	private static void cleanOldPacks(Path packs) {
		try (Stream<Path> s = Files.list(packs)) {
			List<String> enabled = new ArrayList<>(mc().getResourcePackRepository().getSelectedIds());
			for (Path p : s.toList()) {
				String n = p.getFileName().toString();
				if (n.startsWith(PACK_PREFIX) && (n.endsWith(".zip") || n.endsWith(".part")) && !enabled.contains("file/" + n)) {
					try {
						Files.deleteIfExists(p);
					} catch (IOException ignored) {
						// still open: it will be removed the next time
					}
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// leftovers are harmless
		}
	}

	private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
		z.putNextEntry(new ZipEntry(name));
		z.write(data);
		z.closeEntry();
	}

	/** The pack.mcmeta of the font pack: the same format numbers as the game itself, so it is never "incompatible". */
	public static String mcmeta(String name) {
		JsonObject pack = new JsonObject();
		JsonObject game = gamePack();
		if (game != null) {
			for (String k : new String[] {"pack_format", "min_format", "max_format", "supported_formats"}) {
				if (game.has(k)) {
					pack.add(k, game.get(k).deepCopy());
				}
			}
		}
		// Since 1.21.9 a pack newer than format 64 MUST say min_format AND max_format (a lone pack_format is refused: "missing mandatory
		// fields min_format and max_format", and the pack never loads). The game's own file can't be read (it is built into the game),
		// so this is the format of 1.21.11, open at the top so the next versions do not turn it "incompatible".
		if (!pack.has("min_format") || !pack.has("max_format")) {
			int fmt = 75;
			try {
				if (pack.has("pack_format") && pack.get("pack_format").isJsonPrimitive() && pack.get("pack_format").getAsInt() >= 65) {
					fmt = pack.get("pack_format").getAsInt();
				}
			} catch (RuntimeException ignored) {
				// keeps 75
			}
			if (!pack.has("min_format")) {
				pack.addProperty("min_format", fmt);
			}
			if (!pack.has("max_format")) {
				pack.addProperty("max_format", Math.max(fmt, 999));
			}
			pack.remove("pack_format");
			pack.remove("supported_formats");
		}
		pack.addProperty("description", "Sleep custom font: " + name);
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return root.toString();
	}

	/**
	 * Sleep's own generated packs (kubo-font-*.zip) made before this fix have a pack.mcmeta the game refuses, so they never loaded.
	 * Rewrites that one file, then lets the game see the pack again and switches the newest one on. Run once, off the game thread.
	 */
	public static void repairOldPacks() {
		try {
			Path packs = folder();
			if (!Files.isDirectory(packs)) {
				return;
			}
			boolean fixed = false;
			List<Path> zips = new ArrayList<>();
			try (var s = Files.list(packs)) {
				s.filter(p -> p.getFileName().toString().startsWith(PACK_PREFIX) && p.getFileName().toString().endsWith(".zip")).forEach(zips::add);
			}
			for (Path z : zips) {
				String meta = null;
				try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(z.toFile())) {
					var e = zf.getEntry("pack.mcmeta");
					meta = e == null ? null : new String(zf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
				} catch (Exception unreadable) {
					continue;
				}
				if (meta != null && meta.contains("min_format") && meta.contains("max_format")) {
					continue;
				}
				String name = "font";
				if (meta != null && meta.contains("Sleep custom font: ")) {
					int i = meta.indexOf("Sleep custom font: ") + "Sleep custom font: ".length();
					int j = meta.indexOf('"', i);
					name = j > i ? meta.substring(i, j) : name;
				}
				Path tmp = z.resolveSibling(z.getFileName() + ".part");
				try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(z.toFile());
						ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
					put(out, "pack.mcmeta", mcmeta(name).getBytes(StandardCharsets.UTF_8));
					var en = zf.entries();
					while (en.hasMoreElements()) {
						var e = en.nextElement();
						if (!e.isDirectory() && !e.getName().equals("pack.mcmeta")) {
							put(out, e.getName(), zf.getInputStream(e).readAllBytes());
						}
					}
				}
				Files.move(tmp, z, StandardCopyOption.REPLACE_EXISTING);
				fixed = true;
			}
			if (!fixed) {
				return;
			}
			final Path newest = zips.stream().max(java.util.Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
			mc().execute(() -> {
				try {
					PackRepository pm = mc().getResourcePackRepository();
					pm.reload();
					String id = newest == null ? null : "file/" + newest.getFileName();
					if (id != null && pm.getAvailableIds().contains(id) && !installed()) {
						List<String> on = new ArrayList<>(pm.getSelectedIds());
						on.removeIf(s -> s.startsWith("file/" + PACK_PREFIX));
						on.add(id);
						pm.setSelected(on);
						mc().options.updateResourcePacks(pm);
						cachedAt = 0L;
					}
				} catch (Throwable t) {
					System.err.println("[Sleep] font pack repair: " + t);
				}
			});
		} catch (Exception e) {
			System.err.println("[Sleep] font pack repair: " + e);
		}
	}

	private static JsonObject gamePack() {
		try {
			ModContainer c = FabricLoader.getInstance().getModContainer("minecraft").orElse(null);
			if (c == null) {
				return null;
			}
			for (Path root : c.getRootPaths()) {
				Path pm = root.resolve("pack.mcmeta");
				if (Files.isRegularFile(pm)) {
					JsonObject o = JsonParser.parseString(Files.readString(pm, StandardCharsets.UTF_8)).getAsJsonObject();
					if (o.has("pack") && o.get("pack").isJsonObject()) {
						return o.getAsJsonObject("pack");
					}
				}
			}
		} catch (Exception ignored) {
			// falls back to the default in mcmeta()
		}
		return null;
	}

	/** TrueType (00 01 00 00 / "true"), OpenType ("OTTO") or a font collection ("ttcf"). */
	private static boolean looksLikeFont(byte[] d) {
		if (d.length < 12) {
			return false;
		}
		int m = ((d[0] & 0xFF) << 24) | ((d[1] & 0xFF) << 16) | ((d[2] & 0xFF) << 8) | (d[3] & 0xFF);
		return m == 0x00010000 || m == 0x74727565 || m == 0x4F54544F || m == 0x74746366;
	}
}
