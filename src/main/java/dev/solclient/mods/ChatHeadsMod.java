package dev.solclient.mods;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/** The face of the player who wrote each chat message. */
public class ChatHeadsMod extends Mod {

	public static ChatHeadsMod instance;
	private static final String[] FORMS = {
			"{\"type\":\"object\",\"object\":\"player\",\"player\":{\"name\":\"%s\"}}",
			"{\"object\":\"player\",\"player\":{\"name\":\"%s\"}}",
			"{\"type\":\"object\",\"player\":{\"name\":\"%s\"}}"};
	private static int form = -2; // -2 = not tried yet, -1 = this game version can't show heads in text

	public ChatHeadsMod() {
		super("Chat Heads", "chat_heads", "Shows the face of who wrote each chat message.", Category.UTILITY);
		instance = this;
	}

	private static Text head(String name) {
		int from = form == -2 ? 0 : form, to = form == -2 ? FORMS.length : form + 1;
		if (form == -1) return null;
		for (int i = from; i < to; i++) {
			try {
				var r = TextCodecs.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(String.format(FORMS[i], name))).result();
				if (r.isPresent()) {
					form = i;
					return r.get();
				}
			} catch (RuntimeException ignored) {
				// try the next spelling
			}
		}
		if (form == -2) form = -1;
		return null;
	}

	/** Adds the sender's head in front of a chat line. */
	public static Text decorate(Text msg) {
		ChatHeadsMod m = instance;
		if (m == null || !m.enabled || msg == null || m.mc.getNetworkHandler() == null) return msg;
		String s = msg.getString();
		String name = null;
		for (String tok : s.split("[\\s<>\\[\\]:|»>]+", 6)) {
			if (tok.length() >= 3 && tok.length() <= 16 && tok.matches("[A-Za-z0-9_]+") && m.mc.getNetworkHandler().getPlayerListEntry(tok) != null) {
				name = tok;
				break;
			}
		}
		if (name == null) return msg;
		Text h = head(name);
		if (h == null) return msg;
		MutableText out = Text.empty().append(h).append(" ").append(msg);
		return out;
	}
}
