package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.fightcam.Capture;
import dev.sleepclient.fightcam.ClipToast;
import dev.sleepclient.fightcam.Ffmpeg;
import dev.sleepclient.fightcam.FightFilm;
import dev.sleepclient.fightcam.FilmRender;
import dev.sleepclient.fightcam.FightLooks;
import dev.sleepclient.fightcam.Recorder;
import dev.sleepclient.fightcam.Replay;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.SocialToast;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * FightCam: record your fights like a pro.
 * <ul>
 * <li><b>Recording</b>: smooth, sharp .mp4 videos using the hardware video encoder of your computer (Apple VideoToolbox
 * on Mac, NVENC / AMF / QuickSync on Windows) through ffmpeg. The picture is read from the GPU asynchronously, so the
 * game does not stutter. The same recorder keeps a rolling buffer for Clip Events (Medal-style clips).</li>
 * <li><b>Replay</b>: remembers the last minute of the world around you; open it and watch the fight again with a free
 * spectator camera (fly, slow-mo, follow a player, POV) and record it from any angle.</li>
 * <li><b>Camera</b>: a live fighting-game camera that frames you and the player you fight.</li>
 * </ul>
 * If ffmpeg cannot be used, the old built-in recorder (no ffmpeg, lower quality) is used instead.
 */
public class FightCamMod extends Mod {

	public static FightCamMod instance;

	private static final int[] HEIGHTS = {720, 1080, 1440, 4320};
	private static final int[] FPS = {30, 60};
	private static final String[] ENCODERS = {"auto", "h264_videotoolbox", "h264_nvenc", "h264_amf", "h264_qsv", "libx264"};

	private Option.Choice section;
	// recording
	private Option.Key recKey;
	private Option.Choice resolution, fps, encoderPick, source;
	private Option.Num bitrate;
	private Option.Text ffmpegPath, audioDevice;
	private Option.Bool showRec;
	private Option.Choice recPos;
	// replay
	private Option.Bool replayBuffer;
	private Option.Num replayLength, replayStart, replayRange;
	private Option.Key replayKey, filmKey, studioKey, markKey;
	private Option.Num fightRadius, fightQuiet;
	private boolean prevMarkKey;
	private long markStartTick = -1;
	private Option.Bool filmOffer, filmSlow;
	private Option.Choice look, sessionCam;
	private Option.Text lutPath;
	private boolean prevFilmKey, prevStudioKey;
	private int offerTick;
	private long offeredTick = -1;
	// camera
	private Option.Key camKey;
	private Option.Choice mode;
	private Option.Num distance, height, smooth, orbitSpeed, hold;
	private Option.Bool avoidWalls, camWithRec, auto;

	// camera state
	private boolean camOn, camByRec;
	private Perspective previous = Perspective.FIRST_PERSON;
	private Entity hit;
	private long hitMs;
	private long tickNs, lastPoseNs;
	private Vec3d pos, focus;
	private double sideSign = 1, orbit;
	private boolean prevRecKey, prevCamKey, prevReplayKey;

	// recording state (ffmpeg recorder)
	private boolean recording, saving;
	private long recStartMs;
	private boolean captureByRec;
	/** The recorder runs only for the Clip Events buffer, at the light quality (720p 30 fps). */
	private boolean lightRunning;
	// old built-in recorder (fallback)
	private Recorder legacy;
	private File legacyFile;
	private long nextCapture;
	private int failures;
	private int audioIdx = -1;
	private static volatile boolean detecting;

	public FightCamMod() {
		super("FightCam", "fightcam", "Pro recordings (hardware encoder, no lag), fight replays with a spectator camera, and a fight camera.",
				Category.UTILITY);
		instance = this;
		options.clear();
		section = tabs("Section", 0, "Recording", "Replay", "Camera");
		BooleanSupplier rec = () -> section.idx() == 0, rep = () -> section.idx() == 1, cam = () -> section.idx() == 2;

		// ------------------------------------------------------------- Recording
		header("Recorder").showIf(rec);
		action("Recorder", "Check / Set up", this::setup, FightCamMod::recorderStatus).showIf(rec);
		action("ffmpeg", "Install", this::installFfmpeg, () -> Ffmpeg.status).showIf(() -> rec.getAsBoolean() && !Ffmpeg.ready());
		ffmpegPath = text("ffmpeg path (optional)", "", 260, "found automatically");
		ffmpegPath.visibleIf = () -> rec.getAsBoolean() && !Ffmpeg.ready();
		header("Video").showIf(rec);
		resolution = choice("Video Resolution", 1, "720p", "1080p", "1440p", "Window size");
		fps = tabs("Video FPS", 1, "30 fps", "60 fps");
		bitrate = num("Bitrate", 16, 4, 80, 1, " Mbps");
		encoderPick = choice("Encoder", 0, "Auto (best)", "Apple VideoToolbox", "NVIDIA NVENC", "AMD AMF", "Intel QuickSync", "CPU (x264)");
		source = tabs("Record", 0, "Game + HUD", "World only");
		for (Option<?> o : new Option<?>[] {resolution, fps, bitrate, encoderPick, source}) {
			o.visibleIf = rec;
		}
		header("Look (shaders)").showIf(rec);
		look = choice("Video Look", 0, FightLooks.NAMES);
		look.tip("Applied after the video is saved, in a separate low-priority process: the game never lags and you do not need a shader pack.");
		lutPath = text("Custom LUT (.cube)", "", 260, "path to a .cube file");
		look.visibleIf = rec;
		lutPath.visibleIf = () -> rec.getAsBoolean() && look.idx() == FightLooks.NAMES.length - 1;
		audioDevice = text("Microphone", "", 120, "none");
		audioDevice.visibleIf = rec;
		action("Audio input", "Next", this::nextAudio, () -> audioDevice.get().isBlank() ? "No audio" : audioDevice.get()).showIf(rec);
		header("Manual recording").showIf(rec);
		recKey = key("Record Key", 298);
		recKey.visibleIf = rec;
		action("Recording", "Start / Stop", this::toggleRecording, this::recStatus).showIf(rec);
		action("Recordings", "Open Folder", this::openFolder, () -> "kubo-recordings").showIf(rec);
		showRec = bool("Show REC Indicator", true);
		recPos = choice("REC Position", 1, "Top Left", "Top Right", "Bottom Left", "Bottom Right");
		showRec.visibleIf = rec;
		recPos.visibleIf = () -> rec.getAsBoolean() && showRec.on();

		// ---------------------------------------------------------------- Replay
		header("Fight replay").showIf(rep);
		replayBuffer = bool("Remember for replays", true);
		replayBuffer.visibleIf = rep;
		replayLength = num("Replay length", 60, 15, 180, 5, " s");
		replayStart = num("Open at", 15, 3, 60, 1, " s before now");
		replayRange = num("Players within", 96, 32, 160, 8, " blocks");
		replayKey = key("Open Replay Key", 296);
		for (Option<?> o : new Option<?>[] {replayLength, replayStart, replayRange, replayKey}) {
			o.visibleIf = () -> rep.getAsBoolean() && replayBuffer.on();
		}
		header("Fight film (automatic)").showIf(rep);
		filmKey = key("Fight Film Key", 74);
		studioKey = key("Film Studio Key", 75);
		markKey = key("Fight Start / Stop Key", -1);
		markKey.tip("Press once when the fight starts and again when it ends: the film is made of exactly that stretch.");
		fightRadius = num("Fight Radius", 40, 8, 200, 4, " blocks");
		fightRadius.tip("The fight is over only when nobody is closer than this (arenas can be big).");
		fightQuiet = num("Fight Over After No Hits", 10, 3, 60, 1, " s");
		fightQuiet.tip("How long without any hit before the \"Fight over\" message.");
		filmOffer = bool("Offer a film after each fight", true);
		filmSlow = bool("Slow motion on the kill", true);
		sessionCam = choice("Zero-lag recording camera", 0, "My view (first person)", "Chase (behind me)");
		for (Option<?> o : new Option<?>[] {filmKey, studioKey, markKey, fightRadius, fightQuiet, filmOffer, filmSlow, sessionCam}) {
			o.visibleIf = () -> rep.getAsBoolean() && replayBuffer.on();
		}
		action("Film Studio", "Open", this::openStudio, () -> "start / stop, make a film, watch your videos")
				.showIf(() -> rep.getAsBoolean() || rec.getAsBoolean());
		action("Fight film", "Make film now", this::openFilm, () -> "last fight, cinematic camera, saved as .mp4")
				.showIf(() -> rep.getAsBoolean() && replayBuffer.on());
		action("Replay", "Open replay", this::openReplay, () -> String.format("%.0f s remembered", Replay.seconds()))
				.showIf(() -> rep.getAsBoolean() && replayBuffer.on());

		// ---------------------------------------------------------------- Camera
		header("Fight camera").showIf(cam);
		camKey = key("Camera Key", 299);
		mode = choice("Camera Mode", 3, "Fight Side", "Orbit", "Behind You", "Cinematic (Auto Director)");
		distance = num("Distance", 100, 50, 200, 5, "%");
		height = num("Height", 1.2F, 0, 6, 0.1F, " blocks");
		smooth = num("Smoothness", 60, 0, 95, 5, "%");
		orbitSpeed = num("Orbit Speed", 20, 5, 90, 5, "°/s");
		avoidWalls = bool("Avoid Walls", true);
		camWithRec = bool("Camera With Recording", true).tip("The video is filmed by the camera, not by your own eyes.");
		auto = bool("Auto Target Nearest Player", true);
		hold = num("Keep Target", 8, 2, 30, 1, " s");
		for (Option<?> o : new Option<?>[] {camKey, mode, distance, height, smooth, avoidWalls, camWithRec, auto, hold}) {
			o.visibleIf = cam;
		}
		orbitSpeed.visibleIf = () -> cam.getAsBoolean() && mode.idx() == 1;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		detectAsync(false);
	}

	@Override
	protected void onDisable() {
		if (recording) {
			stopRecording();
		}
		if (legacy != null) {
			stopLegacy(false);
		}
		setCam(false);
		Replay.recordingEnabled = false;
		captureMaybeStop();
	}

	public void onAttack(Entity e) {
		if (e instanceof LivingEntity) {
			hit = e;
			hitMs = System.currentTimeMillis();
		}
	}

	// ------------------------------------------------------------------ recorder setup
	private static String recorderStatus() {
		if (detecting) {
			return "Checking...";
		}
		if (Ffmpeg.ready()) {
			return Capture.running() ? Capture.status : Ffmpeg.encoder.label();
		}
		return Ffmpeg.status;
	}

	private String preferredEncoder() {
		return ENCODERS[Math.max(0, Math.min(ENCODERS.length - 1, encoderPick.idx()))];
	}

	/** Finds ffmpeg + the best encoder in the background. */
	public void detectAsync(boolean toast) {
		if (detecting) {
			return;
		}
		detecting = true;
		String custom = ffmpegPath.get(), pref = preferredEncoder();
		int br = bitrate.i();
		Thread t = new Thread(() -> {
			try {
				boolean ok = Ffmpeg.detect(custom, pref, br);
				if (toast) {
					ClipToast.show(ok ? "Recorder ready" : "Recorder not ready", ok ? Ffmpeg.encoder.label() : Ffmpeg.status, null, !ok);
				}
				if (ok && audioDevice.get().isBlank() && !Ffmpeg.audioDevices.isEmpty()) {
					// nothing picked yet: leave it empty (no audio) - the user chooses with "Next"
				}
			} finally {
				detecting = false;
			}
		}, "Sleep-Ffmpeg-Detect");
		t.setDaemon(true);
		t.start();
	}

	private void setup() {
		if (Capture.running()) {
			Capture.stop();
		}
		detectAsync(true);
	}

	private void installFfmpeg() {
		if (Ffmpeg.busy) {
			return;
		}
		Thread t = new Thread(() -> {
			if (Ffmpeg.install()) {
				mc.execute(() -> detectAsync(true));
			} else {
				ClipToast.show("ffmpeg not installed", Ffmpeg.status, null, true);
			}
		}, "Sleep-Ffmpeg-Install");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Clip Events "Record my voice": on = when no input is picked yet, pick the microphone (a device called microphone /
	 * built-in / headset first); turned off = no audio. The running recorder restarts with the new input.
	 */
	public void syncMic(boolean want, boolean changed) {
		List<String> devs = Ffmpeg.audioDevices;
		String cur = audioDevice.get(), next = cur;
		if (want && cur.isBlank() && !devs.isEmpty()) {
			next = devs.get(0);
			for (String d : devs) {
				String l = d.toLowerCase(java.util.Locale.ROOT);
				if (l.contains("micro") || l.contains("built-in") || l.contains("macbook") || l.contains("headset") || l.contains("airpods")) {
					next = d;
					break;
				}
			}
		} else if (!want && changed) {
			next = "";
		}
		if (next.equals(cur)) {
			return;
		}
		audioDevice.set(next);
		audioIdx = devs.indexOf(next);
		dev.sleepclient.Config.save();
		if (Capture.running() && !recording && !saving) {
			Capture.stop(); // restarts with the new input
			mc.execute(this::captureMaybeStart);
		}
	}

	private void nextAudio() {
		List<String> devs = Ffmpeg.audioDevices;
		if (devs.isEmpty()) {
			audioDevice.set("");
			return;
		}
		audioIdx = audioIdx + 1;
		if (audioIdx >= devs.size()) {
			audioIdx = -1;
			audioDevice.set("");
		} else {
			audioDevice.set(devs.get(audioIdx));
		}
		if (Capture.running()) {
			Capture.stop(); // restarts with the new input when needed
			mc.execute(this::captureMaybeStart);
		}
	}

	/** Seconds the rolling buffer has to keep (the longest clip someone may ask for). */
	private int keepSeconds() {
		int k = 30;
		if (ClipEventsMod.instance != null) {
			k = Math.max(k, ClipEventsMod.instance.neededSeconds());
		}
		return k;
	}

	/** Starts the ffmpeg recorder with the current settings (render thread). */
	public boolean startCapture() {
		return startCapture(false);
	}

	/**
	 * light = only the Clip Events rolling buffer needs it: 720p, 30 fps and at most 8 Mbps, so recording in the background
	 * all the time costs a lot less (a manual recording switches to the full quality).
	 */
	public boolean startCapture(boolean light) {
		if (Capture.running()) {
			return true;
		}
		if (!Ffmpeg.ready() || mc.world == null) {
			return false;
		}
		// the encoder's bitrate follows the option
		String id = Ffmpeg.encoder.id();
		for (Ffmpeg.Encoder e : Ffmpeg.known(bitrate.i())) {
			if (e.id().equals(id)) {
				Ffmpeg.encoder = e;
			}
		}
		Capture.Settings s = light
				? new Capture.Settings(30, Math.min(720, HEIGHTS[resolution.idx()]), Math.min(8, bitrate.i()), audioDevice.get(), source.idx() == 0, keepSeconds())
				: new Capture.Settings(FPS[fps.idx()], HEIGHTS[resolution.idx()], bitrate.i(), audioDevice.get(), source.idx() == 0, keepSeconds());
		boolean ok = Capture.start(s);
		lightRunning = ok && light;
		return ok;
	}

	/** Starts the recorder if Clip Events wants the rolling buffer. */
	public void captureMaybeStart() {
		if (ClipEventsMod.instance != null && ClipEventsMod.instance.wantsBuffer()) {
			startCapture(ClipEventsMod.instance.lightBuffer());
		}
	}

	/** Stops the recorder if nothing needs it any more. */
	public void captureMaybeStop() {
		boolean need = recording || saving || (ClipEventsMod.instance != null && ClipEventsMod.instance.wantsBuffer());
		if (!need && Capture.running()) {
			Capture.stop();
		}
	}

	// ------------------------------------------------------------------ ticking
	@Override
	public void tick() {
		tickNs = System.nanoTime();
		Replay.configure(replayLength.i(), replayRange.f());
		Replay.recordingEnabled = replayBuffer.on();
		Replay.tick();
		FightLooks.chain = FightLooks.chainFor(look.idx(), lutPath.get());
		if (mc.player == null || mc.world == null) {
			if (recording) {
				stopRecording();
			}
			if (legacy != null) {
				stopLegacy(false);
			}
			setCam(false);
			return;
		}
		boolean r = mc.currentScreen == null && Keys.down(recKey.code());
		boolean c = mc.currentScreen == null && Keys.down(camKey.code());
		boolean rp = mc.currentScreen == null && Keys.down(replayKey.code());
		if (r && !prevRecKey) {
			toggleRecording();
		}
		if (c && !prevCamKey) {
			camByRec = false;
			setCam(!camOn);
		}
		if (rp && !prevReplayKey && replayBuffer.on()) {
			openReplay();
		}
		prevRecKey = r;
		prevCamKey = c;
		prevReplayKey = rp;
		boolean fk = mc.currentScreen == null && Keys.down(filmKey.code());
		if (fk && !prevFilmKey && replayBuffer.on()) {
			openFilm();
		}
		prevFilmKey = fk;
		boolean sk = mc.currentScreen == null && Keys.down(studioKey.code());
		if (sk && !prevStudioKey) {
			openStudio();
		}
		prevStudioKey = sk;
		boolean mk = mc.currentScreen == null && Keys.down(markKey.code());
		if (mk && !prevMarkKey && replayBuffer.on()) {
			if (markStartTick < 0) {
				markStartTick = Replay.currentTick();
				ClipToast.show("Fight started", "Press the same key when it ends: the film is made of that stretch", null, false);
			} else {
				long st = markStartTick;
				markStartTick = -1;
				openFilmRange(st, Replay.currentTick());
			}
		}
		prevMarkKey = mk;
		if (filmOffer.on() && replayBuffer.on() && !Replay.active() && mc.currentScreen == null && ++offerTick % 40 == 0) {
			Replay.Frame[] fr = Replay.framesCopy();
			FightFilm.Fight f = FightFilm.analyze(fr, Math.max(0, fr.length - 400));
			if (f != null && f.tick != offeredTick && markStartTick < 0 && FightFilm.finished(f, fr.length, fightQuiet.i() * 20) && !playersNear(fightRadius.f())) { // still next to somebody = still fighting
				offeredTick = f.tick;
				ClipToast.show("Fight over", "Press the Fight Film key (J): cinematic film of it, " + (int) f.seconds() + " s", null, false);
			}
		}
		if (camOn && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK); // the camera needs to see you
		}
	}

	private void setCam(boolean on) {
		if (on == camOn) {
			return;
		}
		camOn = on;
		pos = null;
		focus = null;
		lastPoseNs = 0;
		if (on) {
			previous = mc.options.getPerspective();
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else {
			mc.options.setPerspective(previous);
		}
	}

	/** Fight Film: finds the last fight in the buffer, writes a cinematic camera for it and records it to an .mp4 by itself. */
	public void openFilm() {
		if (mc.world == null || Replay.active()) {
			return;
		}
		if (!Ffmpeg.ready()) {
			ClipToast.show("Set up the recorder first", "FightCam > Recording > Recorder", null, true);
			return;
		}
		if (camOn) {
			setCam(false);
		}
		String err = Replay.start(0);
		if (err != null) {
			ClipToast.show("No film", err, null, true);
			return;
		}
		Replay.Frame[] fr = Replay.playFrames();
		FightFilm.Fight f = FightFilm.analyze(fr, 0, false);
		if (f == null) {
			Replay.stop();
			ClipToast.show("No fight found", "No hits and nobody close together in the last " + (int) Replay.seconds() + " s", null, true);
			return;
		}
		FightFilm.build(f, fr, filmSlow.on());
		dev.sleepclient.fightcam.FilmEdit.last = FightFilm.makeInfo(f);
		Replay.camMode = Replay.PATH;
		Replay.seek(f.start);
		Replay.stopAt = f.end;
		Replay.playing = false;
		mc.setScreen(new dev.sleepclient.gui.ReplayScreen(true));
	}

	/** The Film Studio: start / stop recording, make the fight film, watch the videos. */
	public void openStudio() {
		if (mc.world == null) {
			return;
		}
		mc.setScreen(new dev.sleepclient.gui.FilmStudioScreen(null));
	}

	public boolean sessionActive() {
		return Replay.sessionTick >= 0;
	}

	public double sessionSeconds() {
		return Replay.sessionTick < 0 ? 0 : (Replay.currentTick() - Replay.sessionTick) / 20.0;
	}

	/** Zero-lag recording: only the replay data is kept while you play (it is already being remembered); the video is rendered after. */
	public void startSession() {
		if (mc.world == null) {
			return;
		}
		if (!replayBuffer.on()) {
			ClipToast.show("Turn on \"Remember for replays\"", "FightCam > Replay", null, true);
			return;
		}
		Replay.sessionTick = Replay.currentTick();
		ClipToast.show("Recording (no lag)", "Play normally. Stop it in the Film Studio and the video is rendered.", null, false);
	}

	/** Stops the session and renders it to an .mp4 frame by frame (smooth, nothing skipped). */
	public void stopSession() {
		long st = Replay.sessionTick;
		Replay.sessionTick = -1;
		if (st < 0 || mc.world == null || Replay.active()) {
			return;
		}
		if (!Ffmpeg.ready()) {
			ClipToast.show("Set up the recorder first", "FightCam > Recording > Recorder", null, true);
			return;
		}
		String err = Replay.start(0);
		if (err != null) {
			ClipToast.show("Nothing to render", err, null, true);
			return;
		}
		Replay.Frame[] fr = Replay.playFrames();
		int from = Replay.indexOfTick(fr, st);
		if (fr.length - from < 40) {
			Replay.stop();
			ClipToast.show("Too short", "Record at least 2 seconds", null, true);
			return;
		}
		dev.sleepclient.fightcam.FilmEdit.Info sessionInfo = new dev.sleepclient.fightcam.FilmEdit.Info();
		sessionInfo.startFrame = from;
		sessionInfo.seconds = (fr.length - 1 - from) / 20.0;
		dev.sleepclient.fightcam.FilmEdit.last = sessionInfo;
		java.util.UUID me = mc.player.getUuid();
		if (sessionCam.idx() == 1) {
			FightFilm.buildChase(fr, me, from, fr.length - 1);
		}
		if (sessionCam.idx() == 1 && Replay.KEYS.size() >= 2) {
			Replay.camMode = Replay.PATH;
		} else {
			Replay.KEYS.clear();
			Replay.camMode = Replay.POV;
			Replay.target = me;
		}
		Replay.seek(from);
		Replay.stopAt = fr.length - 1;
		Replay.playing = false;
		mc.setScreen(new dev.sleepclient.gui.ReplayScreen(true));
	}

	public boolean isRecording() {
		return recording || legacy != null;
	}

	/** Starts the frame-exact film render into 'out' with the chosen quality and look. Returns an error text or null. */
	public String startFilm(java.io.File out) {
		return FilmRender.start(out, FPS[fps.idx()], HEIGHTS[resolution.idx()], FightLooks.chain);
	}

	/** Film of a stretch you marked with the start / stop key. */
	public void openFilmRange(long startTick, long endTick) {
		if (mc.world == null || Replay.active()) {
			return;
		}
		if (!Ffmpeg.ready()) {
			ClipToast.show("Set up the recorder first", "FightCam > Recording > Recorder", null, true);
			return;
		}
		String err = Replay.start(0);
		if (err != null) {
			ClipToast.show("No film", err, null, true);
			return;
		}
		Replay.Frame[] fr = Replay.playFrames();
		int s = Replay.indexOfTick(fr, startTick), e = Replay.indexOfTick(fr, endTick);
		if (e - s < 40) {
			Replay.stop();
			ClipToast.show("Too short", "The marked fight is shorter than 2 seconds", null, true);
			return;
		}
		FightFilm.Fight f = FightFilm.analyze(fr, s, false);
		if (f == null || f.hits == 0) {
			f = FightFilm.manual(fr, s, e, mc.player.getUuid(), fightRadius.f());
		}
		if (f == null) {
			Replay.stop();
			ClipToast.show("No players", "Nobody was around in that stretch", null, true);
			return;
		}
		f.start = s;
		f.end = Math.min(fr.length - 1, e);
		FightFilm.build(f, fr, filmSlow.on());
		dev.sleepclient.fightcam.FilmEdit.last = FightFilm.makeInfo(f);
		Replay.camMode = Replay.PATH;
		Replay.seek(f.start);
		Replay.stopAt = f.end;
		Replay.playing = false;
		mc.setScreen(new dev.sleepclient.gui.ReplayScreen(true));
	}

	/** Another player within r blocks of you right now. */
	private boolean playersNear(double r) {
		for (var p : mc.world.getPlayers()) {
			if (p != mc.player && !p.isSpectator() && p.squaredDistanceTo(mc.player) < r * r) {
				return true;
			}
		}
		return false;
	}

	public void openReplay() {
		if (mc.world == null || Replay.active()) {
			return;
		}
		if (camOn) {
			setCam(false);
		}
		String err = Replay.start(replayStart.f());
		if (err != null) {
			ClipToast.show("No replay", err, null, true);
			return;
		}
		mc.setScreen(new dev.sleepclient.gui.ReplayScreen());
	}

	// ------------------------------------------------------------------ manual recording
	public void toggleRecording() {
		if (recording || legacy != null) {
			if (legacy != null) {
				stopLegacy(true);
			} else {
				stopRecording();
			}
			return;
		}
		if (saving || mc.world == null) {
			return;
		}
		if (Ffmpeg.ready()) {
			if (Capture.running() && lightRunning) {
				// the background buffer runs at the light quality: restart the recorder at the full quality first
				lightRunning = false;
				Capture.stop();
				mc.execute(this::toggleRecording);
				return;
			}
			boolean was = Capture.running();
			if (!startCapture()) {
				ClipToast.show("Can't record", Capture.status, null, true);
				return;
			}
			captureByRec = !was;
			Capture.beginRecording();
			recording = true;
			recStartMs = System.currentTimeMillis();
			if (camWithRec.on() && !camOn) {
				camByRec = true;
				setCam(true);
			}
			ClipToast.show("Recording", Ffmpeg.encoder.label(), null, false);
		} else {
			startLegacy();
		}
	}

	private void stopRecording() {
		if (!recording) {
			return;
		}
		recording = false;
		saving = true;
		long secs = (System.currentTimeMillis() - recStartMs) / 1000;
		if (camByRec) {
			camByRec = false;
			setCam(false);
		}
		File f = new File(Capture.outputDir(), "FightCam_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".mp4");
		Capture.endRecording(f, err -> {
			saving = false;
			if (err == null) {
				ClipToast.show("Recording saved", f.getName() + " (" + secs / 60 + ":" + String.format("%02d", secs % 60) + ")",
						Capture.thumbnail(ClipToast.thumbW(), ClipToast.thumbH()), false);
				if (mc.player != null) {
					mc.player.sendMessage(Text.literal("§aFightCam saved: §f" + f.getAbsolutePath()), false);
				}
			} else {
				ClipToast.show("Recording failed", err, null, true);
			}
			if (captureByRec) {
				captureByRec = false;
				captureMaybeStop();
			}
			// the recording ran at full quality: give the Clip Events buffer its light quality back
			mc.execute(() -> {
				if (!recording && !saving && Capture.running() && !lightRunning && ClipEventsMod.instance != null
						&& ClipEventsMod.instance.wantsBuffer() && ClipEventsMod.instance.lightBuffer()) {
					Capture.stop();
					mc.execute(this::captureMaybeStart);
				}
			});
		});
	}

	/** Finishes a recording that is still running (the game is closing). */
	public static void shutdown() {
		if (instance != null && instance.legacy != null) {
			instance.stopLegacy(false);
		}
		Capture.stop();
	}

	private String recStatus() {
		if (recording) {
			long s = (System.currentTimeMillis() - recStartMs) / 1000;
			return "Recording " + s / 60 + ":" + String.format("%02d", s % 60);
		}
		if (legacy != null) {
			return "Recording (basic recorder)";
		}
		return saving ? "Saving..." : "Key " + Keys.name(recKey.code());
	}

	private void openFolder() {
		net.minecraft.util.Util.getOperatingSystem().open(Capture.outputDir());
	}

	/** The REC indicator. It is drawn after the picture for the video was taken (with "World only"). */
	public void drawRec(DrawContext c) {
		if (!(recording || legacy != null) || !showRec.on()) {
			return;
		}
		long now = System.currentTimeMillis();
		long s = (now - recStartMs) / 1000;
		String t = "REC " + s / 60 + ":" + String.format("%02d", s % 60);
		int w = Gfx.tw(t) + 26, h = 18;
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		int p = recPos.idx();
		int x = p == 0 || p == 2 ? 6 : sw - w - 6;
		int y = p == 0 || p == 1 ? 6 : sh - h - 6;
		Gfx.rrect(c, x, y, w, h, 6, Gfx.withAlpha(0x121317, 205));
		boolean on = (now / 500) % 2 == 0;
		Gfx.circle(c, x + 10, y + h / 2F, 4, Gfx.withAlpha(0xE5484D, on ? 255 : 90));
		Gfx.text(c, t, x + 19, y + 5, 0xFFF1F3F8);
	}

	// ------------------------------------------------------------------ old built-in recorder (no ffmpeg)
	@Override
	public void frame() {
		Recorder r = legacy;
		if (r == null || mc.world == null) {
			return;
		}
		if (r.isFull() || r.error() != null) {
			stopLegacy(true);
			return;
		}
		long now = System.nanoTime();
		if (now < nextCapture) {
			return;
		}
		nextCapture = Math.max(nextCapture + 1_000_000_000L / 30, now);
		try {
			ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> r.submit(new Shot(image), now));
			failures = 0;
		} catch (RuntimeException e) {
			if (++failures > 5) {
				stopLegacy(true);
			}
		}
	}

	private void startLegacy() {
		int fw = mc.getWindow().getFramebufferWidth(), fh = mc.getWindow().getFramebufferHeight();
		int oh = Math.min(fh, 720);
		int ow = Math.max(64, Math.round((float) fw * oh / fh) & ~1);
		oh = Math.max(64, oh & ~1);
		File f = new File(Capture.outputDir(), "FightCam_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".mp4");
		int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2 - 1));
		try {
			legacy = new Recorder(f, ow, oh, 30, 24, workers);
		} catch (IOException | RuntimeException e) {
			legacy = null;
			SocialToast.show("FightCam", "Could not start: " + e.getMessage());
			return;
		}
		legacyFile = f;
		recStartMs = System.currentTimeMillis();
		nextCapture = 0;
		ClipToast.show("Recording (basic)", "Install ffmpeg in FightCam for pro quality", null, false);
	}

	private void stopLegacy(boolean async) {
		Recorder r = legacy;
		if (r == null) {
			return;
		}
		legacy = null;
		File f = legacyFile;
		long now = System.nanoTime();
		Runnable work = () -> {
			r.stop(now);
			ClipToast.show(r.error() == null ? "Recording saved" : "Recording failed", r.error() == null ? f.getName() : r.error(), null,
					r.error() != null);
		};
		if (async) {
			Thread t = new Thread(work, "Sleep-FightCam-Stop");
			t.setDaemon(true);
			t.start();
		} else {
			work.run();
		}
	}

	// ------------------------------------------------------------------ camera
	public static boolean camActive() {
		return instance != null && instance.enabled && instance.camOn && instance.mc.player != null && instance.mc.world != null
				&& !FreelookMod.active;
	}

	private Vec3d centre(Entity e, float frac) {
		return e.getLerpedPos(frac).add(0, e.getHeight() * 0.55, 0);
	}

	private Entity findTarget() {
		long now = System.currentTimeMillis();
		Entity h = hit;
		if (h != null && h.isAlive() && !h.isRemoved() && now - hitMs < hold.f() * 1000 && mc.player.distanceTo(h) < 40) {
			return h;
		}
		if (!auto.on()) {
			return null;
		}
		Entity best = null;
		double bd = 16 * 16;
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player || p.isSpectator() || !p.isAlive()) {
				continue;
			}
			double d = mc.player.squaredDistanceTo(p);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** @return x, y, z, yaw, pitch of the camera for this frame */
	public static double[] pose() {
		FightCamMod m = instance;
		return m == null ? null : m.computePose();
	}

	private int shot = -1;
	private long shotStartMs, shotLenMs;
	private double shotAng;
	private Vec3d lastSubj, moveDir;

	private double[] computePose() {
		PlayerEntity me = mc.player;
		long now = System.nanoTime();
		double dt = lastPoseNs == 0 ? 0.016 : Math.min(0.25, (now - lastPoseNs) / 1e9);
		lastPoseNs = now;
		float frac = (float) Math.max(0, Math.min(1, (now - tickNs) / 5e7));
		double distMul = distance.f() / 100.0;
		double tau = 0.02 + smooth.f() / 100.0 * 0.55;
		Entity t = mode.idx() == 2 ? null : findTarget();
		boolean director = mode.idx() == 3;
		Vec3d wantPos, wantFocus;
		double yaw, pitch;
		boolean lookAtFocus = true;
		Vec3d origin; // where walls are tested from
		if (director) {
			Vec3d c0 = centre(me, frac);
			Vec3d subj = t == null ? c0 : c0.add(centre(t, frac)).multiply(0.5);
			double spread = t == null ? 0 : Math.min(10, c0.distanceTo(centre(t, frac)));
			long ms = System.currentTimeMillis();
			if (shot < 0 || ms - shotStartMs > shotLenMs) {
				int next;
				do {
					next = java.util.concurrent.ThreadLocalRandom.current().nextInt(6);
				} while (next == shot);
				shot = next;
				shotStartMs = ms;
				shotLenMs = 4200 + java.util.concurrent.ThreadLocalRandom.current().nextInt(2800);
				shotAng = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
				pos = null; // a hard cut, like in an edit
			}
			double st = (ms - shotStartMs) / 1000.0;
			double base = (5 + spread) * distMul;
			double yr = Math.toRadians(me.getYaw());
			Vec3d fwd = new Vec3d(-Math.sin(yr), 0, Math.cos(yr)), right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
			Vec3d mv = Vec3d.ZERO;
			if (lastSubj != null) {
				Vec3d d0 = subj.subtract(lastSubj);
				d0 = new Vec3d(d0.x, 0, d0.z);
				if (d0.length() > 0.01) {
					mv = d0.normalize();
					moveDir = moveDir == null ? mv : moveDir.multiply(0.9).add(mv.multiply(0.1));
				}
			}
			lastSubj = subj;
			Vec3d dir = moveDir != null && moveDir.length() > 0.2 ? moveDir.normalize() : fwd;
			origin = subj;
			wantFocus = subj.add(0, 0.1, 0);
			switch (shot) {
				case 0 -> { // wide orbit
					double a = shotAng + st * 0.38;
					wantPos = subj.add(Math.cos(a) * base * 1.25, 2.0 + height.f() * 0.6, Math.sin(a) * base * 1.25);
				}
				case 1 -> { // low hero angle, looking up at them
					wantPos = new Vec3d(subj.x, subj.y - 0.95, subj.z).subtract(dir.multiply(base * 0.7)).add(right.multiply(base * 0.45));
					wantFocus = subj.add(0, 0.45, 0);
				}
				case 2 -> { // over the shoulder, looking where they look
					Vec3d eye = me.getLerpedPos(frac).add(0, me.getHeight() * 0.95, 0);
					wantPos = eye.subtract(fwd.multiply(2.3)).add(right.multiply(0.75)).add(0, 0.45, 0);
					wantFocus = t == null ? eye.add(fwd.multiply(7)) : centre(t, frac);
				}
				case 3 -> { // drone: rises and circles
					double a = shotAng + st * 0.28;
					double h = Math.min(11, 2.2 + st * 1.7);
					wantPos = subj.add(Math.cos(a) * base * 0.9, h, Math.sin(a) * base * 0.9);
				}
				case 4 -> { // slow push in
					double dd = Math.max(2.7, base * 1.7 - st * 0.95);
					wantPos = subj.add(Math.cos(shotAng) * dd, 1.1 + height.f() * 0.3, Math.sin(shotAng) * dd);
				}
				default -> { // tracking from the side, a bit ahead of them
					Vec3d side = new Vec3d(-dir.z, 0, dir.x);
					wantPos = subj.add(dir.multiply(1.6 + st * 0.15)).add(side.multiply(base * 0.95)).add(0, 1.0 + height.f() * 0.3, 0);
				}
			}
			yaw = 0;
			pitch = 0;
		} else if (t == null) {
			// behind you: the view follows your mouse, so you can still aim
			float yw = me.getYaw(), pt = me.getPitch();
			Vec3d look = Vec3d.fromPolar(pt, yw);
			Vec3d eye = me.getLerpedPos(frac).add(0, me.getHeight() * 0.9, 0);
			double yr = Math.toRadians(yw);
			Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
			double d = 3.2 * distMul;
			origin = eye;
			wantPos = eye.subtract(look.multiply(d)).add(right.multiply(0.6)).add(0, 0.3, 0);
			wantFocus = eye;
			lookAtFocus = false;
			yaw = yw;
			pitch = pt;
		} else {
			Vec3d p = centre(me, frac), q = centre(t, frac);
			Vec3d mid = p.add(q).multiply(0.5);
			double dx = q.x - p.x, dz = q.z - p.z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1e-3) {
				double yr = Math.toRadians(me.getYaw());
				dx = -Math.sin(yr);
				dz = Math.cos(yr);
				len = 1;
			}
			double nx = -dz / len, nz = dx / len;
			double d = Math.max(3, Math.min(30, 2.2 + len * 1.1)) * distMul;
			if (mode.idx() == 1) {
				orbit += Math.toRadians(orbitSpeed.f()) * dt;
				nx = Math.cos(orbit);
				nz = Math.sin(orbit);
			} else if (pos != null) {
				// stay on the same side of the fight, even when the two of you swap places
				double side = (pos.x - mid.x) * nx + (pos.z - mid.z) * nz;
				sideSign = side >= 0 ? 1 : -1;
			} else {
				sideSign = 1;
			}
			double sgn = mode.idx() == 1 ? 1 : sideSign;
			origin = mid;
			wantPos = mid.add(nx * d * sgn, height.f(), nz * d * sgn);
			wantFocus = mid.add(0, 0.1, 0);
			yaw = 0;
			pitch = 0;
		}
		if (avoidWalls.on()) {
			try {
				BlockHitResult hr = mc.world.raycast(new RaycastContext(origin, wantPos, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, me));
				if (hr != null && hr.getType() != HitResult.Type.MISS) {
					Vec3d dir = wantPos.subtract(origin);
					double l = dir.length();
					Vec3d back = l > 1e-4 ? dir.multiply(0.3 / l) : Vec3d.ZERO;
					wantPos = hr.getPos().subtract(back);
				}
			} catch (RuntimeException ignored) {
				// keep the wanted position
			}
		}
		double k = t == null ? 1 - Math.exp(-dt / Math.max(0.02, tau * 0.4)) : 1 - Math.exp(-dt / tau);
		if (pos == null) {
			pos = wantPos;
			focus = wantFocus;
		} else {
			pos = pos.add(wantPos.subtract(pos).multiply(k));
			focus = focus.add(wantFocus.subtract(focus).multiply(k));
		}
		if (lookAtFocus) {
			Vec3d v = focus.subtract(pos);
			double hz = Math.sqrt(v.x * v.x + v.z * v.z);
			yaw = Math.toDegrees(Math.atan2(-v.x, v.z));
			pitch = -Math.toDegrees(Math.atan2(v.y, hz));
		}
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
	}

	// ------------------------------------------------------------------ capture
	private static final class Shot implements Recorder.Source {
		private final NativeImage img;

		Shot(NativeImage img) {
			this.img = img;
		}

		@Override
		public int width() {
			return img.getWidth();
		}

		@Override
		public int height() {
			return img.getHeight();
		}

		@Override
		public int argb(int x, int y) {
			return img.getColorArgb(x, y);
		}

		@Override
		public void release() {
			img.close();
		}
	}
}
