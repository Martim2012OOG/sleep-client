package dev.solclient.video;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * The music that plays on a Watch Party screen while an ad is skipped ("massobeats - honey jam", non-copyright music, credited on the
 * screen). It starts somewhere in the middle of the track, fades in and out, and its level follows the Watch Party volume and the
 * distance to the screen.
 */
public final class AdMusic implements AutoCloseable {

	private static final String FILE = "/assets/sol_client/watchparty/honey_jam.mp3";

	public volatile float volume = 0.5F;
	private volatile boolean run = true;
	private volatile boolean fadeOut;

	public void start() {
		Thread t = new Thread(this::play, "Sleep ad music");
		t.setDaemon(true);
		t.start();
	}

	/** Fades out and stops (an ad is over). */
	public void stop() {
		fadeOut = true;
	}

	@Override
	public void close() {
		run = false;
	}

	private void play() {
		SourceDataLine line = null;
		try {
			boolean first = true;
			float gain = 0F;
			while (run && !(fadeOut && gain <= 0.001F)) {
				try (InputStream raw = AdMusic.class.getResourceAsStream(FILE)) {
					if (raw == null) {
						return;
					}
					javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(new BufferedInputStream(raw));
					javazoom.jl.decoder.Decoder dec = new javazoom.jl.decoder.Decoder();
					// first time: start somewhere in the middle (a frame is about 26 ms: 132 s of music is about 5000 frames)
					int skip = first ? 1700 + ThreadLocalRandom.current().nextInt(1300) : 0;
					first = false;
					javazoom.jl.decoder.Header h;
					while (run && (h = bs.readFrame()) != null) {
						if (skip > 0) {
							skip--;
							bs.closeFrame();
							continue;
						}
						javazoom.jl.decoder.SampleBuffer sb = (javazoom.jl.decoder.SampleBuffer) dec.decodeFrame(h, bs);
						if (line == null) {
							AudioFormat fmt = new AudioFormat(sb.getSampleFrequency(), 16, sb.getChannelCount(), true, false);
							line = AudioSystem.getSourceDataLine(fmt);
							line.open(fmt, sb.getSampleFrequency() * 2 * sb.getChannelCount() / 4);
							line.start();
						}
						short[] pcm = sb.getBuffer();
						int n = sb.getBufferLength();
						float target = fadeOut ? 0F : volume;
						byte[] out = new byte[n * 2];
						for (int i = 0; i < n; i++) {
							// about one second to fade in / out
							gain += (target - gain) * 0.00002F;
							int v = Math.max(-32768, Math.min(32767, Math.round(pcm[i] * gain)));
							out[i * 2] = (byte) v;
							out[i * 2 + 1] = (byte) (v >> 8);
						}
						line.write(out, 0, out.length);
						bs.closeFrame();
						if (fadeOut && gain <= 0.001F) {
							break;
						}
					}
				}
			}
		} catch (Throwable ignored) {
			// no sound device: the ad is just skipped in silence
		} finally {
			if (line != null) {
				line.stop();
				line.close();
			}
		}
	}
}
