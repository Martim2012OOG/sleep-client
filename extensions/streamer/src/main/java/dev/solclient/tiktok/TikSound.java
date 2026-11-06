package dev.solclient.tiktok;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libc.LibCStdlib;

/**
 * Plays YOUR sound files (.ogg, .mp3, .wav) for TikTok events.
 *
 * No lag by design: the file is read and decoded on a background thread (once, then cached in memory), and the game
 * thread only hands the ready PCM to OpenAL. At most {@link #MAX_VOICES} sounds play at once; extra ones are skipped.
 */
public final class TikSound {

	private static final int MAX_VOICES = 4;
	private static final int MAX_SAMPLES = 48000 * 2 * 45; // ~45 s of stereo: more than enough for an alert
	private static final Map<String, Pcm> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<Play> READY = new ConcurrentLinkedQueue<>();
	private static final List<int[]> VOICES = new ArrayList<>(); // {source, buffer}
	/** Which file each voice plays (same order as VOICES): the same sound never plays twice at once. */
	private static final List<String> VOICE_KEYS = new ArrayList<>();
	/** Ticks to stay away from OpenAL after a (re)load of the sound engine. */
	private static int quiet = 100;

	private record Pcm(short[] data, int channels, int rate, long modified) {
	}

	private record Play(Pcm pcm, float volume, String key) {
	}

	private TikSound() {
	}

	/** Starts a sound file. Safe to call from any thread; does nothing if the file is unreadable. */
	public static void play(Path file, float volume) {
		if (file == null || volume <= 0.001F) {
			return;
		}
		String key = file.toAbsolutePath().toString();
		long mod;
		try {
			mod = Files.getLastModifiedTime(file).toMillis();
		} catch (Exception e) {
			return;
		}
		Pcm cached = CACHE.get(key);
		if (cached != null && cached.modified == mod) {
			READY.add(new Play(cached, volume, key));
			return;
		}
		if (LOADING.putIfAbsent(key, Boolean.TRUE) != null) {
			return; // already being decoded: this trigger is skipped, the next one will have it
		}
		dev.solclient.social.Social.IO.execute(() -> {
			try {
				Pcm p = decode(file, mod);
				CACHE.put(key, p);
				READY.add(new Play(p, volume, key));
			} catch (Throwable t) {
				System.err.println("[Sleep TikTok] could not play " + file.getFileName() + ": " + t);
			} finally {
				LOADING.remove(key);
			}
		});
	}

	/** Every client tick: starts what is ready, frees what finished. */
	public static void tick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		// While Minecraft (re)loads its sound engine, touching OpenAL breaks it: stay away (same rule as SeasonMusic)
		if (mc.getOverlay() != null || mc.getSoundManager() == null) {
			VOICES.clear();
			VOICE_KEYS.clear();
			READY.clear();
			quiet = 100;
			return;
		}
		if (quiet > 0) {
			quiet--;
			return;
		}
		try {
			for (int i = VOICES.size() - 1; i >= 0; i--) {
				int[] v = VOICES.get(i);
				if (AL10.alGetSourcei(v[0], AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
					AL10.alDeleteSources(v[0]);
					AL10.alDeleteBuffers(v[1]);
					VOICES.remove(i);
					VOICE_KEYS.remove(i);
				}
			}
			Play p;
			int started = 0;
			while (started < 2 && (p = READY.poll()) != null) {
				if (VOICES.size() >= MAX_VOICES) {
					continue; // too many at once: skip
				}
				if (VOICE_KEYS.contains(p.key)) {
					continue; // this sound is still playing: let it play on, no second copy on top
				}
				start(p);
				started++;
			}
		} catch (Throwable t) {
			VOICES.clear();
			VOICE_KEYS.clear();
			READY.clear();
			quiet = 100;
		}
	}

	/** True while one of your sounds is playing (the background music goes quieter). */
	public static boolean playing() {
		return !VOICES.isEmpty() || !READY.isEmpty();
	}

	public static void stopAll() {
		READY.clear();
		try {
			for (int[] v : VOICES) {
				AL10.alSourceStop(v[0]);
				AL10.alDeleteSources(v[0]);
				AL10.alDeleteBuffers(v[1]);
			}
		} catch (Throwable ignored) {
			// sound engine is gone already
		}
		VOICES.clear();
		VOICE_KEYS.clear();
	}

	private static void start(Play p) {
		Pcm d = p.pcm;
		int buffer = AL10.alGenBuffers();
		AL10.alBufferData(buffer, d.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, d.data, d.rate);
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE); // plays "in your head", not from a place in the world
		AL10.alSource3f(source, AL10.AL_POSITION, 0F, 0F, 0F);
		MinecraftClient mc = MinecraftClient.getInstance();
		float gain = p.volume * mc.options.getSoundVolume(SoundCategory.MASTER);
		AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0F, Math.min(1F, gain)));
		AL10.alSourcePlay(source);
		VOICES.add(new int[] {source, buffer});
		VOICE_KEYS.add(p.key);
	}

	/** File called name.mp3 / name.ogg / name.wav (any case) in a folder, or null. */
	public static Path find(Path dir, String name) {
		if (dir == null || name == null || name.isBlank() || !Files.isDirectory(dir)) {
			return null;
		}
		String want = name.toLowerCase(Locale.ROOT);
		for (String ext : new String[] {".mp3", ".ogg", ".wav"}) {
			Path direct = dir.resolve(name + ext);
			if (Files.isRegularFile(direct)) {
				return direct;
			}
		}
		try (var s = Files.list(dir)) {
			return s.filter(Files::isRegularFile).filter(f -> {
				String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
				int dot = n.lastIndexOf('.');
				return dot > 0 && n.substring(0, dot).equals(want) && supported(n);
			}).findFirst().orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	public static boolean supported(String fileName) {
		String n = fileName.toLowerCase(Locale.ROOT);
		return n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav");
	}

	private static Pcm decode(Path file, long modified) throws Exception {
		String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (n.endsWith(".mp3")) {
			return mp3(file, modified);
		}
		if (n.endsWith(".ogg")) {
			return ogg(file, modified);
		}
		if (n.endsWith(".wav")) {
			return wav(file, modified);
		}
		throw new IllegalArgumentException("use .mp3, .ogg or .wav");
	}

	private static Pcm ogg(Path file, long modified) {
		try (MemoryStack st = MemoryStack.stackPush()) {
			IntBuffer ch = st.mallocInt(1), rate = st.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(file.toAbsolutePath().toString(), ch, rate);
			if (pcm == null) {
				throw new IllegalStateException("not a valid .ogg (Vorbis) file");
			}
			try {
				int len = Math.min(pcm.remaining(), MAX_SAMPLES);
				short[] out = new short[len];
				pcm.get(out, 0, len);
				return new Pcm(out, Math.min(2, ch.get(0)), rate.get(0), modified);
			} finally {
				LibCStdlib.free(pcm);
			}
		}
	}

	private static Pcm mp3(Path file, long modified) throws Exception {
		short[] all = new short[1 << 18];
		int len = 0, rate = 0, channels = 0;
		try (InputStream in = new java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
			javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(in);
			javazoom.jl.decoder.Decoder dec = new javazoom.jl.decoder.Decoder();
			javazoom.jl.decoder.Header h;
			while ((h = bs.readFrame()) != null && len < MAX_SAMPLES) {
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
		return new Pcm(java.util.Arrays.copyOf(all, len), Math.min(2, channels), rate, modified);
	}

	/** WAV (any PCM depth) through javax.sound, converted to 16-bit little endian. */
	private static Pcm wav(Path file, long modified) throws Exception {
		try (javax.sound.sampled.AudioInputStream raw = javax.sound.sampled.AudioSystem.getAudioInputStream(file.toFile())) {
			javax.sound.sampled.AudioFormat f = raw.getFormat();
			int ch = Math.min(2, Math.max(1, f.getChannels()));
			javax.sound.sampled.AudioFormat target = new javax.sound.sampled.AudioFormat(
					javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, f.getChannels(), f.getChannels() * 2,
					f.getSampleRate(), false);
			try (javax.sound.sampled.AudioInputStream pcm = javax.sound.sampled.AudioSystem.getAudioInputStream(target, raw)) {
				ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
				byte[] buf = new byte[1 << 15];
				int r;
				while ((r = pcm.read(buf)) > 0 && bo.size() < MAX_SAMPLES * 2) {
					bo.write(buf, 0, r);
				}
				byte[] b = bo.toByteArray();
				int n = b.length / 2;
				short[] out = new short[n];
				for (int i = 0; i < n; i++) {
					out[i] = (short) ((b[i * 2] & 0xFF) | (b[i * 2 + 1] << 8));
				}
				if (n == 0) {
					throw new IllegalStateException("empty .wav");
				}
				return new Pcm(out, ch, Math.round(f.getSampleRate()), modified);
			}
		}
	}
}
