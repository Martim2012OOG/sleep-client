package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.fightcam.Ffmpeg;
import dev.sleepclient.option.Option;
import dev.sleepclient.video.FfVideo;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;

/**
 * Cinematic Intro (like the "Reimagined Intro" mod, built into Sleep with no extra mods): a black loading screen,
 * then an intro video when the game opens, and a looping video + music behind the home screen.
 * The videos are files in config/kubo-intro (intro.mp4, main.mp4, audio.wav): use "Import" to take them from the
 * Reimagined Intro jar, or put your own there. Videos play with ffmpeg (the same one Fight Cam uses).
 */
public class CinematicIntroMod extends Mod {

	public static CinematicIntroMod instance;

	private final Option.Bool playIntro = bool("Intro Video At Start", true);
	private final Option.Bool homeVideo = bool("Video Behind The Home Screen", true);
	private final Option.Bool music = bool("Music", true);
	private final Option.Num volume = num("Volume", 80, 0, 100, 5, "%");
	private final Option.Bool blackLoading = bool("Black Loading Screen While Intro Plays", false).tip("Paints the game loading screen black before the intro. Off by default: the normal loading screen (with the Sleep logo) shows instead.");
	private final Option.Bool canSkip = bool("Esc / Click Skips The Intro", true);
	private String importMsg = "";

	private static boolean introDone;
	private static FfVideo home;
	private static Clip clip;
	private static volatile String ffmpeg;
	private static volatile boolean finding;

	public CinematicIntroMod() {
		super("Cinematic Intro", "cinematic_intro", "Intro video when the game opens and a video behind the home screen, like Reimagined Intro.",
				Category.VISUAL);
		instance = this;
		inSettings = true; // lives in Sleep Settings now, not in the mods list
		action("Videos (intro.mp4, main.mp4, audio.wav)", "Open Folder", () -> {
			try {
				Files.createDirectories(dir());
			} catch (Exception ignored) {
			}
			net.minecraft.util.Util.getPlatform().openFile(dir().toFile());
		}, () -> has("intro.mp4") || has("main.mp4") ? (has("intro.mp4") ? "intro " : "") + (has("main.mp4") ? "main " : "")
				+ (has("audio.wav") ? "audio" : "") : "No videos yet");
		action("Take the videos from Reimagined Intro", "Import", () -> dev.sleepclient.social.Social.IO.execute(this::importJar), () -> importMsg);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-intro");
	}

	private static final java.util.Map<String, long[]> HAS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Is this video file there? Asked every frame by the loading / home screens, so the answer is kept for 2 s (no disk check per frame). */
	private static boolean has(String f) {
		long now = System.currentTimeMillis();
		long[] v = HAS.get(f);
		if (v != null && now - v[0] < 2000L) {
			return v[1] == 1L;
		}
		boolean ok = Files.isRegularFile(dir().resolve(f));
		HAS.put(f, new long[] {now, ok ? 1L : 0L});
		return ok;
	}

	private static boolean on() {
		return instance != null && instance.enabled;
	}

	/** Finds ffmpeg in the background as soon as the game starts (so the intro can begin right away). */
	public static void warmUp() {
		if (ffmpeg != null || finding || !on()) {
			return;
		}
		finding = true;
		Thread t = new Thread(() -> {
			ffmpeg = Ffmpeg.findPath();
			finding = false;
		}, "Sleep ffmpeg finder");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------ hooks
	/** Splash overlay: draw it black (the intro comes right after). */
	public static boolean blackLoading() {
		return on() && instance.blackLoading.on() && (has("intro.mp4") || has("main.mp4") || instance.playIntro.on() && seasonBundled());
	}

	private static final String BUNDLE = "/assets/sleep_client/intro/";

	/** True when a Christmas / Halloween intro is in the jar and a season is on. */
	private static String bundledKey;
	private static boolean bundledHas;

	private static boolean seasonBundled() {
		String k = dev.sleepclient.gui.IntroScreen.seasonKey();
		if (k == null) {
			return false;
		}
		if (!k.equals(bundledKey)) { // looking inside the jar is slow: once per season
			bundledKey = k;
			bundledHas = CinematicIntroMod.class.getResource(BUNDLE + k + ".mp4") != null;
		}
		return bundledHas;
	}

	/** Copies the bundled seasonal intro (video + sound) next to the other videos (ffmpeg needs real files). */
	private static boolean extractSeason(String key) {
		try {
			Files.createDirectories(dir());
			for (String ext : new String[] {"mp4", "wav"}) {
				Path out = dir().resolve("season_" + key + "." + ext);
				try (InputStream in = CinematicIntroMod.class.getResourceAsStream(BUNDLE + key + "." + ext)) {
					if (in == null) {
						return false;
					}
					byte[] data = in.readAllBytes();
					if (!Files.isRegularFile(out) || Files.size(out) != data.length) {
						Files.write(out, data);
					}
				}
			}
			return true;
		} catch (Exception e) {
			System.err.println("[Sleep] seasonal intro: " + e);
			return false;
		}
	}

	/** The title screen is about to open for the first time: play the intro first. */
	public static Screen maybeIntro(Screen title) {
		if (introDone || !on() || !instance.playIntro.on()) {
			introDone = true;
			return title;
		}
		introDone = true;
		// Christmas / Halloween: the bundled seasonal intro (ends on the seasonal home screen)
		String key = dev.sleepclient.gui.IntroScreen.seasonKey();
		if (key != null && extractSeason(key)) {
			return new dev.sleepclient.gui.IntroScreen(title, dir().resolve("season_" + key + ".mp4"), instance.canSkip.on(),
					dir().resolve("season_" + key + ".wav"), true);
		}
		if (!has("intro.mp4")) {
			return title;
		}
		return new dev.sleepclient.gui.IntroScreen(title, dir().resolve("intro.mp4"), instance.canSkip.on());
	}

	public static String ffmpeg() {
		warmUp();
		return ffmpeg;
	}

	public static boolean ffmpegSearching() {
		return finding;
	}

	/** Home screen background. Returns true when it drew the video. */
	public static boolean drawHome(GuiGraphicsExtractor c, int width, int height) {
		if (!on() || !instance.homeVideo.on() || !has("main.mp4")) {
			stopHome();
			return false;
		}
		if (home == null) {
			String ff = ffmpeg();
			if (ff == null) {
				return false;
			}
			home = new FfVideo(dir().resolve("main.mp4"), 1280, 720, true, Gfx.id("video/home_bg"));
			home.start(ff);
		}
		if (!home.upload()) {
			c.fill(0, 0, width, height, 0xFF000000);
			return true;
		}
		cover(c, home.id(), 1280, 720, width, height);
		return true;
	}

	/** Draws a 16:9 texture covering the whole screen (cropping the sides or top like the original). */
	public static void cover(GuiGraphicsExtractor c, net.minecraft.resources.Identifier id, int tw, int th, int width, int height) {
		cover(c, id, tw, th, width, height, 1F, 1F);
	}

	/** Same, with a zoom and a transparency (for the transition into the seasonal home screen). */
	public static void cover(GuiGraphicsExtractor c, net.minecraft.resources.Identifier id, int tw, int th, int width, int height, float zoom, float alpha) {
		float s = Math.max(width / (float) tw, height / (float) th) * zoom;
		int dw = Math.round(tw * s), dh = Math.round(th * s);
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		Gfx.tex(c, id, (width - dw) / 2, (height - dh) / 2, dw, dh, tw, th, (a << 24) | 0xFFFFFF);
	}

	public static boolean homeActive() {
		return home != null && home.hasPicture();
	}

	public static void stopHome() {
		if (home != null) {
			home.close();
			home = null;
		}
	}

	// ------------------------------------------------------------------ music
	public static void startMusic() {
		startMusic(dir().resolve("audio.wav"));
	}

	public static void startMusic(Path wav) {
		if (!on() || !instance.music.on() || !Files.isRegularFile(wav) || clip != null) {
			return;
		}
		try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
			Clip c = AudioSystem.getClip();
			c.open(in);
			clip = c;
			applyVolume();
			c.start();
		} catch (Exception e) {
			System.err.println("[Sleep] intro music: " + e);
		}
	}

	public static boolean musicPlaying() {
		return clip != null && clip.isRunning();
	}

	private static void applyVolume() {
		if (clip == null) {
			return;
		}
		try {
			var mc = net.minecraft.client.Minecraft.getInstance();
			float v = instance.volume.f() / 100F * mc.options.getFinalSoundSourceVolume(SoundSource.MASTER) * mc.options.getFinalSoundSourceVolume(SoundSource.MUSIC);
			FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			float db = v <= 0.0001F ? g.getMinimum() : (float) (20 * Math.log10(v));
			g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
		} catch (Exception ignored) {
		}
	}

	public static void stopMusic() {
		if (clip != null) {
			try {
				clip.stop();
				clip.close();
			} catch (Exception ignored) {
			}
			clip = null;
		}
	}

	/** Every client tick: music / video only on the menus, never in a world. */
	public static void clientTick(net.minecraft.client.Minecraft mc) {
		warmUp();
		if (mc.level != null) {
			stopMusic();
			stopHome();
			return;
		}
		if (clip != null) {
			applyVolume();
			if (!clip.isRunning() && clip.getFramePosition() >= clip.getFrameLength() - 1) {
				stopMusic(); // played once, like the original (the menu music takes over after)
			}
		}
		if (home != null && !(dev.sleepclient.compat.Mc.screen(mc) instanceof dev.sleepclient.gui.PlegumoTitleScreen)) {
			stopHome(); // only behind the home screen
		}
	}

	// ------------------------------------------------------------------ import
	private void importJar() {
		importMsg = "Looking for the jar...";
		try {
			Path jar = findJar();
			if (jar == null) {
				importMsg = "Put reimagined-intro*.jar in mods or Downloads";
				return;
			}
			Files.createDirectories(dir());
			int n = 0;
			try (ZipFile z = new ZipFile(jar.toFile())) {
				for (String name : new String[] {"intro.mp4", "main.mp4", "audio.wav"}) {
					ZipEntry e = z.getEntry("fancymenu/assets/" + name);
					if (e == null) {
						continue;
					}
					importMsg = "Copying " + name + "...";
					Path raw = dir().resolve(name.endsWith(".mp4") ? "raw_" + name : name);
					try (InputStream in = z.getInputStream(e)) {
						Files.copy(in, raw, StandardCopyOption.REPLACE_EXISTING);
					}
					if (name.endsWith(".mp4")) {
						shrink(raw, dir().resolve(name));
					}
					n++;
				}
			}
			importMsg = n == 0 ? "No videos inside that jar" : "Done! Restart the game to see it";
		} catch (Exception e) {
			importMsg = "Import failed: " + e.getMessage();
		}
	}

	/** The originals are 4K: make a 720p copy so they play smoothly (keeps the 4K one if ffmpeg can't). */
	private void shrink(Path raw, Path out) {
		String ff = Ffmpeg.findPath();
		if (ff == null) {
			try {
				Files.move(raw, out, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
			return;
		}
		importMsg = "Making " + out.getFileName() + " lighter (720p)...";
		List<String> cmd = List.of("-y", "-v", "error", "-i", raw.toString(), "-an", "-vf", "scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720",
				"-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", out.toString());
		String err = Ffmpeg.run(cmd, 300);
		try {
			if (Files.isRegularFile(out) && Files.size(out) > 1000) {
				Files.deleteIfExists(raw);
			} else {
				Files.move(raw, out, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception ignored) {
		}
	}

	private static Path findJar() throws Exception {
		List<Path> dirs = new ArrayList<>();
		dirs.add(FabricLoader.getInstance().getGameDir().resolve("mods"));
		dirs.add(Path.of(System.getProperty("user.home"), "Downloads"));
		dirs.add(Path.of(System.getProperty("user.home"), "Desktop"));
		dirs.add(dir());
		for (Path d : dirs) {
			if (!Files.isDirectory(d)) {
				continue;
			}
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(d, "*reimagined*intro*.jar")) {
				for (Path p : ds) {
					return p;
				}
			}
		}
		return null;
	}
}
