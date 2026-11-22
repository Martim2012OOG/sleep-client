package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.social.Party;
import dev.sleepclient.account.Accounts;
import dev.sleepclient.skin.SkinChanger;
import dev.sleepclient.social.RemoteTextures;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;

/**
 * Plegumo home screen: logo at the top centre, Singleplayer, Multiplayer and three icon buttons
 * (settings, Plegumo menu, shop); on the right your skin in 3D (looking a little to the left) and the
 * account tiles (switch with one click, + adds a Microsoft or offline account).
 */
public class PlegumoTitleScreen extends UiScreen {

	public PlegumoTitleScreen() {
		super("Sleep");
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private long openAt;

	/** 0..1 ease-out (cubic) of the entrance, delayed by {@code delay} seconds. */
	private float intro(float delay, float len) {
		float t = (System.currentTimeMillis() - openAt) / 1000F - delay;
		float k = Math.max(0F, Math.min(1F, t / len));
		float r = 1F - k;
		return 1F - r * r * r;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
		if (click.button() == 0 && Season.snowGrab(sx(click.x()), sx(click.y()))) {
			return true; // picked up a snowflake
		}
		if (click.button() == 0) {
			double cmx = sx(click.x()), cmy = sx(click.y());
			pressInSkin = cmx >= skinX && cmx < skinX + skinW && cmy >= skinY && cmy < skinY + skinH;
			pressX = cmx;
			pressY = cmy;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
		if (Season.snowHeld()) {
			Season.snowDrag(sx(click.x()), sx(click.y()));
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.gui.Click click) {
		if (Season.snowHeld()) {
			Season.snowRelease();
			return true;
		}
		if (click.button() == 0 && pressInSkin) {
			pressInSkin = false;
			if (Math.hypot(sx(click.x()) - pressX, sx(click.y()) - pressY) < 5) {
				toggleSkinEmote(); // a click, not a drag
			}
		}
		return super.mouseReleased(click);
	}

	private int skinX, skinY, skinW, skinH;
	private boolean pressInSkin;
	private double pressX, pressY;
	private int lastEmote = -1;

	/** Click on your skin: a random emote you own plays; click again and it stops. */
	private void toggleSkinEmote() {
		if (dev.sleepclient.cosmetic.SkinPreview.menuEmotePlaying()) {
			dev.sleepclient.cosmetic.SkinPreview.menuEmote(null);
			return;
		}
		java.util.List<dev.sleepclient.emote.EmoteLibrary.Info> l = new java.util.ArrayList<>(dev.sleepclient.emote.EmoteLibrary.mine());
		if (l.isEmpty()) {
			return;
		}
		java.util.Collections.shuffle(l);
		for (dev.sleepclient.emote.EmoteLibrary.Info e : l) {
			int h = e.id().hashCode();
			if ((h != lastEmote || l.size() == 1) && dev.sleepclient.emote.EmoteLibrary.animation(e) != null) {
				lastEmote = h;
				dev.sleepclient.cosmetic.SkinPreview.menuEmote(e);
				return;
			}
		}
	}

	@Override
	public void removed() {
		dev.sleepclient.cosmetic.SkinPreview.menuEmote(null);
		super.removed();
	}

	private boolean lobbyRedirected;

	@Override
	protected void draw(DrawContext c, float delta) {
		if (!lobbyRedirected && Party.active()) {
			// in a party the main menu is the 3D lobby, until you leave the party
			lobbyRedirected = true;
			mc.execute(() -> mc.setScreen(new PartyLobbyScreen(this)));
		}
		if (openAt == 0) {
			openAt = System.currentTimeMillis();
		}
		int cx = width / 2;
		int slide = 0; // no intro motion
		if (Season.active()) {
			// Halloween / Christmas: the seasonal wallpaper with moving decorations wins over the video / custom sky
			Season.background(c, width, height);
			Season.overlay(c, width, height);
		} else if (!dev.sleepclient.mods.CinematicIntroMod.drawHome(c, width, height) && !dev.sleepclient.mods.CustomSkyMod.menuBackdrop(c, width, height)) {
			Season.background(c, width, height);
		}

		// quit (top-right)
		int qx = width - 26;
		boolean hq = hovered(qx, 6, 20, 20);
		Gfx.button(c, qx, 6, 20, 20, hq, false);
		Gfx.tex(c, Theme.icon2("close"), qx + 4, 9, 12, 12, 64, 64, hq ? 0xFFFF6B6B : 0xFFE6E8EE);
		if (hq) {
			tooltip = "Quit game";
		}
		region(qx, 6, 20, 20, () -> mc.scheduleStop());

		// logo, top centre
		int logoSize = Math.max(56, Math.min(110, height / 4));
		int bob = Math.round((float) Math.sin(System.currentTimeMillis() / 1000.0 * 1.6) * 2.2F); // the logo floats gently
		int logoY = Math.max(10, height / 12) + slide + bob - Math.round((1F - intro(0F, 0.55F)) * 16F);
		if (!dev.sleepclient.mods.CinematicIntroMod.homeActive()) { // the intro video has its own logo
			Gfx.tex(c, Gfx.id("icon.png"), cx - logoSize / 2, logoY, logoSize, logoSize, 256, 256, 0xFFFFFFFF);
			Season.decorateLogo(c, cx - logoSize / 2, logoY, logoSize);
		}

		// buttons
		int bw = Math.min(200, width / 3), bh = 20;
		int by = Math.max(logoY + logoSize + 16, height / 2 - 20);
		int o1 = Math.round((1F - intro(0.06F, 0.5F)) * 18F), o2 = Math.round((1F - intro(0.12F, 0.5F)) * 18F), o3 = Math.round((1F - intro(0.18F, 0.5F)) * 18F);
		button(c, "sp", cx - bw / 2, by + o1, bw, bh, "Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this)));
		button(c, "mp", cx - bw / 2, by + bh + 6 + o2, bw, bh, "Multiplayer", () -> mc.setScreen(new MultiplayerScreen(this)));
		// Party: create one (or open yours) and play with friends
				Party.Invite firstInv = Party.INVITES.isEmpty() ? null : Party.INVITES.get(0);
		String partyLabel = Party.active() ? Lang.tr("Party", "Party") + "  " + Party.size() + "/" + Party.MAX
				: firstInv != null ? Lang.tr("Join ", "Entrar em ") + fit(firstInv.leaderNick + (Lang.pt() ? "" : "'s party"), bw - 60) : Lang.tr("Create Party", "Criar Party");
		// tucked away in the top-left corner, small, not in front of the main buttons
		int px = 6, py = 6;
		iconButton(c, "party", px, py, 20, "party_hud", partyLabel, () -> {
			if (!Party.active() && firstInv != null) {
				Party.accept(firstInv);
			}
			if (Party.active() || Party.create()) {
				Party.openLobby(mc);
			}
		});
		if (Party.pendingInvites() > 0) {
			// red badge with the number of invites waiting
			Gfx.circle(c, px + 18, py + 2, 8, 0xFFE5484D);
			Gfx.textC(c, String.valueOf(Party.pendingInvites()), px + 18, py - 2, 0xFFFFFFFF);
		}
		int is = 20, ig = 6, iy = by + 2 * bh + 20 + o3;
		boolean mm = ModMenuLink.preferred();
		int n = mm ? 5 : 4;
		int ix = cx - (n * is + (n - 1) * ig) / 2;
		iconButton(c, "set", ix, iy, is, "settings", "Settings", () -> mc.setScreen(new OptionsScreen(this, mc.options)));
		iconButton(c, "mods", ix + is + ig, iy, is, "sleep_client", "Sleep menu", () -> mc.setScreen(SolMenuScreen.open(SolMenuScreen.VIEW_MODS)));
		iconButton(c, "shop", ix + 2 * (is + ig), iy, is, "shop", "Shop", () -> mc.setScreen(SolMenuScreen.open(SolMenuScreen.VIEW_SHOP)));
		iconButton(c, "pconn", ix + 3 * (is + ig), iy, is, "private_connection", "Private Connection", () -> mc.setScreen(new PrivateConnectionScreen(this)));
		if (mm) {
			iconButton(c, "modmenu", ix + 4 * (is + ig), iy, is, "mods", "Mod Menu", () -> ModMenuLink.open(this));
		}

		// your skin in 3D + account selector (right)
		drawAccount(c, slide);

		// bottom-right: music volume + shop banner (like Lunar)
		drawCorner(c);
		// bottom-left: social buttons (Discord on top of YouTube / TikTok; links coming soon)
		drawSocials(c);

		// Christmas snow on top of everything
		Season.snow(c, width, height, mouseX, mouseY);

		// soft fade in from black when the screen opens
		int fade = Math.round((1F - intro(0F, 0.4F)) * 255F);
		if (fade > 2) {
			c.fill(0, 0, width, height, fade << 24);
		}
	}

	// ------------------------------------------------------------------ skin + accounts
	private String lastMsg;
	private long msgAt;

	// ---- the 3D skin: drag to spin it (it keeps going with the force you gave it and slows down smoothly), a second after it stops it
	// glides back to how it stood
	private static final float SKIN_YAW = -25F;
	private float skinYaw, skinTarget, skinVel;
	private double skinDragX = Double.NaN;
	private long skinFrameAt, skinLastDragAt, skinIdleAt;

	private void stepSkinSpin(boolean held) {
		long now = System.currentTimeMillis();
		float dt = skinFrameAt == 0 ? 0F : Math.min(0.05F, (now - skinFrameAt) / 1000F);
		skinFrameAt = now;
		boolean dragging = held || now - skinLastDragAt < 90L;
		if (dragging) {
			skinIdleAt = now;
			if (held && now - skinLastDragAt > 60L) {
				skinVel *= (float) Math.exp(-14.0 * dt); // holding it still: no fling when you let go
			}
		} else {
			skinDragX = Double.NaN;
			if (Math.abs(skinVel) > 4F) {
				skinTarget += skinVel * dt; // keeps turning with the force you gave it...
				skinVel *= (float) Math.exp(-2.1 * dt); // ...and slows down smoothly, never a sudden stop
				skinIdleAt = now;
			} else {
				skinVel = 0F;
				if (now - skinIdleAt > 1100L) {
					float home = 360F * Math.round(skinTarget / 360F); // back to how it stood, the short way round
					skinTarget += (home - skinTarget) * (1F - (float) Math.exp(-3.2 * dt));
				}
			}
		}
		skinVel = Math.max(-1400F, Math.min(1400F, skinVel));
		skinYaw += (skinTarget - skinYaw) * (1F - (float) Math.exp(-20.0 * dt)); // the model follows its target smoothly
	}

	private void drawAccount(DrawContext c, int slide) {
		int pw = Math.min(150, width / 4);
		float u = Math.max(3F, Math.min(5.5F, (height - 110F) / 48F)); // one skin pixel in GUI units: sized so the WHOLE body fits
		int bodyH = Math.round(32 * u);
		// fixed on the right side of the screen, as it always was
		int cx = width - pw / 2 - 16;
		int top = Math.max(36, (height - (bodyH + 86)) / 2) + slide;

		int rx = cx - Math.round(14 * u), ry = top - 6, rw = Math.round(28 * u), rh = bodyH + 12;
		skinX = rx;
		skinY = ry;
		skinW = rw;
		skinH = rh;
		boolean dragging = isDragging(rx, ry);
		stepSkinSpin(dragging);
		RemoteTextures.Tex t = SkinChanger.myPreview();
		if (t != null) {
			// the whole 3D model (hat layer, idle arms), not cropped
			boolean ok = dev.sleepclient.cosmetic.SkinPreview.draw(c, t, dev.sleepclient.skin.SkinChanger.selfSlim(), cx * ui, (top + bodyH) * ui, u * ui,
					SKIN_YAW + skinYaw, 6F, null);
			if (!ok) {
				SkinModel3D.draw(c, t, cx, top, u, 20, 30, 12);
			}
		}
		regionDrag(rx, ry, rw, rh, mx2 -> {
			long now = System.currentTimeMillis();
			if (!Double.isNaN(skinDragX)) {
				float d = (float) (mx2 - skinDragX) * 1.1F;
				skinTarget += d;
				float dts = Math.max(0.008F, (now - skinLastDragAt) / 1000F);
				skinVel = skinVel * 0.5F + (d / dts) * 0.5F;
			}
			skinLastDragAt = now;
			skinDragX = mx2;
		});

		// nick + account type, click = account manager
		int ay = top + bodyH + 14;
		String name = Accounts.current();
		String type = Accounts.currentType();
		boolean ms = type.equals("Microsoft");
		int ls = 11; // account logo (Microsoft / offline)
		int nw = Gfx.tw(name) + 6 + ls;
		boolean hn = hovered(cx - nw / 2 - 4, ay - 2, nw + 8, 14);
		Gfx.text(c, name, cx - nw / 2, ay, hn ? Theme.accent() : 0xFFFFFFFF);
		int bx = cx - nw / 2 + Gfx.tw(name) + 6;
		Gfx.tex(c, Theme.icon2(ms ? "account_microsoft" : "account_offline"), bx, ay - 2, ls, ls, 64, 64, 0xFFFFFFFF);
		if (hovered(bx, ay - 2, ls, ls)) {
			tooltip = ms ? "Microsoft account" : type + " account";
		}
		if (hn && tooltip == null) {
			tooltip = "Manage accounts";
		}
		region(cx - nw / 2 - 4, ay - 2, nw + 8, 14, () -> mc.setScreen(new AccountsScreen(this)));

		// account tiles (like Essential): [+] then every account, the one in use highlighted
		List<Accounts.Account> list = Accounts.all();
		int ts = 24, gap = 4;
		int max = Math.max(2, (pw + 40 + gap) / (ts + gap));
		boolean more = list.size() + 1 > max;
		int shown = more ? max - 2 : list.size();
		int n = 1 + shown + (more ? 1 : 0);
		int rowW = n * ts + (n - 1) * gap;
		int tx = cx - rowW / 2, ty = ay + 18;
		tile(c, "acc+", tx, ty, ts, null, "Add Account", () -> mc.setScreen(new AccountsScreen(this)));
		tx += ts + gap;
		for (int i = 0; i < shown; i++) {
			Accounts.Account a = list.get(i);
			tile(c, "acc" + i, tx, ty, ts, a, a.name + " (" + a.label() + ")", () -> Accounts.use(a));
			tx += ts + gap;
		}
		if (more) {
			tile(c, "accmore", tx, ty, ts, null, "All accounts", () -> mc.setScreen(new AccountsScreen(this)));
			Gfx.textFit(c, "...", tx + ts / 2, ts - 6, ty + 8, Theme.TEXT);
		}

		// switching status (a few seconds)
		String m = Accounts.message;
		if (m != null && !m.equals(lastMsg)) {
			lastMsg = m;
			msgAt = System.currentTimeMillis();
		}
		if (m != null && (Accounts.busy || System.currentTimeMillis() - msgAt < 4000)) {
			Gfx.textC(c, fit(m, pw + 40), cx, ty + ts + 8, Accounts.messageError ? 0xFFFF8A8A : Theme.DIM);
		}
	}

	/** One square account tile: a head, or "+" when acc is null. */
	private void tile(DrawContext c, String id, int x, int y, int s, Accounts.Account acc, String tip, Runnable run) {
		boolean hov = hovered(x, y, s, s);
		float a = anim(id, hov);
		boolean cur = acc != null && Accounts.isCurrent(acc);
		int accent = Theme.accent();
		int bg = acc == null && !"accmore".equals(id) ? Gfx.blend(Gfx.withAlpha(accent, 0xC0), accent, a) : Gfx.blend(0xE0202228, 0xF02C2F38, a);
		Gfx.rrectBorder(c, x, y, s, s, 5, bg, cur ? 0xFFFFFFFF : hov ? Gfx.withAlpha(0xFFFFFF, 0x90) : 0x30000000);
		if (acc == null && !"accmore".equals(id)) {
			c.fill(x + s / 2 - 4, y + s / 2 - 1, x + s / 2 + 4, y + s / 2 + 1, 0xFFFFFFFF);
			c.fill(x + s / 2 - 1, y + s / 2 - 4, x + s / 2 + 1, y + s / 2 + 4, 0xFFFFFFFF);
		} else if (acc != null) {
			AccountsScreen.face(c, AccountsScreen.skinOf(acc), x + 4, y + 4, s - 8);
		}
		if (hov) {
			tooltip = tip;
		}
		regionButtons(x, y, s, s, b -> {
			if (b == 1) {
				mc.setScreen(new AccountsScreen(this));
			} else {
				run.run();
			}
		});
	}

	// ------------------------------------------------------------------ bottom-right corner
	private void drawCorner(DrawContext c) {
		int season = Season.current();
		boolean banner = false; // the shop preview in the corner is gone
		int bw = 124, bh = 58, m = 10;
		int bx = width - bw - m, by = height - bh - m;
		if (banner) {
			drawBanner(c, season, bx, by, bw, bh);
		}
		// volume: speaker icon (click = mute) + slider
		int vy = banner ? by - 18 : height - 22;
		int sw = 70, sx = width - m - sw - 4, ix = sx - 20;
		float vol = SeasonMusic.volume();
		boolean hi = hovered(ix, vy, 16, 14);
		Gfx.tex(c, Theme.icon2(vol > 0.001F ? "volume" : "volume_off"), ix + 1, vy, 14, 14, 64, 64, hi ? 0xFFFFFFFF : 0xCCE6E8EE);
		if (hi) {
			tooltip = "Music: " + SeasonMusic.trackName() + " (click to " + (vol > 0.001F ? "mute" : "unmute") + ")";
		}
		region(ix, vy, 16, 14, SeasonMusic::toggleMute);
		int ty = vy + 5;
		boolean hs = hovered(sx - 4, vy - 2, sw + 8, 18);
		SmoothSlider.draw(c, "music", sx, ty + 2F, sw, vol, isDragging(sx - 4, vy - 2), hs, Math.round(vol * 100) + "%");
		regionDrag(sx - 4, vy - 2, sw + 8, 18, mx -> {
			float v = (float) Math.max(0, Math.min(1, (mx - sx) / sw));
			SmoothSlider.dragTo("music", v);
			SeasonMusic.setVolume(v);
			volumeDirty = true;
		});
		if (volumeDirty && !hs) {
			volumeDirty = false;
			SeasonMusic.save();
		}
	}

	private boolean volumeDirty;

	private void drawBanner(DrawContext c, int season, int x, int y, int w, int h) {
		boolean hov = hovered(x, y, w, h);
		int top, bottom, edge;
		String title, key;
		if (season == Season.CHRISTMAS) {
			top = 0xFFC8343C; bottom = 0xFF7E1A22; edge = 0xFFE86A70; title = "CHRISTMAS SHOP"; key = "christmas";
		} else if (season == Season.HALLOWEEN) {
			top = 0xFFE07A22; bottom = 0xFF7A3A10; edge = 0xFFF2A55C; title = "HALLOWEEN SHOP"; key = "halloween";
		} else {
			top = 0xFF3A4462; bottom = 0xFF1E2333; edge = 0xFF5A6690; title = "NEW IN THE SHOP"; key = null;
		}
		if (hov) {
			top = Gfx.blend(top, 0xFFFFFFFF, 0.08F);
			bottom = Gfx.blend(bottom, 0xFFFFFFFF, 0.08F);
		}
		// clean rounded card: soft outline, flat body, lighter top half
		Gfx.rrect(c, x - 1, y - 1, w + 2, h + 2, 6, Gfx.withAlpha(edge, 0x90));
		Gfx.rrect(c, x, y, w, h, 5, bottom);
		Gfx.rrect(c, x, y, w, h / 2 + 4, 5, top);
		Gfx.rrect(c, x, y + h / 2 - 4, w, 10, 0, Gfx.blend(top, bottom, 0.5F));
		Gfx.text(c, title, x + 6, y + 5, 0xFFFFFFFF);
		List<dev.sleepclient.social.Social.Item> items = dev.sleepclient.social.Social.showcase(key, 3);
		int is = 34, gap = 4, iy = y + 17;
		int ix = x + (w - (3 * is + 2 * gap)) / 2;
		if (items.isEmpty()) {
			Gfx.textFit(c, "Open the Locker", x + w / 2, w - 6, iy + 13, 0xCCFFFFFF);
		}
		for (int i = 0; i < items.size(); i++) {
			int xx = ix + i * (is + gap);
			Gfx.rrect(c, xx, iy, is, is, 4, 0x2A000000);
			RemoteTextures.Tex t = RemoteTextures.get(items.get(i).imageUrl);
			if (t != null) {
				Gfx.tex(c, t.id(), xx + 2, iy + 2, is - 4, is - 4, t.width(), t.height(), 0xFFFFFFFF);
			}
		}
		if (hov) {
			tooltip = "Open the shop";
		}
		region(x, y, w, h, () -> mc.setScreen(SolMenuScreen.open(SolMenuScreen.VIEW_SHOP)));
	}

	// ------------------------------------------------------------------ bottom-left socials
	private static final String[][] SOCIALS = {{"discord", "Discord"}, {"youtube", "YouTube"}, {"tiktok", "TikTok"}};

	private void drawSocials(DrawContext c) {
		int s = 24, gap = 5, m = 10;
		int x = m, y = height - m - SOCIALS.length * (s + gap) + gap;
		for (String[] so : SOCIALS) {
			boolean h = hovered(x, y, s, s);
			Gfx.button(c, x, y, s, s, h, false);
			int is = 16;
			Gfx.tex(c, Gfx.id("textures/gui/social/" + so[0] + ".png"), x + (s - is) / 2, y + (s - is) / 2 - 1, is, is, 128, 128, 0xFFFFFFFF);
			if (h) {
				tooltip = so[1] + " - coming soon";
			}
			region(x, y, s, s, () -> {
			}); // not linked yet
			y += s + gap;
		}
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean keyPressed(KeyInput input) {
		return super.keyPressed(input);
	}
}
