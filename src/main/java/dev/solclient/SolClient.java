package dev.solclient;

import dev.solclient.gui.EmoteWheelScreen;
import dev.solclient.gui.MoveHudsScreen;
import dev.solclient.gui.SolMenuScreen;
import dev.solclient.mods.*;
import dev.solclient.social.Social;
import dev.solclient.social.SocialToast;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.ActionResult;

public class SolClient implements ClientModInitializer {

	public static final String ID = "sol_client";
	public static final List<Mod> MODS = new ArrayList<>();

	public static SolClientMod settings;
	public static ReachDisplayMod reach;
	public static ComboCounterMod combo;
	public static ParticlesMod particles;

	/** Set to false when the menu was closed with the key, so it is not re-opened until released. */
	public static boolean armed = true;
	private static boolean editArmed = true;
	/** Same as {@link #armed}, for the emote wheel key. */
	public static boolean emoteArmed = true;

	@Override
	public void onInitializeClient() {
		settings = new SolClientMod();
		reach = new ReachDisplayMod();
		combo = new ComboCounterMod();
		particles = new ParticlesMod();

		MODS.add(settings);
		MODS.add(new FpsMod());
		MODS.add(new CpsMod());
		MODS.add(new PingMod());
		MODS.add(new CoordinatesMod());
		MODS.add(new KeystrokesMod());
		MODS.add(new EquipmentMod());
		MODS.add(new PotionEffectsMod());
		MODS.add(combo);
		MODS.add(reach);
		MODS.add(new CrosshairMod());
		MODS.add(new ToggleSprintMod());
		MODS.add(new FreelookMod());
		MODS.add(new TaplookMod());
		MODS.add(new ZoomMod());
		MODS.add(particles);
		MODS.add(new TimeChangerMod());
		MODS.add(new CustomSkyMod());
		MODS.add(new ScoreboardMod());
		MODS.add(new PackUpdaterMod());
		MODS.add(new TweaksMod());
		MODS.add(new ChatMod());
		MODS.add(new MenuBlurMod());
		MODS.add(new BlockSelectionMod());
		MODS.add(new HitColourMod());
		MODS.add(new TabListMod());
		MODS.add(new TimersMod());
		MODS.add(new OverlayMod());
		MODS.add(new ClockMod());
		MODS.add(new DirectionMod());
		MODS.add(new ServerAddressMod());
		MODS.add(new MemoryMod());
		MODS.add(new SessionTimeMod());
		MODS.add(new FriendsHudMod());
		MODS.add(new ItemCounterMod());
		MODS.add(new CustomTexturesMod());
		MODS.add(new TextureStylesMod());
		MODS.add(new TotemPopsMod());
		MODS.add(new PlayerCardMod());
		MODS.add(new NickHiderMod());
		MODS.add(new AutoPasswordMod());
		MODS.add(new CapePhysicsMod());
		MODS.add(new NoHurtCamMod());
		MODS.add(new PerformanceMod());
		MODS.add(new OwnNametagMod());
		MODS.add(new ChatSearchMod());
		MODS.add(new ChatHistoryMod());
		MODS.add(new AutoReconnectMod());
		MODS.add(new BiomeMod());
		MODS.add(new LightLevelMod());
		MODS.add(new EntityCountMod());
		MODS.add(new ChunkPosMod());
		MODS.add(new HealthMod());
		MODS.add(new HungerMod());
		MODS.add(new XpLevelMod());
		MODS.add(new ArmourPointsMod());
		MODS.add(new GameTimeMod());
		MODS.add(new PlayerCountMod());
		MODS.add(new RotationMod());
		MODS.add(new ChatTimestampsMod());
		MODS.add(new ChatBubblesMod());
		MODS.add(new WaypointsMod());
		MODS.add(new FullbrightMod());
		MODS.add(new BossBarMod());
		MODS.add(new PackDisplayMod());
		MODS.add(new WatchPartyMod());
		MODS.add(new WeatherMod());
		MODS.add(new DayCounterMod());
		MODS.add(new dev.solclient.voice.VoiceChatMod());
		MODS.add(new dev.solclient.mods.PersonasMod());
		MODS.add(new dev.solclient.mods.TrustRadarMod());
		MODS.add(new dev.solclient.mods.WavesMod());
		MODS.add(new dev.solclient.mods.BetterVisualMod());
		MODS.add(new dev.solclient.mods.InputOptimizerMod());
		MODS.add(new dev.solclient.mods.ArmorStandPosesMod());
		MODS.add(new dev.solclient.mods.CustomRainMod());
		MODS.add(new StableFovMod());
		MODS.add(new FovChangerMod());
		MODS.add(new NoBobbingMod());
		MODS.add(new PatPatMod());
		MODS.add(new ChristmasWorldMod());
		MODS.add(new TotemAnimationMod());
		MODS.add(new BodyGlowMod());
		MODS.add(new NoFluidOverlayMod());
		MODS.add(new BactroTweaksMod());
		MODS.add(new LowShieldMod());
		MODS.add(new LowFireMod());
		MODS.add(new EnchantmentGlintMod());
		MODS.add(new CustomHandMod());
		MODS.add(new OldAnimationsMod());
		MODS.add(new HitboxesMod());
		MODS.add(new WailaMod());
		MODS.add(new TntTimerMod());
		MODS.add(new CommandGuardMod());
		MODS.add(new TitlesMod());
		MODS.add(new ActionBarMod());
		MODS.add(new NameTagStyleMod());
		MODS.add(new ChatHeadsMod());
		MODS.add(new GhostItemFixMod());
		MODS.add(new dev.solclient.mods.BetterSearchMod());
		MODS.add(new dev.solclient.mods.ItemFinderMod());
		MODS.add(new FightCamMod());
		MODS.add(new ClipEventsMod());
		MODS.add(new PhotoModeMod());
		MODS.add(new CinematicAfkMod());
		MODS.add(new CinematicIntroMod());
		MODS.add(new TweaksPlusMod());
		MODS.add(new SoundMixerMod());
		MODS.add(new ItemPhysicsMod());
		MODS.add(new NametagHealthMod());
		MODS.add(new HostWorldMod());
		MODS.add(new NameColourMod());
		MODS.add(new ServerFavouritesMod());
		MODS.add(new SmoothFishingLineMod());
		MODS.add(new TripwireColourMod());
		MODS.add(new AutoProfilesMod());
		MODS.add(new HudLayoutsMod());
		MODS.add(new PackProfilesMod());
		MODS.add(new CloudSyncMod());
		MODS.add(new VerticalClipsMod());
		MODS.add(new SkinEditorMod());
		MODS.add(new TpsDisplayMod());
		MODS.add(new SmoothCameraMod());
		MODS.add(new AspectRatioMod());
		MODS.add(new NoRenderMod());
		MODS.add(new CrystalTweaksMod());
		MODS.add(new SpotifyMod());
		MODS.add(new BrowserMod());
		MODS.add(new TransparentPlayersMod());
		MODS.add(new HideArmorMod());
		MODS.add(new DurabilityOverlayMod());
		MODS.add(new CleanViewMod());
		MODS.add(new HideInventoryModelMod());
		MODS.add(new SpeedFovLimiterMod());
		MODS.add(new CrossbowTweaksMod());
		MODS.add(new CrosshairAddonsMod());
		MODS.add(new AnimatedPacksMod());
		// extensions downloaded from the Sleep store (Mods > Extensions): their mods join the list here
		dev.solclient.ext.Extensions.loadInstalled();
		// small mods live inside one card each (fewer cards in the menu; every mod keeps its settings)
		group(new ModGroup("Info HUD", "info_hud", "Clock, biome, memory, TPS, rotation and other one-line HUDs.", Mod.Category.HUD),
				ClockMod.class, GameTimeMod.class, SessionTimeMod.class, DayCounterMod.class, MemoryMod.class, ServerAddressMod.class,
				PlayerCountMod.class, BiomeMod.class, EntityCountMod.class, ChunkPosMod.class, RotationMod.class,
				DirectionMod.class, HealthMod.class, HungerMod.class, XpLevelMod.class, ArmourPointsMod.class, TpsDisplayMod.class,
				PackDisplayMod.class, FriendsHudMod.class, WailaMod.class);
		group(new ModGroup("Screen Tweaks", "screen_tweaks", "No hurt cam, no bobbing, low fire / shield, titles, boss bar and more.", Mod.Category.VISUAL),
				NoHurtCamMod.class, NoBobbingMod.class, LowFireMod.class, LowShieldMod.class, NoFluidOverlayMod.class, TotemAnimationMod.class,
				TitlesMod.class, ActionBarMod.class, CleanViewMod.class, HideInventoryModelMod.class, BossBarMod.class, EnchantmentGlintMod.class);
		group(new ModGroup("FOV", "fov", "FOV Changer, Stable FOV and the Speed FOV Limiter.", Mod.Category.VISUAL),
				FovChangerMod.class, StableFovMod.class, SpeedFovLimiterMod.class);
		group(new ModGroup("Nametags", "nametags", "Name tag style, your own nametag, hearts under names and your name colour.", Mod.Category.VISUAL),
				NameTagStyleMod.class, OwnNametagMod.class, NametagHealthMod.class, NameColourMod.class);
		group(new ModGroup("Player Visuals", "player_visuals", "Transparent players, hide armour, durability overlay, hitboxes and body glow.", Mod.Category.VISUAL),
				TransparentPlayersMod.class, HideArmorMod.class, DurabilityOverlayMod.class, HitboxesMod.class, BodyGlowMod.class);
		group(new ModGroup("Hand & Items", "hand_items", "Custom hand, 1.7 animations, item physics, crossbow and a smooth fishing line.", Mod.Category.VISUAL),
				CustomHandMod.class, OldAnimationsMod.class, ItemPhysicsMod.class, CrossbowTweaksMod.class, SmoothFishingLineMod.class);
		group(new ModGroup("Profiles & Sync", "profiles_sync", "Auto profiles, HUD layouts, resource pack sets and cloud settings.", Mod.Category.UTILITY),
				AutoProfilesMod.class, HudLayoutsMod.class, PackProfilesMod.class, CloudSyncMod.class);
		group(new ModGroup("Camera", "camera_group", "Zoom, Freelook, Taplook, Smooth Camera, Aspect Ratio and Cinematic AFK Camera.", Mod.Category.UTILITY),
				ZoomMod.class, FreelookMod.class, TaplookMod.class, SmoothCameraMod.class, AspectRatioMod.class, CinematicAfkMod.class);
		group(new ModGroup("Recording", "recording", "FightCam, Clip Events, TikTok Clips and Photo Mode.", Mod.Category.UTILITY),
				FightCamMod.class, ClipEventsMod.class, VerticalClipsMod.class, PhotoModeMod.class);
		group(new ModGroup("World", "world_group", "Time Changer, Weather, Fullbright, No Render and Block Selection.", Mod.Category.VISUAL),
				TimeChangerMod.class, WeatherMod.class, FullbrightMod.class, NoRenderMod.class, BlockSelectionMod.class);
		group(new ModGroup("Combat HUD", "combat_hud", "Combo counter, reach display and crosshair hit markers.", Mod.Category.HUD),
				ComboCounterMod.class, ReachDisplayMod.class, CrosshairAddonsMod.class);
		group(new ModGroup("PvP", "pvp_group", "Hit delay fix, shiny potions, crystal tweaks, TNT timer, hit colour and attack particles.", Mod.Category.VISUAL),
				TweaksPlusMod.class, CrystalTweaksMod.class, TntTimerMod.class, HitColourMod.class, ParticlesMod.class);
		// the Chat card holds the other chat mods
		for (Mod m : MODS) {
			if (m instanceof ChatHistoryMod || m instanceof ChatSearchMod || m instanceof ChatHeadsMod || m instanceof ChatTimestampsMod
					|| m instanceof ChatBubblesMod) {
				for (Mod c : MODS) {
					if (c instanceof ChatMod) {
						m.parent = c;
					}
				}
			}
		}

		for (Mod m : MODS) {
			m.enabled = m.locked || (m.enabledByDefault() && !dev.solclient.ext.Extensions.isFromExtension(m));
		}
		Config.load(MODS);
		// Discord "Playing Sleep Client": built in, always on (not a mod in the list)
		new dev.solclient.discord.DiscordPresenceMod().startBackground();
		for (Mod m : MODS) {
			m.init();
		}
		dev.solclient.gui.Theme.refresh();
		Config.save();

		PayloadTypeRegistry.playS2C().register(TimersPayload.ID, TimersPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(TimersPayload.ID, (payload, context) -> TimersMod.handle(payload.data()));

		dev.solclient.addons.Addons.init();
		// PvP Practice / Bridge Practice now live in the "practice" extension (it registers itself in Addons)
		Social.init();
		dev.solclient.account.Accounts.init();
		dev.solclient.social.Presence.onEmote = dev.solclient.emote.EmoteManager::playRemote;

		ClientTickEvents.END_CLIENT_TICK.register(SolClient::tick);
		removeOldVerityFiles();
		dev.solclient.gui.WorldTransfer.register();
		HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));
		dev.solclient.social.ServerShare.register();
		dev.solclient.video.WatchPartyHooks.register();
		dev.solclient.video.Panels.register();
		dev.solclient.cosmetic.pet.Pets.init();
		dev.solclient.fx.MaceFx.init();
		dev.solclient.fx.MaceArt.init();
		dev.solclient.fx.LiquidFx.init();
		dev.solclient.music.SkyLyrics.register();
		dev.solclient.music.NowPlayingCard.register();
		dev.solclient.fightcam.Capture.reapStrays();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			FightCamMod.shutdown();
			Config.save();
			dev.solclient.stats.KuboStats.save();
			CloudSyncMod.onQuit();
			Social.shutdown();
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient() && GhostItemFixMod.instance != null && GhostItemFixMod.instance.enabled) {
				GhostItemFixMod.instance.onUse(player, hand);
			}
			return ActionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient()) {
				if (reach.enabled) reach.onAttack(entity);
				if (combo.enabled) combo.onAttack(entity);
				if (particles.enabled) particles.onAttack(entity);
				if (FightCamMod.instance != null && FightCamMod.instance.enabled) FightCamMod.instance.onAttack(entity);
				if (ClipEventsMod.instance != null && ClipEventsMod.instance.enabled) ClipEventsMod.instance.onAttack(entity);
				if (CustomSkyMod.instance != null && CustomSkyMod.instance.enabled) CustomSkyMod.instance.onAttack(entity);
				if (CrosshairAddonsMod.instance != null && CrosshairAddonsMod.instance.enabled) CrosshairAddonsMod.instance.onAttack(entity);
				dev.solclient.ext.KuboApi.fireAttack(entity);
				dev.solclient.fx.killfx.KillFxTrigger.onAttack(entity);
			}
			return ActionResult.PASS;
		});
	}

	/** Puts the registered mods of these classes inside a group card (and adds the card). */
	@SafeVarargs
	private static void group(ModGroup g, Class<? extends Mod>... types) {
		int at = -1;
		for (int i = 0; i < MODS.size(); i++) {
			Mod m = MODS.get(i);
			for (Class<? extends Mod> t : types) {
				if (t.isInstance(m)) {
					m.parent = g;
					if (at < 0) {
						at = i;
					}
				}
			}
		}
		MODS.add(at < 0 ? MODS.size() : at, g); // the card sits where its first mod was
	}

	private static boolean commanderArmed = true, keybindsArmed, hostInviteArmed = true;

	private static void pollKeys() {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean kbk = settings.keybindsKey != null && Keys.down(settings.keybindsKey.code());
		if (kbk && !keybindsArmed && mc.currentScreen == null && mc.player != null) {
			mc.setScreen(new dev.solclient.gui.KeybindsScreen(null));
		}
		keybindsArmed = kbk;
		// Commander: Ctrl / Cmd + K anywhere in game
		boolean cmd = (Keys.down(341) || Keys.down(345) || Keys.down(343) || Keys.down(347)) && Keys.down(75);
		if (!cmd) {
			commanderArmed = true;
		} else if (commanderArmed && mc.currentScreen == null && mc.player != null) {
			commanderArmed = false;
			dev.solclient.gui.CommanderScreen.open();
		}
		// Host World: Shift + Tab while hosting opens the invite list
		boolean hostInv = Keys.shiftHeld() && Keys.down(258);
		if (!hostInv) {
			hostInviteArmed = true;
		} else if (hostInviteArmed && mc.currentScreen == null && mc.player != null && dev.solclient.social.HostWorld.hosting()) {
			hostInviteArmed = false;
			mc.setScreen(new dev.solclient.gui.HostWorldScreen(null, true));
		}
		boolean down = Keys.down(settings.modsKey.code());
		if (!down) {
			armed = true;
		} else if (armed && mc.currentScreen == null && mc.player != null && mc.world != null) {
			armed = false;
			mc.setScreen(new SolMenuScreen());
		}
		boolean edit = Keys.down(settings.editHudKey.code());
		if (!edit) {
			editArmed = true;
		} else if (editArmed && mc.currentScreen == null && mc.player != null && mc.world != null) {
			editArmed = false;
			mc.setScreen(new MoveHudsScreen(false));
		}
		boolean emote = Keys.down(settings.emoteKey.code());
		if (!emote) {
			emoteArmed = true;
		} else if (emoteArmed && mc.currentScreen == null && mc.player != null && mc.world != null) {
			emoteArmed = false;
			mc.setScreen(new EmoteWheelScreen());
		}
	}

	private static boolean startupFixes;

	private static void tick(MinecraftClient client) {
		try {
			ResourceGuard.tick(client);
		} catch (RuntimeException e) {
			System.err.println("[Sleep] resource pack guard: " + e);
		}
		if (loadingScreenUp(client)) {
			// Minecraft is loading: its main thread is busy with the reload, so only what must watch the loading
			// screen runs. Everything else (social, menus, capes, badges, fonts...) waits until it is done, so the
			// loading screen keeps moving instead of freezing.
			dev.solclient.gui.SeasonMusic.tick();
			CinematicIntroMod.clientTick(client);
			return;
		}
		if (!startupFixes) {
			startupFixes = true;
			Thread t = new Thread(() -> {
				dev.solclient.CustomFont.repairOldPacks();
				client.execute(dev.solclient.mods.TextureStylesMod::ensureApplied);
			}, "sleep-startup-fixes");
			t.setDaemon(true);
			t.start();
		}
		long kuboPerfT0 = System.nanoTime();
		pollKeys();
		dev.solclient.fx.killfx.KillFxTrigger.tick();
		dev.solclient.emote.EmoteManager.tick();
		HitColourMod.refresh();
		Social.tick(client);
		dev.solclient.gui.SeasonMusic.tick();
		dev.solclient.gamemods.GameMods.tick();
		dev.solclient.account.LastServers.tick(client);
		dev.solclient.gui.Menus.tick(client);
		FullbrightMod.pollKey();
		dev.solclient.video.VideoScreens.tick();
		dev.solclient.social.CapeMedia.tick();
		CinematicIntroMod.clientTick(client);
		dev.solclient.social.KuboLink.tick(client);
		dev.solclient.social.ProfileBadges.tick(client);
		dev.solclient.social.HostWorld.tick(client);
		recorderTick(client);
		Perf.add("Client background (social, menus, capes...)", System.nanoTime() - kuboPerfT0);
		Perf.tick(client, MODS);
		if (settings != null && settings.font != null) {
			Gfx.setFont(settings.font.idx()); // Settings > Font (cheap: only does work when it changed)
			if (settings.gameFont != null) GameFont.sync(settings.gameFont.idx()); // Settings > Whole Game Font
		}
		if (client.player == null) {
			return;
		}
		KeyConflicts.warnOnce();
		LagDoctor.tick(client, MODS);
		try {
			dev.solclient.net.PrivateConnection.autoTick();
		} catch (RuntimeException ignored) {
		}
		dev.solclient.stats.KuboStats.tick();
		for (Mod m : MODS) {
			if (m.enabled) {
				long t0 = System.nanoTime();
				m.tick();
				m.addCost(System.nanoTime() - t0);
			}
		}
	}

	/** The mods that cost the most time right now, for Settings > Performance. */
	public static String slowestMods() {
		List<Mod> l = new ArrayList<>();
		for (Mod m : MODS) {
			if (m.enabled && m.costUs > 20F) {
				l.add(m);
			}
		}
		l.sort((a, b) -> Float.compare(b.costUs, a.costUs));
		if (l.isEmpty()) {
			return "Nothing heavy (all mods < 0.02 ms)";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(3, l.size()); i++) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(l.get(i).name).append(String.format(" %.2f ms", l.get(i).costUs / 1000F));
		}
		return sb.toString();
	}

	private static int recorderTicks;

	/** Recorder housekeeping: old buffer pieces deleted once a second; the recorder stops when you leave the world. */
	private static void recorderTick(MinecraftClient client) {
		if (++recorderTicks % 20 == 0) {
			dev.solclient.fightcam.Capture.housekeeping();
		}
		if (client.world == null && dev.solclient.fightcam.Capture.running() && !dev.solclient.fightcam.Capture.recording()) {
			dev.solclient.fightcam.Capture.stop();
		}
		if (client.world == null && dev.solclient.fightcam.Replay.active()) {
			dev.solclient.fightcam.Replay.stop();
		}
	}

	private static void frame() {
		pollKeys();
		CpsMonitor.LMB.poll();
		CpsMonitor.RMB.poll();
		for (Mod m : MODS) {
			if (m.enabled) {
				long t0 = System.nanoTime();
				m.frame();
				m.addCost(System.nanoTime() - t0);
			}
		}
	}

	// ---- HUD diagnostics: what the in-game HUD is doing (shown in Sleep settings > HUD status, and printed to the log once)
	private static volatile long hudAt;
	private static int hudCalls, hudFallbacks, hudSkipHidden, hudSkipLoading, hudSkipF3, hudRan, hudWidgets;
	private static String hudErr;

	public static String hudDiag() {
		return "frames " + hudCalls + " (backup " + hudFallbacks + "), drawn " + hudRan + ", widgets " + hudWidgets + ", skipped: hidden " + hudSkipHidden + " loading "
				+ hudSkipLoading + " F3 " + hudSkipF3 + (hudErr == null ? "" : ", error " + hudErr);
	}

	public static void hudDiagReset() {
		hudCalls = hudFallbacks = hudSkipHidden = hudSkipLoading = hudSkipF3 = hudRan = hudWidgets = 0;
		hudErr = null;
	}

	/** Called at the end of InGameHud.render (mixin): draws the HUD itself if the Fabric callback did not run in the last moment. */
	public static void hudBackup(DrawContext context) {
		if (System.currentTimeMillis() - hudAt < 800L) {
			return;
		}
		hudFallbacks++;
		renderHudBody(context);
	}

	private static void renderHud(DrawContext context) {
		hudAt = System.currentTimeMillis();
		renderHudBody(context);
	}

	private static void renderHudBody(DrawContext context) {
		try {
			renderHudInner(context);
		} catch (Throwable t) {
			// never let the HUD take the frame (or the callback) down with it
			if (hudErr == null) {
				hudErr = "HUD: " + t;
				System.err.println("[Sleep] HUD failed");
				t.printStackTrace();
			}
		}
	}

	/** Only the real "Minecraft is loading" screen counts: some other mod's overlay that never closes must not hide our HUD for good. */
	public static boolean loadingScreenUp(MinecraftClient mc) {
		return mc.getOverlay() instanceof net.minecraft.client.gui.screen.SplashOverlay;
	}

	private static boolean hudWhyTold;

	/** If the HUD still has not drawn anything ~10 s into a world, say why in the chat (so a friend can read it out). */
	private static void hudWhy(MinecraftClient mc, int n) {
		if (hudWhyTold || mc.player == null || hudRan > 0) {
			return;
		}
		if (++hudWorldFrames < 600) {
			return;
		}
		hudWhyTold = true;
		String m = "[Sleep] HUD did not draw: " + hudDiag() + " | hudHidden=" + mc.options.hudHidden + " F3=" + f3Open(mc) + " overlay="
				+ (mc.getOverlay() == null ? "none" : mc.getOverlay().getClass().getName());
		System.out.println(m);
		mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(m));
	}

	private static int hudWorldFrames;

	/**
	 * The full F3 screen is open (the key was pressed). Not DebugHud.shouldShowDebugHud(): since 1.21.9 that is also true while
	 * any single debug line is set to "always shown" (F3 + F6, or a mod / profile that keeps the FPS line on), which hid the
	 * whole Sleep HUD forever on some computers.
	 */
	public static boolean f3Open(MinecraftClient mc) {
		try {
			return mc.debugHudEntryList.isF3Enabled() && !mc.options.hudHidden;
		} catch (Throwable t) {
			return mc.getDebugHud().shouldShowDebugHud();
		}
	}

	private static void renderHudInner(DrawContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int n = ++hudCalls;
		if (n == 1 || n == 60 || n == 300 || n % 3000 == 0) {
			System.out.println("[Sleep] HUD status #" + n + " (player " + (mc.player != null) + ", screen " + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName())
					+ ", overlay " + (mc.getOverlay() != null) + "): " + hudDiag());
		}
		Perf.frame();
		try {
			frame();
		} catch (Throwable t) {
			if (hudErr == null) {
				hudErr = "frame: " + t;
				t.printStackTrace();
			}
		}
		try {
			CinematicAfkMod.drawOverlay(context); // cinema bars + hint: before the "HUD hidden" check
		} catch (Throwable t) {
			if (hudErr == null) {
				hudErr = "cinematic: " + t;
				t.printStackTrace();
			}
		}
		hudWhy(mc, n);
		if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof MoveHudsScreen) {
			if (mc.player != null && mc.options.hudHidden) {
				hudSkipHidden++;
			}
			return;
		}
		// while Minecraft reloads resources (e.g. after picking a resource pack) our textures/fonts are being
		// reloaded too: drawing the HUD then is what glitches on the loading screen
		if (loadingScreenUp(mc) && settings.hideHudWhileLoading.on() && !dev.solclient.gui.SplashLogo.quietReload) {
			hudSkipLoading++;
			return;
		}
		dev.solclient.ext.KuboApi.drawF3(context);
		// FIX: o HUD aparece SEMPRE. Antes escondia-se quando f3Open() dava true; em alguns PCs isso acontecia sem F3 aberto
		// (o HUD só voltava ao ligar as hitboxes com F3+B). Já não depende do estado do F3 / hitboxes.
		if (dev.solclient.mods.ItemFinderMod.instance != null && dev.solclient.mods.ItemFinderMod.instance.enabled) {
			dev.solclient.mods.ItemFinderMod.instance.draw(context);
		}
		if (WaypointsMod.instance != null && WaypointsMod.instance.enabled) {
			WaypointsMod.instance.draw(context);
		}
		if (PatPatMod.instance != null && PatPatMod.instance.enabled) {
			PatPatMod.instance.draw(context);
		}
		if (TntTimerMod.instance != null && TntTimerMod.instance.enabled) {
			TntTimerMod.instance.draw(context);
		}
		hudRan++;
		dev.solclient.ext.KuboApi.drawHud(context, false);
		int widgets = 0;
		for (Mod m : MODS) {
			if (m.enabled && m instanceof HudMod h) {
				if (dev.solclient.ext.KuboApi.hidesHud(m)) {
					continue;
				}
				long t0 = System.nanoTime();
				try {
					h.draw(context, false);
					widgets++;
				} catch (Throwable t) {
					// one broken widget must not stop the others
					if (hudErr == null) {
						hudErr = m.name + ": " + t;
						System.err.println("[Sleep] HUD widget failed: " + m.name);
						t.printStackTrace();
					}
				}
				m.addCost(System.nanoTime() - t0);
			}
		}
		hudWidgets = widgets;
		if (ScoreboardMod.replacesVanilla()) {
			ScoreboardMod.instance.draw(context);
		}
		if (TabListMod.replacesVanilla() && mc.options.playerListKey.isPressed()) {
			TabListMod.instance.draw(context);
		}
		if (CrosshairMod.replacesVanilla()) {
			CrosshairMod.instance.draw(context);
		}
		if (EquipmentMod.instance != null && EquipmentMod.instance.enabled) {
			EquipmentMod.instance.drawHotbar(context);
		}
		if (CrossbowTweaksMod.instance != null && CrossbowTweaksMod.instance.enabled) {
			CrossbowTweaksMod.instance.draw(context);
		}
		if (CrosshairAddonsMod.instance != null && CrosshairAddonsMod.instance.enabled) {
			CrosshairAddonsMod.instance.draw(context);
		}
		if (FightCamMod.instance != null && FightCamMod.instance.enabled) {
			FightCamMod.instance.drawRec(context);
		}
		dev.solclient.fightcam.ClipToast.draw(context);
		if (CustomSkyMod.instance != null && CustomSkyMod.instance.enabled) {
			CustomSkyMod.instance.draw(context);
		}
		ScreenFxMod.drawAll(context);
		if (BrowserMod.instance != null && BrowserMod.instance.enabled) {
			BrowserMod.instance.drawHud(context);
		}
		dev.solclient.voice.VoiceIcons.drawHud(context);
		dev.solclient.video.VideoScreens.drawHud(context);
		dev.solclient.ext.KuboApi.drawHud(context, true);
		if (MinecraftClient.getInstance().currentScreen == null) {
			dev.solclient.gui.Notify.draw(context); // with a screen open it is drawn over the screen (NotifyScreenMixin)
		}
	}

	/**
	 * Verity is now a plain pet. The old Verity downloaded his AI brain (Ollama + models), his voice (Piper) and kept a config:
	 * delete all of that once, in the background, so it does not keep taking disk space.
	 */
	private static void removeOldVerityFiles() {
		Thread t = new Thread(() -> {
			try {
				java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("verity");
				if (java.nio.file.Files.isDirectory(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
					try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(dir)) {
						walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
							try {
								java.nio.file.Files.deleteIfExists(f);
							} catch (Exception ignored) {
								// a file in use: it goes next time
							}
						});
					}
					System.out.println("[Sleep] removed the old Verity downloads");
				}
				java.nio.file.Files.deleteIfExists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-verity.json"));
			} catch (Throwable e) {
				System.err.println("[Sleep] could not remove the old Verity files: " + e);
			}
		}, "Sleep-verity-cleanup");
		t.setDaemon(true);
		t.start();
	}
}
