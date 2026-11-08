package dev.solclient.addons.ranked;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.ext.KuboApi;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * What the players see: "match found", the searching pill, the duel intro and the victory screen. The intro works like
 * the intro page (duelo-intro-best.html) but in Minecraft: the real arena and players filmed by {@link RankedCinema},
 * the page's graphics drawn by {@link DuelHud}, and its music + 3-2-1 (one track mixed on the page's beats). It starts
 * when the server freezes everyone for the intro and ends on FIGHT!, exactly when the server lets you move.
 * The victory screen starts the moment the server says who won.
 */
public final class RankedFx {

	private RankedFx() {
	}

	// ------------------------------------------------------------------ state
	private static String introFor;
	private static long introStartMs;
	private static RankedCinema cinema;
	private static DuelHud.Info info;
	private static boolean introOn, camOn, soundStarted;
	private static Perspective prevPerspective;
	private static boolean prevHudHidden, savedView;

	private static String endFor;
	private static String endWinner;
	private static boolean endWon, endNone;
	private static long endShownAt, leaveAt;
	private static boolean endSoundPlayed;
	private static Ranked.Match endMatch;
	private static float[] leaveBox;

	static void found(Ranked.Match m) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F, 0.9F));
		dev.solclient.social.Social.say(Lang.tr("Match found! ", "Partida encontrada! ") + names(m.teamA()) + " vs " + names(m.teamB())
				+ (Ranked.iAmHost() ? Lang.tr(" - you host it, opening the arena...", " - és tu o host, a abrir a arena...")
						: Lang.tr(" - joining the host...", " - a entrar no host...")), false);
	}

	/** The site's result arrived (ELO change); opens the victory screen if the server's signal was missed. */
	static void showEnd(Ranked.Match m) {
		endMatch = m;
		if (m.id().equals(endFor) || m.id().equals(lastEnded) || m.winner() == null) {
			return;
		}
		startEnd(m, m.winner());
	}

	private static String names(List<Ranked.Player> t) {
		StringBuilder sb = new StringBuilder();
		for (var p : t) {
			if (sb.length() > 0) {
				sb.append(" & ");
			}
			sb.append(p.mcUsername());
		}
		return sb.toString();
	}

	static String kitName(String id) {
		return Ranked.kit(id).name();
	}

	private static DuelHud.Info infoOf(Ranked.Match m, String local) {
		String other = "A".equals(local) ? "B" : "A";
		return new DuelHud.Info(m.ranked() ? "Ranked match" : "Friendly duel", "Arena " + m.arena(), m.mode(), m.kit(), local, other,
				"A".equals(local) ? m.teamA() : m.teamB(), "A".equals(local) ? m.teamB() : m.teamA(), m.ranked() ? 2 : m.firstTo());
	}

	// ------------------------------------------------------------------ client tick
	public static void tick(MinecraftClient mc) {
		Ranked.Match m = Ranked.match;
		var player = mc.player;
		// the intro starts when the server's intro freeze is on you
		if (m != null && player != null && !m.id().equals(introFor) && m.has(Ranked.me())) {
			var eff = player.getStatusEffect(StatusEffects.SLOWNESS);
			if (eff != null && eff.getAmplifier() == RankedServer.INTRO_MARK) {
				int total = (RankedServer.INTRO_TICKS / 20 + 1) * 20;
				float pos = Math.max(0F, (total - eff.getDuration()) / 20F);
				startIntro(mc, m, pos);
			}
		}
		reportLoaded(mc, m);
		if (testDummyUntil > 0 && System.currentTimeMillis() > testDummyUntil) {
			testDummyUntil = 0;
			removeDummy(mc);
		}
		if (introOn) {
			double t = introT();
			if (t >= RankedCinema.POV && camOn) {
				// your own eyes now: give the camera back, looking at your opponent
				stopCamera(mc, true);
			}
			if (t >= RankedCinema.GO && mc.currentScreen instanceof Stage) {
				mc.setScreen(null); // FIGHT!: you can move
				restoreView(mc);
			}
			if (t >= RankedCinema.END) {
				introOn = false;
				restoreView(mc);
			}
		}
		// the end: the server's Luck level says who won
		if (m != null && player != null && !m.id().equals(endFor) && !m.id().equals(lastEnded) && m.has(Ranked.me())) {
			var luck = player.getStatusEffect(StatusEffects.LUCK);
			if (luck != null && luck.getAmplifier() >= RankedServer.WIN_MARK_A && luck.getAmplifier() <= RankedServer.WIN_MARK_NONE) {
				int a = luck.getAmplifier();
				String who = a == RankedServer.WIN_MARK_A ? "A" : a == RankedServer.WIN_MARK_B ? "B" : "none";
				Ranked.confirmResult(m.id(), who);
				startEnd(m, who);
			}
		}
		if (endFor != null) {
			double vt = (System.currentTimeMillis() - endShownAt) / 1000.0;
			if (!endSoundPlayed && !endNone && vt >= (endWon ? .74 - .062 : .74 - .284)) {
				endSoundPlayed = true;
				play(mc, endWon ? "duel.win" : "duel.lose");
			}
			if (leaveAt > 0 && System.currentTimeMillis() > leaveAt) {
				leave(mc);
			}
		}
	}

	private static long waitSeenAt;
	private static int readyCooldown;

	/** While the server parks you at your spawn: tells it once the arena has really loaded on your screen. */
	private static void reportLoaded(MinecraftClient mc, Ranked.Match m) {
		var eff = mc.player == null ? null : mc.player.getStatusEffect(StatusEffects.SLOWNESS);
		if (m == null || eff == null || eff.getAmplifier() != RankedServer.WAIT_MARK || mc.world == null) {
			waitSeenAt = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (waitSeenAt == 0) {
			waitSeenAt = now;
		}
		if (--readyCooldown > 0 || mc.currentScreen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen) {
			return;
		}
		RankedWorld.Arena a = RankedWorld.arena(m.arena());
		boolean chunks = a == null || loaded(mc, a.a()) && loaded(mc, a.b());
		boolean drawn = mc.worldRenderer.isTerrainRenderComplete();
		// very slow machines never get "complete" while chunks keep arriving: 10 s at the spawn is enough
		if (chunks && drawn && now - waitSeenAt > 500L || now - waitSeenAt > 10_000L) {
			readyCooldown = 20;
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RankedReady.ID)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new RankedReady());
			}
		}
	}

	private static boolean loaded(MinecraftClient mc, RankedWorld.Spot s) {
		return mc.world.getChunkManager().isChunkLoaded((int) Math.floor(s.x()) >> 4, (int) Math.floor(s.z()) >> 4);
	}

	private static double introT() {
		return (System.currentTimeMillis() - introStartMs) / 1000.0;
	}

	// ------------------------------------------------------------------ test (/ranked intro, /ranked victory)
	private static net.minecraft.client.network.OtherClientPlayerEntity dummy;

	private static Ranked.Match testMatch(MinecraftClient mc, String kit) {
		String me = Ranked.me();
		Ranked.Player a = new Ranked.Player("", me, me, "", 1312, "LT4");
		Ranked.Player b = new Ranked.Player("", "Steve", "Steve", "", 1405, "HT4");
		return new Ranked.Match("test-" + System.currentTimeMillis(), true, "1v1", kit, 7, "", me, null, "live", List.of(a), List.of(b), null, 0, 2);
	}

	/** A dummy opponent (only on your screen) 12 blocks in front of you, looking at you. */
	private static Vec3d spawnDummy(MinecraftClient mc) {
		removeDummy(mc);
		var p = mc.player;
		double yaw = Math.toRadians(p.getYaw());
		Vec3d at = new Vec3d(p.getX() - Math.sin(yaw) * 12, p.getY(), p.getZ() + Math.cos(yaw) * 12);
		try {
			dummy = new net.minecraft.client.network.OtherClientPlayerEntity(mc.world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Steve"));
			dummy.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw() + 180F, 0F);
			dummy.setHeadYaw(p.getYaw() + 180F);
			dummy.setBodyYaw(p.getYaw() + 180F);
			mc.world.addEntity(dummy);
		} catch (Throwable t) {
			dummy = null;
		}
		return at;
	}

	private static void removeDummy(MinecraftClient mc) {
		if (dummy != null && mc.world != null) {
			mc.world.removeEntity(dummy.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
		}
		dummy = null;
	}

	/** Plays the intro here, against a dummy (nothing is sent anywhere). */
	public static void testIntro(MinecraftClient mc, String kit) {
		if (mc.player == null || mc.world == null) {
			return;
		}
		Ranked.Match m = testMatch(mc, kit);
		Vec3d op = spawnDummy(mc);
		java.util.Map<String, Vec3d> pos = new java.util.HashMap<>();
		pos.put(Ranked.me().toLowerCase(java.util.Locale.ROOT), new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		pos.put("steve", op);
		introFor = m.id();
		introStartMs = System.currentTimeMillis();
		info = infoOf(m, "A");
		cinema = RankedCinema.of(m, "A", pos);
		introOn = true;
		saveView(mc);
		mc.options.hudHidden = true;
		if (cinema != null) {
			camOn = true;
			KuboApi.INSTANCE.cameraOverride(RankedFx::cameraPose);
		}
		mc.setScreen(new Stage(false));
		play(mc, "duel.intro");
		testDummyUntil = System.currentTimeMillis() + 17_000L;
	}

	/** Shows the victory (won) or defeat screen here. */
	public static void testEnd(MinecraftClient mc, boolean won) {
		if (mc.player == null) {
			return;
		}
		Ranked.Match m = testMatch(mc, "sword");
		startEnd(m, won ? "A" : "B");
	}

	private static long testDummyUntil;

	private static void startIntro(MinecraftClient mc, Ranked.Match m, float pos) {
		introFor = m.id();
		String local = m.team(Ranked.me());
		if (local == null || pos >= RankedCinema.GO) {
			return;
		}
		introStartMs = System.currentTimeMillis() - Math.round(pos * 1000);
		info = infoOf(m, local);
		cinema = RankedCinema.of(m, local);
		introOn = true;
		soundStarted = false;
		saveView(mc);
		mc.options.hudHidden = true;
		if (cinema != null && pos < RankedCinema.POV) {
			camOn = true;
			KuboApi.INSTANCE.cameraOverride(RankedFx::cameraPose);
		}
		mc.setScreen(new Stage(false));
		if (pos < 0.6F) {
			soundStarted = true;
			play(mc, "duel.intro");
		}
	}

	private static void saveView(MinecraftClient mc) {
		if (!savedView) {
			savedView = true;
			prevPerspective = mc.options.getPerspective();
			prevHudHidden = mc.options.hudHidden;
		}
	}

	private static void restoreView(MinecraftClient mc) {
		if (camOn) {
			stopCamera(mc, false);
		}
		if (savedView) {
			savedView = false;
			mc.options.setPerspective(prevPerspective == null ? Perspective.FIRST_PERSON : prevPerspective);
			mc.options.hudHidden = prevHudHidden;
		}
	}

	private static void stopCamera(MinecraftClient mc, boolean lookAtOpponent) {
		camOn = false;
		KuboApi.INSTANCE.cameraOverride(null);
		mc.options.setPerspective(Perspective.FIRST_PERSON);
		if (lookAtOpponent && cinema != null && mc.player != null) {
			Vec3d o = cinema.opponentHead();
			if (o != null) {
				Vec3d d = o.subtract(mc.player.getEyePos());
				mc.player.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
				mc.player.setPitch((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
			}
		}
	}

	/** Called every frame by the game's camera while the intro / victory camera is on. */
	private static double[] cameraPose() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (endFor != null && endCam != null) {
			double vt = (System.currentTimeMillis() - endShownAt) / 1000.0;
			RankedCinema.Shot s = RankedCinema.victory(endCam, endCamYaw, vt);
			double shake = .12 * Math.exp(-vt * 5);
			return RankedCinema.pose(s, shake, vt);
		}
		if (!introOn || cinema == null) {
			return null;
		}
		double t = introT();
		RankedCinema.Shot s = cinema.at(t);
		if (s == null) {
			return null;
		}
		// third person to see yourself, first person when the camera is about to enter your head
		boolean near = mc.player != null && s.p().distanceTo(mc.player.getEyePos()) < 2.2;
		Perspective want = near ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK;
		if (mc.options.getPerspective() != want) {
			mc.options.setPerspective(want);
		}
		return RankedCinema.pose(s, RankedCinema.fx(t)[2], t);
	}

	private static void play(MinecraftClient mc, String id) {
		try {
			SoundEvent ev = SoundEvent.of(Identifier.of("kubo_ext_practice", id));
			mc.getSoundManager().play(new PositionedSoundInstance(ev.id(), net.minecraft.sound.SoundCategory.MASTER, 1F, 1F,
					SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
		} catch (Throwable t) {
			System.err.println("[Sleep] duel sound " + id + ": " + t);
		}
	}

	// ------------------------------------------------------------------ the end
	private static Vec3d endCam;
	private static float endCamYaw;

	private static void startEnd(Ranked.Match m, String winner) {
		MinecraftClient mc = MinecraftClient.getInstance();
		endFor = m.id();
		endMatch = m;
		endWinner = winner;
		String local = m.team(Ranked.me());
		endNone = "none".equals(winner) || local == null;
		endWon = !endNone && winner.equals(local);
		endShownAt = System.currentTimeMillis();
		leaveAt = endShownAt + 14_000L;
		endSoundPlayed = false;
		if (introOn) {
			introOn = false;
			restoreView(mc);
		}
		if (local != null) {
			info = infoOf(m, local);
		}
		// the camera goes in front of the winner (when they're still in the world)
		endCam = null;
		if (!endNone && mc.world != null) {
			List<Ranked.Player> ws = "A".equals(winner) ? m.teamA() : m.teamB();
			for (PlayerEntity e : mc.world.getPlayers()) {
				if (!ws.isEmpty() && e.getName().getString().equalsIgnoreCase(ws.get(0).mcUsername())) {
					endCam = new Vec3d(e.getX(), e.getY(), e.getZ());
					endCamYaw = e.getBodyYaw();
				}
			}
		}
		saveView(mc);
		mc.options.hudHidden = true;
		if (endCam != null) {
			camOn = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			KuboApi.INSTANCE.cameraOverride(RankedFx::cameraPose);
		}
		if (info != null) {
			mc.setScreen(new Stage(true));
		}
	}

	/** LEAVE / ESC / after 14 s: back to the title screen (saving the world properly). */
	static void leave(MinecraftClient mc) {
		leaveAt = 0;
		Ranked.afterMatch();
		boolean inArena = mc.world != null && (mc.getServer() != null && Ranked.WORLD.equals(worldName(mc)) || mc.getServer() == null
				&& mc.getCurrentServerEntry() != null && dev.solclient.social.KuboRelay.isRelayAddress(mc.getCurrentServerEntry().address));
		if (endFor != null) {
			lastEnded = endFor; // never shown twice
		}
		endFor = null;
		endCam = null;
		restoreView(mc);
		if (mc.currentScreen instanceof Stage) {
			mc.setScreen(null);
		}
		if (inArena) {
			Ranked.quit(mc, true);
		}
	}

	/** The last match whose victory screen was shown (never shown twice). */
	private static String lastEnded;

	private static String worldName(MinecraftClient mc) {
		try {
			return mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString();
		} catch (Exception e) {
			return "";
		}
	}

	// ------------------------------------------------------------------ drawing
	/** HUD (when no screen of ours is open): the searching pill, and FIGHT! after the intro screen closed. */
	public static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		Ranked.Match m = Ranked.match;
		if (!"idle".equals(Ranked.queueState) && m == null) {
			long s = (System.currentTimeMillis() - Ranked.queuedAt) / 1000;
			String t = ("waiting_teammate".equals(Ranked.queueState) ? Lang.tr("Waiting for your teammate", "À espera do teu colega")
					: Lang.tr("Searching", "A procurar")) + " · Ranked " + Ranked.queueMode + " " + kitName(Ranked.queueKit) + "  " + s / 60 + ":" + String.format("%02d", s % 60);
			int tw = Gfx.tw(t) + 26, x = w / 2 - tw / 2;
			Gfx.rrect(c, x, 6, tw, 16, 8, 0xCC12141C);
			float ph = (System.currentTimeMillis() % 1000) / 1000F;
			Gfx.circle(c, x + 9, 14, 2.5F + (float) Math.sin(ph * Math.PI * 2) * 0.8F, 0xFFFF5A5A);
			Gfx.text(c, t, x + 16, 10, 0xFFFFFFFF);
		}
		if (introOn && !(mc.currentScreen instanceof Stage) && info != null) {
			DuelHud.frame(w, h);
			DuelHud.intro(c, info, introT());
		}
	}

	/** Our full-screen stage (intro until FIGHT!, then the victory screen): no background, the game keeps running. */
	static final class Stage extends Screen {
		private final boolean end;

		Stage(boolean end) {
			super(Text.literal(end ? "Victory" : "Duel"));
			this.end = end;
		}

		@Override
		public boolean shouldPause() {
			return false;
		}

		@Override
		public boolean shouldCloseOnEsc() {
			return false;
		}

		@Override
		public void renderBackground(DrawContext c, int mx, int my, float delta) {
			// the world stays visible
		}

		@Override
		public void render(DrawContext c, int mx, int my, float delta) {
			DuelHud.frame(width, height);
			if (end) {
				if (info == null) {
					return;
				}
				double vt = (System.currentTimeMillis() - endShownAt) / 1000.0;
				int delta2 = endMatch != null && endMatch.myChange() != 0 && endMatch.ranked() ? endMatch.myChange() : 0;
				Ranked.Match live = Ranked.match;
				if (live != null && live.id().equals(endMatch == null ? "" : endMatch.id()) && live.myChange() != 0 && live.ranked()) {
					delta2 = live.myChange();
				}
				DuelHud.bars(c, (float) (RankedCinema.out3(RankedCinema.seg(vt, 0, .6)) * 6));
				leaveBox = DuelHud.victory(c, info, endWinner == null ? "none" : endWinner, endWon, endNone, delta2, vt, mx, my);
				double flash = Math.min(.95, .9 * Math.exp(-vt * 4.5));
				DuelHud.flashFade(c, flash * .6, 0);
				return;
			}
			if (info == null) {
				return;
			}
			double t = introT();
			DuelHud.intro(c, info, t);
			double[] fx = RankedCinema.fx(t);
			DuelHud.flashFade(c, fx[0], fx[1]);
		}

		@Override
		public boolean mouseClicked(Click click, boolean doubled) {
			if (end && leaveBox != null && click.x() >= leaveBox[0] && click.x() < leaveBox[0] + leaveBox[2] && click.y() >= leaveBox[1]
					&& click.y() < leaveBox[1] + leaveBox[3]) {
				leave(MinecraftClient.getInstance());
				return true;
			}
			return true;
		}

		@Override
		public boolean keyPressed(KeyInput input) {
			if (end && input.key() == 256) {
				leave(MinecraftClient.getInstance());
			}
			return true;
		}
	}
}
