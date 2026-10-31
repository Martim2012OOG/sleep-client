package dev.solclient.fightcam;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * The Mac voice helper: Apple's speech recognition (kubo/speech-helper in the jar, built from kubo/speech.swift by
 * tools/build_speech_helper.sh, universal Apple Silicon + Intel). It comes ready-made, so nothing has to be compiled on
 * the player's Mac. It is copied once into ~/Library/Application Support/Sleep/helper.
 */
final class AppleSpeech {

	private static String path;
	private static boolean tried;
	static volatile String problem = "";

	private AppleSpeech() {
	}

	/** Path of the helper, or null (then {@link #problem} says why). */
	static synchronized String path() {
		if (tried) {
			return path;
		}
		tried = true;
		try (InputStream in = AppleSpeech.class.getResourceAsStream("/kubo/speech-helper")) {
			if (in == null) {
				problem = "the voice helper is not inside the jar";
				return null;
			}
			byte[] bin = in.readAllBytes();
			StringBuilder hash = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-1").digest(bin)) {
				hash.append(String.format("%02x", b));
			}
			Path dir = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Kubo", "helper");
			Files.createDirectories(dir);
			Path exe = dir.resolve("speech-" + hash.substring(0, 10));
			if (!Files.isRegularFile(exe) || Files.size(exe) != bin.length) {
				Path tmp = dir.resolve(exe.getFileName() + ".part");
				Files.write(tmp, bin);
				Files.move(tmp, exe, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			if (!exe.toFile().setExecutable(true, false) && !Files.isExecutable(exe)) {
				problem = "could not make the voice helper runnable";
				return null;
			}
			path = exe.toString();
		} catch (Exception e) {
			problem = "could not prepare the voice helper: " + e.getMessage();
		}
		return path;
	}
}
