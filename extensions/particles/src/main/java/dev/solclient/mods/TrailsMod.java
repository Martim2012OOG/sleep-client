package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;

/** Particles follow you while you move. */
public class TrailsMod extends Mod {

	private final Option.Choice type = choice("Particle", 0, Fx.NAMES);
	private final Option.Num amount = num("Amount", 2, 1, 6, 1, "");
	private final Option.Bool stillToo = bool("Show When Standing", false);
	private final Option.Bool hideFirstPerson = bool("Hide In First Person", false);

	public TrailsMod() {
		super("Trails", "trails", "Leave a trail of particles behind you.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null || p.isSpectator() || p.isInvisible()) {
			return;
		}
		if (hideFirstPerson.on() && mc.options.getPerspective().isFirstPerson()) {
			return;
		}
		var v = p.getVelocity();
		boolean moving = v.x * v.x + v.z * v.z > 0.0025;
		if (!moving && !stillToo.on()) {
			return;
		}
		var r = java.util.concurrent.ThreadLocalRandom.current();
		for (int i = 0; i < amount.i(); i++) {
			Fx.spawn(Fx.type(type.idx()), p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + 0.1 + r.nextDouble() * 0.2,
					p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
		}
	}
}
