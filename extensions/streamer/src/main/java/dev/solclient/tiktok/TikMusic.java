package dev.solclient.tiktok;

import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Background music for lives: your 1 or 2 songs (.mp3, .ogg, .wav) play one after the other, forever.
 *
 * Streamed, so long songs cost almost no memory and never lag the game: a background thread decodes about half a
 * second at a time into a small queue (a few seconds ahead), and the game thread only feeds those pieces to one
 * OpenAL source. It goes quieter while a donation sound plays.
 */
public final class TikMusic {

	private record Chunk(short[] data, int channels, int rate, int gen) {
	}

	private static final int AL_BUFFERS = 4;
	private static final ArrayBlockingQueue<Chunk> QUEUE = new ArrayBlockingQueue<>(10);

	private static volatile List<Path> playlist = List.of();
	private static volatile int gen;
	private static Thread decoder;
	private static int source;
	private static final int[] buffers = new int[AL_BUFFERS];
	private static int curChannels, curRate;
	private static Chunk pending;
	private static float duck = 1F;
	private static int quiet = 100;
	private static volatile String now = "";

	private TikMusic() {
	}

	/** Name of the song playing, or "". */
	public static String playing() {
		return now;
	}

	/**
	 * Call every client tick. {@code songs} = the files to loop (empty = silence), {@code volume} 0..1, {@code duckNow}
	 * = a donation sound is playing.
	 */
	public static void tick(List<Path> songs, float volume, boolean duckNow) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getOverlay() != null || mc.getSoundManager() == null) {
			dropSource(); // the sound engine is (re)loading: OpenAL ids are gone
			quiet = 100;
			return;
		}
		if (!songs.equals(playlist)) {
			playlist = List.copyOf(songs);
			restartDecoder();
		}
		if (songs.isEmpty() || volume <= 0.001F) {
			stopSource();
			return;
		}
		if (quiet > 0) {
			quiet--;
			return;
		}
		try {
			feed();
			duck += ((duckNow ? 0.3F : 1F) - duck) * 0.08F;
			if (source != 0) {
				float gain = volume * duck * mc.options.getSoundVolume(SoundCategory.MASTER);
				AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0F, Math.min(1F, gain)));
			}
		} catch (Throwable t) {
			dropSource();
			quiet = 100;
		}
	}

	/** Stops the music and the decoder (mod turned off). */
	public static void stop() {
		playlist = List.of();
		restartDecoder();
		stopSource();
	}

	/** Starts the songs again from the first one (after you pick a new file). */
	public static void restart() {
		playlist = List.of(); // the next tick sees a "new" playlist
		stopSource();
	}

	// ---------------------------------------------------------------- game thread: OpenAL
	private static void feed() {
		if (source == 0) {
			source = AL10.alGenSources();
			AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(source, AL10.AL_POSITION, 0F, 0F, 0F);
			FREE.clear();
			for (int i = 0; i < AL_BUFFERS; i++) {
				buffers[i] = AL10.alGenBuffers();
				FREE.add(buffers[i]);
			}
			curChannels = 0;
		}
		// buffers that finished playing come back
		int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
		while (done-- > 0) {
			FREE.add(AL10.alSourceUnqueueBuffers(source));
		}
		while (!FREE.isEmpty()) {
			Chunk c = pending != null ? pending : QUEUE.poll();
			pending = null;
			if (c == null) {
				break;
			}
			if (c.gen != gen) {
				continue; // from a playlist that was replaced
			}
			if (curChannels != 0 && (c.channels != curChannels || c.rate != curRate) && FREE.size() < AL_BUFFERS) {
				pending = c; // next song has another format: let the old pieces finish first
				break;
			}
			curChannels = c.channels;
			curRate = c.rate;
			int b = FREE.poll();
			ShortBuffer sb = BufferUtils.createShortBuffer(c.data.length);
			sb.put(c.data).flip();
			AL10.alBufferData(b, c.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, sb, c.rate);
			AL10.alSourceQueueBuffers(source, b);
		}
		if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) > 0) {
			AL10.alSourcePlay(source);
		}
	}

	/** OpenAL buffers that are not queued (they come back when they finished playing). */
	private static final java.util.ArrayDeque<Integer> FREE = new java.util.ArrayDeque<>();

	private static void stopSource() {
		if (source == 0) {
			return;
		}
		try {
			AL10.alSourceStop(source);
			AL10.alSourcei(source, AL10.AL_BUFFER, 0);
			AL10.alDeleteSources(source);
			AL10.alDeleteBuffers(buffers);
		} catch (Throwable ignored) {
			// engine already gone
		}
		dropSource();
	}

	private static void dropSource() {
		source = 0;
		curChannels = 0;
		pending = null;
		FREE.clear();
		now = "";
	}

	// ---------------------------------------------------------------- background thread: decoding
	private static synchronized void restartDecoder() {
		final int g = ++gen;
		QUEUE.clear();
		pending = null;
		if (decoder != null) {
			decoder.interrupt();
		}
		List<Path> songs = playlist;
		if (songs.isEmpty()) {
			decoder = null;
			return;
		}
		decoder = new Thread(() -> {
			int i = 0, fails = 0;
			while (g == gen && fails < songs.size() * 3) {
				Path p = songs.get(i % songs.size());
				i++;
				now = p.getFileName().toString();
				try {
					decode(p, g);
					fails = 0;
				} catch (InterruptedException e) {
					return;
				} catch (Throwable t) {
					fails++;
					System.err.println("[Caos TikTok] background music: " + p.getFileName() + ": " + t);
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}, "kubo-tiktok-music");
		decoder.setDaemon(true);
		decoder.start();
	}

	private static void put(short[] data, int len, int ch, int rate, int g) throws InterruptedException {
		if (len <= 0) {
			return;
		}
		Chunk c = new Chunk(len == data.length ? data : java.util.Arrays.copyOf(data, len), ch, rate, g);
		while (g == gen) {
			if (QUEUE.offer(c, 200, TimeUnit.MILLISECONDS)) {
				return;
			}
		}
		throw new InterruptedException();
	}

	private static void decode(Path file, int g) throws Exception {
		String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (n.endsWith(".mp3")) {
			mp3(file, g);
		} else if (n.endsWith(".ogg")) {
			ogg(file, g);
		} else if (n.endsWith(".wav")) {
			wav(file, g);
		} else {
			throw new IllegalArgumentException("use .mp3, .ogg or .wav");
		}
	}

	private static void mp3(Path file, int g) throws Exception {
		try (InputStream in = new java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
			javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(in);
			javazoom.jl.decoder.Decoder dec = new javazoom.jl.decoder.Decoder();
			short[] buf = new short[48000];
			int len = 0, rate = 0, ch = 0;
			javazoom.jl.decoder.Header h;
			while (g == gen && (h = bs.readFrame()) != null) {
				javazoom.jl.decoder.SampleBuffer sb = (javazoom.jl.decoder.SampleBuffer) dec.decodeFrame(h, bs);
				rate = sb.getSampleFrequency();
				ch = Math.min(2, sb.getChannelCount());
				int k = sb.getBufferLength();
				if (len + k > buf.length) {
					put(buf, len, ch, rate, g);
					buf = new short[48000];
					len = 0;
				}
				System.arraycopy(sb.getBuffer(), 0, buf, len, k);
				len += k;
				bs.closeFrame();
			}
			put(buf, len, ch, rate, g);
			bs.close();
		}
	}

	private static void ogg(Path file, int g) throws Exception {
		long v;
		int ch, rate;
		try (MemoryStack st = MemoryStack.stackPush()) {
			IntBuffer err = st.mallocInt(1);
			v = STBVorbis.stb_vorbis_open_filename(file.toAbsolutePath().toString(), err, null);
			if (v == 0) {
				throw new IllegalStateException("not a valid .ogg (Vorbis) file");
			}
			var info = org.lwjgl.stb.STBVorbisInfo.malloc(st);
			STBVorbis.stb_vorbis_get_info(v, info);
			ch = Math.min(2, info.channels());
			rate = info.sample_rate();
		}
		ShortBuffer sb = MemoryUtil.memAllocShort(24000 * ch);
		try {
			while (g == gen) {
				sb.clear();
				int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(v, ch, sb);
				if (frames <= 0) {
					break;
				}
				short[] out = new short[frames * ch];
				sb.get(out, 0, out.length);
				put(out, out.length, ch, rate, g);
			}
		} finally {
			MemoryUtil.memFree(sb);
			STBVorbis.stb_vorbis_close(v);
		}
	}

	private static void wav(Path file, int g) throws Exception {
		try (javax.sound.sampled.AudioInputStream raw = javax.sound.sampled.AudioSystem.getAudioInputStream(file.toFile())) {
			javax.sound.sampled.AudioFormat f = raw.getFormat();
			int ch = Math.min(2, Math.max(1, f.getChannels()));
			javax.sound.sampled.AudioFormat target = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
					f.getSampleRate(), 16, ch, ch * 2, f.getSampleRate(), false);
			try (javax.sound.sampled.AudioInputStream pcm = javax.sound.sampled.AudioSystem.getAudioInputStream(target, raw)) {
				byte[] b = new byte[96000];
				int r;
				while (g == gen && (r = pcm.readNBytes(b, 0, b.length)) > 0) {
					short[] out = new short[r / 2];
					for (int i = 0; i < out.length; i++) {
						out[i] = (short) ((b[i * 2] & 0xFF) | (b[i * 2 + 1] << 8));
					}
					put(out, out.length, ch, Math.round(f.getSampleRate()), g);
				}
			}
		}
	}
}
