package dev.solclient.mods;

import dev.solclient.fightcam.ClipToast;
import dev.solclient.fightcam.Ffmpeg;
import dev.solclient.option.Option;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * TikTok Clips: every clip you save also gets a vertical 9:16 copy (1080x1920) ready for TikTok / Reels / Shorts:
 * the game in the middle over a blurred copy of itself, your name on it, and music from your music folder if you want.
 */
public class VerticalClipsMod extends Mod {

	public static VerticalClipsMod instance;
	private final Option.Choice layout = choice("Layout", 0, "Blurred Background", "Zoom (fill the screen)", "Black Bars");
	private final Option.Text watermark = text("Text On The Video", "", 32, "your name / @tiktok");
	private final Option.Bool music = bool("Add Music", false);
	private final Option.Num musicVolume = num("Music Volume", 35, 5, 100, 5, "%");
	private final Option.Bool keepGameSound = bool("Keep Game Sound", true);

	public VerticalClipsMod() {
		super("TikTok Clips", "vertical_clips", "Every clip also saved in vertical 9:16 for TikTok, Reels and Shorts.", Category.UTILITY);
		instance = this;
		action("Music for clips (.mp3 / .wav / .ogg)", "Open Folder", () -> {
			try {
				Files.createDirectories(musicDir());
			} catch (Exception ignored) {
			}
			net.minecraft.util.Util.getPlatform().openFile(musicDir().toFile());
		}, () -> "");
	}

	private static Path musicDir() {
		return FabricLoader.getInstance().getGameDir().resolve("kubo-clip-music");
	}

	private Path pickMusic() {
		List<Path> l = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(musicDir(), "*.{mp3,wav,ogg,m4a}")) {
			for (Path p : ds) {
				l.add(p);
			}
		} catch (Exception ignored) {
		}
		return l.isEmpty() ? null : l.get((int) (Math.random() * l.size()));
	}

	/** Called after a clip was saved (any thread). */
	public static void export(File clip) {
		if (instance == null || !instance.enabled || clip == null || Ffmpeg.path == null) {
			return;
		}
		dev.solclient.social.Social.IO.execute(() -> instance.run(clip));
	}

	private void run(File clip) {
		String name = clip.getName().replaceFirst("\\.mp4$", "") + "_TikTok.mp4";
		File out = new File(clip.getParentFile(), name);
		String text = watermark.get().trim().replaceAll("[^\\p{L}\\p{N} @._#-]", "");
		String base = switch (layout.idx()) {
			case 1 -> "[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920[v0]";
			case 2 -> "[0:v]scale=1080:-2,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:black[v0]";
			default -> "[0:v]split[a][b];[a]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,boxblur=24:2,eq=brightness=-0.08[bg];"
					+ "[b]scale=1080:-2[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2[v0]";
		};
		Path song = music.on() ? pickMusic() : null;
		boolean ok = attempt(clip, out, base, text, song) || !text.isEmpty() && attempt(clip, out, base, "", song) || song != null && attempt(clip, out, base, "", null);
		if (ok) {
			ClipToast.show("TikTok clip ready", name, null, false);
		}
	}

	private boolean attempt(File clip, File out, String base, String text, Path song) {
		String vf = base;
		String vout = "[v0]";
		if (!text.isEmpty()) {
			// drawtext needs ffmpeg with freetype: if it fails we try again without the text
			vf += ";[v0]drawtext=text='" + text + "':fontcolor=white:fontsize=56:borderw=4:bordercolor=black@0.6:x=(w-text_w)/2:y=h*0.80[v1]";
			vout = "[v1]";
		}
		List<String> a = new ArrayList<>(List.of("-y", "-v", "error", "-i", clip.getAbsolutePath()));
		if (song != null) {
			a.add("-stream_loop");
			a.add("-1");
			a.add("-i");
			a.add(song.toAbsolutePath().toString());
		}
		String audio;
		if (song != null && keepGameSound.on()) {
			vf += ";[1:a]volume=" + musicVolume.f() / 100F + "[m];[0:a][m]amix=inputs=2:duration=first:dropout_transition=0[aout]";
			audio = "[aout]";
		} else if (song != null) {
			vf += ";[1:a]volume=" + musicVolume.f() / 100F + "[aout]";
			audio = "[aout]";
		} else {
			audio = keepGameSound.on() ? "0:a?" : null;
		}
		a.add("-filter_complex");
		a.add(vf);
		a.add("-map");
		a.add(vout);
		if (audio != null) {
			a.add("-map");
			a.add(audio);
			a.add("-c:a");
			a.add("aac");
			a.add("-b:a");
			a.add("160k");
		} else {
			a.add("-an");
		}
		a.add("-shortest");
		a.add("-c:v");
		a.add(Ffmpeg.encoder != null && Ffmpeg.encoder.id().contains("videotoolbox") ? "h264_videotoolbox" : "libx264");
		a.add("-b:v");
		a.add("10M");
		a.add("-pix_fmt");
		a.add("yuv420p");
		a.add("-movflags");
		a.add("+faststart");
		a.add(out.getAbsolutePath());
		String err = Ffmpeg.run(a, 240);
		return err == null && out.isFile() && out.length() > 1000;
	}
}
