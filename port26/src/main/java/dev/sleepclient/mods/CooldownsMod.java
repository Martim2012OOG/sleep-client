package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cooldowns: the item cooldowns that matter in a fight (ender pearl, golden apples, chorus fruit, shield, wind charge...) as small
 * icons with the time left, anywhere on the screen. Works with the cooldowns of the game and of servers that set their own.
 */
public class CooldownsMod extends HudMod {

	private record Entry(Item item, Option.Bool on) {
	}

	private final Option.Header hItems = header("Items");
	private final List<Entry> entries = new ArrayList<>();
	private final Option.Header hLook = header("Look");
	private final Option.Choice layout = choice("Layout", 0, "Horizontal", "Vertical");
	private final Option.Choice mode = choice("Show", 0, "Only while cooling down", "Always");
	private final Option.Bool seconds = bool("Show Time Left", true);
	private final Option.Bool sweep = bool("Dark Sweep On The Icon", true);
	private final Option.Bool bar = bool("Progress Bar", true);
	private final Option.Col barColour = colour("Bar Colour", 0xFF4F86E8).showIf(bar::on);
	private final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF).showIf(seconds::on);
	private final Option.Num gap = num("Spacing", 4, 0, 16, 1, "");

	/** When each item started cooling and the length worked out from it (the game only tells us a fraction left). */
	private final Map<Item, long[]> track = new HashMap<>();

	public CooldownsMod() {
		super("Cooldowns", "item_cooldowns", "Shows the cooldown of ender pearls, golden apples, chorus fruit, shield and more, with the time left.", 0.45F, 0.80F);
		add(Items.ENDER_PEARL, "Ender Pearl", true);
		add(Items.GOLDEN_APPLE, "Golden Apple", true);
		add(Items.ENCHANTED_GOLDEN_APPLE, "Enchanted Golden Apple", true);
		add(Items.CHORUS_FRUIT, "Chorus Fruit", true);
		add(Items.SHIELD, "Shield", true);
		add(Items.WIND_CHARGE, "Wind Charge", true);
		add(Items.TRIDENT, "Trident", false);
		add(Items.CROSSBOW, "Crossbow", false);
		add(Items.BOW, "Bow", false);
		add(Items.FIREWORK_ROCKET, "Firework Rocket", false);
		add(Items.SNOWBALL, "Snowball", false);
		add(Items.EGG, "Egg", false);
		add(Items.GOAT_HORN, "Goat Horn", false);
		add(Items.TOTEM_OF_UNDYING, "Totem Of Undying", false);
		add(Items.ENDER_EYE, "Eye Of Ender", false);
		add(Items.MACE, "Mace", false);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private void add(Item item, String name, boolean def) {
		entries.add(new Entry(item, bool(name, def)));
	}

	/** Items to draw this frame, with the fraction of the cooldown that is left (0 = ready). */
	private List<Object[]> current(boolean edit) {
		List<Object[]> out = new ArrayList<>();
		var p = mc.player;
		for (Entry e : entries) {
			if (!e.on.on()) {
				continue;
			}
			float left = 0F;
			if (p != null) {
				ItemStack st = new ItemStack(e.item);
				left = p.getCooldowns().getCooldownPercent(st, 0F);
			}
			if (edit) {
				out.add(new Object[] {e.item, left > 0 ? left : 0.6F});
			} else if (left > 0F || mode.idx() == 1) {
				out.add(new Object[] {e.item, left});
			}
		}
		if (edit && out.isEmpty()) {
			out.add(new Object[] {Items.ENDER_PEARL, 0.6F});
		}
		return out;
	}

	@Override
	public boolean visible() {
		return mc.player != null && !current(false).isEmpty();
	}

	private int count(boolean edit) {
		return Math.max(1, current(edit).size());
	}

	@Override
	public int width() {
		int n = count(isEditing());
		return layout.idx() == 0 ? n * 20 + (n - 1) * gap.i() : seconds.on() ? 46 : 20;
	}

	@Override
	public int height() {
		int n = count(isEditing());
		return layout.idx() == 0 ? (seconds.on() ? 28 : 20) : n * 20 + (n - 1) * gap.i();
	}

	private boolean isEditing() {
		return dev.sleepclient.compat.Mc.screen(mc) != null && dev.sleepclient.compat.Mc.screen(mc).getClass().getSimpleName().toLowerCase().contains("hud");
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		List<Object[]> items = current(edit);
		long now = System.currentTimeMillis();
		int x = 0, y = 0;
		for (Object[] it : items) {
			Item item = (Item) it[0];
			float left = (Float) it[1];
			// work out the full length from how fast the fraction falls
			long[] t = track.computeIfAbsent(item, k -> new long[] {0, 0});
			if (left <= 0F) {
				t[0] = 0;
			} else if (t[0] == 0) {
				t[0] = now;
				t[1] = 0;
			} else if (left < 0.97F) {
				t[1] = Math.round((now - t[0]) / (1F - left));
			}
			c.item(new ItemStack(item), x + 2, y + 2);
			if (left > 0F && sweep.on()) {
				int h = Math.round(16 * left);
				c.fill(x + 2, y + 2 + (16 - h), x + 18, y + 18, 0xA0000000);
			}
			if (bar.on() && left > 0F) {
				c.fill(x + 2, y + 19, x + 18, y + 20, 0x80000000);
				c.fill(x + 2, y + 19, x + 2 + Math.round(16 * (1F - left)), y + 20, barColour.c());
			}
			if (seconds.on() && left > 0F) {
				String s;
				if (t[1] > 0) {
					float sec = Math.max(0F, left * t[1] / 1000F);
					s = (sec >= 10 ? String.valueOf(Math.round(sec)) : String.format("%.1f", sec)) + "s";
				} else {
					s = Math.round(left * 100) + "%";
				}
				if (layout.idx() == 0) {
					Gfx.textC(c, s, x + 10, y + 21, textColour.c());
				} else {
					Gfx.text(c, s, x + 22, y + 6, textColour.c());
				}
			}
			if (layout.idx() == 0) {
				x += 20 + gap.i();
			} else {
				y += 20 + gap.i();
			}
		}
	}
}
