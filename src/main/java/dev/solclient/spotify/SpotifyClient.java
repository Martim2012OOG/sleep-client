package dev.solclient.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Talks to Spotify's Web API: login (PKCE, no secret), what is playing, play / pause / next / previous, playlists.
 * Spotify cannot stream its audio into a game, so the music plays in the Spotify app on your PC / phone and this
 * only shows it and controls it. Everything runs on one background thread; the game only reads the fields.
 */
public final class SpotifyClient {

	public record Playlist(String name, String uri) {
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "kubo-spotify");
		t.setDaemon(true);
		return t;
	});

	// ---- state read by the HUD
	public static volatile String status = "Not connected";
	public static volatile boolean connected;
	public static volatile boolean hasTrack;
	public static volatile boolean playing;
	public static volatile String title = "", artist = "", artUrl = "", artKey = "", device = "";
	public static volatile long durationMs, progressMs, fetchedAt;
	public static volatile String playlistName = "";

	private static volatile boolean polling;
	private static volatile String clientId = "";
	private static String accessToken, refreshToken;
	private static long expiresAt;
	private static final List<Playlist> PLAYLISTS = new ArrayList<>();
	private static int playlistIdx = -1;
	private static volatile ServerSocket pending;

	private SpotifyClient() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-spotify.json");
	}

	private enum Refresh { OK, INVALID, OFFLINE }

	private static final java.util.concurrent.atomic.AtomicBoolean LOGIN = new java.util.concurrent.atomic.AtomicBoolean();
	private static final java.util.concurrent.atomic.AtomicBoolean RETRYING = new java.util.concurrent.atomic.AtomicBoolean();
	private static volatile String pendingUrl = "";
	private static volatile int tokenStatus;

	/** Called by the mod when it is switched on: remembers the Client ID and logs in again silently (never opens the browser). */
	public static void start(String id) {
		clientId = id == null ? "" : id.trim();
		IO.execute(SpotifyClient::restore);
	}

	/** Silent login with the saved token. A bad connection only retries later, it never throws the login away. */
	private static void restore() {
		if (connected) {
			startPolling();
			return;
		}
		if (refreshToken == null) {
			loadToken();
		}
		if (clientId.isEmpty()) {
			status = "Paste your Client ID in the settings";
			return;
		}
		if (refreshToken == null) {
			status = "Not connected: press Connect";
			return;
		}
		Refresh r = refreshState();
		if (r == Refresh.OK) {
			connected = true;
			status = "Connected";
			startPolling();
		} else if (r == Refresh.INVALID) {
			status = "Session expired: press Connect";
		} else {
			status = "No connection to Spotify: retrying...";
			retryLater();
		}
	}

	private static void retryLater() {
		if (!RETRYING.compareAndSet(false, true)) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(15_000);
			} catch (InterruptedException e) {
				return;
			} finally {
				RETRYING.set(false);
			}
			IO.execute(SpotifyClient::restore);
		}, "kubo-spotify-retry");
		t.setDaemon(true);
		t.start();
	}

	public static void stop() {
		polling = false;
	}

	public static void setClientId(String id) {
		clientId = id == null ? "" : id.trim();
	}

	// ------------------------------------------------------------------ login
	/**
	 * The Connect button. Already connected: nothing happens. A saved login that still works is reused silently. The browser
	 * only opens when there is really nothing to reuse, and only ONE login can wait at a time (pressing again re-opens that
	 * same page instead of opening new windows).
	 */
	public static void connect() {
		if (clientId.isEmpty()) {
			status = "Paste your Client ID first (see the steps)";
			return;
		}
		if (connected) {
			status = "Already connected";
			return;
		}
		if (!LOGIN.compareAndSet(false, true)) {
			String u = pendingUrl;
			if (!u.isEmpty()) {
				status = "Approve in the browser...";
				net.minecraft.util.Util.getOperatingSystem().open(URI.create(u));
			}
			return;
		}
		Thread t = new Thread(() -> {
			try {
				if (refreshToken == null) {
					loadToken();
				}
				if (refreshToken != null) {
					status = "Checking your saved login...";
					Refresh r = refreshState();
					if (r == Refresh.OK) {
						connected = true;
						status = "Connected";
						startPolling();
						return;
					}
					if (r == Refresh.OFFLINE) {
						status = "No connection to Spotify: try again in a moment";
						return;
					}
				}
				login();
			} finally {
				pendingUrl = "";
				LOGIN.set(false);
			}
		}, "kubo-spotify-login");
		t.setDaemon(true);
		t.start();
	}

	private static void login() {
		try {
			String ver = SpotifyAuth.verifier();
			String state = SpotifyAuth.verifier().substring(0, 16);
			ServerSocket ss = new ServerSocket(SpotifyAuth.PORT, 1, InetAddress.getByName("127.0.0.1"));
			ss.setSoTimeout(180_000);
			pending = ss;
			status = "Approve in the browser...";
			String url = SpotifyAuth.authorizeUrl(clientId, SpotifyAuth.challenge(ver), state);
			pendingUrl = url;
			net.minecraft.util.Util.getOperatingSystem().open(URI.create(url));
			String code = null;
			try (Socket s = ss.accept()) {
				s.setSoTimeout(10_000);
				BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
				var q = SpotifyAuth.parseRequestLine(in.readLine());
				boolean ok = q.containsKey("code") && state.equals(q.get("state"));
				if (ok) {
					code = q.get("code");
				}
				String page = ok ? "Spotify connected to Sleep Client. You can close this tab and go back to the game."
						: "Could not connect: " + q.getOrDefault("error", "unknown error") + ". Close this tab and try again.";
				byte[] body = ("<html><body style=\"font-family:sans-serif;background:#121212;color:#fff;text-align:center;padding-top:80px\"><h2>" + page + "</h2><script>setTimeout(function(){window.close()},1500)</script></body></html>").getBytes(StandardCharsets.UTF_8);
				OutputStream out = s.getOutputStream();
				out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
				out.write(body);
				out.flush();
			} finally {
				ss.close();
				pending = null;
			}
			if (code == null) {
				status = "Login was cancelled";
				return;
			}
			JsonObject t = token(SpotifyAuth.form("grant_type", "authorization_code", "code", code, "redirect_uri", SpotifyAuth.REDIRECT,
					"client_id", clientId, "code_verifier", ver));
			if (t == null || !t.has("access_token")) {
				status = "Spotify refused the login (check the Client ID and the redirect address)";
				return;
			}
			applyToken(t);
			saveToken();
			connected = true;
			status = "Connected";
			startPolling();
		} catch (java.net.SocketTimeoutException e) {
			status = "Login timed out";
		} catch (java.net.BindException e) {
			status = "Port " + SpotifyAuth.PORT + " is busy: close the other login and retry";
		} catch (Exception e) {
			status = "Login failed: " + e.getClass().getSimpleName();
		}
	}

	public static void disconnect() {
		polling = false;
		connected = false;
		hasTrack = false;
		accessToken = null;
		refreshToken = null;
		PLAYLISTS.clear();
		playlistIdx = -1;
		playlistName = "";
		try {
			Files.deleteIfExists(file());
		} catch (Exception ignored) {
		}
		status = "Disconnected";
	}

	private static JsonObject token(String form) {
		try {
			HttpRequest r = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
					.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(form)).build();
			tokenStatus = 0;
			HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
			tokenStatus = res.statusCode();
			if (res.statusCode() / 100 != 2) {
				return null;
			}
			return JsonParser.parseString(res.body()).getAsJsonObject();
		} catch (Exception e) {
			tokenStatus = 0;
			return null;
		}
	}

	private static void applyToken(JsonObject t) {
		accessToken = t.get("access_token").getAsString();
		if (t.has("refresh_token")) {
			refreshToken = t.get("refresh_token").getAsString();
		}
		long secs = t.has("expires_in") ? t.get("expires_in").getAsLong() : 3600;
		expiresAt = System.currentTimeMillis() + (secs - 60) * 1000;
	}

	/** OK = new token; INVALID = Spotify says the saved login is no good (revoked / wrong Client ID); OFFLINE = no answer, try later. */
	private static Refresh refreshState() {
		if (refreshToken == null || clientId.isEmpty()) {
			return Refresh.INVALID;
		}
		JsonObject t = token(SpotifyAuth.form("grant_type", "refresh_token", "refresh_token", refreshToken, "client_id", clientId));
		if (t != null && t.has("access_token")) {
			applyToken(t);
			saveToken();
			return Refresh.OK;
		}
		int c = tokenStatus;
		return c == 400 || c == 401 || c == 403 ? Refresh.INVALID : Refresh.OFFLINE;
	}

	private static boolean refresh() {
		Refresh r = refreshState();
		if (r == Refresh.OK) {
			return true;
		}
		if (r == Refresh.INVALID) {
			status = "Session expired: press Connect";
			connected = false;
			hasTrack = false;
			polling = false;
		} else {
			status = "No connection to Spotify";
		}
		return false;
	}

	private static void saveToken() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("refresh", refreshToken);
			Files.writeString(file(), o.toString());
		} catch (Exception ignored) {
		}
	}

	private static void loadToken() {
		try {
			if (Files.exists(file())) {
				refreshToken = JsonParser.parseString(Files.readString(file())).getAsJsonObject().get("refresh").getAsString();
			}
		} catch (Exception ignored) {
		}
	}

	// ------------------------------------------------------------------ api
	/** 0 = no answer, otherwise the HTTP status; the body goes to out[0]. */
	private static int call(String method, String path, String body, String[] out) {
		try {
			if (accessToken == null || System.currentTimeMillis() > expiresAt) {
				if (!refresh()) {
					return 0;
				}
			}
			for (int attempt = 0; attempt < 2; attempt++) {
				HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1" + path))
						.timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + accessToken);
				if (body != null) {
					b.header("Content-Type", "application/json");
				}
				b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
				HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 401 && attempt == 0 && refresh()) {
					continue;
				}
				if (out != null) {
					out[0] = res.body();
				}
				return res.statusCode();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception ignored) {
		}
		return 0;
	}

	private static void startPolling() {
		if (polling) {
			return;
		}
		polling = true;
		Thread t = new Thread(() -> {
			while (polling) {
				try {
					poll();
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					return;
				} catch (Exception ignored) {
				}
			}
		}, "kubo-spotify-poll");
		t.setDaemon(true);
		t.start();
	}

	private static void poll() {
		String[] out = new String[1];
		long askedAt = System.currentTimeMillis();
		int code = call("GET", "/me/player?additional_types=track,episode", null, out);
		if (code == 204 || code == 202) {
			hasTrack = false;
			status = "Open Spotify on a device and play something";
			return;
		}
		if (code != 200 || out[0] == null || out[0].isBlank()) {
			if (code == 0) {
				status = "No connection to Spotify";
			}
			return;
		}
		JsonObject o = JsonParser.parseString(out[0]).getAsJsonObject();
		playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
		progressMs = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
		// the position is from the moment Spotify answered, about halfway through the request: not when the answer got here
		fetchedAt = (askedAt + System.currentTimeMillis()) / 2;
		if (o.has("device") && o.get("device").isJsonObject() && o.getAsJsonObject("device").has("name")) {
			device = o.getAsJsonObject("device").get("name").getAsString();
		}
		JsonElement item = o.get("item");
		if (item == null || !item.isJsonObject()) {
			hasTrack = false;
			return;
		}
		JsonObject it = item.getAsJsonObject();
		title = it.has("name") ? it.get("name").getAsString() : "";
		durationMs = it.has("duration_ms") ? it.get("duration_ms").getAsLong() : 0;
		StringBuilder ar = new StringBuilder();
		if (it.has("artists") && it.get("artists").isJsonArray()) {
			for (JsonElement a : it.getAsJsonArray("artists")) {
				if (ar.length() > 0) {
					ar.append(", ");
				}
				ar.append(a.getAsJsonObject().get("name").getAsString());
			}
		} else if (it.has("show") && it.get("show").isJsonObject()) {
			ar.append(it.getAsJsonObject("show").get("name").getAsString());
		}
		artist = ar.toString();
		JsonObject holder = it.has("album") && it.get("album").isJsonObject() ? it.getAsJsonObject("album")
				: it.has("images") ? it : null;
		String img = "";
		if (holder != null && holder.has("images") && holder.get("images").isJsonArray()) {
			JsonArray imgs = holder.getAsJsonArray("images");
			if (imgs.size() > 0) {
				// the medium one (~300px) so the cover stays sharp when the HUD is made bigger
				img = imgs.get(imgs.size() > 1 ? imgs.size() - 2 : 0).getAsJsonObject().get("url").getAsString();
			}
		}
		artUrl = img;
		artKey = it.has("id") && !it.get("id").isJsonNull() ? it.get("id").getAsString() : title;
		hasTrack = true;
		status = "Connected";
	}

	/** Cover colours found so far (album picture URL -> accent colour ARGB), filled while converting the picture. */
	private static final java.util.Map<String, Integer> COLOURS = new java.util.concurrent.ConcurrentHashMap<>();

	/** The colour found for the cover at address {@code from} is also the colour of {@code to} (a cover found online for a local picture). */
	public static void aliasColour(String from, String to) {
		Integer c = from == null ? null : COLOURS.get(from);
		if (c != null && to != null && !to.isEmpty()) {
			COLOURS.put(to, c);
		}
	}

	/** The main colour of the cover of this URL (bright enough to be used as an accent), or def while unknown. */
	public static int colourFor(String url, int def) {
		Integer c = url == null ? null : COLOURS.get(url);
		return c == null ? def : c;
	}

	/** The converted album picture (PNG, 64x64) for RemoteTextures.local, or null. Runs off the render thread. */
	public static byte[] albumPng(String url) {
		return albumPng(url, 0);
	}

	/**
	 * The album picture as a 128x128 PNG. shape 0 = square, 1 = round (vinyl), 2 = rounded corners.
	 * Also remembers the main colour of the cover (see {@link #colourFor}).
	 */
	public static byte[] albumPng(String url, int shape) {
		try {
			// a local file (covers that Windows gives for the song) or a web address
			byte[] raw = url.startsWith("file:") ? Files.readAllBytes(Path.of(URI.create(url))) : dev.solclient.social.SolarApi.download(url);
			if (raw == null) {
				return null;
			}
			java.awt.image.BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
			if (src == null) {
				return null;
			}
			final int n = 128;
			java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = dst.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
			// centre square: wide pictures (YouTube thumbnails) are cropped, not squashed
			int side = Math.min(src.getWidth(), src.getHeight());
			int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
			g.drawImage(src, 0, 0, n, n, sx, sy, sx + side, sy + side, null);
			g.dispose();
			COLOURS.computeIfAbsent(url, k -> dominant(dst));
			if (shape != 0) {
				// soft (anti-aliased) mask: circle, or square with round corners
				double r = shape == 1 ? n / 2.0 : n * 0.16;
				for (int y = 0; y < n; y++) {
					for (int x = 0; x < n; x++) {
						double px = x + 0.5, py = y + 0.5;
						double cx = Math.max(r, Math.min(n - r, px)), cy = Math.max(r, Math.min(n - r, py));
						double d = Math.hypot(px - cx, py - cy);
						double cover = Math.max(0, Math.min(1, r - d + 0.5));
						if (cover < 1) {
							int argb = dst.getRGB(x, y);
							int a = (int) Math.round(((argb >>> 24) & 255) * cover);
							dst.setRGB(x, y, a << 24 | (argb & 0xFFFFFF));
						}
					}
				}
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageIO.write(dst, "png", bos);
			return bos.toByteArray();
		} catch (Exception e) {
			return null;
		}
	}

	/** Strongest colourful hue of the picture, made bright enough to read on a dark card. */
	static int dominant(java.awt.image.BufferedImage img) {
		double[] w = new double[24], r = new double[24], gr = new double[24], b = new double[24];
		float[] hsb = new float[3];
		for (int y = 0; y < img.getHeight(); y += 2) {
			for (int x = 0; x < img.getWidth(); x += 2) {
				int c = img.getRGB(x, y);
				int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
				java.awt.Color.RGBtoHSB(cr, cg, cb, hsb);
				double weight = hsb[1] * hsb[1] * hsb[2] + 0.02;
				int bin = Math.min(23, (int) (hsb[0] * 24));
				w[bin] += weight;
				r[bin] += cr * weight;
				gr[bin] += cg * weight;
				b[bin] += cb * weight;
			}
		}
		int best = 0;
		for (int i = 1; i < 24; i++) {
			// neighbouring bins count a bit too, so a hue split over two bins still wins
			double si = w[i] + 0.5 * (w[(i + 23) % 24] + w[(i + 1) % 24]);
			double sb = w[best] + 0.5 * (w[(best + 23) % 24] + w[(best + 1) % 24]);
			if (si > sb) {
				best = i;
			}
		}
		if (w[best] <= 0) {
			return 0xFF1DB954;
		}
		int cr = (int) (r[best] / w[best]), cg = (int) (gr[best] / w[best]), cb = (int) (b[best] / w[best]);
		java.awt.Color.RGBtoHSB(cr, cg, cb, hsb);
		float sat = Math.max(0.35F, Math.min(0.9F, hsb[1] * 1.15F));
		float bri = Math.max(0.75F, hsb[2]);
		if (hsb[1] < 0.12F) {
			sat = hsb[1]; // a black & white cover stays grey-ish
			bri = 0.85F;
		}
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(hsb[0], sat, bri) & 0xFFFFFF);
	}

	// ------------------------------------------------------------------ controls
	public static void playPause() {
		command("PUT", playing ? "/me/player/pause" : "/me/player/play", null);
	}

	public static void next() {
		command("POST", "/me/player/next", null);
	}

	public static void previous() {
		command("POST", "/me/player/previous", null);
	}

	private static void command(String method, String path, String body) {
		if (!connected) {
			return;
		}
		IO.execute(() -> {
			int c = call(method, path, body, null);
			if (c == 403) {
				status = "Spotify Premium is needed to control playback";
			} else if (c == 404) {
				status = "Open Spotify on a device first";
			}
			try {
				Thread.sleep(350);
			} catch (InterruptedException e) {
				return;
			}
			poll();
		});
	}

	/** Starts the next one of your playlists (the first press loads the list). */
	public static void nextPlaylist() {
		if (!connected) {
			status = "Connect Spotify first";
			return;
		}
		IO.execute(() -> {
			if (PLAYLISTS.isEmpty()) {
				String[] out = new String[1];
				if (call("GET", "/me/playlists?limit=50", null, out) == 200 && out[0] != null) {
					JsonArray items = JsonParser.parseString(out[0]).getAsJsonObject().getAsJsonArray("items");
					for (JsonElement e : items) {
						if (e.isJsonObject() && e.getAsJsonObject().has("uri") && e.getAsJsonObject().has("name")) {
							PLAYLISTS.add(new Playlist(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject().get("uri").getAsString()));
						}
					}
				}
			}
			if (PLAYLISTS.isEmpty()) {
				status = "No playlists found";
				return;
			}
			playlistIdx = (playlistIdx + 1) % PLAYLISTS.size();
			Playlist p = PLAYLISTS.get(playlistIdx);
			JsonObject b = new JsonObject();
			b.addProperty("context_uri", p.uri());
			int c = call("PUT", "/me/player/play", b.toString(), null);
			if (c == 403) {
				status = "Spotify Premium is needed to start playlists";
			} else if (c == 404) {
				status = "Open Spotify on a device first";
			} else {
				playlistName = p.name();
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				return;
			}
			poll();
		});
	}
}
