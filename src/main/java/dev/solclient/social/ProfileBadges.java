package dev.solclient.social;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.solclient.account.Accounts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * Profile badges you can equip (Sleep menu > button left of your name). The equipped badge is drawn at the RIGHT of your
 * name (nametag and tab list). Rules: offline / cracked accounts can only use the pirate badge, Microsoft (original)
 * accounts only the Minecraft one, and the crown is only for the owner's original account (Marzox_x).
 */
public final class ProfileBadges {

	public enum Badge {
		CROWN("crown", "badge_crown", "Owner", "Dono"),
		MINECRAFT("minecraft", "badge_mc", "Original account", "Conta original"),
		PIRATE("pirate", "badge_pirate", "Pirate account", "Conta pirata"),
		HANDS("hands", "badge_hands", "Handshake", "Aperto de mão"),
		BUG_FIXER("bug_fixer", "badge_bug_fixer", "Bug Fixer", "Bug Fixer"),
		BETA_TESTER("beta_tester", "badge_beta_tester", "Beta Tester", "Beta Tester"),
		SON("son", "badge_son", "Son", "Son"),
		NITRO_BRONZE("nitro_bronze", "badge_nitro_bronze", "Boost Bronze", "Boost Bronze"),
		NITRO_SILVER("nitro_silver", "badge_nitro_silver", "Boost Silver", "Boost Prata"),
		NITRO_GOLD("nitro_gold", "badge_nitro_gold", "Boost Gold", "Boost Ouro"),
		NITRO_PLATINUM("nitro_platinum", "badge_nitro_platinum", "Boost Platinum", "Boost Platina"),
		NITRO_DIAMOND("nitro_diamond", "badge_nitro_diamond", "Boost Diamond", "Boost Diamante"),
		NITRO_EMERALD("nitro_emerald", "badge_nitro_emerald", "Boost Emerald", "Boost Esmeralda"),
		NITRO_RUBY("nitro_ruby", "badge_nitro_ruby", "Boost Ruby", "Boost Rubi"),
		NITRO_OPAL("nitro_opal", "badge_nitro_opal", "Boost Opal", "Boost Opala"),
		VIP("vip", "badge_vip", "VIP", "VIP"),
		YOUTUBE("youtube", "badge_youtube", "YouTuber", "YouTuber"),
		TWITCH("twitch", "badge_twitch", "Twitch streamer", "Streamer da Twitch"),
		TIKTOK("tiktok", "badge_tiktok", "TikToker", "TikToker");

		public final String id, font, en, pt;

		Badge(String id, String font, String en, String pt) {
			this.id = id;
			this.font = font;
			this.en = en;
			this.pt = pt;
		}

		/** VIP, YouTube, Twitch and TikTok: the creator badges (they also give a coloured name). */
		public boolean creator() {
			return this == VIP || this == YOUTUBE || this == TWITCH || this == TIKTOK;
		}

		public boolean nitro() {
			return id.startsWith("nitro_");
		}

		/** The 32x32 texture, for the menu. */
		public Identifier texture() {
			return Identifier.of("sol_client", "textures/font/" + font + ".png");
		}
	}

	/** The only account that can wear the crown (a real Microsoft account with this nick). */
	public static final String OWNER_NICK = "Marzox_x";

	/** Accounts (real Microsoft ones) that wear the handshake badge. */
	public static final String[] HANDS_NICKS = {"Itz_Vxiper", "Marzox_x", "NoOneAboveMe"};

	public static final String BUG_FIXER_NICK = "Itz_Vxiper", BETA_TESTER_NICK = "NoOneAboveMe";

	/** Badges that anybody may claim for other players (just for fun / not checkable): the others are checked against the nick lists. */
	public static boolean allowedForNick(String nick, Badge b) {
		if (nick == null) {
			return false;
		}
		if (nick.equalsIgnoreCase(OWNER_NICK)) {
			return true;
		}
		switch (b) {
			case SON:
			case MINECRAFT:
			case PIRATE:
				return true;
			case HANDS:
				for (String n : HANDS_NICKS) {
					if (n.equalsIgnoreCase(nick)) {
						return true;
					}
				}
				return false;
			case BUG_FIXER:
				return nick.equalsIgnoreCase(BUG_FIXER_NICK);
			case BETA_TESTER:
				return nick.equalsIgnoreCase(BETA_TESTER_NICK);
			case VIP:
			case YOUTUBE:
			case TWITCH:
			case TIKTOK:
				return true; // the site decides who has them; the other players only draw what they are told
			default:
				return false;
		}
	}

	// ---- badges you earn by playing (Son: drown to death)
	private static JsonObject earnedData;

	private static Path earnedFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-badges-earned.json");
	}

	private static JsonObject earnedData() {
		if (earnedData == null) {
			earnedData = new JsonObject();
			try {
				if (Files.exists(earnedFile())) {
					JsonObject o = GSON.fromJson(Files.readString(earnedFile()), JsonObject.class);
					if (o != null) {
						earnedData = o;
					}
				}
			} catch (java.io.IOException | RuntimeException ignored) {
				// nothing earned yet
			}
		}
		return earnedData;
	}

	public static boolean earned(Badge b) {
		try {
			return earnedData().has(key()) && earnedData().get(key()).getAsString().contains("," + b.id + ",");
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Unlocks a badge for this account (and tells the player). */
	public static void earn(Badge b) {
		if (earned(b)) {
			return;
		}
		String cur = earnedData().has(key()) ? earnedData().get(key()).getAsString() : ",";
		earnedData().addProperty(key(), cur + b.id + ",");
		try {
			Files.writeString(earnedFile(), GSON.toJson(earnedData()));
		} catch (java.io.IOException ignored) {
			// cannot save
		}
		try {
			Social.say("Badge unlocked: " + b.en, false);
		} catch (Throwable ignored) {
			// no toast
		}
	}

	// ---- badges of the other Sleep players on your server (sent through the presence room)
	/** How long the badge / name style of another player stays after his last message (he repeats it every 10-12 s). */
	private static final long FRESH_MS = 300_000L;
	private static final Map<String, Badge> REMOTE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<String, Long> REMOTE_AT = new java.util.concurrent.ConcurrentHashMap<>();

	/** Another player says which badge he wears ("" = none). Only badges that his nick is allowed to wear are accepted. */
	public static void onRemote(String nick, String id) {
		if (nick == null) {
			return;
		}
		String k = nick.toLowerCase(Locale.ROOT);
		Badge found = null;
		for (Badge b : Badge.values()) {
			if (b.id.equals(id) && allowedForNick(nick, b)) {
				found = b;
			}
		}
		if (found == null) {
			REMOTE.remove(k);
		} else {
			REMOTE.put(k, found);
			REMOTE_AT.put(k, System.currentTimeMillis());
		}
	}

	public static Badge remote(String nick) {
		if (nick == null) {
			return null;
		}
		String k = nick.toLowerCase(Locale.ROOT);
		Long at = REMOTE_AT.get(k);
		if (at == null || System.currentTimeMillis() - at > FRESH_MS) {
			return null;
		}
		return REMOTE.get(k);
	}

	private static long drowningAt;
	private static boolean wasDead;

	/** Every client tick: the Son badge is earned by drowning to death (air all used up, then dying within 3 seconds). */
	public static void tick(MinecraftClient mc) {
		var p = mc.player;
		if (p == null) {
			wasDead = false;
			return;
		}
		long now = System.currentTimeMillis();
		if (p.getAir() <= -18 && p.isSubmergedInWater()) {
			drowningAt = now;
		}
		boolean dead = p.isDead() || p.getHealth() <= 0F;
		if (dead && !wasDead && now - drowningAt < 3000L) {
			earn(Badge.SON);
		}
		wasDead = dead;
	}

	/** The id of the badge you wear, for the others ("" = none). */
	public static String myId() {
		Badge b = equipped();
		return b == null ? "" : b.id;
	}

	private static boolean isOwner() {
		String nick = Accounts.current();
		return original() && nick != null && nick.equalsIgnoreCase(OWNER_NICK);
	}

	private static final Gson GSON = new Gson();
	private static final Map<Badge, Text> TEXTS = new EnumMap<>(Badge.class);
	private static JsonObject saved;

	private ProfileBadges() {
	}

	private static boolean original() {
		return Accounts.currentType().equals("Microsoft");
	}

	/** Can the CURRENT account wear this badge? */
	public static boolean allowed(Badge b) {
		String nick = Accounts.current();
		if (isOwner()) {
			return true; // the owner wears every badge
		}
		switch (b) {
			case SON:
				return earned(b);
			case BUG_FIXER:
				return original() && nick != null && nick.equalsIgnoreCase(BUG_FIXER_NICK);
			case BETA_TESTER:
				return original() && nick != null && nick.equalsIgnoreCase(BETA_TESTER_NICK);
			case HANDS:
				if (original() && nick != null) {
					for (String n : HANDS_NICKS) {
						if (n.equalsIgnoreCase(nick)) {
							return true;
						}
					}
				}
				return false;
			case PIRATE:
				return !original();
			case MINECRAFT:
				return original();
			case CROWN:
				return original() && nick != null && nick.equalsIgnoreCase(OWNER_NICK);
			case VIP:
			case YOUTUBE:
			case TWITCH:
			case TIKTOK:
				return grantedByServer(b); // given to the account by the site
			default:
				return false; // the Discord boost badges: for now only the owner (they do nothing yet)
		}
	}

	/** Badges the site gave to this account (the "badges" list of your profile). */
	private static boolean grantedByServer(Badge b) {
		var me = Social.me;
		return me != null && me.badges != null && me.badges.contains(b.id);
	}

	/** The Owner and Team (handshake) badges can style the name (Name Colour mod). */
	public static boolean canStyleName() {
		return allowed(Badge.CROWN) || allowed(Badge.HANDS);
	}

	// ---- name styles of the other players (only the Owner and Team nicks are believed)
	private static final Map<String, dev.solclient.mods.NameColourMod.Look> REMOTE_LOOK = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<String, Long> REMOTE_LOOK_AT = new java.util.concurrent.ConcurrentHashMap<>();

	public static void onRemoteLook(String nick, String data) {
		if (nick == null) {
			return;
		}
		String k = nick.toLowerCase(Locale.ROOT);
		var l = allowedForNick(nick, Badge.CROWN) || allowedForNick(nick, Badge.HANDS) ? dev.solclient.mods.NameColourMod.decode(data) : null;
		if (l != null && l.look == 4 && !allowedForNick(nick, Badge.CROWN)) {
			l = null; // the custom gradient is the owner's
		}
		if (l == null) {
			REMOTE_LOOK.remove(k);
		} else {
			REMOTE_LOOK.put(k, l);
			REMOTE_LOOK_AT.put(k, System.currentTimeMillis());
		}
	}

	private static dev.solclient.mods.NameColourMod.Look remoteLook(String nick) {
		if (nick == null) {
			return null;
		}
		String k = nick.toLowerCase(Locale.ROOT);
		Long at = REMOTE_LOOK_AT.get(k);
		return at == null || System.currentTimeMillis() - at > FRESH_MS ? null : REMOTE_LOOK.get(k);
	}

	/** Only VIPs can upload their own cape. */
	public static boolean canUploadCape() {
		return allowed(Badge.VIP);
	}

	// ---- coloured name of the creator badges: everybody who uses Sleep sees it (the badge id is already sent to them)
	private static final java.util.Set<String> NO_COLOUR = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static void onRemoteColour(String nick, boolean on) {
		if (nick == null) {
			return;
		}
		if (on) {
			NO_COLOUR.remove(nick.toLowerCase(Locale.ROOT));
		} else {
			NO_COLOUR.add(nick.toLowerCase(Locale.ROOT));
		}
	}

	private static Path colourFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-badge-colour.txt");
	}

	private static Boolean colourOn;

	/** Do you want your name coloured by your creator badge? (default yes) */
	public static boolean myColourOn() {
		if (colourOn == null) {
			colourOn = true;
			try {
				if (Files.exists(colourFile())) {
					colourOn = !Files.readString(colourFile()).trim().equals("off");
				}
			} catch (java.io.IOException ignored) {
				// default
			}
		}
		return colourOn;
	}

	public static void setMyColour(boolean on) {
		colourOn = on;
		try {
			Files.writeString(colourFile(), on ? "on" : "off");
		} catch (java.io.IOException ignored) {
			// cannot save
		}
	}

	/** The name colour of a creator badge: gold / red / purple / (TikTok: light blue - white - pink gradient). */
	private static int[] palette(Badge b) {
		return switch (b) {
			case VIP -> new int[]{0xFFD83A};
			case YOUTUBE -> new int[]{0xFF3B3B};
			case TWITCH -> new int[]{0xA970FF};
			default -> new int[]{0x25F4EE, 0xFFFFFF, 0xFE2C55};
		};
	}

	private static int at(int[] pal, float k) {
		if (pal.length == 1) {
			return pal[0];
		}
		float x = Math.max(0F, Math.min(0.9999F, k)) * (pal.length - 1);
		int i = (int) x;
		float t = x - i;
		int a = pal[i], c = pal[i + 1];
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((c >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((c >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (c & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	/** Paints every place where {@code nick} appears in this text with the colour of the badge. */
	private static Text paint(Text t, String nick, Badge b) {
		if (nick == null || nick.isEmpty() || !t.getString().contains(nick)) {
			return t;
		}
		int[] pal = palette(b);
		net.minecraft.text.MutableText out = Text.empty();
		t.visit((style, str) -> {
			int from = 0, i;
			while ((i = str.indexOf(nick, from)) >= 0) {
				if (i > from) {
					out.append(Text.literal(str.substring(from, i)).setStyle(style));
				}
				int[] cps = nick.codePoints().toArray();
				for (int j = 0; j < cps.length; j++) {
					float k = cps.length <= 1 ? 0F : j / (float) (cps.length - 1);
					out.append(Text.literal(new String(Character.toChars(cps[j]))).setStyle(style.withColor(at(pal, k))));
				}
				from = i + nick.length();
			}
			if (from < str.length()) {
				out.append(Text.literal(str.substring(from)).setStyle(style));
			}
			return java.util.Optional.empty();
		}, net.minecraft.text.Style.EMPTY);
		return out;
	}

	public static Badge[] available() {
		return java.util.Arrays.stream(Badge.values()).filter(ProfileBadges::allowed).toArray(Badge[]::new);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-badge.json");
	}

	private static JsonObject data() {
		if (saved == null) {
			saved = new JsonObject();
			try {
				if (Files.exists(file())) {
					JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
					if (o != null) {
						saved = o;
					}
				}
			} catch (java.io.IOException | RuntimeException e) {
				System.err.println("[Sleep] Could not read badge config: " + e);
			}
		}
		return saved;
	}

	private static String key() {
		String nick = Accounts.current();
		return (original() ? "ms:" : "off:") + (nick == null ? "" : nick.toLowerCase(Locale.ROOT));
	}

	/** The badge equipped on this account (null = none). Never returns one the account is not allowed to wear. */
	public static Badge equipped() {
		try {
			if (!data().has(key())) {
				return null;
			}
			String id = data().get(key()).getAsString();
			for (Badge b : Badge.values()) {
				if (b.id.equals(id) && allowed(b)) {
					return b;
				}
			}
		} catch (RuntimeException ignored) {
			// no badge
		}
		return null;
	}

	/** Equips a badge (or null to take it off). Ignored if this account may not wear it. */
	public static void equip(Badge b) {
		if (b != null && !allowed(b)) {
			return;
		}
		if (b == null) {
			data().remove(key());
		} else {
			data().addProperty(key(), b.id);
		}
		try {
			Files.writeString(file(), GSON.toJson(data()));
		} catch (java.io.IOException e) {
			System.err.println("[Sleep] Could not save badge config: " + e);
		}
	}

	private static Text gapText;

	/**
	 * The small fixed space between a name and its badge / flag. It is NOT a normal space: a space is as wide as the font says, and
	 * with a wide custom game font the badge ended up 2 or 3 spaces away from the name. This one is always 3 pixels.
	 */
	public static Text gap() {
		if (gapText == null) {
			JsonObject o = new JsonObject();
			o.addProperty("text", "\ue001");
			o.addProperty("font", "sol_client:badge_gap");
			o.addProperty("color", "white");
			gapText = TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Text.literal(" "));
		}
		return gapText;
	}

	public static Text text(Badge b) {
		return TEXTS.computeIfAbsent(b, k -> {
			JsonObject o = new JsonObject();
			o.addProperty("text", "\ue000");
			o.addProperty("font", "sol_client:" + k.font);
			o.addProperty("color", "white");
			return TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Text.literal(""));
		});
	}

	/** Is this player (uuid / nick) you? */
	private static boolean isMe(UUID id, String nick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getSession() == null) {
			return false;
		}
		if (id != null && (id.equals(mc.getSession().getUuidOrNull()) || mc.player != null && id.equals(mc.player.getUuid()))) {
			return true;
		}
		return nick != null && nick.equalsIgnoreCase(mc.getSession().getUsername());
	}

	// ---- country flag next to the name (off until you turn it on in your profile)
	private static Boolean flagOn;
	private static final Map<String, String> REMOTE_FLAG = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<String, Long> REMOTE_FLAG_AT = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<String, Text> FLAG_TEXT = new java.util.concurrent.ConcurrentHashMap<>();

	private static Path flagFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-badge-flag.txt");
	}

	/** Do you show your country's flag next to your name? (default no) */
	public static boolean myFlagOn() {
		if (flagOn == null) {
			flagOn = false;
			try {
				if (Files.exists(flagFile())) {
					flagOn = Files.readString(flagFile()).trim().equals("on");
				}
			} catch (java.io.IOException ignored) {
				// default
			}
		}
		return flagOn;
	}

	public static void setMyFlag(boolean on) {
		flagOn = on;
		try {
			Files.writeString(flagFile(), on ? "on" : "off");
		} catch (java.io.IOException ignored) {
			// cannot save
		}
	}

	/** Your country (two letters, from the computer's region), or "". */
	public static String myCountry() {
		String cc = Locale.getDefault().getCountry();
		return cc != null && cc.matches("[A-Za-z]{2}") ? cc.toLowerCase(Locale.ROOT) : "";
	}

	/** What the other Sleep players are told ("" = no flag). */
	public static String myFlagCode() {
		return myFlagOn() ? myCountry() : "";
	}

	public static void onRemoteFlag(String nick, String cc) {
		if (nick == null) {
			return;
		}
		String k = nick.toLowerCase(Locale.ROOT);
		if (cc == null || !cc.matches("[a-z]{2}")) {
			REMOTE_FLAG.remove(k);
		} else {
			REMOTE_FLAG.put(k, cc);
			REMOTE_FLAG_AT.put(k, System.currentTimeMillis());
		}
	}

	private static String flagOf(boolean me, String nick) {
		if (me) {
			return myFlagCode();
		}
		if (nick == null) {
			return "";
		}
		String k = nick.toLowerCase(Locale.ROOT);
		Long at = REMOTE_FLAG_AT.get(k);
		return at == null || System.currentTimeMillis() - at > FRESH_MS ? "" : REMOTE_FLAG.getOrDefault(k, "");
	}

	/** The little flag (one glyph of the sol_client:flags font, made from textures/gui/flag). */
	private static Text flagText(String cc) {
		return FLAG_TEXT.computeIfAbsent(cc, k -> {
			JsonObject o = new JsonObject();
			o.addProperty("text", String.valueOf((char) (0xE100 + (k.charAt(0) - 'a') * 26 + (k.charAt(1) - 'a'))));
			o.addProperty("font", "sol_client:flags");
			o.addProperty("color", "white");
			return TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Text.literal(""));
		});
	}

	/** {@code name} followed (to the right) by the badge and, when turned on, the country flag. */
	/** Account uuid (no dashes) -> nick of the Sleep players we heard from (fills in for servers whose tab rows are not real players). */
	private static final Map<String, String> NICK_BY_UUID = new java.util.concurrent.ConcurrentHashMap<>();

	/** Called for every profile message: remember whose account this is. */
	public static void rememberUuid(String uuid, String nick) {
		if (uuid != null && nick != null) {
			NICK_BY_UUID.put(uuid.replace("-", "").toLowerCase(Locale.ROOT), nick);
		}
	}

	private static boolean knownRemote(String nick) {
		if (nick == null) {
			return false;
		}
		String k = nick.toLowerCase(Locale.ROOT);
		return REMOTE_AT.containsKey(k) || REMOTE_LOOK_AT.containsKey(k) || REMOTE_FLAG_AT.containsKey(k);
	}

	/**
	 * Which Sleep player a name belongs to. Many servers (tab plugins, BungeeCord / Velocity lobbies) fill the tab list with
	 * fake rows: the profile name is something like "!a01" and the real nick is only in the text shown. Then the badge, name
	 * style and flag were never found and only YOU saw your crown. Tries the profile nick, then the account uuid, then a nick
	 * written in the shown text.
	 */
	private static String resolveNick(UUID id, String nick, Text name) {
		if (knownRemote(nick)) {
			return nick;
		}
		if (id != null) {
			String byId = NICK_BY_UUID.get(id.toString().replace("-", "").toLowerCase(Locale.ROOT));
			if (byId != null) {
				return byId;
			}
		}
		String shown = name.getString();
		for (String n : NICK_BY_UUID.values()) {
			if (n.length() >= 3 && containsWord(shown, n)) {
				return n;
			}
		}
		return nick;
	}

	/** The nick appears in the text as a whole word (not inside another nick). */
	private static boolean containsWord(String text, String nick) {
		String t = text.toLowerCase(Locale.ROOT), n = nick.toLowerCase(Locale.ROOT);
		int i = t.indexOf(n);
		while (i >= 0) {
			boolean left = i == 0 || !isNickChar(t.charAt(i - 1)), right = i + n.length() >= t.length() || !isNickChar(t.charAt(i + n.length()));
			if (left && right) {
				return true;
			}
			i = t.indexOf(n, i + 1);
		}
		return false;
	}

	private static boolean isNickChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	public static Text decorate(UUID id, String nick, Text name) {
		if (name == null) {
			return name;
		}
		if (!isMe(id, nick)) {
			nick = resolveNick(id, nick, name);
		} else if (nick == null || !nick.equalsIgnoreCase(MinecraftClient.getInstance().getSession().getUsername())) {
			nick = MinecraftClient.getInstance().getSession().getUsername();
		}
		Text out = decorateBadge(id, nick, name);
		String cc = flagOf(isMe(id, nick), nick);
		if (cc.isEmpty()) {
			return out;
		}
		return Text.empty().append(out).append(gap()).append(flagText(cc));
	}

	/** {@code name} followed (to the right) by your equipped badge, when this player is you. */
	private static Text decorateBadge(UUID id, String nick, Text name) {
		if (name == null) {
			return name;
		}
		boolean me = isMe(id, nick);
		if (!me) {
			var look = remoteLook(nick);
			if (look != null) {
				name = dev.solclient.mods.NameColourMod.applyTo(name, nick, look); // Owner / Team style, seen by every Sleep player
			}
		}
		Badge b = me ? equipped() : remote(nick);
		if (b == null || text(b).getString().isEmpty()) {
			return name;
		}
		boolean ownColour = dev.solclient.mods.NameColourMod.on() || !me && remoteLook(nick) != null;
		if (b.creator() && (me ? myColourOn() && !ownColour : !ownColour && !NO_COLOUR.contains(nick == null ? "" : nick.toLowerCase(Locale.ROOT)))) {
			name = paint(name, nick, b);
		}
		return Text.empty().append(name).append(gap()).append(text(b));
	}
}
