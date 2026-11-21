package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.fightcam.Capture;
import dev.sleepclient.fightcam.ClipToast;
import dev.sleepclient.fightcam.Ffmpeg;
import dev.sleepclient.fightcam.VoiceTrigger;
import dev.sleepclient.option.Option;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Clip Events: Medal-style clips. The recorder keeps the last minute or so of video in the background; when something
 * happens it saves the last N seconds as an .mp4 and shows "Clip saved".
 * <ul>
 * <li>Hotkey (F8 by default).</li>
 * <li>Your voice: say your phrase ("Jarvis, clipa isto") - recognised offline on this computer.</li>
 * <li>Automatically: kills, Hypixel final kills / bed breaks, clutches (MLG water, hay, bed, cobweb, slime/honey, boat,
 * mounts, ladders/vines), your deaths.</li>
 * </ul>
 * Uses the FightCam recorder settings (quality, encoder, microphone).
 */
public class ClipEventsMod extends Mod {

	public static ClipEventsMod instance;

	private final Option.Choice section;
	// clips
	private final Option.Bool recordClips, notify;
	/** One-time switch: the always-on background recorder is off unless you turn it on (it made the game stutter, most on Mac). */
	private Option.Bool recorderOff;
	private final Option.Choice bufferQuality;
	private final Option.Num clipLength, after, cooldown;
	private final Option.Key clipKey;
	// voice
	private final Option.Bool voice;
	private final Option.Text phrase;
	private final Option.Choice voiceLang, voiceEngine;
	private final Option.Bool myVoice;
	private int engineStarted = -1;
	private boolean myVoicePrev = true;
	private final Option.Num strict;
	// events
	private final Option.Bool multiKills, lowHp;
	private final Option.Bool kills, hypixel, deaths, clutches, water, hay, bed, cobweb, slime, boat, mount, ladder;
	private final Option.Num minFall;

	private boolean prevKey, wasDead, voiceWanted;
	private int voiceLangStarted = -1;
	private long lastClip, pendingAt = -1, lastStartTry;
	private String pendingReason = "";
	private final Map<Entity, Long> hits = new HashMap<>();
	// clutch tracking
	private double peakY = Double.NaN;
	private int ticks;
	private static boolean hooked;

	public ClipEventsMod() {
		super("Clip Events", "clip_events", "Medal-style clips: hotkey, your voice (\"Jarvis, clipa isto\") or automatically on kills and clutches.",
				Category.UTILITY);
		instance = this;
		section = tabs("Section", 0, "Clips", "Voice", "Events");
		BooleanSupplier cl = () -> section.idx() == 0, vo = () -> section.idx() == 1, ev = () -> section.idx() == 2;

		// ------------------------------------------------------------------ Clips
		header("Clips").showIf(cl);
		action("Recorder", "Set up", () -> {
			if (FightCamMod.instance != null) {
				FightCamMod.instance.detectAsync(true);
			}
		}, () -> !Ffmpeg.ready() ? Ffmpeg.status : Capture.running() ? "Buffering - " + Ffmpeg.encoder.label() : "Ready").showIf(cl);
		recordClips = bool("Record clips", false);
		recorderOff = bool("v2 recorder off", false);
		recorderOff.showIf(() -> false);
		myVoice = bool("Record my voice", true);
		myVoice.tip("Your microphone goes into the clips (picked automatically).");
		bufferQuality = choice("Background quality", 0, "Light (720p 30 fps)", "Same as FightCam");
		bufferQuality.tip("Only when \"Record clips\" is on: clips are recorded all the time in the background, which costs frames (most on Mac). Light uses much less.");
		notify = bool("Clip notification", true);
		clipLength = num("Clip length", 30, 5, 120, 5, " s");
		after = num("Keep recording after", 3, 0, 10, 1, " s");
		cooldown = num("Cooldown", 5, 0, 30, 1, " s");
		clipKey = key("Clip Key", 297);
		for (Option<?> o : new Option<?>[] {recordClips, myVoice, bufferQuality, notify, clipLength, after, cooldown, clipKey}) {
			o.visibleIf = cl;
		}
		action("Clips", "Open folder", () -> net.minecraft.util.Util.getOperatingSystem().open(clipDir()), () -> "kubo-recordings/clips")
				.showIf(cl);

		// ------------------------------------------------------------------ Voice
		header("Save by voice").showIf(vo);
		voice = bool("Save by voice", false);
		voice.visibleIf = vo;
		phrase = text("Voice phrase", "jarvis clipa isto", 60, "what you say");
		voiceLang = tabs("Voice language", 0, VoiceTrigger.LANGS);
		voiceEngine = choice("Voice engine", 0, "Apple (best)", "Offline (Vosk)", "My own voice (recorded)");
		voiceEngine.tip("My own voice: record yourself saying the phrase 3 times; it clips when you say it the same way.");
		voiceEngine.visibleIf = vo;
		action("My phrase", "Record", VoiceTrigger::recordPhrase, () -> VoiceTrigger.recordingPhrase() ? "Say it now..."
				: VoiceTrigger.phraseRecordings() + "/3 recordings").showIf(() -> vo.getAsBoolean() && voiceEngine.idx() == 2);
		action("Recordings", "Delete", VoiceTrigger::clearPhrase, () -> "").showIf(() -> vo.getAsBoolean() && voiceEngine.idx() == 2
				&& VoiceTrigger.phraseRecordings() > 0);
		strict = num("How exact", 70, 50, 95, 5, "%");
		for (Option<?> o : new Option<?>[] {phrase, voiceLang, strict}) {
			o.visibleIf = vo;
		}
		action("Voice", "Restart", this::restartVoice, () -> VoiceTrigger.status).showIf(vo);
		action("Heard", "Test clip", () -> trigger("Voice test"), () -> VoiceTrigger.heard.isEmpty() ? "(say something)" : "\"" + VoiceTrigger.heard + "\"")
				.showIf(() -> vo.getAsBoolean() && voice.on());

		// ------------------------------------------------------------------ Events
		header("Fights").showIf(ev);
		kills = bool("Kills", true);
		hypixel = bool("Final Kills & Bed Breaks", true);
		deaths = bool("Your Deaths", false);
		multiKills = bool("Double / Triple Kills", true);
		lowHp = bool("Low HP Survivals", true);
		header("Clutches").showIf(ev);
		clutches = bool("Clutches", true);
		minFall = num("Minimum Fall Height", 10, 4, 40, 1, " blocks");
		water = bool("Water", true);
		hay = bool("Hay Bale", true);
		bed = bool("Bed", true);
		cobweb = bool("Cobweb", true);
		slime = bool("Slime and Honey", true);
		boat = bool("Boat", true);
		mount = bool("Horse and other Mounts", true);
		ladder = bool("Ladder and Vines", true);
		for (Option<?> o : new Option<?>[] {kills, hypixel, deaths, clutches, multiKills, lowHp}) {
			o.visibleIf = ev;
		}
		for (Option<?> o : new Option<?>[] {minFall, water, hay, bed, cobweb, slime, boat, mount, ladder}) {
			o.visibleIf = () -> ev.getAsBoolean() && clutches.on();
		}
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (!recorderOff.on()) {
			recorderOff.set(true);
			recordClips.set(false); // the background recorder reads the screen from the GPU all the time: it is now opt-in
		}
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
				if (!overlay && enabled) {
					onChat(message.getString());
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Clip Events: no chat events: " + t);
		}
	}

	@Override
	protected void onDisable() {
		VoiceTrigger.stop();
		voiceWanted = false;
		pendingAt = -1;
		if (FightCamMod.instance != null) {
			FightCamMod.instance.captureMaybeStop();
		}
	}

	/** The background buffer records at the light quality (720p 30 fps). */
	public boolean lightBuffer() {
		return bufferQuality.idx() == 0;
	}

	/** True while the rolling buffer should run. */
	public boolean wantsBuffer() {
		return enabled && recordClips.on() && mc.world != null;
	}

	public int neededSeconds() {
		return clipLength.i() + after.i() + 4;
	}

	private File clipDir() {
		File d = new File(Capture.outputDir(), "clips");
		d.mkdirs();
		return d;
	}

	public void onAttack(Entity e) {
		if (e instanceof LivingEntity) {
			hits.put(e, System.currentTimeMillis());
		}
	}

	private void restartVoice() {
		VoiceTrigger.stop();
		voiceWanted = false;
	}

	// ------------------------------------------------------------------ tick
	@Override
	public void tick() {
		ticks++;
		Capture.keep(neededSeconds());
		// rolling buffer on / off
		if (wantsBuffer() && !Capture.running() && Ffmpeg.ready() && FightCamMod.instance != null && System.currentTimeMillis() - lastStartTry > 10_000) {
			lastStartTry = System.currentTimeMillis();
			FightCamMod.instance.startCapture(lightBuffer());
		} else if (!recordClips.on() && Capture.running() && FightCamMod.instance != null && ticks % 20 == 0) {
			FightCamMod.instance.captureMaybeStop();
		}
		// voice
		boolean wantVoice = voice.on();
		VoiceTrigger.setPhrase(phrase.get(), strict.i());
		VoiceTrigger.preferApple = voiceEngine.idx() == 0;
		VoiceTrigger.engine = voiceEngine.idx();
		if (wantVoice && (!voiceWanted || voiceLangStarted != voiceLang.idx() || engineStarted != voiceEngine.idx())) {
			VoiceTrigger.stop();
			voiceWanted = true;
			voiceLangStarted = voiceLang.idx();
			engineStarted = voiceEngine.idx();
			mc.execute(() -> VoiceTrigger.start(voiceLangStarted, () -> mc.execute(() -> trigger("Voice"))));
		} else if (!wantVoice && voiceWanted) {
			voiceWanted = false;
			VoiceTrigger.stop();
		}
		// your voice in the clips: the microphone is picked by itself
		if (ticks % 20 == 0 && FightCamMod.instance != null && Ffmpeg.ready()) {
			FightCamMod.instance.syncMic(myVoice.on(), myVoice.on() != myVoicePrev);
			myVoicePrev = myVoice.on();
		}
		// hotkey
		boolean k = Keys.down(clipKey.code()) && mc.currentScreen == null;
		if (k && !prevKey) {
			trigger("Hotkey");
		}
		prevKey = k;
		var p = mc.player;
		if (p == null) {
			return;
		}
		// kills: someone you hit recently died
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Entity, Long>> it = hits.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Entity, Long> e = it.next();
			Entity ent = e.getKey();
			if (now - e.getValue() > 4000) {
				it.remove();
				continue;
			}
			if (ent instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0F)) {
				it.remove();
				if (kills.on() && ent instanceof PlayerEntity) {
					onKill(now);
				}
			}
		}
		// your deaths
		boolean dead = p.isDead();
		if (dead && !wasDead && deaths.on()) {
			trigger("Death");
		}
		wasDead = dead;
		// clutches
		if (clutches.on()) {
			clutch(p);
		}
		if (lowHp.on()) {
			lowHealth(p, now);
		}
		// a clip waiting for "keep recording after"
		if (pendingAt >= 0 && now >= pendingAt) {
			pendingAt = -1;
			saveNow(pendingReason);
		}
	}

	private final long[] killTimes = new long[3];
	private int killStreak;
	private long lowSince = -1;

	/** A kill; 2 or 3 within a few seconds become a Double / Triple Kill (the clip on its way is renamed and extended). */
	private void onKill(long now) {
		killStreak = now - killTimes[0] < 6000 ? killStreak + 1 : 1;
		killTimes[0] = now;
		if (killStreak >= 2 && multiKills.on() && pendingAt >= 0) {
			pendingReason = killStreak >= 3 ? "Triple Kill" : "Double Kill";
			pendingAt = Math.max(pendingAt, now + after.i() * 1000L); // keep the moment after the last kill too
			return;
		}
		if (killStreak >= 2 && multiKills.on()) {
			lastClip = 0; // multi kills ignore the cooldown
			trigger(killStreak >= 3 ? "Triple Kill" : "Double Kill");
			return;
		}
		trigger("Kill");
	}

	/** Dropped to 2 hearts or less and lived: clip it once you are safe again. */
	private void lowHealth(PlayerEntity p, long now) {
		float hp = p.getHealth();
		if (p.isDead()) {
			lowSince = -1;
			return;
		}
		if (hp <= 4F && lowSince < 0) {
			lowSince = now;
		} else if (lowSince >= 0 && hp >= 10F) {
			if (now - lowSince < 30000) {
				trigger("Low HP Survive");
			}
			lowSince = -1;
		} else if (lowSince >= 0 && now - lowSince > 30000) {
			lowSince = -1;
		}
	}

	private void clutch(PlayerEntity p) {
		boolean water0 = p.isTouchingWater();
		boolean climb = p.isClimbing();
		boolean ride = p.hasVehicle();
		BlockPos feet = p.getBlockPos();
		BlockState at = mc.world.getBlockState(feet), below = mc.world.getBlockState(feet.down());
		boolean web = at.isOf(Blocks.COBWEB);
		boolean landed = p.isOnGround() || water0 || climb || ride || web;
		if (!landed) {
			if (Double.isNaN(peakY) || p.getY() > peakY) {
				peakY = p.getY();
			}
			return;
		}
		if (!Double.isNaN(peakY)) {
			double drop = peakY - p.getY();
			peakY = Double.NaN;
			if (drop >= minFall.f() && !p.isDead()) {
				String type = null;
				if (water0 && water.on()) {
					type = "Water";
				} else if (web && cobweb.on()) {
					type = "Cobweb";
				} else if (climb && ladder.on()) {
					type = "Ladder";
				} else if (ride) {
					Entity v = p.getVehicle();
					if (v instanceof AbstractBoatEntity) {
						type = boat.on() ? "Boat" : null;
					} else if (mount.on()) {
						type = "Mount";
					}
				} else {
					BlockState s = below.isAir() ? at : below;
					if (s.isOf(Blocks.HAY_BLOCK) && hay.on()) {
						type = "Hay";
					} else if (s.getBlock() instanceof BedBlock && bed.on()) {
						type = "Bed";
					} else if ((s.isOf(Blocks.SLIME_BLOCK) || s.isOf(Blocks.HONEY_BLOCK)) && slime.on()) {
						type = "Slime";
					}
				}
				if (type != null) {
					trigger("Clutch " + type);
				}
			}
		}
	}

	private void onChat(String s) {
		if (!hypixel.on() || mc.player == null) {
			return;
		}
		String me = mc.player.getName().getString();
		if (me.isEmpty() || !s.contains(me)) {
			return;
		}
		boolean finalKill = s.contains("FINAL KILL") && !s.startsWith(me);
		boolean bedBreak = s.contains("BED DESTRUCTION") && s.indexOf(me) > s.indexOf("BED DESTRUCTION");
		if (finalKill || bedBreak) {
			mc.execute(() -> trigger(finalKill ? "Final Kill" : "Bed Break"));
		}
	}

	// ------------------------------------------------------------------ saving
	/** Something worth clipping happened: save after "keep recording after" seconds (respecting the cooldown). */
	public void trigger(String reason) {
		long now = System.currentTimeMillis();
		if (pendingAt >= 0) {
			return; // a clip is already on its way (it will include this moment too)
		}
		if (now - lastClip < cooldown.f() * 1000) {
			return;
		}
		if (!Capture.running()) {
			if (!Ffmpeg.ready()) {
				ClipToast.show("Can't clip", "Set up the recorder (Clip Events > Recorder)", null, true);
			} else if (FightCamMod.instance != null && !FightCamMod.instance.startCapture(lightBuffer())) {
				ClipToast.show("Can't clip", Capture.status, null, true);
			} else {
				ClipToast.show("Clips on", "The recorder just started - clips will work in a moment", null, false);
			}
			return;
		}
		lastClip = now;
		pendingReason = reason;
		pendingAt = now + after.i() * 1000L;
	}

	private void saveNow(String reason) {
		int secs = clipLength.i() + after.i();
		String safe = reason.replaceAll("[^A-Za-z0-9]+", "-");
		File out = new File(clipDir(), "Clip_" + safe + "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".mp4");
		int[] thumb = Capture.thumbnail(ClipToast.thumbW(), ClipToast.thumbH());
		Capture.save(secs, out, err -> {
			if (err == null) {
				dev.sleepclient.stats.KuboStats.add("clips", 1);
				VerticalClipsMod.export(out); // TikTok / Shorts version (if that mod is on)
			}
			if (!notify.on() && err == null) {
				return;
			}
			if (err == null) {
				ClipToast.show("Clip saved", dev.sleepclient.Lang.tr("Clipped the last " + secs + " seconds", "Gravados os últimos " + secs + " segundos")
						+ (reason.equals("Hotkey") ? "" : " · " + dev.sleepclient.Lang.t(reason)), thumb, false);
			} else {
				ClipToast.show("Clip failed", err, null, true);
			}
		});
	}
}
