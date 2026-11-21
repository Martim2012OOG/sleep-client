package dev.sleepclient.fightcam;

import java.io.ByteArrayOutputStream;

/**
 * A small H.264 encoder (Baseline profile, CAVLC, 4:2:0, every picture is an intra picture), written from scratch so
 * the mod needs no extra library. Every picture can be decoded on its own, which also lets several pictures be
 * encoded at the same time on different threads. One instance is not thread safe: use one per thread.
 */
public final class H264Encoder {

	private static final int[] ZIGZAG = {0, 1, 4, 8, 5, 2, 3, 6, 9, 12, 13, 10, 7, 11, 14, 15};
	/** luma block index (z-order) to block x / y inside the macroblock */
	private static final int[] BLK_X = {0, 1, 0, 1, 2, 3, 2, 3, 0, 1, 0, 1, 2, 3, 2, 3};
	private static final int[] BLK_Y = {0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3};
	private static final int[][] MF = {{13107, 5243, 8066}, {11916, 4660, 7490}, {10082, 4194, 6554}, {9362, 3647, 5825}, {8192, 3355, 5243}, {7282, 2893, 4559}};
	private static final int[][] V = {{10, 16, 13}, {11, 18, 14}, {13, 20, 16}, {14, 23, 18}, {16, 25, 20}, {18, 29, 23}};
	private static final int[] QPC = {29, 30, 31, 32, 32, 33, 34, 34, 35, 35, 36, 36, 37, 37, 37, 38, 38, 38, 39, 39, 39, 39};

	private final int w, h, mbW, mbH, pw, ph, qp, qpc, fps;
	private final int[] yP, cbP, crP; // source planes (padded)
	private final int[] yR, cbR, crR; // reconstructed planes
	private final int[] tcY, tcCb, tcCr; // total_coeff per 4x4 block, for the nC context
	private final BitWriter bw = new BitWriter();
	private int idrId;

	// scratch
	private final int[][] lumaLevels = new int[16][16];
	private final int[] dcLevels = new int[16];
	private final int[][] cLevels = new int[8][16]; // 0..3 Cb, 4..7 Cr (index 1..15 are AC)
	private final int[][] cDc = new int[2][4];
	private final int[] blk = new int[16];
	private final int[] pred = new int[256];
	private final int[] pc = new int[64];
	private final int[][] coefL = new int[16][16];
	private final int[] dcBlk = new int[16];
	private final int[] tD = new int[16], dD = new int[16], lvD = new int[16], fdD = new int[16];
	private final int[][] preds = new int[2][64];
	private final int[][][] coefsC = new int[2][4][16];
	private final int[] acTmp = new int[15];
	private final int[] rVals = new int[16], rRuns = new int[16];

	/** @param w width and h height in pixels (both must be even). qp is 16..40: lower is better quality, bigger files. */
	public H264Encoder(int w, int h, int fps, int qp) {
		this.w = w & ~1;
		this.h = h & ~1;
		this.fps = Math.max(1, fps);
		this.qp = Math.max(16, Math.min(40, qp));
		this.qpc = QPC_OF(this.qp);
		this.mbW = (this.w + 15) / 16;
		this.mbH = (this.h + 15) / 16;
		this.pw = mbW * 16;
		this.ph = mbH * 16;
		yP = new int[pw * ph];
		cbP = new int[pw / 2 * ph / 2];
		crP = new int[pw / 2 * ph / 2];
		yR = new int[pw * ph];
		cbR = new int[pw / 2 * ph / 2];
		crR = new int[pw / 2 * ph / 2];
		tcY = new int[mbW * 4 * mbH * 4];
		tcCb = new int[mbW * 2 * mbH * 2];
		tcCr = new int[mbW * 2 * mbH * 2];
	}

	private static int QPC_OF(int q) {
		return q < 30 ? q : QPC[q - 30];
	}

	public int width() {
		return w;
	}

	public int height() {
		return h;
	}

	// ------------------------------------------------------------------ parameter sets
	/** Sequence parameter set NAL (without start code). */
	public byte[] sps() {
		BitWriter b = new BitWriter();
		b.u(8, 66); // baseline
		b.u(8, 0xC0); // constraint set 0 and 1
		int mbs = mbW * mbH;
		long rate = (long) mbs * fps;
		int level = rate <= 245_760L && mbs <= 8192 ? 40 : rate <= 522_240L && mbs <= 8704 ? 42 : 51;
		b.u(8, level);
		b.ue(0); // sps id
		b.ue(0); // log2_max_frame_num - 4
		b.ue(2); // pic order count type 2
		b.ue(1); // max reference frames
		b.u(1, 0); // gaps
		b.ue(mbW - 1);
		b.ue(mbH - 1);
		b.u(1, 1); // frame_mbs_only
		b.u(1, 1); // direct_8x8_inference
		boolean crop = pw != w || ph != h;
		b.bit(crop);
		if (crop) {
			b.ue(0);
			b.ue((pw - w) / 2);
			b.ue(0);
			b.ue((ph - h) / 2);
		}
		b.u(1, 1); // vui present
		b.u(1, 0); // aspect ratio
		b.u(1, 0); // overscan
		b.u(1, 1); // video signal type
		b.u(3, 5);
		b.u(1, 0); // limited range
		b.u(1, 1); // colour description
		b.u(8, 1); // BT.709
		b.u(8, 1);
		b.u(8, 1);
		b.u(1, 0); // chroma loc
		b.u(1, 0); // timing
		b.u(1, 0); // nal hrd
		b.u(1, 0); // vcl hrd
		b.u(1, 0); // pic struct
		b.u(1, 0); // bitstream restriction
		b.trailing();
		return nal(3, 7, b.bytes());
	}

	/** Picture parameter set NAL (without start code). */
	public byte[] pps() {
		BitWriter b = new BitWriter();
		b.ue(0);
		b.ue(0);
		b.u(1, 0); // CAVLC
		b.u(1, 0);
		b.ue(0); // slice groups
		b.ue(0);
		b.ue(0);
		b.u(1, 0);
		b.u(2, 0);
		b.se(0); // init qp 26
		b.se(0);
		b.se(0); // chroma qp offset
		b.u(1, 1); // deblocking control present
		b.u(1, 0);
		b.u(1, 0);
		b.trailing();
		return nal(3, 8, b.bytes());
	}

	/** Adds the NAL header and the emulation prevention bytes. */
	private static byte[] nal(int refIdc, int type, byte[] rbsp) {
		ByteArrayOutputStream o = new ByteArrayOutputStream(rbsp.length + 16);
		o.write((refIdc << 5) | type);
		int zeros = 0;
		for (byte x : rbsp) {
			int v = x & 255;
			if (zeros >= 2 && v <= 3) {
				o.write(3);
				zeros = 0;
			}
			o.write(v);
			zeros = v == 0 ? zeros + 1 : 0;
		}
		return o.toByteArray();
	}

	// ------------------------------------------------------------------ picture
	/**
	 * Encodes one picture.
	 * @param rgb w*h pixels as 0xRRGGBB (alpha ignored)
	 * @return one MP4 sample: the picture's NAL unit with a 4 byte length in front
	 */
	public byte[] encode(int[] rgb) {
		toYuv(rgb);
		java.util.Arrays.fill(tcY, 0);
		java.util.Arrays.fill(tcCb, 0);
		java.util.Arrays.fill(tcCr, 0);
		bw.reset();
		// slice header
		bw.ue(0);
		bw.ue(7); // I slice
		bw.ue(0);
		bw.u(4, 0); // frame_num
		bw.ue(idrId);
		idrId ^= 1;
		bw.u(1, 0); // no_output_of_prior_pics
		bw.u(1, 0); // long_term_reference
		bw.se(qp - 26);
		bw.ue(0); // deblocking on
		bw.se(0);
		bw.se(0);
		for (int my = 0; my < mbH; my++) {
			for (int mx = 0; mx < mbW; mx++) {
				macroblock(mx, my);
			}
		}
		bw.trailing();
		byte[] rbsp = java.util.Arrays.copyOf(bw.raw(), bw.size());
		byte[] n = nal(3, 5, rbsp);
		byte[] out = new byte[n.length + 4];
		out[0] = (byte) (n.length >>> 24);
		out[1] = (byte) (n.length >>> 16);
		out[2] = (byte) (n.length >>> 8);
		out[3] = (byte) n.length;
		System.arraycopy(n, 0, out, 4, n.length);
		return out;
	}

	private void toYuv(int[] rgb) {
		for (int y = 0; y < ph; y++) {
			int sy = Math.min(y, h - 1);
			for (int x = 0; x < pw; x++) {
				int c = rgb[sy * w + Math.min(x, w - 1)];
				int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
				yP[y * pw + x] = ((47 * r + 157 * g + 16 * b + 128) >> 8) + 16;
			}
		}
		int cw = pw / 2, ch = ph / 2;
		for (int y = 0; y < ch; y++) {
			int sy = Math.min(2 * y, h - 2);
			for (int x = 0; x < cw; x++) {
				int sx = Math.min(2 * x, w - 2);
				int c0 = rgb[sy * w + sx], c1 = rgb[sy * w + sx + 1], c2 = rgb[(sy + 1) * w + sx], c3 = rgb[(sy + 1) * w + sx + 1];
				int r = (((c0 >> 16) & 255) + ((c1 >> 16) & 255) + ((c2 >> 16) & 255) + ((c3 >> 16) & 255) + 2) >> 2;
				int g = (((c0 >> 8) & 255) + ((c1 >> 8) & 255) + ((c2 >> 8) & 255) + ((c3 >> 8) & 255) + 2) >> 2;
				int b = ((c0 & 255) + (c1 & 255) + (c2 & 255) + (c3 & 255) + 2) >> 2;
				cbP[y * cw + x] = ((-26 * r - 87 * g + 112 * b + 128) >> 8) + 128;
				crP[y * cw + x] = ((112 * r - 102 * g - 10 * b + 128) >> 8) + 128;
			}
		}
	}

	private static int clip(int v) {
		return v < 0 ? 0 : Math.min(255, v);
	}

	// ------------------------------------------------------------------ macroblock
	private void macroblock(int mx, int my) {
		boolean left = mx > 0, top = my > 0;
		// ---------- luma prediction: choose the mode with the smallest difference
		int bestMode = 2;
		long bestSad = Long.MAX_VALUE;
		for (int mode = 0; mode < 4; mode++) {
			if ((mode == 0 && !top) || (mode == 1 && !left) || (mode == 3 && !(top && left))) {
				continue;
			}
			predLuma(mx, my, mode, left, top, pred);
			long sad = 0;
			for (int y = 0; y < 16; y++) {
				int o = (my * 16 + y) * pw + mx * 16;
				for (int x = 0; x < 16; x++) {
					sad += Math.abs(yP[o + x] - pred[y * 16 + x]);
				}
			}
			if (sad < bestSad) {
				bestSad = sad;
				bestMode = mode;
			}
		}
		predLuma(mx, my, bestMode, left, top, pred);
		boolean lumaAc = lumaTransform(mx, my);
		// ---------- chroma
		int cMode = chooseChromaMode(mx, my, left, top);
		int cbpC = chromaTransform(mx, my, cMode, left, top);
		// ---------- bits
		bw.ue(1 + bestMode + 4 * cbpC + (lumaAc ? 12 : 0));
		bw.ue(cMode);
		bw.se(0); // mb_qp_delta
		// luma DC (context of block 0)
		residual(dcLevels, 16, nC(tcY, mx * 4, my * 4, mbW * 4, left, top), 0);
		if (lumaAc) {
			for (int i = 0; i < 16; i++) {
				int bx = mx * 4 + BLK_X[i], by = my * 4 + BLK_Y[i];
				int n = residualAc(lumaLevels[i], nC(tcY, bx, by, mbW * 4, bx > 0, by > 0));
				tcY[by * mbW * 4 + bx] = n;
			}
		}
		if (cbpC > 0) {
			residual(cDc[0], 4, -1, 1);
			residual(cDc[1], 4, -1, 1);
			if (cbpC == 2) {
				for (int p = 0; p < 2; p++) {
					int[] tc = p == 0 ? tcCb : tcCr;
					for (int i = 0; i < 4; i++) {
						int bx = mx * 2 + (i & 1), by = my * 2 + (i >> 1);
						int n = residualAc(cLevels[p * 4 + i], nC(tc, bx, by, mbW * 2, bx > 0, by > 0));
						tc[by * mbW * 2 + bx] = n;
					}
				}
			}
		}
	}

	private static int nC(int[] tc, int bx, int by, int stride, boolean leftAvail, boolean topAvail) {
		if (leftAvail && topAvail) {
			return (tc[by * stride + bx - 1] + tc[(by - 1) * stride + bx] + 1) >> 1;
		}
		if (leftAvail) {
			return tc[by * stride + bx - 1];
		}
		if (topAvail) {
			return tc[(by - 1) * stride + bx];
		}
		return 0;
	}

	// ------------------------------------------------------------------ luma
	private void predLuma(int mx, int my, int mode, boolean left, boolean top, int[] out) {
		int x0 = mx * 16, y0 = my * 16;
		switch (mode) {
			case 0:
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						out[y * 16 + x] = yR[(y0 - 1) * pw + x0 + x];
					}
				}
				break;
			case 1:
				for (int y = 0; y < 16; y++) {
					int v = yR[(y0 + y) * pw + x0 - 1];
					for (int x = 0; x < 16; x++) {
						out[y * 16 + x] = v;
					}
				}
				break;
			case 2: {
				int s = 0, dc;
				if (top) {
					for (int x = 0; x < 16; x++) {
						s += yR[(y0 - 1) * pw + x0 + x];
					}
				}
				if (left) {
					for (int y = 0; y < 16; y++) {
						s += yR[(y0 + y) * pw + x0 - 1];
					}
				}
				if (top && left) {
					dc = (s + 16) >> 5;
				} else if (top || left) {
					dc = (s + 8) >> 4;
				} else {
					dc = 128;
				}
				java.util.Arrays.fill(out, 0, 256, dc);
				break;
			}
			default: {
				int hh = 0, vv = 0;
				for (int i = 0; i < 8; i++) {
					int tr = yR[(y0 - 1) * pw + x0 + 8 + i];
					int tl = i == 7 ? yR[(y0 - 1) * pw + x0 - 1] : yR[(y0 - 1) * pw + x0 + 6 - i];
					hh += (i + 1) * (tr - tl);
					int bl = yR[(y0 + 8 + i) * pw + x0 - 1];
					int tu = i == 7 ? yR[(y0 - 1) * pw + x0 - 1] : yR[(y0 + 6 - i) * pw + x0 - 1];
					vv += (i + 1) * (bl - tu);
				}
				int a = 16 * (yR[(y0 + 15) * pw + x0 - 1] + yR[(y0 - 1) * pw + x0 + 15]);
				int b = (5 * hh + 32) >> 6, c = (5 * vv + 32) >> 6;
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						out[y * 16 + x] = clip((a + b * (x - 7) + c * (y - 7) + 16) >> 5);
					}
				}
			}
		}
	}

	/** Transforms and quantises the luma residual, fills the levels and reconstructs the macroblock. @return true if there are AC levels */
	private boolean lumaTransform(int mx, int my) {
		int qbits = 15 + qp / 6, f = (1 << qbits) / 3;
		int[] mf = MF[qp % 6], v = V[qp % 6];
		boolean anyAc = false;
		int[][] coef = coefL;
		for (int i = 0; i < 16; i++) {
			int bx = BLK_X[i], by = BLK_Y[i];
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					int px = mx * 16 + bx * 4 + x, py = my * 16 + by * 4 + y;
					blk[y * 4 + x] = yP[py * pw + px] - pred[(by * 4 + y) * 16 + bx * 4 + x];
				}
			}
			forward(blk);
			dcBlk[by * 4 + bx] = blk[0];
			for (int k = 1; k < 16; k++) {
				int pos = ZIGZAG[k];
				int cls = cls(pos);
				int a = Math.abs(blk[pos]);
				int lv = (a * mf[cls] + f) >> qbits;
				int sv = blk[pos] < 0 ? -lv : lv;
				lumaLevels[i][k] = sv;
				if (lv != 0) {
					anyAc = true;
				}
				coef[i][pos] = sv;
			}
		}
		// DC: 4x4 Hadamard
		int[] t = tD, d = dD, lvl = lvD, fd = fdD;
		for (int y = 0; y < 4; y++) {
			int a = dcBlk[y * 4], b = dcBlk[y * 4 + 1], c = dcBlk[y * 4 + 2], e = dcBlk[y * 4 + 3];
			t[y * 4] = a + b + c + e;
			t[y * 4 + 1] = a + b - c - e;
			t[y * 4 + 2] = a - b - c + e;
			t[y * 4 + 3] = a - b + c - e;
		}
		for (int x = 0; x < 4; x++) {
			int a = t[x], b = t[4 + x], c = t[8 + x], e = t[12 + x];
			d[x] = (a + b + c + e) / 2;
			d[4 + x] = (a + b - c - e) / 2;
			d[8 + x] = (a - b - c + e) / 2;
			d[12 + x] = (a - b + c - e) / 2;
		}
		for (int k = 0; k < 16; k++) {
			int a = Math.abs(d[k]);
			int lv = (a * mf[0] + 2 * f) >> (qbits + 1);
			lvl[k] = d[k] < 0 ? -lv : lv;
		}
		for (int k = 0; k < 16; k++) {
			dcLevels[k] = lvl[ZIGZAG[k]];
		}
		if (!anyAc) {
			for (int i = 0; i < 16; i++) {
				java.util.Arrays.fill(coef[i], 0);
			}
		}
		// reconstruction (same maths as the decoder)
		for (int y = 0; y < 4; y++) {
			int a = lvl[y * 4], b = lvl[y * 4 + 1], c = lvl[y * 4 + 2], e = lvl[y * 4 + 3];
			t[y * 4] = a + b + c + e;
			t[y * 4 + 1] = a + b - c - e;
			t[y * 4 + 2] = a - b - c + e;
			t[y * 4 + 3] = a - b + c - e;
		}
		for (int x = 0; x < 4; x++) {
			int a = t[x], b = t[4 + x], c = t[8 + x], e = t[12 + x];
			fd[x] = a + b + c + e;
			fd[4 + x] = a + b - c - e;
			fd[8 + x] = a - b - c + e;
			fd[12 + x] = a - b + c - e;
		}
		int ls0 = 16 * v[0];
		for (int i = 0; i < 16; i++) {
			int bx = BLK_X[i], by = BLK_Y[i];
			int fv = fd[by * 4 + bx];
			int dc = qp >= 36 ? (fv * ls0) << (qp / 6 - 6) : (fv * ls0 + (1 << (5 - qp / 6))) >> (6 - qp / 6);
			for (int k = 0; k < 16; k++) {
				blk[k] = coef[i][k] * v[cls(k)] << (qp / 6);
			}
			blk[0] = dc;
			inverse(blk);
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					int px = mx * 16 + bx * 4 + x, py = my * 16 + by * 4 + y;
					yR[py * pw + px] = clip(pred[(by * 4 + y) * 16 + bx * 4 + x] + blk[y * 4 + x]);
				}
			}
		}
		return anyAc;
	}

	private static int cls(int pos) {
		int x = pos & 3, y = pos >> 2;
		if ((x & 1) == 0 && (y & 1) == 0) {
			return 0;
		}
		return (x & 1) == 1 && (y & 1) == 1 ? 1 : 2;
	}

	// ------------------------------------------------------------------ chroma
	private void predChroma(int[] plane, int mx, int my, int mode, boolean left, boolean top, int[] out) {
		int cw = pw / 2, x0 = mx * 8, y0 = my * 8;
		if (mode == 1) { // horizontal
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					out[y * 8 + x] = plane[(y0 + y) * cw + x0 - 1];
				}
			}
		} else if (mode == 2) { // vertical
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					out[y * 8 + x] = plane[(y0 - 1) * cw + x0 + x];
				}
			}
		} else { // DC, decided per 4x4 block
			for (int b = 0; b < 4; b++) {
				int bx = (b & 1) * 4, by = (b >> 1) * 4;
				int st = 0, sl = 0;
				if (top) {
					for (int i = 0; i < 4; i++) {
						st += plane[(y0 - 1) * cw + x0 + bx + i];
					}
				}
				if (left) {
					for (int i = 0; i < 4; i++) {
						sl += plane[(y0 + by + i) * cw + x0 - 1];
					}
				}
				int dc;
				if (b == 0 || b == 3) {
					dc = top && left ? (st + sl + 4) >> 3 : left ? (sl + 2) >> 2 : top ? (st + 2) >> 2 : 128;
					if (b == 3 && !(top && left)) {
						dc = top ? (st + 2) >> 2 : left ? (sl + 2) >> 2 : 128;
					}
				} else if (b == 1) {
					dc = top ? (st + 2) >> 2 : left ? (sl + 2) >> 2 : 128;
				} else {
					dc = left ? (sl + 2) >> 2 : top ? (st + 2) >> 2 : 128;
				}
				for (int y = 0; y < 4; y++) {
					for (int x = 0; x < 4; x++) {
						out[(by + y) * 8 + bx + x] = dc;
					}
				}
			}
		}
	}

	private int chooseChromaMode(int mx, int my, boolean left, boolean top) {
		int cw = pw / 2;
		int best = 0;
		long bestSad = Long.MAX_VALUE;
		for (int mode = 0; mode < 3; mode++) {
			if ((mode == 1 && !left) || (mode == 2 && !top)) {
				continue;
			}
			long sad = 0;
			for (int p = 0; p < 2; p++) {
				predChroma(p == 0 ? cbR : crR, mx, my, mode, left, top, pc);
				int[] src = p == 0 ? cbP : crP;
				for (int y = 0; y < 8; y++) {
					for (int x = 0; x < 8; x++) {
						sad += Math.abs(src[(my * 8 + y) * cw + mx * 8 + x] - pc[y * 8 + x]);
					}
				}
			}
			if (sad < bestSad) {
				bestSad = sad;
				best = mode;
			}
		}
		return best;
	}

	/** @return the coded block pattern for chroma (0, 1 or 2) */
	private int chromaTransform(int mx, int my, int mode, boolean left, boolean top) {
		int cw = pw / 2;
		int qbits = 15 + qpc / 6, f = (1 << qbits) / 3;
		int[] mf = MF[qpc % 6], v = V[qpc % 6];
		boolean anyAc = false, anyDc = false;
		int[][] preds = this.preds;
		int[][][] coefs = coefsC;
		int[] dcqv = new int[8];
		for (int p = 0; p < 2; p++) {
			int[] src = p == 0 ? cbP : crP;
			predChroma(p == 0 ? cbR : crR, mx, my, mode, left, top, preds[p]);
			int[] dcw = new int[4]; // small, escape analysis removes it
			for (int i = 0; i < 4; i++) {
				int bx = (i & 1) * 4, by = (i >> 1) * 4;
				for (int y = 0; y < 4; y++) {
					for (int x = 0; x < 4; x++) {
						blk[y * 4 + x] = src[(my * 8 + by + y) * cw + mx * 8 + bx + x] - preds[p][(by + y) * 8 + bx + x];
					}
				}
				forward(blk);
				dcw[i] = blk[0];
				for (int k = 1; k < 16; k++) {
					int pos = ZIGZAG[k];
					int a = Math.abs(blk[pos]);
					int lv = (a * mf[cls(pos)] + f) >> qbits;
					int s = blk[pos] < 0 ? -lv : lv;
					cLevels[p * 4 + i][k] = s;
					coefs[p][i][pos] = s;
					if (lv != 0) {
						anyAc = true;
					}
				}
			}
			int a = dcw[0], b = dcw[1], c = dcw[2], d = dcw[3];
			int[] hd = {a + b + c + d, a - b + c - d, a + b - c - d, a - b - c + d}; // [0][0], [0][1], [1][0], [1][1]
			for (int i = 0; i < 4; i++) {
				int ab = Math.abs(hd[i]);
				int lv = (ab * mf[0] + 2 * f) >> (qbits + 1);
				int s = hd[i] < 0 ? -lv : lv;
				cDc[p][i] = s;
				dcqv[p * 4 + i] = s;
				if (lv != 0) {
					anyDc = true;
				}
			}
		}
		int cbp = anyAc ? 2 : anyDc ? 1 : 0;
		for (int p = 0; p < 2; p++) {
			int[] dst = p == 0 ? cbR : crR;
			int[] lv = {dcqv[p * 4], dcqv[p * 4 + 1], dcqv[p * 4 + 2], dcqv[p * 4 + 3]};
			if (cbp == 0) {
				java.util.Arrays.fill(lv, 0);
			}
			int a = lv[0], b = lv[1], c = lv[2], d = lv[3];
			int[] fdc = {a + b + c + d, a - b + c - d, a + b - c - d, a - b - c + d};
			for (int i = 0; i < 4; i++) {
				int bx = (i & 1) * 4, by = (i >> 1) * 4;
				int dc = ((fdc[i] * 16 * v[0]) << (qpc / 6)) >> 5;
				for (int k = 0; k < 16; k++) {
					blk[k] = cbp == 2 ? coefs[p][i][k] * v[cls(k)] << (qpc / 6) : 0;
				}
				blk[0] = dc;
				inverse(blk);
				for (int y = 0; y < 4; y++) {
					for (int x = 0; x < 4; x++) {
						dst[(my * 8 + by + y) * cw + mx * 8 + bx + x] = clip(preds[p][(by + y) * 8 + bx + x] + blk[y * 4 + x]);
					}
				}
			}
		}
		if (cbp == 0) {
			java.util.Arrays.fill(cDc[0], 0);
			java.util.Arrays.fill(cDc[1], 0);
		}
		return cbp;
	}

	// ------------------------------------------------------------------ transforms
	private static void forward(int[] b) {
		for (int y = 0; y < 4; y++) { // rows
			int o = y * 4;
			int s0 = b[o] + b[o + 3], s1 = b[o + 1] + b[o + 2], d0 = b[o] - b[o + 3], d1 = b[o + 1] - b[o + 2];
			b[o] = s0 + s1;
			b[o + 1] = 2 * d0 + d1;
			b[o + 2] = s0 - s1;
			b[o + 3] = d0 - 2 * d1;
		}
		for (int x = 0; x < 4; x++) { // columns
			int s0 = b[x] + b[12 + x], s1 = b[4 + x] + b[8 + x], d0 = b[x] - b[12 + x], d1 = b[4 + x] - b[8 + x];
			b[x] = s0 + s1;
			b[4 + x] = 2 * d0 + d1;
			b[8 + x] = s0 - s1;
			b[12 + x] = d0 - 2 * d1;
		}
	}

	private static void inverse(int[] b) {
		for (int y = 0; y < 4; y++) {
			int o = y * 4;
			int e0 = b[o] + b[o + 2], e1 = b[o] - b[o + 2], e2 = (b[o + 1] >> 1) - b[o + 3], e3 = b[o + 1] + (b[o + 3] >> 1);
			b[o] = e0 + e3;
			b[o + 1] = e1 + e2;
			b[o + 2] = e1 - e2;
			b[o + 3] = e0 - e3;
		}
		for (int x = 0; x < 4; x++) {
			int e0 = b[x] + b[8 + x], e1 = b[x] - b[8 + x], e2 = (b[4 + x] >> 1) - b[12 + x], e3 = b[4 + x] + (b[12 + x] >> 1);
			b[x] = (e0 + e3 + 32) >> 6;
			b[4 + x] = (e1 + e2 + 32) >> 6;
			b[8 + x] = (e1 - e2 + 32) >> 6;
			b[12 + x] = (e0 - e3 + 32) >> 6;
		}
	}

	// ------------------------------------------------------------------ CAVLC
	private int residualAc(int[] levelsFrom1, int nc) {
		System.arraycopy(levelsFrom1, 1, acTmp, 0, 15);
		return residual(acTmp, 15, nc, 0);
	}

	/** Writes one residual block. @param chromaDc 1 for the 2x2 chroma DC block. @return total_coeff */
	private int residual(int[] lv, int maxCoeff, int nc, int chromaDc) {
		int total = 0, last = -1;
		for (int i = 0; i < maxCoeff; i++) {
			if (lv[i] != 0) {
				total++;
				last = i;
			}
		}
		int[] vals = rVals, runs = rRuns;
		int n = 0, prev = -1;
		for (int i = 0; i <= last; i++) {
			if (lv[i] != 0) {
				vals[n] = lv[i];
				runs[n] = i - prev - 1; // zeros before this coefficient
				n++;
				prev = i;
			}
		}
		// trailing ones: up to 3 values of +-1 at the high-frequency end
		int t1 = 0;
		for (int i = total - 1; i >= 0 && t1 < 3 && Math.abs(vals[i]) == 1; i--) {
			t1++;
		}
		int idx = total * 4 + t1;
		if (nc < 0) {
			bw.u(CavlcTables.CDC_LEN[idx], CavlcTables.CDC_BITS[idx]);
		} else {
			int cls = nc < 2 ? 0 : nc < 4 ? 1 : nc < 8 ? 2 : 3;
			bw.u(CavlcTables.COEFF_LEN[cls][idx], CavlcTables.COEFF_BITS[cls][idx]);
		}
		if (total == 0) {
			return 0;
		}
		for (int i = 0; i < t1; i++) { // sign of the trailing ones, highest frequency first
			bw.u(1, vals[total - 1 - i] < 0 ? 1 : 0);
		}
		int suffixLength = total > 10 && t1 < 3 ? 1 : 0;
		for (int i = t1; i < total; i++) {
			int level = vals[total - 1 - i];
			int code = level > 0 ? 2 * level - 2 : -2 * level - 1;
			if (i == t1 && t1 < 3) {
				code -= 2;
			}
			if (suffixLength == 0) {
				if (code < 14) {
					bw.u(code, 0);
					bw.u(1, 1);
				} else if (code < 30) {
					bw.u(14, 0);
					bw.u(1, 1);
					bw.u(4, code - 14);
				} else {
					bw.u(15, 0);
					bw.u(1, 1);
					bw.u(12, Math.min(4095, code - 30));
				}
			} else {
				if (code < (15 << suffixLength)) {
					bw.u(code >> suffixLength, 0);
					bw.u(1, 1);
					bw.u(suffixLength, code & ((1 << suffixLength) - 1));
				} else {
					bw.u(15, 0);
					bw.u(1, 1);
					bw.u(12, Math.min(4095, code - (15 << suffixLength)));
				}
			}
			if (suffixLength == 0) {
				suffixLength = 1;
			}
			if (Math.abs(level) > (3 << (suffixLength - 1)) && suffixLength < 6) {
				suffixLength++;
			}
		}
		int totalZeros = last + 1 - total;
		if (total < maxCoeff) {
			if (chromaDc == 1) {
				bw.u(CavlcTables.CTZ_LEN[total - 1][totalZeros], CavlcTables.CTZ_BITS[total - 1][totalZeros]);
			} else {
				bw.u(CavlcTables.TZ_LEN[total - 1][totalZeros], CavlcTables.TZ_BITS[total - 1][totalZeros]);
			}
		}
		int zerosLeft = totalZeros;
		for (int i = total - 1; i > 0 && zerosLeft > 0; i--) {
			int run = runs[i];
			int t = Math.min(zerosLeft, 7) - 1;
			bw.u(CavlcTables.RUN_LEN[t][run], CavlcTables.RUN_BITS[t][run]);
			zerosLeft -= run;
		}
		return total;
	}
}
