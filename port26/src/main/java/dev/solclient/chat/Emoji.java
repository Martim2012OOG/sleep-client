package dev.solclient.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.solclient.Gfx;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Colour emojis in the friends chat: a bitmap font (assets/sol_client/font/emoji.json, pictures from Google's
 * Noto Color Emoji, OFL licence) mixed with the UI font. Messages keep normal Unicode emojis, so they also look
 * right on the site.
 */
public final class Emoji {

	private static List<String> all;
	private static Set<Integer> set;
	private static final Map<String, Component> CACHE = new HashMap<>();
	public static final String[] QUICK = {"❤", "😂", "👍", "🔥", "😮", "😢"};

	private Emoji() {
	}

	/** Every emoji of the picker, in order. */
	public static List<String> all() {
		if (all == null) {
			List<String> l = new ArrayList<>();
			Set<Integer> s = new HashSet<>();
			try (InputStream in = Emoji.class.getResourceAsStream("/assets/sol_client/emoji_list.txt")) {
				String t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				t.codePoints().forEach(cp -> {
					l.add(new String(Character.toChars(cp)));
					s.add(cp);
				});
			} catch (Exception ignored) {
			}
			all = l;
			set = s;
		}
		return all;
	}

	public static boolean is(int cp) {
		all();
		return set.contains(cp);
	}

	/** UI text with the emojis drawn in colour. */
	public static Component rich(String s) {
		Component t = CACHE.get(s);
		if (t != null) {
			return t;
		}
		JsonArray parts = new JsonArray();
		parts.add("");
		StringBuilder plain = new StringBuilder();
		int[] cps = s.codePoints().toArray();
		for (int cp : cps) {
			if (cp == 0xFE0F) {
				continue; // emoji presentation selector: not needed
			}
			if (is(cp)) {
				flush(parts, plain);
				JsonObject e = new JsonObject();
				e.addProperty("text", new String(Character.toChars(cp)));
				e.addProperty("font", "sol_client:emoji");
				e.addProperty("color", "white");
				parts.add(e);
			} else {
				plain.appendCodePoint(cp);
			}
		}
		flush(parts, plain);
		t = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, parts).result().orElse(Gfx.ui(s));
		if (CACHE.size() > 3000) {
			CACHE.clear();
		}
		CACHE.put(s, t);
		return t;
	}

	private static void flush(JsonArray parts, StringBuilder plain) {
		if (plain.length() > 0) {
			JsonObject o = new JsonObject();
			o.addProperty("text", plain.toString());
			o.addProperty("font", Gfx.fontId()); // Settings > Font
			parts.add(o);
			plain.setLength(0);
		}
	}

	private static final Map<String, Integer> WIDTHS = new HashMap<>();
	private static long widthsAt;

	public static int width(String s) {
		long now = System.currentTimeMillis();
		if (now - widthsAt > 30_000L || WIDTHS.size() > 4000) {
			WIDTHS.clear();
			widthsAt = now;
		}
		Integer w = WIDTHS.get(s);
		if (w == null) {
			w = Minecraft.getInstance().font.width(rich(s));
			WIDTHS.put(s, w);
		}
		return w;
	}

	/** Called when the UI font changes. */
	public static void clearCaches() {
		CACHE.clear();
		WIDTHS.clear();
	}

	public static void draw(GuiGraphicsExtractor c, String s, int x, int y, int color) {
		c.text(Minecraft.getInstance().font, rich(s), x, y, color, false);
	}

	/** True if the message is only 1-3 emojis (drawn big, like Discord). */
	public static boolean onlyEmoji(String s) {
		if (s == null || s.isBlank()) {
			return false;
		}
		int n = 0;
		for (int cp : s.strip().codePoints().toArray()) {
			if (cp == 0xFE0F || cp == ' ') {
				continue;
			}
			if (!is(cp)) {
				return false;
			}
			n++;
		}
		return n > 0 && n <= 3;
	}
}
