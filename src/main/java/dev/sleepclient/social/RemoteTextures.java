package dev.sleepclient.social;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Downloads PNGs from URLs (capes, skins, shop pictures) and registers them as Minecraft textures.
 * {@link #get(String)} never blocks: it returns null until the image is ready.
 */
public final class RemoteTextures {

	/** A downloaded texture: {@code id} is registered in the TextureManager, {@code assetId} is the same file without "textures/" and ".png". */
	public record Tex(Identifier id, Identifier assetId, int width, int height) {
	}

	private static final Map<String, Tex> READY = new ConcurrentHashMap<>();
	private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();

	private RemoteTextures() {
	}

	public static Tex get(String url) {
		if (url == null || url.isBlank() || !url.startsWith("http")) {
			return null;
		}
		Tex t = READY.get(url);
		if (t != null) {
			return t;
		}
		long now = System.currentTimeMillis();
		Long started = PENDING.get(url);
		if (started != null && now - started < 60_000L) {
			return null; // downloading, or failed less than a minute ago
		}
		PENDING.put(url, now);
		Social.IO.execute(() -> load(url));
		return null;
	}

	/**
	 * Like {@link #get(String)} for pictures that exist on several mirrors (player skins): tries each address in order,
	 * keeps the last good copy on disk (config/kubo/faces, fresh for 12 h, used as it is when every mirror is down) and
	 * tries again after 15 s instead of a minute. {@code key} names the picture in the cache.
	 */
	public static Tex getFirst(String key, java.util.List<String> urls) {
		if (key == null || key.isBlank() || urls == null || urls.isEmpty()) {
			return null;
		}
		String k = "first:" + key;
		Tex t = READY.get(k);
		if (t != null) {
			return t;
		}
		long now = System.currentTimeMillis();
		Long started = PENDING.get(k);
		if (started != null && now - started < 15_000L) {
			return null;
		}
		PENDING.put(k, now);
		Social.IO.execute(() -> loadFirst(k, key, urls));
		return null;
	}

	/** NativeImage only reads PNG: any other picture ImageIO knows (JPG, GIF first frame, odd PNGs) is converted first. */
	static byte[] asPng(byte[] d) {
		if (isPng(d)) {
			return d;
		}
		try {
			java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(d));
			return bi == null ? d : CapeMediaLogic.toPng(bi);
		} catch (Exception e) {
			return d;
		}
	}

	private static boolean isPng(byte[] d) {
		return d != null && d.length > 24 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
	}

	private static void loadFirst(String k, String key, java.util.List<String> urls) {
		byte[] data = null;
		java.nio.file.Path file = null;
		try {
			file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("faces")
					.resolve(hash(key) + ".png");
			if (java.nio.file.Files.exists(file)) {
				byte[] disk = java.nio.file.Files.readAllBytes(file);
				long age = System.currentTimeMillis() - java.nio.file.Files.getLastModifiedTime(file).toMillis();
				if (isPng(disk) && age < 12L * 3600_000L) {
					data = disk;
				}
			}
		} catch (Exception ignored) {
		}
		if (data == null) {
			for (String u : urls) {
				byte[] d = SolarApi.download(u, 1_000_000);
				if (isPng(d)) { // a rate-limit page or an error is not a skin: try the next mirror
					data = d;
					break;
				}
			}
			try {
				if (file != null && data != null) {
					java.nio.file.Files.createDirectories(file.getParent());
					java.nio.file.Files.write(file, data);
				} else if (file != null && java.nio.file.Files.exists(file)) {
					byte[] old = java.nio.file.Files.readAllBytes(file); // every mirror is down: the old copy is better than no face
					if (isPng(old)) {
						data = old;
					}
				}
			} catch (Exception ignored) {
			}
		}
		if (data == null) {
			return; // PENDING keeps the time: tried again in 15 s
		}
		final byte[] png = data;
		MinecraftClient.getInstance().execute(() -> {
			try {
				NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
				String h = hash(k);
				Identifier asset = Identifier.of("sleep_client", "solar/" + h);
				Identifier id = Identifier.of("sleep_client", "textures/solar/" + h + ".png");
				int w = img.getWidth(), hh = img.getHeight();
				NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "kubo_face_" + h, img);
				tex.upload();
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
				READY.put(k, new Tex(id, asset, w, hh));
				PENDING.remove(k);
			} catch (Throwable e) {
				System.err.println("[Sleep] Could not load face " + key + ": " + e);
			}
		});
	}

	/**
	 * The elytra made from the cape at this address (see {@link ElytraFromCape}), or null until ready / when it
	 * can't be made (then Minecraft uses the cape as it always did).
	 */
	public static Tex elytra(String capeUrl) {
		if (capeUrl == null || capeUrl.isBlank() || !capeUrl.startsWith("http")) {
			return null;
		}
		String k = "elytra:" + capeUrl;
		Tex t = READY.get(k);
		if (t != null) {
			return t;
		}
		long now = System.currentTimeMillis();
		Long started = PENDING.get(k);
		if (started != null && now - started < 60_000L) {
			return null;
		}
		PENDING.put(k, now);
		Social.IO.execute(() -> {
			byte[] data = SolarApi.download(capeUrl);
			if (data == null) {
				return;
			}
			MinecraftClient.getInstance().execute(() -> {
				try (NativeImage cape = NativeImage.read(new ByteArrayInputStream(data))) {
					Tex made = register(k, ElytraFromCape.make(cape));
					if (made != null) {
						READY.put(k, made);
						PENDING.remove(k);
					}
				} catch (Throwable e) {
					System.err.println("[Sleep] Could not make the elytra for " + capeUrl + ": " + e);
				}
			});
		});
		return null;
	}

	/** Registers a finished image under a key (null image = nothing). The texture owns the image. */
	static Tex register(String key, NativeImage img) {
		if (img == null) {
			return null;
		}
		String h = hash(key);
		Identifier id = Identifier.of("sleep_client", "textures/solar/" + h + ".png");
		NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "kubo_elytra_" + h, img);
		tex.upload();
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
		return new Tex(id, Identifier.of("sleep_client", "solar/" + h), img.getWidth(), img.getHeight());
	}

	private static void load(String url) {
		// HD capes (2048x1024 and up) are bigger than the 4 MB default
		byte[] data = SolarApi.download(url, 24 * 1024 * 1024);
		if (data == null) {
			System.err.println("[Sleep] could not download " + url + " (error, empty or bigger than 24 MB); retry in 60 s");
			return;
		}
		final byte[] png = asPng(data);
		MinecraftClient.getInstance().execute(() -> {
			try {
				NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
				String h = hash(url);
				Identifier asset = Identifier.of("sleep_client", "solar/" + h);
				Identifier id = Identifier.of("sleep_client", "textures/solar/" + h + ".png");
				int w = img.getWidth(), hh = img.getHeight();
				NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "plegumo_remote_" + h, img);
				tex.upload();
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
				READY.put(url, new Tex(id, asset, w, hh));
				PENDING.remove(url);
			} catch (Throwable e) {
				System.err.println("[Sleep] Could not load image " + url + ": " + e);
			}
		});
	}

	/** Pictures from {@link #local} that failed (no bytes / not readable), and how many times each was tried. */
	private static final java.util.Set<String> LOCAL_FAILED = ConcurrentHashMap.newKeySet();
	private static final Map<String, Integer> LOCAL_TRIES = new ConcurrentHashMap<>();

	private static void localFailed(String k) {
		LOCAL_FAILED.add(k);
		PENDING.put(k, System.currentTimeMillis());
	}

	/**
	 * A picture from local bytes (mod icons, music covers), cached under 'key'. The bytes are made off the render thread
	 * and the texture on it; null until ready. A failure is NOT kept for good (a cover may need the player a moment, or
	 * the network was down): it is tried again after 10 s, up to 6 times.
	 */
	public static Tex local(String key, java.util.function.Supplier<byte[]> bytes) {
		String k = "local:" + key;
		Tex t = READY.get(k);
		if (t != null) {
			return t;
		}
		Long started = PENDING.get(k);
		if (started != null) {
			if (!LOCAL_FAILED.contains(k) || System.currentTimeMillis() - started < 10_000L || LOCAL_TRIES.getOrDefault(k, 0) >= 6) {
				return null;
			}
		}
		LOCAL_FAILED.remove(k);
		LOCAL_TRIES.merge(k, 1, Integer::sum);
		PENDING.put(k, System.currentTimeMillis());
		Social.IO.execute(() -> {
			byte[] data;
			try {
				data = bytes.get();
			} catch (Throwable e) {
				System.err.println("[Sleep] Could not make picture " + key + ": " + e);
				data = null;
			}
			if (data == null) {
				localFailed(k);
				return;
			}
			final byte[] png = data;
			MinecraftClient.getInstance().execute(() -> {
				try {
					NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
					String h = hash(k);
					Identifier id = Identifier.of("sleep_client", "textures/solar/" + h + ".png");
					int w = img.getWidth(), hh = img.getHeight();
					NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "plegumo_local_" + h, img);
					tex.upload();
					MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
					READY.put(k, new Tex(id, Identifier.of("sleep_client", "solar/" + h), w, hh));
					LOCAL_TRIES.remove(k);
				} catch (Throwable e) {
					System.err.println("[Sleep] Could not load icon " + key + ": " + e);
					localFailed(k);
				}
			});
		});
		return null;
	}

	private static String hash(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 10; i++) {
				sb.append(String.format("%02x", d[i]));
			}
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
