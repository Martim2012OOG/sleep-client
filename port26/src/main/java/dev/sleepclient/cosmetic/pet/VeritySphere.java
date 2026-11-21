package dev.sleepclient.cosmetic.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Verity exactly as the original mod draws him: a 16x16 sphere (radius 0.25 blocks) with the face projected flat on it
 * (u scale 1.3, v scale 1.4444, clamped), always turned towards the camera, rolling 150 degrees per block. The angry
 * faces have a second sphere with the teeth drawn over the first.
 */
final class VeritySphere {

	/** Face number (what Verity.face() returns) to the original texture name. */
	private static final String[] FACES = {"noface", "serious_1", "serious_2", "happy_talking", "neutral_talking", "serious_3", "serious_talking", "crazy",
			"crazy_talking", "evil_talking", "smiling_evil", "happy", "neutral", "happy"};
	private static final Set<String> TEETH = Set.of("crazy", "crazy_talking", "evil", "evil_talking", "neutral_talking", "smiling_evil");
	private static final int N = 16;
	/** The colour the original mod tints the pale face texture with (255, 225, 30). */
	private static final int TINT = 0xFFFFE11E;

	/** x, y, z, u, v per vertex, 4 vertices per quad. */
	private static float[] mesh;

	private VeritySphere() {
	}

	private static void build() {
		float[] out = new float[N * N * 4 * 5];
		int o = 0;
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < N; j++) {
				float a0 = (float) (i * Math.PI / N), a1 = (float) ((i + 1) * Math.PI / N);
				float b0 = (float) (j * 2 * Math.PI / N), b1 = (float) ((j + 1) * 2 * Math.PI / N);
				float[][] c = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
				for (float[] k : c) {
					float a = k[0], b = k[1];
					out[o++] = 0.5F * (float) (Math.sin(a) * Math.cos(b));
					out[o++] = 0.5F * (float) Math.cos(a);
					out[o++] = 0.5F * (float) (Math.sin(a) * Math.sin(b));
					float u = 0.5F + ((float) (b / (2 * Math.PI)) - 0.5F) * 1.3F;
					float v0 = 0.25F + (float) (a / Math.PI) * 0.5F;
					float v = 0.5F + (v0 - 0.5F) * 1.4444444F;
					out[o++] = Math.max(0F, Math.min(1F, u));
					out[o++] = Math.max(0F, Math.min(1F, v));
				}
			}
		}
		mesh = out;
	}

	private static String name(int face) {
		return FACES[Math.max(0, Math.min(FACES.length - 1, face))];
	}

	/**
	 * @param x,y,z where his feet are (blocks, already with the bounce offset)
	 * @param rollRad how far he has rolled (radians), heading = the direction he is rolling in (yaw, degrees)
	 * @param size 1 = normal, bigger when he rages; sy / sxz = the squash and stretch
	 */
	static void draw(PoseStack m, SubmitNodeCollector q, Vec3 cam, double x, double y, double z, int face, float rollRad, float headingDeg,
			float size, float sy, float sxz, int light) {
		if (mesh == null) {
			build();
		}
		String n = name(face);
		float r = 0.5F * size;
		double cy = y + 0.25 * size * sy;
		// towards the camera
		double dx = cam.x - x, dy = cam.y - cy, dz = cam.z - z;
		double hd = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, hd));
		// the ball rolls about the horizontal axis across its heading
		double hr = Math.toRadians(headingDeg);
		float hx = (float) -Math.sin(hr), hz = (float) Math.cos(hr);
		m.pushPose();
		m.translate(x - cam.x, cy - cam.y, z - cam.z);
		if (Math.abs(rollRad) > 1e-4F) {
			m.mulPose(new Quaternionf().rotationAxis(rollRad, hz, 0F, -hx));
		}
		m.mulPose(Axis.YP.rotationDegrees(yaw));
		m.mulPose(Axis.XP.rotationDegrees(pitch));
		m.mulPose(Axis.YP.rotationDegrees(90F));
		m.scale(r * sxz, r * sy, r * sxz);
		emit(m, q, dev.sleepclient.Gfx.id("textures/cosmetics/ovf_" + n + ".png"), TINT, light);
		if (TEETH.contains(n)) {
			m.scale(1.002F, 1.002F, 1.002F);
			emit(m, q, dev.sleepclient.Gfx.id("textures/cosmetics/ovt_" + n + ".png"), 0xFFFFFFFF, light);
		}
		m.popPose();
	}

	private static void emit(PoseStack m, SubmitNodeCollector q, Identifier tex, int color, int light) {
		final float[] v = mesh;
		q.submitCustomGeometry(m, RenderTypes.entityCutout(tex), (e, vc) -> {
			for (int k = 0; k + 4 < v.length; k += 5) {
				float px = v[k] * 2F, py = v[k + 1] * 2F, pz = v[k + 2] * 2F; // unit sphere: the normal
				vc.addVertex(e, v[k], v[k + 1], v[k + 2]).setColor(color).setUv(v[k + 3], v[k + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
						.setNormal(e, px, py, pz);
			}
		});
	}

	// ---- the bounce he does when he drops out of the box (the original's VerityAnimation, t = ticks 0..50)
	static float bounceScaleY(float t) {
		if (t < 0 || t > 50) {
			return 1F;
		}
		if (t <= 12) {
			float u = t / 12F;
			return 1F + u * u * 0.3F;
		}
		if (t <= 18) {
			float u = (t - 12F) / 6F;
			return 1F - (float) Math.sin(u * Math.PI) * 0.6F;
		}
		if (t >= 30 && t <= 36) {
			float u = (t - 30F) / 6F;
			return 1F - (float) Math.sin(u * Math.PI) * 0.3F;
		}
		if (t >= 42 && t <= 46) {
			float u = (t - 42F) / 4F;
			return 1F - (float) Math.sin(u * Math.PI) * 0.1F;
		}
		return 1F;
	}

	static float bounceScaleXZ(float t) {
		return 1F + (1F - bounceScaleY(t)) * 0.75F;
	}

	static float bounceYOffset(float t) {
		if (t < 0 || t > 50) {
			return 0F;
		}
		if (t <= 12) {
			float u = t / 12F;
			return 1F - u * u * u;
		}
		if (t <= 30) {
			float u = (t - 12F) / 18F;
			return 4F * u * (1F - u) * 0.5F;
		}
		if (t <= 42) {
			float u = (t - 30F) / 12F;
			return 4F * u * (1F - u) * 0.15F;
		}
		return 0F;
	}
}
