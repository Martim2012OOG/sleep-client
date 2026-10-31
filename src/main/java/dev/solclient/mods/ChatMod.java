package dev.solclient.mods;

import dev.solclient.option.Option;

/** Simplified Chat mod: drives the vanilla chat settings (size and background). */
public class ChatMod extends Mod {

	private final Option.Num width = num("Width", 320, 40, 320, 1, "");
	private final Option.Num closedHeight = num("Height (Closed)", 90, 20, 180, 1, "");
	private final Option.Num openHeight = num("Height (Open)", 180, 20, 180, 1, "");
	private final Option.Bool background = bool("Background", true);
	private final Option.Num backgroundOpacity = num("Background Opacity", 50, 0, 100, 1, "%");
	private final Option.Bool timestamps = bool("Timestamps", false);
	private final Option.Choice timestampFormat = choice("Timestamp Format", 0, "14:05", "2:05 PM", "14:05:09");

	public static ChatMod instance;

	/** "[14:05] " in grey, or null when off. */
	public static String timestamp() {
		if (instance == null || !instance.enabled || !instance.timestamps.on()) {
			return null;
		}
		String p = switch (instance.timestampFormat.idx()) {
			case 1 -> "h:mm a";
			case 2 -> "HH:mm:ss";
			default -> "HH:mm";
		};
		return "[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(p)) + "] ";
	}

	private boolean applied;
	private double pw, pc, po, pb;

	public ChatMod() {
		super("Chat", "chat", "Customise the size and background of the chat, and add timestamps.", Category.HUD);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	protected void onDisable() {
		if (applied) {
			mc.options.getChatWidth().setValue(pw);
			mc.options.getChatHeightUnfocused().setValue(pc);
			mc.options.getChatHeightFocused().setValue(po);
			mc.options.getTextBackgroundOpacity().setValue(pb);
			applied = false;
		}
	}

	@Override
	public void tick() {
		if (!applied) {
			pw = mc.options.getChatWidth().getValue();
			pc = mc.options.getChatHeightUnfocused().getValue();
			po = mc.options.getChatHeightFocused().getValue();
			pb = mc.options.getTextBackgroundOpacity().getValue();
			applied = true;
		}
		mc.options.getChatWidth().setValue((width.f() - 40.0) / 280.0);
		mc.options.getChatHeightUnfocused().setValue((closedHeight.f() - 20.0) / 160.0);
		mc.options.getChatHeightFocused().setValue((openHeight.f() - 20.0) / 160.0);
		mc.options.getTextBackgroundOpacity().setValue(background.on() ? backgroundOpacity.f() / 100.0 : 0.0);
	}
}
