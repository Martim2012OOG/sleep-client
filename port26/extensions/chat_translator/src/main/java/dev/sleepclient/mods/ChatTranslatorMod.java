package dev.sleepclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.sleepclient.option.Option;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Chat Translator: messages in other languages get a translated line under them, and you can write in another
 * language with "!en hello" (sends it translated to English). Uses Google Translate's free endpoint.
 */
public class ChatTranslatorMod extends Mod {

	public static ChatTranslatorMod instance;
	private static final String[] CODES = {"pt", "en", "es", "fr", "de", "it", "pt-BR"};
	private final Option.Choice target = choice("Translate To", 0, "Português", "English", "Español", "Français", "Deutsch", "Italiano",
			"Português (Brasil)");
	private final Option.Bool incoming = bool("Translate Messages I Get", true);
	private final Option.Bool outgoing = bool("!en / !es ... Translates What I Send", true);
	private final Option.Bool serverMessages = bool("Also Server Messages", false);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
	private static boolean hooked;
	private long lastReq;

	public ChatTranslatorMod() {
		super("Chat Translator", "chat_translator", "Translates chat from other languages, and lets you write in any language with !en.", Category.UTILITY);
		instance = this;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> {
			if (instance != null && instance.enabled && instance.incoming.on()) {
				String who = sender == null ? null : NickHiderMod.profileNameOf(sender);
				instance.onIncoming(message.getString(), who);
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay && instance != null && instance.enabled && instance.incoming.on()) {
				String s = message.getString();
				// most servers send player chat as "game" messages: "<Nick> hi", "Nick: hi", "[Rank] Nick » hi"
				if (instance.serverMessages.on() || s.matches("^(\\[[^\\]]{1,20}\\] ?)*<?[A-Za-z0-9_]{3,16}>? ?[:»>] .+")) {
					instance.onIncoming(s, null);
				}
			}
		});
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			if (instance == null || !instance.enabled || !instance.outgoing.on()) {
				return true;
			}
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("^!([a-zA-Z]{2}(?:-[a-zA-Z]{2})?) (.+)$").matcher(message);
			if (!m.matches()) {
				return true;
			}
			String lang = m.group(1).toLowerCase(Locale.ROOT);
			String body = m.group(2);
			translate(body, lang, (out, from) -> instance.mc.execute(() -> {
				var nh = instance.mc.getConnection();
				if (nh != null) {
					nh.sendChat(out == null ? body : out);
				}
			}));
			return false; // sent after translating
		});
	}

	private void onIncoming(String line, String who) {
		String me = mc.getUser().getName();
		if (line == null || line.length() < 4 || line.length() > 256 || who != null && who.equalsIgnoreCase(me) || line.contains("<" + me + ">")
				|| line.startsWith(me + ":")) {
			return;
		}
		// only the message part after "Nick: " when we can tell
		String body = line;
		int cut = line.indexOf("> ");
		if (cut < 0 || cut > 40) {
			cut = line.indexOf(": ");
		}
		String prefix = "";
		if (cut > 0 && cut < 40) {
			prefix = line.substring(0, cut).replaceAll("[<>\\[\\]]", "").trim();
			body = line.substring(cut + 2);
		}
		if (body.replaceAll("[^\\p{L}]", "").length() < 3) {
			return; // only emojis / numbers
		}
		long now = System.currentTimeMillis();
		if (now - lastReq < 250) {
			return;
		}
		lastReq = now;
		String tl = CODES[target.idx()];
		String fp = prefix;
		final String fbody = body;
		translate(body, tl, (out, from) -> {
			if (out == null || from == null || from.equalsIgnoreCase(tl) || tl.startsWith(from) || out.equalsIgnoreCase(fbody)) {
				return;
			}
			mc.execute(() -> {
				if (mc.gui != null) {
					Component t = Component.literal("  ↳ ").withStyle(ChatFormatting.DARK_GRAY)
							.append(Component.literal("[" + from.toUpperCase(Locale.ROOT) + "] ").withStyle(ChatFormatting.AQUA))
							.append(Component.literal((fp.isEmpty() ? "" : fp + ": ") + out).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					dev.sleepclient.compat.Mc.chat(mc).addClientSystemMessage(t);
				}
			});
		});
	}

	/** Translates text (any thread). done(translation, detected source language) or (null, null) on failure. */
	public static void translate(String text, String to, java.util.function.BiConsumer<String, String> done) {
		String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
				+ "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("User-Agent", "Mozilla/5.0").GET().build();
		CompletableFuture<HttpResponse<String>> f = HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		f.whenComplete((res, err) -> {
			if (err != null || res == null || res.statusCode() != 200) {
				done.accept(null, null);
				return;
			}
			try {
				JsonArray root = JsonParser.parseString(res.body()).getAsJsonArray();
				StringBuilder sb = new StringBuilder();
				for (JsonElement part : root.get(0).getAsJsonArray()) {
					JsonArray p = part.getAsJsonArray();
					if (!p.get(0).isJsonNull()) {
						sb.append(p.get(0).getAsString());
					}
				}
				String from = root.size() > 2 && !root.get(2).isJsonNull() ? root.get(2).getAsString() : null;
				done.accept(sb.toString(), from);
			} catch (RuntimeException e) {
				done.accept(null, null);
			}
		});
	}
}
