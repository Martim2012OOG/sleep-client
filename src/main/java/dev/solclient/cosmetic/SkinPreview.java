package dev.solclient.cosmetic;

import dev.solclient.social.RemoteTextures;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * A real 3D player (Minecraft's own player model, like the skin widget in the options) that works without a world:
 * the home screen skin and the Locker preview outside the game. It gently moves the arms (idle animation) and
 * draws the 3D cosmetics on it with a depth buffer.
 */
public final class SkinPreview {

	/** Set while Minecraft renders a player-skin element in a menu (see PlayerSkinRendererMixin). */
	public static volatile VertexConsumerProvider provider;
	private static Model wide, slim;
	private static boolean broken;

	private SkinPreview() {
	}

	static final class Model extends PlayerEntityModel {
		List<CosmeticModels.Def> wear = List.of();

		Model(ModelPart root, boolean slim) {
			super(root, slim);
		}

	}

	private static net.minecraft.util.Identifier greySkin;

	/** A plain grey player skin (made here, no image from anywhere): lighter body, darker head, like a mannequin. */
	private static net.minecraft.util.Identifier greySkin() {
		if (greySkin == null) {
			net.minecraft.client.texture.NativeImage img = new net.minecraft.client.texture.NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int g = 0x8C;
					if (y < 16 && x < 32) {
						g = 0x6A; // head
					} else if ((x >= 16 && x < 40 && y >= 16 && y < 32)) {
						g = 0xA0; // body
					} else if (y >= 16 && (x < 16 || x >= 32 || y >= 48)) {
						g = 0x84; // limbs
					}
					// a little shade at the edges of every 8 px patch so the cubes read
					int shade = ((x % 8 == 0) || (y % 8 == 0)) ? -10 : 0;
					int v = Math.max(0, g + shade);
					img.setColorArgb(x, y, 0xFF000000 | v << 16 | v << 8 | v);
				}
			}
			// face: two dark eyes on the head front (8..16, 8..16)
			for (int ey = 11; ey < 13; ey++) {
				img.setColorArgb(10, ey, 0xFF2A2A2E);
				img.setColorArgb(13, ey, 0xFF2A2A2E);
			}
			net.minecraft.util.Identifier id = dev.solclient.Gfx.id("textures/solar/grey_mannequin.png");
			net.minecraft.client.texture.NativeImageBackedTexture tex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "kubo_grey_mannequin", img);
			tex.upload();
			net.minecraft.client.MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			greySkin = id;
		}
		return greySkin;
	}

	/** One model per emote card: the screen draws later in the frame, so cards must not share a model (they would all show the last pose). */
	private static final java.util.LinkedHashMap<String, Model> CARD_MODELS = new java.util.LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, Model> e) {
			return size() > 48;
		}
	};

	private static Model cardModel(String id) {
		return CARD_MODELS.computeIfAbsent(id, k -> new Model(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel(), false));
	}

	private static long cardHoverStart;
	private static dev.solclient.emote.EmoteLibrary.Info cardHover;

	/**
	 * A grey mannequin doing this emote, for the Locker cards: still on its most telling pose, playing while the mouse is over it.
	 * Draws inside x..x+w / y..y+h (GUI units). Returns false when it can't (the caller draws a plain tile).
	 */
	public static boolean drawEmoteCard(DrawContext c, dev.solclient.emote.EmoteLibrary.Info info, boolean play, int x, int y, int w, int h) {
		if (broken) {
			return false;
		}
		try {
			Model m = cardModel(info.id());
			m.wear = List.of();
			m.head.resetTransform();
			m.body.resetTransform();
			m.rightArm.resetTransform();
			m.leftArm.resetTransform();
			m.rightLeg.resetTransform();
			m.leftLeg.resetTransform();
			dev.solclient.emote.EmoteRig.resetBends(m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			dev.solclient.emote.Animation anim = dev.solclient.emote.EmoteLibrary.animation(info);
			if (anim != null) {
				float len = Math.max(1, anim.lengthTicks());
				float ticks;
				if (play) {
					if (cardHover != info) {
						cardHover = info;
						cardHoverStart = System.currentTimeMillis();
					}
					ticks = (System.currentTimeMillis() - cardHoverStart) / 50F % (len + 12F);
				} else {
					if (cardHover == info) {
						cardHover = null;
					}
					ticks = len * (anim.loop ? 0.3F : 0.4F); // a pose that tells the emote apart from standing
				}
				dev.solclient.emote.EmoteRig.apply(anim, anim.animTime(Math.min(ticks, len)), 1F, m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			}
			float perBlock = (h - 6) / 2.25F;
			float cx = x + w / 2F, feetY = y + h - 3;
			float centerY = feetY - 1.0625F * perBlock;
			int half = Math.round(perBlock * 1.6F);
			c.addPlayerSkin(m, greySkin(), perBlock, -4F, -28F, -1.0625F, Math.round(cx - half), Math.round(centerY - half), Math.round(cx + half),
					Math.round(centerY + half));
			return true;
		} catch (Throwable e) {
			broken = true;
			System.err.println("[Sleep] emote card preview unavailable: " + e);
			return false;
		}
	}

	/** Called right after Minecraft drew a skin-preview model (PlayerSkinRendererMixin): adds the cosmetics. */
	public static void afterModel(Object model, MatrixStack matrices, int light) {
		if (!(model instanceof Model m)) {
			return;
		}
		VertexConsumerProvider p = provider;
		if (p == null || m.wear.isEmpty()) {
			return;
		}
		try {
			float age = System.currentTimeMillis() / 50F;
			for (CosmeticModels.Def d : m.wear) {
				if (d.livePet()) {
					dev.solclient.cosmetic.pet.PetModel pm = dev.solclient.cosmetic.pet.PetModel.get(d.pet());
					if (pm != null) {
						VertexConsumer pvc = p.getBuffer(RenderLayers.entityCutoutNoCull(pm.texture));
						matrices.push();
						dev.solclient.cosmetic.pet.Pets.drawPreview(d, matrices, bone -> dev.solclient.cosmetic.pet.Pets.emit(bone, matrices.peek(), pvc, light));
						matrices.pop();
					}
					continue;
				}
				VertexConsumer vc = p.getBuffer(RenderLayers.entityCutoutNoCull(d.textureId()));
				for (CosmeticModels.Part part : CosmeticModels.geometry(d)) {
					matrices.push();
					if (CosmeticFeatureRenderer.place(matrices, m, part.attach(), age, false)) {
						matrices.scale(1F / 16F, 1F / 16F, 1F / 16F);
						CosmeticFeatureRenderer.emit(part, matrices.peek(), vc, light);
					}
					matrices.pop();
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static Model model(boolean isSlim) {
		if (isSlim) {
			if (slim == null) {
				slim = new Model(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, true), 64, 64).createModel(), true);
			}
			return slim;
		}
		if (wide == null) {
			wide = new Model(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel(), false);
		}
		return wide;
	}

	/**
	 * Draws the player with its feet at y = feetY and 'px' screen units per skin pixel, centred on cx (screen GUI units),
	 * turned by yaw degrees and tilted by pitch. Returns false if it can't (then draw the old preview).
	 */
	public static boolean draw(DrawContext c, RemoteTextures.Tex skin, boolean isSlim, float cx, float feetY, float px, float yaw, float pitch,
			List<CosmeticModels.Def> wear) {
		if (broken || skin == null) {
			return false;
		}
		try {
			Model m = model(isSlim);
			m.wear = wear == null ? List.of() : wear;
			// the model is shared: start from the standing pose every frame
			m.head.resetTransform();
			m.body.resetTransform();
			m.rightArm.resetTransform();
			m.leftArm.resetTransform();
			m.rightLeg.resetTransform();
			m.leftLeg.resetTransform();
			dev.solclient.emote.EmoteRig.resetBends(m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			dev.solclient.emote.EmoteManager.Remote pr = dev.solclient.emote.EmoteManager.previewRemote();
			// idle arms, like Minecraft's standing animation
			float t = System.currentTimeMillis() / 50F;
			float roll = (float) Math.cos(t * 0.09F) * 0.05F + 0.05F, pitchA = (float) Math.sin(t * 0.067F) * 0.05F;
			m.rightArm.roll = roll;
			m.leftArm.roll = -roll;
			m.rightArm.pitch = pitchA;
			m.leftArm.pitch = -pitchA;
			if (pr != null) { // the Locker shows the selected emote
				dev.solclient.emote.EmoteManager.applyPreview(pr, m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			}
			float perBlock = px * 16F;
			// the model is centred on the point 1.0625 blocks above the feet
			float centerY = feetY - 1.0625F * perBlock;
			int half = Math.round(perBlock * 1.6F);
			int x1 = Math.round(cx - half), x2 = Math.round(cx + half);
			int y1 = Math.round(centerY - half), y2 = Math.round(centerY + half);
			c.addPlayerSkin(m, skin.id(), perBlock, pitch, yaw, -1.0625F, x1, y1, x2, y2);
			return true;
		} catch (Throwable e) {
			broken = true;
			System.err.println("[Sleep] skin preview unavailable: " + e);
			return false;
		}
	}
}
