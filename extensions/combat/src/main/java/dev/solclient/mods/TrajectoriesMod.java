package dev.solclient.mods;

import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Trajectories: while you hold an ender pearl, bow, crossbow, trident, snowball, egg, potion, XP bottle or wind charge,
 * a glowing line shows exactly where it will fly and a marker shows where it lands (same physics as Minecraft:
 * speed, gravity, air drag, your own movement). Great for pearl clutches and bow fights.
 */
public class TrajectoriesMod extends Mod {

	public static TrajectoriesMod instance;

	private final Option.Col lineColour = colour("Line Colour", 0xFF7DD3FC);
	private final Option.Col landColour = colour("Landing Colour", 0xFFFF5C8A);
	private final Option.Num width = num("Line Width", 100, 30, 300, 10, "%");
	private final Option.Bool fade = bool("Fade Out", true);
	private final Option.Bool marker = bool("Landing Marker", true);
	private final Option.Bool bowOnlyDrawn = bool("Bow: Only While Drawing", true);
	private static boolean hooked;

	public TrajectoriesMod() {
		super("Trajectories", "trajectories", "Shows where your pearl / arrow / potion will fly and land before you throw it.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
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
					long t0 = System.nanoTime();
					try {
						instance.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
					instance.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Caos] Trajectories unavailable: " + t);
		}
	}

	/** speed, gravity, drag, pitch offset for the item, or null if it is not a projectile. */
	private float[] physics(ItemStack st) {
		var p = mc.player;
		if (st.isOf(Items.ENDER_PEARL) || st.isOf(Items.SNOWBALL) || st.isOf(Items.EGG)) {
			return new float[] {1.5F, 0.03F, 0.99F, 0F};
		}
		if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.LINGERING_POTION)) {
			return new float[] {0.5F, 0.05F, 0.99F, -20F};
		}
		if (st.isOf(Items.EXPERIENCE_BOTTLE)) {
			return new float[] {0.7F, 0.07F, 0.99F, -20F};
		}
		if (st.isOf(Items.WIND_CHARGE)) {
			return new float[] {1.5F, 0.0F, 1.0F, 0F};
		}
		if (st.isOf(Items.TRIDENT)) {
			if (!p.isUsingItem()) {
				return null;
			}
			return new float[] {2.5F, 0.05F, 0.99F, 0F};
		}
		if (st.isOf(Items.BOW)) {
			if (!p.isUsingItem()) {
				return bowOnlyDrawn.on() ? null : new float[] {3.0F, 0.05F, 0.99F, 0F};
			}
			float pull = BowItem.getPullProgress(p.getItemUseTime());
			if (pull < 0.1F) {
				return null;
			}
			return new float[] {3.0F * pull, 0.05F, 0.99F, 0F};
		}
		if (st.isOf(Items.CROSSBOW)) {
			return CrossbowItem.isCharged(st) ? new float[] {3.15F, 0.05F, 0.99F, 0F} : null;
		}
		return null;
	}

	private void render(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		var p = mc.player;
		if (p == null || mc.world == null || mc.currentScreen != null) {
			return;
		}
		ItemStack st = p.getStackInHand(Hand.MAIN_HAND);
		float[] ph = physics(st);
		if (ph == null) {
			st = p.getStackInHand(Hand.OFF_HAND);
			ph = physics(st);
		}
		if (ph == null) {
			return;
		}
		float yaw = p.getYaw(), pitch = p.getPitch();
		// start where Minecraft spawns the projectile (eye height, a little lower)
		boolean firstPerson = mc.options.getPerspective() == Perspective.FIRST_PERSON;
		Vec3d eye = firstPerson ? cam : p.getEyePos();
		double x = eye.x, y = eye.y - 0.1, z = eye.z;
		double pr = Math.toRadians(pitch + ph[3]), yr = Math.toRadians(yaw);
		double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
		double vx = dx * ph[0], vy = dy * ph[0], vz = dz * ph[0];
		// your own movement is added (horizontal always, vertical only when in the air)
		Vec3d pv = p.getVelocity();
		vx += pv.x;
		vz += pv.z;
		if (!p.isOnGround()) {
			vy += pv.y;
		}
		WorldDraw.Lines lines = new WorldDraw.Lines(cam);
		float w = 0.035F * width.f() / 100F;
		int lc = lineColour.c();
		Vec3d hit = null;
		Direction face = null;
		double sx = x, sy = y, sz = z;
		for (int i = 0; i < 300; i++) {
			Vec3d from = new Vec3d(x, y, z), to = new Vec3d(x + vx, y + vy, z + vz);
			BlockHitResult hr = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
			if (hr != null && hr.getType() != HitResult.Type.MISS) {
				hit = hr.getPos();
				face = hr.getSide();
				int a = alphaAt(i, lc);
				lines.line(x, y, z, hit.x, hit.y, hit.z, w, a, a);
				break;
			}
			int a0 = alphaAt(i, lc), a1 = alphaAt(i + 1, lc);
			// skip the first bit right in front of your eyes
			if (Math.abs(x - sx) + Math.abs(y - sy) + Math.abs(z - sz) > 0.6) {
				lines.line(x, y, z, to.x, to.y, to.z, w, a0, a1);
			}
			x = to.x;
			y = to.y;
			z = to.z;
			vx *= ph[2];
			vy *= ph[2];
			vz *= ph[2];
			vy -= ph[1];
			if (y < mc.world.getBottomY() - 16) {
				break;
			}
		}
		if (hit != null && marker.on()) {
			int c = landColour.c();
			double r = 0.35;
			if (face == null || face.getAxis() == Direction.Axis.Y) {
				double hy = hit.y + 0.02;
				lines.line(hit.x - r, hy, hit.z - r, hit.x + r, hy, hit.z - r, w * 1.4F, c);
				lines.line(hit.x + r, hy, hit.z - r, hit.x + r, hy, hit.z + r, w * 1.4F, c);
				lines.line(hit.x + r, hy, hit.z + r, hit.x - r, hy, hit.z + r, w * 1.4F, c);
				lines.line(hit.x - r, hy, hit.z + r, hit.x - r, hy, hit.z - r, w * 1.4F, c);
				lines.line(hit.x - r * 0.4, hy, hit.z, hit.x + r * 0.4, hy, hit.z, w * 1.4F, c);
				lines.line(hit.x, hy, hit.z - r * 0.4, hit.x, hy, hit.z + r * 0.4, w * 1.4F, c);
			} else {
				// on a wall: a cross on the wall
				lines.line(hit.x, hit.y - r, hit.z, hit.x, hit.y + r, hit.z, w * 1.4F, c);
				double ox = face.getAxis() == Direction.Axis.X ? 0 : r, oz = face.getAxis() == Direction.Axis.Z ? 0 : r;
				lines.line(hit.x - ox, hit.y, hit.z - oz, hit.x + ox, hit.y, hit.z + oz, w * 1.4F, c);
			}
		}
		lines.draw(m, q);
	}

	private int alphaAt(int i, int c) {
		int a = (c >>> 24) & 255;
		if (fade.on()) {
			a = Math.round(a * Math.max(0.25F, 1F - i / 120F));
		}
		return a << 24 | (c & 0xFFFFFF);
	}
}
