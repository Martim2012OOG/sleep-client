package dev.solclient.verity;

import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Verity's mouth and ears.
 * <ul>
 * <li>Mouth: the computer's own voice (macOS "say", Windows speech, Linux espeak), higher and bouncier when he is happy,
 * low and slow when he is angry. No download, no account.</li>
 * <li>Ears: what you say in ANY voice chat (the microphone hook every voice mod goes through) is cut into sentences by
 * loudness and sent to Whisper; plus a talk key that records the microphone directly.</li>
 * </ul>
 */
final class VerityVoice {

	private static Process speaking;
	private static final boolean MAC, WIN;

	static {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		MAC = os.contains("mac");
		WIN = os.contains("win");
	}

	private VerityVoice() {
	}

	private static javax.sound.sampled.Clip clip;
	private static long sayId;
	private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();

	/** Says the text out loud (stops what he was saying): with Verity's Fish Audio voice when there is a key, else the computer's voice. */
	static synchronized void say(String text, String mood) {
		stop();
		if (text == null || text.isBlank()) {
			return;
		}
		VerityMod m = VerityMod.instance;
		if (m != null && !m.fishKey.get().isBlank()) {
			long id = ++sayId;
			String key = m.fishKey.get().trim(), voice = m.fishVoice.get().trim(), model = m.fishModel.get().trim();
			boolean dark = mood.equals("angry") || mood.equals("furious") || mood.equals("creepy");
			Thread t = new Thread(() -> {
				try {
					com.google.gson.JsonObject b = new com.google.gson.JsonObject();
					b.addProperty("text", text);
					if (!voice.isEmpty()) {
						b.addProperty("reference_id", voice);
					}
					b.addProperty("format", "wav");
					b.addProperty("sample_rate", 44100);
					b.addProperty("latency", "balanced");
					com.google.gson.JsonObject pr = new com.google.gson.JsonObject();
					pr.addProperty("speed", dark ? 0.85 : 1.05);
					b.add("prosody", pr);
					var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.fish.audio/v1/tts")).timeout(java.time.Duration.ofSeconds(30))
							.header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
							.header("model", model.isEmpty() ? "s2.1-pro-free" : model).POST(java.net.http.HttpRequest.BodyPublishers.ofString(b.toString())).build();
					var res = HTTP.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
					if (res.statusCode() != 200) {
						Verity.status = "Fish Audio error " + res.statusCode() + ": " + new String(res.body(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");
						system(text, mood); // still say it, with the computer's voice
						return;
					}
					synchronized (VerityVoice.class) {
						if (id != sayId) {
							return; // something newer was said meanwhile
						}
						var in = AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(new java.io.ByteArrayInputStream(res.body())));
						clip = AudioSystem.getClip();
						clip.open(in);
						clip.start();
					}
				} catch (Exception e) {
					Verity.status = "Fish Audio: " + e.getMessage();
					system(text, mood);
				}
			}, "kubo-verity-voice");
			t.setDaemon(true);
			t.start();
			return;
		}
		system(text, mood);
	}

	/** The computer's own voice. */
	private static synchronized void system(String text, String mood) {
		String clean = text.replaceAll("[\\r\\n\"`$\\\\]", " ").trim();
		boolean dark = mood.equals("angry") || mood.equals("furious") || mood.equals("creepy");
		try {
			ProcessBuilder pb;
			if (MAC) {
				int pitch = dark ? 30 : mood.equals("happy") || mood.equals("excited") ? 66 : 58;
				int rate = dark ? 150 : 205;
				pb = new ProcessBuilder("say", "-r", String.valueOf(rate), "[[pbas " + pitch + "]]" + clean);
			} else if (WIN) {
				String pitch = dark ? "-40%" : "+35%", rate = dark ? "-20%" : "+10%";
				String ssml = "<speak version='1.0' xml:lang='en-US'><prosody pitch='" + pitch + "' rate='" + rate + "'>"
						+ clean.replace("&", "and").replace("<", " ").replace(">", " ").replace("'", "") + "</prosody></speak>";
				pb = new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
						"Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.SpeakSsml('" + ssml + "')");
			} else {
				pb = new ProcessBuilder("espeak", "-p", dark ? "20" : "75", "-s", dark ? "130" : "175", clean);
			}
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			speaking = pb.start();
		} catch (Exception e) {
			Verity.status = "No voice on this computer: " + e.getMessage();
		}
	}

	static synchronized void stop() {
		if (speaking != null && speaking.isAlive()) {
			speaking.destroy();
		}
		speaking = null;
		if (clip != null) {
			clip.stop();
			clip.close();
			clip = null;
		}
	}

	static synchronized boolean speaking() {
		return speaking != null && speaking.isAlive() || clip != null && clip.isRunning();
	}

	// ------------------------------------------------------------------ ears: voice chat microphone
	private static final int MAX = 16000 * 12;
	private static short[] buf = new short[MAX];
	private static int len, rate = 16000, silent;
	private static boolean inSpeech;
	private static float noise = 300F;
	private static long lastFeed;

	/** Samples from any voice chat's microphone (mono 16-bit), straight from the capture hook. */
	static void feed(java.nio.ShortBuffer sb, int count, int sampleRate) {
		if (!Verity.listening() || speaking()) {
			reset();
			return;
		}
		synchronized (VerityVoice.class) {
			lastFeed = System.currentTimeMillis();
			rate = sampleRate;
			double sum = 0;
			for (int i = 0; i < count; i++) {
				int v = sb.get(i);
				sum += (double) v * v;
			}
			float rms = (float) Math.sqrt(sum / Math.max(1, count));
			boolean loud = rms > Math.max(500F, noise * 3F);
			if (!inSpeech) {
				noise += (rms - noise) * 0.02F; // background level, learnt while you are quiet
				if (!loud) {
					return;
				}
				inSpeech = true;
				len = 0;
				silent = 0;
			}
			for (int i = 0; i < count && len < MAX; i++) {
				buf[len++] = sb.get(i);
			}
			silent = loud ? 0 : silent + count;
			if (silent > sampleRate * 0.7 || len >= Math.min(MAX, sampleRate * 12)) {
				inSpeech = false;
				if (len > sampleRate * 0.45) { // at least half a second: a word, not a click
					short[] copy = java.util.Arrays.copyOf(buf, len);
					int r = sampleRate;
					Verity.heard(copy, copy.length, r, true);
				}
				len = 0;
			}
		}
	}

	static void feed(short[] pcm, int sampleRate) {
		feed(java.nio.ShortBuffer.wrap(pcm), pcm.length, sampleRate);
	}

	private static synchronized void reset() {
		inSpeech = false;
		len = 0;
	}

	/** A voice chat microphone fed us in the last 2 seconds. */
	static boolean voiceChatLive() {
		return System.currentTimeMillis() - lastFeed < 2000L;
	}

	// ------------------------------------------------------------------ ears: the talk key (records the microphone itself)
	private static volatile boolean recording;
	private static Thread recorder;

	static synchronized void startRecording() {
		if (recording) {
			return;
		}
		recording = true;
		recorder = new Thread(() -> {
			AudioFormat fmt = new AudioFormat(16000, 16, 1, true, false);
			short[] pcm = new short[16000 * 15];
			int n = 0;
			try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt))) {
				line.open(fmt);
				line.start();
				byte[] b = new byte[3200];
				while (recording && n < pcm.length) {
					int r = line.read(b, 0, b.length);
					for (int i = 0; i + 1 < r && n < pcm.length; i += 2) {
						pcm[n++] = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
					}
				}
				line.stop();
			} catch (Exception e) {
				Verity.status = "Could not open the microphone: " + e.getMessage();
				recording = false;
				return;
			}
			if (n > 16000 * 0.3) {
				Verity.heard(pcm, n, 16000, false);
			}
		}, "kubo-verity-mic");
		recorder.setDaemon(true);
		recorder.start();
	}

	static synchronized void stopRecording() {
		recording = false;
	}

	static boolean recording() {
		return recording;
	}
}
