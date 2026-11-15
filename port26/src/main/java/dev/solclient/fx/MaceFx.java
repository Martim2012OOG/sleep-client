package dev.solclient.fx;

import dev.solclient.Gfx;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
		ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
		r.register(SHOCKWAVE, sp -> new Ring.Factory(sp, false));
		r.register(WINDWAVE, sp -> new Ring.Factory(sp, true));
		r.register(SPARK, Spark.Factory::new);
		r.register(FLASH, FireworkParticles.SparkProvider::new);
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Gfx.id(name), FabricParticleTypes.simple());
	}

	/** Spawns the whole effect at the point where the mace hit the ground. */
	public static void slam(double x, double y, double z, double power) {
		ClientLevel w = Minecraft.getInstance().level;
		if (!enabled || w == null || SHOCKWAVE == null) {
			return;
		}
		double p = Mth.clamp(power, 1, 10);
		java.util.Random rnd = new java.util.Random();
		double ox = rnd.nextDouble(-0.6, 0.6), oy = rnd.nextDouble(-1.2, 1.2), oz = rnd.nextDouble(-0.6, 0.6);
		w.addParticle(SHOCKWAVE, x, y, z, 0.8, 0.8, 1.5 * size * p);
		w.addParticle(SHOCKWAVE, x, y, z, 0.4, 1.0, 0.35 * size * p);
		w.addParticle(WINDWAVE, x, y, z, 0.8, 0.8, 1.75 * size * p);
		if (sparks) {
			w.addParticle(SPARK, x + ox, y + oy + 0.9, z + oz, 0.5, 0, 0);
			w.addParticle(SPARK, x + ox, y + oy + 0.9, z + oz, 0.2, 0, 0);
		}
		if (flash) {
			w.addParticle(FLASH, x, y, z, 0, 0, 0);
		}
	}

	/** A flat ring that grows and fades (velocity slots carry: growth speed, opacity, size). */
	static final class Ring extends SingleQuadParticle {
		// facing up (and the mirrored copy so it shows from below); the wind ring is tilted
		private static final Quaternionf FLAT = new Quaternionf(0, 0.7, 0.7F, 0), FLAT_BACK = new Quaternionf(0, -0.7, 0.7F, 0);
		private static final Quaternionf TILT = new Quaternionf(0, 0.7, 0.7F, 0), TILT_BACK = new Quaternionf(-0.2705981F, 0.6532815F, 0.6532815F, 0.2705981F);
		private final SpriteSet sprites;
		private final boolean wind;
		private float growth, step, fade;

		Ring(ClientLevel w, double x, double y, double z, SpriteSet sprites, double a, double b, double c, boolean wind) {
			super(w, x, y, z, a, b, c, sprites.first());
			this.sprites = sprites;
			this.wind = wind;
			growth = (float) a;
			fade = (float) b;
			step = (float) c * 0.1F;
			alpha = Mth.clamp(fade * opacity, 0F, 1F);
			quadSize = 0.5F;
			lifetime = 40;
			gravity = 0F;
			setSprite(sprites.first());
		}

		@Override
		public void tick() {
			super.tick();
			quadSize += growth * step;
			growth -= 1F / lifetime;
			fade -= 1F / lifetime;
			growth = Mth.clamp(growth, 0F, 1F);
			alpha = Mth.clamp(fade * opacity, 0F, 1F);
			setSprite(sprites.get(Math.min(age, lifetime / 2), lifetime / 2));
		}

		@Override
		protected void extractRotatedQuad(QuadParticleRenderState out, Quaternionf q, float x, float y, float z, float tickProgress) {
			int light = getLightCoords(tickProgress);
			int color = net.minecraft.util.ARGB.colorFromFloat(alpha, rCol, gCol, bCol);
			Quaternionf a = wind ? TILT : FLAT, b = wind ? TILT_BACK : FLAT_BACK;
			out.add(getLayer(), x, y, z, a.x, a.y, a.z, a.w, getQuadSize(tickProgress), getU0(), getU1(), getV0(), getV1(), color, light);
			out.add(getLayer(), x, y, z, b.x, b.y, b.z, b.w, getQuadSize(tickProgress), getU0(), getU1(), getV0(), getV1(), color, light);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		static final class Factory implements ParticleProvider<SimpleParticleType> {
			private final SpriteSet sprites;
			private final boolean wind;

			Factory(SpriteSet sprites, boolean wind) {
				this.sprites = sprites;
				this.wind = wind;
			}

			@Override
			public Particle createParticle(SimpleParticleType t, ClientLevel w, double x, double y, double z, double vx, double vy, double vz,
					RandomSource r) {
				return new Ring(w, x, y, z, sprites, vx, vy, vz, wind);
			}
		}
	}

	/** A little spark that shrinks and fades in one second (the first velocity slot is its size). */
	static final class Spark extends SingleQuadParticle {
		private float time;
		private final float size;

		Spark(ClientLevel w, double x, double y, double z, SpriteSet sprites, double s) {
			super(w, x, y, z, sprites.first());
			xd = yd = zd = 0;
			size = (float) s;
			alpha = 1F;
			lifetime = 20;
			gravity = 0F;
		}

		@Override
		public void tick() {
			super.tick();
			alpha = Mth.clamp((1F - time) * 1.5F, 0.2F, 1F) - 0.2F;
			time += 1F / lifetime;
			quadSize = alpha * size;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		static final class Factory implements ParticleProvider<SimpleParticleType> {
			private final SpriteSet sprites;

			Factory(SpriteSet sprites) {
				this.sprites = sprites;
			}

			@Override
			public Particle createParticle(SimpleParticleType t, ClientLevel w, double x, double y, double z, double vx, double vy, double vz,
					RandomSource r) {
				return new Spark(w, x, y, z, sprites, vx);
			}
		}
	}
}
