package dev.solclient.video;

import dev.solclient.Keys;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Interaction with the floating panels (the mixins call the on... methods, Panels calls update() every frame):
 *  - MIDDLE button on a panel + turn your head: move it (scroll = closer / further). LEFT drag on the title bar also moves it;
 *  - RIGHT button on a bottom corner + drag: scale it (keeps the shape, Shift = free);
 *  - LEFT on the page: click + typing mode (Esc or a click elsewhere leaves it); scroll on the page scrolls the page;
 *  - scroll on the title bar / frame: bigger / smaller;
 *  - title bar: back, forward, reload, address (click, type, Enter), X closes (Shift + X closes all);
 *  - side mouse buttons: back / forward.
 */
public final class PanelInput {

	private static final double MAX_DIST = 64.0, MARGIN = 0.04;

	/** The panel being aimed at (or being moved / scaled). */
	public static PanelBox hover;
	/** What is typed in the address bar while it is being edited. */
	public static String urlText = "";

	private static PanelBox moving, resizing, focused, urlPanel, dragPanel;
	private static int movingButton = -1;
	private static double grabDist, grabLx, grabLy;
	private static int rsx, rsy;
	private static double rOffX, rOffY, rW0, rH0;
	private static boolean rKeep;
	private static int dragAwt, dragX, dragY;
	private static PanelBox sentPanel;
	private static int sentX = -1, sentY = -1;
	private static long clickAt;
	private static PanelBox clickPanel;
	private static int clickX, clickY;
	private static final Set<Integer> cancelled = new HashSet<>();

	private PanelInput() {
	}

	private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Called by the mixins: prints one line the first time each hook runs. If a line never shows up, that hook is not installed. */
	public static void alive(String what) {
		if (SEEN.add(what)) {
			System.out.println("[Sleep] input hook active: " + what);
		}
	}

	/** True while the keyboard belongs to a panel (page typing or the address bar). */
	public static boolean typing() {
		return focused != null || urlPanel != null;
	}

	public static boolean typingIn(PanelBox p) {
		return focused == p;
	}

	public static boolean urlEditing(PanelBox p) {
		return urlPanel == p;
	}

	public static PanelBox resizing() {
		return resizing;
	}

	public static void reset() {
		moving = null;
		resizing = null;
		hover = null;
		dragPanel = null;
		dragAwt = 0;
		movingButton = -1;
		sentPanel = null;
		clickPanel = null;
		cancelled.clear();
		stopTyping();
	}

	private static boolean raw(int code) {
		return Keys.rawDown(code);
	}

	private static boolean shift() {
		return raw(340) || raw(344);
	}

	private static boolean ctrl() {
		return raw(341) || raw(345);
	}

	/** Ctrl, or Cmd on a Mac: the key for copy / paste / select all. */
	private static boolean shortcut() {
		return ctrl() || raw(343) || raw(347);
	}

	private static int mods() {
		return BrowserKeys.mods(shift(), ctrl(), raw(342) || raw(346));
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// ------------------------------------------------------------------ every frame

	public static void update(Minecraft mc, Vec3 eye, Vec3 look) {
		for (PanelBox p : Panels.LIST) {
			p.hovered = false;
			p.cornerX = 0;
			p.cornerY = 0;
		}
		if (dev.solclient.compat.Mc.screen(mc) != null || mc.player == null) {
			moving = null;
			resizing = null;
			hover = null;
			dragPanel = null;
			dragAwt = 0;
			movingButton = -1;
			cancelled.clear();
			stopTyping();
			return;
		}
		if (focused != null && !Panels.LIST.contains(focused) || urlPanel != null && !Panels.LIST.contains(urlPanel)) {
			stopTyping();
		}
		PanelBox pf = pendingFocus;
		pendingFocus = null;
		if (pf != null && pf == focused && pf.browser != null) {
			pf.browser.focus();
		}
		if (moving != null && !Panels.LIST.contains(moving)) {
			moving = null;
		}
		if (resizing != null && !Panels.LIST.contains(resizing)) {
			resizing = null;
		}
		if (dragPanel != null && !Panels.LIST.contains(dragPanel)) {
			dragPanel = null;
			dragAwt = 0;
		}

		PanelBox best = null;
		double bt = MAX_DIST;
		for (PanelBox p : Panels.LIST) {
			double t = raycast(p, eye, look);
			if (t >= 0 && t < bt) {
				bt = t;
				best = p;
			}
		}
		for (PanelBox p : Panels.LIST) {
			if (p != best) {
				p.cornerX = 0;
				p.cornerY = 0;
			}
		}
		if (best != null) {
			raycast(best, eye, look); // leaves the hit of the nearest panel in its fields
		}
		hover = moving != null ? moving : resizing != null ? resizing : best;

		if (moving != null) {
			Vec3 target = eye.add(look.scale(grabDist));
			Vec3 flat = new Vec3(target.x - eye.x, 0, target.z - eye.z);
			if (flat.lengthSqr() > 1.0E-4) {
				moving.facing = flat.normalize();
			}
			moving.center = target.subtract(moving.right().scale(grabLx)).subtract(moving.up().scale(grabLy));
			moving.hovered = true;
		} else if (resizing != null) {
			applyResize(resizing, eye, look);
			resizing.hovered = true;
			resizing.cornerX = rsx;
			resizing.cornerY = rsy;
		} else {
			if (best != null) {
				best.hovered = true;
			}
			if (dragAwt != 0 && dragPanel != null) {
				// a button is held on a page: keep dragging, also when the aim leaves the panel (clamped to the page)
				double[] h = planeLocal(dragPanel, eye, look);
				if (h != null && dragPanel.mirrorSrc != null) {
					double[] uv = appUV(dragPanel, h[0], h[1]);
					long nowMs = System.currentTimeMillis();
					if (uv != null && nowMs - appMoveAt > 30) {
						appMoveAt = nowMs;
						AppControl.mouse(dragPanel.mirrorSrc, "drag", dragAwt == 3 ? 1 : dragAwt == 2 ? 2 : 0, uv[0], uv[1], 0);
					}
				} else if (h != null && dragPanel.browser != null) {
					int px = dragPanel.pixelX(h[0]), py = dragPanel.pixelY(h[1]);
					if (px != dragX || py != dragY) {
						dragX = px;
						dragY = py;
						dragPanel.browser.mouse(506, px, py, dragAwt, 0);
					}
				}
			} else if (best != null && best.mirrorSrc != null && best.mirror != null && best.inContent(best.hitX, best.hitY)) {
				// hover on an app: mouse moves, a few times a second (menus, tooltips)
				long nowMs = System.currentTimeMillis();
				if (nowMs - appMoveAt > 80) {
					double[] uv = appUV(best, best.hitX, best.hitY);
					if (uv != null && (Math.abs(uv[0] - appLastU) > 0.002 || Math.abs(uv[1] - appLastV) > 0.002)) {
						appMoveAt = nowMs;
						appLastU = uv[0];
						appLastV = uv[1];
						AppControl.mouse(best.mirrorSrc, "move", 0, uv[0], uv[1], 0);
					}
				}
			} else if (best != null && best.browser != null && best.inContent(best.hitX, best.hitY)) {
				int px = best.pixelX(best.hitX), py = best.pixelY(best.hitY);
				if (sentPanel != best || px != sentX || py != sentY) {
					sentPanel = best;
					sentX = px;
					sentY = py;
					best.browser.mouse(503, px, py, 0, 0);
				}
			}
		}
	}

	private static long appMoveAt;
	private static double appLastU = -1, appLastV = -1;

	/** Where a point of an app panel is inside the app's window (0..1), or null (outside the picture). */
	private static double[] appUV(PanelBox p, double x, double y) {
		double u = Math.max(0, Math.min(1, (x + p.width / 2) / p.width));
		double v = Math.max(0, Math.min(1, 1.0 - (y + p.height / 2) / p.contentH()));
		if (p.mirror instanceof MacWindowView mw) {
			return mw.toWindow(u, v);
		}
		return new double[] {u, v};
	}

	private static double[] planeLocal(PanelBox p, Vec3 eye, Vec3 look) {
		Vec3 n = p.normal();
		double denom = look.dot(n);
		if (Math.abs(denom) < 1.0E-6) {
			return null;
		}
		double t = p.center.subtract(eye).dot(n) / denom;
		if (t <= 0 || t > MAX_DIST) {
			return null;
		}
		Vec3 d = eye.add(look.scale(t)).subtract(p.center);
		return new double[] {d.dot(p.right()), d.dot(p.up()), t};
	}

	/** Distance to the panel along the aim, or -1; fills the panel's hit fields. */
	private static double raycast(PanelBox p, Vec3 eye, Vec3 look) {
		double[] h = planeLocal(p, eye, look);
		if (h == null) {
			return -1;
		}
		double x = h[0], y = h[1], hw = p.width / 2, hh = p.height / 2;
		if (Math.abs(x) > hw + MARGIN || Math.abs(y) > hh + MARGIN) {
			return -1;
		}
		p.hitX = x;
		p.hitY = y;
		p.hitDist = h[2];
		double cz = p.cornerSize();
		if (Math.abs(x) >= hw - cz && y <= -hh + cz) {
			p.cornerX = x >= 0 ? 1 : -1;
			p.cornerY = -1;
		} else {
			p.cornerX = 0;
			p.cornerY = 0;
		}
		return h[2];
	}

	private static void applyResize(PanelBox p, Vec3 eye, Vec3 look) {
		double[] h = planeLocal(p, eye, look);
		if (h == null) {
			return;
		}
		double cx = h[0] - rOffX, cy = h[1] - rOffY;
		double hw = p.width / 2, hh = p.height / 2;
		Vec3 opposite = p.point(-rsx * hw, -rsy * hh, 0);
		double w = rsx * cx + hw, hgt = rsy * cy + hh;
		if (rKeep) {
			double s = Math.max(w / rW0, hgt / rH0);
			double lo = Math.max(PanelBox.MIN_W / rW0, PanelBox.MIN_H / rH0), hi = Math.min(PanelBox.MAX_W / rW0, PanelBox.MAX_H / rH0);
			s = clamp(s, lo, hi);
			w = rW0 * s;
			hgt = rH0 * s;
		} else {
			w = clamp(w, PanelBox.MIN_W, PanelBox.MAX_W);
			hgt = clamp(hgt, PanelBox.MIN_H, PanelBox.MAX_H);
		}
		p.width = w;
		p.height = hgt;
		p.center = opposite.add(p.right().scale(rsx * w / 2)).add(p.up().scale(rsy * hgt / 2));
	}

	private static void scaleAround(PanelBox p, double f) {
		double lo = Math.max(PanelBox.MIN_W / p.width, PanelBox.MIN_H / p.height);
		double hi = Math.min(PanelBox.MAX_W / p.width, PanelBox.MAX_H / p.height);
		f = clamp(f, lo, hi);
		p.width *= f;
		p.height *= f;
	}

	// ------------------------------------------------------------------ mouse (called by the mixin)

	private static int awt(int glfwButton) {
		return glfwButton == 0 ? 1 : glfwButton == 1 ? 3 : glfwButton == 2 ? 2 : 0; // GLFW 0 left, 1 right, 2 middle -> AWT 1, 3, 2
	}

	/** True = the event is used up (the game does not see it). */
	public static boolean onMouseButton(int button, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (!Panels.active() || dev.solclient.compat.Mc.screen(mc) != null || mc.player == null) {
			return false;
		}
		if (action != GLFW.GLFW_PRESS) {
			boolean used = cancelled.remove(button);
			int a = awt(button);
			if (dragAwt != 0 && a == dragAwt && dragPanel != null) {
				if (dragPanel.mirrorSrc != null) {
					double[] uv = appUV(dragPanel, dragPanel.hitX, dragPanel.hitY);
					if (uv != null) {
						AppControl.mouse(dragPanel.mirrorSrc, "up", a == 3 ? 1 : a == 2 ? 2 : 0, uv[0], uv[1], 1);
					}
				} else if (dragPanel.browser != null) {
					dragPanel.browser.mouse(502, dragX, dragY, dragAwt, 1);
				}
				dragAwt = 0;
				dragPanel = null;
			}
			if (button == movingButton) {
				moving = null;
				movingButton = -1;
			}
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				resizing = null;
			}
			return used;
		}
		PanelBox p = hover;
		if (p == null) {
			if (button <= 2) {
				stopTyping(); // a click away from the panels leaves typing mode
			}
			return false;
		}
		cancelled.add(button); // while you aim at a panel the game does not see your clicks
		double x = p.hitX, y = p.hitY;
		switch (button) {
			case GLFW.GLFW_MOUSE_BUTTON_LEFT -> leftPress(p, x, y);
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
				if (p.cornerX != 0 && p.cornerY == -1) {
					resizing = p;
					rsx = p.cornerX;
					rsy = p.cornerY;
					rOffX = x - rsx * p.width / 2;
					rOffY = y - rsy * p.height / 2;
					rW0 = p.width;
					rH0 = p.height;
					rKeep = !shift();
				} else if (p.inContent(x, y)) {
					pageClick(p, 3);
				}
			}
			case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> {
				if (resizing == null) {
					startMove(p, button);
				}
			}
			case 3 -> {
				if (p.browser != null) {
					p.browser.back();
				}
			}
			case 4 -> {
				if (p.browser != null) {
					p.browser.forward();
				}
			}
			default -> {
			}
		}
		return true;
	}

	private static void startMove(PanelBox p, int button) {
		moving = p;
		movingButton = button;
		grabDist = p.hitDist;
		grabLx = p.hitX;
		grabLy = p.hitY;
	}

	private static void leftPress(PanelBox p, double x, double y) {
		int btn = p.buttonAt(x, y);
		if (btn == PanelBox.BTN_CLOSE) {
			if (shift()) {
				Panels.clear();
				hover = null;
			} else {
				closePanel(p);
			}
			return;
		}
		if (btn == PanelBox.BTN_COPY && p.browser != null && AppMirror.MAC) {
			// share button: open this page in the computer's real browser and show THAT on the panel (Open Apps).
			// The built-in browser can't play Instagram / TikTok videos (H.264 / AAC are not in it), the real one can.
			stopTyping();
			Panels.openInRealBrowser(p);
			return;
		}
		if (btn == PanelBox.BTN_COPY) {
			stopTyping();
			String u = p.shownUrl == null ? "" : p.shownUrl;
			Minecraft mc = Minecraft.getInstance();
			mc.keyboardHandler.setClipboard(u);
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.literal("Address copied"));
			}
			return;
		}
		if (btn == PanelBox.BTN_NEW) {
			stopTyping();
			Panels.create();
			return;
		}
		if (btn == PanelBox.BTN_BACK || btn == PanelBox.BTN_FWD || btn == PanelBox.BTN_RELOAD) {
			stopTyping();
			if (p.browser != null) {
				if (btn == PanelBox.BTN_BACK) {
					p.browser.back();
				} else if (btn == PanelBox.BTN_FWD) {
					p.browser.forward();
				} else {
					p.browser.reload();
				}
			}
			return;
		}
		if (p.inUrl(x, y)) {
			startUrl(p);
			return;
		}
		if (p.inContent(x, y) && p.mirrorSrc != null) {
			if (p.mirror != null && p.mirrorSrc.controllable()) {
				startTyping(p);
				pageClick(p, 1);
			}
			return;
		}
		if (p.inContent(x, y)) {
			if (p.browser == null) {
				if (Mcef.failed()) {
					Mcef.retry();
				}
				return;
			}
			startTyping(p);
			pageClick(p, 1);
			return;
		}
		if (p.inTitle(x, y)) {
			stopTyping();
			startMove(p, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		}
	}

	private static void pageClick(PanelBox p, int awtButton) {
		if (p.mirrorSrc != null) {
			double[] uv = appUV(p, p.hitX, p.hitY);
			if (uv == null || p.mirror == null) {
				return;
			}
			long now = System.currentTimeMillis();
			int clicks = clickPanel == p && now - clickAt < 350 ? 2 : 1;
			clickAt = now;
			clickPanel = p;
			AppControl.mouse(p.mirrorSrc, "down", awtButton == 3 ? 1 : awtButton == 2 ? 2 : 0, uv[0], uv[1], clicks);
			dragAwt = awtButton;
			dragPanel = p;
			return;
		}
		if (p.browser == null) {
			return;
		}
		int px = p.pixelX(p.hitX), py = p.pixelY(p.hitY);
		long now = System.currentTimeMillis();
		int clicks = awtButton == 1 && clickPanel == p && now - clickAt < 350 && Math.abs(px - clickX) < 8 && Math.abs(py - clickY) < 8 ? 2 : 1;
		clickAt = now;
		clickPanel = p;
		clickX = px;
		clickY = py;
		p.browser.mouse(501, px, py, awtButton, clicks);
		dragAwt = awtButton;
		dragPanel = p;
		dragX = px;
		dragY = py;
	}

	private static void closePanel(PanelBox p) {
		if (focused == p || urlPanel == p) {
			stopTyping();
		}
		if (moving == p) {
			moving = null;
		}
		if (resizing == p) {
			resizing = null;
		}
		if (dragPanel == p) {
			dragPanel = null;
			dragAwt = 0;
		}
		Panels.remove(p);
		hover = null;
	}

	/** Scroll: moving = closer / further; on the page = scroll the page; elsewhere on the panel = bigger / smaller. */
	public static boolean onScroll(double vertical) {
		Minecraft mc = Minecraft.getInstance();
		if (!Panels.active() || dev.solclient.compat.Mc.screen(mc) != null || mc.player == null) {
			return false;
		}
		if (moving != null) {
			if (shift()) {
				moving.rotate(0, vertical > 0 ? 5 : -5);
			} else if (ctrl() || raw(343) || raw(347)) {
				moving.rotate(vertical > 0 ? 5 : -5, 0);
			} else {
				grabDist = clamp(grabDist + vertical * 0.6, 1.0, 32.0);
			}
			return true;
		}
		PanelBox p = hover;
		if (p == null) {
			return false;
		}
		// Shift + scroll: tilt up / down; Ctrl (Cmd) + scroll: turn left / right - anywhere on the panel
		if (shift()) {
			p.rotate(0, vertical > 0 ? 5 : -5);
			return true;
		}
		if (ctrl() || raw(343) || raw(347)) {
			p.rotate(vertical > 0 ? 5 : -5, 0);
			return true;
		}
		if (p.inContent(p.hitX, p.hitY) && p.mirrorSrc != null) {
			double[] uv = appUV(p, p.hitX, p.hitY);
			if (uv != null) {
				AppControl.scroll(p.mirrorSrc, uv[0], uv[1], vertical);
			}
			return true;
		}
		if (p.inContent(p.hitX, p.hitY)) {
			if (p.browser != null) {
				p.browser.wheel(p.pixelX(p.hitX), p.pixelY(p.hitY), vertical > 0 ? 1 : -1);
			}
			return true;
		}
		scaleAround(p, vertical > 0 ? 1.07 : 1 / 1.07);
		return true;
	}

	// ------------------------------------------------------------------ keyboard (typing mode)

	/**
	 * Gives Chromium's keyboard focus back to the page being typed into. Every new browser (another panel, a Watch
	 * Party screen, a video cape...) takes the focus when it is created, which made typing stop working "sometimes".
	 */
	public static void refocus() {
		if (focused != null) {
			pendingFocus = focused;
		}
	}

	private static PanelBox pendingFocus;

	private static void startTyping(PanelBox p) {
		boolean was = typing();
		urlPanel = null;
		focused = p;
		pendingFocus = p; // given on the next frame: calling into Chromium from the click itself froze the game
		if (!was) {
			enteredTyping("Typing mode ON (Esc to leave)");
		}
	}

	private static void startUrl(PanelBox p) {
		boolean was = typing();
		focused = null;
		urlPanel = p;
		urlText = p.shownUrl == null ? "" : p.shownUrl;
		if (!was) {
			enteredTyping("Address bar (Enter to go, Esc to cancel)");
		}
	}

	private static void enteredTyping(String msg) {
		// keys that were held when typing started (WASD...) would stay "pressed" because their release is swallowed
		KeyMapping.releaseAll();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(msg));
		}
	}

	public static void stopTyping() {
		boolean was = typing();
		focused = null;
		urlPanel = null;
		if (was) {
			KeyMapping.releaseAll();
		}
	}

	/** True = the key is used up. */
	public static boolean onKey(int action, int key) {
		return onKey(action, key, null);
	}

	/** keyInput: Minecraft's own key event, handed to MCEF as it is (MCEF knows how to turn it into a Chromium key). */
	public static boolean onKey(int action, int key, Object keyInput) {
		if (!typing()) {
			return false;
		}
		boolean release = action == GLFW.GLFW_RELEASE;
		if (urlPanel != null) {
			if (!release) {
				urlKey(key);
			}
			return true;
		}
		if (key == 256) { // Esc
			if (action == GLFW.GLFW_PRESS) {
				stopTyping();
			}
			return true;
		}
		if (focused.mirrorSrc != null) {
			// an app: letters come through onChar; here the other keys and the shortcuts (Cmd / Ctrl + key)
			boolean printable = key == 32 || key >= 39 && key <= 96;
			boolean cmd = raw(343) || raw(347), ctl = raw(341) || raw(345), alt = raw(342) || raw(346);
			if (!printable || cmd || ctl) {
				AppControl.key(focused.mirrorSrc, key, !release, shift(), ctl, alt, cmd);
			}
			return true;
		}
		Mcef.Browser b = focused.browser;
		if (b == null) {
			return true;
		}
		int awt = BrowserKeys.awt(key);
		if (release) {
			if (!b.keyMc(keyInput, false) && awt != 0) {
				b.key(402, awt, (char) 0, mods());
			}
			return true;
		}
		if (shortcut()) {
			// Ctrl / Cmd + A C X V Z Y: Chromium's own edit commands (real clipboard), JavaScript as a fallback
			switch (key) {
				case 86 -> { // V
					if (!b.edit("paste")) {
						String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
						if (clip != null && !clip.isEmpty()) {
							b.js("document.execCommand('insertText', false, " + jsString(clip) + ");");
						}
					}
					return true;
				}
				case 65 -> { // A
					if (!b.edit("selectAll")) {
						b.js("document.execCommand('selectAll');");
					}
					return true;
				}
				case 67 -> { // C
					if (!b.edit("copy")) {
						copyByScript(b);
					}
					return true;
				}
				case 88 -> { // X
					if (!b.edit("cut")) {
						copyByScript(b);
						b.js("document.execCommand('delete');");
					}
					return true;
				}
				case 90 -> { // Z (Shift = redo)
					if (!b.edit(shift() ? "redo" : "undo")) {
						b.js("document.execCommand('" + (shift() ? "redo" : "undo") + "');");
					}
					return true;
				}
				case 89 -> { // Y
					if (!b.edit("redo")) {
						b.js("document.execCommand('redo');");
					}
					return true;
				}
				default -> {
				}
			}
		}
		if (!b.keyMc(keyInput, true) && awt != 0) {
			b.key(401, awt, (char) 0, mods());
		}
		return true;
	}

	private static void urlKey(int key) {
		PanelBox p = urlPanel;
		if (key == 256) {
			stopTyping();
		} else if (key == 257 || key == 335) {
			String target = BrowserKeys.address(urlText);
			if (p != null && p.browser != null) {
				p.browser.load(target);
				p.shownUrl = target;
			}
			stopTyping();
		} else if (key == 259) {
			if (!urlText.isEmpty()) {
				urlText = urlText.substring(0, urlText.length() - 1);
			}
		} else if (shortcut() && key == 86) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			urlText = (urlText + (clip == null ? "" : clip.replace("\n", "").replace("\r", ""))).trim();
		} else if (shortcut() && (key == 67 || key == 88)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(urlText);
			if (key == 88) {
				urlText = "";
			}
		} else if (shortcut() && key == 65) {
			urlText = "";
		}
	}

	/** True = the character is used up. */
	public static boolean onChar(int cp) {
		return onChar(cp, null);
	}

	public static boolean onChar(int cp, Object input) {
		if (!typing()) {
			return false;
		}
		if (cp < 32 || cp == 127 || cp > 0xFFFF) {
			return true;
		}
		if (urlPanel != null) {
			if (urlText.length() < 500) {
				urlText += (char) cp;
			}
			return true;
		}
		if (focused.mirrorSrc != null) {
			if (!(raw(343) || raw(347) || raw(341) || raw(345))) {
				AppControl.text(focused.mirrorSrc, new String(Character.toChars(cp)));
			}
			return true;
		}
		Mcef.Browser b = focused.browser;
		if (b != null && !b.charMc(input)) {
			b.key(400, 0, (char) cp, mods());
		}
		return true;
	}

	/** Copy when Chromium's own copy command is not available: the page copies its selection itself. Last resort. */
	private static void copyByScript(Mcef.Browser b) {
		b.js("(function(){var s=String(window.getSelection?window.getSelection():'');"
				+ "var a=document.activeElement;if(!s&&a&&typeof a.selectionStart==='number'){s=a.value.substring(a.selectionStart,a.selectionEnd);}"
				+ "if(s){try{navigator.clipboard.writeText(s);}catch(e){}document.execCommand('copy');}})();");
	}

	private static String jsString(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (char ch : s.toCharArray()) {
			switch (ch) {
				case '\\' -> sb.append("\\\\");
				case '"' -> sb.append("\\\"");
				case '\n' -> sb.append("\\n");
				case '\r' -> {
				}
				default -> sb.append(ch);
			}
		}
		return sb.append('"').toString();
	}
}
