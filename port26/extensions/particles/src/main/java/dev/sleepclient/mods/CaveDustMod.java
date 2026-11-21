package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.LightLayer;

/** Floating dust in caves and dark places. */
public class CaveDustMod extends Mod {

	private final Option.Num density = num("Density", 4, 1, 12, 1, "");
	private final java.util.Random rnd = new java.util.Random();

	public CaveDustMod() {
		super("Cave Dust", "cave_dust", "Floating dust in caves and dark places.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		var w = mc.level;
		if (p == null || w == null) return;
		for (int i = 0; i < density.i(); i++) {
			double x = p.getX() + (rnd.nextDouble() - 0.5) * 24, y = p.getY() + (rnd.nextDouble() - 0.3) * 10, z = p.getZ() + (rnd.nextDouble() - 0.5) * 24;
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!w.getBlockState(pos).isAir() || w.getBrightness(LightLayer.SKY, pos) > 0) continue;
			Fx.spawn(ParticleTypes.WHITE_ASH, x, y, z, 0, -0.002, 0);
		}
	}
}
