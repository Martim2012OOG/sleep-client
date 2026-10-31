package dev.solclient.mods;

import dev.solclient.fx.LiquidFx;
import dev.solclient.option.Option;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.Heightmap;

/**
 * Realistic Liquids: clearer, more natural water and living liquids, in four steps from light to heavy. Water gets a clear
 * blue-green colour, the sun glints on its surface, rivers carry foam along the current, lava throws embers and a heat haze, and
 * underwater is full of floating motes. All of it is particles and colour (no shaders), so it runs on any computer; "Protect My FPS"
 * lowers the amount by itself when the game gets slow.
 */
public class RealisticLiquidsMod extends Mod {

	public static RealisticLiquidsMod instance;

	private final Option.Choice level = tabs("Realism", 1, "Low", "Realistic", "Very Realistic", "Real Life");
	private final Option.Bool tint = bool("Clear Water Colour", true);
	private final Option.Bool glints = bool("Sun Glints On Water", true);
	private final Option.Bool foam = bool("Foam On Currents", true);
	private final Option.Bool lava = bool("Lava Embers And Heat", true);
	private final Option.Bool motes = bool("Underwater Motes", true);
	private final Option.Bool auto = bool("Protect My FPS", true).tip("Shows less when the game drops below 40 fps, and nothing below 25.");

	// per level: radius (blocks), glints / foam / lava / motes per tick, how much of the clear colour is mixed in
	private static final int[] RADIUS = {12, 18, 26, 34};
	private static final float[] GLINT = {1F, 3F, 6F, 11F}, FOAM = {1.5F, 5F, 10F, 20F}, LAVA = {1F, 2.5F, 5F, 9F}, MOTE = {1F, 2F, 4F, 7F};
	private static final float[] MIX = {0.20F, 0.35F, 0.50F, 0.62F};
	/** The colour water drifts towards: a clear blue with a touch of green. */
	private static final int CLEAR = 0x2F93CC;

	private static boolean hooked;
	private int applied = -1;

	public RealisticLiquidsMod() {
		super("Realistic Liquids", "realistic_liquids", "Clearer water, sun glints, foam on rivers, lava embers and underwater life. Four levels from light to heavy.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			SimpleFluidRenderHandler h = new SimpleFluidRenderHandler(SimpleFluidRenderHandler.WATER_STILL, SimpleFluidRenderHandler.WATER_FLOWING,
					SimpleFluidRenderHandler.WATER_OVERLAY) {
				@Override
				public int getFluidColor(BlockRenderView view, BlockPos pos, FluidState state) {
					return 0xFF000000 | tint(view.getColor(pos, BiomeColors.WATER_COLOR) & 0xFFFFFF);
				}
			};
			FluidRenderHandlerRegistry.INSTANCE.register(Fluids.WATER, Fluids.FLOWING_WATER, h);
		} catch (Throwable t) {
			System.err.println("[Sleep] Realistic Liquids: water colour unavailable: " + t);
		}
	}

	/** The water colour the renderer should use for this biome colour. */
	public static int tint(int rgb) {
		RealisticLiquidsMod m = instance;
		if (m == null || !m.enabled || !m.tint.on()) {
			return rgb;
		}
		float k = MIX[m.level.idx()];
		int r = Math.round((rgb >> 16 & 255) * (1 - k) + (CLEAR >> 16 & 255) * k);
		int g = Math.round((rgb >> 8 & 255) * (1 - k) + (CLEAR >> 8 & 255) * k);
		int b = Math.round((rgb & 255) * (1 - k) + (CLEAR & 255) * k);
		return r << 16 | g << 8 | b;
	}

	@Override
	protected void onDisable() {
		applied = -1;
		if (mc.worldRenderer != null && mc.world != null) {
			mc.worldRenderer.reload(); // the water goes back to the biome colour
		}
	}

	private float quality() {
		if (!auto.on()) {
			return 1F;
		}
		int fps = mc.getCurrentFps();
		return fps >= 60 ? 1F : fps >= 40 ? 0.6F : fps >= 25 ? 0.25F : 0F;
	}

	/** Whole number of tries this tick for a fractional rate. */
	private static int tries(float rate) {
		int n = (int) rate;
		return n + (ThreadLocalRandom.current().nextFloat() < rate - n ? 1 : 0);
	}

	@Override
	public void tick() {
		ClientWorld w = mc.world;
		if (w == null || mc.player == null) {
			return;
		}
		int key = level.idx() * 2 + (tint.on() ? 1 : 0);
		if (applied != key) {
			if (applied != -1 && mc.worldRenderer != null) {
				mc.worldRenderer.reload(); // chunks are drawn again with the new water colour
			}
			applied = key;
		}
		float q = quality();
		if (q <= 0F) {
			return;
		}
		int lv = level.idx();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		int radius = RADIUS[lv];
		boolean sunny = w.isDay() && w.getRainGradient(1F) < 0.3F;

		if (glints.on() && sunny) {
			for (int i = tries(GLINT[lv] * q); i > 0; i--) {
				double a = rnd.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(rnd.nextDouble());
				double x = px + Math.cos(a) * r, z = pz + Math.sin(a) * r;
				BlockPos wp = new BlockPos((int) Math.floor(x), w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) - 1, (int) Math.floor(z));
				FluidState fs = w.getFluidState(wp);
				if (fs.isIn(FluidTags.WATER) && Math.abs(wp.getY() - py) < 24) {
					w.addParticleClient(LiquidFx.GLINT, x, wp.getY() + fs.getHeight(w, wp) + 0.03, z, 0.05 + rnd.nextDouble() * 0.07, 0, 0);
				}
			}
		}
		if (foam.on()) {
			for (int i = tries(FOAM[lv] * q); i > 0; i--) {
				BlockPos bp = BlockPos.ofFloored(px + (rnd.nextDouble() * 2 - 1) * radius, py + rnd.nextInt(-6, 7), pz + (rnd.nextDouble() * 2 - 1) * radius);
				FluidState fs = w.getFluidState(bp);
				if (!fs.isIn(FluidTags.WATER) || fs.isStill() || !w.getFluidState(bp.up()).isEmpty()) {
					continue;
				}
				Vec3d v = fs.getVelocity(w, bp);
				if (v.x * v.x + v.z * v.z < 0.0009) {
					continue;
				}
				w.addParticleClient(LiquidFx.FOAM, bp.getX() + rnd.nextDouble(), bp.getY() + fs.getHeight(w, bp) + 0.02, bp.getZ() + rnd.nextDouble(),
						v.x * 0.06, 0, v.z * 0.06);
			}
		}
		if (lava.on()) {
			int lr = Math.min(radius, 22);
			for (int i = tries(LAVA[lv] * q); i > 0; i--) {
				BlockPos bp = BlockPos.ofFloored(px + (rnd.nextDouble() * 2 - 1) * lr, py + rnd.nextInt(-8, 9), pz + (rnd.nextDouble() * 2 - 1) * lr);
				FluidState fs = w.getFluidState(bp);
				if (!fs.isIn(FluidTags.LAVA) || !w.getFluidState(bp.up()).isEmpty() || !w.getBlockState(bp.up()).isAir()) {
					continue;
				}
				double x = bp.getX() + rnd.nextDouble(), y = bp.getY() + fs.getHeight(w, bp), z = bp.getZ() + rnd.nextDouble();
				int roll = rnd.nextInt(10);
				if (roll < 3) {
					w.addParticleClient(ParticleTypes.LAVA, x, y + 0.05, z, 0, 0, 0); // a bubble pops and throws a spark
				} else if (roll < 7 && lv >= 1) {
					w.addParticleClient(ParticleTypes.SMALL_FLAME, x, y + 0.1, z, (rnd.nextDouble() - 0.5) * 0.01, 0.03 + rnd.nextDouble() * 0.03, (rnd.nextDouble() - 0.5) * 0.01);
				} else if (lv >= 2) {
					w.addParticleClient(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.04, 0); // heat haze
				}
			}
		}
		if (motes.on() && mc.player.isSubmergedInWater()) {
			for (int i = tries(MOTE[lv] * q); i > 0; i--) {
				double x = px + (rnd.nextDouble() * 2 - 1) * 8, y = mc.player.getEyeY() + (rnd.nextDouble() * 2 - 1) * 5, z = pz + (rnd.nextDouble() * 2 - 1) * 8;
				if (w.getFluidState(BlockPos.ofFloored(x, y, z)).isIn(FluidTags.WATER)) {
					w.addParticleClient(ParticleTypes.UNDERWATER, x, y, z, 0, 0, 0);
					if (lv >= 3 && rnd.nextInt(6) == 0) {
						w.addParticleClient(ParticleTypes.BUBBLE, x, y, z, 0, 0.03, 0);
					}
				}
			}
		}
	}
}
