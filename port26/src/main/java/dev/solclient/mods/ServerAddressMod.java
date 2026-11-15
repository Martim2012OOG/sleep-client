package dev.solclient.mods;

import net.minecraft.client.multiplayer.ServerData;

public class ServerAddressMod extends TextHudMod {

	public ServerAddressMod() {
		super("Server Address", "server_address", "Shows the address of the server you are on.", 0.01F, 0.38F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.isLocalServer()) {
			return "Singleplayer";
		}
		ServerData s = mc.getCurrentServer();
		return s == null || s.ip == null ? (edit ? "play.server.com" : "") : s.ip;
	}
}
