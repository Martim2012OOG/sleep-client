package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

public class MemoryMod extends TextHudMod {

	private final Option.Choice style = choice("Style", 0, "Percent", "Used / Max");

	public MemoryMod() {
		super("Memory", "memory", "Shows how much RAM the game is using.", 0.01F, 0.42F);
	}

	@Override
	protected String text(boolean edit) {
		Runtime r = Runtime.getRuntime();
		long used = r.totalMemory() - r.freeMemory(), max = r.maxMemory();
		if (style.idx() == 0) {
			return "RAM " + Math.round(used * 100.0 / max) + "%";
		}
		return String.format("%d / %d MB", used / 1048576L, max / 1048576L);
	}
}
