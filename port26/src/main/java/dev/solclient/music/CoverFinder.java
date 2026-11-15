package dev.solclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.gui.Notify;
import dev.solclient.social.SolarApi;
import dev.solclient.spotify.SpotifyClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Gets the cover of the song on screen, trying one way after another and saying why when none works:
 * <ol>
 * <li>the picture the player / browser gave (a local file on Windows, an address on Spotify),</li>
 * <li>iTunes Search (free, no key): by song and artist, the title is cleaned first ("(Official Video)", "- Topic"...),</li>
 * <li>Deezer Search (free, no key), the same way.</li>
 * </ol>
 * Runs off the render thread (called from {@code RemoteTextures.local}). The last problem is in {@link #error}: the Music
 * settings show it and the HUD cover gets a small red "!", so a missing cover is never a silent failure.
 */
public final class CoverFinder {

	/** Why the cover of the song on screen is missing, or "" when it is fine / still loading. */
	public static volatile String error = "";

	/** search text -> cover address found online ("" = looked and found nothing). */
	private static final Map<String, String> FOUND = new ConcurrentHashMap<>();
	private static volatile String notifiedFor = "";

	private CoverFinder() {
	}

	/** The cover as a 128x128 PNG in the given shape (see {@link SpotifyClient#albumPng(String, int)}), or null. */
	public static byte[] png(String url, String title, String artist, int shape) {
		StringBuilder why = new StringBuilder();
		// 1) what the player gave
		if (url != null && !url.isEmpty()) {
			byte[] b = SpotifyClient.albumPng(url, shape);
			if (b != null) {
				error = "";
				return b;
			}
			why.append("the player's picture could not be read (")
					.append(url.toLowerCase(Locale.ROOT).endsWith(".webp") ? "WebP is not supported" : "unknown format or file missing")
					.append("); ");
		} else {
			String pe = MusicHub.playerArtError;
			why.append(pe == null || pe.isEmpty() ? "the player gave no picture" : pe).append("; ");
		}
		// 2) + 3) online
		String query = query(title, artist);
		if (query.isEmpty()) {
			return fail(title, artist, why.append("no song name to search").toString());
		}
		String found = FOUND.get(query);
		if (found == null) {
			String offline = "";
			found = itunes(query);
			if (found == null) {
				found = deezer(query);
			}
			if (found == null) {
				offline = " (no result, or no internet)";
			}
			found = found == null ? "" : found;
			if (found.isEmpty()) {
				why.append("no cover found online").append(offline).append("; ");
				// do not remember a miss for good: the network may have been down
				return fail(title, artist, why.toString());
			}
			FOUND.put(query, found);
			if (FOUND.size() > 300) {
				FOUND.clear();
			}
		}
		if (found.isEmpty()) {
			return fail(title, artist, why.append("no cover found online; ").toString());
		}
		byte[] b = SpotifyClient.albumPng(found, shape);
		if (b != null) {
			SpotifyClient.aliasColour(found, url == null ? "" : url);
			error = "";
			return b;
		}
		return fail(title, artist, why.append("the online cover could not be downloaded; ").toString());
	}

	private static byte[] fail(String title, String artist, String why) {
		why = why.trim();
		if (why.endsWith(";")) {
			why = why.substring(0, why.length() - 1);
		}
		error = why;
		System.err.println("[Sleep] music cover for \"" + title + "\" - \"" + artist + "\": " + why);
		String key = title + "\u0001" + artist;
		if (!key.equals(notifiedFor)) {
			notifiedFor = key;
			final String msg = why;
			Minecraft.getInstance().execute(() -> {
				try {
					Notify.show(Notify.Kind.ERROR, "Music cover failed", msg);
				} catch (Throwable ignored) {
				}
			});
		}
		return null;
	}

	// ------------------------------------------------------------------ search text
	/** "Artist - Song (Official Video)" -> "Artist - Song"; a plain title gets the artist in front. */
	static String query(String title, String artist) {
		String t = clean(title), a = clean(artist).replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").trim();
		if (t.isEmpty()) {
			return "";
		}
		return t.contains(" - ") || a.isEmpty() || t.toLowerCase(Locale.ROOT).contains(a.toLowerCase(Locale.ROOT)) ? t : a + " " + t;
	}

	private static String clean(String s) {
		if (s == null) {
			return "";
		}
		s = s.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]|\\u3010[^\\u3011]*\\u3011", " ");
		s = s.replaceAll("(?i)\\b(official|music video|lyric video|lyrics|audio|video|hd|hq|4k|visuali[sz]er|remastered|remaster)\\b", " ");
		return s.replaceAll("\\s+", " ").trim();
	}

	private static Set<String> tokens(String s) {
		Set<String> out = new HashSet<>();
		for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
			if (!w.isEmpty()) {
				out.add(w);
			}
		}
		return out;
	}

	/** How much of the result's song name is in what we searched (0..1): picks the right result, rejects unrelated ones. */
	private static double score(Set<String> wanted, String resultTitle) {
		Set<String> r = tokens(resultTitle);
		if (r.isEmpty()) {
			return 0;
		}
		int hit = 0;
		for (String w : r) {
			if (wanted.contains(w)) {
				hit++;
			}
		}
		return hit / (double) r.size();
	}

	// ------------------------------------------------------------------ providers
	private static String itunes(String query) {
		try {
			byte[] d = SolarApi.download("https://itunes.apple.com/search?media=music&entity=song&limit=6&term="
					+ URLEncoder.encode(query, StandardCharsets.UTF_8));
			if (d == null) {
				return null;
			}
			JsonArray res = JsonParser.parseString(new String(d, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("results");
			Set<String> wanted = tokens(query);
			String best = null;
			double bs = 0.6;
			for (JsonElement e : res) {
				JsonObject o = e.getAsJsonObject();
				if (!o.has("artworkUrl100") || !o.has("trackName")) {
					continue;
				}
				double sc = score(wanted, o.get("trackName").getAsString());
				if (sc >= bs) {
					bs = sc;
					best = o.get("artworkUrl100").getAsString().replaceAll("/\\d+x\\d+bb", "/300x300bb");
				}
			}
			return best;
		} catch (Throwable t) {
			System.err.println("[Sleep] music cover (iTunes): " + t);
			return null;
		}
	}

	private static String deezer(String query) {
		try {
			byte[] d = SolarApi.download("https://api.deezer.com/search?limit=6&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
			if (d == null) {
				return null;
			}
			JsonArray res = JsonParser.parseString(new String(d, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("data");
			Set<String> wanted = tokens(query);
			String best = null;
			double bs = 0.6;
			for (JsonElement e : res) {
				JsonObject o = e.getAsJsonObject();
				if (!o.has("title") || !o.has("album")) {
					continue;
				}
				JsonObject al = o.getAsJsonObject("album");
				String img = al.has("cover_big") ? al.get("cover_big").getAsString() : al.has("cover_medium") ? al.get("cover_medium").getAsString() : null;
				double sc = score(wanted, o.get("title").getAsString());
				if (img != null && sc >= bs) {
					bs = sc;
					best = img;
				}
			}
			return best;
		} catch (Throwable t) {
			System.err.println("[Sleep] music cover (Deezer): " + t);
			return null;
		}
	}
}
