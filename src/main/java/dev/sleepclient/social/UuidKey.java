package dev.sleepclient.social;

import java.util.Locale;
import java.util.UUID;

/**
 * The key format of the site's /players/lookup: lower case, 32 hex digits, no hyphens. No Minecraft code, so it can be
 * unit tested. Anything that is not a UUID gives null.
 */
public final class UuidKey {

	private UuidKey() {
	}

	/** "AB12...-..." or "ab12..." (any case, with or without hyphens) -> 32 lower case hex digits; null when not a UUID. */
	public static String normalize(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim().toLowerCase(Locale.ROOT).replace("-", "");
		if (t.length() != 32) {
			return null;
		}
		for (int i = 0; i < 32; i++) {
			char c = t.charAt(i);
			if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
				return null;
			}
		}
		return t;
	}

	public static String normalize(UUID u) {
		return u == null ? null : normalize(u.toString());
	}
}
