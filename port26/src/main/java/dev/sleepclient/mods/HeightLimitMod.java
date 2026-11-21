package dev.sleepclient.mods;

/** The world's build limit and how far above you it is (builders and bridge fights). */
public class HeightLimitMod extends TextHudMod {

	public HeightLimitMod() {
		super("Height Limit", "height_limit", "Shows the build limit of this world and how many blocks you are from it.", 0.85F, 0.02F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.level == null || mc.player == null) {
			return edit ? "Height Limit: 320  Distance: 222" : null;
		}
		int top = mc.level.getMaxY();
		int dist = (int) Math.floor(top - mc.player.getY());
		return "Height Limit: " + top + "  Distance: " + dist;
	}
}
