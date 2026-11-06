package dev.solclient.verity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.solclient.Lang;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Verity's voice, 100% on this computer: Piper (a small offline text-to-speech program) with a Portuguese (Portugal) or an
 * English voice. The Install button downloads Piper and the voice once into .minecraft/kubo/verity.
 */
final class Piper {

	private static final String RELEASE = "https://api.github.com/repos/rhasspy/piper/releases/tags/2023.11.14-2";
	private static final String VOICES = "https://huggingface.co/rhasspy/piper-voices/";

	private static volatile String status = "";
	private static volatile boolean busy;

	private Piper() {
	}

	static String status() {
		if (busy || !status.isEmpty()) {
			return status;
		}
		if (!original() && VerityLang.current() == null) {
			return Lang.tr("No Piper voice for this language: the computer's own voice speaks it", "Sem voz Piper para esta língua: fala com a voz do próprio computador");
		}
		return ready() ? Lang.tr("Ready", "Pronto") : Lang.tr("Not installed: press Install (one download)", "Não instalado: carrega em Instalar (uma só descarga)");
	}

	static Path binary() {
		return Net.find(Net.root().resolve("piper"), "piper", "piper.exe");
	}

	private static final String ORIGINAL = "en_US-ryan-medium";
	/** The voice of the original Verity mod (Piper, en_US ryan medium). */
	private static final VerityLang.L RYAN = new VerityLang.L("English", "English", "en/en_US/ryan/medium", ORIGINAL, 1);

	/** His original voice (the one of the Verity mod: Ryan, downloaded once); it speaks any language, with an English accent. */
	static boolean original() {
		VerityMod m = VerityMod.instance;
		return m == null || m.originalVoice.on();
	}

	private static Path voicePath() {
		if (original()) {
			return Net.root().resolve("voices").resolve(ORIGINAL + ".onnx");
		}
		VerityLang.L l = VerityLang.current();
		return l == null ? null : voice(l);
	}

	private static Path voice(VerityLang.L l) {
		return Net.root().resolve("voices").resolve(l.piperFile() + ".onnx");
	}

	/** Piper and the voice of the chosen language are installed (false for "Other": the computer's own voice speaks). */
	static boolean ready() {
		Path v = voicePath();
		if (v == null) {
			return false;
		}
		return binary() != null && Files.isRegularFile(v) && Files.isRegularFile(v.resolveSibling(v.getFileName() + ".json"));
	}

	static void install() {
		if (busy) {
			return;
		}
		boolean orig = original();
		VerityLang.L lang = VerityLang.current();
		if (!orig && lang == null) {
			status = Lang.tr("No Piper voice for this language: the computer's own voice speaks it", "Sem voz Piper para esta língua: fala com a voz do próprio computador");
			return;
		}
		busy = true;
		status = Lang.tr("Starting...", "A começar...");
		Thread t = new Thread(() -> {
			try {
				if (binary() == null) {
					downloadProgram();
				}
				Path v = voicePath();
				if (!Files.isRegularFile(v) || !Files.isRegularFile(v.resolveSibling(v.getFileName() + ".json"))) {
					downloadVoice(orig ? RYAN : lang);
				}
				status = "";
			} catch (Exception e) {
				status = Lang.tr("Failed: ", "Falhou: ") + e.getMessage();
			} finally {
				busy = false;
			}
		}, "kubo-verity-voice-install");
		t.setDaemon(true);
		t.start();
	}

	private static void downloadProgram() throws Exception {
		status = Lang.tr("Looking for the voice engine...", "A procurar o motor de voz...");
		JsonObject rel = Net.json(RELEASE).getAsJsonObject();
		String os = Net.mac() ? "macos" : Net.windows() ? "windows" : "linux";
		String arch = Net.windows() ? "amd64" : Net.arm() ? "aarch64" : Net.mac() ? "x64" : "x86_64";
		String url = null, name = null;
		for (JsonElement e : rel.getAsJsonArray("assets")) {
			JsonObject a = e.getAsJsonObject();
			String n = a.get("name").getAsString().toLowerCase(Locale.ROOT);
			if (n.contains(os) && n.contains(arch) && (n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".zip"))) {
				url = a.get("browser_download_url").getAsString();
				name = n;
				break;
			}
		}
		if (url == null) {
			throw new IOException(Lang.tr("no voice engine for this computer (the computer's own voice will be used)",
					"sem motor de voz para este computador (vai ser usada a voz do próprio computador)"));
		}
		Path file = Net.root().resolve("download").resolve(name);
		Net.download(url, file, Lang.tr("Downloading the voice engine", "A descarregar o motor de voz"), s -> status = s);
		status = Lang.tr("Unpacking the voice engine...", "A desempacotar o motor de voz...");
		Path dir = Net.root().resolve("piper");
		Net.deleteTree(dir);
		Net.unpack(file, dir);
		Files.deleteIfExists(file);
		Path bin = binary();
		if (bin == null) {
			throw new IOException(Lang.tr("the voice engine was not found after the download", "o motor de voz não foi encontrado depois da descarga"));
		}
		bin.toFile().setExecutable(true, false);
	}

	private static void downloadVoice(VerityLang.L lang) throws Exception {
		status = Lang.tr("Looking for the voice...", "A procurar a voz...");
		String folder = lang.piperDir();
		StringBuilder enc = new StringBuilder();
		for (String seg : folder.split("/")) {
			enc.append(enc.length() == 0 ? "" : "/").append(URLEncoder.encode(seg, StandardCharsets.UTF_8));
		}
		var list = Net.json(VOICES.replace("huggingface.co/", "huggingface.co/api/models/") + "tree/main/" + enc).getAsJsonArray();
		String onnx = null, cfg = null;
		for (JsonElement e : list) {
			JsonObject o = e.getAsJsonObject();
			String p = o.get("path").getAsString();
			if (p.endsWith(".onnx")) {
				onnx = p;
			} else if (p.endsWith(".onnx.json")) {
				cfg = p;
			}
		}
		if (onnx == null || cfg == null) {
			throw new IOException(Lang.tr("the voice files were not found", "os ficheiros da voz não foram encontrados"));
		}
		Path target = voice(lang);
		Net.download(resolve(cfg), target.resolveSibling(target.getFileName() + ".json"), Lang.tr("Downloading the voice", "A descarregar a voz"), s -> status = s);
		Net.download(resolve(onnx), target, Lang.tr("Downloading the voice", "A descarregar a voz"), s -> status = s);
	}

	private static String resolve(String path) {
		StringBuilder enc = new StringBuilder();
		for (String seg : path.split("/")) {
			enc.append(enc.length() == 0 ? "" : "/").append(URLEncoder.encode(seg, StandardCharsets.UTF_8));
		}
		return VOICES + "resolve/main/" + enc;
	}

	/** Speaks the text to a WAV (bytes). lengthScale: 1 normal, bigger = slower and deeper-feeling. Blocks: call it off the game thread. */
	static byte[] speak(String text, float lengthScale) throws Exception {
		Path bin = binary();
		Path v = voicePath();
		if (bin == null || v == null || !Files.isRegularFile(v)) {
			throw new IOException("voice not installed");
		}
		Path out = Files.createTempFile("verity-voice", ".wav");
		try {
			ProcessBuilder pb = new ProcessBuilder(bin.toString(), "--model", v.toString(), "--output_file", out.toString(), "--length_scale",
					String.format(Locale.ROOT, "%.2f", lengthScale));
			pb.directory(bin.getParent().toFile());
			pb.environment().put("DYLD_LIBRARY_PATH", bin.getParent().toString());
			pb.environment().put("LD_LIBRARY_PATH", bin.getParent().toString());
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			Process p = pb.start();
			try (var os = p.getOutputStream()) {
				os.write((text.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
			}
			if (!p.waitFor(40, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				throw new IOException("the voice took too long");
			}
			if (Files.size(out) < 100) {
				throw new IOException("the voice engine made no sound (exit " + p.exitValue() + ")");
			}
			return Files.readAllBytes(out);
		} finally {
			Files.deleteIfExists(out);
		}
	}
}
