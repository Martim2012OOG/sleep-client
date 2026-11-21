package dev.sleepclient.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Lives for Watch Party. The browser inside the game cannot decode the H.264 that YouTube lives use, so a live is played another
 * way: yt-dlp (a free, open-source tool) finds the stream address of the live, ffmpeg decodes picture and sound, the picture goes
 * on the screen in the world ({@link FfVideo}) and the sound plays through {@link Audio}. Runs on your computer only.
 */
public final class LiveStream {

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	private static final String RELEASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";

	private LiveStream() {
	}

	private static Path home() {
		return FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("tools");
	}

	private static Path bundled() {
		return home().resolve(WINDOWS ? "yt-dlp.exe" : "yt-dlp");
	}

	private static String asset() {
		return WINDOWS ? "yt-dlp.exe" : MAC ? "yt-dlp_macos" : "yt-dlp_linux";
	}

	private static boolean works(String exe) {
		try {
			if (exe.contains(File.separator) && !new File(exe).canExecute()) {
				return false;
			}
			Process p = new ProcessBuilder(exe, "--version").redirectErrorStream(true).start();
			p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
			return p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Finds yt-dlp (ours, the package managers' or the PATH). Blocking. */
	private static String find() {
		List<String> c = new ArrayList<>();
		c.add(bundled().toString());
		if (MAC) {
			c.add("/opt/homebrew/bin/yt-dlp");
			c.add("/usr/local/bin/yt-dlp");
		}
		c.add(WINDOWS ? "yt-dlp.exe" : "yt-dlp");
		for (String e : c) {
			if (works(e)) {
				return e;
			}
		}
		return null;
	}

	/** Downloads the official yt-dlp release for this computer and checks it against the published SHA-256 list. Blocking. */
	private static boolean install() {
		try {
			Files.createDirectories(home());
			HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
			String sums = http.send(HttpRequest.newBuilder(URI.create(RELEASE + "SHA2-256SUMS")).timeout(Duration.ofSeconds(30)).build(),
					HttpResponse.BodyHandlers.ofString()).body();
			String want = null;
			for (String line : sums.split("\\R")) {
				String[] f = line.trim().split("\\s+");
				if (f.length == 2 && f[1].replace("*", "").equals(asset())) {
					want = f[0].toLowerCase(Locale.ROOT);
				}
			}
			if (want == null) {
				return false;
			}
			Path tmp = home().resolve("yt-dlp.download");
			HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(RELEASE + asset())).timeout(Duration.ofMinutes(5)).build(),
					HttpResponse.BodyHandlers.ofInputStream());
			if (r.statusCode() != 200) {
				return false;
			}
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = r.body(); java.io.OutputStream out = Files.newOutputStream(tmp)) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) > 0) {
					md.update(buf, 0, n);
					out.write(buf, 0, n);
				}
			}
			StringBuilder hex = new StringBuilder();
			for (byte b : md.digest()) {
				hex.append(String.format("%02x", b));
			}
			if (!hex.toString().equals(want)) {
				Files.deleteIfExists(tmp);
				System.err.println("[Sleep] yt-dlp download did not match its published checksum, not using it");
				return false;
			}
			Files.move(tmp, bundled(), StandardCopyOption.REPLACE_EXISTING);
			bundled().toFile().setExecutable(true);
			if (MAC) {
				try {
					new ProcessBuilder("xattr", "-d", "com.apple.quarantine", bundled().toString()).start().waitFor(5, TimeUnit.SECONDS);
				} catch (Exception ignored) {
				}
			}
			return true;
		} catch (Exception e) {
			System.err.println("[Sleep] yt-dlp install: " + e);
			return false;
		}
	}

	/** The address ffmpeg can play for this YouTube video / live, or null with the reason in {@code error[0]}. Blocking: worker thread only. */
	public static String resolve(String vid, String[] error) {
		String exe = find();
		if (exe == null) {
			if (!install() || (exe = find()) == null) {
				error[0] = "Could not get yt-dlp (needed for lives). Install it yourself (brew install yt-dlp / winget install yt-dlp) and try again.";
				return null;
			}
		}
		try {
			Process p = new ProcessBuilder(exe, "-g", "-f", "best[height<=720]/best", "--no-playlist", "--no-warnings", "--socket-timeout", "15",
					"https://www.youtube.com/watch?v=" + vid).redirectErrorStream(false).start();
			StringBuilder err = new StringBuilder();
			Thread eth = new Thread(() -> {
				try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
					String l;
					while ((l = r.readLine()) != null) {
						if (err.length() < 400) {
							err.append(l).append(' ');
						}
					}
				} catch (Exception ignored) {
				}
			});
			eth.setDaemon(true);
			eth.start();
			String url = null;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = r.readLine()) != null) {
					if (url == null && l.startsWith("http")) {
						url = l.trim();
					}
				}
			}
			p.waitFor(45, TimeUnit.SECONDS);
			if (url == null) {
				error[0] = "Could not open this live: " + (err.length() == 0 ? "no stream found" : err.toString().strip());
			}
			return url;
		} catch (Exception e) {
			error[0] = "Could not run yt-dlp: " + e.getMessage();
			return null;
		}
	}

	/** The sound of a live: ffmpeg decodes it to raw 16-bit stereo and this plays it (volume follows the distance to the screen). */
	public static final class Audio implements AutoCloseable {

		public volatile float volume = 0.7F;
		private Process proc;
		private volatile boolean run = true;

		public boolean start(String ffmpeg, String url) {
			try {
				proc = new ProcessBuilder(ffmpeg, "-v", "error", "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5", "-i", url, "-vn",
						"-f", "s16le", "-ar", "44100", "-ac", "2", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start();
			} catch (Exception e) {
				return false;
			}
			Thread t = new Thread(this::pump, "Sleep live sound");
			t.setDaemon(true);
			t.start();
			return true;
		}

		private void pump() {
			try (InputStream in = proc.getInputStream()) {
				AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
				SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
				line.open(fmt, 44100 * 4 / 2);
				line.start();
				byte[] buf = new byte[4096];
				try {
					int n;
					while (run && (n = in.read(buf)) > 0) {
						float v = volume;
						for (int i = 0; i + 1 < n; i += 2) {
							int s = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
							s = Math.max(-32768, Math.min(32767, Math.round(s * v)));
							buf[i] = (byte) s;
							buf[i + 1] = (byte) (s >> 8);
						}
						line.write(buf, 0, n - (n % 4));
					}
				} finally {
					line.stop();
					line.close();
				}
			} catch (Throwable ignored) {
				// no sound device or the stream ended: the picture goes on
			}
		}

		@Override
		public void close() {
			run = false;
			Process p = proc;
			proc = null;
			if (p != null) {
				p.destroy();
			}
		}
	}
}
