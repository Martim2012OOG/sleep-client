package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.gui.PhotoModeScreen;
import dev.solclient.option.Option;

/** Photo Mode: press the key and the camera is yours (see {@link PhotoModeScreen}). */
public class PhotoModeMod extends Mod {

	public static PhotoModeMod instance;

	private final Option.Key key = key("Photo Mode Key", 295); // F6
	private final Option.Choice filter = choice("Default Filter", 0, PhotoModeScreen.FILTERS);
	private boolean prev;

	public PhotoModeMod() {
		super("Photo Mode", "photo_mode", "Free camera around you with filters, cinema bars and clean photos without any interface.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public int keyCode() {
		return key.code();
	}

	public int defaultFilter() {
		return filter.idx();
	}

	@Override
	public void tick() {
		boolean d = Keys.down(key.code());
		if (d && !prev && dev.solclient.compat.Mc.screen(mc) == null && mc.player != null && mc.level != null) {
			dev.solclient.compat.Mc.setScreen(mc, new PhotoModeScreen());
		}
		prev = d;
	}
}
