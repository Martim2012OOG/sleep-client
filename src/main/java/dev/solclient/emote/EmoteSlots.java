package dev.solclient.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** The 8 emotes equipped on the wheel (chosen in the Locker), saved in config/plegumo-emote-wheel.json. */
public final class EmoteSlots {

	public static final int SLOTS = 8;
	private static final String[] IDS = new String[SLOTS];
	private static boolean loaded;

	private EmoteSlots() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-emote-wheel.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path f = file();
		boolean ok = false;
		try {
			if (Files.exists(f)) {
				JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				JsonArray a = root.getAsJsonObject().getAsJsonArray("slots");
				for (int i = 0; i < SLOTS && i < a.size(); i++) {
					IDS[i] = a.get(i).isJsonNull() ? null : a.get(i).getAsString();
				}
				ok = true;
			}
		} catch (Exception e) {
			System.err.println("[Caos] could not read the emote wheel: " + e);
		}
		if (ok) {
			// emotes that no longer exist (the library was replaced) leave the wheel
			boolean any = false, changed = false;
			for (int i = 0; i < SLOTS; i++) {
				if (IDS[i] != null && !exists(IDS[i])) {
					IDS[i] = null;
					changed = true;
				}
				any |= IDS[i] != null;
			}
			if (any) {
				if (changed) {
					save();
				}
				return;
			}
			ok = false;
		}
		if (!ok) {
			// first start: the first 8 emotes you own
			List<EmoteLibrary.Info> all = EmoteLibrary.mine();
			for (int i = 0; i < SLOTS; i++) {
				IDS[i] = i < all.size() ? all.get(i).id() : null;
			}
			save();
		}
	}

	private static boolean exists(String id) {
		for (EmoteLibrary.Info i : EmoteLibrary.all()) {
			if (i.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static void save() {
		try {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			for (String id : IDS) {
				a.add(id);
			}
			o.add("slots", a);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[Caos] could not save the emote wheel: " + e);
		}
	}

	/** The emote in a slot, or null when the slot is empty (or the emote no longer exists). */
	public static EmoteLibrary.Info get(int slot) {
		load();
		if (slot < 0 || slot >= SLOTS || IDS[slot] == null) {
			return null;
		}
		for (EmoteLibrary.Info i : EmoteLibrary.all()) {
			if (i.id().equals(IDS[slot])) {
				return EmoteLibrary.owned(i.id()) ? i : null; // only emotes you bought
			}
		}
		return null;
	}

	/** Slot holding this emote, or -1. */
	public static int slotOf(String id) {
		load();
		for (int i = 0; i < SLOTS; i++) {
			if (id != null && id.equals(IDS[i])) {
				return i;
			}
		}
		return -1;
	}

	/** Puts the emote in a slot (moving it if it was already in another one). */
	public static void set(int slot, String id) {
		load();
		if (slot < 0 || slot >= SLOTS) {
			return;
		}
		int old = slotOf(id);
		if (old >= 0) {
			IDS[old] = IDS[slot]; // swap
		}
		IDS[slot] = id;
		save();
	}

	public static void clear(int slot) {
		load();
		if (slot >= 0 && slot < SLOTS) {
			IDS[slot] = null;
			save();
		}
	}

	/** First empty slot, or -1. */
	public static int firstFree() {
		load();
		for (int i = 0; i < SLOTS; i++) {
			if (get(i) == null) {
				return i;
			}
		}
		return -1;
	}
}
