package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import dev.solclient.parkour.ParkourLive;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Parkour race with friends (Host World in the Parkour Paradise 3 map): everyone's level, best first. Shows when 2 or
 * more players are in the map. Move / resize it in the HUD editor; title, colours, background and rows are options.
 */
public class ParkourRaceMod extends HudMod {

	private static final int ROW_H = 11;

	private final Option.Bool showTitle = bool("Show Title", true);
	private final Option.Text title = text("Title", "RACE", 24, "RACE");
	private final Option.Num rows = num("Max Players", 6, 2, 12, 1, "");
	private final Option.Num panelWidth = num("Width", 118, 80, 220, 2, "");
	private final Option.Num background = num("Background", 55, 0, 100, 5, "%");
	private final Option.Bool rank = bool("Show Rank Number", true);
	private final Option.Bool highlightMe = bool("Highlight Me", true);
	private final Option.Col titleColour = colour("Title Colour", 0xFF35D0F5);
	private final Option.Col leaderColour = colour("Leader Colour", 0xFFFFD23C);
	private final Option.Col meColour = colour("My Colour", 0xFF7FD8FF);
	private final Option.Col otherColour = colour("Others Colour", 0xFFE0E4EC);

	public ParkourRaceMod() {
		super("Parkour Race", "parkour_race", "Everyone's level in the Parkour Paradise 3 map when you play with friends (/pkrace start). Move it in the HUD editor.",
				0.012F, 0.30F);
		title.showIf(showTitle::on);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public boolean visible() {
		return ParkourLive.showing() && ParkourLive.standings().size() >= 2;
	}

	private int top() {
		return showTitle.on() ? 14 : 4;
	}

	@Override
	public int width() {
		return panelWidth.i();
	}

	@Override
	public int height() {
		return top() + rows.i() * ROW_H + 2;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		List<Map.Entry<String, Integer>> list = ParkourLive.standings();
		if (edit && list.size() < 2) {
			list = List.of(Map.entry("Marzox_x", 35), Map.entry("Albinhos", 28), Map.entry("Friend", 12));
		}
		String me = mc.player == null ? "" : mc.player.getName().getString();
		int w = width(), n = Math.min(rows.i(), list.size()), top = top();
		int bg = Math.round(background.f() * 2.55F);
		if (bg > 0) {
			Gfx.rrect(c, 0, 0, w, top + n * ROW_H + 2, 4, Gfx.withAlpha(0x14161E, bg));
		}
		if (showTitle.on()) {
			String t = title.get().isBlank() ? (ParkourLive.racing() ? "RACE" : "PARKOUR") : title.get();
			c.text(mc.font, Component.literal(t).withStyle(s -> s.withBold(true)), 6, 4, titleColour.c() | 0xFF000000, true);
		}
		for (int i = 0; i < n; i++) {
			var e = list.get(i);
			int y = top + 1 + i * ROW_H;
			boolean mine = e.getKey().equals(me);
			int col = i == 0 ? leaderColour.c() : mine ? meColour.c() : otherColour.c();
			col |= 0xFF000000;
			if (mine && highlightMe.on()) {
				Gfx.fill(c, 2, y - 1, w - 2, y + ROW_H - 1, Gfx.withAlpha(meColour.c() & 0xFFFFFF, 0x30));
			}
			String name = (rank.on() ? (i + 1) + ". " : "") + e.getKey();
			String lv = String.valueOf(e.getValue());
			int lw = mc.font.width(lv);
			Gfx.textClip(c, name, 6, y + 1, w - 16 - lw, col);
			c.text(mc.font, Component.literal(lv), w - 6 - lw, y + 1, col, true);
		}
	}
}
