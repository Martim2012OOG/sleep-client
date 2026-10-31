package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.util.math.MathHelper;

public class DirectionMod extends TextHudMod {

	private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
	private final Option.Bool degrees = bool("Show Degrees", true);

	public DirectionMod() {
		super("Compass", "direction", "Shows where you are looking (N, NE, E...).", 0.01F, 0.34F);
	}

	@Override
	protected String text(boolean edit) {
		float yaw = mc.player == null ? 180F : MathHelper.wrapDegrees(mc.player.getYaw());
		int i = Math.floorMod(Math.round(yaw / 45F), 8);
		float deg = (yaw + 180F) % 360F; // 0 = north
		return DIRS[i] + (degrees.on() ? "  " + Math.round(deg) + "°" : "");
	}
}
