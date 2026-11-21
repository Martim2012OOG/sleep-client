package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.KuboLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Party HUD: your Sleep friends on the same server, with their head, health, hunger, armour and how far away they
 * are (with an arrow pointing to them). Shares your own health the same way so they see you too.
 */
public class PartyHudMod extends HudMod {

	public static PartyHudMod instance;
	private final Option.Choice who = choice("Show", 0, "Friends", "Every Sleep Player");
	private final Option.Bool distance = bool("Distance & Arrow", true);
	private final Option.Bool hunger = bool("Hunger", true);
	private final Option.Num max = num("Max Players", 6, 1, 10, 1, "");

	public PartyHudMod() {
		super("Party HUD", "party_hud", "See your friends' health, hunger and where they are, like a team game.", 0.01F, 0.3F);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void tick() {
		KuboLink.wantParty = true;
	}

	@Override
	public void onDisable() {
		KuboLink.wantParty = false;
	}

	private List<KuboLink.State> members() {
		List<KuboLink.State> out = new ArrayList<>();
		for (KuboLink.State s : KuboLink.STATES.values()) {
			if (who.idx() == 1 || KuboLink.isFriend(s.uuid, s.nick)) {
				out.add(s);
			}
		}
		out.sort((a, b) -> a.nick.compareToIgnoreCase(b.nick));
		while (out.size() > max.f()) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	@Override
	public boolean visible() {
		return !members().isEmpty();
	}

	@Override
	public int width() {
		return 150;
	}

	@Override
	public int height() {
		return Math.max(1, members().size()) * 28;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		List<KuboLink.State> ms = members();
		if (ms.isEmpty() && edit) {
			KuboLink.State demo = new KuboLink.State();
			demo.nick = "Friend";
			demo.hp = 14;
			demo.food = 17;
			demo.x = mc.player == null ? 0 : mc.player.getX() + 40;
			demo.z = mc.player == null ? 0 : mc.player.getZ();
			demo.dim = mc.level == null ? "" : mc.level.dimension().identifier().toString();
			ms = List.of(demo);
		}
		int y = 0;
		for (KuboLink.State s : ms) {
			row(c, s, y);
			y += 28;
		}
	}

	private void row(GuiGraphicsExtractor c, KuboLink.State s, int y) {
		int w = width();
		Gfx.rrectFree(c, 0, y, w, 25, 6, 0xC00D0F14);
		// head: their real skin when they are near enough to be in the tab list
		boolean drew = false;
		try {
			var nh = mc.getConnection();
			var e = nh == null ? null : nh.getPlayerInfo(s.nick);
			if (e != null) {
				net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(c, e.getSkin(), 4, y + 4, 17);
				drew = true;
			}
		} catch (Throwable ignored) {
		}
		if (!drew) {
			Gfx.rrectFree(c, 4, y + 4, 17, 17, 4, 0xFF2A2D38);
			Gfx.textRaw(c, s.nick.isEmpty() ? "?" : s.nick.substring(0, 1).toUpperCase(), 10, y + 9, 0xFFFFFFFF);
		}
		Gfx.textRaw(c, s.nick, 26, y + 3, 0xFFFFFFFF);
		// health bar
		float k = Math.max(0, Math.min(1, s.hp / Math.max(1, s.maxHp)));
		int col = k > 0.6F ? 0xFF4ADE80 : k > 0.3F ? 0xFFFACC15 : 0xFFEF4444;
		int bw = w - 26 - (distance.on() ? 40 : 6);
		Gfx.rrectFree(c, 26, y + 14, bw, 4, 2, 0x55FFFFFF);
		Gfx.rrectFree(c, 26, y + 14, Math.max(2, bw * k), 4, 2, col);
		if (hunger.on()) {
			Gfx.rrectFree(c, 26, y + 20, bw, 2, 1, 0x33FFFFFF);
			Gfx.rrectFree(c, 26, y + 20, Math.max(1, bw * Math.min(1, s.food / 20F)), 2, 1, 0xFFE0A050);
		}
		String hp = String.format("%.0f❤", s.hp / 2F);
		Gfx.textRaw(c, hp, 26 + bw - Gfx.twRaw(hp), y + 3, col);
		if (distance.on() && mc.player != null) {
			String dim = mc.level == null ? "" : mc.level.dimension().identifier().toString();
			int ax = w - 20, ay = y + 12;
			if (!dim.equals(s.dim)) {
				Gfx.textRaw(c, Lang.tr("other", "outra"), w - 36, y + 5, 0xFF8C90A0);
				Gfx.textRaw(c, Lang.tr("dimension", "dimensão"), w - 36, y + 14, 0xFF8C90A0);
				return;
			}
			double dx = s.x - mc.player.getX(), dz = s.z - mc.player.getZ();
			int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			// arrow: angle between where you look and where they are
			double target = Math.toDegrees(Math.atan2(-dx, dz));
			double rel = Math.toRadians(net.minecraft.util.Mth.wrapDegrees(target - mc.player.getYRot()));
			var m = c.pose();
			m.pushMatrix();
			m.translate(ax, ay - 3);
			m.rotate((float) rel);
			for (int i = 0; i < 5; i++) {
				c.fill(-i, -4 + i, i + 1, -3 + i, 0xFFFFFFFF);
			}
			c.fill(-1, 1, 2, 5, 0xFFFFFFFF);
			m.popMatrix();
			String d = dist >= 1000 ? String.format("%.1fk", dist / 1000F) : dist + "m";
			Gfx.textRaw(c, d, ax - Gfx.twRaw(d) / 2, y + 15, 0xFFB9BDC9);
		}
	}
}
