package dev.sleepclient.addons.pvp;

import dev.sleepclient.Gfx;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Panel with the stats of the round that just ended (right side of the screen, a few seconds). */
public final class FightStatsHud {

	private static final long SHOW_MS = 12000;

	private FightStatsHud() {
	}

	private static void row(DrawContext c, int x, int y, int w, String label, String value) {
		Gfx.text(c, label, x, y, 0xFF8C90A0);
		Gfx.text(c, value, x + w - Gfx.tw(value), y, 0xFFFFFFFF);
	}

	public static void render(DrawContext c) {
		FightStats.Round r = FightStats.last;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (r == null || mc.player == null || !PracticeSession.isActive()) {
			return;
		}
		if (System.currentTimeMillis() - FightStats.shownAt > SHOW_MS) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		int w = 152, h = 122;
		int x = sw - w - 6, y = sh / 2 - h / 2;
		Gfx.rrect(c, x, y, w, h, 6, 0xB00E0F13);

		int tx = x + 8, iw = w - 16, ty = y + 7;
		Gfx.text(c, "Round " + r.number(), tx, ty, 0xFFFFAA00);
		String res = r.won() ? "WON" : "LOST";
		Gfx.text(c, res, tx + iw - Gfx.tw(res), ty, r.won() ? 0xFF55FF55 : 0xFFFF5555);

		ty += 15;
		row(c, tx, ty, iw, "Damage dealt", String.format(Locale.ROOT, "%.1f", r.dealt()));
		row(c, tx, ty + 11, iw, "Damage taken", String.format(Locale.ROOT, "%.1f", r.taken()));
		row(c, tx, ty + 22, iw, "Accuracy", r.accuracy() + "%  (" + r.hits() + "/" + r.swings() + ")");
		row(c, tx, ty + 33, iw, "Combos", r.combos() + "  (best " + r.bestCombo() + ")");
		row(c, tx, ty + 44, iw, "Mace smashes", String.valueOf(r.smashes()));
		row(c, tx, ty + 55, iw, "Hits taken", String.valueOf(r.hitsTaken()));
		row(c, tx, ty + 66, iw, "Time", (r.seconds() / 60) + ":" + String.format(Locale.ROOT, "%02d", r.seconds() % 60));
		Gfx.text(c, "Session  " + FightStats.wins + "W - " + FightStats.losses + "L", tx, ty + 82, 0xFFAAAAAA);
	}
}
