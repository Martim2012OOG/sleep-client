package dev.sleepclient.fightcam;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * After a film is rendered: sound effects and an AI voice-over mixed into it. Everything runs on a background thread.
 * <ul>
 * <li>Sound effects: your own audio files (config/sleep/sfx) on every hit, on the kill and on the slow motion.</li>
 * <li>AI voice-over: a language model (Groq, ChatGPT, Gemini or Claude, with YOUR key) writes a few short lines about the fight, and
 * Fish Audio (YOUR key + the voice you chose) speaks them. Keys are kept only in config/sleep/film-ai.json on this computer and are
 * only sent to the service they belong to.</li>
 * </ul>
 */
public final class FilmEdit {

	public static final String[] PROVIDERS = {"Groq", "ChatGPT", "Gemini", "Claude"};
	public static final String[] LANGS = {"Portuguese", "English", "Spanish"};
	public static final String[] STYLES = {"Hype", "Chill", "Serious"};

	/** One thing that happened in the film (seconds counted in the video). */
	public record Ev(double sec, String kind, String a, String b) {
	}

	/** What is known about the last film. */
	public static final class Info {
		public File file;
		public int fps = 60;
		public double seconds;
		public final List<Ev> events = new ArrayList<>();
		public final List<double[]> slows = new ArrayList<>();
		public int startFrame;
	}

	/** The film that was just rendered (set by the film flow, read by the editor). */
	public static volatile Info last;

	/** Video seconds of a replay frame, counting the slow motion (the film advances by 1/fps of video time per picture). */
	public static double secOf(Info in, double frame) {
		double t = in.startFrame, sec = 0, step = 0.05;
		while (t < frame) {
			double f = 1;
			for (double[] r : in.slows) {
				if (t >= r[0] && t <= r[1]) {
					double k = Math.max(0, Math.min(1, Math.min(t - r[0], r[1] - t) / 10.0));
					f = Math.min(f, 1 + (r[2] - 1) * k);
				}
			}
			double dt = Math.min(step, frame - t);
			t += dt;
			sec += dt / (20.0 * f);
		}
		return sec;
	}

	// ------------------------------------------------------------------ settings
	public static final class Config {
		public int provider, lang, style;
		public String llmKey = "", llmModel = "", fishKey = "", voice = "";
		public boolean ai, realNames, sfxHit, sfxKill, sfxSlow;
		public String hitFile = "", killFile = "", slowFile = "";
	}

	private static final Gson GSON = new Gson();

	private static Path cfgFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("sleep").resolve("film-ai.json");
	}

	public static File sfxDir() {
		File d = FabricLoader.getInstance().getConfigDir().resolve("sleep").resolve("sfx").toFile();
		d.mkdirs();
		return d;
	}

	public static List<String> sfxFiles() {
		List<String> out = new ArrayList<>();
		File[] fs = sfxDir().listFiles((d, n) -> {
			String l = n.toLowerCase(Locale.ROOT);
			return l.endsWith(".mp3") || l.endsWith(".wav") || l.endsWith(".ogg") || l.endsWith(".m4a") || l.endsWith(".aac");
		});
		if (fs != null) {
			for (File f : fs) {
				out.add(f.getName());
			}
		}
		java.util.Collections.sort(out);
		return out;
	}

	public static Config load() {
		try {
			Path f = cfgFile();
			if (Files.isRegularFile(f)) {
				Config c = GSON.fromJson(Files.readString(f), Config.class);
				if (c != null) {
					return c;
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] film editor settings: " + e);
		}
		return new Config();
	}

	public static void save(Config c) {
		try {
			Path f = cfgFile();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(c));
		} catch (IOException e) {
			System.err.println("[Sleep] film editor settings not saved: " + e);
		}
	}

	// ------------------------------------------------------------------ AI
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private static String post(String url, String body, String... headers) throws IOException, InterruptedException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		for (int i = 0; i + 1 < headers.length; i += 2) {
			b.header(headers[i], headers[i + 1]);
		}
		HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() / 100 != 2) {
			String t = r.body() == null ? "" : r.body();
			throw new IOException("HTTP " + r.statusCode() + ": " + t.substring(0, Math.min(160, t.length())));
		}
		return r.body();
	}

	private static String ask(Config c, String system, String user) throws IOException, InterruptedException {
		String key = c.llmKey.trim();
		switch (c.provider) {
			case 2 -> { // Gemini
				JsonObject body = new JsonObject();
				JsonArray contents = new JsonArray();
				JsonObject msg = new JsonObject();
				JsonArray parts = new JsonArray();
				JsonObject part = new JsonObject();
				part.addProperty("text", system + "\n\n" + user);
				parts.add(part);
				msg.add("parts", parts);
				contents.add(msg);
				body.add("contents", contents);
				String model = c.llmModel.isBlank() ? "gemini-2.0-flash" : c.llmModel.trim();
				String out = post("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", GSON.toJson(body), "x-goog-api-key", key);
				return JsonParser.parseString(out).getAsJsonObject().getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content")
						.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
			}
			case 3 -> { // Claude
				JsonObject body = new JsonObject();
				body.addProperty("model", c.llmModel.isBlank() ? "claude-haiku-4-5" : c.llmModel.trim());
				body.addProperty("max_tokens", 900);
				body.addProperty("system", system);
				JsonArray msgs = new JsonArray();
				JsonObject m = new JsonObject();
				m.addProperty("role", "user");
				m.addProperty("content", user);
				msgs.add(m);
				body.add("messages", msgs);
				String out = post("https://api.anthropic.com/v1/messages", GSON.toJson(body), "x-api-key", key, "anthropic-version", "2023-06-01");
				return JsonParser.parseString(out).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
			}
			default -> { // Groq / ChatGPT: the same format
				boolean groq = c.provider == 0;
				JsonObject body = new JsonObject();
				body.addProperty("model", c.llmModel.isBlank() ? (groq ? "llama-3.3-70b-versatile" : "gpt-4o-mini") : c.llmModel.trim());
				body.addProperty("temperature", 0.9);
				JsonArray msgs = new JsonArray();
				JsonObject s = new JsonObject();
				s.addProperty("role", "system");
				s.addProperty("content", system);
				JsonObject u = new JsonObject();
				u.addProperty("role", "user");
				u.addProperty("content", user);
				msgs.add(s);
				msgs.add(u);
				body.add("messages", msgs);
				String url = groq ? "https://api.groq.com/openai/v1/chat/completions" : "https://api.openai.com/v1/chat/completions";
				String out = post(url, GSON.toJson(body), "Authorization", "Bearer " + key);
				return JsonParser.parseString(out).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
			}
		}
	}

	/** The voice id from a Fish Audio link (https://fish.audio/m/<id>) or the id itself. */
	public static String voiceId(String s) {
		if (s == null) {
			return "";
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9a-fA-F]{32}").matcher(s);
		return m.find() ? m.group().toLowerCase(Locale.ROOT) : s.trim();
	}

	private record Line(double sec, String text) {
	}

	private static String nameFor(Info in, Config c, String real, java.util.Map<String, String> alias) {
		if (c.realNames) {
			return real;
		}
		return alias.computeIfAbsent(real, k -> "Player " + (char) ('A' + alias.size()));
	}

	private static List<Line> writeScript(Info in, Config c) throws IOException, InterruptedException {
		java.util.Map<String, String> alias = new java.util.LinkedHashMap<>();
		StringBuilder ev = new StringBuilder();
		int hits = 0;
		for (Ev e : in.events) {
			if (hits > 40) {
				break;
			}
			if (e.kind().equals("hit")) {
				hits++;
			}
			ev.append(String.format(Locale.ROOT, "- %.1fs: %s %s %s%n", e.sec(), nameFor(in, c, e.a(), alias), e.kind().equals("kill") ? "KILLED" : e.kind().equals("slow") ? "(slow motion starts, victim:" : "hit",
					e.b() == null || e.b().isEmpty() ? "" : nameFor(in, c, e.b(), alias) + (e.kind().equals("slow") ? ")" : "")));
		}
		String system = "You write the voice-over for a short Minecraft PvP highlight video. Reply with ONLY a JSON array, no other text.";
		String user = "Video length: " + String.format(Locale.ROOT, "%.1f", in.seconds) + " seconds. Language: " + LANGS[c.lang] + ". Style: " + STYLES[c.style]
				+ ".\nWhat happens in the video (times in seconds):\n" + ev
				+ "\nWrite between 3 and 6 SHORT lines (max 12 words each) the narrator says, at least 2.5 s apart, the first at 0.5 s or later and the last"
				+ " ending before the video ends. React to hits and the kill. Output exactly: [{\"t\":1.2,\"text\":\"...\"}, ...]";
		String out = ask(c, system, user);
		int a = out.indexOf('['), b = out.lastIndexOf(']');
		if (a < 0 || b < a) {
			throw new IOException("the AI did not answer with a list");
		}
		List<Line> lines = new ArrayList<>();
		for (JsonElement el : JsonParser.parseString(out.substring(a, b + 1)).getAsJsonArray()) {
			JsonObject o = el.getAsJsonObject();
			double t = o.get("t").getAsDouble();
			String text = o.get("text").getAsString().trim();
			if (!text.isEmpty() && t >= 0 && t < in.seconds) {
				lines.add(new Line(t, text));
			}
		}
		if (lines.isEmpty()) {
			throw new IOException("the AI wrote no usable lines");
		}
		return lines;
	}

	/** Fish Audio text to speech: the mp3 bytes. */
	private static void speak(Config c, String text, File out) throws IOException, InterruptedException {
		JsonObject body = new JsonObject();
		body.addProperty("text", text);
		body.addProperty("reference_id", voiceId(c.voice));
		body.addProperty("format", "mp3");
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.fish.audio/v1/tts")).timeout(Duration.ofSeconds(90))
				.header("Authorization", "Bearer " + c.fishKey.trim()).header("Content-Type", "application/json").header("model", "s1")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)).build();
		HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() / 100 != 2) {
			String t = new String(r.body(), StandardCharsets.UTF_8);
			throw new IOException("Fish Audio HTTP " + r.statusCode() + ": " + t.substring(0, Math.min(140, t.length())));
		}
		Files.write(out.toPath(), r.body());
	}

	// ------------------------------------------------------------------ mixing
	private record Clip(File file, double sec, double volume) {
	}

	private static File sfx(String name) {
		return name == null || name.isBlank() ? null : new File(sfxDir(), name);
	}

	/**
	 * Builds the final video: the rendered film plus the sound effects and the voice-over. Returns the new file; the original is kept.
	 * progress gets short status texts. Throws with a readable message.
	 */
	public static File compose(Info in, Config c, Consumer<String> progress) throws IOException, InterruptedException {
		if (in == null || in.file == null || !in.file.isFile()) {
			throw new IOException("the film file is gone");
		}
		List<Clip> clips = new ArrayList<>();
		Path tmp = Files.createTempDirectory("sleep-film-edit");
		try {
			if (c.ai) {
				if (c.llmKey.isBlank() || c.fishKey.isBlank() || voiceId(c.voice).isEmpty()) {
					throw new IOException("AI voice needs the AI key, the Fish key and the voice link");
				}
				progress.accept("The AI is writing the commentary...");
				List<Line> lines = writeScript(in, c);
				int n = 0;
				for (Line l : lines) {
					progress.accept("Voice " + (++n) + " / " + lines.size() + "...");
					File f = tmp.resolve("voice" + n + ".mp3").toFile();
					speak(c, l.text(), f);
					clips.add(new Clip(f, l.sec(), 1.0));
				}
			}
			progress.accept("Adding the sound effects...");
			double lastHit = -9;
			for (Ev e : in.events) {
				File f = null;
				double vol = 0.8;
				switch (e.kind()) {
					case "hit" -> {
						if (c.sfxHit && sfx(c.hitFile) != null && e.sec() - lastHit > 0.25) {
							f = sfx(c.hitFile);
							lastHit = e.sec();
						}
					}
					case "kill" -> {
						if (c.sfxKill) {
							f = sfx(c.killFile);
							vol = 1.0;
						}
					}
					case "slow" -> {
						if (c.sfxSlow) {
							f = sfx(c.slowFile);
						}
					}
					default -> {
					}
				}
				if (f != null && f.isFile()) {
					clips.add(new Clip(f, Math.max(0, e.sec()), vol));
				}
			}
			if (clips.isEmpty()) {
				throw new IOException("nothing to add: turn on a sound effect or the AI voice");
			}
			progress.accept("Mixing...");
			File out = new File(in.file.getParentFile(), in.file.getName().replace(".mp4", "") + "_final.mp4");
			List<String> cmd = new ArrayList<>();
			if (!Ffmpeg.WINDOWS) {
				cmd.addAll(List.of("nice", "-n", "5"));
			}
			cmd.addAll(List.of(Ffmpeg.path, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", in.file.getAbsolutePath()));
			StringBuilder fg = new StringBuilder();
			StringBuilder mixIn = new StringBuilder();
			for (int i = 0; i < clips.size(); i++) {
				Clip cl = clips.get(i);
				cmd.addAll(List.of("-i", cl.file().getAbsolutePath()));
				long ms = Math.round(cl.sec() * 1000);
				fg.append(String.format(Locale.ROOT, "[%d:a]aresample=48000,aformat=channel_layouts=stereo,adelay=%d|%d,volume=%.2f[a%d];", i + 1, ms, ms, cl.volume(), i));
				mixIn.append("[a").append(i).append(']');
			}
			if (clips.size() == 1) {
				fg.append("[a0]anull[aout]");
			} else {
				fg.append(mixIn).append("amix=inputs=").append(clips.size()).append(":normalize=0:dropout_transition=0[aout]");
			}
			cmd.addAll(List.of("-filter_complex", fg.toString(), "-map", "0:v", "-map", "[aout]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-t",
					String.format(Locale.ROOT, "%.2f", in.seconds), "-movflags", "+faststart", out.getAbsolutePath()));
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (!p.waitFor(300, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				throw new IOException("ffmpeg took too long");
			}
			if (p.exitValue() != 0 || !out.isFile() || out.length() < 1000) {
				out.delete();
				throw new IOException("ffmpeg: " + log.substring(Math.max(0, log.length() - 160)));
			}
			return out;
		} finally {
			File[] fs = tmp.toFile().listFiles();
			if (fs != null) {
				for (File f : fs) {
					f.delete();
				}
			}
			tmp.toFile().delete();
		}
	}
}
