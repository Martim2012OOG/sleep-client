package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

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

	public static net.minecraft.text.Text apply(net.minecraft.text.Text msg) {
		if (!on() || msg == null) {
			return msg;
		}
		String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(instance.seconds.on() ? "HH:mm:ss" : "HH:mm"));
		return net.minecraft.text.Text.empty().append(net.minecraft.text.Text.literal("[" + t + "] ").formatted(net.minecraft.util.Formatting.DARK_GRAY)).append(msg);
	}
}
