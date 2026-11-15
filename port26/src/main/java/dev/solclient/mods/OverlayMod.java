package dev.solclient.mods;

import dev.solclient.mixin.overlay.PersistentProjectileAccessor;
import dev.solclient.mixin.overlay.WorldRendererInvoker;
import dev.solclient.option.Option;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Lunar-style "Overlay" mod: texture overlays, tweaks and modifiers. */
public class OverlayMod extends Mod {

	public static OverlayMod instance;

	// First Person Options
	private final Option.Choice glint;
	private final Option.Bool minimalBobbing;
	// fire overlay height lives in LowFireMod now
	@SuppressWarnings("unused")
	private final Option.Num fireBlockHeight; // sem efeito ainda
	private final Option.Num shieldHeight;
	private final Option.Num pumpkin;
	private final Option.Num spyglass;
	private final Option.Num frost;

	// World Options
	private final Option.Bool hideFoliage;
	private final Option.Bool groundArrows;
	private final Option.Bool hidePlacedSkulls;
	private final Option.Choice hideEndportals;
	private final Option.Bool entityShadows;
	private final Option.Bool stuckArrows;

	// Texture Options (os que estão marcados "sem efeito" só existem no menu)
	@SuppressWarnings("unused")
	private final Option.Bool clearGlass, powderedSnowOutline, colouredString, customFishingLine,
			oreOutlines, barrierOutlines, customXpOrb;

	// Entity Render Options
	private final Option.Bool headlessHorse;
	private final Option.Bool disableDamage;
	private final Option.Bool disableDeath;
	private final Option.Bool disableFire;

	private boolean foliageApplied;

	public OverlayMod() {
		super("Overlay", "overlay", "Configure various texture overlays, tweaks, and modifiers.", Category.VISUAL);
		instance = this;
		header("First Person Options");
		glint = choice("Show Enchantment Glint", 0, "All", "Items", "Armor", "None");
		minimalBobbing = bool("Minimal View Bobbing", true);
		fireBlockHeight = num("Fire Height (Block)", 1, 0, 2, 0.05F, "");
		shieldHeight = num("Shield Height", 0.5F, 0, 2, 0.05F, "");
		pumpkin = num("Pumpkin Overlay", 1, 0, 1, 0.05F, "");
		spyglass = num("Spyglass Overlay", 1, 0, 1, 0.05F, "");
		frost = num("Frost Overlay", 1, 0, 1, 0.05F, "");

		header("World Options");
		hideFoliage = bool("Hide Foliage", false);
		groundArrows = bool("Show Ground Arrows", true);
		hidePlacedSkulls = bool("Hide Placed Skulls", false);
		hideEndportals = choice("Hide Endportals", 0, "Never", "Always");
		entityShadows = bool("Entity Shadows", true);
		stuckArrows = bool("Show Stuck Arrows", true);

		header("Texture Options");
		clearGlass = bool("Clear Glass", true);
		powderedSnowOutline = bool("Powdered Snow Outline", false);
		colouredString = bool("Colored String", true);
		customFishingLine = bool("Custom Fishing Line", true);
		oreOutlines = bool("Ore Outlines", true);
		barrierOutlines = bool("Barrier Outlines", true);
		customXpOrb = bool("Custom XP Orb", true);

		header("Entity Render Options");
		headlessHorse = bool("Headless Horse", false);
		disableDamage = bool("Disable Damage Overlay", false);
		disableDeath = bool("Disable Death Animation", false);
		disableFire = bool("Disable Fire Overlay", false);
	}

	private static boolean on() {
		return instance != null && instance.enabled;
	}

	// ------------------------------------------------------------ life cycle
	@Override
	public void tick() {
		boolean want = hideFoliage.on();
		if (want != foliageApplied && mc.level != null) {
			foliageApplied = want;
			reloadChunks();
		}
	}

	@Override
	protected void onDisable() {
		if (foliageApplied) {
			foliageApplied = false;
			reloadChunks();
		}
	}

	/** Rebuilds the chunk meshes so foliage appears/disappears (same as F3+A). */
	private void reloadChunks() {
		try {
			if (mc.level != null && mc.levelRenderer != null) {
				((WorldRendererInvoker) (Object) dev.solclient.compat.Mc.levelObj(mc)).sol$reload();
			}
		} catch (Throwable t) {
			System.err.println("[Sleep] Hide Foliage: could not reload chunks: " + t);
		}
	}

	// -------------------------------------------------------- first person
	/** ItemStackMixin: should this stack keep its enchantment glint? */
	public static boolean glintAllowed(ItemStack stack) {
		if (!on()) {
			return true;
		}
		int mode = instance.glint.idx();
		if (mode == 0) {
			return true;
		}
		if (mode == 3) {
			return false;
		}
		boolean armor = stack.has(DataComponents.EQUIPPABLE);
		return mode == 1 ? !armor : armor;
	}

	public static boolean minimalBobbing() {
		return on() && instance.minimalBobbing.on();
	}

	/** Used by ShieldHeightMixin: extra equip progress (lowers the held shield). */
	public static float shieldEquip(float equip) {
		if (!on()) {
			return equip;
		}
		return equip + (1F - instance.shieldHeight.f()) * 0.5F;
	}

	/** Used by InGameHudMixin: opacity multiplier for the overlay texture with this id. */
	public static float overlayOpacity(String textureId, float original) {
		if (textureId.contains("pumpkin") && !BactroTweaksMod.pumpkinBlur()) {
			return 0F;
		}
		if (!on()) {
			return original;
		}
		if (textureId.contains("pumpkin")) {
			return original * instance.pumpkin.f();
		}
		if (textureId.contains("spyglass")) {
			return original * instance.spyglass.f();
		}
		if (textureId.contains("powder_snow")) {
			return original * instance.frost.f();
		}
		return original;
	}

	public static boolean hideFire() {
		return on() && instance.disableFire.on();
	}

	// ----------------------------------------------------------- world
	public static boolean isHiddenFoliage(BlockState s) {
		if (!on() || !instance.hideFoliage.on()) {
			return false;
		}
		return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
				|| s.is(Blocks.LARGE_FERN) || s.is(Blocks.DEAD_BUSH) || s.is(BlockTags.FLOWERS);
	}

	public static boolean hideGroundArrow(Entity e) {
		if (!on() || instance.groundArrows.on()) {
			return false;
		}
		if (!(e instanceof AbstractArrow) || e instanceof ThrownTrident) {
			return false;
		}
		try {
			return ((PersistentProjectileAccessor) (Object) e).sol$inGround();
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean hidePlacedSkulls() {
		return on() && instance.hidePlacedSkulls.on();
	}

	public static boolean hideEndportals() {
		return on() && instance.hideEndportals.idx() == 1;
	}

	public static boolean entityShadows() {
		return !on() || instance.entityShadows.on();
	}

	public static boolean stuckArrows() {
		return !on() || instance.stuckArrows.on();
	}

	// ----------------------------------------------------------- entities
	public static boolean headlessHorse() {
		return on() && instance.headlessHorse.on();
	}

	public static boolean disableDamage() {
		return on() && instance.disableDamage.on();
	}

	public static boolean disableDeathAnimation() {
		return on() && instance.disableDeath.on();
	}
}
