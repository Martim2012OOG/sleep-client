package dev.solclient.voice.persona;

/**
 * Real-time reshaping of a microphone stream (one instance per capture device, so its filters keep their state between chunks):
 * pitch shift (two crossfaded read heads), vibrato, tone tilt, nasal peak, band limit, ring modulation, drive, bit crush,
 * tremolo, echo and a small room reverb. Mono, any sample rate.
 */
final class VoiceDsp {

	private final float sr;
	// pitch shifter
	private final float[] ring;
	private int w;
	private float phase;
	private final float window;
	// vibrato / tremolo / ring oscillators
	private double vibPh, trePh, ringPh;
	// tone (one pole) and band filters
	private float lp, hpL, lpL1, lpL2, hpH, lpH1, lpH2;
	// peaking biquad
	private float b0, b1, b2, a1, a2, z1, z2;
	private float lastPeakHz = -1, lastPeakDb = -999;
	// crush hold
	private float held;
	private int holdCount;
	// echo
	private final float[] echoBuf;
	private int echoW;
	// reverb: 4 combs + 2 allpass
	private final float[][] comb = new float[4][];
	private final int[] combI = new int[4];
	private final float[] combLp = new float[4];
	private final float[][] ap = new float[2][];
	private final int[] apI = new int[2];

	// crowd (chorus): 3 moving delayed copies
	private final float[] chBuf;
	private int chW;
	private double chPh;
	// whisper: envelope + noise
	private float wEnv, wPrev;
	private int seed = 12345;
	// noise gate, bass / treble shelves
	private float gEnv, gGain = 1F, shLow, shHigh;
	// voice size (formants), made when first needed
	private FormantShift formants;
	// auto volume: the character comes out as loud as your normal voice
	private float lvIn, lvOut, lvGain = 1F;

	VoiceDsp(float sampleRate) {
		sr = sampleRate;
		window = sr * 0.03F;
		ring = new float[(int) (window * 2) + 64];
		echoBuf = new float[(int) (sr * 0.22F) + 8];
		chBuf = new float[(int) (sr * 0.06F) + 8];
		int[] cd = {(int) (sr * 0.0297F), (int) (sr * 0.0371F), (int) (sr * 0.0411F), (int) (sr * 0.0437F)};
		for (int i = 0; i < 4; i++) {
			comb[i] = new float[cd[i]];
		}
		ap[0] = new float[(int) (sr * 0.005F)];
		ap[1] = new float[(int) (sr * 0.0017F)];
	}

	/** Processes n samples in place with the character and your extra settings (see {@link PersonaHook.Tune}). */
	void process(float[] x, int n, Persona p, PersonaHook.Tune t) {
		float st = p.pitch + t.pitchAdd;
		float amt = t.amount;
		float ringAmt = Math.min(1F, p.ring * amt + t.addRobot), drive = Math.min(1F, p.drive * amt + t.addDrive), crush = Math.min(1F, p.crush * amt);
		float echoAmt = Math.min(1F, p.echo * amt + t.addEcho), revAmt = Math.min(1F, p.reverb * amt + t.addReverb), trem = Math.min(1F, p.tremolo * amt);
		float vib = p.vibrato * amt, tone = Math.max(-1F, Math.min(1F, p.tone * amt));
		float chorus = Math.min(1F, p.chorus * amt + t.addChorus), whisper = Math.min(1F, p.whisper * amt + t.addWhisper);
		float ringHz = p.ringHz > 0 ? p.ringHz : 90;
		float pre = (float) Math.pow(10, t.micBoostDb / 20.0);
		float gateLin = t.gateDb <= -80 ? 0 : (float) Math.pow(10, t.gateDb / 20.0);
		float lowG = (float) Math.pow(10, t.bassDb / 20.0) - 1, highG = (float) Math.pow(10, t.trebleDb / 20.0) - 1;
		float shA = (float) (1 - Math.exp(-2 * Math.PI * 250 / sr)), shB = (float) (1 - Math.exp(-2 * Math.PI * 3500 / sr));
		float peakDb = p.peakDb * amt;
		if (p.peakHz > 0 && peakDb != 0 && (p.peakHz != lastPeakHz || peakDb != lastPeakDb)) {
			peak(p.peakHz, peakDb);
		}
		float ratio = (float) Math.pow(2, st / 12.0);
		float lpA = (float) (1 - Math.exp(-2 * Math.PI * 1500 / sr));
		float gain = (float) Math.pow(10, (p.gainDb + t.gainDb) / 20.0);
		boolean pitchOn = st != 0 || vib != 0;
		// the pitch shifter moves the formants together with the pitch; this puts them where the character wants them
		float fTarget = Float.isNaN(p.formant) ? st : p.pitch + (p.formant - p.pitch) * amt + t.pitchAdd;
		float fCorr = fTarget - st + t.formantAdd;
		boolean formantOn = Math.abs(fCorr) > 0.05F;
		if (formantOn && formants == null) {
			formants = new FormantShift(sr);
		}
		float hpA = p.bandLow > 0 ? (float) Math.exp(-2 * Math.PI * p.bandLow / sr) : 0;
		float lpB = p.bandHigh > 0 ? (float) (1 - Math.exp(-2 * Math.PI * p.bandHigh / sr)) : 0;
		int bits = 16 - Math.round(crush * 9), hold = 1 + Math.round(crush * 3);
		float q = bits >= 16 ? 0 : (float) (1 << (bits - 1));
		for (int i = 0; i < n; i++) {
			float s = x[i] * pre;
			float dry = Math.abs(s);
			// ---- noise gate: below the threshold the mic fades to silence (no keyboard / fan noise)
			if (gateLin > 0) {
				float a = Math.abs(s);
				gEnv += (a > gEnv ? 0.02F : 0.0008F) * (a - gEnv);
				float target = gEnv > gateLin ? 1F : 0F;
				gGain += (target - gGain) * (target > gGain ? 0.01F : 0.0015F);
				s *= gGain;
			}
			// ---- pitch (+ vibrato)
			if (pitchOn) {
				float r = ratio;
				if (vib != 0) {
					vibPh += 2 * Math.PI * p.vibratoHz / sr;
					r *= (float) Math.pow(2, vib * Math.sin(vibPh) / 12.0);
				}
				ring[w] = s;
				phase += (1F - r) / window;
				phase -= Math.floor(phase);
				float p2 = phase + 0.5F;
				p2 -= Math.floor(p2);
				float g1 = (float) Math.sin(Math.PI * phase), g2 = (float) Math.sin(Math.PI * p2);
				g1 *= g1;
				g2 *= g2;
				s = tap(phase * window) * g1 + tap(p2 * window) * g2;
				w = (w + 1) % ring.length;
			}
			// ---- voice size: a different throat / mouth = a different person
			if (formantOn) {
				s = formants.process(s, fCorr);
			}
			// ---- tone tilt
			if (tone != 0) {
				lp += lpA * (s - lp);
				float hi = s - lp;
				s = lp * (1F - 0.6F * tone) + hi * (1F + 0.9F * tone);
			}
			// ---- nasal / body peak
			if (p.peakHz > 0 && peakDb != 0) {
				float y = b0 * s + z1;
				z1 = b1 * s - a1 * y + z2;
				z2 = b2 * s - a2 * y;
				s = y;
			}
			// ---- band limit (telephone / old radio)
			if (hpA > 0) {
				hpL = hpA * (hpL + s - hpH);
				hpH = s;
				s = hpL;
			}
			if (lpB > 0) {
				lpL1 += lpB * (s - lpL1);
				lpL2 += lpB * (lpL1 - lpL2);
				s = lpL2;
			}
			// ---- robot
			if (ringAmt > 0) {
				ringPh += 2 * Math.PI * ringHz / sr;
				s = s * (1 - ringAmt) + s * (float) Math.sin(ringPh) * ringAmt;
			}
			// ---- crush + drive
			if (crush > 0) {
				if (holdCount-- <= 0) {
					holdCount = hold - 1;
					held = q == 0 ? s : Math.round(s * q) / q;
				}
				s = held;
			}
			if (drive > 0) {
				float k = 1F + drive * 9F;
				s = (float) (Math.tanh(s * k) / Math.tanh(k));
			}
			if (trem > 0) {
				trePh += 2 * Math.PI * 6.0 / sr;
				s *= 1F - trem * 0.5F * (1F + (float) Math.sin(trePh));
			}
			// ---- whisper: keep the loudness shape, replace the voice by breath (bright noise)
			if (whisper > 0) {
				wEnv += 0.004F * (Math.abs(s) * 2.2F - wEnv);
				seed = seed * 1103515245 + 12345;
				float nz = ((seed >>> 8) & 0xFFFF) / 32768F - 1F;
				float hp = nz - wPrev;
				wPrev = nz;
				s = s * (1F - whisper) + hp * wEnv * 1.6F * whisper;
			}
			// ---- crowd: three slowly moving delayed copies (15-40 ms) make one voice sound like several
			if (chorus > 0) {
				chBuf[chW] = s;
				chPh += 2 * Math.PI * 0.35 / sr;
				float acc = 0;
				for (int k = 0; k < 3; k++) {
					float d = sr * (0.018F + 0.007F * k + 0.004F * (float) Math.sin(chPh * (1 + 0.37 * k) + k * 2.1));
					float pos = chW - d;
					while (pos < 0) {
						pos += chBuf.length;
					}
					int i0 = (int) pos, i1 = (i0 + 1) % chBuf.length;
					float f = pos - i0;
					acc += chBuf[i0 % chBuf.length] * (1 - f) + chBuf[i1] * f;
				}
				chW = (chW + 1) % chBuf.length;
				s = s * (1F - 0.35F * chorus) + acc / 3F * 1.1F * chorus;
			}
			// ---- bass / treble
			if (lowG != 0 || highG != 0) {
				shLow += shA * (s - shLow);
				shHigh += shB * (s - shHigh);
				s += shLow * lowG + (s - shHigh) * highG;
			}
			// ---- echo
			if (echoAmt > 0) {
				int rd = (echoW + 1) % echoBuf.length;
				float e = echoBuf[rd];
				echoBuf[echoW] = s + e * 0.38F;
				echoW = rd;
				s += e * echoAmt * 0.6F;
			}
			// ---- room
			if (revAmt > 0) {
				float in = s * 0.3F, acc = 0;
				for (int c = 0; c < 4; c++) {
					float[] cb = comb[c];
					float o = cb[combI[c]];
					combLp[c] += 0.45F * (o - combLp[c]);
					cb[combI[c]] = in + combLp[c] * (0.72F + 0.12F * revAmt);
					combI[c] = (combI[c] + 1) % cb.length;
					acc += o;
				}
				for (int a = 0; a < 2; a++) {
					float[] ab = ap[a];
					float o = ab[apI[a]];
					float v = acc + o * 0.5F;
					ab[apI[a]] = v;
					acc = o - v * 0.5F;
					apI[a] = (apI[a] + 1) % ab.length;
				}
				s += acc * revAmt * 0.9F;
			}
			if (t.autoLevel) {
				lvIn += 0.0004F * (dry - lvIn);
				lvOut += 0.0004F * (Math.abs(s) - lvOut);
				float want = lvOut > 1e-5F ? Math.max(0.25F, Math.min(4F, lvIn / lvOut)) : 1F;
				lvGain += 0.0005F * (want - lvGain);
				s *= lvGain;
			}
			s *= gain;
			// soft limiter so nothing ever clips hard
			x[i] = s > 0.9F || s < -0.9F ? (float) (Math.signum(s) * (0.9 + 0.1 * Math.tanh((Math.abs(s) - 0.9) * 10))) : s;
		}
	}

	private float tap(float delay) {
		float pos = w - delay;
		while (pos < 0) {
			pos += ring.length;
		}
		int i0 = (int) pos, i1 = (i0 + 1) % ring.length;
		float f = pos - i0;
		return ring[i0 % ring.length] * (1 - f) + ring[i1] * f;
	}

	/** RBJ peaking filter. */
	private void peak(float hz, float db) {
		lastPeakHz = hz;
		lastPeakDb = db;
		double a = Math.pow(10, db / 40.0), w0 = 2 * Math.PI * hz / sr, al = Math.sin(w0) / (2 * 1.1), c = Math.cos(w0);
		double a0 = 1 + al / a;
		b0 = (float) ((1 + al * a) / a0);
		b1 = (float) (-2 * c / a0);
		b2 = (float) ((1 - al * a) / a0);
		a1 = (float) (-2 * c / a0);
		a2 = (float) ((1 - al / a) / a0);
	}
}
