package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.option.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** A cat on a counter that counts every click you make (any mouse button). Move it in the HUD editor, resize it, turn it off. */
public class ClickCounterMod extends HudMod {

	/** The cat is drawn from layers made pixel by pixel on one shared grid (tools/gen_cat.py), 2 screen pixels per art pixel. */
	private static final int AW = 76, AH = 58, S = 2, W = AW * S, H = AH * S;
	private static final java.util.Map<String, Identifier> TEX = new java.util.HashMap<>();

	private static final String[] OUTFITS = {"None", "Santa Hat", "Elf Hat", "Party Hat", "Crown", "Headphones", "Sunglasses", "Bow Tie", "Christmas Scarf", "Santa + Scarf", "Witch Hat"};
	private final Option.Choice outfit = choice("Outfit", java.time.LocalDate.now().getMonthValue() >= 11 ? 1 : 0, OUTFITS);
	private final Option.Col accent = colour("Outfit Colour", 0xFFE0453A);
	private final Option.Col plate = colour("Counter Colour", 0xFFF1EFEE);
	private final Option.Bool inMenus = bool("Count Clicks In Menus Too", true);
	private final Option.Action reset = action("Counter", "Reset To 0", ClickCounterMod::resetTotal, () -> format(total) + " clicks");

	private static volatile long total;
	private static long lastSave, tLeft, tRight;
	private static boolean loaded, dirty, nextRight;
	private static final boolean[] PREV = new boolean[3];

	public ClickCounterMod() {
		super("Click Counter", "click_counter", "A cat that taps with its paws and counts all your clicks. Pick its outfit, move it, resize it, turn it off.", 0.86F, 0.78F);
		scale.set(70F);
	}

	private static Identifier tex(String n) {
		return TEX.computeIfAbsent(n, k -> Gfx.id("textures/gui/cat/" + k + ".png"));
	}

	private static void layer(GuiGraphicsExtractor c, String n, int colour) {
		Gfx.tex(c, tex(n), 0, 0, W, H, AW, AH, colour);
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
			boolean down = (menus || dev.sleepclient.compat.Mc.screen(mc) == null) && Keys.mouseDown(b);
			if (down && !PREV[b]) {
				total++;
				dirty = true;
				// left click = left paw, right click = right paw, middle = they take turns
				if (b == 0 || b == 2 && !nextRight) {
					tLeft = now;
				} else {
					tRight = now;
				}
				if (b == 2) {
					nextRight = !nextRight;
				}
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
		return H;
	}

	private static final int[] ACCENTED = {3, 4, 5, 7, 8, 9};

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		load();
		long now = System.currentTimeMillis();
		int o = outfit.idx();
		int acc = accent.c() | 0xFF000000;
		boolean lDown = PREV[0] || now - tLeft < 110, rDown = PREV[1] || now - tRight < 110;
		if (edit && !lDown && !rDown && (now / 450) % 2 == 0) {
			lDown = true; // the editor shows the paws moving
		} else if (edit && !lDown && !rDown) {
			rDown = true;
		}
		layer(c, "base", 0xFFFFFFFF);
		// hats, headphones, glasses: on the head, under the paws
		switch (o) {
			case 1, 9 -> layer(c, "santa", 0xFFFFFFFF);
			case 2 -> layer(c, "elf", 0xFFFFFFFF);
			case 3 -> {
				layer(c, "party", 0xFFFFFFFF);
				layer(c, "party_acc", acc);
			}
			case 4 -> {
				layer(c, "crown", 0xFFFFFFFF);
				layer(c, "crown_acc", acc);
			}
			case 5 -> {
				layer(c, "headphones", 0xFFFFFFFF);
				layer(c, "headphones_acc", acc);
			}
			case 6 -> layer(c, "sunglasses", 0xFFFFFFFF);
			case 10 -> layer(c, "witch", 0xFFFFFFFF);
			default -> {
			}
		}
		// the counter plate, in the same grid: x 8..68, y 47..58
		int px0 = 8 * S, py0 = 47 * S, pw = 60 * S, ph = 11 * S;
		Gfx.rrect(c, px0, py0, pw, ph, 3, 0xFF5B4448);
		Gfx.rrect(c, px0 + 2, py0 + 2, pw - 4, ph - 4, 2, plate.c() | 0xFF000000);
		String n = format(edit && total == 0 ? 35676 : total);
		c.text(mc.font, n, px0 + 8, py0 + (ph - 8) / 2, 0xFF6A6360, false);
		int bx = px0 + pw - 22;
		c.fill(bx - 6, py0 + 4, bx - 5, py0 + ph - 4, 0xFFD8D2D0);
		for (int i = 0; i < 3; i++) {
			c.fill(bx, py0 + 5 + i * 4, bx + 14, py0 + 7 + i * 4, 0xFF5B4448);
		}
		// bow tie and scarf are in front of the counter
		if (o == 7) {
			layer(c, "bowtie", 0xFFFFFFFF);
			layer(c, "bowtie_acc", acc);
		}
		if (o == 8 || o == 9) {
			layer(c, "scarf", 0xFFFFFFFF);
			layer(c, "scarf_acc", acc);
		}
		// the paws last: they come down on the counter when you click
		layer(c, lDown ? "paw_l_dn" : "paw_l_up", 0xFFFFFFFF);
		layer(c, rDown ? "paw_r_dn" : "paw_r_up", 0xFFFFFFFF);
	}
}
