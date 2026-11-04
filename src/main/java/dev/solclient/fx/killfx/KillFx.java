package dev.solclient.fx.killfx;

import dev.solclient.fx.Fx;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
		WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
			if (ACTIVE.isEmpty()) {
				return;
			}
			try {
				render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
			} catch (Throwable t) {
				if (lastError == null) {
					System.err.println("[Sleep] kill effect render: " + t);
					t.printStackTrace();
				}
				lastError = String.valueOf(t);
				ACTIVE.clear();
			}
		});
	}

	/**
	 * Plays the effect on a victim that just died. A player is replaced by a standing copy with their own skin (the real
	 * body, which would tip over, is hidden until the effect ends); other mobs keep their normal death.
	 */
	public static boolean play(String id, net.minecraft.entity.Entity victim) {
		Identifier skin = null;
		int hide = -1;
		if (victim instanceof net.minecraft.client.network.AbstractClientPlayerEntity p) {
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
		MinecraftClient mc = MinecraftClient.getInstance();
		KillFxModel m = KillFxModel.get(id);
		if (m == null || mc.world == null) {
			return false;
		}
		init();
		// model +Z points at you, so the creature crosses your screen from the side
		Vec3d eye = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null ? mc.gameRenderer.getCamera().getCameraPos()
				: mc.player != null ? mc.player.getEyePos() : new Vec3d(x, y, z + 1);
		float yaw = (float) Math.atan2(eye.x - x, eye.z - z);
		synchronized (ACTIVE) {
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
	public static boolean hides(net.minecraft.entity.Entity e) {
		if (HIDDEN.isEmpty() || !HIDDEN.contains(e.getId())) {
			return false;
		}
		// servers often respawn a player with the same id: never hide someone who is alive again
		return !(e instanceof net.minecraft.entity.LivingEntity l) || l.isDead() || l.getHealth() <= 0F;
	}

	public static void clear() {
		synchronized (ACTIVE) {
			ACTIVE.clear();
			HIDDEN.clear();
		}
	}

	// ---------------------------------------------------------------- drawing
	private static final float[] P = new float[3], R = new float[3], S = new float[3];

	private static void render(MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
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
			if (cam.squaredDistanceTo(a.x, a.y, a.z) > 96 * 96) {
				continue;
			}
			// full brightness: the colours stay vivid at night and in caves
			int light = net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;
			RenderLayer layer = RenderLayers.entityCutoutNoCull(m.texture);
			RenderLayer skinLayer = a.skin == null ? null : RenderLayers.entityTranslucent(a.skin);
			matrices.push();
			matrices.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation(a.yaw));
			matrices.scale(1F / 16F, 1F / 16F, 1F / 16F);
			for (int i = 0; i < m.bones.length; i++) {
				KillFxModel.Bone b = m.bones[i];
				if (b.verts.length == 0 || hidden(m, i) || b.skin && skinLayer == null) {
					continue;
				}
				matrices.push();
				apply(m, i, t, matrices, 0);
				final KillFxModel.Bone bone = b;
				queue.submitCustom(matrices, b.skin ? skinLayer : layer, (entry, vc) -> emit(bone, entry, vc, light));
				matrices.pop();
			}
			matrices.pop();
		}
	}

	private static boolean hidden(KillFxModel m, int i) {
		for (int d = 0; i >= 0 && d < 16; d++) {
			if (m.bones[i].preview) {
				return true;
			}
			i = m.bones[i].parent;
		}
		return false;
	}

	/** parent first: translate(pivot + position) * Rz * Ry * Rx * scale * translate(-pivot), like Blockbench. */
	private static void apply(KillFxModel m, int i, float t, MatrixStack ms, int depth) {
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
			ms.multiply(new Quaternionf().rotationZYX(rz, ry, rx));
		}
		if (S[0] != 1 || S[1] != 1 || S[2] != 1) {
			// a bone scaled to 0 must not produce a broken normal matrix
			ms.scale(Math.max(S[0], 1e-4F), Math.max(S[1], 1e-4F), Math.max(S[2], 1e-4F));
		}
		ms.translate(-b.px, -b.py, -b.pz);
	}

	private static void emit(KillFxModel.Bone b, MatrixStack.Entry entry, VertexConsumer vc, int light) {
		float[] v = b.verts, n = b.normals;
		int overlay = OverlayTexture.DEFAULT_UV;
		int quads = v.length / 20;
		for (int q = 0; q < quads; q++) {
			int o = q * 20;
			float nx = n[q * 3], ny = n[q * 3 + 1], nz = n[q * 3 + 2];
			for (int k = 0; k < 4; k++) {
				int i = o + k * 5;
				vc.vertex(entry, v[i], v[i + 1], v[i + 2]).color(0xFFFFFFFF).texture(v[i + 3], v[i + 4]).overlay(overlay).light(light).normal(entry, nx, ny,
						nz);
			}
		}
	}

	// ---------------------------------------------------------------- events: sounds and particles
	private static void fire(ClientWorld world, Active a, String cmd) {
		try {
			String[] p = cmd.trim().split("\\s+");
			switch (p[0]) {
				case "s" -> {
					SoundEvent ev = sound(p[1]);
					if (ev != null) {
						world.playSoundClient(a.x, a.y + 1, a.z, ev, SoundCategory.PLAYERS, f(p, 2, 1F), f(p, 3, 1F), false);
					}
				}
				case "p", "ring" -> particles(a, p, p[0].equals("ring"));
				default -> {
				} // unknown commands are ignored
			}
		} catch (RuntimeException e) {
			System.err.println("[Sleep] kill effect event '" + cmd + "': " + e);
		}
	}

	private static float f(String[] p, int i, float def) {
		return i < p.length ? Float.parseFloat(p[i]) : def;
	}

	private static void particles(Active a, String[] p, boolean ring) {
		ParticleEffect type = particle(p[1]);
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

	private static final net.minecraft.block.Block[] CONFETTI = {Blocks.MAGENTA_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
			Blocks.LIGHT_BLUE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.PINK_CONCRETE};

	private static ParticleEffect particle(String name) {
		return switch (name) {
			case "splash" -> ParticleTypes.SPLASH;
			case "water" -> ParticleTypes.FALLING_WATER;
			case "chunks" -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
			case "blood" -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
			case "gold" -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GOLD_BLOCK.getDefaultState());
			case "poof" -> ParticleTypes.POOF;
			case "cloud" -> ParticleTypes.CLOUD;
			case "flame" -> ParticleTypes.FLAME;
			case "smoke" -> ParticleTypes.LARGE_SMOKE;
			case "heart" -> ParticleTypes.HEART;
			case "sparkle" -> ParticleTypes.END_ROD;
			case "note" -> ParticleTypes.NOTE;
			case "firework" -> ParticleTypes.FIREWORK;
			case "portal" -> ParticleTypes.PORTAL;
			case "dirt" -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());
			case "confetti" -> new BlockStateParticleEffect(ParticleTypes.BLOCK, CONFETTI[ThreadLocalRandom.current().nextInt(CONFETTI.length)].getDefaultState());
			default -> ParticleTypes.CRIT;
		};
	}

	private static final java.util.Map<String, Optional<SoundEvent>> SOUNDS = new java.util.HashMap<>();

	private static SoundEvent sound(String id) {
		return SOUNDS.computeIfAbsent(id, k -> {
			Identifier i = Identifier.tryParse(k);
			return i == null ? Optional.empty() : Registries.SOUND_EVENT.getOptionalValue(i);
		}).orElse(null);
	}
}
