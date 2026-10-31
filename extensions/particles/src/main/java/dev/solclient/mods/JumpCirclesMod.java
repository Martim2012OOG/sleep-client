package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;

/** A ring of particles on the ground every time you jump. */
public class JumpCirclesMod extends Mod {

	private final Option.Choice type = choice("Particle", 3, Fx.NAMES);
	private final Option.Num size = num("Size", 1, 0.5F, 2.5F, 0.1F, " blocks");
	private boolean wasOnGround = true;

	public JumpCirclesMod() {
		super("Jump Circles", "jump_circles", "A ring of particles where you jump.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null) {
			return;
		}
		boolean ground = p.isOnGround();
		if (wasOnGround && !ground && p.getVelocity().y > 0.2 && !p.isTouchingWater() && !p.hasVehicle()) {
			Fx.ring(Fx.type(type.idx()), p.getX(), p.getY() + 0.05, p.getZ(), size.f(), 24, 0.06);
		}
		wasOnGround = ground;
	}
}
