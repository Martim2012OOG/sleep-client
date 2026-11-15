package dev.solclient.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.fx.Fx;
import dev.solclient.fx.Rainbow;
import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Tool Effects: every tool gets its own effect. Swing a sword, an axe, a spear, a mace, a trident, a pickaxe... and a
 * glowing slash flies through the air; when you hit something the effect you picked bursts out of it. Each tool has
 * its own list (sparks, flames, frost, lightning, souls, hearts, void, gold, poison, rainbow, petals, smoke, magic,
 * explosion, wind or just the slash). Other players' swings get the slash too.
 */
public class ToolEffectsMod extends Mod {

	public static ToolEffectsMod instance;

	public static final String[] EFFECTS = { "Off", "Sparks", "Flames", "Frost", "Lightning", "Souls", "Hearts", "Void", "Gold", "Poison", "Rainbow",
			"Petals", "Smoke", "Magic", "Explosion", "Wind", "Slash Only" };
	/** The colour of the slash for each effect (Off has none). */
	private static final int[] EFFECT_COLOUR = { 0xFFFFFFFF, 0xFFFFE066, 0xFFFF7A1A, 0xFF8FE3FF, 0xFFB9A6FF, 0xFF3FD9E6, 0xFFFF6B9A, 0xFFA03CFF, 0xFFFFC21A,
			0xFF7CE04A, 0xFFFFFFFF, 0xFFFFB3D1, 0xFF9A9AA6, 0xFFB86BFF, 0xFFFF9A3C, 0xFFE8F4FF, 0xFFFFFFFF };

	// tool kinds
	private static final int SWORD = 0, AXE = 1, SPEAR = 2, MACE = 3, TRIDENT = 4, PICKAXE = 5, SHOVEL = 6, HOE = 7, HAND = 8, OTHER = 9;

	private final Option.Choice who = choice("Show For", 0, "Everyone", "Only Me", "Only Others");
	private final Option.Choice when = choice("When", 0, "Swing And Hit", "Only When I Hit", "Only Swing");
	private final Option.Num power = num("Strength", 100, 20, 300, 10, "%");
	private final Option.Bool slash = bool("Glowing Slash", true).tip("A bright arc that flies through the air when you swing (follows the tool).");
	private final Option.Num slashSize = num("Slash Size", 100, 40, 220, 10, "%");
	private final Option.Choice slashColour = choice("Slash Colour", 0, "Effect Colour", "Rainbow", "Custom");
	private final Option.Col custom = colour("Custom Slash Colour", 0xFFFF3B6B);
	private final Option.Bool sound = bool("Hit Sound", false).tip("A soft magic ding on a hit (off by default).");

	private final Option.Header hSword = header("Sword");
	private final Option.Choice sword = choice("Sword Effect", 1, EFFECTS);
	private final Option.Header hAxe = header("Axe");
	private final Option.Choice axe = choice("Axe Effect", 2, EFFECTS);
	private final Option.Header hSpear = header("Spear");
	private final Option.Choice spear = choice("Spear Effect", 4, EFFECTS);
	private final Option.Header hMace = header("Mace");
	private final Option.Choice mace = choice("Mace Effect", 14, EFFECTS);
	private final Option.Header hTrident = header("Trident");
	private final Option.Choice trident = choice("Trident Effect", 3, EFFECTS);
	private final Option.Header hPick = header("Pickaxe & Shovel & Hoe");
	private final Option.Choice pickaxe = choice("Pickaxe Effect", 8, EFFECTS);
	private final Option.Choice shovel = choice("Shovel Effect", 12, EFFECTS);
	private final Option.Choice hoe = choice("Hoe Effect", 11, EFFECTS);
	private final Option.Header hOther = header("Hand & Other Items");
	private final Option.Choice hand = choice("Empty Hand Effect", 0, EFFECTS);
	private final Option.Choice other = choice("Other Items Effect", 0, EFFECTS);

	private static boolean hooked;
	private final List<Trail> trails = new ArrayList<>();
	/** player -> was swinging last tick (to see when a swing starts) */
	private final Map<UUID, Float> lastSwing = new HashMap<>();

	private static final class Trail {
		final List<double[]> pts = new ArrayList<>();
		final long start = System.currentTimeMillis();
		int colour;
		boolean rainbow;
		float width;
		long life;
	}

	public ToolEffectsMod() {
		super("Tool Effects", "tool_effects", "Every tool gets its own effect: a glowing slash when you swing and sparks, flames, frost and more when you hit.", Category.VISUAL);
		instance = this;
		custom.showIf(() -> slashColour.idx() == 2);
		slashSize.showIf(slash::on);
		slashColour.showIf(slash::on);
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
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				if (instance != null && instance.enabled && !instance.trails.isEmpty()) {
					long t0 = System.nanoTime();
					try {
						instance.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
					instance.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Tool Effects slash unavailable: " + t);
		}
	}

	@Override
	protected void onDisable() {
		trails.clear();
		lastSwing.clear();
	}

	// ---------------------------------------------------------------- which tool, which effect
	private static int kindOf(ItemStack s) {
		if (s == null || s.isEmpty()) {
			return HAND;
		}
		String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (p.endsWith("_sword")) {
			return SWORD;
		}
		if (p.endsWith("_axe")) {
			return AXE;
		}
		if (p.endsWith("_spear")) {
			return SPEAR;
		}
		if (p.equals("mace")) {
			return MACE;
		}
		if (p.equals("trident")) {
			return TRIDENT;
		}
		if (p.endsWith("_pickaxe")) {
			return PICKAXE;
		}
		if (p.endsWith("_shovel")) {
			return SHOVEL;
		}
		if (p.endsWith("_hoe")) {
			return HOE;
		}
		return OTHER;
	}

	private int effectFor(int kind) {
		return switch (kind) {
			case SWORD -> sword.idx();
			case AXE -> axe.idx();
			case SPEAR -> spear.idx();
			case MACE -> mace.idx();
			case TRIDENT -> trident.idx();
			case PICKAXE -> pickaxe.idx();
			case SHOVEL -> shovel.idx();
			case HOE -> hoe.idx();
			case HAND -> hand.idx();
			default -> other.idx();
		};
	}

	// ---------------------------------------------------------------- you hit something
	public void onAttack(Entity target) {
		if (!enabled || mc.player == null || target == null || !(target instanceof LivingEntity) || who.idx() == 2 || when.idx() == 2) {
			return;
		}
		int fx = effectFor(kindOf(mc.player.getMainHandItem()));
		if (fx == 0 || fx == 16) {
			return;
		}
		double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.55, z = target.getZ();
		burst(fx, x, y, z, power.f() / 100F);
		if (sound.on()) {
			try {
				mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
			} catch (Throwable ignored) {
			}
		}
	}

	/** The particles of one effect at a point; k = strength (1 = normal). */
	private void burst(int fx, double x, double y, double z, float k) {
		var r = ThreadLocalRandom.current();
		float q = mc.getFps() < 40 ? 0.5F : 1F;
		int n = Math.max(2, Math.round(14 * k * q));
		switch (fx) {
			case 1 -> { // sparks
				Fx.burst(ParticleTypes.CRIT, x, y, z, n * 2, 0.9);
				Fx.burst(ParticleTypes.ELECTRIC_SPARK, x, y, z, n, 0.7);
				Fx.burst(ParticleTypes.ENCHANTED_HIT, x, y, z, n / 2, 0.6);
			}
			case 2 -> { // flames
				Fx.burst(ParticleTypes.FLAME, x, y, z, n * 2, 0.35);
				Fx.burst(ParticleTypes.SMOKE, x, y, z, n / 2, 0.15);
				Fx.burst(ParticleTypes.LAVA, x, y, z, Math.max(1, n / 5), 0.3);
			}
			case 3 -> { // frost
				Fx.burst(ParticleTypes.SNOWFLAKE, x, y, z, n * 2, 0.45);
				Fx.burst(ParticleTypes.CLOUD, x, y, z, n / 2, 0.2);
				Fx.burst(ParticleTypes.END_ROD, x, y, z, n / 2, 0.3);
			}
			case 4 -> { // lightning: a bolt of sparks going up and down
				for (int i = 0; i < 12; i++) {
					double yy = y - 1.2 + i * 0.4;
					Fx.spawn(ParticleTypes.ELECTRIC_SPARK, x + (r.nextDouble() - 0.5) * 0.3, yy, z + (r.nextDouble() - 0.5) * 0.3, 0, 0, 0);
					Fx.spawn(ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 0.2, yy, z + (r.nextDouble() - 0.5) * 0.2, 0, 0.02, 0);
				}
				Fx.burst(ParticleTypes.ELECTRIC_SPARK, x, y, z, n * 2, 1.1);
			}
			case 5 -> { // souls
				Fx.burst(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, n * 2, 0.3);
				Fx.burst(ParticleTypes.SOUL, x, y, z, n / 2, 0.15);
			}
			case 6 -> Fx.burst(ParticleTypes.HEART, x, y + 0.2, z, Math.max(3, n / 2), 0.25);
			case 7 -> { // void
				Fx.burst(ParticleTypes.REVERSE_PORTAL, x, y, z, n * 2, 0.5);
				Fx.burst(ParticleTypes.PORTAL, x, y, z, n, 0.8);
			}
			case 8 -> { // gold
				Fx.burst(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, n, 0.6);
				Fx.burst(ParticleTypes.ENCHANTED_HIT, x, y, z, n, 0.5);
				Fx.burst(ParticleTypes.FIREWORK, x, y, z, n / 2, 0.3);
			}
			case 9 -> { // poison
				Fx.burst(ParticleTypes.ITEM_SLIME, x, y, z, n, 0.4);
				Fx.burst(ParticleTypes.WITCH, x, y, z, n, 0.4);
				Fx.burst(ParticleTypes.HAPPY_VILLAGER, x, y, z, n / 2, 0.3);
			}
			case 10 -> { // rainbow
				Fx.burst(ParticleTypes.NOTE, x, y, z, n / 2 + 2, 0.4);
				Fx.burst(ParticleTypes.FIREWORK, x, y, z, n, 0.5);
				Fx.burst(ParticleTypes.END_ROD, x, y, z, n, 0.5);
			}
			case 11 -> Fx.burst(ParticleTypes.CHERRY_LEAVES, x, y + 0.3, z, n * 2, 0.5);
			case 12 -> {
				Fx.burst(ParticleTypes.LARGE_SMOKE, x, y, z, n, 0.2);
				Fx.burst(ParticleTypes.SMOKE, x, y, z, n * 2, 0.4);
			}
			case 13 -> {
				Fx.burst(ParticleTypes.ENCHANT, x, y + 0.5, z, n * 3, 1.2);
				Fx.burst(ParticleTypes.WITCH, x, y, z, n, 0.4);
			}
			case 14 -> {
				Fx.spawn(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
				Fx.burst(ParticleTypes.POOF, x, y, z, n, 0.35);
				Fx.burst(ParticleTypes.FLAME, x, y, z, n, 0.5);
			}
			case 15 -> {
				Fx.ring(ParticleTypes.SMALL_GUST, x, y - 0.3, z, 0.4, 10, 0.25);
				Fx.burst(ParticleTypes.CLOUD, x, y, z, n, 0.35);
				Fx.burst(ParticleTypes.GUST, x, y, z, 1, 0.01);
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- somebody swings (every tick we look for the start of a swing)
	@Override
	public void tick() {
		var w = mc.level;
		if (w == null || mc.player == null) {
			trails.clear();
			lastSwing.clear();
			return;
		}
		if (when.idx() == 1 && !slash.on()) {
			return;
		}
		long t0 = System.nanoTime();
		for (AbstractClientPlayer p : w.players()) {
			boolean mine = p == mc.player;
			if (who.idx() == 1 && !mine || who.idx() == 2 && mine) {
				continue;
			}
			if (!mine && p.distanceToSqr(mc.player) > 48 * 48) {
				continue;
			}
			float prog = p.getAttackAnim(0F);
			Float prev = lastSwing.put(p.getUUID(), prog);
			boolean started = prev != null && prev <= 0.001F && prog > 0.001F;
			if (!started || when.idx() == 1 && !mine) {
				continue;
			}
			int kind = kindOf(p.getMainHandItem());
			int fx = effectFor(kind);
			if (fx == 0) {
				continue;
			}
			swing(p, kind, fx);
		}
		if (lastSwing.size() > 200) {
			lastSwing.clear();
		}
		addCost(System.nanoTime() - t0);
	}

	/** Builds the slash of this swing (and a few particles along it). */
	private void swing(AbstractClientPlayer p, int kind, int fx) {
		if (!slash.on() && fx == 16) {
			return;
		}
		float k = power.f() / 100F;
		float size = slashSize.f() / 100F;
		Vec3 eye = p.getEyePosition();
		float yaw = (float) Math.toRadians(p.getYRot());
		float pitch = (float) Math.toRadians(p.getXRot());
		// forward / right of the player (flat)
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Trail t = new Trail();
		int col = slashColour.idx() == 2 ? custom.c() | 0xFF000000 : EFFECT_COLOUR[fx];
		t.colour = col;
		t.rainbow = slashColour.idx() == 1 || (slashColour.idx() == 0 && fx == 10);
		float radius = (kind == SPEAR ? 2.6F : kind == MACE || kind == AXE ? 2.0F : 1.9F) * size;
		int n = 14;
		t.life = kind == SPEAR ? 220 : 300;
		t.width = (kind == MACE ? 0.30F : kind == SPEAR ? 0.11F : 0.2F) * size;
		for (int i = 0; i <= n; i++) {
			double a = (double) i / n;
			double x, y, z;
			if (kind == SPEAR || kind == TRIDENT) {
				// a thrust: a straight streak forward from the hand
				double d = 0.5 + a * radius;
				x = eye.x + look.x * d + right.x * 0.25;
				y = eye.y + look.y * d - 0.3;
				z = eye.z + look.z * d + right.z * 0.25;
			} else if (kind == AXE || kind == MACE) {
				// an overhead chop: from above-behind down to the front
				double ang = Math.toRadians(-70 + a * 125);
				x = eye.x + (fwd.x * Math.cos(ang) * radius) + right.x * 0.2;
				y = eye.y + Math.sin(-ang) * radius * 0.9 - 0.1;
				z = eye.z + (fwd.z * Math.cos(ang) * radius) + right.z * 0.2;
			} else {
				// a side slash: from the right to the left in front of you, a little downwards
				double ang = Math.toRadians(70 - a * 140);
				double side = Math.sin(ang) * radius, front = Math.cos(ang) * radius;
				x = eye.x + fwd.x * front + right.x * side;
				y = eye.y - 0.45 - a * 0.25 + Math.sin(a * Math.PI) * 0.15;
				z = eye.z + fwd.z * front + right.z * side;
			}
			t.pts.add(new double[] { x, y, z });
			if (fx != 16 && i % 3 == 0 && fx != 0) {
				trailParticle(fx, x, y, z, k);
			}
		}
		if (slash.on()) {
			if (trails.size() >= 24) {
				trails.remove(0);
			}
			trails.add(t);
		}
	}

	/** A couple of particles of the picked effect along the slash. */
	private void trailParticle(int fx, double x, double y, double z, float k) {
		if (ThreadLocalRandom.current().nextFloat() > Math.min(1F, 0.55F * k)) {
			return;
		}
		ParticleOptions p = switch (fx) {
			case 1 -> ParticleTypes.ELECTRIC_SPARK;
			case 2 -> ParticleTypes.FLAME;
			case 3 -> ParticleTypes.SNOWFLAKE;
			case 4 -> ParticleTypes.ELECTRIC_SPARK;
			case 5 -> ParticleTypes.SOUL_FIRE_FLAME;
			case 6 -> ParticleTypes.HEART;
			case 7 -> ParticleTypes.REVERSE_PORTAL;
			case 8 -> ParticleTypes.ENCHANTED_HIT;
			case 9 -> ParticleTypes.ITEM_SLIME;
			case 10 -> ParticleTypes.END_ROD;
			case 11 -> ParticleTypes.CHERRY_LEAVES;
			case 12 -> ParticleTypes.SMOKE;
			case 13 -> ParticleTypes.ENCHANT;
			case 14 -> ParticleTypes.FLAME;
			case 15 -> ParticleTypes.CLOUD;
			default -> null;
		};
		if (p != null) {
			Fx.spawn(p, x, y, z, 0, 0.01, 0);
		}
	}

	// ---------------------------------------------------------------- drawing the slashes
	private void render(PoseStack m, SubmitNodeCollector q, Vec3 cam) {
		long now = System.currentTimeMillis();
		float time = Rainbow.seconds();
		WorldDraw.Lines lines = new WorldDraw.Lines(cam);
		Iterator<Trail> it = trails.iterator();
		while (it.hasNext()) {
			Trail t = it.next();
			float age = (now - t.start) / (float) t.life;
			if (age >= 1F) {
				it.remove();
				continue;
			}
			float fade = (float) Math.pow(1F - age, 1.5);
			// the slash is drawn from its start to the point it has reached so far (it "swings"), then fades out
			float reach = Math.min(1F, age * 4F + 0.2F);
			int last = Math.max(1, Math.round((t.pts.size() - 1) * reach));
			for (int i = 1; i <= last; i++) {
				double[] a = t.pts.get(i - 1), b = t.pts.get(i);
				float pa = (float) (i - 1) / (t.pts.size() - 1), pb = (float) i / (t.pts.size() - 1);
				// brighter at the leading end, thin tail
				float wa = t.width * (0.25F + 0.75F * pa), wb = t.width * (0.25F + 0.75F * pb);
				int ca = shade(t, pa, time, fade * (0.2F + 0.8F * pa));
				int cb = shade(t, pb, time, fade * (0.2F + 0.8F * pb));
				lines.line(a[0], a[1], a[2], b[0], b[1], b[2], (wa + wb) * 0.5F, ca, cb);
				// a thin white core on top
				lines.line(a[0], a[1], a[2], b[0], b[1], b[2], (wa + wb) * 0.2F, Rainbow.alpha(0xFFFFFFFF, fade * 0.9F * pb), Rainbow.alpha(0xFFFFFFFF, fade * 0.9F * pb));
			}
		}
		lines.draw(m, q);
	}

	private static int shade(Trail t, float pos, float time, float alpha) {
		int c = t.rainbow ? Rainbow.hue(pos * 0.6F + time * 0.5F) : t.colour;
		return Rainbow.alpha(c, Math.max(0F, Math.min(1F, alpha)));
	}
}
