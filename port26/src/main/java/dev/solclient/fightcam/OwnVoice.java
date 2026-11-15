package dev.solclient.fightcam;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * "My own voice": you record yourself saying the phrase a few times, and from then on the clip fires when what you say
 * SOUNDS like those recordings (no speech-to-text, so any phrase or made-up word works, offline).
 * <p>
 * How: the microphone is cut into utterances (a burst of sound between silences). Each one becomes a sequence of MFCC
 * frames (the usual "shape of the sound" numbers for speech) and is compared with every recording by dynamic time
 * warping, which lets you say it a bit faster or slower. The recordings are also compared with each other: that tells
 * how close "the same phrase" is for your voice, and the threshold is set from it.
 */
final class OwnVoice {

	static final int RATE = 16000, FRAME = 400, HOP = 160, NFFT = 512, MELS = 26, CEPS = 12;
	static final int MAX_SAMPLES = 3;

	private static final List<float[][]> TEMPLATES = new ArrayList<>();
	private static float intra = -1F;
	private static boolean loaded;
	/** The next utterance becomes a recording. */
	static volatile boolean recordNext;
	static volatile String lastScore = "";

	private OwnVoice() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("voice").resolve("my-phrase");
	}

	static synchronized int count() {
		load();
		return TEMPLATES.size();
	}

	static synchronized void clear() {
		load();
		TEMPLATES.clear();
		intra = -1F;
		try {
			for (int i = 0; i < MAX_SAMPLES; i++) {
				Files.deleteIfExists(dir().resolve("sample" + i + ".pcm"));
			}
		} catch (Exception ignored) {
		}
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		for (int i = 0; i < MAX_SAMPLES; i++) {
			try {
				Path f = dir().resolve("sample" + i + ".pcm");
				if (Files.exists(f)) {
					TEMPLATES.add(mfcc(toFloat(Files.readAllBytes(f))));
				}
			} catch (Exception ignored) {
			}
		}
		calibrate();
	}

	/** Saves an utterance as the next recording (the oldest is replaced when there are already 3). */
	static synchronized void addSample(float[] pcm) {
		load();
		try {
			Files.createDirectories(dir());
			if (TEMPLATES.size() >= MAX_SAMPLES) {
				// full: the oldest goes, the others move down one place (the files keep matching the list)
				TEMPLATES.remove(0);
				Files.deleteIfExists(dir().resolve("sample0.pcm"));
				for (int i = 1; i < MAX_SAMPLES; i++) {
					Path f = dir().resolve("sample" + i + ".pcm");
					if (Files.exists(f)) {
						Files.move(f, dir().resolve("sample" + (i - 1) + ".pcm"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			Files.write(dir().resolve("sample" + TEMPLATES.size() + ".pcm"), toBytes(pcm));
		} catch (Exception ignored) {
		}
		TEMPLATES.add(mfcc(pcm));
		calibrate();
	}

	private static void calibrate() {
		intra = -1F;
		for (int i = 0; i < TEMPLATES.size(); i++) {
			for (int j = i + 1; j < TEMPLATES.size(); j++) {
				intra = Math.max(intra, dtw(TEMPLATES.get(i), TEMPLATES.get(j)));
			}
		}
	}

	/** Is this utterance your phrase? strict 50..95 (higher = it must sound more alike). */
	static synchronized boolean matches(float[] pcm, int strict) {
		load();
		if (TEMPLATES.isEmpty()) {
			return false;
		}
		float[][] f = mfcc(pcm);
		float best = Float.MAX_VALUE;
		for (float[][] t : TEMPLATES) {
			float ratio = f.length / (float) Math.max(1, t.length);
			if (ratio < 0.55F || ratio > 1.8F) {
				continue; // much shorter / longer: another sentence
			}
			best = Math.min(best, dtw(f, t));
		}
		// how far "the same phrase" may be: from your own recordings (with one recording, a typical value)
		float base = intra > 0 ? intra : 11F;
		float limit = base * (1.05F + (100 - strict) / 100F * 1.3F);
		lastScore = best == Float.MAX_VALUE ? "-" : String.format(java.util.Locale.ROOT, "%.1f / %.1f", best, limit);
		return best <= limit;
	}

	// ------------------------------------------------------------------ utterances from the microphone
	/** Cuts the microphone into utterances: feed 16 kHz samples, get whole bursts of speech back. */
	static final class Segmenter {
		private final ByteArrayOutputStream cur = new ByteArrayOutputStream();
		private final float[] pre = new float[HOP * 15];
		private int preLen;
		private float floorDb = -60F;
		private boolean inSpeech;
		private int silent, frames;
		private final float[] hop = new float[HOP];
		private int hopLen;

		/** Returns a finished utterance, or null. */
		float[] feed(float s) {
			hop[hopLen++] = s;
			if (hopLen < HOP) {
				return null;
			}
			hopLen = 0;
			double e = 0;
			for (float v : hop) {
				e += v * v;
			}
			float db = (float) (10 * Math.log10(e / HOP + 1e-10));
			if (!inSpeech) {
				floorDb = db < floorDb ? floorDb * 0.9F + db * 0.1F : floorDb * 0.995F + db * 0.005F; // follows the room noise
			}
			boolean loud = db > floorDb + 12F && db > -55F;
			if (!inSpeech) {
				// keep a little sound from just before the start
				System.arraycopy(pre, HOP, pre, 0, pre.length - HOP);
				System.arraycopy(hop, 0, pre, pre.length - HOP, HOP);
				preLen = Math.min(pre.length, preLen + HOP);
				if (loud) {
					inSpeech = true;
					silent = 0;
					frames = 0;
					cur.reset();
					for (int i = pre.length - preLen; i < pre.length; i++) {
						put(pre[i]);
					}
				}
				return null;
			}
			for (float v : hop) {
				put(v);
			}
			frames++;
			silent = loud ? 0 : silent + 1;
			if (silent >= 28 || frames > 450) { // 0.28 s of quiet ends it (or 4.5 s at most)
				inSpeech = false;
				preLen = 0;
				float[] out = toFloat(cur.toByteArray());
				return frames - silent >= 25 ? trim(out) : null; // under 0.25 s of sound: a click, not a phrase
			}
			return null;
		}

		private void put(float v) {
			int s = Math.max(-32768, Math.min(32767, Math.round(v * 32767)));
			cur.write(s & 255);
			cur.write((s >> 8) & 255);
		}
	}

	/** Cuts the quiet tail off an utterance. */
	private static float[] trim(float[] pcm) {
		int end = pcm.length;
		while (end > HOP * 4) {
			double e = 0;
			for (int i = end - HOP; i < end; i++) {
				e += pcm[i] * pcm[i];
			}
			if (e / HOP > 1e-5) {
				break;
			}
			end -= HOP;
		}
		return java.util.Arrays.copyOf(pcm, end);
	}

	// ------------------------------------------------------------------ MFCC + DTW
	private static float[][] melBank;

	static float[][] mfcc(float[] x) {
		if (melBank == null) {
			melBank = melFilters();
		}
		int n = Math.max(1, (x.length - FRAME) / HOP + 1);
		float[][] out = new float[n][CEPS];
		double[] re = new double[NFFT], im = new double[NFFT];
		double[] mel = new double[MELS];
		for (int f = 0; f < n; f++) {
			int o = f * HOP;
			java.util.Arrays.fill(re, 0);
			java.util.Arrays.fill(im, 0);
			for (int i = 0; i < FRAME && o + i < x.length; i++) {
				double prev = o + i > 0 ? x[o + i - 1] : 0;
				double w = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FRAME - 1));
				re[i] = (x[o + i] - 0.97 * prev) * w;
			}
			fft(re, im);
			for (int m = 0; m < MELS; m++) {
				double s = 0;
				for (int k = 0; k <= NFFT / 2; k++) {
					if (melBank[m][k] != 0) {
						s += melBank[m][k] * (re[k] * re[k] + im[k] * im[k]);
					}
				}
				mel[m] = Math.log(s + 1e-10);
			}
			for (int c = 1; c <= CEPS; c++) {
				double s = 0;
				for (int m = 0; m < MELS; m++) {
					s += mel[m] * Math.cos(Math.PI * c * (m + 0.5) / MELS);
				}
				out[f][c - 1] = (float) s;
			}
		}
		// cepstral mean normalisation: the microphone / room colour cancels out
		for (int c = 0; c < CEPS; c++) {
			double mean = 0;
			for (float[] fr : out) {
				mean += fr[c];
			}
			mean /= n;
			for (float[] fr : out) {
				fr[c] -= (float) mean;
			}
		}
		return out;
	}

	private static float[][] melFilters() {
		float[][] bank = new float[MELS][NFFT / 2 + 1];
		double lo = mel(80), hi = mel(RATE / 2.0);
		int[] bins = new int[MELS + 2];
		for (int i = 0; i < MELS + 2; i++) {
			double hz = 700 * (Math.pow(10, (lo + (hi - lo) * i / (MELS + 1)) / 2595) - 1);
			bins[i] = (int) Math.floor((NFFT + 1) * hz / RATE);
		}
		for (int m = 1; m <= MELS; m++) {
			for (int k = bins[m - 1]; k < bins[m]; k++) {
				bank[m - 1][k] = (k - bins[m - 1]) / (float) Math.max(1, bins[m] - bins[m - 1]);
			}
			for (int k = bins[m]; k < bins[m + 1]; k++) {
				bank[m - 1][k] = (bins[m + 1] - k) / (float) Math.max(1, bins[m + 1] - bins[m]);
			}
		}
		return bank;
	}

	private static double mel(double hz) {
		return 2595 * Math.log10(1 + hz / 700);
	}

	private static void fft(double[] re, double[] im) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				double t = re[i];
				re[i] = re[j];
				re[j] = t;
				t = im[i];
				im[i] = im[j];
				im[j] = t;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
			for (int i = 0; i < n; i += len) {
				double cr = 1, ci = 0;
				for (int k = 0; k < len / 2; k++) {
					int a = i + k, b = a + len / 2;
					double tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
					re[b] = re[a] - tr;
					im[b] = im[a] - ti;
					re[a] += tr;
					im[a] += ti;
					double nr = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = nr;
				}
			}
		}
	}

	/** Average frame distance along the best alignment of a and b. */
	static float dtw(float[][] a, float[][] b) {
		int n = a.length, m = b.length;
		float inf = Float.MAX_VALUE / 4;
		float[] prev = new float[m + 1], cur = new float[m + 1];
		int[] lp = new int[m + 1], lc = new int[m + 1];
		java.util.Arrays.fill(prev, inf);
		prev[0] = 0;
		int band = Math.max(Math.abs(n - m), Math.max(n, m) / 3) + 2;
		for (int i = 1; i <= n; i++) {
			java.util.Arrays.fill(cur, inf);
			int jc = Math.round(i * (float) m / n);
			for (int j = Math.max(1, jc - band); j <= Math.min(m, jc + band); j++) {
				float d = 0;
				for (int c = 0; c < CEPS; c++) {
					float x = a[i - 1][c] - b[j - 1][c];
					d += x * x;
				}
				d = (float) Math.sqrt(d);
				float best = prev[j - 1];
				int len = lp[j - 1];
				if (prev[j] < best) {
					best = prev[j];
					len = lp[j];
				}
				if (cur[j - 1] < best) {
					best = cur[j - 1];
					len = lc[j - 1];
				}
				cur[j] = best + d;
				lc[j] = len + 1;
			}
			float[] t = prev;
			prev = cur;
			cur = t;
			int[] u = lp;
			lp = lc;
			lc = u;
		}
		return prev[m] >= inf ? Float.MAX_VALUE : prev[m] / Math.max(1, lp[m]);
	}

	static float[] toFloat(byte[] b) {
		float[] out = new float[b.length / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = (short) ((b[2 * i] & 255) | b[2 * i + 1] << 8) / 32768F;
		}
		return out;
	}

	private static byte[] toBytes(float[] pcm) {
		byte[] b = new byte[pcm.length * 2];
		for (int i = 0; i < pcm.length; i++) {
			int s = Math.max(-32768, Math.min(32767, Math.round(pcm[i] * 32767)));
			b[2 * i] = (byte) s;
			b[2 * i + 1] = (byte) (s >> 8);
		}
		return b;
	}
}
