package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.option.Option;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.DrawContext;

/**
 * Streamer Mode: for when you record or go live. Hides the server address and coordinates HUDs, private messages,
 * the text of Sleep message notifications, and turns Nick Hider on. A small "LIVE" tag shows it is on.
 */
public class StreamerModeMod extends Mod {

	public static StreamerModeMod instance;
	private final Option.Key toggleKey = key("Toggle Key", -1);
	private final Option.Bool hideIp = bool("Hide Server Address", true);
	private final Option.Bool hideCoords = bool("Hide Coordinates", true);
	private final Option.Bool hideDms = bool("Hide Private Messages", true);
	private final Option.Bool hideToasts = bool("Hide Message Previews", true);
	private final Option.Bool hideNick = bool("Turn On Nick Hider", true);
	private final Option.Bool tag = bool("Show LIVE Tag", true);
	private boolean prev;
	private boolean nickWasOn;
	private static boolean hooked;
	private static final Pattern DM = Pattern.compile(
			"(?i)^(from |\\[?\\w{3,16} ?(->|→) ?(me|mim|you)\\]?|\\w{3,16} whispers( to you)?:|\\w{3,16} sussurra|de \\w{3,16}:|\\[msg\\]|\\(msg\\)).*");

	public StreamerModeMod() {
		super("Streamer Mode", "streamer_mode", "Hides your server IP, coordinates, private messages and nick while you record or stream.", Category.UTILITY);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	/** HUD elements that must not show right now. */
	public static boolean hidesHud(Mod m) {
		return on() && (m instanceof ServerAddressMod && instance.hideIp.on() || m instanceof CoordinatesMod && instance.hideCoords.on());
	}

	public static boolean hideToasts() {
		return on() && instance.hideToasts.on();
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !(on() && instance.hideDms.on() && !overlay && DM.matcher(message.getString()).matches()));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> {
			if (!on() || !instance.hideDms.on() || params == null) {
				return true;
			}
			// private chat types: msg_command_incoming / outgoing
			String type = params.type().getIdAsString();
			return !(type != null && type.contains("msg_command"));
		});
	}

	@Override
	public void onEnable() {
		if (hideNick.on() && NickHiderMod.instance != null) {
			nickWasOn = NickHiderMod.instance.enabled;
			NickHiderMod.instance.setEnabled(true);
		}
	}

	@Override
	public void onDisable() {
		if (hideNick.on() && NickHiderMod.instance != null && !nickWasOn) {
			NickHiderMod.instance.setEnabled(false);
		}
	}

	/** The toggle key works even while the mod is off (it is what turns it on). */
	public static void pollKey() {
		if (instance == null) {
			return;
		}
		var mc = instance.mc;
		boolean d = mc.currentScreen == null && Keys.down(instance.toggleKey.code());
		if (d && !instance.prev) {
			instance.setEnabled(!instance.enabled);
			dev.solclient.social.Social.say(instance.enabled ? Lang.tr("Streamer Mode on", "Modo Streamer ligado") : Lang.tr("Streamer Mode off", "Modo Streamer desligado"), false);
		}
		instance.prev = d;
	}

	public void drawTag(DrawContext c) {
		if (!tag.on()) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth();
		String s = "LIVE";
		int w = Gfx.twRaw(s) + 16;
		int x = sw / 2 - w / 2, y = 3;
		Gfx.rrectFree(c, x, y, w, 12, 6, 0xC0E5484D);
		float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 300.0);
		Gfx.circle(c, x + 6, y + 6, 2.2F, Gfx.withAlpha(0xFFFFFF, Math.round(120 + 135 * pulse)));
		Gfx.textRaw(c, s, x + 11, y + 2, 0xFFFFFFFF);
	}
}
