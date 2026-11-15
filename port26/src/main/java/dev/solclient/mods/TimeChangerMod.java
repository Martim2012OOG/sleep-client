package dev.solclient.mods;

import dev.solclient.option.Option;

public class TimeChangerMod extends Mod {

	public static TimeChangerMod instance;

	private final Option.Num time = num("Time", 1000, 0, 24000, 1, "", false);

	public TimeChangerMod() {
		super("Time Changer", "time_changer", "Change the visual time.", Category.VISUAL);
		instance = this;
	}

	/** Used by WorldMixin. */
	public static long override(long original) {
		if (instance == null || !instance.enabled) {
			return original;
		}
		return (long) instance.time.f();
	}
}
