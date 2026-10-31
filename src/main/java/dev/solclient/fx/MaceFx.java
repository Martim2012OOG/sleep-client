package dev.solclient.fx;

import dev.solclient.Gfx;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.FireworksSparkParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

/**
 * The mace smash effect: a flat shockwave ring on the ground, a tilted wind ring, sparks and a flash, the same look and numbers
 * as the MasEffects mod by MasUN0 (MIT licence, see assets/sol_client/cosmetics/CREDITS.md). Triggered by the smash attack world event.
 */
public final class MaceFx {

	/** World event sent by the server when a mace smash lands (WorldEvents.SMASH_ATTACK). */
	public static final int SMASH_EVENT = 2013;

	public static boolean enabled = true;
	public static float size = 1F, opacity = 1F;
	public static boolean sparks = true, flash = true;

	public static SimpleParticleType SHOCKWAVE, WINDWAVE, SPARK, FLASH;

	private MaceFx() {
	}

	public static void init() {
		SHOCKWAVE = register("mace_shockwave");
		WINDWAVE = register("mace_wind");
		SPARK = register("mace_spark");
		FLASH = register("mace_flash");
		ParticleFactoryRegistry r = ParticleFactoryRegistry.getInstance();
		r.register(SHOCKWAVE, sp -> new Ring.Factory(sp, false));
		r.register(WINDWAVE, sp -> new Ring.Factory(sp, true));
		r.register(SPARK, Spark.Factory::new);
		r.register(FLASH, FireworksSparkParticle.ExplosionFactory::new);
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, Gfx.id(name), FabricParticleTypes.simple());
	}

	/** Spawns the whole effect at the point where the mace hit the ground. */
	public static void slam(double x, double y, double z, double power) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (!enabled || w == null || SHOCKWAVE == null) {
			return;
		}
		double p = MathHelper.clamp(power, 1, 10);
		java.util.Random rnd = new java.util.Random();
		double ox = rnd.nextDouble(-0.6, 0.6), oy = rnd.nextDouble(-1.2, 1.2), oz = rnd.nextDouble(-0.6, 0.6);
		w.addParticleClient(SHOCKWAVE, x, y, z, 0.8, 0.8, 1.5 * size * p);
		w.addParticleClient(SHOCKWAVE, x, y, z, 0.4, 1.0, 0.35 * size * p);
		w.addParticleClient(WINDWAVE, x, y, z, 0.8, 0.8, 1.75 * size * p);
		if (sparks) {
			w.addParticleClient(SPARK, x + ox, y + oy + 0.9, z + oz, 0.5, 0, 0);
			w.addParticleClient(SPARK, x + ox, y + oy + 0.9, z + oz, 0.2, 0, 0);
		}
		if (flash) {
			w.addParticleClient(FLASH, x, y, z, 0, 0, 0);
		}
	}

	/** A flat ring that grows and fades (velocity slots carry: growth speed, opacity, size). */
	static final class Ring extends BillboardParticle {
		// facing up (and the mirrored copy so it shows from below); the wind ring is tilted
		private static final Quaternionf FLAT = new Quaternionf(0, 0.7, 0.7F, 0), FLAT_BACK = new Quaternionf(0, -0.7, 0.7F, 0);
		private static final Quaternionf TILT = new Quaternionf(0, 0.7, 0.7F, 0), TILT_BACK = new Quaternionf(-0.2705981F, 0.6532815F, 0.6532815F, 0.2705981F);
		private final SpriteProvider sprites;
		private final boolean wind;
		private float growth, step, fade;

		Ring(ClientWorld w, double x, double y, double z, SpriteProvider sprites, double a, double b, double c, boolean wind) {
			super(w, x, y, z, a, b, c, sprites.getFirst());
			this.sprites = sprites;
			this.wind = wind;
			growth = (float) a;
			fade = (float) b;
			step = (float) c * 0.1F;
			alpha = MathHelper.clamp(fade * opacity, 0F, 1F);
			scale = 0.5F;
			maxAge = 40;
			gravityStrength = 0F;
			setSprite(sprites.getFirst());
		}

		@Override
		public void tick() {
			super.tick();
			scale += growth * step;
			growth -= 1F / maxAge;
			fade -= 1F / maxAge;
			growth = MathHelper.clamp(growth, 0F, 1F);
			alpha = MathHelper.clamp(fade * opacity, 0F, 1F);
			setSprite(sprites.getSprite(Math.min(age, maxAge / 2), maxAge / 2));
		}

		@Override
		protected void renderVertex(BillboardParticleSubmittable out, Quaternionf q, float x, float y, float z, float tickProgress) {
			int light = getBrightness(tickProgress);
			int color = net.minecraft.util.math.ColorHelper.fromFloats(alpha, red, green, blue);
			Quaternionf a = wind ? TILT : FLAT, b = wind ? TILT_BACK : FLAT_BACK;
			out.render(getRenderType(), x, y, z, a.x, a.y, a.z, a.w, getSize(tickProgress), getMinU(), getMaxU(), getMinV(), getMaxV(), color, light);
			out.render(getRenderType(), x, y, z, b.x, b.y, b.z, b.w, getSize(tickProgress), getMinU(), getMaxU(), getMinV(), getMaxV(), color, light);
		}

		@Override
		protected RenderType getRenderType() {
			return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
		}

		static final class Factory implements ParticleFactory<SimpleParticleType> {
			private final SpriteProvider sprites;
			private final boolean wind;

			Factory(SpriteProvider sprites, boolean wind) {
				this.sprites = sprites;
				this.wind = wind;
			}

			@Override
			public Particle createParticle(SimpleParticleType t, ClientWorld w, double x, double y, double z, double vx, double vy, double vz,
					Random r) {
				return new Ring(w, x, y, z, sprites, vx, vy, vz, wind);
			}
		}
	}

	/** A little spark that shrinks and fades in one second (the first velocity slot is its size). */
	static final class Spark extends BillboardParticle {
		private float time;
		private final float size;

		Spark(ClientWorld w, double x, double y, double z, SpriteProvider sprites, double s) {
			super(w, x, y, z, sprites.getFirst());
			velocityX = velocityY = velocityZ = 0;
			size = (float) s;
			alpha = 1F;
			maxAge = 20;
			gravityStrength = 0F;
		}

		@Override
		public void tick() {
			super.tick();
			alpha = MathHelper.clamp((1F - time) * 1.5F, 0.2F, 1F) - 0.2F;
			time += 1F / maxAge;
			scale = alpha * size;
		}

		@Override
		protected RenderType getRenderType() {
			return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
		}

		static final class Factory implements ParticleFactory<SimpleParticleType> {
			private final SpriteProvider sprites;

			Factory(SpriteProvider sprites) {
				this.sprites = sprites;
			}

			@Override
			public Particle createParticle(SimpleParticleType t, ClientWorld w, double x, double y, double z, double vx, double vy, double vz,
					Random r) {
				return new Spark(w, x, y, z, sprites, vx);
			}
		}
	}
}
