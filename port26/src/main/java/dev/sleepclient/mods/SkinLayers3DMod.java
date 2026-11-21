package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** 3D Skin Layers: hat, jacket, sleeves and pants stick out of the body as real 3D pixels. See SkinLayers3D. */
public class SkinLayers3DMod extends Mod {

	public static SkinLayers3DMod instance;

	private final Option.Num thickness = num("Thickness", 100, 40, 160, 10, "%");
	private final Option.Num range = num("Max Distance", 14, 4, 48, 2, " blocks");
	private final Option.Choice quality = choice("Quality", 1, "Low (many players)", "Medium", "High");
	private final Option.Bool hat = bool("Hat Layer", true);
	private final Option.Bool jacket = bool("Jacket Layer", true);
	private final Option.Bool sleeves = bool("Sleeve Layers", true);
	private final Option.Bool pants = bool("Pants Layers", true);

	public SkinLayers3DMod() {
		super("3D Skin Layers", "skin_layers_3d", "The second layer of every skin (hat, jacket, sleeves, pants) sticks out of the body as real 3D pixels. Works with every skin, no lag.", Category.VISUAL);
		instance = this;
		range.tip("Players farther away keep the normal flat layers (nobody can see the difference there, and it saves FPS).");
		quality.tip("How many 3D pixels may be drawn per frame in total. In a crowded place the nearest players get the 3D layers, the rest stay flat.");
	}

	public double range() {
		return range.f();
	}

	/** Thickness in tenths of a pixel (10 = one pixel, like the game's armour). */
	public int thickness10() {
		return Math.round(thickness.f() / 10F);
	}

	public int budgetQuads() {
		return switch (quality.idx()) {
			case 0 -> 16_000;
			case 2 -> 90_000;
			default -> 40_000;
		};
	}

	public boolean hat() {
		return hat.on();
	}

	public boolean jacket() {
		return jacket.on();
	}

	public boolean sleeves() {
		return sleeves.on();
	}

	public boolean pants() {
		return pants.on();
	}
}
