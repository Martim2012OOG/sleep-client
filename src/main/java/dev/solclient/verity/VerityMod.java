package dev.solclient.verity;

import dev.solclient.mods.Mod;
import dev.solclient.option.Option;

/**
 * Settings of the Verity pet (Locker > Verity > Edit): the Groq key that makes him think and talk, how you talk to him
 * (voice chat, a key, or the chat), his voice, and how far his moods may go (angry, giant, creepier with the days).
 */
public class VerityMod extends Mod {

	public static VerityMod instance;

	private final Option.Header hAi = header("Brain (Groq)");
	public final Option.Text apiKey = text("Groq API Key", "", 200, "gsk_...");
	@SuppressWarnings("unused")
	private final Option.Action getKey = action("Free Groq key", "Open", () -> net.minecraft.util.Util.getOperatingSystem().open("https://console.groq.com/keys"),
			() -> apiKey.get().isBlank() ? "No key yet: Verity can't think" : "Key saved");
	public final Option.Text model = text("Model", "llama-3.3-70b-versatile", 80, "llama-3.3-70b-versatile");
	@SuppressWarnings("unused")
	private final Option.Action status = action("Verity Status", "Test", Verity::test, () -> Verity.status);

	private final Option.Header hTalk = header("Talking to Verity");
	public final Option.Bool listenVoice = bool("Listen In Voice Chat", true)
			.tip("Verity hears you through your voice chat (Simple Voice Chat, Plasmo, Sleep's voice chat...). Say \"Verity\" to call him.");
	public final Option.Bool wakeWord = bool("Only When You Say \"Verity\"", true)
			.tip("In voice chat he only answers when you say his name, then keeps listening for 20 seconds. Off = he answers everything you say.");
	public final Option.Key talkKey = key("Talk Key (hold to speak, tap to type)", 74);
	public final Option.Bool chatPrefix = bool("Chat Starting With \"Verity\" Goes To Him", true)
			.tip("Messages that start with Verity, or with @v, are not sent to the server: Verity answers them.");

	private final Option.Header hVoice = header("Voice");
	public final Option.Bool speak = bool("Verity Speaks Out Loud", true);
	public final Option.Text fishKey = text("Fish Audio API Key", "", 200, "for Verity's real voice");
	@SuppressWarnings("unused")
	private final Option.Action getFish = action("Fish Audio key", "Open", () -> net.minecraft.util.Util.getOperatingSystem().open("https://fish.audio/app/api-keys/"),
			() -> fishKey.get().isBlank() ? "No key: the computer's voice is used" : "Key saved: Verity's voice");
	public final Option.Text fishVoice = text("Fish Audio Voice ID", "8d21b053e2804e2a890e1cf62f267b6f", 80, "voice model id");
	public final Option.Text fishModel = text("Fish Audio Model", "s2.1-pro-free", 40, "s2.1-pro-free / s2.1-pro / s1");
	public final Option.Bool hearOthers = bool("Hear Other Players' Verity", true);
	public final Option.Bool bubbles = bool("Speech Bubble Above Him", true);

	private final Option.Header hMood = header("Personality");
	public final Option.Bool canAnger = bool("Can Get Angry", true);
	public final Option.Bool canGrow = bool("Grows Huge When Furious", true);
	public final Option.Bool creepy = bool("Gets Creepier Over The Days", true);
	public final Option.Bool chatty = bool("Talks By Himself Sometimes", true);
	@SuppressWarnings("unused")
	private final Option.Action reset = action("Memory", "Forget everything", Verity::forget, () -> Verity.memoryLine());

	public VerityMod() {
		super("Verity", "verity", "Your personal helper friend (special pet): talks with AI, hears you in voice chat, has moods... don't make him angry.",
				Category.UTILITY);
		instance = this;
		apiKey.secret = true;
		fishKey.secret = true;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean on() {
		VerityMod m = instance;
		return m != null && m.enabled;
	}
}
