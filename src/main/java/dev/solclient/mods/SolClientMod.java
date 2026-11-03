package dev.solclient.mods;

import dev.solclient.Config;
import dev.solclient.gui.Theme;
import dev.solclient.gui.ThemeCode;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;

/** The "Sleep" settings card. */
public class SolClientMod extends Mod {

	public final Option.Col uiColour;
	public final Option.Num menuSize;
	public final Option.Num menuOpacity;
	public final Option.Key modsKey;
	public final Option.Key editHudKey;
	public final Option.Key emoteKey;

	public final Option.Bool customTitle;
	public final Option.Bool customPause;
	/** 0 = Sleep's own simple pause screen, 1 = Minecraft's pause screen (Essential / Mod Menu buttons stay) with Sleep's buttons + your player. */
	public final Option.Choice pauseStyle;
	public final Option.Bool pausePlayer;
	/** 0 = Sleep's "Mods" tab, 1 = the original Mod Menu screen. */
	public final Option.Choice modsScreen;
	public final Option.Choice seasonal;
	public final Option.Choice buttonStyle;
	public final Option.Bool buttonAnimations;
	public final Option.Num screenSize;
	public final Option.Num cornerRadius;
	public final Option.Bool cosmetics;
	public final Option.Bool othersEmotes;
	public final Option.Bool shareEmotes;
	public final Option.Bool solarCapes;
	public Option.Choice language;
	public Option.Bool mcLanguage;
	public Option.Choice font;
	public Option.Bool shareServer;
	public Option.Key keybindsKey;
	private int appliedLanguage = -1;
	public final Option.Bool animatedCapes;
	public final Option.Bool nameBadge;
	public final Option.Bool seasonalMusic;
	public final Option.Bool seasonalBackground;
	public final Option.Bool restyleMenus;
	public final Option.Bool restyleButtons;
	public final Option.Bool homeShowcase;
	public final Option.Bool hudInF3;
	public final Option.Choice toastPosition;
	public final Option.Bool notifySound;
	public final Option.Num notifyVolume;
	/** How big the notification cards are (percent of the original size). */
	public final Option.Num notifySize;
	// the sound of each kind of notification ("" = the Sleep pling, "silent" = none, or a picked sound)
	private Option.Text soundMessage, soundFriend, soundInvite, soundClip, soundError, soundOther;
	public final Option.Bool splashLogo;
	public final Option.Bool splashOnReload;
	public final Option.Bool hideReloadScreen;
	public final Option.Bool hideHudWhileLoading;
	/** The first-start setup was finished (or skipped). */
	public final Option.Bool onboarded;

	// ---- theme (see gui.Theme): every colour of the menus can be changed
	public final Option.Choice themePreset;
	public final Option.Col bgColour, cardColour, borderColour, fieldColour, textColour, dimColour, buttonColour;
	public final Option.Col onColour, onGlowColour, offColour, dangerColour;
	public final Option.Num bgTransparency, cardTransparency;
	/** Ready-made looks (the same list as the first-time setup); picking one sets the colours below. */
	public final Option.Choice uiTheme;
	/** See-through menus: on top of the background / card transparency. */
	public final Option.Num uiTransparency;
	/** The setup's performance / HUD choices, changeable later. */
	public final Option.Choice perfPreset, hudPreset;
	private final List<Option<?>> themeOptions = new ArrayList<>();
	private long askTheme, askAll, codeMsgAt;
	private String codeMsg = "";

	public SolClientMod() {
		super("Sleep", "sol_client", "Settings for Sleep.", Category.NONE);
		locked = true;
		enabled = true;

		// The settings are shown as sections that open and close (see SolMenuScreen.SECTION_INFO).
		// Order: what people look for first (language, font, theme) ... what they rarely need (setup, reset, testing).

		// ------------------------------------------------------------------ Language
		header("Language");
		language = choice("Language", 0, dev.solclient.Lang.NAMES);
		language.tip("The language of Sleep's menus.");
		mcLanguage = bool("Minecraft in this language too", true);
		mcLanguage.showIf(() -> language.idx() != 0);
		action("Automatic translation", "Translate Again", () -> {
			dev.solclient.AutoTranslate.reset(dev.solclient.Lang.code()); // texts are asked again as they show up
		}, () -> {
			int i = language.idx();
			if (i <= 1) {
				return "";
			}
			String st = dev.solclient.AutoTranslate.status;
			int w = dev.solclient.AutoTranslate.waiting();
			return !st.isEmpty() ? st : w > 0 ? w + " texts waiting" : dev.solclient.AutoTranslate.count(dev.solclient.Lang.code()) + " texts translated";
		}).showIf(() -> language.idx() > 1);

		// ------------------------------------------------------------------ Font
		header("Font");
		font = choice("Font", 0, dev.solclient.Gfx.FONT_NAMES);
		font.tip("The font of all Sleep menus and HUD text.");
		action("Your own font (.ttf)", "Open Folder", dev.solclient.CustomFont::openFolder, dev.solclient.CustomFont::statusText)
				.tip("Drag a .ttf file onto this window, or put it in the folder and click Apply.");
		action("Use the font in the folder", "Apply", dev.solclient.CustomFont::useFolder, () -> "")
				.tip("Uses the newest .ttf / .otf file in the kubo-fonts folder.");
		action("Go back to the normal fonts", "Remove", dev.solclient.CustomFont::remove, () -> "")
				.showIf(dev.solclient.CustomFont::installed);

		// ------------------------------------------------------------------ Theme
		header("Theme");
		uiTheme = choice("Theme", 0, dev.solclient.gui.Presets.THEMES).onPick(dev.solclient.gui.Presets::theme);
		uiTheme.tip("A ready-made look. You can still change every colour in Custom Colours.");
		uiColour = colour("Accent Colour", 0xFF7FB3F5);
		uiColour.tip("The main colour: selected items, sliders, highlights.");
		uiTransparency = num("UI Transparency", 0, 0, 80, 5, "%");
		uiTransparency.tip("Makes every menu see-through.");
		menuOpacity = num("Menu Opacity", 100, 25, 100, 5, "%");
		menuOpacity.tip("How solid the Sleep window is (lower = see the game behind it).");

		// ------------------------------------------------------------------ Custom colours
		header("Custom Colours");
		// no more ready-made templates: every colour has its own picker below. Kept (hidden) so old saves still load.
		themePreset = choice("Theme Preset", 0, Theme.PRESET_NAMES);
		themePreset.showIf(() -> false);
		bgColour = colour("Background Colour", Theme.presetColour(0, 1));
		bgColour.tip("Behind everything in the menu.");
		bgTransparency = num("Background Transparency", 0, 0, 100, 5, "%");
		cardColour = colour("Card Colour", Theme.presetColour(0, 2));
		cardColour.tip("The boxes that hold each mod.");
		cardTransparency = num("Card Transparency", 0, 0, 100, 5, "%");
		borderColour = colour("Border Colour", Theme.presetColour(0, 3));
		fieldColour = colour("Text Field Colour", Theme.presetColour(0, 4));
		fieldColour.tip("Search box and other places where you type.");
		textColour = colour("Text Colour", Theme.presetColour(0, 5));
		dimColour = colour("Secondary Text Colour", Theme.presetColour(0, 6));
		dimColour.tip("Descriptions and small grey text.");
		buttonColour = colour("Button Colour", Theme.presetColour(0, 11));
		onColour = colour("Toggle On Colour", Theme.presetColour(0, 7));
		onGlowColour = colour("Toggle On Highlight", Theme.presetColour(0, 8));
		offColour = colour("Toggle Off Colour", Theme.presetColour(0, 9));
		dangerColour = colour("Danger Colour", Theme.presetColour(0, 10));
		dangerColour.tip("Delete / reset buttons and warnings.");
		themeOptions.add(uiTheme);
		themeOptions.add(uiTransparency);
		themeOptions.add(uiColour);
		themeOptions.add(menuOpacity);
		themeOptions.add(themePreset);
		themeOptions.add(bgColour);
		themeOptions.add(bgTransparency);
		themeOptions.add(cardColour);
		themeOptions.add(cardTransparency);
		themeOptions.add(borderColour);
		themeOptions.add(fieldColour);
		themeOptions.add(textColour);
		themeOptions.add(dimColour);
		themeOptions.add(buttonColour);
		themeOptions.add(onColour);
		themeOptions.add(onGlowColour);
		themeOptions.add(offColour);
		themeOptions.add(dangerColour);
		action("Share your theme (code)", "Copy Code", () -> {
			var mcc = net.minecraft.client.MinecraftClient.getInstance();
			mcc.keyboard.setClipboard(ThemeCode.export(this));
			codeMsg = "Copied! Send it to a friend";
			codeMsgAt = System.currentTimeMillis();
		}, () -> System.currentTimeMillis() - codeMsgAt < 4000L ? codeMsg : "");
		action("Import a theme code", "Paste Code", () -> {
			var mcc = net.minecraft.client.MinecraftClient.getInstance();
			String err = ThemeCode.apply(this, mcc.keyboard.getClipboard());
			codeMsg = err == null ? "Theme imported!" : err;
			codeMsgAt = System.currentTimeMillis();
		}, () -> System.currentTimeMillis() - codeMsgAt < 4000L ? codeMsg : "");
		action("Back to the original look", "Reset Theme", () -> {
			if (asking(askTheme)) {
				askTheme = 0;
				resetTheme();
			} else {
				askTheme = System.currentTimeMillis();
			}
		}, () -> asking(askTheme) ? "Click again to confirm" : "");

		// ------------------------------------------------------------------ Menus
		header("Menus");
		menuSize = num("Menu Size", 1F, 0.5F, 1.6F, 0.05F, "x");
		menuSize.tip("Makes this whole menu bigger or smaller.");
		customTitle = bool("Sleep Home Screen", true);
		customTitle.tip("Replaces Minecraft's title screen with Sleep's.");
		customPause = bool("Sleep Pause Menu", true);
		customPause.tip("Replaces the pause menu (Esc) with Sleep's.");
		pauseStyle = choice("Pause Menu Style", 1, "Sleep (simple)", "Classic + Sleep");
		pauseStyle.showIf(() -> customPause.on());
		pauseStyle.tip("Classic keeps Minecraft's own pause menu and adds Sleep's buttons.");
		pausePlayer = bool("Your Player In The Pause Menu", true);
		pausePlayer.showIf(() -> customPause.on() && pauseStyle.idx() == 1);
		modsScreen = choice("Mods Screen", 0, "Sleep", "Mod Menu (original)");
		modsScreen.showIf(dev.solclient.gui.ModMenuLink::present);
		seasonal = choice("Seasonal Decorations", 0, "Automatic", "Off", "Halloween", "Christmas");
		seasonal.tip("Halloween / Christmas decorations on the home screen.");
		seasonalMusic = bool("Seasonal Menu Music", true);
		action("Seasonal music (.ogg / .mp3 files)", "Open Folder", () -> {
			java.io.File f = new java.io.File(net.minecraft.client.MinecraftClient.getInstance().runDirectory, "plegumo-music");
			f.mkdirs();
			net.minecraft.util.Util.getOperatingSystem().open(f);
		}, dev.solclient.gui.SeasonMusic::trackName);
		seasonalBackground = bool("Seasonal Home Background", true);
		restyleMenus = bool("Sleep Style For Minecraft Menus", true);
		restyleMenus.tip("Gives Minecraft's own menus (options, worlds, servers) Sleep's look.");
		restyleButtons = bool("Sleep Look For Menu Buttons", true);
		restyleButtons.tip("On: the buttons in Minecraft's own menus get the same look as Sleep's menus. Off: they stay the normal Minecraft ones.");
		homeShowcase = bool("Shop Banner On Home Screen", true);
		buttonStyle = choice("Button Style", 0, "Classic", "Rounded", "Outline", "Minimal");
		buttonAnimations = bool("Button Animations", false);
		buttonAnimations.tip("Smooth hover animations on buttons (a little heavier).");
		screenSize = num("Home & Pause Menu Size", 100, 60, 150, 5, "%");
		cornerRadius = num("Corner Radius", 3, 0, 12, 1, "");
		cornerRadius.tip("How round the corners of Sleep's menus are.");

		// ------------------------------------------------------------------ HUD & notifications
		header("HUD & Notifications");
		hudInF3 = bool("Show HUD In F3", false);
		hudInF3.tip("Keep your HUD on screen while the F3 debug screen is open.");
		toastPosition = choice("Notifications Position", 0, "Top Right", "Top Left", "Bottom Right", "Bottom Left");
		toastPosition.showIf(() -> false); // v99: always bottom left, like notificacao.html
		notifySound = bool("Notification Sound", true);
		notifyVolume = num("Notification Volume", 80, 0, 100, 5, "%");
		notifyVolume.showIf(() -> notifySound.on());
		notifySize = num("Notification Size", 55, 20, 300, 5, "%");
		notifySize.tip("How big the notification cards are. Use the preview button to see it.");
		soundMessage = text("Sound Message", "", 160, "").showIf(() -> false);
		soundFriend = text("Sound Friend", "", 160, "").showIf(() -> false);
		soundInvite = text("Sound Invite", "", 160, "").showIf(() -> false);
		soundClip = text("Sound Clip", "", 160, "").showIf(() -> false);
		soundError = text("Sound Error", "", 160, "").showIf(() -> false);
		soundOther = text("Sound Other", "", 160, "").showIf(() -> false);
		action("Preview Notification", "Show it", () -> dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.KUBO,
				"Notification preview", "Size and sound as set in the settings"), () -> "").tip("Shows a notification so you can check its size and sound.");
		pickSound("Message Sound", soundMessage);
		pickSound("Friend Sound", soundFriend);
		pickSound("Invite Sound", soundInvite);
		pickSound("Clip Saved Sound", soundClip);
		pickSound("Error Sound", soundError);
		pickSound("Other Sound", soundOther);

		// ------------------------------------------------------------------ Players
		header("Players");
		othersEmotes = bool("Show Others' Emotes", true);
		othersEmotes.tip("See the emotes other Sleep players do.");
		shareServer = bool("Friends Can See And Join My Server", true);
		shareServer.tip("Your Sleep friends see which server you are on and can join you.");
		shareEmotes = bool("Share My Emotes", true);
		shareEmotes.tip("Other Sleep players see the emotes you do.");
		solarCapes = bool("Show Solar Capes & Skins", true);
		solarCapes.tip("See the capes and skins that other Sleep players picked.");
		animatedCapes = bool("Animated Capes (video)", true);
		nameBadge = bool("Logo On Nametags", true);
		nameBadge.tip("A small Sleep logo next to the name of Sleep players.");
		cosmetics = bool("Show 3D Cosmetics", true);
		cosmetics.tip("3D accessories (hats, wings...) on players.");
		action("3D cosmetics status", "Refresh", () -> { }, dev.solclient.cosmetic.CosmeticFeatureRenderer::status);
		action("HUD status", "Reset", dev.solclient.SolClient::hudDiagReset, dev.solclient.SolClient::hudDiag)
				.tip("What the in-game HUD is doing. \"frames\" must grow while you play. If it stays at 0 the HUD is not being drawn at all.");
		action("Badge sync status", "Send again", dev.solclient.social.KuboLink::resendNow, dev.solclient.social.KuboLink::diag)
				.tip("Crown, badges and name colour reach other players through here. If \"got\" says nothing yet, nobody is sending to you.");

		// ------------------------------------------------------------------ Keys
		header("Keys");
		modsKey = key("Mods Key", 344);
		modsKey.tip("Opens this menu in game.");
		editHudKey = key("Edit HUD Key", 96);
		editHudKey.tip("Lets you drag the HUD elements to a new place.");
		emoteKey = key("Emote Wheel Key", 66);
		action("Edit every key on a keyboard", "Open", () -> mc.setScreen(new dev.solclient.gui.KeybindsScreen(mc.currentScreen)), () -> "");
		keybindsKey = key("Keybinds Editor Key", -1);
		action("Keys used twice", "Fix", () -> dev.solclient.KeyConflicts.fix(), dev.solclient.KeyConflicts::status);

		// ------------------------------------------------------------------ Performance
		header("Performance");
		action("What is lagging", "Refresh", () -> {
		}, dev.solclient.SolClient::slowestMods);
		action("Recorder", "Stop", dev.solclient.fightcam.Capture::stop,
				() -> dev.solclient.fightcam.Capture.running() ? "Running (Clip Events buffer)" : "Off");

		// ------------------------------------------------------------------ Loading screen
		header("Loading Screen");
		splashLogo = bool("Sleep Logo On Loading Screen", true);
		splashOnReload = bool("Also When Reloading Resource Packs", false);
		hideReloadScreen = bool("Hide Loading Screen On Reload", false);
		hideReloadScreen.tip("Adding or changing a resource pack reloads the game without the red Mojang loading screen: you keep seeing the game.");
		hideHudWhileLoading = bool("Hide Sleep HUD While Loading", true);

		// ------------------------------------------------------------------ Setup & reset
		header("Setup & Reset");
		perfPreset = choice("Performance Preset", 1, dev.solclient.gui.Presets.PERF).onPick(dev.solclient.gui.Presets::performance);
		perfPreset.tip("Turns heavy effects on or off in one go.");
		hudPreset = choice("HUD Preset", 1, dev.solclient.gui.Presets.HUD).onPick(dev.solclient.gui.Presets::hud);
		hudPreset.tip("How much HUD is shown: from almost nothing to everything.");
		onboarded = bool("Setup Done", false);
		onboarded.showIf(() -> false);
		action("First-time setup (language, performance, theme, HUD)", "Open", () -> mc.execute(() -> mc.setScreen(new dev.solclient.gui.OnboardingScreen(mc.currentScreen))),
				() -> "");
		action("Everything in this card back to normal", "Reset Settings", () -> {
			if (asking(askAll)) {
				askAll = 0;
				resetAll();
			} else {
				askAll = System.currentTimeMillis();
			}
		}, () -> asking(askAll) ? "Click again to confirm" : "");

		// ------------------------------------------------------------------ Testing
		header("Testing");
		action("Test coins (temporary)", "+1000 Coins", dev.solclient.social.Social::devAddCoins,
				() -> dev.solclient.social.Social.coins < 0 ? "" : dev.solclient.social.Social.coins + " coins");
	}

	/** A font file dragged onto the window while the Sleep settings are open: becomes the "Custom" font. */
	@Override
	public boolean onFileDropped(java.nio.file.Path file) {
		String n = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		if (n.endsWith(".ttf") || n.endsWith(".otf")) {
			dev.solclient.CustomFont.importFile(file);
			return true;
		}
		return false;
	}

	/** True for 3 seconds after the first click of a reset button (second click confirms). */
	private static boolean asking(long since) {
		return since != 0 && System.currentTimeMillis() - since < 3000L;
	}

	/** A "Choose..." row for the sound of one kind of notification (opens the Pick a sound window). */
	private void pickSound(String label, Option.Text slot) {
		Option.Action a = action(label, "Choose...", () -> {
			var screen = mc.currentScreen;
			mc.setScreen(new dev.solclient.gui.SoundPickerScreen(screen, label, slot.get(), "Default sound", true, ref -> {
				slot.set(ref == null ? "" : ref);
				Config.save();
			}));
		}, () -> slot.get().isEmpty() ? "Default" : dev.solclient.SoundLib.pretty(slot.get()));
		a.showIf(() -> notifySound.on());
	}

	/** The sound picked for this kind of notification: "" = the Sleep pling, "silent" = no sound. */
	public String notifySoundFor(dev.solclient.gui.Notify.Kind k) {
		Option.Text t = switch (k) {
			case MSG -> soundMessage;
			case FRIEND, ONLINE -> soundFriend;
			case PLAY -> soundInvite;
			case CLIP, DONE, REC -> soundClip;
			case ERROR -> soundError;
			default -> soundOther;
		};
		return t == null ? "" : t.get();
	}

	private void resetTheme() {
		for (Option<?> o : themeOptions) {
			o.reset();
		}
		Theme.refresh();
		Config.save();
	}

	private void resetAll() {
		for (Option<?> o : options) {
			if (o instanceof Option.Header || o instanceof Option.Action) {
				continue;
			}
			o.reset();
		}
		Theme.refresh();
		Config.save();
	}

	/** Switching the client language can also switch Minecraft itself (only when you change it, never at start-up). */
	@Override
	public void tick() {
		int l = language.idx();
		if (l != appliedLanguage) {
			if (appliedLanguage != -1 && mcLanguage.on()) {
				applyMinecraftLanguage(dev.solclient.Lang.MC_CODES[Math.max(0, Math.min(dev.solclient.Lang.MC_CODES.length - 1, l))]);
			}
			appliedLanguage = l;
		}
	}

	private void applyMinecraftLanguage(String code) {
		try {
			if (code.equals(mc.options.language)) {
				return;
			}
			mc.getLanguageManager().setLanguage(code);
			mc.options.language = code;
			mc.options.write();
			mc.reloadResources();
		} catch (RuntimeException e) {
			System.err.println("[Sleep] could not change the Minecraft language: " + e);
		}
	}
}
