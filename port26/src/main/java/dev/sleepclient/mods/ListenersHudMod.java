package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.music.MusicShare;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.Presence;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;

/** Who is listening along: small heads, like the people in a Discord call (move and resize it in the HUD editor, any corner). */
public class ListenersHudMod extends HudMod {

	private final Option.Num headSize = num("Head Size", 16, 8, 40, 1, "px");
	private final Option.Choice layout = choice("Layout", 0, "Vertical", "Horizontal");
	private final Option.Bool names = bool("Show Names", true);
	private final Option.Bool onlyWhenOn = bool("Hide When Alone", true);

	public ListenersHudMod() {
		super("Music Listeners", "music_listeners", "The heads of the people listening along with you (or with the person you listen with).", 0.012F, 0.30F);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private List<String> list(boolean edit) {
		List<String> l = new ArrayList<>(MusicShare.session());
		if (edit && l.isEmpty()) {
			l.add("00000000000000000000000000000001");
			l.add("00000000000000000000000000000002");
			l.add("00000000000000000000000000000003");
		}
		return l;
	}

	private int rowH() {
		return Math.max(headSize.i(), names.on() ? 10 : 0) + 3;
	}

	@Override
	public boolean visible() {
		return !onlyWhenOn.on() || !MusicShare.session().isEmpty();
	}

	@Override
	public int width() {
		List<String> l = list(true);
		if (layout.idx() == 1) {
			return Math.max(1, l.size() * (headSize.i() + 4));
		}
		int w = headSize.i();
		if (names.on()) {
			int best = 0;
			for (String u : l) {
				best = Math.max(best, mc.font.width(MusicShare.nick(u)));
			}
			w += 5 + Math.max(24, Math.min(best, 80));
		}
		return w;
	}

	@Override
	public int height() {
		List<String> l = list(true);
		return layout.idx() == 1 ? headSize.i() + (names.on() ? 11 : 0) : Math.max(1, l.size() * rowH() - 3);
	}

	private PlayerSkin skin(String u) {
		try {
			UUID id = Presence.dashed(u);
			PlayerInfo e = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
			return e != null ? e.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(id);
		} catch (RuntimeException ex) {
			return net.minecraft.client.resources.DefaultPlayerSkin.get(UUID.randomUUID());
		}
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		List<String> l = list(edit);
		int hs = headSize.i();
		boolean horizontal = layout.idx() == 1;
		for (int i = 0; i < l.size(); i++) {
			String u = l.get(i);
			int x = horizontal ? i * (hs + 4) : 0, y = horizontal ? 0 : i * rowH();
			boolean host = MusicShare.following != null ? i == 0 : false;
			// only the heads: no circle behind them
			String nm = edit && u.startsWith("0000") ? "Friend " + u.substring(u.length() - 1) : MusicShare.nick(u);
			// the head by NAME: on offline-mode servers the uuid is not the account's and the skin never matched
			dev.sleepclient.gui.Faces.draw(c, nm, x, y, hs, 255);
			if (names.on()) {
				if (horizontal) {
					c.text(mc.font, mc.font.plainSubstrByWidth(nm, hs + 4), x, y + hs + 2, 0xFFFFFFFF, true);
				} else {
					c.text(mc.font, nm, x + hs + 5, y + (hs - 8) / 2, host ? 0xFF8AFFC0 : 0xFFFFFFFF, true);
				}
			}
		}
	}
}
