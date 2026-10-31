package dev.solclient;

import dev.solclient.gui.Notify;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;

/**
 * Resource pack guard: when a resource pack breaks the game's letters (every character becomes a box), the packs that
 * were just turned on are turned off again by themselves and the game reloads, with a notification saying which one.
 * <p>
 * How it knows the letters broke: normally "i" is much narrower than "W". When the font is missing, every character is
 * the same box, so both are equally wide. The check only acts when the letters were fine before the reload (so a
 * monospace font pack you already use is never touched).
 */
public final class ResourceGuard {

	private static boolean wasLoading;
	private static int checkIn = -1;
	/** The packs that were on the last time the letters were fine. */
	private static Set<String> lastGood;
	private static boolean fontWasOk = true;
	private static long lastFix;

	private ResourceGuard() {
	}

	/** Every client tick. */
	public static void tick(MinecraftClient mc) {
		boolean loading = mc.getOverlay() != null;
		if (loading && !wasLoading) {
			checkIn = -1; // a reload started: check again when it is done
		}
		if (!loading && wasLoading) {
			checkIn = 10; // half a second after the reload, when everything is in place
		}
		wasLoading = loading;
		if (loading || mc.textRenderer == null) {
			return;
		}
		if (lastGood == null) {
			fontWasOk = fontOk(mc);
			if (fontWasOk) {
				lastGood = enabled(mc);
			}
			return;
		}
		if (checkIn > 0 && --checkIn == 0) {
			check(mc);
		}
	}

	private static boolean fontOk(MinecraftClient mc) {
		int narrow = mc.textRenderer.getWidth("iiiiiiiiii"), wide = mc.textRenderer.getWidth("WWWWWWWWWW");
		return wide > narrow + 5;
	}

	private static Set<String> enabled(MinecraftClient mc) {
		return new LinkedHashSet<>(mc.getResourcePackManager().getEnabledIds());
	}

	private static void check(MinecraftClient mc) {
		boolean ok = fontOk(mc);
		Set<String> now = enabled(mc);
		if (ok) {
			fontWasOk = true;
			lastGood = now;
			return;
		}
		if (!fontWasOk) {
			return; // it was already like this before: not caused by this change
		}
		List<String> added = new ArrayList<>();
		for (String id : now) {
			if (!lastGood.contains(id)) {
				added.add(id);
			}
		}
		long t = System.currentTimeMillis();
		if (added.isEmpty() || t - lastFix < 20_000L) {
			fontWasOk = false; // nothing new to blame (or it just tried): leave the packs alone
			Notify.show(Notify.Kind.ERROR, dev.solclient.Lang.tr("A resource pack broke the letters", "Um pacote de recursos estragou as letras"),
					dev.solclient.Lang.tr("Turn off the last pack you added", "Desliga o último pacote que meteste"));
			return;
		}
		lastFix = t;
		List<String> keep = new ArrayList<>(now);
		keep.removeAll(added);
		StringBuilder names = new StringBuilder();
		for (String id : added) {
			var p = mc.getResourcePackManager().getProfile(id);
			if (names.length() > 0) {
				names.append(", ");
			}
			names.append(p == null ? id : p.getDisplayName().getString());
		}
		System.err.println("[Sleep] resource pack guard: the letters broke after turning on " + added + " - turning them off again");
		mc.getResourcePackManager().setEnabledProfiles(keep);
		mc.options.refreshResourcePacks(mc.getResourcePackManager()); // saves the list and reloads
		Notify.show(Notify.Kind.ERROR, dev.solclient.Lang.tr("Fixed: pack turned off", "Arranjado: pacote desligado"),
				names + dev.solclient.Lang.tr(" broke the letters", " estragava as letras"));
	}
}
