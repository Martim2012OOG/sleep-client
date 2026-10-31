package dev.solclient.mods;

/** HUD mod showing an integer that eases towards its real value (FPS, ping). */
public abstract class CounterHudMod extends SimpleHudMod {

	private int counter;

	protected CounterHudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, dx, dy);
	}

	public abstract int getIntValue();

	public abstract String getSuffix();

	@Override
	public void tick() {
		int actual = getIntValue();
		if (actual > counter) {
			counter += Math.max((actual - counter) / 2, 1);
		} else if (actual < counter) {
			counter -= Math.max((counter - actual) / 2, 1);
		}
	}

	@Override
	public String getText(boolean edit) {
		return (edit ? 0 : counter) + " " + getSuffix();
	}
}
