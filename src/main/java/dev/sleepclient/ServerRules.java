package dev.sleepclient;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Servers with an anticheat that does not allow client-side inventory automation (Hypixel's Watchdog answered "Bad client inventory move"
 * and sent the player to Limbo). On them Sleep sends nothing but the packets the normal game sends: the inventory resync request of
 * Ghost Item Fix and the automatic click sequences (sorting / quick-move buttons) stay off. Everything else keeps working.
 */
public final class ServerRules {

	private ServerRules() {
	}

	/** True while connected to Hypixel (any of its addresses). */
	public static boolean hypixel() {
		try {
			ServerInfo e = MinecraftClient.getInstance().getCurrentServerEntry();
			if (e == null || e.address == null) {
				return false;
			}
			String a = e.address.toLowerCase(Locale.ROOT);
			return a.contains("hypixel.net") || a.contains("hypixel.io");
		} catch (RuntimeException ex) {
			return false;
		}
	}
}
