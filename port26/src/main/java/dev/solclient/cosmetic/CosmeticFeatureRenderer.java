package dev.solclient.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.solclient.SolClient;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Draws the 3D cosmetics a player wears (hats, wings, robe, pets): each piece follows its body part
 * (head turns with the head, sleeves swing with the arms), wings flap, pets float next to your shoulder.
 */
public class CosmeticFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

	/** Diagnostics (Settings > Players > 3D cosmetics status). */
	public static volatile int layers;
	public static volatile long lastDrawn;
	public static volatile String lastError;

	/** Slim (3 pixel) arms: the minime copies it. */
	private final boolean slim;

	public CosmeticFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
		this(context, false);
	}

	public CosmeticFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context, boolean slim) {
		super(context);
		this.slim = slim;
		layers++;
	}

	/** One line for the settings: is the layer there, what you wear, last error. */
	public static String status() {
		java.util.UUID me = Minecraft.getInstance().getUser().getProfileId();
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

	/** The account a rendered player belongs to (you: your session; others: only Caos users), or null. */
	public static UUID uuidOf(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(entityId);
		if (e != null && e == mc.player) {
			// you: always your account (on offline-mode servers your in-game UUID is a different one)
			UUID s = mc.getUser().getProfileId();
			return s != null ? s : e.getUUID();
		}
		// other players: their account uuid when presence can bridge it by nick, else the in-game uuid
		// other players: only when they use the client (the logo shows in the tab list), else nothing is drawn on them
		return e instanceof Player p && dev.solclient.social.SolarBadge.usesClient(p) ? dev.solclient.social.Presence.backendId(p) : null;
	}

	@Override
	public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
		try {
			if (state.isInvisible || SolClient.settings == null || !SolClient.settings.cosmetics.on()
					|| state.distanceToCameraSq > dev.solclient.mods.PerformanceMod.cosmeticDistanceSq()) {
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
			PlayerModel model = getParentModel();
			for (CosmeticModels.Def d : worn) {
				if (d.livePet()) {
					// live pets walk around in the world (Pets); in a menu preview they stand next to you
					if (Cosmetics.inGui) {
						RenderType petLayer = RenderTypes.entityCutout(dev.solclient.cosmetic.pet.PetModel.get(d.pet()) != null
								? dev.solclient.cosmetic.pet.PetModel.get(d.pet()).texture : d.textureId());
						final int pl = light;
						matrices.pushPose();
						dev.solclient.cosmetic.pet.Pets.drawPreview(d, matrices,
								bone -> queue.submitCustomGeometry(matrices, petLayer, (entry, vc) -> dev.solclient.cosmetic.pet.Pets.emit(bone, entry, vc, pl)));
						matrices.popPose();
						lastDrawn = System.currentTimeMillis();
					}
					continue;
				}
				if (Minimes.is(d)) {
					// a small copy of this player, with their skin and their pose
					net.minecraft.resources.Identifier skinId = state.skin.body().texturePath();
					RenderType skinLayer = RenderTypes.entityCutout(skinId);
					Minimes.draw(d, model, slim, matrices, state.ageInTicks, light, (ms, e) -> queue.submitCustomGeometry(ms, skinLayer, e::emit));
					lastDrawn = System.currentTimeMillis();
					continue;
				}
				RenderType layer = RenderTypes.entityCutout(d.textureId());
				if (Auras.is(d)) {
					// sprites around the whole player (not tied to a body part); half of them when the player is not close
					final CosmeticModels.Def ad = d;
					final int al = light;
					final float age = state.ageInTicks;
					final boolean few = state.distanceToCameraSq > 24.0 * 24.0;
					queue.submitCustomGeometry(matrices, layer, (entry, vc) -> Auras.emit(ad, entry, vc, al, age, few));
					lastDrawn = System.currentTimeMillis();
					continue;
				}
				for (CosmeticModels.Part part : CosmeticModels.geometry(d)) {
					matrices.pushPose();
					boolean air = state.fallFlyingTimeInTicks > 0 || state.isFallFlying;
					if (!place(matrices, model, part.attach(), state.ageInTicks, air, elytraSpread(state))) {
						matrices.popPose();
						continue;
					}
					int[] off = Offsets.get(d, id);
					if (off[0] != 0 || off[1] != 0 || off[2] != 0) {
						// model space: y points down, z points to the front of the player, x to the player's left
						matrices.translate(off[2] / 16F, -off[0] / 16F, off[1] / 16F);
					}
					if (off[3] != 0) {
						matrices.mulPose(Axis.YP.rotationDegrees(off[3]));
					}
					matrices.scale(1F / 16F, 1F / 16F, 1F / 16F);
					final CosmeticModels.Part p = part;
					final int l = light;
					final int tint = off[4] == 0 ? 0xFFFFFFFF : off[4] | 0xFF000000;
					queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(p, entry, vc, l, tint));
					lastDrawn = System.currentTimeMillis();
					matrices.popPose();
				}
			}
		} catch (Throwable t) {
			// never break player rendering because of a cosmetic (but say why, once)
			if (lastError == null) {
				System.err.println("[Caos] cosmetics: " + t);
				t.printStackTrace();
			}
			lastError = t.toString();
		}
	}

	/** True when this player wears 3D wings: they take the place of the elytra (the vanilla elytra is not drawn). */
	public static boolean wingsReplaceElytra(Object renderState) {
		try {
			if (!(renderState instanceof AvatarRenderState state) || state.isInvisible || SolClient.settings == null || !SolClient.settings.cosmetics.on()) {
				return false;
			}
			UUID id = uuidOf(state.id);
			if (id == null) {
				return false;
			}
			for (CosmeticModels.Def d : Cosmetics.worn(id)) {
				if (d.livePet()) {
					continue;
				}
				if (Auras.is(d) || Minimes.is(d)) {
					continue;
				}
				for (CosmeticModels.Part part : CosmeticModels.geometry(d)) {
					if (part.attach() == CosmeticModels.WING_LEFT || part.attach() == CosmeticModels.WING_RIGHT) {
						return true;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/**
	 * How open the wings are when they act as the elytra: -1 = not wearing an elytra (they flap on their own), 0 = folded on the back
	 * (standing), up to 1 = fully spread (gliding).
	 */
	private static float elytraSpread(AvatarRenderState state) {
		net.minecraft.world.item.ItemStack chest = state.chestEquipment;
		boolean worn = chest != null && !chest.isEmpty() && (chest.is(net.minecraft.world.item.Items.ELYTRA)
				|| chest.has(net.minecraft.core.component.DataComponents.GLIDER));
		if (!worn && !state.isFallFlying) {
			return -1F;
		}
		if (state.isFallFlying) {
			float open = Math.min(1F, Math.max(0.25F, state.fallFlyingTimeInTicks / 8F));
			return open;
		}
		return state.isCrouching ? 0.12F : 0F;
	}

	/** Moves the matrices to the body part a piece follows (+ wing flapping / pet floating). */
	public static boolean place(PoseStack m, PlayerModel model, int attach, float t, boolean air) {
		return place(m, model, attach, t, air, -1F);
	}

	/** Same, with the wings posed like an elytra when {@code spread} is 0..1 (see {@link #elytraSpread}). */
	public static boolean place(PoseStack m, PlayerModel model, int attach, float t, boolean air, float spread) {
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
		part.translateAndRotate(m);
		if (attach == CosmeticModels.WING_LEFT || attach == CosmeticModels.WING_RIGHT) {
			// calm, slow flap that only opens OUTWARDS/back (never folds in over the body); bigger while gliding
			float speed = air ? 0.22F : 0.045F, amp = air ? 22F : 7F;
			float wave = ((float) Math.sin(t * speed) + 1F) / 2F; // 0..1
			float a = -(4F + wave * amp); // negative = away from the body
			if (spread >= 0F) {
				// elytra mode: folded and still on the back, opening wide while gliding (a little life in the air)
				a = -(5F + spread * (58F + (air ? wave * 6F : 0F)));
			}
			m.translate(0F, 0F, 2F / 16F);
			m.mulPose(Axis.YP.rotationDegrees(attach == CosmeticModels.WING_LEFT ? -a : a));
			m.translate(0F, 0F, -2F / 16F);
			m.translate(0F, 0F, 1F / 16F); // a little further behind the back
		} else if (attach == CosmeticModels.PET) {
			float bob = (float) Math.sin(t * 0.08F) * 1.2F;
			m.translate(-13F / 16F, (-1F + bob) / 16F, 1F / 16F);
		}
		return true;
	}

	public static void emit(CosmeticModels.Part p, PoseStack.Pose entry, VertexConsumer vc, int light) {
		emit(p, entry, vc, light, 0xFFFFFFFF);
	}

	public static void emit(CosmeticModels.Part p, PoseStack.Pose entry, VertexConsumer vc, int light, int tint) {
		float[] v = p.verts(), n = p.normals();
		int tris = p.triangles();
		int overlay = OverlayTexture.NO_OVERLAY;
		for (int t = 0; t < tris; t++) {
			int o = t * 15;
			float nx = n[t * 3], ny = n[t * 3 + 1], nz = n[t * 3 + 2];
			// entity layers draw quads: a triangle is a quad with its last corner repeated
			for (int k = 0; k < 4; k++) {
				int i = o + Math.min(k, 2) * 5;
				vc.addVertex(entry, v[i], v[i + 1], v[i + 2]).setColor(tint).setUv(v[i + 3], v[i + 4]).setOverlay(overlay).setLight(light)
						.setNormal(entry, nx, ny, nz);
			}
		}
	}
}
