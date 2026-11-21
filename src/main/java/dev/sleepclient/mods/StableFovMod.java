package dev.sleepclient.mods;

/** Your field of view stays the same when you sprint, fly or get speed. */
public class StableFovMod extends Mod {

	private Double saved;

	public StableFovMod() {
		super("Stable FOV", "stable_fov", "FOV no longer changes when you sprint or get speed.", Category.VISUAL);
	}

	@Override
	protected void onEnable() {
		if (mc.options == null) {
			throw new IllegalStateException("options not ready"); // Mod.init tries again once the game is up
		}
		saved = mc.options.getFovEffectScale().getValue();
		mc.options.getFovEffectScale().setValue(0.0);
	}

	@Override
	protected void onDisable() {
		if (mc.options != null) {
			mc.options.getFovEffectScale().setValue(saved == null || saved == 0 ? 1.0 : saved);
		}
	}
}
