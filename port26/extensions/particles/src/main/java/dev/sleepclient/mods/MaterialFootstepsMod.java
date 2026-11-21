package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;

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
		if (mc.level == null || mc.player == null || ++t % 3 != 0) return;
		for (var p : mc.level.players()) {
			if (p != mc.player && !others.on()) continue;
			if (!p.onGround() || p.isShiftKeyDown() || p.getDeltaMovement().horizontalDistanceSqr() < 0.002 || p.distanceToSqr(mc.player) > 32 * 32) continue;
			var below = p.blockPosition().below();
			var st = mc.level.getBlockState(below);
			if (st.isAir()) continue;
			var fx = new BlockParticleOption(ParticleTypes.BLOCK, st);
			for (int i = 0; i < amount.i(); i++) {
				Fx.spawn(fx, p.getX() + (Math.random() - 0.5) * 0.4, p.getY() + 0.05, p.getZ() + (Math.random() - 0.5) * 0.4, 0, 0.08, 0);
			}
		}
	}
}
