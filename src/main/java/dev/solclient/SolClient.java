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
		MODS.add(new dev.solclient.mods.RealisticLiquidsMod());
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
		group(new ModGroup("Info HUD", "info_hud", "Clock, biome, light level, memory, TPS, rotation and other one-line HUDs.", Mod.Category.HUD),
				ClockMod.class, GameTimeMod.class, SessionTimeMod.class, DayCounterMod.class, MemoryMod.class, ServerAddressMod.class,
				PlayerCountMod.class, BiomeMod.class, LightLevelMod.class, EntityCountMod.class, ChunkPosMod.class, RotationMod.class,
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
		HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));
		dev.solclient.social.ServerShare.register();
		dev.solclient.video.WatchPartyHooks.register();
		dev.solclient.video.Panels.register();
		dev.solclient.cosmetic.pet.Pets.init();
		dev.solclient.fx.MaceFx.init();
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

	private static void tick(MinecraftClient client) {
		try {
			ResourceGuard.tick(client);
		} catch (RuntimeException e) {
			System.err.println("[Sleep] resource pack guard: " + e);
		}
		pollKeys();
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
		if (settings != null && settings.font != null) {
			Gfx.setFont(settings.font.idx()); // Settings > Font (cheap: only does work when it changed)
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

	private static void renderHud(DrawContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		frame();
		CinematicAfkMod.drawOverlay(context); // cinema bars + hint: before the "HUD hidden" check
		if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof MoveHudsScreen) {
			return;
		}
		// while Minecraft reloads resources (e.g. after picking a resource pack) our textures/fonts are being
		// reloaded too: drawing the HUD then is what glitches on the loading screen
		if (mc.getOverlay() != null && settings.hideHudWhileLoading.on() && !dev.solclient.gui.SplashLogo.quietReload) {
			return;
		}
		dev.solclient.ext.KuboApi.drawF3(context);
		if (!settings.hudInF3.on() && mc.getDebugHud().shouldShowDebugHud()) {
			if (TabListMod.replacesVanilla() && mc.options.playerListKey.isPressed()) {
				TabListMod.instance.draw(context); // the tab list still shows in F3, like vanilla
			}
			return; // like Lunar's "Show HUD in F3" off
		}
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
		dev.solclient.ext.KuboApi.drawHud(context, false);
		for (Mod m : MODS) {
			if (m.enabled && m instanceof HudMod h) {
				if (dev.solclient.ext.KuboApi.hidesHud(m)) {
					continue;
				}
				long t0 = System.nanoTime();
				h.draw(context, false);
				m.addCost(System.nanoTime() - t0);
			}
		}
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
}
