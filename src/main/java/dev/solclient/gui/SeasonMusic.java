package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.SolClient;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libc.LibCStdlib;

/**
 * Menu music of the season, from YOUR files: put .ogg or .mp3 songs in .minecraft/plegumo-music/christmas and
 * .../halloween (see the README.txt there). They play in the menus (not in a world) during the season, one after the
 * other, at Minecraft's Music volume; Minecraft's own menu music is silenced meanwhile. No files = normal Minecraft music.
 */
public final class SeasonMusic {

	private static MusicSound silence;
	private static int source, buffer;
	private static volatile boolean loading;
	private static volatile Decoded ready;
	private static String playingName, playingSeason;
	private static List<Path> queue = new ArrayList<>();
	private static long nextAt;
	private static boolean folderMade;
	/** Ticks to stay away from OpenAL after a (re)load: Minecraft's sound engine may not be up yet. */
	private static int quiet = 100;
	/** When the current song started and how long it is (for the fade in / fade out between songs). */
	private static long playStartMs;
	private static float durSec;
	/** A season playlist is running (Minecraft's own music stays silent also between two songs). */
	private static volatile boolean active;
	private static long checkedSongsAt;
	private static boolean hasSongs;
	private static final float FADE_IN = 2.5F, FADE_OUT = 3.5F;

	private record Decoded(ShortBuffer pcm, int channels, int rate, String name, String season, boolean stb) {
	}

	/** Frees the PCM data with the allocator that created it (stb = malloc, mp3 = LWJGL memAlloc). */
	private static void release(Decoded d) {
		if (d.stb) {
			LibCStdlib.free(d.pcm);
		} else {
			org.lwjgl.system.MemoryUtil.memFree(d.pcm);
		}
	}

	private SeasonMusic() {
	}

	private static Path root() {
		return MinecraftClient.getInstance().runDirectory.toPath().resolve("plegumo-music");
	}

	private static String seasonKey() {
		if (SolClient.settings == null || !SolClient.settings.seasonalMusic.on()) {
			return null;
		}
		int s = Season.current();
		return s == Season.CHRISTMAS ? "christmas" : s == Season.HALLOWEEN ? "halloween" : null;
	}

	private static void makeFolders() {
		if (folderMade) {
			return;
		}
		folderMade = true;
		try {
			Files.createDirectories(root().resolve("christmas"));
			Files.createDirectories(root().resolve("halloween"));
			Path readme = root().resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, """
						Plegumo seasonal menu music
						===========================
						Put songs in .ogg or .mp3 format here:
						  christmas/  -> plays in the menus from 1 December to 6 January
						  halloween/  -> plays in the menus in October (until 2 November)
						They play one after the other (shuffled), with Minecraft's Music volume.
						Files are only read on your computer; nothing is uploaded.
						If you share the client with others, only include music you are allowed to share
						(public domain / Creative Commons, e.g. Musopen, FreePD, incompetech.com).
						""");
			}
		} catch (Exception ignored) {
		}
		dev.solclient.social.Social.IO.execute(SeasonMusic::installBundled);
	}


	/** Songs shipped inside the jar (assets/sol_client/seasonal/<season>/). */
	private static final String[][] BUNDLED = {
		{"christmas", "Silent_Night.mp3", "We_Wish_You.mp3", "Jingle_Bells.mp3", "Jingle_Bells_3.mp3", "Deck_the_Halls_B.mp3"},
		{"halloween", "Toccata_and_Fugue_in_D_Minor.mp3", "Danse_Macabre.mp3"},
	};

	/**
	 * Copies the bundled songs to plegumo-music once (a marker file remembers it, so songs you delete do not come
	 * back). Runs in the background: it is a few MB.
	 */
	private static void installBundled() {
		try {
			Path marker = root().resolve(".bundled-v1");
			if (Files.exists(marker)) {
				return;
			}
			for (String[] row : BUNDLED) {
				Path dir = root().resolve(row[0]);
				Files.createDirectories(dir);
				for (int i = 1; i < row.length; i++) {
					Path out = dir.resolve(row[i]);
					if (Files.exists(out)) {
						continue;
					}
					try (java.io.InputStream in = SeasonMusic.class.getResourceAsStream("/assets/sol_client/seasonal/" + row[0] + "/" + row[i])) {
						if (in == null) {
							continue;
						}
						Path tmp = dir.resolve(row[i] + ".part");
						Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			Files.writeString(marker, "bundled songs installed");
			queue = new ArrayList<>(); // pick up the new files
		} catch (Throwable t) {
			System.err.println("[Caos] could not install the bundled songs: " + t);
		}
	}

	/** Called every client tick. */
	public static void tick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		makeFolders();
		// While Minecraft loads / reloads resources its sound engine is (re)starting. Touching OpenAL then breaks it
		// for good ("No ALCapabilities instance has been set" -> black screen), so do nothing until it is done.
		if (mc.getOverlay() != null || mc.getSoundManager() == null) {
			source = 0; // the old device is gone after a reload, forget our handles without calling OpenAL
			buffer = 0;
			playingName = null;
			playingSeason = null;
			quiet = 100;
			return;
		}
		if (quiet > 0) {
			quiet--;
			return;
		}
		cueTick();
		if (System.currentTimeMillis() < holdUntil) {
			mc.getMusicTracker().stop(); // a cue (the Christmas card) is on: nothing else plays
			return;
		}
		if (dev.solclient.mods.CinematicIntroMod.musicPlaying()) {
			if (source != 0) {
				stopPlayback(); // the intro music plays first
			}
			return;
		}
		// menus: the season's songs; in a world: the Christmas songs while Christmas World is on (instead of Minecraft's)
		String season = mc.world == null ? seasonKey() : dev.solclient.mods.ChristmasWorldMod.music() ? "christmas" : null;
		if (season != null && System.currentTimeMillis() - checkedSongsAt > 5000L) {
			checkedSongsAt = System.currentTimeMillis();
			hasSongs = anySong(season);
		}
		active = season != null && hasSongs;
		try {
			if (season == null || !season.equals(playingSeason) && source != 0) {
				stopPlayback();
				if (season == null) {
					return;
				}
			}
			if (source != 0) {
				// soft fade in at the start and fade out at the end, so one song flows into the next
				float t = (System.currentTimeMillis() - playStartMs) / 1000F;
				float fade = Math.min(1F, t / FADE_IN) * Math.max(0F, Math.min(1F, (durSec - t) / FADE_OUT));
				AL10.alSourcef(source, AL10.AL_GAIN, gain() * fade);
				if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
					stopPlayback();
					nextAt = System.currentTimeMillis() + 300L; // the next one is (usually) already decoded
				}
				// decode the next song in time, so there is no silence while it loads
				if (durSec - t < 15F && !loading && ready == null) {
					prefetch(season);
				}
				return;
			}
			Decoded d = ready;
			if (d != null) {
				ready = null;
				if (d.season.equals(season)) {
					start(d);
				} else {
					release(d);
				}
				return;
			}
			if (!loading && System.currentTimeMillis() >= nextAt) {
				prefetch(season);
			}
		} catch (Throwable t) {
			// sound device changed / not ready: start over later
			source = 0;
			buffer = 0;
			playingName = null;
			nextAt = System.currentTimeMillis() + 5000L;
		}
	}

	// ---------------------------------------------------------------- cues (short sounds that silence everything, e.g. the Christmas card)
	private static final java.util.Map<String, Decoded> CUES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<String> CUE_LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static int cueSrc, cueBuf;
	private static volatile long holdUntil;
	private static boolean duckedByCue;

	/** Decodes assets/sol_client/seasonal/cards/&lt;name&gt;.mp3 in the background so the cue starts the moment it is needed. */
	public static void preloadCue(String name) {
		if (CUES.containsKey(name) || !CUE_LOADING.add(name)) {
			return;
		}
		dev.solclient.social.Social.IO.execute(() -> {
			try (java.io.InputStream in = SeasonMusic.class.getResourceAsStream("/assets/sol_client/seasonal/cards/" + name + ".mp3")) {
				if (in != null) {
					CUES.put(name, decodeMp3(in, name, "cue"));
				}
			} catch (Throwable t) {
				System.err.println("[Caos] could not load the cue " + name + ": " + t);
			} finally {
				CUE_LOADING.remove(name);
			}
		});
	}

	/** Stops every sound and keeps the menu playlist (and an external player's volume) down for {@code ms}. */
	public static void hold(long ms) {
		MinecraftClient mc = MinecraftClient.getInstance();
		holdUntil = Math.max(holdUntil, System.currentTimeMillis() + ms);
		try {
			stopPlayback();
			mc.getSoundManager().stopAll();
			mc.getMusicTracker().stop();
		} catch (Throwable ignored) {
		}
		if (!duckedByCue) {
			duckedByCue = true;
			dev.solclient.music.MusicHub.duck(true);
		}
	}

	/** Plays a preloaded cue (master volume) and returns how long it lasts in ms, or 0 when it is not available. */
	public static long playCue(String name) {
		Decoded d = CUES.get(name);
		if (d == null) {
			preloadCue(name);
			return 0;
		}
		try {
			stopCue();
			cueBuf = AL10.alGenBuffers();
			long ms = Math.round(d.pcm().remaining() / (double) Math.max(1, d.channels()) / Math.max(1, d.rate()) * 1000.0);
			ShortBuffer pcm = d.pcm();
			pcm.rewind();
			AL10.alBufferData(cueBuf, d.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, d.rate());
			cueSrc = AL10.alGenSources();
			AL10.alSourcei(cueSrc, AL10.AL_BUFFER, cueBuf);
			AL10.alSourcei(cueSrc, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(cueSrc, AL10.AL_POSITION, 0F, 0F, 0F);
			AL10.alSourcef(cueSrc, AL10.AL_GAIN, MinecraftClient.getInstance().options.getSoundVolume(SoundCategory.MASTER));
			AL10.alSourcePlay(cueSrc);
			return ms;
		} catch (Throwable t) {
			cueSrc = 0;
			cueBuf = 0;
			return 0;
		}
	}

	public static void stopCue() {
		if (cueSrc != 0) {
			try {
				AL10.alSourceStop(cueSrc);
				AL10.alDeleteSources(cueSrc);
				AL10.alDeleteBuffers(cueBuf);
			} catch (Throwable ignored) {
			}
		}
		cueSrc = 0;
		cueBuf = 0;
	}

	/** Gives everything back right now (the card was closed before its sounds finished). */
	public static void release() {
		stopCue();
		holdUntil = 0;
	}

	private static void cueTick() {
		try {
			if (cueSrc != 0 && AL10.alGetSourcei(cueSrc, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
				stopCue();
			}
		} catch (Throwable t) {
			cueSrc = 0;
			cueBuf = 0;
		}
		if (duckedByCue && System.currentTimeMillis() >= holdUntil) {
			duckedByCue = false;
			dev.solclient.music.MusicHub.duck(false);
		}
	}

	private static void prefetch(String season) {
		Path next = nextFile(season);
		if (next == null) {
			nextAt = System.currentTimeMillis() + 5000L; // look for files again in a bit
			return;
		}
		loading = true;
		String s = season;
		dev.solclient.social.Social.IO.execute(() -> {
			try {
				ready = decode(next, s);
			} catch (Throwable t) {
				System.err.println("[Caos] could not play " + next.getFileName() + ": " + t);
				nextAt = System.currentTimeMillis() + 1000L;
			} finally {
				loading = false;
			}
		});
	}

	/** Is there at least one song of this season that is turned on? */
	private static boolean anySong(String season) {
		try (Stream<Path> s = Files.list(root().resolve(season))) {
			return s.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(ogg|mp3)$")
					&& (!season.equals("christmas") || XmasChoices.songOn(p)));
		} catch (Exception e) {
			return false;
		}
	}

	private static Path nextFile(String season) {
		if (queue.isEmpty() || !queue.get(0).getParent().getFileName().toString().equals(season)) {
			List<Path> files = new ArrayList<>();
			try (Stream<Path> s = Files.list(root().resolve(season))) {
				// Christmas: only the songs you left on (Christmas World > Songs)
				s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(ogg|mp3)$"))
						.filter(p -> !season.equals("christmas") || XmasChoices.songOn(p)).forEach(files::add);
			} catch (Exception ignored) {
			}
			Collections.shuffle(files);
			queue = files;
		}
		while (!queue.isEmpty() && season.equals("christmas") && !XmasChoices.songOn(queue.get(0))) {
			queue.remove(0); // turned off after the list was made
		}
		return queue.isEmpty() ? null : queue.remove(0);
	}

	private static Decoded decode(Path file, String season) throws Exception {
		String n = file.getFileName().toString().replaceAll("(?i)\\.(ogg|mp3)$", "").replace('_', ' ');
		if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
			return decodeMp3(file, n, season);
		}
		try (MemoryStack st = MemoryStack.stackPush()) {
			IntBuffer ch = st.mallocInt(1), rate = st.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(file.toAbsolutePath().toString(), ch, rate);
			if (pcm == null) {
				throw new IllegalStateException("not a valid .ogg (Vorbis) file");
			}
			return new Decoded(pcm, ch.get(0), rate.get(0), n, season, true);
		}
	}

	/** MP3 through JLayer (pure Java, shipped inside the Caos jar). */
	private static Decoded decodeMp3(Path file, String name, String season) throws Exception {
		try (java.io.InputStream in = Files.newInputStream(file)) {
			return decodeMp3(in, name, season);
		}
	}

	private static Decoded decodeMp3(java.io.InputStream raw, String name, String season) throws Exception {
		short[] all = new short[1 << 20];
		int len = 0, rate = 0, channels = 0;
		try (java.io.InputStream in = new java.io.BufferedInputStream(raw, 1 << 16)) {
			javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(in);
			javazoom.jl.decoder.Decoder dec = new javazoom.jl.decoder.Decoder();
			javazoom.jl.decoder.Header h;
			while ((h = bs.readFrame()) != null) {
				javazoom.jl.decoder.SampleBuffer sb = (javazoom.jl.decoder.SampleBuffer) dec.decodeFrame(h, bs);
				rate = sb.getSampleFrequency();
				channels = sb.getChannelCount();
				int n = sb.getBufferLength();
				if (len + n > all.length) {
					all = java.util.Arrays.copyOf(all, Math.max(all.length * 2, len + n));
				}
				System.arraycopy(sb.getBuffer(), 0, all, len, n);
				len += n;
				bs.closeFrame();
			}
			bs.close();
		}
		if (len == 0 || rate == 0) {
			throw new IllegalStateException("not a valid .mp3 file");
		}
		ShortBuffer pcm = org.lwjgl.system.MemoryUtil.memAllocShort(len);
		pcm.put(all, 0, len).flip();
		return new Decoded(pcm, channels, rate, name, season, false);
	}

	private static void start(Decoded d) {
		MinecraftClient.getInstance().getMusicTracker().stop();
		buffer = AL10.alGenBuffers();
		durSec = d.pcm.remaining() / (float) Math.max(1, d.channels) / Math.max(1, d.rate);
		playStartMs = System.currentTimeMillis();
		AL10.alBufferData(buffer, d.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, d.pcm, d.rate);
		release(d);
		source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
		AL10.alSource3f(source, AL10.AL_POSITION, 0F, 0F, 0F);
		AL10.alSourcef(source, AL10.AL_GAIN, 0F); // fades in
		AL10.alSourcePlay(source);
		playingName = d.name;
		playingSeason = d.season;
	}

	private static void stopPlayback() {
		if (source != 0) {
			try {
				AL10.alSourceStop(source);
				AL10.alDeleteSources(source);
				AL10.alDeleteBuffers(buffer);
			} catch (Throwable ignored) {
			}
		}
		source = 0;
		buffer = 0;
		playingName = null;
		playingSeason = null;
	}

	private static float gain() {
		MinecraftClient mc = MinecraftClient.getInstance();
		float v = mc.options.getSoundVolume(SoundCategory.MASTER) * mc.options.getSoundVolume(SoundCategory.MUSIC);
		return mc.world != null ? v * dev.solclient.mods.ChristmasWorldMod.musicVolume() : v;
	}

	/** While one of your songs plays, Minecraft's music is replaced by silence. */
	public static MusicSound current() {
		if (!active && source == 0 && !loading && ready == null) {
			return null;
		}
		if (silence == null) {
			silence = new MusicSound(RegistryEntry.of(SoundEvent.of(Gfx.id("music.silence"))), 20, 40, true);
		}
		return silence;
	}

	public static String trackName() {
		if (playingName != null) {
			return playingName;
		}
		String s = seasonKey();
		if (s != null && MinecraftClient.getInstance().world == null) {
			return "Minecraft music (add .ogg or .mp3 songs to plegumo-music/" + s + ")";
		}
		return "Minecraft music";
	}

	// ---------------------------------------------------------------- volume (Minecraft's Music slider)
	private static SimpleOption<Double> option() {
		return MinecraftClient.getInstance().options.getSoundVolumeOption(SoundCategory.MUSIC);
	}

	public static float volume() {
		return option().getValue().floatValue();
	}

	private static double beforeMute = 0.5;

	public static void setVolume(float v) {
		option().setValue((double) Math.max(0F, Math.min(1F, v)));
	}

	public static void toggleMute() {
		double v = option().getValue();
		if (v > 0.001) {
			beforeMute = v;
			option().setValue(0.0);
		} else {
			option().setValue(beforeMute < 0.05 ? 0.5 : beforeMute);
		}
		save();
	}

	public static void save() {
		MinecraftClient.getInstance().options.write();
	}
}
