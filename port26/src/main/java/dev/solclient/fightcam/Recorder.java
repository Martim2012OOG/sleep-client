package dev.solclient.fightcam;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns the pictures the game hands over into an .mp4 (H.264) on background threads, so the game itself stays smooth.
 * Pictures are scaled and encoded in parallel, then written in order at a fixed frame rate: if the game stalls for a
 * moment the last picture is repeated, so the video always lasts as long as the recording.
 */
public final class Recorder {

	/** One captured picture. The recorder calls release() exactly once when it is done with it. */
	public interface Source {
		int width();

		int height();

		/** Colour of a pixel as 0xAARRGGBB. */
		int argb(int x, int y);

		void release();
	}

	private static final long MAX_BYTES = 1_800_000_000L;

	private static final class Entry {
		final Future<byte[]> sample;
		final long ns;

		Entry(Future<byte[]> sample, long ns) {
			this.sample = sample;
			this.ns = ns;
		}
	}

	private final Mp4Writer writer;
	private final int outW, outH, fps, qp;
	private final ThreadPoolExecutor pool;
	private final LinkedBlockingQueue<Entry> order = new LinkedBlockingQueue<>();
	private final Entry poison = new Entry(null, 0);
	private final AtomicInteger pending = new AtomicInteger();
	private final int maxPending;
	private final Thread writerThread;
	private final ThreadLocal<H264Encoder> encoder;
	private final ThreadLocal<int[]> pixels;
	private volatile boolean full, stopping;
	private volatile String error;
	private volatile long frames;
	private volatile long dropped;
	private long startNs, written, skipped;
	private long stopNs = -1;

	public Recorder(File file, int outW, int outH, int fps, int qp, int workers) throws IOException {
		this.outW = outW & ~1;
		this.outH = outH & ~1;
		this.fps = fps;
		this.qp = qp;
		H264Encoder probe = new H264Encoder(this.outW, this.outH, fps, qp);
		this.writer = new Mp4Writer(file, this.outW, this.outH, fps, probe.sps(), probe.pps());
		this.maxPending = Math.max(3, workers * 3);
		this.pool = new ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
			Thread t = new Thread(r, "Caos-FightCam-Encoder");
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		});
		this.encoder = ThreadLocal.withInitial(() -> new H264Encoder(this.outW, this.outH, this.fps, this.qp));
		this.pixels = ThreadLocal.withInitial(() -> new int[this.outW * this.outH]);
		this.writerThread = new Thread(this::writeLoop, "Caos-FightCam-Writer");
		this.writerThread.setDaemon(true);
		this.writerThread.start();
	}

	public int outWidth() {
		return outW;
	}

	public int outHeight() {
		return outH;
	}

	public long frames() {
		return frames;
	}

	public long droppedFrames() {
		return dropped;
	}

	public boolean isFull() {
		return full;
	}

	public String error() {
		return error;
	}

	/** Bytes in the file so far (approximate, for the REC indicator). */
	public long bytes() {
		return writer.size();
	}

	/** Hands over a picture. Returns false (and releases it) if the encoder is busy: that frame is simply skipped. */
	public boolean submit(Source s, long nowNs) {
		if (error != null || full || stopping || pending.get() >= maxPending) {
			dropped++;
			s.release();
			return false;
		}
		pending.incrementAndGet();
		Future<byte[]> f = pool.submit(() -> {
			try {
				int[] px = pixels.get();
				scale(s, px);
				return encoder.get().encode(px);
			} finally {
				s.release();
			}
		});
		order.add(new Entry(f, nowNs));
		return true;
	}

	/** Stops and finishes the file. Blocks while the last pictures are written: call it from a background thread. */
	public void stop(long nowNs) {
		stopNs = nowNs;
		stopping = true;
		order.add(poison);
		try {
			writerThread.join(60_000);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
		pool.shutdownNow();
	}

	// ------------------------------------------------------------------ writer thread
	private void writeLoop() {
		try {
			while (true) {
				Entry e = order.take();
				if (e == poison) {
					break;
				}
				byte[] sample;
				try {
					sample = e.sample.get();
				} catch (Exception ex) {
					error = String.valueOf(ex.getCause() != null ? ex.getCause() : ex);
					continue;
				} finally {
					pending.decrementAndGet();
				}
				if (error != null) {
					continue;
				}
				try {
					put(sample, e.ns);
				} catch (IOException | RuntimeException ex) {
					error = ex.toString();
				}
			}
			// let the video last until the moment the recording stopped
			if (error == null && written > 0 && stopNs > 0) {
				long target = (stopNs - startNs) * fps / 1_000_000_000L - skipped;
				for (long i = written; i < target && i < written + 3L * fps; i++) {
					writer.repeatLast();
				}
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				if (error == null) {
					error = e.toString();
				}
			}
		}
	}

	private void put(byte[] sample, long ns) throws IOException {
		if (written == 0) {
			startNs = ns;
		}
		long target = (ns - startNs) * fps / 1_000_000_000L - skipped;
		if (written > 0 && target < written) {
			return; // arrived early: skip it, the video must not run faster than real time
		}
		long gap = target - written;
		long cap = 3L * fps;
		if (written > 0 && gap > cap) { // the game was frozen for a long time: do not stretch the video
			skipped += gap - cap;
			gap = cap;
		}
		for (long i = 0; i < gap && written > 0; i++) {
			writer.repeatLast();
			written++;
		}
		writer.add(sample);
		written++;
		frames = written;
		if (writer.size() > MAX_BYTES) {
			full = true;
		}
	}

	/** Scales a picture to the output size (averaging 4 samples when it gets smaller) into an RGB pixel array. */
	private void scale(Source s, int[] dst) {
		int sw = s.width(), sh = s.height();
		if (sw == outW && sh == outH) {
			for (int y = 0; y < outH; y++) {
				for (int x = 0; x < outW; x++) {
					dst[y * outW + x] = s.argb(x, y) & 0xFFFFFF;
				}
			}
			return;
		}
		double rx = (double) sw / outW, ry = (double) sh / outH;
		boolean box = rx > 1.2 || ry > 1.2;
		for (int y = 0; y < outH; y++) {
			int sy0 = Math.min(sh - 1, (int) (y * ry));
			int sy1 = Math.max(sy0, Math.min(sh - 1, (int) ((y + 1) * ry - 0.001)));
			for (int x = 0; x < outW; x++) {
				int sx0 = Math.min(sw - 1, (int) (x * rx));
				if (!box) {
					dst[y * outW + x] = s.argb(sx0, sy0) & 0xFFFFFF;
					continue;
				}
				int sx1 = Math.max(sx0, Math.min(sw - 1, (int) ((x + 1) * rx - 0.001)));
				int a = s.argb(sx0, sy0), b = s.argb(sx1, sy0), c = s.argb(sx0, sy1), d = s.argb(sx1, sy1);
				int r = (((a >> 16) & 255) + ((b >> 16) & 255) + ((c >> 16) & 255) + ((d >> 16) & 255)) >> 2;
				int g = (((a >> 8) & 255) + ((b >> 8) & 255) + ((c >> 8) & 255) + ((d >> 8) & 255)) >> 2;
				int bl = ((a & 255) + (b & 255) + (c & 255) + (d & 255)) >> 2;
				dst[y * outW + x] = (r << 16) | (g << 8) | bl;
			}
		}
	}
}
