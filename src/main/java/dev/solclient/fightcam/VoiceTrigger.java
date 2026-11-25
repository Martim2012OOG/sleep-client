package dev.solclient.fightcam;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import net.fabricmc.loader.api.FabricLoader;

/**
 * "Jarvis, clipa isto": listens to the microphone and fires when you say your phrase.
 * <p>
 * Speech recognition runs 100% on this computer (Vosk, offline - nothing is sent anywhere). The small language model
 * (~40 MB) is downloaded once into .minecraft/kubo/voice. What you say is turned into text and compared with your phrase
 * in a forgiving way (accents, "isto"/"isso", a missed letter...), so you do not have to say it perfectly.
 */
public final class VoiceTrigger {

	public static final String[] LANGS = {"Portugues", "English"};
	private static final String[] MODEL = {"vosk-model-small-pt-0.3", "vosk-model-small-en-us-0.15", "vosk-model-small-es-0.42", "vosk-model-small-fr-0.22",
			"vosk-model-small-de-0.15", "vosk-model-small-it-0.22"};

	public static volatile String status = "Off";
	public static volatile String heard = "";
	public static volatile float level;

	private static volatile boolean running;
	private static volatile String phrase = "jarvis clipa isto";
	private static volatile int strictness = 70;
	private static volatile Runnable onTrigger;
	private static Thread thread;
	private static long lastFire;
	/** Bumped by every start / stop: an old listening thread sees it changed and ends (no two listeners at once). */
	private static volatile int gen;
	/** macOS: use Apple's speech recognition (much better than the small offline model); falls back to Vosk. */
	public static volatile boolean preferApple = true;
	/** 0 Apple (Mac), 1 offline text (Vosk), 2 your own recorded phrase. */
	public static volatile int engine = 0;
	private static volatile Process appleProc;
	/** Why Apple's recognition is not used right now ("" = it is, or it was not tried). */
	public static volatile String appleProblem = "";
	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

	private VoiceTrigger() {
	}

	public static boolean running() {
		return running;
	}

	public static void setPhrase(String p, int strict) {
		phrase = p == null ? "" : p;
		strictness = strict;
	}

	private static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("voice");
	}

	/** Installed = fully unpacked (a ".ok" mark is written only after the whole model was downloaded and unpacked). */
	public static boolean modelInstalled(int lang) {
		Path m = dir().resolve(MODEL[lang]);
		if (Files.exists(m.resolve(".ok"))) {
			return true;
		}
		// older installs (before the mark): accept them only if the main files are there
		return Files.isRegularFile(m.resolve("am").resolve("final.mdl")) && Files.isRegularFile(m.resolve("conf").resolve("model.conf"))
				&& Files.isDirectory(m.resolve("graph"));
	}

	/** Starts listening (downloads the model the first time). trigger is called on a background thread. */
	public static synchronized void start(int lang, Runnable trigger) {
		if (running) {
			return;
		}
		onTrigger = trigger;
		running = true;
		final int l = Math.max(0, Math.min(MODEL.length - 1, lang));
		final int g = ++gen;
		thread = new Thread(() -> {
			if (engine == 2) {
				runOwn(g);
				return;
			}
			if (MAC && preferApple && runApple(l, g)) {
				return; // Apple's recognition ran until it was stopped
			}
			if (live(g)) {
				run(l, g);
			}
		}, "Caos-Voice");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY + 1);
		thread.start();
	}

	public static synchronized void stop() {
		running = false;
		gen++;
		status = "Off";
		level = 0;
		Process p = appleProc;
		appleProc = null;
		if (p != null) {
			p.destroy();
		}
	}

	private static boolean live(int g) {
		return running && gen == g;
	}

	// ------------------------------------------------------------------ Apple speech (macOS)
	/**
	 * Runs the little speech helper (kubo/speech-helper in the jar, Apple's speech recognition on this Mac) and checks
	 * every line it hears. Returns false when it could not be used (then the offline engine takes over).
	 */
	private static boolean runApple(int lang, int g) {
		String bin = AppleSpeech.path();
		if (bin == null) {
			appleProblem = AppleSpeech.problem;
			return false;
		}
		Process p = null;
		boolean ready = false;
		try {
			status = "Starting Apple voice recognition...";
			p = new ProcessBuilder(bin, lang == 0 ? "pt-PT" : "en-US", phrase).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			appleProc = p;
			java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
			OutputStream o = p.getOutputStream();
			Silence quiet = new Silence();
			String line;
			while (live(g) && (line = in.readLine()) != null) {
				if (line.startsWith("READY")) {
					ready = true;
					appleProblem = "";
					status = "Listening for \"" + phrase + "\" (Apple)";
				} else if (line.startsWith("LEVEL ")) {
					try {
						level = Float.parseFloat(line.substring(6).trim());
					} catch (NumberFormatException ignored) {
					}
					quiet.update(level, "Listening for \"" + phrase + "\" (Apple)");
				} else if (line.startsWith("TEXT ")) {
					String t = line.substring(5).trim();
					if (!t.isEmpty()) {
						heard = t;
						if (check(t)) {
							o.write("RESET\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
							o.flush(); // a new sentence: the same words do not fire twice
						}
					}
				} else if (line.startsWith("WARN ")) {
					heard = "(Apple: " + line.substring(5).trim() + ")"; // shown in "Heard", so a problem is visible
				} else if (line.startsWith("ERR ")) {
					String e = line.substring(4);
					appleProblem = e.startsWith("auth") ? "Apple speech recognition is not allowed (System Settings > Privacy & Security > Speech Recognition)"
							: e.startsWith("mic") ? "No microphone for Apple speech recognition"
							: e.startsWith("locale") || e.startsWith("unavailable") ? "Apple speech recognition is not available for this language" : e;
					break;
				}
			}
		} catch (Exception e) {
			appleProblem = "Apple voice helper: " + e.getMessage();
		} finally {
			if (p != null) {
				p.destroy();
			}
			if (appleProc == p) {
				appleProc = null;
			}
			level = 0;
		}
		if (!live(g)) {
			return true; // stopped on purpose
		}
		if (ready && appleProblem.isEmpty()) {
			appleProblem = "Apple voice recognition stopped";
		}
		System.err.println("[Caos] Apple voice: " + appleProblem + " - using the offline engine");
		return false;
	}

	// ------------------------------------------------------------------ your own recorded phrase
	/** The next thing you say is saved as one recording of your phrase. */
	public static void recordPhrase() {
		OwnVoice.recordNext = true;
	}

	public static boolean recordingPhrase() {
		return OwnVoice.recordNext;
	}

	public static int phraseRecordings() {
		return OwnVoice.count();
	}

	public static void clearPhrase() {
		OwnVoice.clear();
	}

	private static void runOwn(int g) {
		TargetDataLine line = null;
		try {
			AudioFormat fmt = new AudioFormat(OwnVoice.RATE, 16, 1, true, false);
			line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
			line.open(fmt, 8192);
			line.start();
			String listening = "Listening for your recorded phrase";
			status = OwnVoice.count() == 0 ? "Record your phrase first (press Record, then say it)" : listening;
			OwnVoice.Segmenter seg = new OwnVoice.Segmenter();
			Silence quiet = new Silence();
			byte[] buf = new byte[3200];
			while (live(g)) {
				int n = line.read(buf, 0, buf.length);
				if (n <= 0) {
					continue;
				}
				level = rms(buf, n);
				quiet.update(level, OwnVoice.count() == 0 ? status : listening);
				for (int i = 0; i + 1 < n; i += 2) {
					float[] utt = seg.feed((short) ((buf[i] & 255) | buf[i + 1] << 8) / 32768F);
					if (utt == null) {
						continue;
					}
					if (OwnVoice.recordNext) {
						OwnVoice.recordNext = false;
						OwnVoice.addSample(utt);
						int c = OwnVoice.count();
						heard = "(recording " + c + "/" + OwnVoice.MAX_SAMPLES + " saved" + (c < OwnVoice.MAX_SAMPLES ? " - record it again" : "") + ")";
						status = listening;
					} else if (OwnVoice.count() > 0) {
						boolean hit = OwnVoice.matches(utt, strictness);
						heard = (hit ? "(your phrase!  " : "(not it  ") + OwnVoice.lastScore + ")";
						if (hit) {
							fire();
						}
					}
				}
			}
		} catch (Throwable t) {
			status = t instanceof javax.sound.sampled.LineUnavailableException || t instanceof IllegalArgumentException
					? "No microphone (Mac: System Settings > Privacy > Microphone > allow the launcher)" : "Voice error: " + t.getMessage();
			System.err.println("[Caos] own voice: " + t);
		} finally {
			if (gen == g) {
				running = false;
				level = 0;
			}
			if (line != null) {
				line.stop();
				line.close();
			}
		}
	}

	private static void fire() {
		long now = System.currentTimeMillis();
		if (now - lastFire > 2500) {
			lastFire = now;
			Runnable t = onTrigger;
			if (t != null) {
				t.run();
			}
		}
	}

	/** Notices a microphone that only sends silence (on a Mac: no permission for the launcher). */
	private static final class Silence {
		private long quietSince = System.currentTimeMillis();
		private boolean warned;

		void update(float lv, String listening) {
			long now = System.currentTimeMillis();
			if (lv > 0.0005F) {
				quietSince = now;
				if (warned) {
					warned = false;
					status = listening;
				}
			} else if (!warned && now - quietSince > 6000) {
				warned = true;
				status = MAC ? "Your microphone sends only silence - allow it for your Minecraft launcher: System Settings > Privacy & Security > Microphone"
						: "Your microphone sends only silence - check the input device / mute switch";
			}
		}
	}

	private static void run(int lang, int g) {
		Object rec = null;
		TargetDataLine line = null;
		try {
			if (!modelInstalled(lang)) {
				download(lang);
				if (!live(g)) {
					return;
				}
			}
			// macOS: the Vosk jar keeps its library where the JNA inside Minecraft no longer looks ("darwin/" instead of
			// "darwin-aarch64/"), so on a Mac the engine "was not available". Put a working libvosk.dylib in place first.
			if (!prepareNative()) {
				return;
			}
			status = "Loading voice model...";
			Engine r;
			try {
				r = macLib != null ? macEngine(macLib, dir().resolve(MODEL[lang]).toString()) : voskEngine(dir().resolve(MODEL[lang]).toString());
			} catch (java.io.IOException bad) {
				// a broken / half model: remove it so the next start downloads a good one
				deleteTree(dir().resolve(MODEL[lang]));
				status = "The voice model was broken - press Restart to download it again";
				return;
			}
			rec = r;
			AudioFormat fmt = new AudioFormat(16000F, 16, 1, true, false);
			line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
			line.open(fmt, 8192);
			line.start();
			String listening = "Listening for \"" + phrase + "\"" + (appleProblem.isEmpty() ? "" : " (offline engine: " + appleProblem + ")");
			status = listening;
			byte[] buf = new byte[3200]; // 0.1 s
			String lastPartial = "";
			Silence quiet = new Silence();
			while (live(g)) {
				int n = line.read(buf, 0, buf.length);
				if (n <= 0) {
					continue;
				}
				level = rms(buf, n);
				quiet.update(level, listening);
				if (r.accept(buf, n)) {
					String text = field(r.result(), "text");
					if (!text.isEmpty()) {
						heard = text;
						check(text);
					}
					lastPartial = "";
				} else {
					String p = field(r.partial(), "partial");
					if (!p.isEmpty() && !p.equals(lastPartial)) {
						lastPartial = p;
						heard = p;
						if (check(p)) {
							r.reset(); // do not fire twice for the same sentence
							lastPartial = "";
						}
					}
				}
			}
		} catch (Throwable t) {
			status = t instanceof UnsatisfiedLinkError || t instanceof NoClassDefFoundError
					? "Voice engine could not start (" + (firstError != null ? firstError : t.getMessage()) + ") - restart Minecraft once"
					: t instanceof javax.sound.sampled.LineUnavailableException || t instanceof IllegalArgumentException
						? "No microphone (Mac: System Settings > Privacy > Microphone > allow the launcher)"
						: t instanceof java.io.IOException ? "Could not download the voice model: " + t.getMessage() + " (press Restart)"
								: "Voice error: " + t.getClass().getSimpleName() + " " + t.getMessage();
			System.err.println("[Caos] Voice trigger: " + t);
		} finally {
			if (gen == g) {
				running = false;
				level = 0;
			}
			if (line != null) {
				line.stop();
				line.close();
			}
			try {
				if (rec instanceof AutoCloseable c) {
					c.close();
				}
			} catch (Exception ignored) {
			}
			if (status.startsWith("Listening")) {
				status = "Off";
			}
		}
	}

	/** One speech recognizer and its model (closing it frees both). */
	private interface Engine extends AutoCloseable {
		boolean accept(byte[] data, int n);

		String result();

		String partial();

		void reset();

		@Override
		void close();
	}

	/** Windows / Linux: Vosk's own Java classes (they unpack the library and its DLLs). */
	private static Engine voskEngine(String modelDir) throws java.io.IOException {
		try {
			org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS);
		} catch (Throwable t) {
			firstError = t.toString(); // the real reason: later tries only say "Could not initialize class"
			throw t;
		}
		org.vosk.Model m = new org.vosk.Model(modelDir);
		org.vosk.Recognizer r = new org.vosk.Recognizer(m, 16000F);
		return new Engine() {
			public boolean accept(byte[] data, int n) {
				return r.acceptWaveForm(data, n);
			}

			public String result() {
				return r.getResult();
			}

			public String partial() {
				return r.getPartialResult();
			}

			public void reset() {
				r.reset();
			}

			public void close() {
				r.close();
				m.close();
			}
		};
	}

	/** macOS: the library through {@link VoskMac} (only the functions used here, so an older libvosk works too). */
	private static Engine macEngine(String lib, String modelDir) throws java.io.IOException {
		VoskMac.Api api;
		try {
			api = VoskMac.api(lib);
			api.vosk_set_log_level(-1);
		} catch (Throwable t) {
			firstError = t.toString();
			throw t;
		}
		com.sun.jna.Pointer m = api.vosk_model_new(modelDir);
		if (m == null) {
			throw new java.io.IOException("Failed to create a model");
		}
		com.sun.jna.Pointer r = api.vosk_recognizer_new(m, 16000F);
		if (r == null) {
			api.vosk_model_free(m);
			throw new java.io.IOException("Failed to create a recognizer");
		}
		return new Engine() {
			private boolean closed;

			public boolean accept(byte[] data, int n) {
				return api.vosk_recognizer_accept_waveform(r, data, n) != 0;
			}

			public String result() {
				return api.vosk_recognizer_result(r);
			}

			public String partial() {
				return api.vosk_recognizer_partial_result(r);
			}

			public void reset() {
				api.vosk_recognizer_reset(r);
			}

			public void close() {
				if (!closed) {
					closed = true;
					api.vosk_recognizer_free(r);
					api.vosk_model_free(m);
				}
			}
		};
	}

	private static float rms(byte[] b, int n) {
		long sum = 0;
		int c = n / 2;
		for (int i = 0; i + 1 < n; i += 2) {
			int s = (short) ((b[i] & 255) | b[i + 1] << 8);
			sum += (long) s * s;
		}
		return c == 0 ? 0 : (float) Math.min(1.0, Math.sqrt(sum / (double) c) / 6000.0);
	}

	private static String field(String json, String key) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			return o.has(key) ? o.get(key).getAsString().trim() : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean check(String text) {
		if (matches(text, phrase, strictness)) {
			long now = System.currentTimeMillis();
			if (now - lastFire > 2500) {
				lastFire = now;
				Runnable t = onTrigger;
				if (t != null) {
					t.run();
				}
			}
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ fuzzy matching (pure Java, unit-testable)
	static String norm(String s) {
		String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
	}

	/**
	 * True when 'heard' contains something close enough to 'phrase'. strict 50..95 = how similar it must be (%).
	 * Compares the phrase with every window of the same number of words in what was heard (letters only).
	 */
	public static boolean matches(String heard, String phrase, int strict) {
		return matchesNorm(norm(heard), norm(phrase), strict) || matchesNorm(sound(norm(heard)), sound(norm(phrase)), strict);
	}

	/** How a word sounds, roughly (Portuguese and English): "jarvis" / "jarbis" / "xarvis" all become "xarbis". */
	static String sound(String s) {
		String t = " " + s + " ";
		t = t.replace("ch", "x").replace("sh", "x").replace("j", "x").replace("g", "x").replace("ph", "f").replace("qu", "k")
				.replace("ck", "k").replace("c", "k").replace("v", "b").replace("w", "u").replace("y", "i").replace("z", "s")
				.replace("h", "").replace("ss", "s").replace("e ", "i ").replace("o ", "u ");
		t = t.replaceAll("(.)\\1+", "$1");
		return t.trim();
	}

	private static boolean matchesNorm(String h, String p, int strict) {
		if (p.isEmpty() || h.isEmpty()) {
			return false;
		}
		if (h.contains(p)) {
			return true;
		}
		String[] pw = p.split(" "), hw = h.split(" ");
		String pj = String.join("", pw);
		double need = strict / 100.0;
		for (int len = Math.max(1, pw.length - 1); len <= pw.length + 1; len++) {
			for (int i = 0; i + len <= hw.length; i++) {
				StringBuilder sb = new StringBuilder();
				for (int k = i; k < i + len; k++) {
					sb.append(hw[k]);
				}
				String w = sb.toString();
				double sim = 1.0 - lev(w, pj) / (double) Math.max(w.length(), pj.length());
				if (sim >= need) {
					return true;
				}
			}
		}
		return false;
	}

	static int lev(String a, String b) {
		int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}

	// ------------------------------------------------------------------ native library (macOS)
	private static final String OSX_LIB = "https://github.com/alphacep/vosk-api/releases/download/v0.3.42/vosk-osx-0.3.42.zip";
	private static boolean nativeReady;
	/** macOS: the absolute path of the libvosk.dylib to load (null on other systems). */
	private static volatile String macLib;
	private static volatile String firstError;

	/**
	 * Makes sure JNA finds libvosk before any org.vosk class loads (a failed load can't be retried without a restart).
	 * Mac: uses the universal library from Vosk's own release (works on Apple Silicon and Intel), downloaded once into
	 * kubo/voice/native. Other systems: nothing to do.
	 */
	private static boolean prepareNative() {
		if (nativeReady) {
			return true;
		}
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (!os.contains("mac")) {
			nativeReady = true;
			return true;
		}
		try {
			Path nat = dir().resolve("native");
			Path lib = nat.resolve("libvosk.dylib");
			boolean arm = System.getProperty("os.arch", "").contains("aarch64");
			if (!Files.isRegularFile(lib) || Files.size(lib) < 1_000_000 || !archOk(lib, arm)) {
				// a library of the wrong kind (an Intel-only copy on an Apple Silicon Mac) is what made the engine "not start"
				Files.deleteIfExists(lib);
				Files.createDirectories(nat);
				String arch = arm ? "darwin-aarch64" : "darwin-x86-64";
				// 1) the copy inside the jar, if this Vosk version has one for this Mac
				for (String res : new String[] {"/" + arch + "/libvosk.dylib", "/darwin/libvosk.dylib"}) {
					try (InputStream in = VoiceTrigger.class.getResourceAsStream(res)) {
						if (in != null) {
							Files.copy(in, lib, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
							if (archOk(lib, arm)) {
								break;
							}
						}
					}
				}
				// 2) the universal one from Vosk's release (the file inside is called libvosk.dyld)
				if (!Files.isRegularFile(lib) || !archOk(lib, arm)) {
					Files.deleteIfExists(lib);
					status = "Downloading the voice engine for Mac...";
					Path zip = nat.resolve("vosk-osx.zip.part");
					HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
					HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(OSX_LIB)).header("User-Agent", "CaosClient").timeout(Duration.ofMinutes(5)).build(),
							HttpResponse.BodyHandlers.ofFile(zip));
					if (r.statusCode() != 200) {
						throw new java.io.IOException("HTTP " + r.statusCode());
					}
					try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
						ZipEntry e;
						while ((e = zin.getNextEntry()) != null) {
							String n = e.getName().toLowerCase(Locale.ROOT);
							if (!e.isDirectory() && n.contains("libvosk") && (n.endsWith(".dylib") || n.endsWith(".dyld"))) {
								Files.copy(zin, lib, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
							}
						}
					}
					Files.deleteIfExists(zip);
					if (!Files.isRegularFile(lib) || !archOk(lib, arm)) {
						throw new java.io.IOException("no voice engine for this Mac (" + System.getProperty("os.arch") + ")");
					}
				}
			}
			String cur = System.getProperty("jna.library.path", "");
			String abs = nat.toAbsolutePath().toString();
			if (!cur.contains(abs)) {
				System.setProperty("jna.library.path", cur.isEmpty() ? abs : abs + java.io.File.pathSeparator + cur);
			}
			try {
				com.sun.jna.NativeLibrary.addSearchPath("vosk", abs); // also for a JNA that already read the property
			} catch (Throwable ignored) {
				// the property is enough on most setups
			}
			macLib = lib.toAbsolutePath().toString();
			nativeReady = true;
			return true;
		} catch (Exception e) {
			status = "Could not get the voice engine for Mac: " + e.getMessage() + " (press Restart)";
			System.err.println("[Caos] vosk native: " + e);
			return false;
		}
	}

	/** Is this .dylib usable on this Mac? A universal ("fat") file always is; a single-architecture one must match. */
	private static boolean archOk(Path lib, boolean arm) {
		try (InputStream in = Files.newInputStream(lib)) {
			byte[] h = in.readNBytes(8);
			if (h.length < 8) {
				return false;
			}
			int m = (h[0] & 255) << 24 | (h[1] & 255) << 16 | (h[2] & 255) << 8 | (h[3] & 255);
			if (m == 0xCAFEBABE || m == 0xBEBAFECA) {
				return true; // universal
			}
			if (m == 0xCFFAEDFE) { // 64 bit, little endian: the CPU type follows
				int cpu = (h[4] & 255) | (h[5] & 255) << 8 | (h[6] & 255) << 16 | (h[7] & 255) << 24;
				return cpu == (arm ? 0x0100000C : 0x01000007);
			}
			return false;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	// ------------------------------------------------------------------ model download
	/** Where the model can come from (the official site first, then a mirror). */
	private static String[] urls(String name) {
		String lang = name.contains("-pt-") ? "pt" : "en";
		return new String[] {"https://alphacephei.com/vosk/models/" + name + ".zip",
				"https://huggingface.co/rhasspy/vosk-models/resolve/main/" + lang + "/" + name + ".zip"};
	}

	/**
	 * Downloads the model to a temporary file first (real % from the download size), checks it is complete, unpacks it
	 * into a temporary folder and only then moves it into place - an interrupted download never leaves a broken model.
	 */
	private static void download(int lang) throws Exception {
		String name = MODEL[lang];
		Files.createDirectories(dir());
		Path zip = dir().resolve(name + ".zip.part");
		Exception last = null;
		boolean ok = false;
		for (String url : urls(name)) {
			try {
				fetch(url, zip);
				ok = true;
				break;
			} catch (Exception e) {
				last = e;
				System.err.println("[Caos] voice model from " + url + ": " + e);
				if (!running && !external) {
					Files.deleteIfExists(zip);
					return;
				}
			}
		}
		if (!ok) {
			Files.deleteIfExists(zip);
			throw last != null ? last : new java.io.IOException("download failed");
		}
		status = "Unpacking voice model...";
		Path tmp = dir().resolve(name + ".unpack");
		deleteTree(tmp);
		Files.createDirectories(tmp);
		Path base = tmp.toAbsolutePath().normalize();
		try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip), 1 << 16))) {
			ZipEntry e;
			byte[] b = new byte[1 << 16];
			while ((e = zin.getNextEntry()) != null) {
				Path out = base.resolve(e.getName()).normalize();
				if (!out.startsWith(base)) {
					continue; // never write outside the folder
				}
				if (e.isDirectory()) {
					Files.createDirectories(out);
					continue;
				}
				Files.createDirectories(out.getParent());
				try (OutputStream o = Files.newOutputStream(out)) {
					int n;
					while ((n = zin.read(b)) > 0) {
						o.write(b, 0, n);
					}
				}
			}
		}
		// the zip has one folder named like the model inside
		Path inner = tmp.resolve(name);
		if (!Files.isDirectory(inner)) {
			try (var st = Files.list(tmp)) {
				inner = st.filter(Files::isDirectory).findFirst().orElse(tmp);
			}
		}
		Path dest = dir().resolve(name);
		deleteTree(dest);
		Files.move(inner, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		Files.writeString(dest.resolve(".ok"), "ok");
		deleteTree(tmp);
		Files.deleteIfExists(zip);
	}

	/** True while Verity's "Install ears" button downloads the model (the download is not stopped because the voice trigger is off). */
	private static volatile boolean external;

	/** Verity's ears: downloads the model like the voice trigger does and prepares the engine. Blocks: call it off the game thread. */
	public static void installModelForVerity(int lang) throws Exception {
		external = true;
		try {
			download(Math.max(0, Math.min(MODEL.length - 1, lang)));
			if (!prepareNative()) {
				throw new java.io.IOException(appleProblem.isEmpty() ? "speech engine not available" : appleProblem);
			}
		} finally {
			external = false;
		}
	}

	private static Engine earsEngine;
	private static int earsLang = -1;

	/**
	 * Verity's ears: turns one sentence (16-bit mono samples at any rate) into text with the offline model. Blocks: call it off the
	 * game thread.
	 */
	public static synchronized String transcribe(short[] pcm, int n, int rate, int lang) throws Exception {
		int l = Math.max(0, Math.min(MODEL.length - 1, lang));
		if (!modelInstalled(l)) {
			throw new java.io.IOException("the ears are not installed");
		}
		if (!prepareNative()) {
			throw new java.io.IOException("speech engine not available");
		}
		if (earsEngine == null || earsLang != l) {
			if (earsEngine != null) {
				earsEngine.close();
				earsEngine = null;
			}
			String md = dir().resolve(MODEL[l]).toString();
			earsEngine = macLib != null ? macEngine(macLib, md) : voskEngine(md);
			earsLang = l;
		}
		// to 16 kHz, then a second of silence so the recognizer closes the sentence
		int outN = (int) ((long) n * 16000 / Math.max(8000, rate));
		byte[] b = new byte[(outN + 16000) * 2];
		for (int i = 0; i < outN; i++) {
			double src = i * (double) rate / 16000.0;
			int i0 = Math.min(n - 1, (int) src), i1 = Math.min(n - 1, i0 + 1);
			double f = src - i0;
			short v = (short) (pcm[i0] * (1 - f) + pcm[i1] * f);
			b[i * 2] = (byte) v;
			b[i * 2 + 1] = (byte) (v >> 8);
		}
		earsEngine.reset();
		StringBuilder text = new StringBuilder();
		for (int off = 0; off < b.length; off += 4000) {
			int c = Math.min(4000, b.length - off);
			byte[] chunk = java.util.Arrays.copyOfRange(b, off, off + c);
			if (earsEngine.accept(chunk, c)) {
				text.append(' ').append(textOf(earsEngine.result()));
			}
		}
		return text.toString().trim();
	}

	private static String textOf(String json) {
		try {
			var o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
			return o.has("text") ? o.get("text").getAsString() : "";
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static void fetch(String url, Path to) throws Exception {
		status = "Downloading voice model...";
		HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
		HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "CaosClient").timeout(Duration.ofMinutes(15)).build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (r.statusCode() != 200) {
			r.body().close();
			throw new java.io.IOException("HTTP " + r.statusCode());
		}
		long total = r.headers().firstValueAsLong("content-length").orElse(-1);
		long read = 0;
		try (InputStream in = new BufferedInputStream(r.body(), 1 << 16); OutputStream o = Files.newOutputStream(to)) {
			byte[] b = new byte[1 << 16];
			int n;
			while ((n = in.read(b)) > 0) {
				o.write(b, 0, n);
				read += n;
				status = total > 0 ? "Downloading voice model... " + Math.min(99, read * 100 / total) + "%" : "Downloading voice model... " + read / 1_000_000 + " MB";
				if (!running && !external) {
					throw new java.io.IOException("stopped");
				}
			}
		}
		if (total > 0 && read < total) {
			throw new java.io.IOException("download cut at " + read + " / " + total);
		}
		if (read < 1_000_000) {
			throw new java.io.IOException("file too small");
		}
	}

	private static void deleteTree(Path p) throws java.io.IOException {
		if (!Files.exists(p)) {
			return;
		}
		try (var st = Files.walk(p)) {
			st.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
				try {
					Files.deleteIfExists(x);
				} catch (java.io.IOException ignored) {
				}
			});
		}
	}
}
