package dev.sleepclient.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleepclient.social.RemoteTextures;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * A real 3D player (Minecraft's own player model, like the skin widget in the options) that works without a world:
 * the home screen skin and the Locker preview outside the game. It gently moves the arms (idle animation) and
 * draws the 3D cosmetics on it with a depth buffer.
 */
public final class SkinPreview {

	/** Set while Minecraft renders a player-skin element in a menu (see PlayerSkinRendererMixin). */
	public static volatile MultiBufferSource provider;
	private static Model wide, slim;
	private static boolean broken;

	private SkinPreview() {
	}

	static final class Model extends PlayerModel {
		List<CosmeticModels.Def> wear = List.of();
		/** The skin this model is drawn with right now and its arm type (the minime copies them). */
		net.minecraft.resources.Identifier skin;
		/** A kill effect shown on this card model (null = none) and when it started. */
		String kill;
		long killStart;
		boolean killStill;
		final boolean slimArms;

		Model(ModelPart root, boolean slim) {
			super(root, slim);
			this.slimArms = slim;
		}

	}

	private static net.minecraft.resources.Identifier greySkin;

	/** A grey Minecraft skin (made here, no image from anywhere): dark hair, light face, grey shirt, darker trousers and shoes. */
	private static net.minecraft.resources.Identifier greySkin() {
		if (greySkin == null) {
			com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int g = 0x9C; // skin
					boolean head = y < 16 && x < 32, body = x >= 16 && x < 40 && y >= 16 && y < 32;
					boolean rArm = x >= 40 && x < 56 && y >= 16 && y < 32, lArm = x >= 32 && x < 48 && y >= 48 && y < 64;
					boolean rLeg = x < 16 && y >= 16 && y < 32, lLeg = x >= 16 && x < 32 && y >= 48 && y < 64;
					if (head) {
						boolean top = y < 8 && x >= 8 && x < 16;
						boolean hairRow = y >= 8 && y < 10; // fringe + hair on the sides and back
						boolean sides = (x < 8 || x >= 16) && y >= 8;
						g = top || hairRow || sides ? 0x4A4A52 : 0xB4B4BC;
						if (x >= 24 && y >= 8) {
							g = 0x4A4A52; // back of the head
						}
					} else if (body) {
						g = 0xBCBEC8;
					} else if (rArm || lArm) {
						int row = rArm ? y - 16 : y - 48;
						g = row >= 4 && row < 8 || row < 4 ? 0xBCBEC8 : 0xB4B4BC; // sleeve, then hand
						if (row < 4) {
							g = 0xBCBEC8;
						}
					} else if (rLeg || lLeg) {
						int row = rLeg ? y - 16 : y - 48;
						g = row >= 9 ? 0x3A3A42 : 0x5C5E6C; // shoes, trousers
					}
					int noise = ((x * 7 + y * 13) % 5) - 2;
					int v = Math.max(0, Math.min(255, g + noise * 3));
					int tint = head && g == 0x4A4A52 ? 0 : 0;
					img.setPixel(x, y, 0xFF000000 | v << 16 | v << 8 | Math.min(255, v + tint + 4));
				}
			}
			// face on the head front (8..16, 8..16): eyes with a white, mouth
			for (int ex : new int[]{9, 13}) {
				img.setPixel(ex, 11, 0xFFE8E8F0);
				img.setPixel(ex + 1, 11, 0xFF22222A);
				img.setPixel(ex, 12, 0xFF2E2E36);
				img.setPixel(ex + 1, 12, 0xFF22222A);
			}
			for (int mx = 11; mx < 13; mx++) {
				img.setPixel(mx, 14, 0xFF6A6A74);
			}
			net.minecraft.resources.Identifier id = dev.sleepclient.Gfx.id("textures/solar/grey_mannequin.png");
			net.minecraft.client.renderer.texture.DynamicTexture tex = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "kubo_grey_mannequin", img);
			tex.upload();
			net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, tex);
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

	private static Model cardModel(String id, boolean slimArms) {
		return CARD_MODELS.computeIfAbsent(id + (slimArms ? "|s" : "|w"),
				k -> new Model(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slimArms), 64, 64).bakeRoot(), slimArms));
	}

	private static long cardHoverStart;
	private static dev.sleepclient.emote.EmoteLibrary.Info cardHover;

	/**
	 * A grey mannequin doing this emote, for the Locker cards: still on its most telling pose, playing while the mouse is over it.
	 * Draws inside x..x+w / y..y+h (GUI units). Returns false when it can't (the caller draws a plain tile).
	 */
	public static boolean drawEmoteCard(GuiGraphicsExtractor c, dev.sleepclient.emote.EmoteLibrary.Info info, boolean play, int x, int y, int w, int h) {
		if (broken) {
			return false;
		}
		try {
			Model m = cardModel(info.id(), false);
			m.wear = List.of();
			m.head.resetPose();
			m.body.resetPose();
			m.rightArm.resetPose();
			m.leftArm.resetPose();
			m.rightLeg.resetPose();
			m.leftLeg.resetPose();
			dev.sleepclient.emote.EmoteRig.resetBends(m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			dev.sleepclient.emote.Animation anim = dev.sleepclient.emote.EmoteLibrary.animation(info);
			if (anim != null) {
				// very long emotes (hold a pose for minutes, rise slowly) are only shown for their first seconds
				float len = Math.max(1, Math.min(anim.lengthTicks(), 120));
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
					ticks = Math.min(len * (anim.loop ? 0.3F : 0.4F), 40F); // a pose that tells the emote apart from standing
				}
				dev.sleepclient.emote.EmoteRig.apply(anim, anim.animTime(Math.min(ticks, len)), 1F, m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			}
			float perBlock = (h - 6) / 2.7F; // leaves room for jumps and flips
			float cx = x + w / 2F, feetY = y + h - 6;
			float centerY = feetY - 1.0625F * perBlock;
			int half = Math.round(perBlock * 1.6F);
			c.skin(m, greySkin(), perBlock, -4F, -28F, -1.0625F, Math.round(cx - half), Math.round(centerY - half), Math.round(cx + half),
					Math.round(centerY + half));
			return true;
		} catch (Throwable e) {
			broken = true;
			System.err.println("[Sleep] emote card preview unavailable: " + e);
			return false;
		}
	}

	/**
	 * Your skin standing in a card, wearing 'wear' (an aura, nothing...). Every card has its OWN model: the menu draws later in the frame,
	 * so cards that shared one model (or the global preview) all showed whatever was set last.
	 */
	public static boolean drawWearCard(GuiGraphicsExtractor c, String key, net.minecraft.resources.Identifier skin, boolean slim, List<CosmeticModels.Def> wear, int x, int y,
			int w, int h) {
		if (broken || skin == null) {
			return false;
		}
		try {
			Model m = cardModel("wear:" + key, slim);
			m.wear = wear == null ? List.of() : wear;
			m.skin = skin;
			m.kill = null;
			m.head.visible = true;
			m.body.visible = true;
			m.rightArm.visible = true;
			m.leftArm.visible = true;
			m.rightLeg.visible = true;
			m.leftLeg.visible = true;
			m.head.resetPose();
			m.body.resetPose();
			m.rightArm.resetPose();
			m.leftArm.resetPose();
			m.rightLeg.resetPose();
			m.leftLeg.resetPose();
			dev.sleepclient.emote.EmoteRig.resetBends(m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			float perBlock = (h - 6) / 2.5F;
			float cx = x + w / 2F, feetY = y + h - 4;
			float centerY = feetY - 1.0625F * perBlock;
			int half = Math.round(perBlock * 1.5F);
			c.skin(m, skin, perBlock, -4F, -20F, -1.0625F, Math.round(cx - half), Math.round(centerY - half), Math.round(cx + half),
					Math.round(centerY + half));
			return true;
		} catch (Throwable e) {
			System.err.println("[Sleep] card preview unavailable: " + e);
			return false;
		}
	}

	private static String killCardId;
	private static long killCardStart;

	/**
	 * Live preview of a kill effect for the Locker card while the mouse is over it: the real effect (same animation as in the world)
	 * around your own skin's stand-in, in 3D. Returns false when it can't (the caller keeps the picture).
	 */
	public static boolean drawKillCard(GuiGraphicsExtractor c, String effectId, net.minecraft.resources.Identifier skin, boolean play, int x, int y, int w, int h) {
		if (broken || skin == null || effectId == null) {
			return false;
		}
		try {
			Model m = cardModel("kill:" + effectId, false);
			m.wear = List.of();
			m.skin = skin;
			m.kill = effectId;
			m.killStill = !play;
			if (play) {
				if (!effectId.equals(killCardId)) {
					killCardId = effectId;
					killCardStart = System.currentTimeMillis();
				}
				m.killStart = killCardStart;
			} else if (effectId.equals(killCardId)) {
				killCardId = null; // the next hover starts from the beginning
			}
			// the effect brings its own stand-in: the mannequin itself is not drawn
			m.head.visible = false;
			m.body.visible = false;
			m.rightArm.visible = false;
			m.leftArm.visible = false;
			m.rightLeg.visible = false;
			m.leftLeg.visible = false;
			float perBlock = (h - 6) / 3.4F;
			float cx = x + w / 2F, feetY = y + h - 6;
			float centerY = feetY - 1.0625F * perBlock;
			int half = Math.round(perBlock * 1.9F);
			c.skin(m, skin, perBlock, 0F, 0F, -1.0625F, Math.round(cx - half), Math.round(centerY - half), Math.round(cx + half),
					Math.round(centerY + half));
			return true;
		} catch (Throwable e) {
			System.err.println("[Sleep] kill effect card preview unavailable: " + e);
			return false;
		}
	}

	/** Called right after Minecraft drew a skin-preview model (PlayerSkinRendererMixin): adds the cosmetics. */
	public static void afterModel(Object model, PoseStack matrices, int light) {
		if (!(model instanceof Model m)) {
			return;
		}
		MultiBufferSource p = provider;
		if (p != null && m.kill != null) {
			try {
				dev.sleepclient.fx.killfx.KillFxPreview.draw(m.kill, m.killStill ? -0.45F : (System.currentTimeMillis() - m.killStart) / 1000F, matrices, p, light, m.skin);
			} catch (Throwable ignored) {
			}
			return;
		}
		if (p == null || m.wear.isEmpty()) {
			return;
		}
		try {
			float age = System.currentTimeMillis() / 50F;
			for (CosmeticModels.Def d : m.wear) {
				if (d.livePet()) {
					dev.sleepclient.cosmetic.pet.PetModel pm = dev.sleepclient.cosmetic.pet.PetModel.get(d.pet());
					if (pm != null) {
						VertexConsumer pvc = p.getBuffer(RenderTypes.entityCutout(pm.texture));
						matrices.pushPose();
						dev.sleepclient.cosmetic.pet.Pets.drawPreview(d, matrices, bone -> dev.sleepclient.cosmetic.pet.Pets.emit(bone, matrices.last(), pvc, light));
						matrices.popPose();
					}
					continue;
				}
				if (Minimes.is(d)) {
					if (m.skin != null) {
						VertexConsumer svc = p.getBuffer(RenderTypes.entityCutout(m.skin));
						Minimes.draw(d, m, m.slimArms, matrices, age, light, (ms, e) -> e.emit(ms.last(), svc));
					}
					continue;
				}
				VertexConsumer vc = p.getBuffer(RenderTypes.entityCutout(d.textureId()));
				if (Auras.is(d)) {
					Auras.emit(d, matrices.last(), vc, light, age, false);
					continue;
				}
				for (CosmeticModels.Part part : CosmeticModels.geometry(d)) {
					matrices.pushPose();
					if (CosmeticFeatureRenderer.place(matrices, m, part.attach(), age, false)) {
						matrices.scale(1F / 16F, 1F / 16F, 1F / 16F);
						CosmeticFeatureRenderer.emit(part, matrices.last(), vc, light);
					}
					matrices.popPose();
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static Model model(boolean isSlim) {
		if (isSlim) {
			if (slim == null) {
				slim = new Model(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot(), true);
			}
			return slim;
		}
		if (wide == null) {
			wide = new Model(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
		}
		return wide;
	}

	/**
	 * Draws the player with its feet at y = feetY and 'px' screen units per skin pixel, centred on cx (screen GUI units),
	 * turned by yaw degrees and tilted by pitch. Returns false if it can't (then draw the old preview).
	 */
	public static boolean draw(GuiGraphicsExtractor c, RemoteTextures.Tex skin, boolean isSlim, float cx, float feetY, float px, float yaw, float pitch,
			List<CosmeticModels.Def> wear) {
		if (broken || skin == null) {
			return false;
		}
		try {
			Model m = model(isSlim);
			m.wear = wear == null ? List.of() : wear;
			m.skin = skin.id();
			// the model is shared: start from the standing pose every frame
			m.head.resetPose();
			m.body.resetPose();
			m.rightArm.resetPose();
			m.leftArm.resetPose();
			m.rightLeg.resetPose();
			m.leftLeg.resetPose();
			dev.sleepclient.emote.EmoteRig.resetBends(m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			dev.sleepclient.emote.EmoteManager.Remote pr = dev.sleepclient.emote.EmoteManager.previewRemote();
			// idle arms, like Minecraft's standing animation
			float t = System.currentTimeMillis() / 50F;
			float roll = (float) Math.cos(t * 0.09F) * 0.05F + 0.05F, pitchA = (float) Math.sin(t * 0.067F) * 0.05F;
			m.rightArm.zRot = roll;
			m.leftArm.zRot = -roll;
			m.rightArm.xRot = pitchA;
			m.leftArm.xRot = -pitchA;
			if (pr != null) { // the Locker shows the selected emote
				dev.sleepclient.emote.EmoteManager.applyPreview(pr, m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
			}
			float perBlock = px * 16F;
			// the model is centred on the point 1.0625 blocks above the feet
			float centerY = feetY - 1.0625F * perBlock;
			int half = Math.round(perBlock * 1.6F);
			int x1 = Math.round(cx - half), x2 = Math.round(cx + half);
			int y1 = Math.round(centerY - half), y2 = Math.round(centerY + half);
			c.skin(m, skin.id(), perBlock, pitch, yaw, -1.0625F, x1, y1, x2, y2);
			return true;
		} catch (Throwable e) {
			broken = true;
			System.err.println("[Sleep] skin preview unavailable: " + e);
			return false;
		}
	}
}
