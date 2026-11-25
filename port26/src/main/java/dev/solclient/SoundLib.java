package dev.solclient;

import dev.solclient.gui.Notify;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Sounds you can pick (Kill Sounds, notification sounds...): every sound of the game and of your resource packs, plus
 * your own .wav files in the {@code kubo-sounds} folder. A sound is saved as text: {@code minecraft:block.note_block.bell},
 * {@code custom:my_sound.wav}, {@link #SILENT} or "" (what "" means is up to the setting: none, or the default sound).
 */
public final class SoundLib {

	/** Prefix of a sound that is one of your own files in kubo-sounds/. */
	public static final String CUSTOM = "custom:";
	/** Picked on purpose: no sound at all. */
	public static final String SILENT = "silent";
	private static final long MAX_WAV_BYTES = 8L * 1024 * 1024;

	private static List<String> cache;
	private static long cacheAt;

	private SoundLib() {
	}

	// ------------------------------------------------------------------------------------------ playing
	/** Plays a picked sound (a game sound or one of your own files). Volume 0..1, pitch 0.5..2 (game sounds only). */
	public static void play(String ref, float volume, float pitch) {
		if (ref == null || ref.isEmpty() || ref.equals(SILENT)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			if (ref.startsWith(CUSTOM)) {
				Path f = customFile(ref.substring(CUSTOM.length()));
				if (f != null) {
					playWav(f, volume * mc.options.getFinalSoundSourceVolume(SoundSource.MASTER));
				}
				return;
			}
			Identifier id = Identifier.tryParse(ref);
			if (id != null) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), pitch, volume));
			}
		} catch (RuntimeException ex) {
			System.err.println("[Caos] sound: " + ex);
		}
	}

	private static void playWav(Path file, float vol) {
		Thread t = new Thread(() -> {
			try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
				Clip c = AudioSystem.getClip();
				c.open(in);
				try {
					FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
					float db = vol <= 0.0001F ? g.getMinimum() : (float) (20 * Math.log10(vol));
					g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
				} catch (Exception ignored) {
					// no volume control on this line: plays at full volume
				}
				c.addLineListener(ev -> {
					if (ev.getType() == LineEvent.Type.STOP) {
						c.close();
					}
				});
				c.start();
			} catch (Exception ex) {
				System.err.println("[Caos] sound (wav): " + ex);
			}
		}, "kubo-sound");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------------------------------ the list
	/** Every sound id the game knows (also the ones from resource packs), sorted; your own files come first. */
	public static List<String> allSounds() {
		long now = System.currentTimeMillis();
		if (cache != null && now - cacheAt < 3000L) {
			return cache;
		}
		List<String> out = new ArrayList<>();
		for (String n : customFiles()) {
			out.add(CUSTOM + n);
		}
		List<String> game = new ArrayList<>();
		try {
			java.util.Collection<Identifier> keys = Minecraft.getInstance().getSoundManager().getAvailableSounds();
			for (Identifier id : keys) {
				game.add(id.toString());
			}
		} catch (RuntimeException ex) {
			System.err.println("[Caos] sound list: " + ex);
		}
		java.util.Collections.sort(game);
		out.addAll(game);
		cache = out;
		cacheAt = now;
		return out;
	}

	/** Short name for buttons / list: "block.note_block.bell", "my_sound.wav (own)". */
	public static String pretty(String ref) {
		if (ref == null || ref.isEmpty()) {
			return "None";
		}
		if (ref.equals(SILENT)) {
			return "Silent";
		}
		if (ref.startsWith(CUSTOM)) {
			return ref.substring(CUSTOM.length()) + " (own)";
		}
		return ref.startsWith("minecraft:") ? ref.substring("minecraft:".length()) : ref;
	}

	// ------------------------------------------------------------------------------------------ own sounds
	public static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubo-sounds");
	}

	public static void openFolder() {
		try {
			Path d = folder();
			Files.createDirectories(d);
			net.minecraft.util.Util.getPlatform().openFile(d.toFile());
		} catch (IOException | RuntimeException ex) {
			Notify.show(Notify.Kind.ERROR, "Could not open the sounds folder", String.valueOf(ex.getMessage()));
		}
	}

	/** The .wav files in kubo-sounds/ (names only, sorted). */
	public static List<String> customFiles() {
		List<String> out = new ArrayList<>();
		try {
			Path d = folder();
			if (!Files.isDirectory(d)) {
				return out;
			}
			try (Stream<Path> s = Files.list(d)) {
				s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"))
						.forEach(p -> out.add(p.getFileName().toString()));
			}
		} catch (IOException | RuntimeException ignored) {
			// no folder yet
		}
		java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	private static Path customFile(String name) {
		Path d = folder().toAbsolutePath().normalize();
		Path f = d.resolve(name).normalize();
		return f.startsWith(d) && Files.isRegularFile(f) ? f : null;
	}

	/** Copies a .wav into kubo-sounds/ and returns its list id, or null when it can't be used (and says why). */
	public static String importFile(Path src) {
		try {
			String fn = src.getFileName().toString();
			if (!fn.toLowerCase(Locale.ROOT).endsWith(".wav")) {
				Notify.show(Notify.Kind.ERROR, "Only .wav files work", "Convert your sound to .wav first");
				return null;
			}
			if (Files.size(src) > MAX_WAV_BYTES) {
				Notify.show(Notify.Kind.ERROR, "That sound is too big", "Max 8 MB (use a short sound)");
				return null;
			}
			Path d = folder();
			Files.createDirectories(d);
			Path dst = d.resolve(fn);
			if (!dst.toAbsolutePath().normalize().equals(src.toAbsolutePath().normalize())) {
				Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
			}
			cache = null;
			Notify.show(Notify.Kind.DONE, "Sound added", fn);
			return CUSTOM + fn;
		} catch (IOException | RuntimeException ex) {
			Notify.show(Notify.Kind.ERROR, "Could not add the sound", String.valueOf(ex.getMessage()));
			return null;
		}
	}
}
