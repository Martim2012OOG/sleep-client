package dev.sleepclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sleepclient.emote.Animation;
import dev.sleepclient.emote.EmoteLibrary;
import dev.sleepclient.emote.EmoteSlots;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Data for the 3D party lobby (the web page): friends with their real skins, the emotes you bought (icon + animation) and
 * the animation of an emote somebody else played. Skins are downloaded in the background and handed to the page as data
 * addresses (the page can not read pictures from other sites).
 */
public final class LobbyData {

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final Map<String, String> SKINS = new ConcurrentHashMap<>();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
	private static final Map<String, JsonObject> ANIMS = new ConcurrentHashMap<>();
	private static final Map<String, String> ICONS = new ConcurrentHashMap<>();
	private static final int MAX_TICKS = 400;

	private LobbyData() {
	}

	// ------------------------------------------------------------------ skins
	/** The skin as a data address, or null until it has been downloaded (asks for it the first time). */
	public static String skin(String key, List<String> urls) {
		String have = SKINS.get(key);
		if (have != null) {
			return have.isEmpty() ? null : have;
		}
		if (LOADING.add(key)) {
			List<String> copy = new ArrayList<>();
			for (String u : urls) {
				if (u != null && !u.isBlank()) {
					copy.add(u);
				}
			}
			Social.IO.execute(() -> {
				String out = "";
				for (String u : copy) {
					try {
						HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).header("User-Agent", "SleepClient/1.0").build(),
								HttpResponse.BodyHandlers.ofByteArray());
						byte[] b = r.body();
						if (r.statusCode() == 200 && b.length > 100 && b.length < 200_000 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') {
							out = "data:image/png;base64," + Base64.getEncoder().encodeToString(b);
							break;
						}
					} catch (Exception ignored) {
						// next mirror
					}
				}
				SKINS.put(key, out);
				if (out.isEmpty()) {
					// try again in a minute
					Social.IO.execute(() -> {
						try {
							Thread.sleep(60_000L);
						} catch (InterruptedException ignored) {
							return;
						}
						SKINS.remove(key);
						LOADING.remove(key);
					});
				}
			});
		}
		return null;
	}

	private static List<String> mirrors(String name, String uuid) {
		List<String> l = new ArrayList<>();
		l.add("https://mc-heads.net/skin/" + name);
		l.add("https://minotar.net/skin/" + name);
		String id = uuid == null ? "" : uuid.replace("-", "");
		if (id.length() == 32) {
			l.add("https://crafatar.com/skins/" + id);
		}
		return l;
	}

	/** Your own skin: the one equipped in Sleep, otherwise your account's. */
	public static String mySkin() {
		String name = Party.myNick();
		List<String> urls = new ArrayList<>();
		byte[] eq = dev.sleepclient.skin.SkinChanger.activePng();
		if (eq != null && eq.length > 100) {
			String k = "me-eq:" + java.util.Arrays.hashCode(eq);
			String d = SKINS.get(k);
			if (d == null) {
				d = "data:image/png;base64," + Base64.getEncoder().encodeToString(eq);
				SKINS.put(k, d);
			}
			return d;
		}
		String own = dev.sleepclient.skin.SkinChanger.ownSkinAddress();
		if (own != null) {
			urls.add(own);
		}
		java.util.UUID id = Minecraft.getInstance().getUser().getProfileId();
		if (id != null) {
			String site = Social.skinUrl(id);
			if (site != null) {
				urls.add(site);
			}
		}
		urls.addAll(mirrors(name, id == null ? null : id.toString()));
		return skin("me:" + name.toLowerCase(java.util.Locale.ROOT), urls);
	}

	// ------------------------------------------------------------------ people
	private static boolean validName(String n) {
		return n != null && n.matches("[A-Za-z0-9_]{1,16}");
	}

	/** Every friend (and party member who is not a friend): name, online, skin, and party state (offline / invited / joined). */
	public static JsonArray people() {
		JsonArray out = new JsonArray();
		java.util.Set<String> seen = new java.util.HashSet<>();
		String me = Party.myNick().toLowerCase(java.util.Locale.ROOT);
		for (Social.User u : Social.friends) {
			Party.Member pm = null;
			for (Party.Member m : Party.MEMBERS) {
				if (u.id != null && u.id.equals(m.friendId) || u.mcUsername != null && u.mcUsername.equalsIgnoreCase(m.nick)) {
					pm = m;
					break;
				}
			}
			String name = pm != null ? pm.nick : u.mcUsername != null && !u.mcUsername.isEmpty() ? u.mcUsername : u.display();
			if (!validName(name) || name.toLowerCase(java.util.Locale.ROOT).equals(me) || !seen.add(name.toLowerCase(java.util.Locale.ROOT))) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("name", name);
			o.addProperty("online", u.online());
			o.addProperty("state", pm != null ? "joined" : Party.invited(u.id) ? "invited" : "offline");
			List<String> urls = new ArrayList<>();
			urls.add(Social.friendSkin(u));
			urls.addAll(mirrors(name, u.mcUuid));
			String sk = skin("f:" + name.toLowerCase(java.util.Locale.ROOT), urls);
			if (sk != null) {
				o.addProperty("skin", sk);
			}
			out.add(o);
		}
		for (Party.Member m : Party.MEMBERS) {
			String name = m.nick;
			if (!validName(name) || name.toLowerCase(java.util.Locale.ROOT).equals(me) || !seen.add(name.toLowerCase(java.util.Locale.ROOT))) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("name", name);
			o.addProperty("online", true);
			o.addProperty("state", "joined");
			String sk = skin("f:" + name.toLowerCase(java.util.Locale.ROOT), mirrors(name, m.uuid));
			if (sk != null) {
				o.addProperty("skin", sk);
			}
			out.add(o);
		}
		return out;
	}

	// ------------------------------------------------------------------ emotes
	/** The emotes in your wheel (the ones you bought): id, name, icon and animation. Empty = the page keeps its emoji wheel. */
	public static JsonArray wheel() {
		JsonArray out = new JsonArray();
		for (int i = 0; i < EmoteSlots.SLOTS; i++) {
			EmoteLibrary.Info info = EmoteSlots.get(i);
			if (info == null) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("id", info.id());
			o.addProperty("n", info.name());
			String icon = icon(info);
			if (icon != null) {
				o.addProperty("icon", icon);
			}
			JsonObject anim = anim(info);
			if (anim != null) {
				o.add("anim", anim);
			}
			out.add(o);
		}
		return out;
	}

	public static JsonObject animFor(String id) {
		for (EmoteLibrary.Info i : EmoteLibrary.all()) {
			if (i.id().equals(id)) {
				return anim(i);
			}
		}
		return null;
	}

	private static String icon(EmoteLibrary.Info info) {
		if (!info.icon()) {
			return null;
		}
		String c = ICONS.get(info.id());
		if (c != null) {
			return c.isEmpty() ? null : c;
		}
		String d = "";
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(info.iconId());
			if (res.isPresent()) {
				try (var in = res.get().open()) {
					d = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
				}
			}
		} catch (Exception ignored) {
			// no icon: the wheel shows the name
		}
		ICONS.put(info.id(), d);
		return d.isEmpty() ? null : d;
	}

	/** The emote's keyframes sampled at 20 ticks per second, for the page: only the properties the emote really animates. */
	private static JsonObject anim(EmoteLibrary.Info info) {
		JsonObject cached = ANIMS.get(info.id());
		if (cached != null) {
			return cached;
		}
		Animation a = EmoteLibrary.animation(info);
		if (a == null) {
			return null; // a shop emote still downloading: asked again next time
		}
		int n = Math.max(2, Math.min(MAX_TICKS, a.lengthTicks()));
		float[][] s = new float[Animation.PARTS.length][Animation.PROPS.length];
		float[][][] samples = new float[n + 1][][];
		for (int t = 0; t <= n; t++) {
			a.sample(a.animTime(t), s);
			float[][] copy = new float[Animation.PARTS.length][];
			for (int p = 0; p < Animation.PARTS.length; p++) {
				copy[p] = s[p].clone();
			}
			samples[t] = copy;
		}
		JsonObject parts = new JsonObject();
		String[] props = {"x", "y", "z", "pitch", "yaw", "roll"};
		for (int p = 0; p <= Animation.LEFT_LEG; p++) {
			JsonObject po = new JsonObject();
			for (int q = 0; q < props.length; q++) {
				if (!a.has(p, q)) {
					continue;
				}
				JsonArray arr = new JsonArray();
				for (int t = 0; t <= n; t++) {
					arr.add(Math.round(samples[t][p][q] * 1000F) / 1000F);
				}
				po.add(props[q], arr);
			}
			if (po.size() > 0) {
				parts.add(Animation.PARTS[p], po);
			}
		}
		JsonObject o = new JsonObject();
		o.addProperty("n", n);
		o.addProperty("loop", a.loop);
		o.add("p", parts);
		ANIMS.put(info.id(), o);
		return o;
	}
}
