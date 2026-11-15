package dev.solclient.tiktok;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Live numbers of your TikTok live: viewers watching right now and total likes. Filled from the gateway's messages:
 * TikFinity / TikTok-Live-Connector send "roomUser" {viewerCount} and "like" {likeCount, totalLikeCount}; Tik.Tools sends
 * "roomUserSeq" {viewerCount}; other gateways use "total" / "totalUser" / "viewers". Every name is tried.
 */
public final class TikStats {

	private TikStats() {
	}

	/** Viewers right now (-1 = not known yet). */
	public static volatile int viewers = -1;
	/** Total likes of the live (-1 = not known yet). */
	public static volatile long likes = -1;
	/** When the viewer number last arrived (ms). */
	public static volatile long viewersAt;
	private static volatile long lastLikeParse;

	public static void reset() {
		viewers = -1;
		likes = -1;
		viewersAt = 0;
	}

	/** For the test button (no live needed). */
	public static void fake(int v, long l) {
		viewers = v;
		likes = l;
		viewersAt = System.currentTimeMillis();
	}

	/** True for event names that carry these numbers (checked before any JSON is read). */
	static boolean wants(String lowName) {
		return lowName.startsWith("roomuser") || lowName.equals("viewercount") || lowName.equals("viewers") || lowName.equals("roomstats")
				|| lowName.equals("like") || lowName.equals("likes") || lowName.equals("likemessage");
	}

	/** Reads a message whose event name passed {@link #wants}. Likes come many per second: read at most 5 times a second. */
	static void read(String lowName, String raw) {
		boolean like = lowName.startsWith("like");
		long now = System.currentTimeMillis();
		if (like && now - lastLikeParse < 200) {
			return;
		}
		try {
			JsonElement el = JsonParser.parseString(raw);
			if (!el.isJsonObject()) {
				return;
			}
			JsonObject root = el.getAsJsonObject();
			JsonObject d = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
			if (like) {
				lastLikeParse = now;
				long total = num(d, "totalLikeCount", "totalLikes", "total_like_count", "total_likes", "total");
				if (total > 0) {
					likes = total;
				} else {
					long add = num(d, "likeCount", "like_count", "count");
					if (add > 0) {
						likes = Math.max(0, likes) + add;
					}
				}
				return;
			}
			long v = num(d, "viewerCount", "viewer_count", "viewers", "userCount", "user_count", "total", "totalUser", "total_user");
			if (v >= 0) {
				viewers = (int) Math.min(Integer.MAX_VALUE, v);
				viewersAt = now;
			}
		} catch (RuntimeException ignored) {
			// not JSON / other shape: keep the last numbers
		}
	}

	private static long num(JsonObject o, String... keys) {
		for (String k : keys) {
			JsonElement e = o.get(k);
			if (e != null && e.isJsonPrimitive()) {
				try {
					return Math.max(0L, Math.round(e.getAsDouble()));
				} catch (RuntimeException ignored) {
					// "1.2K" and the like: try the next name
				}
			}
		}
		return -1;
	}
}
