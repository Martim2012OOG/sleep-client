package dev.solclient.social;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Resource packs of the Sleep site (GET /resource-packs). This class has no Minecraft code at all, so it can be unit
 * tested: file names, SHA-1, install state and what a social link does (open in the browser or copy).
 */
public final class KuboPackLogic {

	private static final java.util.Set<String> DISCORD_HOSTS = java.util.Set.of("discord.gg", "discord.com", "www.discord.com",
			"discordapp.com", "www.discordapp.com", "discord.me", "discord.io");

	private KuboPackLogic() {
	}

	/** One pack as the site sends it. Text fields are never null (empty when missing). */
	public record Pack(String id, String name, String description, String version, String mcVersion, List<String> tags,
			String owner, String credits, String discord, String youtube, String twitter, String website, String icon,
			String banner, String download, String sha1, long size, long downloads) {
	}

	public enum State {
		NOT_INSTALLED, INSTALLED, UPDATE
	}

	/** Only https addresses are ever downloaded or loaded as pictures. */
	public static boolean https(String url) {
		return url != null && url.length() < 2048 && url.regionMatches(true, 0, "https://", 0, 8) && url.indexOf(' ') < 0;
	}

	/** A pack we can show and install: id, name, https download and a real SHA-1. */
	public static boolean usable(Pack p) {
		return p != null && !p.id().isBlank() && !p.name().isBlank() && https(p.download()) && normSha1(p.sha1()) != null;
	}

	/** 40 hex digits in lower case, or null. */
	public static String normSha1(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim().toLowerCase(Locale.ROOT);
		if (t.length() != 40) {
			return null;
		}
		for (int i = 0; i < 40; i++) {
			char c = t.charAt(i);
			if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
				return null;
			}
		}
		return t;
	}

	/** Keeps a piece of a file name safe: a-z 0-9 . _ - only, no leading dot, never empty. */
	public static String safePart(String s) {
		StringBuilder b = new StringBuilder();
		for (char ch : (s == null ? "" : s.trim()).toLowerCase(Locale.ROOT).toCharArray()) {
			boolean ok = ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '_' || ch == '-';
			b.append(ok ? ch : '_');
			if (b.length() >= 64) {
				break;
			}
		}
		while (b.length() > 0 && b.charAt(0) == '.') {
			b.deleteCharAt(0);
		}
		return b.length() == 0 ? "x" : b.toString();
	}

	/** kubo-&lt;id&gt;-&lt;version&gt;.zip, as asked by the site's contract. */
	public static String fileName(Pack p) {
		return "kubo-" + safePart(p.id()) + "-" + safePart(p.version()) + ".zip";
	}

	/** The id Minecraft's ResourcePackManager gives a zip in the resourcepacks folder. */
	public static String managerId(String fileName) {
		return "file/" + fileName;
	}

	/** SHA-1 of a file, lower case hex. */
	public static String sha1Hex(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}
			return hex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	public static String hex(byte[] d) {
		StringBuilder sb = new StringBuilder(d.length * 2);
		for (byte b : d) {
			sb.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
		}
		return sb.toString();
	}

	/**
	 * Install state.
	 * - the exact file (same version) exists with the site's SHA-1 -> INSTALLED
	 * - the exact file exists but with another SHA-1 (the site changed it, or it is damaged) -> UPDATE
	 * - no exact file, but we installed an older file of this pack before and it is still there -> UPDATE
	 * - otherwise NOT_INSTALLED
	 * {@code previousFile} is the file name we recorded the last time we installed this pack (null = never).
	 * Blocking (reads the file): call it off the render thread.
	 */
	public static State state(Pack p, Path dir, String previousFile) {
		Path exact = dir.resolve(fileName(p));
		String want = normSha1(p.sha1());
		if (Files.isRegularFile(exact)) {
			try {
				return want != null && want.equals(sha1Hex(exact)) ? State.INSTALLED : State.UPDATE;
			} catch (IOException e) {
				return State.UPDATE;
			}
		}
		if (previousFile != null && !previousFile.equals(fileName(p)) && !previousFile.contains("/") && !previousFile.contains("\\")
				&& Files.isRegularFile(dir.resolve(previousFile))) {
			return State.UPDATE;
		}
		return State.NOT_INSTALLED;
	}

	/** What a social button does: open {@code url} in the system browser, or (url null) copy {@code copy}. */
	public record Link(String platform, String url, String copy) {
		public boolean opens() {
			return url != null;
		}
	}

	/**
	 * Social value from the site. A URL opens; a bare address (discord.gg/abc, youtube.com/@x) gets https://; anything
	 * else (a Discord user name, "@someone") is copied. Only http(s) ever opens: javascript:, file:... are copied as text.
	 * Null when there is nothing.
	 */
	public static Link link(String platform, String value) {
		if (value == null) {
			return null;
		}
		String v = value.trim();
		if (v.isEmpty() || v.length() > 2048) {
			return null;
		}
		boolean clean = v.chars().noneMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch));
		String low = v.toLowerCase(Locale.ROOT);
		if (clean && (low.startsWith("https://") || low.startsWith("http://")) && v.length() > 8) {
			return new Link(platform, v, null);
		}
		if (clean && !low.contains("://") && !low.contains(":") && !v.startsWith("@") && v.indexOf('.') > 0) {
			String host = v.contains("/") ? v.substring(0, v.indexOf('/')) : v;
			boolean discord = "discord".equalsIgnoreCase(platform);
			// a Discord user name can have dots ("cool.dev"): only known Discord hosts count as an address there
			boolean known = DISCORD_HOSTS.contains(host.toLowerCase(Locale.ROOT));
			if (host.indexOf('.') > 0 && !host.endsWith(".") && (!discord || known)) {
				return new Link(platform, "https://" + v, null);
			}
		}
		return new Link(platform, null, v);
	}
}
