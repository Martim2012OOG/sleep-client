package dev.solclient.fx;

import dev.solclient.Gfx;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

/**
 * The art-directed Mace smash styles (Slash Arcs, Nova Flash, Blade Storm, Shatter, Void Rift). Instead of lines and vanilla
 * particles they use hand-made textures (tools/gen_mace_fx.py): crescent slashes that are drawn and then dissolve, 4-point
 * star flares with a white-hot core, energy rings, glowing ground cracks, light streaks, glass shards and dust. Everything is
 * emissive (full brightness) and is tinted with the colour set chosen in Mace Effects.
 */
public final class MaceArt {

	private MaceArt() {
	}

	public enum Mode {
		/** Always faces the camera. */
		FACE,
		/** Lies on the ground. */
		FLAT,
		/** A vertical plane (turned by yaw, leaned, rolled). */
		VERT
	}

	/** Everything one sprite needs; built with the small fluent methods below. */
	public static final class Spec {
		Mode mode = Mode.FACE;
		float yaw, roll, lean, spin;
		float s0 = 1F, s1 = 1F;
		int life = 10;
		float r = 1F, g = 1F, b = 1F;
		float peak = 1F, fadeIn = 0.1F, fadeOut = 0.5F;
		double vx, vy, vz;
		float gravity, drag = 0.98F;
		boolean ease = true, collide;

		public Spec mode(Mode m) {
			mode = m;
			return this;
		}

		public Spec angles(float yaw, float roll, float lean) {
			this.yaw = yaw;
			this.roll = roll;
			this.lean = lean;
			return this;
		}

		public Spec spin(float s) {
			spin = s;
			return this;
		}

		public Spec size(float from, float to) {
			s0 = from;
			s1 = to;
			return this;
		}

		public Spec life(int l) {
			life = Math.max(2, l);
			return this;
		}

		public Spec colour(int argb) {
			r = (argb >> 16 & 255) / 255F;
			g = (argb >> 8 & 255) / 255F;
			b = (argb & 255) / 255F;
			return this;
		}

		public Spec fade(float in, float out, float peak) {
			fadeIn = in;
			fadeOut = out;
			this.peak = peak;
			return this;
		}

		public Spec move(double vx, double vy, double vz, float gravity, float drag) {
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.gravity = gravity;
			this.drag = drag;
			return this;
		}

		public Spec linear() {
			ease = false;
			return this;
		}

		public Spec collide() {
			collide = true;
			return this;
		}
	}

	public static SimpleParticleType SLASH, SLASHCORE, STAR, STARCORE, RING, CRACK, STREAK, SHARD, GLOW, DUST;
	private static volatile Spec next;
	private static final List<Task> TASKS = new ArrayList<>();

	private static final class Task {
		int left;
		final Runnable run;

		Task(int left, Runnable run) {
			this.left = left;
			this.run = run;
		}
	}

	/** Main colour and the white-hot colour of the effect that is about to play. */
	private static int main = 0xFFFFB84A, hot = 0xFFFFFFFF;

	public static void palette(int mainArgb, int hotArgb) {
		main = mainArgb;
		hot = hotArgb;
	}

	private static SimpleParticleType reg(String name, boolean animatedSprites) {
		SimpleParticleType t = Registry.register(Registries.PARTICLE_TYPE, Gfx.id(name), FabricParticleTypes.simple());
		ParticleFactoryRegistry.getInstance().register(t, sp -> new Factory(sp));
		return t;
	}

	public static void init() {
		SLASH = reg("mfx_slash", true);
		SLASHCORE = reg("mfx_slashcore", true);
		STAR = reg("mfx_star", true);
		STARCORE = reg("mfx_starcore", true);
		RING = reg("mfx_ring", true);
		CRACK = reg("mfx_crack", true);
		STREAK = reg("mfx_streak", false);
		SHARD = reg("mfx_shard", false);
		GLOW = reg("mfx_glow", false);
		DUST = reg("mfx_dust", true);
		ClientTickEvents.END_CLIENT_TICK.register(c -> tick(c));
	}

	private static void tick(MinecraftClient c) {
		if (c.world == null) {
			TASKS.clear();
			return;
		}
		for (int i = TASKS.size() - 1; i >= 0; i--) {
			Task t = TASKS.get(i);
			if (--t.left <= 0) {
				TASKS.remove(i);
				try {
					t.run.run();
				} catch (RuntimeException ignored) {
					// an effect must never break the game
				}
			}
		}
	}

	private static void later(int ticks, Runnable r) {
		if (ticks <= 0) {
			r.run();
		} else {
			TASKS.add(new Task(ticks, r));
		}
	}

	private static void put(SimpleParticleType type, double x, double y, double z, Spec s) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w == null || type == null) {
			return;
		}
		next = s;
		try {
			w.addParticleClient(type, x, y, z, 0, 0, 0);
		} finally {
			next = null;
		}
	}

	private static void putLater(int delay, SimpleParticleType type, double x, double y, double z, Spec s) {
		later(delay, () -> put(type, x, y, z, s));
	}

	// ------------------------------------------------------------------ the sprite
	private static final class Art extends BillboardParticle {
		private final SpriteProvider sprites;
		private final Spec s;

		Art(ClientWorld w, double x, double y, double z, SpriteProvider sprites, Spec s) {
			super(w, x, y, z, 0, 0, 0, sprites.getFirst());
			this.sprites = sprites;
			this.s = s;
			velocityX = s.vx;
			velocityY = s.vy;
			velocityZ = s.vz;
			velocityMultiplier = s.drag;
			gravityStrength = s.gravity;
			collidesWithWorld = s.collide;
			maxAge = s.life;
			scale = s.s0;
			red = s.r;
			green = s.g;
			blue = s.b;
			alpha = 0F;
			setSprite(sprites.getFirst());
		}

		@Override
		public void tick() {
			super.tick();
			float p = Math.min(1F, (float) age / maxAge);
			float in = s.fadeIn <= 0F ? 1F : Math.min(1F, p / s.fadeIn);
			float out = s.fadeOut <= 0F || p < 1F - s.fadeOut ? 1F : Math.max(0F, (1F - p) / s.fadeOut);
			alpha = Math.max(0F, Math.min(1F, s.peak * in * out));
			setSprite(sprites.getSprite(Math.min(age, maxAge - 1), maxAge));
		}

		@Override
		public float getSize(float tickProgress) {
			float p = Math.min(1F, (age + tickProgress) / maxAge);
			float e = s.ease ? 1F - (1F - p) * (1F - p) * (1F - p) : p;
			return s.s0 + (s.s1 - s.s0) * e;
		}

		@Override
		public int getBrightness(float tint) {
			return 0xF000F0; // glows, also in caves and at night
		}

		@Override
		protected RenderType getRenderType() {
			return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
		}

		@Override
		protected void renderVertex(BillboardParticleSubmittable out, Quaternionf q, float x, float y, float z, float tickProgress) {
			float size = getSize(tickProgress);
			int color = net.minecraft.util.math.ColorHelper.fromFloats(alpha, red, green, blue);
			int light = 0xF000F0;
			float t = age + tickProgress;
			if (s.mode == Mode.FACE) {
				Quaternionf r = new Quaternionf(q).rotateZ(s.roll + s.spin * t);
				out.render(getRenderType(), x, y, z, r.x, r.y, r.z, r.w, size, getMinU(), getMaxU(), getMinV(), getMaxV(), color, light);
				return;
			}
			Quaternionf a = new Quaternionf().rotateY(s.yaw);
			if (s.mode == Mode.FLAT) {
				a.rotateX(-(float) Math.PI / 2F);
			} else {
				a.rotateX(s.lean);
			}
			a.rotateZ(s.roll + s.spin * t);
			Quaternionf b = new Quaternionf(a).rotateY((float) Math.PI);
			out.render(getRenderType(), x, y, z, a.x, a.y, a.z, a.w, size, getMinU(), getMaxU(), getMinV(), getMaxV(), color, light);
			out.render(getRenderType(), x, y, z, b.x, b.y, b.z, b.w, size, getMinU(), getMaxU(), getMinV(), getMaxV(), color, light);
		}
	}

	private static final class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider sprites;

		Factory(SpriteProvider sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType t, ClientWorld w, double x, double y, double z, double vx, double vy, double vz, Random r) {
			Spec s = next;
			return s == null ? null : new Art(w, x, y, z, sprites, s);
		}
	}

	// ------------------------------------------------------------------ building blocks
	private static float rnd() {
		return ThreadLocalRandom.current().nextFloat();
	}

	private static float rnd(float a, float b) {
		return a + (b - a) * rnd();
	}

	/** A star flare with its white-hot core. */
	private static void flare(double x, double y, double z, float from, float to, int life, float spin) {
		put(STAR, x, y, z, new Spec().size(from, to).life(life).colour(main).spin(spin).fade(0.05F, 0.6F, 1F));
		put(STARCORE, x, y, z, new Spec().size(from * 0.8F, to * 0.8F).life(life).colour(hot).spin(spin).fade(0.05F, 0.6F, 1F));
		put(GLOW, x, y, z, new Spec().size(from * 1.2F, to * 1.5F).life(life).colour(main).fade(0.1F, 0.7F, 0.8F));
	}

	/** A crescent slash plus its thin white core (same plane). */
	private static void slash(int delay, double x, double y, double z, float yaw, float roll, float lean, float size, int life) {
		Spec a = new Spec().mode(Mode.VERT).angles(yaw, roll, lean).size(size * 0.92F, size).life(life).colour(main).fade(0.05F, 0.45F, 0.95F);
		Spec b = new Spec().mode(Mode.VERT).angles(yaw, roll, lean).size(size * 0.92F, size).life(life).colour(hot).fade(0.05F, 0.45F, 1F);
		putLater(delay, SLASH, x, y, z, a);
		putLater(delay, SLASHCORE, x, y, z, b);
	}

	private static void ring(int delay, double x, double y, double z, float radius, int life, float peak) {
		float spin = rnd(-0.08F, 0.08F);
		putLater(delay, RING, x, y + 0.06, z, new Spec().mode(Mode.FLAT).angles(rnd() * 6.28F, 0F, 0F).spin(spin).size(0.4F, radius / 0.93F).life(life).colour(main).fade(0.02F, 0.6F, peak));
	}

	private static void dust(double x, double y, double z, float radius, int count, float quality) {
		int n = Math.max(3, Math.round(count * quality));
		for (int i = 0; i < n; i++) {
			float a = (float) (Math.PI * 2 * i / n) + rnd(-0.2F, 0.2F);
			float v = rnd(0.12F, 0.22F);
			put(DUST, x + Math.cos(a) * radius * 0.25, y + 0.2, z + Math.sin(a) * radius * 0.25,
					new Spec().size(0.5F, rnd(1.4F, 2.1F)).life(rnd() < 0.5F ? 22 : 28).colour(0xFFBDB6AE).fade(0.15F, 0.7F, 0.55F)
							.move(Math.cos(a) * v, 0.015, Math.sin(a) * v, 0F, 0.9F).spin(rnd(-0.05F, 0.05F)));
		}
	}

	/** Light streaks flying out (or in) along a direction at the given elevation. */
	private static void streak(int delay, double x, double y, double z, float phi, float elev, float speed, float length, int life) {
		float yaw = -phi;
		float roll = -((float) Math.PI / 2F - elev);
		double dx = Math.cos(phi) * Math.cos(elev) * speed, dy = Math.sin(elev) * speed, dz = Math.sin(phi) * Math.cos(elev) * speed;
		Spec s = new Spec().mode(Mode.VERT).angles(yaw, roll, 0F).size(length, length * 0.8F).life(life).colour(hot).fade(0.05F, 0.7F, 0.95F).move(dx, dy, dz, 0F, 0.93F).linear();
		Spec g = new Spec().mode(Mode.VERT).angles(yaw, roll, 0F).size(length * 1.1F, length * 0.9F).life(life).colour(main).fade(0.05F, 0.7F, 0.6F).move(dx, dy, dz, 0F, 0.93F).linear();
		putLater(delay, STREAK, x, y, z, s);
		putLater(delay, STREAK, x, y, z, g);
	}

	private static void shards(double x, double y, double z, int count, float speed, float size) {
		for (int i = 0; i < count; i++) {
			float a = rnd() * 6.28F;
			float up = rnd(0.25F, 0.6F) * speed;
			float out = rnd(0.08F, 0.3F) * speed;
			put(SHARD, x, y, z, new Spec().size(size * rnd(0.6F, 1.1F), size * rnd(0.3F, 0.6F)).life(rnd() < 0.5F ? 18 : 24).colour(rnd() < 0.5F ? hot : main)
					.fade(0F, 0.4F, 1F).move(Math.cos(a) * out, up, Math.sin(a) * out, 0.9F, 0.97F).spin(rnd(-0.5F, 0.5F)).linear().collide());
		}
	}

	// ------------------------------------------------------------------ the styles
	/** 16 Slash Arcs, 17 Nova Flash, 18 Blade Storm, 19 Shatter, 20 Void Rift. {@code size} is the Radius setting divided by 8. */
	public static void play(int style, double x, double y, double z, float pw, float size, float quality) {
		float S = Math.max(0.35F, size * pw);
		switch (style) {
			case 16 -> slashArcs(x, y, z, S, quality);
			case 17 -> nova(x, y, z, S, quality);
			case 18 -> bladeStorm(x, y, z, S, quality);
			case 19 -> shatter(x, y, z, S, quality);
			case 20 -> voidRift(x, y, z, S, quality);
			default -> {
			}
		}
	}

	private static void slashArcs(double x, double y, double z, float S, float quality) {
		float base = rnd() * 6.28F;
		flare(x, y + 1.0, z, 0.5F * S, 2.4F * S, 8, 0.1F);
		for (int i = 0; i < 5; i++) {
			float yaw = base + i * 1.2566F + rnd(-0.2F, 0.2F);
			float roll = (i % 2 == 0 ? 1F : -1F) * rnd(0.45F, 0.95F);
			float lean = rnd(-0.35F, 0.35F);
			double cx = x + Math.cos(yaw) * 0.35, cz = z + Math.sin(yaw) * 0.35;
			slash(i, cx, y + 0.95 + 0.12 * i, cz, yaw, roll, lean, (2.1F + 0.25F * i) * S, 9 + i % 2);
		}
		ring(0, x, y, z, 5.5F * S, 14, 0.95F);
		ring(2, x, y, z, 3.4F * S, 12, 0.6F);
		dust(x, y, z, 2.5F * S, 8, quality);
		int n = Math.round(8 * quality);
		for (int i = 0; i < n; i++) {
			streak(i / 3, x, y + 0.1, z, (float) (Math.PI * 2 * i / n) + rnd(-0.2F, 0.2F), rnd(0.05F, 0.5F), rnd(0.35F, 0.6F) * S, 1.1F * S, 9);
		}
	}

	private static void nova(double x, double y, double z, float S, float quality) {
		flare(x, y + 1.0, z, 0.8F * S, 4.2F * S, 11, 0.05F);
		put(GLOW, x, y + 0.6, z, new Spec().size(1F * S, 6F * S).life(13).colour(main).fade(0.05F, 0.7F, 0.55F));
		ring(0, x, y, z, 6.5F * S, 16, 1F);
		ring(3, x, y, z, 4F * S, 13, 0.7F);
		int n = Math.round(16 * quality);
		for (int i = 0; i < n; i++) {
			float phi = (float) (Math.PI * 2 * i / n) + rnd(-0.1F, 0.1F);
			streak(0, x, y + 0.9, z, phi, rnd(0.0F, 0.9F), rnd(0.45F, 0.8F) * S, rnd(1.0F, 1.7F) * S, 10);
		}
		dust(x, y, z, 3F * S, 10, quality);
	}

	private static void bladeStorm(double x, double y, double z, float S, float quality) {
		flare(x, y + 0.9, z, 0.5F * S, 2.2F * S, 9, 0.1F);
		ring(0, x, y, z, 4.8F * S, 14, 0.8F);
		int n = Math.round(12 * quality);
		float base = rnd() * 6.28F;
		for (int i = 0; i < n; i++) {
			float yaw = base + i * 2.399F; // golden angle: every blade a different direction
			float roll = (i % 2 == 0 ? 1F : -1F) * rnd(0.3F, 1.1F);
			float rad = rnd(0.2F, 1.6F) * S;
			double cx = x + Math.cos(yaw) * rad, cz = z + Math.sin(yaw) * rad;
			slash(i, cx, y + rnd(0.3F, 2.2F), cz, yaw + 1.57F, roll, rnd(-0.5F, 0.5F), rnd(1.5F, 2.8F) * S, 9);
		}
		shards(x, y + 0.3, z, Math.round(10 * quality), 1F, 0.5F * S);
		dust(x, y, z, 2.2F * S, 6, quality);
	}

	private static void shatter(double x, double y, double z, float S, float quality) {
		put(CRACK, x, y + 0.05, z, new Spec().mode(Mode.FLAT).angles(rnd() * 6.28F, 0F, 0F).size(2.2F * S, 6.5F * S).life(34).colour(main).fade(0.03F, 0.45F, 1F));
		put(GLOW, x, y + 0.1, z, new Spec().mode(Mode.FLAT).size(0.8F * S, 3.5F * S).life(16).colour(main).fade(0.05F, 0.7F, 0.8F));
		flare(x, y + 0.7, z, 0.6F * S, 2.6F * S, 8, 0.1F);
		ring(0, x, y, z, 5F * S, 14, 0.9F);
		ring(2, x, y, z, 3F * S, 12, 0.6F);
		shards(x, y + 0.2, z, Math.round(18 * quality), 1.2F, 0.6F * S);
		dust(x, y, z, 3.4F * S, 12, quality);
	}

	private static void voidRift(double x, double y, double z, float S, float quality) {
		// 1) everything is pulled in
		put(GLOW, x, y + 0.8, z, new Spec().size(0.4F * S, 3.2F * S).life(14).colour(0xFF14001F).fade(0.2F, 0.3F, 0.95F));
		int n = Math.round(18 * quality);
		for (int i = 0; i < n; i++) {
			float phi = (float) (Math.PI * 2 * i / n) + rnd(-0.1F, 0.1F);
			float elev = rnd(0.0F, 0.7F);
			double r = rnd(3.2F, 4.6F) * S;
			double px = x + Math.cos(phi) * r * Math.cos(elev), py = y + 0.8 + Math.sin(elev) * r, pz = z + Math.sin(phi) * r * Math.cos(elev);
			// a streak that points at the middle and flies to it
			float yaw = -phi, roll = -((float) Math.PI / 2F - (elev + (float) Math.PI));
			double sp = r / 7.0;
			Spec s = new Spec().mode(Mode.VERT).angles(yaw, roll, 0F).size(1.3F * S, 0.6F * S).life(7).colour(main).fade(0.1F, 0.4F, 0.9F)
					.move(-Math.cos(phi) * Math.cos(elev) * sp, -Math.sin(elev) * sp, -Math.sin(phi) * Math.cos(elev) * sp, 0F, 1F).linear();
			putLater(i % 4, STREAK, px, py, pz, s);
		}
		// 2) the rift opens
		later(7, () -> {
			flare(x, y + 0.9, z, 0.8F * S, 4.5F * S, 11, 0.06F);
			put(CRACK, x, y + 0.05, z, new Spec().mode(Mode.FLAT).angles(rnd() * 6.28F, 0F, 0F).size(2.5F * S, 6.5F * S).life(30).colour(main).fade(0.03F, 0.45F, 1F));
			ring(0, x, y, z, 6.2F * S, 16, 1F);
			ring(2, x, y, z, 3.6F * S, 13, 0.7F);
			shards(x, y + 0.4, z, Math.round(12 * quality), 1.1F, 0.5F * S);
			dust(x, y, z, 3F * S, 10, quality);
		});
	}
}
