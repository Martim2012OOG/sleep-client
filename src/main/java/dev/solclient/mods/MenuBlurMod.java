package dev.solclient.mods;

import dev.solclient.option.Option;

/** Controls the vanilla menu background blur strength. */
public class MenuBlurMod extends Mod {

	private final Option.Num blur = num("Blur", 5, 0, 10, 1, "");
	private boolean applied;
	private int previous;

	public MenuBlurMod() {
		super("Menu Blur", "menu_blur", "Blurs the background of all menus.", Category.VISUAL);
	}

	@Override
	protected void onDisable() {
		if (applied) {
			mc.options.getMenuBackgroundBlurriness().setValue(previous);
			applied = false;
		}
	}

	@Override
	public void tick() {
		if (!applied) {
			previous = mc.options.getMenuBackgroundBlurriness().getValue();
			applied = true;
		}
		mc.options.getMenuBackgroundBlurriness().setValue(blur.i());
	}
}
