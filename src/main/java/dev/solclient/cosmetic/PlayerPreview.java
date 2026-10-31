package dev.solclient.cosmetic;

import dev.solclient.mixin.cosmetic.InventoryScreenAccessor;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Real 3D preview of your own player (the same renderer as in the world: skin, cape, 3D cosmetics with a depth buffer).
 * Only possible while you are in a world; in the title screen the menus fall back to the 2D-projected model.
 */
public final class PlayerPreview {

	private static boolean broken;

	private PlayerPreview() {
	}

	/**
	 * Draws you between x1..x2 / y1..y2 (screen GUI units), turned by yaw degrees, wearing 'wear' (try-on) if not null.
	 * Returns false when it can't (no world / mixin missing) so the caller can draw the fallback.
	 */
	public static boolean draw(DrawContext c, int x1, int y1, int x2, int y2, float yaw, List<CosmeticModels.Def> wear) {
		return draw(c, x1, y1, x2, y2, yaw, wear, null);
	}

	/** Same, and 'cape' (null = the one you wear) is tried on you: shop capes have no 3D model, they are a texture on the cape. */
	public static boolean draw(DrawContext c, int x1, int y1, int x2, int y2, float yaw, List<CosmeticModels.Def> wear,
			dev.solclient.social.CapeMediaLogic.Desc cape) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (broken || mc.player == null || mc.world == null) {
			return false;
		}
		try {
			Cosmetics.preview(wear);
			EntityRenderState st;
			dev.solclient.social.CapeMedia.beginPreview(cape);
			try {
				st = InventoryScreenAccessor.plegumo$state(mc.player); // the skin (and cape) textures are read here
			} finally {
				dev.solclient.social.CapeMedia.endPreview();
			}
			st.displayName = null; // the menu draws its own name pill
			st.shadowPieces.clear();
			if ((Object) st instanceof dev.solclient.emote.EmoteFlag flag) {
				dev.solclient.emote.EmoteManager.Remote pr = dev.solclient.emote.EmoteManager.previewRemote();
				if (pr != null) { // the Locker shows the selected emote on you
					flag.sol$setEmoting(false);
					flag.sol$setRemoteEmote(pr);
				}
			}
			if (st instanceof LivingEntityRenderState l) {
				l.bodyYaw = 180F + yaw;
				l.relativeHeadYaw = 0F;
				l.pitch = 0F;
				l.limbSwingAmplitude = 0F;
				l.limbSwingAnimationProgress = 0F;
			}
			// y2 = the feet, y1..y2 = 2.5 blocks (room for hats): put the feet on y2
			float entScale = mc.player.getScale();
			float size = (y2 - y1) / 2.5F;
			float mid = (y1 + y2) / 2F;
			// addEntity centres the point at height tr.y on the box centre: choose it so the feet land on y2
			Vector3f tr = new Vector3f(0F, (y2 - mid) / size, 0F);
			Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX((float) Math.toRadians(-8)));
			Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(-8));
			cam.conjugate();
			c.addEntity(st, size / entScale, tr, rot, cam, x1, y1, x2, y2);
			return true;
		} catch (Throwable t) {
			broken = true;
			System.err.println("[Sleep] 3D preview unavailable, using the simple one: " + t);
			return false;
		}
	}
}
