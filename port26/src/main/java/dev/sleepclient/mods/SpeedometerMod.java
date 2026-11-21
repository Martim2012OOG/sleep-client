package dev.sleepclient.mods;

/** How fast you move, in blocks per second (ground speed, or full speed when flying / falling). */
public class SpeedometerMod extends TextHudMod {

	private double lx, ly, lz;
	private long lt;
	private double speed;

	public SpeedometerMod() {
		super("Speedometer", "speedometer", "Your speed in blocks per second.", 0.02F, 0.6F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) {
			return edit ? "Speed: 5.6 b/s" : null;
		}
		long now = System.nanoTime();
		if (lt != 0 && now - lt > 0) {
			double dt = (now - lt) / 1e9;
			double dx = mc.player.getX() - lx, dz = mc.player.getZ() - lz, dy = mc.player.getY() - ly;
			double v = Math.sqrt(dx * dx + dz * dz + (mc.player.onGround() ? 0 : dy * dy)) / Math.max(dt, 1e-3);
			speed += (Math.min(v, 200) - speed) * Math.min(1.0, dt * 8); // a little smoothing
		}
		lx = mc.player.getX();
		ly = mc.player.getY();
		lz = mc.player.getZ();
		lt = now;
		return String.format("Speed: %.1f b/s", speed);
	}
}
