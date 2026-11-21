package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** When you get disconnected from a server, joins it again after a few seconds (with a button to do it now or cancel). */
public class AutoReconnectMod extends Mod {

	public static AutoReconnectMod instance;
	public final Option.Num delay = num("Reconnect Delay", 0, 0, 10000, 100, " ms"); // 0 = at once
	public final Option.Num attempts = num("Max Attempts", 5, 1, 20, 1, "");

	private static ServerInfo last;
	public static int tries;

	public AutoReconnectMod() {
		super("Auto Reconnect", "auto_reconnect", "Joins the server again after you get disconnected.", Category.UTILITY);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled && last != null && tries < Math.round(instance.attempts.f());
	}

	public static long delayMs() {
		return instance == null ? 0L : Math.round(instance.delay.f());
	}

	public static String serverName() {
		return last == null ? "" : last.name == null || last.name.isBlank() ? last.address : last.name;
	}

	@Override
	public void tick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ServerInfo s = mc.getCurrentServerEntry();
		if (s != null && mc.world != null) {
			if (last == null || !s.address.equals(last.address)) {
				last = s;
			}
			tries = 0; // connected fine
		}
	}

	public static void reconnect(Screen parent) {
		if (last == null) {
			return;
		}
		tries++;
		MinecraftClient mc = MinecraftClient.getInstance();
		ConnectScreen.connect(parent, mc, ServerAddress.parse(last.address), last, false, null);
	}
}
