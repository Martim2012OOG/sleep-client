package dev.sleepclient.video;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The small native helper that streams one window (ScreenCaptureKit). Its Swift source is inside the jar; the first time
 * it is needed it is compiled with the Mac's own swiftc (Command Line Tools) and kept in
 * ~/Library/Application Support/Sleep/helper. If anything fails, {@link #ensure()} returns null and the old
 * screencapture loop is used instead.
 */
final class WinCapHelper {

	private static String path;
	private static boolean tried;
	static volatile String problem = "";

	private WinCapHelper() {
	}

	/** Path of the compiled helper, or null. Blocks while compiling (about 10-40 s, once). Call from a worker thread. */
	static synchronized String ensure() {
		if (tried) {
			return path;
		}
		tried = true;
		try {
			byte[] src;
			try (InputStream in = WinCapHelper.class.getResourceAsStream("/kubo/wincap.swift")) {
				if (in == null) {
					problem = "wincap.swift is not inside the jar";
					return null;
				}
				src = in.readAllBytes();
			}
			String hash = hex(MessageDigest.getInstance("SHA-1").digest(src)).substring(0, 10);
			Path dir = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Sleep", "helper");
			Files.createDirectories(dir);
			Path bin = dir.resolve("wincap-" + hash);
			if (Files.isExecutable(bin) && Files.size(bin) > 0) {
				path = bin.toString();
				return path;
			}
			Path swift = dir.resolve("wincap-" + hash + ".swift");
			Files.write(swift, src);
			Path log = dir.resolve("compile.log");
			Process p = new ProcessBuilder(List.of("xcrun", "swiftc", "-O", "-swift-version", "5", swift.toString(), "-o", bin.toString()))
					.redirectErrorStream(true).redirectOutput(log.toFile()).start();
			if (!p.waitFor(180, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				problem = "swiftc took too long";
				return null;
			}
			if (p.exitValue() != 0 || !Files.isExecutable(bin)) {
				problem = "swiftc failed (see " + log + "). Install Command Line Tools: xcode-select --install";
				return null;
			}
			path = bin.toString();
		} catch (Exception e) {
			problem = "Could not prepare the window helper: " + e.getMessage();
		}
		return path;
	}

	private static String hex(byte[] b) {
		StringBuilder sb = new StringBuilder();
		for (byte x : b) {
			sb.append(String.format("%02x", x));
		}
		return sb.toString();
	}
}
