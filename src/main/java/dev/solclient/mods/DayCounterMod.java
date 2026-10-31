package dev.solclient.mods;

/** Shows the in-game day ("Day 12"). */
public class DayCounterMod extends TextHudMod {

	public DayCounterMod() {
		super("Day Counter", "day_counter", "Shows how many in-game days have passed in this world.", 0.01F, 0.62F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.world == null) {
			return edit ? "Day 1" : null;
		}
		return "Day " + (mc.world.getTimeOfDay() / 24000L + 1);
	}
}
