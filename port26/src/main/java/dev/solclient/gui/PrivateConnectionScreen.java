package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.net.PrivateConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Private Connection, laid out like a desktop VPN app: on the left a search box, Recents / Countries / My servers and
 * the country list (flag, name, live ping from the background check); on the right a flat dark map with the route from
 * your place to the server's country, the country in big letters with Connect / Disconnect, and a card at the bottom
 * with "Protected · 12 min", the address, the server count, the protocol and a small live latency graph.
 */
public class PrivateConnectionScreen extends UiScreen {

	private final Screen parent;
	private int scroll;
	private final MapView map = new MapView();
	private static final Map<String, Boolean> HAS_FLAG = new java.util.HashMap<>();
	private static final Map<String, String> NAMES = new java.util.HashMap<>();
	private boolean adding;
	private final TextBox addr = new TextBox(120);
	private final TextBox search = new TextBox(32);
	private String addError = "";
	/** Left menu: 0 recents, 1 countries, 2 my servers. */
	private static int nav = 1;
	/** Countries tab: 0 all, 1 working (answered the check). */
	private static int filter;
	/** Countries connected to lately (newest first). */
	private static final List<String> RECENT = new ArrayList<>();
	private int ly0, ly1; // country list bounds
	/** The country under the mouse in the list (previewed on the map while not connected). */
	private String hoverCc;

	private static final int GREEN = 0xFF3DDC84, RED = 0xFFFF5C5C, TEAL = 0xFF2DD4BF;
	/** Madeira: where the map starts when your own country is not known yet. */
	private static final double[] HOME_FALLBACK = {-16.9, 32.7};

	public PrivateConnectionScreen(Screen parent) {
		super("Private Connection");
		this.parent = parent;
		PrivateConnection.load();
		if (PrivateConnection.free.isEmpty()) {
			PrivateConnection.loadFree();
		}
		PrivateConnection.refreshIp();
	}

	@Override
	public void onClose() {
		dev.solclient.compat.Mc.setScreen(mc, parent);
	}

	static String countryName(String cc) {
		boolean pt = dev.solclient.Lang.code().startsWith("pt");
		return NAMES.computeIfAbsent((pt ? "pt:" : "en:") + cc, k -> {
			String n = Locale.of("", cc).getDisplayCountry(pt ? Locale.of("pt", "PT") : Locale.ENGLISH);
			return n == null || n.isEmpty() ? cc : n;
		});
	}

	private static int pingColour(int ms) {
		return ms < 150 ? GREEN : ms < 400 ? 0xFFFFD34D : 0xFFFF9F43;
	}

	private static void connectTo(String cc) {
		if (cc != null) {
			RECENT.remove(cc);
			RECENT.add(0, cc);
			while (RECENT.size() > 8) {
				RECENT.remove(RECENT.size() - 1);
			}
		}
		PrivateConnection.connectBest(cc);
	}

	@Override
	protected void draw(GuiGraphicsExtractor c, float delta) {
		PrivateConnection.pollLatency();
		Gfx.gradientV(c, 0, 0, width, height, 0x70000000, 0xB0000000);
		int pw = Math.min(700, width - 16), ph = Math.min(380, height - 34);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2 - 6;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, 0xFF17181E, BORDER);

		int sw = Math.max(170, Math.min(220, pw * 2 / 7));
		hoverCc = null;
		drawSidebar(c, px, py, sw, ph);
		drawMain(c, px + sw, py, pw - sw, ph);

		Gfx.textFit(c, dev.solclient.Lang.tr("Only the game's server connections use it. Free servers are public: do not trust them with passwords.",
				"Só as ligações do jogo a servidores usam isto. Os servidores grátis são públicos: não confies neles com palavras-passe."),
				width / 2, pw, py + ph + 8, Theme.DIM);
	}

	// ------------------------------------------------------------------ left: countries
	private void drawSidebar(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		Gfx.rrect(c, x + 5, y + 5, w - 6, h - 10, 8, 0xFF1E2028);
		Gfx.tex(c, Theme.icon2("private_connection"), x + 14, y + 13, 13, 13, 64, 64, 0xFFFFFFFF);
		Gfx.text(c, dev.solclient.Lang.tr("Private Connection", "Ligação Privada"), x + 32, y + 16, Theme.TEXT);

		// search ("browse from...")
		int sx = x + 11, sy = y + 32, sW = w - 18;
		Gfx.rrectBorder(c, sx, sy, sW, 20, 6, 0xFF2A2C35, search.isEmpty() ? 0x1AFFFFFF : Gfx.withAlpha(TEAL, 0xC0));
		Gfx.tex(c, Theme.icon2("search"), sx + 7, sy + 5, 10, 10, 64, 64, Theme.DIM);
		if (search.isEmpty()) {
			Gfx.text(c, dev.solclient.Lang.tr("Search country...", "Procurar país..."), sx + 22, sy + 6, 0xFF7A7F8E);
		}
		search.draw(c, sx, sy, sW, 20, sx + 22, sW - 30, !adding && !search.isEmpty(), TEAL, Theme.TEXT);
		regionButtons(sx, sy, sW, 20, b -> search.click(mouseX));

		// Recents / Countries / My servers
		String[] navs = {dev.solclient.Lang.tr("Recents", "Recentes"), dev.solclient.Lang.tr("Countries", "Países"),
				dev.solclient.Lang.tr("My servers", "Os meus servidores")};
		String[] navIcons = {"timers", "language", "settings_small"};
		int ny = sy + 28;
		for (int i = 0; i < navs.length; i++) {
			boolean sel = nav == i, hov = hovered(sx, ny, sW, 17);
			float a = anim("pcnav" + i, hov);
			if (sel) {
				Gfx.rrect(c, sx, ny, sW, 17, 5, 0xFF33363F);
				Gfx.rrect(c, sx - 4, ny + 3, 2, 11, 1, 0xFFFFFFFF);
			} else if (a > 0.01F) {
				Gfx.rrect(c, sx, ny, sW, 17, 5, Gfx.withAlpha(0xFFFFFF, Math.round(0x0E * a)));
			}
			Gfx.tex(c, Theme.icon2(navIcons[i]), sx + 6, ny + 3, 11, 11, 64, 64, sel ? 0xFFFFFFFF : Theme.DIM);
			Gfx.text(c, navs[i], sx + 22, ny + 5, sel ? 0xFFFFFFFF : Gfx.blend(Theme.DIM, Theme.TEXT, 0.4F + 0.6F * a));
			final int idx = i;
			region(sx, ny, sW, 17, () -> {
				nav = idx;
				scroll = 0;
			});
			ny += 19;
		}

		// All / Working (underlined tabs)
		int ty = ny + 6;
		if (nav == 1) {
			String[] tabs = {dev.solclient.Lang.tr("All", "Todos"), dev.solclient.Lang.tr("Working", "A funcionar")};
			int tx = sx + 2;
			for (int i = 0; i < tabs.length; i++) {
				int tw = Gfx.tw(tabs[i]);
				boolean sel = filter == i, hov = hovered(tx - 3, ty - 3, tw + 6, 15);
				Gfx.text(c, tabs[i], tx, ty, sel ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
				if (sel) {
					Gfx.rrect(c, tx, ty + 11, Math.min(tw, 12), 2, 1, 0xFFFFFFFF);
				}
				final int idx = i;
				region(tx - 3, ty - 3, tw + 6, 15, () -> {
					filter = idx;
					scroll = 0;
				});
				tx += tw + 14;
			}
			String cnt = PrivateConnection.free.size() + "";
			Gfx.text(c, cnt, sx + sW - Gfx.tw(cnt) - 2, ty, 0xFF6C7182);
			ty += 18;
		}

		// rows
		List<Object> rows = rows();
		int lx0 = x + 6, lx1 = x + w;
		ly0 = ty;
		ly1 = y + h - 10;
		int rh = 21;
		int content = rows.size() * rh;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (ly1 - ly0))));
		c.enableScissor(lx0, ly0, lx1, ly1);
		int ry = ly0 - scroll;
		for (Object r : rows) {
			if (ry + rh >= ly0 && ry <= ly1) {
				drawRow(c, r, lx0 + 4, ry, w - 16, rh);
			}
			ry += rh;
		}
		c.disableScissor();
		if (content > ly1 - ly0) {
			int bh = Math.max(16, (ly1 - ly0) * (ly1 - ly0) / content);
			int by = ly0 + Math.round((ly1 - ly0 - bh) * (scroll / (float) (content - (ly1 - ly0))));
			Gfx.rrect(c, x + w - 5, by, 2, bh, 1, 0x40FFFFFF);
		}
		if (rows.isEmpty() || (rows.size() == 1 && "*".equals(rows.get(0)))) {
			String msg = nav == 2 ? dev.solclient.Lang.tr("Add one with \"My server\"", "Adiciona um em \"Meu servidor\"")
					: nav == 0 ? dev.solclient.Lang.tr("Countries you connect to show up here", "Os países a que te ligas aparecem aqui")
					: filter == 1 && !PrivateConnection.free.isEmpty() ? dev.solclient.Lang.tr("Still checking...", "Ainda a verificar...")
					: PrivateConnection.freeState;
			Gfx.textFit(c, msg, x + w / 2, w - 20, ly0 + 30, Theme.DIM);
		}
	}

	private List<Object> rowsCache;
	private String rowsKey = "";
	private long rowsAt;

	/** The list rows, made again only when something changed (or every 0.5 s while servers are being checked). */
	private List<Object> rows() {
		String key = nav + "|" + filter + "|" + search.text() + "|" + System.identityHashCode(PrivateConnection.free) + "|" + RECENT.size() + "|"
				+ PrivateConnection.MINE.size();
		long now = System.currentTimeMillis();
		if (rowsCache == null || !key.equals(rowsKey) || now - rowsAt > 500) {
			rowsCache = makeRows();
			rowsKey = key;
			rowsAt = now;
		}
		return rowsCache;
	}

	/** "*" = fastest country, a country code, or one of my servers. */
	private List<Object> makeRows() {
		List<Object> out = new ArrayList<>();
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		if (nav == 2) {
			for (PrivateConnection.Server s : PrivateConnection.MINE) {
				if (q.isEmpty() || s.label().toLowerCase(Locale.ROOT).contains(q)) {
					out.add(s);
				}
			}
			return out;
		}
		if (q.isEmpty()) {
			out.add("*");
		}
		List<String> ccs;
		if (nav == 0) {
			ccs = new ArrayList<>(RECENT);
		} else {
			ccs = new ArrayList<>(PrivateConnection.free.keySet());
			ccs.sort((a, b) -> countryName(a).compareToIgnoreCase(countryName(b)));
		}
		for (String cc : ccs) {
			if (!q.isEmpty() && !countryName(cc).toLowerCase(Locale.ROOT).contains(q) && !cc.equalsIgnoreCase(q)) {
				continue;
			}
			if (nav == 1 && filter == 1) {
				int[] st = PrivateConnection.COUNTRY.get(cc);
				if (st == null || st[1] == 0) {
					continue;
				}
			}
			out.add(cc);
		}
		return out;
	}

	private void drawRow(GuiGraphicsExtractor c, Object r, int x, int y, int w, int h) {
		boolean hov = hovered(x, y, w, h) && mouseY >= ly0 && mouseY < ly1;
		PrivateConnection.Server cur = PrivateConnection.active() ? PrivateConnection.selected : null;
		String key = r instanceof PrivateConnection.Server s ? s.host + s.port : (String) r;
		float a = anim("pcr" + key, hov);
		boolean sel = cur != null && (r instanceof PrivateConnection.Server m ? cur.host.equals(m.host) && cur.port == m.port
				: !"*".equals(r) && !cur.mine && r.equals(cur.country));
		if (sel) {
			Gfx.rrect(c, x, y + 1, w, h - 2, 6, 0xFF2E3A3A);
		} else if (a > 0.01F) {
			Gfx.rrect(c, x, y + 1, w, h - 2, 6, Gfx.withAlpha(0xFFFFFF, Math.round(0x10 * a)));
		}
		String connecting = PrivateConnection.connecting;
		if ("*".equals(r)) {
			Gfx.rrect(c, x + 6, y + 4, 18, 13, 3, 0xFF22C9A0);
			bolt(c, x + 15, y + 10, 0xFF0D2A22);
			Gfx.text(c, dev.solclient.Lang.tr("Fastest country", "País mais rápido"), x + 31, y + 7, Theme.TEXT);
			String fc = PrivateConnection.fastestCountry();
			if (connecting != null && (connecting.equals("*") || connecting.equals(fc))) {
				spinner(c, x + w - 10, y + h / 2);
			} else if (fc != null) {
				flag(c, fc, x + w - 22, y + 6, 14, 10);
			}
			if (hov) {
				hoverCc = fc;
			}
			region(x, y, w, h, () -> connectTo(PrivateConnection.fastestCountry()));
			return;
		}
		if (r instanceof PrivateConnection.Server m) {
			Gfx.rrect(c, x + 6, y + 4, 18, 13, 3, 0xFF2C2F38);
			Gfx.textFit(c, m.type.toUpperCase(Locale.ROOT).substring(0, 1), x + 15, 14, y + 7, TEAL);
			Gfx.text(c, fit(m.label(), w - 52), x + 31, y + 7, Theme.TEXT);
			int dx = x + w - 16, dy = y + 5;
			boolean hx = hovered(dx, dy, 12, 12);
			Gfx.tex(c, Theme.icon2("close"), dx + 1, dy + 1, 10, 10, 64, 64, hx ? RED : Theme.DIM);
			region(dx, dy, 12, 12, () -> PrivateConnection.removeMine(m));
			region(x, y, w - 18, h, () -> PrivateConnection.select(m));
			return;
		}
		String cc = (String) r;
		if (hov) {
			hoverCc = cc;
		}
		flag(c, cc, x + 6, y + 4, 18, 13);
		int[] st = PrivateConnection.COUNTRY.get(cc);
		int servers = PrivateConnection.free.getOrDefault(cc, List.of()).size();
		boolean dead = st != null && st[1] == 0 && st[2] >= Math.min(8, servers) && st[2] > 0;
		String right = null;
		int rc = 0xFF5A5F6E;
		if (!cc.equals(connecting)) {
			if (st != null && st[0] > 0) {
				right = st[0] + " ms";
				rc = pingColour(st[0]);
			} else {
				right = dead ? dev.solclient.Lang.tr("offline", "sem resposta") : "···";
			}
		}
		int rw = right == null ? 14 : Gfx.tw(right);
		String name = countryName(cc);
		int nameMax = w - 44 - rw;
		Gfx.text(c, fit(name, nameMax), x + 31, y + 7, dead && !sel ? 0xFF8A8F9E : Theme.TEXT);
		if (sel) {
			Gfx.circle(c, x + 35 + Math.min(Gfx.tw(name), nameMax), y + h / 2F, 2.5F, TEAL);
		}
		if (right == null) {
			spinner(c, x + w - 10, y + h / 2);
		} else {
			Gfx.text(c, right, x + w - 6 - rw, y + 7, rc);
		}
		if (hov) {
			tooltip = String.format(Locale.ROOT, "%,d", servers).replace(',', ' ') + dev.solclient.Lang.tr(" servers", " servidores")
					+ (st != null && st[2] > 0 ? "  ·  " + st[1] + "/" + st[2] + dev.solclient.Lang.tr(" answered", " responderam") : "");
		}
		region(x, y, w, h, () -> connectTo(cc));
	}

	// ------------------------------------------------------------------ right: map + connection
	private void drawMain(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		boolean on = PrivateConnection.active();
		PrivateConnection.Server cur = PrivateConnection.selected;
		String cc = on && cur != null ? cur.country : "";
		int mx0 = x + 3, my0 = y + 5, mw = w - 8, mh = h - 10;

		// the map (your country -> the server's country; while not connected, the country under the mouse is previewed)
		double[] home = MapView.centre(PrivateConnection.homeCountry);
		if (home == null) {
			home = HOME_FALLBACK;
		}
		double[] dest = MapView.centre(!cc.isEmpty() ? cc : hoverCc);
		map.draw(c, mx0, my0, mw, mh, home, dest, on && !cc.isEmpty());
		Gfx.outline(c, mx0, my0, mw, mh, 0x14FFFFFF);
		int mid = mx0 + mw / 2;

		// close (top right), my server + refresh (top left)
		int bx = mx0 + mw - 22, byy = my0 + 8;
		boolean hx = hovered(bx, byy, 14, 14);
		Gfx.tex(c, Theme.icon2("close"), bx + 1, byy + 1, 12, 12, 64, 64, hx ? 0xFFFFFFFF : Theme.DIM);
		region(bx, byy, 14, 14, this::onClose);
		int sbw = Math.max(62, Gfx.tw(dev.solclient.Lang.tr("My server", "Meu servidor")) + 16);
		smallButton(c, "pcadd", mx0 + 8, my0 + 8, sbw,
				adding ? dev.solclient.Lang.tr("Cancel", "Cancelar") : dev.solclient.Lang.tr("My server", "Meu servidor"), () -> {
					adding = !adding;
					addr.clear();
					addError = "";
				});
		smallButton(c, "pcref", mx0 + 12 + sbw, my0 + 8, Math.max(56, Gfx.tw(dev.solclient.Lang.tr("Refresh", "Atualizar")) + 16),
				dev.solclient.Lang.tr("Refresh", "Atualizar"), () -> {
					PrivateConnection.status = "";
					PrivateConnection.loadFree();
				});

		// title: flag + country (big), then host · protocol chip
		String title = on && cur != null ? (cc.isEmpty() ? cur.label() : countryName(cc)) : dev.solclient.Lang.tr("Not connected", "Não ligado");
		float big = 1.6F;
		int tw = Math.round(Gfx.tw(title) * big) + (cc.isEmpty() ? 0 : 30);
		int tx = mid - tw / 2, ty = my0 + 30;
		if (!cc.isEmpty()) {
			flag(c, cc, tx, ty + 1, 22, 15);
			tx += 30;
		}
		var m = c.pose();
		m.pushMatrix();
		m.translate(tx, ty + 2);
		m.scale(big, big);
		Gfx.text(c, title, 0, 0, 0xFFFFFFFF);
		m.popMatrix();
		if (on && cur != null) {
			String type = (cur.type == null ? "socks5" : cur.type).toUpperCase(Locale.ROOT);
			String host = cur.host + ":" + cur.port;
			int chipW = Gfx.tw(type) + 10, lineW = Gfx.tw(host) + 6 + chipW;
			int lx = mid - lineW / 2, lyy = ty + 22;
			Gfx.text(c, host, lx, lyy + 2, 0xFFB8BECB);
			Gfx.rrectBorder(c, lx + Gfx.tw(host) + 6, lyy, chipW, 12, 3, 0xFF22252E, 0x40FFFFFF);
			Gfx.text(c, type, lx + Gfx.tw(host) + 11, lyy + 2, 0xFFB8BECB);
		} else {
			Gfx.textFit(c, dev.solclient.Lang.tr("The servers see your real address", "Os servidores veem o teu endereço real"), mid, mw - 40, ty + 24,
					0xFFB8BECB);
		}

		// main button
		String busy = PrivateConnection.connecting;
		String bl = busy != null ? dev.solclient.Lang.tr("Connecting...", "A ligar...")
				: on ? dev.solclient.Lang.tr("Disconnect", "Desligar") : dev.solclient.Lang.tr("Quick connect", "Ligação rápida");
		int bw = Math.max(130, Gfx.tw(bl) + 40), bxx = mid - bw / 2, by2 = ty + 40;
		boolean bh = hovered(bxx, by2, bw, 22);
		float ba = anim("pcmain", bh);
		int bcol = on ? Gfx.blend(0xF0484B56, 0xF05A3A40, ba) : Gfx.blend(0xFF1FB89A, 0xFF2DD4BF, ba);
		Gfx.rrect(c, bxx, by2, bw, 22, 5, bcol);
		if (busy != null) {
			spinner(c, bxx + 16, by2 + 11);
		}
		Gfx.textFit(c, bl, mid, bw - 8, by2 + 7, on ? (ba > 0.5F ? 0xFFFFB0B0 : 0xFFFFFFFF) : 0xFF07231C);
		region(bxx, by2, bw, 22, () -> {
			if (PrivateConnection.connecting != null) {
				return;
			}
			if (PrivateConnection.active()) {
				PrivateConnection.setEnabled(false);
			} else {
				connectTo(PrivateConnection.fastestCountry());
			}
		});
		String msg = PrivateConnection.status;
		if (msg.isEmpty() || msg.equals("On") || msg.equals("Off")) {
			msg = PrivateConnection.scanning()
					? dev.solclient.Lang.tr("Checking servers ", "A verificar servidores ") + PrivateConnection.scanDone + "/" + PrivateConnection.scanTotal
					+ "  ·  " + PrivateConnection.freeState
					: PrivateConnection.freeState;
		}
		Gfx.textFit(c, msg, mid, mw - 40, by2 + 28, 0xFF9AA0AE);

		if (adding) {
			drawAdd(c, mx0 + 24, by2 + 44, mw - 48);
		}
		drawStats(c, mx0 + 10, my0 + mh - 66, mw - 20, 58, on, cur);
	}

	/** Bottom card: Protected · time, address, servers, protocol, latency and the live latency graph. */
	private void drawStats(GuiGraphicsExtractor c, int x, int y, int w, int h, boolean on, PrivateConnection.Server cur) {
		Gfx.rrectBorder(c, x, y, w, h, 7, 0xE6181A21, 0x18FFFFFF);
		String state;
		int scol;
		if (on) {
			long mins = Math.max(0, (System.currentTimeMillis() - PrivateConnection.connectedAt) / 60000L);
			String t = PrivateConnection.connectedAt == 0 ? "" : "  ·  " + (mins < 60 ? mins + " min" : mins / 60 + " h " + mins % 60 + " min");
			state = dev.solclient.Lang.tr("Protected", "Protegido") + t;
			scol = GREEN;
		} else {
			state = dev.solclient.Lang.tr("Unprotected", "Desprotegido");
			scol = RED;
		}
		Gfx.tex(c, Theme.icon2("lock"), x + 10, y + 7, 10, 10, 64, 64, scol);
		Gfx.text(c, state, x + 25, y + 8, scol);

		int colW = Math.min(110, (w - 20) / 4);
		int gx = x + 10, gy = y + 22;
		int lat = on && cur != null && cur.ping > 0 ? cur.ping : -1;
		if (on && PrivateConnection.latencyCount > 0) {
			int last = PrivateConnection.LATENCY[PrivateConnection.LATENCY.length - 1];
			if (last > 0) {
				lat = last;
			}
		}
		stat(c, gx, gy + 4, colW + 20, dev.solclient.Lang.tr("Address", "Endereço"), PrivateConnection.myIp.isEmpty() ? "..." : PrivateConnection.myIp);
		stat(c, gx + colW + 24, gy + 4, colW, dev.solclient.Lang.tr("Latency", "Latência"), lat > 0 ? lat + " ms" : "-");

		// live latency graph
		int chX = gx + colW * 2 + 40, chW = x + w - 10 - chX, chY = y + 8, chH = h - 16;
		if (chW < 40) {
			return;
		}
		Gfx.text(c, dev.solclient.Lang.tr("Latency (ms)", "Latência (ms)"), chX, chY, 0xFF8A90A0);
		int gTop = chY + 12, gBot = chY + chH;
		int[] pts;
		int n;
		synchronized (PrivateConnection.LATENCY) {
			pts = PrivateConnection.LATENCY.clone();
			n = PrivateConnection.latencyCount;
		}
		int max = 100;
		for (int i = pts.length - n; i < pts.length; i++) {
			max = Math.max(max, pts[i]);
		}
		max = (max + 99) / 100 * 100;
		String ml = String.valueOf(max);
		Gfx.text(c, ml, chX + chW - Gfx.tw(ml), chY, 0xFF6C7182);
		c.fill(chX, gBot, chX + chW, gBot + 1, 0x20FFFFFF);
		c.fill(chX, gTop + (gBot - gTop) / 2, chX + chW, gTop + (gBot - gTop) / 2 + 1, 0x0CFFFFFF);
		if (!on || n < 1) {
			Gfx.textFit(c, on ? dev.solclient.Lang.tr("measuring...", "a medir...") : "-", chX + chW / 2, chW, gTop + (gBot - gTop) / 2 - 4, 0xFF6C7182);
			return;
		}
		float step = chW / (float) (pts.length - 1);
		float px = -1, py = 0;
		for (int i = pts.length - n; i < pts.length; i++) {
			float xx = chX + i * step;
			float yy = pts[i] <= 0 ? gBot : gBot - (gBot - gTop) * Math.min(1F, pts[i] / (float) max);
			// soft area under the line
			c.fill(Math.round(xx), Math.round(yy), Math.round(xx + Math.max(1, step)), gBot, 0x162DD4BF);
			if (px >= 0) {
				int seg = Math.max(2, Math.round(Math.abs(xx - px) + Math.abs(yy - py)));
				for (int k = 0; k <= seg; k++) {
					float f = k / (float) seg;
					Gfx.circle(c, px + (xx - px) * f, py + (yy - py) * f, 0.7F, pts[i] <= 0 ? RED : TEAL);
				}
			}
			px = xx;
			py = yy;
		}
		Gfx.circle(c, px, py, 2F, TEAL);
	}

	private static void stat(GuiGraphicsExtractor c, int x, int y, int w, String label, String value) {
		Gfx.text(c, label, x, y, 0xFF8A90A0);
		Gfx.text(c, fit(value, w - 8), x, y + 9, 0xFFFFFFFF);
	}

	private void drawAdd(GuiGraphicsExtractor c, int x, int y, int w) {
		Gfx.rrectBorder(c, x, y, w, 76, 8, 0xF015171D, BORDER);
		Gfx.text(c, dev.solclient.Lang.tr("Add your own server", "Adicionar o teu servidor"), x + 10, y + 9, Theme.TEXT);
		Gfx.textFit(c, "host:port  ·  host:port:user:pass  ·  socks4://  http://", x + w / 2, w - 20, y + 22, Theme.DIM);
		int fw = w - 20 - 66;
		Gfx.rrectBorder(c, x + 10, y + 36, fw, 22, 7, 0xFF0A0B0E, TEAL);
		addr.draw(c, x + 10, y + 36, fw, 22, x + 18, fw - 16, true, TEAL, Theme.TEXT);
		regionButtons(x + 10, y + 36, fw, 22, b -> addr.click(mouseX));
		smallButton(c, "addok", x + w - 70, y + 37, 60, dev.solclient.Lang.tr("Add", "Adicionar"), this::addMine);
		if (!addError.isEmpty()) {
			Gfx.textFit(c, addError, x + w / 2, w - 20, y + 63, 0xFFFF8A8A);
		}
	}

	/** A quiet text button: no box, the text lights up (and gets a thin line) when the mouse is over it. */
	private void smallButton(GuiGraphicsExtractor c, String id, int x, int y, int w, String label, Runnable run) {
		boolean hov = hovered(x, y, w, 16);
		float a = anim(id, hov);
		int col = Gfx.blend(0xFF8F96A6, 0xFFFFFFFF, a);
		int tw = Gfx.tw(label);
		Gfx.text(c, label, x + (w - tw) / 2, y + 4, col);
		if (a > 0.05F) {
			c.fill(x + (w - tw) / 2, y + 14, x + (w + tw) / 2, y + 15, Gfx.withAlpha(TEAL & 0xFFFFFF, Math.round(200 * a)));
		}
		region(x, y, w, 16, run);
	}

	/** Small lightning bolt (fastest country). */
	private static void bolt(GuiGraphicsExtractor c, int cx, int cy, int col) {
		c.fill(cx, cy - 4, cx + 2, cy - 3, col);
		c.fill(cx - 1, cy - 3, cx + 1, cy - 1, col);
		c.fill(cx - 2, cy - 1, cx + 2, cy, col);
		c.fill(cx - 1, cy, cx + 1, cy + 2, col);
		c.fill(cx - 2, cy + 2, cx, cy + 3, col);
	}

	/** Three pulsing dots: being tested / connecting. */
	private static void spinner(GuiGraphicsExtractor c, int cx, int cy) {
		double t = System.currentTimeMillis() / 1000D;
		for (int i = 0; i < 3; i++) {
			float a = (float) (0.35 + 0.65 * Math.max(0, Math.sin(t * 6 - i * 0.9)));
			Gfx.circle(c, cx - 6 + i * 5, cy, 1.6F, Gfx.withAlpha(0xFFFFFF, Math.round(255 * a)));
		}
	}

	private void flag(GuiGraphicsExtractor c, String cc, int x, int y, int w, int h) {
		String k = cc.toLowerCase(Locale.ROOT);
		var id = Gfx.id("textures/gui/flag/" + k + ".png");
		boolean has = HAS_FLAG.computeIfAbsent(k, kk -> mc.getResourceManager().getResource(id).isPresent());
		if (has) {
			Gfx.tex(c, id, x, y, w, h, 64, 48, 0xFFFFFFFF);
		} else {
			Gfx.rrect(c, x, y, w, h, 2, 0xFF2C2F38);
			Gfx.textFit(c, cc, x + w / 2, w, y + (h - 8) / 2, Theme.DIM);
		}
	}

	private void addMine() {
		PrivateConnection.Server s = PrivateConnection.parse(addr.text());
		if (s == null) {
			addError = dev.solclient.Lang.tr("Use host:port (for example 1.2.3.4:1080)", "Usa host:porta (por exemplo 1.2.3.4:1080)");
			return;
		}
		PrivateConnection.addMine(s);
		PrivateConnection.select(s);
		adding = false;
		nav = 2;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, scroll - (int) Math.round(v * 21));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (adding) {
			if (k == 257 || k == 335) {
				addMine();
			} else if (k == 256) {
				adding = false;
			} else {
				addr.key(k);
			}
			return true;
		}
		if (k == 256) {
			if (!search.isEmpty()) {
				search.clear();
				return true;
			}
			onClose();
			return true;
		}
		if (search.key(k)) {
			scroll = 0;
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (adding) {
			addr.typed(input.codepoint());
		} else {
			search.typed(input.codepoint()); // typing anywhere searches the countries
			if (nav == 2) {
				nav = 1;
			}
			scroll = 0;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
		addr.drag(sx(click.x()));
		search.drag(sx(click.x()));
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
		addr.release();
		search.release();
		return super.mouseReleased(click);
	}
}
