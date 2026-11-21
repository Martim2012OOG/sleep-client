package dev.sleepclient.mods;

import dev.sleepclient.Lang;
import dev.sleepclient.gui.PackUpdaterScreen;
import dev.sleepclient.option.Option;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Pack Updater: adds an "Update to current version" button to the resource packs screen. It opens a list of your packs;
 * pick one and Sleep finds the textures that Minecraft moved or renamed since the pack was made, fixes the pack format and
 * writes a new, updated copy (the original is never touched). Written from scratch, no third-party code.
 */
public class PackUpdaterMod extends Mod {

	public static PackUpdaterMod instance;
	private static boolean listening;

	private final Option.Bool button = bool("Button On Resource Packs Screen", true);

	public PackUpdaterMod() {
		super("Pack Updater", "pack_updater", "Update an old resource pack to this version of the game: finds textures that Minecraft moved or renamed.",
				Category.UTILITY);
		instance = this;
		action("Pack Updater", "Open", () -> mc.setScreen(new PackUpdaterScreen(mc.currentScreen)), () -> "");
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (listening) {
			return;
		}
		listening = true;
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			PackUpdaterMod m = instance;
			if (m == null || !m.enabled || !m.button.on() || !(screen instanceof PackScreen)) {
				return;
			}
			try {
				int bw = Math.min(170, Math.max(100, w / 2 - 100));
				Screens.getButtons(screen).add(ButtonWidget
						.builder(Text.literal(Lang.tr("Update to current version", "Atualizar para a versão atual")),
								b -> client.setScreen(new PackUpdaterScreen(screen)))
						.dimensions(6, 6, bw, 20).build());
			} catch (Throwable t) {
				System.err.println("[Sleep] Pack Updater button failed: " + t);
			}
		});
	}
}
