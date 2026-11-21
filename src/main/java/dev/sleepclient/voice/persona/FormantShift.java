package dev.sleepclient.voice.persona;

/**
 * Moves the formants (the "size" of the throat and mouth: what makes a voice sound like a particular person) without
 * touching the pitch. Streaming STFT (Hann window, 4x overlap): every frame the spectral envelope is measured (log magnitude
 * smoothed across ~400 Hz, which hides the harmonics), stretched by 2^(semitones/12), and the bins are re-weighted so the
 * spectrum follows the stretched envelope. Phases are kept, so the pitch and the words stay exactly the same.
 * Latency: one frame (about 21 ms at 48 kHz).
 */
final class FormantShift {

	private final int n, hop, latency, half;
	private final float[] inFifo, outFifo, accum, window, re, im, env, warped;
	private int rover;

	FormantShift(float sampleRate) {
		n = sampleRate > 32000 ? 1024 : 512;
		hop = n / 4;
		half = n / 2;
		latency = n - hop;
		inFifo = new float[n];
		outFifo = new float[n];
		accum = new float[n * 2];
		window = new float[n];
		re = new float[n];
		im = new float[n];
		env = new float[half + 1];
		warped = new float[half + 1];
		for (int i = 0; i < n; i++) {
			window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / n));
		}
		rover = latency;
	}

	/** One sample in, one (delayed) sample out. semitones > 0 = smaller / brighter voice, < 0 = bigger / darker. */
	float process(float s, float semitones) {
		inFifo[rover] = s;
		float out = outFifo[rover - latency];
		rover++;
		if (rover >= n) {
			rover = latency;
			frame((float) Math.pow(2, semitones / 12.0));
		}
		return out;
	}

	private void frame(float factor) {
		for (int i = 0; i < n; i++) {
			re[i] = inFifo[i] * window[i];
			im[i] = 0;
		}
		fft(re, im, false);
		// log magnitude of the useful half
		for (int k = 0; k <= half; k++) {
			env[k] = (float) Math.log(1e-9 + Math.hypot(re[k], im[k]));
		}
		smooth(env, 4);
		smooth(env, 4);
		// envelope you want at bin k = the old envelope at k / factor
		for (int k = 0; k <= half; k++) {
			float src = k / factor;
			int i0 = (int) src;
			if (i0 >= half) {
				warped[k] = env[half] - 6F; // nothing up there: keep it quiet
				continue;
			}
			float f = src - i0;
			warped[k] = env[i0] * (1 - f) + env[i0 + 1] * f;
		}
		for (int k = 0; k <= half; k++) {
			float g = (float) Math.exp(Math.max(-3.5F, Math.min(3.5F, warped[k] - env[k])));
			re[k] *= g;
			im[k] *= g;
			if (k > 0 && k < half) { // keep the spectrum of a real signal symmetric
				re[n - k] = re[k];
				im[n - k] = -im[k];
			}
		}
		fft(re, im, true);
		// overlap-add (Hann^2 at 4x overlap sums to 1.5)
		float scale = 1F / (n * 1.5F);
		for (int i = 0; i < n; i++) {
			accum[i] += re[i] * window[i] * scale;
		}
		System.arraycopy(accum, 0, outFifo, 0, hop);
		System.arraycopy(accum, hop, accum, 0, n);
		java.util.Arrays.fill(accum, n, n + hop, 0F);
		System.arraycopy(inFifo, hop, inFifo, 0, latency);
	}

	/** Moving average over 2r+1 bins (in place). */
	private static void smooth(float[] a, int r) {
		float[] c = a.clone();
		for (int k = 0; k < a.length; k++) {
			float sum = 0;
			int cnt = 0;
			for (int j = Math.max(0, k - r); j <= Math.min(a.length - 1, k + r); j++) {
				sum += c[j];
				cnt++;
			}
			a[k] = sum / cnt;
		}
	}

	/** In-place radix-2 FFT (inverse is not scaled). */
	private static void fft(float[] re, float[] im, boolean inverse) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				float t = re[i];
				re[i] = re[j];
				re[j] = t;
				t = im[i];
				im[i] = im[j];
				im[j] = t;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = 2 * Math.PI / len * (inverse ? 1 : -1);
			float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
			for (int i = 0; i < n; i += len) {
				float cr = 1, ci = 0;
				for (int j = 0; j < len / 2; j++) {
					int a = i + j, b = a + len / 2;
					float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
					re[b] = re[a] - xr;
					im[b] = im[a] - xi;
					re[a] += xr;
					im[a] += xi;
					float nr = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = nr;
				}
			}
		}
	}
}
