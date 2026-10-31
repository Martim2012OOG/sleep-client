package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Durability Overlay: every armour piece on a player gets a coloured outline that goes from green (new) to red (almost
 * broken). Choose whose armour shows it: only yours, only other players', or everyone's. Switch the mod off for none.
 */
public class DurabilityOverlayMod extends Mod {

	public static DurabilityOverlayMod instance;

	private final Option.Choice who = tabs("Show On", 2, "Only Me", "Only Others", "Everyone");
	private final Option.Bool onlyDamaged = bool("Only Damaged Armour", false);
	private final Option.Num range = num("Range (Others)", 48, 8, 128, 4, " blocks");

	public DurabilityOverlayMod() {
		super("Durability Overlay", "durability_overlay", "Armour outline that turns from green to red as it wears out. Yours, theirs or everyone's.", Category.VISUAL);
		instance = this;
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** True when this entity's armour should get the outline (also makes vanilla run its outline pass for it). */
	public static boolean wants(Entity e) {
		DurabilityOverlayMod m = instance;
		if (m == null || !m.enabled || !(e instanceof PlayerEntity p) || m.mc.player == null) {
			return false;
		}
		boolean me = e == m.mc.player;
		int w = m.who.idx();
		if (me ? w == 1 : w == 0) {
			return false;
		}
		if (!me && e.squaredDistanceTo(m.mc.player) > m.range.f() * m.range.f()) {
			return false;
		}
		for (EquipmentSlot s : SLOTS) {
			if (colourFor(p.getEquippedStack(s)) != 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean wantsId(int entityId) {
		DurabilityOverlayMod m = instance;
		if (m == null || !m.enabled || m.mc.world == null) {
			return false;
		}
		return wants(m.mc.world.getEntityById(entityId));
	}

	/** True when the body itself must not be outlined (only the armour pieces are). */
	public static boolean armourOnly(int entityId) {
		DurabilityOverlayMod m = instance;
		if (m == null || !m.enabled || m.mc.world == null) {
			return false;
		}
		Entity e = m.mc.world.getEntityById(entityId);
		return wants(e) && !e.isGlowing() && !BodyGlowMod.glows(e);
	}

	/** Outline colour (ARGB) for one armour piece, or 0 when it gets none. */
	public static int colourFor(ItemStack s) {
		DurabilityOverlayMod m = instance;
		if (m == null || s == null || s.isEmpty() || !s.isDamageable()) {
			return 0;
		}
		if (m.onlyDamaged.on() && s.getDamage() <= 0) {
			return 0;
		}
		float left = 1F - (float) s.getDamage() / Math.max(1, s.getMaxDamage());
		return 0xFF000000 | EquipmentMod.colour(left);
	}
}
