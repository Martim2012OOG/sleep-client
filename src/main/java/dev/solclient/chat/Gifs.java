package dev.solclient.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.social.SolarApi;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * GIFs in the friends chat. Search uses GIPHY straight from your game (needs a free GIPHY API key, see
 * config/plegumo-chat.json); a GIF message is just the GIF's link, so the site stores only a short text.
 * GIFs are decoded in the game (all frames) and played in the chat bubble.
 */
public final class Gifs {

	public record Result(String id, String preview, String url) {
	}

	/** A decoded GIF: all frames stacked vertically in one texture. */
	public record Anim(Identifier tex, int w, int h, int frames, int[] ends, int total) {
		public int frameAt(long ms) {
			if (frames <= 1 || total <= 0) {
				return 0;
			}
			long t = ms % total;
			for (int i = 0; i < frames; i++) {
				if (t < ends[i]) {
					return i;
				}
			}
			return frames - 1;
		}
	}

	private static final Pattern GIF_URL = Pattern.compile("^https://(media\\d?\\.giphy\\.com|i\\.giphy\\.com|media\\d?\\.tenor\\.com|c\\.tenor\\.com)/\\S+$|^https://\\S+\\.gif(\\?\\S*)?$");
	private static final Pattern GIPHY_PAGE = Pattern.compile("^https?://(www\\.)?giphy\\.com/(gifs|embed|stickers)/(?:\\S*-)?([A-Za-z0-9]+)/?(\\?.*)?$");
	private static final Pattern TENOR_PAGE = Pattern.compile("^https?://(www\\.)?tenor\\.com/(\\w+/)?view/\\S+$");
	private static final Pattern TENOR_MEDIA = Pattern.compile("https://media\\d?\\.tenor\\.com/[^\"'\\s]+?\\.gif");
	/** Built-in GIPHY key (empty = use config/plegumo-chat.json). */
	public static final String DEFAULT_KEY = "bIrK7Uqtbn1MJDKFOmwTkSAMwdzU6V8S";
	private static final java.util.Set<String> FAILED = ConcurrentHashMap.newKeySet();
	private static final Map<String, Anim> READY = new ConcurrentHashMap<>();
	private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, Long> USED = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75F, true));
	private static int counter;

	public static volatile List<Result> results = List.of();
	public static volatile String state = "";
	private static volatile String lastQuery;

	private Gifs() {
	}

	public static boolean isGif(String body) {
		return body != null && GIF_URL.matcher(body.trim()).matches();
	}

	public static boolean failed(String url) {
		return url != null && FAILED.contains(url.trim());
	}

	/** A GIPHY / Tenor page link pasted in the chat box? */
	public static boolean isGifPage(String text) {
		String t = text == null ? "" : text.trim();
		return GIPHY_PAGE.matcher(t).matches() || TENOR_PAGE.matcher(t).matches();
	}

	/** Turns a GIPHY / Tenor page link into the direct GIF link (Tenor needs one download of the page). */
	public static void resolvePage(String text, java.util.function.Consumer<String> done) {
		String t = text.trim();
		java.util.regex.Matcher g = GIPHY_PAGE.matcher(t);
		if (g.matches()) {
			done.accept("https://media.giphy.com/media/" + g.group(3) + "/200w.gif");
			return;
		}
		dev.solclient.social.Social.IO.execute(() -> {
			byte[] page = SolarApi.download(t, 3 * 1024 * 1024);
			String found = null;
			if (page != null) {
				java.util.regex.Matcher m = TENOR_MEDIA.matcher(new String(page, StandardCharsets.UTF_8));
				if (m.find()) {
					found = m.group();
				}
			}
			String f = found;
			MinecraftClient.getInstance().execute(() -> done.accept(f));
		});
	}

	// ------------------------------------------------------------------ key
	public static String key() {
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("plegumo-chat.json");
			if (!Files.exists(p)) {
				Files.writeString(p, "{\n  \"giphyKey\": \"\"\n}\n");
				return DEFAULT_KEY;
			}
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			String k = o.has("giphyKey") ? o.get("giphyKey").getAsString().trim() : "";
			return k.isEmpty() ? DEFAULT_KEY : k;
		} catch (Exception e) {
			return DEFAULT_KEY;
		}
	}

	// ------------------------------------------------------------------ search
	public static void search(String q) {
		String query = q == null ? "" : q.trim();
		if (query.equals(lastQuery) && !results.isEmpty()) {
			return;
		}
		lastQuery = query;
		String k = key();
		if (k.isEmpty()) {
			state = "GIF search needs a GIPHY key. You can still paste a GIF link from giphy.com or tenor.com in the chat box and send it.";
			results = List.of();
			return;
		}
		state = "Loading...";
		dev.solclient.social.Social.IO.execute(() -> {
			try {
				String u = query.isEmpty()
						? "https://api.giphy.com/v1/gifs/trending?limit=24&rating=pg-13&api_key=" + k
						: "https://api.giphy.com/v1/gifs/search?limit=24&rating=pg-13&api_key=" + k + "&q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
				byte[] data = SolarApi.download(u);
				if (!query.equals(lastQuery)) {
					return;
				}
				if (data == null) {
					state = "Could not load GIFs (check the GIPHY key)";
					return;
				}
				JsonObject o = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
				List<Result> out = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("data")) {
					JsonObject g = e.getAsJsonObject();
					JsonObject img = g.getAsJsonObject("images");
					String prev = url(img, "fixed_width_small_still", "fixed_width_still", "downsized_still");
					String send = url(img, "fixed_width_small", "fixed_width", "downsized");
					if (prev != null && send != null) {
						out.add(new Result(g.get("id").getAsString(), prev, send));
					}
				}
				results = out;
				state = out.isEmpty() ? "No GIFs found" : "";
			} catch (Exception ex) {
				state = "Could not load GIFs";
			}
		});
	}

	private static String url(JsonObject images, String... keys) {
		for (String k : keys) {
			if (images.has(k)) {
				JsonObject o = images.getAsJsonObject(k);
				if (o.has("url")) {
					String u = o.get("url").getAsString();
					int q = u.indexOf('?');
					return q > 0 ? u.substring(0, q) : u;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ decode + play
	/** The GIF of 'url' (downloads and decodes it the first time; null while loading). */
	public static Anim get(String url) {
		Anim a = READY.get(url);
		if (a != null) {
			USED.put(url, System.currentTimeMillis());
			return a;
		}
		Long t = PENDING.get(url);
		long now = System.currentTimeMillis();
		if (t != null && now - t < 60_000L) {
			return null;
		}
		PENDING.put(url, now);
		dev.solclient.social.Social.IO.execute(() -> decode(url));
		return null;
	}

	private static void decode(String url) {
		byte[] data = SolarApi.download(url, 12 * 1024 * 1024);
		if (data == null) {
			FAILED.add(url);
			return;
		}
		ByteBuffer buf = MemoryUtil.memAlloc(data.length);
		buf.put(data).flip();
		ByteBuffer pixels = null;
		try (MemoryStack st = MemoryStack.stackPush()) {
			PointerBuffer delays = st.mallocPointer(1);
			IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), z = st.mallocInt(1), comp = st.mallocInt(1);
			pixels = STBImage.stbi_load_gif_from_memory(buf, delays, w, h, z, comp, 4);
			if (pixels == null) {
				FAILED.add(url);
				return;
			}
			int fw = w.get(0), fh = h.get(0), frames = Math.max(1, z.get(0));
			// keep the texture small: at most 4096 px tall, skip frames evenly if needed
			int step = Math.max(1, (int) Math.ceil(frames * (double) fh / 4096.0));
			int kept = (frames + step - 1) / step;
			IntBuffer dl = delays.get(0) == 0 ? null : MemoryUtil.memIntBuffer(delays.get(0), frames);
			int[] ends = new int[kept];
			int total = 0;
			NativeImage img = new NativeImage(fw, fh * kept, false);
			for (int k = 0; k < kept; k++) {
				int f = k * step;
				int d = 0;
				for (int s = f; s < Math.min(frames, f + step); s++) {
					int ms = dl == null ? 100 : dl.get(s);
					d += ms <= 10 ? 100 : ms;
				}
				total += d;
				ends[k] = total;
				int base = f * fw * fh * 4;
				for (int y = 0; y < fh; y++) {
					for (int x = 0; x < fw; x++) {
						int i = base + (y * fw + x) * 4;
						int r = pixels.get(i) & 0xFF, g = pixels.get(i + 1) & 0xFF, b = pixels.get(i + 2) & 0xFF, al = pixels.get(i + 3) & 0xFF;
						img.setColorArgb(x, k * fh + y, (al << 24) | (r << 16) | (g << 8) | b);
					}
				}
			}
			if (delays.get(0) != 0) {
				STBImage.stbi_image_free(MemoryUtil.memByteBuffer(delays.get(0), 1));
			}
			int keptF = kept, totalF = total;
			MinecraftClient.getInstance().execute(() -> {
				Identifier id = Identifier.of("sol_client", "textures/gif/" + (counter++) + ".png");
				NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "plegumo_gif", img);
				tex.upload();
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
				READY.put(url, new Anim(id, fw, fh, keptF, ends, totalF));
				PENDING.remove(url);
				trim();
			});
		} catch (Throwable t) {
			FAILED.add(url);
			System.err.println("[Sleep] GIF failed " + url + ": " + t);
		} finally {
			if (pixels != null) {
				STBImage.stbi_image_free(pixels);
			}
			MemoryUtil.memFree(buf);
		}
	}

	/** Keeps memory low: forgets GIFs not shown for a while (max 40 kept). */
	private static void trim() {
		synchronized (USED) {
			while (READY.size() > 40 && !USED.isEmpty()) {
				String oldest = USED.keySet().iterator().next();
				USED.remove(oldest);
				Anim a = READY.remove(oldest);
				if (a != null) {
					MinecraftClient.getInstance().getTextureManager().destroyTexture(a.tex);
				}
			}
		}
	}

	/** Draws the current frame of a GIF in x, y, w, h. */
	public static void draw(DrawContext c, Anim a, int x, int y, int w, int h) {
		int f = a.frameAt(System.currentTimeMillis());
		dev.solclient.Gfx.texUv(c, a.tex, x, y, w, h, 0, f * a.h, a.w, a.h, a.w, a.h * a.frames, 0xFFFFFFFF);
	}
}
