package dev.solclient.voice.persona;

import dev.solclient.voice.VoiceConfig;
import java.nio.ShortBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

/**
 * "Hear myself": opens your microphone, lets the character reshape it exactly like in a voice chat and plays the result back, so
 * you can try a character without anybody else (and without any voice chat being on). Use headphones, or it will whistle.
 */
public final class PersonaMonitor {

	private static final int RATE = 16000, CHUNK = 320;
	private static volatile boolean running;
	private static Thread thread;
	public static volatile String status = "";

	private PersonaMonitor() {
	}

	public static boolean on() {
		return running;
	}

	public static synchronized void toggle() {
		if (running) {
			stop();
		} else {
			start();
		}
	}

	public static synchronized void stop() {
		running = false;
		status = "";
	}

	private static synchronized void start() {
		running = true;
		status = "Opening the microphone...";
		thread = new Thread(PersonaMonitor::loop, "Caos-Persona-Monitor");
		thread.setDaemon(true);
		thread.start();
	}

	private static void loop() {
		long dev = 0;
		SourceDataLine out = null;
		try {
			String name = VoiceConfig.mic();
			dev = ALC11.alcCaptureOpenDevice(name == null || name.isEmpty() ? null : name, RATE, AL10.AL_FORMAT_MONO16, RATE);
			if (dev == 0 && name != null) {
				dev = ALC11.alcCaptureOpenDevice((CharSequence) null, RATE, AL10.AL_FORMAT_MONO16, RATE);
			}
			if (dev == 0) {
				status = "Could not open the microphone (Mac: System Settings > Privacy > Microphone)";
				running = false;
				return;
			}
			AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
			out = AudioSystem.getSourceDataLine(fmt);
			out.open(fmt, RATE / 5 * 2); // ~200 ms of room
			out.start();
			ALC11.alcCaptureStart(dev);
			status = "Listening: talk now (use headphones)";
			ShortBuffer buf = BufferUtils.createShortBuffer(CHUNK);
			short[] pcm = new short[CHUNK];
			byte[] bytes = new byte[CHUNK * 2];
			while (running) {
				if (ALC10.alcGetInteger(dev, ALC11.ALC_CAPTURE_SAMPLES) < CHUNK) {
					Thread.sleep(4);
					continue;
				}
				buf.clear();
				ALC11.alcCaptureSamples(dev, buf, CHUNK); // the OpenAL hook reshapes it here when it is in
				buf.get(0, pcm);
				PersonaHook.fallback(pcm, RATE); // same effect when the hook is not in
				for (int i = 0; i < CHUNK; i++) {
					bytes[i * 2] = (byte) pcm[i];
					bytes[i * 2 + 1] = (byte) (pcm[i] >> 8);
				}
				if (out.available() >= bytes.length) { // never let a backlog build up: it would turn into an echo
					out.write(bytes, 0, bytes.length);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			status = "Could not play it back: " + t.getClass().getSimpleName();
			System.err.println("[Caos] personas monitor: " + t);
		} finally {
			running = false;
			if (dev != 0) {
				try {
					ALC11.alcCaptureStop(dev);
					ALC11.alcCaptureCloseDevice(dev);
				} catch (Throwable ignored) {
					// closing anyway
				}
			}
			if (out != null) {
				out.close();
			}
		}
	}
}
