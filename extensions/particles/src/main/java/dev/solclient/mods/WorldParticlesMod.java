package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/** Ambient particles everywhere: snow, ash, petals, spores. */
public class WorldParticlesMod extends Mod {

	private final Option.Num density = num("Density", 4, 1, 12, 1, "");
	private final Option.Choice style = choice("Style", 0, "Snow", "Ash", "Petals", "Crimson Spores", "Warped Spores");
	private final java.util.Random rnd = new java.util.Random();

	public WorldParticlesMod() {
		super("World Particles", "world_particles", "Ambient particles everywhere: snow, ash, petals, spores.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		var w = mc.world;
		if (p == null || w == null) return;
		for (int i = 0; i < density.i(); i++) {
			double x = p.getX() + (rnd.nextDouble() - 0.5) * 24, y = p.getY() + (rnd.nextDouble() - 0.3) * 10, z = p.getZ() + (rnd.nextDouble() - 0.5) * 24;
			BlockPos pos = BlockPos.ofFloored(x, y, z);
			if (!w.getBlockState(pos).isAir()) continue;
			var t = switch (style.idx()) { case 1 -> ParticleTypes.ASH; case 2 -> ParticleTypes.SPORE_BLOSSOM_AIR; case 3 -> ParticleTypes.CRIMSON_SPORE; case 4 -> ParticleTypes.WARPED_SPORE; default -> ParticleTypes.SNOWFLAKE; };
			Fx.spawn(t, x, y, z, 0, -0.02, 0);
		}
	}
}
