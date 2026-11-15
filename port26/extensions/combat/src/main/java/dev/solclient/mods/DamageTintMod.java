package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The screen edges flash red when you take damage. */
public class DamageTintMod extends Mod {

	public static DamageTintMod instance;
	private final Option.Col colour = colour("Colour", 0xFFE5484D);
	private final Option.Num strength = num("Strength", 60, 10, 100, 5, "%");

	public DamageTintMod() {
		super("Damage Tint", "damage_tint", "Red flash at the screen edges when you get hurt.", Category.VISUAL);
		instance = this;
	}

	public void draw(GuiGraphicsExtractor c) {
		var p = mc.player;
		if (p == null || p.hurtTime <= 0) {
			return;
		}
		float f = p.hurtTime / 10F * strength.f() / 100F;
		int a = Math.round(200 * Math.max(0F, Math.min(1F, f)));
		int col = Gfx.withAlpha(colour.c(), a), clear = Gfx.withAlpha(colour.c(), 0);
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
		int e = Math.max(24, Math.min(w, h) / 5);
		c.fillGradient(0, 0, w, e, col, clear);
		c.fillGradient(0, h - e, w, h, clear, col);
		Gfx.gradientH(c, 0, 0, e, h, col, clear);
		Gfx.gradientH(c, w - e, 0, e, h, clear, col);
	}
}
