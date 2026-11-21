package dev.sleepclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic translation of the menus for every language except English and Portuguese (Portugal, which has its own
 * hand-made file). Nothing has to be written per language: the first time a text is shown in, say, German, it is
 * shown in English for a moment, sent off to be translated (in batches, in the background) and saved in
 * config/kubo/lang/de.tsv, so the next time it is instant and works offline.
 *
 * Where it translates: first the Sleep website (POST /translate, see LOVABLE-PROMPT-TRANSLATE.md); if the site does not
 * have it yet, Google's public translate endpoint (the same one the Chat Translator extension uses).
 * A file with the same name in assets/sleep_client/lang/ (e.g. de.tsv) is read first, so ready-made translations can
 * also ship inside the jar.
 */
public final class AutoTranslate {

	private static final Map<String, Map<String, String>> TABLES = new ConcurrentHashMap<>();
	/** Every translation we have, per language: a text that already is a translation is not translated again. */
	private static final Map<String, Set<String>> OUTPUTS = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
	private static final Map<String, Integer> FAILS = new ConcurrentHashMap<>();
	private static final LinkedBlockingQueue<String[]> QUEUE = new LinkedBlockingQueue<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[sdf]");
	private static volatile boolean siteMissing;
	private static Thread worker;
	/** What the worker is doing (shown under Settings > Language). */
	public static volatile String status = "";

	private AutoTranslate() {
	}

	/** The translation, or the English text while it is being translated. */
	public static String get(String code, String s) {
		String v = table(code).get(s);
		if (v != null) {
			return v;
		}
		if (outputs(code).contains(s)) {
			return s; // already in this language (a text built with Lang.tr / Lang.t before being drawn)
		}
		if (worth(s) && PENDING.add(code + "\u0001" + s)) {
			QUEUE.add(new String[] {code, s});
			startWorker();
		}
		return s;
	}

	/** Forgets this language's translations (file too): everything is translated again as it shows up. */
	public static void reset(String code) {
		TABLES.remove(code);
		OUTPUTS.remove(code);
		PENDING.removeIf(k -> k.startsWith(code + "\u0001"));
		FAILS.keySet().removeIf(k -> k.startsWith(code + "\u0001"));
		try {
			Files.deleteIfExists(file(code));
		} catch (Exception ignored) {
		}
		Lang.changed();
	}

	/** How many texts of this language are translated already (for the settings line). */
	public static int count(String code) {
		return table(code).size();
	}

	public static int waiting() {
		return QUEUE.size();
	}

	private static boolean worth(String s) {
		if (s.length() > 500 || s.startsWith("http") || s.contains("://")) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (Character.isLetter(s.charAt(i))) {
				return s.length() > 1; // single letters (keys, initials) stay as they are
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ files
	private static Set<String> outputs(String code) {
		return OUTPUTS.computeIfAbsent(code, c -> {
			Set<String> set = ConcurrentHashMap.newKeySet();
			set.addAll(table(c).values());
			return set;
		});
	}

	private static Path file(String code) {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("lang").resolve(code + ".tsv");
	}

	private static Map<String, String> table(String code) {
		return TABLES.computeIfAbsent(code, c -> {
			Map<String, String> m = new ConcurrentHashMap<>();
			try (InputStream in = AutoTranslate.class.getResourceAsStream("/assets/sleep_client/lang/" + c + ".tsv")) {
				if (in != null) {
					read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), m);
				}
			} catch (Exception ignored) {
			}
			try {
				Path f = file(c);
				if (Files.exists(f)) {
					try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
						read(r, m);
					}
				}
			} catch (Exception e) {
				System.err.println("[Sleep] translations " + c + ": " + e);
			}
			return m;
		});
	}

	private static void read(BufferedReader r, Map<String, String> m) throws java.io.IOException {
		String line;
		while ((line = r.readLine()) != null) {
			int tab = line.indexOf('\t');
			if (tab > 0) {
				m.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
			}
		}
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
	}

	private static String unescape(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '\\' && i + 1 < s.length()) {
				char n = s.charAt(++i);
				b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
			} else {
				b.append(ch);
			}
		}
		return b.toString();
	}

	private static void save(String code, List<String> en, List<String> out) {
		try {
			Path f = file(code);
			Files.createDirectories(f.getParent());
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < en.size(); i++) {
				b.append(escape(en.get(i))).append('\t').append(escape(out.get(i))).append('\n');
			}
			Files.writeString(f, b.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception e) {
			System.err.println("[Sleep] could not save translations: " + e);
		}
	}

	// ------------------------------------------------------------------ worker
	private static synchronized void startWorker() {
		if (worker != null && worker.isAlive()) {
			return;
		}
		worker = new Thread(AutoTranslate::run, "kubo-translate");
		worker.setDaemon(true);
		worker.start();
	}

	private static void run() {
		while (true) {
			try {
				String[] first = QUEUE.poll(30, TimeUnit.SECONDS);
				if (first == null) {
					return; // nothing for a while: the thread stops, the next text starts it again
				}
				Thread.sleep(150); // let a whole screen's worth of texts queue up
				String code = first[0];
				List<String> batch = new ArrayList<>();
				batch.add(first[1]);
				int chars = first[1].length();
				for (var it = QUEUE.iterator(); it.hasNext() && batch.size() < 60 && chars < 3500;) {
					String[] e = it.next();
					if (e[0].equals(code)) {
						it.remove();
						batch.add(e[1]);
						chars += e[1].length();
					}
				}
				status = "Translating " + batch.size() + " texts...";
				List<String> out = translate(code, batch);
				Map<String, String> table = table(code);
				List<String> okEn = new ArrayList<>(), okOut = new ArrayList<>();
				for (int i = 0; i < batch.size(); i++) {
					String en = batch.get(i), tr = out == null ? null : out.get(i);
					if (tr != null && !tr.isBlank()) {
						table.put(en, tr);
						outputs(code).add(tr);
						okEn.add(en);
						okOut.add(tr);
						PENDING.remove(code + "\u0001" + en);
					} else {
						int f = FAILS.merge(code + "\u0001" + en, 1, Integer::sum);
						if (f >= 3) {
							table.put(en, en); // give up for this session: stays in English
						} else {
							PENDING.remove(code + "\u0001" + en); // asked again the next time it is shown
						}
					}
				}
				if (!okEn.isEmpty()) {
					save(code, okEn, okOut);
					Lang.changed();
				}
				status = out == null ? "No internet for translations - English for now" : "";
				if (out == null) {
					Thread.sleep(20_000); // offline: don't hammer
				}
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				status = "Translation error: " + t.getMessage();
				System.err.println("[Sleep] translate: " + t);
			}
		}
	}

	// ------------------------------------------------------------------ services
	/** One translation per input (null entries = failed), or null when nothing could be reached. */
	private static List<String> translate(String code, List<String> texts) {
		// keep %s / %1$s / %d as they are: swap them for {0}, {1}... while translating
		List<List<String>> fmt = new ArrayList<>();
		List<String> prot = new ArrayList<>();
		for (String t : texts) {
			List<String> found = new ArrayList<>();
			Matcher m = FORMAT.matcher(t);
			StringBuilder b = new StringBuilder();
			while (m.find()) {
				m.appendReplacement(b, "{" + found.size() + "}");
				found.add(m.group());
			}
			m.appendTail(b);
			fmt.add(found);
			prot.add(b.toString());
		}
		List<String> out = null;
		if (!siteMissing) {
			out = viaSite(code, prot);
		}
		if (out == null) {
			out = viaGoogle(code, prot);
		}
		if (out == null) {
			return null;
		}
		for (int i = 0; i < out.size(); i++) {
			String s = out.get(i);
			if (s == null) {
				continue;
			}
			List<String> f = fmt.get(i);
			for (int k = 0; k < f.size(); k++) {
				s = s.replace("{" + k + "}", f.get(k)).replace("{ " + k + " }", f.get(k));
			}
			// keep the English look of short labels: ALL CAPS stays all caps, a leading capital stays
			String en = texts.get(i);
			if (en.length() > 1 && en.equals(en.toUpperCase(java.util.Locale.ROOT)) && !en.equals(en.toLowerCase(java.util.Locale.ROOT))) {
				s = s.toUpperCase(java.util.Locale.ROOT);
			} else if (!en.isEmpty() && Character.isUpperCase(en.charAt(0)) && !s.isEmpty() && Character.isLowerCase(s.charAt(0))) {
				s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
			}
			out.set(i, s);
		}
		return out;
	}

	/** Sleep website: POST {to, from, texts[]} -> {texts[]} (same order). */
	private static List<String> viaSite(String code, List<String> texts) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("from", "en");
			body.addProperty("to", code);
			body.addProperty("context", "Labels and short texts of the menus of a Minecraft client. Keep them short.");
			JsonArray arr = new JsonArray();
			texts.forEach(arr::add);
			body.add("texts", arr);
			HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(dev.sleepclient.social.SolarApi.baseUrl() + "/translate"))
					.timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json").header("User-Agent", "Sleep-Client")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
			String key = dev.sleepclient.social.SolarApi.clientKeyValue();
			if (!key.isEmpty()) {
				b.header("X-Client-Key", key);
			}
			HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 404 || r.statusCode() == 405 || r.statusCode() == 501) {
				siteMissing = true;
				return null;
			}
			if (r.statusCode() / 100 != 2) {
				return null;
			}
			JsonArray res = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("texts");
			if (res == null || res.size() != texts.size()) {
				return null;
			}
			List<String> out = new ArrayList<>();
			for (JsonElement e : res) {
				out.add(e == null || e.isJsonNull() ? null : e.getAsString());
			}
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	private static List<String> viaGoogle(String code, List<String> texts) {
		String tl = code.equals("pt-BR") ? "pt" : code;
		List<String> joined = google(tl, String.join("\n", texts));
		if (joined != null && joined.size() == texts.size()) {
			return joined;
		}
		if (joined == null) {
			return null;
		}
		// the lines got merged / split: one by one instead
		List<String> out = new ArrayList<>();
		for (String t : texts) {
			List<String> one = google(tl, t);
			out.add(one == null || one.isEmpty() ? null : String.join(" ", one));
		}
		return out;
	}

	/** The translated text split back into lines, or null when it could not be reached. */
	private static List<String> google(String tl, String text) {
		try {
			String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=" + URLEncoder.encode(tl, StandardCharsets.UTF_8) + "&dt=t";
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
					.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8").header("User-Agent", "Mozilla/5.0")
					.POST(HttpRequest.BodyPublishers.ofString("q=" + URLEncoder.encode(text, StandardCharsets.UTF_8))).build();
			HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() / 100 != 2) {
				return null;
			}
			JsonArray segs = JsonParser.parseString(r.body()).getAsJsonArray().get(0).getAsJsonArray();
			StringBuilder b = new StringBuilder();
			for (JsonElement seg : segs) {
				JsonArray a = seg.getAsJsonArray();
				if (a.size() > 0 && !a.get(0).isJsonNull()) {
					b.append(a.get(0).getAsString());
				}
			}
			List<String> lines = new ArrayList<>();
			for (String l : b.toString().split("\n", -1)) {
				lines.add(l.strip());
			}
			while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			return lines;
		} catch (Exception e) {
			return null;
		}
	}
}
