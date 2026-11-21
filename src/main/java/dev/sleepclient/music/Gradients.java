package dev.sleepclient.music;

import dev.sleepclient.Gfx;

/** Colour gradients for the lyrics and the now playing card: ready-made presets plus a "Custom" one the player builds. */
public final class Gradients {

	/** A preset: name and 2 to 3 colour stops (0xAARRGGBB). */
	public record Preset(String name, int... stops) {
	}

	/** The last entry ("Custom") takes its colours from the player's own pickers. */
	public static final Preset[] PRESETS = {
			new Preset("Aurora", 0xFF00FFAA, 0xFF8B5CF6, 0xFFEC4899),
			new Preset("Sunset", 0xFFFF512F, 0xFFF09819),
			new Preset("Neon", 0xFF00F5A0, 0xFF00D9F5),
			new Preset("Purple haze", 0xFF8B5CF6, 0xFFEC4899),
			new Preset("Ocean", 0xFF2193B0, 0xFF6DD5ED),
			new Preset("Fire", 0xFFF83600, 0xFFF9D423),
			new Preset("Candy", 0xFFFF6FB5, 0xFF7DD3FC),
			new Preset("Gold", 0xFFFDE047, 0xFFF59E0B),
			new Preset("Mint", 0xFFA8FF78, 0xFF78FFD6),
			new Preset("Cyber", 0xFFFF00C8, 0xFF00F0FF),
			new Preset("Vaporwave", 0xFFFF71CE, 0xFF01CDFE, 0xFFB967FF),
			new Preset("Lava", 0xFFFF0844, 0xFFFFB199),
			new Preset("Ice", 0xFFE0F7FF, 0xFF7DD3FC, 0xFF3B82F6),
			new Preset("Galaxy", 0xFF4C1D95, 0xFFC026D3, 0xFF38BDF8),
			new Preset("Rose gold", 0xFFF7CAC9, 0xFFE0A899),
			new Preset("Toxic", 0xFFB6FF00, 0xFF00C853),
			new Preset("Custom", 0xFF8B5CF6, 0xFFEC4899),
	};

	public static String[] names() {
		String[] n = new String[PRESETS.length];
		for (int i = 0; i < n.length; i++) {
			n[i] = PRESETS[i].name();
		}
		return n;
	}

	public static boolean isCustom(int preset) {
		return preset >= PRESETS.length - 1;
	}

	/** The stops of a preset (or of the custom colours when it is "Custom"). */
	public static int[] stops(int preset, int[] custom, int customCount) {
		if (isCustom(preset)) {
			int[] c = new int[Math.max(2, Math.min(custom.length, customCount))];
			System.arraycopy(custom, 0, c, 0, c.length);
			return c;
		}
		return PRESETS[Math.max(0, preset)].stops();
	}

	/**
	 * The colour at position p (0..1) of the gradient. With flow &gt; 0 the gradient slides along and bounces, so the colours move
	 * through the words instead of staying put.
	 */
	public static int at(int[] stops, float p, float flow, double time) {
		if (stops.length == 1) {
			return stops[0] | 0xFF000000;
		}
		float x = p;
		if (flow > 0F) {
			x = (float) (p * 0.8 + time * flow * 0.35);
			x = x - (float) Math.floor(x / 2F) * 2F;
			if (x > 1F) {
				x = 2F - x;
			}
		}
		x = Math.max(0F, Math.min(0.9999F, x)) * (stops.length - 1);
		int i = (int) x;
		return Gfx.blend(stops[i] | 0xFF000000, stops[i + 1] | 0xFF000000, x - i) | 0xFF000000;
	}
}
