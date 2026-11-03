package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.fx.Project;
import dev.solclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * TNT Timer / TNT Cooldown (like Lunar): the time left floats above every lit TNT and counts down
 * (green -> yellow -> red), with a small bar that empties as it gets closer to exploding.
 */
public class TntTimerMod extends Mod {

	public static TntTimerMod instance;

	public final Option.Choice style = choice("Style", 0, "Sleep Label", "Nametag");
	public final Option.Choice format = choice("Format", 0, "2.05", "2.1s", "3s");
	public final Option.Bool colourByTime = bool("Colour By Time", true);
	public final Option.Bool bar = bool("Fuse Bar", true);
	public final Option.Num scale = num("Size", 100, 20, 400, 10, "%");
	public final Option.Num range = num("Max Distance", 48, 8, 128, 4, "");
	/** Kept for old configs: "Show Decimals" off = whole seconds. */
	public final Option.Bool decimals = bool("Show Decimals", true);

	public TntTimerMod() {
		super("TNT Timer", "tnt_timer", "TNT Cooldown: shows the time left above every lit TNT, counting down.", Category.VISUAL);
		instance = this;
		decimals.showIf(() -> false);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	/** True when this TNT should get the vanilla nametag label (Nametag style, used by the renderer mixin). */
	public static boolean shows(Entity e, double squaredDistance) {
		if (!on() || instance.style.idx() != 1 || !(e instanceof TntEntity t) || t.getFuse() <= 0) {
			return false;
		}
		float r = instance.range.f();
		return squaredDistance < r * r;
	}

	public static boolean nametagStyle() {
		return on() && instance.style.idx() == 1;
	}

	private String time(float fuseTicks) {
		float s = Math.max(0F, fuseTicks / 20F);
		int f = decimals.on() ? format.idx() : 2;
		return switch (f) {
			case 1 -> String.format(java.util.Locale.ROOT, "%.1fs", s);
			case 2 -> Math.max(1, (int) Math.ceil(s)) + "s";
			default -> String.format(java.util.Locale.ROOT, "%.2f", s);
		};
	}

	private int colour(float fuseTicks) {
		if (!colourByTime.on()) {
			return 0xFFFFFFFF;
		}
		return fuseTicks > 50 ? 0xFF55FF55 : fuseTicks > 25 ? 0xFFFFFF55 : 0xFFFF5555;
	}

	/** Nametag style text. */
	public static Text label(TntEntity t) {
		String s = instance.time(t.getFuse());
		int rgb = instance.colour(t.getFuse()) & 0xFFFFFF;
		return Text.literal(s).styled(st -> st.withColor(rgb));
	}

	/** Sleep Label style: drawn on the HUD, exactly over each TNT. */
	public void draw(DrawContext c) {
		if (style.idx() != 0 || mc.world == null || mc.player == null || !Project.begin()) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickProgress(true);
		double r2 = range.f() * range.f();
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof TntEntity t) || t.getFuse() <= 0 || mc.player.squaredDistanceTo(e) > r2) {
				continue;
			}
			Vec3d p = e.getLerpedPos(td);
			if (!Project.to(p.x, p.y + 1.45, p.z)) {
				continue;
			}
			float fuse = Math.max(0F, t.getFuse() - td);
			String s = time(fuse);
			int col = colour(fuse);
			float k = (float) Math.max(0.5, Math.min(1.6, 9 / Math.max(1, Project.dist))) * scale.f() / 100F;
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(Project.sx, Project.sy);
			m.scale(k, k);
			int w = Gfx.twRaw(s) + 10, h = bar.on() ? 15 : 13;
			Gfx.rrectFree(c, -w / 2F, -h / 2F, w, h, 4, 0x99000000);
			Gfx.textRaw(c, s, -Gfx.twRaw(s) / 2, -h / 2 + 3, col);
			if (bar.on()) {
				float left = Math.min(1F, fuse / 80F);
				Gfx.rrectFree(c, -w / 2F + 3, h / 2F - 3, w - 6, 1.5F, 0.75F, 0x44FFFFFF);
				Gfx.rrectFree(c, -w / 2F + 3, h / 2F - 3, Math.max(1, (w - 6) * left), 1.5F, 0.75F, col);
			}
			m.popMatrix();
		}
	}
}
