package dev.solclient.tiktok;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Pictures and prices of TikTok gifts, by name. Learnt from the live (the full gift list when the Automatic source
 * connects, and the picture sent with every gift), remembered in config/kubo-tiktok-gifts.json so the icons are there
 * the next time even before you go live. Pictures are downloaded once per session on a background thread.
 */
public final class TikGifts {

	private record Info(String url, int cost) {
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
		String u = url != null && url.startsWith("http") ? url : old == null ? "" : old.url;
		int c = cost > 0 ? cost : old == null ? 0 : old.cost;
		if (old == null || !old.url.equals(u) || old.cost != c) {
			INFO.put(k, new Info(u, c));
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
		String k = key(name);
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
					INFO.putIfAbsent(e.getKey(), new Info(u, c));
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
			// TikTok sends .webp pictures, which Minecraft cannot read: its image server also gives the same picture as PNG
			String asPng = url.replace("~tplv-obj.webp", "~tplv-obj.png").replace(".webp", ".png");
			for (String u : asPng.equals(url) ? new String[] {url} : new String[] {asPng, url}) {
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
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.execute(() -> {
				Identifier id = Identifier.of("sol_client", "tiktok_gift/" + k.replaceAll("[^a-z0-9_]", "_") + "_" + Integer.toHexString(k.hashCode()));
				mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "kubo_gift_" + k, img));
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
