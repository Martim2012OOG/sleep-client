package dev.solclient.mods;

/** Clean View: no potion-effect swirls from your own body in your face (other players' still show). */
public class CleanViewMod extends Mod {

	public static CleanViewMod instance;

	public CleanViewMod() {
		super("Clean View", "clean_view", "Removes the potion swirls coming from your own body.", Category.VISUAL);
		instance = this;
	}

	/** True for an effect particle spawned on yourself. */
	public static boolean hides(net.minecraft.particle.ParticleEffect effect, double x, double y, double z) {
		if (instance == null || !instance.enabled || effect == null || effect.getType() != net.minecraft.particle.ParticleTypes.ENTITY_EFFECT) {
			return false;
		}
		var p = instance.mc.player;
		if (p == null) {
			return false;
		}
		double dx = x - p.getX(), dz = z - p.getZ(), dy = y - p.getY();
		return dx * dx + dz * dz < 0.9 && dy > -0.2 && dy < p.getHeight() + 0.3;
	}
}
