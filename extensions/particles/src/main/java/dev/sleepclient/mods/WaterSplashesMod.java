package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/** Splashes when you move in water and rain hits water. */
public class WaterSplashesMod extends Mod {

	private final Option.Num density = num("Density", 4, 1, 12, 1, "");
	private final java.util.Random rnd = new java.util.Random();

	public WaterSplashesMod() {
		super("Water Splashes", "water_splashes", "Splashes when you move in water and rain hits water.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		var w = mc.world;
		if (p == null || w == null) return;
		for (int i = 0; i < density.i(); i++) {
			double x = p.getX() + (rnd.nextDouble() - 0.5) * 24, y = p.getY() + (rnd.nextDouble() - 0.3) * 10, z = p.getZ() + (rnd.nextDouble() - 0.5) * 24;
			BlockPos pos = BlockPos.ofFloored(x, y, z);
			if (i == 0 && p.isTouchingWater() && p.getVelocity().horizontalLengthSquared() > 0.003) Fx.burst(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.3, p.getZ(), 6, 0.2);
			if (w.isRaining() && w.getFluidState(pos).isStill() && w.getBlockState(pos.up()).isAir()) Fx.spawn(ParticleTypes.SPLASH, x, Math.floor(y) + 1, z, 0, 0.05, 0);
		}
	}
}
