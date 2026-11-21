package dev.sleepclient.gui;

import dev.sleepclient.SleepClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Swaps the vanilla title / pause screens for the Plegumo ones (toggle in Plegumo settings > Menus). */
public final class Menus {

	private Menus() {
	}

	/** Returns the screen to show instead of {@code s} (or {@code s} itself). */
	public static Screen replace(Screen s) {
		if (s == null || SleepClient.settings == null) {
			return s;
		}
		if (s.getClass() == TitleScreen.class) {
			// Cinematic Intro: the intro video plays once before the first home screen
			Screen home = dev.sleepclient.mods.CinematicIntroMod.maybeIntro(SleepClient.settings.customTitle.on() ? new PlegumoTitleScreen() : s);
			// very first start: the short setup (language, performance, HUD) comes first
			if (!SleepClient.settings.onboarded.on()) {
				return new OnboardingScreen(home);
			}
			// 1 Nov - 31 Dec: the Christmas card (once, the first time you start the game in the season)
			return XmasCardScreen.maybeShow(home);
		}
		if (s.getClass() == PauseScreen.class && SleepClient.settings.customPause.on()) {
			if (SleepClient.settings.classicPause()) {
				return s; // "Classic + Sleep": the real Minecraft screen (so Essential / Mod Menu add their buttons); PauseClassicMixin adds Sleep's
			}
			if (PlegumoPauseScreen.allowVanilla) {
				PlegumoPauseScreen.allowVanilla = false;
				return s;
			}
			if (!((PauseScreen) s).showsPauseMenu()) {
				return s; // F3 + Esc: pause without a menu
			}
			return new PlegumoPauseScreen();
		}
		return s;
	}

	/** Fallback if the setScreen mixin did not apply: swap on the next tick. */
	public static void tick(Minecraft mc) {
		Screen cur = dev.sleepclient.compat.Mc.screen(mc);
		if (cur != null && (cur.getClass() == TitleScreen.class || cur.getClass() == PauseScreen.class)) {
			Screen r = replace(cur);
			if (r != cur) {
				dev.sleepclient.compat.Mc.setScreen(mc, r);
			}
		}
	}
}
