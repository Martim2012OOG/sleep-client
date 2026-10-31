package dev.solclient.mods;

import dev.solclient.option.Option;

/** Hold a key to make the mouse slower (for building and aiming). */
public class SlowMouseMod extends Mod {

	private final Option.Key key = key("Hold Key", 86); // V
	private final Option.Num factor = num("Speed", 30, 5, 90, 5, "%");
	private Double saved;

	public SlowMouseMod() {
		super("Slow Mouse", "slow_mouse", "Hold a key to slow your mouse down.", Category.UTILITY);
	}

	@Override
	public void tick() {
		boolean d = mc.currentScreen == null && dev.solclient.Keys.down(key.code());
		var s = mc.options.getMouseSensitivity();
		if (d && saved == null) {
			saved = s.getValue();
			s.setValue(saved * factor.f() / 100.0);
		} else if (!d && saved != null) {
			s.setValue(saved);
			saved = null;
		}
	}

	@Override
	protected void onDisable() {
		if (saved != null) {
			mc.options.getMouseSensitivity().setValue(saved);
			saved = null;
		}
	}
}
