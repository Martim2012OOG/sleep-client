package dev.solclient.cosmetic.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.solclient.SolClient;
import dev.solclient.cosmetic.CosmeticFeatureRenderer;
import dev.solclient.cosmetic.CosmeticModels;
import dev.solclient.cosmetic.Cosmetics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Keeps one live {@link Pet} per player that wears a pet cosmetic, ticks their brains and draws them in the world.
 * Right click your (or anyone's) pet to pet it, holding food to feed it; left click to boop it.
 */
public final class Pets {

	private static final Map<Integer, Pet> PETS = new HashMap<>();
	private static final Identifier SHADOW = Identifier.withDefaultNamespace("textures/misc/shadow.png");
	private static boolean useWas, attackWas, hooked;
	public static volatile String lastError;

	private Pets() {
	}

	public static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		ClientTickEvents.END_CLIENT_TICK.register(Pets::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
			try {
				render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
			} catch (Throwable t) {
				if (lastError == null) {
					System.err.println("[Sleep] pets render: " + t);
					t.printStackTrace();
				}
				lastError = String.valueOf(t);
			}
					} finally {
				dev.solclient.Perf.add("Pets", System.nanoTime() - kuboPerfT0);
			}
		});
	}

	/** The live pet a player wears (first pet cosmetic with a rig), or null. */
	public static CosmeticModels.Def petOf(UUID player) {
		if (player == null) {
			return null;
		}
		if ("1".equals(System.getProperty("kubo.verityTest")) && Minecraft.getInstance().player != null
				&& player.equals(Minecraft.getInstance().player.getUUID())) {
			CosmeticModels.Def v = CosmeticModels.all().get("verity"); // test only (-Dkubo.verityTest=1): Verity without buying him
			if (v != null) {
				return v;
			}
		}
		for (CosmeticModels.Def d : Cosmetics.worn(player)) {
			if (d.livePet()) {
				return d;
			}
		}
		return null;
	}

	public static int count() {
		return PETS.size();
	}

	// ================================================================ tick
	private static void tick(Minecraft mc) {
		ClientLevel world = mc.level;
		if (world == null || mc.player == null || SolClient.settings == null || !SolClient.settings.cosmetics.on()) {
			PETS.clear();
			return;
		}
		try {
			Set<Integer> seen = new HashSet<>();
			double maxSq = dev.solclient.mods.PerformanceMod.cosmeticDistanceSq();
			List<Pet> live = new ArrayList<>(PETS.values());
			for (AbstractClientPlayer p : world.players()) {
				if (p.isSpectator() || p.isInvisible() || p.distanceToSqr(mc.player) > maxSq) {
					continue;
				}
				UUID id = CosmeticFeatureRenderer.uuidOf(p.getId());
				CosmeticModels.Def def = petOf(id);
				PetModel model = def == null ? null : PetModel.get(def.pet());
				if (model == null) {
					continue;
				}
				Pet pet = PETS.get(p.getId());
				if (pet == null || pet.model != model) {
					pet = new Pet(model, p, p == mc.player);
					PETS.put(p.getId(), pet);
				}
				pet.tick(p, world, live);
				seen.add(p.getId());
			}
			PETS.keySet().retainAll(seen);
			interact(mc, world);
		} catch (Throwable t) {
			if (lastError == null) {
				System.err.println("[Sleep] pets: " + t);
				t.printStackTrace();
			}
			lastError = String.valueOf(t);
		}
	}

	/** Right click = pet / feed, left click = boop, on the pet under the crosshair. */
	private static void interact(Minecraft mc, ClientLevel world) {
		boolean use = mc.options.keyUse.isDown(), attack = mc.options.keyAttack.isDown();
		boolean useNow = use && !useWas, attackNow = attack && !attackWas;
		useWas = use;
		attackWas = attack;
		if (!(useNow || attackNow) || dev.solclient.compat.Mc.screen(mc) != null || PETS.isEmpty()) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition(1F);
		Vec3 end = eye.add(mc.player.getViewVector(1F).scale(4.5));
		double block = 1e9;
		HitResult hr = mc.hitResult;
		if (hr != null && hr.getType() != HitResult.Type.MISS) {
			block = hr.getLocation().distanceTo(eye);
		}
		Pet best = null;
		double bestD = 1e9;
		for (Pet p : PETS.values()) {
			double w = Math.max(0.3, p.model.lengthBlocks() * 0.5), h = Math.max(0.4, p.model.heightBlocks());
			AABB box = new AABB(p.x - w, p.y, p.z - w, p.x + w, p.y + h, p.z + w);
			Optional<Vec3> hit = box.clip(eye, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceTo(eye);
				if (d < bestD && d < block + 0.2) {
					bestD = d;
					best = p;
				}
			}
		}
		if (best == null) {
			return;
		}
		if (useNow) {
			boolean food = mc.player.getMainHandItem().has(DataComponents.FOOD);
			best.pet(world, food);
			// PatPat: every click is a pat as fast as you click, with the squish, the pat sound and hearts
			long now = System.currentTimeMillis();
			best.combo = now - best.patAt < 1500L ? best.combo + 1 : 1;
			best.patAt = now;
			float pitch = 0.9F + (float) Math.random() * 0.25F;
			world.playLocalSound(best.x, best.y + 0.4, best.z, dev.solclient.mods.PatPatMod.PAT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, pitch, false);
			if (best.combo % 3 == 0) {
				world.playLocalSound(best.x, best.y + 0.4, best.z, dev.solclient.mods.PatPatMod.HAPPY, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, pitch, false);
			}
			mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		} else {
			best.boop(world);
		}
	}

	// ================================================================ sounds
	private static final Map<String, Optional<SoundEvent>> SOUNDS = new HashMap<>();
	private static final Map<String, String> FALLBACK = Map.of("entity.wolf.ambient", "entity.fox.ambient", "entity.wolf.pant", "entity.fox.sniff",
			"entity.wolf.growl", "entity.fox.aggro");

	static SoundEvent soundEvent(String id) {
		if (id == null) {
			return null;
		}
		return SOUNDS.computeIfAbsent(id, k -> {
			Optional<SoundEvent> s = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(k));
			if (s.isEmpty() && FALLBACK.containsKey(k)) {
				s = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(FALLBACK.get(k)));
			}
			return s;
		}).orElse(null);
	}

	// ================================================================ drawing
	private static float[] poseBuf = new float[9 * 16];

	private static void render(PoseStack matrices, SubmitNodeCollector queue, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (PETS.isEmpty() || mc.level == null) {
			return;
		}
		float d = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (Pet p : PETS.values()) {
			double x = p.rx(d), y = p.ry(d), z = p.rz(d);
			if (cam.distanceToSqr(x, y, z) > 64 * 64) {
				continue;
			}
			// hide your own pet when it sits on your shoulder in first person (it would be inside the camera)
			if (p.mine && p.perched && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			PetModel m = p.model;
			boolean verity = "verity".equals(m.id);
			int light = m.glow ? LightCoordsUtil.FULL_BRIGHT
					: net.minecraft.util.LightCoordsUtil.pack(mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(x, y + Math.min(0.5, m.heightBlocks() * 0.5), z)), mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, BlockPos.containing(x, y + Math.min(0.5, m.heightBlocks() * 0.5), z)));
			if (poseBuf.length < m.bones.length * 9) {
				poseBuf = new float[m.bones.length * 9];
			}
			p.pose(d, poseBuf);
			if (!p.perched) {
				shadow(matrices, queue, cam, x, p.groundY, y, z, m, light);
			}
			if (verity) {
				// a simple ball pet: always the happy face, normal size, rolls along
				float roll = p.prevWalkPhase + (p.walkPhase - p.prevWalkPhase) * d;
				VeritySphere.draw(matrices, queue, cam, x, y, z, 13, roll, p.renderYaw(d), 1F, 1F, 1F, light);
				continue;
			}
			matrices.pushPose();
			matrices.translate(x - cam.x, y - cam.y, z - cam.z);
			matrices.mulPose(Axis.YP.rotationDegrees(180F - p.renderYaw(d)));
			float s = m.scale / 16F;
			float tilt = p.renderTilt(d), bank = p.renderBank(d);
			if (tilt != 0 || bank != 0) {
				// leans into turns and dives around its middle
				float mid = m.heightBlocks() * 0.5F;
				matrices.translate(0, mid, 0);
				matrices.mulPose(Axis.XP.rotationDegrees(-tilt));
				matrices.mulPose(Axis.ZP.rotationDegrees(bank));
				matrices.translate(0, -mid, 0);
			}
			float pk = (System.currentTimeMillis() - p.patAt) / 260F;
			if (pk >= 0F && pk < 1F) {
				// squished under the hand, then it springs back
				float sq = (float) Math.sin(Math.PI * pk);
				matrices.scale(1F + 0.10F * sq, 1F - 0.22F * sq, 1F + 0.10F * sq);
			}
			matrices.scale(s, s, s);
			RenderType layer = RenderTypes.entityCutout(verity
					? dev.solclient.Gfx.id("textures/cosmetics/verity_f13.png") : m.texture);
			drawBones(m, poseBuf, matrices, (bone) -> queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(bone, entry, vc, light)));
			matrices.popPose();
		}
	}

	/** Position of a player's live pet (blocks), or null. Key: owner entity id. */
	public static double[] petPos(int ownerId) {
		Pet p = PETS.get(ownerId);
		return p == null ? null : new double[] {p.x, p.y + p.model.heightBlocks() * 0.5, p.z};
	}

	/** Soft round shadow on the ground, fainter the higher the pet is. */
	private static void shadow(PoseStack matrices, SubmitNodeCollector queue, Vec3 cam, double x, double gy, double y, double z, PetModel m,
			int light) {
		double up = y - gy;
		if (up > 4 || up < -0.5) {
			return;
		}
		boolean ball = "verity".equals(m.id);
		float a = (float) Math.max(0, (ball ? 0.3 : 0.55) * (1 - up / 4));
		float r = Math.max(ball ? 0.1F : 0.25F, m.lengthBlocks() * (ball ? 0.5F : 0.45F)) * (float) (1 - up / 8);
		int alpha = (int) (a * 255);
		matrices.pushPose();
		matrices.translate(x - cam.x, gy + 0.015 - cam.y, z - cam.z);
		queue.submitCustomGeometry(matrices, RenderTypes.entityShadow(SHADOW), (entry, vc) -> {
			int col = (alpha << 24) | 0xFFFFFF;
			vc.addVertex(entry, -r, 0, -r).setColor(col).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0, 1, 0);
			vc.addVertex(entry, -r, 0, r).setColor(col).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0, 1, 0);
			vc.addVertex(entry, r, 0, r).setColor(col).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0, 1, 0);
			vc.addVertex(entry, r, 0, -r).setColor(col).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0, 1, 0);
		});
		matrices.popPose();
	}

	public interface BoneSink {
		/** Called with the matrices already moved to the bone. */
		void bone(PetModel.Bone bone);
	}

	/** Walks the bones with their pose applied (parents first) and hands each one to 'sink'. Units: model pixels. */
	public static void drawBones(PetModel m, float[] pose, PoseStack matrices, BoneSink sink) {
		for (int i = 0; i < m.bones.length; i++) {
			if (m.bones[i].verts.length == 0) {
				continue;
			}
			matrices.pushPose();
			apply(m, pose, i, matrices, 0);
			sink.bone(m.bones[i]);
			matrices.popPose();
		}
	}

	private static void apply(PetModel m, float[] pose, int i, PoseStack ms, int depth) {
		PetModel.Bone b = m.bones[i];
		if (b.parent >= 0 && b.parent != i && depth < 8) {
			apply(m, pose, b.parent, ms, depth + 1);
		}
		int o = i * 9;
		ms.translate(b.px + pose[o + 3], b.py + pose[o + 4], b.pz + pose[o + 5]);
		if (pose[o] != 0 || pose[o + 1] != 0 || pose[o + 2] != 0) {
			ms.mulPose(new Quaternionf().rotationZYX(pose[o + 2], pose[o + 1], pose[o]));
		}
		if (pose[o + 6] != 1 || pose[o + 7] != 1 || pose[o + 8] != 1) {
			ms.scale(pose[o + 6], pose[o + 7], pose[o + 8]);
		}
		ms.translate(-b.px, -b.py, -b.pz);
	}

	public static void emit(PetModel.Bone b, PoseStack.Pose entry, VertexConsumer vc, int light) {
		float[] v = b.verts, n = b.normals;
		int overlay = OverlayTexture.NO_OVERLAY;
		int quads = v.length / 20;
		for (int q = 0; q < quads; q++) {
			int o = q * 20;
			float nx = n[q * 3], ny = n[q * 3 + 1], nz = n[q * 3 + 2];
			for (int k = 0; k < 4; k++) {
				int i = o + k * 5;
				vc.addVertex(entry, v[i], v[i + 1], v[i + 2]).setColor(0xFFFFFFFF).setUv(v[i + 3], v[i + 4]).setOverlay(overlay).setLight(light)
						.setNormal(entry, nx, ny, nz);
			}
		}
	}

	// ================================================================ menus
	private static final float[] PREVIEW = new float[9 * 32];

	/**
	 * Draws the pet in a menu, standing on the ground next to the player (matrices = player model space, y down, pixels
	 * after the caller's 1/16 scale is NOT applied: blocks). 'sink' emits each bone with the caller's buffer.
	 */
	public static void drawPreview(CosmeticModels.Def def, PoseStack matrices, BoneSink sink) {
		PetModel m = PetModel.get(def.pet());
		if (m == null || m.bones.length * 9 > PREVIEW.length) {
			return;
		}
		float t = System.currentTimeMillis() / 50F;
		Pet.previewPose(m, t, PREVIEW);
		boolean fly = m.kind == PetModel.Kind.FLY || m.kind == PetModel.Kind.FLOAT;
		// next to the player's right foot (flying pets at shoulder height), turned a little towards the viewer
		float bob = fly ? (float) Math.sin(t * 0.09F) * 0.06F : 0F;
		matrices.translate(-0.95F, fly ? 0.15F + bob : 1.5F, 0.1F);
		matrices.mulPose(Axis.ZP.rotationDegrees(180F)); // player model space is y down
		matrices.mulPose(Axis.YP.rotationDegrees(-25F));
		float s = Math.min(m.scale, 13F / Math.max(1F, m.sizeY)) / 16F;
		matrices.scale(s, s, s);
		drawBones(m, PREVIEW, matrices, sink);
	}
}
