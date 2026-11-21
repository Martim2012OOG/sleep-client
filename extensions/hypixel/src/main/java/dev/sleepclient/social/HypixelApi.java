package dev.sleepclient.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.mods.HypixelAdditionsMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel player stats (Level Head, Stats Overlay). Needs a key from developer.hypixel.net typed in
 * Hypixel Additions. Results are cached for 10 minutes and requests are spread out (the key allows ~300 / 5 min).
 */
public final class HypixelApi {

	public static final class Stats {
		public volatile boolean loaded, nicked, failed;
		public double level;
		public int bwStar, bwFinalKills, bwFinalDeaths, bwWins, bwLosses, bwBedsBroken;
		public int swLevel, swKills, swDeaths, swWins, swLosses;
		public int duelsWins, duelsLosses, duelsKills;
		public String rank = "";
		public long at;

		public double fkdr() {
			return bwFinalKills / (double) Math.max(1, bwFinalDeaths);
		}

		public double bwWlr() {
			return bwWins / (double) Math.max(1, bwLosses);
		}

		public double swKdr() {
			return swKills / (double) Math.max(1, swDeaths);
		}

		public double duelsWlr() {
			return duelsWins / (double) Math.max(1, duelsLosses);
		}
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Map<UUID, Stats> CACHE = new ConcurrentHashMap<>();
	private static final ArrayDeque<UUID> QUEUE = new ArrayDeque<>();
	private static long nextAt;
	private static volatile long pausedUntil;
	private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT = new java.util.concurrent.atomic.AtomicInteger();
	public static volatile String problem;

	private HypixelApi() {
	}

	public static boolean hasKey() {
		return !HypixelAdditionsMod.key().isEmpty();
	}

	/** Cached stats (never blocks). Unknown players are queued and show up a moment later. */
	public static Stats get(UUID id) {
		if (id == null) {
			return null;
		}
		Stats s = CACHE.get(id);
		long now = System.currentTimeMillis();
		if (s == null || s.loaded && now - s.at > 600_000L || s.failed && now - s.at > 120_000L) {
			if (s == null) {
				s = new Stats();
				CACHE.put(id, s);
			}
			synchronized (QUEUE) {
				if (!QUEUE.contains(id) && QUEUE.size() < 200) {
					QUEUE.add(id);
				}
			}
			s.at = now; // don't queue again right away
		}
		return s;
	}

	/** Called every client tick: sends at most ~2 requests a second. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (!hasKey() || now < nextAt || now < pausedUntil || IN_FLIGHT.get() >= 3) {
			return;
		}
		UUID id;
		synchronized (QUEUE) {
			id = QUEUE.poll();
		}
		if (id == null) {
			return;
		}
		nextAt = now + 450;
		// offline-mode / nicked players have version-3 (random) uuids that Hypixel does not know
		if (id.version() == 3 || id.version() == 2) {
			Stats s = CACHE.get(id);
			if (s != null) {
				s.nicked = true;
				s.loaded = true;
				s.at = now;
			}
			return;
		}
		IN_FLIGHT.incrementAndGet();
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/player?uuid=" + id.toString().replace("-", "")))
				.header("API-Key", HypixelAdditionsMod.key()).timeout(Duration.ofSeconds(10)).GET().build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
			IN_FLIGHT.decrementAndGet();
			Stats s = CACHE.computeIfAbsent(id, k -> new Stats());
			s.at = System.currentTimeMillis();
			if (err != null || res == null) {
				s.failed = true;
				problem = "Hypixel API unreachable";
				return;
			}
			if (res.statusCode() == 403) {
				problem = "Hypixel API key refused";
				pausedUntil = System.currentTimeMillis() + 60_000L;
				s.failed = true;
				return;
			}
			if (res.statusCode() == 429) {
				problem = "Hypixel API: too many requests, waiting";
				pausedUntil = System.currentTimeMillis() + 30_000L;
				s.failed = true;
				return;
			}
			try {
				parse(s, JsonParser.parseString(res.body()).getAsJsonObject());
				problem = null;
			} catch (RuntimeException e) {
				s.failed = true;
			}
		});
	}

	private static int i(JsonObject o, String k) {
		if (o == null) {
			return 0;
		}
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : 0;
	}

	private static JsonObject o(JsonObject o, String k) {
		if (o == null) {
			return null;
		}
		JsonElement e = o.get(k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static void parse(Stats s, JsonObject root) {
		JsonObject p = o(root, "player");
		if (p == null) {
			s.nicked = true; // never joined Hypixel with this uuid: probably a nick
			s.loaded = true;
			return;
		}
		JsonElement exp = p.get("networkExp");
		double x = exp != null && exp.isJsonPrimitive() ? exp.getAsDouble() : 0;
		s.level = Math.sqrt(2 * x + 30625) / 50 - 2.5;
		JsonObject ach = o(p, "achievements");
		s.bwStar = i(ach, "bedwars_level");
		JsonObject st = o(p, "stats");
		JsonObject bw = o(st, "Bedwars");
		s.bwFinalKills = i(bw, "final_kills_bedwars");
		s.bwFinalDeaths = i(bw, "final_deaths_bedwars");
		s.bwWins = i(bw, "wins_bedwars");
		s.bwLosses = i(bw, "losses_bedwars");
		s.bwBedsBroken = i(bw, "beds_broken_bedwars");
		JsonObject sw = o(st, "SkyWars");
		s.swKills = i(sw, "kills");
		s.swDeaths = i(sw, "deaths");
		s.swWins = i(sw, "wins");
		s.swLosses = i(sw, "losses");
		s.swLevel = swLevel(i(sw, "skywars_experience"));
		JsonObject du = o(st, "Duels");
		s.duelsWins = i(du, "wins");
		s.duelsLosses = i(du, "losses");
		s.duelsKills = i(du, "kills");
		String rank = p.has("monthlyPackageRank") && "SUPERSTAR".equals(p.get("monthlyPackageRank").getAsString()) ? "MVP++"
				: p.has("newPackageRank") ? p.get("newPackageRank").getAsString().replace("_PLUS", "+") : "";
		s.rank = rank;
		s.loaded = true;
		s.failed = false;
	}

	/** SkyWars level from experience (Hypixel's table for the first levels, then 10k per level). */
	static int swLevel(int xp) {
		int[] t = {0, 20, 70, 150, 250, 500, 1000, 2000, 3500, 6000, 10000, 15000};
		if (xp >= 15000) {
			return (xp - 15000) / 10000 + 12;
		}
		for (int i = t.length - 1; i >= 0; i--) {
			if (xp >= t[i]) {
				return i + 1;
			}
		}
		return 1;
	}

	/** Colour for a BedWars star count (like Hypixel's prestiges). */
	public static int starColour(int star) {
		int[] c = {0xFFAAAAAA, 0xFFFFFFFF, 0xFFFFAA00, 0xFF55FFFF, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFFF55FF, 0xFF5555FF, 0xFFAA00AA};
		return c[Math.min(c.length - 1, star / 100)];
	}

	/** Colour for an FKDR / KDR (green = easy, red = dangerous). */
	public static int threatColour(double ratio) {
		return ratio >= 10 ? 0xFFAA0000 : ratio >= 5 ? 0xFFFF5555 : ratio >= 3 ? 0xFFFFAA00 : ratio >= 1.5 ? 0xFFFFFF55 : ratio >= 0.75 ? 0xFF55FF55 : 0xFFAAAAAA;
	}
}
