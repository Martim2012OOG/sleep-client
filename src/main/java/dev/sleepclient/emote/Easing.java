package dev.sleepclient.emote;

import java.util.Locale;

/**
 * The easing curves of Emotecraft / PlayerAnimator keyframes (the "easing" of each move), with the same names.
 * Before this only LINEAR, CONSTANT and EASEINOUTQUAD were known and every other name (EASEINSINE, EASEINQUAD ...)
 * silently became LINEAR, so those moves looked stiff instead of smooth.
 */
public final class Easing {

	public static final int LINEAR = 0, CONSTANT = 1;

	private static final String[] NAMES = {"LINEAR", "CONSTANT",
			"EASEINSINE", "EASEOUTSINE", "EASEINOUTSINE",
			"EASEINCUBIC", "EASEOUTCUBIC", "EASEINOUTCUBIC",
			"EASEINQUAD", "EASEOUTQUAD", "EASEINOUTQUAD",
			"EASEINQUART", "EASEOUTQUART", "EASEINOUTQUART",
			"EASEINQUINT", "EASEOUTQUINT", "EASEINOUTQUINT",
			"EASEINEXPO", "EASEOUTEXPO", "EASEINOUTEXPO",
			"EASEINCIRC", "EASEOUTCIRC", "EASEINOUTCIRC",
			"EASEINBACK", "EASEOUTBACK", "EASEINOUTBACK",
			"EASEINELASTIC", "EASEOUTELASTIC", "EASEINOUTELASTIC",
			"EASEINBOUNCE", "EASEOUTBOUNCE", "EASEINOUTBOUNCE"};

	private static final double C1 = 1.70158, C2 = C1 * 1.525, C3 = C1 + 1, C4 = 2 * Math.PI / 3, C5 = 2 * Math.PI / 4.5;

	private Easing() {
	}

	/** Index of an easing name (unknown names = LINEAR, like PlayerAnimator). */
	public static int parse(String name) {
		if (name != null) {
			String n = name.trim().toUpperCase(Locale.ROOT);
			for (int i = 0; i < NAMES.length; i++) {
				if (NAMES[i].equals(n)) {
					return i;
				}
			}
		}
		return LINEAR;
	}

	public static String name(int type) {
		return type >= 0 && type < NAMES.length ? NAMES[type] : "LINEAR";
	}

	/** u in 0..1 -> eased 0..1. */
	public static float apply(int type, float u) {
		double x = Math.max(0.0, Math.min(1.0, u));
		if (type <= LINEAR || type >= NAMES.length) {
			return (float) x;
		}
		return (float) ease(type, x);
	}

	private static double bounceOut(double x) {
		double n1 = 7.5625, d1 = 2.75;
		if (x < 1 / d1) {
			return n1 * x * x;
		} else if (x < 2 / d1) {
			x -= 1.5 / d1;
			return n1 * x * x + 0.75;
		} else if (x < 2.5 / d1) {
			x -= 2.25 / d1;
			return n1 * x * x + 0.9375;
		}
		x -= 2.625 / d1;
		return n1 * x * x + 0.984375;
	}

	private static double ease(int type, double x) {
		switch (type) {
			case CONSTANT:
				return x >= 1.0 ? 1.0 : 0.0;
			// sine
			case 2: return 1 - Math.cos(x * Math.PI / 2);
			case 3: return Math.sin(x * Math.PI / 2);
			case 4: return -(Math.cos(Math.PI * x) - 1) / 2;
			// cubic
			case 5: return x * x * x;
			case 6: return 1 - Math.pow(1 - x, 3);
			case 7: return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
			// quad
			case 8: return x * x;
			case 9: return 1 - (1 - x) * (1 - x);
			case 10: return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
			// quart
			case 11: return x * x * x * x;
			case 12: return 1 - Math.pow(1 - x, 4);
			case 13: return x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
			// quint
			case 14: return x * x * x * x * x;
			case 15: return 1 - Math.pow(1 - x, 5);
			case 16: return x < 0.5 ? 16 * x * x * x * x * x : 1 - Math.pow(-2 * x + 2, 5) / 2;
			// expo
			case 17: return x == 0 ? 0 : Math.pow(2, 10 * x - 10);
			case 18: return x == 1 ? 1 : 1 - Math.pow(2, -10 * x);
			case 19:
				if (x == 0 || x == 1) {
					return x;
				}
				return x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2;
			// circ
			case 20: return 1 - Math.sqrt(1 - x * x);
			case 21: return Math.sqrt(1 - Math.pow(x - 1, 2));
			case 22:
				return x < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2;
			// back
			case 23: return C3 * x * x * x - C1 * x * x;
			case 24: return 1 + C3 * Math.pow(x - 1, 3) + C1 * Math.pow(x - 1, 2);
			case 25:
				return x < 0.5 ? (Math.pow(2 * x, 2) * ((C2 + 1) * 2 * x - C2)) / 2
						: (Math.pow(2 * x - 2, 2) * ((C2 + 1) * (x * 2 - 2) + C2) + 2) / 2;
			// elastic
			case 26:
				if (x == 0 || x == 1) {
					return x;
				}
				return -Math.pow(2, 10 * x - 10) * Math.sin((x * 10 - 10.75) * C4);
			case 27:
				if (x == 0 || x == 1) {
					return x;
				}
				return Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * C4) + 1;
			case 28:
				if (x == 0 || x == 1) {
					return x;
				}
				return x < 0.5 ? -(Math.pow(2, 20 * x - 10) * Math.sin((20 * x - 11.125) * C5)) / 2
						: (Math.pow(2, -20 * x + 10) * Math.sin((20 * x - 11.125) * C5)) / 2 + 1;
			// bounce
			case 29: return 1 - bounceOut(1 - x);
			case 30: return bounceOut(x);
			case 31: return x < 0.5 ? (1 - bounceOut(1 - 2 * x)) / 2 : (1 + bounceOut(2 * x - 1)) / 2;
			default: return x;
		}
	}
}
