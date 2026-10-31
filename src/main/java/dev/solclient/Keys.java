package dev.solclient;

import org.lwjgl.glfw.GLFW;

/** Raw GLFW key / mouse polling, independent of vanilla key bindings. */
public final class Keys {

	private Keys() {
	}

	/** Key combos: these bits on top of the GLFW key code mean "with Ctrl (or Cmd) / Shift / Alt" (e.g. Ctrl+G). */
	public static final int CTRL = 1 << 16, SHIFT = 1 << 17, ALT = 1 << 18, MODS = CTRL | SHIFT | ALT;

	public static int base(int code) {
		return code < 0 ? code : code & 0xFFFF;
	}

	public static boolean ctrlHeld() {
		return rawDown(341) || rawDown(345) || rawDown(343) || rawDown(347);
	}

	public static boolean shiftHeld() {
		return rawDown(340) || rawDown(344);
	}

	public static boolean altHeld() {
		return rawDown(342) || rawDown(346);
	}

	private static final java.util.Set<Integer> COMBO_BASES = new java.util.HashSet<>();
	private static long combosAt;

	/** Keys that also have a Ctrl/Alt combo somewhere: then the plain key does not fire while Ctrl/Alt is held. */
	private static boolean hasCombo(int base) {
		long now = System.currentTimeMillis();
		if (now - combosAt > 1000L) {
			combosAt = now;
			COMBO_BASES.clear();
			for (dev.solclient.mods.Mod m : SolClient.MODS) {
				for (dev.solclient.option.Option<?> o : m.options) {
					if (o instanceof dev.solclient.option.Option.Key k && k.code() >= 0 && (k.code() & (CTRL | ALT)) != 0) {
						COMBO_BASES.add(k.code() & 0xFFFF);
					}
				}
			}
		}
		return COMBO_BASES.contains(base);
	}

	/** Whether a key (or combo) is held. Always false while you type into a floating browser panel, so the other mods' hotkeys stay quiet. */
	public static boolean down(int code) {
		if (code < 0 || dev.solclient.video.PanelInput.typing()) {
			return false;
		}
		int b = code & 0xFFFF;
		if (!rawDown(b)) {
			return false;
		}
		if ((code & MODS) == 0) {
			return !((ctrlHeld() || altHeld()) && hasCombo(b));
		}
		return ((code & CTRL) == 0 || ctrlHeld()) && ((code & SHIFT) == 0 || shiftHeld()) && ((code & ALT) == 0 || altHeld());
	}

	/** The modifier bits for a key press (none when the key itself is a modifier). */
	public static int withMods(int key, boolean ctrl, boolean shift, boolean alt) {
		if (key < 0 || key >= 340 && key <= 347) {
			return key;
		}
		return key | (ctrl ? CTRL : 0) | (shift ? SHIFT : 0) | (alt ? ALT : 0);
	}

	/** The same, without the typing-mode silence (the panels themselves need it for Shift / Ctrl). */
	public static boolean rawDown(int code) {
		if (code < 0) {
			return false;
		}
		long window = GLFW.glfwGetCurrentContext();
		return window != 0L && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
	}

	public static boolean mouseDown(int button) {
		long window = GLFW.glfwGetCurrentContext();
		return window != 0L && GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
	}

	public static String name(int code) {
		if (code < 0) {
			return "None";
		}
		if ((code & MODS) != 0) {
			boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
			return ((code & CTRL) != 0 ? (mac ? "Cmd+" : "Ctrl+") : "") + ((code & ALT) != 0 ? (mac ? "Opt+" : "Alt+") : "")
					+ ((code & SHIFT) != 0 ? "Shift+" : "") + name(code & 0xFFFF);
		}
		switch (code) {
			case 32: return "Space";
			case 256: return "Escape";
			case 257: return "Enter";
			case 258: return "Tab";
			case 259: return "Backspace";
			case 262: return "Right";
			case 263: return "Left";
			case 264: return "Down";
			case 265: return "Up";
			case 340: return "L Shift";
			case 341: return "L Ctrl";
			case 342: return "L Alt";
			case 344: return "R Shift";
			case 345: return "R Ctrl";
			case 346: return "R Alt";
			default:
		}
		if (code >= 290 && code <= 301) {
			return "F" + (code - 289);
		}
		String n = GLFW.glfwGetKeyName(code, 0);
		if (n != null && !n.isEmpty()) {
			return n.toUpperCase();
		}
		return "Key " + code;
	}
}
