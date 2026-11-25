package dev.solclient.mods;

import dev.solclient.option.Option;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.util.Util;

/**
 * Better Search: smarter search bars (creative tab, recipe book, chat commands...). It is the real "Better Search" mod
 * by rivalzin (MIT licence), shipped inside the Caos jar (jar-in-jar) - this card only switches it on and off, by
 * reflection, so Caos has no hard dependency on its classes.
 */
public class BetterSearchMod extends Mod {

	public static final String URL = "https://modrinth.com/mod/bettersearch";

	private final Option.Header credits = header("Credits");
	private final Option.Action author = action("Author: rivalzin", "Open page", () -> {
		try {
			Util.getPlatform().openUri(URL);
		} catch (Throwable ignored) {
			// no browser available
		}
	}, () -> "Better Search (MIT)");

	public BetterSearchMod() {
		super("Better Search", "search", "Smarter, better search in the creative tab, recipe book and chat commands (typos, other languages, tooltips).", Category.UTILITY);
		// after every mod has started, so Better Search's settings exist
		ClientLifecycleEvents.CLIENT_STARTED.register(c -> apply(enabled));
	}

	@Override
	protected void onEnable() {
		apply(true);
	}

	@Override
	protected void onDisable() {
		apply(false);
	}

	private static void apply(boolean on) {
		try {
			Class<?> cl = Class.forName("com.rivalzin.bettersearch.client.BetterSearchClient");
			Object s = cl.getMethod("settings").invoke(null);
			if (s == null) {
				return;
			}
			Object copy = s.getClass().getMethod("copy").invoke(s);
			copy.getClass().getField("enabled").setBoolean(copy, on);
			cl.getMethod("setSettings", s.getClass()).invoke(null, copy);
		} catch (Throwable ignored) {
			// Better Search not present: nothing to switch
		}
	}
}
