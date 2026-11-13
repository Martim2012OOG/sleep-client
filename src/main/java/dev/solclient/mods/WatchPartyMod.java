package dev.solclient.mods;

import dev.solclient.option.Option;
import dev.solclient.video.Mcef;
import dev.solclient.video.VideoScreens;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Watch Party: watch YouTube together on screens in the world. Needs the free "MCEF Modern" mod (a Chromium browser
 * for Minecraft). Commands: /kubo video play &lt;link&gt;, pause, play, seek, size, move, remove.
 */
public class WatchPartyMod extends Mod {

	public static WatchPartyMod instance;

	private final Option.Key key = key("Screen Key", 75); // K
	private final Option.Num volume = num("Volume", 70, 0, 100, 5, "%");
	private final Option.Num range = num("Hearing / Loading Distance", 32, 8, 96, 4, " blocks");
	private final Option.Choice quality = choice("Quality", 1, "480p", "720p", "1080p");
	private final Option.Bool captions = bool("Captions", false);
	private final Option.Bool debug = bool("Debug Info (green text)", false);
	private final Option.Choice from = choice("Show Screens From", 0, "Everyone", "Friends Only");
	private final Option.Num size = num("Quick Screen Width", 6, 2, 32, 1, " blocks");
	private final Option.Bool adMusic = bool("Music During Ads", true);
	private final Option.Choice language = choice("Language", 0, "Auto", "English", "Português");
	private boolean keyDown;
	private boolean clickDown;
	private boolean leftDown, rightDown;
	private final Option.Action engine = action("Video Engine", "Start", Mcef::start, Mcef::status);
	private final Option.Action help = action("Commands", "Show", this::showHelp, () -> "Key " + keyName());

	public WatchPartyMod() {
		super("Watch Party", "watch_party", "Watch YouTube with friends on screens in the world. Press K to make a screen.", Category.UTILITY);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	@Override
	protected void onEnable() {
		if (Mcef.installed()) {
			Mcef.start();
		}
	}

	@Override
	protected void onDisable() {
		VideoScreens.tick(); // closes the browsers (mod off)
	}

	@Override
	public void tick() {
		boolean d = mc.currentScreen == null && dev.solclient.Keys.down(key.code());
		if (d && !keyDown) {
			VideoScreens.onKey();
		}
		keyDown = d;
		boolean c = mc.currentScreen == null && dev.solclient.Keys.mouseDown(1);
		if (c && !clickDown) {
			VideoScreens.onClick();
		}
		clickDown = c;
		boolean l = mc.currentScreen == null && dev.solclient.Keys.down(263); // left arrow
		if (l && !leftDown) {
			VideoScreens.onSeekKey(-1);
		}
		leftDown = l;
		boolean r = mc.currentScreen == null && dev.solclient.Keys.down(262); // right arrow
		if (r && !rightDown) {
			VideoScreens.onSeekKey(1);
		}
		rightDown = r;
	}

	public String keyName() {
		try {
			return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(key.code()).getLocalizedText().getString().toUpperCase();
		} catch (RuntimeException e) {
			return "K";
		}
	}

	public float volume() {
		return volume.f() / 100F;
	}

	public double range() {
		return range.f();
	}

	public boolean debug() {
		return debug.on();
	}

	public boolean captions() {
		return captions.on();
	}

	/** 0 = follow the client language, 1 = English, 2 = Portuguese (the texts of Watch Party). */
	public int languageIdx() {
		return language.idx();
	}

	/** Play the credited ad music while an ad is skipped. */
	public boolean adMusicOn() {
		return adMusic.on();
	}

	public boolean friendsOnly() {
		return from.idx() == 1;
	}

	public float defaultWidth() {
		return size.f();
	}

	public int[] resolution() {
		return switch (quality.idx()) {
			case 0 -> new int[] {854, 480};
			case 2 -> new int[] {1920, 1080};
			default -> new int[] {1280, 720};
		};
	}

	private void showHelp() {
		if (mc.inGameHud == null) {
			return;
		}
		String[] lines = {
				"Press " + keyName() + ": aim at the 4 corner blocks on a wall (press " + keyName() + " on each)",
				"Press " + keyName() + " again: panel to change video, pause, skip, redraw or remove",
				"/kubo video play <youtube link> | pause | play | seek 1:30 | remove | cancel",
				Mcef.installed() ? "Video engine: " + Mcef.status() : "Video engine missing"};
		for (String l : lines) {
			mc.inGameHud.getChatHud().addMessage(Text.literal(dev.solclient.video.WatchPartyText.t(l)).formatted(Formatting.GRAY));
		}
	}
}
