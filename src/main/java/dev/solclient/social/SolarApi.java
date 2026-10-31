package dev.solclient.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Thin HTTP client for the Solar social backend ("solar-social").
 * Every call is blocking: only call it from {@link Social}'s worker threads, never from the render thread.
 */
public final class SolarApi {

	/** Default backend. Can be overridden with "baseUrl" in config/plegumo-social.json. */
	public static final String DEFAULT_BASE = "https://pal-connect-plaza.lovable.app/api/public/gc";

	/**
	 * Client key (X-Client-Key). If the admin turned it on in /admin, paste the key here before compiling,
	 * or put it in config/plegumo-social.json as "clientKey". Empty = header not sent.
	 */
	public static final String CLIENT_KEY = "";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	static volatile String base = DEFAULT_BASE;
	static volatile String clientKey = CLIENT_KEY;
	/** Session token (Bearer). Saved in config/plegumo-social.json so you stay logged in. */
	static volatile String token;

	private SolarApi() {
	}

	/** Backend address (for other parts of Sleep, like the extension store). */
	public static String baseUrl() {
		return base;
	}

	public static String clientKeyValue() {
		return clientKey == null ? "" : clientKey;
	}

	/** Result of one request. {@code status} 0 = network error. */
	public record Res(int status, JsonElement body, String error) {
		boolean ok() {
			return status >= 200 && status < 300;
		}

		JsonObject obj() {
			return Json.obj(body);
		}
	}

	// ---------------------------------------------------------------- settings file
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-social.json");
	}

	static void loadSettings() {
		try {
			Path p = file();
			if (!Files.exists(p)) {
				return;
			}
			JsonObject o = Json.obj(JsonParser.parseString(Files.readString(p)));
			String b = Json.str(o, "baseUrl");
			boolean oldSite = b != null && b.contains("mc-social-core.lovable.app");
			if (oldSite) {
				// moved to the new site/database: forget the old address and the old login
				saveSettings();
				return;
			}
			if (b != null && b.startsWith("http")) {
				base = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
			}
			String k = Json.str(o, "clientKey");
			if (k != null && !k.isBlank()) {
				clientKey = k.trim();
			}
			String t = Json.str(o, "token");
			if (t != null && !t.isBlank()) {
				token = t;
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] Could not read plegumo-social.json: " + e);
		}
	}

	static synchronized void saveSettings() {
		JsonObject o = new JsonObject();
		o.addProperty("baseUrl", base);
		o.addProperty("clientKey", clientKey == null ? "" : clientKey);
		if (token != null) {
			o.addProperty("token", token);
		}
		try {
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException e) {
			System.err.println("[Sleep] Could not save plegumo-social.json: " + e);
		}
	}

	static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- requests
	static Res get(String path) {
		return send("GET", path, null, true, 10);
	}

	static Res post(String path, JsonObject body) {
		return send("POST", path, body == null ? new JsonObject() : body, true, 10);
	}

	static Res put(String path, JsonObject body) {
		return send("PUT", path, body == null ? new JsonObject() : body, true, 10);
	}

	static Res patch(String path, JsonObject body) {
		return send("PATCH", path, body == null ? new JsonObject() : body, true, 10);
	}

	static Res delete(String path) {
		return send("DELETE", path, null, true, 10);
	}

	static Res send(String method, String path, JsonObject body, boolean auth, int timeoutSeconds) {
		try {
			HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
					.timeout(Duration.ofSeconds(timeoutSeconds))
					.header("Accept", "application/json")
					.header("User-Agent", "Plegumo-Mod/1.0");
			String key = clientKey;
			if (key != null && !key.isBlank()) {
				b.header("X-Client-Key", key);
			}
			String t = token;
			if (auth && t != null) {
				b.header("Authorization", "Bearer " + t);
			}
			if (body != null) {
				b.header("Content-Type", "application/json");
				b.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
			} else {
				b.method(method, HttpRequest.BodyPublishers.noBody());
			}
			HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			JsonElement parsed = null;
			String text = r.body();
			if (text != null && !text.isBlank()) {
				try {
					parsed = JsonParser.parseString(text);
				} catch (RuntimeException ignored) {
					// not JSON
				}
			}
			String err = null;
			if (r.statusCode() < 200 || r.statusCode() >= 300) {
				err = Json.str(Json.obj(parsed), "message", "statusMessage");
				if (err == null) {
					err = "HTTP " + r.statusCode();
				}
			}
			return new Res(r.statusCode(), parsed, err);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Res(0, null, "Interrupted");
		} catch (IOException | RuntimeException e) {
			return new Res(0, null, "No connection");
		}
	}

	/** Downloads raw bytes (PNG files: capes, skins, shop images). Public, no auth needed. */
	public static byte[] download(String url) {
		return download(url, 4 * 1024 * 1024);
	}

	/** Downloads up to maxBytes (bigger files are ignored). */
	public static byte[] download(String url, int maxBytes) {
		try {
			HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(maxBytes > 8 * 1024 * 1024 ? 60 : 15))
					.header("User-Agent", "Plegumo-Mod/1.0")
					.GET();
			HttpResponse<byte[]> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
			if (r.statusCode() < 200 || r.statusCode() >= 300) {
				return null;
			}
			byte[] data = r.body();
			return data != null && data.length > 0 && data.length < maxBytes ? data : null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
