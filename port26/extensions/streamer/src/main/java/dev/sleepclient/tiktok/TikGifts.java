package dev.sleepclient.tiktok;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pictures and prices of TikTok gifts, by name. Learnt from the live (the full gift list when the Automatic source
 * connects, and the picture sent with every gift), remembered in config/kubo-tiktok-gifts.json so the icons are there
 * the next time even before you go live. Pictures are downloaded once per session on a background thread.
 */
public final class TikGifts {

	private record Info(String url, int cost, String name, boolean custom) {
	}

	/** One gift for the picker. */
	public record Gift(String name, int cost, boolean custom) {
	}

	/** Only names confirmed to exist: the real list comes from TikTok when a live connects (see {@link #loadFromLive}). */
	private static final String[] SEED = {"Rose", "TikTok", "Heart Me", "White Dove", "Mamma Mia", "Boxing Gloves", "Galaxy", "Lion"};

	/** Asks TikTok for the gift list of someone who is live right now (the same connection as the Automatic source), then leaves. */
	public static void loadFromLive(String user, java.util.function.Consumer<String> status) {
		Thread t = new Thread(() -> {
			try {
				status.accept("@" + user + "...");
				io.github.jwdeveloper.tiktok.client.TikTokLive.newClient(user).configure(c -> {
					c.setClientLanguage("en");
					c.setLogLevel(java.util.logging.Level.OFF);
					c.setPrintToConsole(false);
				}).onConnected((lc, e) -> {
					int n = 0;
					try {
						for (var g : lc.getGiftManager().toList()) {
							learn(g.getName(), g.getPicture() == null ? null : g.getPicture().getLink(), g.getDiamondCost());
							n++;
						}
					} catch (Throwable ignored) {
						// status below says 0
					}
					saveSoon();
					status.accept(n + " gifts");
					lc.disconnect();
				}).onError((lc, e) -> status.accept("@" + user + ": not live?")).build().connect();
			} catch (Throwable e) {
				status.accept("@" + user + ": not live?");
			}
		}, "kubo-gift-list");
		t.setDaemon(true);
		t.start();
	}

	/** Your own gift: a name (exactly as TikTok sends it), its coins and, if you want, a picture from your computer. */
	public static void addCustom(String name, int cost, Path picture) {
		load();
		String k = key(name);
		if (k.isEmpty()) {
			return;
		}
		String url = "";
		try {
			if (picture != null) {
				Path dir = FabricLoader.getInstance().getConfigDir().resolve("kubo-tiktok-gift-images");
				Files.createDirectories(dir);
				Path to = dir.resolve(k.replaceAll("[^a-z0-9_]", "_") + ".png");
				Files.copy(picture, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				url = to.toUri().toString();
			}
		} catch (Exception e) {
			url = "";
		}
		Info old = INFO.get(k);
		INFO.put(k, new Info(url.isEmpty() && old != null ? old.url : url, cost > 0 ? cost : old == null ? 0 : old.cost, name.trim(), true));
		TEX.remove(k);
		FAILED.remove(k);
		dirty = true;
		savedAt = 0;
		saveSoon();
	}

	public static void removeCustom(String name) {
		String k = key(name);
		Info i = INFO.get(k);
		if (i != null && i.custom) {
			INFO.remove(k);
			TEX.remove(k);
			dirty = true;
			savedAt = 0;
			saveSoon();
		}
	}

	/** Every gift known so far, cheapest first (from the live's full list, the gifts seen, and the well-known ones). */
	public static java.util.List<Gift> all() {
		load();
		Map<String, Gift> out = new java.util.LinkedHashMap<>();
		for (String s : SEED) {
			out.put(key(s), new Gift(s, cost(s), false));
		}
		for (var e : INFO.entrySet()) {
			Gift old = out.get(e.getKey());
			String name = e.getValue().name != null && !e.getValue().name.isEmpty() ? e.getValue().name : old != null ? old.name() : pretty(e.getKey());
			out.put(e.getKey(), new Gift(name, e.getValue().cost, e.getValue().custom));
		}
		java.util.List<Gift> list = new java.util.ArrayList<>(out.values());
		list.sort(java.util.Comparator.comparingInt(Gift::cost).thenComparing(Gift::name, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	private static String pretty(String k) {
		return k.isEmpty() ? k : Character.toUpperCase(k.charAt(0)) + k.substring(1);
	}

	private static final Map<String, Info> INFO = new ConcurrentHashMap<>();
	private static final Map<String, Identifier> TEX = new ConcurrentHashMap<>();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
	private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
	private static volatile boolean loaded, dirty;
	private static long savedAt;

	private TikGifts() {
	}

	/** "Paper Crane" -> "papercrane": names are compared without case, spaces or symbols. */
	public static String key(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
	}

	/** Remembers a gift's picture / price (either may be empty / 0). */
	public static void learn(String name, String url, int cost) {
		String k = key(name);
		if (k.isEmpty()) {
			return;
		}
		load();
		Info old = INFO.get(k);
		String u = url != null && url.startsWith("http") && (old == null || !old.custom) ? url : old == null ? "" : old.url;
		int c = cost > 0 ? cost : old == null ? 0 : old.cost;
		String shown = name.trim();
		if (old == null || !old.url.equals(u) || old.cost != c || !shown.equals(old.name)) {
			INFO.put(k, new Info(u, c, shown, old != null && old.custom));
			dirty = true;
			if (old != null && !old.url.equals(u)) {
				FAILED.remove(k);
			}
		}
	}

	/** Coins of one gift, or 0 when not known yet. */
	public static int cost(String name) {
		load();
		Info i = INFO.get(key(name));
		return i == null ? 0 : i.cost;
	}

	/** The gift's picture as a texture, or null while it is not known / still downloading. Call on the render thread. */
	public static Identifier icon(String name) {
		load();
		String k = resolve(key(name));
		Identifier id = TEX.get(k);
		if (id != null) {
			return id;
		}
		Info i = INFO.get(k);
		if (i == null || i.url.isEmpty() || FAILED.contains(k) || !LOADING.add(k)) {
			return null;
		}
		String url = i.url;
		Thread t = new Thread(() -> download(k, url), "kubo-gift-icon");
		t.setDaemon(true);
		t.start();
		return null;
	}

	/** The known gift for a name: the same name, else one whose name contains it (or is contained in it). */
	private static String resolve(String k) {
		if (k.isEmpty() || INFO.containsKey(k)) {
			return k;
		}
		if (k.length() >= 5) {
			for (String o : INFO.keySet()) {
				if (o.contains(k) || (o.length() >= 5 && k.contains(o))) {
					return o;
				}
			}
		}
		return k;
	}

	/** Saves what was learnt (call now and then, e.g. every few seconds). */
	public static void saveSoon() {
		long now = System.currentTimeMillis();
		if (!dirty || now - savedAt < 5000L) {
			return;
		}
		savedAt = now;
		dirty = false;
		JsonObject o = new JsonObject();
		for (var e : INFO.entrySet()) {
			JsonObject g = new JsonObject();
			g.addProperty("url", e.getValue().url);
			g.addProperty("cost", e.getValue().cost);
			g.addProperty("name", e.getValue().name == null ? "" : e.getValue().name);
			g.addProperty("custom", e.getValue().custom);
			o.add(e.getKey(), g);
		}
		String json = o.toString();
		Thread t = new Thread(() -> {
			try {
				Files.writeString(file(), json, StandardCharsets.UTF_8);
			} catch (Exception ignored) {
				// only a cache
			}
		}, "kubo-gift-cache");
		t.setDaemon(true);
		t.start();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-tiktok-gifts.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path f = file();
			if (!Files.exists(f)) {
				return;
			}
			JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				return;
			}
			for (var e : el.getAsJsonObject().entrySet()) {
				if (e.getValue().isJsonObject()) {
					JsonObject g = e.getValue().getAsJsonObject();
					String u = g.has("url") ? g.get("url").getAsString() : "";
					int c = g.has("cost") ? g.get("cost").getAsInt() : 0;
					INFO.putIfAbsent(e.getKey(), new Info(u, c, g.has("name") ? g.get("name").getAsString() : "", g.has("custom") && g.get("custom").getAsBoolean()));
				}
			}
		} catch (Exception ignored) {
			// broken cache: start again
		}
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

	private static void download(String k, String url) {
		try {
			byte[] png = null;
			if (url.startsWith("file:")) {
				png = toPng(Files.readAllBytes(Path.of(URI.create(url))));
			}
			// TikTok sends .webp pictures, which Minecraft cannot read: its image server also gives the same picture as PNG
			String asPng = url.replace("~tplv-obj.webp", "~tplv-obj.png").replace(".webp", ".png");
			for (String u : png != null ? new String[0] : asPng.equals(url) ? new String[] {url} : new String[] {asPng, url}) {
				byte[] b = get(u);
				if (b != null) {
					png = toPng(b);
					if (png != null) {
						break;
					}
				}
			}
			if (png == null) {
				FAILED.add(k);
				return;
			}
			NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				Identifier id = Identifier.fromNamespaceAndPath("sleep_client", "tiktok_gift/" + k.replaceAll("[^a-z0-9_]", "_") + "_" + Integer.toHexString(k.hashCode()));
				mc.getTextureManager().register(id, new DynamicTexture(() -> "kubo_gift_" + k, img));
				TEX.put(k, id);
			});
		} catch (Throwable e) {
			FAILED.add(k);
		} finally {
			LOADING.remove(k);
		}
	}

	private static byte[] get(String url) {
		try {
			HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("User-Agent", "Mozilla/5.0").GET().build();
			HttpResponse<byte[]> res = HTTP.send(r, HttpResponse.BodyHandlers.ofByteArray());
			return res.statusCode() / 100 == 2 && res.body().length > 0 && res.body().length < 4_000_000 ? res.body() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** PNG bytes as they are; anything else Java can read (JPEG, GIF...) converted to PNG; null if unreadable. */
	private static byte[] toPng(byte[] b) {
		if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return b;
		}
		try {
			java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(b));
			if (bi == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			javax.imageio.ImageIO.write(bi, "png", out);
			return out.toByteArray();
		} catch (Exception e) {
			return null;
		}
	}
}
