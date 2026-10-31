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
	private final Option.Bool hideOthers = bool("Hide Others' Names", false);
	private final Option.Bool hideYourSkin = bool("Hide Your Skin", false);
	private final Option.Bool hideOthersSkins = bool("Hide Others' Skins", false);

	public NickHiderMod() {
		super("Nick Hider", "nick_hider", "Hide your nick and skin (or everyone's) on your screen.", Category.UTILITY);
		instance = this;
		customNick.showIf(() -> yourNick.idx() == 4);
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
		if (!on() || t == null) {
			return t;
		}
		Map<String, String> map = new LinkedHashMap<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		String self = mc.getSession().getUsername();
		if (instance.hideYourNick.on() && self != null && !self.isEmpty()) {
			map.put(self, selfName());
		}
		if (instance.hideOthers.on() && mc.getNetworkHandler() != null) {
			for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
				String n = nameOf(e);
				if (n != null && !n.equals(self) && n.length() > 2) {
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
