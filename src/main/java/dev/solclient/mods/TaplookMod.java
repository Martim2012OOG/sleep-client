package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import net.minecraft.client.option.Perspective;

public class TaplookMod extends Mod {

	private final Option.Key key = key("Key", -1);
	private final Option.Choice perspective = choice("Perspective", 1, "First Person", "Third Person Back",
			"Third Person Front");
	private Perspective previous = Perspective.FIRST_PERSON;
	private boolean active;

	public TaplookMod() {
		super("Taplook", "taplook", "Change your perspective by holding a key.", Category.UTILITY);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	protected void onDisable() {
		if (active) {
			stop();
		}
	}

	@Override
	public void tick() {
		boolean down = mc.currentScreen == null && Keys.down(key.code());
		if (down && !active) {
			active = true;
			previous = mc.options.getPerspective();
			Perspective p;
			switch (perspective.idx()) {
				case 0: p = Perspective.FIRST_PERSON; break;
				case 2: p = Perspective.THIRD_PERSON_FRONT; break;
				default: p = Perspective.THIRD_PERSON_BACK;
			}
			mc.options.setPerspective(p);
		} else if (!down && active) {
			stop();
		}
	}

	private void stop() {
		active = false;
		mc.options.setPerspective(previous);
	}
}
