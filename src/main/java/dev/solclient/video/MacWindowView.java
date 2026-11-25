package dev.solclient.video;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * A live picture of ONE window on a Mac: {@code screencapture -l <window id>} in a loop (about 8-15 pictures a second).
 * Unlike capturing the screen, this shows the window itself even when Minecraft (or anything else) is in front of it.
 * Needs Screen Recording permission for the launcher (System Settings > Privacy & Security > Screen Recording).
 */
public final class MacWindowView implements LiveView {

	private final long window;
	private final int w, h;
	private final Identifier id;
	private volatile boolean running = true, failed;
	private volatile int[] latest;
	private volatile int latestW, latestH;
	private volatile long frames;
	private long shown = -1;
	private NativeImageBackedTexture tex;
	private boolean registered;
	private final Thread thread;
	/** Size of the window picture (pixels) the last time: the panel can match the app's shape. */
	public volatile int srcW, srcH;

	public MacWindowView(long window, int w, int h, Identifier id) {
		this.window = window;
		this.w = w;
		this.h = h;
		this.id = id;
		thread = new Thread(this::loop, "Caos window " + window);
		thread.setDaemon(true);
		thread.start();
	}


	/** Fast path: a native helper streams the window (about 30 pictures a second). Falls back to the old screencapture loop. */
	private void loop() {
		String helper = WinCapHelper.ensure();
		if (helper != null && running) {
			if (streamLoop(helper)) {
				return; // ended normally (closed)
			}
		} else if (!WinCapHelper.problem.isEmpty()) {
			System.out.println("[Caos] window helper not used: " + WinCapHelper.problem);
		}
		if (running) {
			pollLoop();
		}
	}

	/** True when it ran fine until the panel was closed; false when the helper never gave a picture (then the fallback runs). */
	private boolean streamLoop(String helper) {
		Process p = null;
		try {
			p = new ProcessBuilder(helper, String.valueOf(window), String.valueOf(w), String.valueOf(h), "30")
					.redirectError(ProcessBuilder.Redirect.INHERIT).start();
			DataInputStream in = new DataInputStream(new BufferedInputStream(p.getInputStream(), 1 << 20));
			int[] front = new int[w * h], back = new int[w * h];
			byte[] raw = new byte[0];
			byte[] head = new byte[8];
			boolean got = false;
			while (running) {
				in.readFully(head);
				ByteBuffer hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
				int fw = hb.getInt(), fh = hb.getInt();
				if (fw < 2 || fh < 2 || fw > 8192 || fh > 8192) {
					return got; // out of step: give up on the helper
				}
				int n = fw * fh * 4;
				if (raw.length < n) {
					raw = new byte[n];
				}
				in.readFully(raw, 0, n);
				int[] frame = new int[fw * fh];
				ByteBuffer.wrap(raw, 0, n).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(frame); // BGRA bytes = ARGB ints
				int cw = Math.min(fw, w), ch = Math.min(fh, h), ox = (w - cw) / 2, oy = (h - ch) / 2;
				java.util.Arrays.fill(back, 0xFF000000);
				for (int y = 0; y < ch; y++) {
					System.arraycopy(frame, y * fw, back, (oy + y) * w + ox, cw);
				}
				int[] t = front;
				front = back;
				back = t;
				srcW = cw;
				srcH = ch;
				latest = front;
				latestW = w;
				latestH = h;
				frames++;
				got = true;
			}
			return true;
		} catch (java.io.EOFException e) {
			return false; // helper ended (window closed or no Screen Recording permission): try the old way
		} catch (Exception e) {
			return false;
		} finally {
			if (p != null) {
				p.destroy();
			}
		}
	}

	private void pollLoop() {
		Path tmp = null;
		int misses = 0;
		try {
			tmp = Files.createTempFile("kubo-win-" + window, ".jpg");
			while (running) {
				long t0 = System.currentTimeMillis();
				Process p = new ProcessBuilder("screencapture", "-x", "-o", "-t", "jpg", "-l" + window, tmp.toString())
						.redirectErrorStream(true).start();
				p.getInputStream().readAllBytes();
				p.waitFor();
				BufferedImage img = Files.size(tmp) > 0 ? ImageIO.read(tmp.toFile()) : null;
				if (img == null) {
					if (++misses > 20) {
						failed = true; // the window closed, or no Screen Recording permission
						return;
					}
					Thread.sleep(200);
					continue;
				}
				misses = 0;
				srcW = img.getWidth();
				srcH = img.getHeight();
				latest = fit(img);
				latestW = w;
				latestH = h;
				frames++;
				long spent = System.currentTimeMillis() - t0;
				Thread.sleep(Math.max(15, 66 - spent));
			}
		} catch (Exception e) {
			failed = true;
		} finally {
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (Exception ignored) {
				}
			}
		}
	}

	/** The window scaled into the panel size, centred, black around (keeps its shape). */
	private int[] fit(BufferedImage img) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		double s = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
		int dw = (int) Math.round(img.getWidth() * s), dh = (int) Math.round(img.getHeight() * s);
		g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
		g.dispose();
		return out.getRGB(0, 0, w, h, null, 0, w);
	}

	/** Where a point of the panel picture (0..1) is inside the window (0..1), or null when it is on the black border. */
	public double[] toWindow(double u, double v) {
		if (srcW <= 0 || srcH <= 0) {
			return new double[] {u, v};
		}
		double s = Math.min(w / (double) srcW, h / (double) srcH);
		double dw = srcW * s, dh = srcH * s, ox = (w - dw) / 2, oy = (h - dh) / 2;
		double px = u * w, py = v * h;
		if (px < ox || py < oy || px > ox + dw || py > oy + dh) {
			return null;
		}
		return new double[] {(px - ox) / dw, (py - oy) / dh};
	}

	@Override
	public boolean upload() {
		int[] px = latest;
		long n = frames;
		if (px == null) {
			return false;
		}
		if (n == shown) {
			return true;
		}
		shown = n;
		if (tex == null) {
			tex = new NativeImageBackedTexture("kubo_window_" + window, w, h, false);
		}
		NativeImage img = tex.getImage();
		if (img == null) {
			return false;
		}
		for (int y = 0; y < h; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				img.setColorArgb(x, y, px[row + x] | 0xFF000000);
			}
		}
		tex.upload();
		if (!registered) {
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			registered = true;
		}
		return true;
	}

	@Override
	public boolean hasPicture() {
		return registered;
	}

	@Override
	public boolean failed() {
		return failed;
	}

	@Override
	public Identifier id() {
		return id;
	}

	@Override
	public int width() {
		return w;
	}

	@Override
	public int height() {
		return h;
	}

	@Override
	public void close() {
		running = false;
		if (registered) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
			registered = false;
		} else if (tex != null) {
			tex.close();
		}
		tex = null;
	}
}
