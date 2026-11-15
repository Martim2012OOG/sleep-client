package dev.solclient.mods;

import dev.solclient.option.Option;

/** Shows the time before every chat message ([14:05]). */
public class ChatTimestampsMod extends Mod {

	public static ChatTimestampsMod instance;
	public final Option.Bool seconds = bool("Show Seconds", false);

	public ChatTimestampsMod() {
		super("Chat Timestamps", "chat_timestamps", "Shows the time before every chat message.", Category.UTILITY);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	public static net.minecraft.network.chat.Component apply(net.minecraft.network.chat.Component msg) {
		if (!on() || msg == null) {
			return msg;
		}
		String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(instance.seconds.on() ? "HH:mm:ss" : "HH:mm"));
		return net.minecraft.network.chat.Component.empty().append(net.minecraft.network.chat.Component.literal("[" + t + "] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)).append(msg);
	}
}
