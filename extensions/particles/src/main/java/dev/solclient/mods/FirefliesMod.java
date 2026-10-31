package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/** Glowing fireflies at night near grass and flowers. */
public class FirefliesMod extends Mod {

	private final Option.Num density = num("Density", 4, 1, 12, 1, "");
	private final java.util.Random rnd = new java.util.Random();

	public FirefliesMod() {
		super("Fireflies", "fireflies", "Glowing fireflies at night near grass and flowers.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		var w = mc.world;
		if (p == null || w == null) return;
		for (int i = 0; i < density.i(); i++) {
			double x = p.getX() + (rnd.nextDouble() - 0.5) * 24, y = p.getY() + (rnd.nextDouble() - 0.3) * 10, z = p.getZ() + (rnd.nextDouble() - 0.5) * 24;
			BlockPos pos = BlockPos.ofFloored(x, y, z);
			long time = w.getTimeOfDay() % 24000;
			if (time < 13000 || time > 23000 || !w.getBlockState(pos).isAir() || w.getLightLevel(LightType.SKY, pos) < 8) continue;
			if (rnd.nextInt(3) == 0) Fx.spawn(ParticleTypes.GLOW, x, y, z, (rnd.nextDouble() - 0.5) * 0.02, 0.005, (rnd.nextDouble() - 0.5) * 0.02);
		}
	}
}
