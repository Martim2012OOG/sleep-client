package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * Nick Hider: hides your nick (and optionally everyone's) in nametags, the tab list and chat,
 * and can show default skins instead of real ones. Only changes what YOU see.
 */
public class NickHiderMod extends Mod {

	public static NickHiderMod instance;

	private final Option.Bool hideYourNick = bool("Hide Your Nick", true);
	private final Option.Choice yourNick = choice("Nickname", 0, "You", "Player", "Me", "Hidden", "Custom");
	private final Option.Text customNick = text("Custom Nickname", "", 32, "any name you like");
	private final Option.Bool shareNick = bool("Show My Nickname To Other Sleep Players", true);
	private final Option.Bool hideOthers = bool("Hide Others' Names", false);
	private final Option.Bool hideYourSkin = bool("Hide Your Skin", false);
	private final Option.Bool hideOthersSkins = bool("Hide Others' Skins", false);

	public NickHiderMod() {
		super("Nick Hider", "nick_hider", "Hide your nick and skin (or everyone's) on your screen.", Category.UTILITY);
		instance = this;
		customNick.showIf(() -> yourNick.idx() == 4);
	}

	/** Nicknames other Sleep players chose to show instead of their real one (real nick lower case -> shown nick, when it arrived). */
	private static final Map<String, Object[]> REMOTE = new java.util.concurrent.ConcurrentHashMap<>();
	private static String lastWarned = "";

	/**
	 * What other Sleep players should see instead of your real nick: your Nick Hider name (a custom one has to pass the word /
	 * phone / link filter), or "" to show the real nick.
	 */
	public static String publicNick() {
		if (!hidingSelf() || !instance.shareNick.on()) {
			return "";
		}
		String n = selfName();
		if (instance.yourNick.idx() == 4) {
			String why = dev.solclient.social.NickFilter.problem(n);
			if (why != null) {
				if (!n.equals(lastWarned)) {
					lastWarned = n;
					MinecraftClient mc = MinecraftClient.getInstance();
					if (mc.player != null) {
						mc.player.sendMessage(Text.literal("[Sleep] Other players will not see that custom nick (" + why + " are not allowed). Only you see it.")
								.formatted(net.minecraft.util.Formatting.YELLOW), false);
					}
				}
				return "Player";
			}
		}
		return n.length() > 32 ? n.substring(0, 32) : n;
	}

	/** A Sleep player told us what to show instead of their nick ("" = back to the real one). */
	public static void onRemote(String realNick, String shown) {
		if (realNick == null || realNick.isEmpty()) {
			return;
		}
		String k = realNick.toLowerCase(java.util.Locale.ROOT);
		String s = shown == null ? "" : shown.strip();
		if (s.isEmpty() || s.equalsIgnoreCase(realNick) || dev.solclient.social.NickFilter.problem(s) != null) {
			REMOTE.remove(k);
			return;
		}
		REMOTE.put(k, new Object[] {realNick, s.length() > 32 ? s.substring(0, 32) : s, System.currentTimeMillis()});
	}

	private static boolean on() {
		return instance != null && instance.enabled;
	}

	/** The name shown instead of yours (the custom one when picked; & colour codes are kept for Name Colour to repaint). */
	public static String selfName() {
		if (instance == null) {
			return "";
		}
		if (instance.yourNick.idx() == 4) {
			String c = instance.customNick.get().strip();
			return c.isEmpty() ? "You" : c;
		}
		return instance.yourNick.label();
	}

	/** True while Nick Hider is replacing your own name (Name Colour then paints the shown name). */
	public static boolean hidingSelf() {
		return on() && instance.hideYourNick.on();
	}

	public static boolean hideSkin(UUID id) {
		if (!on() || id == null) {
			return false;
		}
		UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
		return id.equals(me) ? instance.hideYourSkin.on() : instance.hideOthersSkins.on();
	}

	/** Replaces hidden names inside a text (keeps colours of each piece). */
	public static Text apply(Text t) {
		if (t == null) {
			return t;
		}
		long nowMs = System.currentTimeMillis();
		if (!REMOTE.isEmpty()) {
			REMOTE.values().removeIf(v -> nowMs - (long) v[2] > 60_000L);
		}
		if (!on() && REMOTE.isEmpty()) {
			return t;
		}
		Map<String, String> map = new LinkedHashMap<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		String self = mc.getSession().getUsername();
		if (on() && instance.hideYourNick.on() && self != null && !self.isEmpty()) {
			map.put(self, selfName());
		}
		for (Object[] v : REMOTE.values()) {
			if (!((String) v[0]).equals(self)) {
				map.put((String) v[0], (String) v[1]);
			}
		}
		if (on() && instance.hideOthers.on() && mc.getNetworkHandler() != null) {
			for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
				String n = nameOf(e);
				if (n != null && !n.equals(self) && n.length() > 2 && !map.containsKey(n)) {
					map.put(n, "Player");
				}
			}
		}
		if (map.isEmpty()) {
			return t;
		}
		String plain = t.getString();
		boolean any = false;
		for (String k : map.keySet()) {
			if (plain.contains(k)) {
				any = true;
				break;
			}
		}
		if (!any) {
			return t;
		}
		MutableText out = Text.empty();
		t.visit((style, str) -> {
			String r = str;
			for (Map.Entry<String, String> e : map.entrySet()) {
				r = r.replace(e.getKey(), e.getValue());
			}
			out.append(Text.literal(r).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** The name inside a GameProfile (works with old and new authlib). */
	public static String profileNameOf(Object p) {
		if (p == null) {
			return null;
		}
		try {
			java.lang.reflect.Method m;
			try {
				m = p.getClass().getMethod("name");
			} catch (NoSuchMethodException ex) {
				m = p.getClass().getMethod("getName");
			}
			Object v = m.invoke(p);
			return v instanceof String s ? s : null;
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return null;
		}
	}

	/** The nick of a tab list entry (works with old and new authlib). */
	public static String profileName(PlayerListEntry e) {
		return nameOf(e);
	}

	private static String nameOf(PlayerListEntry e) {
		Object p = e.getProfile();
		try {
			java.lang.reflect.Method m;
			try {
				m = p.getClass().getMethod("name");
			} catch (NoSuchMethodException ex) {
				m = p.getClass().getMethod("getName");
			}
			Object v = m.invoke(p);
			return v instanceof String s ? s : null;
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return null;
		}
	}
}
