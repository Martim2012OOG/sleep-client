package dev.sleepclient.social;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Skins on worlds that allow non-premium accounts (like Host World): such servers send players without any skin data, so
 * everybody looks like a default skin. Here the skin is looked up by the player's name, once, in the background.
 */
public final class HostedSkins {

	private static final Map<UUID, Object> CACHE = new ConcurrentHashMap<>();
	private static final Object NONE = new Object();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private HostedSkins() {
	}

	/** The real skin of a player the server sent without one, or null to keep what the game has. */
	public static PlayerSkin lookup(GameProfile p) {
		if (p == null || p.id() == null || p.id().version() != 3 || p.properties().containsKey("textures")) {
			return null; // version 3 = "offline" id: real accounts on normal servers are never touched
		}
		Object v = CACHE.get(p.id());
		if (v instanceof PlayerSkin t) {
			return t;
		}
		if (v == NONE || v instanceof Long at && System.currentTimeMillis() < at) {
			return null;
		}
		String name = p.name();
		if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
			CACHE.put(p.id(), NONE);
			return null;
		}
		UUID id = p.id();
		CACHE.put(id, System.currentTimeMillis() + 60_000L); // busy / try again in a minute if it fails
		Social.IO.execute(() -> fetch(id, name));
		return null;
	}

	private static void fetch(UUID offlineId, String name) {
		try {
			HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
					.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 404 || r.statusCode() == 204) {
				CACHE.put(offlineId, NONE); // a name without a Minecraft account: default skin
				return;
			}
			if (r.statusCode() != 200) {
				return;
			}
			String hex = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
			UUID real = UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
			Minecraft mc = Minecraft.getInstance();
			var result = mc.services().sessionService().fetchProfile(real, true);
			if (result == null) {
				return;
			}
			mc.getSkinManager().get(result.profile()).thenAccept(o -> CACHE.put(offlineId, o.isPresent() ? o.get() : NONE));
		} catch (Exception e) {
			System.err.println("[Sleep] skin of " + name + ": " + e);
		}
	}
}
