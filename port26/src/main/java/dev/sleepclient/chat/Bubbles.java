package dev.sleepclient.chat;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Chat bubble styles for the friends chat (like the bubble styles of phone chat apps). Every style is a picture made by
 * the Sleep owner (textures/chat/bubble/&lt;id&gt;.png, cut from their own artwork). The picture is drawn around the
 * message: the parts with the mascot / decorations keep their size, only a plain band in the middle grows with the
 * text (Minecraft and Fire repeat their texture instead, so it never looks smeared). Every message you send carries your
 * style ("bubble"), so your friends see it too once the site stores it.
 */
public final class Bubbles {

	/**
	 * One style. {@code bg} = main colour (small icons), {@code text}/{@code sub} = text colours. Picture data (pixels of
	 * the PNG): size {@code pw x ph}; {@code tx0..tx1, ty0..ty1} = where the text goes; {@code band} = first column that
	 * grows ({@code bandW} wide; with {@code tile} those columns are repeated instead of stretched); {@code vband} = the
	 * row that grows for long (multi-line) messages.
	 */
	public record Style(String id, String en, String pt, int bg, int text, int sub, int pw, int ph, int tx0, int ty0, int tx1, int ty1,
			int band, int bandW, boolean tile, int vband) {
		public String name() {
			return Lang.tr(en, pt);
		}

		public boolean plain() {
			return pw == 0;
		}
	}

	private static final int DARK = 0xFF3A2A2A, DARK_SUB = 0x993A2A2A, WHITE = 0xFFFFFFFF, WHITE_SUB = 0xCCFFFFFF;

	/** Same order as the owner's sheet. */
	public static final List<Style> ALL = List.of(
			new Style("", "Default", "Predefinição", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0),
			new Style("cat", "Cat", "Gato", 0xFFEFCCAA, DARK, DARK_SUB, 166, 68, 74, 14, 156, 60, 118, 2, false, 44),
			new Style("frog", "Frog", "Sapo", 0xFF8FE294, 0xFF1E4A22, 0x991E4A22, 144, 76, 12, 26, 134, 62, 72, 2, false, 44),
			new Style("sakura", "Sakura", "Sakura", 0xFFFECCD6, DARK, DARK_SUB, 180, 94, 56, 28, 170, 74, 120, 2, false, 50),
			new Style("catpaw", "Cat Paw", "Pata de Gato", 0xFFF2F2F2, DARK, DARK_SUB, 162, 80, 10, 22, 112, 66, 76, 2, false, 40),
			new Style("heart", "Heart", "Coração", 0xFFFB9DBD, DARK, DARK_SUB, 171, 94, 26, 31, 146, 78, 86, 2, false, 55),
			new Style("dog", "Dog", "Cão", 0xFFF9ECD7, DARK, DARK_SUB, 183, 83, 72, 14, 150, 66, 112, 2, false, 36),
			new Style("star", "Star", "Estrela", 0xFFBAAAF1, DARK, DARK_SUB, 175, 91, 18, 22, 152, 70, 82, 2, false, 46),
			new Style("galaxy", "Galaxy", "Galáxia", 0xFF161621, WHITE, WHITE_SUB, 177, 87, 24, 22, 120, 66, 80, 2, false, 46),
			new Style("minecraft", "Minecraft", "Minecraft", 0xFF695044, WHITE, WHITE_SUB, 172, 74, 10, 22, 160, 62, 60, 50, true, 44),
			new Style("fire", "Fire", "Fogo", 0xFF7A201A, WHITE, WHITE_SUB, 172, 84, 26, 16, 146, 56, 60, 50, true, 36),
			new Style("snow", "Snow", "Neve", 0xFFA4D4FD, 0xFF1E3550, 0xAA1E3550, 163, 83, 52, 18, 152, 64, 110, 2, false, 40));

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

	private static Identifier tex(Style s) {
		return Gfx.id("textures/chat/bubble/" + s.id + ".png");
	}

	/**
	 * How far the picture goes past a text area of height {@code h}, in GUI pixels: {left, top, right, bottom} as drawn
	 * for your own messages (mirrored left / right for other people's).
	 */
	public static int[] extents(Style s, int h) {
		return extents(s, 1000, h);
	}

	/** Same for a text area of w x h (a short text makes the picture go further on the mascot's side). */
	public static int[] extents(Style s, int w, int h) {
		if (s.plain()) {
			return new int[4];
		}
		float k = Math.min(MAX_SCALE, h / (float) (s.ty1 - s.ty0));
		float extra = Math.max(0, (s.tx1 - s.tx0 - s.bandW) * k - w);
		return new int[] {Math.round(s.tx0 * k + extra), Math.round(s.ty0 * k), Math.round((s.pw - s.tx1) * k), Math.round((s.ph - s.ty1) * k)};
	}

	/** Biggest scale of the picture (above it, long messages grow the middle row instead of the mascot). */
	private static final float MAX_SCALE = 0.65F;

	/**
	 * Draws the bubble so its text area covers (x, y, w, h). {@code defaultBg} is the normal colour (accent / grey) for
	 * the Default style. {@code mascotLeft}: your messages (head on the right) keep the picture as drawn; other people's
	 * messages get it mirrored, so the mascot sits on the free side and never covers their head.
	 */
	public static void draw(GuiGraphicsExtractor c, Style s, int x, int y, int w, int h, int defaultBg, boolean mascotLeft) {
		if (s.plain()) {
			Gfx.rrect(c, x, y, w, h, 6, defaultBg);
			return;
		}
		boolean flip = !mascotLeft;
		if (flip) {
			float cx = x + w / 2F;
			c.pose().pushMatrix();
			c.pose().translate(cx, 0);
			c.pose().scale(-1F, 1F);
			c.pose().translate(-cx, 0);
		}
		try {
			drawPicture(c, s, x, y, w, h);
		} finally {
			if (flip) {
				c.pose().popMatrix();
			}
		}
	}

	private static void drawPicture(GuiGraphicsExtractor c, Style s, int x, int y, int w, int h) {
		Identifier id = tex(s);
		int textH = s.ty1 - s.ty0;
		float k = Math.min(MAX_SCALE, h / (float) textH);
		// columns: [0, band) fixed | [band, band + bandW) grows | [band + bandW, pw) fixed
		int b0 = s.band, b1 = s.band + s.bandW;
		float leftW = (b0 - s.tx0) * k, rightW = (s.tx1 - b1) * k;
		// a very short message: the picture keeps its size and grows past the text on the mascot's side (away from the head)
		float xb1 = x + w - rightW, xb0 = Math.min(xb1, x + leftW);
		float x0 = xb0 - b0 * k, xEnd = xb1 + (s.pw - b1) * k;
		// rows: [0, vband) fixed | one row grows | [vband + 1, ph) fixed; centred on the text when it doesn't grow
		float growH = Math.max(0, h - textH * k);
		float y0 = y - s.ty0 * k + (growH > 0.5F ? 0 : (h - textH * k) / 2F);
		float yv0 = y0 + s.vband * k, yv1 = yv0 + growH, yEnd = yv1 + (s.ph - s.vband - 1) * k;
		float[][] rows = growH > 0.5F
				? new float[][] {{0, s.vband, y0, yv0}, {s.vband, 1, yv0, yv1}, {s.vband + 1, s.ph - s.vband - 1, yv1, yEnd}}
				: new float[][] {{0, s.ph, y0, y0 + s.ph * k}};
		for (float[] r : rows) {
			int sv = (int) r[0], sh = (int) r[1];
			int dy0 = Math.round(r[2]), dy1 = Math.round(r[3]);
			if (sh <= 0 || dy1 <= dy0) {
				continue;
			}
			part(c, id, s, 0, b0, x0, xb0, sv, sh, dy0, dy1);
			if (s.tile) {
				float tw = s.bandW * k;
				for (float tx = xb0; tx < xb1 - 0.5F; tx += tw) {
					float end = Math.min(xb1, tx + tw);
					int cols = Math.max(1, Math.min(s.bandW, Math.round((end - tx) / k)));
					part(c, id, s, b0, cols, tx, end, sv, sh, dy0, dy1);
				}
			} else {
				part(c, id, s, b0, s.bandW, xb0, xb1, sv, sh, dy0, dy1);
			}
			part(c, id, s, b1, s.pw - b1, xb1, xEnd, sv, sh, dy0, dy1);
		}
	}

	/** Source columns [su, su + sw) x rows [sv, sv + sh) of the picture drawn into dx0..dx1, dy0..dy1. */
	private static void part(GuiGraphicsExtractor c, Identifier id, Style s, int su, int sw, float dx0, float dx1, int sv, int sh, int dy0, int dy1) {
		int a = Math.round(dx0), b = Math.round(dx1);
		if (sw <= 0 || b <= a) {
			return;
		}
		Gfx.texUv(c, id, a, dy0, b - a, dy1 - dy0, su, sv, sw, sh, s.pw, s.ph, 0xFFFFFFFF);
	}
}
