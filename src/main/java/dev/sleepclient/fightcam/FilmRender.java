package dev.sleepclient.fightcam;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * Frame-exact film renderer (like the Replay Mod's "render"): the replay does NOT play in real time. For every video frame the
 * replay moves forward by exactly 1/fps seconds, the picture is read back from the GPU, and only then does the replay move on. So
 * the video is perfectly smooth (every frame is there, slow motion included) however slow the computer is: a slow computer only
 * takes longer to render, the film never stutters or skips. The pictures go to a separate ffmpeg process (hardware encoder, the
 * chosen look applied inside it), so the game thread only does the GPU copy.
 */
public final class FilmRender {

	private static final MinecraftClient mc = MinecraftClient.getInstance();
	private static final byte[] POISON = new byte[0];

	private static volatile boolean active;
	/** 0 = waiting for the next world picture, 1 = copying it, 2 = the picture is written: the replay may move on */
	private static volatile int state;
	private static int fps, srcW, srcH, capW, capH, frameBytes;
	private static Process proc;
	private static Thread writer;
	private static ArrayBlockingQueue<byte[]> free, queue;
	private static volatile long hookCalls, copies, done;
	private static SimpleFramebuffer small;
	private static File outFile;
	private static volatile String error, logText = "";
	private static volatile long frames, stateAt;

	private FilmRender() {
	}

	public static boolean active() {
		return active;
	}

	public static int fps() {
		return fps;
	}

	public static long frames() {
		return frames;
	}

	/** How long the render has been waiting for the GPU picture / the encoder (0 when it is not waiting). */
	public static long stalledMs() {
		return active && state != 2 ? System.currentTimeMillis() - stateAt : 0;
	}

	public static String error() {
		return error;
	}

	/** The ffmpeg process is still running. */
	public static boolean alive() {
		return proc != null && proc.isAlive();
	}

	public static String log() {
		return logText;
	}

	public static File file() {
		return outFile;
	}

	/** The picture of the current moment is written (so the replay can show the next one). */
	public static boolean frameDone() {
		return active && state == 2;
	}

	/** Called by the replay for every rendered frame: how many seconds it may move (0 = wait for the picture). */
	public static float takeStep() {
		if (state == 2) {
			state = 0;
			stateAt = System.currentTimeMillis();
			return 1F / fps;
		}
		return 0F;
	}

	/** Starts rendering into 'out' (render thread). Returns an error text or null. */
	public static String start(File out, int wantFps, int height, String lookChain) {
		if (active) {
			return "already rendering";
		}
		if (!Ffmpeg.ready()) {
			return "ffmpeg is not ready";
		}
		Framebuffer fb = mc.getFramebuffer();
		if (fb == null || fb.getColorAttachment() == null || fb.textureWidth < 16 || fb.textureHeight < 16) {
			return "window is minimised";
		}
		fps = Math.max(24, Math.min(120, wantFps));
		srcW = fb.textureWidth;
		srcH = fb.textureHeight;
		int wantH = Math.min(srcH, Math.max(240, height)) & ~1;
		capW = srcW;
		capH = srcH;
		small = null;
		// always render through a picture of a FIXED size: if the window changes size (it can, when the replay opens) the film keeps going
		try {
			int tw = Math.max(2, Math.round((float) srcW * wantH / srcH) & ~1);
			small = new SimpleFramebuffer("Sleep film", tw, wantH, false);
			capW = tw;
			capH = wantH;
		} catch (RuntimeException e) {
			small = null;
			capW = srcW;
			capH = srcH;
		}
		frameBytes = capW * capH * 4;
		out.getParentFile().mkdirs();
		List<String> cmd = new ArrayList<>(List.of(Ffmpeg.path, "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s",
				capW + "x" + capH, "-framerate", Integer.toString(fps), "-i", "-"));
		String look = lookChain == null || lookChain.isEmpty() ? "" : "," + lookChain;
		cmd.addAll(List.of("-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2" + look + ",format=nv12", "-r", Integer.toString(fps)));
		cmd.addAll(Ffmpeg.encoder.args());
		cmd.addAll(List.of("-movflags", "+faststart", out.getAbsolutePath()));
		if (!Ffmpeg.WINDOWS) {
			cmd.addAll(0, List.of("nice", "-n", "5"));
		}
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			proc = pb.start();
		} catch (IOException e) {
			freeGpu();
			return "could not start ffmpeg: " + e.getMessage();
		}
		free = new ArrayBlockingQueue<>(6);
		queue = new ArrayBlockingQueue<>(8);
		for (int i = 0; i < 5; i++) {
			free.add(new byte[frameBytes]);
		}
		hookCalls = copies = done = 0;
		outFile = out;
		error = null;
		logText = "";
		frames = 0;
		state = 0;
		stateAt = System.currentTimeMillis();
		active = true;
		final Process p = proc;
		writer = new Thread(() -> writeLoop(p), "Sleep-Film-Writer");
		writer.setDaemon(true);
		writer.start();
		Thread log = new Thread(() -> {
			try {
				byte[] b = p.getInputStream().readAllBytes();
				String t = new String(b).trim();
				logText = t.substring(Math.max(0, t.length() - 200));
			} catch (IOException ignored) {
				// ffmpeg is gone
			}
		}, "Sleep-Film-Log");
		log.setDaemon(true);
		log.start();
		return null;
	}

	private static void writeLoop(Process p) {
		try (OutputStream os = new BufferedOutputStream(p.getOutputStream(), 1 << 20)) {
			while (true) {
				byte[] f = queue.poll(200, TimeUnit.MILLISECONDS);
				if (f == null) {
					if (!p.isAlive()) {
						break;
					}
					continue;
				}
				if (f == POISON) {
					break;
				}
				os.write(f, 0, frameBytes);
				free.offer(f);
			}
			os.flush();
		} catch (IOException e) {
			if (error == null) {
				error = "ffmpeg stopped: " + e.getMessage();
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	/** What the render is doing (shown when the first picture takes long). */
	public static String debug() {
		return "hook " + hookCalls + "  copies " + copies + "  done " + done + "  state " + state;
	}

	/** Right after the world was drawn (render thread): takes the picture when the replay is showing a new moment. */
	public static void onWorldFrame() {
		hookCalls++;
		if (!active || state != 0) {
			return;
		}
		Framebuffer fb = mc.getFramebuffer();
		if ((fb.textureWidth != srcW || fb.textureHeight != srcH) && small == null) {
			error = "the window was resized while rendering";
			state = 2; // lets the screen notice and stop
			return;
		}
		byte[] dst = free.poll();
		if (dst == null) {
			return; // the encoder is behind: wait, the replay does not move on, nothing is skipped
		}
		Framebuffer src = fb;
		if (small != null) {
			try {
				fb.drawBlit(small.getColorAttachmentView());
				src = small;
			} catch (RuntimeException e) {
				error = "could not scale the picture: " + e;
				free.offer(dst);
				state = 2;
				return;
			}
		}
		state = 1;
		stateAt = System.currentTimeMillis();
		copies++;
		final byte[] d = dst;
		final int fw = capW, fh = capH;
		try {
			// the same way the game takes its own screenshots (F2): proven to work on every system
			net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(src, img -> {
				try {
					if (img.getWidth() != fw || img.getHeight() != fh) {
						error = "unexpected picture size " + img.getWidth() + "x" + img.getHeight();
						free.offer(d);
					} else {
						int o = 0;
						for (int y = 0; y < fh; y++) {
							for (int x = 0; x < fw; x++) {
								int p = img.getColorArgb(x, y);
								d[o++] = (byte) (p >> 16);
								d[o++] = (byte) (p >> 8);
								d[o++] = (byte) p;
								d[o++] = (byte) 255;
							}
						}
						queue.put(d);
						frames++;
					}
				} catch (RuntimeException | InterruptedException e) {
					error = String.valueOf(e);
				} finally {
					img.close();
					done++;
					state = 2;
				}
			});
		} catch (RuntimeException e) {
			error = String.valueOf(e);
			free.offer(dst);
			state = 2;
		}
	}

	/** Finishes the file (in the background); done gets null or an error, on the render thread. */
	public static void finish(Consumer<String> done) {
		if (!active) {
			done.accept("not rendering");
			return;
		}
		active = false;
		final Process p = proc;
		final Thread w = writer;
		final File out = outFile;
		Thread t = new Thread(() -> {
			String err = error;
			try {
				queue.put(POISON);
				w.join(60_000);
				if (!p.waitFor(300, TimeUnit.SECONDS)) {
					p.destroyForcibly();
					err = "ffmpeg did not finish";
				} else if (p.exitValue() != 0 && err == null) {
					err = logText.isEmpty() ? "ffmpeg exit " + p.exitValue() : logText;
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (err != null || !out.isFile() || out.length() < 1000) {
				out.delete();
				if (err == null) {
					err = "the film is empty";
				}
			}
			final String e2 = err;
			mc.execute(() -> {
				freeGpu();
				done.accept(e2);
			});
		}, "Sleep-Film-Finish");
		t.setDaemon(true);
		t.start();
	}

	/** Cancels the render and deletes the unfinished file. */
	public static void abort() {
		if (!active) {
			return;
		}
		active = false;
		final Process p = proc;
		final File out = outFile;
		if (p != null) {
			p.destroyForcibly();
		}
		Thread t = new Thread(() -> {
			try {
				if (p != null) {
					p.waitFor(5, TimeUnit.SECONDS);
				}
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			if (out != null) {
				out.delete();
			}
		}, "Sleep-Film-Abort");
		t.setDaemon(true);
		t.start();
		freeGpu();
	}

	private static void freeGpu() {
		if (small != null) {
			try {
				small.delete();
			} catch (RuntimeException ignored) {
				// already gone
			}
			small = null;
		}
	}
}
