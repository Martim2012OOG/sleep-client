package dev.sleepclient.emote;

import java.util.HashMap;
import java.util.Map;

/** Vanilla stand-ins for the props of each emote: {right hand, left hand} (null = nothing). */
public final class EmoteItems {

	private static final Map<String, String[]> MAP = new HashMap<>();

	private static void r(String emote, String right) {
		MAP.put(emote, new String[]{right, null});
	}

	private static void rl(String emote, String right, String left) {
		MAP.put(emote, new String[]{right, left});
	}

	private static void l(String emote, String left) {
		MAP.put(emote, new String[]{null, left});
	}

	static {
		// the emotes of the client (assets/sleep_client/emotes) that go with an accessory in the hand; the 3D props are made by
		// tools/make_emote_props.py (assets/sleep_client/items/prop_<name>.json)
		l("selfie", "prop:phone"); // the selfie raises the left arm
		r("inspect", "prop:magnifier");
		rl("squats", "prop:dumbbell", "prop:dumbbell");
		r("item_look", "diamond");
	}

	private EmoteItems() {
	}

	/** Accessories in the hands during emotes (phone, magnifier, dumbbells...). */
	public static final boolean ENABLED = true;

	/** Accessories of the emotes the site added: id -> {right, left}. */
	private static final Map<String, String[]> REMOTE = new java.util.concurrent.ConcurrentHashMap<>();

	public static void setRemote(String emoteId, String right, String left) {
		if (emoteId == null) {
			return;
		}
		if ((right == null || right.isBlank()) && (left == null || left.isBlank())) {
			REMOTE.remove(emoteId);
		} else {
			REMOTE.put(emoteId, new String[] {blank(right), blank(left)});
		}
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	public static String[] get(String emoteId) {
		if (!ENABLED) {
			return null;
		}
		String[] r = REMOTE.get(emoteId);
		return r != null ? r : MAP.get(emoteId);
	}
}
