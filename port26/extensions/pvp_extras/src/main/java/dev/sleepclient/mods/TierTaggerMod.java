package dev.sleepclient.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.option.Option;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * TierTagger: everyone's PvP tier from MCTiers (like "HT3") next to their name, in the nametag and the tab list.
 * Pick the mode (Vanilla / Sword / Axe / ...) or "Best" for their highest tier.
 */
public class TierTaggerMod extends Mod {

	public static TierTaggerMod instance;

	private static final String[] MODES = {"Best", "Vanilla", "Sword", "Axe", "Pot", "Neth Pot", "SMP", "UHC", "Mace"};
	private static final String[] KEYS = {"", "vanilla", "sword", "axe", "pot", "neth_pot", "smp", "uhc", "mace"};

	private final Option.Choice mode = choice("Mode", 0, MODES);
	private final Option.Bool tab = bool("In The Tab List", true);
	private final Option.Bool self = bool("Show Mine", true);
	private final Option.Bool modeIcon = bool("Show The Mode Name", false);

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
	private static final Map<UUID, JsonObject> RANKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();

	public TierTaggerMod() {
		super("TierTagger", "tier_tagger", "Shows players' PvP tier from MCTiers (like HT3) next to their name.", Category.VISUAL);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.enabled;
	}

	public static boolean inTab() {
		return on() && instance.tab.on();
	}

	/** "[HT3] Name" when the player has a tier. */
	public static Component decorate(UUID id, Component name) {
		if (!on() || id == null || name == null) {
			return name;
		}
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player != null && id.equals(mc.player.getUUID()) && !instance.self.on()) {
			return name;
		}
		JsonObject r = RANKS.get(id);
		if (r == null) {
			fetch(id);
			return name;
		}
		String[] t = instance.tier(r);
		if (t == null) {
			return name;
		}
		MutableComponent out = Component.literal(t[0]).withStyle(s -> s.withColor(Integer.parseInt(t[1])));
		if (instance.modeIcon.on() && t[2] != null) {
			out.append(Component.literal(" " + t[2]).withStyle(s -> s.withColor(0xAAAAAA)));
		}
		return out.append(Component.literal(" ")).append(name);
	}

	/** {label, colour, mode} of the tier to show, or null. pos 0 = High, 1 = Low; tier 1 is the best. */
	private String[] tier(JsonObject profile) {
		JsonObject ranks = profile.has("rankings") && profile.get("rankings").isJsonObject() ? profile.getAsJsonObject("rankings") : null;
		if (ranks == null) {
			return null;
		}
		String bestKey = null;
		int bestScore = Integer.MAX_VALUE, bestTier = 0, bestPos = 0;
		boolean bestRetired = false;
		for (int i = 1; i < KEYS.length; i++) {
			if (mode.idx() != 0 && mode.idx() != i) {
				continue;
			}
			JsonElement e = ranks.get(KEYS[i]);
			if (e == null || !e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			int tierN = o.has("tier") ? o.get("tier").getAsInt() : 0, pos = o.has("pos") ? o.get("pos").getAsInt() : 1;
			if (tierN <= 0) {
				continue;
			}
			int score = tierN * 2 + pos;
			if (score < bestScore) {
				bestScore = score;
				bestKey = MODES[i];
				bestTier = tierN;
				bestPos = pos;
				bestRetired = o.has("retired") && o.get("retired").getAsBoolean();
			}
		}
		if (bestKey == null) {
			return null;
		}
		String label = (bestRetired ? "R" : "") + (bestPos == 0 ? "HT" : "LT") + bestTier;
		int col = switch (bestTier) {
			case 1 -> 0xFFD34A;
			case 2 -> 0xC9D2E3;
			case 3 -> 0xD68A4C;
			case 4 -> 0x7FD3A0;
			default -> 0x9AA3B8;
		};
		return new String[] {label, Integer.toString(col), bestKey};
	}

	private static void fetch(UUID id) {
		long now = System.currentTimeMillis();
		Long last = ASKED.get(id);
		if (last != null && now - last < 10 * 60_000L) {
			return;
		}
		ASKED.put(id, now);
		dev.sleepclient.social.Social.IO.execute(() -> {
			try {
				String u = id.toString().replace("-", "");
				HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://mctiers.com/api/profile/" + u)).timeout(Duration.ofSeconds(8))
						.header("User-Agent", "SleepClient").build(), HttpResponse.BodyHandlers.ofString());
				if (r.statusCode() == 200) {
					RANKS.put(id, JsonParser.parseString(r.body()).getAsJsonObject());
				} else {
					RANKS.put(id, new JsonObject()); // no tier: remember it so we don't ask again
				}
			} catch (Exception e) {
				ASKED.put(id, now - 9 * 60_000L); // try again in a minute
			}
		});
	}
}
