package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.addons.pvp.KitStore;
import dev.sleepclient.addons.pvp.PracticeKit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Kit editor of the PvP Practice add-on: build your own inventory (hotbar, inventory, armor, offhand) and save it
 * as a kit. Opening a built-in kit gives you an editable copy, so you can also rearrange the layout of the ones
 * that come with the add-on. Click an item (palette or slot) to pick it up, click a slot to put it down (items
 * swap), right-click a slot to clear it, Shift+click a palette item to drop it in the first free slot, scroll on
 * a slot to change the amount.
 */
public class KitEditorScreen extends Screen {

	private static final int S = 18;

	private record P(String label, String spec) {
	}

	private static final String[] CATS = {"Weapons", "Armor", "Potions", "Food", "Misc"};
	private static final List<List<P>> PALETTE = new ArrayList<>();

	static {
		String sh = "[enchantments={sharpness:5,unbreaking:3}]";
		String neo = "[enchantments={sharpness:5,unbreaking:3,mending:1}]";
		List<P> w = new ArrayList<>();
		w.add(new P("Diamond Sword", "minecraft:diamond_sword" + sh));
		w.add(new P("Netherite Sword", "minecraft:netherite_sword" + neo));
		w.add(new P("Iron Sword", "minecraft:iron_sword"));
		w.add(new P("Diamond Axe", "minecraft:diamond_axe[enchantments={sharpness:3}]"));
		w.add(new P("Netherite Axe", "minecraft:netherite_axe" + neo));
		w.add(new P("Mace", "minecraft:mace[enchantments={density:5,wind_burst:3,unbreaking:3}]"));
		w.add(new P("Mace (density 3)", "minecraft:mace[enchantments={density:3,wind_burst:2,unbreaking:3}]"));
		w.add(new P("Netherite Spear", "minecraft:netherite_spear[enchantments={lunge:3,unbreaking:3}]"));
		w.add(new P("Iron Spear", "minecraft:iron_spear"));
		PALETTE.add(w);

		List<P> a = new ArrayList<>();
		for (String tier : new String[] {"diamond", "netherite", "iron"}) {
			String ench = tier.equals("iron") ? "[enchantments={protection:3}]" : "[enchantments={protection:4,unbreaking:3}]";
			String t = Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
			a.add(new P(t + " Helmet", "minecraft:" + tier + "_helmet" + ench));
			a.add(new P(t + " Chestplate", "minecraft:" + tier + "_chestplate" + ench));
			a.add(new P(t + " Leggings", "minecraft:" + tier + "_leggings" + ench));
			a.add(new P(t + " Boots", "minecraft:" + tier + "_boots" + ench));
		}
		PALETTE.add(a);

		String pot = "minecraft:splash_potion[potion_contents={potion:\"minecraft:";
		List<P> p = new ArrayList<>();
		p.add(new P("Healing II (splash)", pot + "strong_healing\"}]"));
		p.add(new P("Speed II (splash)", pot + "strong_swiftness\"}]"));
		p.add(new P("Strength II (splash)", pot + "strong_strength\"}]"));
		p.add(new P("Regeneration II (splash)", pot + "strong_regeneration\"}]"));
		p.add(new P("Fire Resistance (splash)", pot + "fire_resistance\"}]"));
		PALETTE.add(p);

		List<P> f = new ArrayList<>();
		f.add(new P("Golden Apple", "minecraft:golden_apple 16"));
		f.add(new P("Enchanted Golden Apple", "minecraft:enchanted_golden_apple 8"));
		f.add(new P("Cooked Beef", "minecraft:cooked_beef 64"));
		f.add(new P("Golden Carrot", "minecraft:golden_carrot 32"));
		PALETTE.add(f);

		List<P> m = new ArrayList<>();
		m.add(new P("Shield", "minecraft:shield"));
		m.add(new P("Totem of Undying", "minecraft:totem_of_undying"));
		m.add(new P("Ender Pearl", "minecraft:ender_pearl 16"));
		m.add(new P("Wind Charge", "minecraft:wind_charge 64"));
		m.add(new P("Water Bucket", "minecraft:water_bucket"));
		m.add(new P("Cobweb", "minecraft:cobweb 16"));
		PALETTE.add(m);
	}

	private record Hit(int x, int y, int w, int h, IntConsumer run) {
		boolean in(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record SlotRect(int x, int y, String slot) {
	}

	private final Screen parent;
	private final Map<String, String> slots = new LinkedHashMap<>();
	private final TextBox name = new TextBox(24);
	private final List<Hit> hits = new ArrayList<>();
	private final List<SlotRect> slotRects = new ArrayList<>();
	private String held;
	private int cat;
	private int styleIdx;
	private String hoverText;
	private String status = "";
	private long statusUntil;
	private double clickX;

	/** @param base kit to start from (a copy is edited), or null for an empty kit */
	public KitEditorScreen(Screen parent, PracticeKit base) {
		super(Component.literal("Kit Editor"));
		this.parent = parent;
		if (base != null) {
			KitStore.Data d = KitStore.fromKit(base);
			slots.putAll(d.slots);
			name.set(base.custom ? d.name : d.name + " copy");
			for (int i = 0; i < KitStore.STYLES.length; i++) {
				if (KitStore.STYLES[i].equals(d.style)) {
					styleIdx = i;
				}
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ items
	private static ItemStack stackOf(String spec) {
		try {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(KitStore.idOf(spec))));
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	private static String describe(String spec) {
		String n = stackOf(spec).getHoverName().getString();
		int c = KitStore.countOf(spec);
		int b = spec.indexOf('[');
		String extra = b >= 0 ? "  " + spec.substring(b, spec.lastIndexOf(']') + 1) : "";
		String t = n + (c > 1 ? " x" + c : "") + extra;
		return t.length() > 70 ? t.substring(0, 69) + "..." : t;
	}

	private static String prettySlot(String slot) {
		return switch (slot) {
			case "armor.head" -> "Helmet slot";
			case "armor.chest" -> "Chestplate slot";
			case "armor.legs" -> "Leggings slot";
			case "armor.feet" -> "Boots slot";
			case "weapon.offhand" -> "Offhand slot";
			default -> slot.startsWith("hotbar.") ? "Hotbar slot " + (Integer.parseInt(slot.substring(7)) + 1) : "Inventory slot";
		};
	}

	private static boolean fits(String slot, String spec) {
		String id = KitStore.idOf(spec);
		return switch (slot) {
			case "armor.head" -> id.endsWith("_helmet");
			case "armor.chest" -> id.endsWith("_chestplate");
			case "armor.legs" -> id.endsWith("_leggings");
			case "armor.feet" -> id.endsWith("_boots");
			default -> true;
		};
	}

	private void say(String s) {
		status = s;
		statusUntil = System.currentTimeMillis() + 3500;
	}

	// ------------------------------------------------------------------ actions
	private void slotClick(String slot, int button) {
		if (button == 1) {
			slots.remove(slot);
			return;
		}
		String cur = slots.get(slot);
		if (held != null) {
			if (!fits(slot, held)) {
				say("That item does not fit in this slot.");
				return;
			}
			slots.put(slot, held);
			held = cur;
		} else if (cur != null) {
			held = cur;
			slots.remove(slot);
		}
	}

	private void quickAdd(String spec) {
		String id = KitStore.idOf(spec);
		String target = null;
		if (id.endsWith("_helmet")) {
			target = "armor.head";
		} else if (id.endsWith("_chestplate")) {
			target = "armor.chest";
		} else if (id.endsWith("_leggings")) {
			target = "armor.legs";
		} else if (id.endsWith("_boots")) {
			target = "armor.feet";
		} else if ((id.endsWith("shield") || id.endsWith("totem_of_undying")) && !slots.containsKey("weapon.offhand")) {
			target = "weapon.offhand";
		}
		if (target == null) {
			for (int i = 0; i < 9 && target == null; i++) {
				if (!slots.containsKey("hotbar." + i)) {
					target = "hotbar." + i;
				}
			}
			for (int i = 0; i < 27 && target == null; i++) {
				if (!slots.containsKey("inventory." + i)) {
					target = "inventory." + i;
				}
			}
		}
		if (target == null) {
			say("The inventory is full.");
			return;
		}
		slots.put(target, spec);
	}

	private void back() {
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), parent);
	}

	private void save() {
		String n = name.text().trim();
		if (n.isEmpty()) {
			say("Give your kit a name.");
			return;
		}
		for (PracticeKit k : PracticeKit.ALL) {
			if (k.name.equalsIgnoreCase(n)) {
				say("That name is used by a built-in kit.");
				return;
			}
		}
		if (slots.isEmpty()) {
			say("Put at least one item in the kit.");
			return;
		}
		KitStore.Data d = new KitStore.Data();
		d.name = n;
		d.style = KitStore.STYLES[styleIdx];
		d.slots.putAll(slots);
		if (!KitStore.save(d)) {
			say("You can have up to " + KitStore.MAX_KITS + " custom kits. Delete one first.");
			return;
		}
		if (parent instanceof PvpPracticeScreen s) {
			s.select(n);
		}
		back();
	}

	// ------------------------------------------------------------------ drawing
	private void chip(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean sel, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = sel ? Theme.accent() : hov ? Theme.CARD_HOVER : Theme.CARD;
		Gfx.rrectBorder(c, x, y, w, h, 7, bg, sel ? Theme.accent() : hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
		Gfx.textC(c, label, x + w / 2, y + (h - 8) / 2, sel ? 0xFFFFFFFF : Theme.TEXT);
		hits.add(new Hit(x, y, w, h, b -> run.run()));
	}

	private void drawSpec(GuiGraphicsExtractor c, String spec, int x, int y) {
		c.item(stackOf(spec), x + 1, y + 1);
		int n = KitStore.countOf(spec);
		if (n > 1) {
			String t = String.valueOf(n);
			Gfx.text(c, t, x + S - 1 - Gfx.tw(t), y + S - 9, 0xFFFFFFFF);
		}
	}

	private void cell(GuiGraphicsExtractor c, int mx, int my, int x, int y, String spec, boolean hov) {
		Gfx.rrectBorder(c, x, y, S, S, 4, hov ? Theme.CARD_HOVER : Theme.CARD, hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
		if (spec != null) {
			drawSpec(c, spec, x, y);
		}
	}

	private void slot(GuiGraphicsExtractor c, int mx, int my, int x, int y, String slot) {
		boolean hov = mx >= x && mx < x + S && my >= y && my < y + S;
		String spec = slots.get(slot);
		cell(c, mx, my, x, y, spec, hov);
		if (hov) {
			hoverText = spec != null ? describe(spec) : prettySlot(slot);
		}
		hits.add(new Hit(x, y, S, S, b -> slotClick(slot, b)));
		slotRects.add(new SlotRect(x, y, slot));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		super.extractRenderState(c, mx, my, delta);
		hits.clear();
		slotRects.clear();
		hoverText = null;
		int w = Math.min(width - 16, 400), h = Math.min(height - 16, 236);
		int x = (width - w) / 2, y = (height - h) / 2;
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		int ix = x + 14, iw = w - 28;

		Gfx.text(c, "Kit Editor", ix, y + 10, Theme.TEXT);
		Gfx.text(c, "Build your inventory and save it as a kit.", ix + Gfx.tw("Kit Editor") + 10, y + 10, Theme.DIM);

		// inventory: armor column + offhand, 3 rows of inventory, hotbar
		int gx = ix, gy = y + 30;
		String[] armor = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};
		for (int i = 0; i < 4; i++) {
			slot(c, mx, my, gx, gy + i * S, armor[i]);
		}
		slot(c, mx, my, gx, gy + 4 * S + 6, "weapon.offhand");
		int ox = gx + S + 6;
		for (int r = 0; r < 3; r++) {
			for (int col = 0; col < 9; col++) {
				slot(c, mx, my, ox + col * S, gy + r * S, "inventory." + (r * 9 + col));
			}
		}
		for (int col = 0; col < 9; col++) {
			slot(c, mx, my, ox + col * S, gy + 3 * S + 8, "hotbar." + col);
		}

		// palette
		int px = ox + 9 * S + 14;
		int cw = (ix + iw - px - 8) / 3;
		for (int k = 0; k < CATS.length; k++) {
			final int kk = k;
			chip(c, mx, my, px + (k % 3) * (cw + 4), y + 28 + (k / 3) * 16, cw, 14, CATS[k], k == cat, () -> cat = kk);
		}
		List<P> items = PALETTE.get(cat);
		for (int i = 0; i < items.size(); i++) {
			P it = items.get(i);
			int cx = px + (i % 9) * S, cy = y + 66 + (i / 9) * S;
			boolean hov = mx >= cx && mx < cx + S && my >= cy && my < cy + S;
			cell(c, mx, my, cx, cy, it.spec(), hov);
			if (hov) {
				hoverText = it.label() + (it.spec().contains("[") ? "  " + it.spec().substring(it.spec().indexOf('[')) : "");
				if (hoverText.length() > 70) {
					hoverText = hoverText.substring(0, 69) + "...";
				}
			}
			hits.add(new Hit(cx, cy, S, S, b -> {
				if (b == 0) {
					if (Keys.down(340) || Keys.down(344)) {
						quickAdd(it.spec());
					} else {
						held = it.spec();
					}
				}
			}));
		}

		// hints + clear
		Gfx.text(c, "Click an item to pick it up, click a slot to put it down (items swap).", ix, y + 136, Theme.DIM);
		Gfx.text(c, "Shift+click: quick add  |  Right-click a slot: clear  |  Scroll on a slot: amount", ix, y + 147, Theme.DIM);
		chip(c, mx, my, ix, y + 160, 64, 16, "Clear all", false, () -> {
			slots.clear();
			held = null;
		});
		if (held != null) {
			Gfx.text(c, "Holding: " + stackOf(held).getHoverName().getString(), ix + 72, y + 164, Theme.accent());
		}

		// status / hover line
		int by = y + h - 30, bh = 22;
		boolean showStatus = System.currentTimeMillis() < statusUntil;
		String line = showStatus ? status : hoverText;
		if (line != null) {
			Gfx.text(c, line, ix, by - 14, showStatus ? 0xFFFF7777 : Theme.TEXT);
		}

		// bottom: back, name, bot style, save
		chip(c, mx, my, ix, by, 60, bh, "Back", false, this::back);
		int nx = ix + 66, nw = 110;
		Gfx.rrectBorder(c, nx, by, nw, bh, 7, Theme.CARD, Theme.CARD_BORDER);
		if (name.isEmpty()) {
			Gfx.text(c, "Kit name", nx + 8, by + 7, Theme.DIM);
		}
		name.draw(c, nx, by, nw, bh, nx + 8, nw - 16, true, Theme.accent(), Theme.TEXT);
		hits.add(new Hit(nx, by, nw, bh, b -> name.click(clickX)));
		chip(c, mx, my, ix + 182, by, 100, bh, "Bot: " + KitStore.STYLE_LABELS[styleIdx], false,
				() -> styleIdx = (styleIdx + 1) % KitStore.STYLES.length);
		int sw = 80, sx = ix + iw - sw;
		boolean hov = mx >= sx && mx < sx + sw && my >= by && my < by + bh;
		Gfx.rrect(c, sx, by, sw, bh, 8, hov ? Theme.accentHover() : Theme.accent());
		Gfx.textC(c, "Save kit", sx + sw / 2, by + 7, 0xFFFFFFFF);
		hits.add(new Hit(sx, by, sw, bh, b -> save()));

		if (held != null) {
			c.item(stackOf(held), mx - 8, my - 8);
		}
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		clickX = click.x();
		int b = click.button();
		for (Hit hit : new ArrayList<>(hits)) {
			if (hit.in(click.x(), click.y())) {
				hit.run().accept(b);
				return true;
			}
		}
		if (b == 1) {
			held = null;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		name.drag(click.x());
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		name.release();
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		for (SlotRect r : slotRects) {
			if (mx >= r.x() && mx < r.x() + S && my >= r.y() && my < r.y() + S) {
				String spec = slots.get(r.slot());
				if (spec != null && stackOf(spec).getMaxStackSize() > 1 && v != 0) {
					int step = (Keys.down(340) || Keys.down(344)) ? 8 : 1;
					int n = Math.max(1, Math.min(64, KitStore.countOf(spec) + (v > 0 ? step : -step)));
					slots.put(r.slot(), KitStore.withCount(spec, n));
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (k == 256) {
			if (held != null) {
				held = null;
			} else {
				back();
			}
			return true;
		}
		if (k == 257 || k == 335) {
			save();
			return true;
		}
		name.key(k);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		name.typed(input.codepoint());
		return true;
	}
}
