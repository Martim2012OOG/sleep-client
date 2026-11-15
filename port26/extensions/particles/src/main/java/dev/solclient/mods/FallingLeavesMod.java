package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

/** Leaves drift down from trees. */
public class FallingLeavesMod extends Mod {

	private final Option.Num density = num("Density", 4, 1, 12, 1, "");
	private final java.util.Random rnd = new java.util.Random();

	public FallingLeavesMod() {
		super("Falling Leaves", "falling_leaves", "Leaves drift down from trees.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		var w = mc.level;
		if (p == null || w == null) return;
		for (int i = 0; i < density.i(); i++) {
			double x = p.getX() + (rnd.nextDouble() - 0.5) * 24, y = p.getY() + (rnd.nextDouble() - 0.3) * 10, z = p.getZ() + (rnd.nextDouble() - 0.5) * 24;
			BlockPos pos = BlockPos.containing(x, y, z);
			BlockPos up = pos.above();
			if (!w.getBlockState(pos).isAir() || !w.getBlockState(up).is(net.minecraft.tags.BlockTags.LEAVES)) continue;
			Fx.spawn(ParticleTypes.COMPOSTER, x, y + 0.9, z, (rnd.nextDouble() - 0.5) * 0.03, -0.03, (rnd.nextDouble() - 0.5) * 0.03);
		}
	}
}
