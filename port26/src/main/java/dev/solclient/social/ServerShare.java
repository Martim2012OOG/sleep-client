package dev.solclient.social;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * "Join your friend": tells your Caos friends which server you are on (your activity says "Playing on <address>")
 * and lets you join theirs with one click from the friends list. Turn it off in Caos settings > Players.
 */
public final class ServerShare {

	private static final Pattern PLAYING = Pattern.compile("(?i)playing on (kubo:[a-z0-9]{6,12}|[a-z0-9.-]+\\.[a-z]{2,}/[a-z0-9_]{3,16}|[a-z0-9.-]+(?::\\d{1,5})?)");

	private ServerShare() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(() -> {
			ServerData e = c.getCurrentServer();
			if (e != null && e.ip != null && !c.isLocalServer() && allowed()) {
				post("Playing on " + e.ip.toLowerCase(Locale.ROOT));
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			if (allowed()) {
				post("In the menus");
			}
		});
	}

	private static boolean allowed() {
		return dev.solclient.SolClient.settings == null || dev.solclient.SolClient.settings.shareServer == null
				|| dev.solclient.SolClient.settings.shareServer.on();
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
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			boolean sp = mc.isLocalServer();
			mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
			if (sp) {
				mc.disconnectWithSavingScreen();
			} else {
				mc.disconnectWithProgressScreen();
			}
		}
		String name = address;
		if (KuboRelay.isRelayAddress(address)) {
			// a friend's hosted world through the Caos relay: connect to the small local tunnel
			try {
				address = KuboRelay.localFor(address);
			} catch (Exception e) {
				Social.say("Could not join: " + e.getMessage(), true);
				return;
			}
		}
		ServerData info = new ServerData(name, address, ServerData.Type.OTHER);
		ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc, ServerAddress.parseString(address), info, false, null);
	}
}
