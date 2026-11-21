package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import dev.sleepclient.tiktok.TikClient;
import dev.sleepclient.tiktok.TikEvent;
import dev.sleepclient.tiktok.TikFx;
import dev.sleepclient.tiktok.TikMusic;
import dev.sleepclient.tiktok.TikSound;
import dev.sleepclient.tiktok.TikStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * TikTok Live: the chat of your live on top of the game, gift alerts, a sound of YOUR choice for every donation, and
 * big full-screen effects when someone donates.
 *
 * Where the events come from (TikTok has no official API for this):
 *  - TikFinity (free desktop app, on this PC): it talks to TikTok and re-sends everything on ws://127.0.0.1:21213/.
 *  - Tik.Tools (online gateway): give it the @user or the link of your live and an API key.
 *  - Custom address: any WebSocket that sends {"event": "chat" | "gift" | "follow", "data": {...}}.
 *
 * Nothing here can make the game lag: the connection and the decoding of sounds run on other threads, events wait in
 * a capped queue (chat is dropped first if the game is behind), at most a few events are handled per tick, effects
 * have a hard particle cap and shrink when the FPS drops.
 */
public class TikTokLiveMod extends HudMod {

	public static TikTokLiveMod instance;

	private static final String[] SMALL = concat("Auto", "Random", TikFx.NAMES);

	// ---------------------------------------------------------------- options
	private final Option.Header hConn = header("Connection");
	private final Option.Choice source = choice("Source", 0, "Automatic (just your @)", "TikFinity (app)", "Tik.Tools (online)", "Custom address")
			.tip("Automatic: type your @ and that's it - no app, no key. It waits by itself and starts the moment you go live.");
	private final Option.Text liveUser = text("Live @user or link", "", 160, "@name or tiktok.com/@name/live");
	private final Option.Text apiKey = text("API key / token", "", 120, "paste your key");
	private final Option.Text tikfinityAddr = text("TikFinity address", "ws://127.0.0.1:21213/", 120, "ws://127.0.0.1:21213/");
	private final Option.Text customAddr = text("Custom address", "", 300, "wss://host/path?user={user}&key={key}");
	private final Option.Bool autoConnect = bool("Auto Connect", true);
	private final Option.Action btnConnect = action("Status", "Connect / Disconnect", this::toggleConnection, () -> this.client.status());
	private final Option.Action btnSeen = action("Events that arrived (diagnose)", "Show in chat", this::showSeen, () -> this.client.total() + " msgs");
	private final Option.Bool debug = bool("Debug Log (raw events)", false);

	private final Option.Header hStream = header("Smooth stream");
	private final Option.Bool keepFps = bool("Keep Full FPS While Live", true)
			.tip("While you are live the game never drops to the background / AFK frame rate, so the stream stays smooth when you click OBS or the TikTok chat.");
	private final Option.Choice lockFps = choice("Lock FPS While Live", 2, "Off", "30", "60", "120")
			.tip("A steady frame rate looks smoother on stream than one that jumps around, and leaves your Mac's GPU free for OBS. 60 is best for a 60 FPS live, 30 for a 30 FPS live.");
	private final Option.Action btnObs = action("OBS settings for TikTok", "Copy", this::copyObsGuide, () -> this.obsNote);
	private final Option.Header hTest = header("Test it (no live needed)");
	private final Option.Bool testLive = bool("Test Live Mode", false)
			.tip("Pretends you are live: chat, gifts (small and big), follows, shares, viewers and likes arrive by themselves, with your sounds and effects. Nothing is sent to TikTok.");
	private final Option.Choice testSpeed = choice("Test Live Speed", 1, "Calm", "Normal", "Crazy");
	private final Option.Choice testGiftPick = choice("Test Gift", 2, GIFT_NAMES);
	private final Option.Action btnTestPick = action("Send the test gift", "Send", this::sendPickedGift, () -> GIFT_COINS[testGiftPick.idx()] + " coins");
	private final Option.Action btnTestChat = action("Fake chat message", "Send", this::testChat, () -> "");
	private final Option.Action btnTestSmall = action("Small gift (5 coins)", "Send", () -> testGift("Rose", 1, 5), () -> "");
	private final Option.Action btnTestBig = action("Big gift (500 coins)", "Send", () -> testGift("Lion", 1, 500), () -> "");
	private final Option.Action btnTestFollow = action("New follower", "Send", this::testFollow, () -> "");
	private final Option.Action btnTestViewers = action("Viewers / likes counter", "Send", this::testViewers, () -> "");

	private final Option.Header hParkour = header("Parkour Live (Parkour Paradise 3)");
	/** A row with a "Choose" button that opens the gift picker for one list. */
	private Option.Action giftPicker(String label, Option.Text list, Option.Bool only) {
		Option.Action act = action(label, dev.sleepclient.Lang.tr("Choose", "Escolher"), () -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			dev.sleepclient.compat.Mc.setScreen(mc, new dev.sleepclient.gui.GiftPickerScreen(dev.sleepclient.compat.Mc.screen(mc), label, list));
		}, () -> {
			String v = list.get().trim();
			return v.isEmpty() ? dev.sleepclient.Lang.tr("None", "Nenhum") : v.length() > 42 ? v.substring(0, 40) + ".." : v;
		});
		if (only != null) {
			act.showIf(only::on);
		}
		return act;
	}

	public final Option.Bool pkOn = bool("Parkour Live", true)
			.tip("In the Parkour Paradise 3 map (Add-ons): LEVEL X / 100 on screen instead of the boss bar, and gifts move you up or down, start a RESET timer, save you or punch you.");
	private final Option.Text pkUp1 = text("UP 1 LVL gifts", "Paper Crane, Heart Me", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkUp1Pick = giftPicker("UP 1 LVL", pkUp1, null);
	private final Option.Text pkDown1 = text("DOWN 1 LVL gifts", "Hat and Mustache, Cowboy Hat", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkDown1Pick = giftPicker("DOWN 1 LVL", pkDown1, null);
	private final Option.Text pkUp10 = text("UP 10 LVL gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkUp10Pick = giftPicker("UP 10 LVL", pkUp10, null);
	private final Option.Text pkDown10 = text("DOWN 10 LVL gifts", "Gift Box", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkDown10Pick = giftPicker("DOWN 10 LVL", pkDown10, null);
	private final Option.Text pkReset = text("RESET gifts", "Galaxy", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkResetPick = giftPicker("RESET", pkReset, null);
	private final Option.Text pkSave = text("SAVE gifts", "Watermelon Sugar, Watermelon", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkSavePick = giftPicker("SAVE", pkSave, null);
	private final Option.Text pkPunch = text("PUNCH gifts", "Boxing Gloves", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkPunchPick = giftPicker("PUNCH", pkPunch, null);
	private final Option.Text pkJail = text("JAIL gifts", "Sunglasses", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkJailPick = giftPicker("JAIL", pkJail, null);
	public final Option.Num pkJailSeconds = num("Jail Time", 20, 5, 60, 1, "s");
	public final Option.Choice pkTargets = choice("Gifts Affect", 0, "Only Me", "Everyone", "My Friends Only")
			.tip("Who the parkour gifts move, reset, punch and jail when friends play your hosted map with you.");
	public final Option.Num pkResetSeconds = num("Reset Timer", 60, 10, 300, 5, "s");

	private final Option.Header hFriend = header("Gifts for your friend");
	public final Option.Bool pkFriendOn = bool("Friend Gifts", false)
			.tip("Separate gifts that only hit your friend when they play your hosted parkour map. The gifts above keep hitting you. A gift listed in both hits both of you.");
	public final Option.Text pkFriendName = text("Friend's Name", "", 120, "empty = everyone except you").showIf(pkFriendOn::on);
	private final Option.Text pkFUp1 = text("Friend: UP 1 LVL gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFUp1Pick = giftPicker("Friend: UP 1 LVL", pkFUp1, pkFriendOn);
	private final Option.Text pkFDown1 = text("Friend: DOWN 1 LVL gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFDown1Pick = giftPicker("Friend: DOWN 1 LVL", pkFDown1, pkFriendOn);
	private final Option.Text pkFUp10 = text("Friend: UP 10 LVL gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFUp10Pick = giftPicker("Friend: UP 10 LVL", pkFUp10, pkFriendOn);
	private final Option.Text pkFDown10 = text("Friend: DOWN 10 LVL gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFDown10Pick = giftPicker("Friend: DOWN 10 LVL", pkFDown10, pkFriendOn);
	private final Option.Text pkFReset = text("Friend: RESET gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFResetPick = giftPicker("Friend: RESET", pkFReset, pkFriendOn);
	private final Option.Text pkFSave = text("Friend: SAVE gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFSavePick = giftPicker("Friend: SAVE", pkFSave, pkFriendOn);
	private final Option.Text pkFPunch = text("Friend: PUNCH gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFPunchPick = giftPicker("Friend: PUNCH", pkFPunch, pkFriendOn);
	private final Option.Text pkFJail = text("Friend: JAIL gifts", "", 200, "gift names, separated by commas").showIf(() -> false);
	private final Option.Action pkFJailPick = giftPicker("Friend: JAIL", pkFJail, pkFriendOn);
	public final Option.Num pkPunchStrength = num("Punch Strength", 100, 30, 300, 10, "%");
	private final Option.Choice pkTestPick = choice("Test Parkour Gift", 0, "UP 1 LVL", "DOWN 1 LVL", "UP 10 LVL", "DOWN 10 LVL", "RESET", "SAVE", "PUNCH", "JAIL");
	private final Option.Action pkTestBtn = action("Send the parkour test", "Send",
			() -> dev.sleepclient.parkour.ParkourLive.test(dev.sleepclient.parkour.ParkourLive.Act.values()[pkTestPick.idx()]),
			() -> dev.sleepclient.parkour.ParkourLive.giftOf(dev.sleepclient.parkour.ParkourLive.Act.values()[pkTestPick.idx()]));

	private final Option.Action pkTestFriendBtn = action("Send the test to your friend", "Send",
			() -> dev.sleepclient.parkour.ParkourLive.testFriend(dev.sleepclient.parkour.ParkourLive.Act.values()[pkTestPick.idx()]), () -> "").showIf(pkFriendOn::on);

	private final Option.Action pkSndUp1 = action("Sound: UP 1 LVL", "Choose file", () -> pickSlot("pk_up1"), () -> pkSound(0));
	private final Option.Action pkSndDown1 = action("Sound: DOWN 1 LVL", "Choose file", () -> pickSlot("pk_down1"), () -> pkSound(1));
	private final Option.Action pkSndUp10 = action("Sound: UP 10 LVL", "Choose file", () -> pickSlot("pk_up10"), () -> pkSound(2));
	private final Option.Action pkSndDown10 = action("Sound: DOWN 10 LVL", "Choose file", () -> pickSlot("pk_down10"), () -> pkSound(3));
	private final Option.Action pkSndReset = action("Sound: RESET", "Choose file", () -> pickSlot("pk_reset"), () -> pkSound(4));
	private final Option.Action pkSndSave = action("Sound: SAVE", "Choose file", () -> pickSlot("pk_save"), () -> pkSound(5));
	private final Option.Action pkSndPunch = action("Sound: PUNCH", "Choose file", () -> pickSlot("pk_punch"), () -> pkSound(6));
	private final Option.Action pkSndJail = action("Sound: JAIL", "Choose file", () -> pickSlot("pk_jail"), () -> pkSound(7));
	private final Option.Action pkSndClear = action("Remove parkour sounds", "Clear", this::clearParkourSounds, () -> "");
	private final Option.Action pkSavePic = action("SAVE picture (PNG)", "Choose file", this::pickSavePicture, () -> this.savePicStatus);
	private final Option.Action pkSavePicReset = action("SAVE picture: back to the watermelon", "Reset", this::resetSavePicture, () -> "");
	private volatile String savePicStatus = "";

	private void pickSavePicture() {
		savePicStatus = "Choosing...";
		Thread t = new Thread(() -> {
			String path;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.png"));
				filters.flip();
				path = TinyFileDialogs.tinyfd_openFileDialog("Choose the SAVE picture (PNG)", "", filters, "PNG pictures", false);
			} catch (Throwable e) {
				savePicStatus = "No file picker";
				return;
			}
			if (path == null || path.isBlank()) {
				savePicStatus = "";
				return;
			}
			try {
				Path src = Path.of(path);
				if (Files.size(src) > 8L * 1024 * 1024) {
					savePicStatus = "Picture too big (max 8 MB)";
					return;
				}
				byte[] head = new byte[8];
				try (var in = Files.newInputStream(src)) {
					in.readNBytes(head, 0, 8);
				}
				if ((head[0] & 0xFF) != 0x89 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
					savePicStatus = "Use a .png picture";
					return;
				}
				Path dst = dev.sleepclient.parkour.ParkourLive.savePictureFile();
				Files.createDirectories(dst.getParent());
				Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
				savePicStatus = src.getFileName().toString();
				dev.sleepclient.parkour.ParkourLive.reloadSavePicture();
			} catch (Exception e) {
				savePicStatus = "Could not copy the picture";
			}
		}, "kubo-save-picture");
		t.setDaemon(true);
		t.start();
	}

	private void resetSavePicture() {
		try {
			Files.deleteIfExists(dev.sleepclient.parkour.ParkourLive.savePictureFile());
		} catch (Exception ignored) {
			// in use
		}
		savePicStatus = "";
		dev.sleepclient.parkour.ParkourLive.reloadSavePicture();
	}

	private volatile String[] pkSoundNames = new String[8];

	private String pkSound(int i) {
		String n = pkSoundNames[i];
		return n == null || n.isEmpty() ? "none" : n;
	}

	private static Path pkSoundFile(dev.sleepclient.parkour.ParkourLive.Act a) {
		return TikSound.find(root().resolve("parkour"), "pk_" + a.name().toLowerCase(Locale.ROOT));
	}

	/** Plays your sound for this Parkour Live action (used by the test buttons / pktest). */
	public void playParkourSound(dev.sleepclient.parkour.ParkourLive.Act a) {
		float vol = Math.max(0.3F, volume.f() / 100F);
		dev.sleepclient.social.Social.IO.execute(() -> TikSound.play(pkSoundFile(a), vol));
	}

	private void clearParkourSounds() {
		dev.sleepclient.social.Social.IO.execute(() -> {
			for (var a : dev.sleepclient.parkour.ParkourLive.Act.values()) {
				for (String ext : new String[] {".mp3", ".ogg", ".wav"}) {
					try {
						Files.deleteIfExists(root().resolve("parkour").resolve("pk_" + a.name().toLowerCase(Locale.ROOT) + ext));
					} catch (Exception ignored) {
						// in use
					}
				}
			}
			refreshSlots();
		});
	}

	/** The gift names (comma separated) that do this in Parkour Live. */
	/** The gifts that do this to your friend ("" = none, or Friend Gifts is off). */
	public String pkFriendGifts(dev.sleepclient.parkour.ParkourLive.Act a) {
		if (!pkFriendOn.on()) {
			return "";
		}
		return switch (a) {
			case UP1 -> pkFUp1.get();
			case DOWN1 -> pkFDown1.get();
			case UP10 -> pkFUp10.get();
			case DOWN10 -> pkFDown10.get();
			case RESET -> pkFReset.get();
			case SAVE -> pkFSave.get();
			case PUNCH -> pkFPunch.get();
			case JAIL -> pkFJail.get();
		};
	}

	public String pkGifts(dev.sleepclient.parkour.ParkourLive.Act a) {
		return switch (a) {
			case UP1 -> pkUp1.get();
			case DOWN1 -> pkDown1.get();
			case UP10 -> pkUp10.get();
			case DOWN10 -> pkDown10.get();
			case RESET -> pkReset.get();
			case SAVE -> pkSave.get();
			case PUNCH -> pkPunch.get();
			case JAIL -> pkJail.get();
		};
	}

	private final Option.Header hChat = header("Live chat on screen");
	private final Option.Bool showChat = bool("Show Chat", true);
	private final Option.Num chatLines = num("Max Lines", 8, 3, 16, 1, "");
	private final Option.Num chatWidth = num("Width", 230, 60, 800, 5, "");
	private final Option.Num chatSeconds = num("Message Time", 20, 4, 120, 1, "s");
	private final Option.Num chatBg = num("Background", 35, 0, 90, 5, "%");
	private final Option.Bool hideCommands = bool("Hide !commands", true);
	private final Option.Bool giftsInChat = bool("Gifts in Chat", true);
	private final Option.Bool followsInChat = bool("Follows in Chat", true);

	private final Option.Header hAlert = header("Gift alerts");
	private final Option.Bool alerts = bool("Gift Alert Banner", true);
	private final Option.Num alertSeconds = num("Alert Time", 5, 2, 12, 1, "s");
	private final Option.Num alertScale = num("Alert Size", 100, 20, 400, 5, "%");

	private final Option.Header hFx = header("Screen effects");
	private final Option.Bool effects = bool("Effects", true);
	private final Option.Choice smallEffect = choice("Small Gift Effect", 0, SMALL);
	private final Option.Choice bigEffect = choice("Big Gift Effect", 0, SMALL);
	private final Option.Num bigFrom = num("Big Gift From", 100, 5, 5000, 5, " coins");
	private final Option.Bool bigExtra = bool("Shockwave / Lightning on Big Gift", true);
	private final Option.Bool followFx = bool("Hearts on New Follower", true);
	private final Option.Num intensity = num("Intensity", 100, 30, 250, 5, "%");
	private final Option.Num maxParticles = num("Max Particles", 500, 100, 1500, 50, "");
	private final Option.Bool worldFx = bool("Particles Around You (in-game)", true);
	private final Option.Action btnFxSmall = action("Preview small effect", "Play", () -> preview(false), () -> "");
	private final Option.Action btnFxBig = action("Preview big effect", "Play", () -> preview(true), () -> "");

	private final Option.Header hSound = header("Sounds (your files: mp3, ogg, wav)");
	private final Option.Bool sounds = bool("Play Sounds", true);
	private final Option.Num volume = num("Sound Volume", 80, 0, 100, 5, "%");
	private final Option.Action btnDonate = action("Donation sound", "Choose file", () -> pickSlot("donate"), () -> slotName(0));
	private final Option.Action btnBig = action("Big gift sound", "Choose file", () -> pickSlot("big"), () -> slotName(1));
	private final Option.Action btnFollow = action("New follower sound", "Choose file", () -> pickSlot("follow"), () -> slotName(2));
	private final Option.Action btnClear = action("Remove my sounds", "Clear", this::clearSlots, () -> "");
	private final Option.Action btnTestSound = action("Test donation sound", "Play", this::testSound, () -> "");
	private final Option.Action btnFolder = action("Sounds folder (gifts/Rose.mp3)", "Open", this::openFolder, () -> "");

	private final Option.Header hMusic = header("Background music (plays all the time)");
	private final Option.Bool bgMusic = bool("Background Music", true)
			.tip("Your 1 or 2 songs play one after the other, forever, while this mod is on. They go quieter while a donation sound plays.");
	private final Option.Num musicVolume = num("Music Volume", 35, 0, 100, 5, "%");
	private final Option.Action btnBg1 = action("Background song 1", "Choose file", () -> pickSlot("bg1"), () -> bgName(0));
	private final Option.Action btnBg2 = action("Background song 2", "Choose file", () -> pickSlot("bg2"), () -> bgName(1));
	private final Option.Action btnBgClear = action("Remove background songs", "Clear", this::clearMusic, () -> TikMusic.playing().isEmpty() ? "" : "playing: " + TikMusic.playing());

	// ---------------------------------------------------------------- state
	private final TikClient client = new TikClient();
	private final TikFx fx = new TikFx();
	private final Random rnd = new Random();
	private final ArrayDeque<TikEvent> injected = new ArrayDeque<>();
	private final LinkedHashSet<String> seen = new LinkedHashSet<>();
	private final ArrayDeque<Line> chat = new ArrayDeque<>();
	private final ArrayList<Line> rows = new ArrayList<>();
	private final ArrayList<Alert> shown = new ArrayList<>();
	private final Map<String, Alert> streaks = new HashMap<>();
	private final ArrayDeque<Pending> pending = new ArrayDeque<>();
	private String lastConn = "";
	private long connChangedAt;
	private int ticks;
	private int fxCooldown;
	private volatile String sounds0 = "", sounds1 = "", sounds2 = "";
	private volatile String pickStatus = "";

	private static final class Line {
		final String name, text;
		final int colour;
		final long born;
		String[] wrapped;
		int wrappedW = -1;

		Line(String name, String text, int colour, long born) {
			this.name = name;
			this.text = text;
			this.colour = colour;
			this.born = born;
		}
	}

	private static final class Alert {
		final String who, gift;
		int count, coins;
		final int firstCoins;
		long born;

		Alert(String who, String gift, int count, int coins) {
			this.who = who;
			this.gift = gift;
			this.count = count;
			this.coins = coins;
			this.firstCoins = coins;
			this.born = System.currentTimeMillis();
		}
	}

	private record Pending(int type, float intensity, float seconds, int priority) {
	}

	public TikTokLiveMod() {
		super("TikTok Live", "tiktok_live", "Your TikTok live chat in the game, gift alerts, your own donation sounds and big screen effects.", 0.012F, 0.46F);
		instance = this;
		liveUser.showIf(() -> source.idx() != 1);
		apiKey.showIf(() -> source.idx() >= 2);
		apiKey.secret = true;
		tikfinityAddr.showIf(() -> source.idx() == 1);
		customAddr.showIf(() -> source.idx() == 3);
	}

	private static String[] concat(String a, String b, String[] rest) {
		String[] out = new String[rest.length + 2];
		out[0] = a;
		out[1] = b;
		System.arraycopy(rest, 0, out, 2, rest.length);
		return out;
	}

	// ---------------------------------------------------------------- life cycle
	@Override
	protected void onEnable() {
		lastConn = connKey();
		connChangedAt = 0L;
		refreshSlots();
		if (autoConnect.on()) {
			connectNow();
		}
	}

	@Override
	protected void onDisable() {
		client.disconnect();
		TikStats.reset();
		fx.clear();
		pending.clear();
		shown.clear();
		streaks.clear();
		TikSound.stopAll();
		TikMusic.stop();
	}

	@Override
	public void tick() {
		ticks++;
		if (testLive.on()) {
			simulateLive();
		} else if (testViewers > 0) {
			testViewers = 0;
			if (!client.connected()) {
				TikStats.reset();
			}
		}
		client.setDebug(debug.on());
		TikSound.tick();
		TikMusic.tick(bgMusic.on() ? bgSongs : List.of(), musicVolume.f() / 100F, TikSound.playing());
		watchSettings();
		// chat / gifts: a few per tick at most, so even a huge live never costs a frame
		int budget = 14;
		TikEvent e;
		while (budget > 0 && (e = injected.poll()) != null) {
			handle(e);
			budget--;
		}
		while (budget > 0 && (e = client.poll()) != null) {
			handle(e);
			budget--;
		}
		// start queued effects one by one (every 0.2 s) so a flood of gifts becomes a nice sequence, not a freeze
		if (fxCooldown > 0) {
			fxCooldown--;
		} else if (!pending.isEmpty()) {
			Pending p = pending.poll();
			fx.start(p.type, p.intensity, p.seconds);
			fxCooldown = 4;
		}
		if (ticks % 100 == 0) {
			long now = System.currentTimeMillis();
			chat.removeIf(l -> now - l.born > chatSeconds.i() * 1000L + 1500L);
			streaks.values().removeIf(a -> now - a.born > 40_000L);
			shown.removeIf(a -> now - a.born > alertSeconds.i() * 1000L + 600L);
		}
	}

	// ---------------------------------------------------------------- connection
	private String connKey() {
		return source.idx() + "|" + liveUser.get() + "|" + apiKey.get() + "|" + tikfinityAddr.get() + "|" + customAddr.get();
	}

	/** When you finish typing a new @user / key / address, reconnect by itself (after 1.5 s without typing). */
	private void watchSettings() {
		if (ticks % 5 != 0) {
			return;
		}
		String k = connKey();
		long now = System.currentTimeMillis();
		if (!k.equals(lastConn)) {
			lastConn = k;
			connChangedAt = now;
		} else if (connChangedAt != 0L && now - connChangedAt > 1500L) {
			connChangedAt = 0L;
			if (autoConnect.on()) {
				connectNow();
			}
		}
	}

	/** Writes in the TikTok chat box which event names arrived so far: if "follow" or "gift" is not there, they are not coming. */
	private void showSeen() {
		var lines = client.summary();
		if (lines.isEmpty()) {
			addChat("Sleep", "Nothing arrived yet (" + client.status() + ")", 0xFF7FB3F5);
			return;
		}
		addChat("Sleep", "Events that arrived:", 0xFF7FB3F5);
		for (String s : lines) {
			addChat("Sleep", s, 0xFF7FB3F5);
		}
	}

	private void toggleConnection() {
		if (client.wanted()) {
			client.disconnect();
		} else {
			connectNow();
		}
	}

	private void connectNow() {
		String user = TikClient.username(liveUser.get());
		switch (source.idx()) {
			case 0 -> {
				if (user == null) {
					client.fail("Type your @user (that's all)");
				} else {
					client.connectDirect(user);
				}
			}
			case 1 -> {
				String a = tikfinityAddr.get().trim();
				if (a.isEmpty()) {
					a = "ws://127.0.0.1:21213/";
				}
				if (!a.contains("://")) {
					a = "ws://" + a;
				}
				client.connect(a);
			}
			case 2 -> {
				if (user == null) {
					client.fail("Type your @user or live link");
				} else if (apiKey.get().isBlank()) {
					client.fail("Paste your Tik.Tools API key");
				} else {
					client.connect(TikClient.fill("wss://api.tik.tools?uniqueId={user}&apiKey={key}", user, apiKey.get().trim()));
				}
			}
			default -> {
				String t = customAddr.get().trim();
				if (t.isEmpty()) {
					client.fail("Type the WebSocket address");
				} else if (t.contains("{user}") && user == null) {
					client.fail("Type your @user or live link");
				} else {
					client.connect(TikClient.fill(t, user, apiKey.get().trim()));
				}
			}
		}
	}

	// ---------------------------------------------------------------- events
	private void handle(TikEvent e) {
		if (e.id() != null) {
			if (!seen.add(e.id())) {
				return; // the same event twice
			}
			if (seen.size() > 300) {
				var it = seen.iterator();
				it.next();
				it.remove();
			}
		}
		switch (e.type()) {
			case CHAT -> {
				if (hideCommands.on() && e.text().startsWith("!")) {
					return;
				}
				addChat(e.who(), e.text(), colourOf(e.user() == null ? e.who() : e.user()));
			}
			case GIFT -> {
				try {
					dev.sleepclient.parkour.ParkourLive.onGift(e);
				} catch (Throwable t) {
					System.err.println("[Sleep] Parkour Live: " + t);
				}
				onGift(e);
			}
			case FOLLOW -> {
				if (followsInChat.on()) {
					addChat(e.who(), "followed you!", 0xFFFF6BB5);
				}
				if (followFx.on() && effects.on()) {
					queue(TikFx.HEARTS, 1.1F * intensity.f() / 100F, 3F, 1);
				}
				playSlot("follow");
			}
			case SHARE -> {
				if (followsInChat.on()) {
					addChat(e.who(), "shared the live", 0xFF7FB3F5);
				}
			}
			case SUBSCRIBE -> {
				addChat(e.who(), "subscribed!", 0xFFFFD93D);
				addAlert(new Alert(e.who(), "Subscription", 1, 0));
				trigger(new TikEvent(TikEvent.Type.GIFT, e.user(), e.nick(), "", "Subscription", 0, 100, 1, false, null), 100);
			}
		}
	}

	private void onGift(TikEvent e) {
		int total = e.coins();
		String key = e.streakKey();
		Alert running = streaks.get(key);
		if (e.streaking()) {
			if (running != null) { // the viewer keeps holding the button: just update the counter
				running.count = e.repeat();
				running.coins = total;
				running.born = System.currentTimeMillis();
				return;
			}
			Alert a = new Alert(e.who(), e.gift(), e.repeat(), total);
			streaks.put(key, a);
			addAlert(a);
			giftChat(e);
			trigger(e, e.coinsEach());
			return;
		}
		if (running != null) { // the streak ended: final count
			streaks.remove(key);
			running.count = e.repeat();
			running.coins = total;
			running.born = System.currentTimeMillis();
			if (total >= bigFrom.i() && running.firstCoins < bigFrom.i()) {
				trigger(e, total); // the streak grew into a big gift
			}
			return;
		}
		addAlert(new Alert(e.who(), e.gift(), e.repeat(), total));
		giftChat(e);
		trigger(e, total);
	}

	private void giftChat(TikEvent e) {
		if (giftsInChat.on()) {
			addChat(e.who(), "sent " + e.gift() + (e.repeat() > 1 ? " x" + e.repeat() : ""), 0xFFFFD93D);
		}
	}

	private void addChat(String name, String text, int colour) {
		chat.addLast(new Line(name.length() > 20 ? name.substring(0, 20) : name, text, colour, System.currentTimeMillis()));
		while (chat.size() > 40) {
			chat.pollFirst();
		}
	}

	private void addAlert(Alert a) {
		if (!alerts.on()) {
			return;
		}
		shown.add(a);
		while (shown.size() > 4) {
			shown.remove(0);
		}
	}

	private static int colourOf(String s) {
		float hue = (Math.abs(s.hashCode()) % 360) / 360F;
		return java.awt.Color.HSBtoRGB(hue, 0.5F, 1F) | 0xFF000000;
	}

	// ---------------------------------------------------------------- reactions to a gift
	private void trigger(TikEvent e, int coins) {
		boolean big = coins >= bigFrom.i();
		if (sounds.on()) {
			playGiftSound(e.gift(), coins);
		}
		if (effects.on()) {
			float inten = (float) Math.max(0.7, Math.min(2.6, 0.7 + Math.log10(coins + 1) * 0.45)) * intensity.f() / 100F;
			float secs = 2.2F + Math.min(5F, coins / 60F);
			if (big) {
				secs *= 1.4F;
			}
			queue(effectFor(coins, big), inten, secs, big ? 3 : 2);
			if (big && bigExtra.on()) {
				queue(coins >= bigFrom.i() * 5 ? TikFx.LIGHTNING : TikFx.SHOCKWAVE, inten, 2F, 3);
			}
		}
		if (worldFx.on()) {
			worldBurst(big);
		}
	}

	private int effectFor(int coins, boolean big) {
		int sel = (big ? bigEffect : smallEffect).idx();
		if (sel >= 2) {
			return sel - 2;
		}
		if (sel == 1) {
			return rnd.nextInt(TikFx.NAMES.length);
		}
		if (big) {
			int[] pool = {TikFx.FIREWORKS, TikFx.COINS, TikFx.STARFALL, TikFx.RAINBOW};
			return coins >= bigFrom.i() * 5 ? TikFx.FIREWORKS : pool[rnd.nextInt(pool.length)];
		}
		int[] pool = coins >= 30 ? new int[] {TikFx.CONFETTI, TikFx.STARFALL, TikFx.SAKURA} : new int[] {TikFx.HEARTS, TikFx.CONFETTI, TikFx.SAKURA};
		return pool[rnd.nextInt(pool.length)];
	}

	/** Effects wait in a short line; when it is full, the least important one goes away. */
	private void queue(int type, float inten, float secs, int priority) {
		if (pending.size() >= 8) {
			Pending weakest = null;
			for (Pending p : pending) {
				if (weakest == null || p.priority < weakest.priority) {
					weakest = p;
				}
			}
			if (weakest == null || weakest.priority > priority) {
				return;
			}
			pending.remove(weakest);
		}
		pending.add(new Pending(type, inten, secs, priority));
	}

	private void worldBurst(boolean big) {
		var p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		double x = p.getX(), y = p.getY() + 1.2, z = p.getZ();
		if (big) {
			Fx.burst(ParticleTypes.FIREWORK, x, y + 0.8, z, 40, 0.6);
			Fx.ring(ParticleTypes.END_ROD, x, y - 0.8, z, 1.2, 24, 0.05);
			Fx.burst(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 20, 0.5);
		} else {
			Fx.burst(ParticleTypes.HEART, x, y + 0.5, z, 8, 0.25);
			Fx.burst(ParticleTypes.HAPPY_VILLAGER, x, y + 0.3, z, 6, 0.3);
		}
	}

	private void preview(boolean big) {
		int coins = big ? Math.max(bigFrom.i(), 100) : 5;
		float inten = (big ? 1.8F : 1F) * intensity.f() / 100F;
		queue(effectFor(coins, big), inten, big ? 6F : 3F, 3);
		if (big && bigExtra.on()) {
			queue(TikFx.SHOCKWAVE, inten, 2F, 3);
		}
	}

	// ---------------------------------------------------------------- tests
	private static final String[] NAMES = {"Maria", "xX_gamer_Xx", "Joao", "Bea", "Tiago", "Luna", "Rui", "Sofia"};
	private static final String[] MSGS = {"boa live!!", "first!", "como e que fazes isso?", "gg", "eu adoro este jogo", "manda cora\u00e7\u00f5es",
			"quem esta a ver de portugal?", "wow que lindo", "mais um gift pra ti"};

	private void testChat() {
		String n = NAMES[rnd.nextInt(NAMES.length)];
		injected.add(TikEvent.chat(n.toLowerCase(Locale.ROOT), n, MSGS[rnd.nextInt(MSGS.length)]));
	}

	private void testGift(String gift, int repeat, int coins) {
		String n = NAMES[rnd.nextInt(NAMES.length)];
		injected.add(TikEvent.gift(n.toLowerCase(Locale.ROOT), n, gift, coins, repeat));
	}

	private void testViewers() {
		int v = TikStats.viewers < 0 ? 120 + rnd.nextInt(400) : Math.max(1, TikStats.viewers + rnd.nextInt(80) - 25);
		TikStats.fake(v, Math.max(0, TikStats.likes) + 50 + rnd.nextInt(900));
	}

	// ---------------------------------------------------------------- smooth stream
	private volatile String obsNote = "";

	/** For the frame-rate hook: -1 not live, 0 keep full FPS, n also lock to n FPS. */
	public int streamFps() {
		if (!enabled || !client.connected() || !keepFps.on()) {
			return -1;
		}
		return switch (lockFps.idx()) {
			case 1 -> 30;
			case 2 -> 60;
			case 3 -> 120;
			default -> 0;
		};
	}

	/** Connected to the live right now (the viewer counter shows only then, unless it is set to always show). */
	public boolean live() {
		return enabled && (client.connected() || testLive.on());
	}

	// ---------------------------------------------------------------- test live
	/** Real TikTok gifts and their price in coins. */
	private static final String[] GIFT_NAMES = {"Rose", "TikTok", "Finger Heart", "Doughnut", "Hand Hearts", "Corgi", "Money Gun", "Galaxy", "Whale diving",
			"Lion", "Universe"};
	private static final int[] GIFT_COINS = {1, 1, 5, 30, 100, 299, 500, 1000, 2150, 29999, 44999};
	private int testViewers = 0;

	private void sendPickedGift() {
		int i = testGiftPick.idx();
		testGift(GIFT_NAMES[i], 1, GIFT_COINS[i]);
	}

	/** One tick of the pretend live: events arrive at random, like a real one. */
	private void simulateLive() {
		float k = switch (testSpeed.idx()) {
			case 0 -> 0.4F;
			case 2 -> 3F;
			default -> 1F;
		};
		// chances per tick (20 ticks = 1 s)
		if (rnd.nextFloat() < 0.035F * k) {
			testChat();
		}
		if (rnd.nextFloat() < 0.006F * k) {
			int i = rnd.nextInt(5); // the cheap ones most of the time
			testGift(GIFT_NAMES[i], 1 + (rnd.nextInt(4) == 0 ? rnd.nextInt(10) : 0), GIFT_COINS[i]);
		}
		if (rnd.nextFloat() < 0.0009F * k) {
			int i = 5 + rnd.nextInt(GIFT_NAMES.length - 5);
			testGift(GIFT_NAMES[i], 1, GIFT_COINS[i]);
		}
		if (rnd.nextFloat() < 0.004F * k) {
			testFollow();
		}
		if (rnd.nextFloat() < 0.002F * k) {
			String n = NAMES[rnd.nextInt(NAMES.length)];
			injected.add(TikEvent.simple(TikEvent.Type.SHARE, n.toLowerCase(Locale.ROOT), n));
		}
		if (ticks % 40 == 0) {
			// viewers drift up and down around a level that grows slowly
			if (testViewers <= 0) {
				testViewers = 40 + rnd.nextInt(60);
			}
			testViewers = Math.max(1, testViewers + Math.round((rnd.nextInt(21) - 8) * k));
			long likesNow = Math.max(0, TikStats.likes) + Math.round((20 + rnd.nextInt(120)) * k);
			TikStats.fake(testViewers, likesNow);
		}
	}

	private static final String OBS_GUIDE = String.join("\n",
			"TikTok LIVE from OBS (best settings, Mac M1):",
			"1) Stream key: tiktok.com > Go LIVE (livecenter.tiktok.com/producer) > Save & Go LIVE > copy Server URL + Stream Key into OBS > Settings > Stream > Custom.",
			"2) Video: Base (canvas) 1080x1920, Output 1080x1920 (vertical, made for phones), FPS 30 (or 60 on fast internet).",
			"3) Output > Streaming: Encoder 'Apple VT H264 Hardware Encoder' (uses the Mac's media engine, not the GPU the game needs), Rate control CBR, Bitrate 4500 kbps (3000 on weak Wi-Fi, 6000 max), Keyframe interval 2 s, Profile high.",
			"4) Audio: 128 kbps, 48 kHz.",
			"5) Capture the game with 'macOS Screen Capture' > Window, pick Minecraft. Put the game in the middle of the vertical canvas (or use the Vertical Clips mod layout), the TikTok chat / viewer counter of Sleep are already in the game picture.",
			"6) Advanced > Process priority: Above normal. Use a cable instead of Wi-Fi if you can (5 Mbps upload or more).",
			"7) In Sleep: TikTok Live > Smooth stream: Keep Full FPS While Live ON, Lock FPS 60. If OBS says 'encoding overloaded' or 'rendering lag', lower Minecraft's render distance or turn off the Mac's sharp Retina (Performance).");

	private void copyObsGuide() {
		try {
			Minecraft.getInstance().keyboardHandler.setClipboard(OBS_GUIDE);
			obsNote = "Copied - paste it anywhere";
		} catch (Exception e) {
			obsNote = "Could not copy";
		}
		if (Minecraft.getInstance().player != null) {
			for (String l : OBS_GUIDE.split("\n")) {
				Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(l));
			}
		}
	}

	private void testFollow() {
		String n = NAMES[rnd.nextInt(NAMES.length)];
		injected.add(TikEvent.simple(TikEvent.Type.FOLLOW, n.toLowerCase(Locale.ROOT), n));
	}

	// ---------------------------------------------------------------- sounds
	private static Path root() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubo-tiktok").resolve("sounds");
	}

	private static String sanitize(String s) {
		return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
	}

	/** Looks for the file on a background thread (never touches the disk on the game thread), then plays it. */
	private void playGiftSound(String gift, int coins) {
		float vol = volume.f() / 100F;
		int bigLimit = bigFrom.i();
		dev.sleepclient.social.Social.IO.execute(() -> {
			Path f = TikSound.find(root().resolve("gifts"), sanitize(gift));
			if (f == null) {
				// a Parkour Live gift (Galaxy = RESET...): your sound for that action
				var act = dev.sleepclient.parkour.ParkourLive.actFor(gift);
				if (act != null) {
					f = pkSoundFile(act);
				}
			}
			if (f == null && coins >= bigLimit) {
				f = TikSound.find(root(), "big");
			}
			if (f == null) {
				f = TikSound.find(root(), "donate");
			}
			TikSound.play(f, vol);
		});
	}

	private void playSlot(String slot) {
		if (!sounds.on()) {
			return;
		}
		float vol = volume.f() / 100F;
		dev.sleepclient.social.Social.IO.execute(() -> TikSound.play(TikSound.find(root(), slot), vol));
	}

	private void testSound() {
		float vol = Math.max(0.3F, volume.f() / 100F);
		dev.sleepclient.social.Social.IO.execute(() -> {
			Path f = TikSound.find(root(), "donate");
			if (f == null) {
				pickStatus = "Choose a donation sound first";
				return;
			}
			TikSound.play(f, vol);
		});
	}

	private String slotName(int i) {
		String n = i == 0 ? sounds0 : i == 1 ? sounds1 : sounds2;
		if (!pickStatus.isEmpty() && i == 0) {
			return pickStatus;
		}
		return n.isEmpty() ? "none" : n;
	}

	private void refreshSlots() {
		dev.sleepclient.social.Social.IO.execute(() -> {
			try {
				Files.createDirectories(root().resolve("gifts"));
				Path readme = root().resolve("README.txt");
				if (!Files.exists(readme)) {
					Files.writeString(readme, """
							Sleep - TikTok Live sounds
							=========================
							Put your own sounds here (.mp3, .ogg or .wav):
							  donate.mp3  -> plays for every gift
							  big.mp3     -> plays for big gifts (Big Gift From, in the mod settings)
							  follow.mp3  -> plays when someone follows
							  gifts/Rose.mp3, gifts/Lion.mp3 ... -> a different sound for each gift (file name = gift name)
							You can also pick the files with the buttons in the mod settings,
							or drag a sound onto the Sleep menu while the TikTok Live settings are open.
							Short sounds (1-5 seconds) work best. Only use sounds you are allowed to use on stream.
							""");
				}
			} catch (Exception ignored) {
				// no folder: the mod just has no custom sounds
			}
			sounds0 = nameOf(TikSound.find(root(), "donate"));
			sounds1 = nameOf(TikSound.find(root(), "big"));
			sounds2 = nameOf(TikSound.find(root(), "follow"));
			List<Path> bg = new ArrayList<>();
			for (String slot : new String[] {"bg1", "bg2"}) {
				Path f = TikSound.find(root().resolve("music"), slot);
				if (f != null) {
					bg.add(f);
				}
			}
			bgSongs = List.copyOf(bg);
			String[] pk = new String[8];
			for (var a : dev.sleepclient.parkour.ParkourLive.Act.values()) {
				pk[a.ordinal()] = nameOf(pkSoundFile(a));
			}
			pkSoundNames = pk;
			bgNames = new String[] {nameOf(TikSound.find(root().resolve("music"), "bg1")), nameOf(TikSound.find(root().resolve("music"), "bg2"))};
			pickStatus = "";
		});
	}

	private static String nameOf(Path p) {
		return p == null ? "" : p.getFileName().toString();
	}

	private void pickSlot(String slot) {
		pickStatus = "Choosing...";
		Thread t = new Thread(() -> {
			String path;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(3);
				filters.put(stack.UTF8("*.mp3")).put(stack.UTF8("*.ogg")).put(stack.UTF8("*.wav"));
				filters.flip();
				path = TinyFileDialogs.tinyfd_openFileDialog("Choose a sound (" + slot + ")", "", filters, "Sounds", false);
			} catch (Throwable e) {
				pickStatus = "No file picker: drag the sound onto the window";
				return;
			}
			if (path == null || path.isBlank()) {
				pickStatus = "";
				return;
			}
			saveSlot(slot, Path.of(path));
		}, "kubo-tiktok-picker");
		t.setDaemon(true);
		t.start();
	}

	/** Called when a file is dropped onto the Sleep menu while this mod's settings are open (becomes the donation sound). */
	@Override
	public boolean onFileDropped(Path file) {
		dropFile(file);
		return true;
	}

	public void dropFile(Path file) {
		if (file == null || !TikSound.supported(file.getFileName().toString())) {
			pickStatus = "Drop a .mp3, .ogg or .wav";
			return;
		}
		dev.sleepclient.social.Social.IO.execute(() -> saveSlot("donate", file));
	}

	private void saveSlot(String slot, Path src) {
		try {
			String n = src.getFileName().toString();
			if (!TikSound.supported(n)) {
				pickStatus = "Use .mp3, .ogg or .wav";
				return;
			}
			boolean music = slot.startsWith("bg") || slot.startsWith("pk_");
			if (Files.size(src) > (music ? 40L : 20L) * 1024 * 1024) {
				pickStatus = "File too big (max " + (music ? 40 : 20) + " MB)";
				return;
			}
			Path dir = slot.startsWith("pk_") ? root().resolve("parkour") : music ? root().resolve("music") : root();
			Files.createDirectories(dir);
			for (String ext : new String[] {".mp3", ".ogg", ".wav"}) {
				Files.deleteIfExists(dir.resolve(slot + ext));
			}
			String ext = n.substring(n.lastIndexOf('.')).toLowerCase(Locale.ROOT);
			Files.copy(src, dir.resolve(slot + ext), StandardCopyOption.REPLACE_EXISTING);
			pickStatus = "";
			refreshSlots();
			if (slot.startsWith("pk_")) {
				TikSound.play(dir.resolve(slot + ext), Math.max(0.3F, volume.f() / 100F)); // so you hear it right away
			} else if (music) {
				TikMusic.restart(); // starts again with the new song
			} else {
				TikSound.play(dir.resolve(slot + ext), Math.max(0.3F, volume.f() / 100F)); // so you hear it right away
			}
		} catch (Exception e) {
			pickStatus = "Could not copy the file";
		}
	}

	private volatile List<Path> bgSongs = List.of();
	private volatile String[] bgNames = {"", ""};

	private String bgName(int i) {
		String n = bgNames[i];
		return n.isEmpty() ? "none" : n;
	}

	private void clearMusic() {
		dev.sleepclient.social.Social.IO.execute(() -> {
			for (String slot : new String[] {"bg1", "bg2"}) {
				for (String ext : new String[] {".mp3", ".ogg", ".wav"}) {
					try {
						Files.deleteIfExists(root().resolve("music").resolve(slot + ext));
					} catch (Exception ignored) {
						// in use
					}
				}
			}
			refreshSlots();
		});
	}

	private void clearSlots() {
		dev.sleepclient.social.Social.IO.execute(() -> {
			for (String slot : new String[] {"donate", "big", "follow"}) {
				for (String ext : new String[] {".mp3", ".ogg", ".wav"}) {
					try {
						Files.deleteIfExists(root().resolve(slot + ext));
					} catch (Exception ignored) {
						// in use: leave it
					}
				}
			}
			refreshSlots();
		});
	}

	/** Opens the folder in the system's file manager (plain Java, no game API involved). */
	private void openFolder() {
		dev.sleepclient.social.Social.IO.execute(() -> {
			try {
				Files.createDirectories(root().resolve("gifts"));
				String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
				String p = root().toAbsolutePath().toString();
				new ProcessBuilder(os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open", p).start();
			} catch (Exception e) {
				pickStatus = "Folder: " + root();
			}
		});
	}

	// ---------------------------------------------------------------- drawing: chat (movable HUD box)
	@Override
	public int width() {
		return chatWidth.i();
	}

	@Override
	public int height() {
		return chatLines.i() * 11;
	}

	@Override
	public boolean visible() {
		return showChat.on() && !chat.isEmpty();
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		int lh = 11, wpx = width(), maxRows = chatLines.i();
		long now = System.currentTimeMillis();
		long life = chatSeconds.i() * 1000L;
		if (edit && chat.isEmpty()) {
			Gfx.rrect(c, 0, 0, wpx, maxRows * lh, 4, Gfx.withAlpha(0x000000, 90));
			Gfx.text(c, "TikTok Live chat", 4, 3, 0xFFFFFFFF);
			Gfx.text(c, "Maria: boa live!!", 4, 3 + lh, 0xFFAAAAAA);
			return;
		}
		// newest messages first until the box is full
		rows.clear();
		int used = 0;
		var it = chat.descendingIterator();
		while (it.hasNext()) {
			Line l = it.next();
			if (now - l.born > life) {
				continue;
			}
			wrap(l, wpx);
			if (used + l.wrapped.length > maxRows && used > 0) {
				break;
			}
			rows.add(l);
			used += l.wrapped.length;
			if (used >= maxRows) {
				break;
			}
		}
		if (rows.isEmpty()) {
			return;
		}
		int bg = chatBg.i();
		int top = Math.max(0, maxRows - used) * lh;
		if (bg > 0) {
			Gfx.rrect(c, 0, top - 2, wpx, used * lh + 4, 4, Gfx.withAlpha(0x000000, bg * 255 / 100));
		}
		int y = top;
		for (int i = rows.size() - 1; i >= 0; i--) { // oldest of the visible ones first, newest at the bottom
			Line l = rows.get(i);
			long age = now - l.born;
			int a = age > life - 1000L ? (int) Math.max(0, 255 * (life - age) / 1000L) : 255;
			int nameW = Gfx.tw(l.name + ": ");
			for (int r = 0; r < l.wrapped.length; r++) {
				if (r == 0) {
					Gfx.text(c, l.name + ":", 4, y + 2, Gfx.withAlpha(l.colour, a));
					Gfx.text(c, l.wrapped[0], 4 + nameW, y + 2, Gfx.withAlpha(0xFFFFFF, a));
				} else {
					Gfx.text(c, l.wrapped[r], 4, y + 2, Gfx.withAlpha(0xFFFFFF, a));
				}
				y += lh;
			}
		}
	}

	/** Word wrap, cached in the line (recomputed only when the width changes): drawing a frame allocates nothing. */
	private static void wrap(Line l, int widthPx) {
		if (l.wrapped != null && l.wrappedW == widthPx) {
			return;
		}
		l.wrappedW = widthPx;
		int avail = widthPx - 8;
		int nameW = Gfx.tw(l.name + ": ");
		List<String> out = new ArrayList<>(3);
		StringBuilder cur = new StringBuilder();
		int curW = nameW;
		for (String word : l.text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			int ww = Gfx.tw(word);
			int sp = cur.length() == 0 ? 0 : Gfx.tw(" ");
			if (curW + sp + ww <= avail) {
				if (cur.length() > 0) {
					cur.append(' ');
				}
				cur.append(word);
				curW += sp + ww;
				continue;
			}
			if (cur.length() > 0) {
				out.add(cur.toString());
				cur.setLength(0);
				curW = 0;
			}
			while (Gfx.tw(word) > avail && word.length() > 1) { // a single very long word: cut it
				int cut = word.length() - 1;
				while (cut > 1 && Gfx.tw(word.substring(0, cut)) > avail) {
					cut--;
				}
				out.add(word.substring(0, cut));
				word = word.substring(cut);
			}
			cur.append(word);
			curW = Gfx.tw(word);
			if (out.size() >= 4) {
				break;
			}
		}
		if (cur.length() > 0 || out.isEmpty()) {
			out.add(cur.toString());
		}
		l.wrapped = out.toArray(new String[0]);
	}

	// ---------------------------------------------------------------- drawing: effects + gift alerts (full screen)
	public void drawOverlay(GuiGraphicsExtractor c) {
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
		if (effects.on()) {
			int fps = mc.getFps();
			fx.setCap(maxParticles.i());
			fx.setLoad(fps < 30 ? 0.4F : fps < 50 ? 0.75F : 1F);
			fx.draw(c, w, h);
		} else if (fx.active()) {
			fx.clear();
		}
		if (alerts.on() && !shown.isEmpty()) {
			drawAlerts(c, w);
		}
	}

	private void drawAlerts(GuiGraphicsExtractor c, int sw) {
		long now = System.currentTimeMillis();
		long life = alertSeconds.i() * 1000L;
		float s = alertScale.f() / 100F;
		int y = 8;
		for (int i = 0; i < shown.size(); i++) {
			Alert a = shown.get(i);
			long age = now - a.born;
			if (age > life) {
				continue;
			}
			float in = Math.min(1F, age / 260F);
			float ease = 1F - (1F - in) * (1F - in);
			int alpha = age > life - 400L ? (int) Math.max(0, 255 * (life - age) / 400L) : 255;
			if (alpha < 8) {
				continue;
			}
			String who = a.who.length() > 16 ? a.who.substring(0, 16) : a.who;
			String mid = " sent ";
			String cnt = a.count > 1 ? "  x" + a.count : "";
			int tw = Gfx.tw(who) + Gfx.tw(mid) + Gfx.tw(a.gift) + Gfx.tw(cnt);
			String coinTxt = a.coins > 0 ? Integer.toString(a.coins) : "";
			int bw = Math.max(150, tw + 36 + (coinTxt.isEmpty() ? 0 : Gfx.tw(coinTxt) + 20));
			int accent = a.coins >= bigFrom.i() * 5 ? 0xFFB57BFF : a.coins >= bigFrom.i() ? 0xFFFFD93D : a.coins >= 30 ? 0xFFFF6BB5 : 0xFF7FB3F5;
			int x = Math.round((sw / s - bw) / 2F);
			int yy = Math.round(y / s - (1F - ease) * 26F);
			c.pose().pushMatrix();
			c.pose().scale(s, s);
			Gfx.rrect(c, x, yy, bw, 24, 8, Gfx.withAlpha(0x0E0F13, alpha * 230 / 255));
			Gfx.rrectBorder(c, x, yy, bw, 24, 8, 0x00000000, Gfx.withAlpha(accent, alpha));
			Gfx.rrect(c, x + 5, yy + 6, 3, 12, 1, Gfx.withAlpha(accent, alpha));
			int tx = x + 14;
			Gfx.text(c, who, tx, yy + 8, Gfx.withAlpha(0xFFFFFF, alpha));
			tx += Gfx.tw(who);
			Gfx.text(c, mid, tx, yy + 8, Gfx.withAlpha(0x8C90A0, alpha));
			tx += Gfx.tw(mid);
			Gfx.text(c, a.gift, tx, yy + 8, Gfx.withAlpha(accent, alpha));
			tx += Gfx.tw(a.gift);
			if (!cnt.isEmpty()) {
				Gfx.text(c, cnt, tx, yy + 8, Gfx.withAlpha(0xFFFFFF, alpha));
			}
			if (!coinTxt.isEmpty()) {
				int rx = x + bw - 10 - Gfx.tw(coinTxt);
				Gfx.text(c, coinTxt, rx, yy + 8, Gfx.withAlpha(0xFFD93D, alpha));
				Gfx.coin(c, rx - 13, yy + 5, 11);
			}
			c.pose().popMatrix();
			y += Math.round(28 * s);
		}
	}
}
