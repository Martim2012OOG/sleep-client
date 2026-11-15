package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class CoordinatesMod extends HudMod {

	private final Option.Bool background = bool("Background", true);
	private final Option.Col backgroundColour = colour("Background Colour", 0x64000000);
	private final Option.Bool border = bool("Border", false);
	private final Option.Col borderColour = colour("Border Colour", 0xFF000000);
	private final Option.Col axisLabelColour = colour("Axis Label Colour", 0xFF0096FF);
	private final Option.Col axisValueColour = colour("Axis Value Colour", 0xFFFFFFFF);
	private final Option.Bool cardinalDirection = bool("Cardinal Direction", true);
	private final Option.Col cardinalDirectionColour = colour("Cardinal Direction Colour", 0xFF0096FF);
	private final Option.Bool axisDirection = bool("Axis Direction", true);
	private final Option.Col axisDirectionColour = colour("Axis Direction Colour", 0xFF0096FF);
	private final Option.Bool shadow = bool("Text Shadow", true);

	private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	public CoordinatesMod() {
		super("Coordinates", "coordinates", "Display your coordinates.", 0.01F, 0.43F);
	}

	@Override
	public int width() {
		return 82;
	}

	@Override
	public int height() {
		return 4 + 9 + 2 + 9 + 2 + 9 + 2;
	}

	private void line(GuiGraphicsExtractor c, String label, String value, int y, String dir, int dirColour) {
		int sub = shadow.on() ? 1 : 0;
		int x = 4 + mc.font.width(label);
		c.text(mc.font, label, 4, y, axisLabelColour.c(), shadow.on());
		c.text(mc.font, value, x - sub, y, axisValueColour.c(), shadow.on());
		if (dir != null) {
			c.text(mc.font, dir, 80 - mc.font.width(dir) - 2, y, dirColour, shadow.on());
		}
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		if (background.on()) {
			c.fill(0, 0, width(), height(), backgroundColour.c());
		}
		if (border.on()) {
			Gfx.outline(c, 0, 0, width(), height(), borderColour.c());
		}
		double x = 0, y = 0, z = 0, yaw = -90;
		if (!edit && mc.player != null) {
			x = mc.player.getX();
			y = mc.player.getY();
			z = mc.player.getZ();
			yaw = mc.player.getYRot();
		}
		int index = Mth.floor(((Mth.wrapDegrees(yaw) + 180D + 22.5D) % 360D) / 45D) & 7;
		String xDir = null, zDir = null;
		switch (index) {
			case 0: zDir = "--"; break;
			case 1: zDir = "-"; xDir = "+"; break;
			case 2: xDir = "++"; break;
			case 3: zDir = "+"; xDir = "+"; break;
			case 4: zDir = "++"; break;
			case 5: zDir = "+"; xDir = "-"; break;
			case 6: xDir = "--"; break;
			case 7: zDir = "-"; xDir = "-"; break;
			default:
		}
		int row = 9 + 2;
		line(c, "X ", Integer.toString((int) x), 4, axisDirection.on() ? xDir : null, axisDirectionColour.c());
		line(c, "Y ", Integer.toString((int) y), 4 + row, cardinalDirection.on() ? CARDINALS[index] : null,
				cardinalDirectionColour.c());
		line(c, "Z ", Integer.toString((int) z), 4 + row * 2, axisDirection.on() ? zDir : null,
				axisDirectionColour.c());
	}
}
