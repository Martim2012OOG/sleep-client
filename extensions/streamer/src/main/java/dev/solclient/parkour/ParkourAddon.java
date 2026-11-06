package dev.solclient.parkour;

import dev.solclient.Lang;
import dev.solclient.addons.Addon;
import dev.solclient.gui.WorldTransfer;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * Add-ons > Parkour Paradise 3: the parkour map for TikTok lives. The first time it is unpacked from the extension
 * jar into saves/ ("Parkour Paradise 3 Live"); after that the same world opens again, so your level is kept.
 */
public class ParkourAddon implements Addon {

	public static final String WORLD = "Parkour Paradise 3 Live";
	private static final String RESOURCE = "/kubo_streamer/parkour_paradise_3.zip";
	private static volatile boolean busy;

	@Override
	public String id() {
		return "parkour_live";
	}

	@Override
	public String name() {
		return "Parkour Paradise 3";
	}

	@Override
	public String description() {
		return Lang.tr("100 levels. TikTok gifts move you up, down, reset or punch you (TikTok Live mod).",
				"100 níveis. Os presentes do TikTok sobem-te, descem-te, fazem reset ou dão-te um soco (mod TikTok Live).");
	}

	@Override
	public String tag() {
		return "TikTok";
	}

	@Override
	public String icon() {
		return "addon_parkour";
	}

	@Override
	public void open(Screen parent) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Path saves = mc.getLevelStorage().getSavesDirectory();
		if (Files.exists(saves.resolve(WORLD).resolve("level.dat"))) {
			start(mc, parent);
			return;
		}
		if (busy) {
			return;
		}
		busy = true;
		SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal("Parkour Paradise 3"),
				Text.literal(Lang.tr("Installing the map...", "A instalar o mapa...")));
		Thread t = new Thread(() -> {
			Path tmp = null;
			try (InputStream in = ParkourAddon.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					throw new IllegalStateException("map missing from the jar");
				}
				tmp = Files.createTempFile("kubo-parkour", ".zip");
				Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
				WorldTransfer.unpack(tmp, saves, WORLD);
				mc.execute(() -> start(mc, parent));
			} catch (Throwable e) {
				System.err.println("[Sleep] Parkour Paradise 3 install failed: " + e);
				mc.execute(() -> SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal("Parkour Paradise 3"),
						Text.literal(Lang.tr("Could not install the map", "Não deu para instalar o mapa"))));
			} finally {
				busy = false;
				if (tmp != null) {
					try {
						Files.deleteIfExists(tmp);
					} catch (Exception ignored) {
						// temp file
					}
				}
			}
		}, "kubo-parkour-install");
		t.setDaemon(true);
		t.start();
	}

	private static void start(MinecraftClient mc, Screen parent) {
		mc.createIntegratedServerLoader().start(WORLD, () -> mc.setScreen(parent));
	}
}
