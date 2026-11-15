package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;

/** Outlines the hitboxes of players and mobs near you. */
public class HitboxesMod extends Mod {

	private final Option.Num range = num("Range", 12, 4, 32, 1, " blocks");
	private final Option.Bool playersOnly = bool("Players Only", false);
	private int t;

	public HitboxesMod() {
		super("Hitboxes", "hitboxes", "Shows the hitboxes of players and mobs.", Category.VISUAL);
	}

	@Override
	public void tick() {
		if (mc.player == null || mc.level == null || ++t % 6 != 0) return;
		double r2 = range.f() * range.f();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !(e instanceof net.minecraft.world.entity.LivingEntity) || e.distanceToSqr(mc.player) > r2) continue;
			if (playersOnly.on() && !(e instanceof net.minecraft.world.entity.player.Player)) continue;
			var b = e.getBoundingBox();
			double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
			for (double s = 0; s <= 1.0001; s += 0.25) {
				double x = b.minX + (b.maxX - b.minX) * s, y = b.minY + (b.maxY - b.minY) * s, z = b.minZ + (b.maxZ - b.minZ) * s;
				for (double yy : ys) for (double zz : zs) Fx.spawn(ParticleTypes.WAX_OFF, x, yy, zz, 0, 0, 0);
				for (double xx : xs) for (double zz : zs) Fx.spawn(ParticleTypes.WAX_OFF, xx, y, zz, 0, 0, 0);
				for (double xx : xs) for (double yy : ys) Fx.spawn(ParticleTypes.WAX_OFF, xx, yy, z, 0, 0, 0);
			}
		}
	}
}
