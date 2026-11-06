package dev.solclient.cosmetic.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The cardboard box Verity comes out of (model: "Minecraft Verity Box" by Jojojossy76, CC-BY-4.0): it lands, its two flaps
 * swing open, Verity rises out, and the box sinks into the ground.
 */
final class VerityBox {

	private static final Identifier TEX = dev.solclient.Gfx.id("textures/cosmetics/verity_box.png");
	/** main, lid_r, lid_l: triangles as x, y, z, u, v (blocks). */
	private static float[][] parts;
	private static float[][] pivots;

	private VerityBox() {
	}

	private static boolean load() {
		if (parts != null) {
			return parts.length > 0;
		}
		parts = new float[0][];
		try {
			var res = MinecraftClient.getInstance().getResourceManager().getResource(dev.solclient.Gfx.id("verity/box.json"));
			if (res.isEmpty()) {
				return false;
			}
			try (BufferedReader r = res.get().getReader()) {
				JsonArray ps = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("parts");
				float[][] out = new float[ps.size()][], piv = new float[ps.size()][];
				for (int i = 0; i < ps.size(); i++) {
					JsonObject o = ps.get(i).getAsJsonObject();
					JsonArray t = o.getAsJsonArray("tris");
					out[i] = new float[t.size()];
					for (int k = 0; k < out[i].length; k++) {
						out[i][k] = t.get(k).getAsFloat();
					}
					JsonArray pv = o.getAsJsonArray("pivot");
					piv[i] = new float[] {pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat()};
				}
				parts = out;
				pivots = piv;
			}
		} catch (Exception e) {
			System.err.println("[Sleep] verity box: " + e);
		}
		return parts.length > 0;
	}

	/** age = ms since Verity appeared. */
	static void draw(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam, double x, double y, double z, long age, ClientWorld w) {
		if (!load()) {
			return;
		}
		int light = WorldRenderer.getLightmapCoordinates(w, BlockPos.ofFloored(x, y + 0.3, z));
		float drop = age < 250 ? (1F - age / 250F) * 0.8F : 0F; // falls in
		float open = Math.max(0F, Math.min(1F, (age - 350F) / 450F)); // flaps open
		float sink = age > 4500 ? (age - 4500F) / 1500F * 0.45F : 0F; // goes away
		m.push();
		m.translate(x - cam.x, y - cam.y + drop - sink, z - cam.z);
		m.scale(0.55F, 0.55F, 0.55F);
		for (int i = 0; i < parts.length; i++) {
			m.push();
			if (i > 0) {
				float[] pv = pivots[i];
				float ang = open * 125F * (pv[0] > 0 ? -1F : 1F) + (float) Math.sin(age / 90.0) * 3F * open;
				m.translate(pv[0], pv[1], pv[2]);
				m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ang));
				m.translate(-pv[0], -pv[1], -pv[2]);
			}
			final float[] t = parts[i];
			q.submitCustom(m, RenderLayers.entityCutoutNoCull(TEX), (e, vc) -> {
				for (int k = 0; k + 14 < t.length; k += 15) {
					float ax = t[k + 5] - t[k], ay = t[k + 6] - t[k + 1], az = t[k + 7] - t[k + 2];
					float bx = t[k + 10] - t[k], by = t[k + 11] - t[k + 1], bz = t[k + 12] - t[k + 2];
					float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
					float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6F;
					// a triangle as a quad with its last corner repeated
					for (int c = 0; c < 4; c++) {
						int o = k + Math.min(c, 2) * 5;
						vc.vertex(e, t[o], t[o + 1], t[o + 2]).color(0xFFFFFFFF).texture(t[o + 3], t[o + 4]).overlay(OverlayTexture.DEFAULT_UV).light(light)
								.normal(e, nx / l, ny / l, nz / l);
					}
				}
			});
			m.pop();
		}
		m.pop();
	}
}
