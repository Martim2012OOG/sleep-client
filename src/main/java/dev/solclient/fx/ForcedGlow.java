package dev.solclient.fx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Entities (by id) that are drawn with the glowing outline on this client only, like a spectral arrow hit, whatever the server says. */
public final class ForcedGlow {

	private static final Map<Integer, Integer> COLOURS = new ConcurrentHashMap<>();

	private ForcedGlow() {
	}

	public static void set(int entityId, int rgb) {
		COLOURS.put(entityId, rgb & 0xFFFFFF);
	}

	public static void clear() {
		COLOURS.clear();
	}

	public static boolean has(int entityId) {
		return !COLOURS.isEmpty() && COLOURS.containsKey(entityId);
	}

	public static Integer colour(int entityId) {
		return COLOURS.isEmpty() ? null : COLOURS.get(entityId);
	}
}
