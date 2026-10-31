package dev.solclient.mods;

import dev.solclient.option.Option;

/** Client-side weather: always clear, or always rain / thunder. Only changes what you see. */
public class WeatherMod extends Mod {

	public static WeatherMod instance;
	public final Option.Choice weather = choice("Weather", 0, "Clear", "Rain", "Thunder");

	public WeatherMod() {
		super("Weather Changer", "weather", "Change the weather you see (clear, rain or thunder).", Category.VISUAL);
		instance = this;
	}

	/** Rain amount to show, or -1 to keep the server's. */
	public static float rain() {
		if (instance == null || !instance.enabled) {
			return -1F;
		}
		return instance.weather.idx() == 0 ? 0F : 1F;
	}

	public static float thunder() {
		if (instance == null || !instance.enabled) {
			return -1F;
		}
		return instance.weather.idx() == 2 ? 1F : 0F;
	}
}
