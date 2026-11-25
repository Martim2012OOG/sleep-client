package dev.solclient.gui;

import dev.solclient.SolClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Link to the original Mod Menu (by reflection: Caos does not depend on it). Settings > Menus > "Mods Screen" decides if
 * the mods list opens Caos's own tab or the original Mod Menu screen.
 */
public final class ModMenuLink {

	private ModMenuLink() {
	}

	/** Mod Menu is installed. */
	public static boolean present() {
		return FabricLoader.getInstance().isModLoaded("modmenu");
	}

	/** Essential is installed (it adds its own buttons to the pause menu). */
	public static boolean essentialPresent() {
		FabricLoader l = FabricLoader.getInstance();
		return l.isModLoaded("essential") || l.isModLoaded("essential-loader");
	}

	/** The player chose the original Mod Menu AND it is installed. */
	public static boolean preferred() {
		return present() && SolClient.settings != null && SolClient.settings.modsScreen.idx() == 1;
	}

	/** Opens Mod Menu's mods screen; {@code parent} is where Esc / Done goes back to. False if it could not. */
	public static boolean open(Screen parent) {
		if (!present()) {
			return false;
		}
		try {
			Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen", true, ModMenuLink.class.getClassLoader());
			Object s = c.getConstructor(Screen.class).newInstance(parent);
			if (s instanceof Screen sc) {
				MinecraftClient.getInstance().setScreen(sc);
				return true;
			}
		} catch (Throwable t) {
			System.err.println("[Caos] Could not open Mod Menu: " + t);
		}
		return false;
	}
}
