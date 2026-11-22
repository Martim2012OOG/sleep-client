package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Hitboxes: the real 3D boxes around players and mobs (like F3 + B), with the colour you choose (or rainbow), the thickness, the
 * range and a key to turn them on and off. Drawn in the world, so they follow the perspective and hide behind blocks like the game's own.
 */
public class HitboxesMod extends Mod {

	public static HitboxesMod instance;

	private final Option.Num range = num("Range", 48, 4, 160, 2, " blocks");
	private final Option.Bool playersOnly = bool("Players Only", false);
	private final Option.Bool showSelf = bool("Show My Own (third person)", true);
	private final Option.Col lineColour = colour("Colour", 0xFFFFFFFF);
	private final Option.Bool rainbow = bool("Rainbow", false);
	private final Option.Num rainbowSpeed = num("Rainbow Speed", 4, 1, 20, 1, "");
	private final Option.Num width = num("Line Thickness", 2, 1, 8, 1, "");
	private final Option.Bool eyeLine = bool("Eye Height Line (red)", true);
	private final Option.Bool lookLine = bool("Look Direction (blue)", false);
	private final Option.Key toggleKey = key("Start / Stop Key", -1);
	private boolean shown = true, prevKey;
	private static boolean hooked;

	public HitboxesMod() {
		super("Hitboxes", "hitboxes", "The real 3D hitboxes of players and mobs (like F3+B): your colour, rainbow, thickness, range and a start / stop key.", Category.VISUAL);
		instance = this;
		rainbowSpeed.showIf(rainbow::on);
		lineColour.showIf(() -> !rainbow.on());
	}

	@Override
	public void init() {
		super.init();
		if (!hooked) {
			hooked = true;
			try {
				WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> draw(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos));
			} catch (Throwable t) {
				System.err.println("[Sleep] hitboxes: could not hook the world rendering: " + t);
			}
		}
	}

	@Override
	public void tick() {
		boolean down = mc.currentScreen == null && Keys.down(toggleKey.code());
		if (down && !prevKey) {
			shown = !shown;
		}
		prevKey = down;
	}

	private int colour() {
		if (rainbow.on()) {
			float h = ((System.currentTimeMillis() % 10000L) / 10000F * rainbowSpeed.f()) % 1F;
			float x = 1 - Math.abs((h * 6) % 2 - 1);
			float r, g, b;
			switch ((int) (h * 6) % 6) {
				case 0 -> { r = 1; g = x; b = 0; }
				case 1 -> { r = x; g = 1; b = 0; }
				case 2 -> { r = 0; g = 1; b = x; }
				case 3 -> { r = 0; g = x; b = 1; }
				case 4 -> { r = x; g = 0; b = 1; }
				default -> { r = 1; g = 0; b = x; }
			}
			return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
		}
		return lineColour.c() | 0xFF000000;
	}

	private static void line(MatrixStack.Entry en, VertexConsumer vc, double cx, double cy, double cz, double x0, double y0, double z0, double x1, double y1, double z1, int col, float w) {
		float nx = (float) (x1 - x0), ny = (float) (y1 - y0), nz = (float) (z1 - z0);
		float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (l < 1e-6F) {
			return;
		}
		nx /= l;
		ny /= l;
		nz /= l;
		vc.vertex(en, (float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).color(col).normal(en, nx, ny, nz).lineWidth(w);
		vc.vertex(en, (float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).color(col).normal(en, nx, ny, nz).lineWidth(w);
	}

	private static void box(MatrixStack.Entry en, VertexConsumer vc, double cx, double cy, double cz, double a, double b, double c, double d, double e, double f, int col, float w) {
		double[] xs = {a, d}, ys = {b, e}, zs = {c, f};
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				line(en, vc, cx, cy, cz, xs[0], ys[i], zs[j], xs[1], ys[i], zs[j], col, w); // along x
				line(en, vc, cx, cy, cz, xs[i], ys[0], zs[j], xs[i], ys[1], zs[j], col, w); // along y
				line(en, vc, cx, cy, cz, xs[i], ys[j], zs[0], xs[i], ys[j], zs[1], col, w); // along z
			}
		}
	}

	private static void draw(MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam) {
		HitboxesMod m = instance;
		if (m == null || !m.enabled || !m.shown || m.mc.world == null || m.mc.player == null) {
			return;
		}
		float td = m.mc.getRenderTickCounter().getTickProgress(false);
		double r2 = m.range.f() * m.range.f();
		List<double[]> boxes = new ArrayList<>();
		for (Entity e : m.mc.world.getEntities()) {
			if (!(e instanceof LivingEntity) || e.isRemoved() || e.squaredDistanceTo(m.mc.player) > r2) {
				continue;
			}
			if (m.playersOnly.on() && !(e instanceof PlayerEntity)) {
				continue;
			}
			if (e == m.mc.player && (!m.showSelf.on() || m.mc.options.getPerspective().isFirstPerson())) {
				continue;
			}
			Vec3d p = e.getLerpedPos(td);
			Box b = e.getBoundingBox().offset(p.x - e.getX(), p.y - e.getY(), p.z - e.getZ());
			double eye = p.y + e.getStandingEyeHeight();
			Vec3d look = e.getRotationVec(td);
			boxes.add(new double[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, eye, p.x, p.z, look.x, look.y, look.z});
		}
		if (boxes.isEmpty()) {
			return;
		}
		int col = m.colour();
		float w = m.width.f();
		boolean eyeOn = m.eyeLine.on(), lookOn = m.lookLine.on();
		queue.submitCustom(matrices, RenderLayers.lines(), (en, vc) -> {
			for (double[] a : boxes) {
				box(en, vc, cam.x, cam.y, cam.z, a[0], a[1], a[2], a[3], a[4], a[5], col, w);
				if (eyeOn) {
					box(en, vc, cam.x, cam.y, cam.z, a[0], a[6], a[2], a[3], a[6], a[5], 0xFFFF2020, w);
				}
				if (lookOn) {
					line(en, vc, cam.x, cam.y, cam.z, a[7], a[6], a[8], a[7] + a[9] * 2, a[6] + a[10] * 2, a[8] + a[11] * 2, 0xFF3060FF, w);
				}
			}
		});
	}
}
