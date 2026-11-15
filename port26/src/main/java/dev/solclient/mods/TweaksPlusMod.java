package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** PvP Tweaks: Hit Delay Fix (missing a hit no longer locks your clicks) and Shiny Potions (potions glow like Lunar's). */
public class TweaksPlusMod extends Mod {

	public static TweaksPlusMod instance;
	private final Option.Bool hitDelay = bool("Hit Delay Fix", true);
	private final Option.Bool shinyPots = bool("Shiny Potions", true);
	private final Option.Bool shinySplashOnly = bool("Only Splash Potions", false);

	public TweaksPlusMod() {
		super("PvP Tweaks", "pvp_tweaks", "Hit Delay Fix and Shiny Potions, like Lunar and Badlion.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean hitDelayFix() {
		return instance != null && instance.enabled && instance.hitDelay.on();
	}

	/** Potions get the enchanted shine. */
	public static boolean shiny(ItemStack st) {
		if (instance == null || !instance.enabled || !instance.shinyPots.on() || st == null) {
			return false;
		}
		if (instance.shinySplashOnly.on()) {
			return st.is(Items.SPLASH_POTION) || st.is(Items.LINGERING_POTION);
		}
		return st.has(DataComponents.POTION_CONTENTS) && (st.is(Items.POTION) || st.is(Items.SPLASH_POTION) || st.is(Items.LINGERING_POTION));
	}
}
