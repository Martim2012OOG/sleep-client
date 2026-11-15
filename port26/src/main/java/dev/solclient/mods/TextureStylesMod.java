package dev.solclient.mods;

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
		custom("Texture Styles", dev.solclient.gui.TextureStylesPanel.INSTANCE.drawer());
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
			pending = false;
			GEN.incrementAndGet();
			TextureStyles.activate(null);
			TextureStyles.removeGenerated("");
			status = "";
		}
	}

	private static boolean checked;

	/** Once after the game started: your chosen styles must be on. If their pack is not among the enabled ones (an old pack the game refused), build it. */
	public static void ensureApplied() {
		if (checked) {
			return;
		}
		checked = true;
		TextureStyles.load();
		TextureStylesMod m = instance;
		if (m == null || !m.enabled || TextureStyles.CHOICES.isEmpty()) {
			return;
		}
		for (String id : net.minecraft.client.Minecraft.getInstance().getResourcePackRepository().getSelectedIds()) {
			if (id.startsWith("file/kubo-styles-")) {
				return;
			}
		}
		apply();
	}

	/** Only the newest click counts when several are made quickly. */
	private static final java.util.concurrent.atomic.AtomicInteger GEN = new java.util.concurrent.atomic.AtomicInteger();
	/** A pack that is built and waits for the menu to close (one reload for all your changes, not one per click). */
	private static volatile boolean pending;
	private static volatile String pendingFile;

	private static boolean menuOpen(net.minecraft.client.Minecraft mc) {
		return dev.solclient.compat.Mc.screen(mc) instanceof dev.solclient.gui.SolMenuScreen;
	}

	/** Shown under the pictures. */
	public static String statusText() {
		if ("Building...".equals(status)) {
			return dev.solclient.Lang.tr("Getting the textures...", "A buscar as texturas...");
		}
		if (pending) {
			return dev.solclient.Lang.tr("Ready. It shows when you close this menu.", "Pronto. Aparece quando fechares este menu.");
		}
		return status.startsWith("Error") ? status : "";
	}

	private static void activateNow(String file) {
		pending = false;
		pendingFile = null;
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			java.util.List<String> on = new java.util.ArrayList<>(mc.getResourcePackRepository().getSelectedIds());
			String want = file == null ? null : "file/" + file;
			boolean same = want == null ? on.stream().noneMatch(i -> i.startsWith("file/kubo-styles-")) : on.contains(want);
			if (!same) { // already the pack in use: no reload at all
				TextureStyles.activate(file);
			}
			TextureStyles.removeGenerated(file == null ? "" : file);
		} catch (Throwable e) {
			status = "Error: " + e.getMessage();
		}
	}

	/** In the title screen there is no tick: a waiting pack is turned on from the panel. */
	public static void flushIfNoWorld() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (pending && mc.player == null) {
			activateNow(pendingFile);
		}
	}

	@Override
	public void tick() {
		if (pending && !menuOpen(mc)) {
			activateNow(pendingFile);
		}
	}

	/**
	 * Builds the generated pack from the choices (in a thread: only the chosen items' files are fetched) and turns it on. While the
	 * Sleep menu is open in a world the game is not reloaded on every click: the pack waits and is turned on once, when the menu closes.
	 */
	public static void apply() {
		TextureStyles.load();
		TextureStylesMod m = instance;
		if (m == null || !m.enabled) {
			return;
		}
		final int gen = GEN.incrementAndGet();
		status = "Building...";
		Thread t = new Thread(() -> {
			try {
				TextureStyles.Built b = TextureStyles.buildPack();
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				mc.execute(() -> {
					if (gen != GEN.get()) {
						return; // a newer click is on its way
					}
					status = b == null ? "" : TextureStyles.CHOICES.size() + " styles";
					// on at once, like Lunar: it used to wait for the menu to close
					activateNow(b == null ? null : b.packFile());
				});
			} catch (Throwable e) {
				if (gen == GEN.get()) {
					status = "Error: " + e.getMessage();
				}
			}
		}, "kubo-texture-styles");
		t.setDaemon(true);
		t.start();
	}
}
