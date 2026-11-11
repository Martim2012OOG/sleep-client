package dev.solclient.cosmetic;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Minimes: a small copy of the player (their own skin) that sits on the shoulder, on the head, or floats next to them, and
 * copies every move (walking, sneaking, emotes) because it is drawn with the pose of the real model's parts. A cosmetic of
 * category "Minimes" with "anim": "minime:&lt;where&gt;" in cosmetics/index.json. The boxes are emitted here with the
 * standard 64x64 skin layout, so nothing but the skin texture is needed.
 */
public final class Minimes {

	public static final int SHOULDER = 0, HEAD = 1, BUDDY = 2, BACKPACK = 3;

	/** One box of the player model: skin position (u, v), corner (x, y, z), size (w, h, d), inflate. */
	private record Box(int u, int v, float x, float y, float z, int w, int h, int d, float grow) {
	}

	private static final Box HEAD_BOX = new Box(0, 0, -4, -8, -4, 8, 8, 8, 0F), HAT = new Box(32, 0, -4, -8, -4, 8, 8, 8, 0.5F),
			BODY = new Box(16, 16, -4, 0, -2, 8, 12, 4, 0F), RIGHT_LEG = new Box(0, 16, -2, 0, -2, 4, 12, 4, 0F),
			LEFT_LEG = new Box(16, 48, -2, 0, -2, 4, 12, 4, 0F);
	private static final Box[] RIGHT_ARM = {new Box(40, 16, -3, -2, -2, 4, 12, 4, 0F), new Box(40, 16, -2, -2, -2, 3, 12, 4, 0F)},
			LEFT_ARM = {new Box(32, 48, -1, -2, -2, 4, 12, 4, 0F), new Box(32, 48, -1, -2, -2, 3, 12, 4, 0F)};

	private Minimes() {
	}

	public static boolean is(CosmeticModels.Def d) {
		return d != null && d.anim() != null && d.anim().startsWith("minime:");
	}

	public static int where(CosmeticModels.Def d) {
		return switch (d.anim().substring(7)) {
			case "head" -> HEAD;
			case "buddy" -> BUDDY;
			case "backpack" -> BACKPACK;
			default -> SHOULDER;
		};
	}

	/** What draws one box: the matrices are already at the part (in pixels). */
	public interface Sink {
		void box(MatrixStack matrices, Emitter e);
	}

	public interface Emitter {
		void emit(MatrixStack.Entry entry, VertexConsumer vc);
	}

	/**
	 * Places the minime and hands every box to 'sink' (the world queues them, the menu preview draws them at once).
	 * 'age' in ticks; the matrices are in the player's model space.
	 */
	public static void draw(CosmeticModels.Def d, PlayerEntityModel model, boolean slim, MatrixStack m, float age, int light, Sink sink) {
		int where = where(d);
		float k;
		m.push();
		switch (where) {
			case HEAD -> {
				k = 0.3F;
				model.head.applyTransform(m);
				m.translate(0F, -(8F + 24F * k) / 16F, 0F);
			}
			case BUDDY -> {
				k = 0.42F;
				float bob = (float) Math.sin(age * 0.09F) * 1.5F;
				m.translate(-15F / 16F, (2F + bob) / 16F, 3F / 16F);
			}
			case BACKPACK -> {
				k = 0.36F;
				model.body.applyTransform(m);
				m.translate(0F, 1.5F / 16F, (2F + 2F * k + 0.4F) / 16F); // hanging on your back
			}
			default -> {
				k = 0.3F;
				model.body.applyTransform(m);
				m.translate(6.2F / 16F, -(24F * k) / 16F, 0F); // standing on the left shoulder
			}
		}
		m.scale(k, k, k);
		int s = slim ? 1 : 0;
		part(m, model.head, HEAD_BOX, light, sink);
		part(m, model.head, HAT, light, sink);
		part(m, model.body, BODY, light, sink);
		part(m, model.rightArm, RIGHT_ARM[s], light, sink);
		part(m, model.leftArm, LEFT_ARM[s], light, sink);
		part(m, model.rightLeg, RIGHT_LEG, light, sink);
		part(m, model.leftLeg, LEFT_LEG, light, sink);
		m.pop();
	}

	private static void part(MatrixStack m, ModelPart p, Box b, int light, Sink sink) {
		m.push();
		p.applyTransform(m);
		m.scale(1F / 16F, 1F / 16F, 1F / 16F);
		sink.box(m, (entry, vc) -> box(entry, vc, b, light));
		m.pop();
	}

	private static void box(MatrixStack.Entry e, VertexConsumer vc, Box b, int light) {
		float g = b.grow;
		float x0 = b.x - g, y0 = b.y - g, z0 = b.z - g, x1 = b.x + b.w + g, y1 = b.y + b.h + g, z1 = b.z + b.d + g;
		// skin layout of a box at (u, v): top and bottom on the first row, then right side, front, left side, back
		float j = b.u, k = b.u + b.d, l = k + b.w, mm = l + b.w, n = l + b.d, o = n + b.w;
		float p = b.v, q = b.v + b.d, r = q + b.h;
		// front (z0) and back (z1)
		quad(e, vc, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, k, q, l, r, 0, 0, -1);
		quad(e, vc, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, n, q, o, r, 0, 0, 1);
		// the player's right side (x0) and left side (x1)
		quad(e, vc, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, j, q, k, r, -1, 0, 0);
		quad(e, vc, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, l, q, n, r, 1, 0, 0);
		// top (y0) and bottom (y1)
		quad(e, vc, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, k, p, l, q, 0, -1, 0);
		quad(e, vc, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, l, p, mm, q, 0, 1, 0);
	}

	/** Corners in the order: (u0, v0), (u1, v0), (u1, v1), (u0, v1). */
	private static void quad(MatrixStack.Entry e, VertexConsumer vc, int light, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
		int ov = OverlayTexture.DEFAULT_UV;
		float s = 1F / 64F;
		vc.vertex(e, ax, ay, az).color(0xFFFFFFFF).texture(u0 * s, v0 * s).overlay(ov).light(light).normal(e, nx, ny, nz);
		vc.vertex(e, bx, by, bz).color(0xFFFFFFFF).texture(u1 * s, v0 * s).overlay(ov).light(light).normal(e, nx, ny, nz);
		vc.vertex(e, cx, cy, cz).color(0xFFFFFFFF).texture(u1 * s, v1 * s).overlay(ov).light(light).normal(e, nx, ny, nz);
		vc.vertex(e, dx, dy, dz).color(0xFFFFFFFF).texture(u0 * s, v1 * s).overlay(ov).light(light).normal(e, nx, ny, nz);
	}
}
