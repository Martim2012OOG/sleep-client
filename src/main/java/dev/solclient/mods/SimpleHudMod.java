package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import net.minecraft.client.gui.DrawContext;

/** HUD mod that shows one line of text in a small box. */
public abstract class SimpleHudMod extends HudMod {

	protected final Option.Bool background = bool("Background", true);
	protected final Option.Col backgroundColour = colour("Background Colour", 0x64000000);
	protected final Option.Bool border = bool("Border", false);
	protected final Option.Col borderColour = colour("Border Colour", 0xFF000000);
	protected final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF);
	protected final Option.Bool shadow = bool("Text Shadow", true);

	protected SimpleHudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, dx, dy);
	}

	@Override
	public int width() {
		return 53;
	}

	@Override
	public int height() {
		return 16;
	}

	public abstract String getText(boolean edit);

	protected void box(DrawContext c) {
		if (background.on()) {
			c.fill(0, 0, width(), height(), backgroundColour.c());
		}
		if (border.on()) {
			Gfx.outline(c, 0, 0, width(), height(), borderColour.c());
		}
	}

	protected int vw(String s) {
		return mc.textRenderer.getWidth(s);
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		String text = getText(edit);
		if (text == null) {
			return;
		}
		box(c);
		c.drawText(mc.textRenderer, text, width() / 2 - vw(text) / 2, 4, textColour.c(), shadow.on());
	}
}
