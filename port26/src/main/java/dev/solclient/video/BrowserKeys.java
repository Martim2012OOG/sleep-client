package dev.solclient.video;

/** GLFW key codes -> Java AWT key codes (what the embedded Chromium understands), and the modifier masks. */
public final class BrowserKeys {

	public static final int SHIFT = 64, CTRL = 128, ALT = 512;

	private BrowserKeys() {
	}

	/** 0 when the key has no AWT equivalent. */
	public static int awt(int glfw) {
		if (glfw >= 65 && glfw <= 90 || glfw >= 48 && glfw <= 57 || glfw == 32) {
			return glfw; // letters, digits, space: same numbers
		}
		if (glfw >= 290 && glfw <= 301) {
			return 112 + (glfw - 290); // F1..F12
		}
		if (glfw >= 320 && glfw <= 329) {
			return 96 + (glfw - 320); // keypad digits
		}
		return switch (glfw) {
			case 257, 335 -> 10; // enter
			case 259 -> 8; // backspace
			case 261 -> 127; // delete
			case 258 -> 9; // tab
			case 256 -> 27; // escape
			case 262 -> 39; // right
			case 263 -> 37; // left
			case 264 -> 40; // down
			case 265 -> 38; // up
			case 268 -> 36; // home
			case 269 -> 35; // end
			case 266 -> 33; // page up
			case 267 -> 34; // page down
			case 260 -> 155; // insert
			case 340, 344 -> 16; // shift
			case 341, 345 -> 17; // ctrl
			case 342, 346 -> 18; // alt
			case 45 -> 45; // -
			case 61 -> 61; // =
			case 44 -> 44; // ,
			case 46 -> 46; // .
			case 47 -> 47; // /
			case 59 -> 59; // ;
			case 39 -> 222; // '
			case 91 -> 91; // [
			case 93 -> 93; // ]
			case 92 -> 92; // backslash
			case 96 -> 192; // `
			default -> 0;
		};
	}

	public static int mods(boolean shift, boolean ctrl, boolean alt) {
		return (shift ? SHIFT : 0) | (ctrl ? CTRL : 0) | (alt ? ALT : 0);
	}

	/** What to load for what was typed in the address bar: a link as it is, a search otherwise. */
	public static String address(String typed) {
		String t = typed == null ? "" : typed.trim();
		if (t.isEmpty()) {
			return "about:blank";
		}
		String low = t.toLowerCase(java.util.Locale.ROOT);
		if (low.startsWith("http://") || low.startsWith("https://") || low.startsWith("file:") || low.startsWith("about:") || low.startsWith("data:")) {
			return t;
		}
		if (!t.contains(" ") && (t.contains(".") || low.startsWith("localhost"))) {
			return "https://" + t;
		}
		return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, java.nio.charset.StandardCharsets.UTF_8);
	}
}
