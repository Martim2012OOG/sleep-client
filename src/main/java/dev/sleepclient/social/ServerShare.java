package dev.sleepclient.social;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * "Join your friend": tells your Sleep friends which server you are on (your activity says "Playing on <address>")
 * and lets you join theirs with one click from the friends list. Turn it off in Sleep settings > Players.
 */
public final class ServerShare {

	private static final Pattern PLAYING = Pattern.compile("(?i)playing on (kubo:[a-z0-9]{6,12}|[a-z0-9.-]+\\.[a-z]{2,}/[a-z0-9_]{3,16}|[a-z0-9.-]+(?::\\d{1,5})?)");

	private ServerShare() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(() -> {
			ServerInfo e = c.getCurrentServerEntry();
			if (e != null && e.address != null && !c.isInSingleplayer() && allowed()) {
				post("Playing on " + e.address.toLowerCase(Locale.ROOT));
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			if (allowed()) {
				post("In the menus");
			}
		});
	}

	private static boolean allowed() {
		return dev.sleepclient.SleepClient.settings == null || dev.sleepclient.SleepClient.settings.shareServer == null
				|| dev.sleepclient.SleepClient.settings.shareServer.on();
	}

	/** Sets your activity line for friends ("Playing on ..." makes the Join button appear for them). */
	public static void postActivity(String activity) {
		if (allowed()) {
			post(activity);
		}
	}

	private static void post(String activity) {
		if (Social.me == null) {
			return;
		}
		Social.IO.execute(() -> {
			JsonObject b = new JsonObject();
			b.addProperty("activity", activity);
			SolarApi.post("/me/activity", b);
		});
	}

	/** The server a friend is playing on (from their activity), or null. */
	public static String serverOf(Social.User u) {
		if (u == null || u.activity == null || !u.online()) {
			return null;
		}
		Matcher m = PLAYING.matcher(u.activity);
		return m.find() ? m.group(1) : null;
	}

	public static void join(String address) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null) {
			boolean sp = mc.isInSingleplayer();
			mc.world.disconnect(net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT);
			if (sp) {
				mc.disconnectWithSavingScreen();
			} else {
				mc.disconnectWithProgressScreen();
			}
		}
		String name = address;
		if (KuboRelay.isRelayAddress(address)) {
			// a friend's hosted world through the Sleep relay: connect to the small local tunnel
			try {
				address = KuboRelay.localFor(address);
			} catch (Exception e) {
				Social.say("Could not join: " + e.getMessage(), true);
				return;
			}
		}
		ServerInfo info = new ServerInfo(name, address, ServerInfo.ServerType.OTHER);
		ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), mc, ServerAddress.parse(address), info, false, null);
	}
}
