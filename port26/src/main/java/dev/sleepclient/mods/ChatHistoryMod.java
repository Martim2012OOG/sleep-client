package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Keeps your chat when you leave a server or join another one (Minecraft normally clears it). */
public class ChatHistoryMod extends Mod {

	public static ChatHistoryMod instance;
	public final Option.Bool separator = bool("Show Server Separator", true);

	public ChatHistoryMod() {
		super("Chat History", "chat_history", "Keeps the chat when you switch servers, with a line showing where each server starts.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private String lastServer;

	@Override
	public void tick() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		var e = mc.getCurrentServer();
		String now = e == null ? (mc.isLocalServer() ? "Singleplayer" : null) : (e.name == null || e.name.isBlank() ? e.ip : e.name);
		if (now != null && !now.equals(lastServer)) {
			if (lastServer != null && separator.on()) {
				dev.sleepclient.compat.Mc.chat(mc).addClientSystemMessage(net.minecraft.network.chat.Component.literal("\u00a78\u00a7m          \u00a7r \u00a77" + now + " \u00a78\u00a7m          "));
			}
			lastServer = now;
		}
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
