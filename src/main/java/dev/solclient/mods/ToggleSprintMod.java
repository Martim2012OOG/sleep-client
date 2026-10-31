package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;

public class ToggleSprintMod extends SimpleHudMod {

	public enum State {
		HELD("Held"), TOGGLED("Toggled");

		final String label;

		State(String label) {
			this.label = label;
		}
	}

	private final Option.Bool hud = bool("HUD", false);
	private final Option.Key sprintKey = key("Sprint Key", 341);
	private State sprint;
	private boolean wasDown;
	private boolean forced;
	private long startTime;

	public ToggleSprintMod() {
		super("Toggle Sprint", "toggle_sprint", "Toggle the sprint key.", 0.01F, 0.9F);
	}

	@Override
	public boolean visible() {
		return hud.on();
	}

	@Override
	public String getText(boolean edit) {
		if (!hud.on()) {
			return null;
		}
		if (edit) {
			return "Toggled";
		}
		return sprint == null ? null : sprint.label;
	}

	@Override
	protected void onDisable() {
		sprint = null;
		wasDown = false;
	}

	@Override
	public void tick() {
		boolean down = mc.currentScreen == null && Keys.down(sprintKey.code());
		if (down) {
			if (!wasDown) {
				startTime = System.currentTimeMillis();
				sprint = sprint == State.TOGGLED ? State.HELD : State.TOGGLED;
			} else if (System.currentTimeMillis() - startTime > 250) {
				sprint = State.HELD;
			}
		} else if (sprint == State.HELD) {
			sprint = null;
		}
		wasDown = down;
		if (mc.currentScreen == null) {
			if (sprint == State.TOGGLED) {
				mc.options.sprintKey.setPressed(true);
				forced = true;
			} else if (forced) {
				mc.options.sprintKey.setPressed(false);
				forced = false;
			}
		}
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}
}
