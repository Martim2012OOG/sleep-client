package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.fightcam.Ffmpeg;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * A small video player for the film editor: ffmpeg decodes the film (in real time, at low resolution) and the pictures are shown in
 * a texture. No sound here (use "Watch with sound" for that). Play, pause and jumping to a moment.
 */
public final class FilmPreview {

	private final File file;
	private final int w, h;
	private Process proc;
	private volatile byte[] latest;
	private volatile boolean ended;
	private NativeImageBackedTexture tex;
	private Identifier id;
	private byte[] shown;
	private boolean playing;
	private double fromSec, pausedAt;
	private long startedMs;
	private final double length;

	public FilmPreview(File file, int w, int h, double length) {
		this.file = file;
		this.w = w;
		this.h = h;
		this.length = Math.max(0.1, length);
		start(0, false);
	}

	private void start(double sec, boolean play) {
		stop();
		ended = false;
		playing = play;
		fromSec = sec;
		pausedAt = sec;
		startedMs = System.currentTimeMillis();
		List<String> cmd = new ArrayList<>();
		cmd.addAll(List.of(Ffmpeg.path, "-nostdin", "-hide_banner", "-loglevel", "quiet"));
		if (play) {
			cmd.add("-re");
		}
		cmd.addAll(List.of("-ss", String.format(java.util.Locale.ROOT, "%.2f", sec), "-i", file.getAbsolutePath(), "-an", "-vf", "scale=" + w + ":" + h + ",fps=30",
				"-pix_fmt", "rgba", "-f", "rawvideo"));
		if (!play) {
			cmd.addAll(List.of("-frames:v", "1"));
		}
		cmd.add("-");
		try {
			final Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
			proc = p;
			Thread t = new Thread(() -> {
				try (DataInputStream in = new DataInputStream(p.getInputStream())) {
					while (true) {
						byte[] f = new byte[w * h * 4];
						in.readFully(f);
						latest = f;
					}
				} catch (IOException e) {
					if (proc == p) {
						ended = true;
					}
				}
			}, "Sleep-Film-Preview");
			t.setDaemon(true);
			t.start();
		} catch (IOException e) {
			ended = true;
		}
	}

	private void stop() {
		Process p = proc;
		proc = null;
		if (p != null) {
			p.destroyForcibly();
		}
	}

	public boolean playing() {
		return playing && !ended;
	}

	public double position() {
		if (playing && !ended) {
			return Math.min(length, fromSec + (System.currentTimeMillis() - startedMs) / 1000.0);
		}
		return ended && playing ? length : pausedAt;
	}

	public double length() {
		return length;
	}

	public void play() {
		start(position() >= length - 0.1 ? 0 : position(), true);
	}

	public void pause() {
		double at = position();
		stop();
		playing = false;
		pausedAt = at;
	}

	public void seek(double sec) {
		start(Math.max(0, Math.min(length - 0.05, sec)), playing && !ended);
	}

	public void toggle() {
		if (playing()) {
			pause();
		} else {
			play();
		}
	}

	/** Draws the current picture in the box (call every frame). */
	public void draw(DrawContext c, int x, int y, int dw, int dh) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (tex == null) {
			tex = new NativeImageBackedTexture("sleep_film_preview", w, h, false);
			id = Gfx.id("film_preview");
			mc.getTextureManager().registerTexture(id, tex);
		}
		byte[] f = latest;
		if (f != null && f != shown) {
			shown = f;
			NativeImage img = tex.getImage();
			if (img != null) {
				int o = 0;
				for (int yy = 0; yy < h; yy++) {
					for (int xx = 0; xx < w; xx++) {
						img.setColorArgb(xx, yy, 0xFF000000 | (f[o] & 255) << 16 | (f[o + 1] & 255) << 8 | (f[o + 2] & 255));
						o += 4;
					}
				}
				tex.upload();
			}
		}
		c.fill(x, y, x + dw, y + dh, 0xFF000000);
		if (shown != null) {
			Gfx.tex(c, id, x, y, dw, dh, w, h, 0xFFFFFFFF);
		}
	}

	public void close() {
		stop();
		if (tex != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
			tex = null;
		}
	}
}
