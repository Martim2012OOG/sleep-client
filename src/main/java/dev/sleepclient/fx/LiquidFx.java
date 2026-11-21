package dev.sleepclient.fx;

import dev.sleepclient.Gfx;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

/**
 * Particles for Realistic Liquids and Custom Rain: puddles lying on the ground, ripple rings, sun glints on water and foam that
 * drifts with the current. Flat ones are drawn lying on the surface (like the mace shockwave), the glints face the camera.
 * The velocity slots carry the settings of each particle (see the kind).
 */
public final class LiquidFx {

	public static SimpleParticleType PUDDLE, RIPPLE, GLINT, FOAM, WAVE;

	private LiquidFx() {
	}

	enum Kind {
		/** vx = half width in blocks, vz = lifetime in ticks. Fades in, stays, fades out; dries up when the rain stops. */
		PUDDLE(true),
		/** vx = final half width, vz = lifetime. A ring that spreads and fades. */
		RIPPLE(true),
		/** vx, vz = the current's speed (blocks / tick). A bit of foam carried along the surface. */
		FOAM(true),
		/** vx = size. A little star of sunlight that flashes and goes. */
		GLINT(false),
		/** vx = size in blocks, vz = wind speed (blocks / tick, along z). A soft crest of light that drifts over open water and fades. */
		WAVE(true);

		final boolean flat;

		Kind(boolean flat) {
			this.flat = flat;
		}
	}

	public static void init() {
		PUDDLE = register("liquid_puddle");
		RIPPLE = register("liquid_ripple");
		GLINT = register("liquid_glint");
		FOAM = register("liquid_foam");
		WAVE = register("liquid_wave");
		ParticleFactoryRegistry r = ParticleFactoryRegistry.getInstance();
		r.register(PUDDLE, sp -> new Factory(sp, Kind.PUDDLE));
		r.register(RIPPLE, sp -> new Factory(sp, Kind.RIPPLE));
		r.register(GLINT, sp -> new Factory(sp, Kind.GLINT));
		r.register(FOAM, sp -> new Factory(sp, Kind.FOAM));
		r.register(WAVE, sp -> new Factory(sp, Kind.WAVE));
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, Gfx.id(name), FabricParticleTypes.simple());
	}

	private static final Quaternionf FLAT = new Quaternionf(0, 0.7F, 0.7F, 0);

	static final class P extends BillboardParticle {
		private final Kind kind;
		private final float size;
		private final int life;

		P(ClientWorld w, double x, double y, double z, SpriteProvider sprites, double a, double b, double c, Kind kind) {
			super(w, x, y, z, sprites.getFirst());
			this.kind = kind;
			velocityX = velocityY = velocityZ = 0;
			gravityStrength = 0F;
			collidesWithWorld = false;
			velocityMultiplier = 1F;
			size = (float) a;
			switch (kind) {
				case PUDDLE -> {
					scale = size;
					life = Math.max(100, (int) c);
					alpha = 0F;
				}
				case RIPPLE -> {
					scale = 0.02F;
					life = c > 0 ? (int) c : 18;
					alpha = 0F;
				}
				case FOAM -> {
					scale = 0.07F + (float) Math.abs(a) * 0.3F;
					scale = Math.min(scale, 0.14F);
					velocityX = a;
					velocityZ = c;
					velocityMultiplier = 0.985F;
					life = 38 + w.random.nextInt(24);
					alpha = 0F;
				}
				case WAVE -> {
					scale = size * 0.7F;
					velocityZ = c;
					life = 60 + w.random.nextInt(50);
					alpha = 0F;
				}
				default -> {
					scale = size;
					life = 10 + w.random.nextInt(6);
					alpha = 0F;
				}
			}
			maxAge = life;
		}

		@Override
		public void tick() {
			super.tick();
			float t = Math.min(1F, age / (float) Math.max(1, maxAge));
			switch (kind) {
				case PUDDLE -> {
					if (age % 10 == 0 && maxAge - age > 70 && world.getRainGradient(1F) < 0.05F) {
						maxAge = age + 60; // the rain stopped: it dries up
					}
					float in = Math.min(1F, age / 40F), out = Math.min(1F, (maxAge - age) / 60F);
					alpha = 0.62F * in * Math.max(0F, out);
				}
				case RIPPLE -> {
					scale = Math.max(0.02F, size * (1F - (1F - t) * (1F - t)));
					alpha = 0.85F * (float) Math.pow(1F - t, 1.4);
				}
				case FOAM -> alpha = 0.75F * (float) Math.sin(Math.PI * t);
				case WAVE -> {
					scale = size * (0.7F + 0.5F * t); // swells as it travels
					alpha = 0.5F * (float) Math.pow(Math.sin(Math.PI * t), 1.3);
				}
				default -> alpha = 0.95F * (float) Math.sin(Math.PI * t);
			}
		}

		@Override
		protected void renderVertex(BillboardParticleSubmittable out, Quaternionf q, float x, float y, float z, float tickProgress) {
			if (!kind.flat) {
				super.renderVertex(out, q, x, y, z, tickProgress);
				return;
			}
			int color = ColorHelper.fromFloats(MathHelper.clamp(alpha, 0F, 1F), red, green, blue);
			out.render(getRenderType(), x, y, z, FLAT.x, FLAT.y, FLAT.z, FLAT.w, getSize(tickProgress), getMinU(), getMaxU(), getMinV(), getMaxV(), color,
					getBrightness(tickProgress));
		}

		@Override
		protected RenderType getRenderType() {
			return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
		}
	}

	static final class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider sprites;
		private final Kind kind;

		Factory(SpriteProvider sprites, Kind kind) {
			this.sprites = sprites;
			this.kind = kind;
		}

		@Override
		public Particle createParticle(SimpleParticleType t, ClientWorld w, double x, double y, double z, double vx, double vy, double vz, Random r) {
			return new P(w, x, y, z, sprites, vx, vy, vz, kind);
		}
	}
}
