package dev.solclient.account;

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
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Microsoft login for the account switcher, with the "device code" flow (the same one the Xbox / Switch
 * versions use): you get a short code, open microsoft.com/link in your browser, type it and log in there.
 * Your password is never typed into the game.
 *
 * Chain: Microsoft (live.com) token -> Xbox Live user token -> XSTS token -> Minecraft token -> Minecraft profile.
 * The refresh token is kept in config/plegumo-accounts.json so you don't have to log in again.
 */
public final class MicrosoftAuth {

	/** Public client id of Minecraft for Nintendo Switch: supports the device code flow without our own Azure app. */
	private static final String CLIENT_ID = "00000000441cc96b";
	private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	/** Result of a full login. */
	public record Profile(String name, String uuid, String mcToken, String refreshToken) {
	}

	/** A running "go to microsoft.com/link and type this code" login. Read by the accounts screen. */
	public static final class DeviceLogin {
		public volatile String userCode, verifyUrl;
		public volatile String status = "Asking Microsoft for a code...";
		public volatile boolean done, failed, cancelled;
		public volatile Profile result;
	}

	private MicrosoftAuth() {
	}

	// ---------------------------------------------------------------- http
	private record Resp(int code, JsonObject json, String raw) {
	}

	private static String form(Map<String, String> m) {
		StringJoiner j = new StringJoiner("&");
		m.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return j.toString();
	}

	private static Resp send(HttpRequest.Builder b) throws Exception {
		HttpResponse<String> r = HTTP.send(b.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
		JsonObject o = null;
		try {
			JsonElement e = JsonParser.parseString(r.body());
			if (e.isJsonObject()) {
				o = e.getAsJsonObject();
			}
		} catch (Exception ignored) {
		}
		return new Resp(r.statusCode(), o == null ? new JsonObject() : o, r.body());
	}

	private static Resp postForm(String url, Map<String, String> f) throws Exception {
		return send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(form(f))));
	}

	private static Resp postJson(String url, JsonObject body, String bearer) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
				.header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		if (bearer != null) {
			b.header("Authorization", "Bearer " + bearer);
		}
		return send(b);
	}

	private static String str(JsonObject o, String k) {
		return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	// ---------------------------------------------------------------- device code
	/** Starts a login on a background thread; poll the returned object. */
	public static DeviceLogin startDeviceLogin() {
		DeviceLogin d = new DeviceLogin();
		Thread t = new Thread(() -> {
			try {
				Resp code = postForm("https://login.live.com/oauth20_connect.srf",
						Map.of("client_id", CLIENT_ID, "scope", SCOPE, "response_type", "device_code"));
				String device = str(code.json, "device_code");
				if (device == null) {
					throw new IllegalStateException("Microsoft didn't give a code (" + code.code + ")");
				}
				d.userCode = str(code.json, "user_code");
				d.verifyUrl = str(code.json, "verification_uri") == null ? "https://www.microsoft.com/link" : str(code.json, "verification_uri");
				int interval = Math.max(2, code.json.has("interval") ? code.json.get("interval").getAsInt() : 5);
				long expires = System.currentTimeMillis() + (code.json.has("expires_in") ? code.json.get("expires_in").getAsLong() : 900) * 1000L;
				d.status = "Waiting for you to log in in the browser...";
				while (!d.cancelled) {
					Thread.sleep(interval * 1000L);
					if (System.currentTimeMillis() > expires) {
						throw new IllegalStateException("The code expired, try again");
					}
					Resp tok = postForm("https://login.live.com/oauth20_token.srf", Map.of("client_id", CLIENT_ID, "device_code", device,
							"grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
					String err = str(tok.json, "error");
					if ("authorization_pending".equals(err)) {
						continue;
					}
					if ("slow_down".equals(err)) {
						interval += 2;
						continue;
					}
					if (err != null) {
						throw new IllegalStateException("authorization_declined".equals(err) ? "Login cancelled" : "Microsoft: " + err);
					}
					d.status = "Logging in to Xbox Live...";
					d.result = minecraft(str(tok.json, "access_token"), str(tok.json, "refresh_token"), s -> d.status = s);
					d.status = "Logged in as " + d.result.name();
					d.done = true;
					return;
				}
			} catch (Exception e) {
				d.status = e.getMessage() == null ? e.toString() : e.getMessage();
				d.failed = true;
			}
		}, "Caos Microsoft login");
		t.setDaemon(true);
		t.start();
		return d;
	}

	/** New Minecraft token from a saved refresh token (blocking; call off the render thread). */
	public static Profile refresh(String refreshToken) throws Exception {
		Resp tok = postForm("https://login.live.com/oauth20_token.srf", Map.of("client_id", CLIENT_ID, "refresh_token", refreshToken,
				"grant_type", "refresh_token", "scope", SCOPE));
		String access = str(tok.json, "access_token");
		if (access == null) {
			throw new IllegalStateException("Microsoft session expired, log in again");
		}
		String newRefresh = str(tok.json, "refresh_token");
		return minecraft(access, newRefresh == null ? refreshToken : newRefresh, s -> {
		});
	}

	// ---------------------------------------------------------------- xbox -> minecraft
	private static Profile minecraft(String msToken, String refresh, java.util.function.Consumer<String> status) throws Exception {
		// Xbox Live user token (tokens of this client id go in as "t=<token>"; try the other forms if refused)
		Resp xbl = null;
		for (String pre : new String[]{"t=", "d=", ""}) {
			JsonObject props = new JsonObject();
			props.addProperty("AuthMethod", "RPS");
			props.addProperty("SiteName", "user.auth.xboxlive.com");
			props.addProperty("RpsTicket", pre + msToken);
			JsonObject b = new JsonObject();
			b.add("Properties", props);
			b.addProperty("RelyingParty", "http://auth.xboxlive.com");
			b.addProperty("TokenType", "JWT");
			xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", b, null);
			if (xbl.code == 200) {
				break;
			}
		}
		String xblToken = str(xbl.json, "Token");
		if (xblToken == null) {
			throw new IllegalStateException("Xbox Live refused the login (" + xbl.code + ")");
		}
		status.accept("Getting the Minecraft token...");
		JsonObject props = new JsonObject();
		props.addProperty("SandboxId", "RETAIL");
		JsonArray ut = new JsonArray();
		ut.add(xblToken);
		props.add("UserTokens", ut);
		JsonObject b = new JsonObject();
		b.add("Properties", props);
		b.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		b.addProperty("TokenType", "JWT");
		Resp xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", b, null);
		if (xsts.code == 401) {
			long x = xsts.json.has("XErr") ? xsts.json.get("XErr").getAsLong() : 0;
			throw new IllegalStateException(x == 2148916233L ? "This Microsoft account has no Xbox profile (create one on xbox.com)"
					: x == 2148916238L ? "Child account: an adult must add it to a family on xbox.com"
					: "Xbox refused the account (" + x + ")");
		}
		String xstsToken = str(xsts.json, "Token");
		String uhs = null;
		try {
			uhs = xsts.json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		} catch (Exception ignored) {
		}
		if (xstsToken == null || uhs == null) {
			throw new IllegalStateException("Xbox login failed (" + xsts.code + ")");
		}
		JsonObject lb = new JsonObject();
		lb.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
		Resp mcr = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", lb, null);
		String mcToken = str(mcr.json, "access_token");
		if (mcToken == null) {
			throw new IllegalStateException("Minecraft login failed (" + mcr.code + ")");
		}
		status.accept("Loading your profile...");
		Resp prof = send(HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
				.header("Authorization", "Bearer " + mcToken).GET());
		if (prof.code == 404) {
			throw new IllegalStateException("This account doesn't own Minecraft Java");
		}
		String name = str(prof.json, "name"), id = str(prof.json, "id");
		if (name == null || id == null) {
			throw new IllegalStateException("Could not read the profile (" + prof.code + ")");
		}
		return new Profile(name, id, mcToken, refresh);
	}

	/**
	 * Changes the skin of a Microsoft account on Mojang's servers (everyone sees it after rejoining a server).
	 * Returns null when it worked, else the error.
	 */
	public static String uploadSkin(String mcToken, byte[] png, boolean slim) {
		try {
			String boundary = "----plegumo" + System.nanoTime();
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic") + "\r\n"
					+ "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n";
			out.write(head.getBytes(StandardCharsets.UTF_8));
			out.write(png);
			out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			Resp r = send(HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
					.header("Authorization", "Bearer " + mcToken).header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())));
			if (r.code >= 200 && r.code < 300) {
				return null;
			}
			if (r.code == 401) {
				return "Mojang refused the token (log in again in the account switcher)";
			}
			if (r.code == 429) {
				return "Too many skin changes, wait a minute";
			}
			String msg = str(r.json, "errorMessage");
			return "Mojang: " + (msg != null ? msg : "error " + r.code);
		} catch (Exception e) {
			return "Could not reach Mojang: " + e.getMessage();
		}
	}
}
