package dev.solclient.voice;

import dev.solclient.Keys;
import dev.solclient.mods.Mod;
import dev.solclient.option.Option;
import dev.solclient.social.Presence;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Proximity voice chat (off by default). When it is off you don't send, don't hear anybody and no voice icons
 * are drawn. Works on any server where the other players also use Plegumo with voice on.
 */
public class VoiceChatMod extends Mod {

	public static VoiceChatMod instance;

	public final Option.Key openKey = key("Voice Chat Menu Key", 86);
	public final Option.Choice activation = choice("Activation", 0, "Voice", "Push To Talk");
	public final Option.Key pttKey = key("Push To Talk Key", 280);
	public final Option.Num threshold = num("Voice Activation Level", -40, -70, 0, 1, " dB");
	public final Option.Num micGain = num("Microphone Gain", 0, -10, 20, 1, " dB");
	public final Option.Num volume = num("Voice Chat Volume", 100, 0, 200, 5, "%");
	public final Option.Num distance = num("Distance", 48, 8, 96, 4, " blocks");
	public final Option.Bool spatial = bool("3D Audio", true);
	public final Option.Bool showIcons = bool("Show Icons Above Heads", true);
	public final Option.Bool hudIcon = bool("Show My Mic Icon", true);
	// remembered toggles (not shown in the list)
	public final Option.Bool muted;
	public final Option.Bool deafened;

	private boolean armed = true;
	private boolean lastOn, lastMuted, lastDeaf, lastRoom;
	private long lastStateSent;

	public VoiceChatMod() {
		super("Voice Chat", "voice_chat", "Talk with Sleep players near you on the same server, or with friends in a voice group.", Category.UTILITY);
		instance = this;
		muted = bool("Muted", false).showIf(() -> false);
		deafened = bool("Deafened", false).showIf(() -> false);
	}

	public boolean mutedPlayer(String uuid) {
		return VoiceConfig.muted(uuid);
	}

	@Override
	protected void onDisable() {
		VoiceNet.sendState(false, false, false);
		VoiceEngine.INSTANCE.stop();
		VoiceNet.close();
	}

	@Override
	public void tick() {
		VoiceEngine e = VoiceEngine.INSTANCE;
		if (!e.running) {
			e.start();
		}
		// positions of the players around you (only these can be heard)
		java.util.Map<String, double[]> fresh = new java.util.HashMap<>();
		if (mc.world != null) {
			for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
				if (p == mc.player) {
					continue;
				}
				double[] pos = new double[]{p.getX(), p.getEyeY(), p.getZ()};
				fresh.put(p.getUuid().toString().replace("-", ""), pos);
				fresh.put("n:" + p.getName().getString().toLowerCase(java.util.Locale.ROOT), pos);
			}
		}
		e.positions.putAll(fresh); // never empty for a moment (the speaker thread reads it at the same time)
		e.positions.keySet().retainAll(fresh.keySet());
		VoiceNet.tick();
		// tell the room our state when it changes (and every 20 s)
		boolean room = VoiceNet.ready();
		long now = System.currentTimeMillis();
		if (room && (!lastRoom || lastOn != true || lastMuted != muted.on() || lastDeaf != deafened.on() || now - lastStateSent > 20_000L)) {
			VoiceNet.sendState(true, muted.on(), deafened.on());
			lastOn = true;
			lastMuted = muted.on();
			lastDeaf = deafened.on();
			lastStateSent = now;
		}
		lastRoom = room;
	}

	@Override
	public void frame() {
		VoiceEngine e = VoiceEngine.INSTANCE;
		if (mc.player != null) {
			// the listener is your head
			e.lx = mc.player.getX();
			e.ly = mc.player.getEyeY();
			e.lz = mc.player.getZ();
			e.lyaw = mc.player.getYaw();
			e.lpitch = mc.player.getPitch();
		}
		e.pttDown = mc.currentScreen == null && Keys.down(pttKey.code());
		boolean down = Keys.down(openKey.code());
		if (!down) {
			armed = true;
		} else if (armed && mc.currentScreen == null && mc.player != null) {
			armed = false;
			mc.setScreen(new VoiceChatScreen());
		}
	}

	/** Called by the voice screen after closing with the key, so it doesn't reopen at once. */
	public void disarm() {
		armed = false;
	}

	/** Status line for the voice screens. */
	public String status() {
		VoiceEngine e = VoiceEngine.INSTANCE;
		if (e.error != null) {
			return e.error;
		}
		if (VoiceNet.roomNow() == null) {
			return VoiceGroups.all().isEmpty() ? "Join a server or make a group to talk" : "Join a server or a group to talk";
		}
		if (!VoiceNet.ready()) {
			return VoiceNet.problem != null ? VoiceNet.problem : "Connecting...";
		}
		if (e.silentMic && !muted.on()) {
			return "Your microphone sends no sound (Mac: System Settings > Privacy > Microphone)";
		}
		int n = VoiceNet.members().size() - 1;
		String where = VoiceNet.groupMode() ? VoiceGroups.activeName() : dev.solclient.Lang.tr("this server", "este servidor");
		return n <= 0 ? dev.solclient.Lang.tr("Connected to " + where + " - nobody else yet", "Ligado a " + where + " - ainda não está mais ninguém")
				: dev.solclient.Lang.tr("Connected to " + where + " - " + n + (n == 1 ? " other person" : " other people"),
						"Ligado a " + where + " - " + n + (n == 1 ? " outra pessoa" : " outras pessoas"));
	}

	public boolean connected() {
		return VoiceNet.ready() && VoiceEngine.INSTANCE.error == null;
	}
}
