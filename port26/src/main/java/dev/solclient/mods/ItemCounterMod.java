package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** PvP item counter: how many pots, gapples, pearls, arrows and totems you have left. */
public class ItemCounterMod extends HudMod {

	private final Option.Bool potions = bool("Splash Potions", true);
	private final Option.Bool gapples = bool("Golden Apples", true);
	private final Option.Bool pearls = bool("Ender Pearls", true);
	private final Option.Bool arrows = bool("Arrows", false);
	private final Option.Bool totems = bool("Totems", false);
	private final Option.Bool hideZero = bool("Hide When 0", false);
	private final Option.Choice layout = choice("Layout", 0, "Vertical", "Horizontal");
	private final Option.Bool background = bool("Background", true);
	private final Option.Col backgroundColour = colour("Background Colour", 0x64000000);
	private final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF);

	private record Row(ItemStack icon, int count) {
	}

	public ItemCounterMod() {
		super("Item Counter", "item_counter", "Counts your splash potions, golden apples, ender pearls, arrows and totems.", 0.01F, 0.56F);
	}

	private int count(Item... items) {
		if (mc.player == null) {
			return 0;
		}
		Inventory inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			for (Item it : items) {
				if (s.is(it)) {
					n += s.getCount();
				}
			}
		}
		return n;
	}

	private List<Row> rows(boolean edit) {
		List<Row> l = new ArrayList<>();
		add(l, potions, Items.SPLASH_POTION, edit ? 12 : count(Items.SPLASH_POTION), edit);
		add(l, gapples, Items.GOLDEN_APPLE, edit ? 8 : count(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE), edit);
		add(l, pearls, Items.ENDER_PEARL, edit ? 16 : count(Items.ENDER_PEARL), edit);
		add(l, arrows, Items.ARROW, edit ? 64 : count(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW), edit);
		add(l, totems, Items.TOTEM_OF_UNDYING, edit ? 2 : count(Items.TOTEM_OF_UNDYING), edit);
		return l;
	}

	private void add(List<Row> l, Option.Bool on, Item icon, int n, boolean edit) {
		if (on.on() && (edit || n > 0 || !hideZero.on())) {
			l.add(new Row(new ItemStack(icon), n));
		}
	}

	private int cellW() {
		return 40;
	}

	@Override
	public int width() {
		int n = Math.max(1, rows(true).size());
		return layout.idx() == 0 ? cellW() : n * cellW();
	}

	@Override
	public int height() {
		int n = Math.max(1, rows(true).size());
		return layout.idx() == 0 ? n * 18 : 18;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		List<Row> l = rows(edit);
		if (l.isEmpty()) {
			return;
		}
		boolean vertical = layout.idx() == 0;
		int w = vertical ? cellW() : l.size() * cellW(), h = vertical ? l.size() * 18 : 18;
		if (background.on()) {
			c.fill(0, 0, w, h, backgroundColour.c());
		}
		for (int i = 0; i < l.size(); i++) {
			int x = vertical ? 0 : i * cellW(), y = vertical ? i * 18 : 0;
			c.item(l.get(i).icon(), x + 2, y + 1);
			c.text(mc.font, String.valueOf(l.get(i).count()), x + 21, y + 5, l.get(i).count() == 0 ? 0xFFFF5555 : textColour.c(), true);
		}
	}
}
