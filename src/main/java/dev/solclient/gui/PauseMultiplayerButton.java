package dev.solclient.gui;

import dev.solclient.Lang;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * A "Multiplayer" button in the pause menu while you are on a server (like Lunar): it opens the server list without leaving first, and joining another
 * server from there takes you straight to it.
 */
public final class PauseMultiplayerButton {

	private static boolean hooked;

	private PauseMultiplayerButton() {
	}

	public static void register() {
		if (hooked) {
			return;
		}
		hooked = true;
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof GameMenuScreen) || client.getCurrentServerEntry() == null) {
				return;
			}
			try {
				ButtonWidget anchor = null;
				for (var wd : Screens.getButtons(screen)) {
					if (wd instanceof ButtonWidget bw && bw.getMessage().getContent() instanceof TranslatableTextContent t
							&& ("menu.disconnect".equals(t.getKey()) || "menu.returnToMenu".equals(t.getKey()))) {
						anchor = bw;
					}
				}
				if (anchor == null) {
					return;
				}
				ButtonWidget multi = ButtonWidget.builder(Text.literal(Lang.tr("Multiplayer", "Multijogador")), b -> client.setScreen(new MultiplayerScreen(screen)))
						.dimensions(anchor.getX(), anchor.getY() + anchor.getHeight() + 4, anchor.getWidth(), 20).build();
				Screens.getButtons(screen).add(multi);
			} catch (Throwable t) {
				System.err.println("[Sleep] pause menu Multiplayer button failed: " + t);
			}
		});
	}
}
