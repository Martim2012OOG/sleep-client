package dev.solclient.mods;

/** One-line HUD whose box grows with the text (server address, clock with seconds...). */
public abstract class TextHudMod extends SimpleHudMod {

	private int lastW = 53;

	protected TextHudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, dx, dy);
	}

	@Override
	public int width() {
		return lastW;
	}

	@Override
	public String getText(boolean edit) {
		String t = text(edit);
		if (t != null) {
			lastW = Math.max(40, mc.textRenderer.getWidth(t) + 12);
		}
		return t;
	}

	protected abstract String text(boolean edit);
}
