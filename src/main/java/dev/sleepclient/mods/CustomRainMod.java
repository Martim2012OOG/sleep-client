package dev.sleepclient.mods;

import dev.sleepclient.fx.LiquidFx;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * Custom Rain: when it rains, puddles slowly form on the ground (only where the rain really reaches it and the biome has rain, not
 * snow), rings spread in them as the drops land, open water gets rings too, and when the rain stops the puddles dry up. Follows the
 * rain you see, so it also works with Weather Changer. Particles only: no shaders.
 */
public class CustomRainMod extends Mod {

	public static CustomRainMod instance;

	private final Option.Bool puddles = bool("Puddles", true);
	private final Option.Num amount = num("Puddle Amount", 60, 0, 100, 5, "%");
	private final Option.Num size = num("Puddle Size", 100, 50, 200, 10, "%");
	private final Option.Bool ripples = bool("Ripples", true);
	private final Option.Num rate = num("Ripple Amount", 5, 1, 10, 1, "");
	private final Option.Bool water = bool("Ripples On Water", true);
	private final Option.Bool auto = bool("Protect My FPS", true);

	/** x, y, z, half width, tick it appeared, lifetime (ticks). */
	private final List<double[]> list = new ArrayList<>();
	private int tick, dry;

	public CustomRainMod() {
		super("Custom Rain", "custom_rain", "Rain leaves puddles on the ground with ripples, and rings on water. They dry up when it stops.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	protected void onDisable() {
		list.clear();
	}

	private float quality() {
		if (!auto.on()) {
			return 1F;
		}
		int fps = mc.getCurrentFps();
		return fps >= 60 ? 1F : fps >= 40 ? 0.6F : fps >= 25 ? 0.25F : 0F;
	}

	/** The y of the top of a plain, open, rainy spot at this column (the surface a puddle can lie on), or NaN. */
	private double ground(ClientWorld w, int bx, int bz) {
		int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
		BlockPos below = new BlockPos(bx, top - 1, bz), above = new BlockPos(bx, top, bz);
		if (!w.getBlockState(above).isAir() || !w.getFluidState(below).isEmpty()) {
			return Double.NaN;
		}
		BlockState st = w.getBlockState(below);
		if (st.isAir() || st.isIn(BlockTags.LEAVES) || !st.isSideSolidFullSquare(w, below, Direction.UP)) {
			return Double.NaN;
		}
		if (w.getBiome(above).value().getPrecipitation(above, w.getSeaLevel()) != Biome.Precipitation.RAIN) {
			return Double.NaN;
		}
		return top + 0.012;
	}

	@Override
	public void tick() {
		ClientWorld w = mc.world;
		if (w == null || mc.player == null) {
			list.clear();
			return;
		}
		tick++;
		float g = w.getRainGradient(1F);
		if (g < 0.05F) {
			if (++dry > 100) {
				list.clear(); // the puddles dry on their own (they fade out), we just forget them
			}
			return;
		}
		dry = 0;
		float q = quality();
		if (q <= 0F) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double px = mc.player.getX(), pz = mc.player.getZ();
		// ---- puddles form
		if (puddles.on() && amount.f() > 0 && tick % 5 == 0) {
			int max = (int) ((18 + amount.f() * 1.7F) * q);
			list.removeIf(e -> tick - e[4] > e[5] || Math.hypot(e[0] - px, e[2] - pz) > 50);
			int attempts = 1 + (int) (g * 3);
			for (int i = 0; i < attempts && list.size() < max; i++) {
				double a = rnd.nextDouble() * Math.PI * 2, r = 4 + 20 * Math.sqrt(rnd.nextDouble());
				int bx = (int) Math.floor(px + Math.cos(a) * r), bz = (int) Math.floor(pz + Math.sin(a) * r);
				double top = ground(w, bx, bz);
				if (Double.isNaN(top)) {
					continue;
				}
				double s = size.f() / 100.0;
				add(w, bx + 0.5 + (rnd.nextDouble() - 0.5) * 0.3, top, bz + 0.5 + (rnd.nextDouble() - 0.5) * 0.3, (0.42 + rnd.nextDouble() * 0.2) * s);
				// a puddle is bigger than a block: neighbours at the same height join in
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if ((dx != 0 || dz != 0) && rnd.nextFloat() < 0.45F) {
							double t2 = ground(w, bx + dx, bz + dz);
							if (!Double.isNaN(t2) && Math.abs(t2 - top) < 0.001) {
								add(w, bx + dx + 0.5 + (rnd.nextDouble() - 0.5) * 0.25, t2, bz + dz + 0.5 + (rnd.nextDouble() - 0.5) * 0.25, (0.4 + rnd.nextDouble() * 0.2) * s);
							}
						}
					}
				}
			}
		}
		// ---- drops landing: rings in puddles and on open water
		if (ripples.on()) {
			float n = rate.f() * 0.7F * g * q;
			int count = (int) n + (rnd.nextFloat() < n - (int) n ? 1 : 0);
			for (int i = 0; i < count && !list.isEmpty(); i++) {
				for (int tries = 0; tries < 6; tries++) {
					double[] e = list.get(rnd.nextInt(list.size()));
					if (Math.hypot(e[0] - px, e[2] - pz) > 18) {
						continue;
					}
					double ox = (rnd.nextDouble() * 2 - 1) * e[3] * 0.7, oz = (rnd.nextDouble() * 2 - 1) * e[3] * 0.7;
					w.addParticleClient(LiquidFx.RIPPLE, e[0] + ox, e[1] + 0.006, e[2] + oz, 0.1 + rnd.nextDouble() * 0.18, 0, 14 + rnd.nextInt(8));
					break;
				}
			}
		}
		if (water.on()) {
			float n = rate.f() * 0.9F * g * q;
			int count = (int) n + (rnd.nextFloat() < n - (int) n ? 1 : 0);
			for (int i = 0; i < count; i++) {
				double a = rnd.nextDouble() * Math.PI * 2, r = 20 * Math.sqrt(rnd.nextDouble());
				int bx = (int) Math.floor(px + Math.cos(a) * r), bz = (int) Math.floor(pz + Math.sin(a) * r);
				BlockPos wp = new BlockPos(bx, w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1, bz);
				FluidState fs = w.getFluidState(wp);
				if (fs.isIn(FluidTags.WATER) && w.getBlockState(wp.up()).isAir()) {
					w.addParticleClient(LiquidFx.RIPPLE, bx + rnd.nextDouble(), wp.getY() + fs.getHeight(w, wp) + 0.015, bz + rnd.nextDouble(), 0.12 + rnd.nextDouble() * 0.2,
							0, 14 + rnd.nextInt(8));
				}
			}
		}
	}

	private void add(ClientWorld w, double x, double y, double z, double half) {
		int life = 1500 + ThreadLocalRandom.current().nextInt(2200);
		list.add(new double[] {x, y, z, Math.min(0.62, half), tick, life});
		w.addParticleClient(LiquidFx.PUDDLE, x, y, z, Math.min(0.62, half), 0, life);
	}
}
