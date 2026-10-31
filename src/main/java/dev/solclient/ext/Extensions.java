package dev.solclient.ext;

import dev.solclient.mods.Mod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Sleep extensions. They all ship inside the Sleep jar (nested jars), but an extension does nothing until the player
 * installs it in Mods > Extensions: before that its mods are not even created, so it costs no FPS and no memory.
 * Installing / removing works straight away, no restart, and nothing is downloaded: no website is involved.
 * The installed ones are remembered in config/kubo-extensions.txt (one mod id per line).
 */
public final class Extensions {

	/** One extension (a card in Mods > Extensions, and a folder in the mod list once installed). */
	public static final class Item {
		public String id = "", modId = "", name = "", description = "", descriptionPt = "", version = "", icon = "", author = "";
		public final List<String> mods = new ArrayList<>();
		public final List<String> tags = new ArrayList<>();
		EntrypointContainer<KuboExtension> container;

		/** Description in the client's language. */
		public String text() {
			return dev.solclient.Lang.pt() && !descriptionPt.isEmpty() ? descriptionPt : dev.solclient.Lang.t(description);
		}

		public boolean matches(String q) {
			if (q == null || q.isBlank()) {
				return true;
			}
			String s = q.toLowerCase(Locale.ROOT).trim();
			if (name.toLowerCase(Locale.ROOT).contains(s) || description.toLowerCase(Locale.ROOT).contains(s)
					|| descriptionPt.toLowerCase(Locale.ROOT).contains(s) || id.contains(s)) {
				return true;
			}
			for (String m : mods) {
				if (m.toLowerCase(Locale.ROOT).contains(s)) {
					return true;
				}
			}
			return false;
		}
	}

	public enum State {
		NOT_INSTALLED, DOWNLOADING, RESTART_TO_ADD, INSTALLED, UPDATE, RESTART_TO_REMOVE
	}

	public static volatile String status = "";
	public static volatile String downloading;
	public static volatile float progress;

	private static final List<Item> items = new ArrayList<>();
	private static final List<String> loaded = new ArrayList<>();
	/** Mods each installed extension added, by fabric mod id. */
	private static final Map<String, List<Mod>> modsOf = new ConcurrentHashMap<>();
	private static final Map<String, List<KuboExtension>> instances = new ConcurrentHashMap<>();
	private static final Set<String> installed = new LinkedHashSet<>();
	/** The folder cards of the installed extensions (cached, so the menu keeps the same objects). */
	private static final Map<String, dev.solclient.mods.ExtFolder> folders = new ConcurrentHashMap<>();

	private Extensions() {
	}

	// ------------------------------------------------------------------ start-up
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-extensions.txt");
	}

	private static void saveInstalled() {
		try {
			Files.writeString(file(), String.join("\n", installed) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[Sleep] could not save the installed extensions: " + e);
		}
	}

	private static String shortId(String modId) {
		return modId.startsWith("kubo_ext_") ? modId.substring(9).replace('_', '-') : modId.replace('_', '-');
	}

	/**
	 * Finds the extensions inside the jar and starts the installed ones. Called by SolClient before the settings load.
	 * The ones that are not installed are only listed: no mod of theirs exists yet.
	 */
	public static void loadInstalled() {
		List<EntrypointContainer<KuboExtension>> list;
		try {
			list = FabricLoader.getInstance().getEntrypointContainers("kubo", KuboExtension.class);
		} catch (Throwable t) {
			System.err.println("[Sleep] extensions: " + t);
			return;
		}
		try {
			if (Files.isRegularFile(file())) {
				for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
					if (!l.isBlank()) {
						installed.add(l.trim());
					}
				}
			}
		} catch (IOException e) {
			System.err.println("[Sleep] extensions: " + e);
		}
		for (EntrypointContainer<KuboExtension> c : list) {
			var md = c.getProvider().getMetadata();
			Item it = new Item();
			it.modId = md.getId();
			it.id = shortId(it.modId);
			String n = md.getName();
			for (String p : new String[] {"Sleep – ", "Sleep - ", "Sleep: "}) {
				if (n.startsWith(p)) {
					n = n.substring(p.length());
				}
			}
			it.name = n;
			it.description = md.getDescription();
			it.version = md.getVersion().getFriendlyString();
			it.container = c;
			items.add(it);
		}
		items.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		for (Item it : items) {
			if (installed.contains(it.modId)) {
				activate(it, false);
			}
		}
	}

	/** Runs the extension's register(): its mods are created and join the list. Returns the mods it added. */
	private static List<Mod> activate(Item it, boolean live) {
		try {
			List<Mod> mods = dev.solclient.SolClient.MODS;
			int before = mods.size();
			KuboExtension e = it.container.getEntrypoint();
			e.register(KuboApi.INSTANCE);
			instances.computeIfAbsent(it.modId, k -> new ArrayList<>()).add(e);
			List<Mod> added = new ArrayList<>(mods.subList(before, mods.size()));
			modsOf.put(it.modId, added);
			loaded.add(it.modId);
			it.mods.clear();
			for (Mod m : added) {
				if (m.parent == null && !m.inSettings) {
					it.mods.add(m.name);
				}
			}
			if (live) {
				for (Mod m : added) {
					m.enabled = m.locked; // an installed extension never switches its mods on by itself: the player decides
				}
				dev.solclient.Config.load(added);
				for (Mod m : added) {
					m.init();
				}
				dev.solclient.Config.save();
			}
			System.out.println("[Sleep] extension started: " + it.modId + " (" + added.size() + " mods)");
			return added;
		} catch (Throwable t) {
			System.err.println("[Sleep] extension " + it.modId + " failed: " + t);
			status = it.name + ": error";
			return null;
		}
	}

	/** True when this mod was added by an extension (those never start switched on by themselves). */
	public static boolean isFromExtension(Mod m) {
		return ownerOf(m) != null;
	}

	/** Fabric mod id of the extension that added this mod, or null. */
	public static String ownerOf(Mod m) {
		for (Map.Entry<String, List<Mod>> e : modsOf.entrySet()) {
			if (e.getValue().contains(m)) {
				return e.getKey();
			}
		}
		return null;
	}

	public static List<Mod> modsOf(String modId) {
		List<Mod> l = modsOf.get(modId);
		return l == null ? List.of() : l;
	}

	public static Item byModId(String modId) {
		for (Item it : items) {
			if (it.modId.equals(modId)) {
				return it;
			}
		}
		return null;
	}

	/** The folder cards for the mod list (installed extensions only). */
	public static List<dev.solclient.mods.ExtFolder> folders() {
		List<dev.solclient.mods.ExtFolder> out = new ArrayList<>();
		for (Item it : items) {
			if (modsOf.containsKey(it.modId)) {
				out.add(folders.computeIfAbsent(it.modId, k -> new dev.solclient.mods.ExtFolder(it.modId, it.id, it.name, it.text())));
			}
		}
		return out;
	}

	public static List<String> loaded() {
		return loaded;
	}

	// ------------------------------------------------------------------ the list
	public static State state(Item it) {
		return modsOf.containsKey(it.modId) ? State.INSTALLED : State.NOT_INSTALLED;
	}

	public static List<Item> catalog() {
		return items;
	}

	public static boolean fetching() {
		return false;
	}

	public static void refresh() {
	}

	public static boolean restartNeeded() {
		return false;
	}

	// ------------------------------------------------------------------ install / remove (instant)
	public static void install(Item it) {
		if (modsOf.containsKey(it.modId)) {
			return;
		}
		List<Mod> added = activate(it, true);
		if (added == null) {
			return;
		}
		installed.add(it.modId);
		saveInstalled();
		status = it.name + " " + dev.solclient.Lang.tr("installed - find it as a folder in All Mods", "instalada - está numa pasta em Todos os mods");
	}

	public static void remove(Item it) {
		List<Mod> mods = modsOf.remove(it.modId);
		List<KuboExtension> exts = instances.remove(it.modId);
		if (exts != null) {
			for (KuboExtension e : exts) {
				try {
					e.unload();
				} catch (Throwable t) {
					System.err.println("[Sleep] unload " + it.modId + ": " + t);
				}
			}
		}
		if (mods != null) {
			for (Mod m : mods) {
				try {
					m.setEnabled(false);
				} catch (Throwable ignored) {
				}
				dev.solclient.SolClient.MODS.remove(m);
			}
		}
		loaded.remove(it.modId);
		folders.remove(it.modId);
		installed.remove(it.modId);
		saveInstalled();
		dev.solclient.Config.save();
		status = it.name + " " + dev.solclient.Lang.tr("removed", "removida");
	}
}
