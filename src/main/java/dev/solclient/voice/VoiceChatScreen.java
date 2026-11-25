package dev.solclient.voice;

import dev.solclient.Config;
import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.gui.AvatarStack;
import dev.solclient.gui.CheckBox;
import dev.solclient.gui.Faces;
import dev.solclient.gui.KuboButton;
import dev.solclient.gui.SlideBg;
import dev.solclient.gui.TextBox;
import dev.solclient.gui.Theme;
import dev.solclient.gui.UiScreen;
import dev.solclient.social.Social;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * The voice chat window (V by default), in the Right Shift menu's layout (v99): sections on the left
 * (Voice, Groups, Players, Settings), the section on the right with plain rows, checkboxes and the shop buttons.
 * Groups: make one, invite friends by clicking their heads, drag the members into the order you like,
 * remove people (the owner), join / leave the call.
 */
public class VoiceChatScreen extends UiScreen {

	private static int view; // remembered between opens
	private static String selGroup;
	private static final String[] TABS = {"Voice", "Groups", "Players", "Settings"};
	private static final String[] TAB_ICONS = {"voice_chat", "players", "player", "settings_small"};
	private boolean bindingPtt, bindingOpen;
	private int scroll;
	private final TextBox groupName = new TextBox(24);
	private boolean nameFocus;
	// member list drag (reorder)
	private String dragMember;
	private int dragFrom = -1;
	private double dragY, dragGrabOffset;
	private final java.util.Map<String, float[]> rowY = new java.util.HashMap<>();

	public VoiceChatScreen() {
		super("Voice Chat");
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static VoiceChatMod mod() {
		return VoiceChatMod.instance;
	}

	private static Identifier vt(String n) {
		return Gfx.id("textures/voice/" + n + ".png");
	}

	private static Identifier icon(String n) {
		return Gfx.id("textures/gui/icon/" + n + ".png");
	}

	// ================================================================ frame
	@Override
	protected void draw(DrawContext c, float delta) {
		VoiceChatMod m = mod();
		VoiceEngine e = VoiceEngine.INSTANCE;
		int pw = Math.min(480, width - 24), ph = Math.min(290, height - 24);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, Theme.PANEL_UI, 0xFF23252D);

		// ---- side list
		int sw = 128, sx = px + 8, sy = py + 8, sh = ph - 16;
		Gfx.rrect(c, sx, sy, sw, sh, 8, Theme.SUB);
		Gfx.tex(c, vt("speaker"), sx + 10, sy + 11, 12, 12, 16, 16, 0xFFFFFFFF);
		Gfx.text(c, "Voice Chat", sx + 28, sy + 13, Theme.TEXT);
		for (int i = 0; i < TABS.length; i++) {
			int iy = sy + 34 + i * 28;
			boolean cur = i == view, hov = hovered(sx + 4, iy, sw - 8, 24);
			if (cur) {
				Gfx.rrect(c, sx + 4, iy, sw - 8, 24, 4, SlideBg.colour());
			} else if (hov) {
				Gfx.rrect(c, sx + 4, iy, sw - 8, 24, 4, 0x0CFFFFFF);
			}
			int col = cur ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM;
			Gfx.tex(c, icon(TAB_ICONS[i]), sx + 12, iy + 5, 14, 14, 64, 64, col);
			Gfx.textMid(c, TABS[i], sx + 32, iy, 24, col);
			if (i == 1) {
				int inv = 0;
				for (VoiceGroups.Group g : VoiceGroups.all()) {
					inv += g.invite ? 1 : 0;
				}
				if (inv > 0) {
					Gfx.circle(c, sx + sw - 16, iy + 12, 5, 0xFFE5484D);
					Gfx.textRaw(c, String.valueOf(inv), sx + sw - 16 - Gfx.twRaw(String.valueOf(inv)) / 2, iy + 8, 0xFFFFFFFF);
				}
			}
			final int idx = i;
			region(sx + 4, iy, sw - 8, 24, () -> {
				view = idx;
				scroll = 0;
			});
		}
		// mute / deafen at the bottom of the side list (always at hand)
		int by = sy + sh - 30;
		smallToggle(c, "mute", sx + 8, by, (sw - 22) / 2, m.muted.on() ? "microphone_off" : "microphone", m.muted.on(),
				m.muted.on() ? "Unmute microphone" : "Mute microphone", () -> m.muted.set(!m.muted.on()));
		smallToggle(c, "deaf", sx + 14 + (sw - 22) / 2, by, (sw - 22) / 2, m.deafened.on() ? "speaker_off" : "speaker", m.deafened.on(),
				m.deafened.on() ? "Hear everybody again" : "Hear nobody", () -> m.deafened.set(!m.deafened.on()));

		// ---- the section
		int cx = sx + sw + 14, cw = px + pw - 14 - cx, cy = py + 14, ch = py + ph - 12 - cy;
		boolean ok = m.connected();
		int dot = ok ? 0xFF4ADE80 : e.error != null || e.silentMic ? 0xFFF87171 : 0xFFFBBF24;
		Gfx.text(c, TABS[view], cx, cy, Theme.TEXT);
		Gfx.circle(c, cx + 3, cy + 18, 2.5F, dot);
		Gfx.textClip(c, m.status(), cx + 10, cy + 14, cw - 12, Theme.DIM);
		int top = cy + 32, h = ch - 32;
		switch (view) {
			case 1 -> groups(c, cx, top, cw, h);
			case 2 -> players(c, e, cx, top, cw, h);
			case 3 -> settings(c, m, e, cx, top, cw, h);
			default -> main(c, m, e, cx, top, cw, h);
		}
	}

	private void smallToggle(DrawContext c, String id, int x, int y, int w, String ic, boolean active, String tip, Runnable run) {
		boolean hov = hovered(x, y, w, 22);
		if (active) {
			KuboButton.danger(c, x, y, w, 22, "", hov);
		} else {
			KuboButton.neutral(c, x, y, w, 22, "", hov);
		}
		Gfx.tex(c, vt(ic), x + w / 2 - 8, y + 3, 16, 16, 16, 16, 0xFFFFFFFF);
		if (hov) {
			tooltip = tip;
		}
		region(x, y, w, 22, () -> {
			run.run();
			Config.save();
		});
	}

	/** A settings-style on / off row: checkbox + name. */
	private void checkRow(DrawContext c, String key, int x, int y, int w, String label, boolean on, Runnable toggle) {
		boolean hov = hovered(x, y, w, 22);
		if (hov) {
			Gfx.rrect(c, x, y, w, 22, 4, 0x0AFFFFFF);
		}
		CheckBox.draw(c, "vc" + key, x + 6, y + 5.5F, 11, on, hov);
		Gfx.textClip(c, label, x + 24, y + 7, w - 30, on ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, hov ? 1F : 0.5F));
		region(x, y, w, 22, () -> {
			toggle.run();
			Config.save();
		});
	}

	private static String header(String s) {
		return Lang.t(s).toUpperCase(java.util.Locale.ROOT);
	}

	private void section(DrawContext c, String s, int x, int y) {
		Gfx.textRaw(c, header(s), x + 2, y, 0xFF6B7080);
	}

	// ================================================================ Voice
	private void main(DrawContext c, VoiceChatMod m, VoiceEngine e, int x, int y, int w, int h) {
		// who is in the call
		section(c, VoiceNet.groupMode() ? VoiceGroups.activeName() : "Around you", x, y);
		List<AvatarStack.Person> people = people(e);
		int sy = y + 14;
		int used = AvatarStack.draw(c, "vcmain", x, sy, 28, 9, people, 5);
		Gfx.textClip(c, describe(people), x + used + (used > 0 ? 10 : 0), sy + 10, w - used - 12, people.isEmpty() ? Theme.DIM : Theme.TEXT);
		int yy = sy + 40;
		section(c, "Microphone", x, yy);
		yy += 12;
		meter(c, x, yy, w, 10, e, m);
		yy += 18;
		checkRow(c, "mute", x, yy, w, "Microphone off", m.muted.on(), () -> m.muted.set(!m.muted.on()));
		yy += 24;
		checkRow(c, "deaf", x, yy, w, "Hear nobody", m.deafened.on(), () -> m.deafened.set(!m.deafened.on()));
		yy += 24;
		checkRow(c, "icons", x, yy, w, "Icons above heads", m.showIcons.on(), () -> m.showIcons.set(!m.showIcons.on()));
		yy += 30;
		if (VoiceNet.groupMode()) {
			int bw = Math.max(110, Gfx.tw("Back to server voice") + 24);
			KuboButton.neutral(c, x, yy, bw, 20, "Back to server voice", hovered(x, yy, bw, 20));
			region(x, yy, bw, 20, VoiceGroups::leaveCall);
		} else if (m.activation.idx() == 1) {
			Gfx.textClip(c, Lang.t("Push To Talk") + ": " + Keys.name(m.pttKey.code()), x + 2, yy + 6, w, Theme.DIM);
		}
	}

	private List<AvatarStack.Person> people(VoiceEngine e) {
		List<AvatarStack.Person> out = new ArrayList<>();
		for (VoiceNet.Member mm : VoiceNet.list()) {
			boolean me = mm.uuid().equals(VoiceNet.you());
			out.add(new AvatarStack.Person(mm.uuid(), mm.name(), me ? e.talking : e.isTalking(mm.uuid())));
		}
		return out;
	}

	/** "Nobody here" / "Rafa is here" / "Rafa and Inês are here" / "Rafa, Inês and 2 others are here". */
	private static String describe(List<AvatarStack.Person> p) {
		if (p.isEmpty()) {
			return Lang.tr("Nobody here", "Ninguém aqui");
		}
		String a = p.get(0).name();
		if (p.size() == 1) {
			return Lang.tr(a + " is here", a + " está aqui");
		}
		String b = p.get(1).name();
		if (p.size() == 2) {
			return Lang.tr(a + " and " + b + " are here", a + " e " + b + " estão aqui");
		}
		int rest = p.size() - 2;
		return Lang.tr(a + ", " + b + " and " + rest + (rest == 1 ? " other are here" : " others are here"),
				a + ", " + b + " e mais " + rest + (rest == 1 ? " estão aqui" : " estão aqui"));
	}

	/** Mic level bar with the activation threshold marker (drag the marker to change it). */
	private void meter(DrawContext c, int x, int y, int w, int h, VoiceEngine e, VoiceChatMod m) {
		Gfx.rrect(c, x, y, w, h, 3, 0xFF0B0C0F);
		float lvl = (Math.max(-70F, Math.min(0F, e.micDb)) + 70F) / 70F;
		boolean over = e.micDb > m.threshold.f();
		int col = m.muted.on() ? 0xFF555A66 : over ? 0xFF4ADE80 : 0xFF6B7280;
		if (lvl > 0.01F) {
			Gfx.rrect(c, x + 1, y + 1, Math.max(3, Math.round((w - 2) * lvl)), h - 2, 2, col);
		}
		if (m.activation.idx() == 0) {
			int tx = x + Math.round((w - 2) * (m.threshold.f() + 70F) / 70F);
			c.fill(tx, y - 2, tx + 2, y + h + 2, 0xFFFFFFFF);
			if (hovered(x, y - 2, w, h + 4)) {
				tooltip = Lang.t("Activation level") + " " + Math.round(m.threshold.f()) + " dB";
			}
			regionDrag(x, y - 2, w, h + 4, mx -> m.threshold.set((float) Math.round(Math.max(-70, Math.min(0, (mx - x) / (w - 2) * 70 - 70)))));
		}
	}

	// ================================================================ Groups
	private void groups(DrawContext c, int x, int y, int w, int h) {
		List<VoiceGroups.Group> list = VoiceGroups.all();
		int lw = 132, lx = x, dx = x + lw + 10, dw = w - lw - 10;
		// ---- list + new group
		int ly = y;
		for (VoiceGroups.Group g : list) {
			if (ly + 26 > y + h - 60) {
				break;
			}
			boolean sel = g.id.equals(selGroup), hov = hovered(lx, ly, lw, 26);
			Gfx.rrect(c, lx, ly, lw, 26, 4, sel ? SlideBg.colour() : hov ? 0x0CFFFFFF : Theme.ROW);
			boolean live = g.id.equals(VoiceGroups.activeId());
			Gfx.textClip(c, g.name, lx + 8, ly + 5, lw - 16, sel ? 0xFFFFFFFF : Theme.TEXT);
			String sub = g.invite ? Lang.tr("Invite from ", "Convite de ") + g.invitedBy : live ? Lang.tr("In the call", "Na chamada") : g.members.size() + Lang.tr(" people", " pessoas");
			Gfx.textClip(c, sub, lx + 8, ly + 15, lw - 16, g.invite ? 0xFFFBBF24 : live ? 0xFF4ADE80 : 0xFF6B7080);
			final String id = g.id;
			region(lx, ly, lw, 26, () -> selGroup = id);
			ly += 29;
		}
		if (selGroup == null && !list.isEmpty()) {
			selGroup = list.get(0).id;
		}
		// new group: name + button
		int ny = y + h - 56;
		section(c, "New group", lx, ny);
		Gfx.rrectBorder(c, lx, ny + 12, lw, 20, 5, 0xFF0A0B0E, nameFocus ? Theme.accent() : 0xFF23252D);
		groupName.draw(c, lx, ny + 12, lw, 20, lx + 6, lw - 12, nameFocus, Theme.accent(), Theme.TEXT);
		if (groupName.isEmpty() && !nameFocus) {
			Gfx.text(c, "Name...", lx + 6, ny + 18, 0xFF5E6272);
		}
		region(lx, ny + 12, lw, 20, () -> nameFocus = true);
		int bw = lw;
		KuboButton.neutral(c, lx, ny + 35, bw, 18, "Create group", hovered(lx, ny + 35, bw, 18));
		region(lx, ny + 35, bw, 18, this::createGroup);

		// ---- the selected group
		VoiceGroups.Group g = selGroup == null ? null : VoiceGroups.get(selGroup);
		if (g == null) {
			Gfx.textClip(c, Lang.tr("Make a group and invite friends: you hear each other on any server.",
					"Cria um grupo e convida amigos: ouvem-se em qualquer servidor."), dx, y + 4, dw, Theme.DIM);
			return;
		}
		Gfx.textClip(c, g.name, dx, y + 2, dw, Theme.TEXT);
		List<AvatarStack.Person> heads = new ArrayList<>();
		for (String n : g.members) {
			heads.add(new AvatarStack.Person(n.toLowerCase(java.util.Locale.ROOT), n, talkingName(n)));
		}
		AvatarStack.draw(c, "vcgroup" + g.id, dx, y + 14, 22, 7, heads, 6);
		// buttons
		int byy = y + 42;
		boolean live = g.id.equals(VoiceGroups.activeId());
		String join = g.invite ? "Accept and join" : live ? "Leave the call" : "Join the call";
		int jw = Math.min(dw / 2 - 3, Math.max(90, Gfx.tw(join) + 20));
		if (live) {
			KuboButton.neutral(c, dx, byy, jw, 20, join, hovered(dx, byy, jw, 20));
			region(dx, byy, jw, 20, VoiceGroups::leaveCall);
		} else {
			KuboButton.draw(c, dx, byy, jw, 20, join, hovered(dx, byy, jw, 20), Theme.GREEN_BG, Theme.GREEN_BG_HOVER, 0xFFFFFFFF);
			region(dx, byy, jw, 20, () -> {
				VoiceGroups.join(g);
				view = 0;
			});
		}
		String out = g.invite ? "Decline" : "Leave group";
		int ow = Math.min(dw - jw - 6, Math.max(70, Gfx.tw(out) + 20));
		KuboButton.danger(c, dx + jw + 6, byy, ow, 20, out, hovered(dx + jw + 6, byy, ow, 20));
		region(dx + jw + 6, byy, ow, 20, () -> {
			VoiceGroups.leave(g);
			selGroup = null;
		});

		// members: drag to change the order (grip on the left), owner can remove
		int my = byy + 28;
		section(c, "Members", dx, my);
		my += 12;
		members(c, g, dx, my, dw, Math.max(0, y + h - 58 - my));

		// invite: friends' heads, click one to add them
		int iy = y + h - 40;
		section(c, "Invite friends", dx, iy);
		int hx = dx, hs = 20;
		int shown = 0;
		for (Social.User u : Social.friends) {
			String n = u.display();
			if (VoiceGroups.has(g, n) || n.equals("?")) {
				continue;
			}
			if (hx + hs > dx + dw) {
				break;
			}
			boolean hov = hovered(hx, iy + 12, hs, hs);
			Gfx.rrectF(c, hx, iy + 12, hs, hs, 4F, hov ? 0xFF57534E : 0xFF44403C);
			Faces.draw(c, n, hx + 3, iy + 15, hs - 6, 255);
			if (u.online()) {
				Gfx.circle(c, hx + hs - 3, iy + 12 + hs - 3, 3, 0xFF4ADE80);
			}
			if (hov) {
				tooltip = Lang.tr("Invite ", "Convidar ") + n;
			}
			region(hx, iy + 12, hs, hs, () -> VoiceGroups.invite(g, n));
			hx += hs + 4;
			shown++;
		}
		if (shown == 0) {
			Gfx.textClip(c, Social.me == null ? Lang.tr("Log in to Caos to invite friends", "Entra no Caos para convidar amigos")
					: Lang.tr("Every friend is in this group", "Todos os amigos estão neste grupo"), dx + 2, iy + 18, dw, Theme.DIM);
		}
	}

	private boolean talkingName(String n) {
		VoiceEngine e = VoiceEngine.INSTANCE;
		if (n.equalsIgnoreCase(mc.getSession().getUsername())) {
			return e.talking && VoiceNet.groupMode();
		}
		for (VoiceNet.Member mm : VoiceNet.members().values()) {
			if (mm.name().equalsIgnoreCase(n)) {
				return e.isTalking(mm.uuid());
			}
		}
		return false;
	}

	private boolean inCall(String n) {
		for (VoiceNet.Member mm : VoiceNet.members().values()) {
			if (mm.name().equalsIgnoreCase(n)) {
				return true;
			}
		}
		return false;
	}

	/** Member rows like the user's ReorderList: grip, head, name; the held row lifts and the others make room. */
	private void members(DrawContext c, VoiceGroups.Group g, int x, int y, int w, int h) {
		int rh = 22, gap = 3;
		List<String> list = g.members;
		boolean owner = VoiceGroups.isOwner(g);
		// where the dragged row would go
		int target = dragFrom;
		if (dragMember != null) {
			target = (int) Math.round((dragY - dragGrabOffset - y) / (rh + gap));
			target = Math.max(0, Math.min(list.size() - 1, target));
		}
		c.enableScissor(x - 2, y - 2, x + w + 2, y + h);
		String held = null;
		for (int i = 0; i < list.size(); i++) {
			String n = list.get(i);
			int slot = i;
			if (dragMember != null && !n.equals(dragMember)) {
				if (dragFrom < target && i > dragFrom && i <= target) {
					slot = i - 1;
				} else if (dragFrom > target && i >= target && i < dragFrom) {
					slot = i + 1;
				}
			}
			float want = y + slot * (rh + gap);
			float[] ry = rowY.computeIfAbsent(g.id + n, k -> new float[] {want});
			ry[0] += (want - ry[0]) * 0.35F;
			if (n.equals(dragMember)) {
				held = n;
				continue;
			}
			memberRow(c, g, n, x, Math.round(ry[0]), w, rh, false, owner, i);
		}
		if (held != null) {
			int hy = (int) Math.round(Math.max(y, Math.min(y + (list.size() - 1) * (rh + gap), dragY - dragGrabOffset)));
			memberRow(c, g, held, x, hy, w, rh, true, owner, dragFrom);
		}
		c.disableScissor();
		pendingTarget = target;
	}

	private int pendingTarget = -1;

	private void memberRow(DrawContext c, VoiceGroups.Group g, String n, int x, int y, int w, int rh, boolean lifted, boolean owner, int index) {
		boolean hov = hovered(x, y, w, rh) && dragMember == null;
		if (lifted) {
			Gfx.rrectF(c, x + 1, y + 3, w, rh, 4F, 0x66000000); // shadow under the lifted row
		}
		Gfx.rrectBorder(c, x, y, w, rh, 6, lifted ? 0xFF1D1D1A : hov ? 0xFF1A1C22 : Theme.ROW, lifted ? 0xFF57534E : 0xFF2A2C33);
		// grip: two columns of three dots
		int gc = lifted ? 0xFFA8A29E : 0xFF57534E;
		for (int r = 0; r < 3; r++) {
			for (int k = 0; k < 2; k++) {
				Gfx.circle(c, x + 7 + k * 4, y + 6 + r * 5, 1.1F, gc);
			}
		}
		Faces.draw(c, n, x + 18, y + 4, 14, 255);
		boolean me = n.equalsIgnoreCase(mc.getSession().getUsername());
		String label = n + (me ? Lang.tr(" (you)", " (tu)") : "") + (g.owner != null && g.owner.equalsIgnoreCase(n) ? Lang.tr("  · owner", "  · dono") : "");
		Gfx.textClip(c, label, x + 37, y + 7, w - 70, Theme.TEXT);
		// in the call / talking
		boolean talk = talkingName(n), in = inCall(n) || me && VoiceNet.groupMode() && g.id.equals(VoiceGroups.activeId());
		if (in) {
			Gfx.circle(c, x + w - (owner && !me ? 28 : 12), y + rh / 2F, 3, talk ? 0xFF4ADE80 : 0xFF6B7080);
		}
		if (owner && !me) {
			int bx = x + w - 18, by = y + 4;
			boolean xh = hovered(bx, by, 14, 14);
			if (xh) {
				Gfx.rrect(c, bx, by, 14, 14, 3, 0x40E5484D);
				tooltip = Lang.tr("Remove ", "Remover ") + n;
			}
			int col = xh ? 0xFFFF8A8A : 0xFF8C90A0;
			for (int i = 0; i < 6; i++) {
				c.fill(bx + 4 + i, by + 4 + i, bx + 5 + i, by + 5 + i, col);
				c.fill(bx + 9 - i, by + 4 + i, bx + 10 - i, by + 5 + i, col);
			}
			region(bx, by, 14, 14, () -> VoiceGroups.remove(g, n));
		}
		if (!lifted && dragMember == null) {
			final int idx = index;
			region(x, y, w - (owner && !me ? 22 : 0), rh, () -> {
				dragMember = n;
				dragFrom = idx;
				dragY = mouseY;
				dragGrabOffset = mouseY - y;
			});
		}
	}

	private void createGroup() {
		VoiceGroups.Group g = VoiceGroups.create(groupName.text());
		groupName.clear();
		nameFocus = false;
		selGroup = g.id;
	}

	// ================================================================ Players
	private void players(DrawContext c, VoiceEngine e, int x, int y, int w, int h) {
		List<AbstractClientPlayerEntity> list = nearby();
		if (list.isEmpty()) {
			Gfx.textClip(c, Lang.tr("Nobody near you. Players show up here when they are in your world.",
					"Ninguém perto de ti. Os jogadores aparecem aqui quando estão no teu mundo."), x + 2, y + 4, w, Theme.DIM);
			return;
		}
		int rh = 30;
		c.enableScissor(x - 2, y - 2, x + w + 2, y + h);
		int yy = y - scroll;
		for (AbstractClientPlayerEntity p : list) {
			String u = p.getUuid().toString().replace("-", "");
			VoiceEngine.Peer peer = e.peer(u);
			boolean has = peer != null && peer.on;
			boolean muted = VoiceConfig.muted(u);
			Gfx.rrect(c, x, yy, w, rh - 4, 6, Theme.ROW);
			try {
				net.minecraft.client.gui.PlayerSkinDrawer.draw(c, p.getSkin(), x + 5, yy + 5, 16);
			} catch (Throwable ignored) {
			}
			Gfx.textClip(c, p.getName().getString(), x + 26, yy + 4, 92, Theme.TEXT);
			Gfx.textClip(c, has ? (e.isTalking(u) ? "Talking" : "Voice on") : "No voice", x + 26, yy + 14, 92, e.isTalking(u) ? 0xFF4ADE80 : Theme.DIM);
			int sx = x + 128, swd = w - 128 - 30;
			float vol = VoiceConfig.playerVolume(u);
			slider(c, "pv" + u, sx, yy + 2, swd, Math.round(vol * 100) + "%", vol / 2F, v -> VoiceConfig.setPlayerVolume(u, Math.round(v * 40F) * 5 / 100F));
			int mx = x + w - 24;
			boolean hov = hovered(mx, yy + 3, 20, 20);
			if (muted) {
				KuboButton.danger(c, mx, yy + 3, 20, 20, "", hov);
			} else {
				KuboButton.neutral(c, mx, yy + 3, 20, 20, "", hov);
			}
			Gfx.tex(c, vt(muted ? "speaker_small_off" : "other_volume"), mx + 2, yy + 5, 16, 16, 16, 16, 0xFFFFFFFF);
			if (hov) {
				tooltip = (muted ? Lang.tr("Unmute ", "Voltar a ouvir ") : Lang.tr("Mute ", "Silenciar ")) + p.getName().getString();
			}
			region(mx, yy + 3, 20, 20, () -> VoiceConfig.setMuted(u, !muted));
			yy += rh;
		}
		c.disableScissor();
		scroll = Math.max(0, Math.min(scroll, list.size() * rh - h));
	}

	private List<AbstractClientPlayerEntity> nearby() {
		List<AbstractClientPlayerEntity> out = new ArrayList<>();
		if (mc.world != null) {
			for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
				if (p != mc.player) {
					out.add(p);
				}
			}
		}
		if (mc.player != null) {
			out.sort((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)));
		}
		return out;
	}

	// ================================================================ Settings
	private void settings(DrawContext c, VoiceChatMod m, VoiceEngine e, int x, int y, int w, int h) {
		int rh = 20, gap = 6;
		c.enableScissor(x - 2, y - 2, x + w + 2, y + h);
		int yy = y - scroll;
		yy = device(c, "mic", x, yy, w, "Microphone", VoiceConfig.mic(), VoiceEngine.microphones(), VoiceConfig::setMic) + gap;
		yy = device(c, "spk", x, yy, w, "Speaker", VoiceConfig.speaker(), VoiceEngine.speakers(), VoiceConfig::setSpeaker) + gap;
		section(c, "Microphone level", x, yy);
		meter(c, x, yy + 12, w, 10, e, m);
		yy += 30;
		int half = (w - gap) / 2;
		String act = Lang.t("Activation") + ": " + Lang.t(m.activation.label());
		KuboButton.neutral(c, x, yy, half, rh, act, hovered(x, yy, half, rh));
		region(x, yy, half, rh, () -> {
			m.activation.cycle(1);
			Config.save();
		});
		String bind = bindingPtt ? Lang.tr("> press a key <", "> carrega numa tecla <") : Lang.t("PTT Key") + ": " + Keys.name(m.pttKey.code());
		KuboButton.neutral(c, x + half + gap, yy, half, rh, bind, hovered(x + half + gap, yy, half, rh));
		region(x + half + gap, yy, half, rh, () -> {
			bindingPtt = true;
			bindingOpen = false;
		});
		yy += rh + gap + 2;
		slider(c, "thr", x, yy, w, Lang.t("Activation Level") + ": " + Math.round(m.threshold.f()) + " dB", (m.threshold.f() + 70F) / 70F,
				v -> m.threshold.set((float) Math.round(v * 70F - 70F)));
		yy += 24;
		slider(c, "gain", x, yy, w, Lang.t("Microphone Gain") + ": " + Math.round(m.micGain.f()) + " dB", (m.micGain.f() + 10F) / 30F,
				v -> m.micGain.set((float) Math.round(v * 30F - 10F)));
		yy += 24;
		slider(c, "vol", x, yy, w, Lang.t("Voice Chat Volume") + ": " + Math.round(m.volume.f()) + "%", m.volume.f() / 200F,
				v -> m.volume.set((float) (Math.round(v * 40F) * 5)));
		yy += 24;
		slider(c, "dist", x, yy, w, Lang.t("Distance") + ": " + Math.round(m.distance.f()) + Lang.t(" blocks"), (m.distance.f() - 8F) / 88F,
				v -> m.distance.set((float) (8 + Math.round(v * 22F) * 4)));
		yy += 26;
		checkRow(c, "3d", x, yy, half, "3D Audio", m.spatial.on(), () -> m.spatial.set(!m.spatial.on()));
		checkRow(c, "hud", x + half + gap, yy, half, "My Mic Icon", m.hudIcon.on(), () -> m.hudIcon.set(!m.hudIcon.on()));
		yy += 26;
		String test = e.testing ? "Stop Mic Test" : "Test Microphone";
		KuboButton.neutral(c, x, yy, half, rh, test, hovered(x, yy, half, rh));
		region(x, yy, half, rh, () -> e.testing = !e.testing);
		String ob = bindingOpen ? Lang.tr("> press a key <", "> carrega numa tecla <") : Lang.t("Menu Key") + ": " + Keys.name(m.openKey.code());
		KuboButton.neutral(c, x + half + gap, yy, half, rh, ob, hovered(x + half + gap, yy, half, rh));
		region(x + half + gap, yy, half, rh, () -> {
			bindingOpen = true;
			bindingPtt = false;
		});
		yy += rh;
		c.disableScissor();
		int content = yy + scroll - y;
		scroll = Math.max(0, Math.min(scroll, content - h));
	}

	/** "< device name >" row: click (left / right) picks the next / previous device and restarts audio. */
	private int device(DrawContext c, String id, int x, int y, int w, String label, String cur, List<String> list, java.util.function.Consumer<String> set) {
		section(c, label, x, y);
		int by = y + 11, bh = 20;
		boolean hov = hovered(x, by, w, bh);
		KuboButton.neutral(c, x, by, w, bh, "", hov);
		String name = cur == null || cur.isEmpty() ? Lang.t("Default") : cur;
		Gfx.tex(c, vt("device_selected"), x + 5, by + 2, 16, 16, 16, 16, 0xFFFFFFFF);
		Gfx.textClip(c, name, x + 25, by + 6, w - 50, Theme.TEXT);
		Gfx.text(c, "<", x + w - 24, by + 6, Theme.DIM);
		Gfx.text(c, ">", x + w - 12, by + 6, Theme.DIM);
		List<String> all = new ArrayList<>();
		all.add("");
		all.addAll(list);
		int i = Math.max(0, all.indexOf(cur == null ? "" : cur));
		regionButtons(x, by, w, bh, btn -> {
			int dir = btn == 1 || mouseX < x + w - 18 && mouseX >= x + w - 28 ? -1 : 1;
			set.accept(all.get(Math.floorMod(i + dir, all.size())));
			VoiceEngine.INSTANCE.restart();
		});
		return by + bh;
	}

	// ================================================================ input
	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, scroll - (int) Math.round(v * 16));
		return true;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		boolean wasFocus = nameFocus;
		nameFocus = false;
		boolean r = super.mouseClicked(click, doubled);
		if (nameFocus && wasFocus) {
			groupName.click(sx(click.x()));
		}
		return r;
	}

	@Override
	public boolean mouseDragged(Click click, double dx, double dy) {
		if (dragMember != null) {
			dragY = sx(click.y());
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (dragMember != null) {
			VoiceGroups.Group g = selGroup == null ? null : VoiceGroups.get(selGroup);
			if (g != null && pendingTarget >= 0) {
				VoiceGroups.move(g, dragFrom, pendingTarget);
			}
			dragMember = null;
			dragFrom = -1;
		}
		super.mouseReleased(click);
		Config.save();
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (nameFocus) {
			groupName.typed(input.codepoint());
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (bindingPtt || bindingOpen) {
			int code = k == 256 ? -1 : k;
			if (bindingPtt) {
				mod().pttKey.set(code);
			} else if (code != -1) {
				mod().openKey.set(code);
			}
			bindingPtt = bindingOpen = false;
			Config.save();
			return true;
		}
		if (nameFocus) {
			if (k == 256) {
				nameFocus = false;
				return true;
			}
			if (k == 257 || k == 335) {
				createGroup();
				return true;
			}
			groupName.key(k);
			return true;
		}
		if (k == 256 || k == mod().openKey.code()) {
			if (k != 256) {
				mod().disarm();
			}
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void removed() {
		VoiceEngine.INSTANCE.testing = false;
		Config.save();
		super.removed();
	}
}
