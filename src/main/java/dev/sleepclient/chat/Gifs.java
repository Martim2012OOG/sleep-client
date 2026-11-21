package dev.sleepclient.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.social.SolarApi;
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
		dev.sleepclient.social.Social.IO.execute(() -> {
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
	/** What the picker shows: 0 GIFs, 1 stickers (transparent), 2 the ones you sent lately, 3 your favourites. */
	public static volatile int tab;
	public static final String[] TABS = {"GIFs", "Stickers", "Recent", "Favourites"};
	/** Quick searches shown as chips under the search box ("" = trending). */
	public static final String[] QUICK = {"", "lol", "gg", "ez", "dance", "rage", "sad", "wow", "hype", "bye", "minecraft", "cat"};
	private static volatile int lastTab = -1, offset;
	private static volatile boolean loadingMore, noMore;

	public static void setTab(int t) {
		if (t != tab) {
			tab = t;
			lastQuery = null;
		}
	}

	public static void search(String q) {
		String query = q == null ? "" : q.trim();
		if (tab >= 2) {
			// local lists: filter by what was typed (in the link, which holds the GIF's name on GIPHY)
			lastQuery = query;
			lastTab = tab;
			List<Result> src = tab == 2 ? recent() : favourites();
			List<Result> out = new ArrayList<>();
			for (Result r : src) {
				if (query.isEmpty() || r.url().toLowerCase().contains(query.toLowerCase())) {
					out.add(r);
				}
			}
			results = out;
			state = !out.isEmpty() ? "" : tab == 2 ? "GIFs you send show up here" : "Right-click a GIF to add it to your favourites";
			return;
		}
		if (query.equals(lastQuery) && lastTab == tab && !results.isEmpty()) {
			return;
		}
		lastQuery = query;
		lastTab = tab;
		offset = 0;
		noMore = false;
		String k = key();
		if (k.isEmpty()) {
			state = "GIF search needs a GIPHY key. You can still paste a GIF link from giphy.com or tenor.com in the chat box and send it.";
			results = List.of();
			return;
		}
		results = List.of();
		state = "Loading...";
		fetch(query, tab, 0, k);
	}

	/** The next page of the same search (called when the grid is scrolled to the bottom). */
	public static void more() {
		if (tab >= 2 || loadingMore || noMore || results.isEmpty() || lastQuery == null || results.size() >= 240) {
			return;
		}
		String k = key();
		if (!k.isEmpty()) {
			fetch(lastQuery, tab, results.size(), k);
		}
	}

	private static void fetch(String query, int t, int from, String k) {
		loadingMore = from > 0;
		dev.sleepclient.social.Social.IO.execute(() -> {
			try {
				String kind = t == 1 ? "stickers" : "gifs";
				String u = query.isEmpty()
						? "https://api.giphy.com/v1/" + kind + "/trending?limit=24&offset=" + from + "&rating=pg-13&api_key=" + k
						: "https://api.giphy.com/v1/" + kind + "/search?limit=24&offset=" + from + "&rating=pg-13&api_key=" + k + "&q="
								+ java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
				byte[] data = SolarApi.download(u);
				if (!query.equals(lastQuery) || t != tab) {
					return;
				}
				if (data == null) {
					if (from == 0) {
						state = "Could not load GIFs (check the GIPHY key)";
					}
					return;
				}
				JsonObject o = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
				List<Result> out = from == 0 ? new ArrayList<>() : new ArrayList<>(results);
				java.util.Set<String> seen = new java.util.HashSet<>();
				for (Result r : out) {
					seen.add(r.id());
				}
				int got = 0;
				for (JsonElement e : o.getAsJsonArray("data")) {
					JsonObject g = e.getAsJsonObject();
					JsonObject img = g.getAsJsonObject("images");
					String prev = url(img, "fixed_width_small_still", "fixed_width_still", "downsized_still");
					String send = url(img, "fixed_width_small", "fixed_width", "downsized");
					String id = g.get("id").getAsString();
					if (prev != null && send != null && seen.add(id)) {
						out.add(new Result(id, prev, send));
						got++;
					}
				}
				noMore = got == 0;
				results = out;
				state = out.isEmpty() ? "No GIFs found" : "";
			} catch (Exception ex) {
				if (from == 0) {
					state = "Could not load GIFs";
				}
			} finally {
				loadingMore = false;
			}
		});
	}

	public static boolean loadingMore() {
		return loadingMore;
	}

	// ------------------------------------------------------------------ recent + favourites (config/kubo-gifs.json)
	private static List<Result> recentList, favList;

	private static synchronized void loadLists() {
		if (recentList != null) {
			return;
		}
		recentList = new ArrayList<>();
		favList = new ArrayList<>();
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("kubo-gifs.json");
			if (Files.exists(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				readList(o, "recent", recentList);
				readList(o, "favourites", favList);
			}
		} catch (Exception ignored) {
			// a broken file: start empty
		}
	}

	private static void readList(JsonObject o, String k, List<Result> into) {
		if (o.has(k)) {
			for (JsonElement e : o.getAsJsonArray(k)) {
				JsonObject r = e.getAsJsonObject();
				into.add(new Result(r.get("id").getAsString(), r.get("preview").getAsString(), r.get("url").getAsString()));
			}
		}
	}

	private static synchronized void saveLists() {
		JsonObject o = new JsonObject();
		o.add("recent", writeList(recentList));
		o.add("favourites", writeList(favList));
		String text = o.toString();
		dev.sleepclient.social.Social.IO.execute(() -> {
			try {
				Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("kubo-gifs.json"), text);
			} catch (Exception ignored) {
				// read-only config folder: lists only last this session
			}
		});
	}

	private static JsonArray writeList(List<Result> l) {
		JsonArray a = new JsonArray();
		for (Result r : l) {
			JsonObject j = new JsonObject();
			j.addProperty("id", r.id());
			j.addProperty("preview", r.preview());
			j.addProperty("url", r.url());
			a.add(j);
		}
		return a;
	}

	private static Result fromUrl(String url) {
		return new Result(url, url, url);
	}

	public static synchronized List<Result> recent() {
		loadLists();
		return new ArrayList<>(recentList);
	}

	public static synchronized List<Result> favourites() {
		loadLists();
		return new ArrayList<>(favList);
	}

	/** Remember a GIF you sent (newest first, at most 60). */
	public static synchronized void sent(Result r) {
		loadLists();
		recentList.removeIf(x -> x.url().equals(r.url()));
		recentList.add(0, r);
		while (recentList.size() > 60) {
			recentList.remove(recentList.size() - 1);
		}
		saveLists();
	}

	public static void sent(String url) {
		sent(fromUrl(url.trim()));
	}

	public static synchronized boolean isFavourite(String url) {
		loadLists();
		for (Result r : favList) {
			if (r.url().equals(url)) {
				return true;
			}
		}
		return false;
	}

	/** Adds or removes a favourite; true when it is a favourite now. */
	public static synchronized boolean toggleFavourite(Result r) {
		loadLists();
		boolean had = favList.removeIf(x -> x.url().equals(r.url()));
		if (!had) {
			favList.add(0, r);
		}
		saveLists();
		if (tab == 3) {
			lastQuery = null;
		}
		return !had;
	}

	public static boolean toggleFavourite(String url) {
		return toggleFavourite(fromUrl(url.trim()));
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
		dev.sleepclient.social.Social.IO.execute(() -> decode(url));
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
				Identifier id = Identifier.of("sleep_client", "textures/gif/" + (counter++) + ".png");
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
		dev.sleepclient.Gfx.texUv(c, a.tex, x, y, w, h, 0, f * a.h, a.w, a.h, a.w, a.h * a.frames, 0xFFFFFFFF);
	}
}
