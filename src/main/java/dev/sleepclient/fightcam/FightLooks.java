package dev.sleepclient.fightcam;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks ("shaders") for recordings. They are applied AFTER the video is saved, by a separate low-priority ffmpeg process that
 * re-encodes the clip with a colour / glow / film filter chain, so the game itself never pays for them and you do not need to
 * play with a shader pack. Pick one, or use your own .cube colour LUT.
 */
public final class FightLooks {

	public static final String[] NAMES = {"Off", "Cinematic", "Vibrant", "Warm Sunset", "Icy Cold", "Noir", "Dreamy Glow", "PvP Punch", "Retro VHS",
			"Custom LUT (.cube)"};

	private static final String[] CHAINS = {
			"",
			"eq=contrast=1.12:saturation=1.15:gamma=0.97,colorbalance=rs=-0.04:bs=0.06:rm=0.03:bm=-0.02:rh=0.06:gh=0.02:bh=-0.06,vignette=PI/6,noise=alls=6:allf=t",
			"eq=contrast=1.08:saturation=1.45,unsharp=5:5:0.6",
			"colorbalance=rs=0.08:gs=0.02:bs=-0.08:rm=0.07:bm=-0.06:rh=0.06:bh=-0.05,eq=saturation=1.2:contrast=1.06,vignette=PI/7",
			"colorbalance=rs=-0.06:bs=0.1:rm=-0.04:bm=0.08:bh=0.06,eq=saturation=1.1:brightness=0.02:contrast=1.08",
			"hue=s=0,eq=contrast=1.25:brightness=-0.02,vignette=PI/5,noise=alls=8:allf=t",
			"split[a][b];[b]gblur=sigma=14[bl];[a][bl]blend=all_mode=screen:all_opacity=0.35,eq=saturation=1.15",
			"eq=contrast=1.15:saturation=1.3,unsharp=5:5:1.0,vignette=PI/8",
			"eq=saturation=1.4:contrast=1.1,gblur=sigma=0.7,noise=alls=14:allf=t",
			null};

	/** The currently chosen look as an ffmpeg filter chain ("" = none). Set by the FightCam mod, read when a video is saved. */
	public static volatile String chain = "";

	private FightLooks() {
	}

	/** The filter chain of look number idx (custom = the LUT file, ignored when it does not exist). */
	public static String chainFor(int idx, String lutPath) {
		if (idx <= 0 || idx >= CHAINS.length) {
			return "";
		}
		if (CHAINS[idx] != null) {
			return CHAINS[idx];
		}
		if (lutPath == null || lutPath.isBlank() || !new File(lutPath.trim()).isFile()) {
			return "";
		}
		String p = lutPath.trim().replace('\\', '/').replace(":", "\\:").replace("'", "\\'");
		return "lut3d=file='" + p + "'";
	}

	/** ffmpeg arguments that turn 'in' into 'out' with the look applied (same encoder as the recorder, sound copied). */
	public static List<String> args(String chain, File in, File out) {
		List<String> a = new ArrayList<>(List.of("-hide_banner", "-loglevel", "error", "-y", "-i", in.getAbsolutePath(), "-vf", chain + ",format=nv12"));
		a.addAll(Ffmpeg.encoder.args());
		a.addAll(List.of("-c:a", "copy", "-movflags", "+faststart", out.getAbsolutePath()));
		return a;
	}

	/**
	 * Applies the chosen look to a saved video, in place. Returns null when done (or when no look is set), else an error text; on any
	 * error the original video is kept untouched.
	 */
	public static String apply(File video) {
		String c = chain;
		if (c == null || c.isEmpty() || !video.isFile()) {
			return null;
		}
		File tmp = new File(video.getParentFile(), video.getName().replace(".mp4", "") + ".look.mp4");
		List<String> cmd = new ArrayList<>();
		if (!Ffmpeg.WINDOWS) {
			cmd.addAll(List.of("nice", "-n", "10")); // the game always wins over the look
		}
		cmd.add(Ffmpeg.path);
		cmd.add("-nostdin");
		cmd.addAll(args(c, video, tmp));
		String err = null;
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
			if (!p.waitFor(900, java.util.concurrent.TimeUnit.SECONDS)) {
				p.destroyForcibly();
				err = "timeout";
			} else if (p.exitValue() != 0) {
				err = "ffmpeg exit " + p.exitValue();
			}
		} catch (Exception e) {
			err = e.toString();
		}
		if (err != null || !tmp.isFile() || tmp.length() < 1000) {
			tmp.delete();
			return err == null ? "look failed" : err;
		}
		if (!video.delete() || !tmp.renameTo(video)) {
			tmp.delete();
			return "could not replace the video";
		}
		return null;
	}
}
