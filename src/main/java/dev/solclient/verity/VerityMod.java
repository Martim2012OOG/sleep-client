package dev.solclient.verity;

import dev.solclient.mods.Mod;
import dev.solclient.option.Option;

/**
 * Settings of the Verity pet (Locker > Verity > Edit): install his brain, voice and ears (all run on this computer), the language
 * he uses, how you talk to him (voice chat, a key, or the chat) and how far his moods may go (angry, giant, creepier with the days).
 */
public class VerityMod extends Mod {

	public static VerityMod instance;

	private final Option.Header hAi = header("Brain (on this computer)");
	@SuppressWarnings("unused")
	private final Option.Action brain = action("Install Verity's Brain", "Install", Ollama::install, Ollama::status);
	public final Option.Text model = text("Model", Ollama.DEFAULT_MODEL, 80, "qwen2.5:3b");
	public final Option.Choice language = choice("Language", 0, VerityLang.labels());
	public final Option.Text otherLanguage = text("Other Language", "", 40, "e.g. Polish (only when Language is Other)");
	@SuppressWarnings("unused")
	private final Option.Action status = action("Verity Status", "Test", Verity::test, () -> Verity.status);

	private final Option.Header hTalk = header("Talking to Verity");
	public final Option.Bool listenVoice = bool("Listen In Voice Chat", true)
			.tip("Verity hears you through your voice chat (Simple Voice Chat, Plasmo, Sleep's voice chat...). Just talk: he listens.");
	public final Option.Bool wakeWord = bool("Only Answer When You Say His Name", false)
			.tip("Off = you just talk and he listens and answers (voice chat on). On = he only answers when you say \"Verity\", then keeps listening for 20 seconds.");
	public final Option.Key talkKey = key("Talk Key (hold to speak, tap to type)", 74);
	public final Option.Bool chatPrefix = bool("Chat Starting With \"Verity\" Goes To Him", true)
			.tip("Messages that start with Verity, or with @v, are not sent to the server: Verity answers them.");

	private final Option.Header hVoice = header("Voice");
	public final Option.Bool speak = bool("Verity Speaks Out Loud", true);
	@SuppressWarnings("unused")
	private final Option.Action voiceInstall = action("Install Verity's Voice", "Install", Piper::install, Piper::status);
	@SuppressWarnings("unused")
	private final Option.Action earsInstall = action("Install Verity's Ears", "Install", Ears::install, Ears::status);
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
		hiddenFromList = true; // his settings are in the Locker (pet Verity > Edit Verity), not in the mods grid
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
