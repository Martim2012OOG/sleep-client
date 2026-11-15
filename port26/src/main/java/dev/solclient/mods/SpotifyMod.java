package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.music.CoverFinder;
import dev.solclient.music.Lyrics;
import dev.solclient.music.LyricsView;
import dev.solclient.music.MusicHub;
import dev.solclient.option.Option;
import dev.solclient.social.RemoteTextures;
import dev.solclient.spotify.SpotifyAuth;
import dev.solclient.spotify.SpotifyClient;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Music: shows what is playing on this computer (cover, title, artist, progress) from ANY player - Spotify, YouTube Music,
 * Apple Music, SoundCloud... in an app or a browser tab - and controls it with keys (see {@link MusicHub}).
 * Connecting a Spotify account is optional (best covers, playlists). The id stays "spotify" so saved settings keep working.
 * <p>
 * Settings are split in tabs (Look / Colours / Animation / Account) with a live preview on top:
 * 4 styles (Card, Pill, Vinyl, Minimal), width, cover size, roundness, colours taken from the album cover or your own,
 * glow, spinning vinyl, visualizer bars, song-change animations. Move / resize the box like any other HUD (HUD editor).
 */
public class SpotifyMod extends HudMod {

	public static SpotifyMod instance;

	private static final int CARD = 0, PILL = 1, VINYL = 2, MINIMAL = 3, PLAYER = 4;

	private Option.Choice section;
	// look
	private Option.Choice style;
	private Option.Num widthOpt, coverSize, roundness;
	private Option.Bool showArt, showTimes, showArtist, showProgress, hidePaused, textShadow;
	// colours
	private Option.Choice colourMode;
	private Option.Col bg, accent, textColour, artistColour, trackColour;
	private Option.Num tintStrength, glow;
	// animation
	private Option.Bool showBars, spin, pulse, smooth, cinematic;
	private Option.Choice barsStyle, songAnim;
	private Option.Num barCount, scrollSpeed, spinSpeed;
	// lyrics (in the sky + top capsule)
	private Option.Bool lyricsOn, lyricsBar, lyricsWorld, lyricsOnlyPlaying, lyricsScript;
	private Option.Choice lyricsBacking;
	private Option.Num lyricsFrontLift, lyricsWords;
	private Option.Num lyricsBackingAlpha;
	private Option.Bool lyricsFrontFollow;
	private Option.Num lyricsFrontDist, lyricsFrontFade, lyricsFrontKeep, lyricsSpread, lyricsSize, lyricsHeight, lyricsSync, lyricsBarSize, lyricsOpacity, lyricsDepth, lyricsSway;
	private Option.Col lyricsHighlight, lyricsText, lyricsText2, lyricsText3, lyricsText4;
	private Option.Choice lyricsColours, lyricsPosition, lyricsPlace, lyricsFont;
	private Option.Bool lyricsBehind;
	private Option.Num lyricsPalette, lyricsGradFlow, lyricsGradN, lyricsGlow;
	private Option.Choice lyricsGradient, lyricsAnim;
	private Option.Col lyricsGradA, lyricsGradB, lyricsGradC;
	private Option.Bool lyricsUpper;
	private final LyricsView.Cfg lyricsCfg = new LyricsView.Cfg();
	// now playing card (artist first, in the sky or flat)
	private Option.Bool cardOn, cardOnlyPlaying, cardHideLyrics, cardLabelOn, cardArtist, cardSource, cardLines, cardUpper, cardFollow, cardBehind;
	private Option.Choice cardPlace, cardFont, cardPos, cardAnim, cardColourMode;
	private Option.Choice cardGradient;
	private Option.Num cardGradFlow;
	private Option.Num cardSeconds, cardRepeat, cardSize, cardArtistSize, cardLabelSize, cardHeight, cardSpread, cardDepth, cardOpacity, cardGlow;
	private Option.Col cardTitleCol, cardArtistCol, cardLabelCol, cardAccent;
	private Option.Text cardLabel;
	private final dev.solclient.music.NowPlayingCard.Cfg cardCfg = new dev.solclient.music.NowPlayingCard.Cfg();
	// account / controls
	private Option.Text clientId, extraSites;
	private Option.Key kPlay, kNext, kPrev;

	private final boolean[] down = new boolean[3];

	// animation state
	private String lastKey = "";
	private long changedAt, lastDrawn, appearAt;
	private float playT = 1F, shownProgress = -1F, spinAngle;
	private long lastFrame;
	private final float[] barH = new float[32];
	/** global alpha of the element (appear animation) */
	private float ga = 1F;

	public SpotifyMod() {
		super("Music", "spotify", "What is playing on your computer, from any player: Spotify, YouTube Music, Apple Music, SoundCloud... in an app or in the browser. Keys to pause / skip.", 0.01F, 0.80F);
		instance = this;
		options.clear();

		section = tabs("Section", 0, "Look", "Colours", "Animation", "Account", "Lyrics", "Intro");
		BooleanSupplier look = () -> section.idx() == 0, cols = () -> section.idx() == 1, anim = () -> section.idx() == 2,
				acc = () -> section.idx() == 3, lyr = () -> section.idx() == 4, np = () -> section.idx() == 5;

		// ------------------------------------------------------------------ Look
		style = tabs("Style", PLAYER, "Card", "Pill", "Vinyl", "Minimal", "Player");
		style.visibleIf = look;
		options.add(scale);
		scale.visibleIf = look;
		widthOpt = num("Width", 150, 40, 600, 1, " px");
		coverSize = num("Cover size", 38, 8, 160, 1, " px");
		roundness = num("Roundness", 10, 0, 24, 1, "");
		widthOpt.visibleIf = look;
		coverSize.visibleIf = () -> look.getAsBoolean() && (style.idx() == CARD || style.idx() == VINYL || style.idx() == MINIMAL) && showArt.on();
		roundness.visibleIf = () -> look.getAsBoolean() && style.idx() != PILL;
		header("Show").showIf(look);
		showArt = bool("Cover", true);
		showArtist = bool("Artist", true);
		showProgress = bool("Progress Bar", true);
		showTimes = bool("Times", true);
		textShadow = bool("Text Shadow", true);
		hidePaused = bool("Hide When Paused", false);
		for (Option<?> o : new Option<?>[] {showArt, showArtist, showProgress, textShadow, hidePaused}) {
			o.visibleIf = look;
		}
		showTimes.visibleIf = () -> look.getAsBoolean() && showProgress.on() && style.idx() != PILL;

		// --------------------------------------------------------------- Colours
		colourMode = tabs("Colours from", 0, "Album cover", "My colours");
		colourMode.visibleIf = cols;
		accent = colour("Bar Colour", 0xFF1DB954);
		accent.visibleIf = () -> cols.getAsBoolean() && colourMode.idx() == 1;
		bg = colour("Background Colour", 0x98111111);
		textColour = colour("Text Colour", 0xFFFFFFFF);
		artistColour = colour("Artist Colour", 0xA6FFFFFF);
		trackColour = colour("Progress Track Colour", 0x33FFFFFF);
		tintStrength = num("Cover tint on background", 10, 0, 60, 1, "%");
		glow = num("Glow", 20, 0, 100, 1, "%");
		for (Option<?> o : new Option<?>[] {bg, textColour, artistColour, trackColour, glow}) {
			o.visibleIf = cols;
		}
		tintStrength.visibleIf = () -> cols.getAsBoolean() && style.idx() != MINIMAL;
		action("Colours", "Reset colours", this::resetColours, () -> "").showIf(cols);

		// ------------------------------------------------------------- Animation
		showBars = bool("Animated Bars", true);
		showBars.visibleIf = anim;
		barsStyle = tabs("Bars", 0, "Corner", "Visualizer");
		barsStyle.visibleIf = () -> anim.getAsBoolean() && showBars.on();
		barCount = num("Visualizer bars", 16, 6, 32, 1, "");
		barCount.visibleIf = () -> anim.getAsBoolean() && showBars.on() && barsStyle.idx() == 1;
		songAnim = choice("Song change", 2, "None", "Fade", "Slide", "Pop");
		songAnim.visibleIf = anim;
		spin = bool("Spinning Vinyl", true);
		spinSpeed = num("Spin speed", 35, 5, 100, 1, "%");
		spin.visibleIf = () -> anim.getAsBoolean() && style.idx() == VINYL;
		spinSpeed.visibleIf = () -> anim.getAsBoolean() && style.idx() == VINYL && spin.on();
		pulse = bool("Pulse Glow", true);
		smooth = bool("Smooth Progress", true);
		pulse.visibleIf = anim;
		smooth.visibleIf = anim;
		scrollSpeed = num("Text scroll speed", 50, 10, 100, 1, "%");
		scrollSpeed.visibleIf = anim;
		cinematic = bool("Cinematic Mode", false);
		cinematic.visibleIf = anim;

		// --------------------------------------------------------------- Account
		header("Any player (automatic)").showIf(acc);
		action("Playing from", "Look again", MusicHub::refreshNow, MusicHub::statusLine).showIf(acc);
		extraSites = text("Extra sites", "", 120, "browser sites to watch, e.g. radio.example.com");
		extraSites.visibleIf = acc;
		header("Spotify account (optional)").showIf(acc);
		clientId = text("Client ID", "", 64, "from developer.spotify.com");
		clientId.visibleIf = acc;
		action("1. Create a free app", "Open Dashboard",
				() -> net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create("https://developer.spotify.com/dashboard")),
				() -> "Redirect URI: " + SpotifyAuth.REDIRECT).showIf(acc);
		action("2. Log in", "Connect Spotify", SpotifyClient::connect, () -> SpotifyClient.status).showIf(acc);
		action("Account", "Disconnect", SpotifyClient::disconnect, () -> SpotifyClient.connected ? "Connected" : "").showIf(acc);
		header("Playback").showIf(acc);
		action("Your playlists", "Next playlist", SpotifyClient::nextPlaylist, () -> SpotifyClient.playlistName).showIf(acc);
		kPlay = key("Play / Pause", -1);
		kNext = key("Next Song", -1);
		kPrev = key("Previous Song", -1);
		kPlay.visibleIf = acc;
		kNext.visibleIf = acc;
		kPrev.visibleIf = acc;

		// ---------------------------------------------------------------- Lyrics
		header("Lyrics").showIf(lyr);
		lyricsOn = bool("Show Lyrics", false);
		lyricsBar = bool("Lyrics Top Bar", true);
		lyricsWorld = bool("Lyrics In The Sky", true);
		lyricsOnlyPlaying = bool("Lyrics Only While Playing", true);
		lyricsScript = bool("Lyrics Handwriting Font", true);
		lyricsFont = choice("Lyrics Font", 0, dev.solclient.music.LyricFonts.names());
		lyricsFont.tip("Pick a font. \"My font\" uses a font you imported (button below, or the Custom Font mod).");
		action("Lyrics font file", "Import font", () -> dev.solclient.CustomFont.openFolder(), () -> dev.solclient.CustomFont.installed() ? "Font ready" : "Put a .ttf / .otf in the folder, then Use");
		action("Use the font in the folder", "Use", () -> dev.solclient.CustomFont.useFolder(), () -> "");
		lyricsSize = num("Lyrics Size", 100, 50, 200, 5, "%");
		lyricsPlace = tabs("Lyrics Where", 1, "Screen (flat)", "Real Sky (in the world)", "In front of me (3D)");
		lyricsFrontDist = num("Lyrics Distance Ahead", 8, 2, 40, 1, " blocks");
		lyricsFrontLift = num("Lyrics Height Ahead", 0, -3, 6, 0.5F, " blocks");
		lyricsWords = num("Lyrics Words Per Text", 0, 0, 8, 1, "").tip("How many words appear together. 0 = the whole line, 1 = one word at a time.");
		lyricsFrontFade = num("Lyrics Fade Distance", 24, 8, 60, 2, " blocks");
		lyricsFrontKeep = num("Lines Left Behind", 3, 0, 8, 1, "");
		lyricsFrontDist.tip("How far in front of you each line appears. You keep walking and it stays where it was.");
		lyricsFrontFade.tip("How far behind you a line is when it has completely faded away.");
		lyricsBehind = bool("Hidden Behind Mountains", false).tip("Off: always visible. On: hills and buildings can cover the sky words.");
		lyricsHeight = num("Lyrics Height", 18, 0, 45, 1, "\u00b0");
		lyricsPosition = tabs("Lyrics Position", 0, "Centre", "Left", "Right", "Left and right", "Random");
		lyricsSpread = num("Lyrics Side Distance", 24, 5, 40, 1, "%");
		lyricsDepth = num("Lyrics 3D Depth", 70, 0, 100, 5, "%");
		lyricsBacking = tabs("Lyrics Letter Backing", 2, "None", "Soft shadow", "3D same colour", "3D dark");
		lyricsBacking.tip("What is drawn behind the letters. None = only the letters, no dark shape behind them.");
		lyricsBackingAlpha = num("Lyrics Backing Opacity", 60, 0, 100, 5, "%");
		lyricsFrontFollow = bool("Line Follows Me While Sung", true)
				.tip("On: the line being sung stays in front of you as you walk, and is left behind when its last word has been sung.");
		lyricsSway = num("Lyrics Camera Sway", 60, 0, 100, 5, "%");
		lyricsBarSize = num("Lyrics Bar Size", 100, 60, 160, 5, "%");
		lyricsOpacity = num("Lyrics Opacity", 100, 10, 100, 5, "%");
		lyricsSync = num("Lyrics Sync", 0, -3000, 3000, 50, " ms");
		lyricsHighlight = colour("Lyrics Highlight Colour", 0xFF8B5CF6);
		lyricsText = colour("Lyrics Text Colour", 0xFFFFFFFF);
		lyricsColours = tabs("Lyrics Colours", 0, "One colour", "My colours", "Rainbow", "Gradient");
		lyricsPalette = num("Lyrics Colour Count", 4, 2, 4, 1, "");
		lyricsText2 = colour("Lyrics Colour 2", 0xFFFF6FB5);
		lyricsText3 = colour("Lyrics Colour 3", 0xFF7DD3FC);
		lyricsText4 = colour("Lyrics Colour 4", 0xFFFDE047);
		lyricsGradient = choice("Lyrics Gradient", 0, dev.solclient.music.Gradients.names());
		lyricsGradN = num("Lyrics Gradient Colours", 2, 2, 3, 1, "");
		lyricsGradA = colour("Lyrics Gradient Colour 1", 0xFF8B5CF6);
		lyricsGradB = colour("Lyrics Gradient Colour 2", 0xFFEC4899);
		lyricsGradC = colour("Lyrics Gradient Colour 3", 0xFF38BDF8);
		lyricsGradFlow = num("Lyrics Gradient Flow", 0, 0, 100, 5, "%").tip("0 = the colours stay where they are. More = they slide through the words.");
		lyricsUpper = bool("Lyrics Capital Letters", false);
		lyricsGlow = num("Lyrics Glow", 100, 0, 150, 5, "%");
		lyricsAnim = tabs("Lyrics Animation", 0, "Letters pop", "Fade", "Rise", "Zoom");
		for (Option<?> o : new Option<?>[] {lyricsPlace, lyricsFrontDist, lyricsFrontFade, lyricsFrontKeep, lyricsBehind, lyricsOn, lyricsBar, lyricsWorld, lyricsOnlyPlaying, lyricsScript, lyricsSize, lyricsHeight, lyricsSync, lyricsBarSize, lyricsOpacity, lyricsDepth, lyricsSway, lyricsPosition, lyricsSpread,
				lyricsHighlight, lyricsText, lyricsColours, lyricsPalette, lyricsText2, lyricsText3, lyricsText4}) {
			o.visibleIf = lyr;
		}
		lyricsBar.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on();
		lyricsWorld.visibleIf = lyricsBar.visibleIf;
		lyricsPlace.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsWorld.on();
		lyricsBehind.visibleIf = () -> lyricsPlace.visibleIf.getAsBoolean() && lyricsPlace.idx() >= 1;
		lyricsFrontDist.visibleIf = () -> lyricsPlace.visibleIf.getAsBoolean() && lyricsPlace.idx() == 2;
		lyricsFrontFade.visibleIf = lyricsFrontDist.visibleIf;
		lyricsFrontKeep.visibleIf = lyricsFrontDist.visibleIf;
		lyricsFrontFollow.visibleIf = lyricsFrontDist.visibleIf;
		lyricsFrontLift.visibleIf = lyricsFrontDist.visibleIf;
		lyricsWords.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsWorld.on();
		lyricsBacking.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsWorld.on();
		lyricsBackingAlpha.visibleIf = () -> lyricsBacking.visibleIf.getAsBoolean() && lyricsBacking.idx() != 0;
		lyricsOnlyPlaying.visibleIf = lyricsBar.visibleIf;
		lyricsScript.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsWorld.on();
		lyricsSize.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsWorld.on();
		lyricsHeight.visibleIf = lyricsSize.visibleIf;
		lyricsDepth.visibleIf = lyricsSize.visibleIf;
		lyricsPosition.visibleIf = lyricsSize.visibleIf;
		lyricsSpread.visibleIf = () -> lyricsSize.visibleIf.getAsBoolean() && lyricsPosition.idx() != 0;
		lyricsSway.visibleIf = () -> lyricsSize.visibleIf.getAsBoolean() && lyricsDepth.i() > 0;
		lyricsSync.visibleIf = lyricsBar.visibleIf;
		lyricsOpacity.visibleIf = lyricsBar.visibleIf;
		lyricsBarSize.visibleIf = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsBar.on();
		lyricsHighlight.visibleIf = lyricsBar.visibleIf;
		lyricsText.visibleIf = lyricsSize.visibleIf;
		lyricsColours.visibleIf = lyricsSize.visibleIf;
		lyricsPalette.visibleIf = () -> lyricsSize.visibleIf.getAsBoolean() && lyricsColours.idx() == 1;
		lyricsText2.visibleIf = lyricsPalette.visibleIf;
		lyricsText3.visibleIf = () -> lyricsPalette.visibleIf.getAsBoolean() && lyricsPalette.i() >= 3;
		lyricsText4.visibleIf = () -> lyricsPalette.visibleIf.getAsBoolean() && lyricsPalette.i() >= 4;
		lyricsScript.visibleIf = () -> false; // the font list below replaces the old "handwriting" switch
		BooleanSupplier gradOn = () -> lyr.getAsBoolean() && lyricsOn.on() && lyricsColours.idx() == 3;
		lyricsGradient.visibleIf = gradOn;
		lyricsGradFlow.visibleIf = gradOn;
		lyricsGradN.visibleIf = () -> gradOn.getAsBoolean() && dev.solclient.music.Gradients.isCustom(lyricsGradient.idx());
		lyricsGradA.visibleIf = lyricsGradN.visibleIf;
		lyricsGradB.visibleIf = lyricsGradN.visibleIf;
		lyricsGradC.visibleIf = () -> lyricsGradN.visibleIf.getAsBoolean() && lyricsGradN.i() >= 3;
		lyricsUpper.visibleIf = lyricsBar.visibleIf;
		lyricsGlow.visibleIf = lyricsSize.visibleIf;
		lyricsAnim.visibleIf = lyricsSize.visibleIf;
		action("Lyrics Status", "Search again", Lyrics::retry, () -> lyricsOn.on() ? Lyrics.status : "Turn on Show Lyrics").showIf(lyr);

		// ---------------------------------------------------------------- Intro card (who sings it)
		header("Now Playing Card").showIf(np);
		cardOn = bool("Show Now Playing Card", true);
		cardPlace = tabs("Card Where", 1, "Screen (flat)", "Real Sky (in the world)");
		action("Card Preview", "Preview now", dev.solclient.music.NowPlayingCard::preview, () -> "Shows the card again");
		cardSeconds = num("Card Duration", 6, 2, 20, 1, " s");
		cardRepeat = num("Card Repeat Every", 0, 0, 300, 15, " s").tip("0 = only when a new song starts.");
		cardOnlyPlaying = bool("Card Only While Playing", true);
		cardHideLyrics = bool("Hide Lyrics While Card Shows", false).tip("The lyrics wait until the card is gone.");
		cardLabelOn = bool("Card Label", true);
		cardLabel = text("Card Label Text", "NOW PLAYING", 24, "NOW PLAYING");
		cardArtist = bool("Card Artist", true);
		cardSource = bool("Card Player Name", true).tip("Shows \"via YouTube Music\", \"via Spotify\"... under the artist.");
		cardLines = bool("Card Lines", true);
		cardUpper = bool("Card Title Uppercase", false);
		cardFont = choice("Card Font", 0, dev.solclient.music.LyricFonts.names());
		cardSize = num("Card Size", 100, 40, 250, 5, "%");
		cardArtistSize = num("Card Artist Size", 100, 50, 200, 5, "%");
		cardLabelSize = num("Card Label Size", 100, 50, 200, 5, "%");
		cardHeight = num("Card Height", 14, -10, 50, 1, "\u00b0");
		cardPos = tabs("Card Position", 0, "Centre", "Left", "Right", "Random");
		cardSpread = num("Card Side Distance", 24, 5, 45, 1, "\u00b0");
		cardDepth = num("Card 3D Depth", 70, 0, 100, 5, "%");
		cardFollow = bool("Card Follows My View", false).tip("Off: it hangs in one place of the sky. On: it stays in front of you.");
		cardBehind = bool("Card Hidden Behind Mountains", false);
		cardAnim = tabs("Card Animation", 0, "Fade", "Typewriter", "Rise", "Zoom");
		cardOpacity = num("Card Opacity", 100, 10, 100, 5, "%");
		cardGlow = num("Card Glow", 50, 0, 100, 5, "%");
		cardColourMode = tabs("Card Title Colour Mode", 0, "Solid", "Shimmer", "Gradient");
		cardGradient = choice("Card Gradient", 0, dev.solclient.music.Gradients.names());
		cardGradFlow = num("Card Gradient Flow", 0, 0, 100, 5, "%");
		cardTitleCol = colour("Card Title Colour", 0xFFFFFFFF);
		cardArtistCol = colour("Card Artist Colour", 0xFFC4B5FD);
		cardLabelCol = colour("Card Label Colour", 0xFFFFFFFF);
		cardAccent = colour("Card Accent Colour", 0xFF8B5CF6);
		for (Option<?> o : new Option<?>[] {cardOn, cardPlace, cardSeconds, cardRepeat, cardOnlyPlaying, cardHideLyrics, cardLabelOn, cardLabel, cardArtist, cardSource, cardLines,
				cardUpper, cardFont, cardSize, cardArtistSize, cardLabelSize, cardHeight, cardPos, cardSpread, cardDepth, cardFollow, cardBehind, cardAnim, cardOpacity,
				cardGlow, cardColourMode, cardGradient, cardGradFlow, cardTitleCol, cardArtistCol, cardLabelCol, cardAccent}) {
			o.visibleIf = () -> np.getAsBoolean() && (o == cardOn || cardOn.on());
		}
		BooleanSupplier cardSky = () -> np.getAsBoolean() && cardOn.on() && cardPlace.idx() == 1;
		cardLabel.visibleIf = () -> np.getAsBoolean() && cardOn.on() && cardLabelOn.on();
		cardLabelSize.visibleIf = cardLabel.visibleIf;
		cardLabelCol.visibleIf = cardLabel.visibleIf;
		cardArtistSize.visibleIf = () -> np.getAsBoolean() && cardOn.on() && cardArtist.on();
		cardArtistCol.visibleIf = cardArtistSize.visibleIf;
		cardSpread.visibleIf = () -> np.getAsBoolean() && cardOn.on() && cardPos.idx() != 0;
		cardDepth.visibleIf = cardSky;
		cardFollow.visibleIf = cardSky;
		cardBehind.visibleIf = cardSky;
		cardGradient.visibleIf = () -> np.getAsBoolean() && cardOn.on() && cardColourMode.idx() == 2;
		cardGradient.tip("Custom = from the title colour to the accent colour.");
		cardGradFlow.visibleIf = cardGradient.visibleIf;
	}

	private void resetColours() {
		for (Option<?> o : new Option<?>[] {accent, bg, textColour, artistColour, trackColour, tintStrength, glow}) {
			o.reset();
		}
	}

	@Override
	protected void onEnable() {
		SpotifyClient.start(clientId.get());
		MusicHub.setExtraSites(extraSites.get());
		MusicHub.start();
	}

	@Override
	protected void onDisable() {
		SpotifyClient.stop();
		MusicHub.stop();
		dev.solclient.music.SkyLyrics.active = false;
		Lyrics.follow(false, "", "", 0);
	}

	@Override
	public void tick() {
		SpotifyClient.setClientId(clientId.get());
		MusicHub.setExtraSites(extraSites.get());
		// lyrics: look for the ones of the song that is showing (nothing is downloaded while the option is off)
		Lyrics.follow(lyricsOn.on() && MusicHub.hasTrack, MusicHub.title, MusicHub.artist, MusicHub.durationMs);
		if (dev.solclient.compat.Mc.screen(mc) != null) {
			return;
		}
		if (edge(0, kPlay)) {
			MusicHub.playPause();
		}
		if (edge(1, kNext)) {
			MusicHub.next();
		}
		if (edge(2, kPrev)) {
			MusicHub.previous();
		}
	}

	private boolean edge(int i, Option.Key k) {
		boolean d = k.code() > 0 && Keys.down(k.code());
		boolean e = d && !down[i];
		down[i] = d;
		return e;
	}

	private float cineT, cineIn;
	private long cineLast, cineTitleAt;
	private String cineKey = "";

	/**
	 * Cinematic mode: while a song plays the screen gets film bars that slide in, a soft vignette and, at every new song, a title card
	 * that opens like movie credits (spaced letters + a line that draws itself), with a thin progress line in the bottom bar.
	 */
	private void drawCinematic(GuiGraphicsExtractor c) {
		long now = System.currentTimeMillis();
		float dt = cineLast == 0 ? 0F : Math.min(0.1F, (now - cineLast) / 1000F);
		cineLast = now;
		boolean show = cinematic.on() && MusicHub.hasTrack && MusicHub.playing;
		cineT += ((show ? 1F : 0F) - cineT) * Math.min(1F, dt * 2.6F);
		if (cineT < 0.01F) {
			cineKey = "";
			return;
		}
		String key = MusicHub.title + "|" + MusicHub.artist;
		if (!key.equals(cineKey)) {
			cineKey = key;
			cineTitleAt = now;
		}
		float e = cineT * cineT * (3F - 2F * cineT);
		int w = c.guiWidth(), h = c.guiHeight();
		int bar = Math.round(h * 0.105F * e);
		// vignette: dark corners and edges
		int edge = Math.round(h * 0.28F);
		Gfx.gradientV(c, 0, 0, w, edge, Gfx.withAlpha(0x000000, Math.round(120 * e)), 0x00000000);
		Gfx.gradientV(c, 0, h - edge, w, edge, 0x00000000, Gfx.withAlpha(0x000000, Math.round(120 * e)));
		int side = Math.round(w * 0.16F);
		Gfx.gradientH(c, 0, 0, side, h, Gfx.withAlpha(0x000000, Math.round(90 * e)), 0x00000000);
		Gfx.gradientH(c, w - side, 0, side, h, 0x00000000, Gfx.withAlpha(0x000000, Math.round(90 * e)));
		// film bars
		Gfx.fill(c, 0, 0, w, bar, 0xFF000000);
		Gfx.fill(c, 0, h - bar, w, h, 0xFF000000);
		// progress hairline inside the bottom bar
		if (bar > 6 && MusicHub.durationMs > 0) {
			long pos = Math.max(0, Math.min(MusicHub.durationMs, MusicHub.progressMs + (MusicHub.playing ? now - MusicHub.fetchedAt : 0)));
			int x0 = Math.round(w * 0.12F), x1 = Math.round(w * 0.88F), y = h - bar / 2;
			Gfx.fill(c, x0, y, x1, y + 1, Gfx.withAlpha(0xFFFFFF, Math.round(40 * e)));
			Gfx.fill(c, x0, y, x0 + Math.round((x1 - x0) * (pos / (float) MusicHub.durationMs)), y + 1, Gfx.withAlpha(0xFFFFFF, Math.round(210 * e)));
		}
		// title card: 0..6 s after a new song
		float t = (now - cineTitleAt) / 1000F;
		if (t < 6.4F) {
			float in = Math.min(1F, t / 1.4F), out = t > 5.4F ? 1F - (t - 5.4F) / 1F : 1F;
			float a = Math.max(0F, Math.min(1F, in)) * Math.max(0F, out) * e;
			String title = MusicHub.title.toUpperCase(java.util.Locale.ROOT);
			StringBuilder sp = new StringBuilder();
			float spacing = 3.2F - 2.4F * in; // letters start far apart and close in
			for (int i = 0; i < title.length(); i++) {
				sp.append(title.charAt(i));
				if (i < title.length() - 1 && spacing > 0.5F) {
					sp.append(' ');
				}
			}
			String shown = spacing > 1.8F ? sp.toString() : title;
			float sc = 2F;
			int tw = Math.round(Gfx.tw(shown) * sc);
			int ty = h - bar - Math.round(h * 0.17F) + Math.round((1F - in) * 8F);
			int al = Math.round(255 * a);
			if (al > 4) {
				c.pose().pushMatrix();
				c.pose().translate((w - tw) / 2F, ty);
				c.pose().scale(sc, sc);
				Gfx.textRaw(c, shown, 0, 0, Gfx.withAlpha(0xFFFFFF, al));
				c.pose().popMatrix();
				int lw = Math.round(Math.min(1F, t / 2.2F) * Math.max(tw, 80) * 0.6F);
				Gfx.fill(c, w / 2 - lw / 2, ty + 22, w / 2 + lw / 2, ty + 23, Gfx.withAlpha(0xFFFFFF, Math.round(150 * a)));
				String art = MusicHub.artist;
				int aw = Gfx.tw(art);
				Gfx.textRaw(c, art, (w - aw) / 2, ty + 29, Gfx.withAlpha(0xFFFFFF, Math.round(190 * a)));
			}
		}
	}

	/** The widget itself, plus the lyrics (top capsule + words floating in the sky), which do not depend on the widget being visible. */
	@Override
	public void draw(GuiGraphicsExtractor c, boolean edit) {
		super.draw(c, edit);
		if (!edit && cinematic != null) {
			drawCinematic(c);
		}
		if (!edit && cardOn != null) {
			dev.solclient.music.NowPlayingCard.Cfg q = cardCfg;
			q.enabled = cardOn.on();
			q.sky = cardPlace.idx() == 1;
			q.onlyPlaying = cardOnlyPlaying.on();
			q.hideLyrics = cardHideLyrics.on();
			q.showLabel = cardLabelOn.on();
			q.label = cardLabel.get();
			q.showArtist = cardArtist.on();
			q.showSource = cardSource.on();
			q.lines = cardLines.on();
			q.upper = cardUpper.on();
			q.font = cardFont.idx();
			q.size = cardSize.f() / 100F;
			q.artistSize = cardArtistSize.f() / 100F;
			q.labelSize = cardLabelSize.f() / 100F;
			q.height = cardHeight.f();
			q.position = cardPos.idx();
			q.spread = cardSpread.f();
			q.depth = cardDepth.f() / 100F;
			q.follow = cardFollow.on();
			q.behind = cardBehind.on();
			q.anim = cardAnim.idx();
			q.colourMode = cardColourMode.idx();
			q.gradient = cardGradient.idx();
			q.gradFlow = cardGradFlow.f() / 100F;
			q.opacity = cardOpacity.f() / 100F;
			q.glow = cardGlow.f() / 100F;
			q.seconds = cardSeconds.f();
			q.repeatSec = cardRepeat.f();
			q.titleCol = cardTitleCol.c();
			q.artistCol = cardArtistCol.c();
			q.labelCol = cardLabelCol.c();
			q.accent = cardAccent.c();
			dev.solclient.music.NowPlayingCard.update(q);
			dev.solclient.music.NowPlayingCard.drawFlat(c);
		}
		if (edit || !lyricsOn.on()) {
			return;
		}
		LyricsView.Cfg k = lyricsCfg;
		k.bar = lyricsBar.on();
		boolean sky = lyricsWorld.on() && lyricsPlace.idx() >= 1;
		k.world = lyricsWorld.on() && !sky; // the flat screen sheet only when the sky is not used
		k.onlyPlaying = lyricsOnlyPlaying.on();
		dev.solclient.music.LyricFonts.apply(k, lyricsFont.idx());
		k.upper = lyricsUpper.on();
		k.glow = lyricsGlow.f() / 100F;
		k.lAnim = lyricsAnim.idx();
		k.gradFlow = lyricsGradFlow.f() / 100F;
		k.grad = dev.solclient.music.Gradients.stops(lyricsGradient.idx(), new int[] {lyricsGradA.c(), lyricsGradB.c(), lyricsGradC.c()}, lyricsGradN.i());
		k.size = lyricsSize.f() / 100F;
		k.front = lyricsPlace.idx() == 2;
		k.frontDist = lyricsFrontDist.f();
		k.frontFade = lyricsFrontFade.f();
		k.frontKeep = lyricsFrontKeep.i();
		k.height = lyricsHeight.f();
		k.depth = lyricsDepth.f() / 100F;
		k.backing = lyricsBacking.idx();
		k.backingAlpha = lyricsBackingAlpha.f() / 100F;
		k.frontFollow = lyricsFrontFollow.on();
		k.frontLift = lyricsFrontLift.f();
		k.wordsPerPiece = Math.round(lyricsWords.f());
		k.sway = lyricsSway.f() / 100F;
		k.position = lyricsPosition.idx();
		k.spread = lyricsSpread.f() / 100F;
		k.barSize = lyricsBarSize.f() / 100F;
		k.opacity = lyricsOpacity.f() / 100F;
		k.offsetMs = lyricsSync.f();
		k.highlight = lyricsHighlight.c();
		k.text = lyricsText.c();
		k.colourMode = lyricsColours.idx();
		k.paletteSize = lyricsPalette.i();
		k.palette[0] = lyricsText.c();
		k.palette[1] = lyricsText2.c();
		k.palette[2] = lyricsText3.c();
		k.palette[3] = lyricsText4.c();
		dev.solclient.music.SkyLyrics.cfg = k;
		dev.solclient.music.SkyLyrics.behindTerrain = lyricsBehind.on();
		dev.solclient.music.SkyLyrics.active = sky;
		dev.solclient.music.SkyLyrics.lastSet = System.currentTimeMillis();
		LyricsView.draw(c, k);
	}

	// ------------------------------------------------------------------ size
	@Override
	public int width() {
		return widthOpt.i();
	}

	@Override
	public int height() {
		return switch (style.idx()) {
			case PILL -> 22;
			case MINIMAL -> {
				int text = 10 + (showArtist.on() ? 10 : 0) + (showProgress.on() ? 6 : 0);
				yield Math.max(showArt.on() ? Math.min(coverSize.i(), 40) + 4 : 0, text + 4);
			}
			case PLAYER -> {
				int inner = width() - 14;
				int h = 7 + (showArt.on() ? Math.round(inner * 0.64F) + 5 : 0) + 10 + (showArtist.on() ? 10 : 0);
				h += showProgress.on() ? 6 + (showTimes.on() ? 8 : 0) : 0;
				yield h + 3 + 20 + 7;
			}
			default -> {
				int text = 10 + (showArtist.on() ? 10 : 0) + (showProgress.on() ? (showTimes.on() ? 14 : 7) : 0);
				int cov = showArt.on() ? coverSize.i() : 0;
				yield Math.max(24, Math.max(cov, text) + 14);
			}
		};
	}

	@Override
	public boolean visible() {
		return MusicHub.hasTrack && !(hidePaused.on() && !MusicHub.playing);
	}

	// ------------------------------------------------------------------ helpers
	private int al(int color, float a) {
		int ca = (color >>> 24) & 255;
		return Math.round(ca * Math.max(0F, Math.min(1F, a * ga))) << 24 | (color & 0xFFFFFF);
	}

	private void text(GuiGraphicsExtractor c, String s, int x, int y, int color, boolean shadow) {
		if (((color >>> 24) & 255) < 8) {
			return;
		}
		c.text(mc.font, s, x, y, color, shadow);
	}

	/** Text that scrolls (with a pause at the start) when it does not fit in maxW. */
	private void marquee(GuiGraphicsExtractor c, String s, int x, int y, int maxW, int color, boolean shadow) {
		if (maxW <= 4) {
			return;
		}
		int tw = mc.font.width(s);
		if (tw <= maxW) {
			text(c, s, x, y, color, shadow);
			return;
		}
		int gap = 28, total = tw + gap;
		long msPerPx = Math.max(6, Math.round(60 - scrollSpeed.f() * 0.5F));
		long cycle = total * msPerPx + 2000;
		long ph = System.currentTimeMillis() % cycle;
		int off = ph < 2000 ? 0 : (int) ((ph - 2000) / msPerPx);
		c.enableScissor(x, y - 2, x + maxW, y + 11);
		text(c, s, x - off, y, color, shadow);
		text(c, s, x - off + total, y, color, shadow);
		c.disableScissor();
	}

	private static float ease(float k) {
		k = Math.max(0F, Math.min(1F, k));
		return 1F - (1F - k) * (1F - k) * (1F - k);
	}

	/** Cover texture of the current song in the given shape (0 square, 1 round, 2 rounded corners), or null while loading. */
	private RemoteTextures.Tex cover(int shape) {
		String url = MusicHub.artUrl, title = MusicHub.title, artist = MusicHub.artist;
		if (!MusicHub.hasTrack || url.isEmpty() && title.isEmpty()) {
			return null;
		}
		// the player's picture first, then a cover found online (CoverFinder); a failure is tried again later, never kept for good
		return RemoteTextures.local("spotify:" + shape + ":" + MusicHub.artKey, () -> CoverFinder.png(url, title, artist, shape));
	}

	private int accentColour() {
		return colourMode.idx() == 0 ? SpotifyClient.colourFor(MusicHub.artUrl, accent.c()) : accent.c();
	}

	// ------------------------------------------------------------------ drawing
	/** Dark see-through glass like the reference player (#111111 at 60%). */
	private static final int GLASS = 0x98111111;

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		long nowMs = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (nowMs - lastFrame) / 1000F);
		lastFrame = nowMs;
		// appear animation when it shows up again after being hidden (fade + small rise)
		if (nowMs - lastDrawn > 400) {
			appearAt = nowMs;
		}
		lastDrawn = nowMs;
		ga = edit ? 1F : ease((nowMs - appearAt) / 300F);

		boolean has = MusicHub.hasTrack;
		boolean on = !has || MusicHub.playing;
		playT += ((on ? 1F : 0F) - playT) * Math.min(1F, dt * 6F);
		String key = has ? MusicHub.artKey : "";
		if (!key.equals(lastKey)) {
			if (!lastKey.isEmpty()) {
				changedAt = nowMs;
			}
			lastKey = key;
			shownProgress = -1F;
		}
		float ch = songAnim.idx() == 0 ? 1F : ease((nowMs - changedAt) / 420F);

		String title = has ? MusicHub.title : "Song title";
		String artist = has ? MusicHub.artist : "Artist name";
		long dur = has ? MusicHub.durationMs : 180000, pos = has ? MusicHub.progressMs : 47000;
		if (has && MusicHub.playing) {
			pos += nowMs - MusicHub.fetchedAt;
		}
		pos = Math.max(0, Math.min(dur, pos));
		float prog = dur <= 0 ? 0F : pos / (float) dur;
		if (smooth.on() && shownProgress >= 0F && Math.abs(prog - shownProgress) < 0.2F) {
			shownProgress += (prog - shownProgress) * Math.min(1F, dt * 8F);
		} else {
			shownProgress = prog;
		}
		if (spin.on() && style.idx() == VINYL) {
			spinAngle += dt * playT * (0.6F + spinSpeed.f() / 100F * 4F);
		}
		double t = nowMs / 1000.0;
		for (int i = 0; i < barH.length; i++) {
			float target = (float) (0.25 + 0.75 * Math.abs(Math.sin(t * (2.1 + (i % 5) * 0.37) + i * 1.7) * Math.sin(t * (1.3 + (i % 3) * 0.5) + i * 0.9)));
			target *= playT;
			barH[i] += (Math.max(0.08F, target) - barH[i]) * Math.min(1F, dt * 12F);
		}

		Frame f = new Frame();
		f.w = width();
		f.h = height();
		f.ac = accentColour();
		f.fill = colourMode.idx() == 1 ? accent.c() : 0xFFFFFFFF; // the bar: white like the reference, or your colour
		f.textA = songAnim.idx() == 1 || songAnim.idx() == 2 ? ch : 1F;
		f.slide = songAnim.idx() == 2 ? (1F - ch) * 10F : 0F;
		f.coverScale = songAnim.idx() == 3 ? 0.8F + 0.2F * ch + (float) Math.sin(Math.min(1F, ch) * Math.PI) * 0.08F : 1F;
		f.title = title;
		f.artist = artist;
		f.pos = pos;
		f.dur = dur;
		f.on = on;
		f.t = t;

		var m = c.pose();
		m.pushMatrix();
		if (!edit && ga < 1F) {
			m.translate(0F, (1F - ga) * 6F);
		}
		switch (style.idx()) {
			case PILL -> drawPill(c, f);
			case MINIMAL -> drawMinimal(c, f);
			case PLAYER -> drawPlayer(c, f);
			default -> drawCard(c, f, style.idx() == VINYL);
		}
		m.popMatrix();
	}

	/** Everything one frame of the widget needs. */
	private static final class Frame {
		int w, h, ac, fill;
		float textA, slide, coverScale;
		String title, artist;
		long pos, dur;
		boolean on;
		double t;
	}

	/** Soft coloured glow + the glass background. */
	private void background(GuiGraphicsExtractor c, Frame f, float radius) {
		float g = glow.f() / 100F * (0.55F + 0.45F * playT);
		if (pulse.on() && f.on) {
			g *= 0.8F + 0.2F * (float) (0.5 + 0.5 * Math.sin(f.t * 3.0));
		}
		if (g > 0.01F) {
			for (int i = 4; i >= 1; i--) {
				float e = i * 1.4F;
				Gfx.rrectFree(c, -e, -e + 1F, f.w + e * 2, f.h + e * 2, radius + e, al(f.ac, g * 0.10F / i));
			}
		}
		int base = bg.c();
		int fill = Gfx.blend(base, f.ac | (base & 0xFF000000), tintStrength.f() / 100F);
		Gfx.rrectFree(c, 0, 0, f.w, f.h, radius, al(fill, 1F));
		// hairline on the top edge, like glass
		Gfx.rrectFree(c, radius * 0.6F, 0, Math.max(0F, f.w - radius * 1.2F), 0.6F, 0.3F, al(0x22FFFFFF, 1F));
	}

	/** Thin progress bar (track + fill), no knob - like the reference player. */
	private void bar(GuiGraphicsExtractor c, float x, float y, float w, float hgt, int fillCol) {
		Gfx.rrectFree(c, x, y, w, hgt, hgt / 2F, al(trackColour.c(), 1F));
		float filled = w * Math.max(0F, Math.min(1F, shownProgress));
		if (filled > 0.4F) {
			Gfx.rrectFree(c, x, y, filled, hgt, hgt / 2F, al(fillCol, 0.75F + 0.25F * playT));
		}
	}

	private void times(GuiGraphicsExtractor c, Frame f, int x, int y, int right) {
		String l = SpotifyAuth.time(f.pos), r = SpotifyAuth.time(f.dur);
		int tc = al(artistColour.c(), 0.95F);
		var m = c.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(0.75F, 0.75F);
		text(c, l, 0, 0, tc, false);
		m.popMatrix();
		m.pushMatrix();
		m.translate(right - mc.font.width(r) * 0.75F, y);
		m.scale(0.75F, 0.75F);
		text(c, r, 0, 0, tc, false);
		m.popMatrix();
	}

	private void cornerBars(GuiGraphicsExtractor c, Frame f, float right, float baseY) {
		for (int i = 0; i < 4; i++) {
			float bh = 2F + barH[i] * 6F;
			Gfx.rrectFree(c, right - 11 + i * 3F, baseY - bh, 1.8F, bh, 0.9F, al(f.fill, 0.5F + 0.5F * playT));
		}
	}

	private void pausedBadge(GuiGraphicsExtractor c, float cx, float cy, float r) {
		if (playT >= 0.98F) {
			return;
		}
		float a = 1F - playT;
		Gfx.circle(c, cx, cy, r, al(0xA0000000, a));
		Gfx.rrectFree(c, cx - r * 0.38F, cy - r * 0.45F, r * 0.26F, r * 0.9F, 0.5F, al(0xFFFFFFFF, a));
		Gfx.rrectFree(c, cx + r * 0.12F, cy - r * 0.45F, r * 0.26F, r * 0.9F, 0.5F, al(0xFFFFFFFF, a));
	}

	// ---- Card (and Vinyl): cover on the left, title / artist / thin bar on the right
	private void drawCard(GuiGraphicsExtractor c, Frame f, boolean vinyl) {
		float radius = roundness.f();
		background(c, f, radius);
		int pad = 7;
		int tx = pad + 2;
		if (showArt.on()) {
			int d = Math.min(coverSize.i(), f.h - pad * 2);
			int cy = (f.h - d) / 2;
			if (vinyl) {
				drawVinyl(c, pad, cy, d, f.coverScale, f.ac);
			} else {
				drawCover(c, 2, pad, cy, d, f.coverScale, 0F, f.ac);
				pausedBadge(c, pad + d / 2F, f.h / 2F, Math.max(5F, d * 0.2F));
			}
			tx = pad + d + 8;
		}
		int right = f.w - 8;
		boolean bars = showBars.on() && barsStyle.idx() == 0;
		int textRight = bars ? right - 14 : right;
		if (showBars.on() && barsStyle.idx() == 1) {
			visualizer(c, f, radius);
		}
		int lines = 1 + (showArtist.on() ? 1 : 0);
		int block = lines * 10 + (showProgress.on() ? (showTimes.on() ? 14 : 7) : 0);
		int ty = Math.max(5, (f.h - block) / 2 + 1);
		var m = c.pose();
		m.pushMatrix();
		m.translate(f.slide, 0F);
		int textW = Math.max(8, textRight - tx - Math.round(f.slide));
		marquee(c, f.title, tx, ty, textW, al(textColour.c(), f.textA), textShadow.on());
		if (showArtist.on()) {
			marquee(c, f.artist, tx, ty + 10, textW, al(artistColour.c(), f.textA), false);
		}
		m.popMatrix();
		if (bars) {
			cornerBars(c, f, right, ty + 7);
		}
		if (showProgress.on()) {
			float by = ty + lines * 10 + 2;
			bar(c, tx, by, right - tx, 2F, f.fill);
			if (showTimes.on()) {
				times(c, f, tx, Math.round(by) + 4, right);
			}
		}
	}

	// ---- Pill: a small capsule, round cover, one line, hairline progress
	private void drawPill(GuiGraphicsExtractor c, Frame f) {
		float radius = f.h / 2F;
		background(c, f, radius);
		int pad = 3;
		int tx = Math.round(radius * 0.7F);
		if (showArt.on()) {
			int d = f.h - pad * 2;
			drawCover(c, 1, pad, pad, d, f.coverScale, 0F, f.ac);
			pausedBadge(c, pad + d / 2F, f.h / 2F, d * 0.32F);
			tx = pad + d + 6;
		}
		int right = f.w - Math.round(radius * 0.7F);
		boolean bars = showBars.on();
		int textRight = bars ? right - 13 : right;
		String line = showArtist.on() ? f.title + "  ·  " + f.artist : f.title;
		var m = c.pose();
		m.pushMatrix();
		m.translate(f.slide, 0F);
		marquee(c, line, tx, f.h / 2 - 4 - (showProgress.on() ? 1 : 0), Math.max(8, textRight - tx), al(textColour.c(), f.textA), textShadow.on());
		m.popMatrix();
		if (bars) {
			cornerBars(c, f, right + 1, f.h / 2F + 4);
		}
		if (showProgress.on()) {
			bar(c, tx, f.h - 6.5F, textRight - tx, 1.5F, f.fill);
		}
	}

	// ---- Minimal: no box, small cover, text with shadow, thin bar
	private void drawMinimal(GuiGraphicsExtractor c, Frame f) {
		int tx = 0;
		if (showArt.on()) {
			int d = Math.min(coverSize.i(), f.h - 4);
			drawCover(c, roundness.f() > 0 ? 2 : 0, 0, (f.h - d) / 2, d, f.coverScale, 0F, f.ac);
			tx = d + 7;
		}
		int right = f.w - 2;
		boolean bars = showBars.on();
		int textRight = bars ? right - 13 : right;
		int lines = 1 + (showArtist.on() ? 1 : 0);
		int block = lines * 10 + (showProgress.on() ? 6 : 0);
		int ty = Math.max(1, (f.h - block) / 2);
		var m = c.pose();
		m.pushMatrix();
		m.translate(f.slide, 0F);
		marquee(c, f.title, tx, ty, Math.max(8, textRight - tx), al(textColour.c(), f.textA), true);
		if (showArtist.on()) {
			marquee(c, f.artist, tx, ty + 10, Math.max(8, textRight - tx), al(artistColour.c(), f.textA), true);
		}
		m.popMatrix();
		if (bars) {
			cornerBars(c, f, right, ty + 7);
		}
		if (showProgress.on()) {
			bar(c, tx, ty + lines * 10 + 2, right - tx, 1.5F, f.fill);
		}
	}

	// ---- Player: like the reference music player - big cover, centred title, thin bar, times, a row of controls
	private void drawPlayer(GuiGraphicsExtractor c, Frame f) {
		float radius = Math.max(8F, roundness.f() + 6F);
		background(c, f, radius);
		int pad = 7, inner = f.w - pad * 2;
		int y = pad;
		if (showArt.on()) {
			int ch = Math.round(inner * 0.64F);
			RemoteTextures.Tex tex = cover(2);
			float cr = Math.max(4F, radius - 4F);
			Gfx.rrectFree(c, pad, y, inner, ch, cr, al(0x33FFFFFF, 1F));
			if (tex != null) {
				// "object-cover": the middle band of the square cover
				int band = Math.round(128F * ch / inner);
				Gfx.texUv(c, tex.id(), pad, y, inner, ch, 0, (128 - band) / 2F, 128, band, 128, 128, al(Gfx.blend(0xFF9A9A9A, 0xFFFFFFFF, playT), 1F));
			} else {
				Gfx.tex(c, Gfx.id("textures/gui/icon/spotify.png"), pad + inner / 2 - 10, y + ch / 2 - 10, 20, 20, 64, 64, al(0x88FFFFFF, 1F));
			}
			y += ch + 5;
		}
		var m = c.pose();
		m.pushMatrix();
		m.translate(f.slide, 0F);
		centred(c, f.title, pad, y, inner, al(textColour.c(), f.textA), textShadow.on());
		y += 10;
		if (showArtist.on()) {
			centred(c, f.artist, pad, y, inner, al(artistColour.c(), f.textA), false);
			y += 10;
		}
		m.popMatrix();
		if (showProgress.on()) {
			y += 2;
			bar(c, pad, y, inner, 2F, f.fill);
			y += 4;
			if (showTimes.on()) {
				times(c, f, pad, y, f.w - pad);
				y += 8;
			}
		}
		// controls: shuffle, previous, play / pause, next, repeat in a darker rounded box
		y += 3;
		int ic = 10, gap = 7, n = 5;
		int bw = n * ic + (n - 1) * gap + 12, bh = ic + 10;
		int bx = (f.w - bw) / 2;
		Gfx.rrectFree(c, bx, y, bw, bh, 7F, al(GLASS, 1F));
		String[] icons = {"shuffle", "prev", f.on ? "pause" : "play", "next", "repeat"};
		for (int i = 0; i < n; i++) {
			int ix = bx + 6 + i * (ic + gap);
			int col = i == 2 ? 0xFFFFFFFF : (i == 0 || i == 4 ? 0x99FFFFFF : 0xE6FFFFFF);
			if (i == 2) {
				Gfx.circle(c, ix + ic / 2F, y + bh / 2F, ic * 0.85F, al(0x26FFFFFF, 1F));
			}
			Gfx.tex(c, Gfx.id("textures/gui/spotify/" + icons[i] + ".png"), ix, y + 5, ic, ic, 64, 64, al(col, 1F));
		}
	}

	private void centred(GuiGraphicsExtractor c, String s, int x, int y, int w, int col, boolean shadow) {
		int tw = mc.font.width(s);
		if (tw <= w) {
			text(c, s, x + (w - tw) / 2, y, col, shadow);
		} else {
			marquee(c, s, x, y, w, col, shadow);
		}
	}

	private void visualizer(GuiGraphicsExtractor c, Frame f, float radius) {
		int n = barCount.i();
		float inset = Math.max(4F, radius * 0.6F);
		float bw = (f.w - inset * 2) / n;
		float maxH = f.h * 0.42F;
		for (int i = 0; i < n; i++) {
			float bh = Math.max(1F, barH[i % barH.length] * maxH);
			float bx = inset + i * bw;
			Gfx.rrectFree(c, bx + bw * 0.2F, f.h - 2 - bh, bw * 0.6F, bh, Math.min(1.5F, bw * 0.3F), al(f.fill, 0.16F));
		}
	}

	private void drawCover(GuiGraphicsExtractor c, int shape, int x, int y, int d, float scale, float angle, int ac) {
		RemoteTextures.Tex tex = cover(shape);
		var m = c.pose();
		m.pushMatrix();
		m.translate(x + d / 2F, y + d / 2F);
		if (scale != 1F) {
			m.scale(scale, scale);
		}
		if (angle != 0F) {
			m.rotate(angle);
		}
		int half = d / 2;
		float r = shape == 1 ? d / 2F : shape == 2 ? Math.max(2F, d * 0.18F) : 0F;
		if (tex != null) {
			Gfx.tex(c, tex.id(), -half, -half, d, d, 128, 128, al(Gfx.blend(0xFF9A9A9A, 0xFFFFFFFF, playT), 1F));
		} else {
			Gfx.rrectFree(c, -half, -half, d, d, r, al(0x33FFFFFF, 1F));
			int is = Math.max(6, d / 2);
			Gfx.tex(c, Gfx.id("textures/gui/icon/spotify.png"), -is / 2, -is / 2, is, is, 64, 64, al(0x99FFFFFF, 1F));
			if (!CoverFinder.error.isEmpty()) {
				// the cover could not be found: a small red "!" (the reason is in Music settings > Account and in the log)
				int bs = Math.max(8, d / 3);
				Gfx.circle(c, half - bs / 2F, -half + bs / 2F, bs / 2F, al(0xFFE5484D, 1F));
				Gfx.textC(c, "!", half - bs / 2, -half + bs / 2 - 4, al(0xFFFFFFFF, 1F));
			}
		}
		m.popMatrix();
	}

	private void drawVinyl(GuiGraphicsExtractor c, int x, int y, int d, float scale, int ac) {
		var m = c.pose();
		float cx = x + d / 2F, cy = y + d / 2F, r = d / 2F;
		m.pushMatrix();
		m.translate(cx, cy);
		if (scale != 1F) {
			m.scale(scale, scale);
		}
		Gfx.circle(c, 0, 0, r, al(0xFF0B0B0B, 1F));
		for (int i = 1; i <= 3; i++) {
			float gr = r * (0.94F - i * 0.09F);
			Gfx.circle(c, 0, 0, gr, al(0xFF1A1A1A, 1F));
			Gfx.circle(c, 0, 0, gr - 0.5F, al(0xFF0E0E0E, 1F));
		}
		Gfx.circle(c, -r * 0.38F, -r * 0.38F, r * 0.16F, al(0x12FFFFFF, 1F));
		m.popMatrix();
		int label = Math.round(d * 0.56F);
		drawCover(c, 1, Math.round(cx - label / 2F), Math.round(cy - label / 2F), label, scale, spinAngle, ac);
		Gfx.circle(c, cx, cy, Math.max(1.2F, d * 0.04F), al(0xFF0A0A0A, 1F));
	}

	// ------------------------------------------------------------------ settings preview
	/** Live preview at the top of the settings (uses your current song, or a placeholder). Returns its height. */
	public int preview(GuiGraphicsExtractor c, int x, int y, int w) {
		int h = Math.max(70, Math.min(200, height() + 30));
		Gfx.text(c, "PREVIEW", x + 4, y + 2, 0xFF8A8F9C);
		int py = y + 14;
		// a real clear Minecraft sky behind, so the see-through glass can be judged
		dev.solclient.gui.Backdrops.draw(c, dev.solclient.gui.Backdrops.CLEAR, x, py, w, h);
		Gfx.outline(c, x, py, w, h, 0x40FFFFFF);
		float k = Math.min(1.6F, Math.min((w - 24) / (float) width(), (h - 20) / (float) height()));
		var m = c.pose();
		m.pushMatrix();
		m.translate(x + (w - width() * k) / 2F, py + (h - height() * k) / 2F);
		m.scale(k, k);
		renderHud(c, true);
		m.popMatrix();
		return 14 + h;
	}
}
