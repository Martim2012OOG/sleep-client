package dev.solclient.addons.ranked;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.video.HtmlOverlay;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * What the players see: "match found", the searching pill, the duel intro (your intro page through MCEF, in sync with
 * the server's freeze; a simple drawn VS screen without MCEF) and the victory / defeat screen with the ELO change.
 */
public final class RankedFx {

	private static HtmlOverlay page;
	private static String pageFor;
	private static boolean injected;
	private static long injectAt;
	private static long endShownAt, leaveAt;
	private static Ranked.Match endMatch;
	private static long foundAt;

	private RankedFx() {
	}

	static void found(Ranked.Match m) {
		foundAt = System.currentTimeMillis();
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F, 0.9F));
		dev.solclient.social.Social.say(Lang.tr("Match found! ", "Partida encontrada! ") + names(m.teamA()) + " vs " + names(m.teamB())
				+ (Ranked.iAmHost() ? Lang.tr(" - you host it, opening the arena...", " - és tu o host, a abrir a arena...")
						: Lang.tr(" - joining the host...", " - a entrar no host...")), false);
	}

	static void showEnd(Ranked.Match m) {
		endMatch = m;
		endShownAt = System.currentTimeMillis();
		leaveAt = endShownAt + 12_000L;
		if (page != null && injected && m.winner() != null && !"none".equals(m.winner())) {
			page.js("try{window.DuelIntro.config.pointsWin=" + Math.abs(m.myChange()) + ";window.DuelIntro.victory('" + m.winner() + "')}catch(e){}");
		}
	}

	private static String names(List<Ranked.Player> t) {
		StringBuilder sb = new StringBuilder();
		for (var p : t) {
			if (sb.length() > 0) {
				sb.append(" & ");
			}
			sb.append(p.mcUsername());
		}
		return sb.toString();
	}

	/** Client tick: leave the arena a while after the end screen. */
	public static void tick(MinecraftClient mc) {
		if (leaveAt > 0 && System.currentTimeMillis() > leaveAt) {
			leaveAt = 0;
			Ranked.afterMatch();
			closePage();
			if (mc.world != null && (mc.getServer() != null && Ranked.WORLD.equals(worldName(mc)) || mc.getServer() == null && mc.getCurrentServerEntry() != null
					&& dev.solclient.social.KuboRelay.isRelayAddress(mc.getCurrentServerEntry().address))) {
				mc.disconnectWithProgressScreen();
			}
		}
	}

	private static String worldName(MinecraftClient mc) {
		try {
			return mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString();
		} catch (Exception e) {
			return "";
		}
	}

	// ---------------------------------------------------------------- drawing (last on the HUD)
	public static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		Ranked.Match m = Ranked.match;
		// searching pill
		if (!"idle".equals(Ranked.queueState) && m == null) {
			long s = (System.currentTimeMillis() - Ranked.queuedAt) / 1000;
			String t = ("waiting_teammate".equals(Ranked.queueState) ? Lang.tr("Waiting for your teammate", "À espera do teu colega")
					: Lang.tr("Searching", "A procurar")) + " · Ranked " + Ranked.queueMode + " " + kitName(Ranked.queueKit) + "  " + s / 60 + ":" + String.format("%02d", s % 60);
			int tw = Gfx.tw(t) + 26, x = w / 2 - tw / 2;
			Gfx.rrect(c, x, 6, tw, 16, 8, 0xCC12141C);
			float ph = (System.currentTimeMillis() % 1000) / 1000F;
			Gfx.circle(c, x + 9, 14, 2.5F + (float) Math.sin(ph * Math.PI * 2) * 0.8F, 0xFFFF5A5A);
			Gfx.text(c, t, x + 16, 10, 0xFFFFFFFF);
		}
		if (m == null) {
			return;
		}
		// the intro, while the server's intro freeze runs
		var player = mc.player;
		var eff = player == null ? null : player.getStatusEffect(StatusEffects.SLOWNESS);
		boolean intro = eff != null && eff.getAmplifier() == RankedServer.INTRO_MARK && m.has(Ranked.me());
		if (intro) {
			float pos = Math.max(0F, (RankedServer.INTRO_TICKS - eff.getDuration()) / 20F);
			if (!m.id().equals(pageFor)) {
				closePage();
				pageFor = m.id();
				page = HtmlOverlay.open(introUrl(m));
				injected = false;
				injectAt = System.currentTimeMillis() + 600;
			}
			if (page != null) {
				if (!injected && page.ready() && System.currentTimeMillis() > injectAt) {
					injected = true;
					page.js(introJs(m, pos));
				}
				page.draw(c);
			} else {
				drawVs(c, m, w, h, pos);
			}
			return;
		}
		// end screen
		Ranked.Match e = endMatch;
		if (e != null && System.currentTimeMillis() - endShownAt < 10_000L) {
			if (page != null && injected) {
				page.draw(c);
			}
			drawEnd(c, e, w, h);
		} else if (page != null && endMatch != null) {
			closePage();
		}
	}

	private static void closePage() {
		if (page != null) {
			page.close();
			page = null;
		}
		pageFor = null;
		injected = false;
	}

	static String kitName(String id) {
		for (Ranked.Kit k : Ranked.KITS) {
			if (k.id().equals(id)) {
				return k.name();
			}
		}
		return id;
	}

	private static String introUrl(Ranked.Match m) {
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("duel_intro.html");
			Files.createDirectories(f.getParent());
			try (InputStream in = RankedFx.class.getResourceAsStream("/kubo_practice/duel_intro.html")) {
				Files.write(f, in.readAllBytes());
			}
			return f.toUri() + "?mode=" + m.mode() + "&local=" + (m.team(Ranked.me()) == null ? "A" : m.team(Ranked.me()));
		} catch (Exception e) {
			return "about:blank";
		}
	}

	private static String js(String s) {
		return "'" + (s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'")) + "'";
	}

	/** Puts the real players, tiers, arena and kit in the intro page, then plays it from the server's moment. */
	private static String introJs(Ranked.Match m, float pos) {
		StringBuilder sb = new StringBuilder("(function(){try{var D=window.DuelIntro,C=D.config;C.testUI=false;['#ui','#bmenu','#start'].forEach(function(q){var e=document.querySelector(q);if(e){e.hidden=true;e.style.display='none';}});");
		sb.append("C.mode=").append(js(m.mode())).append(";C.queue=").append(js(m.ranked() ? "Ranked match" : "Friendly duel")).append(";C.arena=").append(js("Arena " + m.arena()))
				.append(";C.round='ROUND 1';C.gamemode=").append(js(m.kit())).append(";");
		sb.append("function mk(t,p){var o=Object.assign({},t);o.name=p.n;o.title=p.t+' "+ kitName(m.kit()).replace("'", "") + "';o.rank=p.t;o.elo=p.e;o.tiers=Object.assign({},t.tiers);o.tiers[C.gamemode]=p.t;o.skin=null;o.position=0;o.region='';o.rankTitle=p.t+' · '+p.e+' ELO';o.points=p.e;return o;}");
		for (String t : new String[] {"A", "B"}) {
			List<Ranked.Player> ps = "A".equals(t) ? m.teamA() : m.teamB();
			sb.append("var T").append(t).append("=C.teams.").append(t).append(".players;C.teams.").append(t).append(".players=[");
			for (int i = 0; i < ps.size(); i++) {
				Ranked.Player p = ps.get(i);
				sb.append(i > 0 ? "," : "").append("mk(T").append(t).append("[").append(Math.min(i, 1)).append("],{n:").append(js(p.mcUsername())).append(",t:").append(js(p.tier()))
						.append(",e:").append(p.elo()).append("})");
			}
			sb.append("];");
		}
		sb.append("D.play(").append(String.format(java.util.Locale.ROOT, "%.2f", pos)).append(");}catch(e){console.log(e)}})()");
		return sb.toString();
	}

	/** Without MCEF: a drawn VS screen with the names, tiers and the countdown. */
	private static void drawVs(DrawContext c, Ranked.Match m, int w, int h, float pos) {
		float k = Math.min(1F, pos / 0.6F);
		Gfx.fill(c, 0, 0, w, h, Gfx.withAlpha(0x05060C, (int) (200 * k)));
		Gfx.gradientH(c, 0, h / 2 - 40, w / 2, 80, 0x003B8CFF, 0xAA3B8CFF);
		Gfx.gradientH(c, w / 2, h / 2 - 40, w / 2, 80, 0xAAFF3B4E, 0x00FF3B4E);
		var tr = MinecraftClient.getInstance().textRenderer;
		int slide = (int) ((1F - k) * 60);
		team(c, m.teamA(), w / 4 - slide, h / 2 - 18);
		team(c, m.teamB(), w * 3 / 4 + slide, h / 2 - 18);
		big(c, "VS", w / 2, h / 2 - 12, 3F, 0xFFFFFFFF);
		String top = (m.ranked() ? "RANKED " : "DUEL ") + m.mode().toUpperCase() + " · " + kitName(m.kit()).toUpperCase() + " · ARENA " + m.arena();
		big(c, top, w / 2, h / 2 - 70, 1.4F, 0xFFFFCF4A);
		int left = (int) Math.ceil(RankedServer.INTRO_TICKS / 20F - pos);
		if (left <= 3 && left > 0) {
			big(c, String.valueOf(left), w / 2, h / 2 + 50, 4F, 0xFFFFFFFF);
		}
	}

	private static void team(DrawContext c, List<Ranked.Player> ps, int cx, int y) {
		for (int i = 0; i < ps.size(); i++) {
			var p = ps.get(i);
			big(c, p.mcUsername(), cx, y + i * 30, 2F, 0xFFFFFFFF);
			big(c, p.tier() + "  ·  " + p.elo() + " ELO", cx, y + i * 30 + 18, 1F, Ranked.tierColour(p.tier()));
		}
	}

	private static void big(DrawContext c, String s, int cx, int y, float sc, int col) {
		var tr = MinecraftClient.getInstance().textRenderer;
		Text t = Text.literal(s).styled(st -> st.withBold(true));
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(cx, y);
		c.getMatrices().scale(sc, sc);
		c.drawText(tr, t, -tr.getWidth(t) / 2, 0, col, true);
		c.getMatrices().popMatrix();
	}

	private static void drawEnd(DrawContext c, Ranked.Match m, int w, int h) {
		String mine = m.team(Ranked.me());
		if (m.winner() == null || mine == null) {
			return;
		}
		boolean won = mine.equals(m.winner());
		boolean none = "none".equals(m.winner()) || "cancelled".equals(m.status());
		float k = Math.min(1F, (System.currentTimeMillis() - endShownAt) / 400F);
		int y = h / 2 - 60;
		Gfx.fill(c, 0, y - 8, w, y + 70, Gfx.withAlpha(0x05060C, (int) (170 * k)));
		big(c, none ? Lang.tr("NO CONTEST", "SEM RESULTADO") : won ? Lang.tr("VICTORY", "VITÓRIA") : Lang.tr("DEFEAT", "DERROTA"), w / 2, y, 3.5F * (0.8F + 0.2F * k),
				none ? 0xFFB0B4C0 : won ? 0xFFFFCF4A : 0xFFFF4E5E);
		if (m.ranked() && !none) {
			String ch = (m.myChange() >= 0 ? "+" : "") + m.myChange() + " ELO";
			var r = Ranked.rating(m.kit(), m.mode());
			big(c, ch + (r != null ? "  ·  " + r.tier() + " " + kitName(m.kit()) + " (" + r.elo() + ")" : ""), w / 2, y + 42, 1.3F,
					m.myChange() >= 0 ? 0xFF55FF7F : 0xFFFF6B6B);
		}
	}
}
