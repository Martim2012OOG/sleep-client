package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** A spinning marker above the last thing you hit. */
public class TargetMarkerMod extends Mod {

	public static TargetMarkerMod instance;
	private final Option.Choice type = choice("Particle", 9, Fx.NAMES);
	private final Option.Num seconds = num("Show For", 5, 1, 20, 1, " s");
	private Entity target;
	private long until;
	private float spin;

	public TargetMarkerMod() {
		super("Target Marker", "target_marker", "Marks the player or mob you are fighting.", Category.VISUAL);
		instance = this;
	}

	public void onAttack(Entity e) {
		if (e instanceof LivingEntity) {
			target = e;
			until = System.currentTimeMillis() + (long) (seconds.f() * 1000);
		}
	}

	@Override
	public void tick() {
		Entity t = target;
		if (t == null || System.currentTimeMillis() > until || !t.isAlive() || t.isRemoved()) {
			target = null;
			return;
		}
		spin += 0.35F;
		double r = Math.max(0.4, t.getWidth() * 0.7);
		for (int i = 0; i < 3; i++) {
			double a = spin + i * Math.PI * 2 / 3;
			Fx.spawn(Fx.type(type.idx()), t.getX() + Math.cos(a) * r, t.getY() + t.getHeight() + 0.3, t.getZ() + Math.sin(a) * r, 0, 0, 0);
		}
	}
}
