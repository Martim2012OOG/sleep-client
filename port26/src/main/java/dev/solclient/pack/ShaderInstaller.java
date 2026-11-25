package dev.solclient.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import net.fabricmc.loader.api.FabricLoader;

/**
 * One-click realistic water: downloads a shader pack from Modrinth (Complementary Reimagined: real 3D waves, reflections and
 * see-through depth) into the shaderpacks folder and selects it for the next start (Iris). Nothing is bundled: the author's
 * licence is respected and the player downloads it. Vanilla water can not do this without shaders.
 */
public final class ShaderInstaller {

	private static final String UA = "Caos-Client/1.0 (Minecraft client mod)";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
	public static final String[] PROJECTS = {"complementary-reimagined", "photon-shader", "bsl-shaders"};

	public static volatile String status = "";
	private static volatile boolean busy;

	private ShaderInstaller() {
	}

	public static String status() {
		return status;
	}

	public static void install() {
		install(PROJECTS[0]);
	}

	public static void install(String project) {
		if (busy) {
			return;
		}
		busy = true;
		status = "Downloading...";
		Thread t = new Thread(() -> {
			try {
				String gv = FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("1.21.11");
				JsonArray versions = versions(project, "?game_versions=" + URLEncoder.encode("[\"" + gv + "\"]", StandardCharsets.UTF_8));
				boolean exact = !versions.isEmpty();
				if (!exact) {
					versions = versions(project, "");
				}
				if (versions.isEmpty()) {
					status = "No version found";
					return;
				}
				JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
				JsonObject pick = files.get(0).getAsJsonObject();
				for (JsonElement f : files) {
					if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
						pick = f.getAsJsonObject();
					}
				}
				String name = pick.get("filename").getAsString();
				Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
				Files.createDirectories(dir);
				Path dest = dir.resolve(name);
				if (!Files.isRegularFile(dest)) {
					Path tmp = dir.resolve(name + ".part");
					HttpRequest rq = HttpRequest.newBuilder(URI.create(pick.get("url").getAsString())).timeout(Duration.ofMinutes(3)).header("User-Agent", UA).GET().build();
					HttpResponse<Path> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofFile(tmp));
					if (r.statusCode() < 200 || r.statusCode() >= 300) {
						Files.deleteIfExists(tmp);
						status = "Download failed (HTTP " + r.statusCode() + ")";
						return;
					}
					Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
				}
				select(name);
				status = "Installed " + name + (exact ? "" : " (not made for " + gv + " yet)") + ". Restart the game.";
			} catch (Throwable e) {
				status = "Failed: " + e.getMessage();
			} finally {
				busy = false;
			}
		}, "kubo-shader-install");
		t.setDaemon(true);
		t.start();
	}

	private static JsonArray versions(String project, String query) throws Exception {
		HttpRequest rq = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + project + "/version" + query))
				.timeout(Duration.ofSeconds(20)).header("User-Agent", UA).GET().build();
		HttpResponse<String> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new java.io.IOException("Modrinth HTTP " + r.statusCode());
		}
		return JsonParser.parseString(r.body()).getAsJsonArray();
	}

	/** Tells Iris (config/iris.properties) to use this pack and have shaders on at the next start. */
	private static void select(String pack) throws Exception {
		Path f = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");
		java.util.List<String> out = new java.util.ArrayList<>();
		if (Files.isRegularFile(f)) {
			for (String l : Files.readAllLines(f)) {
				if (!l.startsWith("shaderPack=") && !l.startsWith("enableShaders=")) {
					out.add(l);
				}
			}
		}
		out.add("shaderPack=" + pack);
		out.add("enableShaders=true");
		Files.write(f, out);
	}
}
