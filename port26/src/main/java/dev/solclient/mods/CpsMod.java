package dev.solclient.mods;

import dev.solclient.CpsMonitor;
import dev.solclient.option.Option;

public class CpsMod extends SimpleHudMod {

	private final Option.Bool rmb = bool("RMB", false);

	public CpsMod() {
		super("CPS", "cps", "Display your CPS (clicks per second).", 0.01F, 0.15F);
	}

	@Override
	public void frame() {
		// CpsMonitor is polled globally; nothing to do here.
	}

	@Override
	public String getText(boolean edit) {
		if (rmb.on()) {
			return (edit ? 0 : CpsMonitor.LMB.getCps()) + " | " + (edit ? 0 : CpsMonitor.RMB.getCps()) + " CPS";
		}
		return (edit ? 0 : CpsMonitor.LMB.getCps()) + " CPS";
	}
}
