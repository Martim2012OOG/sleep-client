package dev.solclient.chat;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;

/**
 * Chat bubble styles for the friends chat (like the bubble styles of phone chat apps), all drawn here with shapes: no
 * pictures from anyone else. Every message you send carries your style ("bubble" in the message), so your friends with
 * Sleep see it too once the site stores it. A little mascot peeks over the corner away from the name.
 */
public final class Bubbles {

	/** One style: id sent with messages, names, bubble colours (two = gradient), text colour, and what peeks over it. */
	public record Style(String id, String en, String pt, int bg, int bg2, int text, int sub, Deco deco) {
		public String name() {
			return Lang.tr(en, pt);
		}
	}

	public enum Deco {
		NONE, FROG, CAT, GHOST, PUMPKIN, WEB, BEAR, SHARK, BUNNY, DINO, CLOUD, GALAXY, FIRE
	}

	public static final List<Style> ALL = List.of(
			new Style("", "Default", "Predefinição", 0, 0, 0, 0, Deco.NONE),
			new Style("frog", "Frog", "Sapo", 0xFF3FAE5A, 0, 0xFFFFFFFF, 0xCCFFFFFF, Deco.FROG),
			new Style("cat", "Cat", "Gato", 0xFFFFF1D6, 0, 0xFF3A2A1A, 0xAA3A2A1A, Deco.CAT),
			new Style("ghost", "Ghost", "Fantasma", 0xFFF2F2F7, 0, 0xFF22222A, 0x9922222A, Deco.GHOST),
			new Style("pumpkin", "Pumpkin", "Abóbora", 0xFFE0731F, 0, 0xFFFFFFFF, 0xCCFFFFFF, Deco.PUMPKIN),
			new Style("web", "Spider Web", "Teia", 0xFF141418, 0, 0xFFFFFFFF, 0xAAFFFFFF, Deco.WEB),
			new Style("bear", "Bear", "Urso", 0xFFB27B4C, 0, 0xFFFFFFFF, 0xCCFFFFFF, Deco.BEAR),
			new Style("shark", "Shark", "Tubarão", 0xFF4F86E8, 0, 0xFFFFFFFF, 0xCCFFFFFF, Deco.SHARK),
			new Style("bunny", "Bunny", "Coelho", 0xFFF6B6CF, 0, 0xFF4A2234, 0xAA4A2234, Deco.BUNNY),
			new Style("dino", "Dino", "Dino", 0xFF39B5A8, 0, 0xFFFFFFFF, 0xCCFFFFFF, Deco.DINO),
			new Style("cloud", "Cloud", "Nuvem", 0xFFC9E6FF, 0, 0xFF1E3550, 0xAA1E3550, Deco.CLOUD),
			new Style("galaxy", "Galaxy", "Galáxia", 0xFF5B2FA6, 0xFF1E2470, 0xFFFFFFFF, 0xCCFFFFFF, Deco.GALAXY),
			new Style("fire", "Fire", "Fogo", 0xFFE5402B, 0xFFF59A23, 0xFFFFFFFF, 0xCCFFFFFF, Deco.FIRE));

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

	/**
	 * Draws the bubble (x, y, w, h) in a style. {@code defaultBg} is the normal colour (accent / grey) for the Default style.
	 * {@code peekLeft}: the mascot goes on the top-left corner (your messages, whose name is on the right).
	 */
	public static void draw(DrawContext c, Style s, int x, int y, int w, int h, int defaultBg, boolean peekLeft) {
		if (s.deco == Deco.NONE) {
			Gfx.rrect(c, x, y, w, h, 6, defaultBg);
			return;
		}
		if (s.bg2 != 0) {
			Gfx.rrect(c, x, y, w, h, 6, s.bg);
			Gfx.gradientH(c, x + 3, y, w - 6, h, s.bg, s.bg2);
		} else {
			Gfx.rrect(c, x, y, w, h, 6, s.bg);
		}
		c.fill(x + 5, y, x + w - 5, y + 1, 0x33FFFFFF);
		int cx = peekLeft ? x + 8 : x + w - 8; // the mascot's corner
		int cy = y + 1;
		switch (s.deco) {
			case FROG -> {
				Gfx.circle(c, cx, cy, 6, 0xFF2F8F47);
				for (int e : new int[] {-3, 3}) {
					Gfx.circle(c, cx + e, cy - 4, 2.6F, 0xFFFFFFFF);
					Gfx.circle(c, cx + e, cy - 4, 1.2F, 0xFF101010);
				}
				c.fill(cx - 3, cy + 2, cx + 3, cy + 3, 0xFFE24A4A); // smile
			}
			case CAT -> {
				tri(c, cx - 5, cy - 9, 4, 0xFFF3D9A8);
				tri(c, cx + 1, cy - 9, 4, 0xFFF3D9A8);
				Gfx.circle(c, cx, cy - 1, 6, 0xFFF3D9A8);
				c.fill(cx - 3, cy - 2, cx - 2, cy - 1, 0xFF2A1D10);
				c.fill(cx + 2, cy - 2, cx + 3, cy - 1, 0xFF2A1D10);
				c.fill(cx - 1, cy + 1, cx + 1, cy + 2, 0xFFE58AA0); // nose
				c.fill(cx - 9, cy, cx - 6, cy + 1, 0x88FFFFFF); // whiskers
				c.fill(cx + 6, cy, cx + 9, cy + 1, 0x88FFFFFF);
			}
			case GHOST -> {
				Gfx.circle(c, cx, cy - 2, 6, 0xFFFFFFFF);
				c.fill(cx - 6, cy - 2, cx + 6, cy + 4, 0xFFFFFFFF);
				for (int i = -4; i <= 4; i += 4) {
					Gfx.circle(c, cx + i, cy + 4, 2, 0xFFFFFFFF);
				}
				c.fill(cx - 3, cy - 4, cx - 1, cy - 1, 0xFF151515);
				c.fill(cx + 1, cy - 4, cx + 3, cy - 1, 0xFF151515);
				// a wavy bottom edge on the bubble
				for (int i = x + 6; i < x + w - 6; i += 6) {
					Gfx.circle(c, i + 3, y + h, 2.2F, s.bg);
				}
			}
			case PUMPKIN -> {
				Gfx.circle(c, cx, cy - 1, 6, 0xFFF08A2A);
				c.fill(cx - 1, cy - 9, cx + 1, cy - 6, 0xFF3E7A2E); // stem
				c.fill(cx + 1, cy - 9, cx + 4, cy - 8, 0xFF5BA845); // leaf
				tri(c, cx - 4, cy - 3, 2, 0xFF3A1A05);
				tri(c, cx + 2, cy - 3, 2, 0xFF3A1A05);
				c.fill(cx - 3, cy + 2, cx + 3, cy + 3, 0xFF3A1A05);
			}
			case WEB -> {
				// web in the corner, and a little spider hanging from the other side
				int wx = peekLeft ? x : x + w;
				int dir = peekLeft ? 1 : -1;
				for (int r = 4; r <= 10; r += 3) {
					for (int k = 0; k <= r; k++) {
						c.fill(wx + dir * k, y + r - k, wx + dir * k + 1, y + r - k + 1, 0x99FFFFFF);
					}
				}
				for (int k = 0; k < 11; k++) {
					c.fill(wx + dir * k, y + k, wx + dir * k + 1, y + k + 1, 0x77FFFFFF);
				}
				int sx = peekLeft ? x + w - 10 : x + 10;
				c.fill(sx, y - 6, sx + 1, y + 1, 0x99FFFFFF);
				Gfx.circle(c, sx + 0.5F, y - 7, 2.6F, 0xFF0A0A0A);
				for (int l = -1; l <= 1; l += 2) {
					c.fill(sx + l * 3, y - 9, sx + l * 3 + 1, y - 5, 0xFF0A0A0A);
				}
			}
			case BEAR -> {
				Gfx.circle(c, cx - 5, cy - 5, 2.6F, 0xFF8A5A34);
				Gfx.circle(c, cx + 5, cy - 5, 2.6F, 0xFF8A5A34);
				Gfx.circle(c, cx, cy - 1, 6, 0xFF8A5A34);
				Gfx.circle(c, cx, cy + 1, 3, 0xFFD9B48C);
				c.fill(cx - 3, cy - 3, cx - 2, cy - 2, 0xFF1A1008);
				c.fill(cx + 2, cy - 3, cx + 3, cy - 2, 0xFF1A1008);
				c.fill(cx - 1, cy, cx + 1, cy + 1, 0xFF1A1008);
			}
			case SHARK -> {
				int fx = peekLeft ? x + 10 : x + w - 18;
				for (int r = 0; r < 8; r++) {
					c.fill(fx + r, y - 8 + r, fx + 8, y - 7 + r, 0xFF2F5FB8); // fin
				}
				for (int t = x + 8; t < x + w - 8; t += 5) {
					tri(c, t, y + h - 3, 2, 0xEEFFFFFF); // teeth along the bottom
				}
			}
			case BUNNY -> {
				Gfx.rrect(c, cx - 5, cy - 12, 3, 9, 1, 0xFFFFE3EE);
				Gfx.rrect(c, cx + 2, cy - 12, 3, 9, 1, 0xFFFFE3EE);
				c.fill(cx - 4, cy - 10, cx - 3, cy - 4, 0xFFF39BBE);
				c.fill(cx + 3, cy - 10, cx + 4, cy - 4, 0xFFF39BBE);
				Gfx.circle(c, cx, cy - 1, 5, 0xFFFFE3EE);
				c.fill(cx - 2, cy - 2, cx - 1, cy - 1, 0xFF2A1A20);
				c.fill(cx + 1, cy - 2, cx + 2, cy - 1, 0xFF2A1A20);
			}
			case DINO -> {
				for (int t = x + 10; t < x + w - 12; t += 9) {
					tri(c, t, y - 5, 3, 0xFF2A8C82); // back spikes
				}
				Gfx.circle(c, cx, cy - 1, 5, 0xFF2A8C82);
				Gfx.circle(c, cx + (peekLeft ? -2 : 2), cy - 3, 1.8F, 0xFFFFFFFF);
				c.fill(cx + (peekLeft ? -2 : 2), cy - 3, cx + (peekLeft ? -1 : 3), cy - 2, 0xFF101010);
			}
			case CLOUD -> {
				for (int i = x + 6; i < x + w - 4; i += 9) {
					Gfx.circle(c, i + 2, y + 1, 4, s.bg);
				}
				Gfx.circle(c, cx, cy - 3, 3.5F, 0xFFFFE07A); // a little sun
			}
			case GALAXY -> {
				int[][] stars = {{3, 3}, {w - 6, 4}, {w / 2, h - 4}, {7, h - 5}, {w - 12, h - 6}, {w / 3, 2}};
				for (int[] st : stars) {
					if (st[0] > 1 && st[0] < w - 1) {
						c.fill(x + st[0], y + st[1], x + st[0] + 1, y + st[1] + 1, 0xDDFFFFFF);
					}
				}
				Gfx.circle(c, cx, cy - 2, 4.5F, 0xFFFFD45A); // planet
				c.fill(cx - 7, cy - 2, cx + 7, cy - 1, 0xCCF6C26B); // its ring
			}
			case FIRE -> {
				for (int t = x + 6; t < x + w - 8; t += 7) {
					tri(c, t, y - 5, 3, 0xFFF59A23);
					tri(c, t + 1, y - 3, 2, 0xFFFFE066);
				}
			}
			default -> {
			}
		}
	}

	/** A small upward triangle: top point at (x + half, y), base width 2 * half + 1, half + 1 rows tall. */
	private static void tri(DrawContext c, int x, int y, int half, int col) {
		for (int r = 0; r <= half; r++) {
			c.fill(x + half - r, y + r, x + half + r + 1, y + r + 1, col);
		}
	}
}
