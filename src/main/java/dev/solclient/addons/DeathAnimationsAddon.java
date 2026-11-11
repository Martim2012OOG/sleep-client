package dev.solclient.addons;

import dev.solclient.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Add-ons > Death Animations: opens the settings and catalog of the Death Animations + mod that ships inside Sleep. */
public final class DeathAnimationsAddon implements Addon {

	private static final String SCREEN = "com.baom5.deathanimation.client.DeathAnimationsScreen";

	@Override
	public String id() {
		return "death_animations";
	}

	@Override
	public String name() {
		return "Death Animations";
	}

	@Override
	public String description() {
		return Lang.tr("48 death animations for players, with previews and settings.", "48 animações de morte para os jogadores, com pré-visualização e definições.");
	}

	@Override
	public String tag() {
		return "PvP";
	}

	@Override
	public String icon() {
		return "pvp_group";
	}

	@Override
	public void open(Screen parent) {
		try {
			Screen s = (Screen) Class.forName(SCREEN).getConstructor(Screen.class).newInstance(parent);
			MinecraftClient.getInstance().setScreen(s);
		} catch (Throwable t) {
			System.err.println("[Sleep] Death Animations: " + t);
			dev.solclient.social.Social.say(Lang.tr("Death Animations could not open.", "Não foi possível abrir as Death Animations."), true);
		}
	}
}
