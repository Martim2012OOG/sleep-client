package dev.solclient;

import dev.solclient.mods.Mod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Says what is making the game slow. When the frame rate stays low for several seconds it looks at what the client itself costs
 * (every mod's time per frame, the recorder, video capes) and tells you the main culprit once in a while, so a heavy mod can be
 * turned off at once instead of guessing. It never changes anything by itself.
 */
public final class LagDoctor {

	private static long lowSince, lastTold;
	private static final long LOW_FOR_MS = 8_000L, EVERY_MS = 150_000L;

	private LagDoctor() {
	}

	public static void tick(Minecraft mc, List<Mod> mods) {
		if (mc.level == null || mc.player == null || !mc.isWindowActive() || dev.solclient.compat.Mc.screen(mc) != null) {
			lowSince = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (mc.getFps() >= 40) {
			lowSince = 0;
			return;
		}
		if (lowSince == 0) {
			lowSince = now;
			return;
		}
		if (now - lowSince < LOW_FOR_MS || now - lastTold < EVERY_MS) {
			return;
		}
		lastTold = now;
		String why = culprit(mods);
		System.err.println("[Sleep] Lag Doctor: " + mc.getFps() + " fps. " + why);
		dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.INFO, Lang.tr("The game is slow", "O jogo está lento"), why);
	}

	private static String culprit(List<Mod> mods) {
		List<Mod> heavy = new ArrayList<>();
		for (Mod m : mods) {
			if (m.enabled && m.costUs > 700F) {
				heavy.add(m);
			}
		}
		heavy.sort((a, b) -> Float.compare(b.costUs, a.costUs));
		if (!heavy.isEmpty()) {
			Mod m = heavy.get(0);
			return String.format(java.util.Locale.ROOT, Lang.tr("%s uses %.1f ms per frame: turn it off or lower its settings.", "%s gasta %.1f ms por frame: desliga-o ou baixa as definições."),
					m.name, m.costUs / 1000F);
		}
		if (dev.solclient.fightcam.Capture.running()) {
			return Lang.tr("The video recorder is running: turn off Clip Events > Record clips (it reads the screen all the time).",
					"O gravador de vídeo está ligado: desliga Clip Events > Record clips (lê o ecrã o tempo todo).");
		}
		var top = Perf.top();
		if (!top.isEmpty() && top.get(0).getValue() >= 0.7F) {
			return String.format(java.util.Locale.ROOT, Lang.tr("%s uses %.1f ms per frame: turn it off or lower its settings.", "%s gasta %.1f ms por frame: desliga-o ou baixa as definições."),
					top.get(0).getKey(), top.get(0).getValue());
		}
		return Lang.tr("Nothing in Sleep is heavy: lower the render distance or the video settings.", "Nada do Sleep está pesado: baixa a distância de visão ou os gráficos.");
	}
}
