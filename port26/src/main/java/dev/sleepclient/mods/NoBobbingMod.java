package dev.sleepclient.mods;

/** Turns off the camera bobbing while you walk. */
public class NoBobbingMod extends Mod {

	private Boolean saved;

	public NoBobbingMod() {
		super("No Bobbing", "no_bobbing", "No camera bobbing while walking.", Category.VISUAL);
	}

	@Override
	protected void onEnable() {
		saved = mc.options.bobView().get();
		mc.options.bobView().set(false);
	}

	@Override
	protected void onDisable() {
		mc.options.bobView().set(saved == null || saved);
	}
}
