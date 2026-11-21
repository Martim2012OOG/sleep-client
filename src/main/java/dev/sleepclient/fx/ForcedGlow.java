package dev.sleepclient.fx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Entities (by id) that are drawn with the glowing outline on this client only, like a spectral arrow hit, whatever the server says. */
public final class ForcedGlow {

	private static final Map<Integer, Integer> COLOURS = new ConcurrentHashMap<>();

	/** RGB the dropped items glow with, or -1 when item glow is off (Item Finder). */
	/** Not used any more (the Item Finder was removed: it showed items through walls). */
	public static volatile int itemRgb = -1;

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

	public static boolean has(net.minecraft.entity.Entity e) {
		return has(e.getId()) || itemRgb >= 0 && e instanceof net.minecraft.entity.ItemEntity;
	}

	public static Integer colour(net.minecraft.entity.Entity e) {
		Integer c = colour(e.getId());
		return c != null ? c : itemRgb >= 0 && e instanceof net.minecraft.entity.ItemEntity ? Integer.valueOf(itemRgb) : null;
	}

	public static Integer colour(int entityId) {
		return COLOURS.isEmpty() ? null : COLOURS.get(entityId);
	}
}
