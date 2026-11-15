package dev.solclient.mods;

import java.text.DecimalFormat;

public class SpeedometerMod extends SimpleHudMod {

	private static final DecimalFormat FORMAT = new DecimalFormat("0.00");
	private double lastX, lastZ;
	private double speed;
	private boolean hasLast;

	public SpeedometerMod() {
		super("Speedometer", "speedometer", "Display your speed on the HUD.", 0.01F, 0.22F);
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			hasLast = false;
			return;
		}
		double x = mc.player.getX();
		double z = mc.player.getZ();
		if (hasLast) {
			double dx = x - lastX;
			double dz = z - lastZ;
			speed = Math.sqrt(dx * dx + dz * dz);
		}
		lastX = x;
		lastZ = z;
		hasLast = true;
	}

	@Override
	public String getText(boolean edit) {
		return edit ? "0.00 m/s" : FORMAT.format(speed / 0.05D) + " m/s";
	}
}
