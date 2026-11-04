package dev.solclient.mods;

import dev.solclient.gui.TextureStylesScreen;
import dev.solclient.pack.TextureStyles;

/**
 * Texture Styles: pick a ready-made community texture style PER ITEM (totem, swords, pearls, crystals, hearts, fire...),
 * from a Prime-Visuals-like window. Nothing is bundled: styles are resource packs from Modrinth that the player downloads;
 * only the files of the chosen categories are copied into ONE generated pack. While the mod is ON the pack is on.
 */
public class TextureStylesMod extends Mod {

	public static TextureStylesMod instance;
	/** Last result, shown next to the button. */
	public static volatile String status = "";
	private boolean starting;

	public TextureStylesMod() {
		super("Texture Styles", "texture_styles",
				"Choose a texture style for each item (totems, swords, pearls, crystals, hearts, fire...) from community packs.", Category.VISUAL);
		instance = this;
		action("Texture Styles", "Open", () -> mc.setScreen(new TextureStylesScreen(mc.currentScreen)), () -> status);
	}

	@Override
	public void init() {
		TextureStyles.load();
		starting = true;
		super.init();
		starting = false;
	}

	@Override
	protected void onEnable() {
		if (!starting) {
			apply();
		}
	}

	@Override
	protected void onDisable() {
		if (!starting) {
			TextureStyles.activate(null);
			TextureStyles.removeGenerated("");
			status = "";
		}
	}

	/** Builds the generated pack from the choices (in a thread) and turns it on. Safe to call any time. */
	public static void apply() {
		TextureStyles.load();
		TextureStylesMod m = instance;
		if (m == null || !m.enabled) {
			return;
		}
		status = "Building...";
		Thread t = new Thread(() -> {
			try {
				TextureStyles.Built b = TextureStyles.buildPack();
				net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
				mc.execute(() -> {
					try {
						TextureStyles.activate(b == null ? null : b.packFile());
						TextureStyles.removeGenerated(b == null ? "" : b.packFile());
						int n = 0;
						if (b != null) {
							for (int v : b.files().values()) {
								n += v;
							}
						}
						status = b == null ? "" : TextureStyles.CHOICES.size() + " styles, " + n + " files";
					} catch (Throwable e) {
						status = "Error: " + e.getMessage();
					}
				});
			} catch (Throwable e) {
				status = "Error: " + e.getMessage();
			}
		}, "kubo-texture-styles");
		t.setDaemon(true);
		t.start();
	}
}
