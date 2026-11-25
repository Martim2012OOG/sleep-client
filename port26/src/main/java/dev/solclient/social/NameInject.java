package dev.solclient.social;

import dev.solclient.SolClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * The Caos logo for nametags that are NOT the player's own vanilla nametag: servers (PvPHQ...) often hide it and show a
 * text display / armor stand with the rank, name and flag instead. Finds the nick of a player who uses the client inside
 * that text and puts the logo right before it. Texts that already have the logo are left alone.
 */
public final class NameInject {

	private record Hit(Component out, long at) {
	}

	private static final Map<String, Hit> CACHE = new HashMap<>();
	private static boolean logged;

	private NameInject() {
	}

	/** {@code in} with the logo and badge next to the nick of a client user, and Nick Hider applied (same object when there is nothing to change). */
	public static Component apply(Component in) {
		if (in == null) {
			return null;
		}
		// Called for every text display, every frame. The same Text object comes back each time, so remember the answer
		// for that exact object (no string building / hashing / player list scan on every call).
		long now = System.currentTimeMillis();
		synchronized (FAST) {
			Object[] c = FAST.get(in);
			if (c != null && (long) c[0] > now) {
				return (Component) c[1];
			}
		}
		Component out = dev.solclient.mods.NickHiderMod.apply(inject(in));
		synchronized (FAST) {
			if (FAST.size() > 512) {
				FAST.clear();
			}
			FAST.put(in, new Object[] {now + 1500L, out});
		}
		return out;
	}

	private static final java.util.IdentityHashMap<Component, Object[]> FAST = new java.util.IdentityHashMap<>();

	private static synchronized Component inject(Component in) {
		if (in == null) {
			return null;
		}
		try {
			var st = SolClient.settings;
			Minecraft mc = Minecraft.getInstance();
			if (st == null || !st.nameBadge.on() || mc.level == null || mc.getConnection() == null) {
				return in;
			}
			String s = in.getString();
			if (s.length() < 3 || s.length() > 400) {
				return in;
			}
			long now = System.currentTimeMillis();
			Hit h = CACHE.get(s);
			if (h != null && now - h.at() < 2000L) {
				return h.out() == null ? in : h.out();
			}
			Component out = build(in, s, mc);
			if (CACHE.size() > 256) {
				CACHE.clear();
			}
			CACHE.put(s, new Hit(out, now));
			if (out != null && !logged) {
				logged = true;
				System.out.println("[Caos] logo added to a custom nametag: " + s.replace('\n', ' '));
			}
			return out == null ? in : out;
		} catch (RuntimeException e) {
			return in;
		}
	}

	private static boolean nameChar(char c) {
		return c == '_' || Character.isLetterOrDigit(c);
	}

	private static Component build(Component in, String s, Minecraft mc) {
		Component logo = SolarBadge.decorate(Component.empty()); // logo + a space
		String logoStr = logo.getString().trim();
		boolean logoFree = !logoStr.isEmpty() && !s.contains(logoStr);
		// the first nick (of a client user, or of someone with a profile badge) that appears as a whole word
		int best = -1;
		String bestNick = null;
		java.util.UUID bestId = null;
		List<String> nicks = new ArrayList<>();
		List<java.util.UUID> ids = new ArrayList<>();
		for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
			nicks.add(SolarBadge.nameOf(e.getProfile()));
			ids.add(SolarBadge.idOf(e.getProfile()));
		}
		if (mc.player != null) {
			nicks.add(mc.player.getName().getString());
			ids.add(mc.player.getUUID());
		}
		for (int i = 0; i < nicks.size(); i++) {
			String n = nicks.get(i);
			if (n == null || n.length() < 3) {
				continue;
			}
			int from = 0, at;
			while ((at = s.indexOf(n, from)) >= 0) {
				boolean ok = (at == 0 || !nameChar(s.charAt(at - 1))) && (at + n.length() >= s.length() || !nameChar(s.charAt(at + n.length())));
				if (ok) {
					if ((best < 0 || at < best) && (SolarBadge.uses(ids.get(i), n) || badgeOf(ids.get(i), n) != null)) {
						best = at;
						bestNick = n;
						bestId = ids.get(i);
					}
					break;
				}
				from = at + 1;
			}
		}
		if (best < 0) {
			return null;
		}
		java.util.TreeMap<Integer, Component> ins = new java.util.TreeMap<>();
		if (logoFree && SolarBadge.uses(bestId, bestNick)) {
			ins.put(best, logo);
		}
		Component badge = badgeOf(bestId, bestNick);
		if (badge != null && !s.contains(badge.getString().trim())) {
			ins.merge(best + bestNick.length(), badge, (x, y) -> x);
		}
		if (ins.isEmpty()) {
			return null;
		}
		List<Object[]> segs = new ArrayList<>();
		in.<Object>visit((Style style, String str) -> {
			segs.add(new Object[] {str, style});
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent out = Component.empty();
		java.util.Set<Integer> used = new java.util.HashSet<>();
		int pos = 0;
		for (Object[] seg : segs) {
			String str = (String) seg[0];
			Style style = (Style) seg[1];
			int cur = 0;
			for (var en : ins.entrySet()) {
				int k = en.getKey() - pos;
				if (!used.contains(en.getKey()) && k >= cur && k <= str.length()) {
					if (k > cur) {
						out.append(Component.literal(str.substring(cur, k)).setStyle(style));
					}
					out.append(en.getValue());
					cur = k;
					used.add(en.getKey());
				}
			}
			if (cur < str.length()) {
				out.append(Component.literal(str.substring(cur)).setStyle(style));
			}
			pos += str.length();
		}
		return used.isEmpty() ? null : out;
	}

	/** The profile badge (with a space before it) of this player, or null when they have none. */
	private static Component badgeOf(java.util.UUID id, String nick) {
		Component d = ProfileBadges.decorate(id, nick, Component.empty());
		return d == null || d.getString().isBlank() ? null : d;
	}
}
