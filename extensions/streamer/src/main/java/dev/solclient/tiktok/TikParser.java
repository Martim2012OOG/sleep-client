package dev.solclient.tiktok;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;

/**
 * Turns one WebSocket message {"event": "...", "data": {...}} into a {@link TikEvent}.
 * TikFinity, Tik.Tools and TikTok-Live-Connector style gateways all use this shape, but the field names differ a
 * little, so every field is looked up under several names. Unknown events (likes, joins, viewer counts...) are
 * ignored on purpose: a live can send hundreds of them per second and we do not want to spend any time on them.
 */
final class TikParser {

	private TikParser() {
	}

	/** Returns null for anything we do not use (or cannot read). */
	static TikEvent parse(String raw) {
		try {
			JsonElement el = JsonParser.parseString(raw);
			if (!el.isJsonObject()) {
				return null;
			}
			JsonObject root = el.getAsJsonObject();
			String ev = str(root, "event", "type", "eventName");
			if (ev == null) {
				return null;
			}
			ev = ev.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
			JsonObject d = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
			String user = str(d, "uniqueId", "unique_id", "username", "userName");
			String nick = str(d, "nickname", "nickName", "displayName", "display_name");
			JsonObject u = obj(d, "user");
			if (u != null) {
				if (user == null) {
					user = str(u, "uniqueId", "unique_id", "username", "userName");
				}
				if (nick == null) {
					nick = str(u, "nickname", "nickName", "displayName", "display_name");
				}
			}
			// only ids that identify ONE message: a plain "id" is often the viewer's id, which made their gift/follow look like a repeat of their chat
			String id = str(d, "msgId", "msg_id", "eventId", "event_id");
			if (id != null) {
				id = ev + ":" + id;
			}
			// TikTok sends follows / shares as one "social" message with an action (1 = follow, 3 = share)
			if (ev.equals("social") || ev.equals("socialmessage") || ev.equals("webcastsocialmessage")) {
				String act = String.valueOf(str(d, "displayType", "display_type", "label", "type")).toLowerCase(Locale.ROOT);
				int code = intOf(d, "action");
				ev = act.contains("follow") || code == 1 ? "follow" : act.contains("share") || code == 3 ? "share" : ev;
			}
			switch (ev) {
				case "chat", "comment", "chatmessage", "webcastchatmessage", "comments" -> {
					String text = str(d, "comment", "text", "message", "content");
					if (text == null || text.isBlank()) {
						return null;
					}
					return new TikEvent(TikEvent.Type.CHAT, user, nick, clean(text, 200), "", 0, 0, 0, false, id);
				}
				case "gift", "gifts", "giftmessage", "webcastgiftmessage", "donation" -> {
					JsonObject g = obj(d, "gift");
					if (g == null) {
						g = obj(d, "giftDetails");
					}
					String name = str(d, "giftName", "gift_name");
					if (name == null && g != null) {
						name = str(g, "giftName", "gift_name", "name");
					}
					if (name == null) {
						name = "Gift";
					}
					int each = intOf(d, "diamondCount", "diamond_count", "coins", "coinCount", "diamonds", "giftValue", "value");
					if (each <= 0 && g != null) {
						each = intOf(g, "diamondCount", "diamond_count", "coins", "diamonds", "value");
					}
					int repeat = Math.max(1, intOf(d, "repeatCount", "repeat_count", "amount", "count", "comboCount"));
					int type = intOf(d, "giftType", "gift_type");
					if (type == 0 && g != null) {
						type = intOf(g, "giftType", "gift_type", "type");
					}
					// giftType 1 = streak gift: it sends many events while the viewer holds the button, the last one has repeatEnd
					boolean end = !d.has("repeatEnd") && !d.has("repeat_end") || bool(d, "repeatEnd", "repeat_end");
					boolean streaking = type == 1 && !end;
					int gid = intOf(d, "giftId", "gift_id");
					return new TikEvent(TikEvent.Type.GIFT, user, nick, "", clean(name, 40), gid, each, repeat, streaking, id);
				}
				case "follow", "followmessage", "newfollower", "newfollow", "webcastfollowmessage", "follower" -> {
					return new TikEvent(TikEvent.Type.FOLLOW, user, nick, "", "", 0, 0, 0, false, id);
				}
				case "share", "sharemessage", "webcastsharemessage" -> {
					return new TikEvent(TikEvent.Type.SHARE, user, nick, "", "", 0, 0, 0, false, id);
				}
				case "subscribe", "sub", "subscription", "subnotify", "subscriber", "newsubscriber" -> {
					return new TikEvent(TikEvent.Type.SUBSCRIBE, user, nick, "", "", 0, 0, 0, false, id);
				}
				default -> {
					return null;
				}
			}
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Removes control characters and cuts long text. */
	private static String clean(String s, int max) {
		StringBuilder b = new StringBuilder(Math.min(s.length(), max));
		for (int i = 0; i < s.length() && b.length() < max; i++) {
			char ch = s.charAt(i);
			if (ch >= 32 && ch != 127) {
				b.append(ch);
			}
		}
		return b.toString().trim();
	}

	private static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String str(JsonObject o, String... keys) {
		for (String k : keys) {
			JsonElement e = o.get(k);
			if (e != null && e.isJsonPrimitive()) {
				String s = e.getAsString();
				if (!s.isEmpty()) {
					return s;
				}
			}
		}
		return null;
	}

	private static int intOf(JsonObject o, String... keys) {
		for (String k : keys) {
			JsonElement e = o.get(k);
			if (e != null && e.isJsonPrimitive()) {
				try {
					return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, Math.round(e.getAsDouble())));
				} catch (RuntimeException ignored) {
					// not a number: try the next name
				}
			}
		}
		return 0;
	}

	private static boolean bool(JsonObject o, String... keys) {
		for (String k : keys) {
			JsonElement e = o.get(k);
			if (e != null && e.isJsonPrimitive()) {
				if (e.getAsJsonPrimitive().isBoolean()) {
					return e.getAsBoolean();
				}
				try {
					return e.getAsInt() != 0;
				} catch (RuntimeException ignored) {
					return "true".equalsIgnoreCase(e.getAsString());
				}
			}
		}
		return false;
	}
}
