package dev.sleepclient.voice;

/**
 * IMA ADPCM (4 bits per sample): 20 ms of 16 kHz mono = 320 samples -> 4 byte header + 160 bytes.
 * Simple, no native library, about 8 KB/s per person talking.
 */
public final class Adpcm {

	public static final int SAMPLES = 320;
	public static final int BYTES = 4 + SAMPLES / 2;

	private static final int[] INDEX = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
	private static final int[] STEP = {7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
			50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408,
			449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499,
			2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635,
			13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};

	private Adpcm() {
	}

	/** Encodes 320 samples. The predictor state starts fresh every frame, so lost packets never break later ones. */
	public static byte[] encode(short[] pcm) {
		byte[] out = new byte[BYTES];
		int pred = pcm[0], index = 0;
		// pick a starting step close to the signal level
		int peak = 0;
		for (int i = 1; i < Math.min(16, pcm.length); i++) {
			peak = Math.max(peak, Math.abs(pcm[i] - pcm[i - 1]));
		}
		while (index < 88 && STEP[index] < peak / 4) {
			index++;
		}
		out[0] = (byte) (pred & 0xFF);
		out[1] = (byte) ((pred >> 8) & 0xFF);
		out[2] = (byte) index;
		for (int i = 0; i < SAMPLES; i++) {
			int step = STEP[index];
			int diff = pcm[i] - pred;
			int code = 0;
			if (diff < 0) {
				code = 8;
				diff = -diff;
			}
			int delta = step >> 3;
			if (diff >= step) {
				code |= 4;
				diff -= step;
				delta += step;
			}
			step >>= 1;
			if (diff >= step) {
				code |= 2;
				diff -= step;
				delta += step;
			}
			step >>= 1;
			if (diff >= step) {
				code |= 1;
				delta += step;
			}
			pred += (code & 8) != 0 ? -delta : delta;
			pred = Math.max(-32768, Math.min(32767, pred));
			index = Math.max(0, Math.min(88, index + INDEX[code]));
			int b = 4 + i / 2;
			out[b] |= (byte) ((i & 1) == 0 ? code : code << 4);
		}
		return out;
	}

	public static short[] decode(byte[] in, int off) {
		short[] pcm = new short[SAMPLES];
		int pred = (short) ((in[off] & 0xFF) | (in[off + 1] << 8));
		int index = Math.max(0, Math.min(88, in[off + 2]));
		for (int i = 0; i < SAMPLES; i++) {
			int b = in[off + 4 + i / 2] & 0xFF;
			int code = (i & 1) == 0 ? b & 0x0F : b >> 4;
			int step = STEP[index];
			int delta = step >> 3;
			if ((code & 4) != 0) {
				delta += step;
			}
			if ((code & 2) != 0) {
				delta += step >> 1;
			}
			if ((code & 1) != 0) {
				delta += step >> 2;
			}
			pred += (code & 8) != 0 ? -delta : delta;
			pred = Math.max(-32768, Math.min(32767, pred));
			index = Math.max(0, Math.min(88, index + INDEX[code]));
			pcm[i] = (short) pred;
		}
		return pcm;
	}
}
