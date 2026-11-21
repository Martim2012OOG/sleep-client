package dev.sleepclient.video;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Plays an mp4 as a texture with ffmpeg (no browser needed): ffmpeg decodes in real time ("-re") into raw BGRA
 * frames on a pipe, a reader thread keeps the newest frame and the render thread copies it into a texture.
 * Used by the Cinematic Intro (intro video + looping home screen background).
 */
public final class FfVideo implements LiveView {

	private final Path file;
	private final int w, h;
	private final boolean loop;
	private final Identifier id;
	private Process proc;
	private Thread reader;
	private volatile byte[] latest;
	private volatile long frames;
	private volatile boolean eof, failed;
	private long shown = -1;
	private DynamicTexture tex;
	private boolean registered;
	private long startedAt;

	public FfVideo(Path file, int w, int h, boolean loop, Identifier id) {
		this.file = file;
		this.w = w;
		this.h = h;
		this.loop = loop;
		this.id = id;
	}

	/** Live input (a window / the screen of this computer): ffmpeg input arguments and an optional first filter (crop). */
	private List<String> liveInput;
	private String liveFilter;

	public FfVideo(List<String> liveInput, String firstFilter, int w, int h, Identifier id) {
		this(null, w, h, false, id);
		this.liveInput = liveInput;
		this.liveFilter = firstFilter;
	}

	public Identifier id() {
		return id;
	}

	public int width() {
		return w;
	}

	public int height() {
		return h;
	}

	/** Starts decoding (ffmpeg must already be found: pass its path). */
	public boolean start(String ffmpeg) {
		if (ffmpeg == null) {
			failed = true;
			return false;
		}
		List<String> cmd = new ArrayList<>();
		cmd.add(ffmpeg);
		cmd.add("-v");
		cmd.add("error");
		if (liveInput != null) {
			cmd.addAll(liveInput);
			cmd.add("-an");
			cmd.add("-vf");
			String fit = "scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease,pad=" + w + ":" + h + ":(ow-iw)/2:(oh-ih)/2:black";
			cmd.add(liveFilter == null || liveFilter.isEmpty() ? fit : liveFilter + "," + fit);
		} else {
			boolean webm = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".webm");
			if (dev.sleepclient.fightcam.Ffmpeg.MAC && !webm) { // VP8/VP9 have no VideoToolbox decoder: software is faster to start
				cmd.add("-hwaccel");
				cmd.add("videotoolbox");
			}
			if (loop) {
				cmd.add("-stream_loop");
				cmd.add("-1");
			}
			cmd.add("-re");
			cmd.add("-i");
			cmd.add(file.toAbsolutePath().toString());
			cmd.add("-an");
			cmd.add("-vf");
			cmd.add("fps=30,scale=" + w + ":" + h + ":force_original_aspect_ratio=increase,crop=" + w + ":" + h); // capped at 30 fps
		}
		cmd.add("-f");
		cmd.add("rawvideo");
		cmd.add("-pix_fmt");
		cmd.add("bgra");
		cmd.add("-");
		try {
			proc = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		} catch (Exception e) {
			System.err.println("[Sleep] video: " + e);
			failed = true;
			return false;
		}
		startedAt = System.currentTimeMillis();
		reader = new Thread(this::read, "Sleep video " + (file == null ? "live" : file.getFileName()));
		reader.setDaemon(true);
		reader.start();
		return true;
	}

	private void read() {
		int size = w * h * 4;
		try (InputStream in = proc.getInputStream(); DataInputStream din = new DataInputStream(in)) {
			byte[] a = new byte[size], b = new byte[size];
			boolean useA = true;
			while (true) {
				byte[] buf = useA ? a : b;
				din.readFully(buf);
				latest = buf;
				frames++;
				useA = !useA;
			}
		} catch (Exception e) {
			// end of the video (or closed)
		} finally {
			eof = true;
		}
	}

	/** The picture currently in the texture (null before the first frame). */
	public NativeImage image() {
		return tex == null ? null : tex.getPixels();
	}

	/** How many frames have been copied into the texture so far (changes when the picture changes). */
	public long shownFrames() {
		return shown;
	}

	/** Render thread: copies the newest frame into the texture. Returns true once there is a picture. */
	public boolean upload() {
		byte[] f = latest;
		long n = frames;
		if (f == null) {
			return false;
		}
		if (n == shown) {
			return true;
		}
		shown = n;
		Minecraft mc = Minecraft.getInstance();
		if (tex == null) {
			tex = new DynamicTexture("kubo_video_" + id.getPath().replace('/', '_'), w, h, false);
		}
		NativeImage img = tex.getPixels();
		if (img == null) {
			return false;
		}
		// BGRA bytes read as little-endian ints are 0xAARRGGBB
		IntBuffer px = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		int i = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setPixel(x, y, px.get(i++) | 0xFF000000);
			}
		}
		tex.upload();
		if (!registered) {
			mc.getTextureManager().register(id, tex);
			registered = true;
		}
		return true;
	}

	public boolean hasPicture() {
		return latest != null && registered;
	}

	/** The video finished (not for looping ones) and its last frame was shown. */
	public boolean finished() {
		return eof && (shown == frames || latest == null);
	}

	public boolean failed() {
		return failed || eof && latest == null && System.currentTimeMillis() - startedAt > 4000L;
	}

	public long frames() {
		return frames;
	}

	@Override
	public void close() {
		Process p = proc;
		proc = null;
		if (p != null) {
			p.destroy();
		}
		if (registered) {
			Minecraft.getInstance().getTextureManager().release(id);
			registered = false;
			tex = null;
		} else if (tex != null) {
			tex.close();
			tex = null;
		}
	}
}
