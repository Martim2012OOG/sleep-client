package dev.sleepclient.mods;

/** Skin Editor: paint your own skin inside the game and wear it. */
public class SkinEditorMod extends Mod {

	public static SkinEditorMod instance;

	public SkinEditorMod() {
		super("Skin Editor", "skin_editor", "Paint your skin pixel by pixel, see it on your character and wear it.", Category.UTILITY);
		instance = this;
		action("Open the editor", "Open", () -> mc.setScreen(new dev.sleepclient.gui.SkinEditorScreen(mc.currentScreen)), () -> "");
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}
}
