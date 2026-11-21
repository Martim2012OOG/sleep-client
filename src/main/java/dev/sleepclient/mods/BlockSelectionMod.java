package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Customises the vanilla block outline (colour / width) through WorldRendererMixin. */
public class BlockSelectionMod extends Mod {

	public static BlockSelectionMod instance;

	private final Option.Bool outline = bool("Outline", true);
	private final Option.Num outlineWidth = num("Outline Width", 2, 1, 10, 1, "");
	private final Option.Col outlineColour = colour("Outline Colour", 0x66000000);
	private final Option.Bool rainbow = bool("Rainbow", false);
	private final Option.Num rainbowSpeed = num("Rainbow Speed", 4, 1, 10, 1, "");

	public BlockSelectionMod() {
		super("Block Selection", "block_selection", "Customise block selection.", Category.VISUAL);
		instance = this;
	}

	public static int color(int original) {
		if (instance == null || !instance.enabled) {
			return original;
		}
		if (!instance.outline.on()) {
			return 0;
		}
		if (instance.rainbow.on()) {
			float hue = ((System.currentTimeMillis() % 10000L) / 10000F * instance.rainbowSpeed.f()) % 1F;
			return hsv(hue);
		}
		return instance.outlineColour.c();
	}

	public static float width(float original) {
		if (instance == null || !instance.enabled) {
			return original;
		}
		return instance.outlineWidth.f();
	}

	private static int hsv(float h) {
		float x = 1 - Math.abs((h * 6) % 2 - 1);
		float r, g, b;
		switch ((int) (h * 6) % 6) {
			case 0: r = 1; g = x; b = 0; break;
			case 1: r = x; g = 1; b = 0; break;
			case 2: r = 0; g = 1; b = x; break;
			case 3: r = 0; g = x; b = 1; break;
			case 4: r = x; g = 0; b = 1; break;
			default: r = 1; g = 0; b = x;
		}
		return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}
}
