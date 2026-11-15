package dev.solclient.mods;

import net.minecraft.world.entity.player.Player;

/** Pings nearby players: how many and where the closest one is. */
public class SonarMod extends TextHudMod {

	private final dev.solclient.option.Option.Num range = num("Range", 48, 8, 128, 8, " blocks");
	private final dev.solclient.option.Option.Bool ring = bool("Ping Ring", true);
	private int t;

	public SonarMod() {
		super("Sonar", "sonar", "Pings players near you.", 0.5F, 0.08F);
	}

	@Override
	public void tick() {
		if (mc.player != null && ring.on() && ++t % 60 == 0) {
			dev.solclient.fx.Fx.ring(net.minecraft.core.particles.ParticleTypes.END_ROD, mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(), 0.6, 32, 0.25);
		}
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null || mc.level == null) return edit ? "Sonar: 2 players | closest 14m" : null;
		double r2 = range.f() * range.f(), best = Double.MAX_VALUE;
		int n = 0;
		for (Player o : mc.level.players()) {
			if (o == mc.player || o.isSpectator()) continue;
			double d = o.distanceToSqr(mc.player);
			if (d <= r2) { n++; best = Math.min(best, d); }
		}
		return n == 0 ? "Sonar: nobody near" : "Sonar: " + n + (n == 1 ? " player" : " players") + " | closest " + Math.round(Math.sqrt(best)) + "m";
	}
}
