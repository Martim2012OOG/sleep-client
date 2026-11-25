package dev.solclient.mods;

import dev.solclient.option.Option;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ghost Item Fix (anti-ghost): after you throw / eat / drink something, it asks the SERVER to send the real inventory
 * again, so a used item never stays as a ghost and an identical stack never stays invisible.
 * <p>
 * RULES (see CLAUDE.md, "Inventory sync"): the server is always the boss.
 * <ul>
 * <li>Never cancel, delay or overwrite inventory packets from the server.</li>
 * <li>Never change the inventory on our side (no predicting, no moving, no clicking real slots).</li>
 * <li>The only thing sent is a "resync request": a middle-click (CLONE) on a hotbar slot with an outdated revision
 * number. In survival a middle-click does nothing, and the outdated revision makes the server answer with the full,
 * real inventory + cursor (vanilla behaviour). Nothing moves, nothing can drop.</li>
 * </ul>
 * Why the old version broke: it fixed ghosts by really clicking the slot (pick up + put back) and waited a fixed 8
 * ticks for the server. With lag the server's answer came later than that, so the mod thought the slot was empty and
 * stopped after the first click: the server had already put the potion on your (invisible) cursor. That is the
 * "potion that only appears when I click a slot", and the clicks with old data also left ghosts in the hotbar.
 */
public class GhostItemFixMod extends Mod {

	public static GhostItemFixMod instance;

	private final Option.Header h1 = header("Asks the server for the real inventory after you use items");
	private final Option.Bool eating = bool("Food, Potions & Drinks", true);

	private static final Item[] THROWN = {Items.SPLASH_POTION, Items.LINGERING_POTION, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE,
			Items.SNOWBALL, Items.EGG, Items.WIND_CHARGE, Items.FIREWORK_ROCKET};

	/** World ticks at which to ask for a resync (two of them: a quick one, and a later one for laggy connections). */
	private long syncA = -1, syncB = -1, lastSync = -1000;
	private boolean wasUsing;
	private long cursorStuckSince = -1;

	public GhostItemFixMod() {
		super("Ghost Item Fix", "ghost_item_fix", "Fixes items that stay as ghosts or vanish after you use them (throw, eat, drink).", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private static boolean thrown(ItemStack s) {
		for (Item i : THROWN) {
			if (s.is(i)) {
				return true;
			}
		}
		return false;
	}

	/** Round trip to the server in ticks (at least 2), so the late check waits long enough even with high ping. */
	private int pingTicks() {
		try {
			var e = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
			int ms = e == null ? 100 : e.getLatency();
			return Math.max(2, Math.min(60, ms / 50 + 2));
		} catch (RuntimeException ex) {
			return 5;
		}
	}

	/** Something was used up: schedule the resync requests (many quick throws = one request after the last one). */
	private void schedule(long now) {
		int rtt = pingTicks();
		syncA = now + rtt + 2;
		syncB = now + rtt * 2 + 15;
	}

	/** Right click with an item in hand (called from the use-item event). Only reads, never changes anything. */
	public void onUse(Player p, InteractionHand hand) {
		if (mc.player == null || mc.level == null || p != mc.player || p.isCreative()) {
			return;
		}
		ItemStack s = p.getItemInHand(hand);
		if (s.isEmpty() || !thrown(s)) {
			return;
		}
		schedule(mc.level.getGameTime());
	}

	/**
	 * Asks the server to send the real inventory: an outdated-revision middle-click on the selected hotbar slot.
	 * No local change at all (the packet is sent directly, not through the client's click prediction).
	 */
	private void requestResync(Player p, long now) {
		if (mc.getConnection() == null || p.isCreative() || now - lastSync < 4) {
			return;
		}
		InventoryMenu h = p.inventoryMenu;
		if (p.containerMenu != h) {
			return; // a chest / other screen is open: the server keeps that one in sync itself
		}
		lastSync = now;
		int slot = 36 + p.getInventory().getSelectedSlot(); // hotbar slot in the player screen
		int staleRevision = (h.getStateId() + 1000) & 0x7FFF;
		mc.getConnection().send(new ServerboundContainerClickPacket(h.containerId, staleRevision, (short) slot, (byte) 2, ContainerInput.CLONE,
				new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
	}

	@Override
	public void tick() {
		Player p = mc.player;
		if (p == null || mc.level == null) {
			syncA = syncB = -1;
			return;
		}
		long now = mc.level.getGameTime();
		// food / drinks: when a use really finishes, resync the same way
		boolean using = eating.on() && p.isUsingItem();
		if (wasUsing && !using) {
			schedule(now);
		}
		wasUsing = using;
		if (syncA >= 0 && now >= syncA) {
			syncA = -1;
			requestResync(p, now);
		}
		if (syncB >= 0 && now >= syncB) {
			syncB = -1;
			requestResync(p, now);
		}
		// an item stuck on the cursor with no screen open is invisible: give it back the vanilla way (closing the
		// inventory makes the server put the cursor item back into the inventory)
		boolean stuck = dev.solclient.compat.Mc.screen(mc) == null && p.containerMenu == p.inventoryMenu && !p.inventoryMenu.getCarried().isEmpty();
		if (stuck) {
			if (cursorStuckSince < 0) {
				cursorStuckSince = now;
			} else if (now - cursorStuckSince > 20 && mc.getConnection() != null) {
				mc.getConnection().send(new ServerboundContainerClosePacket(p.inventoryMenu.containerId));
				cursorStuckSince = -1;
				schedule(now);
			}
		} else {
			cursorStuckSince = -1;
		}
	}
}
