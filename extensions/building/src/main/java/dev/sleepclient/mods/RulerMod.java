package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.fx.WorldDraw;
import dev.sleepclient.option.Option;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Ruler (for building): look at a block and press the key to set point A, again for point B. A glowing box shows the
 * area between them and the HUD shows width x height x length, how many blocks fill it, the outline (walls) count
 * and the distance. Press the key while looking at the sky to clear.
 */
public class RulerMod extends HudMod {

	public static RulerMod instance;

	private final Option.Key setKey = key("Set Point Key", 78); // N
	private final Option.Col boxColour = colour("Box Colour", 0xFF7DD3FC);
	private final Option.Col aColour = colour("Point A Colour", 0xFF4ADE80);
	private final Option.Col bColour = colour("Point B Colour", 0xFFFF5C8A);
	private final Option.Bool showDistance = bool("Distance", true);
	private final Option.Bool showWalls = bool("Wall Blocks", true);
	private BlockPos a, b;
	private boolean prev;
	private static boolean hooked;

	public RulerMod() {
		super("Ruler", "ruler", "Measure builds: set 2 points, see size, block count and distance with a glowing box.", 0.5F, 0.06F);
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
				if (instance != null && instance.enabled && instance.a != null) {
					try {
						instance.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Ruler unavailable: " + t);
		}
	}

	@Override
	public void tick() {
		boolean d = mc.currentScreen == null && Keys.down(setKey.code());
		if (d && !prev) {
			HitResult hr = mc.crosshairTarget;
			if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
				BlockPos p = bh.getBlockPos().toImmutable();
				if (a == null || b != null) {
					a = p;
					b = null;
				} else {
					b = p;
				}
			} else {
				a = null;
				b = null;
			}
		}
		prev = d;
	}

	private BlockPos second() {
		if (b != null) {
			return b;
		}
		// before B is set, the box follows what you look at
		if (mc.crosshairTarget instanceof BlockHitResult bh && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
			return bh.getBlockPos();
		}
		return a;
	}

	private void render(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		BlockPos p2 = second();
		if (a == null || p2 == null) {
			return;
		}
		WorldDraw.Lines l = new WorldDraw.Lines(cam);
		double e = 0.004;
		int x0 = Math.min(a.getX(), p2.getX()), y0 = Math.min(a.getY(), p2.getY()), z0 = Math.min(a.getZ(), p2.getZ());
		int x1 = Math.max(a.getX(), p2.getX()) + 1, y1 = Math.max(a.getY(), p2.getY()) + 1, z1 = Math.max(a.getZ(), p2.getZ()) + 1;
		int bc = b == null ? Gfx.withAlpha(boxColour.c(), 140) : boxColour.c();
		l.box(x0 - e, y0 - e, z0 - e, x1 + e, y1 + e, z1 + e, 0.05F, bc);
		l.box(a.getX() - 0.01, a.getY() - 0.01, a.getZ() - 0.01, a.getX() + 1.01, a.getY() + 1.01, a.getZ() + 1.01, 0.035F, aColour.c());
		if (b != null) {
			l.box(b.getX() - 0.01, b.getY() - 0.01, b.getZ() - 0.01, b.getX() + 1.01, b.getY() + 1.01, b.getZ() + 1.01, 0.035F, bColour.c());
			if (showDistance.on()) {
				l.line(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 0.025F, aColour.c(), bColour.c());
			}
		}
		l.draw(m, q);
	}

	@Override
	public int width() {
		return 170;
	}

	@Override
	public int height() {
		return showWalls.on() ? 44 : 34;
	}

	@Override
	public boolean visible() {
		return a != null;
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		BlockPos p2 = second();
		int w = width(), h = height();
		Gfx.rrectFree(c, 0, 0, w, h, 6, 0xD0101218);
		if (a == null || p2 == null) {
			Gfx.textC(c, "Ruler: press " + Keys.name(setKey.code()) + " on a block", w / 2, h / 2 - 4, 0xFFFFFFFF);
			return;
		}
		int dx = Math.abs(a.getX() - p2.getX()) + 1, dy = Math.abs(a.getY() - p2.getY()) + 1, dz = Math.abs(a.getZ() - p2.getZ()) + 1;
		long vol = (long) dx * dy * dz;
		String size = dx + " x " + dy + " x " + dz + (b == null ? dev.sleepclient.Lang.tr("  (press again for B)", "  (carrega outra vez para B)") : "");
		c.drawText(mc.textRenderer, size, 8, 6, 0xFFFFFFFF, true);
		double dist = Math.sqrt(Math.pow(a.getX() - p2.getX(), 2) + Math.pow(a.getY() - p2.getY(), 2) + Math.pow(a.getZ() - p2.getZ(), 2));
		String line2 = vol + dev.sleepclient.Lang.tr(" blocks", " blocos") + (showDistance.on() ? String.format(dev.sleepclient.Lang.tr("  ·  %.1f away", "  ·  a %.1f"), dist) : "");
		c.drawText(mc.textRenderer, line2, 8, 18, 0xFFB9BDC9, false);
		if (showWalls.on()) {
			long inner = (long) Math.max(0, dx - 2) * Math.max(0, dy - 2) * Math.max(0, dz - 2);
			long stacks = (vol + 63) / 64;
			c.drawText(mc.textRenderer, dev.sleepclient.Lang.tr("Hollow: ", "Oco: ") + (vol - inner) + "  ·  " + stacks + " stacks", 8, 30, 0xFF7DD3FC, false);
		}
	}
}
