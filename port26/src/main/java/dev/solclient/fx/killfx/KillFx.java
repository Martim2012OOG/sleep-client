package dev.solclient.fx.killfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.solclient.fx.Fx;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Plays 3D kill effects (Blockbench models + keyframe animation, see {@link KillFxModel}) in the world, client side only.
 * The effect stands where the victim died and is turned so you see it from the side. Its bite is timed at 1 s, when
 * vanilla removes the dead body, so the victim "disappears" into the creature without touching the real entity.
 */
public final class KillFx {

	private static final class Active {
		final KillFxModel model;
		final double x, y, z;
		final float yaw;
		/** The victim's skin for the stand-in, or null (not a player: the stand-in is not drawn). */
		final Identifier skin;
		/** The real dead body hidden while the effect plays (entity id), or -1. */
		final int hideId;
		final long start = System.nanoTime();
		int nextEvent;

		Active(KillFxModel model, double x, double y, double z, float yaw, Identifier skin, int hideId) {
			this.model = model;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.skin = skin;
			this.hideId = hideId;
		}

		float time() {
			return (System.nanoTime() - start) / 1e9F;
		}
	}

	private static final List<Active> ACTIVE = new ArrayList<>();
	/** Dead bodies that the effect replaces with its own stand-in (entity ids). */
	private static final java.util.Set<Integer> HIDDEN = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final int MAX = 6;
	private static boolean hooked;
	public static volatile String lastError;

	private KillFx() {
	}

	private static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
			if (ACTIVE.isEmpty()) {
				return;
			}
			try {
				render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
			} catch (Throwable t) {
				if (lastError == null) {
					System.err.println("[Caos] kill effect render: " + t);
					t.printStackTrace();
				}
				lastError = String.valueOf(t);
				ACTIVE.clear();
			}
					} finally {
				dev.solclient.Perf.add("Kill effects", System.nanoTime() - kuboPerfT0);
			}
		});
	}

	/**
	 * Plays the effect on a victim that just died. A player is replaced by a standing copy with their own skin (the real
	 * body, which would tip over, is hidden until the effect ends); other mobs keep their normal death.
	 */
	public static boolean play(String id, net.minecraft.world.entity.Entity victim) {
		Identifier skin = null;
		int hide = -1;
		if (victim instanceof net.minecraft.client.player.AbstractClientPlayer p) {
			try {
				skin = p.getSkin().body().texturePath();
			} catch (RuntimeException e) {
				skin = null;
			}
			if (skin != null) {
				hide = p.getId();
			}
		}
		return play(id, victim.getX(), victim.getY(), victim.getZ(), skin, hide);
	}

	/** Plays the effect 'id' at a position. skin = whose stand-in to draw (null = none), hideId = body to hide (-1 = none). */
	public static boolean play(String id, double x, double y, double z, Identifier skin, int hideId) {
		Minecraft mc = Minecraft.getInstance();
		KillFxModel m = KillFxModel.get(id);
		if (m == null || mc.level == null) {
			return false;
		}
		init();
		System.out.println("[Caos] kill effect: playing " + id + " at " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) + (skin != null ? " with skin" : ""));
		// model +Z points at you, so the creature crosses your screen from the side
		Vec3 eye = mc.gameRenderer != null && dev.solclient.compat.Mc.camera(mc) != null ? dev.solclient.compat.Mc.camera(mc).position()
				: mc.player != null ? mc.player.getEyePosition() : new Vec3(x, y, z + 1);
		float yaw = (float) Math.atan2(eye.x - x, eye.z - z);
		synchronized (ACTIVE) {
			// never the same effect twice at the same place within a second (double signals / double clicks)
			for (Active o : ACTIVE) {
				if (o.time() < 1F && (o.x - x) * (o.x - x) + (o.z - z) * (o.z - z) < 4.0 && Math.abs(o.y - y) < 3.0) {
					return true;
				}
			}
			if (ACTIVE.size() >= MAX) {
				Active old = ACTIVE.remove(0);
				HIDDEN.remove(old.hideId);
			}
			ACTIVE.add(new Active(m, x, y, z, yaw, skin, hideId));
			if (hideId >= 0) {
				HIDDEN.add(hideId);
			}
		}
		return true;
	}

	/** True while a kill effect stands in for this (dead) entity: its renderer skips it. */
	public static boolean hides(net.minecraft.world.entity.Entity e) {
		if (HIDDEN.isEmpty() || !HIDDEN.contains(e.getId())) {
			return false;
		}
		// servers often respawn a player with the same id: never hide someone who is alive again
		return !(e instanceof net.minecraft.world.entity.LivingEntity l) || l.isDeadOrDying() || l.getHealth() <= 0F;
	}

	public static void clear() {
		synchronized (ACTIVE) {
			ACTIVE.clear();
			HIDDEN.clear();
		}
	}

	// ---------------------------------------------------------------- drawing
	private static final float[] P = new float[3], R = new float[3], S = new float[3];

	private static void render(PoseStack matrices, SubmitNodeCollector queue, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel world = mc.level;
		if (world == null) {
			clear();
			return;
		}
		List<Active> list;
		synchronized (ACTIVE) {
			ACTIVE.removeIf(a -> {
				if (a.time() > a.model.length + 0.05F) {
					HIDDEN.remove(a.hideId);
					return true;
				}
				return false;
			});
			list = new ArrayList<>(ACTIVE);
		}
		for (Active a : list) {
			float t = Math.min(a.time(), a.model.length);
			KillFxModel m = a.model;
			while (a.nextEvent < m.events.length && m.eventTimes[a.nextEvent] <= t) {
				fire(world, a, m.events[a.nextEvent++]);
			}
			if (cam.distanceToSqr(a.x, a.y, a.z) > 96 * 96) {
				continue;
			}
			// full brightness: the colours stay vivid at night and in caves
			int light = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
			RenderType layer = RenderTypes.entityCutout(m.texture);
			RenderType skinLayer = a.skin == null ? null : RenderTypes.entityTranslucent(a.skin);
			matrices.pushPose();
			matrices.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
			matrices.mulPose(Axis.YP.rotation(a.yaw));
			matrices.scale(1F / 16F, 1F / 16F, 1F / 16F);
			for (int i = 0; i < m.bones.length; i++) {
				KillFxModel.Bone b = m.bones[i];
				if (b.verts.length == 0 || hidden(m, i) || b.skin && skinLayer == null) {
					continue;
				}
				matrices.pushPose();
				apply(m, i, t, matrices, 0);
				final KillFxModel.Bone bone = b;
				queue.submitCustomGeometry(matrices, b.skin ? skinLayer : layer, (entry, vc) -> emit(bone, entry, vc, light));
				matrices.popPose();
			}
			matrices.popPose();
		}
	}

	static boolean hidden(KillFxModel m, int i) {
		for (int d = 0; i >= 0 && d < 16; d++) {
			if (m.bones[i].preview) {
				return true;
			}
			i = m.bones[i].parent;
		}
		return false;
	}

	/** parent first: translate(pivot + position) * Rz * Ry * Rx * scale * translate(-pivot), like Blockbench. */
	static void apply(KillFxModel m, int i, float t, PoseStack ms, int depth) {
		KillFxModel.Bone b = m.bones[i];
		if (b.parent >= 0 && b.parent != i && depth < 16) {
			apply(m, b.parent, t, ms, depth + 1);
		}
		KillFxModel.sample(b.pos, t, 0F, P);
		KillFxModel.sample(b.rot, t, 0F, R);
		KillFxModel.sample(b.scale, t, 1F, S);
		ms.translate(b.px + P[0], b.py + P[1], b.pz + P[2]);
		float rx = b.restX + R[0], ry = b.restY + R[1], rz = b.restZ + R[2];
		if (rx != 0 || ry != 0 || rz != 0) {
			ms.mulPose(new Quaternionf().rotationZYX(rz, ry, rx));
		}
		if (S[0] != 1 || S[1] != 1 || S[2] != 1) {
			// a bone scaled to 0 must not produce a broken normal matrix
			ms.scale(Math.max(S[0], 1e-4F), Math.max(S[1], 1e-4F), Math.max(S[2], 1e-4F));
		}
		ms.translate(-b.px, -b.py, -b.pz);
	}

	static void emit(KillFxModel.Bone b, PoseStack.Pose entry, VertexConsumer vc, int light) {
		float[] v = b.verts, n = b.normals;
		int overlay = OverlayTexture.NO_OVERLAY;
		int quads = v.length / 20;
		for (int q = 0; q < quads; q++) {
			int o = q * 20;
			float nx = n[q * 3], ny = n[q * 3 + 1], nz = n[q * 3 + 2];
			for (int k = 0; k < 4; k++) {
				int i = o + k * 5;
				vc.addVertex(entry, v[i], v[i + 1], v[i + 2]).setColor(0xFFFFFFFF).setUv(v[i + 3], v[i + 4]).setOverlay(overlay).setLight(light).setNormal(entry, nx, ny,
						nz);
			}
		}
	}

	// ---------------------------------------------------------------- events: sounds and particles
	private static void fire(ClientLevel world, Active a, String cmd) {
		try {
			String[] p = cmd.trim().split("\\s+");
			switch (p[0]) {
				case "s" -> {
					SoundEvent ev = sound(p[1]);
					if (ev != null) {
						world.playLocalSound(a.x, a.y + 1, a.z, ev, SoundSource.PLAYERS, f(p, 2, 1F), f(p, 3, 1F), false);
					}
				}
				case "p", "ring" -> particles(a, p, p[0].equals("ring"));
				default -> {
				} // unknown commands are ignored
			}
		} catch (RuntimeException e) {
			System.err.println("[Caos] kill effect event '" + cmd + "': " + e);
		}
	}

	private static float f(String[] p, int i, float def) {
		return i < p.length ? Float.parseFloat(p[i]) : def;
	}

	private static void particles(Active a, String[] p, boolean ring) {
		ParticleOptions type = particle(p[1]);
		boolean perParticle = p[1].equals("confetti");
		int count = (int) f(p, 2, 1);
		float ox = f(p, 3, 0) / 16F, oy = f(p, 4, 0) / 16F, oz = f(p, 5, 0) / 16F, spread = f(p, 6, 0) / 16F, speed = f(p, 7, 0.2F);
		float c = (float) Math.cos(a.yaw), s = (float) Math.sin(a.yaw);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			double x = ox, y = oy, z = oz, vx, vy, vz;
			if (ring) {
				double an = Math.PI * 2 * i / count;
				x += Math.cos(an) * spread;
				z += Math.sin(an) * spread;
				vx = Math.cos(an) * speed * 0.5;
				vz = Math.sin(an) * speed * 0.5;
				vy = speed * (0.5 + r.nextDouble() * 0.7);
			} else {
				x += (r.nextDouble() - 0.5) * 2 * spread;
				y += (r.nextDouble() - 0.5) * 1.2 * spread;
				z += (r.nextDouble() - 0.5) * 2 * spread;
				vx = (r.nextDouble() - 0.5) * 2 * speed;
				vz = (r.nextDouble() - 0.5) * 2 * speed;
				vy = r.nextDouble() * speed * 1.2;
			}
			// same turn as the model (rotation about Y by yaw)
			double wx = x * c + z * s, wz = -x * s + z * c, wvx = vx * c + vz * s, wvz = -vx * s + vz * c;
			Fx.spawn(perParticle ? particle(p[1]) : type, a.x + wx, a.y + y, a.z + wz, wvx, vy, wvz);
		}
	}

	/** Bloco pelo nome (as constantes Blocks.X_CONCRETE foram agrupadas no 26.2). */
	private static net.minecraft.world.level.block.Block blk(String name) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name));
	}

	private static final net.minecraft.world.level.block.Block[] CONFETTI = {blk("magenta_concrete"), blk("yellow_concrete"), blk("lime_concrete"),
			blk("light_blue_concrete"), blk("orange_concrete"), blk("purple_concrete"), blk("white_concrete"), blk("pink_concrete")};

	private static ParticleOptions particle(String name) {
		return switch (name) {
			case "splash" -> ParticleTypes.SPLASH;
			case "water" -> ParticleTypes.FALLING_WATER;
			case "chunks" -> new BlockParticleOption(ParticleTypes.BLOCK, blk("light_blue_stained_glass").defaultBlockState());
			case "blood" -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
			case "gold" -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GOLD_BLOCK.defaultBlockState());
			case "poof" -> ParticleTypes.POOF;
			case "cloud" -> ParticleTypes.CLOUD;
			case "flame" -> ParticleTypes.FLAME;
			case "smoke" -> ParticleTypes.LARGE_SMOKE;
			case "heart" -> ParticleTypes.HEART;
			case "sparkle" -> ParticleTypes.END_ROD;
			case "note" -> ParticleTypes.NOTE;
			case "firework" -> ParticleTypes.FIREWORK;
			case "portal" -> ParticleTypes.PORTAL;
			case "dirt" -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
			case "confetti" -> new BlockParticleOption(ParticleTypes.BLOCK, CONFETTI[ThreadLocalRandom.current().nextInt(CONFETTI.length)].defaultBlockState());
			default -> ParticleTypes.CRIT;
		};
	}

	private static final java.util.Map<String, Optional<SoundEvent>> SOUNDS = new java.util.HashMap<>();

	private static SoundEvent sound(String id) {
		return SOUNDS.computeIfAbsent(id, k -> {
			Identifier i = Identifier.tryParse(k);
			return i == null ? Optional.empty() : BuiltInRegistries.SOUND_EVENT.getOptional(i);
		}).orElse(null);
	}
}
