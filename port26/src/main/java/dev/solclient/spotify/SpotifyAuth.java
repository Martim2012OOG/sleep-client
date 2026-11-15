package dev.solclient.spotify;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** The pure parts of the Spotify login (PKCE): no network, no Minecraft. */
public final class SpotifyAuth {

	public static final int PORT = 8898;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	public static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private playlist-read-collaborative";
	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

	private SpotifyAuth() {
	}

	public static String verifier() {
		SecureRandom r = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 64; i++) {
			sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}

	/** base64url(sha256(verifier)) without padding. */
	public static String challenge(String verifier) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public static String authorizeUrl(String clientId, String challenge, String state) {
		return "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId)
				+ "&scope=" + enc(SCOPES) + "&redirect_uri=" + enc(REDIRECT)
				+ "&code_challenge_method=S256&code_challenge=" + enc(challenge) + "&state=" + enc(state);
	}

	/** "GET /callback?code=x&state=y HTTP/1.1" -> {code: x, state: y}. */
	public static Map<String, String> parseRequestLine(String line) {
		Map<String, String> out = new HashMap<>();
		if (line == null) {
			return out;
		}
		String[] parts = line.split(" ");
		if (parts.length < 2) {
			return out;
		}
		int q = parts[1].indexOf('?');
		if (q < 0) {
			return out;
		}
		for (String kv : parts[1].substring(q + 1).split("&")) {
			int e = kv.indexOf('=');
			if (e > 0) {
				out.put(URLDecoder.decode(kv.substring(0, e), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
			}
		}
		return out;
	}

	public static String form(String... kv) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			if (sb.length() > 0) {
				sb.append('&');
			}
			sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
		}
		return sb.toString();
	}

	/** "1:05" style. */
	public static String time(long ms) {
		long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
}
