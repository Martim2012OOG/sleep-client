package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;

/** Particles slowly circling around you. */
public class AuraMod extends Mod {

	private final Option.Choice type = choice("Particle", 4, Fx.NAMES);
	private final Option.Num radius = num("Radius", 0.8F, 0.4F, 2F, 0.1F, " blocks");
	private final Option.Num speed = num("Speed", 1, 0.2F, 3F, 0.1F, "x");
	private final Option.Choice style = choice("Style", 0, "Orbit", "Helix", "Crown");
	private float angle;

	public AuraMod() {
		super("Aura", "aura", "Particles circling around your player.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null || p.isSpectator() || p.isInvisible()) {
			return;
		}
		angle += 0.25F * speed.f();
		double r = radius.f();
		var t = Fx.type(type.idx());
		switch (style.idx()) {
			case 1 -> {
				double h = (Math.sin(angle * 0.5) + 1) * 0.5 * p.getBbHeight();
				Fx.spawn(t, p.getX() + Math.cos(angle) * r, p.getY() + h, p.getZ() + Math.sin(angle) * r, 0, 0, 0);
				Fx.spawn(t, p.getX() - Math.cos(angle) * r, p.getY() + p.getBbHeight() - h, p.getZ() - Math.sin(angle) * r, 0, 0, 0);
			}
			case 2 -> {
				for (int i = 0; i < 3; i++) {
					double a = angle + i * Math.PI * 2 / 3;
					Fx.spawn(t, p.getX() + Math.cos(a) * r * 0.5, p.getY() + p.getBbHeight() + 0.35, p.getZ() + Math.sin(a) * r * 0.5, 0, 0, 0);
				}
			}
			default -> {
				for (int i = 0; i < 2; i++) {
					double a = angle + i * Math.PI;
					Fx.spawn(t, p.getX() + Math.cos(a) * r, p.getY() + 1.0, p.getZ() + Math.sin(a) * r, 0, 0, 0);
				}
			}
		}
	}
}
