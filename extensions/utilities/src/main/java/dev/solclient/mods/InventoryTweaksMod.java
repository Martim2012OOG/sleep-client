package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

/** Press a key with your inventory open to sort your backpack (the 27 slots above the hotbar). */
public class InventoryTweaksMod extends Mod {

	private final Option.Key sortKey = key("Sort Key", 82); // R
	private final Option.Num delay = num("Speed", 2, 1, 10, 1, " ticks");
	/** Swaps still to do, each {slotA, slotB}. */
	private final Deque<int[]> plan = new ArrayDeque<>();
	private boolean wasDown;
	private int wait;

	public InventoryTweaksMod() {
		super("Inventory Tweaks", "inventory_tweaks", "Sort your backpack with one key while your inventory is open.", Category.UTILITY);
	}

	@Override
	public void tick() {
		if (dev.solclient.ServerRules.hypixel()) {
			plan.clear(); // Hypixel: no automatic click sequences
			wasDown = false;
			return;
		}
		if (mc.player == null || mc.interactionManager == null || !(mc.currentScreen instanceof InventoryScreen)) {
			plan.clear();
			wasDown = false;
			return;
		}
		boolean down = sortKey.code() > 0 && Keys.down(sortKey.code());
		if (down && !wasDown && plan.isEmpty() && mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
			buildPlan();
		}
		wasDown = down;
		if (plan.isEmpty()) {
			return;
		}
		if (--wait > 0) {
			return;
		}
		wait = delay.i();
		int[] s = plan.poll();
		if (!mc.player.playerScreenHandler.getCursorStack().isEmpty() && s != null) {
			plan.clear(); // something else grabbed an item: stop, never lose anything
			return;
		}
		if (s != null) {
			swap(s[0], s[1]);
		}
	}

	/** Slots 9..35 of the player's screen handler are the backpack. */
	private void buildPlan() {
		var h = mc.player.playerScreenHandler;
		int n = 27;
		String[] key = new String[n];
		for (int i = 0; i < n; i++) {
			key[i] = keyOf(h.getSlot(9 + i).getStack());
		}
		// selection sort on the keys: the swaps are what we replay with clicks
		for (int i = 0; i < n; i++) {
			int best = i;
			for (int j = i + 1; j < n; j++) {
				if (key[j].compareTo(key[best]) < 0) {
					best = j;
				}
			}
			if (best != i && !key[best].equals(key[i])) {
				String t = key[i];
				key[i] = key[best];
				key[best] = t;
				plan.add(new int[] {9 + i, 9 + best});
			}
		}
	}

	/** Empty slots sort last; the rest by item, then bigger stacks first. */
	private static String keyOf(ItemStack st) {
		if (st.isEmpty()) {
			return "~";
		}
		return Registries.ITEM.getId(st.getItem()).toString() + String.format("%03d", 999 - st.getCount());
	}

	/** Swap two slots with three clicks: pick up A, put on B (takes B's item), put B's item back on A. */
	private void swap(int a, int b) {
		int id = mc.player.playerScreenHandler.syncId;
		mc.interactionManager.clickSlot(id, a, 0, SlotActionType.PICKUP, mc.player);
		mc.interactionManager.clickSlot(id, b, 0, SlotActionType.PICKUP, mc.player);
		mc.interactionManager.clickSlot(id, a, 0, SlotActionType.PICKUP, mc.player);
	}
}
