package dev.solclient.mods;

/** Skin Editor: paint your own skin inside the game and wear it. */
public class SkinEditorMod extends Mod {

	public static SkinEditorMod instance;

	public SkinEditorMod() {
		super("Skin Editor", "skin_editor", "Paint your skin pixel by pixel, see it on your character and wear it.", Category.UTILITY);
		instance = this;
		action("Open the editor", "Open", () -> dev.solclient.compat.Mc.setScreen(mc, new dev.solclient.gui.SkinEditorScreen(dev.solclient.compat.Mc.screen(mc))), () -> "");
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}
}
