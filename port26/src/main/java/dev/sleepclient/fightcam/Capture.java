package dev.sleepclient.fightcam;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * The recorder behind FightCam and Clip Events. Built so it does NOT lag the game:
 * <ol>
 * <li>The picture is copied from the GPU asynchronously (the GPU writes it into a buffer and tells us when it is ready,
 * a frame or two later) - the game never waits for it. Three buffers rotate; if all are busy the frame is skipped.</li>
 * <li>The bytes are handed to a background thread that feeds them, at a perfectly steady frame rate, to ffmpeg.</li>
 * <li>ffmpeg (a separate program) flips / scales the picture and encodes it with the hardware encoder of the computer
 * (VideoToolbox on Mac, NVENC / AMF / QuickSync on Windows) at a high bitrate: smooth, sharp video.</li>
 * <li>The video goes into 2-second pieces on disk (a rolling buffer, like Medal). A clip = the last pieces glued together
 * and cut to the exact length: instant, no re-encoding. A normal recording = every piece since you pressed Record.</li>
 * </ol>
 */
public final class Capture {

	/** What the recorder needs to start. */
	public record Settings(int fps, int height, int bitrateMbps, String audioDevice, boolean withHud, int keepSeconds) {
	}

	private static final Minecraft mc = Minecraft.getInstance();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Sleep-Capture-IO");
		t.setDaemon(true);
		return t;
	});

	public static volatile String status = "Off";
	public static volatile String lastError = "";

	// ---- running state
	private static volatile boolean running;
	private static Settings cfg;
	private static Process proc;
	private static Thread writer, logger;
	private static Path dir;
	private static int srcW, srcH, capW, capH, frameBytes;
	/** small framebuffer the picture is shrunk into on the GPU (so a Retina screen is not copied at full size) */
	private static TextureTarget small;
	private static boolean noGpuScale;
	private static long startNs, nextNs, intervalNs;
	private static volatile long recordFromMs = -1;
	private static volatile long framesIn, framesOut, framesDropped;
	private static volatile int keepSeconds = 30;

	// ---- frame slots (CPU) and readback buffers (GPU)
	private static ArrayBlockingQueue<byte[]> free, ready;
	private static GpuBuffer[] gpu;
	private static boolean[] gpuBusy;
	private static final Object THUMB_LOCK = new Object();
	private static byte[] lastWritten;

	private Capture() {
	}

	public static boolean running() {
		return running;
	}

	public static boolean recording() {
		return running && recordFromMs >= 0;
	}

	public static long recordingStartedMs() {
		return recordFromMs;
	}

	public static Settings settings() {
		return cfg;
	}

	public static String stats() {
		if (!running) {
			return status;
		}
		return framesOut + " frames, " + framesDropped + " skipped";
	}

	public static Path bufferDir() {
		return mc.gameDirectory.toPath().resolve("kubo-recordings").resolve(".buffer");
	}

	public static File outputDir() {
		File d = new File(mc.gameDirectory, "kubo-recordings");
		d.mkdirs();
		return d;
	}

	/** Keep at least this many seconds of video in the rolling buffer (clip length + extra). */
	public static void keep(int seconds) {
		keepSeconds = Math.max(10, seconds);
	}

	// ------------------------------------------------------------------ start / stop
	/** Starts the background recorder (render thread). Needs Ffmpeg.ready(). */
	public static synchronized boolean start(Settings s) {
		if (running) {
			return true;
		}
		if (!Ffmpeg.ready()) {
			status = "ffmpeg not ready";
			return false;
		}
		RenderTarget fb = dev.sleepclient.compat.Mc.mainTarget(mc);
		if (fb == null || fb.getColorTexture() == null || fb.width < 16 || fb.height < 16) {
			return false; // minimised window: try again later
		}
		cfg = s;
		keepSeconds = Math.max(10, s.keepSeconds());
		srcW = fb.width;
		srcH = fb.height;
		int wantH = Math.min(srcH, Math.max(240, s.height())) & ~1;
		capW = srcW;
		capH = srcH;
		if (wantH < srcH && !noGpuScale) {
			// shrink on the GPU: 2-6x less memory to copy and feed to ffmpeg (the main cause of lag on Retina Macs)
			try {
				int w = Math.max(2, Math.round((float) srcW * wantH / srcH) & ~1);
				small = dev.sleepclient.compat.Gpu.newTarget("Sleep capture", w, wantH, false);
				capW = w;
				capH = wantH;
			} catch (RuntimeException e) {
				small = null;
				noGpuScale = true;
			}
		}
		frameBytes = capW * capH * 4;
		dir = bufferDir();
		try {
			Files.createDirectories(dir);
			clearDir(dir);
		} catch (IOException e) {
			status = "Cannot write " + dir;
			return false;
		}
		int outH = Math.min(capH, Math.max(240, s.height())) & ~1;
		// Each picture gets the real time it arrived ("wallclock"), and the output is made constant frame rate from that.
		// Before, every picture was counted as exactly 1/fps: when the encoder fell behind, pictures were skipped and the
		// clip played too fast ("at the speed of light"). Now a slow moment just repeats a picture and time stays real.
		List<String> cmd = new ArrayList<>(List.of(Ffmpeg.path, "-hide_banner", "-loglevel", "error", "-y",
				"-use_wallclock_as_timestamps", "1", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", capW + "x" + capH,
				"-framerate", Integer.toString(s.fps()), "-thread_queue_size", "512", "-i", "-"));
		List<String> audio = Ffmpeg.audioInput(s.audioDevice());
		cmd.addAll(audio);
		String scale = outH < capH ? ",scale=-2:" + outH + ":flags=bicubic" : ",scale=trunc(iw/2)*2:trunc(ih/2)*2";
		cmd.addAll(List.of("-vf", "vflip" + scale + ",format=nv12"));
		cmd.addAll(List.of("-vsync", "cfr", "-r", Integer.toString(s.fps())));
		cmd.addAll(Ffmpeg.encoder.args());
		cmd.addAll(List.of("-g", Integer.toString(s.fps()), "-force_key_frames", "expr:gte(t,n_forced*1)", "-bf", "0"));
		if (!audio.isEmpty()) {
			cmd.addAll(List.of("-c:a", "aac", "-b:a", "160k", "-ar", "48000"));
		}
		cmd.addAll(List.of("-f", "segment", "-segment_time", "2", "-segment_format", "mpegts", "-reset_timestamps", "0",
				dir.resolve("seg%06d.ts").toString()));
		reapOrphans(dir);
		hookShutdown();
		try {
			if (!Ffmpeg.WINDOWS) {
				// lower priority than the game: if the computer is busy, the game wins (on Mac/Linux)
				cmd.addAll(0, List.of("nice", "-n", "8"));
			}
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			proc = pb.start();
		} catch (IOException e) {
			status = "Could not start ffmpeg: " + e.getMessage();
			return false;
		}
		free = new ArrayBlockingQueue<>(4);
		ready = new ArrayBlockingQueue<>(4);
		for (int i = 0; i < 3; i++) {
			free.add(new byte[frameBytes]);
		}
		gpu = new GpuBuffer[3];
		gpuBusy = new boolean[3];
		for (int i = 0; i < 3; i++) {
			// usage 9 = map for reading (1) + copy destination (8), the same as vanilla screenshots
			gpu[i] = RenderSystem.getDevice().createBuffer(() -> "Sleep capture", 9, (long) frameBytes);
		}
		intervalNs = 1_000_000_000L / s.fps();
		startNs = System.nanoTime();
		nextNs = 0;
		framesIn = framesOut = framesDropped = 0;
		lastError = "";
		running = true;
		final Process p = proc;
		writer = new Thread(() -> writeLoop(p), "Sleep-Capture-Writer");
		writer.setDaemon(true);
		writer.setPriority(Thread.NORM_PRIORITY - 1);
		writer.start();
		logger = new Thread(() -> logLoop(p), "Sleep-Capture-Log");
		logger.setDaemon(true);
		logger.start();
		status = "Buffering (" + Ffmpeg.encoder.label() + ", " + capW + "x" + capH + " " + s.fps() + " fps)";
		return true;
	}

	private static boolean hooked;

	/**
	 * At game start: stops ffmpeg programs this game left behind in an earlier run (their parent is gone, so the system adopted them:
	 * parent id 1 / none). They keep the video encoder and a CPU core busy for ever. A running game's own ffmpeg has the game as parent
	 * and is never touched, so two games open at once do not hurt each other.
	 */
	public static void reapStrays() {
		Thread t = new Thread(() -> {
			try {
				String game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toString();
				long me = ProcessHandle.current().pid();
				int killed = 0;
				for (ProcessHandle ph : (Iterable<ProcessHandle>) ProcessHandle.allProcesses()::iterator) {
					var info = ph.info();
					if (!info.command().orElse("").contains("ffmpeg") || ph.pid() == me) {
						continue;
					}
					long parent = ph.parent().map(ProcessHandle::pid).orElse(1L);
					if (parent != 1L) {
						continue; // somebody alive owns it
					}
					if (String.join(" ", info.arguments().orElse(new String[0])).contains(game)) {
						ph.destroyForcibly();
						killed++;
					}
				}
				if (killed > 0) {
					System.err.println("[Sleep] stopped " + killed + " leftover ffmpeg program(s) from earlier runs");
				}
			} catch (RuntimeException e) {
				System.err.println("[Sleep] leftover ffmpeg check: " + e);
			}
		}, "Sleep-ReapStrays");
		t.setDaemon(true);
		t.start();
	}

	/** When the game exits (or is closed by the launcher) the recorder's ffmpeg must not survive it: its audio input never ends by itself. */
	private static synchronized void hookShutdown() {
		if (hooked) {
			return;
		}
		hooked = true;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			Process p = proc;
			if (p != null) {
				p.destroyForcibly();
			}
		}, "Sleep-Capture-Shutdown"));
	}

	/**
	 * Kills ffmpeg recorders left over from earlier runs of THIS game profile (found by the buffer folder in their command line). A game
	 * killed without a clean exit used to leave them running forever: each one keeps the hardware encoder and a CPU core busy, and a few
	 * of them make the whole computer (and every new recording) crawl at 1 fps.
	 */
	private static void reapOrphans(Path buffer) {
		try {
			String key = buffer.toString();
			long me = ProcessHandle.current().pid();
			int killed = 0;
			for (ProcessHandle ph : (Iterable<ProcessHandle>) ProcessHandle.allProcesses()::iterator) {
				if (ph.pid() == me) {
					continue;
				}
				var info = ph.info();
				String cmd = info.command().orElse("");
				if (!cmd.contains("ffmpeg")) {
					continue;
				}
				String all = String.join(" ", info.arguments().orElse(new String[0]));
				if (all.contains(key)) {
					ph.destroyForcibly();
					killed++;
				}
			}
			if (killed > 0) {
				System.err.println("[Sleep] FightCam: stopped " + killed + " leftover ffmpeg recorder(s) from an earlier run");
			}
		} catch (RuntimeException e) {
			System.err.println("[Sleep] FightCam: could not look for leftover recorders: " + e);
		}
	}

	/** Stops the recorder. Pieces on disk stay until the next start. */
	public static synchronized void stop() {
		if (!running) {
			return;
		}
		running = false;
		recordFromMs = -1;
		Process p = proc;
		Thread w = writer;
		proc = null;
		IO.execute(() -> {
			try {
				if (w != null) {
					w.join(3000);
				}
				if (p != null) {
					try {
						p.getOutputStream().close();
					} catch (IOException ignored) {
					}
					if (!p.waitFor(8, TimeUnit.SECONDS)) {
						p.destroyForcibly();
					}
				}
			} catch (InterruptedException ignored) {
			}
		});
		mc.execute(Capture::freeGpu);
		status = "Off";
	}

	private static void freeGpu() {
		if (small != null) {
			try {
				small.destroyBuffers();
			} catch (RuntimeException ignored) {
			}
			small = null;
		}
		if (gpu == null) {
			return;
		}
		for (int i = 0; i < gpu.length; i++) {
			if (gpu[i] != null && !gpuBusy[i]) {
				gpu[i].close();
			}
			gpu[i] = null;
		}
	}

	// ------------------------------------------------------------------ per frame (render thread)
	/**
	 * Called twice per rendered frame: once before the HUD is drawn (withHud = false: world only) and once at the very end
	 * (withHud = true: world + HUD + menus). Only the one matching the setting grabs the picture.
	 */
	public static void onFrame(boolean withHud) {
		if (!running) {
			return;
		}
		Settings s = cfg;
		boolean wantHud = s.withHud() && !ReplayForceWorldOnly.on;
		if (withHud != wantHud) {
			return;
		}
		long now = System.nanoTime();
		if (now < nextNs) {
			return;
		}
		nextNs = Math.max(nextNs + intervalNs, now - intervalNs);
		RenderTarget fb = dev.sleepclient.compat.Mc.mainTarget(mc);
		if (fb.width < 16 || fb.height < 16) {
			return; // minimised: nothing to record, and no restart loop
		}
		if (fb.width != srcW || fb.height != srcH) {
			// the window changed size: restart with the new size (the old pieces have another size, they are dropped)
			Settings keep = cfg;
			long rec = recordFromMs;
			stop();
			mc.execute(() -> {
				if (start(keep) && rec >= 0) {
					recordFromMs = System.currentTimeMillis();
				}
			});
			return;
		}
		GpuTexture tex = fb.getColorTexture();
		if (tex == null) {
			return;
		}
		int slot = -1;
		for (int i = 0; i < gpuBusy.length; i++) {
			if (!gpuBusy[i] && gpu[i] != null) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			framesDropped++;
			return;
		}
		if (small != null) {
			try {
				dev.sleepclient.compat.Gpu.shrink(fb, small);
				tex = small.getColorTexture();
			} catch (RuntimeException e) {
				// this GPU / version can't shrink like this: restart copying the full picture
				noGpuScale = true;
				Settings keep = cfg;
				long rec = recordFromMs;
				stop();
				mc.execute(() -> {
					if (start(keep) && rec >= 0) {
						recordFromMs = System.currentTimeMillis();
					}
				});
				return;
			}
		}
		slot = -1;
		for (int i = 0; i < gpuBusy.length; i++) {
			if (!gpuBusy[i] && gpu[i] != null) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			framesDropped++;
			return;
		}
		final int i = slot;
		final GpuBuffer buf = gpu[i];
		gpuBusy[i] = true;
		try {
			CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
			enc.copyTextureToBuffer(tex, buf, 0L, () -> {
				try {
					if (running) {
						byte[] dst = free.poll();
						if (dst == null) {
							framesDropped++;
						} else {
							dev.sleepclient.compat.Gpu.read(buf, dst);
							framesIn++;
							if (!ready.offer(dst)) {
								free.offer(dst);
								framesDropped++;
							}
						}
					}
				} catch (RuntimeException e) {
					lastError = String.valueOf(e);
				} finally {
					gpuBusy[i] = false;
					if (!running && gpu != null && gpu[i] == null) {
						buf.close();
					}
				}
			}, 0);
		} catch (RuntimeException e) {
			gpuBusy[i] = false;
			lastError = String.valueOf(e);
			framesDropped++;
		}
	}

	// ------------------------------------------------------------------ writer thread
	/** Feeds ffmpeg at a constant frame rate: repeats the last picture if the game is slower, skips if it is faster. */
	private static void writeLoop(Process p) {
		byte[] last = null;
		long written = 0;
		try (OutputStream out = new BufferedOutputStream(p.getOutputStream(), 1 << 20)) {
			while (running) {
				long due = (System.nanoTime() - startNs) / intervalNs + 1; // frames that should exist by now
				if (written >= due) {
					Thread.sleep(Math.max(1, (intervalNs - (System.nanoTime() - startNs) % intervalNs) / 1_000_000));
					continue;
				}
				// newest picture wins; older ones go back to the pool
				byte[] f = ready.poll(last == null ? 200 : 0, TimeUnit.MILLISECONDS);
				if (f != null) {
					byte[] newer;
					while ((newer = ready.poll()) != null) {
						free.offer(f);
						f = newer;
					}
					if (last != null) {
						synchronized (THUMB_LOCK) {
							free.offer(last);
							last = f;
							lastWritten = f;
						}
					} else {
						last = f;
						synchronized (THUMB_LOCK) {
							lastWritten = f;
						}
					}
				}
				if (last == null) {
					continue;
				}
				if (due - written > 1) {
					// behind (the computer is struggling): never write a burst of copies - the timestamps come from the
					// real time each picture arrives, so skipping here does not speed the video up
					written = due - 1;
				}
				out.write(last, 0, frameBytes);
				written++;
				framesOut++;
			}
			out.flush();
		} catch (IOException e) {
			if (running) {
				lastError = "ffmpeg stopped: " + e.getMessage();
				status = "Error: " + lastError;
				running = false;
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private static long p_maxCatchUp() {
		return Math.max(2, cfg.fps() / 4);
	}

	private static void logLoop(Process p) {
		try (InputStream in = p.getInputStream()) {
			byte[] b = new byte[4096];
			int n;
			StringBuilder tail = new StringBuilder();
			while ((n = in.read(b)) > 0) {
				tail.append(new String(b, 0, n));
				if (tail.length() > 2000) {
					tail.delete(0, tail.length() - 2000);
				}
				String t = tail.toString().trim();
				if (!t.isEmpty()) {
					lastError = t.substring(t.lastIndexOf('\n') + 1);
				}
			}
		} catch (IOException ignored) {
		}
		// clean up old pieces while running (keeps the disk small)
	}

	// ------------------------------------------------------------------ rolling buffer housekeeping
	/** Deletes pieces that are too old to ever be needed again. Called about once a second. */
	public static void housekeeping() {
		if (!running || dir == null) {
			return;
		}
		Path d = dir;
		long keepFrom = System.currentTimeMillis() - (keepSeconds + 6) * 1000L;
		long rec = recordFromMs;
		if (rec >= 0) {
			keepFrom = Math.min(keepFrom, rec - 4000);
		}
		final long limit = keepFrom;
		IO.execute(() -> {
			File[] fs = d.toFile().listFiles((f, n) -> n.startsWith("seg") && n.endsWith(".ts"));
			if (fs == null || fs.length <= 2) {
				return;
			}
			Arrays.sort(fs);
			for (int i = 0; i < fs.length - 2; i++) {
				if (fs[i].lastModified() < limit) {
					fs[i].delete();
				}
			}
		});
	}

	private static void clearDir(Path d) {
		File[] fs = d.toFile().listFiles();
		if (fs != null) {
			for (File f : fs) {
				f.delete();
			}
		}
	}

	// ------------------------------------------------------------------ clips + recordings
	/** Starts a normal recording (the pieces from now on are kept until you stop). */
	public static void beginRecording() {
		recordFromMs = System.currentTimeMillis();
	}

	/** Stops a normal recording and saves it. done gets null + the file, or an error. */
	public static void endRecording(File out, Consumer<String> done) {
		long from = recordFromMs;
		recordFromMs = -1;
		if (from < 0) {
			done.accept("Not recording");
			return;
		}
		double secs = (System.currentTimeMillis() - from) / 1000.0;
		save(secs, out, done);
	}

	/** Saves the last 'seconds' of video into out (.mp4). Runs in the background; done is called on the render thread. */
	public static void save(double seconds, File out, Consumer<String> done) {
		if (!running || dir == null) {
			done.accept("The recorder is not running");
			return;
		}
		Path d = dir;
		long now = System.currentTimeMillis();
		IO.execute(() -> {
			String err;
			try {
				// let ffmpeg finish the piece it is writing a little (it is still readable while being written)
				Thread.sleep(300);
				File[] fs = d.toFile().listFiles((f, n) -> n.startsWith("seg") && n.endsWith(".ts"));
				if (fs == null || fs.length == 0) {
					err = "Nothing recorded yet";
				} else {
					Arrays.sort(fs);
					long from = now - (long) (seconds * 1000) - 2500; // a piece ends at its lastModified, starts ~2 s earlier
					List<File> use = new ArrayList<>();
					for (File f : fs) {
						if (f.lastModified() >= from) {
							use.add(f);
						}
					}
					if (use.isEmpty()) {
						use.add(fs[fs.length - 1]);
					}
					// MPEG-TS pieces can simply be glued together byte by byte
					File joined = d.resolve("join.tmp.ts").toFile();
					try (OutputStream o = new BufferedOutputStream(Files.newOutputStream(joined.toPath()), 1 << 20)) {
						for (File f : use) {
							Files.copy(f.toPath(), o);
						}
					}
					out.getParentFile().mkdirs();
					List<String> args = new ArrayList<>(List.of("-hide_banner", "-loglevel", "error", "-y", "-sseof", "-" + String.format(java.util.Locale.ROOT, "%.2f", seconds),
							"-i", joined.getAbsolutePath(), "-c", "copy", "-movflags", "+faststart", out.getAbsolutePath()));
					err = Ffmpeg.run(args, 120);
					if (err != null) {
						// some ffmpeg builds dislike -sseof on a growing file: save it whole instead
						err = Ffmpeg.run(List.of("-hide_banner", "-loglevel", "error", "-y", "-i", joined.getAbsolutePath(), "-c", "copy",
								"-movflags", "+faststart", out.getAbsolutePath()), 120);
					}
					joined.delete();
				}
			} catch (Exception e) {
				err = e.toString();
			}
			final String e2 = err;
			mc.execute(() -> done.accept(e2));
		});
	}

	/** A small picture (w x h, ARGB, right way up) of the latest frame, for the "Clip saved" card. Null if none yet. */
	public static int[] thumbnail(int w, int h) {
		synchronized (THUMB_LOCK) {
			byte[] f = lastWritten;
			if (f == null || capW <= 0) {
				return null;
			}
			int srcW = capW, srcH = capH;
			int[] out = new int[w * h];
			for (int y = 0; y < h; y++) {
				int sy = srcH - 1 - (int) ((y + 0.5) * srcH / h); // the GPU picture is upside down
				for (int x = 0; x < w; x++) {
					int sx = (int) ((x + 0.5) * srcW / w);
					int o = (sy * srcW + sx) * 4;
					int r = f[o] & 255, g = f[o + 1] & 255, b = f[o + 2] & 255;
					out[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b;
				}
			}
			return out;
		}
	}

	/** Set while the replay viewer records, so the timeline / menus never end up in the video. */
	public static final class ReplayForceWorldOnly {
		public static volatile boolean on;

		private ReplayForceWorldOnly() {
		}
	}
}
