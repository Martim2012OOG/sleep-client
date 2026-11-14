package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.option.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** A cat on a counter that counts every click you make (any mouse button). Move it in the HUD editor, resize it, turn it off. */
public class ClickCounterMod extends HudMod {

	private static final Identifier CAT = Gfx.id("textures/gui/bongo_cat.png");
	private static final int CAT_W = 131, CAT_H = 60;
	private static final int W = 92, CATH = W * CAT_H / CAT_W, TOP = 12; // TOP: room above the head for the hats

	private static final String[] OUTFITS = {"None", "Santa Hat", "Elf Hat", "Party Hat", "Crown", "Headphones", "Sunglasses", "Bow Tie", "Christmas Scarf", "Santa + Scarf", "Pumpkin Witch Hat"};
	private final Option.Choice outfit = choice("Outfit", java.time.LocalDate.now().getMonthValue() >= 11 ? 1 : 0, OUTFITS);
	private final Option.Col accent = colour("Outfit Colour", 0xFFE0453A);
	private final Option.Col plate = colour("Counter Colour", 0xFFF1EFEE);
	private final Option.Bool bounce = bool("Cat Bounces On Click", true);
	private final Option.Bool inMenus = bool("Count Clicks In Menus Too", true);
	private final Option.Action reset = action("Counter", "Reset To 0", ClickCounterMod::resetTotal, () -> format(total) + " clicks");

	private static volatile long total;
	private static long lastClick, lastSave;
	private static boolean loaded, dirty;
	private static final boolean[] PREV = new boolean[3];

	public ClickCounterMod() {
		super("Click Counter", "click_counter", "A cat on a counter that counts all your clicks. Move it, resize it, turn it off.", 0.86F, 0.80F);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("clicks.txt");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				total = Long.parseLong(Files.readString(file()).trim());
			}
		} catch (Exception ignored) {
			total = 0;
		}
		Runtime.getRuntime().addShutdownHook(new Thread(ClickCounterMod::save));
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), Long.toString(total));
			dirty = false;
		} catch (Exception ignored) {
			// not important
		}
	}

	private static void resetTotal() {
		total = 0;
		save();
	}

	/** 35676 -> "35 676" */
	private static String format(long n) {
		return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@Override
	public void frame() {
		load();
		long now = System.currentTimeMillis();
		boolean menus = inMenus.on();
		for (int b = 0; b < 3; b++) {
			boolean down = (menus || mc.currentScreen == null) && Keys.mouseDown(b);
			if (down && !PREV[b]) {
				total++;
				lastClick = now;
				dirty = true;
			}
			PREV[b] = down;
		}
		if (dirty && now - lastSave > 8000L) {
			lastSave = now;
			save();
		}
	}

	@Override
	public int width() {
		return W;
	}

	@Override
	public int height() {
		return TOP + CATH + 14;
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		load();
		long dt = System.currentTimeMillis() - lastClick;
		float pulse = bounce.on() && dt < 140 ? 1F - dt / 140F : 0F;
		int lift = Math.round(pulse * 3F);
		// the cat sits on the counter, a little squashed when you click
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(0F, TOP);
		m.pushMatrix();
		m.translate(0F, CATH + lift * 0.5F);
		m.scale(1F + pulse * 0.04F, 1F - pulse * 0.06F);
		m.translate(0F, -(CATH + lift * 0.5F));
		Gfx.tex(c, CAT, 0, 0, W, CATH, CAT_W, CAT_H, 0xFFFFFFFF);
		drawOutfit(c, outfit.idx(), accent.c() | 0xFF000000, false);
		m.popMatrix();
		// the counter plate (light, like a little display) with the number and three bars on the right
		int y = CATH - 5, h = 17;
		Gfx.rrect(c, 0, y, W, h, 4, 0xFF6B5753);
		Gfx.rrect(c, 1, y + 1, W - 2, h - 2, 3, plate.c() | 0xFF000000);
		String n = format(edit && total == 0 ? 35676 : total);
		int tx = 7, ty = y + 5;
		c.drawText(mc.textRenderer, n, tx, ty, 0xFF6A6360, false);
		int bx = W - 15;
		c.fill(bx - 4, y + 2, bx - 3, y + h - 2, 0xFFD8D2D0);
		for (int i = 0; i < 3; i++) {
			Gfx.rrect(c, bx, y + 4 + i * 3, 9, 2, 1, 0xFF6B5753);
		}
		// a scarf goes over the counter edge, in front of the plate
		if (outfit.idx() == 8 || outfit.idx() == 9) {
			drawOutfit(c, outfit.idx(), accent.c() | 0xFF000000, true);
		}
		m.popMatrix();
	}

	// ---- outfits: little pixel pieces in the cat's own 131 x 60 picture, scaled with it
	private static int sx(float u) {
		return Math.round(u * W / (float) CAT_W);
	}

	private static void px(DrawContext c, float x, float y, float w, float h, int col) {
		int x0 = sx(x), y0 = sx(y), x1 = Math.max(x0 + 1, sx(x + w)), y1 = Math.max(y0 + 1, sx(y + h));
		c.fill(x0, y0, x1, y1, col);
	}

	private static final int RED = 0xFFD93A3A, DRED = 0xFFA82727, WHITE = 0xFFFFFFFF, GREEN = 0xFF2F9E55, DGREEN = 0xFF1F7A40, GOLD = 0xFFF2C23A, DGOLD = 0xFFC79A1F, BLACK = 0xFF2A2326;

	private static void santa(DrawContext c) {
		px(c, 60, 0, 36, 10, RED);
		px(c, 66, -7, 24, 8, RED);
		px(c, 80, -10, 18, 7, RED);
		px(c, 94, -8, 10, 8, DRED);       // the tip falling to the side
		px(c, 56, 8, 46, 6, WHITE);       // the white band
		px(c, 100, -12, 10, 10, WHITE);   // the pompom
	}

	private static void drawOutfit(DrawContext c, int o, int accent, boolean front) {
		if (front) {
			if (o == 8 || o == 9) { // scarf, striped, over the edge of the counter
				for (int i = 0; i < 9; i++) {
					px(c, 34 + i * 7, 52, 7, 9, i % 2 == 0 ? accent : WHITE);
				}
				px(c, 92, 56, 9, 14, accent);
				px(c, 92, 62, 9, 3, WHITE);
			}
			return;
		}
		switch (o) {
			case 1, 9 -> santa(c);
			case 2 -> { // elf hat: green, a red band, a little bell
				px(c, 62, 2, 32, 10, GREEN);
				px(c, 70, -6, 22, 9, GREEN);
				px(c, 86, -10, 16, 6, DGREEN);
				px(c, 98, -12, 6, 6, GOLD);
				px(c, 58, 9, 42, 5, RED);
			}
			case 3 -> { // party hat: a striped cone with a pompom
				px(c, 70, 4, 26, 8, accent);
				px(c, 74, -2, 18, 7, WHITE);
				px(c, 78, -8, 10, 7, accent);
				px(c, 81, -12, 4, 5, WHITE);
				px(c, 80, -15, 6, 5, GOLD);
			}
			case 4 -> { // crown
				px(c, 66, 4, 32, 8, GOLD);
				px(c, 66, -3, 6, 8, GOLD);
				px(c, 79, -6, 6, 11, GOLD);
				px(c, 92, -3, 6, 8, GOLD);
				px(c, 66, 9, 32, 3, DGOLD);
				px(c, 80, 5, 4, 4, accent);
			}
			case 5 -> { // headphones: a band over the head, the cups on the sides
				px(c, 52, 4, 6, 24, BLACK);
				px(c, 52, 2, 52, 5, BLACK);
				px(c, 100, 4, 6, 28, BLACK);
				px(c, 47, 20, 10, 16, accent);
				px(c, 101, 22, 10, 16, accent);
			}
			case 6 -> { // sunglasses
				px(c, 36, 36, 20, 11, BLACK);
				px(c, 72, 38, 20, 11, BLACK);
				px(c, 55, 40, 18, 3, BLACK);
				px(c, 39, 38, 6, 2, 0xFF6C6468);
				px(c, 75, 40, 6, 2, 0xFF6C6468);
			}
			case 7 -> { // bow tie under the chin
				px(c, 52, 50, 12, 9, accent);
				px(c, 70, 50, 12, 9, accent);
				px(c, 63, 52, 8, 6, 0xFFB22F27);
			}
			case 10 -> { // a witch hat with a buckle
				px(c, 56, 8, 48, 5, 0xFF3A2F4A);
				px(c, 66, 0, 28, 9, 0xFF4C3C61);
				px(c, 74, -9, 16, 10, 0xFF4C3C61);
				px(c, 80, -14, 8, 6, 0xFF4C3C61);
				px(c, 66, 6, 28, 3, GOLD);
			}
			default -> {
			}
		}
	}
}
