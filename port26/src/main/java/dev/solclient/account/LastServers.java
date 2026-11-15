package dev.solclient.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** The last servers you played on (title screen list with a play button). Saved in config/plegumo-servers.json. */
public final class LastServers {

	public record Entry(String name, String address, long at) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX = 6;
	private static final List<Entry> LIST = new ArrayList<>();
	private static boolean loaded;
	private static String lastSeen;

	private LastServers() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-servers.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path p = file();
			if (!Files.exists(p)) {
				return;
			}
			JsonElement root = JsonParser.parseString(Files.readString(p));
			if (!root.isJsonArray()) {
				return;
			}
			for (JsonElement e : root.getAsJsonArray()) {
				JsonObject o = e.getAsJsonObject();
				LIST.add(new Entry(o.get("name").getAsString(), o.get("address").getAsString(), o.has("at") ? o.get("at").getAsLong() : 0));
			}
		} catch (Exception e) {
			System.err.println("[Sleep] Could not read last servers: " + e);
		}
	}

	private static void save() {
		JsonArray a = new JsonArray();
		for (Entry e : LIST) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e.name());
			o.addProperty("address", e.address());
			o.addProperty("at", e.at());
			a.add(o);
		}
		try {
			Files.writeString(file(), GSON.toJson(a));
		} catch (Exception e) {
			System.err.println("[Sleep] Could not save last servers: " + e);
		}
	}

	public static List<Entry> list() {
		load();
		return Collections.unmodifiableList(new ArrayList<>(LIST));
	}

	public static void remove(Entry e) {
		load();
		LIST.remove(e);
		save();
	}

	/** Every client tick: remembers the server you are on. */
	public static void tick(Minecraft mc) {
		ServerData s = mc.level == null ? null : mc.getCurrentServer();
		String addr = s == null || mc.isLocalServer() ? null : s.ip;
		if (addr == null || addr.isBlank()) {
			lastSeen = null;
			return;
		}
		if (addr.equals(lastSeen)) {
			return;
		}
		lastSeen = addr;
		load();
		String name = s.name == null || s.name.isBlank() ? addr : s.name;
		LIST.removeIf(e -> e.address().equalsIgnoreCase(addr));
		LIST.add(0, new Entry(name, addr, System.currentTimeMillis()));
		while (LIST.size() > MAX) {
			LIST.remove(LIST.size() - 1);
		}
		save();
	}
}
