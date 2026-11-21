package dev.sleepclient.social;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * People you muted, kept only on this computer (config/kubo-muted-players.json): nothing is sent to any server. A muted friend
 * makes no pop-up card (messages, invites, "is online", "is playing") and no notification sound; you can still read their messages
 * in the chat when you open it yourself.
 */
public final class Mutes {

	private static final Gson GSON = new Gson();
	private static final Set<String> IDS = ConcurrentHashMap.newKeySet();
	private static boolean loaded;

	private Mutes() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-muted-players.json");
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
				if (o != null && o.has("muted")) {
					for (var e : o.getAsJsonArray("muted")) {
						IDS.add(e.getAsString().toLowerCase(Locale.ROOT));
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] muted players: " + e);
		}
	}

	private static synchronized void save() {
		try {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			IDS.forEach(a::add);
			o.add("muted", a);
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] could not save muted players: " + e);
		}
	}

	private static String key(Social.User u) {
		return u == null || u.id == null ? null : u.id.toLowerCase(Locale.ROOT);
	}

	public static boolean isMuted(Social.User u) {
		load();
		String k = key(u);
		return k != null && IDS.contains(k);
	}

	public static void toggle(Social.User u) {
		load();
		String k = key(u);
		if (k == null) {
			return;
		}
		boolean now = IDS.add(k);
		if (!now) {
			IDS.remove(k);
		}
		save();
		Social.say(now ? u.display() + dev.sleepclient.Lang.tr(" muted (only on this client)", " silenciado (só neste client)")
				: u.display() + dev.sleepclient.Lang.tr(" unmuted", " já não está silenciado"), false);
	}
}
