package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** Little bits of the block you walk on fly up from your feet (sand, snow, grass...). */
public class MaterialFootstepsMod extends Mod {

	private final Option.Num amount = num("Amount", 2, 1, 6, 1, "");
	private final Option.Bool others = bool("Other Players Too", true);
	private int t;

	public MaterialFootstepsMod() {
		super("Material Footsteps", "material_footsteps", "Bits of the block you walk on at your feet.", Category.VISUAL);
	}

	@Override
	public void tick() {
		if (mc.world == null || mc.player == null || ++t % 3 != 0) return;
		for (var p : mc.world.getPlayers()) {
			if (p != mc.player && !others.on()) continue;
			if (!p.isOnGround() || p.isSneaking() || p.getVelocity().horizontalLengthSquared() < 0.002 || p.squaredDistanceTo(mc.player) > 32 * 32) continue;
			var below = p.getBlockPos().down();
			var st = mc.world.getBlockState(below);
			if (st.isAir()) continue;
			var fx = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
			for (int i = 0; i < amount.i(); i++) {
				Fx.spawn(fx, p.getX() + (Math.random() - 0.5) * 0.4, p.getY() + 0.05, p.getZ() + (Math.random() - 0.5) * 0.4, 0, 0.08, 0);
			}
		}
	}
}
