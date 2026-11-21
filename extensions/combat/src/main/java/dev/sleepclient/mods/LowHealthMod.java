package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.DrawContext;

/** Red pulsing glow around the screen edges when your health is low. */
public class LowHealthMod extends Mod {

	public static LowHealthMod instance;
	private final Option.Num threshold = num("Health Threshold", 6, 1, 19, 1, " HP");
	private final Option.Col colour = colour("Colour", 0xFFE5484D);
	private final Option.Bool pulse = bool("Pulse", true);

	public LowHealthMod() {
		super("Low Health Warning", "low_health", "Red glow at the screen edges when your health is low.", Category.VISUAL);
		instance = this;
	}

	public void draw(DrawContext c) {
		if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
			return;
		}
		float hp = mc.player.getHealth();
		if (hp > threshold.f() || hp <= 0) {
			return;
		}
		float strength = 1F - hp / Math.max(1F, threshold.f()) * 0.6F;
		if (pulse.on()) {
			strength *= 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 180.0);
		}
		int a = Math.round(170 * Math.max(0F, Math.min(1F, strength)));
		int col = Gfx.withAlpha(colour.c(), a), clear = Gfx.withAlpha(colour.c(), 0);
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		int e = Math.max(20, Math.min(w, h) / 6);
		c.fillGradient(0, 0, w, e, col, clear);
		c.fillGradient(0, h - e, w, h, clear, col);
		Gfx.gradientH(c, 0, 0, e, h, col, clear);
		Gfx.gradientH(c, w - e, 0, e, h, clear, col);
	}
}
