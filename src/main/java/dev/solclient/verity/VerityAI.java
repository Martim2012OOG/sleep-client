package dev.solclient.verity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Verity's brain and ears on Groq (the player's own free key): chat completions in JSON mode for what he says and how he
 * feels, and Whisper for turning what you said into text. Everything runs off the game thread.
 */
final class VerityAI {

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final String BASE = "https://api.groq.com/openai/v1/";

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
				+ "Always answer in the same language the player wrote in. Do not use emojis or stage directions. "
				+ "Do not repeat the same phrases. Your normal body is a floating yellow smiley; you can change your face.\n");
		s.append("Answer ONLY with a JSON object: {\"say\": \"what you say\", \"mood\": \"happy|normal|bored|annoyed|angry|sad|scared|creepy|furious|excited\", "
				+ "\"anger\": a number from -30 to 30 (how much angrier this makes you; negative calms you down), "
				+ "\"action\": \"none|follow|stay|spin|grow|shrink\"}. Use \"grow\" only when you are furious and want to scare the player.");
		return s.toString();
	}

	/** Asks Verity. history = previous turns (role, content). Throws with a readable message when it fails. */
	static Reply chat(String key, String model, String system, String context, List<String[]> history, String user) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("model", model == null || model.isBlank() ? "llama-3.3-70b-versatile" : model.trim());
		body.addProperty("temperature", 0.9);
		body.addProperty("max_tokens", 220);
		JsonObject fmt = new JsonObject();
		fmt.addProperty("type", "json_object");
		body.add("response_format", fmt);
		JsonArray msgs = new JsonArray();
		msgs.add(msg("system", system));
		msgs.add(msg("system", "Right now: " + context));
		for (String[] h : history) {
			msgs.add(msg(h[0], h[1]));
		}
		msgs.add(msg("user", user));
		body.add("messages", msgs);
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "chat/completions")).timeout(Duration.ofSeconds(25))
				.header("Authorization", "Bearer " + key.trim()).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) {
			throw new IllegalStateException(problem(res.statusCode(), res.body()));
		}
		String content = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message")
				.get("content").getAsString();
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

	/** Speech to text (Whisper on Groq). pcm = 16-bit mono samples. Returns the text, "" when nothing was understood. */
	static String transcribe(String key, short[] pcm, int n, int rate) throws Exception {
		byte[] wav = wav(pcm, n, rate);
		String boundary = "----verity" + System.nanoTime();
		ByteArrayOutputStream out = new ByteArrayOutputStream(wav.length + 600);
		field(out, boundary, "model", "whisper-large-v3-turbo");
		field(out, boundary, "response_format", "json");
		field(out, boundary, "temperature", "0");
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
				.getBytes(StandardCharsets.UTF_8));
		out.write(wav);
		out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "audio/transcriptions")).timeout(Duration.ofSeconds(25))
				.header("Authorization", "Bearer " + key.trim()).header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) {
			throw new IllegalStateException(problem(res.statusCode(), res.body()));
		}
		JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
		return o.has("text") ? o.get("text").getAsString().trim() : "";
	}

	private static String problem(int code, String body) {
		if (code == 401) {
			return "The Groq key is wrong (401)";
		}
		if (code == 429) {
			return "Groq says: too many requests, wait a moment (429)";
		}
		String m = body == null ? "" : body;
		try {
			JsonObject o = JsonParser.parseString(m).getAsJsonObject();
			if (o.has("error")) {
				m = o.getAsJsonObject("error").get("message").getAsString();
			}
		} catch (RuntimeException ignored) {
			// keep the raw body
		}
		return "Groq error " + code + ": " + (m.length() > 120 ? m.substring(0, 120) : m);
	}

	private static JsonObject msg(String role, String content) {
		JsonObject o = new JsonObject();
		o.addProperty("role", role);
		o.addProperty("content", content);
		return o;
	}

	private static void field(ByteArrayOutputStream out, String b, String name, String value) throws java.io.IOException {
		out.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
	}

	static byte[] wav(short[] pcm, int n, int rate) {
		int data = n * 2;
		java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + data).put("WAVE".getBytes(StandardCharsets.US_ASCII));
		b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2)
				.putShort((short) 16);
		b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data);
		for (int i = 0; i < n; i++) {
			b.putShort(pcm[i]);
		}
		return b.array();
	}
}
