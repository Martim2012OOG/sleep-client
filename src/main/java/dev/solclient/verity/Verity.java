package dev.solclient.verity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.solclient.Lang;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Verity, the special pet: your personal helper friend. He follows you like the other pets (same brain), but he also
 * talks (Groq, with your own key), hears you in voice chat, and has moods: happy, bored, annoyed, angry... and when he is
 * furious he grows huge. Every Sleep player near you sees his face, his size and his speech bubble, and hears him.
 */
public final class Verity {

	/** For the settings page. */
	public static volatile String status = "";

	// ---- my Verity
	private static float anger, size = 1F, sizeTarget = 1F;
	private static String mood = "normal";
	private static long moodUntil, rageUntil, lastUserAt, lastSpokeAt, lastIdleLineAt, lastBoopAt, hurtLineAt, listenUntil;
	private static int days, lastDay = -1, boops;
	private static String bubble = "";
	private static long bubbleUntil, bubbleId;
	private static final List<String[]> HISTORY = new ArrayList<>();
	private static volatile boolean thinking;
	private static boolean was, keyDown;
	private static long keyDownAt;
	private static float lastHealth = -1;

	// ---- other players' Verity (from their client, through the server room)
	private record Remote(int face, float size, String text, long textId, long at) {
	}

	private static final Map<String, Remote> REMOTE = new ConcurrentHashMap<>();
	/** When each owner's Verity was first seen (for the box he comes out of). Key: player entity id. */
	private static final Map<Integer, Long> SPAWN = new ConcurrentHashMap<>();

	private static boolean hooked;

	private Verity() {
	}

	public static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		load();
		ClientTickEvents.END_CLIENT_TICK.register(Verity::tick);
		ClientSendMessageEvents.ALLOW_CHAT.register(msg -> !onChat(msg));
	}

	/** You wear Verity and the mod is on. */
	public static boolean mine() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || !VerityMod.on()) {
			return false;
		}
		var d = dev.solclient.cosmetic.pet.Pets.petOf(mc.player.getUuid());
		return d != null && "verity".equals(d.pet());
	}

	/** Microphone samples from a voice chat (any thread). */
	public static void feedMic(java.nio.ShortBuffer sb, int count, int rate) {
		VerityVoice.feed(sb, count, rate);
	}

	static boolean listening() {
		VerityMod m = VerityMod.instance;
		return mine() && m.listenVoice.on() && !m.apiKey.get().isBlank() && !thinking;
	}

	// ================================================================ every tick
	private static void tick(MinecraftClient mc) {
		boolean on = mine();
		if (on && !was) {
			SPAWN.put(mc.player.getId(), System.currentTimeMillis()); // he comes out of his box
			if (lastUserAt == 0) {
				lastUserAt = System.currentTimeMillis();
			}
			if (HISTORY.isEmpty()) {
				greet();
			}
		}
		was = on;
		if (!on) {
			VerityVoice.stopRecording();
			return;
		}
		VerityMod m = VerityMod.instance;
		long now = System.currentTimeMillis();
		ClientPlayerEntity p = mc.player;
		// talk key: hold = speak (microphone), tap = type
		boolean down = mc.currentScreen == null && dev.solclient.Keys.down(m.talkKey.code());
		if (down && !keyDown) {
			keyDownAt = now;
		}
		if (down && now - keyDownAt > 250 && !VerityVoice.recording() && !m.apiKey.get().isBlank()) {
			VerityVoice.startRecording();
			mc.inGameHud.setOverlayMessage(Text.literal(Lang.tr("Verity is listening...", "O Verity está a ouvir...")).formatted(Formatting.YELLOW), false);
		}
		if (!down && keyDown) {
			if (VerityVoice.recording()) {
				VerityVoice.stopRecording();
			} else if (now - keyDownAt <= 250) {
				mc.setScreen(new net.minecraft.client.gui.screen.ChatScreen("@v ", false));
			}
		}
		keyDown = down;
		// days together
		int day = (int) (mc.world.getTimeOfDay() / 24000L);
		if (lastDay >= 0 && day != lastDay) {
			days++;
			save();
		}
		lastDay = day;
		// anger cools down slowly
		if (now > rageUntil) {
			anger = Math.max(0F, anger - (anger > 60 ? 0.01F : 0.03F));
			if (rageUntil != 0 && sizeTarget > 1F) {
				sizeTarget = 1F; // the rage is over
				rageUntil = 0;
				if (anger > 70) {
					anger = 70;
				}
			}
		} else if ((now / 1200) % 2 == 0 && ThreadLocalRandom.current().nextInt(24) == 0) {
			double[] pos = dev.solclient.cosmetic.pet.Pets.petPos(p.getId());
			if (pos != null) {
				mc.world.playSoundClient(pos[0], pos[1], pos[2], SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.NEUTRAL, 1.4F, 0.7F, false);
			}
		}
		size += (sizeTarget - size) * 0.06F;
		// he notices things
		float hp = p.getHealth();
		if (lastHealth >= 0 && hp < lastHealth && hp <= 6F && now - hurtLineAt > 20_000L) {
			hurtLineAt = now;
			event(Lang.tr("The player got badly hurt, health " + Math.round(hp) + "/20. React.", "O jogador ficou muito ferido, vida " + Math.round(hp) + "/20. Reage."));
		}
		lastHealth = hp;
		if (m.chatty.on() && now - lastUserAt > 5 * 60_000L && now - lastIdleLineAt > 6 * 60_000L && ThreadLocalRandom.current().nextInt(600) == 0) {
			lastIdleLineAt = now;
			event("The player has not talked to you for a few minutes. Say something to them (a tip, a question, or how you feel being ignored).");
		}
		if (now - lastUserAt > 3 * 60_000L && now > moodUntil && anger < 20) {
			mood = "bored";
		}
	}

	// ================================================================ talking
	/** Chat messages starting with Verity / @v go to him (not to the server). Returns true when he took it. */
	private static boolean onChat(String msg) {
		VerityMod m = VerityMod.instance;
		if (!mine() || m == null || msg == null) {
			return false;
		}
		String t = msg.trim(), low = t.toLowerCase(Locale.ROOT);
		String body = null;
		if (low.startsWith("@v ") || low.startsWith("@verity")) {
			body = t.substring(low.startsWith("@verity") ? 7 : 3).trim();
		} else if (m.chatPrefix.on() && (low.startsWith("verity ") || low.startsWith("verity,") || low.startsWith("verity!") || low.equals("verity"))) {
			body = t.substring(6).replaceFirst("^[,!:\\s]+", "").trim();
		}
		if (body == null) {
			return false;
		}
		String said = body.isEmpty() ? "Verity?" : body;
		MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("<" + MinecraftClient.getInstance().getSession().getUsername() + " → Verity> " + said)
				.formatted(Formatting.GRAY));
		ask(said);
		return true;
	}

	/** What the microphone heard (voice chat or talk key): to text, then to Verity if it was for him. */
	static void heard(short[] pcm, int n, int rate, boolean voiceChat) {
		VerityMod m = VerityMod.instance;
		if (m == null || m.apiKey.get().isBlank()) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				String text = VerityAI.transcribe(m.apiKey.get(), pcm, n, rate);
				if (text.isBlank() || text.length() < 2) {
					return;
				}
				String low = text.toLowerCase(Locale.ROOT);
				boolean named = low.contains("verity") || low.contains("veriti") || low.contains("very tea") || low.contains("ferity") || low.contains("verit");
				boolean forHim = !voiceChat || !m.wakeWord.on() || named || System.currentTimeMillis() < listenUntil;
				if (!forHim) {
					return;
				}
				MinecraftClient.getInstance().execute(() -> {
					MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("🎤 " + text).formatted(Formatting.GRAY));
					ask(text);
				});
			} catch (Exception e) {
				status = e.getMessage();
			}
		}, "kubo-verity-ears");
		t.setDaemon(true);
		t.start();
	}

	/** Something happened that Verity should react to (not said by the player). */
	private static void event(String what) {
		VerityMod m = VerityMod.instance;
		if (m == null || m.apiKey.get().isBlank() || thinking) {
			return;
		}
		send("[event, not said by the player] " + what, false);
	}

	private static void ask(String text) {
		VerityMod m = VerityMod.instance;
		lastUserAt = System.currentTimeMillis();
		if (m.apiKey.get().isBlank()) {
			reply(Lang.tr("I can't think yet! Give me a Groq key: Locker > Verity > Edit.", "Ainda não consigo pensar! Dá-me uma chave Groq: Locker > Verity > Editar."),
					"sad");
			return;
		}
		send(text, true);
	}

	private static void send(String text, boolean fromPlayer) {
		VerityMod m = VerityMod.instance;
		if (thinking) {
			return;
		}
		thinking = true;
		MinecraftClient mc = MinecraftClient.getInstance();
		String system = VerityAI.systemPrompt(mc.getSession().getUsername(), days, m.creepy.on(), m.canAnger.on());
		String context = context(mc);
		List<String[]> hist = new ArrayList<>(HISTORY);
		String key = m.apiKey.get(), model = m.model.get();
		Thread t = new Thread(() -> {
			try {
				VerityAI.Reply r = VerityAI.chat(key, model, system, context, hist, text);
				mc.execute(() -> {
					thinking = false;
					HISTORY.add(new String[] {"user", text});
					HISTORY.add(new String[] {"assistant", "{\"say\":\"" + r.say().replace("\"", "'") + "\",\"mood\":\"" + r.mood() + "\"}"});
					while (HISTORY.size() > 20) {
						HISTORY.remove(0);
					}
					apply(r);
					save();
				});
			} catch (Exception e) {
				mc.execute(() -> {
					thinking = false;
					status = e.getMessage();
					if (fromPlayer) {
						mc.inGameHud.getChatHud().addMessage(Text.literal("[Verity] " + e.getMessage()).formatted(Formatting.RED));
					}
				});
			}
		}, "kubo-verity-brain");
		t.setDaemon(true);
		t.start();
	}

	private static void apply(VerityAI.Reply r) {
		VerityMod m = VerityMod.instance;
		if (m.canAnger.on()) {
			anger = Math.max(0, Math.min(100, anger + r.angerDelta()));
		}
		if (r.angerDelta() <= -10 && rageUntil != 0) {
			rageUntil = 1; // forgiven: shrinks back
		}
		String md = r.mood();
		if (!m.canAnger.on() && (md.equals("angry") || md.equals("furious"))) {
			md = "annoyed";
		}
		if (m.canAnger.on() && m.canGrow.on() && (anger >= 100 || "grow".equals(r.action()) && (md.equals("furious") || anger > 80))) {
			rage();
		}
		if ("shrink".equals(r.action())) {
			sizeTarget = 1F;
			rageUntil = Math.min(rageUntil, 1);
		}
		reply(r.say(), md);
		status = Lang.tr("Working", "A funcionar") + " (" + VerityMod.instance.model.get() + ")";
	}

	private static void rage() {
		MinecraftClient mc = MinecraftClient.getInstance();
		anger = 100;
		rageUntil = System.currentTimeMillis() + 30_000L;
		sizeTarget = 7F;
		mood = "furious";
		moodUntil = rageUntil;
		double[] pos = dev.solclient.cosmetic.pet.Pets.petPos(mc.player.getId());
		if (pos != null) {
			mc.world.playSoundClient(pos[0], pos[1], pos[2], SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.NEUTRAL, 1F, 1.3F, false);
		}
	}

	/** Verity says something: chat line, speech bubble (everyone near sees it) and his voice. */
	private static void reply(String say, String md) {
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = System.currentTimeMillis();
		mood = md;
		moodUntil = now + 12_000L;
		lastSpokeAt = now;
		listenUntil = now + 20_000L; // a conversation: keeps listening without his name for a moment
		bubble = say;
		bubbleId = now;
		bubbleUntil = now + Math.max(4000L, say.length() * 75L);
		mc.inGameHud.getChatHud().addMessage(Text.literal("<").append(Text.literal("Verity").formatted(color(md)))
				.append(Text.literal("> " + say)));
		if (VerityMod.instance.speak.on()) {
			VerityVoice.say(say, md);
		}
	}

	private static Formatting color(String md) {
		return switch (md) {
			case "angry", "furious" -> Formatting.RED;
			case "creepy" -> Formatting.DARK_RED;
			case "sad", "scared" -> Formatting.BLUE;
			default -> Formatting.YELLOW;
		};
	}

	private static void greet() {
		reply(Lang.tr("Hello, I'm Verity, your personal helper friend! Ask me anything, I know everything.",
				"Olá, eu sou o Verity, o teu amigo ajudante pessoal! Pergunta-me qualquer coisa, eu sei tudo."), "happy");
	}

	private static String context(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		StringBuilder s = new StringBuilder();
		long t = mc.world.getTimeOfDay() % 24000L;
		s.append("player health ").append(Math.round(p.getHealth())).append("/20, food ").append(p.getHungerManager().getFoodLevel()).append("/20; ");
		s.append(t < 12500 ? "daytime" : "night").append(", ").append(mc.world.isRaining() ? (mc.world.isThundering() ? "thunderstorm" : "raining") : "clear sky")
				.append("; dimension ").append(mc.world.getRegistryKey().getValue().getPath());
		mc.world.getBiome(p.getBlockPos()).getKey().ifPresent(k -> s.append(", biome ").append(k.getValue().getPath()));
		s.append(", y=").append(p.getBlockY());
		if (!p.getMainHandStack().isEmpty()) {
			s.append("; holding ").append(p.getMainHandStack().getName().getString());
		}
		int monsters = mc.world.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(16), e -> true).size();
		if (monsters > 0) {
			s.append("; ").append(monsters).append(" monsters nearby");
		}
		s.append("; days together: ").append(days).append("; your anger: ").append(Math.round(anger)).append("/100");
		if (rageUntil > System.currentTimeMillis()) {
			s.append(" (you are HUGE and furious right now)");
		}
		s.append("; times the player hit you recently: ").append(boops);
		return s.toString();
	}

	// ================================================================ being touched
	/** Right click on him (petting). */
	public static void onPet() {
		if (!mine()) {
			return;
		}
		anger = Math.max(0, anger - 6);
		boops = Math.max(0, boops - 1);
		if (anger < 30) {
			mood = "happy";
			moodUntil = System.currentTimeMillis() + 3000L;
		}
		lastUserAt = System.currentTimeMillis();
	}

	/** Left click on him (a hit). He gets annoyed, then angry, then... */
	public static void onHit() {
		if (!mine()) {
			return;
		}
		VerityMod m = VerityMod.instance;
		long now = System.currentTimeMillis();
		boops = now - lastBoopAt < 60_000L ? boops + 1 : 1;
		lastBoopAt = now;
		lastUserAt = now;
		if (!m.canAnger.on()) {
			mood = "sad";
			moodUntil = now + 3000L;
			return;
		}
		anger = Math.min(100, anger + 14);
		mood = anger > 70 ? "angry" : "annoyed";
		moodUntil = now + 5000L;
		if (anger >= 100 && m.canGrow.on()) {
			rage();
		}
		if (boops == 2 || boops == 4 || anger >= 100) {
			if (!m.apiKey.get().isBlank()) {
				event("The player just hit you (" + boops + " times in the last minute). React to it.");
			} else {
				String[] lines = anger >= 100 ? new String[] {Lang.tr("Stop. Now.", "Para. Já."), Lang.tr("You will regret that.", "Vais arrepender-te disso.")}
						: new String[] {Lang.tr("Ow! Why would you do that?", "Au! Porque fizeste isso?"), Lang.tr("That's not very nice...", "Isso não foi nada simpático...")};
				reply(lines[ThreadLocalRandom.current().nextInt(lines.length)], mood);
			}
		}
	}

	// ================================================================ what the renderer needs
	/** Face picture (verity_fN) for a player's Verity. */
	public static int face(int ownerEntityId, UUID owner) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && ownerEntityId == mc.player.getId()) {
			return myFace();
		}
		Remote r = owner == null ? null : REMOTE.get(key(owner));
		return r == null || System.currentTimeMillis() - r.at > 60_000L ? 13 : r.face;
	}

	public static float size(int ownerEntityId, UUID owner) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && ownerEntityId == mc.player.getId()) {
			return size;
		}
		Remote r = owner == null ? null : REMOTE.get(key(owner));
		return r == null || System.currentTimeMillis() - r.at > 60_000L ? 1F : r.size;
	}

	/** The speech bubble above a player's Verity ("" = none). */
	public static String bubble(int ownerEntityId, UUID owner) {
		VerityMod m = VerityMod.instance;
		if (m == null || !m.bubbles.on()) {
			return "";
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = System.currentTimeMillis();
		if (mc.player != null && ownerEntityId == mc.player.getId()) {
			return now < bubbleUntil ? bubble : thinking ? "..." : "";
		}
		Remote r = owner == null ? null : REMOTE.get(key(owner));
		return r == null || r.text.isEmpty() || now - r.at > Math.max(4000L, r.text.length() * 75L) ? "" : r.text;
	}

	/** When this player's Verity came out of his box (0 = long ago). */
	public static long spawnedAt(int ownerEntityId) {
		return SPAWN.getOrDefault(ownerEntityId, 0L);
	}

	public static void seen(int ownerEntityId) {
		SPAWN.putIfAbsent(ownerEntityId, System.currentTimeMillis());
	}

	private static int myFace() {
		long now = System.currentTimeMillis();
		String md = now < moodUntil ? mood : anger >= 75 ? "angry" : anger >= 45 ? "annoyed" : mood.equals("bored") ? "bored" : "normal";
		if (rageUntil > now) {
			return (now / 400) % 2 == 0 ? 7 : 8;
		}
		int f = faceOf(md);
		// mouth moves while he talks
		if (VerityVoice.speaking() && (now / 140) % 2 == 0) {
			f = switch (md) {
				case "angry", "annoyed" -> 6;
				case "creepy", "furious" -> 9;
				case "sad", "scared" -> 4;
				default -> 3;
			};
		}
		// the creepy days: now and then, for a split second, the smile is wrong
		if (VerityMod.instance.creepy.on() && days >= 3 && (now / 100) % 97 == 0) {
			f = 10;
		}
		return f;
	}

	private static int faceOf(String md) {
		return switch (md) {
			case "happy", "excited" -> 11;
			case "bored" -> 12;
			case "annoyed" -> 5;
			case "angry" -> 1;
			case "sad" -> 2;
			case "scared" -> 4;
			case "creepy" -> 10;
			case "furious" -> 8;
			default -> 13;
		};
	}

	// ================================================================ other players see it (server room)
	/** Changes whenever what the others must see changes (so it is sent right away). */
	public static String syncSig() {
		if (!mine()) {
			return "";
		}
		return myFace() + "|" + Math.round(size * 4) + "|" + bubbleId;
	}

	public static void write(JsonObject kx) {
		if (!mine()) {
			return;
		}
		long now = System.currentTimeMillis();
		kx.addProperty("vf", myFace());
		kx.addProperty("vz", Math.round(size * 100) / 100F);
		if (now < bubbleUntil) {
			kx.addProperty("vt", bubble.length() > 220 ? bubble.substring(0, 220) : bubble);
			kx.addProperty("vi", bubbleId);
			kx.addProperty("vm", mood);
		}
	}

	public static void onRemote(String uuid, String nick, JsonObject kx) {
		if (uuid == null || !kx.has("vf")) {
			return;
		}
		String k = uuid.replace("-", "").toLowerCase(Locale.ROOT);
		Remote old = REMOTE.get(k);
		String text = kx.has("vt") ? kx.get("vt").getAsString() : "";
		long id = kx.has("vi") ? kx.get("vi").getAsLong() : 0;
		long at = old != null && old.textId == id ? old.at : System.currentTimeMillis();
		REMOTE.put(k, new Remote(kx.get("vf").getAsInt(), kx.has("vz") ? kx.get("vz").getAsFloat() : 1F, text, id, text.isEmpty() ? System.currentTimeMillis() : at));
		if (id != 0 && (old == null || old.textId != id) && !text.isEmpty()) {
			// a new line from someone else's Verity: show it (and say it) when they are close
			MinecraftClient mc = MinecraftClient.getInstance();
			var owner = mc.world == null ? null : mc.world.getPlayers().stream().filter(pl -> pl.getUuid().toString().replace("-", "").equalsIgnoreCase(k)).findFirst()
					.orElse(null);
			if (owner != null && mc.player != null && owner.squaredDistanceTo(mc.player) < 32 * 32) {
				mc.inGameHud.getChatHud().addMessage(Text.literal("<").append(Text.literal("Verity").formatted(Formatting.YELLOW))
						.append(Text.literal(" (" + (nick == null ? owner.getName().getString() : nick) + ")> " + text).formatted(Formatting.GRAY)));
				VerityMod m = VerityMod.instance;
				if (m != null && m.hearOthers.on()) {
					VerityVoice.say(text, kx.has("vm") ? kx.get("vm").getAsString() : "normal");
				}
			}
		}
	}

	private static String key(UUID u) {
		return u.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	// ================================================================ settings page
	static void test() {
		VerityMod m = VerityMod.instance;
		if (m.apiKey.get().isBlank()) {
			status = Lang.tr("Paste your Groq key first", "Cola primeiro a tua chave Groq");
			return;
		}
		status = Lang.tr("Asking Verity...", "A perguntar ao Verity...");
		String key = m.apiKey.get(), model = m.model.get();
		Thread t = new Thread(() -> {
			try {
				VerityAI.Reply r = VerityAI.chat(key, model, VerityAI.systemPrompt("Player", 0, false, true), "testing", List.of(), "Say hi in five words.");
				status = "OK: " + r.say();
			} catch (Exception e) {
				status = e.getMessage();
			}
		}, "kubo-verity-test");
		t.setDaemon(true);
		t.start();
	}

	static void forget() {
		HISTORY.clear();
		anger = 0;
		days = 0;
		boops = 0;
		save();
		status = Lang.tr("Verity forgot everything", "O Verity esqueceu tudo");
	}

	static String memoryLine() {
		return HISTORY.size() / 2 + Lang.tr(" messages remembered, ", " mensagens lembradas, ") + days + Lang.tr(" days together", " dias juntos");
	}

	// ================================================================ memory on disk
	private static final Gson GSON = new Gson();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-verity.json");
	}

	private static void load() {
		try {
			if (Files.exists(file())) {
				JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
				anger = o.has("anger") ? o.get("anger").getAsFloat() : 0;
				days = o.has("days") ? o.get("days").getAsInt() : 0;
				if (o.has("history")) {
					for (var e : o.getAsJsonArray("history")) {
						JsonArray a = e.getAsJsonArray();
						HISTORY.add(new String[] {a.get(0).getAsString(), a.get(1).getAsString()});
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] verity memory: " + e);
		}
	}

	private static void save() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("anger", Math.min(anger, 70));
			o.addProperty("days", days);
			JsonArray h = new JsonArray();
			for (String[] s : HISTORY) {
				JsonArray a = new JsonArray();
				a.add(s[0]);
				a.add(s[1]);
				h.add(a);
			}
			o.add("history", h);
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			System.err.println("[Sleep] verity memory: " + e);
		}
	}
}
