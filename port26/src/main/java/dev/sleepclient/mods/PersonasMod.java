package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.option.Option;
import dev.sleepclient.voice.persona.Persona;
import dev.sleepclient.voice.persona.PersonaHook;
import dev.sleepclient.voice.persona.PersonaMonitor;
import dev.sleepclient.voice.persona.PersonaPanel;
import dev.sleepclient.voice.persona.SystemVoice;
import java.util.List;

/**
 * Personas: pick a character and everything you say sounds like them. In Minecraft it is not tied to one voice mod: the
 * microphone itself is reshaped (see {@link PersonaHook}), so Simple Voice Chat, Plasmo Voice, Voice Messages and this client's
 * voice chat all get it. "Whole computer" also sends it to a virtual microphone for Discord and any other app.
 */
public class PersonasMod extends Mod {

	public static PersonasMod instance;

	public final Option.Text character = text("Character", "me", 40, "").showIf(() -> false);
	/** Favourite voices (ids, comma separated). */
	public final Option.Text favs = text("Favourite Voices", "", 2000, "").showIf(() -> false);

	@SuppressWarnings("unused")
	private final Option.Custom panel = custom("Voices", PersonaPanel.INSTANCE.drawer());

	public boolean isFav(String id) {
		return ("," + favs.get() + ",").contains("," + id + ",");
	}

	public void toggleFav(String id) {
		java.util.List<String> l = new java.util.ArrayList<>(java.util.Arrays.asList(favs.get().split(",")));
		l.removeIf(String::isBlank);
		if (!l.remove(id)) {
			l.add(id);
		}
		favs.set(String.join(",", l));
		dev.sleepclient.Config.save();
	}

	// ---- where
	private final Option.Header hWhere = header("Where it works");
	public final Option.Choice where = choice("Use It In", 0, "Only Minecraft (all voice mods)", "Whole computer (Discord, etc.)", "Minecraft + whole computer").showIf(() -> false) // picked in the panel at the top
			.tip("Only Minecraft: Simple Voice Chat, Plasmo Voice and every other voice mod. Whole computer: also a virtual microphone you pick in Discord, OBS, TikTok LIVE Studio...");
	public final Option.Text cable = text("Virtual Microphone", "", 80, "auto (BlackHole / VB-CABLE)");
	@SuppressWarnings("unused")
	private final Option.Action cableStatus = action("Virtual microphone", "Get it (free)", this::openCableSite, PersonasMod::cableLine);

	// ---- voice
	private final Option.Header hVoice = header("Voice");
	public final Option.Num pitch = num("Fine Pitch", 0, -12, 12, 0.5F, " st");
	public final Option.Num amount = num("Effect Strength", 100, 0, 150, 5, "%");
	public final Option.Num volume = num("Loudness", 0, -12, 12, 1, " dB");
	public final Option.Num disguise = num("Voice Disguise", 0, -6, 6, 0.5F, " st")
			.tip("Changes the size of your throat and mouth without changing the pitch: below 0 = bigger / older, above 0 = smaller / younger. Makes your voice much harder to recognise.");
	public final Option.Bool autoLevel = bool("Auto Volume", true).tip("Every character comes out about as loud as your normal voice.");

	// ---- microphone
	private final Option.Header hMic = header("Microphone");
	public final Option.Num micBoost = num("Mic Boost", 0, -12, 18, 1, " dB");
	public final Option.Num gate = num("Noise Gate", -90, -90, -20, 1, " dB").tip("Mutes the mic below this level: no keyboard or fan noise. -90 = off.");

	// ---- sound
	private final Option.Header hSound = header("Sound");
	public final Option.Num bass = num("Bass", 0, -12, 12, 1, " dB");
	public final Option.Num treble = num("Treble", 0, -12, 12, 1, " dB");

	// ---- extra effects on top of any character
	private final Option.Header hExtra = header("Extra effects (on top of the character)");
	public final Option.Num xEcho = num("Extra Echo", 0, 0, 100, 5, "%");
	public final Option.Num xRoom = num("Extra Room / Reverb", 0, 0, 100, 5, "%");
	public final Option.Num xRobot = num("Extra Robot", 0, 0, 100, 5, "%");
	public final Option.Num xDrive = num("Extra Distortion", 0, 0, 100, 5, "%");
	public final Option.Num xCrowd = num("Extra Crowd", 0, 0, 100, 5, "%");
	public final Option.Num xWhisper = num("Extra Whisper", 0, 0, 100, 5, "%");

	// ---- keys
	private final Option.Header hKeys = header("Keys");
	public final Option.Key openKey = key("Characters Menu Key", 75);
	public final Option.Choice keyMode = choice("Effect Is On", 0, "Always", "Only while holding the key", "Toggle with the key");
	public final Option.Key effectKey = key("Effect Key", -1);
	public final Option.Key nextKey = key("Next Character", -1);
	public final Option.Key prevKey = key("Previous Character", -1);
	public final Option.Key randomKey = key("Random Character", -1);
	public final Option.Bool announce = bool("Show Character Name When Changed", true);

	private boolean toggled = true;
	private int frames;

	public PersonasMod() {
		super("Personas", "personas", "Pick a character and talk as them in any voice chat - or the whole computer: 60+ voices, from robot to dragon. Add your own too.",
				Category.UTILITY);
		instance = this;
		cable.showIf(() -> where.idx() != 0);
		cableStatus.showIf(() -> where.idx() != 0);
	}

	public Persona current() {
		return Persona.find(character.get());
	}

	public void pick(Persona p) {
		character.set(p.id);
		if (announce.on() && mc.gui != null && mc.player != null) {
			dev.sleepclient.compat.Mc.overlayMessage(mc, net.minecraft.network.chat.Component.literal("Talking as " + p.name), false);
		}
	}

	/** Opens the client menu straight on this page (the voices are drawn inside it). */
	public void openPage() {
		dev.sleepclient.gui.SolMenuScreen s = new dev.sleepclient.gui.SolMenuScreen();
		dev.sleepclient.compat.Mc.setScreen(mc, s);
		s.openSettingsFor(this);
	}

	/** A photo dropped on the window becomes the chosen voice's picture. */
	@Override
	public boolean onFileDropped(java.nio.file.Path file) {
		PersonaPanel.INSTANCE.setPicture(current(), file);
		return true;
	}

	private static String cableLine() {
		if (SystemVoice.on()) {
			return SystemVoice.status;
		}
		if (!SystemVoice.status.isEmpty()) {
			return SystemVoice.status;
		}
		List<String> c = SystemVoice.cables();
		return c.isEmpty() ? "Not installed yet" : "Found: " + c.get(0);
	}

	private void openCableSite() {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		String url = os.contains("mac") ? "https://existential.audio/blackhole/" : "https://vb-audio.com/Cable/";
		net.minecraft.util.Util.getPlatform().openUri(url);
	}

	@Override
	protected void onDisable() {
		PersonaHook.active = null;
		SystemVoice.stop();
	}

	private boolean pressed(Option.Key k, boolean[] arm) {
		boolean d = Keys.down(k.code());
		if (!d) {
			arm[0] = true;
			return false;
		}
		if (arm[0] && dev.sleepclient.compat.Mc.screen(mc) == null) {
			arm[0] = false;
			return true;
		}
		return false;
	}

	private final boolean[] aOpen = {true}, aEff = {true}, aNext = {true}, aPrev = {true}, aRand = {true};

	private void step(int dir) {
		List<Persona> all = Persona.all();
		int i = 0;
		for (int k = 0; k < all.size(); k++) {
			if (all.get(k).id.equals(character.get())) {
				i = k;
			}
		}
		pick(all.get(Math.floorMod(i + dir, all.size())));
	}

	@Override
	public void frame() {
		// keys
		if (pressed(openKey, aOpen) && mc.player != null) {
			openPage();
		}
		if (PersonaMonitor.on() && System.currentTimeMillis() - PersonaPanel.lastDrawn > 1000) {
			PersonaMonitor.stop(); // never leave the microphone playing back when the voice page is gone
		}
		if (pressed(nextKey, aNext)) {
			step(1);
		}
		if (pressed(prevKey, aPrev)) {
			step(-1);
		}
		if (pressed(randomKey, aRand)) {
			List<Persona> all = Persona.all();
			pick(all.get(1 + new java.util.Random().nextInt(Math.max(1, all.size() - 1))));
		}
		boolean effOn;
		switch (keyMode.idx()) {
			case 1 -> effOn = effectKey.code() < 0 || Keys.down(effectKey.code());
			case 2 -> {
				if (pressed(effectKey, aEff)) {
					toggled = !toggled;
					if (announce.on() && mc.gui != null && mc.player != null) {
						dev.sleepclient.compat.Mc.overlayMessage(mc, net.minecraft.network.chat.Component.literal(toggled ? "Voice: " + current().name : "Voice: normal"), false);
					}
				}
				effOn = toggled;
			}
			default -> effOn = true;
		}

		// settings -> the audio thread
		Persona p = current();
		PersonaHook.pitchAdd = pitch.f();
		PersonaHook.amount = amount.f() / 100F;
		PersonaHook.gainDb = volume.f();
		PersonaHook.Tune t = PersonaHook.TUNE;
		t.autoLevel = autoLevel.on();
		t.micBoostDb = micBoost.f();
		t.gateDb = gate.f();
		t.bassDb = bass.f();
		t.trebleDb = treble.f();
		t.addEcho = xEcho.f() / 100F;
		t.addReverb = xRoom.f() / 100F;
		t.addRobot = xRobot.f() / 100F;
		t.addDrive = xDrive.f() / 100F;
		t.addChorus = xCrowd.f() / 100F;
		t.addWhisper = xWhisper.f() / 100F;
		t.formantAdd = disguise.f();
		boolean extras = micBoost.f() != 0 || gate.f() > -90 || bass.f() != 0 || treble.f() != 0 || xEcho.f() + xRoom.f() + xRobot.f() + xDrive.f() + xCrowd.f() + xWhisper.f() > 0;
		boolean nothing = p.neutral() && pitch.f() == 0 && volume.f() == 0 && disguise.f() == 0 && !extras;
		PersonaHook.active = nothing || !effOn ? null : p;

		// whole computer
		boolean pc = where.idx() != 0;
		if (pc && ++frames % 200 == 0 && SystemVoice.status.startsWith("No virtual") && !SystemVoice.cables().isEmpty()) {
			SystemVoice.status = ""; // you just installed the cable: try again
		}
		SystemVoice.pcOnly = where.idx() == 1;
		if (pc && !SystemVoice.on() && !PersonaMonitor.on() && SystemVoice.status.indexOf("No virtual") < 0) {
			SystemVoice.start(cable.get());
		} else if (!pc && SystemVoice.on()) {
			SystemVoice.stop();
			SystemVoice.status = "";
		}
	}
}
