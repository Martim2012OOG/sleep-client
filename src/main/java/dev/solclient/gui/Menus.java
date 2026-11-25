package dev.solclient.gui;

import dev.solclient.SolClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;

/** Swaps the vanilla title / pause screens for the Plegumo ones (toggle in Plegumo settings > Menus). */
public final class Menus {

	private Menus() {
	}

	/** Returns the screen to show instead of {@code s} (or {@code s} itself). */
	public static Screen replace(Screen s) {
		if (s == null || SolClient.settings == null) {
			return s;
		}
		if (s.getClass() == TitleScreen.class) {
			// Cinematic Intro: the intro video plays once before the first home screen
			Screen home = dev.solclient.mods.CinematicIntroMod.maybeIntro(SolClient.settings.customTitle.on() ? new PlegumoTitleScreen() : s);
			// very first start: the short setup (language, performance, HUD) comes first
			if (!SolClient.settings.onboarded.on()) {
				return new OnboardingScreen(home);
			}
			// 1 Nov - 31 Dec: the Christmas card (once, the first time you start the game in the season)
			return XmasCardScreen.maybeShow(home);
		}
		if (s.getClass() == GameMenuScreen.class && SolClient.settings.customPause.on()) {
			if (SolClient.settings.classicPause()) {
				return s; // "Classic + Caos": the real Minecraft screen (so Essential / Mod Menu add their buttons); PauseClassicMixin adds Caos's
			}
			if (PlegumoPauseScreen.allowVanilla) {
				PlegumoPauseScreen.allowVanilla = false;
				return s;
			}
			if (!((GameMenuScreen) s).shouldShowMenu()) {
				return s; // F3 + Esc: pause without a menu
			}
			return new PlegumoPauseScreen();
		}
		return s;
	}

	/** Fallback if the setScreen mixin did not apply: swap on the next tick. */
	public static void tick(MinecraftClient mc) {
		Screen cur = mc.currentScreen;
		if (cur != null && (cur.getClass() == TitleScreen.class || cur.getClass() == GameMenuScreen.class)) {
			Screen r = replace(cur);
			if (r != cur) {
				mc.setScreen(r);
			}
		}
	}
}
