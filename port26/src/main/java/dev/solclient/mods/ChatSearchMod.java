package dev.solclient.mods;

/** Search the chat: a search bar above the chat (button or Ctrl+F) that shows only the lines with the word, highlighted. */
public class ChatSearchMod extends Mod {

	public static ChatSearchMod instance;

	public ChatSearchMod() {
		super("Chat Search", "chat_search", "Search your chat (Ctrl+F in chat): matching lines, highlighted, with next / previous.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
