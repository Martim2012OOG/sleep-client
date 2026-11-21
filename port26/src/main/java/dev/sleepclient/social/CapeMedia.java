package dev.sleepclient.social;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sleepclient.social.CapeMediaLogic.Desc;
import dev.sleepclient.social.CapeMediaLogic.Kind;
import dev.sleepclient.social.CapeMediaLogic.Side;
import dev.sleepclient.video.CapeVideo;
import dev.sleepclient.video.Mcef;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Shop capes that are not a plain PNG atlas (see {@link CapeMediaLogic}):
 * - panels / JPG pictures: downloaded off the render thread and composed ONCE into a cape atlas texture (static);
 * - videos, GIFs, WebP: one shared Chromium page per cape ({@link CapeVideo}), at most {@link #MAX_VIDEOS} at a time.
 * Everything is cached by the cape's key, so all players wearing the same cape share one texture / decoder, and
 * anything not asked for any more (nobody visible wears it, or it was unequipped) is released.
 */
public final class CapeMedia {

	/** Most Chromium pages playing at the same time (more capes simply wait until one is released). */
	public static final int MAX_VIDEOS = 3;
	private static final int STATIC_K = 12; // static atlas: 768 x 384
	private static final int VIDEO_K = 8; // video atlas: 512 x 256
	private static final int STATIC_MAX_W = 2048; // HD picture atlases keep their real size up to 2048 x 1024
	/** Most ffmpeg decoders running at once (one per cape URL, shared by every player wearing it). */
	public static final int MAX_DECODERS = 4;
	private static final long GIF_IDLE_MS = 15_000L;
	private static final long VIDEO_IDLE_MS = 4_000L;
	private static final long STATIC_IDLE_MS = 60_000L;
	private static final long RETRY_MS = 60_000L;

	private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

	private CapeMedia() {
	}

	/** 0 = not looked yet, 1 = looking / installing, 2 = ffmpeg ready, 3 = not available. */
	private static volatile int ffState;

	/** Finds ffmpeg (or downloads it once, ~25-80 MB) on its own thread; the cape video starts by itself afterwards. */
	private static void ensureFfmpeg() {
		if (ffState != 0) {
			return;
		}
		ffState = 1;
		Thread t = new Thread(() -> {
			try {
				String p = dev.sleepclient.fightcam.Ffmpeg.findPath();
				if (p == null) {
					System.err.println("[Sleep] cape video: ffmpeg not found, downloading it");
					if (dev.sleepclient.fightcam.Ffmpeg.install()) {
						p = dev.sleepclient.fightcam.Ffmpeg.findPath();
					}
				}
				ffState = p != null ? 2 : 3;
				if (p == null) {
					System.err.println("[Sleep] cape video: no ffmpeg (" + dev.sleepclient.fightcam.Ffmpeg.status + "), using the browser fallback");
				}
			} catch (Throwable ex) {
				ffState = 3;
				System.err.println("[Sleep] cape video: ffmpeg lookup failed: " + ex);
			}
		}, "kubo-cape-ffmpeg");
		t.setDaemon(true);
		t.start();
	}

	private static int activeDecoders() {
		int n = 0;
		for (Entry x : ENTRIES.values()) {
			if (x.started && !x.failed && CapeMediaLogic.atlasVideo(x.desc) && !x.browser) {
				n++;
			}
		}
		return n;
	}

	private static final class Entry {
		final String key;
		final Desc desc;
		volatile boolean browser, released;
		final Identifier id;
		volatile long last = System.currentTimeMillis();
		volatile long failedAt;
		volatile boolean failed, started;
		volatile RemoteTextures.Tex tex, elytra;
		long elytraAt, elytraStamp = -1;
		CapeVideo vid;
		// GIF capes (decoded in Java): one picture per frame, swapped into the texture as time goes by
		volatile NativeImage[] frames;
		volatile int[] delays;
		DynamicTexture animTex;
		int frame;
		long nextFrameAt;
		// video capes through ffmpeg
		dev.sleepclient.video.FfVideo ff;
		volatile java.nio.file.Path videoFile;

		Entry(String key, Desc desc) {
			this.key = key;
			this.desc = desc;
			// atlas videos are played by ffmpeg (no browser); the browser is only the fallback when ffmpeg can't be found / installed
			this.browser = CapeMediaLogic.needsBrowser(desc) && !CapeMediaLogic.atlasVideo(desc);
			this.id = Identifier.fromNamespaceAndPath("sleep_client", "textures/solar/cape_" + hash(key) + ".png");
		}

		Identifier asset() {
			return Identifier.fromNamespaceAndPath("sleep_client", "solar/cape_" + hash(key));
		}

		void fail(String why) {
			failed = true;
			failedAt = System.currentTimeMillis();
			System.err.println("[Sleep] cape media failed: " + why);
		}
	}

	// ---------------------------------------------------------------- parsing (lookup)
	/** Reads the "capeMedia" object of a lookup player; null when absent or unusable. */
	public static Desc parse(JsonObject o) {
		if (o == null) {
			return null;
		}
		Side back = readSide(Json.obj(o, "back"));
		Side front = readSide(Json.obj(o, "front"));
		return CapeMediaLogic.make(Json.str(o, "layout"), back, front);
	}

	private static Side readSide(JsonObject s) {
		return s == null ? null : CapeMediaLogic.side(Json.str(s, "url"), Json.str(s, "type"));
	}

	// ---------------------------------------------------------------- use (render thread)
	/** Cape shown instead of yours while the Locker preview extracts your render state (try-on). */
	private static volatile Desc previewDesc;
	private static volatile boolean previewing;

	/** Starts / ends the try-on of a cape in the menu preview (call around the render-state extraction of the preview). */
	public static void beginPreview(Desc d) {
		previewDesc = d;
		previewing = d != null;
	}

	public static void endPreview() {
		previewing = false;
		previewDesc = null;
	}

	/** The cape a shop item shows: its own capeMedia if the site sent one, else its picture (see {@link CapeMediaLogic#auto}). */
	public static Desc forItem(Social.Item it) {
		if (it == null) {
			return null;
		}
		if (it.capeMedia != null && CapeMediaLogic.custom(it.capeMedia)) {
			return it.capeMedia;
		}
		// a plain picture (or a PNG address): a real atlas is used as it is, a flat picture is put on the cape
		return CapeMediaLogic.auto(it.capeMedia != null ? it.capeMedia.back().url() : it.imageUrl);
	}

	private static Desc desc(UUID player) {
		if (previewing && player.equals(Minecraft.getInstance().getUser().getProfileId())) {
			return previewDesc;
		}
		return Social.capeDesc(player);
	}

	/** True when this player's cape must go through this class (panels, JPG atlas, GIF / video). */
	public static boolean custom(UUID player) {
		return CapeMediaLogic.custom(desc(player));
	}

	/**
	 * The cape texture of this player, or null while it is not ready (downloading, video engine starting, failed).
	 * Calling it counts as "this cape is visible": that keeps its texture / decoder alive.
	 */
	public static RemoteTextures.Tex tex(UUID player) {
		Desc d = desc(player);
		if (d == null || !CapeMediaLogic.custom(d)) {
			return null;
		}
		Entry e = ENTRIES.computeIfAbsent(d.key(), k -> new Entry(k, d));
		e.last = System.currentTimeMillis();
		if (e.failed) {
			return null;
		}
		if (CapeMediaLogic.atlasVideo(d) && !e.browser && !e.started) {
			var st = dev.sleepclient.SleepClient.settings;
			if (st != null && !st.animatedCapes.on()) {
				return null; // animated capes are switched off
			}
			if (dev.sleepclient.fightcam.Ffmpeg.path == null) {
				ensureFfmpeg();
				if (ffState != 3) {
					return null; // looking for / installing ffmpeg
				}
				e.browser = true; // no ffmpeg anywhere: browser fallback
			} else if (activeDecoders() >= MAX_DECODERS) {
				return null; // all decoders busy: waits until one is released
			}
		}
		if (e.browser) {
			if (e.vid != null && e.vid.refresh() && e.tex == null) {
				e.tex = new RemoteTextures.Tex(e.id, e.asset(), CapeMediaLogic.pageW(VIDEO_K), CapeMediaLogic.pageH(VIDEO_K));
			}
			return e.tex;
		}
		if (!e.started) {
			e.started = true;
			if (CapeMediaLogic.atlasVideo(e.desc)) {
				Social.IO.execute(() -> loadVideo(e));
			} else {
				Social.IO.execute(() -> loadStatic(e));
			}
		}
		animate(e);
		return e.tex;
	}

	/** The elytra made from a static picture cape (null for videos / GIF pages and while loading). */
	public static RemoteTextures.Tex elytra(UUID player) {
		Desc d = desc(player);
		if (d == null || !CapeMediaLogic.custom(d)) {
			return null;
		}
		Entry e = ENTRIES.get(d.key());
		return e == null || e.browser ? null : e.elytra; // GIF / video: an elytra that is redrawn from the moving cape (see animateElytra)
	}

	/** Every client tick: starts waiting video pages, releases what nobody uses. */
	public static void tick() {
		if (ENTRIES.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		int videos = 0;
		for (Entry e : ENTRIES.values()) {
			if (e.vid != null) {
				videos++;
			}
		}
		for (Entry e : ENTRIES.values()) {
			long idle = now - e.last;
			if (e.failed) {
				if (now - e.failedAt > RETRY_MS || idle > VIDEO_IDLE_MS && !e.browser) {
					release(e);
				}
				continue;
			}
			if (e.browser) {
				if (idle > VIDEO_IDLE_MS) {
					release(e);
				} else if (e.vid == null && videos < MAX_VIDEOS && startVideo(e)) {
					videos++;
				}
			} else {
				boolean video = CapeMediaLogic.atlasVideo(e.desc);
				boolean moving = e.frames != null || CapeMediaLogic.gif(e.desc);
				long lim = video ? VIDEO_IDLE_MS : moving ? GIF_IDLE_MS : STATIC_IDLE_MS;
				if (idle > lim) {
					release(e); // nobody sees this cape any more: decoder + texture are freed
				}
			}
		}
	}

	private static boolean startVideo(Entry e) {
		var s = dev.sleepclient.SleepClient.settings;
		if (s != null && !s.animatedCapes.on()) {
			return false; // switched off: animated capes simply do not show
		}
		if (!Mcef.installed()) {
			e.fail("video engine missing");
			return false;
		}
		Mcef.start(); // first time: MCEF downloads its browser files, then the video starts by itself
		if (!Mcef.ready()) {
			return false;
		}
		String html = CapeMediaLogic.html(e.desc, VIDEO_K);
		CapeVideo v = CapeVideo.open(html, CapeMediaLogic.pageW(VIDEO_K), CapeMediaLogic.pageH(VIDEO_K), e.id);
		if (v == null) {
			e.fail("could not open the video page");
			return false;
		}
		e.vid = v;
		return true;
	}

	/** Next GIF frame when it's time (render thread), or the newest ffmpeg video frame. */
	private static void animate(Entry e) {
		if (e.ff != null) {
			if (e.ff.upload() && e.tex == null) {
				e.tex = new RemoteTextures.Tex(e.id, e.asset(), e.ff.width(), e.ff.height());
			}
			if (e.ff.failed()) {
				e.fail("ffmpeg could not play the cape video");
			}
			animateElytra(e, e.ff.image(), e.ff.shownFrames());
			return;
		}
		NativeImage[] fr = e.frames;
		if (fr == null || fr.length < 2 || e.animTex == null) {
			return;
		}
		var s = dev.sleepclient.SleepClient.settings;
		if (s != null && !s.animatedCapes.on()) {
			return; // switched off: stays on the frame it is on
		}
		long now = System.currentTimeMillis();
		if (now < e.nextFrameAt) {
			return;
		}
		e.frame = (e.frame + 1) % fr.length;
		e.nextFrameAt = now + Math.max(20, e.delays[e.frame]);
		NativeImage img = e.animTex.getPixels();
		if (img != null) {
			img.copyFrom(fr[e.frame]);
			e.animTex.upload();
			animateElytra(e, img, e.frame);
		}
	}

	/**
	 * Keeps the elytra of a moving cape (GIF / video) in step with it: every few frames the wings are redrawn from the cape's
	 * current picture, with the same wing shape and shading as the still capes. Limited to ~12 redraws a second.
	 */
	private static void animateElytra(Entry e, NativeImage cape, long stamp) {
		if (cape == null || stamp == e.elytraStamp) {
			return;
		}
		long now = System.currentTimeMillis();
		if (e.elytra != null && now - e.elytraAt < 80) {
			return;
		}
		try {
			NativeImage made = ElytraFromCape.make(cape);
			if (made == null) {
				e.elytraStamp = stamp; // odd cape size: Minecraft keeps using the cape
				return;
			}
			e.elytraAt = now;
			e.elytraStamp = stamp;
			if (e.elytra == null) {
				e.elytra = RemoteTextures.register("elytra:" + e.key, made);
				return;
			}
			net.minecraft.client.renderer.texture.AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(e.elytra.id());
			if (t instanceof DynamicTexture nt && nt.getPixels() != null && nt.getPixels().getWidth() == made.getWidth()
					&& nt.getPixels().getHeight() == made.getHeight()) {
				nt.getPixels().copyFrom(made);
				nt.upload();
			}
			made.close();
		} catch (Throwable ex) {
			e.elytraStamp = stamp;
			System.err.println("[Sleep] animated elytra: " + ex);
		}
	}

	private static void loadVideo(Entry e) {
		try {
			java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("capes");
			java.nio.file.Files.createDirectories(dir);
			String url = e.desc.back().url();
			String ext = url.toLowerCase(java.util.Locale.ROOT).contains(".webm") ? ".webm" : ".mp4";
			java.nio.file.Path f = dir.resolve(hash(url) + ext);
			if (!java.nio.file.Files.isRegularFile(f) || java.nio.file.Files.size(f) < 1000) {
				byte[] data = SolarApi.download(url, 60 * 1024 * 1024);
				if (data == null) {
					e.fail("could not download the cape video");
					return;
				}
				java.nio.file.Path part = dir.resolve(hash(url) + ext + ".part");
				java.nio.file.Files.write(part, data);
				java.nio.file.Files.move(part, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			e.videoFile = f;
			Minecraft.getInstance().execute(() -> {
				if (e.released) {
					return;
				}
				dev.sleepclient.video.FfVideo v = new dev.sleepclient.video.FfVideo(f, 64 * VIDEO_K, 32 * VIDEO_K, true, e.id);
				if (v.start(dev.sleepclient.fightcam.Ffmpeg.path)) {
					e.ff = v;
				} else {
					e.fail("ffmpeg did not start (" + dev.sleepclient.fightcam.Ffmpeg.path + ")");
				}
			});
		} catch (Throwable ex) {
			e.fail(String.valueOf(ex));
		}
	}

	private static void release(Entry e) {
		e.released = true;
		ENTRIES.remove(e.key, e);
		if (e.ff != null) {
			e.ff.close();
			e.ff = null;
		}
		NativeImage[] fr = e.frames;
		e.frames = null;
		if (fr != null) {
			for (NativeImage n : fr) {
				if (n != null) {
					n.close();
				}
			}
		}
		if (e.vid != null) {
			e.vid.close();
			e.vid = null;
		}
		RemoteTextures.Tex t = e.tex;
		e.tex = null;
		if (t != null && !e.browser) {
			Minecraft.getInstance().getTextureManager().release(t.id());
		}
		e.animTex = null;
		RemoteTextures.Tex el = e.elytra;
		e.elytra = null;
		if (el != null) {
			Minecraft.getInstance().getTextureManager().release(el.id());
		}
	}

	/** Forgets everything (leaving the game / switching accounts). */
	public static void clear() {
		for (Entry e : ENTRIES.values()) {
			release(e);
		}
	}

	// ---------------------------------------------------------------- static pictures
	private static void loadStatic(Entry e) {
		if (CapeMediaLogic.gif(e.desc)) {
			loadGif(e);
			return;
		}
		try {
			BufferedImage back = fetch(e.desc.back());
			BufferedImage front = e.desc.sameMedia() ? back : fetch(e.desc.front());
			if (back == null || front == null) {
				e.fail("could not download / read the picture");
				return;
			}
			// a shop picture that is already a real cape atlas is used as it is; a flat picture is cropped onto the cape
			// a 2:1 picture is a real cape atlas (64x32, 2048x1024...): kept at its real size (max 2048 wide), never squeezed to 64x32
			byte[] png = !e.desc.panels() && CapeMediaLogic.looksLikeAtlas(back)
					? CapeMediaLogic.toPng(CapeMediaLogic.atlasFrame(e.desc, back, front, STATIC_MAX_W))
					: CapeMediaLogic.compose(e.desc, back, front, STATIC_K);
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				if (e.released) {
					return;
				}
				try {
					NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
					int w = img.getWidth(), h = img.getHeight();
					DynamicTexture t = new DynamicTexture(() -> "kubo_cape_" + e.key.hashCode(), img);
					t.upload();
					mc.getTextureManager().register(e.id, t);
					e.tex = new RemoteTextures.Tex(e.id, e.asset(), w, h);
					try {
						e.elytra = RemoteTextures.register("elytra:" + e.key, ElytraFromCape.make(img));
					} catch (Throwable ignored) {
						// no elytra of our own: Minecraft uses the cape, like before
					}
				} catch (Throwable ex) {
					e.fail(String.valueOf(ex));
				}
			});
		} catch (Throwable ex) {
			e.fail(String.valueOf(ex));
		}
	}

	/** GIF cape: decoded in Java (no browser), every frame turned into the cape atlas, then played in animate(). */
	private static void loadGif(Entry e) {
		try {
			byte[] data = SolarApi.download(e.desc.back().url(), 30 * 1024 * 1024);
			if (data == null || !GifFrames.isGif(data)) {
				e.fail("not a GIF");
				return;
			}
			GifFrames.Anim an = GifFrames.decode(data, 150);
			if (an.frames().isEmpty()) {
				e.fail("empty GIF");
				return;
			}
			// big GIFs with many frames get a smaller texture (memory): 2048 x 1024 x 150 frames would be 1.2 GB
			int maxW = an.frames().size() > 60 ? 384 : 512;
			java.util.List<int[]> px = new java.util.ArrayList<>();
			int w = 0, h = 0;
			for (BufferedImage f : an.frames()) {
				BufferedImage a = CapeMediaLogic.atlasFrame(e.desc, f, f, maxW);
				w = a.getWidth();
				h = a.getHeight();
				px.add(a.getRGB(0, 0, w, h, null, 0, w));
			}
			final int fw = w, fh = h;
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				if (e.released) {
					return;
				}
				try {
					NativeImage[] imgs = new NativeImage[px.size()];
					for (int i = 0; i < imgs.length; i++) {
						NativeImage n = new NativeImage(fw, fh, true);
						int[] p = px.get(i);
						for (int y = 0; y < fh; y++) {
							for (int x = 0; x < fw; x++) {
								n.setPixel(x, y, p[y * fw + x]);
							}
						}
						imgs[i] = n;
					}
					NativeImage first = new NativeImage(fw, fh, true);
					first.copyFrom(imgs[0]);
					DynamicTexture t = new DynamicTexture(() -> "kubo_cape_gif_" + e.key.hashCode(), first);
					t.upload();
					mc.getTextureManager().register(e.id, t);
					e.animTex = t;
					e.delays = an.delaysMs();
					e.frames = imgs;
					e.nextFrameAt = System.currentTimeMillis() + e.delays[0];
					e.tex = new RemoteTextures.Tex(e.id, e.asset(), fw, fh);
					e.elytra = null; // made from the first frame on the next animate() and redrawn as the GIF moves
				} catch (Throwable ex) {
					e.fail(String.valueOf(ex));
				}
			});
		} catch (Throwable ex) {
			e.fail(String.valueOf(ex));
		}
	}

	private static BufferedImage fetch(Side s) throws java.io.IOException {
		byte[] data = SolarApi.download(s.url(), 20 * 1024 * 1024);
		if (data == null) {
			return null;
		}
		return ImageIO.read(new ByteArrayInputStream(data));
	}

	private static String hash(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 10; i++) {
				sb.append(String.format("%02x", d[i]));
			}
			return sb.toString();
		} catch (Exception ex) {
			return Integer.toHexString(s.hashCode());
		}
	}

	/** Used by the unit tests / diagnostics: how a descriptor would be shown. */
	public static String describe(Desc d) {
		if (d == null) {
			return "none";
		}
		if (CapeMediaLogic.legacyAtlas(d)) {
			return "png atlas (normal cape renderer)";
		}
		return (CapeMediaLogic.needsBrowser(d) ? "animated (shared browser page)" : "static picture (composed atlas)")
				+ (d.panels() ? ", panels" : d.auto() ? ", auto" : ", atlas") + ", " + kindName(d.back()) + "/" + kindName(d.front());
	}

	private static String kindName(Side s) {
		Kind k = CapeMediaLogic.kind(s);
		return k.name().toLowerCase(java.util.Locale.ROOT);
	}
}
