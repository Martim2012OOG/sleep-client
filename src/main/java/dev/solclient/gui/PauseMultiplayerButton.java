package dev.solclient.gui;

import dev.solclient.Lang;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * A "Multiplayer" button in the pause menu while you are on a server (like Lunar), right ABOVE the Mods button: it opens the server list without
 * leaving first, and joining another server from there takes you straight to it. The buttons from Mods down move one row lower to make room.
 */
public final class PauseMultiplayerButton {

	private static boolean hooked;
	private static final Map<Screen, Boolean> DONE = new WeakHashMap<>();

	private PauseMultiplayerButton() {
	}

	public static void register() {
		if (hooked) {
			return;
		}
		hooked = true;
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof GameMenuScreen) {
				insert(screen, screen.children(), b -> Screens.getButtons(screen).add(b));
			}
		});
	}

	/** Adds the button once per screen (the Caos pause menu calls it first, the event is the fallback). Returns true when it is there. */
	public static boolean insert(Screen screen, List<? extends Element> children, Consumer<ButtonWidget> add) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getCurrentServerEntry() == null) {
			return false;
		}
		if (DONE.containsKey(screen)) {
			return true;
		}
		try {
			int cx = screen.width / 2;
			PressableWidget mods = null, disconnect = null;
			for (Element o : children) {
				if (!(o instanceof PressableWidget w) || w.getX() < cx - 110 || w.getX() + w.getWidth() > cx + 110) {
					continue; // only the middle column (Essential / Mod Menu icons sit on the sides)
				}
				String txt = w.getMessage() == null ? "" : w.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
				boolean modMenu = o.getClass().getName().startsWith("com.terraformersmc.modmenu");
				if ((txt.startsWith("mods") || modMenu) && w.getWidth() >= 98) {
					mods = w;
				}
				if (w.getMessage() != null && w.getMessage().getContent() instanceof TranslatableTextContent t
						&& ("menu.disconnect".equals(t.getKey()) || "menu.returnToMenu".equals(t.getKey()))) {
					disconnect = w;
				}
			}
			String label = Lang.tr("Multiplayer", "Multijogador");
			if (mods != null) {
				int y0 = mods.getY(), row = 24;
				for (Element o : children) {
					if (o instanceof PressableWidget w && w.getX() >= cx - 110 && w.getX() + w.getWidth() <= cx + 110 && w.getY() >= y0) {
						w.setY(w.getY() + row);
					}
				}
				add.accept(ButtonWidget.builder(Text.literal(label), b -> mc.setScreen(new MultiplayerScreen(screen))).dimensions(mods.getX(), y0, mods.getWidth(), 20).build());
			} else if (disconnect != null) { // no Mods button: above Disconnect
				int y0 = disconnect.getY();
				for (Element o : children) {
					if (o instanceof PressableWidget w && w.getX() >= cx - 110 && w.getX() + w.getWidth() <= cx + 110 && w.getY() >= y0) {
						w.setY(w.getY() + 24);
					}
				}
				add.accept(ButtonWidget.builder(Text.literal(label), b -> mc.setScreen(new MultiplayerScreen(screen))).dimensions(disconnect.getX(), y0, disconnect.getWidth(), 20).build());
			} else {
				return false;
			}
			DONE.put(screen, Boolean.TRUE);
			return true;
		} catch (Throwable t) {
			System.err.println("[Caos] pause menu Multiplayer button failed: " + t);
			return false;
		}
	}
}
