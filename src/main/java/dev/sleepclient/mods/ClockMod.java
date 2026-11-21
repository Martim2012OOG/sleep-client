package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockMod extends TextHudMod {

	private final Option.Bool hour24 = bool("24 Hour Clock", true);
	private final Option.Bool seconds = bool("Show Seconds", false);

	public ClockMod() {
		super("Clock", "clock", "Shows the real time.", 0.01F, 0.30F);
	}

	@Override
	protected String text(boolean edit) {
		String p = (hour24.on() ? "HH:mm" : "h:mm") + (seconds.on() ? ":ss" : "") + (hour24.on() ? "" : " a");
		return LocalTime.now().format(DateTimeFormatter.ofPattern(p));
	}
}
