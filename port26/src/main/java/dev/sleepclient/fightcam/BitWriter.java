package dev.sleepclient.fightcam;

import java.util.Arrays;

/** Writes bits (most significant first) into a growing byte array. */
final class BitWriter {

	private byte[] buf = new byte[1 << 16];
	private int len;
	private int cur, nbits;

	void u(int bits, int value) {
		for (int i = bits - 1; i >= 0; i--) {
			cur = (cur << 1) | ((value >>> i) & 1);
			if (++nbits == 8) {
				put(cur);
				cur = 0;
				nbits = 0;
			}
		}
	}

	void bit(boolean b) {
		u(1, b ? 1 : 0);
	}

	/** Unsigned Exp-Golomb. */
	void ue(int v) {
		long x = (long) v + 1;
		int n = 63 - Long.numberOfLeadingZeros(x);
		u(n, 0);
		for (int i = n; i >= 0; i--) {
			u(1, (int) ((x >>> i) & 1));
		}
	}

	/** Signed Exp-Golomb. */
	void se(int v) {
		ue(v > 0 ? 2 * v - 1 : -2 * v);
	}

	void trailing() {
		u(1, 1);
		while (nbits != 0) {
			u(1, 0);
		}
	}

	private void put(int b) {
		if (len == buf.length) {
			buf = Arrays.copyOf(buf, len * 2);
		}
		buf[len++] = (byte) b;
	}

	void reset() {
		len = 0;
		cur = 0;
		nbits = 0;
	}

	/** The bytes written so far (the stream must be byte aligned). */
	byte[] bytes() {
		return Arrays.copyOf(buf, len);
	}

	int size() {
		return len;
	}

	byte[] raw() {
		return buf;
	}
}
