package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Pop Chams: when a player pops a totem, a coloured ghost of them stays where it happened, rises and fades out -
 * so you see every pop even in a messy fight.
 */
public class PopChamsMod extends Mod {

	public static PopChamsMod instance;

	private final Option.Col colour = colour("Colour", 0xA0FF3B6B);
	private final Option.Num time = num("Time", 12, 4, 40, 1, " / 10 s");
	private final Option.Bool rise = bool("Float Up", true);
	private final Option.Bool self = bool("Show My Own", false);
	private static boolean hooked;

	private record Ghost(double x, double y, double z, float yaw, long at) {
	}

	private final List<Ghost> ghosts = new ArrayList<>();

	public PopChamsMod() {
		super("Pop Chams", "pop_chams", "A coloured ghost where a player popped a totem.", Category.VISUAL);
		instance = this;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				if (instance != null && instance.enabled) {
					try {
						instance.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Pop Chams unavailable: " + t);
		}
	}

	/** Called when an entity's totem pops (status 35). */
	public static void onPop(LivingEntity e) {
		if (instance == null || !instance.enabled || !(e instanceof PlayerEntity)) {
			return;
		}
		if (e == instance.mc.player && !instance.self.on()) {
			return;
		}
		synchronized (instance.ghosts) {
			instance.ghosts.add(new Ghost(e.getX(), e.getY(), e.getZ(), e.bodyYaw, System.currentTimeMillis()));
			if (instance.ghosts.size() > 24) {
				instance.ghosts.remove(0);
			}
		}
	}

	private void render(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		long now = System.currentTimeMillis();
		long life = Math.round(time.f() * 100);
		List<float[]> boxes = new ArrayList<>();
		synchronized (ghosts) {
			ghosts.removeIf(g -> now - g.at > life);
			for (Ghost g : ghosts) {
				float k = (now - g.at) / (float) life;
				float a = (1F - k) * (1F - k);
				float up = rise.on() ? k * 0.7F : 0F;
				float sc = 1F + k * 0.08F;
				double ox = g.x - cam.x, oy = g.y - cam.y + up, oz = g.z - cam.z;
				// a body like the player's: legs, torso, arms, head (in blocks, before turning by the body yaw)
				float[][] parts = {{-0.125F, 0.375F, 0, 0.125F, 0.375F, 0.125F}, {0.125F, 0.375F, 0, 0.125F, 0.375F, 0.125F},
						{0, 1.125F, 0, 0.25F, 0.375F, 0.125F}, {-0.375F, 1.125F, 0, 0.125F, 0.375F, 0.125F}, {0.375F, 1.125F, 0, 0.125F, 0.375F, 0.125F},
						{0, 1.75F, 0, 0.25F, 0.25F, 0.25F}};
				for (float[] p : parts) {
					boxes.add(new float[] {(float) ox, (float) oy, (float) oz, g.yaw, p[0] * sc, p[1] * sc, p[2], p[3] * sc, p[4] * sc, p[5] * sc, a});
				}
			}
		}
		if (boxes.isEmpty()) {
			return;
		}
		int col = colour.c();
		q.submitCustom(m, RenderLayers.entityTranslucent(dev.solclient.fx.WorldDraw.WHITE), (e, vc) -> {
			for (float[] b : boxes) {
				box(vc, e, b, col);
			}
		});
	}

	/** One turned, filled box: b = {x, y, z, yaw, cx, cy, cz, hx, hy, hz, alpha}. */
	private static void box(VertexConsumer vc, MatrixStack.Entry e, float[] b, int col) {
		double yr = Math.toRadians(-b[3]);
		float cos = (float) Math.cos(yr), sin = (float) Math.sin(yr);
		int a = Math.round(((col >>> 24) & 255) * b[10]);
		int c = a << 24 | (col & 0xFFFFFF);
		float[][] corner = new float[8][3];
		for (int i = 0; i < 8; i++) {
			float lx = b[4] + ((i & 1) == 0 ? -b[7] : b[7]), ly = b[5] + ((i & 2) == 0 ? -b[8] : b[8]), lz = b[6] + ((i & 4) == 0 ? -b[9] : b[9]);
			corner[i][0] = b[0] + lx * cos - lz * sin;
			corner[i][1] = b[1] + ly;
			corner[i][2] = b[2] + lx * sin + lz * cos;
		}
		int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
		for (int[] f : faces) {
			for (int k : f) {
				vc.vertex(e, corner[k][0], corner[k][1], corner[k][2]).color(c).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
						.normal(e, 0F, 1F, 0F);
			}
			// both sides, so it shows from inside / any angle
			for (int i = 3; i >= 0; i--) {
				int k = f[i];
				vc.vertex(e, corner[k][0], corner[k][1], corner[k][2]).color(c).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
						.normal(e, 0F, 1F, 0F);
			}
		}
	}
}
