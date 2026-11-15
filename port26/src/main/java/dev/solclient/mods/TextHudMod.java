package dev.solclient.mods;

/** One-line HUD whose box grows with the text (server address, clock with seconds...). */
public abstract class TextHudMod extends SimpleHudMod {

	protected TextHudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, dx, dy);
		minWidth.set(40F);
	}

	@Override
	public String getText(boolean edit) {
		return text(edit);
	}

	protected abstract String text(boolean edit);
}
