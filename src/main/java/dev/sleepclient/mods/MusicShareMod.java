package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.music.MusicShare;
import dev.sleepclient.option.Option;

/**
 * Music Share: your song above your head for other Sleep players, and "listen along" with Spotify. Needs the Music mod on (it is what
 * reads the song you play). See {@link MusicShare}.
 */
public class MusicShareMod extends Mod {

	public static MusicShareMod instance;

	private final Option.Header hShare = header("What I Share");
	private final Option.Choice showTo = choice("Who Can See My Music", 0, "Everyone", "Friends Only", "Nobody");
	private final Option.Choice listenFrom = choice("Who Can Listen Along", 1, "Everyone", "Friends Only", "Nobody");
	private final Option.Bool ask = bool("Ask Me Before Someone Joins", true);
	private final Option.Bool streamMine = bool("Send My Sound To Listeners (Mac)", true);
	private final Option.Num maxListeners = num("Max Listeners", 10, 1, 10, 1, "");
	private final Option.Header hSee = header("What I See");
	private final Option.Bool tags = bool("Music Above Players' Heads", true);
	private final Option.Bool tagMine = bool("Show My Own Tag (3rd Person)", true);
	private final Option.Num tagLift = num("Height When There Is No Name Tag", 10, 0, 200, 5, " cm");
	private final Option.Num tagRange = num("Tag Distance", 24, 6, 64, 2, " blocks");
	private final Option.Key listenKey = key("Listen Along Key", 76); // L
	private final Option.Action leave = action("Listening Along", "Leave", MusicShare::leave, () -> MusicShare.following == null ? "Not listening along" : "With " + MusicShare.nick(MusicShare.following));
	private final Option.Action kick = action("My Listeners", "Remove All", MusicShare::stopHosting, () -> MusicShare.hosting() ? MusicShare.session().size() + " listening" : "Nobody");
	private boolean keyDown;

	public MusicShareMod() {
		super("Music Share", "music_share", "Your song above your head and listen along with friends. Needs the Music mod on (and Spotify to listen along).", Category.UTILITY);
		instance = this;
		showTo.tip("Nobody: others only see a note and a scrambled name, never the song. Friends: the song is sent but only your friends are shown it.");
		listenFrom.tip("Who is allowed to ask to listen along with you.");
		ask.tip("Off: everyone who is allowed joins at once, no question.");
		tags.tip("Shows the note, the song and a headphones icon above the name of players who share what they listen to. Press the Listen Along key while looking at one to ask to listen along.");
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	/** Send the sound of this computer to the friends who listen along (macOS: asks once for the screen / system audio permission). */
	public boolean streamMine() {
		return streamMine.on();
	}

	public int showToIdx() {
		return showTo.idx();
	}

	public int listenFromIdx() {
		return listenFrom.idx();
	}

	public boolean ask() {
		return ask.on();
	}

	public int maxListeners() {
		return maxListeners.i();
	}

	public boolean tags() {
		return tags.on();
	}

	public boolean tagMine() {
		return tagMine.on();
	}

	/** Blocks above the usual name tag height, for servers that draw their own tags (the line must not sit on theirs). */
	public double tagLift() {
		return tagLift.f() / 100.0;
	}

	public double tagRange() {
		return tagRange.f();
	}

	public String keyName() {
		try {
			return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(listenKey.code()).getLocalizedText().getString().toUpperCase();
		} catch (RuntimeException e) {
			return "L";
		}
	}

	@Override
	public void tick() {
		MusicShare.tick(mc, this); // also in menus: the listen-along must go on
		if (mc.player == null || mc.world == null) {
			return;
		}
		boolean d = mc.currentScreen == null && Keys.down(listenKey.code());
		if (d && !keyDown) {
			String u = MusicShare.aimed(mc);
			if (u != null) {
				MusicShare.request(u);
			} else if (MusicShare.following != null) {
				MusicShare.request(MusicShare.following); // already listening: says so
			} else {
				MusicShare.noTarget();
			}
		}
		keyDown = d;
	}
}
