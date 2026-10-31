package dev.solclient.mods;

import java.util.Locale;

/** Asks you to confirm risky commands (like /kill or /clear) by sending them twice. */
public class CommandGuardMod extends Mod {

	public static CommandGuardMod instance;
	private static final String[] RISKY = {"kill", "clear", "op", "deop", "ban", "stop", "reload", "fill", "kick", "gamemode", "tp", "lobby", "hub", "leave", "pay", "is delete", "island delete", "f disband", "warp"};
	private String last;
	private long lastAt;

	public CommandGuardMod() {
		super("Command Guard", "command_guard", "Confirm risky commands like /kill before they run.", Category.UTILITY);
		instance = this;
	}

	/** true = stop this command (first try). */
	public static boolean block(String cmd) {
		CommandGuardMod m = instance;
		if (m == null || !m.enabled || cmd == null) return false;
		String c = cmd.trim().toLowerCase(Locale.ROOT);
		boolean risky = false;
		for (String r : RISKY) if (c.equals(r) || c.startsWith(r + " ")) risky = true;
		if (!risky) return false;
		long now = System.currentTimeMillis();
		if (c.equals(m.last) && now - m.lastAt < 6000) {
			m.last = null;
			return false;
		}
		m.last = c;
		m.lastAt = now;
		if (m.mc.inGameHud != null) m.mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Command Guard: send /" + cmd + " again in 6s to confirm").formatted(net.minecraft.util.Formatting.GOLD));
		return true;
	}
}
