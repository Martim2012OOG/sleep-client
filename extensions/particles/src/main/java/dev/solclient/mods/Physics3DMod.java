package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.GrindstoneScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.client.gui.screen.ingame.StonecutterScreen;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3x2f;

/**
 * 3D Physics.
 * <p>
 * Blocks: a broken block falls apart into a few small, spinning 3D cubes made of the block's own texture. They use
 * the same single-batch cube pool as Explosion Debris (hard limits, shrinks away, automatic quality drop when the FPS is
 * low), so there is no lag.
 * <p>
 * Screens: the inventory, crafting table, furnaces and the other workstations lean towards your cursor like a thick
 * 3D slab with a shadow. Never the Caos menu, the pause menu, or chests: only the screens listed below. The tilt is
 * always pivoted on the cursor, so what you see under the cursor is exactly what you click.
 */
public class Physics3DMod extends Mod {

	public static Physics3DMod instance;

	// ---- blocks
	private final Option.Header hBlocks = header("Broken Blocks");
	private final Option.Bool blocks = bool("3D Broken Blocks", true).tip("Broken blocks fall apart into big 3D chunks with the block's texture.");
	private final Option.Num amount = num("Pieces Per Block", 4, 1, 16, 1, "");
	private final Option.Num pieceSize = num("Piece Size", 100, 50, 200, 10, "%");
	private final Option.Num lifetime = num("Lifetime", 4, 1, 10, 1, " s");
	private final Option.Num speed = num("Pop Strength", 100, 40, 200, 10, "%");
	private final Option.Bool spin = bool("Spin", true);
	private final Option.Bool keepFlat = bool("Keep Flat Particles Too", false).tip("Also keep the normal flat vanilla break particles.");
	private final Option.Num maxPieces = num("Max Pieces", 400, 100, 1000, 50, "");
	private final Option.Bool autoQuality = bool("Auto Quality (keeps FPS)", true).tip("Fewer pieces automatically when your FPS drops. No lag.");
	private final Option.Num range = num("Max Distance", 32, 8, 64, 4, " blocks");

	// ---- screens
	private final Option.Header hGui = header("3D Screens (Beta)");
	private final Option.Bool gui = bool("3D Screens", true).tip("BETA, it does not always look right. Inventory, crafting table, furnace and the other workstations lean towards your cursor like a 3D slab. Never the Caos menu, the pause menu or chests.");
	private final Option.Bool gInv = bool("Inventory", true);
	private final Option.Bool gCraft = bool("Crafting Table", true);
	private final Option.Bool gFurnace = bool("Furnaces", true);
	private final Option.Bool gOther = bool("Anvil, Enchanting, Brewing, Smithing...", true).tip("Anvil, enchanting table, brewing stand, smithing table, stonecutter, loom, grindstone and cartography table.");
	private final Option.Choice style = choice("Style", 0, "Floating Screen", "Lean To Cursor");
	private final Option.Num angle = num("Angle", 14, 6, 30, 1, "°");
	private final Option.Choice turn = choice("Turned Towards", 0, "Left", "Right");
	private final Option.Bool floatAnim = bool("Floating Animation", true);
	private final Option.Bool glow = bool("Glow Frame", false);
	private final Option.Bool clear = bool("Keep The World Clear", true).tip("No dark veil and no blur behind the screen, so the world stays visible around it.");
	private final Option.Num tilt = num("Tilt", 100, 20, 200, 10, "%");
	private final Option.Num thickness = num("Thickness", 5, 0, 12, 1, "");
	private final Option.Bool shadow = bool("Shadow", true);
	private final Option.Bool pop = bool("Pop-In Animation", true);

	// ---- state
	private int tickBreaks;
	private Screen last;
	private long openedAt;
	private float sx, sy; // smoothed cursor offset, -1..1
	private long lastFrame;
	private boolean floating;
	private int fx0, fy0, fw, fh, farDir;

	public Physics3DMod() {
		super("3D Physics", "physics_3d", "Broken blocks fall apart into real 3D cubes, and the inventory, crafting table and furnaces become 3D slabs. No lag.", Category.VISUAL);
		instance = this;
		amount.showIf(blocks::on);
		pieceSize.showIf(blocks::on);
		lifetime.showIf(blocks::on);
		speed.showIf(blocks::on);
		spin.showIf(blocks::on);
		keepFlat.showIf(blocks::on);
		maxPieces.showIf(blocks::on);
		autoQuality.showIf(blocks::on);
		range.showIf(blocks::on);
		gInv.showIf(gui::on);
		gCraft.showIf(gui::on);
		gFurnace.showIf(gui::on);
		gOther.showIf(gui::on);
		style.showIf(gui::on);
		angle.showIf(() -> gui.on() && style.idx() == 0);
		turn.showIf(() -> gui.on() && style.idx() == 0);
		floatAnim.showIf(() -> gui.on() && style.idx() == 0);
		glow.showIf(() -> gui.on() && style.idx() == 0);
		clear.showIf(() -> gui.on() && style.idx() == 0);
		tilt.showIf(() -> gui.on() && style.idx() == 1);
		thickness.showIf(gui::on);
		shadow.showIf(gui::on);
		pop.showIf(gui::on);
	}

	@Override
	protected void onDisable() {
		sync();
	}

	@Override
	protected void onEnable() {
		sync();
	}

	private void sync() {
		var ed = ExplosionDebrisMod.instance;
		if (ed != null) {
			ed.external = enabled;
		}
	}

	// ---------------------------------------------------------------- broken blocks
	private float quality() {
		if (!autoQuality.on()) {
			return 1F;
		}
		int fps = mc.getCurrentFps();
		return fps <= 0 ? 1F : fps < 25 ? 0.2F : fps < 40 ? 0.45F : fps < 55 ? 0.75F : 1F;
	}

	/** A block broke. Returns true when the flat vanilla particles should not be spawned. */
	public boolean onBreak(BlockPos pos, BlockState st) {
		var ed = ExplosionDebrisMod.instance;
		if (!enabled || !blocks.on() || ed == null || mc.world == null || mc.player == null || st == null || st.isAir()) {
			return false;
		}
		ed.external = true;
		double dx = pos.getX() + 0.5 - mc.player.getX(), dy = pos.getY() + 0.5 - mc.player.getY(), dz = pos.getZ() + 0.5 - mc.player.getZ();
		double r = range.f();
		if (dx * dx + dy * dy + dz * dz > r * r) {
			return false;
		}
		long t0 = System.nanoTime();
		tickBreaks++;
		// a big explosion breaks hundreds of blocks in one tick: fewer pieces each so the total stays small
		int count = Math.round(amount.f() * quality());
		if (tickBreaks > 30) {
			count = Math.min(count, 1);
		} else if (tickBreaks > 10) {
			count = Math.min(count, 2);
		}
		count = Math.max(count, 1);
		ed.spawnBreak(pos, st, count, speed.f() / 100F, pieceSize.f() / 100F, Math.round(lifetime.f() * 20F), spin.on(), maxPieces.i());
		addCost(System.nanoTime() - t0);
		return !keepFlat.on();
	}

	@Override
	public void tick() {
		tickBreaks = 0;
		var ed = ExplosionDebrisMod.instance;
		if (ed != null && !ed.enabled && blocks.on()) {
			ed.tick(); // Explosion Debris is off: its pool still has to move
		}
	}

	// ---------------------------------------------------------------- 3D screens
	/** Only these. Never SolMenuScreen, the pause menu, chests, shulker boxes, hoppers... */
	public boolean allowed(Screen s) {
		if (!enabled || !gui.on() || s == null) {
			return false;
		}
		if (s instanceof InventoryScreen) {
			return gInv.on();
		}
		if (s instanceof CraftingScreen) {
			return gCraft.on();
		}
		if (s instanceof AbstractFurnaceScreen<?>) {
			return gFurnace.on();
		}
		if (s instanceof AnvilScreen || s instanceof EnchantmentScreen || s instanceof BrewingStandScreen || s instanceof SmithingScreen
				|| s instanceof StonecutterScreen || s instanceof LoomScreen || s instanceof GrindstoneScreen || s instanceof CartographyTableScreen) {
			return gOther.on();
		}
		return false;
	}

	private static float ease(float t) {
		t = Math.max(0F, Math.min(1F, t));
		float c1 = 1.2F, c3 = c1 + 1F;
		return 1F + c3 * (float) Math.pow(t - 1F, 3) + c1 * (float) Math.pow(t - 1F, 2);
	}

	/**
	 * Called before the screen draws: pushes the tilt. The point under the cursor stays where it is (it is the pivot),
	 * so clicks and hover always match what is shown. Returns true when {@link #end} has to be called.
	 */
	public boolean begin(DrawContext c, Screen s, int mx, int my, int px, int py, int pw, int ph) {
		if (!allowed(s)) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (s != last) {
			last = s;
			openedAt = now;
			sx = 0F;
			sy = 0F;
			lastFrame = now;
		}
		float dt = Math.min(0.1F, (now - lastFrame) / 1000F);
		lastFrame = now;
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		float tx = Math.max(-1F, Math.min(1F, (mx - w / 2F) / (w / 2F))), ty = Math.max(-1F, Math.min(1F, (my - h / 2F) / (h / 2F)));
		float k = Math.min(1F, dt * 10F);
		sx += (tx - sx) * k;
		sy += (ty - sy) * k;
		if (style.idx() == 0) {
			floating = true;
			return beginFloating(c, s, mx, my, px, py, pw, ph, now);
		}
		floating = false;
		dev.solclient.fx.ScreenClear.screen = null;
		float deg = 7F * tilt.f() / 100F;
		float ay = sx * (float) Math.toRadians(deg), ax = -sy * (float) Math.toRadians(deg);
		float a = (float) Math.cos(ay), d = (float) Math.cos(ax);
		float b = (float) Math.sin(ay) * 0.30F, cc = (float) Math.sin(ax) * 0.30F;
		float grow = 1F;
		if (pop.on()) {
			float p = ease((now - openedAt) / 220F);
			grow = 0.86F + 0.14F * p;
		}
		var ms = c.getMatrices();
		ms.pushMatrix();
		ms.translate(mx, my);
		ms.mul(new Matrix3x2f(a * grow, b * grow, cc * grow, d * grow, 0F, 0F));
		ms.translate(-mx, -my);
		// the slab behind the panel: a soft shadow and a stack of darker copies = the thickness of the block
		if (pw > 0 && ph > 0) {
			float ox = -sx * 1.1F, oy = -sy * 1.1F + 0.4F;
			if (shadow.on()) {
				for (int i = 0; i < 5; i++) {
					int al = 22 - i * 4;
					int grow2 = 2 + i * 2;
					int sxo = Math.round(ox * 3F), syo = Math.round(oy * 3F) + 4;
					c.fill(px - grow2 + sxo, py - grow2 + syo, px + pw + grow2 + sxo, py + ph + grow2 + syo, al << 24);
				}
			}
			int layers = thickness.i();
			for (int i = layers; i >= 1; i--) {
				float f = i / (float) Math.max(1, layers);
				int shade = 0x24 + Math.round((1F - f) * 0x30);
				int col = 0xFF000000 | shade << 16 | shade << 8 | shade + 6;
				int ax2 = Math.round(ox * i), ay2 = Math.round(oy * i);
				c.fill(px + ax2, py + ay2, px + pw + ax2, py + ph + ay2, col);
			}
		}
		return true;
	}

	/**
	 * "Floating Screen": the panel hangs turned in front of you (fixed pose, a little parallax from the cursor), with a thick edge, a
	 * shadow, a glowing frame and a dark gradient on the far side so it reads as depth. The world stays clear behind it.
	 */
	private boolean beginFloating(DrawContext c, Screen s, int mx, int my, int px, int py, int pw, int ph, long now) {
		dev.solclient.fx.ScreenClear.screen = clear.on() ? s : null;
		float p = ease((now - openedAt) / 340F);
		float t = now / 1000F;
		int sign = turn.idx() == 0 ? 1 : -1;
		float yawDeg = sign * angle.f() * p + sx * 3F + (floatAnim.on() ? (float) Math.sin(t * 0.9F) * 1.3F : 0F);
		float pitchDeg = -3.5F * p + sy * 2F + (floatAnim.on() ? (float) Math.sin(t * 1.3F + 1F) * 0.8F : 0F);
		float ay = (float) Math.toRadians(yawDeg), ax = (float) Math.toRadians(pitchDeg);
		float a = (float) Math.cos(ay), d = (float) Math.cos(ax);
		float b = (float) Math.sin(ay) * 0.55F, cc = (float) Math.sin(ax) * 0.30F;
		float grow = 0.9F + 0.1F * p;
		fx0 = px;
		fy0 = py;
		fw = pw;
		fh = ph;
		farDir = sign; // +1: the right side is the far one
		var ms = c.getMatrices();
		ms.pushMatrix();
		// pivoted on the cursor (like the old tilt): what is under the cursor is exactly what you click
		ms.translate(mx, my);
		ms.mul(new Matrix3x2f(a * grow, b * grow, cc * grow, d * grow, 0F, 0F));
		ms.translate(-mx, -my);
		float ox = -sign * 1.4F, oy = 0.3F;
		if (shadow.on()) {
			for (int i = 0; i < 5; i++) {
				int al = Math.round((26 - i * 4) * p);
				int g = 2 + i * 2;
				c.fill(px - g + Math.round(ox * 4F), py - g + 6, px + pw + g + Math.round(ox * 4F), py + ph + g + 6, al << 24);
			}
		}
		if (glow.on()) {
			int ga = Math.round(80 * p);
			for (int i = 0; i < 5; i++) {
				int al = Math.max(0, ga - i * 14), o = 2 + i * 2;
				c.fill(px - o, py - o, px + pw + o, py - o + 2, al << 24 | 0x6FD8FF);
				c.fill(px - o, py + ph + o - 2, px + pw + o, py + ph + o, al << 24 | 0x6FD8FF);
				c.fill(px - o, py - o, px - o + 2, py + ph + o, al << 24 | 0x6FD8FF);
				c.fill(px + pw + o - 2, py - o, px + pw + o, py + ph + o, al << 24 | 0x6FD8FF);
			}
		}
		int layers = thickness.i();
		for (int i = layers; i >= 1; i--) {
			float f = i / (float) Math.max(1, layers);
			int shade = 0x24 + Math.round((1F - f) * 0x30);
			int col = 0xFF000000 | shade << 16 | shade << 8 | shade + 6;
			c.fill(px + Math.round(ox * i), py + Math.round(oy * i), px + pw + Math.round(ox * i), py + ph + Math.round(oy * i), col);
		}
		return true;
	}

	public void end(DrawContext c) {
		if (floating && fw > 0) {
			// depth cue: the far side gets darker
			int n = 16, sw = Math.max(1, fw / n);
			for (int i = 0; i < n; i++) {
				float k = i / (float) (n - 1);
				float far = farDir > 0 ? k : 1F - k;
				int al = Math.round(60 * far * far);
				int x0 = fx0 + i * sw, x1 = i == n - 1 ? fx0 + fw : x0 + sw;
				c.fill(x0, fy0, x1, fy0 + fh, al << 24);
			}
		}
		c.getMatrices().popMatrix();
	}
}
