package dev.solclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.social.KuboPackLogic.Pack;
import dev.solclient.social.KuboPackLogic.State;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

/**
 * Resource packs of the Caos site: list (GET /resource-packs), install (download + SHA-1 + activate) and the
 * pictures (icons / banners). The decisions (file names, SHA-1, install state, links) live in {@link KuboPackLogic},
 * which is unit tested. Everything that blocks runs on {@link Social#IO}, never on the render thread.
 */
public final class KuboPacks {

	private static final String UA = "Caos-Client/1.0 (Minecraft client mod)";
	private static final long MAX_PACK_BYTES = 1_500_000_000L;
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(12)).build();

	private KuboPacks() {
	}

	// ------------------------------------------------------------------ list
	/** The packs of the site (public: no session). {@code q} may be empty. Blocking. */
	public static List<Pack> list(String q) throws IOException {
		String path = "/resource-packs" + (q == null || q.isBlank() ? "" : "?q=" + SolarApi.enc(q.trim()));
		SolarApi.Res r = SolarApi.send("GET", path, null, false, 15);
		if (!r.ok()) {
			throw new IOException(r.error() == null ? "HTTP " + r.status() : r.error());
		}
		List<Pack> out = new ArrayList<>();
		JsonObject root = r.obj();
		if (root == null) {
			return out;
		}
		for (JsonElement e : Json.arr(root, "packs")) {
			JsonObject o = Json.obj(e);
			if (o == null) {
				continue;
			}
			JsonObject soc = Json.obj(o, "socials");
			Pack p = new Pack(s(o, "id"), s(o, "name"), s(o, "description"), s(o, "version"), s(o, "mcVersion"), Json.strings(o, "tags"),
					s(o, "owner"), s(o, "credits"), soc == null ? "" : s(soc, "discord"), soc == null ? "" : s(soc, "youtube"),
					soc == null ? "" : s(soc, "twitter"), soc == null ? "" : s(soc, "website"), s(o, "icon"), s(o, "banner"),
					s(o, "download"), s(o, "sha1"), Json.num(o, "size", 0L), Json.num(o, "downloads", 0L));
			if (KuboPackLogic.usable(p)) { // no id / no https download / no SHA-1: it could not be installed safely
				out.add(p);
			}
		}
		return out;
	}

	private static String s(JsonObject o, String k) {
		String v = Json.str(o, k);
		return v == null ? "" : v;
	}

	// ------------------------------------------------------------------ install state
	public static Path dir() {
		return MinecraftClient.getInstance().runDirectory.toPath().resolve("resourcepacks");
	}

	private static Path recordFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-resource-packs.json");
	}

	/** id -> file name we installed last time (so a newer version shows "Update"). */
	private static synchronized Map<String, String> readRecord() {
		Map<String, String> m = new LinkedHashMap<>();
		try {
			Path f = recordFile();
			if (Files.exists(f)) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String k : o.keySet()) {
					m.put(k, o.get(k).getAsString());
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// unreadable record: everything just shows Install
		}
		return m;
	}

	private static synchronized void writeRecord(String id, String fileName) {
		Map<String, String> m = readRecord();
		m.put(id, fileName);
		JsonObject o = new JsonObject();
		m.forEach(o::addProperty);
		try {
			Files.writeString(recordFile(), o.toString(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// not important
		}
	}

	private static final Map<String, State> STATES = new ConcurrentHashMap<>();

	/** Cached state (never touches the disk). NOT_INSTALLED until {@link #refreshStates} has answered. */
	public static State state(Pack p) {
		return STATES.getOrDefault(p.id(), State.NOT_INSTALLED);
	}

	/** Works out the state of these packs (reads the files to check their SHA-1). Blocking: call off the render thread. */
	public static void loadStates(List<Pack> packs) {
		Map<String, String> rec = readRecord();
		for (Pack p : packs) {
			STATES.put(p.id(), KuboPackLogic.state(p, dir(), rec.get(p.id())));
		}
	}

	/** True when the pack's file is in the game's list of enabled packs. Cheap; render thread is fine. */
	public static boolean active(Pack p) {
		try {
			return MinecraftClient.getInstance().getResourcePackManager().getEnabledIds()
					.contains(KuboPackLogic.managerId(KuboPackLogic.fileName(p)));
		} catch (RuntimeException e) {
			return false;
		}
	}

	// ------------------------------------------------------------------ install
	public static final class Inst {
		public volatile float progress;
		/** 0 = downloading, 1 = installed, 2 = failed. */
		public volatile int state;
		public volatile String msg = "";
	}

	private static final Map<String, Inst> INSTALLS = new ConcurrentHashMap<>();

	public static Inst installing(Pack p) {
		return INSTALLS.get(p.id());
	}

	/**
	 * Downloads the pack asynchronously (progress in {@link Inst}), checks its SHA-1 (a wrong one deletes the file and
	 * reports an error), counts the download (errors ignored), then turns the pack on and reloads the resources.
	 */
	public static void install(Pack p) {
		Inst cur = INSTALLS.get(p.id());
		if (cur != null && cur.state == 0) {
			return;
		}
		final Inst in = new Inst();
		INSTALLS.put(p.id(), in);
		final String name = KuboPackLogic.fileName(p);
		Social.IO.execute(() -> {
			Path tmp = null;
			try {
				if (!KuboPackLogic.usable(p)) {
					throw new IOException("bad pack data");
				}
				Path dir = dir();
				Files.createDirectories(dir);
				Path target = dir.resolve(name);
				tmp = dir.resolve(name + ".part");
				HttpRequest rq = HttpRequest.newBuilder(URI.create(p.download())).timeout(Duration.ofMinutes(15)).header("User-Agent", UA).GET().build();
				HttpResponse<InputStream> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofInputStream());
				if (r.statusCode() < 200 || r.statusCode() >= 300) {
					throw new IOException("HTTP " + r.statusCode());
				}
				long len = r.headers().firstValueAsLong("Content-Length").orElse(p.size());
				if (len > MAX_PACK_BYTES) {
					throw new IOException("too big");
				}
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				try (InputStream is = r.body(); OutputStream os = Files.newOutputStream(tmp)) {
					byte[] buf = new byte[65536];
					long read = 0;
					int n;
					while ((n = is.read(buf)) > 0) {
						os.write(buf, 0, n);
						md.update(buf, 0, n);
						read += n;
						if (read > MAX_PACK_BYTES) {
							throw new IOException("too big");
						}
						if (len > 0) {
							in.progress = Math.min(0.99F, read / (float) len);
						}
					}
				}
				if (!KuboPackLogic.normSha1(p.sha1()).equals(KuboPackLogic.hex(md.digest()))) {
					throw new IOException("SHA-1 does not match (file deleted)");
				}
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				tmp = null;
				String previous = readRecord().get(p.id());
				writeRecord(p.id(), name);
				STATES.put(p.id(), State.INSTALLED);
				in.progress = 1F;
				in.state = 1;
				countDownload(p);
				activate(p, previous);
			} catch (Throwable t) {
				in.state = 2;
				in.msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				try {
					if (tmp != null) {
						Files.deleteIfExists(tmp); // a bad / half file never stays
					}
				} catch (IOException ignored) {
					// leftover .part, harmless
				}
			}
		});
	}

	/** Counter only: any error (offline, 404, 429...) is ignored. */
	private static void countDownload(Pack p) {
		try {
			SolarApi.send("POST", "/resource-packs/" + SolarApi.enc(p.id()) + "/download", new JsonObject(), false, 8);
		} catch (RuntimeException ignored) {
			// only a counter
		}
	}

	/** Turns the pack on (on top of the others) and reloads the resources. Safe to call from any thread. */
	public static void activate(Pack p) {
		activate(p, null);
	}

	private static void activate(Pack p, String previousFile) {
		final String id = KuboPackLogic.managerId(KuboPackLogic.fileName(p));
		final String old = previousFile == null ? null : KuboPackLogic.managerId(previousFile);
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			try {
				ResourcePackManager pm = mc.getResourcePackManager();
				pm.scanPacks(); // the new zip shows up without restarting
				if (!pm.getIds().contains(id)) {
					Social.say("Pack file not found by the game", true);
					return;
				}
				List<String> on = new ArrayList<>(pm.getEnabledIds());
				on.remove(id);
				if (old != null) {
					on.remove(old); // the older version of this same pack is replaced
				}
				on.add(id); // last = highest priority
				pm.setEnabledProfiles(on);
				mc.options.refreshResourcePacks(pm); // saves options and reloads the resources
			} catch (Throwable t) {
				Social.say("Could not turn the pack on: " + t.getMessage(), true);
			}
		});
	}

	// ------------------------------------------------------------------ pictures (icons / banners)
	/** A picture ready to draw. */
	public record Img(Identifier id, int width, int height) {
	}

	private static final int MAX_IMAGES = 80;
	private static final Map<String, Img> READY = new LinkedHashMap<>(); // render thread only; oldest first
	private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();
	private static int serial;

	/**
	 * The picture at {@code url} (null until it is ready, and when it can't be shown). Downloaded and decoded off the
	 * render thread, cached by address and scaled down to {@code maxW} pixels wide. Render thread only.
	 */
	/** Own threads for icons: 30 icons at once must not queue behind the 3 shared social threads. */
	private static final java.util.concurrent.ExecutorService ICONS = java.util.concurrent.Executors.newFixedThreadPool(6, r -> {
		Thread t = new Thread(r, "kubo-icons");
		t.setDaemon(true);
		return t;
	});

	public static Img image(String url, int maxW) {
		if (!KuboPackLogic.https(url)) {
			return null;
		}
		String key = url + "|" + maxW;
		Img t = READY.get(key);
		if (t != null) {
			return t;
		}
		long now = System.currentTimeMillis();
		Long started = PENDING.get(key);
		if (started != null && now - started < 60_000L) {
			return null;
		}
		PENDING.put(key, now);
		ICONS.execute(() -> loadImage(key, url, maxW));
		return null;
	}

	private static void loadImage(String key, String url, int maxW) {
		try {
			byte[] data = SolarApi.download(url, 6 * 1024 * 1024);
			if (data == null) {
				return;
			}
			BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data)); // PNG / JPG / GIF (first frame); Java can't read WebP
			if (bi == null && isWebp(data)) {
				// Most Modrinth icons are WebP. The JDK and the game can't decode it, so ask an image proxy for a PNG of it
				// (only the public icon address is sent). If that fails the pack just keeps its letter placeholder.
				String bare = url.replaceFirst("^https?://", "");
				byte[] png = SolarApi.download("https://wsrv.nl/?url=" + java.net.URLEncoder.encode(bare, java.nio.charset.StandardCharsets.UTF_8)
						+ "&output=png&w=" + Math.max(16, maxW), 6 * 1024 * 1024);
				if (png != null) {
					data = png;
					bi = ImageIO.read(new ByteArrayInputStream(data));
				}
			}
			NativeImage ni;
			if (bi == null) {
				ni = NativeImage.read(new ByteArrayInputStream(data)); // let the game try (it only reads PNG)
			} else {
				if (bi.getWidth() > maxW) {
					int nh = Math.max(1, Math.round(bi.getHeight() * (maxW / (float) bi.getWidth())));
					BufferedImage sc = new BufferedImage(maxW, nh, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = sc.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g.drawImage(bi, 0, 0, maxW, nh, null);
					g.dispose();
					bi = sc;
				}
				ByteArrayOutputStream png = new ByteArrayOutputStream();
				ImageIO.write(bi, "png", png);
				ni = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
			}
			final NativeImage img = ni;
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.execute(() -> { // only the GPU upload happens on the render thread
				try {
					Identifier id = Identifier.of("sol_client", "textures/kubopacks/" + (serial++) + ".png");
					NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "kubo_pack_img", img);
					tex.upload();
					mc.getTextureManager().registerTexture(id, tex);
					READY.put(key, new Img(id, img.getWidth(), img.getHeight()));
					PENDING.remove(key);
					while (READY.size() > MAX_IMAGES) { // free the oldest ones
						Iterator<Map.Entry<String, Img>> it = READY.entrySet().iterator();
						Map.Entry<String, Img> first = it.next();
						it.remove();
						mc.getTextureManager().destroyTexture(first.getValue().id());
					}
				} catch (Throwable t) {
					img.close();
				}
			});
		} catch (Throwable t) {
			// stays pending for a minute, then it is tried again
		}
	}

	/** "RIFF....WEBP" */
	private static boolean isWebp(byte[] d) {
		return d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
	}

	/** Frees every picture texture (the screen was closed). Render thread only. */
	public static void releaseImages() {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Img i : READY.values()) {
			mc.getTextureManager().destroyTexture(i.id());
		}
		READY.clear();
		PENDING.clear();
	}
}
