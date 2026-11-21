package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Locks hotbar slots so you can't drop their items by accident. */
public class SlotLockMod extends Mod {

	public static SlotLockMod instance;
	private final Option.Key key = key("Lock / Unlock Key", 76); // L
	private final boolean[] locked = new boolean[9];
	private boolean down;

	public SlotLockMod() {
		super("Slot Lock", "slot_lock", "Lock a hotbar slot (L) so Q can't drop it.", Category.UTILITY);
		instance = this;
	}

	private int slot() {
		return mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
	}

	@Override
	public void tick() {
		boolean d = dev.sleepclient.compat.Mc.screen(mc) == null && dev.sleepclient.Keys.down(key.code());
		int s = slot();
		if (d && !down && s >= 0 && s < 9) {
			locked[s] = !locked[s];
			if (mc.gui != null) dev.sleepclient.compat.Mc.overlayMessage(mc, net.minecraft.network.chat.Component.literal("Slot " + (s + 1) + (locked[s] ? " locked" : " unlocked")), false);
		}
		down = d;
	}

	/** Called at the start of each tick, before the game reads the drop key. */
	public static void blockDrop() {
		SlotLockMod m = instance;
		if (m == null || !m.enabled) return;
		int s = m.slot();
		if (s >= 0 && s < 9 && m.locked[s]) {
			boolean any = false;
			while (m.mc.options.keyDrop.consumeClick()) any = true;
			if (any && m.mc.gui != null) dev.sleepclient.compat.Mc.overlayMessage(m.mc, net.minecraft.network.chat.Component.literal("This slot is locked (press L to unlock)"), false);
		}
	}
}
