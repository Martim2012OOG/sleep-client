package dev.sleepclient.mods;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Inventory Tweaks: small buttons on chests, shulker boxes, barrels and your inventory: sort, take everything, put everything.
 * The moves are normal inventory clicks sent a few per tick (so servers see a player, not a burst).
 */
public class InventoryButtonsMod extends Mod {

	private static boolean hooked;
	private static InventoryButtonsMod self;

	private final dev.sleepclient.option.Option.Num clicksPerTick = num("Clicks Per Tick", 6, 1, 20, 1, "");
	private final dev.sleepclient.option.Option.Bool includeHotbar = bool("Put All Includes Hotbar", true);
	private final dev.sleepclient.option.Option.Bool showTips = bool("Button Tooltips", true);

	private record Op(int syncId, int slot, int button, SlotActionType type) {
	}

	private final ArrayDeque<Op> queue = new ArrayDeque<>();
	private final List<int[]> rects = new ArrayList<>();
	private final List<Runnable> acts = new ArrayList<>();

	public InventoryButtonsMod() {
		super("Inventory Buttons", "inventory_buttons", "Buttons on chests and your inventory: sort, take all, put all.", Category.UTILITY);
		self = this;
		enabled = true;
	}

	@Override
	protected void onEnable() {
		if (hooked) {
			return;
		}
		hooked = true;
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof HandledScreen<?> hs)) {
				return;
			}
			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
				if (self != null && self.enabled) {
					self.render(hs, ctx, mx, my);
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((Screen s, Click click) -> !(self != null && self.enabled && self.click(click)));
		});
	}

	@Override
	public void tick() {
		if (dev.sleepclient.ServerRules.hypixel()) {
			queue.clear(); // Hypixel: no automatic click sequences
			return;
		}
		if (queue.isEmpty()) {
			return;
		}
		if (mc.player == null || mc.interactionManager == null || !(mc.currentScreen instanceof HandledScreen<?> hs)
				|| hs.getScreenHandler().syncId != queue.peek().syncId()) {
			queue.clear();
			return;
		}
		int n = Math.max(1, Math.round(clicksPerTick.f()));
		while (n-- > 0 && !queue.isEmpty()) {
			Op o = queue.poll();
			mc.interactionManager.clickSlot(o.syncId(), o.slot(), o.button(), o.type(), mc.player);
		}
	}

	// ------------------------------------------------------------------ slots
	private static boolean supported(ScreenHandler h) {
		return h instanceof net.minecraft.screen.GenericContainerScreenHandler || h instanceof net.minecraft.screen.ShulkerBoxScreenHandler
				|| h instanceof net.minecraft.screen.HopperScreenHandler || h instanceof net.minecraft.screen.Generic3x3ContainerScreenHandler;
	}

	private static List<Slot> containerSlots(ScreenHandler h) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : h.slots) {
			if (!(s.inventory instanceof PlayerInventory)) {
				out.add(s);
			}
		}
		return out;
	}

	/** The 27 main inventory slots (and the hotbar when asked), in screen order. */
	private List<Slot> playerSlots(ScreenHandler h, boolean hotbar) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : h.slots) {
			if (s.inventory instanceof PlayerInventory && (s.getIndex() >= 9 && s.getIndex() < 36 || hotbar && s.getIndex() < 9)) {
				out.add(s);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ actions
	private void takeAll(ScreenHandler h) {
		for (Slot s : containerSlots(h)) {
			if (s.hasStack()) {
				queue.add(new Op(h.syncId, s.id, 0, SlotActionType.QUICK_MOVE));
			}
		}
	}

	private void putAll(ScreenHandler h) {
		for (Slot s : playerSlots(h, includeHotbar.on())) {
			if (s.hasStack()) {
				queue.add(new Op(h.syncId, s.id, 0, SlotActionType.QUICK_MOVE));
			}
		}
	}

	private void sort(ScreenHandler h, List<Slot> slots) {
		int n = slots.size();
		ItemStack[] st = new ItemStack[n];
		for (int i = 0; i < n; i++) {
			st[i] = slots.get(i).getStack().copy();
		}
		int sync = h.syncId;
		// 1) merge half-empty stacks of the same thing
		for (int i = 0; i < n; i++) {
			if (st[i].isEmpty() || st[i].getCount() >= st[i].getMaxCount()) {
				continue;
			}
			for (int j = i + 1; j < n && st[i].getCount() < st[i].getMaxCount(); j++) {
				if (st[j].isEmpty() || !ItemStack.areItemsAndComponentsEqual(st[i], st[j])) {
					continue;
				}
				int total = st[i].getCount() + st[j].getCount();
				int newI = Math.min(st[i].getMaxCount(), total), newJ = total - newI;
				queue.add(new Op(sync, slots.get(j).id, 0, SlotActionType.PICKUP));
				queue.add(new Op(sync, slots.get(i).id, 0, SlotActionType.PICKUP));
				if (newJ > 0) {
					queue.add(new Op(sync, slots.get(j).id, 0, SlotActionType.PICKUP));
				}
				st[i].setCount(newI);
				if (newJ == 0) {
					st[j] = ItemStack.EMPTY;
				} else {
					st[j].setCount(newJ);
				}
			}
		}
		// 2) the order we want: by item, biggest stacks first
		List<ItemStack> want = new ArrayList<>();
		for (ItemStack s : st) {
			if (!s.isEmpty()) {
				want.add(s);
			}
		}
		want.sort(Comparator.<ItemStack, String>comparing(s -> Registries.ITEM.getId(s.getItem()).toString()).thenComparing(s -> -s.getCount()));
		// 3) put them there with swaps
		for (int pos = 0; pos < want.size(); pos++) {
			ItemStack target = want.get(pos);
			int j = -1;
			for (int k = pos; k < n; k++) {
				if (st[k] == target) {
					j = k;
					break;
				}
			}
			if (j < 0 || j == pos) {
				continue;
			}
			if (!st[pos].isEmpty()) {
				queue.add(new Op(sync, slots.get(pos).id, 0, SlotActionType.PICKUP));
			}
			queue.add(new Op(sync, slots.get(j).id, 0, SlotActionType.PICKUP));
			queue.add(new Op(sync, slots.get(pos).id, 0, SlotActionType.PICKUP));
			ItemStack t = st[pos];
			st[pos] = st[j];
			st[j] = t;
		}
	}

	// ------------------------------------------------------------------ buttons
	private boolean click(Click click) {
		if (click.button() != 0) {
			return false;
		}
		for (int i = rects.size() - 1; i >= 0; i--) {
			int[] r = rects.get(i);
			if (click.x() >= r[0] && click.x() < r[0] + r[2] && click.y() >= r[1] && click.y() < r[1] + r[3]) {
				acts.get(i).run();
				return true;
			}
		}
		return false;
	}

	private void render(HandledScreen<?> hs, DrawContext c, int mx, int my) {
		rects.clear();
		acts.clear();
		ScreenHandler h = hs.getScreenHandler();
		List<Slot> own = playerSlots(h, false);
		if (own.isEmpty()) {
			return;
		}
		int maxBottom = 0;
		for (Slot s : h.slots) {
			maxBottom = Math.max(maxBottom, s.y + 18);
		}
		// the screen's real top-left corner and size (read from the screen itself: guessing broke on the creative / modded inventories)
		int x0, y0, bgW;
		try {
			var acc = (dev.sleepclient.mixin.overlay.HandledScreenAccessor) hs;
			x0 = acc.sol$x();
			y0 = acc.sol$y();
			bgW = acc.sol$w();
		} catch (Throwable t) {
			x0 = (hs.width - 176) / 2;
			y0 = (hs.height - (maxBottom + 6)) / 2;
			bgW = 176;
		}
		String tip = null;
		if (supported(h)) {
			int bx = x0 + bgW - 7 - 3 * 14, by = y0 + 3;
			tip = button(c, mx, my, bx, by, 0, "Sort the chest", tip, () -> sort(h, containerSlots(h)));
			tip = button(c, mx, my, bx + 14, by, 1, "Take everything", tip, () -> takeAll(h));
			tip = button(c, mx, my, bx + 28, by, 2, "Put everything in", tip, () -> putAll(h));
		}
		// above the right end of the first row of your inventory, where every inventory has a free corner
		Slot rowEnd = own.get(Math.min(8, own.size() - 1));
		int ly = y0 + own.get(0).y - 14;
		tip = button(c, mx, my, x0 + rowEnd.x + 4, ly, 0, "Sort my inventory", tip, () -> sort(h, own));
		if (tip != null && showTips.on()) {
			var tr = mc.textRenderer;
			int w = tr.getWidth(tip);
			int tx = Math.min(mx + 8, hs.width - w - 8), ty = my - 16;
			c.fill(tx - 3, ty - 2, tx + w + 3, ty + 10, 0xE0101218);
			c.drawText(tr, tip, tx, ty, 0xFFFFFFFF, false);
		}
	}

	private String button(DrawContext c, int mx, int my, int x, int y, int icon, String tip, String curTip, Runnable run) {
		boolean hov = mx >= x && mx < x + 12 && my >= y && my < y + 12;
		c.fill(x, y, x + 12, y + 12, 0xFF000000);
		c.fill(x + 1, y + 1, x + 11, y + 11, hov ? 0xFF4A4E5C : 0xFF2B2E38);
		int col = hov ? 0xFFFFFFFF : 0xFFC9CCD8;
		switch (icon) {
			case 0 -> { // sort: three bars getting shorter
				c.fill(x + 2, y + 3, x + 10, y + 4, col);
				c.fill(x + 2, y + 5, x + 8, y + 6, col);
				c.fill(x + 2, y + 7, x + 6, y + 8, col);
				c.fill(x + 2, y + 9, x + 4, y + 10, col);
			}
			case 1 -> { // take: arrow down into the inventory
				c.fill(x + 5, y + 2, x + 7, y + 7, col);
				c.fill(x + 3, y + 6, x + 9, y + 7, col);
				c.fill(x + 4, y + 7, x + 8, y + 8, col);
				c.fill(x + 5, y + 8, x + 7, y + 9, col);
				c.fill(x + 3, y + 10, x + 9, y + 11, col);
			}
			default -> { // put: arrow up into the chest
				c.fill(x + 5, y + 5, x + 7, y + 10, col);
				c.fill(x + 3, y + 5, x + 9, y + 6, col);
				c.fill(x + 4, y + 4, x + 8, y + 5, col);
				c.fill(x + 5, y + 3, x + 7, y + 4, col);
				c.fill(x + 3, y + 1, x + 9, y + 2, col);
			}
		}
		rects.add(new int[] {x, y, 12, 12});
		acts.add(run);
		return hov ? tip : curTip;
	}
}
