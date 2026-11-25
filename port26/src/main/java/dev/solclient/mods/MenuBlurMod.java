package dev.solclient.mods;

import dev.solclient.option.Option;

/** Controls the vanilla menu background blur strength. */
public class MenuBlurMod extends Mod {

	private static MenuBlurMod instance;
	private final Option.Num blur = num("Blur", 5, 0, 10, 1, "");
	private final Option.Bool fastMenu = bool("Fast Caos Menu Background", true).tip("The Caos menu uses a flat dark backdrop instead of blurring the whole game every frame. No fps drop when it is open.");

	/** True when the Caos menu should skip the (expensive) live blur. */
	public static boolean fast() {
		MenuBlurMod m = instance;
		return m == null || m.fastMenu.on();
	}
	private boolean applied;
	private int previous;

	public MenuBlurMod() {
		super("Menu Blur", "menu_blur", "Blurs the background of all menus.", Category.VISUAL);
		instance = this;
	}

	@Override
	protected void onDisable() {
		if (applied) {
			mc.options.menuBackgroundBlurriness().set(previous);
			applied = false;
		}
	}

	@Override
	public void tick() {
		if (!applied) {
			previous = mc.options.menuBackgroundBlurriness().get();
			applied = true;
		}
		mc.options.menuBackgroundBlurriness().set(blur.i());
	}
}
