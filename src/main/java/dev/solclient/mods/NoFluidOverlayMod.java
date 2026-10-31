package dev.solclient.mods;

/** Removes the blue tint you see under water. */
public class NoFluidOverlayMod extends Mod {

	public static NoFluidOverlayMod instance;

	public NoFluidOverlayMod() {
		super("No Fluid Overlay", "no_fluid_overlay", "No blue water overlay on your screen.", Category.VISUAL);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
