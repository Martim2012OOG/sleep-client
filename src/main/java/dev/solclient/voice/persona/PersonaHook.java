package dev.solclient.voice.persona;

import java.nio.ShortBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryUtil;

/**
 * Where the character is applied to the microphone. Every voice mod that records through OpenAL (Simple Voice Chat, Plasmo Voice,
 * this client's own voice chat...) reads the mic with alcCaptureSamples: AlcCaptureMixin lets us reshape those samples right after
 * they are read and before the voice mod sees them, so it works with all of them without knowing about them.
 */
public final class PersonaHook {

	/** The character to apply right now (null = leave the microphone alone). Set by the Personas mod. */
	public static volatile Persona active;
	public static volatile float pitchAdd, amount = 1F, gainDb;

	/** Diagnostics for the settings screen. */
	public static volatile long lastHookAt;
	public static volatile int streams;

	private static final Map<Long, int[]> FORMAT = new ConcurrentHashMap<>();
	private static final Map<Long, VoiceDsp> DSP = new ConcurrentHashMap<>();
	private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[4096]);

	private PersonaHook() {
	}

	/** A capture device was opened (the mixin calls this with what the voice mod asked for). */
	public static void opened(long device, int freq, int format) {
		if (device != 0) {
			FORMAT.put(device, new int[]{freq, format});
			streams = FORMAT.size();
		}
	}

	/** Called by the mixin after alcCaptureSamples filled 'addr' with 'count' samples. */
	public static void samples(long device, long addr, int count) {
		lastHookAt = System.currentTimeMillis();
		Persona p = active;
		if (p == null || addr == 0 || count <= 0) {
			return;
		}
		int[] f = FORMAT.get(device);
		if (f == null || f[1] != 0x1101) { // AL_FORMAT_MONO16 only
			return;
		}
		try {
			ShortBuffer sb = MemoryUtil.memShortBuffer(addr, count);
			run(device, f[0], sb, count, p);
		} catch (Throwable t) {
			active = null; // never break somebody's microphone: switch off and say why once
			System.err.println("[Sleep] personas: switched off after an error: " + t);
		}
	}

	private static void run(long key, int rate, ShortBuffer sb, int count, Persona p) {
		float[] x = TMP.get();
		if (x.length < count) {
			x = new float[count];
			TMP.set(x);
		}
		for (int i = 0; i < count; i++) {
			x[i] = sb.get(i) / 32768F;
		}
		VoiceDsp d = DSP.computeIfAbsent(key, k -> new VoiceDsp(rate));
		d.process(x, count, p, pitchAdd, amount, gainDb);
		for (int i = 0; i < count; i++) {
			int v = Math.round(x[i] * 32767F);
			sb.put(i, (short) Math.max(-32768, Math.min(32767, v)));
		}
	}

	/** True while the mixin is really being called (a voice mod is recording and our hook is in). */
	public static boolean hookAlive() {
		return System.currentTimeMillis() - lastHookAt < 2500L;
	}

	/** Used by the client's own voice chat when the hook is not in: same effect, applied to its own samples. */
	public static void fallback(short[] pcm, int rate) {

		Persona p = active;
		if (p == null || hookAlive()) {
			return;
		}
		float[] x = new float[pcm.length];
		for (int i = 0; i < pcm.length; i++) {
			x[i] = pcm[i] / 32768F;
		}
		VoiceDsp d = DSP.computeIfAbsent(-1L, k -> new VoiceDsp(rate));
		d.process(x, pcm.length, p, pitchAdd, amount, gainDb);
		for (int i = 0; i < pcm.length; i++) {
			pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(x[i] * 32767F)));
		}
	}
}
