package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.fx.Project;
import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Trident Assist: everything to use a trident well, only visual (it never aims or moves for you).
 * - a charge bar under the crosshair that turns green (with a ding) when the throw / riptide is ready;
 * - Riptide: the path you will fly and where you land, and a warning when there is no water or rain;
 * - normal tridents: where the throw will go;
 * - your thrown trident: a marker with its distance, even off screen, so you never lose it.
 */
public class TridentAssistMod extends Mod {

	public static TridentAssistMod instance;

	private final Option.Bool chargeBar = bool("Charge Bar", true);
	private final Option.Bool readySound = bool("Sound When Ready", true);
	private final Option.Bool riptidePath = bool("Riptide Path", true);
	private final Option.Bool throwPath = bool("Throw Path", true);
	private final Option.Bool tracker = bool("Find My Trident", true);
	private final Option.Col colour = colour("Colour", 0xFF5EEAD4);
	private static final int READY_TICKS = 10; // TridentItem.MIN_DRAW_DURATION
	private static boolean hooked;
	private boolean dinged;

	public TridentAssistMod() {
		super("Trident Assist", "trident_assist", "Charge bar, Riptide path and landing, throw path and a marker on your thrown trident.",
				Category.VISUAL);
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
					long t0 = System.nanoTime();
					try {
						instance.world(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
					instance.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Trident Assist path unavailable: " + t);
		}
	}

	private ItemStack chargingTrident() {
		PlayerEntity p = mc.player;
		if (p == null || !p.isUsingItem()) {
			return null;
		}
		ItemStack st = p.getActiveItem();
		return st != null && st.isOf(Items.TRIDENT) ? st : null;
	}

	private float riptide(ItemStack st) {
		try {
			return EnchantmentHelper.getTridentSpinAttackStrength(st, mc.player);
		} catch (RuntimeException e) {
			return 0F;
		}
	}

	// ------------------------------------------------------------------ world
	private void world(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		PlayerEntity p = mc.player;
		if (p == null || mc.world == null) {
			return;
		}
		WorldDraw.Lines lines = new WorldDraw.Lines(cam);
		int col = colour.c();
		ItemStack st = chargingTrident();
		if (st != null && mc.currentScreen == null) {
			float strength = riptide(st);
			boolean ready = p.getItemUseTime() >= READY_TICKS;
			int c = ready ? col : (col & 0x00FFFFFF) | 0x77000000;
			if (strength > 0 && riptidePath.on()) {
				if (p.isTouchingWaterOrRain()) {
					riptide(lines, p, strength, c);
				}
			} else if (strength <= 0 && throwPath.on() && !(TrajectoriesMod.instance != null && TrajectoriesMod.instance.enabled)) {
				thrown(lines, p, cam, c);
			}
		}
		if (tracker.on()) {
			float td = mc.getRenderTickCounter().getTickProgress(true);
			for (Entity e : mc.world.getEntities()) {
				if (e instanceof TridentEntity t && t.getOwner() == p) {
					Vec3d tp = e.getLerpedPos(td);
					double r = 0.35;
					lines.box(tp.x - r, tp.y - 0.1, tp.z - r, tp.x + r, tp.y + 0.6, tp.z + r, 0.03F, col);
				}
			}
		}
		lines.draw(m, q);
	}

	/** Riptide: the player is pushed along the look direction (plus 1.2 up if on the ground), then falls normally. */
	private void riptide(WorldDraw.Lines lines, PlayerEntity p, float strength, int col) {
		double yr = Math.toRadians(p.getYaw()), pr = Math.toRadians(p.getPitch());
		double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		Vec3d v0 = p.getVelocity();
		double vx = v0.x + dx * strength / len, vy = v0.y + dy * strength / len, vz = v0.z + dz * strength / len;
		double x = p.getX(), y = p.getY() + (p.isOnGround() ? 1.2 : 0) + 0.9, z = p.getZ();
		Vec3d hit = sim(lines, p, x, y, z, vx, vy, vz, 0.08, 0.98, 0.91, col, 0.9);
		if (hit != null) {
			landing(lines, hit, col);
		}
	}

	private void thrown(WorldDraw.Lines lines, PlayerEntity p, Vec3d cam, int col) {
		double yr = Math.toRadians(p.getYaw()), pr = Math.toRadians(p.getPitch());
		double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
		Vec3d eye = mc.options.getPerspective() == Perspective.FIRST_PERSON ? cam : p.getEyePos();
		Vec3d pv = p.getVelocity();
		double vx = dx * 2.5 + pv.x, vy = dy * 2.5 + (p.isOnGround() ? 0 : pv.y), vz = dz * 2.5 + pv.z;
		Vec3d hit = sim(lines, p, eye.x, eye.y - 0.1, eye.z, vx, vy, vz, 0.05, 0.99, 0.99, col, 0);
		if (hit != null) {
			landing(lines, hit, col);
		}
	}

	/** Steps the motion tick by tick and draws it. Returns where it hits a block (or null). yOff = body centre. */
	private Vec3d sim(WorldDraw.Lines lines, PlayerEntity p, double x, double y, double z, double vx, double vy, double vz, double gravity,
			double dragY, double dragXZ, int col, double yOff) {
		double sx = x, sy = y, sz = z;
		for (int i = 0; i < 160; i++) {
			Vec3d from = new Vec3d(x, y, z), to = new Vec3d(x + vx, y + vy, z + vz);
			BlockHitResult hr = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
			int a = fade(col, i);
			if (hr != null && hr.getType() != HitResult.Type.MISS) {
				Vec3d h = hr.getPos();
				lines.line(x, y, z, h.x, h.y, h.z, 0.04F, a);
				return new Vec3d(h.x, h.y - yOff * (hr.getSide().getAxis() == net.minecraft.util.math.Direction.Axis.Y ? 1 : 0), h.z);
			}
			if (Math.abs(x - sx) + Math.abs(y - sy) + Math.abs(z - sz) > 0.6) {
				lines.line(x, y, z, to.x, to.y, to.z, 0.04F, a, fade(col, i + 1));
			}
			x = to.x;
			y = to.y;
			z = to.z;
			vy = (vy - gravity) * dragY;
			vx *= dragXZ;
			vz *= dragXZ;
			if (y < mc.world.getBottomY() - 16) {
				break;
			}
		}
		return null;
	}

	private static int fade(int c, int i) {
		int a = (c >>> 24) & 255;
		return Math.round(a * Math.max(0.25F, 1F - i / 90F)) << 24 | (c & 0xFFFFFF);
	}

	private static void landing(WorldDraw.Lines lines, Vec3d h, int col) {
		double r = 0.45, y = h.y + 0.03;
		int n = 20;
		for (int i = 0; i < n; i++) {
			double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
			lines.line(h.x + Math.cos(a0) * r, y, h.z + Math.sin(a0) * r, h.x + Math.cos(a1) * r, y, h.z + Math.sin(a1) * r, 0.05F, col);
		}
		lines.line(h.x - 0.15, y, h.z, h.x + 0.15, y, h.z, 0.05F, col);
		lines.line(h.x, y, h.z - 0.15, h.x, y, h.z + 0.15, 0.05F, col);
	}

	// ------------------------------------------------------------------ HUD
	public void draw(DrawContext c) {
		PlayerEntity p = mc.player;
		if (p == null || mc.world == null || mc.options.hudHidden) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		int col = colour.c() | 0xFF000000;
		ItemStack st = chargingTrident();
		if (st == null) {
			dinged = false;
		} else if (chargeBar.on() && mc.currentScreen == null) {
			float strength = riptide(st);
			boolean noWater = strength > 0 && !p.isTouchingWaterOrRain();
			float k = Math.min(1F, p.getItemUseTime() / (float) READY_TICKS);
			boolean ready = k >= 1F;
			if (ready && !dinged) {
				dinged = true;
				if (readySound.on() && !noWater) {
					p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5F, 1.9F);
				}
			}
			int w = 34, x = sw / 2 - w / 2, y = sh / 2 + 10;
			Gfx.rrectFree(c, x - 1, y - 1, w + 2, 5, 2.5F, 0x99000000);
			int fill = noWater ? 0xFFEF4444 : ready ? 0xFF4ADE80 : col;
			Gfx.rrectFree(c, x, y, Math.max(2, w * k), 3, 1.5F, fill);
			String label = noWater ? dev.solclient.Lang.tr("No water / rain", "Sem água / chuva")
					: ready ? (strength > 0 ? "RIPTIDE" : dev.solclient.Lang.tr("READY", "PRONTO")) : null;
			if (label != null) {
				Gfx.textRaw(c, label, sw / 2 - Gfx.twRaw(label) / 2, y + 6, fill);
			}
		}
		if (tracker.on() && Project.begin()) {
			float td = mc.getRenderTickCounter().getTickProgress(true);
			for (Entity e : mc.world.getEntities()) {
				if (!(e instanceof TridentEntity t) || t.getOwner() != p) {
					continue;
				}
				Vec3d tp = e.getLerpedPos(td);
				boolean on = Project.edge(tp.x, tp.y + 0.8, tp.z, 18);
				String d = Math.round(Math.sqrt(p.squaredDistanceTo(e))) + "m";
				int bw = Gfx.twRaw(d) + 22;
				int bx = Math.max(2, Math.min(sw - bw - 2, Project.sx - bw / 2)), by = Math.max(2, Math.min(sh - 16, Project.sy - 7));
				Gfx.rrectFree(c, bx, by, bw, 14, 4, on ? 0x99000000 : 0xCC000000);
				Gfx.tex(c, dev.solclient.gui.Theme.iconPublic("trident_assist"), bx + 3, by + 2, 10, 10, 64, 64, col);
				Gfx.textRaw(c, d, bx + 16, by + 3, 0xFFFFFFFF);
			}
		}
	}
}
