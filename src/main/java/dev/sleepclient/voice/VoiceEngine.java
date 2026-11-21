package dev.sleepclient.voice;

import dev.sleepclient.social.Presence;
import dev.sleepclient.voice.persona.PersonaHook;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTThreadLocalContext;

/**
 * Proximity voice chat engine.
 *
 * - Microphone: OpenAL capture, 16 kHz mono, 20 ms frames, gain, voice activation (threshold + 300 ms hangover)
 *   or push-to-talk, encoded with IMA ADPCM and sent to the presence Worker's room for this server.
 * - Speakers: our OWN OpenAL device + context on our own thread (EXT_thread_local_context), so Minecraft's sound
 *   engine is never touched. One streaming source per person, placed at their head (3D) and faded with distance.
 * - Only players loaded in your world and within the distance are heard (true proximity chat).
 */
public final class VoiceEngine implements Presence.VoiceLink {

	public static final int RATE = 16000;

	public static final VoiceEngine INSTANCE = new VoiceEngine();

	// ---- state read by the UI
	public volatile boolean running;
	public volatile String error;
	public volatile float micDb = -90F;
	public volatile boolean talking;
	public volatile boolean testing;
	/** The microphone gives only silence (no permission / wrong device). */
	public volatile boolean silentMic;
	private long openedAt, lastSound;

	// ---- inputs written by the client thread (VoiceChatMod.tick)
	volatile boolean pttDown;
	volatile double lx, ly, lz;
	volatile float lyaw, lpitch;
	final Map<String, double[]> positions = new ConcurrentHashMap<>();

	/** Other people: audio queue + what the Worker told us about them. */
	static final class Peer {
		final ConcurrentLinkedDeque<short[]> queue = new ConcurrentLinkedDeque<>();
		volatile long lastAudio;
		volatile boolean on, muted, deafened;
		int source = -1;
		final List<Integer> freeBuffers = new ArrayList<>();
		boolean playing;
	}

	final Map<String, Peer> peers = new ConcurrentHashMap<>();
	private final ConcurrentLinkedDeque<short[]> selfQueue = new ConcurrentLinkedDeque<>();

	private Thread capture, output;
	private int seq;

	private VoiceEngine() {
	}

	// ------------------------------------------------------------------ devices
	public static List<String> microphones() {
		try {
			List<String> l = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
			return l == null ? List.of() : l;
		} catch (Throwable t) {
			return List.of();
		}
	}

	public static List<String> speakers() {
		try {
			List<String> l = ALUtil.getStringList(0L, 0x1013 /* ALC_ALL_DEVICES_SPECIFIER */);
			if (l == null || l.isEmpty()) {
				l = ALUtil.getStringList(0L, ALC10.ALC_DEVICE_SPECIFIER);
			}
			return l == null ? List.of() : l;
		} catch (Throwable t) {
			return List.of();
		}
	}

	// ------------------------------------------------------------------ lifecycle
	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		error = null;
		// (voice goes through VoiceNet since v99)
		capture = new Thread(this::captureLoop, "plegumo-voice-mic");
		output = new Thread(this::outputLoop, "plegumo-voice-out");
		capture.setDaemon(true);
		output.setDaemon(true);
		capture.start();
		output.start();
	}

	public synchronized void stop() {
		running = false;

		talking = false;
		peers.clear();
		selfQueue.clear();
		Thread c = capture, o = output;
		capture = output = null;
		try {
			if (c != null) {
				c.join(500);
			}
			if (o != null) {
				o.join(500);
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	/** Device changed in the settings: reopen everything. */
	public void restart() {
		stop();
		start();
	}

	// ------------------------------------------------------------------ network in
	@Override
	public void audio(String uuid, byte[] msg, int off) {
		VoiceChatMod m = VoiceChatMod.instance;
		if (!running || m == null || m.deafened.on() || msg.length - off < 2 + Adpcm.BYTES) {
			return;
		}
		Peer p = peers.computeIfAbsent(uuid, k -> new Peer());
		p.lastAudio = System.currentTimeMillis();
		if (m.mutedPlayer(uuid) || !VoiceNet.groupMode() && posOf(uuid) == null) {
			return; // muted by you, or (server voice) not near you / not loaded in your world
		}
		short[] pcm = Adpcm.decode(msg, off + 2);
		p.queue.addLast(pcm);
		while (p.queue.size() > 12) {
			p.queue.pollFirst(); // never fall far behind
		}
	}

	@Override
	public void state(String uuid, boolean on, boolean muted, boolean deafened) {
		if (uuid == null) {
			return;
		}
		Peer p = peers.computeIfAbsent(uuid, k -> new Peer());
		p.on = on;
		p.muted = muted;
		p.deafened = deafened;
	}

	/**
	 * Where this person is in your world: by their account UUID, or by name (offline-mode servers give players
	 * different UUIDs than their Minecraft accounts, so the name is the only thing that matches there).
	 */
	double[] posOf(String uuid) {
		double[] p = positions.get(uuid);
		if (p == null) {
			VoiceNet.Member mm = VoiceNet.members().get(uuid);
			if (mm != null && mm.name() != null) {
				p = positions.get("n:" + mm.name().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return p;
	}

	public boolean isTalking(String uuid) {
		Peer p = peers.get(uuid);
		return p != null && System.currentTimeMillis() - p.lastAudio < 300;
	}

	public Peer peer(String uuid) {
		return peers.get(uuid);
	}

	// ------------------------------------------------------------------ microphone
	private void captureLoop() {
		VoiceChatMod m = VoiceChatMod.instance;
		long dev = 0;
		try {
			String name = VoiceConfig.mic();
			dev = ALC11.alcCaptureOpenDevice(name == null || name.isEmpty() ? null : name, RATE, AL10.AL_FORMAT_MONO16, RATE);
			if (dev == 0 && name != null) {
				dev = ALC11.alcCaptureOpenDevice((CharSequence) null, RATE, AL10.AL_FORMAT_MONO16, RATE);
			}
			if (dev == 0) {
				error = "Could not open the microphone";
				return;
			}
			ALC11.alcCaptureStart(dev);
			openedAt = System.currentTimeMillis();
			lastSound = openedAt;
			ShortBuffer buf = BufferUtils.createShortBuffer(Adpcm.SAMPLES);
			long lastLoud = 0;
			while (running) {
				int avail = ALC10.alcGetInteger(dev, ALC11.ALC_CAPTURE_SAMPLES);
				if (avail < Adpcm.SAMPLES) {
					Thread.sleep(4);
					continue;
				}
				buf.clear();
				ALC11.alcCaptureSamples(dev, buf, Adpcm.SAMPLES);
				short[] pcm = new short[Adpcm.SAMPLES];
				buf.get(0, pcm);
				PersonaHook.fallback(pcm, RATE); // character (only when the OpenAL hook is not in)
				// gain + level
				float gain = (float) Math.pow(10, m.micGain.f() / 20.0);
				double sum = 0;
				for (int i = 0; i < pcm.length; i++) {
					int v = Math.round(pcm[i] * gain);
					v = Math.max(-32768, Math.min(32767, v));
					pcm[i] = (short) v;
					sum += (double) v * v;
				}
				double rms = Math.sqrt(sum / pcm.length);
				float db = rms <= 1 ? -90F : (float) (20 * Math.log10(rms / 32768.0));
				micDb = db;
				long now = System.currentTimeMillis();
				boolean active;
				if (m.activation.idx() == 1) {
					active = pttDown;
				} else {
					if (db > m.threshold.f()) {
						lastLoud = now;
					}
					active = now - lastLoud < 300;
				}
				boolean send = active && !m.muted.on() && !m.deafened.on();
				talking = send;
				if (testing) {
					selfQueue.addLast(pcm);
					while (selfQueue.size() > 10) {
						selfQueue.pollFirst();
					}
				}
				// no sound at all for a while: on a Mac the game may not have microphone permission
				if (db > -89F) {
					lastSound = now;
				}
				silentMic = now - lastSound > 6_000L && now - openedAt > 6_000L;
				if (send && VoiceNet.ready()) {
					byte[] enc = Adpcm.encode(pcm);
					byte[] frame = new byte[3 + enc.length];
					frame[0] = 1;
					frame[1] = (byte) (seq >> 8);
					frame[2] = (byte) seq;
					seq = (seq + 1) & 0xFFFF;
					System.arraycopy(enc, 0, frame, 3, enc.length);
					VoiceNet.sendAudio(frame);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			error = "Microphone error: " + t.getClass().getSimpleName();
			System.err.println("[Sleep] voice mic: " + t);
		} finally {
			talking = false;
			if (dev != 0) {
				try {
					ALC11.alcCaptureStop(dev);
					ALC11.alcCaptureCloseDevice(dev);
				} catch (Throwable ignored) {
					// closing anyway
				}
			}
		}
	}

	// ------------------------------------------------------------------ speakers
	private void outputLoop() {
		long dev = 0, ctx = 0;
		int selfSource = -1;
		List<Integer> selfFree = new ArrayList<>();
		try {
			String name = VoiceConfig.speaker();
			dev = ALC10.alcOpenDevice(name == null || name.isEmpty() ? null : name);
			if (dev == 0 && name != null) {
				dev = ALC10.alcOpenDevice((CharSequence) null);
			}
			if (dev == 0) {
				error = "Could not open the speakers";
				return;
			}
			ALCCapabilities alcCaps = ALC.createCapabilities(dev);
			if (!alcCaps.ALC_EXT_thread_local_context) {
				error = "Your OpenAL has no thread contexts (voice output off)";
				return;
			}
			ctx = ALC10.alcCreateContext(dev, (java.nio.IntBuffer) null);
			EXTThreadLocalContext.alcSetThreadContext(ctx);
			ALCapabilities caps = AL.createCapabilities(alcCaps);
			AL.setCurrentThread(caps);
			AL10.alDistanceModel(AL11.AL_LINEAR_DISTANCE_CLAMPED);
			ShortBuffer pcmBuf = BufferUtils.createShortBuffer(Adpcm.SAMPLES);
			while (running) {
				VoiceChatMod m = VoiceChatMod.instance;
				// listener = your camera
				AL10.alListener3f(AL10.AL_POSITION, (float) lx, (float) ly, (float) lz);
				double yaw = Math.toRadians(lyaw), pitch = Math.toRadians(lpitch);
				float fx = (float) (-Math.sin(yaw) * Math.cos(pitch)), fy = (float) -Math.sin(pitch), fz = (float) (Math.cos(yaw) * Math.cos(pitch));
				AL10.alListenerfv(AL10.AL_ORIENTATION, new float[]{fx, fy, fz, 0F, 1F, 0F});
				AL10.alListenerf(AL10.AL_GAIN, m.volume.f() / 100F);
				float maxDist = m.distance.f();
				boolean spatial = m.spatial.on();

				for (Map.Entry<String, Peer> e : peers.entrySet()) {
					Peer p = e.getValue();
					double[] pos = posOf(e.getKey());
					if (p.source == -1) {
						if (p.queue.isEmpty()) {
							continue;
						}
						p.source = AL10.alGenSources();
						AL10.alSourcef(p.source, AL10.AL_REFERENCE_DISTANCE, 2F);
						AL10.alSourcef(p.source, AL10.AL_ROLLOFF_FACTOR, 1F);
					}
					AL10.alSourcef(p.source, AL10.AL_MAX_DISTANCE, maxDist);
					AL10.alSourcef(p.source, AL10.AL_GAIN, VoiceConfig.playerVolume(e.getKey()));
					boolean group = VoiceNet.groupMode();
					if (group) {
						// voice group: no distance, everybody in the middle at their own volume
						AL10.alSourcei(p.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
						AL10.alSource3f(p.source, AL10.AL_POSITION, 0F, 0F, 0F);
						AL10.alSourcef(p.source, AL10.AL_MAX_DISTANCE, 1000F);
					} else if (pos == null) {
						p.queue.clear();
					} else if (spatial) {
						AL10.alSourcei(p.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
						AL10.alSource3f(p.source, AL10.AL_POSITION, (float) pos[0], (float) pos[1], (float) pos[2]);
					} else {
						// no 3D: centred, louder when close
						AL10.alSourcei(p.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
						AL10.alSource3f(p.source, AL10.AL_POSITION, 0F, 0F, 0F);
						double d = Math.sqrt(Math.pow(pos[0] - lx, 2) + Math.pow(pos[1] - ly, 2) + Math.pow(pos[2] - lz, 2));
						float g = (float) Math.max(0, 1 - Math.max(0, d - 2) / Math.max(1, maxDist - 2));
						AL10.alSourcef(p.source, AL10.AL_GAIN, g * VoiceConfig.playerVolume(e.getKey()));
					}
					feed(p.source, p.freeBuffers, p.queue, pcmBuf);
				}
				// mic test: hear yourself
				if (testing || !selfQueue.isEmpty()) {
					if (selfSource == -1) {
						selfSource = AL10.alGenSources();
						AL10.alSourcei(selfSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
					}
					feed(selfSource, selfFree, selfQueue, pcmBuf);
				}
				// forget people who left long ago
				long now = System.currentTimeMillis();
				peers.entrySet().removeIf(e -> {
					Peer p = e.getValue();
					if (now - p.lastAudio > 30_000L && !p.on && p.source != -1) {
						AL10.alSourceStop(p.source);
						AL10.alDeleteSources(p.source);
						return true;
					}
					return false;
				});
				Thread.sleep(8);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			error = "Speaker error: " + t.getClass().getSimpleName();
			System.err.println("[Sleep] voice out: " + t);
		} finally {
			try {
				for (Peer p : peers.values()) {
					if (p.source != -1) {
						AL10.alSourceStop(p.source);
						AL10.alDeleteSources(p.source);
						p.source = -1;
					}
				}
				if (selfSource != -1) {
					AL10.alDeleteSources(selfSource);
				}
			} catch (Throwable ignored) {
				// context going away
			}
			if (ctx != 0) {
				EXTThreadLocalContext.alcSetThreadContext(0L);
				ALC10.alcDestroyContext(ctx);
			}
			if (dev != 0) {
				ALC10.alcCloseDevice(dev);
			}
		}
	}

	/** Streams queued 20 ms frames into a source (keeps 2-4 buffered = small jitter buffer). */
	private static void feed(int src, List<Integer> free, ConcurrentLinkedDeque<short[]> queue, ShortBuffer pcmBuf) {
		int processed = AL10.alGetSourcei(src, AL10.AL_BUFFERS_PROCESSED);
		while (processed-- > 0) {
			free.add(AL10.alSourceUnqueueBuffers(src));
		}
		int queued = AL10.alGetSourcei(src, AL10.AL_BUFFERS_QUEUED);
		while (queued < 4 && !queue.isEmpty()) {
			short[] pcm = queue.pollFirst();
			int b = free.isEmpty() ? AL10.alGenBuffers() : free.remove(free.size() - 1);
			pcmBuf.clear();
			pcmBuf.put(pcm).flip();
			AL10.alBufferData(b, AL10.AL_FORMAT_MONO16, pcmBuf, RATE);
			AL10.alSourceQueueBuffers(src, b);
			queued++;
		}
		int state = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
		if (state != AL10.AL_PLAYING && queued >= 2) {
			AL10.alSourcePlay(src);
		}
	}
}
