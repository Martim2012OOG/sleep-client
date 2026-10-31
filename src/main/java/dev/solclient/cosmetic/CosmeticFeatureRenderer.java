package dev.solclient.cosmetic;

import dev.solclient.SolClient;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the 3D cosmetics a player wears (hats, wings, robe, pets): each piece follows its body part
 * (head turns with the head, sleeves swing with the arms), wings flap, pets float next to your shoulder.
 */
public class CosmeticFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

	/** Diagnostics (Settings > Players > 3D cosmetics status). */
	public static volatile int layers;
	public static volatile long lastDrawn;
	public static volatile String lastError;

	public CosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
		layers++;
	}

	/** One line for the settings: is the layer there, what you wear, last error. */
	public static String status() {
		java.util.UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
		List<CosmeticModels.Def> worn = me == null ? List.of() : Cosmetics.worn(me);
		StringBuilder sb = new StringBuilder();
		sb.append(layers > 0 ? "Layer OK" : "Layer NOT added (send me the log)");
		sb.append(" | wearing: ").append(worn.isEmpty() ? "nothing" : String.join(", ", worn.stream().map(CosmeticModels.Def::name).toList()));
		if (lastDrawn > 0) {
			sb.append(" | drawn ").append((System.currentTimeMillis() - lastDrawn) / 1000).append("s ago");
		}
		if (lastError != null) {
			sb.append(" | error: ").append(lastError);
		}
		return sb.toString();
	}

	/** The account a rendered player belongs to (you: your session; others: only Sleep users), or null. */
	public static UUID uuidOf(int entityId) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return null;
		}
		Entity e = mc.world.getEntityById(entityId);
		if (e != null && e == mc.player) {
			// you: always your account (on offline-mode servers your in-game UUID is a different one)
			UUID s = mc.getSession().getUuidOrNull();
			return s != null ? s : e.getUuid();
		}
		// other players: their account uuid when presence can bridge it by nick, else the in-game uuid
		// other players: only when they use the client (the logo shows in the tab list), else nothing is drawn on them
		return e instanceof PlayerEntity p && dev.solclient.social.SolarBadge.usesClient(p) ? dev.solclient.social.Presence.backendId(p) : null;
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
		try {
			if (state.invisible || SolClient.settings == null || !SolClient.settings.cosmetics.on()
					|| state.squaredDistanceToCamera > dev.solclient.mods.PerformanceMod.cosmeticDistanceSq()) {
				return;
			}
			UUID id = uuidOf(state.id);
			if (id == null) {
				return;
			}
			List<CosmeticModels.Def> worn = Cosmetics.worn(id);
			if (worn.isEmpty()) {
				return;
			}
			PlayerEntityModel model = getContextModel();
			for (CosmeticModels.Def d : worn) {
				if (d.livePet()) {
					// live pets walk around in the world (Pets); in a menu preview they stand next to you
					if (Cosmetics.inGui) {
						RenderLayer petLayer = RenderLayers.entityCutoutNoCull(dev.solclient.cosmetic.pet.PetModel.get(d.pet()) != null
								? dev.solclient.cosmetic.pet.PetModel.get(d.pet()).texture : d.textureId());
						final int pl = light;
						matrices.push();
						dev.solclient.cosmetic.pet.Pets.drawPreview(d, matrices,
								bone -> queue.submitCustom(matrices, petLayer, (entry, vc) -> dev.solclient.cosmetic.pet.Pets.emit(bone, entry, vc, pl)));
						matrices.pop();
						lastDrawn = System.currentTimeMillis();
					}
					continue;
				}
				RenderLayer layer = RenderLayers.entityCutoutNoCull(d.textureId());
				for (CosmeticModels.Part part : CosmeticModels.geometry(d)) {
					matrices.push();
					boolean air = state.glidingTicks > 0 || state.isGliding;
					if (!place(matrices, model, part.attach(), state.age, air)) {
						matrices.pop();
						continue;
					}
					int[] off = Offsets.get(d);
					if (off[0] != 0 || off[1] != 0) {
						// model space: y points down, z points to the front of the player
						matrices.translate(0F, -off[0] / 16F, off[1] / 16F);
					}
					matrices.scale(1F / 16F, 1F / 16F, 1F / 16F);
					final CosmeticModels.Part p = part;
					final int l = light;
					queue.submitCustom(matrices, layer, (entry, vc) -> emit(p, entry, vc, l));
					lastDrawn = System.currentTimeMillis();
					matrices.pop();
				}
			}
		} catch (Throwable t) {
			// never break player rendering because of a cosmetic (but say why, once)
			if (lastError == null) {
				System.err.println("[Sleep] cosmetics: " + t);
				t.printStackTrace();
			}
			lastError = t.toString();
		}
	}

	/** Moves the matrices to the body part a piece follows (+ wing flapping / pet floating). */
	public static boolean place(MatrixStack m, PlayerEntityModel model, int attach, float t, boolean air) {
		ModelPart part = switch (attach) {
			case CosmeticModels.HEAD -> model.head;
			case CosmeticModels.RIGHT_ARM -> model.rightArm;
			case CosmeticModels.LEFT_ARM -> model.leftArm;
			case CosmeticModels.RIGHT_LEG -> model.rightLeg;
			case CosmeticModels.LEFT_LEG -> model.leftLeg;
			default -> model.body;
		};
		if (part == null || (attach == CosmeticModels.HEAD && !part.visible)) {
			return false;
		}
		part.applyTransform(m);
		if (attach == CosmeticModels.WING_LEFT || attach == CosmeticModels.WING_RIGHT) {
			// calm, slow flap that only opens OUTWARDS/back (never folds in over the body); bigger while gliding
			float speed = air ? 0.22F : 0.045F, amp = air ? 22F : 7F;
			float wave = ((float) Math.sin(t * speed) + 1F) / 2F; // 0..1
			float a = -(4F + wave * amp); // negative = away from the body
			m.translate(0F, 0F, 2F / 16F);
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(attach == CosmeticModels.WING_LEFT ? -a : a));
			m.translate(0F, 0F, -2F / 16F);
			m.translate(0F, 0F, 1F / 16F); // a little further behind the back
		} else if (attach == CosmeticModels.PET) {
			float bob = (float) Math.sin(t * 0.08F) * 1.2F;
			m.translate(-13F / 16F, (-1F + bob) / 16F, 1F / 16F);
		}
		return true;
	}

	public static void emit(CosmeticModels.Part p, MatrixStack.Entry entry, VertexConsumer vc, int light) {
		float[] v = p.verts(), n = p.normals();
		int tris = p.triangles();
		int overlay = OverlayTexture.DEFAULT_UV;
		for (int t = 0; t < tris; t++) {
			int o = t * 15;
			float nx = n[t * 3], ny = n[t * 3 + 1], nz = n[t * 3 + 2];
			// entity layers draw quads: a triangle is a quad with its last corner repeated
			for (int k = 0; k < 4; k++) {
				int i = o + Math.min(k, 2) * 5;
				vc.vertex(entry, v[i], v[i + 1], v[i + 2]).color(0xFFFFFFFF).texture(v[i + 3], v[i + 4]).overlay(overlay).light(light)
						.normal(entry, nx, ny, nz);
			}
		}
	}
}
