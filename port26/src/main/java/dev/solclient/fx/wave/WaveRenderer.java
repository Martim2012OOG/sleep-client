package dev.solclient.fx.wave;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the waves as flat animated quads. Adapted from Wavify / Tidal by Superkat32 (MIT licence). Each texture is a strip of
 * 16x16 frames; the frame is chosen from the age of the wave. Quads are drawn on both sides so they are visible from above and below.
 */
public class WaveRenderer {
	private static Identifier tex(String n) {
		return Identifier.fromNamespaceAndPath("sol_client", "textures/wave/" + n + ".png");
	}

	private static final Identifier MOVING = tex("moving"), MOVING_W = tex("moving_white");
	private static final Identifier TOP = tex("washing_top_colorable"), TOP_W = tex("washing_top_white");
	private static final Identifier BOT = tex("washing_bottom_colorable"), BOT_W = tex("washing_bottom_white");
	// frames in each strip (height / 16)
	private static final int F_MOVING = 16, F_TOP = 18, F_BOT = 24;

	private final WaveHandler handler;

	public WaveRenderer(WaveHandler handler, ClientLevel world) {
		this.handler = handler;
	}

	public void render(PoseStack matrices, SubmitNodeCollector queue, Vec3 cam, float tickDelta) {
		List<Wave> waves = handler.getWaves();
		if (waves == null || waves.isEmpty()) {
			return;
		}
		for (Wave w : waves) {
			if (w != null) {
				renderWave(matrices, queue, cam, w);
			}
		}
	}

	private void renderWave(PoseStack matrices, SubmitNodeCollector queue, Vec3 cam, Wave wave) {
		AABB box = wave.getBoundingBox();
		Vec3 c = new Vec3((box.minX + box.maxX) / 2.0 - cam.x, box.minY - cam.y, (box.minZ + box.maxZ) / 2.0 - cam.z);
		if (c.lengthSqr() > 96 * 96) {
			return;
		}
		matrices.pushPose();
		matrices.translate(c.x, c.y, c.z);
		matrices.mulPose(Axis.YP.rotationDegrees(-wave.yaw + 90));
		matrices.mulPose(Axis.XP.rotationDegrees(wave.pitch));
		matrices.scale(wave.scale, 1, wave.scale);
		matrices.translate(-wave.width / 3f, 0, 0);

		boolean washing = wave.isWashingUp();
		int light = wave.getLight();
		int age = wave.getAge(), maxAge = Math.max(1, wave.getMaxAge());
		int col = argb(wave.alpha, wave.red, wave.green, wave.blue);
		int white = argb(wave.alpha, 1, 1, 1);
		int n = Math.max(1, (int) wave.width);
		float len = wave.length;

		Identifier a = washing ? TOP : MOVING, b = washing ? TOP_W : MOVING_W;
		int frames = washing ? F_TOP : F_MOVING;
		int ft = washing ? 0 : 5;
		draw(matrices, queue, a, frame(age, maxAge, frames, ft), frames, n, 0f, 0f, 1f, len, col, light, 0f);
		draw(matrices, queue, b, frame(age, maxAge, frames, ft), frames, n, 0.05f, 0f, 1f, len, white, light, 0f);

		if (washing && wave.bigWave) {
			float d = (float) age / maxAge;
			float back = 0.5f;
			float wl = d > back ? Mth.lerp((d - back) * 2, 2f, 3f) : 2f;
			float wz = d > back ? Mth.lerp((d - back) * 2, 1.35f, 0f) : 1.35f;
			int fr = frame(age, maxAge, F_BOT, 0);
			draw(matrices, queue, BOT, fr, F_BOT, n, -0.05f, wz, 1f, wl, col, light, -0.15f);
			draw(matrices, queue, BOT_W, fr, F_BOT, n, -0.01f, wz, 1f, wl, white, light, -0.15f);
		}
		matrices.popPose();
	}

	private static int frame(int age, int maxAge, int frames, int frameTime) {
		if (frameTime <= 0) {
			return Mth.clamp((int) ((float) age / maxAge * frames), 0, frames - 1);
		}
		return (age / frameTime) % frames;
	}

	private static int argb(float a, float r, float g, float b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(float f) {
		return Mth.clamp((int) (f * 255f), 0, 255);
	}

	private static void draw(PoseStack m, SubmitNodeCollector q, Identifier t, int frame, int frames, int count, float y, float z, float width,
			float length, int col, int light, float xOff) {
		final float v0 = (float) frame / frames, v1 = (float) (frame + 1) / frames;
		q.submitCustomGeometry(m, RenderTypes.entityTranslucent(t), (e, vc) -> {
			float hw = width / 2f, hl = length / 2f;
			for (int i = 0; i < count; i++) {
				float x = i + xOff;
				// top face, then the same quad reversed so it also shows from below
				v(vc, e, x - hw, y, z - hl, 0, v1, col, light, 1);
				v(vc, e, x - hw, y, z + hl, 0, v0, col, light, 1);
				v(vc, e, x + hw, y, z + hl, 1, v0, col, light, 1);
				v(vc, e, x + hw, y, z - hl, 1, v1, col, light, 1);
				v(vc, e, x + hw, y, z - hl, 1, v1, col, light, -1);
				v(vc, e, x + hw, y, z + hl, 1, v0, col, light, -1);
				v(vc, e, x - hw, y, z + hl, 0, v0, col, light, -1);
				v(vc, e, x - hw, y, z - hl, 0, v1, col, light, -1);
			}
		});
	}

	private static void v(VertexConsumer vc, PoseStack.Pose e, float x, float y, float z, float u, float v, int col, int light, float ny) {
		vc.addVertex(e, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, 0, ny, 0);
	}
}
