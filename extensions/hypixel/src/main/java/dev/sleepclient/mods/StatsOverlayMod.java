package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.HypixelApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Stats Overlay (BedWars / SkyWars / Duels): a table with the stats of everyone in the lobby so you know who is
 * dangerous. Shows while you hold Tab (or always). Needs your Hypixel API key in Hypixel Additions.
 */
public class StatsOverlayMod extends HudMod {

	public static StatsOverlayMod instance;
	private final Option.Choice mode = choice("Game", 0, "BedWars", "SkyWars", "Duels");
	private final Option.Choice when = choice("Show", 0, "While Holding Tab", "Always");
	private final Option.Num rows = num("Max Players", 16, 4, 24, 1, "");

	public StatsOverlayMod() {
		super("Stats Overlay", "stats_overlay", "See the stats of everyone in your game (BedWars, SkyWars, Duels) on Hypixel.", 0.3F, 0.1F);
		instance = this;
	}

	private record Line(String name, HypixelApi.Stats s, double threat) {
	}

	private List<Line> lines() {
		List<Line> out = new ArrayList<>();
		var nh = mc.getNetworkHandler();
		if (nh == null) {
			return out;
		}
		for (PlayerListEntry e : nh.getPlayerList()) {
			if (e.getProfile() == null) {
				continue;
			}
			String n = NickHiderMod.profileName(e);
			if (n == null || n.isEmpty() || n.startsWith("!") || n.contains(" ")) {
				continue;
			}
			HypixelApi.Stats s = HypixelApi.get(dev.sleepclient.social.SolarBadge.idOf(e.getProfile()));
			double t = s == null || !s.loaded ? -1 : s.nicked ? 99 : switch (mode.idx()) {
				case 1 -> s.swKdr();
				case 2 -> s.duelsWlr();
				default -> s.fkdr();
			};
			out.add(new Line(n, s, t));
		}
		out.sort(Comparator.comparingDouble((Line l) -> -l.threat));
		while (out.size() > rows.f()) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	@Override
	public boolean visible() {
		if (!HypixelAdditionsMod.onHypixel() || !HypixelApi.hasKey()) {
			return false;
		}
		return when.idx() == 1 || mc.options.playerListKey.isPressed();
	}

	@Override
	public int width() {
		return 300;
	}

	@Override
	public int height() {
		return 30 + Math.max(1, Math.min((int) rows.f(), mc.getNetworkHandler() == null ? 4 : mc.getNetworkHandler().getPlayerList().size())) * 11;
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		List<Line> ls = lines();
		int w = width(), h = 30 + Math.max(1, ls.size()) * 11;
		Gfx.rrectFree(c, 0, 0, w, h, 8, 0xD00D0F14);
		String[] cols = switch (mode.idx()) {
			case 1 -> new String[] {"Lv", "Player", "KDR", "W/L", "Wins", "Kills"};
			case 2 -> new String[] {"", "Player", "W/L", "Wins", "Losses", "Kills"};
			default -> new String[] {"✫", "Player", "FKDR", "W/L", "Finals", "Beds"};
		};
		int[] xs = {8, 40, 150, 190, 228, 266};
		for (int i = 0; i < cols.length; i++) {
			Gfx.text(c, cols[i], xs[i], 7, 0xFF8C90A0);
		}
		c.fill(8, 19, w - 8, 20, 0x22FFFFFF);
		if (ls.isEmpty()) {
			Gfx.text(c, edit ? "Player stats appear here" : "Loading...", 8, 24, 0xFF8C90A0);
			return;
		}
		int y = 24;
		for (Line l : ls) {
			HypixelApi.Stats s = l.s;
			Gfx.textRaw(c, l.name.length() > 16 ? l.name.substring(0, 15) + "." : l.name, xs[1], y, 0xFFFFFFFF);
			if (s == null || !s.loaded) {
				Gfx.textRaw(c, "...", xs[2], y, 0xFF8C90A0);
			} else if (s.nicked) {
				Gfx.textRaw(c, Lang.tr("NICKED", "NICK"), xs[2], y, 0xFFFF5555);
			} else {
				String[] v;
				int tc;
				switch (mode.idx()) {
					case 1 -> {
						v = new String[] {Integer.toString(s.swLevel), String.format("%.2f", s.swKdr()), String.format("%.2f", s.swWins / (double) Math.max(1, s.swLosses)),
								Integer.toString(s.swWins), Integer.toString(s.swKills)};
						tc = HypixelApi.threatColour(s.swKdr());
					}
					case 2 -> {
						v = new String[] {"", String.format("%.2f", s.duelsWlr()), Integer.toString(s.duelsWins), Integer.toString(s.duelsLosses), Integer.toString(s.duelsKills)};
						tc = HypixelApi.threatColour(s.duelsWlr());
					}
					default -> {
						v = new String[] {Integer.toString(s.bwStar), String.format("%.2f", s.fkdr()), String.format("%.2f", s.bwWlr()),
								Integer.toString(s.bwFinalKills), Integer.toString(s.bwBedsBroken)};
						tc = HypixelApi.threatColour(s.fkdr());
					}
				}
				Gfx.textRaw(c, v[0], xs[0], y, mode.idx() == 0 ? HypixelApi.starColour(s.bwStar) : 0xFF55FFFF);
				Gfx.textRaw(c, v[1], xs[2], y, tc);
				Gfx.textRaw(c, v[2], xs[3], y, 0xFFE6E8EE);
				Gfx.textRaw(c, v[3], xs[4], y, 0xFFE6E8EE);
				Gfx.textRaw(c, v[4], xs[5], y, 0xFFE6E8EE);
			}
			y += 11;
		}
	}
}
