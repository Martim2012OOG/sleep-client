package dev.solclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-synced lyrics for the song {@link MusicHub} is showing. They come from LRCLIB (lrclib.net: free, open, no account or
 * key) as an ".lrc" text - one timestamp per line. LRCLIB has no per-word timing, so every line is cut in small chunks (1 to 2
 * words, like "just can't" / "way, back") and the line's singing time is shared between them by length. The result is
 * the "karaoke" look of the Neonlyrics mod. All the networking runs on one background thread; the game only reads fields.
 *
 * Two rules keep the lyrics honest: a result is only used when its TITLE and its ARTIST really match the song that is
 * playing (no more "the most famous song with that name"), and a search for a song that was skipped is cancelled at once
 * (no more waiting behind old searches).
 */
public final class Lyrics {

	/** A few words of a line and when they are sung (ms from the start of the song). */
	public record Chunk(String text, long start, long end) {
	}

	/** One line of the song. {@code chunks} is empty for an instrumental gap. */
	public record Line(long start, long end, String text, List<Chunk> chunks) {
		public boolean blank() {
			return chunks.isEmpty();
		}
	}

	/** A song as LRCLIB describes it. */
	record Cand(String track, String artist, double durSec, boolean instrumental, String synced) {
	}

	// ---- state read by the HUD
	/** One line for the settings, e.g. "Found (42 lines)" or "No lyrics found for this song". */
	public static volatile String status = "Waiting for a song";
	public static volatile List<Line> lines = List.of();
	/** Changes every time new lyrics are loaded (so the renderer can reset its animation state). */
	public static volatile int version;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "kubo-lyrics");
		t.setDaemon(true);
		return t;
	});
	private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern FEAT = Pattern.compile(
			"(?i)\\s*[(\\[]\\s*(feat\\.?|ft\\.?|with|prod\\.?|remaster(ed)?|live|version|radio edit|explicit|deluxe)[^)\\]]*[)\\]]");
	private static final Pattern DASH_TAIL = Pattern.compile("(?i)\\s+-\\s+(remaster(ed)?|\\d{4}|live|radio edit|single version|mono|stereo).*$");
	private static final Pattern BRACKETS = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]");
	private static final Pattern ARTIST_SPLIT = Pattern.compile("(?i)\\s*(,|;|&|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sx\\s|\\sand\\s|\\se\\s|\\swith\\s)\\s*");

	/** Songs already found (so going back to a song is instant). Only successes are kept. */
	private static final Map<String, List<Line>> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<Line>> e) {
			return size() > 40;
		}
	});

	private static final Object LOCK = new Object();
	private static volatile String wantedKey = "";
	private static volatile String lastTitle = "", lastArtist = "";
	private static volatile long lastDur;
	/** length that was used for the current search, and the length of the previous song (the player may still report that one for a moment) */
	private static volatile long searchedDur, prevSongDur;
	/** Every new search gets a new number; a search whose number is not the current one is stale and stops. */
	private static volatile int generation;
	private static Future<?> running;

	private Lyrics() {
	}

	private static void bump() {
		synchronized (LOCK) {
			version++;
		}
	}

	/** Call often (every tick is fine): starts a search only when the song changed. Pass hasTrack=false to switch off. */
	public static void follow(boolean hasTrack, String title, String artist, long durationMs) {
		if (!hasTrack || title == null || title.isBlank()) {
			if (!wantedKey.isEmpty()) {
				wantedKey = "";
				cancelRunning();
				lines = List.of();
				status = "Waiting for a song";
				bump();
			}
			return;
		}
		String a = artist == null ? "" : artist;
		String key = title + "\u0001" + a;
		if (key.equals(wantedKey)) {
			if (durationMs > 0) {
				lastDur = durationMs; // the player often tells the length a moment after the title
				// the length changed after we searched (it was the old song's): the lyrics we have may be another version, search again
				if (searchedDur >= 0 && Math.abs(durationMs - searchedDur) > 3000) {
					searchedDur = durationMs; // once per change
					CACHE.remove(key);
					search(key, false);
				}
			}
			return;
		}
		wantedKey = key;
		lastTitle = title;
		lastArtist = a;
		boolean stale = durationMs > 0 && durationMs == prevSongDur; // still the previous song's length
		lastDur = stale ? 0 : durationMs;
		searchedDur = -1; // -1 = the search has not started yet
		if (durationMs > 0) {
			prevSongDur = durationMs;
		}
		search(key, true);
	}

	/** Search again for the current song (settings button). */
	public static void retry() {
		if (wantedKey.isEmpty()) {
			return;
		}
		CACHE.remove(wantedKey);
		search(wantedKey, false);
	}

	private static void cancelRunning() {
		Future<?> old;
		synchronized (LOCK) {
			generation++;
			old = running;
			running = null;
		}
		if (old != null) {
			old.cancel(true); // interrupts the request that is in the air
		}
	}

	private static void search(String key, boolean useCache) {
		int gen;
		Future<?> old;
		synchronized (LOCK) {
			gen = ++generation;
			old = running;
			running = null;
		}
		if (old != null) {
			old.cancel(true);
		}
		lines = List.of();
		if (useCache) {
			List<Line> hit = CACHE.get(key);
			if (hit != null) {
				lines = hit;
				status = "Synced lyrics found (" + hit.size() + " lines)";
				bump();
				return;
			}
		}
		status = "Searching lyrics...";
		bump();
		Future<?> f = IO.submit(() -> run(gen, key));
		synchronized (LOCK) {
			if (gen == generation) {
				running = f;
			} else {
				f.cancel(true);
			}
		}
	}

	private static boolean alive(int gen) {
		return gen == generation;
	}

	private static void run(int gen, String key) {
		try {
			// the player does not update title, artist and length in the same instant: let them settle
			Thread.sleep(100);
			for (int waited = 0; lastDur <= 0 && waited < 1500 && alive(gen); waited += 50) {
				Thread.sleep(50);
			}
			if (!alive(gen)) {
				return;
			}
			searchedDur = lastDur;
			List<Line> found = fetch(gen, lastTitle, lastArtist, lastDur);
			if (!alive(gen)) {
				return; // the song changed while we were searching
			}
			if (found.isEmpty()) {
				lines = List.of();
				status = "No synced lyrics found for this song";
			} else {
				CACHE.put(key, found);
				lines = found;
				status = "Synced lyrics found (" + found.size() + " lines)";
			}
			bump();
		} catch (InterruptedException ie) {
			// cancelled because the song changed: the new search owns the status now
		} catch (Throwable t) {
			if (alive(gen)) {
				lines = List.of();
				status = "Could not reach the lyrics service";
				bump();
			}
		}
	}

	// ------------------------------------------------------------------ network
	private static List<Line> fetch(int gen, String rawTitle, String rawArtist, long durMs) throws Exception {
		String title = clean(rawTitle);
		List<String> artists = artistVariants(rawArtist);
		String base = "https://lrclib.net/api/";

		// 1) exact match. The answer is still checked: title and artist must be the song that is playing
		for (String a : artists) {
			String q = "track_name=" + enc(title) + "&artist_name=" + enc(a);
			if (durMs > 0) {
				Cand c = candOf(get(base + "get?" + q + "&duration=" + Math.round(durMs / 1000.0)));
				if (c != null && usable(c) && score(title, rawArtist, durMs, c) >= 0) {
					return parse(c.synced());
				}
			}
			if (!alive(gen)) {
				return List.of();
			}
			Cand c = candOf(get(base + "get?" + q));
			if (c != null && usable(c) && score(title, rawArtist, durMs, c) >= 0) {
				return parse(c.synced());
			}
		}
		// 2) search: the best result whose title AND artist match and whose length is close
		for (String a : artists) {
			if (!alive(gen)) {
				return List.of();
			}
			List<Line> l = best(get(base + "search?track_name=" + enc(title) + "&artist_name=" + enc(a)), title, rawArtist, durMs);
			if (!l.isEmpty()) {
				return l;
			}
		}
		if (!alive(gen)) {
			return List.of();
		}
		return best(get(base + "search?q=" + enc((rawArtist == null ? "" : rawArtist) + " " + title)), title, rawArtist, durMs);
	}

	private static String get(String url) throws Exception {
		HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "SleepClient").GET().build();
		HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return res.statusCode() == 200 ? res.body() : "";
	}

	private static boolean usable(Cand c) {
		return !c.instrumental() && c.synced() != null && !c.synced().isBlank();
	}

	private static Cand candOf(String body) {
		try {
			if (body == null || body.isEmpty()) {
				return null;
			}
			JsonElement e = JsonParser.parseString(body);
			return e.isJsonObject() ? candOf(e.getAsJsonObject()) : null;
		} catch (Exception ex) {
			return null;
		}
	}

	private static Cand candOf(JsonObject o) {
		double d = 0;
		JsonElement de = o.get("duration");
		if (de != null && de.isJsonPrimitive()) {
			try {
				d = de.getAsDouble();
			} catch (Exception ignored) {
			}
		}
		JsonElement ie = o.get("instrumental");
		boolean inst = ie != null && ie.isJsonPrimitive() && ie.getAsJsonPrimitive().isBoolean() && ie.getAsBoolean();
		return new Cand(str(o, "trackName"), str(o, "artistName"), d, inst, str(o, "syncedLyrics"));
	}

	private static List<Line> best(String body, String title, String artist, long durMs) {
		try {
			if (body == null || body.isEmpty()) {
				return List.of();
			}
			JsonElement e = JsonParser.parseString(body);
			if (!e.isJsonArray()) {
				return List.of();
			}
			JsonArray a = e.getAsJsonArray();
			Cand bestC = null;
			double bestScore = -1;
			for (JsonElement it : a) {
				if (!it.isJsonObject()) {
					continue;
				}
				Cand c = candOf(it.getAsJsonObject());
				if (!usable(c)) {
					continue;
				}
				double s = score(title, artist, durMs, c);
				if (s > bestScore) {
					bestScore = s;
					bestC = c;
				}
			}
			return bestC == null ? List.of() : parse(bestC.synced());
		} catch (Exception ex) {
			return List.of();
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
	}

	// ------------------------------------------------------------------ is this the song that is playing?
	/** How well a result fits the song that is playing: negative = it is another song (never used), higher = better. */
	static double score(String wantTitle, String wantArtist, long wantDurMs, Cand c) {
		return score(wantTitle, wantArtist, wantDurMs, c.track(), c.artist(), c.durSec());
	}

	static double score(String wantTitle, String wantArtist, long wantDurMs, String gotTitle, String gotArtist, double gotDurSec) {
		int ts = titleScore(wantTitle, gotTitle);
		if (ts == 0) {
			return -1;
		}
		int as = artistScore(wantArtist, gotArtist);
		if (as == 0) {
			return -1;
		}
		double dscore = 0.3; // length unknown on one side: no information
		if (wantDurMs > 0 && gotDurSec > 0) {
			double diff = Math.abs(gotDurSec - wantDurMs / 1000.0);
			if (diff > 7) {
				return -1; // another version (live, extended, other intro...): the timing would be off
			}
			dscore = Math.exp(-diff / 2.5); // the closest length wins clearly: same length = same recording = same timing
		}
		return ts * 10 + as * 10 + dscore * 20;
	}

	/** 2 = same title, 1 = same title with a little extra, 0 = another song. */
	static int titleScore(String want, String got) {
		String a = titleKey(want), b = titleKey(got);
		if (a.isEmpty() || b.isEmpty()) {
			return 0;
		}
		if (a.equals(b)) {
			return 2;
		}
		String shorter = a.length() <= b.length() ? a : b, longer = shorter == a ? b : a;
		boolean words = (" " + longer + " ").contains(" " + shorter + " ");
		// "Hello" is not "Hello Goodbye": the extra part must be small
		return words && shorter.length() >= 0.6 * longer.length() ? 1 : 0;
	}

	/** 2 = same artist, 1 = close enough (one name inside the other), 0 = another artist. A song with no artist is not blocked. */
	static int artistScore(String want, String got) {
		if (want == null || want.isBlank()) {
			return 1;
		}
		if (got == null || got.isBlank()) {
			return 1;
		}
		Set<String> w = artistNames(want), g = artistNames(got);
		for (String x : w) {
			if (g.contains(x)) {
				return 2;
			}
		}
		String wn = norm(want), gn = norm(got);
		if (wn.length() >= 3 && gn.length() >= 3 && (wn.contains(gn) || gn.contains(wn))) {
			return 1;
		}
		return 0;
	}

	private static Set<String> artistNames(String artist) {
		Set<String> out = new HashSet<>();
		String full = norm(artist);
		if (!full.isEmpty()) {
			out.add(full);
		}
		for (String p : ARTIST_SPLIT.split(artist)) {
			String n = norm(p);
			if (n.length() >= 3) {
				out.add(n);
			}
		}
		return out;
	}

	private static String titleKey(String t) {
		String s = clean(t);
		String stripped = BRACKETS.matcher(s).replaceAll("");
		String k = norm(stripped);
		return k.isEmpty() ? norm(s) : k;
	}

	/** lower case, no accents, no punctuation, single spaces ("Beyonc\u00e9" = "beyonce", "Rock & Roll" = "rock and roll"). */
	static String norm(String s) {
		if (s == null) {
			return "";
		}
		String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace("&", " and ");
		return d.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}

	/** The artist as shown first, then the first of several ("A, B" / "A & B" / "A feat. B"): 'Tyler, The Creator' is tried whole. */
	private static List<String> artistVariants(String artist) {
		List<String> out = new ArrayList<>();
		String full = artist == null ? "" : artist.trim();
		out.add(full);
		String first = firstArtist(artist);
		if (!first.isEmpty() && !first.equals(full)) {
			out.add(first);
		}
		return out;
	}

	// ------------------------------------------------------------------ parsing
	/** Turns ".lrc" text into lines with chunk timings. Public for tests. */
	public static List<Line> parse(String lrc) {
		if (lrc == null || lrc.isBlank()) {
			return List.of();
		}
		record Raw(long t, String text) {
		}
		List<Raw> raw = new ArrayList<>();
		for (String ln : lrc.split("\\r?\\n")) {
			Matcher m = STAMP.matcher(ln);
			List<Long> stamps = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
				long ms = 0;
				if (m.group(3) != null) {
					ms = Long.parseLong((m.group(3) + "00").substring(0, 3));
				}
				stamps.add(min * 60_000 + sec * 1000 + ms);
				end = m.end();
			}
			if (stamps.isEmpty()) {
				continue; // [ar:...] [ti:...] and other tags
			}
			String text = ln.substring(end).replaceAll("<\\d+:\\d+(?:[.:]\\d+)?>", "").trim(); // drop enhanced-lrc word tags
			for (long t : stamps) {
				raw.add(new Raw(t, text));
			}
		}
		raw.sort((a, b) -> Long.compare(a.t(), b.t()));
		List<Line> out = new ArrayList<>();
		for (int i = 0; i < raw.size(); i++) {
			Raw r = raw.get(i);
			long next = i + 1 < raw.size() ? raw.get(i + 1).t() : r.t() + 5000;
			if (r.text().isEmpty()) {
				out.add(new Line(r.t(), next, "", List.of()));
				continue;
			}
			out.add(new Line(r.t(), next, r.text(), chunks(r.text(), r.t(), next)));
		}
		return List.copyOf(out);
	}

	/** Cuts a line into chunks of 1-2 words (about 4-14 letters) and shares the singing time by length. */
	static List<Chunk> chunks(String text, long start, long next) {
		String[] words = text.trim().split("\\s+");
		List<String> parts = new ArrayList<>();
		int i = 0;
		while (i < words.length) {
			String a = words[i];
			if (i + 1 < words.length && a.length() + words[i + 1].length() <= 13 && !a.endsWith(",") && !a.endsWith(".")) {
				parts.add(a + " " + words[i + 1]);
				i += 2;
			} else {
				parts.add(a);
				i++;
			}
		}
		// a normal line is sung almost for its whole gap; only after a long gap (instrumental) it is capped
		long gap = Math.max(300, next - start);
		long sung;
		if (gap > 7000) {
			sung = Math.min(gap, Math.max(2500, text.length() * 95L));
		} else {
			sung = Math.max(300, gap - Math.min(250L, Math.round(gap * 0.08)));
		}
		// share the time by syllables (a long word takes longer to sing, a short one does not)
		double[] wt = new double[parts.size()];
		double total = 0;
		for (int k = 0; k < wt.length; k++) {
			wt[k] = syllables(parts.get(k)) + 0.3;
			total += wt[k];
		}
		List<Chunk> out = new ArrayList<>();
		long t = start;
		for (int k = 0; k < parts.size(); k++) {
			long d = k == parts.size() - 1 ? start + sung - t : Math.round(sung * (wt[k] / total));
			d = Math.max(80, d);
			out.add(new Chunk(parts.get(k), t, t + d));
			t += d;
		}
		return List.copyOf(out);
	}

	/** Rough number of syllables (groups of vowels), at least 1 per word. */
	static int syllables(String s) {
		int n = 0;
		boolean in = false;
		for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
			boolean v = "aeiouy\u00e0\u00e1\u00e2\u00e3\u00e4\u00e8\u00e9\u00ea\u00ec\u00ed\u00f2\u00f3\u00f4\u00f5\u00f9\u00fa".indexOf(ch) >= 0;
			if (v && !in) {
				n++;
			}
			in = v;
		}
		return Math.max(1, n);
	}

	// ------------------------------------------------------------------ title cleaning
	static String clean(String title) {
		String s = title == null ? "" : title;
		for (int i = 0; i < 3; i++) {
			s = FEAT.matcher(s).replaceAll("");
			s = DASH_TAIL.matcher(s).replaceAll("");
		}
		return s.trim();
	}

	private static String firstArtist(String artist) {
		if (artist == null) {
			return "";
		}
		String a = ARTIST_SPLIT.split(artist)[0].trim();
		return a.isEmpty() ? artist.trim() : a;
	}

	// ------------------------------------------------------------------ lookup (used every frame by the renderer)
	/** Index of the line that is being sung at this position, or -1 before the first line. */
	public static int indexAt(List<Line> l, long posMs) {
		int lo = 0, hi = l.size() - 1, ans = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (l.get(mid).start() <= posMs) {
				ans = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return ans;
	}
}
