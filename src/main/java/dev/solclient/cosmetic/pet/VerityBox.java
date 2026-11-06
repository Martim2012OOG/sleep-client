package dev.solclient.cosmetic.pet;

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
 * The original mod's cardboard box (box.geo.json + box.animation.json): an 18x17x16 px cube with two flaps. Shut it hovers
 * and pulses; opened (age >= 0, ms) the flaps swing open, the box floats up, then shrinks away.
 */
final class VerityBox {

	private static final Identifier TEX = dev.solclient.Gfx.id("textures/cosmetics/verity_box.png");
	/** The geo says 128x128 (the png is 256x256: uv is relative to the geo size). */
	private static final float TW = 128F;

	private VerityBox() {
	}

	/** One face: 4 corners (x, y, z in px) and the texture rectangle (px) [u0, v0, u1, v1]; corner 0 = (u0, v0). */
	private record Face(float[] c, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
	}

	private static final java.util.List<Face> MAIN = cube(-9, 0, -8, 18, 17, 16, 0, 0);
	private static final java.util.List<Face> FLAP1 = flat(0, 17.05F, -8, 9, 16, 0, 33, 16);
	private static final java.util.List<Face> FLAP2 = flat(-9, 17.05F, -8, 9, 16, 0, 49, 16);

	/** Bedrock box uv: row 1 = up, down; row 2 = east, north, west, south. */
	private static java.util.List<Face> cube(float ox, float oy, float oz, float w, float h, float d, float u, float v) {
		java.util.List<Face> f = new java.util.ArrayList<>();
		float x1 = ox + w, y1 = oy + h, z1 = oz + d;
		// +x
		f.add(new Face(new float[] {x1, y1, oz, x1, y1, z1, x1, oy, z1, x1, oy, oz}, u, v + d, u + d, v + d + h, 1, 0, 0));
		// -z
		f.add(new Face(new float[] {x1, y1, oz, ox, y1, oz, ox, oy, oz, x1, oy, oz}, u + d, v + d, u + d + w, v + d + h, 0, 0, -1));
		// -x
		f.add(new Face(new float[] {ox, y1, z1, ox, y1, oz, ox, oy, oz, ox, oy, z1}, u + d + w, v + d, u + 2 * d + w, v + d + h, -1, 0, 0));
		// +z
		f.add(new Face(new float[] {ox, y1, z1, x1, y1, z1, x1, oy, z1, ox, oy, z1}, u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, 0, 0, 1));
		// up
		f.add(new Face(new float[] {x1, y1, oz, ox, y1, oz, ox, y1, z1, x1, y1, z1}, u + d, v, u + d + w, v + d, 0, 1, 0));
		// down
		f.add(new Face(new float[] {x1, oy, z1, ox, oy, z1, ox, oy, oz, x1, oy, oz}, u + d + w, v, u + d + 2 * w, v + d, 0, -1, 0));
		return f;
	}

	/** A flap: no height, only a top and a bottom. */
	private static java.util.List<Face> flat(float ox, float y, float oz, float w, float d, float u, float v, float dd) {
		java.util.List<Face> f = new java.util.ArrayList<>();
		float x1 = ox + w, z1 = oz + d;
		f.add(new Face(new float[] {x1, y, oz, ox, y, oz, ox, y, z1, x1, y, z1}, u + dd, v, u + dd + w, v + d, 0, 1, 0));
		f.add(new Face(new float[] {x1, y, z1, ox, y, z1, ox, y, oz, x1, y, oz}, u + dd + w, v, u + dd + 2 * w, v + d, 0, -1, 0));
		return f;
	}

	private static float easeOutCirc(float x) {
		return (float) Math.sqrt(1 - Math.pow(x - 1, 2));
	}

	private static float easeInExpo(float x) {
		return x <= 0 ? 0 : (float) Math.pow(2, 10 * x - 10);
	}

	private static float easeInOutSine(float x) {
		return -((float) Math.cos(Math.PI * x) - 1F) / 2F;
	}

	/**
	 * @param age ms since the box was opened; negative = shut, hovering
	 */
	static void draw(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam, double x, double y, double z, long age, ClientWorld w) {
		float lift = 0F, scale = 1F, flap = 0F;
		if (age < 0) {
			// "hover": bobs 2 px over 2 s and pulses three times
			float t = (System.currentTimeMillis() % 2000L) / 1000F;
			lift = 1F - (float) Math.cos(Math.PI * t);
			for (float c : new float[] {0.5F, 1.2917F, 1.5417F}) {
				scale += 0.1F * Math.max(0F, 1F - Math.abs(t - c) / 0.0833F);
			}
		} else {
			// "open"
			float t = age / 1000F;
			if (t > 2.05F) {
				return; // the box is gone
			}
			lift = 16F * easeOutCirc(Math.min(1F, t / 0.75F));
			flap = 130F * easeInOutSine(Math.max(0F, Math.min(1F, (t - 0.5F) / 0.5F)));
			if (t > 1.25F) {
				scale = 1F - 0.9F * easeInExpo(Math.min(1F, (t - 1.25F) / 0.75F));
			}
		}
		int light = WorldRenderer.getLightmapCoordinates(w, BlockPos.ofFloored(x, y + 0.5, z));
		m.push();
		m.translate(x - cam.x, y - cam.y, z - cam.z);
		m.scale(1F / 16F, 1F / 16F, 1F / 16F);
		m.translate(0, lift, 0);
		m.scale(scale, scale, scale);
		part(m, q, MAIN, light);
		flap(m, q, FLAP1, 9F, -flap, light);
		flap(m, q, FLAP2, -9F, flap, light);
		m.pop();
	}

	private static void flap(MatrixStack m, OrderedRenderCommandQueue q, java.util.List<Face> faces, float pivotX, float angle, int light) {
		m.push();
		m.translate(pivotX, 17.05F, 0);
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
		m.translate(-pivotX, -17.05F, 0);
		part(m, q, faces, light);
		m.pop();
	}

	private static void part(MatrixStack m, OrderedRenderCommandQueue q, java.util.List<Face> faces, int light) {
		q.submitCustom(m, RenderLayers.entityCutoutNoCull(TEX), (e, vc) -> {
			for (Face f : faces) {
				float[] c = f.c();
				float[] us = {f.u0(), f.u1(), f.u1(), f.u0()};
				float[] vs = {f.v0(), f.v0(), f.v1(), f.v1()};
				for (int k = 0; k < 4; k++) {
					vc.vertex(e, c[k * 3], c[k * 3 + 1], c[k * 3 + 2]).color(0xFFFFFFFF).texture(us[k] / TW, vs[k] / TW)
							.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, f.nx(), f.ny(), f.nz());
				}
			}
		});
	}
}
