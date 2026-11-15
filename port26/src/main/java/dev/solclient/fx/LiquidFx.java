package dev.solclient.fx;

import dev.solclient.Gfx;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
		ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
		r.register(PUDDLE, sp -> new Factory(sp, Kind.PUDDLE));
		r.register(RIPPLE, sp -> new Factory(sp, Kind.RIPPLE));
		r.register(GLINT, sp -> new Factory(sp, Kind.GLINT));
		r.register(FOAM, sp -> new Factory(sp, Kind.FOAM));
		r.register(WAVE, sp -> new Factory(sp, Kind.WAVE));
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Gfx.id(name), FabricParticleTypes.simple());
	}

	private static final Quaternionf FLAT = new Quaternionf(0, 0.7F, 0.7F, 0);

	static final class P extends SingleQuadParticle {
		private final Kind kind;
		private final float size;
		private final int life;

		P(ClientLevel w, double x, double y, double z, SpriteSet sprites, double a, double b, double c, Kind kind) {
			super(w, x, y, z, sprites.first());
			this.kind = kind;
			xd = yd = zd = 0;
			gravity = 0F;
			hasPhysics = false;
			friction = 1F;
			size = (float) a;
			switch (kind) {
				case PUDDLE -> {
					quadSize = size;
					life = Math.max(100, (int) c);
					alpha = 0F;
				}
				case RIPPLE -> {
					quadSize = 0.02F;
					life = c > 0 ? (int) c : 18;
					alpha = 0F;
				}
				case FOAM -> {
					quadSize = 0.07F + (float) Math.abs(a) * 0.3F;
					quadSize = Math.min(quadSize, 0.14F);
					xd = a;
					zd = c;
					friction = 0.985F;
					life = 38 + w.getRandom().nextInt(24);
					alpha = 0F;
				}
				case WAVE -> {
					quadSize = size * 0.7F;
					zd = c;
					life = 60 + w.getRandom().nextInt(50);
					alpha = 0F;
				}
				default -> {
					quadSize = size;
					life = 10 + w.getRandom().nextInt(6);
					alpha = 0F;
				}
			}
			lifetime = life;
		}

		@Override
		public void tick() {
			super.tick();
			float t = Math.min(1F, age / (float) Math.max(1, lifetime));
			switch (kind) {
				case PUDDLE -> {
					if (age % 10 == 0 && lifetime - age > 70 && level.getRainLevel(1F) < 0.05F) {
						lifetime = age + 60; // the rain stopped: it dries up
					}
					float in = Math.min(1F, age / 40F), out = Math.min(1F, (lifetime - age) / 60F);
					alpha = 0.62F * in * Math.max(0F, out);
				}
				case RIPPLE -> {
					quadSize = Math.max(0.02F, size * (1F - (1F - t) * (1F - t)));
					alpha = 0.85F * (float) Math.pow(1F - t, 1.4);
				}
				case FOAM -> alpha = 0.75F * (float) Math.sin(Math.PI * t);
				case WAVE -> {
					quadSize = size * (0.7F + 0.5F * t); // swells as it travels
					alpha = 0.5F * (float) Math.pow(Math.sin(Math.PI * t), 1.3);
				}
				default -> alpha = 0.95F * (float) Math.sin(Math.PI * t);
			}
		}

		@Override
		protected void extractRotatedQuad(QuadParticleRenderState out, Quaternionf q, float x, float y, float z, float tickProgress) {
			if (!kind.flat) {
				super.extractRotatedQuad(out, q, x, y, z, tickProgress);
				return;
			}
			int color = ARGB.colorFromFloat(Mth.clamp(alpha, 0F, 1F), rCol, gCol, bCol);
			out.add(getLayer(), x, y, z, FLAT.x, FLAT.y, FLAT.z, FLAT.w, getQuadSize(tickProgress), getU0(), getU1(), getV0(), getV1(), color,
					getLightCoords(tickProgress));
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}
	}

	static final class Factory implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		private final Kind kind;

		Factory(SpriteSet sprites, Kind kind) {
			this.sprites = sprites;
			this.kind = kind;
		}

		@Override
		public Particle createParticle(SimpleParticleType t, ClientLevel w, double x, double y, double z, double vx, double vy, double vz, RandomSource r) {
			return new P(w, x, y, z, sprites, vx, vy, vz, kind);
		}
	}
}
