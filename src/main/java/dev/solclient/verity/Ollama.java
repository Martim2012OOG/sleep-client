package dev.solclient.verity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Lang;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Verity's brain, 100% on this computer, like the original mod: Ollama runs a small language model. The Install button in
 * Verity's settings downloads the Ollama engine (unless it is already installed) and the model once, into
 * .minecraft/kubo/verity. No account, no key, nothing is sent anywhere.
 */
final class Ollama {

	static final String DEFAULT_MODEL = "qwen2.5:3b";
	private static final int OWN_PORT = 11437; // not 11434, so it never clashes with an Ollama the player runs himself

	private static volatile String status = "";
	private static volatile boolean busy;
	private static volatile String base;
	private static Process proc;
	private static boolean hooked;
	private static long checkedAt;
	private static boolean readyCache;

	private Ollama() {
	}

	/** One line for the settings page. */
	static String status() {
		if (busy || !status.isEmpty()) {
			return status;
		}
		return ready() ? Lang.tr("Ready", "Pronto") : Lang.tr("Not installed: press Install (one download, then it works offline)",
				"Não instalado: carrega em Instalar (uma só descarga, depois funciona sem internet)");
	}

	/** The engine and the model are installed. Cheap: safe to call every frame. */
	static boolean ready() {
		long now = System.currentTimeMillis();
		if (now - checkedAt > 2500L) {
			checkedAt = now;
			readyCache = Files.exists(marker()) && binary() != null;
		}
		return readyCache;
	}

	private static Path marker() {
		return Net.root().resolve("brain.ok");
	}

	private static String modelName() {
		VerityMod m = VerityMod.instance;
		return normalize(m == null ? "" : m.model.get());
	}

	/** A model name Ollama knows: an old Groq name left in the config ("llama-3.3-70b-versatile", "openai/gpt-oss-20b") becomes the default. */
	static String normalize(String s) {
		String n = s == null ? "" : s.trim();
		return n.isEmpty() || n.contains("versatile") || n.contains("instant") || n.contains("gpt-oss") || n.contains("/") || n.startsWith("llama-") || n.startsWith("gemma2-") ? DEFAULT_MODEL : n;
	}

	/** The ollama program: the one we downloaded, or one the player already installed. */
	static Path binary() {
		Path own = Net.find(Net.root().resolve("ollama"), "ollama", "ollama.exe");
		if (own != null) {
			return own;
		}
		List<String> c = new ArrayList<>();
		if (Net.mac()) {
			c.add("/Applications/Ollama.app/Contents/Resources/ollama");
			c.add("/usr/local/bin/ollama");
			c.add("/opt/homebrew/bin/ollama");
		} else if (Net.windows()) {
			String la = System.getenv("LOCALAPPDATA");
			if (la != null) {
				c.add(la + "\\Programs\\Ollama\\ollama.exe");
			}
		} else {
			c.add("/usr/local/bin/ollama");
			c.add("/usr/bin/ollama");
		}
		for (String s : c) {
			Path p = Path.of(s);
			if (Files.isRegularFile(p) && Files.isExecutable(p)) {
				return p;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ install (button)
	static void install() {
		if (busy) {
			return;
		}
		busy = true;
		status = Lang.tr("Starting...", "A começar...");
		Thread t = new Thread(() -> {
			try {
				Path bin = binary();
				if (bin == null) {
					downloadEngine();
					bin = binary();
					if (bin == null) {
						throw new IOException(Lang.tr("the engine was not found after the download", "o motor não foi encontrado depois da descarga"));
					}
				}
				bin.toFile().setExecutable(true, false);
				status = Lang.tr("Starting the engine...", "A ligar o motor...");
				if (!ensureServer()) {
					throw new IOException(Lang.tr("the engine did not start (see kubo/verity/ollama.log)", "o motor não arrancou (vê kubo/verity/ollama.log)"));
				}
				String model = modelName();
				if (!hasModel(model)) {
					pull(model);
				}
				Files.createDirectories(Net.root());
				Files.writeString(marker(), model);
				checkedAt = 0;
				status = "";
			} catch (Exception e) {
				status = Lang.tr("Failed: ", "Falhou: ") + e.getMessage();
			} finally {
				busy = false;
			}
		}, "kubo-verity-install");
		t.setDaemon(true);
		t.start();
	}

	private static void downloadEngine() throws Exception {
		status = Lang.tr("Looking for the engine...", "A procurar o motor...");
		JsonObject rel = Net.json("https://api.github.com/repos/ollama/ollama/releases/latest").getAsJsonObject();
		String want = Net.mac() ? "darwin" : Net.windows() ? (Net.arm() ? "windows-arm64" : "windows-amd64") : (Net.arm() ? "linux-arm64" : "linux-amd64");
		String url = null, name = null;
		for (int pass = 0; pass < 2 && url == null; pass++) {
			for (JsonElement e : rel.getAsJsonArray("assets")) {
				JsonObject a = e.getAsJsonObject();
				String n = a.get("name").getAsString().toLowerCase(java.util.Locale.ROOT);
				boolean tar = n.endsWith(".tgz") || n.endsWith(".tar.gz");
				if (!n.contains(want) || n.contains("rocm") || n.contains("jetpack") || !(pass == 0 ? tar : n.endsWith(".zip"))) {
					continue;
				}
				url = a.get("browser_download_url").getAsString();
				name = n;
				break;
			}
		}
		if (url == null) {
			throw new IOException(Lang.tr("no download for this computer: install Ollama from ollama.com and press Install again",
					"sem descarga para este computador: instala o Ollama em ollama.com e carrega em Instalar outra vez"));
		}
		Path dir = Net.root().resolve("ollama");
		Path file = Net.root().resolve("download").resolve(name);
		Net.download(url, file, Lang.tr("Downloading the engine", "A descarregar o motor"), s -> status = s);
		status = Lang.tr("Unpacking the engine...", "A desempacotar o motor...");
		Net.deleteTree(dir);
		Net.unpack(file, dir);
		Files.deleteIfExists(file);
		Path bin = Net.find(dir, "ollama", "ollama.exe");
		if (bin != null) {
			bin.toFile().setExecutable(true, false);
		}
	}

	// ------------------------------------------------------------------ the server
	private static boolean alive(String b) {
		try {
			HttpRequest r = HttpRequest.newBuilder(URI.create(b + "/api/version")).timeout(Duration.ofSeconds(2)).build();
			return Net.HTTP.send(r, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	/** Makes sure a server answers (an Ollama the player already runs, or our own). Blocks up to ~30 s: call it off the game thread. */
	private static synchronized boolean ensureServer() {
		if (base != null && alive(base)) {
			return true;
		}
		if (alive("http://127.0.0.1:11434")) {
			base = "http://127.0.0.1:11434";
			return true;
		}
		String own = "http://127.0.0.1:" + OWN_PORT;
		if (alive(own)) {
			base = own;
			return true;
		}
		Path bin = binary();
		if (bin == null) {
			return false;
		}
		try {
			Files.createDirectories(Net.root());
			ProcessBuilder pb = new ProcessBuilder(bin.toString(), "serve");
			pb.directory(bin.getParent().toFile());
			pb.environment().put("OLLAMA_HOST", "127.0.0.1:" + OWN_PORT);
			pb.environment().put("OLLAMA_MODELS", Net.root().resolve("ollama-models").toString());
			pb.redirectErrorStream(true);
			pb.redirectOutput(Net.root().resolve("ollama.log").toFile());
			proc = pb.start();
			if (!hooked) {
				hooked = true;
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					Process p = proc;
					if (p != null) {
						p.destroy();
					}
				}));
			}
		} catch (IOException e) {
			status = Lang.tr("Failed: ", "Falhou: ") + e.getMessage();
			return false;
		}
		for (int i = 0; i < 60; i++) {
			if (alive(own)) {
				base = own;
				return true;
			}
			if (!proc.isAlive()) {
				return false;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private static boolean hasModel(String model) throws Exception {
		JsonObject o = Net.json(base + "/api/tags").getAsJsonObject();
		if (!o.has("models") || !o.get("models").isJsonArray()) {
			return false;
		}
		for (JsonElement e : o.getAsJsonArray("models")) {
			String n = e.getAsJsonObject().get("name").getAsString();
			if (n.equalsIgnoreCase(model) || !model.contains(":") && n.equalsIgnoreCase(model + ":latest")) {
				return true;
			}
		}
		return false;
	}

	private static void pull(String model) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("name", model);
		body.addProperty("stream", true);
		HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/pull")).timeout(Duration.ofHours(3)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		HttpResponse<java.io.InputStream> res = Net.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() != 200) {
			String msg = new String(res.body().readAllBytes(), StandardCharsets.UTF_8);
			throw new IOException("HTTP " + res.statusCode() + " " + (msg.length() > 120 ? msg.substring(0, 120) : msg));
		}
		String label = Lang.tr("Downloading the brain (" + model + ")... ", "A descarregar o cérebro (" + model + ")... ");
		status = label;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				JsonObject o = JsonParser.parseString(line).getAsJsonObject();
				if (o.has("error")) {
					throw new IOException(o.get("error").getAsString());
				}
				if (o.has("total") && o.has("completed") && o.get("total").getAsLong() > 0) {
					status = label + (o.get("completed").getAsLong() * 100 / o.get("total").getAsLong()) + "%";
				} else if (o.has("status")) {
					status = label + o.get("status").getAsString();
				}
			}
		}
	}

	// ------------------------------------------------------------------ talking
	/** What the model answered (a JSON string, see VerityAI.systemPrompt). Blocks: call it off the game thread. */
	static String chat(String model, JsonArray messages) throws Exception {
		if (!ensureServer()) {
			throw new IllegalStateException(Lang.tr("Verity's brain is not installed: Locker > Verity > Edit > Install",
					"O cérebro do Verity não está instalado: Locker > Verity > Editar > Instalar"));
		}
		JsonObject body = new JsonObject();
		body.addProperty("model", normalize(model));
		body.add("messages", messages);
		body.addProperty("stream", false);
		body.addProperty("format", "json");
		body.addProperty("keep_alive", "20m");
		JsonObject opt = new JsonObject();
		opt.addProperty("temperature", 0.9);
		opt.addProperty("num_predict", 220);
		body.add("options", opt);
		HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/chat")).timeout(Duration.ofSeconds(180)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		HttpResponse<String> res = Net.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() == 404) {
			throw new IllegalStateException(Lang.tr("That model is not installed: press Install in Verity's settings", "Esse modelo não está instalado: carrega em Instalar nas definições do Verity"));
		}
		if (res.statusCode() != 200) {
			String m = res.body() == null ? "" : res.body();
			throw new IllegalStateException("Ollama " + res.statusCode() + ": " + (m.length() > 120 ? m.substring(0, 120) : m));
		}
		return JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
	}
}
