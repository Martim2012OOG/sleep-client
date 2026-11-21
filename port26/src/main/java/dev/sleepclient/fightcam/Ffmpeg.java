package dev.sleepclient.fightcam;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Finds (or downloads) ffmpeg and picks the best video encoder of this computer.
 * <ul>
 * <li>Mac: Apple VideoToolbox (the hardware encoder in every Mac, M1/M2/M3 and Intel) - no lag, pro quality.</li>
 * <li>Windows / Linux: NVIDIA NVENC, AMD AMF, Intel QuickSync, then x264 (CPU) as the last resort.</li>
 * </ul>
 * ffmpeg runs as a separate program, so encoding never slows the game thread down.
 */
public final class Ffmpeg {

	/** One encoder we know how to drive, with the arguments that make it look good in real time. */
	public record Encoder(String id, String label, List<String> args) {
	}

	public static volatile String path;
	public static volatile Encoder encoder;
	public static volatile String status = "Not checked yet";
	public static volatile boolean busy;
	public static volatile List<String> audioDevices = List.of();

	private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	public static final boolean MAC = OS.contains("mac"), WINDOWS = OS.contains("win");

	private Ffmpeg() {
	}

	public static Path home() {
		return FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("ffmpeg");
	}

	private static Path bundled() {
		return home().resolve(WINDOWS ? "ffmpeg.exe" : "ffmpeg");
	}

	public static boolean ready() {
		return path != null && encoder != null;
	}

	// ------------------------------------------------------------------ find
	/** Looks for ffmpeg and picks an encoder. Blocking (runs ffmpeg a few times): call it off the render thread. */
	public static synchronized boolean detect(String custom, String preferred, int bitrateMbps) {
		List<String> candidates = new ArrayList<>();
		if (custom != null && !custom.isBlank()) {
			candidates.add(custom.trim());
		}
		candidates.add(bundled().toString());
		if (MAC) {
			candidates.add("/opt/homebrew/bin/ffmpeg"); // Apple Silicon Homebrew
			candidates.add("/usr/local/bin/ffmpeg"); // Intel Homebrew
			candidates.add("/opt/local/bin/ffmpeg"); // MacPorts
		}
		candidates.add(WINDOWS ? "ffmpeg.exe" : "ffmpeg"); // PATH
		path = null;
		for (String c : candidates) {
			if (works(c)) {
				path = c;
				break;
			}
		}
		if (path == null) {
			encoder = null;
			status = "ffmpeg not found - press Install";
			return false;
		}
		encoder = pickEncoder(preferred, bitrateMbps);
		audioDevices = listAudio();
		status = encoder == null ? "No working encoder" : "Ready: " + encoder.label();
		return encoder != null;
	}

	/** Just finds ffmpeg (no encoder test): for playing videos (Cinematic Intro). Blocking, call off the render thread. */
	public static synchronized String findPath() {
		if (path != null) {
			return path;
		}
		List<String> candidates = new ArrayList<>();
		candidates.add(bundled().toString());
		if (MAC) {
			candidates.add("/opt/homebrew/bin/ffmpeg");
			candidates.add("/usr/local/bin/ffmpeg");
			candidates.add("/opt/local/bin/ffmpeg");
		}
		candidates.add(WINDOWS ? "ffmpeg.exe" : "ffmpeg");
		for (String c : candidates) {
			if (works(c)) {
				path = c;
				return c;
			}
		}
		return null;
	}

	private static boolean works(String exe) {
		try {
			if (exe.contains(File.separator) && !new File(exe).canExecute()) {
				return false;
			}
			Process p = new ProcessBuilder(exe, "-hide_banner", "-version").redirectErrorStream(true).start();
			drain(p.getInputStream());
			return p.waitFor(8, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static String drain(InputStream in) {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String l;
			while ((l = r.readLine()) != null) {
				if (sb.length() < 20000) {
					sb.append(l).append('\n');
				}
			}
		} catch (Exception ignored) {
		}
		return sb.toString();
	}

	/** All encoders in the order we try them on this OS. b = bitrate in Mbit/s. */
	public static List<Encoder> known(int b) {
		String br = b + "M", max = Math.round(b * 1.5F) + "M", buf = b * 2 + "M";
		List<Encoder> l = new ArrayList<>();
		Encoder vt = new Encoder("h264_videotoolbox", "Apple VideoToolbox (hardware)",
				List.of("-c:v", "h264_videotoolbox", "-realtime", "1", "-profile:v", "high", "-b:v", br, "-maxrate", max,
						"-bufsize", buf, "-allow_sw", "0"));
		Encoder nv = new Encoder("h264_nvenc", "NVIDIA NVENC (hardware)",
				List.of("-c:v", "h264_nvenc", "-preset", "p4", "-tune", "hq", "-rc", "vbr", "-b:v", br, "-maxrate", max, "-bufsize", buf,
						"-profile:v", "high", "-forced-idr", "1"));
		Encoder amf = new Encoder("h264_amf", "AMD AMF (hardware)",
				List.of("-c:v", "h264_amf", "-usage", "lowlatency", "-quality", "balanced", "-rc", "vbr_peak", "-b:v", br, "-maxrate", max));
		Encoder qsv = new Encoder("h264_qsv", "Intel QuickSync (hardware)",
				List.of("-c:v", "h264_qsv", "-preset", "faster", "-b:v", br, "-maxrate", max));
		int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
		Encoder x264 = new Encoder("libx264", "x264 (CPU)",
				List.of("-c:v", "libx264", "-preset", "superfast", "-tune", "zerolatency", "-crf", "20", "-maxrate", max, "-bufsize", buf,
						"-threads", Integer.toString(threads)));
		if (MAC) {
			l.add(vt);
		} else {
			l.add(nv);
			l.add(amf);
			l.add(qsv);
		}
		l.add(x264);
		return l;
	}

	private static Encoder pickEncoder(String preferred, int b) {
		List<Encoder> all = known(b);
		if (preferred != null && !preferred.isBlank() && !preferred.equals("auto")) {
			for (Encoder e : all) {
				if (e.id().equals(preferred) && test(e)) {
					return e;
				}
			}
		}
		for (Encoder e : all) {
			if (test(e)) {
				return e;
			}
		}
		return null;
	}

	/** Encodes a few black frames with this encoder: tells us if the hardware / driver really supports it. */
	private static boolean test(Encoder e) {
		try {
			List<String> cmd = new ArrayList<>(List.of(path, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=320x180:r=30",
					"-frames:v", "8", "-pix_fmt", "nv12"));
			cmd.addAll(e.args());
			cmd.addAll(List.of("-f", "null", "-"));
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			drain(p.getInputStream());
			return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception ex) {
			return false;
		}
	}

	/** Microphones / audio inputs ffmpeg can record from (names to put in the Audio option). */
	private static List<String> listAudio() {
		List<String> out = new ArrayList<>();
		try {
			List<String> cmd = MAC ? List.of(path, "-hide_banner", "-f", "avfoundation", "-list_devices", "true", "-i", "")
					: WINDOWS ? List.of(path, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy") : List.of();
			if (cmd.isEmpty()) {
				out.add("default");
				return out;
			}
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			String text = drain(p.getInputStream());
			p.waitFor(8, TimeUnit.SECONDS);
			boolean audio = false;
			for (String line : text.split("\n")) {
				if (MAC) {
					if (line.contains("AVFoundation audio devices")) {
						audio = true;
						continue;
					}
					if (line.contains("AVFoundation video devices")) {
						audio = false;
						continue;
					}
					if (audio) {
						int i = line.indexOf("] [");
						if (i >= 0) {
							int e = line.indexOf(']', i + 3);
							if (e > 0 && e + 2 <= line.length()) {
								out.add(line.substring(e + 2).trim());
							}
						}
					}
				} else if (line.contains("(audio)")) {
					int a = line.indexOf('"'), b = line.indexOf('"', a + 1);
					if (a >= 0 && b > a) {
						out.add(line.substring(a + 1, b));
					}
				}
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	/** ffmpeg arguments to record from this audio input (the name from the list), or empty for no audio. */
	public static List<String> audioInput(String device) {
		if (device == null || device.isBlank()) {
			return List.of();
		}
		if (MAC) {
			return List.of("-f", "avfoundation", "-thread_queue_size", "1024", "-i", ":" + device);
		}
		if (WINDOWS) {
			return List.of("-f", "dshow", "-thread_queue_size", "1024", "-audio_buffer_size", "50", "-i", "audio=" + device);
		}
		return List.of("-f", "pulse", "-thread_queue_size", "1024", "-i", device.equals("default") ? "default" : device);
	}

	// ------------------------------------------------------------------ install
	/** Downloads a ready-made ffmpeg for this computer into .minecraft/kubo/ffmpeg (about 25-80 MB). Blocking. */
	public static boolean install() {
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		boolean arm = arch.contains("aarch64") || arch.contains("arm");
		String plat = MAC ? (arm ? "darwin-arm64" : "darwin-x64") : WINDOWS ? "win32-x64" : arm ? "linux-arm64" : "linux-x64";
		String url = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/ffmpeg-" + plat + ".gz";
		busy = true;
		status = "Downloading ffmpeg...";
		try {
			Files.createDirectories(home());
			HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
			HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).build(),
					HttpResponse.BodyHandlers.ofInputStream());
			if (r.statusCode() != 200) {
				status = "Download failed (HTTP " + r.statusCode() + ")";
				return false;
			}
			long total = r.headers().firstValueAsLong("content-length").orElse(-1);
			Path tmp = home().resolve("ffmpeg.download");
			try (InputStream raw = r.body(); CountingIn counted = new CountingIn(raw, total); InputStream in = new GZIPInputStream(counted);
					OutputStream out = Files.newOutputStream(tmp)) {
				in.transferTo(out);
			}
			Files.move(tmp, bundled(), StandardCopyOption.REPLACE_EXISTING);
			bundled().toFile().setExecutable(true);
			if (MAC) {
				// downloaded files are quarantined by macOS; clear it so ffmpeg can run
				try {
					new ProcessBuilder("xattr", "-d", "com.apple.quarantine", bundled().toString()).start().waitFor(5, TimeUnit.SECONDS);
				} catch (Exception ignored) {
				}
			}
			status = "Installed";
			return true;
		} catch (Exception e) {
			status = "Download failed: " + e.getClass().getSimpleName();
			return false;
		} finally {
			busy = false;
		}
	}

	/** Progress of the download in the status line. */
	private static final class CountingIn extends java.io.FilterInputStream {
		private final long total;
		private long read;

		CountingIn(InputStream in, long total) {
			super(in);
			this.total = total;
		}

		private void add(long n) {
			if (n > 0) {
				read += n;
				status = total > 0 ? "Downloading ffmpeg... " + read * 100 / total + "%" : "Downloading ffmpeg... " + read / 1_000_000 + " MB";
			}
		}

		@Override
		public int read() throws java.io.IOException {
			int b = super.read();
			if (b >= 0) {
				add(1);
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws java.io.IOException {
			int n = super.read(b, off, len);
			add(n);
			return n;
		}
	}

	/** Runs ffmpeg with these arguments and waits (for saving clips). Returns null when OK, or the error text. */
	public static String run(List<String> args, int timeoutSec) {
		try {
			List<String> cmd = new ArrayList<>();
			cmd.add(path);
			cmd.addAll(args);
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			String text = drain(p.getInputStream());
			if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return "timeout";
			}
			if (p.exitValue() != 0) {
				String t = text.trim();
				return t.isEmpty() ? "exit " + p.exitValue() : t.substring(Math.max(0, t.length() - 200));
			}
			return null;
		} catch (Exception e) {
			return e.toString();
		}
	}
}
