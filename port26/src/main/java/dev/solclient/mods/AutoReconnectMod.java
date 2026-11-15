package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** When you get disconnected from a server, joins it again after a few seconds (with a button to do it now or cancel). */
public class AutoReconnectMod extends Mod {

	public static AutoReconnectMod instance;
	public final Option.Num delay = num("Reconnect Delay", 0, 0, 10000, 100, " ms"); // 0 = at once
	public final Option.Num attempts = num("Max Attempts", 5, 1, 20, 1, "");

	private static ServerData last;
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
		return last == null ? "" : last.name == null || last.name.isBlank() ? last.ip : last.name;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		ServerData s = mc.getCurrentServer();
		if (s != null && mc.level != null) {
			if (last == null || !s.ip.equals(last.ip)) {
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
		Minecraft mc = Minecraft.getInstance();
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(last.ip), last, false, null);
	}
}
