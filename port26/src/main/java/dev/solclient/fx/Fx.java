package dev.solclient.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/** Small client-side particle helpers for the effect mods (Trails, Jump Circles, Aura...). */
public final class Fx {

	public static final String[] NAMES = {"Flame", "Soul Flame", "Hearts", "End Rod", "Enchant", "Emerald", "Witch", "Notes", "Totem", "Spark", "Snow", "Cloud"};

	private static long budgetTick = -1;
	private static int budget;

	private Fx() {
	}

	public static ParticleOptions type(int idx) {
		return switch (idx) {
			case 1 -> ParticleTypes.SOUL_FIRE_FLAME;
			case 2 -> ParticleTypes.HEART;
			case 3 -> ParticleTypes.END_ROD;
			case 4 -> ParticleTypes.ENCHANT;
			case 5 -> ParticleTypes.HAPPY_VILLAGER;
			case 6 -> ParticleTypes.WITCH;
			case 7 -> ParticleTypes.NOTE;
			case 8 -> ParticleTypes.TOTEM_OF_UNDYING;
			case 9 -> ParticleTypes.ELECTRIC_SPARK;
			case 10 -> ParticleTypes.SNOWFLAKE;
			case 11 -> ParticleTypes.CLOUD;
			default -> ParticleTypes.FLAME;
		};
	}

	public static void spawn(ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.particleEngine == null) {
			return;
		}
		// keep it smooth: a budget per tick, and fewer effects when FPS is low
		long tick = mc.level.getGameTime();
		if (tick != budgetTick) {
			budgetTick = tick;
			budget = mc.getFps() < 40 ? 60 : 250;
		}
		if (--budget < 0) {
			return;
		}
		try {
			mc.particleEngine.createParticle(p, x, y, z, vx, vy, vz);
		} catch (RuntimeException ignored) {
			// a particle type that needs data the client doesn't have: skip it
		}
	}

	/** A flat ring of particles, optionally pushed outwards. */
	public static void ring(ParticleOptions p, double x, double y, double z, double radius, int count, double outSpeed) {
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count;
			double cx = Math.cos(a), cz = Math.sin(a);
			spawn(p, x + cx * radius, y, z + cz * radius, cx * outSpeed, 0, cz * outSpeed);
		}
	}

	/** A burst in every direction. */
	public static void burst(ParticleOptions p, double x, double y, double z, int count, double speed) {
		java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			spawn(p, x, y, z, (r.nextDouble() - 0.5) * speed, r.nextDouble() * speed, (r.nextDouble() - 0.5) * speed);
		}
	}
}
