package dev.solclient.mods;

import dev.solclient.fx.CameraShake;
import dev.solclient.fx.Fx;
import dev.solclient.fx.Rainbow;
import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Mace Effects: when somebody (you or another player) smashes with a mace, the ground reacts. Pieces of the real floor
 * jump up in a wave that spreads out, glowing shockwave rings expand over the ground, a beam of light shoots up, the
 * screen shakes a little (more when it is close to you) and sparks fly. The server sends the smash sound at the place
 * of the hit, and that is what starts the effect, so it works for every player on any server.
 */
public class MaceEffectsMod extends Mod {

	public static MaceEffectsMod instance;

	private final Option.Choice style = choice("Effect", 0, "Classic", "Meteor", "Lightning", "Ground Cracks", "Tornado", "Eruption", "Gravity Well", "Soul Wave", "Crater", "Star Burst", "Halo Dome", "Helix", "Light Cage", "Aurora", "Random Each Smash", "MasEffects Look", "Slash Arcs", "Nova Flash", "Blade Storm", "Shatter", "Void Rift");
	private final Option.Choice who = choice("Show For", 0, "Everyone", "Only Me", "Only Others");
	private final Option.Num power = num("Power", 100, 30, 250, 10, "%");
	private final Option.Num radius = num("Radius", 8, 3, 16, 1, " blocks");
	private final Option.Choice look = choice("Colour", 5, "Rainbow", "Custom", "Fire", "Ice", "Void", "Impact (white-hot)");
	private final Option.Col custom = colour("Custom Colour", 0xFFFF3B6B);
	private final Option.Num speed = num("Colour Speed", 60, 10, 200, 10, "%");
	private final Option.Header hFx = header("Effects");
	private final Option.Bool ground = bool("Ground Ripple", true).tip("Pieces of the real floor jump up in a wave, so the ground looks like it moves.");
	private final Option.Bool rings = bool("Shockwave Rings", true);
	private final Option.Bool beam = bool("Light Beam", false);
	/** One-time move to the new look (Impact colours, no beam) for configs made before it. */
	private final Option.Bool newLook = bool("v2 look", false);
	/** One-time move to the MasEffects look (the default now). */
	private final Option.Bool masLook = bool("v3 mas look", false);
	/** One-time move to the art-directed look (Slash Arcs) for configs made before it. */
	private final Option.Bool artLook = bool("v4 art look", false);
	private final Option.Bool burst = bool("Block Burst", true);
	private final Option.Bool sparks = bool("Sparks", true);
	private final Option.Bool shake = bool("Screen Shake", true);
	private final Option.Num shakeAmount = num("Shake Strength", 100, 20, 250, 10, "%");
	private final Option.Bool air = bool("Air Smash Too", true).tip("The smaller smash when the mace hits something in the air.");

	private static boolean hooked;
	private final List<Quake> quakes = new ArrayList<>();

	private static final class Quake {
		final double x, y, z;
		final float pw;
		final boolean air;
		int style;
		long seed;
		final long start = System.currentTimeMillis();
		int age;

		Quake(double x, double y, double z, float pw, boolean air) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.pw = pw;
			this.air = air;
		}
	}

	public MaceEffectsMod() {
		super("Mace Effects", "mace_effects", "The ground shakes, rings and light shoot out when someone smashes with a mace.", Category.VISUAL);
		instance = this;
		custom.showIf(() -> look.idx() == 1);
		ground.showIf(() -> style.idx() == 0);
		rings.showIf(() -> style.idx() == 0);
		beam.showIf(() -> style.idx() == 0);
		burst.showIf(() -> style.idx() == 0);
		sparks.showIf(() -> style.idx() == 0);
		shakeAmount.showIf(shake::on);
		newLook.showIf(() -> false);
		masLook.showIf(() -> false);
		artLook.showIf(() -> false);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (!newLook.on()) {
			newLook.set(true);
			if (look.idx() == 0) {
				look.set(5); // the old rainbow default looked cheap: white-hot impact colours
			}
			beam.set(false);
		}
		if (!masLook.on()) {
			masLook.set(true);
			style.set(15); // the shockwave ring + wind + sparks + flash of the MasEffects mod
		}
		if (!artLook.on()) {
			artLook.set(true);
			style.set(16); // the new hand-drawn Slash Arcs (the old styles stay in the list)
		}
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				if (instance != null && instance.enabled && !instance.quakes.isEmpty()) {
					long t0 = System.nanoTime();
					try {
						instance.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
					instance.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Mace Effects unavailable: " + t);
		}
	}

	@Override
	protected void onDisable() {
		quakes.clear();
	}

	// ---------------------------------------------------------------- a smash happened (the server played its sound)
	public void onSound(String id, double x, double y, double z) {
		if (!enabled || mc.world == null || mc.player == null) {
			return;
		}
		if (id.startsWith("minecraft:")) {
			id = id.substring(10);
		}
		float pw;
		boolean isAir = false;
		switch (id) {
			case "item.mace.smash_ground_heavy" -> pw = 1.6F;
			case "item.mace.smash_ground" -> pw = 1F;
			case "item.mace.smash_air" -> {
				if (!air.on()) {
					return;
				}
				pw = 0.55F;
				isAir = true;
			}
			default -> {
				return;
			}
		}
		double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
		boolean mine = dx * dx + dz * dz < 16;
		if (who.idx() == 1 && !mine || who.idx() == 2 && mine) {
			return;
		}
		pw *= power.f() / 100F;
		int art = artStyle();
		if (art >= 16) {
			paletteFor(art);
			float quality = mc.getCurrentFps() < 40 ? 0.55F : 1F;
			dev.solclient.fx.MaceArt.play(art, x, y + 0.02, z, pw, radius.f() / 8F, quality);
			if (art == 19 && !isAir) {
				floorChunks(new Quake(x, y, z, pw, false), 2.8F, Math.round(10 * pw * quality), 1F);
			}
			if (shake.on()) {
				double d = mc.player.squaredDistanceTo(x, y, z);
				float near = (float) Math.max(0, 1 - Math.sqrt(d) / (radius.f() * 3F));
				if (near > 0F) {
					CameraShake.add(2.2F * pw * near * (shakeAmount.f() / 100F) * (mine ? 1.2F : 1F));
				}
			}
			return;
		}
		if (style.idx() == 15) {
			// MasEffects look: flat shockwave ring, tilted wind ring, sparks and a flash (assets and numbers from MasEffects, MIT)
			if (!isAir) {
				dev.solclient.fx.MaceFx.size = radius.f() / 8F * pw;
				dev.solclient.fx.MaceFx.sparks = sparks.on();
				dev.solclient.fx.MaceFx.slam(x, y + 0.05, z, 5.0);
			}
			if (shake.on()) {
				double d = mc.player.squaredDistanceTo(x, y, z);
				float near = (float) Math.max(0, 1 - Math.sqrt(d) / (radius.f() * 3F));
				if (near > 0F) {
					CameraShake.add(2.2F * pw * near * (shakeAmount.f() / 100F));
				}
			}
			return;
		}
		if (quakes.size() >= 8) {
			quakes.remove(0);
		}
		Quake q = new Quake(x, y, z, pw, isAir);
		int st0 = style.idx();
		if (st0 == 14) {
			st0 = 1 + ThreadLocalRandom.current().nextInt(13);
		}
		q.style = isAir && st0 != 0 ? 9 : st0; // the air smash is always the light star
		q.seed = ThreadLocalRandom.current().nextLong();
		quakes.add(q);
		impact(q, mine);
	}

	/** 16..20 when a hand-drawn style plays now ("Random" picks one of them 60% of the time), else 0. */
	private int artStyle() {
		int i = style.idx();
		if (i == 14 && ThreadLocalRandom.current().nextFloat() < 0.6F) {
			return 16 + ThreadLocalRandom.current().nextInt(5);
		}
		return i >= 16 ? i : 0;
	}

	/** The two colours of a hand-drawn style: the body and the white-hot core (each style has its own unless you picked a colour). */
	private void paletteFor(int st) {
		int l = look.idx();
		if (l == 0) {
			l = switch (st) {
				case 17 -> 3;
				case 19 -> 2;
				case 20 -> 4;
				default -> 5;
			};
			if (look.idx() == 0 && st == 18) {
				l = 0;
			}
		}
		int main, hot;
		switch (l) {
			case 1 -> {
				main = custom.c() | 0xFF000000;
				hot = Rainbow.mix(main, 0xFFFFFFFF, 0.85F);
			}
			case 2 -> {
				main = 0xFFFF6A1A;
				hot = 0xFFFFE2A0;
			}
			case 3 -> {
				main = 0xFF6FD8FF;
				hot = 0xFFFFFFFF;
			}
			case 4 -> {
				main = 0xFF8A3BFF;
				hot = 0xFFFFC8FF;
			}
			case 5 -> {
				main = 0xFFFFB347;
				hot = 0xFFFFFFFF;
			}
			default -> {
				main = Rainbow.hue(ThreadLocalRandom.current().nextFloat());
				hot = 0xFFFFFFFF;
			}
		}
		dev.solclient.fx.MaceArt.palette(main, hot);
	}

	private void impact(Quake q, boolean mine) {
		var rnd = ThreadLocalRandom.current();
		double[] gy = new double[1];
		BlockState st = floor(q.x, q.y, q.z, gy);
		double fy = st == null ? q.y : gy[0];
		float quality = mc.getCurrentFps() < 40 ? 0.5F : 1F;
		if (shake.on()) {
			double d = mc.player.squaredDistanceTo(q.x, q.y, q.z);
			float near = (float) Math.max(0, 1 - Math.sqrt(d) / (radius.f() * 3F));
			if (near > 0F) {
				CameraShake.add(2.2F * q.pw * near * (shakeAmount.f() / 100F) * (mine ? 1.2F : 1F));
			}
		}
		if (q.style != 0) {
			styleImpact(q, st, fy, quality);
			return;
		}
		if (burst.on() && st != null && !q.air) {
			ParticleEffect blocks = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
			Fx.burst(blocks, q.x, fy + 0.2, q.z, Math.round(36 * q.pw * quality), 0.9);
			// a column of dust and pieces going up
			for (double dy = 0; dy < 1.6 * q.pw; dy += 0.4) {
				Fx.spawn(blocks, q.x + (rnd.nextDouble() - 0.5) * 0.7, fy + dy, q.z + (rnd.nextDouble() - 0.5) * 0.7, 0, 0.28 * q.pw, 0);
			}
			Fx.ring(ParticleTypes.POOF, q.x, fy + 0.15, q.z, 0.9, Math.round(14 * quality), 0.22 * q.pw);
		}
		if (sparks.on()) {
			ParticleEffect p = switch (look.idx()) {
				case 2 -> ParticleTypes.FLAME;
				case 3 -> ParticleTypes.SNOWFLAKE;
				case 4 -> ParticleTypes.REVERSE_PORTAL;
				case 5 -> ParticleTypes.CRIT;
				default -> ParticleTypes.END_ROD;
			};
			// sparks are fast and few: they read as an impact, a big cloud of slow ones reads as noise
			Fx.burst(p, q.x, fy + 0.4, q.z, Math.round(16 * q.pw * quality), 1.1);
			Fx.burst(ParticleTypes.ELECTRIC_SPARK, q.x, fy + 0.4, q.z, Math.round(8 * q.pw * quality), 1.0);
			if (look.idx() == 5) {
				Fx.spawn(net.minecraft.particle.TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFE0A0), q.x, fy + 0.3, q.z, 0, 0, 0);
			}
			if (look.idx() == 2) {
				Fx.burst(ParticleTypes.LAVA, q.x, fy + 0.4, q.z, Math.round(6 * q.pw * quality), 0.5);
			}
		}
	}

	// ---------------------------------------------------------------- the wave in the floor (every tick)
	@Override
	public void tick() {
		var w = mc.world;
		if (w == null || mc.player == null) {
			quakes.clear();
			return;
		}
		if (quakes.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		var rnd = ThreadLocalRandom.current();
		float maxR = radius.f();
		double[] gy = new double[1];
		float quality = mc.getCurrentFps() < 40 ? 0.5F : 1F;
		for (int i = quakes.size() - 1; i >= 0; i--) {
			Quake q = quakes.get(i);
			q.age++;
			if (q.age > 40) {
				quakes.remove(i);
				continue;
			}
			if (q.style != 0) {
				styleTick(q, quality, rnd, gy);
				continue;
			}
			if (!ground.on() || q.air) {
				continue;
			}
			float reach = maxR * (0.6F + 0.4F * Math.min(1.6F, q.pw));
			double r = 0.9 + q.age * 1.5; // a fast wave feels heavier than a slow one
			if (r > reach) {
				continue;
			}
			int count = Math.max(8, Math.min(32, (int) Math.round(Math.PI * 2 * r * 0.7 * quality)));
			double off = rnd.nextDouble() * Math.PI * 2;
			for (int k = 0; k < count; k++) {
				double a = off + Math.PI * 2 * k / count;
				double c = Math.cos(a), s = Math.sin(a);
				double px = q.x + c * r, pz = q.z + s * r;
				BlockState st = floor(px, q.y, pz, gy);
				if (st == null) {
					continue;
				}
				// the wave is strong near the middle and calms down towards the edge
				double lift = (0.30 + rnd.nextDouble() * 0.12) * q.pw * (1.0 - 0.55 * (r / reach));
				Fx.spawn(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), px, gy[0] + 0.1, pz, c * 0.05, lift, s * 0.05);
			}
		}
		addCost(System.nanoTime() - t0);
	}

	/** The block you would stand on near (x, y, z): looks 1 up and 3 down for a solid block with air above it. */
	private BlockState floor(double x, double y, double z, double[] outY) {
		var w = mc.world;
		BlockPos.Mutable m = new BlockPos.Mutable();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
		for (int dy = 1; dy >= -3; dy--) {
			m.set(bx, by + dy, bz);
			BlockState s = w.getBlockState(m);
			if (s.isAir() || !s.getFluidState().isEmpty()) {
				continue;
			}
			m.set(bx, by + dy + 1, bz);
			if (w.getBlockState(m).isAir()) {
				outY[0] = by + dy + 1;
				return s;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- colours and drawing
	private int curLook = -1;

	/** The colour set: yours when you picked one, else each effect style has its own. */
	private int lookFor(int st) {
		if (look.idx() != 0) {
			return look.idx();
		}
		return switch (st) {
			case 1, 3, 5, 8 -> 2; // fire
			case 2, 4, 7, 9, 10 -> 3; // ice / white-blue
			case 6 -> 4; // void
			default -> 0;
		};
	}

	private int colourAt(float phase, float time) {
		return switch (curLook < 0 ? look.idx() : curLook) {
			case 1 -> custom.c() | 0xFF000000;
			case 2 -> Rainbow.mix(0xFFFF4A00, 0xFFFFD43B, 0.5F + 0.5F * (float) Math.sin(phase * 12.566F + time * 6F));
			case 3 -> Rainbow.mix(0xFF6FD8FF, 0xFFFFFFFF, 0.5F + 0.5F * (float) Math.sin(phase * 12.566F + time * 6F));
			case 4 -> Rainbow.mix(0xFF7A2BFF, 0xFFFF3BD4, 0.5F + 0.5F * (float) Math.sin(phase * 12.566F + time * 6F));
			case 5 -> impactColour(curAge);
			default -> Rainbow.hue(phase + time * 0.8F);
		};
	}

	/** 0..1 through the effect: white-hot at the hit, then gold, then a deep orange as it fades. */
	private float curAge;

	private static int impactColour(float p) {
		p = Math.max(0F, Math.min(1F, p));
		return p < 0.35F ? Rainbow.mix(0xFFFFFFFF, 0xFFFFD27A, p / 0.35F) : Rainbow.mix(0xFFFFD27A, 0xFFFF6A2A, (p - 0.35F) / 0.65F);
	}

	private void render(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		long now = System.currentTimeMillis();
		float time = Rainbow.seconds() * (speed.f() / 100F);
		WorldDraw.Lines lines = new WorldDraw.Lines(cam);
		float maxR = radius.f();
		for (Quake k : quakes) {
			float t = (now - k.start) / 1000F;
			curAge = Math.min(1F, t / 0.8F);
			double fy = k.y;
			curLook = lookFor(k.style);
			if (k.style != 0) {
				styleLines(lines, k, t, time, maxR);
				continue;
			}
			if (rings.on()) {
				float reach = maxR * (0.6F + 0.4F * Math.min(1.6F, k.pw));
				int nRings = k.air ? 1 : 2;
				for (int j = 0; j < nRings; j++) {
					float p = (t - 0.07F * j) / (j == 0 ? 0.45F : 0.6F);
					curAge = Math.max(0F, Math.min(1F, p));
					if (p <= 0F || p >= 1F) {
						continue;
					}
					float ease = 1F - (1F - p) * (1F - p) * (1F - p);
					float r = Math.max(0.2F, reach * ease * (1F - 0.12F * j));
					float a = (float) Math.pow(1F - p, 1.6) * (j == 0 ? 1F : 0.45F);
					float wd = (j == 0 ? 0.32F : 0.12F) * (1F - 0.7F * p) * (0.7F + 0.3F * k.pw);
					int segs = Math.max(28, Math.min(80, Math.round(r * 9)));
					double yy = fy + 0.08 + j * 0.02;
					double px = k.x + r, pz = k.z;
					int pc = Rainbow.alpha(colourAt(0F, time), a);
					for (int s = 1; s <= segs; s++) {
						double ang = Math.PI * 2 * s / segs;
						double nx = k.x + Math.cos(ang) * r, nz = k.z + Math.sin(ang) * r;
						int cc = Rainbow.alpha(colourAt((float) s / segs, time), a);
						lines.line(px, yy, pz, nx, yy, nz, wd, pc, cc);
						px = nx;
						pz = nz;
						pc = cc;
					}
				}
			}
			if (!k.air && t < 0.18F) {
				float p = t / 0.18F;
				curAge = p * 0.3F;
				float fr = (0.6F + 1.6F * easeOut(p)) * Math.min(1.5F, k.pw);
				for (int j = 0; j < 3; j++) {
					circle(lines, k.x, fy + 0.06 + j * 0.01, k.z, fr * (0.35F + 0.3F * j), 0.5F * (1F - p) + 0.05F,
							Rainbow.alpha(colourAt(0F, time), (1F - p) * (1F - 0.25F * j)));
				}
			}
			if (beam.on() && !k.air && t < 0.6F) {
				float p = t / 0.6F;
				float a = (1F - p) * (1F - p);
				float h = (2.5F + 7F * (1F - (1F - p) * (1F - p))) * Math.min(1.5F, k.pw);
				int c = Rainbow.alpha(colourAt(0.1F, time), a);
				lines.line(k.x, fy, k.z, k.x, fy + h, k.z, 0.55F * (1F - p) + 0.08F, Rainbow.alpha(colourAt(0.5F, time), a * 0.7F), Rainbow.alpha(c, 0.15F));
				lines.line(k.x, fy, k.z, k.x, fy + h * 0.9F, k.z, 0.2F * (1F - p) + 0.04F, Rainbow.alpha(0xFFFFFFFF, a), Rainbow.alpha(0xFFFFFFFF, 0.1F));
			}
		}
		curLook = -1;
		lines.draw(m, q);
	}

	// ================================================================ the extra effect styles
	private static float easeOut(float p) {
		p = Math.max(0F, Math.min(1F, p));
		return 1F - (1F - p) * (1F - p) * (1F - p);
	}

	private float reachOf(Quake q) {
		return radius.f() * (0.6F + 0.4F * Math.min(1.6F, q.pw));
	}

	/** Big chunks of the real floor fly out as 3D pieces (same pool as Explosion Debris / 3D Physics). */
	private void floorChunks(Quake q, float rad, int count, float speed) {
		var ed = ExplosionDebrisMod.instance;
		if (ed == null) {
			return;
		}
		ed.external = true;
		var rnd = ThreadLocalRandom.current();
		double[] gy = new double[1];
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * rad;
			double px = q.x + Math.cos(a) * r, pz = q.z + Math.sin(a) * r;
			BlockState st = floor(px, q.y, pz, gy);
			if (st == null) {
				continue;
			}
			BlockPos bp = BlockPos.ofFloored(px, gy[0] - 1, pz);
			ed.spawnBreak(bp.up(), st, 1, speed * (1.5F + rnd.nextFloat()), 1.6F, 90, true, 900);
		}
	}

	private void styleImpact(Quake q, BlockState st, double fy, float quality) {
		var rnd = ThreadLocalRandom.current();
		float pw = q.pw;
		ParticleEffect blocks = st == null ? ParticleTypes.POOF : new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
		float reach = reachOf(q);
		switch (q.style) {
			case 1 -> { // meteor: fireball, lava and a column of smoke
				Fx.burst(ParticleTypes.FLAME, q.x, fy + 0.4, q.z, Math.round(60 * pw * quality), 0.9);
				Fx.burst(ParticleTypes.LAVA, q.x, fy + 0.4, q.z, Math.round(14 * pw * quality), 0.6);
				Fx.burst(ParticleTypes.LARGE_SMOKE, q.x, fy + 0.5, q.z, Math.round(26 * pw * quality), 0.5);
				Fx.ring(ParticleTypes.FLAME, q.x, fy + 0.2, q.z, 1.2, Math.round(24 * quality), 0.35 * pw);
				floorChunks(q, 2.5F, Math.round(10 * pw * quality), 1F);
			}
			case 2 -> { // lightning
				Fx.burst(ParticleTypes.ELECTRIC_SPARK, q.x, fy + 0.6, q.z, Math.round(60 * pw * quality), 1.1);
				Fx.burst(ParticleTypes.END_ROD, q.x, fy + 1.5, q.z, Math.round(22 * pw * quality), 0.8);
				Fx.ring(ParticleTypes.ELECTRIC_SPARK, q.x, fy + 0.2, q.z, 1.0, Math.round(26 * quality), 0.4 * pw);
			}
			case 3 -> { // ground cracks
				Fx.burst(blocks, q.x, fy + 0.2, q.z, Math.round(30 * pw * quality), 0.7);
				Fx.burst(ParticleTypes.LAVA, q.x, fy + 0.3, q.z, Math.round(8 * pw * quality), 0.4);
				floorChunks(q, 2.2F, Math.round(6 * pw * quality), 0.8F);
			}
			case 4 -> Fx.burst(ParticleTypes.CLOUD, q.x, fy + 0.3, q.z, Math.round(26 * pw * quality), 0.5); // tornado
			case 5 -> { // eruption
				Fx.burst(blocks, q.x, fy + 0.2, q.z, Math.round(24 * pw * quality), 0.8);
				Fx.ring(ParticleTypes.LARGE_SMOKE, q.x, fy + 0.2, q.z, 1.0, Math.round(16 * quality), 0.2 * pw);
			}
			case 6 -> { /* gravity well: the blast comes at the end, see styleTick */ }
			case 7 -> { // soul wave
				Fx.burst(ParticleTypes.SOUL, q.x, fy + 0.5, q.z, Math.round(30 * pw * quality), 0.5);
				Fx.burst(ParticleTypes.SOUL_FIRE_FLAME, q.x, fy + 0.3, q.z, Math.round(30 * pw * quality), 0.7);
			}
			case 8 -> { // crater: a lot of big 3D chunks and dust
				Fx.burst(blocks, q.x, fy + 0.2, q.z, Math.round(40 * pw * quality), 0.9);
				Fx.ring(ParticleTypes.POOF, q.x, fy + 0.15, q.z, 1.0, Math.round(20 * quality), 0.3 * pw);
				floorChunks(q, reach * 0.6F, Math.round(28 * pw * quality), 1.2F);
			}
			case 10 -> { // halo dome: a flash and a ring of light at the rim
				Fx.burst(ParticleTypes.END_ROD, q.x, fy + 0.5, q.z, Math.round(44 * pw * quality), 0.8);
				Fx.burst(ParticleTypes.ELECTRIC_SPARK, q.x, fy + 0.5, q.z, Math.round(24 * pw * quality), 1.0);
				Fx.ring(ParticleTypes.END_ROD, q.x, fy + 0.2, q.z, 1.0, Math.round(30 * quality), 0.5 * pw);
			}
			case 11 -> { // helix
				Fx.burst(ParticleTypes.END_ROD, q.x, fy + 0.4, q.z, Math.round(20 * pw * quality), 0.6);
				Fx.ring(ParticleTypes.CLOUD, q.x, fy + 0.2, q.z, 1.0, Math.round(14 * quality), 0.25 * pw);
			}
			case 12 -> { // light cage
				Fx.ring(ParticleTypes.END_ROD, q.x, fy + 0.2, q.z, reach * 0.55, Math.round(32 * quality), 0.02);
				Fx.burst(blocks, q.x, fy + 0.2, q.z, Math.round(18 * pw * quality), 0.6);
			}
			case 13 -> { // aurora
				Fx.ring(ParticleTypes.END_ROD, q.x, fy + 0.2, q.z, 1.0, Math.round(26 * quality), 0.35 * pw);
				Fx.burst(ParticleTypes.GLOW, q.x, fy + 0.8, q.z, Math.round(24 * pw * quality), 0.5);
			}
			default -> { // star burst
				Fx.burst(ParticleTypes.END_ROD, q.x, fy + 0.6, q.z, Math.round(40 * pw * quality), 0.9);
				Fx.burst(ParticleTypes.FIREWORK, q.x, fy + 0.6, q.z, Math.round(26 * pw * quality), 0.7);
			}
		}
	}

	private void styleTick(Quake q, float quality, ThreadLocalRandom rnd, double[] gy) {
		float reach = reachOf(q);
		int age = q.age;
		double fy = q.y;
		switch (q.style) {
			case 1 -> { // falling embers
				if (age < 14) {
					for (int i = 0; i < Math.round(5 * quality); i++) {
						double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 3.5;
						Fx.spawn(ParticleTypes.FLAME, q.x + Math.cos(a) * r, fy + 5 + rnd.nextDouble() * 3, q.z + Math.sin(a) * r, 0, -0.3, 0);
					}
				}
			}
			case 3 -> { // embers at the tips of the cracks
				if (age < 14) {
					double r = 1 + reach * easeOut(age / 14F);
					for (int i = 0; i < Math.round(6 * quality); i++) {
						double a = rnd.nextDouble() * Math.PI * 2;
						Fx.spawn(rnd.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, q.x + Math.cos(a) * r, fy + 0.15, q.z + Math.sin(a) * r, 0, 0.12, 0);
					}
				}
			}
			case 4 -> { // tornado: a spiral of cloud and floor pieces going up
				if (age < 32) {
					BlockState st = floor(q.x, q.y, q.z, gy);
					ParticleEffect blocks = st == null ? ParticleTypes.CLOUD : new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
					for (int j = 0; j < Math.round(5 * quality); j++) {
						double a = age * 0.75 + j * Math.PI * 2 / 5, r = 0.7 + (age % 16) * 0.12;
						double h = (age % 16) * 0.35;
						Fx.spawn(j % 2 == 0 ? ParticleTypes.CLOUD : blocks, q.x + Math.cos(a) * r, fy + 0.2 + h * 0.3, q.z + Math.sin(a) * r, -Math.sin(a) * 0.22, 0.2, Math.cos(a) * 0.22);
					}
				}
			}
			case 5 -> { // eruption pillars
				if (age >= 2 && age < 16) {
					for (int i = 0; i < Math.round(3 * quality); i++) {
						double a = rnd.nextDouble() * Math.PI * 2, r = 1.5 + rnd.nextDouble() * reach * 0.7;
						double px = q.x + Math.cos(a) * r, pz = q.z + Math.sin(a) * r;
						BlockState st = floor(px, q.y, pz, gy);
						if (st == null) {
							continue;
						}
						ParticleEffect blocks = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
						for (int h = 0; h < 6; h++) {
							Fx.spawn(blocks, px + (rnd.nextDouble() - 0.5) * 0.5, gy[0] + 0.1 + h * 0.3, pz + (rnd.nextDouble() - 0.5) * 0.5, 0, 0.38, 0);
						}
						Fx.spawn(ParticleTypes.FLAME, px, gy[0] + 0.3, pz, 0, 0.3, 0);
					}
				}
			}
			case 6 -> { // gravity well: everything is pulled in, then it blows
				if (age < 14) {
					for (int i = 0; i < Math.round(8 * quality); i++) {
						double a = rnd.nextDouble() * Math.PI * 2, r = reach * (1F - age / 14F) + 0.5;
						double px = q.x + Math.cos(a) * r, pz = q.z + Math.sin(a) * r;
						Fx.spawn(ParticleTypes.REVERSE_PORTAL, px, fy + 0.3 + rnd.nextDouble() * 1.5, pz, -Math.cos(a) * 0.25, 0.04, -Math.sin(a) * 0.25);
					}
				} else if (age == 14) {
					Fx.burst(ParticleTypes.PORTAL, q.x, fy + 0.8, q.z, Math.round(70 * q.pw * quality), 1.1);
					Fx.burst(ParticleTypes.END_ROD, q.x, fy + 0.8, q.z, Math.round(30 * q.pw * quality), 0.9);
					Fx.ring(ParticleTypes.REVERSE_PORTAL, q.x, fy + 0.3, q.z, 1.0, Math.round(26 * quality), 0.45 * q.pw);
					floorChunks(q, 3F, Math.round(8 * quality), 1F);
				}
			}
			case 7 -> { // soul wave: a ring of soul fire that spreads
				if (age < 22) {
					double r = 0.9 + age * 0.5;
					if (r < reach) {
						int n = Math.max(10, Math.round((float) (r * 4 * quality)));
						for (int i = 0; i < n; i++) {
							double a = Math.PI * 2 * i / n + age * 0.1;
							Fx.spawn(i % 3 == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, q.x + Math.cos(a) * r, fy + 0.15, q.z + Math.sin(a) * r, 0, 0.1 + rnd.nextDouble() * 0.06, 0);
						}
					}
				}
			}
			case 8 -> { // crater: dust ring
				if (age < 10) {
					double r = 1 + age * 0.7;
					int n = Math.max(10, Math.round((float) (r * 3 * quality)));
					for (int i = 0; i < n; i++) {
						double a = Math.PI * 2 * i / n;
						Fx.spawn(ParticleTypes.POOF, q.x + Math.cos(a) * r, fy + 0.2, q.z + Math.sin(a) * r, Math.cos(a) * 0.05, 0.05, Math.sin(a) * 0.05);
					}
				}
			}
			case 10 -> { // sparks run along the rim of the dome
				if (age < 14) {
					double r = 0.8 + reach * 0.85 * easeOut(age / 14F);
					int n = Math.max(12, Math.round((float) (r * 4 * quality)));
					for (int i = 0; i < n; i++) {
						double a = Math.PI * 2 * i / n + age * 0.2;
						Fx.spawn(ParticleTypes.END_ROD, q.x + Math.cos(a) * r, fy + 0.15, q.z + Math.sin(a) * r, 0, 0.06, 0);
					}
				}
			}
			case 11 -> { // two strands of sparks climbing
				if (age < 26) {
					for (int sd = 0; sd < 2; sd++) {
						double a = age * 0.55 + sd * Math.PI, h = age * 0.3, r = 1.3 - 0.25 * h / 8;
						Fx.spawn(ParticleTypes.END_ROD, q.x + Math.cos(a) * r, fy + 0.2 + h, q.z + Math.sin(a) * r, 0, 0.02, 0);
						if (age % 2 == 0) {
							Fx.spawn(ParticleTypes.ELECTRIC_SPARK, q.x + Math.cos(a) * r, fy + 0.2 + h, q.z + Math.sin(a) * r, 0, 0, 0);
						}
					}
				}
			}
			case 12 -> { // the cage closes: a burst at the top
				if (age == 12) {
					Fx.burst(ParticleTypes.FIREWORK, q.x, fy + 7.5, q.z, Math.round(50 * q.pw * quality), 0.8);
					Fx.burst(ParticleTypes.END_ROD, q.x, fy + 7.5, q.z, Math.round(26 * q.pw * quality), 0.6);
				}
			}
			case 13 -> { // glowing dust rising in the curtains
				if (age < 26) {
					for (int i = 0; i < Math.round(6 * quality); i++) {
						double a = rnd.nextDouble() * Math.PI * 2, r = reach * (0.35 + 0.2 * rnd.nextInt(3));
						Fx.spawn(rnd.nextInt(3) == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD, q.x + Math.cos(a) * r, fy + 0.3, q.z + Math.sin(a) * r, 0, 0.07 + rnd.nextDouble() * 0.05, 0);
					}
				}
			}
			default -> { }
		}
	}

	/** A jagged line from a to b (the same shape every frame: the random numbers come from the quake's seed). */
	private void bolt(WorldDraw.Lines lines, java.util.Random r, double ax, double ay, double az, double bx, double by, double bz, int segs, double jitter, float width, int c0, int c1) {
		double px = ax, py = ay, pz = az;
		for (int i = 1; i <= segs; i++) {
			double f = i / (double) segs;
			double nx = ax + (bx - ax) * f, ny = ay + (by - ay) * f, nz = az + (bz - az) * f;
			if (i < segs) {
				nx += (r.nextDouble() - 0.5) * jitter;
				ny += (r.nextDouble() - 0.5) * jitter * 0.4;
				nz += (r.nextDouble() - 0.5) * jitter;
			}
			lines.line(px, py, pz, nx, ny, nz, width, c0, c1);
			px = nx;
			py = ny;
			pz = nz;
		}
	}

	private void styleLines(WorldDraw.Lines lines, Quake k, float t, float time, float maxR) {
		double fy = k.y;
		float reach = reachOf(k);
		var r = new java.util.Random(k.seed);
		switch (k.style) {
			case 1 -> { // meteor: a fiery streak from the sky, then a ring
				if (t < 0.25F) {
					float p = t / 0.25F;
					double top = fy + 18 * (1 - p);
					lines.line(k.x + 4 * (1 - p), top + 5, k.z + 2 * (1 - p), k.x, fy + 0.2, k.z, 0.5F, Rainbow.alpha(colourAt(0.1F, time), 0.1F), Rainbow.alpha(0xFFFFFFFF, 0.9F));
				}
				ringLine(lines, k, t, time, reach, 0.7F, 0.3F, 0F);
			}
			case 2 -> { // lightning: a bolt from the sky + forks over the ground
				float a = Math.max(0F, 1F - t / 0.7F) * ((int) (t * 16) % 2 == 0 ? 1F : 0.55F);
				if (a > 0.02F) {
					int c = Rainbow.alpha(colourAt(0.2F, time), a), w = Rainbow.alpha(0xFFFFFFFF, a);
					bolt(lines, r, k.x, fy + 22, k.z, k.x, fy + 0.1, k.z, 12, 2.4, 0.28F * a + 0.05F, c, c);
					r = new java.util.Random(k.seed);
					bolt(lines, r, k.x, fy + 22, k.z, k.x, fy + 0.1, k.z, 12, 2.4, 0.1F, w, w);
					float e = easeOut(t / 0.35F);
					for (int i = 0; i < 7; i++) {
						double ang = r.nextDouble() * Math.PI * 2, len = reach * (0.6 + r.nextDouble() * 0.5) * e;
						bolt(lines, r, k.x, fy + 0.15, k.z, k.x + Math.cos(ang) * len, fy + 0.15, k.z + Math.sin(ang) * len, 9, 1.3, 0.16F, c, Rainbow.alpha(c, 0.15F));
					}
				}
			}
			case 3 -> { // ground cracks glowing, spreading out
				float e = easeOut(t / 0.55F), a = t < 1.2F ? 1F : Math.max(0F, 1F - (t - 1.2F) / 0.8F);
				for (int i = 0; i < 10; i++) {
					double ang = Math.PI * 2 * i / 10 + (r.nextDouble() - 0.5) * 0.4, len = reach * (0.55 + r.nextDouble() * 0.45) * e;
					int c = Rainbow.alpha(colourAt(i / 10F, time), a);
					double ex = k.x + Math.cos(ang) * len, ez = k.z + Math.sin(ang) * len;
					bolt(lines, r, k.x, fy + 0.07, k.z, ex, fy + 0.07, ez, 10, 0.9, 0.14F, c, Rainbow.alpha(c, 0.3F * a));
					double mx = k.x + Math.cos(ang) * len * 0.55, mz = k.z + Math.sin(ang) * len * 0.55;
					for (int b = 0; b < 2; b++) {
						double ba = ang + (b == 0 ? 0.7 : -0.7), bl = len * 0.35;
						bolt(lines, r, mx, fy + 0.07, mz, mx + Math.cos(ba) * bl, fy + 0.07, mz + Math.sin(ba) * bl, 5, 0.6, 0.08F, c, Rainbow.alpha(c, 0.2F * a));
					}
				}
			}
			case 4 -> { // tornado strands
				float a = t < 1.1F ? 1F : Math.max(0F, 1F - (t - 1.1F) / 0.9F), hgt = 9F * Math.min(1F, t / 0.5F);
				for (int s = 0; s < 3; s++) {
					double px = 0, py = 0, pz = 0;
					for (int i = 0; i <= 28; i++) {
						double h = hgt * i / 28.0, ang = s * 2.094 + h * 1.1 + t * 9, rad = 0.5 + h * 0.2;
						double nx = k.x + Math.cos(ang) * rad, ny = fy + 0.1 + h, nz = k.z + Math.sin(ang) * rad;
						if (i > 0) {
							int c = Rainbow.alpha(colourAt(i / 28F, time), a * (1F - i / 40F));
							lines.line(px, py, pz, nx, ny, nz, 0.14F, c, c);
						}
						px = nx;
						py = ny;
						pz = nz;
					}
				}
			}
			case 5 -> { // eruption: columns of light over the places that erupt
				for (int i = 0; i < 9; i++) {
					double ang = r.nextDouble() * Math.PI * 2, rad = 1.5 + r.nextDouble() * reach * 0.7;
					float st = 0.1F + i * 0.06F, p = (t - st) / 0.7F;
					if (p <= 0F || p >= 1F) {
						continue;
					}
					float hh = (2F + 4F * r.nextFloat()) * (float) Math.sin(p * Math.PI);
					int c = Rainbow.alpha(colourAt(i / 9F, time), 1F - p);
					double px = k.x + Math.cos(ang) * rad, pz = k.z + Math.sin(ang) * rad;
					lines.line(px, fy, pz, px, fy + hh, pz, 0.35F * (1F - p) + 0.06F, c, Rainbow.alpha(c, 0.1F));
				}
				ringLine(lines, k, t, time, reach, 0.6F, 0.2F, 0F);
			}
			case 6 -> { // gravity well: rings shrink in, then a flash and a ring out
				if (t < 0.7F) {
					float p = t / 0.7F;
					for (int j = 0; j < 3; j++) {
						float rr = Math.max(0.3F, reach * (1F - p) * (1F - 0.25F * j));
						circle(lines, k.x, fy + 0.1 + j * 0.5 * p, k.z, rr, 0.12F, Rainbow.alpha(colourAt(j / 3F, time), 0.3F + 0.7F * p));
					}
				} else {
					float p = (t - 0.7F) / 0.8F;
					if (p < 1F) {
						circle(lines, k.x, fy + 0.1, k.z, reach * easeOut(p), 0.3F * (1F - p) + 0.04F, Rainbow.alpha(colourAt(0.3F, time), 1F - p));
						lines.line(k.x, fy, k.z, k.x, fy + 10 * (1F - p) + 1, k.z, 0.5F * (1F - p), Rainbow.alpha(0xFFFFFFFF, 1F - p), Rainbow.alpha(0xFFFFFFFF, 0.05F));
					}
				}
			}
			case 7 -> { // soul wave: wobbling rings
				for (int j = 0; j < 2; j++) {
					float p = (t - 0.18F * j) / 1.1F;
					if (p <= 0F || p >= 1F) {
						continue;
					}
					float rr = Math.max(0.3F, reach * easeOut(p));
					int segs = Math.max(32, Math.round(rr * 8));
					double px = 0, py = 0, pz = 0;
					int c = Rainbow.alpha(colourAt(j * 0.4F, time), 1F - p);
					for (int i = 0; i <= segs; i++) {
						double ang = Math.PI * 2 * i / segs;
						double nx = k.x + Math.cos(ang) * rr, nz = k.z + Math.sin(ang) * rr, ny = fy + 0.3 + 0.35 * Math.sin(ang * 7 + t * 10) * (1F - p);
						if (i > 0) {
							lines.line(px, py, pz, nx, ny, nz, 0.18F * (1F - p) + 0.03F, c, c);
						}
						px = nx;
						py = ny;
						pz = nz;
					}
				}
			}
			case 8 -> ringLine(lines, k, t, time, reach, 0.9F, 0.45F, 0.1F); // crater: heavy dark-ish ring
			case 10 -> { // halo dome: a glass half-sphere that grows and fades
				float p = t / 1.0F;
				if (p < 1F) {
					float e = easeOut(p), a = (float) Math.pow(1F - p, 1.3);
					float rr = reach * 0.85F * e;
					circle(lines, k.x, fy + 0.08, k.z, Math.max(0.3F, rr), 0.22F * (1F - 0.5F * p) + 0.03F, Rainbow.alpha(colourAt(0.2F, time), a));
					for (int j = 1; j <= 4; j++) {
						double lat = j * (Math.PI / 2) / 5;
						circle(lines, k.x, fy + 0.08 + Math.sin(lat) * rr * 0.8, k.z, (float) Math.max(0.2, Math.cos(lat) * rr), 0.08F, Rainbow.alpha(colourAt(j / 5F, time), a * 0.75F));
					}
					for (int m = 0; m < 12; m++) {
						double ang = Math.PI * 2 * m / 12 + p * 0.6;
						double px = k.x + Math.cos(ang) * rr, py = fy + 0.08, pz = k.z + Math.sin(ang) * rr;
						for (int i = 1; i <= 8; i++) {
							double lat = i * (Math.PI / 2) / 8, cr = Math.cos(lat) * rr;
							double nx = k.x + Math.cos(ang) * cr, ny = fy + 0.08 + Math.sin(lat) * rr * 0.8, nz = k.z + Math.sin(ang) * cr;
							lines.line(px, py, pz, nx, ny, nz, 0.07F, Rainbow.alpha(colourAt(m / 12F, time), a * 0.7F), Rainbow.alpha(colourAt(m / 12F + 0.2F, time), a * 0.25F));
							px = nx;
							py = ny;
							pz = nz;
						}
					}
				}
			}
			case 11 -> { // helix: two glowing strands spiral up, with a ring on top
				float a = t < 1.1F ? 1F : Math.max(0F, 1F - (t - 1.1F) / 0.7F);
				if (a > 0F) {
					float hgt = 8F * easeOut(t / 0.7F);
					for (int sd = 0; sd < 2; sd++) {
						double px = 0, py = 0, pz = 0;
						for (int i = 0; i <= 36; i++) {
							double h = hgt * i / 36.0, ang = sd * Math.PI + h * 1.3 + t * 6, rad = 1.3 - 0.25 * h / 8;
							double nx = k.x + Math.cos(ang) * rad, ny = fy + 0.1 + h, nz = k.z + Math.sin(ang) * rad;
							if (i > 0) {
								lines.line(px, py, pz, nx, ny, nz, 0.15F, Rainbow.alpha(colourAt(i / 36F + sd * 0.5F, time), a), Rainbow.alpha(colourAt((i + 1) / 36F + sd * 0.5F, time), a));
							}
							px = nx;
							py = ny;
							pz = nz;
						}
					}
					lines.line(k.x, fy, k.z, k.x, fy + hgt, k.z, 0.1F, Rainbow.alpha(0xFFFFFFFF, a * 0.8F), Rainbow.alpha(0xFFFFFFFF, 0.05F));
					circle(lines, k.x, fy + 0.1 + hgt, k.z, 1.05F, 0.12F, Rainbow.alpha(colourAt(0.5F, time), a));
					circle(lines, k.x, fy + 0.1, k.z, 1.5F, 0.14F, Rainbow.alpha(colourAt(0F, time), a * 0.8F));
				}
			}
			case 12 -> { // light cage: pillars of light rise around the hit and close above it
				float a = t < 1.0F ? 1F : Math.max(0F, 1F - (t - 1.0F) / 0.6F);
				if (a > 0F) {
					float rr = reach * 0.55F, hgt = 7F * easeOut(Math.min(1F, t / 0.6F));
					float close = Math.max(0F, Math.min(1F, (t - 0.5F) / 0.4F));
					int n = 16;
					for (int i = 0; i < n; i++) {
						double ang = Math.PI * 2 * i / n;
						double bx = k.x + Math.cos(ang) * rr, bz = k.z + Math.sin(ang) * rr;
						int c = Rainbow.alpha(colourAt(i / (float) n, time), a);
						lines.line(bx, fy, bz, bx, fy + hgt, bz, 0.14F, c, Rainbow.alpha(c, 0.6F));
						if (close > 0F) {
							lines.line(bx, fy + hgt, bz, k.x, fy + hgt + 3.2 * close, k.z, 0.08F, Rainbow.alpha(c, 0.8F), Rainbow.alpha(0xFFFFFFFF, a));
						}
					}
					circle(lines, k.x, fy + 0.08, k.z, rr, 0.2F, Rainbow.alpha(colourAt(0.3F, time), a));
					circle(lines, k.x, fy + hgt, k.z, rr * (1F - 0.15F * close), 0.14F, Rainbow.alpha(colourAt(0.7F, time), a));
					circle(lines, k.x, fy + hgt * 0.5, k.z, rr, 0.07F, Rainbow.alpha(colourAt(0.5F, time), a * 0.6F));
				}
			}
			case 13 -> { // aurora: rippling curtains of light rise from three rings
				float a = t < 1.2F ? 1F : Math.max(0F, 1F - (t - 1.2F) / 0.7F);
				if (a > 0F) {
					float grow = easeOut(Math.min(1F, t / 0.5F));
					for (int j = 0; j < 3; j++) {
						float rr = reach * (0.35F + 0.2F * j) * grow;
						int n = 48;
						for (int i = 0; i < n; i++) {
							double ang = Math.PI * 2 * i / n;
							double h = (1.6 + 1.3 * Math.sin(ang * 3 + t * 5 + j * 1.7)) * grow * (1.0 - 0.2 * j);
							double bx = k.x + Math.cos(ang) * rr, bz = k.z + Math.sin(ang) * rr;
							int c = Rainbow.alpha(colourAt(i / (float) n + j * 0.2F, time), a);
							lines.line(bx, fy + 0.1, bz, bx, fy + 0.1 + h, bz, 0.16F, c, Rainbow.alpha(c, 0.04F));
						}
						circle(lines, k.x, fy + 0.1, k.z, Math.max(0.3F, rr), 0.1F, Rainbow.alpha(colourAt(j * 0.3F, time), a * 0.8F));
					}
				}
			}
			default -> { // star burst: beams going out and up
				float p = t / 0.9F;
				if (p < 1F) {
					float e = easeOut(p), a = 1F - p;
					for (int i = 0; i < 12; i++) {
						double ang = Math.PI * 2 * i / 12 + 0.26;
						double len = reach * 0.9 * e, up = (i % 2 == 0 ? 0.9 : 0.4) * len;
						int c = Rainbow.alpha(colourAt(i / 12F, time), a);
						lines.line(k.x, fy + 0.2, k.z, k.x + Math.cos(ang) * len, fy + 0.2 + up, k.z + Math.sin(ang) * len, 0.1F + 0.12F * a, Rainbow.alpha(0xFFFFFFFF, a), Rainbow.alpha(c, 0.2F * a));
					}
					lines.line(k.x, fy, k.z, k.x, fy + 12 * e, k.z, 0.4F * a + 0.05F, Rainbow.alpha(0xFFFFFFFF, a), Rainbow.alpha(colourAt(0.5F, time), 0.1F));
				}
			}
		}
	}

	private void circle(WorldDraw.Lines lines, double cx, double y, double cz, float r, float w, int col) {
		int segs = Math.max(28, Math.min(80, Math.round(r * 9)));
		double px = cx + r, pz = cz;
		for (int i = 1; i <= segs; i++) {
			double a = Math.PI * 2 * i / segs, nx = cx + Math.cos(a) * r, nz = cz + Math.sin(a) * r;
			lines.line(px, y, pz, nx, y, nz, w, col, col);
			px = nx;
			pz = nz;
		}
	}

	private void ringLine(WorldDraw.Lines lines, Quake k, float t, float time, float reach, float dur, float width, float lift) {
		for (int j = 0; j < 2; j++) {
			float p = (t - 0.1F * j) / dur;
			if (p <= 0F || p >= 1F) {
				continue;
			}
			float rr = Math.max(0.3F, reach * easeOut(p) * (1F - 0.15F * j));
			circle(lines, k.x, k.y + 0.08 + lift, k.z, rr, width * (1F - 0.6F * p), Rainbow.alpha(colourAt(j * 0.3F, time), (float) Math.pow(1F - p, 1.4)));
		}
	}
}
