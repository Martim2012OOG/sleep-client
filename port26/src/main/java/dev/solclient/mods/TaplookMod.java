package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import net.minecraft.client.CameraType;

public class TaplookMod extends Mod {

	private final Option.Key key = key("Key", -1);
	private final Option.Choice perspective = choice("Perspective", 1, "First Person", "Third Person Back",
			"Third Person Front");
	private CameraType previous = CameraType.FIRST_PERSON;
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
		boolean down = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(key.code());
		if (down && !active) {
			active = true;
			previous = mc.options.getCameraType();
			CameraType p;
			switch (perspective.idx()) {
				case 0: p = CameraType.FIRST_PERSON; break;
				case 2: p = CameraType.THIRD_PERSON_FRONT; break;
				default: p = CameraType.THIRD_PERSON_BACK;
			}
			mc.options.setCameraType(p);
		} else if (!down && active) {
			stop();
		}
	}

	private void stop() {
		active = false;
		mc.options.setCameraType(previous);
	}
}
