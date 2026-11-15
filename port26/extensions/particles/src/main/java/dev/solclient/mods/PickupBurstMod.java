package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;

/** A little sparkle when you pick items up. */
public class PickupBurstMod extends Mod {

	private final Option.Choice type = choice("Particle", 5, Fx.NAMES);
	private int lastCount = -1;

	public PickupBurstMod() {
		super("Pickup Burst", "pickup_burst", "Sparkles when you pick up items.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null) {
			lastCount = -1;
			return;
		}
		var inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			n += inv.getItem(i).getCount();
		}
		if (lastCount >= 0 && n > lastCount && dev.solclient.compat.Mc.screen(mc) == null) {
			Fx.burst(Fx.type(type.idx()), p.getX(), p.getY() + 0.8, p.getZ(), 8, 0.2);
		}
		lastCount = n;
	}
}
