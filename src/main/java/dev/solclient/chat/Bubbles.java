package dev.solclient.chat;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Chat bubble styles for the friends chat (like the bubble styles of phone chat apps). The bubble is a soft rounded shape
 * (shadow, gradient, thin border, shine) and a mascot picture peeks over one corner: original drawings made for Sleep
 * (sources in tools/bubble-art/*.svg, 128 px PNGs in textures/chat/bubble). Every message you send carries your style
 * ("bubble"), so your friends see it too once the site stores it.
 */
public final class Bubbles {

	/** One style: id sent with messages, names, colours (bg2 != 0 = left-to-right gradient), text colours, mascot picture. */
	public record Style(String id, String en, String pt, int bg, int bg2, int text, int sub, String mascot) {
		public String name() {
			return Lang.tr(en, pt);
		}

		public boolean plain() {
			return mascot.isEmpty();
		}
	}

	public static final List<Style> ALL = List.of(
			new Style("", "Default", "Predefinição", 0, 0, 0, 0, ""),
			new Style("frog", "Frog", "Sapo", 0xFF4CC05E, 0, 0xFFFFFFFF, 0xD0FFFFFF, "frog"),
			new Style("cat", "Cat", "Gato", 0xFFFFF0D4, 0, 0xFF3A2A1A, 0xAA3A2A1A, "cat"),
			new Style("ghost", "Ghost", "Fantasma", 0xFFF4F5FB, 0, 0xFF22222A, 0x9922222A, "ghost"),
			new Style("pumpkin", "Pumpkin", "Abóbora", 0xFFF07A24, 0, 0xFFFFFFFF, 0xD0FFFFFF, "pumpkin"),
			new Style("web", "Spider", "Aranha", 0xFF1F1F2A, 0, 0xFFFFFFFF, 0xAAFFFFFF, "spider"),
			new Style("bear", "Bear", "Urso", 0xFFB98256, 0, 0xFFFFFFFF, 0xD0FFFFFF, "bear"),
			new Style("shark", "Shark", "Tubarão", 0xFF4F86E8, 0, 0xFFFFFFFF, 0xD0FFFFFF, "shark"),
			new Style("bunny", "Bunny", "Coelho", 0xFFFFC6DA, 0, 0xFF4A2234, 0xAA4A2234, "bunny"),
			new Style("dino", "Dino", "Dino", 0xFF3DBFA9, 0, 0xFFFFFFFF, 0xD0FFFFFF, "dino"),
			new Style("cloud", "Cloud", "Nuvem", 0xFFD5ECFF, 0, 0xFF1E3550, 0xAA1E3550, "cloud"),
			new Style("galaxy", "Galaxy", "Galáxia", 0xFF6A3FC4, 0xFF232A7A, 0xFFFFFFFF, 0xD0FFFFFF, "galaxy"),
			new Style("fire", "Fire", "Fogo", 0xFFE5402B, 0xFFF59A23, 0xFFFFFFFF, 0xD0FFFFFF, "fire"));

	private static volatile String mine;

	private Bubbles() {
	}

	public static Style of(String id) {
		if (id != null && !id.isEmpty()) {
			for (Style s : ALL) {
				if (s.id.equals(id)) {
					return s;
				}
			}
		}
		return ALL.get(0);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("chat-bubble.txt");
	}

	/** Your style (sent with every message). */
	public static String mine() {
		if (mine == null) {
			try {
				Path f = file();
				mine = Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8).trim() : "";
			} catch (Exception e) {
				mine = "";
			}
			mine = of(mine).id; // an unknown id (older / newer client) is the default
		}
		return mine;
	}

	public static void setMine(String id) {
		mine = of(id).id;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), mine, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			// only a preference
		}
	}

	private static Identifier mascot(Style s) {
		return Gfx.id("textures/chat/bubble/" + s.mascot + ".png");
	}

	/**
	 * Draws the bubble (x, y, w, h). {@code defaultBg} is the normal colour (accent / grey) for the Default style.
	 * {@code mascotLeft}: the mascot peeks over the top-left corner (your messages, whose head and name are on the right).
	 */
	public static void draw(DrawContext c, Style s, int x, int y, int w, int h, int defaultBg, boolean mascotLeft) {
		if (s.plain()) {
			Gfx.rrect(c, x, y, w, h, 6, defaultBg);
			return;
		}
		int r = 7;
		// soft shadow under the bubble
		Gfx.rrect(c, x, y + 2, w, h, r, 0x38000000);
		// body: a thin darker border, then the colour (gradient for the two-colour styles), then a light top
		int border = Gfx.blend(s.bg, 0xFF000000, s.text == 0xFFFFFFFF ? 0.28F : 0.14F);
		Gfx.rrect(c, x, y, w, h, r, border);
		if (s.bg2 != 0) {
			Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, r - 1, s.bg);
			Gfx.gradientH(c, x + 4, y + 1, w - 8, h - 2, s.bg, s.bg2);
			Gfx.rrect(c, x + w - 9, y + 1, 8, h - 2, r - 1, s.bg2);
		} else {
			Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, r - 1, s.bg);
			int top = Gfx.blend(s.bg, 0xFFFFFFFF, 0.16F);
			Gfx.gradientV(c, x + 3, y + 1, w - 6, Math.max(2, (h - 2) / 2), top, s.bg);
		}
		Gfx.rrect(c, x + 4, y + 1, w - 8, 1, 0, 0x55FFFFFF); // shine on the top edge
		// the mascot: mostly above the bubble, half outside its corner, so it never covers the text
		int m = Math.max(16, Math.min(24, h + 6));
		int mx = mascotLeft ? x - m / 2 : x + w - m / 2;
		int my = y - m + Math.max(4, m / 4);
		Gfx.tex(c, mascot(s), mx, my, m, m, 128, 128, 0xFFFFFFFF);
	}
}
