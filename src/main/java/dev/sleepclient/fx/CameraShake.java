package dev.sleepclient.fx;

/** A short camera shake (rumble). Effects call {@link #add}; the camera mixin reads the offsets every frame. */
public final class CameraShake {

	private static float energy;
	private static long last = System.nanoTime();

	private CameraShake() {
	}

	/** Adds a shake. Strength is about 1 for a small bump and 3+ for an earthquake. */
	public static void add(float strength) {
		decay();
		energy = Math.min(6F, Math.max(energy, 0F) + strength);
	}

	private static void decay() {
		long now = System.nanoTime();
		float dt = (now - last) / 1e9F;
		last = now;
		if (dt > 0F && energy > 0F) {
			// fast at first, then it settles
			energy *= (float) Math.exp(-dt * 3.2F);
			if (energy < 0.02F) {
				energy = 0F;
			}
		}
	}

	public static boolean active() {
		return energy > 0.02F;
	}

	/** Yaw offset in degrees for this frame. */
	public static float yaw() {
		decay();
		if (energy <= 0F) {
			return 0F;
		}
		double t = System.nanoTime() / 1e9;
		return energy * 0.55F * (float) (Math.sin(t * 53) + 0.5 * Math.sin(t * 91 + 1.3));
	}

	/** Pitch offset in degrees for this frame. */
	public static float pitch() {
		if (energy <= 0F) {
			return 0F;
		}
		double t = System.nanoTime() / 1e9;
		return energy * 0.45F * (float) (Math.sin(t * 61 + 0.7) + 0.5 * Math.sin(t * 83 + 2.1));
	}
}
