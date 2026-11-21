package dev.sleepclient.gui;

import dev.sleepclient.Config;
import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.mods.HostWorldMod;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.HostWorld;
import dev.sleepclient.social.KuboRelay;
import dev.sleepclient.social.RemoteTextures;
import dev.sleepclient.social.Social;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Host World (v87 look): one quiet panel in the same style as the Right Shift menu.
 * <ul>
 * <li>Header: world name and a status line (dot + "Hosting · 2/8").</li>
 * <li>Sliding tabs: Settings / Players / Invite.</li>
 * <li>Rows like the menu settings: label on the left, the control on the right (switch, ‹ value ›, slider, field).</li>
 * <li>Footer: one wide button (Host World, or the address with Copy + Stop while hosting).</li>
 * </ul>
 * Shift + Tab in game opens it on Invite while hosting.
 */
public class HostWorldScreen extends UiScreen {

	private final Screen parent;
	private int tab; // 0 settings, 1 invite, 2 players & whitelist
	private boolean portFocus, nameFocus;
	private String nameText = "";
	private float scroll;
	private final java.util.Set<String> sent = new java.util.HashSet<>();

	private static final int ROW_H = 24, GAP = 4, FIELD_BG = 0xFF0C0D11, LINE = 0x14FFFFFF, OK = 0xFF4ADE80, WARN = 0xFFFFD27A;

	public HostWorldScreen(Screen parent) {
		this(parent, false);
	}

	public HostWorldScreen(Screen parent, boolean inviteTab) {
		super("Host World");
		this.parent = parent;
		this.tab = inviteTab && HostWorld.hosting() ? 1 : 0;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ frame
	@Override
	protected void draw(GuiGraphicsExtractor c, float delta) {
		c.fill(0, 0, width, height, 0x88000000);
		HostWorldMod o = HostWorldMod.instance;
		if (o == null) {
			return;
		}
		int pw = Math.min(340, width - 20), ph = Math.min(height - 28, 300);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2 - 6;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, Theme.PANEL_UI, Theme.CARD_BORDER);

		// powered by (under the panel)
		String pb = KuboRelay.POWERED_BY;
		int pbw = Gfx.twRaw(pb) + 10, pbx = width / 2 - pbw / 2, pby = py + ph + 6;
		Gfx.circle(c, pbx + 2, pby + 4, 2F, KuboRelay.hostConnected() ? OK : 0xFF5E6272);
		Gfx.textRaw(c, pb, pbx + 8, pby, 0xFF6B7080);

		// header: world name + status line, close on the right
		int hx = px + 14, hy = py + 12;
		Gfx.text(c, fitRaw(HostWorld.worldName(), pw - 60), hx, hy, Theme.TEXT);
		boolean hosting = HostWorld.hosting();
		String status;
		int dot;
		if (hosting) {
			status = Lang.tr("Hosting", "A hospedar") + "  ·  " + HostWorld.playerCount() + "/" + Math.round(o.slots.f()) + Lang.tr(" players", " jogadores");
			dot = OK;
		} else if (HostWorld.canHost()) {
			status = Lang.tr("Not hosting yet", "Ainda não está aberto");
			dot = 0xFF5E6272;
		} else {
			status = Lang.tr("Open a singleplayer world first", "Abre primeiro um mundo singleplayer");
			dot = WARN;
		}
		Gfx.circle(c, hx + 2.5F, hy + 17.5F, 2.5F, dot);
		Gfx.textRaw(c, fitRaw(status, pw - 60), hx + 9, hy + 14, Theme.DIM);
		int cx = px + pw - 24, cy = py + 10;
		float ha = anim("hwClose", hovered(cx, cy, 16, 16));
		if (ha > 0.01F) {
			Gfx.rrect(c, cx, cy, 16, 16, 4, Gfx.withAlpha(0xFFFFFF, Math.round(0x18 * ha)));
		}
		Gfx.textC(c, "×", cx + 8, cy + 4, Gfx.blend(Theme.DIM, Theme.TEXT, ha));
		region(cx, cy, 16, 16, this::onClose);

		// tabs (one soft highlight slides to the selected one)
		int ty = py + 42, tx = px + 12, tw = pw - 24;
		Gfx.rrect(c, tx, ty, tw, 22, 7, Theme.ROW);
		int seg = (tw - 4) / 3;
		int[] order = {0, 2, 1}; // Settings, Players, Invite
		String[] names = {Lang.tr("Settings", "Definições"), Lang.tr("Players", "Jogadores"), Lang.tr("Invite", "Convidar")};
		int selIdx = tab == 0 ? 0 : tab == 2 ? 1 : 2;
		SlideBg.draw(c, "hostTabs", tx + 2 + selIdx * seg, ty + 2, seg, 18, 5F, SlideBg.colour(), true);
		for (int i = 0; i < 3; i++) {
			int t = order[i];
			boolean enabled = t != 1 || hosting;
			boolean on = selIdx == i;
			int sx = tx + 2 + i * seg;
			boolean hov = enabled && hovered(sx, ty + 2, seg, 18);
			Gfx.textFit(c, names[i], sx + seg / 2, seg - 6, ty + 7, !enabled ? 0xFF4A4E5A : on ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
			if (enabled) {
				region(sx, ty + 2, seg, 18, () -> {
					if (tab != t) {
						tab = t;
						scroll = 0;
					}
				});
			}
		}
		int bodyY = ty + 30, footH = 40;
		int bodyH = py + ph - footH - bodyY;
		c.fill(px + 1, py + ph - footH, px + pw - 1, py + ph - footH + 1, LINE);

		if (!HostWorld.canHost() && !hosting) {
			Gfx.textC(c, Lang.tr("Host World works in your own singleplayer worlds.", "O Host World funciona nos teus mundos singleplayer."), width / 2,
					bodyY + bodyH / 2 - 10, Theme.DIM);
			Gfx.textC(c, Lang.tr("Open one, then press Esc.", "Abre um e carrega em Esc."), width / 2, bodyY + bodyH / 2 + 2, 0xFF5E6272);
			footer(c, px, py + ph - footH, pw, footH, o);
			return;
		}
		if (tab == 1 && hosting) {
			drawInvite(c, px + 12, bodyY, pw - 24, bodyH);
		} else if (tab == 2) {
			drawPlayers(c, px + 12, bodyY, pw - 24, bodyH);
		} else {
			drawSettings(c, px + 12, bodyY, pw - 24, bodyH, o);
		}
		footer(c, px, py + ph - footH, pw, footH, o);
	}

	// ------------------------------------------------------------------ footer
	private void footer(GuiGraphicsExtractor c, int px, int fy, int pw, int fh, HostWorldMod o) {
		int x = px + 12, w = pw - 24, by = fy + 10, bh = 22;
		if (!HostWorld.hosting()) {
			String st = HostWorld.fullStatus();
			boolean can = HostWorld.canHost();
			if (st != null && !st.isEmpty()) {
				int sw = Math.min(w / 2, Gfx.twRaw(st));
				Gfx.textRaw(c, fitRaw(st, w / 2 - 6), x, by + 7, WARN);
				primary(c, x + sw + 8, by, w - sw - 8, bh, Lang.tr("Host World", "Abrir Mundo"), can, this::startHosting);
			} else {
				primary(c, x, by, w, bh, Lang.tr("Host World", "Abrir Mundo"), can, this::startHosting);
			}
			return;
		}
		// hosting: address chip (click = copy) + Stop
		String addr = HostWorld.address();
		int stopW = Gfx.tw(Lang.tr("Stop", "Parar")) + 24;
		int aw = w - stopW - 6;
		boolean hov = hovered(x, by, aw, bh);
		float a = anim("hwAddr", hov);
		Gfx.rrectBorder(c, x, by, aw, bh, 6, Gfx.blend(FIELD_BG, 0xFF15171D, a), Gfx.blend(Theme.CARD_BORDER, Theme.accent(), a * 0.6F));
		String copy = copiedAt > 0 && System.currentTimeMillis() - copiedAt < 1500 ? Lang.tr("Copied", "Copiado") : Lang.tr("Copy", "Copiar");
		int cw = Gfx.twRaw(copy);
		Gfx.textRaw(c, fitRaw(addr, aw - cw - 22), x + 8, by + 7, Theme.TEXT);
		Gfx.textRaw(c, copy, x + aw - 8 - cw, by + 7, copiedAt > 0 && System.currentTimeMillis() - copiedAt < 1500 ? OK : Theme.DIM);
		region(x, by, aw, bh, this::copyAddress);
		danger(c, x + aw + 6, by, stopW, bh, Lang.tr("Stop", "Parar"), () -> {
			HostWorld.stop();
			tab = 0;
			scroll = 0;
		});
	}

	private long copiedAt;

	private void copyAddress() {
		mc.keyboardHandler.setClipboard(HostWorld.address());
		copiedAt = System.currentTimeMillis();
	}

	private void startHosting() {
		Config.save();
		if (HostWorld.host()) {
			tab = 1;
			scroll = 0;
		}
	}

	// ------------------------------------------------------------------ settings tab
	private void drawSettings(GuiGraphicsExtractor c, int x, int y, int w, int h, HostWorldMod o) {
		boolean hosting = HostWorld.hosting();
		// content height: 3 section titles + 7 rows + the hint
		int contentH = 3 * 16 + 7 * (ROW_H + GAP) + 26;
		int maxScroll = Math.max(0, contentH - h);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int top = y, bottom = y + h;
		c.enableScissor(x, top, x + w, bottom); // the GUI scale is already applied by DrawContext
		int yy = y - Math.round(scroll);
		yy = section(c, x, yy, Lang.tr("WORLD", "MUNDO"));
		yy = switchRow(c, x, yy, w, o.cheats, hosting ? Lang.tr("set before hosting", "antes de abrir") : null, !hosting, top, bottom);
		yy = stepperRow(c, x, yy, w, o.gameMode, !hosting, top, bottom);
		yy = stepperRow(c, x, yy, w, o.difficulty, true, top, bottom);
		yy = sliderRow(c, x, yy, w, o.slots, top, bottom);
		yy = section(c, x, yy, Lang.tr("WHO CAN JOIN", "QUEM PODE ENTRAR"));
		yy = stepperRow(c, x, yy, w, o.privacy, true, top, bottom);
		yy = switchRow(c, x, yy, w, o.autoHost, null, true, top, bottom);
		yy = section(c, x, yy, Lang.tr("ADVANCED", "AVANÇADO"));
		yy = portRow(c, x, yy, w, o, !hosting, top, bottom);
		String hint = Lang.tr("Friends join with host.endlock.net/" + mc.getUser().getName() + "  ·  Shift + Tab invites",
				"Os amigos entram com host.endlock.net/" + mc.getUser().getName() + "  ·  Shift + Tab convida");
		Gfx.textRaw(c, fitRaw(hint, w - 4), x + 2, yy + 4, 0xFF5E6272);
		c.disableScissor();
		scrollbar(c, x + w + 4, top, h, contentH);
	}

	private void scrollbar(GuiGraphicsExtractor c, int x, int y, int h, int contentH) {
		if (contentH <= h) {
			return;
		}
		ScrollBar.draw(c, x - 2, y, y + h, contentH, scroll, contentH - h);
	}

	private int section(GuiGraphicsExtractor c, int x, int y, String name) {
		Gfx.textRaw(c, name, x + 2, y + 5, 0xFF6B7080);
		return y + 16;
	}

	private static boolean visible(int y, int h, int top, int bottom) {
		return y + h > top && y < bottom;
	}

	private boolean inBody(int top, int bottom) {
		return mouseY >= top && mouseY < bottom;
	}

	private void rowBg(GuiGraphicsExtractor c, int x, int y, int w, String label, boolean enabled) {
		Gfx.rrect(c, x, y, w, ROW_H, 7, Theme.ROW);
		Gfx.textRaw(c, fitRaw(Lang.t(label), w - 140), x + 10, y + 8, enabled ? Theme.TEXT : 0xFF5E6272);
	}

	/** Label + an ON / OFF switch (same idea as the menu's switch: a block slides from red to green). */
	private int switchRow(GuiGraphicsExtractor c, int x, int y, int w, Option.Bool b, String note, boolean enabled, int top, int bottom) {
		if (visible(y, ROW_H, top, bottom)) {
			rowBg(c, x, y, w, b.name, enabled);
			int sw = 38, sh = 14, sx = x + w - sw - 8, sy = y + 5;
			float k = anim("hwSw" + b.name, b.on());
			int track = Gfx.blend(0xFF2A1A1D, 0xFF17301F, k), knob = Gfx.blend(0xFFE5484D, 0xFF2FB36A, k);
			if (!enabled) {
				track = 0xFF1E2027;
				knob = 0xFF4A4E5A;
			}
			Gfx.rrect(c, sx, sy, sw, sh, 4, track);
			float kw = sw / 2F - 1;
			Gfx.rrectF(c, sx + 2 + k * (sw - 4 - kw), sy + 2, kw, sh - 4, 2.5F, knob);
			if (note != null) {
				Gfx.textRaw(c, note, sx - 8 - Gfx.twRaw(note), y + 8, 0xFF5E6272);
			}
			if (enabled && inBody(top, bottom)) {
				region(x, y, w, ROW_H, () -> {
					b.set(!b.on());
					Config.save();
				});
			}
		}
		return y + ROW_H + GAP;
	}

	/** Label + "‹ value ›": left half goes back, right half forward. */
	private int stepperRow(GuiGraphicsExtractor c, int x, int y, int w, Option.Choice ch, boolean enabled, int top, int bottom) {
		if (visible(y, ROW_H, top, bottom)) {
			rowBg(c, x, y, w, ch.name, enabled);
			int cw = 118, cx = x + w - cw - 5, cy = y + 4, chh = ROW_H - 8;
			Gfx.rrect(c, cx, cy, cw, chh, 5, FIELD_BG);
			boolean hl = enabled && hovered(cx, cy, cw / 2, chh) && inBody(top, bottom);
			boolean hr = enabled && hovered(cx + cw / 2, cy, cw - cw / 2, chh) && inBody(top, bottom);
			int arrow = enabled ? Theme.DIM : 0xFF3A3D49;
			Gfx.textRaw(c, "‹", cx + 6, cy + 4, hl ? Theme.TEXT : arrow);
			Gfx.textRaw(c, "›", cx + cw - 6 - Gfx.twRaw("›"), cy + 4, hr ? Theme.TEXT : arrow);
			Gfx.textFit(c, Lang.t(ch.label()), cx + cw / 2, cw - 6, cy + 4, enabled ? Theme.TEXT : 0xFF5E6272);
			if (enabled && inBody(top, bottom)) {
				region(cx, cy, cw / 2, chh, () -> {
					ch.cycle(-1);
					changed();
				});
				region(cx + cw / 2, cy, cw - cw / 2, chh, () -> {
					ch.cycle(1);
					changed();
				});
			}
		}
		return y + ROW_H + GAP;
	}

	private int sliderRow(GuiGraphicsExtractor c, int x, int y, int w, Option.Num n, int top, int bottom) {
		if (visible(y, ROW_H, top, bottom)) {
			rowBg(c, x, y, w, n.name, true);
			String v = Integer.toString(Math.round(n.f()));
			int lw = 96, lx = x + w - lw - 12;
			Gfx.textRaw(c, v, lx - 10 - Gfx.twRaw(v), y + 8, Theme.DIM);
			float k = (n.f() - n.min) / (n.max - n.min);
			SmoothSlider.draw(c, n, lx, y + ROW_H / 2F, lw, k, isDragging(lx - 6, y), hovered(lx - 6, y, lw + 12, ROW_H), v);
			if (inBody(top, bottom)) {
				regionDrag(lx - 6, y, lw + 12, ROW_H, mx -> {
					float t = (float) Math.max(0, Math.min(1, (mx - lx) / lw));
					SmoothSlider.dragTo(n, t);
					n.set(n.min + Math.round(t * (n.max - n.min)));
					Config.save();
				});
			}
		}
		return y + ROW_H + GAP;
	}

	private int portRow(GuiGraphicsExtractor c, int x, int y, int w, HostWorldMod o, boolean enabled, int top, int bottom) {
		if (visible(y, ROW_H, top, bottom)) {
			rowBg(c, x, y, w, o.port.name, enabled);
			int fw = 118, fx = x + w - fw - 5, fy = y + 4, fh = ROW_H - 8;
			String v = HostWorld.hosting() ? Integer.toString(HostWorld.port()) : o.port.get();
			boolean f = portFocus && enabled;
			Gfx.rrectBorder(c, fx, fy, fw, fh, 5, FIELD_BG, f ? Theme.accent() : FIELD_BG);
			boolean blink = f && (System.currentTimeMillis() / 500) % 2 == 0;
			Gfx.textRaw(c, v.isEmpty() && !f ? Lang.tr("Automatic", "Automática") : v + (blink ? "_" : ""), fx + 7, fy + 4,
					v.isEmpty() ? 0xFF5E6272 : Theme.TEXT);
			if (enabled && inBody(top, bottom)) {
				region(fx, fy, fw, fh, () -> portFocus = true);
			}
		}
		return y + ROW_H + GAP;
	}

	private void changed() {
		Config.save();
		if (HostWorld.hosting()) {
			HostWorld.host(); // applies difficulty / privacy now
		}
	}

	// ------------------------------------------------------------------ invite tab
	private void drawInvite(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		// how friends join (relay code, or the IP when the router port is open)
		String how = KuboRelay.hostConnected() ? Lang.tr("Friends with Sleep join with the address below", "Os amigos com Sleep entram com o endereço em baixo")
				: Lang.tr("Share the address below", "Partilha o endereço em baixo");
		Gfx.textRaw(c, fitRaw(how, w - 4), x + 2, y + 2, Theme.DIM);
		if (KuboRelay.hostConnected() && HostWorld.routerOk()) {
			String ip = Lang.tr("Without Sleep: ", "Sem Sleep: ") + HostWorld.ipAddress();
			Gfx.textRaw(c, fitRaw(ip, w - 4), x + 2, y + 13, 0xFF5E6272);
		}
		int ly = y + 28, lh = h - 28;
		Gfx.textRaw(c, Lang.tr("SLEEP FRIENDS", "AMIGOS DO SLEEP"), x + 2, ly - 2, 0xFF6B7080);
		ly += 12;
		lh -= 12;
		List<Social.User> list = new ArrayList<>(Social.friends);
		list.sort((a, b) -> Boolean.compare(b.online(), a.online()));
		if (Social.me == null) {
			Gfx.textFit(c, Lang.tr("Log in to Sleep (Right Shift > Friends) to invite friends", "Entra no Sleep (Right Shift > Amigos) para convidar"), x + w / 2, w - 6, ly + 24,
					Theme.DIM);
			return;
		}
		if (list.isEmpty()) {
			Gfx.textFit(c, Lang.tr("No Sleep friends yet - copy the address instead", "Ainda não tens amigos no Sleep - copia o endereço"), x + w / 2, w - 6, ly + 24, Theme.DIM);
			return;
		}
		int rowH = ROW_H + GAP, maxScroll = Math.max(0, list.size() * rowH - lh);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		c.enableScissor(x, ly, x + w, ly + lh); // the GUI scale is already applied by DrawContext
		for (int i = 0; i < list.size(); i++) {
			Social.User u = list.get(i);
			int ry = ly + i * rowH - Math.round(scroll);
			if (!visible(ry, ROW_H, ly, ly + lh)) {
				continue;
			}
			boolean inView = inBody(ly, ly + lh);
			Gfx.rrect(c, x, ry, w, ROW_H, 7, Theme.ROW);
			face(c, u.mcUsername, x + 5, ry + 4, 16);
			Gfx.circle(c, x + 20, ry + 19, 2.5F, u.online() ? OK : 0xFF4A4E5A);
			Gfx.textRaw(c, fitRaw(u.display(), w - 110), x + 28, ry + 8, u.online() ? Theme.TEXT : Theme.DIM);
			boolean done = sent.contains(u.id) || HostWorld.invited(u.mcUsername);
			int bw = 62, bx = x + w - bw - 4, by = ry + 4;
			if (done) {
				Gfx.rrect(c, bx, by, bw, ROW_H - 8, 5, 0xFF17301F);
				Gfx.textFit(c, Lang.tr("Invited", "Convidado"), bx + bw / 2, bw - 6, by + 4, OK);
			} else {
				primary(c, bx, by, bw, ROW_H - 8, Lang.tr("Invite", "Convidar"), inView, () -> {
					HostWorld.invite(u);
					sent.add(u.id);
				});
			}
		}
		c.disableScissor();
		scrollbar(c, x + w + 4, ly, lh, list.size() * rowH);
	}

	// ------------------------------------------------------------------ players / whitelist / bans
	private void drawPlayers(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		HostWorldMod o = HostWorldMod.instance;
		List<String> online = HostWorld.guests();
		List<String> white = HostWorld.list(o.whitelist), bans = HostWorld.list(o.banned);
		// add to whitelist: field + Add
		boolean f = nameFocus;
		int addW = Gfx.tw(Lang.tr("Add", "Adicionar")) + 22, fw = w - addW - 6;
		Gfx.rrectBorder(c, x, y, fw, 20, 6, FIELD_BG, f ? Theme.accent() : Theme.CARD_BORDER);
		boolean blink = f && (System.currentTimeMillis() / 500) % 2 == 0;
		Gfx.textRaw(c, nameText.isEmpty() && !f ? Lang.tr("Minecraft name to whitelist", "Nick do Minecraft para a whitelist") : nameText + (blink ? "_" : ""),
				x + 8, y + 6, nameText.isEmpty() ? 0xFF5E6272 : Theme.TEXT);
		region(x, y, fw, 20, () -> nameFocus = true);
		primary(c, x + fw + 6, y, addW, 20, Lang.tr("Add", "Adicionar"), true, this::addName);

		int ly = y + 28, lh = h - 28;
		List<Object[]> rows = new ArrayList<>(); // {kind, name}: 0 header, 1 online, 2 whitelist, 3 banned, 4 empty note
		rows.add(new Object[] {0, Lang.tr("IN THE WORLD", "NO MUNDO") + "  " + online.size()});
		for (String n : online) {
			rows.add(new Object[] {1, n});
		}
		if (online.isEmpty()) {
			rows.add(new Object[] {4, HostWorld.hosting() ? Lang.tr("Nobody yet", "Ainda ninguém") : Lang.tr("Not hosting", "Não estás a hospedar")});
		}
		rows.add(new Object[] {0, "WHITELIST  " + white.size()});
		for (String n : white) {
			rows.add(new Object[] {2, n});
		}
		if (white.isEmpty()) {
			rows.add(new Object[] {4, Lang.tr("Empty - invited friends are added here", "Vazia - os amigos convidados ficam aqui")});
		}
		rows.add(new Object[] {0, Lang.tr("BANNED", "BANIDOS") + "  " + bans.size()});
		for (String n : bans) {
			rows.add(new Object[] {3, n});
		}
		int total = 0;
		for (Object[] r : rows) {
			total += height((Integer) r[0]);
		}
		int maxScroll = Math.max(0, total - lh);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		c.enableScissor(x, ly, x + w, ly + lh); // the GUI scale is already applied by DrawContext
		int ry = ly - Math.round(scroll);
		boolean inView = inBody(ly, ly + lh);
		for (Object[] r : rows) {
			int kind = (Integer) r[0];
			String n = (String) r[1];
			int rh = height(kind);
			if (!visible(ry, rh, ly, ly + lh)) {
				ry += rh;
				continue;
			}
			if (kind == 0) {
				Gfx.textRaw(c, n, x + 2, ry + 6, 0xFF6B7080);
			} else if (kind == 4) {
				Gfx.textRaw(c, n, x + 10, ry + 3, 0xFF4A4E5A);
			} else {
				Gfx.rrect(c, x, ry, w, ROW_H, 7, Theme.ROW);
				face(c, n, x + 5, ry + 4, 16);
				Gfx.textRaw(c, n, x + 28, ry + 8, kind == 3 ? Theme.RED : Theme.TEXT);
				int bx = x + w - 4, by = ry + 4;
				if (kind == 1) {
					bx = small(c, bx, by, Lang.tr("Ban", "Banir"), true, inView, () -> HostWorld.ban(n));
					bx = small(c, bx, by, Lang.tr("Kick", "Expulsar"), false, inView, () -> HostWorld.kick(n, "You were kicked by the host."));
					if (!HostWorld.onList(o.whitelist, n)) {
						small(c, bx, by, "Whitelist", false, inView, () -> HostWorld.addTo(o.whitelist, n));
					}
				} else if (kind == 2) {
					bx = small(c, bx, by, Lang.tr("Ban", "Banir"), true, inView, () -> HostWorld.ban(n));
					small(c, bx, by, Lang.tr("Remove", "Remover"), false, inView, () -> HostWorld.removeFrom(o.whitelist, n));
				} else {
					small(c, bx, by, Lang.tr("Unban", "Desbanir"), false, inView, () -> HostWorld.removeFrom(o.banned, n));
				}
			}
			ry += rh;
		}
		c.disableScissor();
		scrollbar(c, x + w + 4, ly, lh, total);
	}

	private static int height(int kind) {
		return kind == 0 ? 18 : kind == 4 ? 16 : ROW_H + GAP;
	}

	private void face(GuiGraphicsExtractor c, String mcName, int x, int y, int size) {
		RemoteTextures.Tex t = dev.sleepclient.social.FaceSkins.of(mcName);
		if (t != null) {
			AccountsScreen.face(c, t, x, y, size);
		} else {
			Gfx.rrect(c, x, y, size, size, 3, 0xFF262931);
		}
	}

	/** Small quiet button right-aligned at bx (red text for dangerous ones); returns the next x to the left. */
	private int small(GuiGraphicsExtractor c, int bx, int y, String label, boolean danger, boolean active, Runnable run) {
		int w = Gfx.tw(label) + 14, h = ROW_H - 8, x = bx - w;
		boolean hov = active && hovered(x, y, w, h);
		if (danger) {
			KuboButton.danger(c, x, y, w, h, label, hov);
		} else {
			KuboButton.neutral(c, x, y, w, h, label, hov);
		}
		if (active) {
			region(x, y, w, h, run);
		}
		return x - 4;
	}

	/** The main action: accent fill, white text, softly lighter on hover. */
	private void primary(GuiGraphicsExtractor c, int x, int y, int w, int h, String label, boolean enabled, Runnable run) {
		boolean hov = enabled && hovered(x, y, w, h);
		if (enabled) {
			KuboButton.primary(c, x, y, w, h, label, hov);
			region(x, y, w, h, run);
		} else {
			KuboButton.draw(c, x, y, w, h, label, false, Theme.OFF_BG, Theme.OFF_BG, 0xFF5E6272);
		}
	}

	/** Stop hosting: dark red, red text. */
	private void danger(GuiGraphicsExtractor c, int x, int y, int w, int h, String label, Runnable run) {
		KuboButton.danger(c, x, y, w, h, label, hovered(x, y, w, h));
		region(x, y, w, h, run);
	}

	private void addName() {
		String n = nameText.trim();
		if (n.matches("[A-Za-z0-9_]{3,16}")) {
			HostWorld.addTo(HostWorldMod.instance.whitelist, n);
			HostWorld.removeFrom(HostWorldMod.instance.banned, n);
			nameText = "";
		}
		nameFocus = false;
	}

	private static String fitRaw(String s, int maxW) {
		if (s == null) {
			return "";
		}
		if (Gfx.twRaw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	// ------------------------------------------------------------------ input
	@Override
	protected boolean clickedNothing(double mx, double my) {
		portFocus = false;
		nameFocus = false;
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * 20;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (nameFocus) {
			if (k == 259) {
				if (!nameText.isEmpty()) {
					nameText = nameText.substring(0, nameText.length() - 1);
				}
				return true;
			}
			if (k == 257 || k == 335) {
				addName();
				return true;
			}
			if (k == 256) {
				nameFocus = false;
				return true;
			}
		}
		if (portFocus) {
			if (k == 259) { // backspace
				String v = HostWorldMod.instance.port.get();
				if (!v.isEmpty()) {
					HostWorldMod.instance.port.set(v.substring(0, v.length() - 1));
				}
				return true;
			}
			if (k == 257 || k == 335 || k == 256) {
				portFocus = false;
				Config.save();
				return true;
			}
		}
		if (k == 256) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		int cp = input.codepoint();
		if (nameFocus) {
			if ((Character.isLetterOrDigit(cp) || cp == '_') && cp < 128 && nameText.length() < 16) {
				nameText += (char) cp;
			}
			return true;
		}
		if (portFocus && cp >= '0' && cp <= '9') {
			String v = HostWorldMod.instance.port.get();
			if (v.length() < 5) {
				HostWorldMod.instance.port.set(v + (char) cp);
			}
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void onClose() {
		Config.save();
		dev.sleepclient.compat.Mc.setScreen(mc, parent);
	}
}
