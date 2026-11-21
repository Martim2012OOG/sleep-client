package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.account.Accounts;
import dev.sleepclient.account.MicrosoftAuth;
import dev.sleepclient.skin.SkinChanger;
import dev.sleepclient.social.RemoteTextures;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Account manager: every account as a card (head, nick, Microsoft / Offline), click to switch, X to remove;
 * "Add Microsoft Account" (code + microsoft.com/link, no password in the game) and "Add Offline Account".
 */
public class AccountsScreen extends UiScreen {

	private final Screen parent;
	private MicrosoftAuth.DeviceLogin login;
	private boolean addingOffline;
	private final TextBox nick = new TextBox(16, cp -> cp < 128 && (Character.isLetterOrDigit(cp) || cp == '_'));
	private int scroll;

	public AccountsScreen(Screen parent) {
		super("Accounts");
		this.parent = parent;
	}

	/** The face (and hat layer) of a skin texture. */
	public static void face(GuiGraphicsExtractor c, RemoteTextures.Tex t, int x, int y, int size) {
		if (t == null) {
			Gfx.rrect(c, x, y, size, size, Math.max(2, size / 6), 0xFF2C2F38);
			return;
		}
		Gfx.texUv(c, t.id(), x, y, size, size, 8, 8, 8, 8, t.width(), t.height(), 0xFFFFFFFF);
		if (t.height() == 64 || t.width() == 64) {
			Gfx.texUv(c, t.id(), x, y, size, size, 40, 8, 8, 8, t.width(), t.height(), 0xFFFFFFFF);
		}
	}

	/** Skin of an account for its head picture. */
	static RemoteTextures.Tex skinOf(Accounts.Account a) {
		if (Accounts.isCurrent(a)) {
			return SkinChanger.myPreview();
		}
		return dev.sleepclient.social.FaceSkins.of(a.name);
	}

	@Override
	protected void draw(GuiGraphicsExtractor c, float delta) {
		Gfx.gradientV(c, 0, 0, width, height, 0x60000000, 0xA0000000);
		int pw = Math.min(300, width - 20), ph = Math.min(250, height - 20);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		panel(c, px, py, pw, ph, null);
		Gfx.text(c, "Accounts", px + 12, py + 11, Theme.TEXT);
		Gfx.text(c, "Playing as " + Accounts.current(), px + pw - 12 - Gfx.tw("Playing as " + Accounts.current()), py + 11, Theme.DIM);
		c.fill(px + 10, py + 28, px + pw - 10, py + 29, 0x30FFFFFF);

		if (login != null) {
			drawLogin(c, px, py, pw, ph);
			return;
		}

		// account cards
		List<Accounts.Account> list = Accounts.all();
		int ly0 = py + 36, ly1 = py + ph - 62, rh = 34;
		int content = list.size() * (rh + 4);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (ly1 - ly0))));
		c.enableScissor(px + 6, ly0, px + pw - 6, ly1);
		int y = ly0 - scroll;
		for (Accounts.Account a : list) {
			if (y + rh >= ly0 && y <= ly1) {
				card(c, a, px + 10, y, pw - 20, rh, ly0, ly1);
			}
			y += rh + 4;
		}
		c.disableScissor();

		// add buttons / offline nick field
		int by = py + ph - 54, bw = (pw - 26) / 2;
		if (addingOffline) {
			Gfx.rrectBorder(c, px + 10, by, pw - 20 - 62, 22, 7, 0xFF0A0B0E, Theme.accent());
			if (nick.isEmpty()) {
				Gfx.text(c, "Nick (3-16 letters, numbers or _)", px + 18, by + 7, Theme.DIM);
			}
			nick.draw(c, px + 10, by, pw - 82, 22, px + 18, pw - 98, true, Theme.accent(), Theme.TEXT);
			regionButtons(px + 10, by, pw - 82, 22, b -> nick.click(mouseX));
			button(c, "addoff", px + pw - 68, by, 58, 22, "Add", this::addOffline);
		} else {
			button(c, "ms", px + 10, by, bw, 22, "Add Microsoft Account", this::startLogin);
			button(c, "off", px + 16 + bw, by, bw, 22, "Add Offline Account", () -> {
				addingOffline = true;
				nick.clear();
			});
		}
		button(c, "done", px + pw / 2 - 50, py + ph - 28, 100, 20, "Done", this::onClose);

		// status
		String m = Accounts.message;
		if (m != null) {
			Gfx.textFit(c, fit(m, pw - 20), px + pw / 2, pw - 6, py + ph + 6, Accounts.messageError ? 0xFFFF8A8A : Theme.DIM);
		}
	}

	private void card(GuiGraphicsExtractor c, Accounts.Account a, int x, int y, int w, int h, int clip0, int clip1) {
		boolean cur = Accounts.isCurrent(a);
		boolean hov = hovered(x, y, w, h) && mouseY >= clip0 && mouseY < clip1;
		float an = anim("acc" + a.type + a.name, hov);
		Gfx.rrectBorder(c, x, y, w, h, 8, cur ? Gfx.blend(TILE, Theme.accent(), 0.25F) : Gfx.blend(TILE, TILE_HOVER, an),
				cur ? Theme.accent() : Gfx.blend(BORDER, Theme.accent(), an * 0.6F));
		face(c, skinOf(a), x + 6, y + 5, 24);
		Gfx.text(c, fit(a.name, w - 150), x + 38, y + 7, 0xFFFFFFFF);
		String sub = a.type.equals(Accounts.LAUNCHER) ? "Launcher account" : a.type.equals(Accounts.MICROSOFT) ? "Saved login" : "Offline servers only";
		Gfx.text(c, sub, x + 38, y + 19, Theme.DIM);
		String label = a.label();
		int bw = 14, bx = x + w - bw - (a.type.equals(Accounts.LAUNCHER) ? 8 : 28);
		boolean ms = label.equals("Microsoft");
		Gfx.tex(c, Theme.icon2(ms ? "account_microsoft" : "account_offline"), bx, y + 10, 14, 14, 64, 64, 0xFFFFFFFF);
		if (cur) {
			Gfx.text(c, "Current", bx - Gfx.tw("Current") - 8, y + 13, Theme.accent());
		}
		region(x, y, w, h, () -> Accounts.use(a));
		if (!a.type.equals(Accounts.LAUNCHER)) {
			int dx = x + w - 22, dy = y + h / 2 - 7;
			boolean hx = hovered(dx, dy, 14, 14);
			Gfx.tex(c, Theme.icon2("close"), dx + 1, dy + 1, 12, 12, 64, 64, hx ? Theme.RED : Theme.DIM);
			if (hx) {
				tooltip = "Remove " + a.name;
			}
			region(dx, dy, 14, 14, () -> Accounts.remove(a));
		}
	}

	// ------------------------------------------------------------------ microsoft login
	private void startLogin() {
		login = MicrosoftAuth.startDeviceLogin();
	}

	private void drawLogin(GuiGraphicsExtractor c, int px, int py, int pw, int ph) {
		MicrosoftAuth.DeviceLogin d = login;
		int cx = px + pw / 2, y = py + 44;
		Gfx.textC(c, "Log in with Microsoft", cx, y, 0xFFFFFFFF);
		y += 16;
		Gfx.textC(c, "1. Open the link below in your browser", cx, y, Theme.DIM);
		Gfx.textC(c, "2. Type this code and log in there", cx, y + 11, Theme.DIM);
		y += 30;
		if (d.userCode != null) {
			String code = d.userCode;
			float s = 2F;
			int cw = Math.round(Gfx.tw(code) * s) + 30;
			Gfx.rrectBorder(c, cx - cw / 2, y, cw, 34, 9, 0xFF0A0B0E, Theme.accent());
			c.pose().pushMatrix();
			c.pose().translate(cx, y + 9);
			c.pose().scale(s, s);
			Gfx.textC(c, code, 0, 0, 0xFFFFFFFF);
			c.pose().popMatrix();
			y += 44;
			int bw = (pw - 40) / 2;
			button(c, "open", px + 15, y, bw, 22, "Open " + shortUrl(d.verifyUrl), () -> {
				mc.keyboardHandler.setClipboard(code);
				net.minecraft.util.Util.getPlatform().openUri(d.verifyUrl);
				Accounts.message = "Code copied: just paste it in the page";
				Accounts.messageError = false;
			});
			button(c, "copy", px + 25 + bw, y, bw, 22, "Copy Code", () -> {
				mc.keyboardHandler.setClipboard(code);
				Accounts.message = "Code copied";
				Accounts.messageError = false;
			});
			y += 32;
		} else {
			y += 20;
		}
		Gfx.textC(c, fit(d.status, pw - 20), cx, y, d.failed ? 0xFFFF8A8A : d.done ? 0xFF9BF0AE : Theme.TEXT);
		if (!d.done && !d.failed) {
			// little spinner
			long t = System.currentTimeMillis() / 120;
			for (int i = 0; i < 3; i++) {
				Gfx.circle(c, cx - 8 + i * 8, y + 18, 2, (t % 3) == i ? Theme.accent() : 0x55FFFFFF);
			}
		}
		if (d.done && d.result != null) {
			Accounts.addMicrosoft(d.result);
			login = null;
			return;
		}
		button(c, "cancel", cx - 50, py + ph - 30, 100, 20, d.failed ? "Back" : "Cancel", () -> {
			d.cancelled = true;
			login = null;
		});
		if (d.failed) {
			button(c, "retry", cx - 50, py + ph - 56, 100, 20, "Try Again", this::startLogin);
		}
	}

	private static String shortUrl(String u) {
		return u == null ? "link" : u.replaceFirst("^https?://(www\\.)?", "");
	}

	private void addOffline() {
		if (Accounts.addOffline(nick.text())) {
			for (Accounts.Account a : Accounts.all()) {
				if (a.type.equals(Accounts.OFFLINE) && a.name.equalsIgnoreCase(nick.text())) {
					Accounts.use(a);
				}
			}
			addingOffline = false;
		} else {
			Accounts.message = "Nick must be 3-16 letters, numbers or _";
			Accounts.messageError = true;
		}
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, scroll - (int) Math.round(v * 18));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (addingOffline) {
			if (k == 257 || k == 335) {
				addOffline();
			} else if (k == 256) {
				addingOffline = false;
			} else {
				nick.key(k);
			}
			return true;
		}
		if (k == 256) {
			if (login != null) {
				login.cancelled = true;
				login = null;
			} else {
				onClose();
			}
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (addingOffline) {
			nick.typed(input.codepoint());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
		nick.drag(sx(click.x()));
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
		nick.release();
		return super.mouseReleased(click);
	}

	@Override
	public void onClose() {
		if (login != null) {
			login.cancelled = true;
		}
		dev.sleepclient.compat.Mc.setScreen(mc, parent);
	}
}
