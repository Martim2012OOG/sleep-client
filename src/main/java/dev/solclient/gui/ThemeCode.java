package dev.solclient.gui;

import dev.solclient.mods.SolClientMod;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Share a theme as a short text ("CAOS-..."): copy it, send it to a friend, paste it back with one click. */
public final class ThemeCode {

	private static final String PREFIX = "CAOS-";
	private static final int VERSION = 1;
	private static final int LEN = 1 + 12 * 3 + 5 + 1;
	private static final Pattern FIND = Pattern.compile("(?:CAOS|KUBO)-[A-Za-z0-9_-]{20,120}");

	private ThemeCode() {
	}

	private static int[] colours(SolClientMod s) {
		return new int[] {s.uiColour.c(), s.bgColour.c(), s.cardColour.c(), s.borderColour.c(), s.fieldColour.c(), s.textColour.c(),
				s.dimColour.c(), s.onColour.c(), s.onGlowColour.c(), s.offColour.c(), s.dangerColour.c(), s.buttonColour.c()};
	}

	/** Current theme as a code. */
	public static String export(SolClientMod s) {
		byte[] b = new byte[LEN];
		int i = 0;
		b[i++] = (byte) VERSION;
		for (int c : colours(s)) {
			b[i++] = (byte) (c >> 16);
			b[i++] = (byte) (c >> 8);
			b[i++] = (byte) c;
		}
		b[i++] = (byte) Math.round(s.bgTransparency.f());
		b[i++] = (byte) Math.round(s.cardTransparency.f());
		b[i++] = (byte) Math.round(s.menuOpacity.f());
		b[i++] = (byte) s.buttonStyle.idx();
		b[i++] = (byte) Math.round(s.cornerRadius.f());
		b[i] = checksum(b, i);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	/** Applies a code found anywhere in the text. Returns null on success or a short error message. */
	public static String apply(SolClientMod s, String text) {
		if (text == null) {
			return "Clipboard is empty";
		}
		Matcher m = FIND.matcher(text);
		if (!m.find()) {
			return "No theme code in clipboard";
		}
		byte[] b;
		try {
			b = Base64.getUrlDecoder().decode(m.group().substring(m.group().indexOf('-') + 1));
		} catch (IllegalArgumentException e) {
			return "Invalid theme code";
		}
		if (b.length != LEN || b[LEN - 1] != checksum(b, LEN - 1)) {
			return "Invalid theme code";
		}
		if ((b[0] & 255) != VERSION) {
			return "Code from another version";
		}
		int i = 1;
		int[] c = new int[12];
		for (int k = 0; k < 12; k++) {
			c[k] = 0xFF000000 | (b[i] & 255) << 16 | (b[i + 1] & 255) << 8 | (b[i + 2] & 255);
			i += 3;
		}
		s.uiColour.set(c[0]);
		s.bgColour.set(c[1]);
		s.cardColour.set(c[2]);
		s.borderColour.set(c[3]);
		s.fieldColour.set(c[4]);
		s.textColour.set(c[5]);
		s.dimColour.set(c[6]);
		s.onColour.set(c[7]);
		s.onGlowColour.set(c[8]);
		s.offColour.set(c[9]);
		s.dangerColour.set(c[10]);
		s.buttonColour.set(c[11]);
		s.bgTransparency.set((float) (b[i++] & 255));
		s.cardTransparency.set((float) (b[i++] & 255));
		s.menuOpacity.set((float) (b[i++] & 255));
		s.buttonStyle.set(Math.min(3, b[i++] & 255));
		s.cornerRadius.set((float) (b[i] & 255));
		Theme.refresh();
		return null;
	}

	private static byte checksum(byte[] b, int len) {
		int h = 0x5A;
		for (int i = 0; i < len; i++) {
			h = (h * 31 + (b[i] & 255)) & 255;
		}
		return (byte) h;
	}
}
