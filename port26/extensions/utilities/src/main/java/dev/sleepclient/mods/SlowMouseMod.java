package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

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
		boolean d = dev.sleepclient.compat.Mc.screen(mc) == null && dev.sleepclient.Keys.down(key.code());
		var s = mc.options.sensitivity();
		if (d && saved == null) {
			saved = s.get();
			s.set(saved * factor.f() / 100.0);
		} else if (!d && saved != null) {
			s.set(saved);
			saved = null;
		}
	}

	@Override
	protected void onDisable() {
		if (saved != null) {
			mc.options.sensitivity().set(saved);
			saved = null;
		}
	}
}
