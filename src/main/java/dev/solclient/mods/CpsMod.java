package dev.solclient.mods;

import dev.solclient.CpsMonitor;
import dev.solclient.Gfx;
import dev.solclient.option.Option;
import net.minecraft.client.gui.DrawContext;

public class CpsMod extends SimpleHudMod {

	private final Option.Bool rmb = bool("RMB", false);
	private final Option.Col separatorColour = colour("Separator Colour", 0xFF404040);

	public CpsMod() {
		super("CPS", "cps", "Display your CPS (clicks per second).", 0.01F, 0.15F);
	}

	@Override
	public void frame() {
		// CpsMonitor is polled globally; nothing to do here.
	}

	@Override
	public void renderHud(DrawContext c, boolean edit) {
		super.renderHud(c, edit);
		if (rmb.on()) {
			box(c);
			int l = edit ? 0 : CpsMonitor.LMB.getCps();
			int r = edit ? 0 : CpsMonitor.RMB.getCps();
			int total = vw(l + " | " + r + " CPS") - 2;
			int x = width() / 2 - total / 2;
			int y = 4;
			String ls = Integer.toString(l);
			c.drawText(mc.textRenderer, ls, x, y, textColour.c(), shadow.on());
			x += vw(ls);
			x--;
			if (shadow.on()) {
				x--;
			}
			x += vw(" ");
			Gfx.vline(c, x, y - 1, y + 7, separatorColour.c());
			if (shadow.on()) {
				Gfx.vline(c, x + 1, y, y + 8, Gfx.withAlpha(separatorColour.c(), 90));
			}
			x += 1;
			x += vw(" ");
			c.drawText(mc.textRenderer, r + " CPS", x, y, textColour.c(), shadow.on());
		}
	}

	@Override
	public String getText(boolean edit) {
		if (rmb.on()) {
			return null; // drawn manually in renderHud
		}
		return (edit ? 0 : CpsMonitor.LMB.getCps()) + " CPS";
	}
}
