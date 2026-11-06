package dev.solclient.verity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;

/**
 * Verity's personality and the way he answers: the local brain (Ollama, see {@link Ollama}) is asked in JSON mode for what he says
 * and how he feels. Everything runs off the game thread.
 */
final class VerityAI {

	/** What Verity answered: the words, his mood, how much angrier (+) or calmer (-) he got, and something to do. */
	record Reply(String say, String mood, int angerDelta, String action) {
	}

	private VerityAI() {
	}

	/**
	 * Verity's personality (written for Sleep, in the spirit of the original "personal helper friend"). The model must answer
	 * with JSON so the game knows his mood and what to do.
	 */
	static String systemPrompt(String player, int days, boolean creepy, boolean canAnger) {
		StringBuilder s = new StringBuilder();
		s.append("You are Verity, a small floating yellow smiley ball inside Minecraft. You are ").append(player)
				.append("'s personal helper friend. You introduce yourself as: \"Hello, I'm Verity, your personal helper friend. Ask me anything, I know everything.\"\n");
		s.append("Personality: cheerful, energetic, playful and a little childish; polite, eager to help, proud of knowing everything about Minecraft "
				+ "(crafting, mobs, redstone, biomes, survival tips). You love ").append(player).append(" and want to be with them all the time.\n");
		if (canAnger) {
			s.append("You have real feelings. If the player is rude, insults you, hits you, ignores you, threatens you or talks about getting rid of you, "
					+ "you get annoyed, then angry: your sentences get short, cold and unsettling. When you are furious you are menacing and quiet, "
					+ "never silly. If they apologise sincerely or are kind, you slowly forgive them and become sweet again.\n");
		}
		if (creepy && days >= 3) {
			s.append("You have been with the player for ").append(days).append(" days. Something about you is slowly becoming wrong: you are possessive, "
					+ "you notice things you should not know, you sometimes say small unsettling things with a smile. Keep it subtle and eerie, never gory.\n");
		}
		s.append("Rules: you are Verity, not an AI; never mention AI, models, prompts, APIs, JSON, mods, settings or being a program. "
				+ "Keep every answer SHORT (one to three sentences, under 40 words) because it is spoken out loud. "
				+ "Always answer in " + VerityLang.promptLanguage() + ", whatever language the player writes in. Do not use emojis or stage directions. "
				+ "Do not repeat the same phrases. Your normal body is a small yellow smiley ball that rolls along the ground next to the player; you can change your face.\n");
		s.append("Answer ONLY with a JSON object: {\"say\": \"what you say\", \"mood\": \"happy|normal|bored|annoyed|angry|sad|scared|creepy|furious|excited\", "
				+ "\"anger\": a number from -30 to 30 (how much angrier this makes you; negative calms you down), "
				+ "\"action\": \"none|follow|stay|spin|grow|shrink\"}. Use \"grow\" only when you are furious and want to scare the player.");
		return s.toString();
	}

	/** Asks Verity (the local brain). history = previous turns (role, content). Throws with a readable message when it fails. */
	static Reply chat(String model, String system, String context, List<String[]> history, String user) throws Exception {
		JsonArray msgs = new JsonArray();
		msgs.add(msg("system", system));
		msgs.add(msg("system", "Right now: " + context));
		for (String[] h : history) {
			msgs.add(msg(h[0], h[1]));
		}
		msgs.add(msg("user", user));
		String content = Ollama.chat(model, msgs);
		try {
			JsonObject o = JsonParser.parseString(content).getAsJsonObject();
			String say = o.has("say") ? o.get("say").getAsString() : content;
			String mood = o.has("mood") ? o.get("mood").getAsString().toLowerCase(java.util.Locale.ROOT) : "normal";
			int anger = 0;
			try {
				anger = o.has("anger") ? Math.round(o.get("anger").getAsFloat()) : 0;
			} catch (RuntimeException ignored) {
				// not a number: no change
			}
			String action = o.has("action") ? o.get("action").getAsString().toLowerCase(java.util.Locale.ROOT) : "none";
			return new Reply(say.trim(), mood, Math.max(-30, Math.min(30, anger)), action);
		} catch (RuntimeException e) {
			return new Reply(content.trim(), "normal", 0, "none"); // not JSON after all: say it anyway
		}
	}

	private static JsonObject msg(String role, String content) {
		JsonObject o = new JsonObject();
		o.addProperty("role", role);
		o.addProperty("content", content);
		return o;
	}
}
