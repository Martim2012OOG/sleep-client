package dev.solclient.mods;

import dev.solclient.option.Option;

/**
 * Host World (like Lunar): open your singleplayer world to your friends, even on other networks, choose game mode,
 * difficulty, cheats, slots and who can join, and invite your Sleep friends with Shift + Tab.
 * The settings here are the ones the Host World screen uses.
 */
public class HostWorldMod extends Mod {

	public static HostWorldMod instance;

	public final Option.Bool cheats = bool("Allow Cheats", false);
	public final Option.Choice gameMode = choice("Game Mode", 0, "Survival", "Creative", "Adventure", "Spectator");
	public final Option.Choice difficulty = choice("Difficulty", 2, "Peaceful", "Easy", "Normal", "Hard");
	public final Option.Num slots = num("Max Slots", 8, 2, 20, 1, "");
	public final Option.Choice privacy = choice("World Privacy", 0, "Whitelist", "Friends Only", "Public");
	public final Option.Bool autoHost = bool("Auto Host On Join", false);
	public final Option.Text port = text("Custom Port", "", 5, "Automatic");
	public final Option.Bool anywhere = bool("Friends From Anywhere (Sleep Relay)", true);
	public final Option.Bool openRouter = bool("Also Open The Router Port (UPnP)", true);
	public final Option.Text relay = text("Relay Server", "", 120, "host.endlock.net");
	/** Names allowed in (Whitelist): edited in the Host World screen, comma separated. */
	public final Option.Text whitelist = text("Whitelist", "", 4000, "names, separated by commas");
	/** Names never allowed in. */
	public final Option.Text banned = text("Banned", "", 4000, "names, separated by commas");

	public HostWorldMod() {
		super("Host World", "host_world", "Host your singleplayer world and invite friends, even from other houses.", Category.UTILITY);
		instance = this;
		whitelist.showIf(() -> false);
		banned.showIf(() -> false);
		action("Host World screen (or Shift + Tab while hosting)", "Open", () -> mc.execute(() -> mc.setScreen(new dev.solclient.gui.HostWorldScreen(mc.currentScreen))),
				() -> dev.solclient.social.HostWorld.statusLine());
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
