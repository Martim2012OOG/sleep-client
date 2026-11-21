package dev.sleepclient.mods;

import dev.sleepclient.fx.Rainbow;
import dev.sleepclient.option.Option;

/**
 * Smooth Fishing Line: the fishing line is drawn with many more segments, so it is a smooth curve instead of a stair
 * of 16 straight pieces. It can also be coloured (one colour, rainbow, or a gradient between two colours).
 */
public class SmoothFishingLineMod extends Mod {

	public static SmoothFishingLineMod instance;

	private final Option.Num segments = num("Smoothness", 64, 16, 128, 8, " segments");
	private final Option.Choice mode = choice("Line Colour", 2, "Vanilla (black)", "Colour", "Rainbow", "Gradient", "Pulse");
	private final Option.Col colour = colour("Colour", 0xFFFF7A00);
	private final Option.Col colour2 = colour("Second Colour", 0xFF2E7BFF);
	private final Option.Num speed = num("Colour Speed", 40, 5, 200, 5, "%");
	private final Option.Num spread = num("Rainbow Spread", 100, 10, 300, 10, "%");

	/** Position of the vertex being coloured inside the line (the colour hook is called once per vertex, in order). */
	private static int counter;
	private static long calls;
	private final Option.Action info = action("Status", "Check", () -> { }, () -> calls == 0 ? "Colour hook not used yet (cast the rod)" : "Colour hook working (" + calls + ")");

	public SmoothFishingLineMod() {
		super("Smooth Fishing Line", "smooth_fishing_line", "Draws the fishing line as a smooth curve, and lets you colour it (rainbow and more).",
				Category.VISUAL);
		instance = this;
		colour.showIf(() -> mode.idx() == 1 || mode.idx() == 3 || mode.idx() == 4);
		colour2.showIf(() -> mode.idx() == 3);
		speed.showIf(() -> mode.idx() >= 2);
		spread.showIf(() -> mode.idx() == 2 || mode.idx() == 3);
	}

	/** Number of pieces for the line (vanilla uses 16). */
	public static int pieces() {
		return instance != null && instance.enabled ? Math.max(16, Math.round(instance.segments.f())) : 16;
	}

	/** True when the line has its own colour (not the vanilla black). */
	public static boolean coloured() {
		return instance != null && instance.enabled && instance.mode.idx() != 0;
	}

	/**
	 * Colour (ARGB) for the next vertex of the line. Called once per vertex while the line is drawn, from the first
	 * point to the last, so the position inside the line is counted here.
	 */
	public static int nextColour(int vanilla) {
		SmoothFishingLineMod m = instance;
		if (m == null || !m.enabled || m.mode.idx() == 0) {
			return vanilla;
		}
		calls++;
		int n = pieces() + 1;
		float t = (counter++ % n) / (float) Math.max(1, n - 1);
		float time = Rainbow.seconds() * (m.speed.f() / 100F);
		return switch (m.mode.idx()) {
			case 1 -> m.colour.c() | 0xFF000000;
			case 2 -> Rainbow.hue(time * 0.5F - t * (m.spread.f() / 100F));
			case 3 -> {
				// the gradient slides along the line (back and forth)
				float k = 0.5F + 0.5F * (float) Math.sin(time * 3F - t * 3F * (m.spread.f() / 100F));
				yield Rainbow.mix(m.colour.c() | 0xFF000000, m.colour2.c() | 0xFF000000, k);
			}
			default -> {
				// pulse: the colour breathes between dark and bright
				float k = 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(time * 4F - t * 2F));
				yield Rainbow.mix(0xFF000000, m.colour.c() | 0xFF000000, k);
			}
		};
	}
}
