package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Lunar-style compass strip at the top of the screen: ticks every 15°, N / NE / E..., and your heading. */
public class CompassBarMod extends HudMod {

	private final Option.Num span = num("Visible Degrees", 120, 60, 240, 10, "°");
	private final Option.Bool degrees = bool("Show Heading Number", true);
	private final Option.Bool background = bool("Background", false);
	private final Option.Col backgroundColour = colour("Background Colour", 0x50000000);
	private final Option.Col colour = colour("Colour", 0xFFFFFFFF);
	private final Option.Col markerColour = colour("Marker Colour", 0xFFFFFFFF);

	public CompassBarMod() {
		super("Compass Bar", "compass_bar", "A compass strip at the top of the screen, like Lunar's.", 0.35F, 0.005F);
	}

	@Override
	public int width() {
		return 200;
	}

	@Override
	public int height() {
		return 30;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		float yaw = mc.player == null ? 0F : Mth.wrapDegrees(mc.player.getYRot());
		float heading = (yaw + 180F + 360F) % 360F; // 0 = north, 90 = east
		int w = width(), mid = w / 2;
		float pxPerDeg = w / span.f();
		if (background.on()) {
			c.fill(0, 10, w, 30, backgroundColour.c());
		}
		int col = colour.c();
		if (degrees.on()) {
			String d = String.valueOf(Math.round(heading) % 360);
			c.text(mc.font, d, mid - mc.font.width(d) / 2, 0, col, true);
		}
		c.fill(mid, 9, mid + 1, 13, markerColour.c());
		int first = (int) Math.floor((heading - span.f() / 2) / 15F) * 15;
		for (int a = first; a <= heading + span.f() / 2; a += 15) {
			float x = mid + (a - heading) * pxPerDeg;
			if (x < 0 || x > w) {
				continue;
			}
			int ix = Math.round(x);
			int norm = ((a % 360) + 360) % 360;
			String label = switch (norm) {
				case 0 -> "N";
				case 45 -> "NE";
				case 90 -> "E";
				case 135 -> "SE";
				case 180 -> "S";
				case 225 -> "SW";
				case 270 -> "W";
				case 315 -> "NW";
				default -> null;
			};
			int fade = Math.round(255 * Math.max(0.25F, 1F - Math.abs(x - mid) / (w / 2F)));
			int fc = (fade << 24) | (col & 0xFFFFFF);
			if (label != null) {
				c.text(mc.font, label, ix - mc.font.width(label) / 2, 18, label.length() == 1 ? fc : (fc & 0x00FFFFFF) | ((fade * 3 / 4) << 24), true);
			} else {
				c.fill(ix, 14, ix + 1, 19, fc);
				String n = String.valueOf(norm);
				c.pose().pushMatrix();
				c.pose().translate(ix, 21);
				c.pose().scale(0.6F, 0.6F);
				c.text(mc.font, n, -mc.font.width(n) / 2, 0, fc, false);
				c.pose().popMatrix();
			}
		}
	}
}
