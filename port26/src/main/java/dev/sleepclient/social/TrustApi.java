package dev.sleepclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Talks to the Sleep site about suspicious players: sends reports and asks what the community thinks. Never blocks the game. */
public final class TrustApi {

	/** What the community knows about a player. {@code trust} is -1 when nobody reported him. */
	public record Community(int trust, int reports, int reporters, long fetchedMs) {
	}

	private static final Map<UUID, Community> CACHE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();

	private TrustApi() {
	}

	public static Community cached(UUID id) {
		return CACHE.get(id);
	}

	/** Asks the site (in the background) about these players, at most once every 5 minutes each. */
	public static void lookup(Map<UUID, String> who) {
		if (SolarApi.token == null || who.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		StringBuilder names = new StringBuilder();
		Map<String, UUID> byName = new ConcurrentHashMap<>();
		for (Map.Entry<UUID, String> e : who.entrySet()) {
			Long last = ASKED.get(e.getKey());
			if (last != null && now - last < 300_000L) {
				continue;
			}
			ASKED.put(e.getKey(), now);
			byName.put(e.getValue().toLowerCase(), e.getKey());
			names.append(names.length() == 0 ? "" : ",").append(e.getValue());
			if (byName.size() >= 30) {
				break;
			}
		}
		if (byName.isEmpty()) {
			return;
		}
		String q = names.toString();
		Thread t = new Thread(() -> {
			SolarApi.Res r = SolarApi.get("/anticheat/trust?names=" + SolarApi.enc(q));
			if (!r.ok()) {
				return;
			}
			JsonArray arr = Json.arr(r.obj(), "players");
			for (JsonElement e : arr) {
				JsonObject o = Json.obj(e);
				String n = Json.str(o, "name");
				UUID id = n == null ? null : byName.get(n.toLowerCase());
				if (id != null) {
					CACHE.put(id, new Community((int) Json.num(o, "trust", -1), (int) Json.num(o, "reports", 0), (int) Json.num(o, "reporters", 0),
							System.currentTimeMillis()));
				}
			}
		}, "Sleep-Trust-Lookup");
		t.setDaemon(true);
		t.start();
	}

	/** Sends one report (in the background). {@code checks} are the 0..100 points of each check. */
	public static void report(UUID target, String name, int trust, int suspicion, Map<String, Integer> checks, int seconds, int ping, String server, String version) {
		if (SolarApi.token == null) {
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("targetUuid", target.toString());
		body.addProperty("targetName", name);
		body.addProperty("trust", trust);
		body.addProperty("suspicion", suspicion);
		JsonObject c = new JsonObject();
		checks.forEach(c::addProperty);
		body.add("checks", c);
		body.addProperty("observedSeconds", seconds);
		body.addProperty("ping", ping);
		body.addProperty("server", server == null ? "" : server);
		body.addProperty("clientVersion", version == null ? "" : version);
		Thread t = new Thread(() -> SolarApi.post("/anticheat/report", body), "Sleep-Trust-Report");
		t.setDaemon(true);
		t.start();
	}

	public static List<String> noop() {
		return List.of();
	}
}
