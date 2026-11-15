package dev.solclient.mods;

import dev.solclient.option.Option;

/**
 * View tweaks in one place: a brightness boost, the first person fire and shield height, the pumpkin blur, and every fog
 * (blindness, darkness, lava, water, powder snow, the distance fog) can be switched off separately.
 */
public class BactroTweaksMod extends Mod {

	public static BactroTweaksMod instance;

	private final Option.Num brightness = num("Brightness Boost", 0, 0, 15, 1, "");
	private final Option.Num fire = num("Fire Offset", 0, -100, 100, 5, "");
	private final Option.Num shield = num("Shield Offset", 0, -100, 100, 5, "");
	private final Option.Bool pumpkin = bool("Pumpkin Blur", true);
	private final Option.Bool blindness = bool("Blindness Fog", true);
	private final Option.Bool darkness = bool("Darkness Fog", true);
	private final Option.Bool lava = bool("Lava Fog", true);
	private final Option.Bool water = bool("Water Fog", true);
	private final Option.Bool powder = bool("Powder Snow Fog", true);
	private final Option.Bool atmos = bool("Atmospheric Fog", true);

	public BactroTweaksMod() {
		super("Bactro Tweaks", "bactro_tweaks", "Brightness boost, fire and shield height, pumpkin blur and every fog on or off.", Category.VISUAL);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.enabled;
	}

	/** Lightmap gamma (1 = vanilla, up to 16), or null when the boost is 0. */
	public static Double boostGamma() {
		return on() && instance.brightness.f() > 0 ? Double.valueOf(1.0 + instance.brightness.f()) : null;
	}

	/** Extra vertical offset of the first person fire overlay (negative = lower). */
	public static double fireY() {
		return on() ? instance.fire.f() / 100.0 * 0.7 : 0;
	}

	/** Extra shield equip progress (positive = lower). */
	public static float shieldDown() {
		return on() ? -instance.shield.f() / 100F * 0.5F : 0F;
	}

	public static boolean pumpkinBlur() {
		return !on() || instance.pumpkin.on();
	}

	/** Should this fog modifier be switched off? (by its class name) */
	public static boolean fogOff(Object modifier) {
		if (!on() || modifier == null) {
			return false;
		}
		String n = modifier.getClass().getSimpleName();
		BactroTweaksMod m = instance;
		return n.contains("Blindness") ? !m.blindness.on() : n.contains("Darkness") ? !m.darkness.on() : n.contains("Lava") ? !m.lava.on()
				: n.contains("Water") ? !m.water.on() : n.contains("Powder") ? !m.powder.on() : n.contains("Atmospheric") ? !m.atmos.on() : false;
	}
}
