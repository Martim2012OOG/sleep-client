package dev.sleepclient.mods;

import net.minecraft.client.network.ServerInfo;

public class ServerAddressMod extends TextHudMod {

	public ServerAddressMod() {
		super("Server Address", "server_address", "Shows the address of the server you are on.", 0.01F, 0.38F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.isInSingleplayer()) {
			return "Singleplayer";
		}
		ServerInfo s = mc.getCurrentServerEntry();
		return s == null || s.address == null ? (edit ? "play.server.com" : "") : s.address;
	}
}
