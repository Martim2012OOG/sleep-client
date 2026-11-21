package dev.sleepclient;

import dev.sleepclient.mods.Mod;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Finds keys used by two things at once (two Sleep mods, or a mod and a Minecraft control) and fixes them. */
public final class KeyConflicts {

	public record Use(String who, Mod mod, Option.Key opt) {
	}

	private static long warnedAt;

	private KeyConflicts() {
	}

	/** key code -> everything using it, only for codes used more than once. */
	public static Map<Integer, List<Use>> find() {
		Map<Integer, List<Use>> by = new HashMap<>();
		for (Mod m : SleepClient.MODS) {
			if (!m.enabled) {
				continue;
			}
			for (Option<?> o : m.options) {
				if (o instanceof Option.Key k && k.code() >= 0) {
					by.computeIfAbsent(k.code(), x -> new ArrayList<>()).add(new Use(m.name + " - " + Lang.t(k.name), m, k));
				}
			}
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options != null) {
			for (KeyBinding kb : mc.options.allKeys) {
				try {
					InputUtil.Key key = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
					if (key.getCategory() != InputUtil.Type.KEYSYM || key.getCode() < 0) {
						continue;
					}
					List<Use> l = by.get(key.getCode());
					if (l != null) {
						l.add(new Use("Minecraft - " + net.minecraft.text.Text.translatable(kb.getId()).getString(), null, null));
					}
				} catch (RuntimeException ignored) {
				}
			}
		}
		by.values().removeIf(l -> l.size() < 2);
		return by;
	}

	public static String status() {
		Map<Integer, List<Use>> f = find();
		if (f.isEmpty()) {
			return Lang.tr("No conflicts", "Sem conflitos");
		}
		var e = f.entrySet().iterator().next();
		String first = Keys.name(e.getKey()) + ": " + e.getValue().get(0).who() + " + " + e.getValue().get(1).who();
		return f.size() + Lang.tr(" conflict(s) - ", " conflito(s) - ") + first;
	}

	/** Keeps the first user of each key (Minecraft controls always win) and unbinds the other Sleep mods. */
	public static int fix() {
		int n = 0;
		for (List<Use> l : find().values()) {
			boolean mcUses = l.stream().anyMatch(u -> u.mod() == null);
			boolean kept = mcUses;
			for (Use u : l) {
				if (u.opt() == null) {
					continue;
				}
				if (!kept) {
					kept = true;
					continue;
				}
				u.opt().set(-1);
				n++;
			}
		}
		if (n > 0) {
			Config.save();
		}
		return n;
	}

	/** Once per session, when you join a world: tells you if two things share a key. */
	public static void warnOnce() {
		long now = System.currentTimeMillis();
		if (warnedAt != 0) {
			return;
		}
		warnedAt = now;
		Map<Integer, List<Use>> f = find();
		if (!f.isEmpty()) {
			dev.sleepclient.fightcam.ClipToast.show(Lang.tr("Key conflict", "Teclas repetidas"), status() + Lang.tr("  (Settings > Keys > Fix)", "  (Definições > Teclas > Corrigir)"), null, true);
		}
	}
}
