package dev.solclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.solclient.Lang;
import dev.solclient.mods.MusicShareMod;
import dev.solclient.social.Presence;
import dev.solclient.social.Social;
import dev.solclient.spotify.SpotifyClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Music Share: what you listen to shows above your head for the other Sleep players on the server (a note, the song, a headphones icon),
 * and anybody can ask to listen along: when the host says yes, the listener's Spotify plays the same song at the same moment and follows
 * the host. Everything travels in the same small "kx" messages as the badges (no site / backend involved), at most a couple per second, and
 * all Spotify calls run on a worker thread: nobody lags. At most 10 listeners per host.
 * <p>
 * Privacy: "nobody" never sends the song, only that something is playing (shown as a note and a scrambled name). "Friends" sends the song
 * and other Sleep clients show it only to the host's friends.
 */
public final class MusicShare {

	public static final int MAX_LISTENERS = 10;
	private static final StyleSpriteSource FONT = new StyleSpriteSource.Font(Identifier.of("sol_client", "music"));
	private static final String NOTE = "", PHONES = "";
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Sleep music share");
		t.setDaemon(true);
		return t;
	});

	/** What another player is listening to (as they told us). */
	public static final class Remote {
		public String uuid, nick = "", title = "", artist = "", source = "", cover = "";
		public int vis, lis;
		public long posMs, durMs, posAt, seen;
		public boolean playing, hasSong, stream;
		public final List<String> listeners = new ArrayList<>();
	}

	private static final Map<String, Remote> REMOTE = new ConcurrentHashMap<>();
	/** People listening to ME: uuid -> last heartbeat. */
	private static final Map<String, Long> LISTENERS = new LinkedHashMap<>();
	private static final Map<String, String> NICKS = new ConcurrentHashMap<>();
	/** Requests waiting for my answer: uuid -> nick. */
	private static final Map<String, String> PENDING = new LinkedHashMap<>();
	private static final ArrayDeque<JsonObject> OUT = new ArrayDeque<>();

	/** The host I listen along with (no dashes), or null. */
	public static volatile String following;
	private static String followKey = "";
	private static boolean followPlaying = true;
	private static long followStartedAt, nextHb, nextDrift, lastFollowFail;
	private static boolean followBusy;

	private static String lastSig = "";
	private static long lastSent, nextBeat;
	private static boolean wasOn;

	private MusicShare() {
	}

	// ------------------------------------------------------------------ helpers
	private static String me() {
		UUID u = MinecraftClient.getInstance().getSession().getUuidOrNull();
		return u == null ? "me" : u.toString().replace("-", "");
	}

	private static String key(UUID u) {
		return u.toString().replace("-", "");
	}

	public static boolean isFriend(String uuid) {
		if (uuid == null) {
			return false;
		}
		for (Social.User f : Social.friends) {
			if (f.mcUuid != null && f.mcUuid.replace("-", "").equalsIgnoreCase(uuid)) {
				return true;
			}
		}
		return false;
	}

	private static void say(String msg, boolean error) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.inGameHud != null) {
			mc.inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("[Music] ").formatted(Formatting.GREEN))
					.append(Text.literal(msg).formatted(error ? Formatting.RED : Formatting.GRAY)));
		}
	}

	private static String nickOf(String uuid) {
		Remote r = REMOTE.get(uuid);
		if (r != null && !r.nick.isEmpty()) {
			return r.nick;
		}
		String n = NICKS.get(uuid);
		return n == null ? "player" : n;
	}

	/** My own song right now (from the music player this computer is using), or null. */
	private static Track mine() {
		return MusicHub.hasTrack && !MusicHub.title.isEmpty() ? new Track(MusicHub.source, "", MusicHub.title, MusicHub.artist, "", MusicHub.durationMs,
				MusicHub.progressMs + (MusicHub.playing ? Math.max(0, System.currentTimeMillis() - MusicHub.fetchedAt) : 0), MusicHub.playing, null) : null;
	}

	// ------------------------------------------------------------------ what others see
	/** Can I see the song (not just "something")? */
	public static boolean songVisible(Remote r) {
		return r.hasSong && (r.vis == 2 || r.vis == 1 && isFriend(r.uuid));
	}

	/** Can I ask to listen along? */
	public static boolean canListen(Remote r) {
		return r.hasSong && (r.lis == 2 || r.lis == 1 && isFriend(r.uuid));
	}

	public static Remote remote(String uuid) {
		Remote r = REMOTE.get(uuid);
		return r != null && System.currentTimeMillis() - r.seen < 15_000L ? r : null;
	}

	public static List<Remote> remotes() {
		long now = System.currentTimeMillis();
		List<Remote> out = new ArrayList<>();
		for (Remote r : REMOTE.values()) {
			if (now - r.seen < 15_000L) {
				out.add(r);
			}
		}
		return out;
	}

	private static MutableText glyph(String g, int rgb) {
		return Text.literal(g).setStyle(Style.EMPTY.withFont(FONT).withColor(rgb));
	}

	/** A short window of the title that slides from one side to the other (like the signs in a supermarket) when the title is longer than the window. */
	private static String marquee(String s, int win) {
		if (s.length() <= win) {
			return s;
		}
		String loop = s + "   ·   ";
		int off = (int) ((System.currentTimeMillis() / 240L) % loop.length());
		String two = loop + loop;
		return two.substring(off, off + win);
	}

	private static String clip(String s, int max) {
		return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
	}

	/** The line above a player's name (null = nothing to show for this player). */
	public static Text tagFor(UUID id, String nick, double distSq) {
		MusicShareMod m = MusicShareMod.instance;
		if (m == null || !m.enabled || !m.tags() || distSq > m.tagRange() * m.tagRange()) {
			return null;
		}
		String u = key(id);
		// servers in offline mode (many of them) give the player an uuid made from the name: it is not the account's. The nick is what matches.
		boolean mine = u.equals(me()) || nick != null && nick.equalsIgnoreCase(MinecraftClient.getInstance().getSession().getUsername());
		if (mine) {
			if (!m.tagMine()) {
				return null;
			}
			Track t = mine();
			if (t == null) {
				return null;
			}
			boolean show = m.showToIdx() != 2;
			return build(show ? t.title() : null, true, LISTENERS.size(), show && hasCover(MusicHub.artUrl));
		}
		Remote r = remote(u);
		if (r == null && nick != null) {
			r = remoteByNick(nick);
		}
		if (r == null || !r.hasSong) {
			return null;
		}
		boolean visible = songVisible(r);
		return build(visible ? r.title : null, canListen(r), r.listeners.size(), visible && hasCover(r.cover));
	}

	/** The cover picture to show above this player's head (null = none): mine, or the one the other player sent. */
	public static String coverFor(UUID id, String nick) {
		String u = key(id);
		boolean mine = u.equals(me()) || nick != null && nick.equalsIgnoreCase(MinecraftClient.getInstance().getSession().getUsername());
		if (mine) {
			MusicShareMod m = MusicShareMod.instance;
			return m != null && m.showToIdx() != 2 && hasCover(MusicHub.artUrl) ? MusicHub.artUrl : null;
		}
		Remote r = remote(u);
		if (r == null && nick != null) {
			r = remoteByNick(nick);
		}
		return r != null && songVisible(r) && hasCover(r.cover) ? r.cover : null;
	}

	private static boolean hasCover(String url) {
		return url != null && url.startsWith("https://") && dev.solclient.social.RemoteTextures.get(url) != null;
	}

	/** The player sharing music under this nick (when the entity's uuid is not the account's). */
	private static Remote remoteByNick(String nick) {
		long now = System.currentTimeMillis();
		for (Remote r : REMOTE.values()) {
			String n = r.nick != null && !r.nick.isEmpty() ? r.nick : NICKS.get(r.uuid);
			if (n != null && n.equalsIgnoreCase(nick) && now - r.seen < 15_000L) {
				return r;
			}
		}
		return null;
	}

	/** note + song (or a scrambled name) + headphones (when you can listen along) + how many listen. */
	private static Text build(String title, boolean listenable, int listeners, boolean cover) {
		MutableText t = Text.empty();
		// with a cover the picture takes the place of the note (the room is left here, the picture is drawn by MusicTagMixin)
		t.append(cover ? Text.literal("  ") : glyph(NOTE, 0x55FF99)).append(Text.literal(" "));
		if (title == null) {
			// hidden: the name is scrambled like enchanting-table text
			t.append(Text.literal("MMMMMMMMMMMM").setStyle(Style.EMPTY.withColor(0xB8BCC8).withObfuscated(true)));
		} else {
			t.append(Text.literal(marquee(title, 16)).setStyle(Style.EMPTY.withColor(0xFFFFFF)));
		}
		if (listenable) {
			t.append(Text.literal(" ")).append(glyph(PHONES, 0xA8E6FF));
		}
		if (listeners > 0) {
			t.append(Text.literal(" " + listeners).setStyle(Style.EMPTY.withColor(0xA8E6FF)));
		}
		return t;
	}

	// ------------------------------------------------------------------ incoming
	/** A "mu" (what a player listens to) and / or "mi" (listen-along messages) arrived from the server room (client thread). */
	public static void onRemote(String uuid, String nick, JsonObject kx) {
		if (uuid == null || uuid.equals(me())) {
			return;
		}
		if (nick != null && !nick.isEmpty()) {
			NICKS.put(uuid, nick);
		}
		long now = System.currentTimeMillis();
		if (kx.has("mu") && kx.get("mu").isJsonObject()) {
			JsonObject o = kx.getAsJsonObject("mu");
			Remote r = REMOTE.computeIfAbsent(uuid, k -> new Remote());
			r.uuid = uuid;
			if (nick != null && !nick.isEmpty()) {
				r.nick = nick;
			}
			r.hasSong = o.has("on") && o.get("on").getAsInt() == 1;
			r.vis = o.has("v") ? o.get("v").getAsInt() : 0;
			r.lis = o.has("l") ? o.get("l").getAsInt() : 0;
			r.title = o.has("t") ? o.get("t").getAsString() : "";
			r.artist = o.has("a") ? o.get("a").getAsString() : "";
			r.source = o.has("s") ? o.get("s").getAsString() : "";
			String cu = o.has("c") ? o.get("c").getAsString() : "";
			r.cover = cu.startsWith("https://") && cu.length() <= 200 ? cu : "";
			r.posMs = o.has("p") ? o.get("p").getAsLong() : 0;
			r.durMs = o.has("d") ? o.get("d").getAsLong() : 0;
			r.playing = o.has("pl") && o.get("pl").getAsInt() == 1;
			r.stream = o.has("st") && o.get("st").getAsInt() == 1;
			r.posAt = now;
			r.seen = now;
			r.listeners.clear();
			if (o.has("ls") && o.get("ls").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("ls")) {
					if (r.listeners.size() < MAX_LISTENERS) {
						r.listeners.add(e.getAsString());
					}
				}
			}
		}
		if (kx.has("mi") && kx.get("mi").isJsonObject()) {
			JsonObject i = kx.getAsJsonObject("mi");
			if (i.has("to") && me().equals(i.get("to").getAsString())) {
				onMessage(uuid, i.has("ty") ? i.get("ty").getAsString() : "", i.has("w") ? i.get("w").getAsString() : "");
			}
		}
	}

	private static void onMessage(String from, String type, String why) {
		MusicShareMod m = MusicShareMod.instance;
		if (m == null || !m.enabled) {
			return;
		}
		switch (type) {
			case "req" -> {
				Track t = mine();
				if (t == null) {
					send(from, "no", "idle");
					return;
				}
				boolean allowed = m.listenFromIdx() == 0 || m.listenFromIdx() == 1 && isFriend(from);
				if (!allowed) {
					send(from, "no", "off");
				} else if (LISTENERS.size() >= Math.min(MAX_LISTENERS, m.maxListeners()) && !LISTENERS.containsKey(from)) {
					send(from, "no", "full");
				} else if (!m.ask()) {
					accept(from);
				} else {
					PENDING.put(from, nickOf(from));
					MutableText ask = Text.empty().append(Text.literal(nickOf(from)).formatted(Formatting.AQUA))
							.append(Text.literal(Lang.tr(" wants to listen along to what you play. ", " quer ouvir contigo o que estás a ouvir. ")).formatted(Formatting.GRAY))
							.append(Text.literal("[" + Lang.tr("Accept", "Aceitar") + "]").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
									.withClickEvent(new ClickEvent.RunCommand("/sleep music accept " + from))))
							.append(Text.literal(" "))
							.append(Text.literal("[" + Lang.tr("Decline", "Recusar") + "]").setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)
									.withClickEvent(new ClickEvent.RunCommand("/sleep music decline " + from))));
					MinecraftClient mc = MinecraftClient.getInstance();
					if (mc.inGameHud != null) {
						mc.inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("[Music] ").formatted(Formatting.GREEN)).append(ask));
					}
					dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.PLAY, Lang.tr("Listen along request", "Pedido para ouvir contigo"),
							nickOf(from) + Lang.tr(" wants to listen with you (see the chat)", " quer ouvir contigo (vê o chat)"));
				}
			}
			case "ok" -> {
				following = from;
				followKey = "";
				followPlaying = true;
				followStartedAt = System.currentTimeMillis();
				nextHb = 0;
				nextDrift = 0;
				say(Lang.tr("You are listening along with ", "Estás a ouvir com ") + nickOf(from) + ".", false);
			}
			case "no" -> say(nickOf(from) + switch (why) {
				case "full" -> Lang.tr(" has 10 listeners already.", " já tem 10 ouvintes.");
				case "off" -> Lang.tr(" does not allow listening along.", " não deixa ouvir com ele.");
				case "idle" -> Lang.tr(" is not playing anything.", " não está a ouvir nada.");
				default -> Lang.tr(" said no.", " recusou.");
			}, true);
			case "hb" -> {
				if (LISTENERS.containsKey(from)) {
					LISTENERS.put(from, System.currentTimeMillis());
				}
			}
			case "leave" -> {
				if (LISTENERS.remove(from) != null) {
					say(nickOf(from) + Lang.tr(" stopped listening.", " deixou de ouvir."), false);
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ outgoing
	private static void send(String to, String type, String why) {
		JsonObject i = new JsonObject();
		i.addProperty("to", to);
		i.addProperty("ty", type);
		if (why != null && !why.isEmpty()) {
			i.addProperty("w", why);
		}
		synchronized (OUT) {
			OUT.add(i);
		}
	}

	/** Ask a player to listen along with them. */
	public static void request(String uuid) {
		Remote r = remote(uuid);
		if (r == null || !canListen(r)) {
			say(Lang.tr("You cannot listen along with this player.", "Não podes ouvir com este jogador."), true);
			return;
		}
		if (!SpotifyClient.connected && !r.stream) {
			say(Lang.tr("To listen along connect Spotify first (Settings > Music) - Spotify Premium is needed.",
					"Para ouvir com alguém liga primeiro o Spotify (Definições > Música) - é preciso Spotify Premium."), true);
			return;
		}
		if (uuid.equals(following)) {
			return;
		}
		send(uuid, "req", "");
		say(Lang.tr("Asked ", "Pedido enviado a ") + r.nick + Lang.tr(" if you can listen along...", " para ouvir com ele..."), false);
	}

	public static void accept(String uuid) {
		MusicShareMod m = MusicShareMod.instance;
		PENDING.remove(uuid);
		if (m == null || LISTENERS.size() >= Math.min(MAX_LISTENERS, m.maxListeners())) {
			send(uuid, "no", "full");
			return;
		}
		LISTENERS.put(uuid, System.currentTimeMillis());
		send(uuid, "ok", "");
		lastSig = ""; // tell everybody about the new listener now
		say(nickOf(uuid) + Lang.tr(" is listening along with you now.", " está a ouvir contigo."), false);
	}

	public static void decline(String uuid) {
		PENDING.remove(uuid);
		send(uuid, "no", "");
	}

	/** Stop listening along (the song keeps playing in your player). */
	public static void leave() {
		String h = following;
		if (h != null) {
			send(h, "leave", "");
			following = null;
			say(Lang.tr("You stopped listening along.", "Deixaste de ouvir com ele."), false);
		}
	}

	/** Kick everybody who listens to me. */
	public static void stopHosting() {
		for (String u : new ArrayList<>(LISTENERS.keySet())) {
			send(u, "no", "idle");
		}
		LISTENERS.clear();
	}

	/** The people in my listen-along session: the host (first) and the ones listening to the host, or my own listeners. */
	public static List<String> session() {
		List<String> out = new ArrayList<>();
		String h = following;
		if (h != null) {
			out.add(h);
			Remote r = remote(h);
			if (r != null) {
				out.addAll(r.listeners);
			}
			return out;
		}
		out.addAll(LISTENERS.keySet());
		return out;
	}

	public static boolean hosting() {
		return following == null && !LISTENERS.isEmpty();
	}

	public static String nick(String uuid) {
		return uuid.equals(me()) ? MinecraftClient.getInstance().getSession().getUsername() : nickOf(uuid);
	}

	/** The nearest player in front of you who shares a song you can listen along with (the key of Music Share), or null. */
	/** Said in the chat when the listen key is pressed and nobody is aimed at. */
	public static void noTarget() {
		say(Lang.tr("Look straight at a player who is sharing music (and lets you listen along), then press the key.",
				"Olha direto para um jogador que esteja a partilhar música (e deixe ouvir), e carrega na tecla."), true);
	}

	public static String aimed(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) {
			return null;
		}
		Vec3d eye = mc.player.getEyePos(), look = mc.player.getRotationVec(1F);
		String best = null;
		double bestAngle = Math.cos(Math.toRadians(8));
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player) {
				continue;
			}
			Remote r = remote(key(p.getUuid()));
			if (r == null) {
				// offline-mode servers give the entity an uuid made from the name: the player is then found by the nick
				r = remoteByNick(p.getGameProfile().name());
			}
			if (r == null || !canListen(r)) {
				continue;
			}
			Vec3d d = p.getEyePos().subtract(eye);
			double dist = d.length();
			if (dist > 48 || dist < 0.5) {
				continue;
			}
			double dot = d.normalize().dotProduct(look);
			if (dot > bestAngle) {
				bestAngle = dot;
				best = r.uuid;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ tick
	/** Every client tick while the Music Share mod is on. */
	public static void tick(MinecraftClient mc, MusicShareMod m) {
		long now = System.currentTimeMillis();
		REMOTE.values().removeIf(r -> now - r.seen > 30_000L);
		// listeners that went silent
		LISTENERS.entrySet().removeIf(e -> now - e.getValue() > 12_000L);
		// no world needed: in the title screen, the world list or a loading screen the song is still shared and the listeners keep listening
		Track t = mine();
		boolean on = t != null;
		int vis = m.showToIdx() == 0 ? 2 : m.showToIdx() == 1 ? 1 : 0;
		int lis = m.listenFromIdx() == 0 ? 2 : m.listenFromIdx() == 1 ? 1 : 0;
		if (!on && !LISTENERS.isEmpty()) {
			stopHosting();
		}
		// ---- what I send
		boolean canStream = on && m.streamMine() && AudioStream.capable();
		String sig = on ? t.title() + "|" + t.artist() + "|" + t.playing() + "|" + vis + "|" + lis + "|" + LISTENERS.keySet() + "|" + canStream : "off";
		boolean beat = now >= nextBeat;
		boolean changed = !sig.equals(lastSig);
		JsonObject mu = null;
		if (on && (changed || beat) || !on && wasOn) {
			mu = new JsonObject();
			mu.addProperty("on", on ? 1 : 0);
			if (on) {
				mu.addProperty("v", vis);
				mu.addProperty("l", lis);
				mu.addProperty("st", canStream ? 1 : 0); // "my sound can be streamed": the listener does not need Spotify then
				if (vis > 0) { // nobody: the song is never sent
					mu.addProperty("t", clip(t.title(), 60));
					mu.addProperty("a", clip(t.artist(), 40));
					mu.addProperty("s", t.source());
					String cv = MusicHub.artUrl;
					if (cv != null && cv.startsWith("https://") && cv.length() <= 200) {
						mu.addProperty("c", cv); // the cover: the others show it above the head
					}
				}
				mu.addProperty("p", Math.max(0, t.positionMs()));
				mu.addProperty("d", Math.max(0, t.durationMs()));
				mu.addProperty("pl", t.playing() ? 1 : 0);
				JsonArray ls = new JsonArray();
				for (String u : LISTENERS.keySet()) {
					ls.add(u);
				}
				mu.add("ls", ls);
			}
		}
		JsonObject mi;
		synchronized (OUT) {
			mi = OUT.peek();
		}
		if (mu != null || mi != null) {
			JsonObject kx = new JsonObject();
			kx.addProperty("n", mc.getSession().getUsername());
			if (mu != null) {
				kx.add("mu", mu);
			}
			if (mi != null) {
				kx.add("mi", mi);
			}
			// to the players on this server, and ALWAYS to the global lobby too: that one reaches the friend who is listening with you
			// even when one of you is not in a world (menu, world list) or you are on different servers
			boolean viaSync = dev.solclient.social.SyncLink.sendServer(kx);
			boolean viaLobby = dev.solclient.social.SyncLink.sendLobby(kx);
			if (Presence.sendLink(kx) || viaSync || viaLobby) {
				if (mu != null) {
					lastSig = sig;
					lastSent = now;
					wasOn = on;
					nextBeat = now + (LISTENERS.isEmpty() ? 5000L : 2000L);
				}
				if (mi != null) {
					synchronized (OUT) {
						OUT.poll();
					}
				}
			}
		}
		// ---- sound streaming: capture only while somebody listens; play only when the host we follow streams
		AudioStream.hostWant(canStream && !LISTENERS.isEmpty());
		String fh = following;
		Remote fr = fh == null ? null : remote(fh);
		AudioStream.listenTo(fr != null && fr.stream && songVisible(fr) ? fh : null);
		// ---- listening along
		followTick(mc, now);
	}

	private static void followTick(MinecraftClient mc, long now) {
		String h = following;
		if (h == null) {
			return;
		}
		Remote r = remote(h);
		if (r == null || !r.hasSong) {
			if (now - followStartedAt > 8000L) {
				say(nickOf(h) + Lang.tr(" stopped sharing music.", " deixou de partilhar música."), true);
				following = null;
			}
			return;
		}
		if (now >= nextHb) {
			nextHb = now + 3000L;
			send(h, "hb", "");
		}
		if (r.stream) {
			return; // the sound comes from the host itself (AudioStream): nothing to do with Spotify
		}
		if (followBusy || now < nextDrift) {
			return;
		}
		nextDrift = now + 1000L;
		long hostPos = r.posMs + (r.playing ? now - r.posAt : 0);
		String k = r.title + "\u0001" + r.artist;
		if (!songVisible(r)) {
			return; // we were let in but cannot read the song (friends only): nothing to play
		}
		if (!k.equals(followKey)) {
			if (now - lastFollowFail < 6000L) {
				return;
			}
			followBusy = true;
			final String title = r.title, artist = r.artist;
			final boolean playing = r.playing;
			final long pos = hostPos + 700; // the time it takes to start
			IO.execute(() -> {
				try {
					boolean ok = SpotifyClient.playMatch(title, artist, pos);
					if (ok) {
						followKey = title + "\u0001" + artist;
						followPlaying = true;
						if (!playing) {
							SpotifyClient.setPlaying(false);
							followPlaying = false;
						}
					} else {
						lastFollowFail = System.currentTimeMillis();
						MinecraftClient.getInstance().execute(() -> say(SpotifyClient.status, true));
					}
				} finally {
					followBusy = false;
				}
			});
			return;
		}
		if (r.playing != followPlaying) {
			followPlaying = r.playing;
			IO.execute(() -> SpotifyClient.setPlaying(r.playing));
			return;
		}
		if (r.playing && SpotifyClient.connected && SpotifyClient.hasTrack) {
			long mine = SpotifyClient.progressMs + (SpotifyClient.playing ? Math.max(0, now - SpotifyClient.fetchedAt) : 0);
			if (Math.abs(mine - hostPos) > 4000L) {
				final long to = hostPos + 400;
				IO.execute(() -> SpotifyClient.seek(to));
				nextDrift = now + 4000L;
			}
		}
	}

	/** Width of the tag text (for the HUD / tests). */
	public static int width(TextRenderer tr, Text t) {
		return tr.getWidth(t);
	}
}
