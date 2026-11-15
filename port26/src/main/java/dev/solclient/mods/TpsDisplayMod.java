package dev.solclient.mods;

/** Shows the server's TPS, measured from how often the server sends its world time (every 20 ticks). */
public class TpsDisplayMod extends TextHudMod {

	private static final double[] SAMPLES = new double[5];
	private static int count, next;
	private static long last;

	public TpsDisplayMod() {
		super("TPS Display", "tps_display", "The server's ticks per second (20 = running fine).", 0.01F, 0.70F);
	}

	/** Called every time the server sends the world time (TpsMixin). */
	public static void packet() {
		long now = System.nanoTime();
		if (last != 0) {
			double sec = (now - last) / 1e9;
			if (sec > 0.2 && sec < 20) {
				SAMPLES[next] = Math.min(20.0, 20.0 / sec);
				next = (next + 1) % SAMPLES.length;
				count = Math.min(SAMPLES.length, count + 1);
			}
		}
		last = now;
	}

	@Override
	protected String text(boolean edit) {
		if (mc.level == null) {
			return edit ? "TPS: 20.0" : null;
		}
		if (System.nanoTime() - last > 6_000_000_000L) {
			count = 0; // no news from the server for a while (or a new world): start again
			if (last != 0 && System.nanoTime() - last > 30_000_000_000L) {
				last = 0;
			}
		}
		if (count == 0) {
			return "TPS: --";
		}
		double sum = 0;
		for (int i = 0; i < count; i++) {
			sum += SAMPLES[i];
		}
		return String.format(java.util.Locale.ROOT, "TPS: %.1f", sum / count);
	}
}
