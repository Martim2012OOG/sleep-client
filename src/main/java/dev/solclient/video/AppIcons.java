package dev.solclient.video;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** App icons for Open Apps (macOS: the app's .icns turned into a PNG with sips, cached in kubo/app-icons). */
public final class AppIcons {

	private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> ASKED = new ConcurrentHashMap<>();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "kubo-app-icons");
		t.setDaemon(true);
		return t;
	});

	private AppIcons() {
	}

	/** The icon of this .app (or of the app a window belongs to), null while loading / when there is none. */
	public static Identifier get(AppMirror.Source s) {
		if (!AppMirror.MAC) {
			return null;
		}
		String key = s.installed() ? s.app() : "pid:" + s.pid();
		Identifier id = READY.get(key);
		if (id != null || ASKED.putIfAbsent(key, true) != null) {
			return id;
		}
		IO.execute(() -> load(key, s));
		return null;
	}

	private static void load(String key, AppMirror.Source s) {
		try {
			String app = s.installed() ? s.app() : appOfPid(s.pid());
			if (app == null) {
				return;
			}
			Path cache = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("app-icons");
			Files.createDirectories(cache);
			Path png = cache.resolve(Integer.toHexString(app.hashCode()) + ".png");
			if (!Files.isRegularFile(png)) {
				String iconName = first(AppMirror.run(List.of("defaults", "read", app + "/Contents/Info", "CFBundleIconFile")));
				if (iconName == null || iconName.isBlank()) {
					return;
				}
				if (!iconName.endsWith(".icns")) {
					iconName += ".icns";
				}
				File icns = new File(app + "/Contents/Resources/" + iconName);
				if (!icns.isFile()) {
					return;
				}
				AppMirror.run(List.of("sips", "-s", "format", "png", "-Z", "64", icns.getAbsolutePath(), "--out", png.toString()));
				if (!Files.isRegularFile(png)) {
					return;
				}
			}
			byte[] data = Files.readAllBytes(png);
			MinecraftClient.getInstance().execute(() -> {
				try {
					NativeImage img = NativeImage.read(data);
					String h = Integer.toHexString(key.hashCode());
					NativeImageBackedTexture t = new NativeImageBackedTexture(() -> "kubo_app_icon_" + h, img);
					t.upload();
					Identifier id = Identifier.of("sol_client", "app_icon_" + h);
					MinecraftClient.getInstance().getTextureManager().registerTexture(id, t);
					READY.put(key, id);
				} catch (Exception ignored) {
				}
			});
		} catch (Exception ignored) {
		}
	}

	/** /Applications/Safari.app from a process id. */
	private static String appOfPid(long pid) {
		String exe = first(AppMirror.run(List.of("ps", "-o", "comm=", "-p", String.valueOf(pid))));
		if (exe == null) {
			return null;
		}
		int i = exe.indexOf(".app/");
		return i < 0 ? null : exe.substring(0, i + 4);
	}

	private static String first(List<String> l) {
		return l.isEmpty() ? null : l.get(0).trim();
	}
}
