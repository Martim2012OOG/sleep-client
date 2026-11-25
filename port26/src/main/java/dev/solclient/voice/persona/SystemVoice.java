package dev.solclient.voice.persona;

import dev.solclient.voice.VoiceConfig;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

/**
 * "Whole computer" mode: your microphone goes through the character and comes out of a VIRTUAL microphone that any app can
 * use (Discord, TikTok LIVE Studio, OBS, WhatsApp, browsers...). A program cannot create an audio device by itself, so this
 * needs the free virtual cable once: BlackHole on a Mac (existential.audio/blackhole), VB-CABLE on Windows (vb-audio.com/Cable).
 * The character's sound is written into the cable's input; in the other app you pick the cable as the microphone.
 */
public final class SystemVoice {

	private static final int RATE = 48000, CHUNK = 480; // 10 ms: low delay

	public static volatile String status = "";
	public static volatile String device = "";
	/** "Whole computer only": the game's voice mods keep your normal voice. */
	public static volatile boolean pcOnly;
	private static volatile boolean running;
	private static Thread thread;

	private SystemVoice() {
	}

	public static boolean on() {
		return running;
	}

	/** Names of the outputs that look like a virtual cable (best first). */
	public static List<String> cables() {
		List<String> out = new ArrayList<>();
		try {
			for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
				String n = mi.getName().toLowerCase(Locale.ROOT);
				if (n.contains("blackhole") || n.contains("cable input") || n.contains("vb-audio") || n.contains("loopback") || n.contains("soundflower")
						|| n.contains("virtual")) {
					Mixer m = AudioSystem.getMixer(mi);
					if (m.isLineSupported(new DataLine.Info(SourceDataLine.class, new AudioFormat(RATE, 16, 1, true, false)))) {
						out.add(mi.getName());
					}
				}
			}
		} catch (Throwable ignored) {
			// no Java Sound on this system
		}
		return out;
	}

	/** Every output device (for picking one by hand). */
	public static List<String> outputs() {
		List<String> out = new ArrayList<>();
		try {
			for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
				Mixer m = AudioSystem.getMixer(mi);
				if (m.isLineSupported(new DataLine.Info(SourceDataLine.class, new AudioFormat(RATE, 16, 1, true, false)))) {
					out.add(mi.getName());
				}
			}
		} catch (Throwable ignored) {
			// nothing
		}
		return out;
	}

	public static synchronized void start(String wanted) {
		if (running) {
			return;
		}
		running = true;
		status = "Starting...";
		thread = new Thread(() -> loop(wanted), "Caos-Persona-System");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY - 1); // audio must never wait
		thread.start();
	}

	public static synchronized void stop() {
		running = false;
		PersonaHook.onlyDevice = 0;
	}

	private static Mixer.Info find(String wanted) {
		String w = wanted == null ? "" : wanted.trim().toLowerCase(Locale.ROOT);
		Mixer.Info best = null;
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			String n = mi.getName().toLowerCase(Locale.ROOT);
			if (!w.isEmpty() && n.contains(w)) {
				return mi;
			}
			if (best == null && w.isEmpty() && (n.contains("blackhole") || n.contains("cable input") || n.contains("vb-audio"))) {
				best = mi;
			}
		}
		return best;
	}

	private static void loop(String wanted) {
		long dev = 0;
		SourceDataLine out = null;
		try {
			Mixer.Info mi = find(wanted);
			if (mi == null) {
				status = "No virtual microphone found. Install BlackHole (Mac) or VB-CABLE (Windows), it's free - see the button below.";
				running = false;
				return;
			}
			device = mi.getName();
			AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
			out = (SourceDataLine) AudioSystem.getMixer(mi).getLine(new DataLine.Info(SourceDataLine.class, fmt));
			out.open(fmt, CHUNK * 2 * 6); // ~60 ms of room: low delay, no crackles
			out.start();
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
			ALC11.alcCaptureStart(dev);
			PersonaHook.onlyDevice = pcOnly ? dev : 0;
			status = "On: in Discord / any app pick \"" + device + "\" as the microphone";
			ShortBuffer buf = BufferUtils.createShortBuffer(CHUNK);
			short[] pcm = new short[CHUNK];
			byte[] bytes = new byte[CHUNK * 2];
			while (running) {
				if (ALC10.alcGetInteger(dev, ALC11.ALC_CAPTURE_SAMPLES) < CHUNK) {
					Thread.sleep(2);
					continue;
				}
				buf.clear();
				ALC11.alcCaptureSamples(dev, buf, CHUNK); // the OpenAL hook puts the character on it here
				buf.get(0, pcm);
				PersonaHook.fallback(pcm, RATE); // same, when the hook is not in
				for (int i = 0; i < CHUNK; i++) {
					bytes[i * 2] = (byte) pcm[i];
					bytes[i * 2 + 1] = (byte) (pcm[i] >> 8);
				}
				if (out.available() >= bytes.length) { // never build a backlog (it would turn into delay)
					out.write(bytes, 0, bytes.length);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			status = "Stopped: " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " " + t.getMessage());
			System.err.println("[Caos] personas whole computer: " + t);
		} finally {
			running = false;
			PersonaHook.onlyDevice = 0;
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
