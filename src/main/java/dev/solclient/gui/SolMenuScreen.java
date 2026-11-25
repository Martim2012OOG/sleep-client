package dev.solclient.gui;

import dev.solclient.Config;
import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.SolClient;
import dev.solclient.emote.EmoteLibrary;
import dev.solclient.emote.EmoteSlots;
import dev.solclient.mods.Mod;
import dev.solclient.option.Option;
import dev.solclient.social.RemoteTextures;
import dev.solclient.social.Social;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The Plegumo main menu (Right Shift by default), laid out like the hand-drawn mock-up:
 * top bar (edit button / logo / nick), second row (name / search bar with icons), a button list on the left,
 * the big content panel in the middle (mods, shop, skin editor...), an optional edit panel, and an optional
 * friends panel with a chat panel that slides in when a friend is clicked.
 * Friends, chat, notifications, shop and capes come from the Solar social backend (see dev.solclient.social.Social).
 */
public class SolMenuScreen extends Screen {

	private static final class Region {
		int x, y, w, h, clipY0, clipY1;
		IntConsumer click;
		BiConsumer<Double, Double> drag;
		/** What this region drags (a slider's option), to know which slider the mouse is holding. */
		Object tag;

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipY0 && my < clipY1;
		}
	}

	// ---- design constants (menu units)
	private static final int DESIGN_W = 1000, DESIGN_H = 450;
	private static final int LW = 130, EW = 180, FW = 170, CW = 260, GAP = 8, PAD = 12, ROW_H = 28;
	private static int SUB = 0xFF121317, TILE = 0xFF1A1C22, TILE_HOVER = 0xFF22242C;
	private static int LINE = 0xFF3A3D49;

	// left button list: 0-4 filter the mods, 5 / 6 switch what the big panel shows, 7 opens the HUD editor
	private static final String[] NAV = {"All Mods", "HUD", "Utility", "Visual", "Favourites", "Locker", "Capes & Skins", "Profiles", "Edit HUD", "Add-ons", "Minigames"};
	/** One icon per sidebar button, same order as NAV. */
	private static final String[] NAV_ICON = {"grid", "hud", "nav_utility", "nav_visual", "favourite", "nav_locker", "nav_capes", "player", "edit", "quickplay", "minigames"};
	private static final String[] VIEW_TITLE = {"Mods", "Locker", "Capes & Skins", "Notifications", "Profiles"};
	private static final int V_MODS = 0, V_SHOP = 1, V_SKIN = 2, V_NOTIF = 3, V_PROFILES = 4, V_ADDONS = 5, V_PACKS = 6, V_ME = 7;

	private static final int F_NONE = 0, F_SEARCH = 1, F_FRIEND_SEARCH = 2, F_CHAT = 3, F_PROFILE = 4, F_LOCKER = 5, F_SKIN = 6, F_GIF = 7, F_GROUP = 8, F_OPT = 9, F_PACKS = 10;
	/** Text option being edited + one TextBox per Option.Text. */
	private Option.Text optFocus;
	private final java.util.Map<Option.Text, TextBox> optBoxes = new java.util.HashMap<>();

	private final List<Region> regions = new ArrayList<>();
	private Region activeDrag;
	/** Mouse button that started the current drag (0 left, 1 right) - pixel editor draws / erases. */
	private int dragButton;
	private final Map<String, float[]> toggleAnim = new HashMap<>();
	private int nav;
	private int view = V_MODS;
	private boolean gridView = true;
	private final TextBox search = new TextBox(24);
	private final TextBox friendSearch = new TextBox(36);
	private final TextBox chatInput = new TextBox(256);
	private final TextBox profileName = new TextBox(24, cp -> Character.isLetterOrDigit(cp) || cp == ' ' || cp == '_' || cp == '-');
	/** Field being mouse-dragged (text selection). */
	private TextBox draggingBox;
	private int focus = F_NONE;
	private Mod open;
	private Mod selected;
	private float scroll, scrollTarget;
	private int maxScroll;
	private float friendScroll;
	private int friendMax;
	private int fbx, fby, fbw, fbh; // friends panel bounds in menu units (for mouse wheel)
	private Option.Key listening;
	private Option.Col colourOpen;
	/** The value list of a choice option that is open under its row (null = none). */
	private Option.Choice choiceOpen;
	private final Map<Option.Col, float[]> hsv = new HashMap<>();
	private int clipY0, clipY1;
	/** Tooltip for this frame (drawn last). */
	private String tooltipText;
	private final long openedAt = System.currentTimeMillis();
	private final Map<String, Float> hoverAnim = new HashMap<>();
	private boolean addMode;
	private String confirmKey;
	private long confirmAt;

	// optional panels: wanted state + animation (0..1)
	// side panels / chat keep the state they had when the menu was last closed
	private static boolean savedEdit, savedFriends;
	private static String savedChat;
	private static int savedView = V_MODS, savedNav;
	private boolean editOpen = savedEdit;
	private boolean friendsOpen = savedFriends;
	private String chatId;
	private float aE = savedEdit ? 1F : 0F, aF = savedFriends && savedChat == null ? 1F : 0F, aC = savedChat != null && savedFriends ? 1F : 0F;
	private float chatScroll;
	private int chatMax;
	private int cbx, cby, cbw, cbh; // chat panel bounds (mouse wheel)

	// The menu is laid out in its own "menu units" (design size 1000x540), independent of Minecraft's GUI scale.
	// u = GUI units per menu unit, f = GUI units -> menu units (1 / u), k = real pixels per menu unit (crisp shapes).
	private float u = 1F;
	private float appliedUser = 1F;
	private float k = 2F;
	private float f = 1F;
	private int vw = 960, vh = 540;

	private void updateScale() {
		var win = MinecraftClient.getInstance().getWindow();
		float g = Math.max(1F, (float) win.getScaleFactor());
		if (activeDrag == null && SolClient.settings != null) {
			appliedUser = SolClient.settings.menuSize.f(); // only re-applied when not dragging a slider, so it doesn't jitter
		}
		float user = appliedUser;
		u = Math.min(width * 0.96F / DESIGN_W, height * 0.94F / DESIGN_H) * user;
		u = Math.max(0.25F, u);
		f = 1F / u;
		k = Math.max(0.5F, u * g);
		vw = Math.round(width * f);
		vh = Math.round(height * f);
	}

	public SolMenuScreen() {
		super(Text.literal("Caos"));
		Social.onMenuOpened();
		if (savedChat != null && savedFriends) {
			chatId = savedChat;
			chatScroll = 0;
			Social.openChat(savedChat);
		}
		if (savedView != V_MODS) {
			showView(savedView, savedNav);
		} else {
			nav = savedNav;
		}
	}

	/** Opens the menu straight on one view (used by the title / pause screens: shop, capes...). */
	public static SolMenuScreen open(int view) {
		savedView = view;
		if (view == VIEW_SHOP) {
			lockerShop = true; // the shop icons on the title / pause screens open the shop side
			lockerStore = false;
		}
		savedNav = view == 1 ? 5 : view == 2 ? 6 : 0;
		return new SolMenuScreen();
	}

	public static final int VIEW_MODS = 0, VIEW_SHOP = 1, VIEW_CAPES = 2, VIEW_NOTIFICATIONS = 3;

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------ helpers
	private void region(int x, int y, int w, int h, IntConsumer click, BiConsumer<Double, Double> drag) {
		Region r = new Region();
		r.x = x;
		r.y = y;
		r.w = w;
		r.h = h;
		r.clipY0 = clipY0;
		r.clipY1 = clipY1;
		r.click = click;
		r.drag = drag;
		regions.add(r);
	}

	private void region(int x, int y, int w, int h, Runnable click) {
		region(x, y, w, h, b -> click.run(), null);
	}

	/** Marks the last region as belonging to this slider / object. */
	private void tagLast(Object tag) {
		if (!regions.isEmpty()) {
			regions.get(regions.size() - 1).tag = tag;
		}
	}

	private boolean draggingTag(Object tag) {
		return activeDrag != null && activeDrag.tag == tag;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void closeMenu() {
		SolClient.armed = false;
		Config.save();
		MinecraftClient.getInstance().setScreen(null);
	}

	/** Close / remove icon (circle with an X), drawn around the old s x s cross area. */
	private static void drawX(DrawContext c, int x, int y, int s, int col) {
		int z = s + 5;
		Gfx.tex(c, Theme.icon2("close"), x + s / 2 - z / 2, y + s / 2 - z / 2, z, z, 64, 64, col);
	}

	private static float[] toHsv(int argb) {
		float r = ((argb >> 16) & 255) / 255F, g = ((argb >> 8) & 255) / 255F, b = (argb & 255) / 255F;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6F;
			if (h < 0) h += 1;
		}
		return new float[] {h, max == 0 ? 0 : d / max, max, ((argb >>> 24) & 255) / 255F};
	}

	private static int hsvToRgb(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float c = v * s;
		float x = c * (1 - Math.abs((h * 6) % 2 - 1));
		float m = v - c;
		float r, g, b;
		switch ((int) (h * 6) % 6) {
			case 0: r = c; g = x; b = 0; break;
			case 1: r = x; g = c; b = 0; break;
			case 2: r = 0; g = c; b = x; break;
			case 3: r = 0; g = x; b = c; break;
			case 4: r = x; g = 0; b = c; break;
			default: r = c; g = 0; b = x;
		}
		return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
	}

	private static int hsvToArgb(float[] st) {
		return (hsvToRgb(st[0], st[1], st[2]) & 0xFFFFFF) | (Math.round(st[3] * 255) << 24);
	}

	// ------------------------------------------------------- small vector icons
	private static void star(DrawContext c, int cx, int cy, int r, int col) {
		int d = Math.max(1, r - 1);
		c.fill(cx - r, cy, cx + r + 1, cy + 1, col);
		c.fill(cx, cy - r, cx + 1, cy + r + 1, col);
		for (int i = -d; i <= d; i++) {
			c.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
			c.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, col);
		}
	}

	private static void cartIcon(DrawContext c, int cx, int cy, int col) {
		Gfx.tex(c, Theme.icon2("shop"), cx - 8, cy - 8, 16, 16, 64, 64, col);
	}

	private static void bellIcon(DrawContext c, int cx, int cy, int col) {
		Gfx.tex(c, Theme.icon2("bell"), cx - 8, cy - 8, 16, 16, 64, 64, col);
	}

	private static void gridIcon(DrawContext c, int x, int y, int col) {
		Gfx.tex(c, Theme.icon2("grid"), x - 1, y - 1, 14, 14, 64, 64, col);
	}

	private static void listIcon(DrawContext c, int x, int y, int col) {
		Gfx.tex(c, Theme.icon2("list"), x - 1, y - 1, 14, 14, 64, 64, col);
	}

	/** Mod icon centred on cx, cy (falls back to a coloured circle with the first letter). */
	private void modIcon(DrawContext c, Mod m, float cx, float cy, int size, boolean lit) {
		Identifier icon = Theme.icon(m.id);
		if (icon != null) {
			Gfx.tex(c, icon, Math.round(cx - size / 2F), Math.round(cy - size / 2F), size, size, 64, 64, lit ? 0xFFFFFFFF : 0xFF7D8190);
		} else {
			Gfx.circle(c, cx, cy, size / 2F, Theme.OFF_BG_HOVER);
			Gfx.textC(c, m.name.substring(0, 1), Math.round(cx), Math.round(cy) - 4, Theme.TEXT);
		}
	}

	private static String fit(String s, int maxW) {
		if (Gfx.tw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.tw(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	private static List<String> wrap(String s, int maxW, int maxLines) {
		List<String> out = new ArrayList<>();
		String cur = "";
		for (String word : s.split(" ")) {
			String t = cur.isEmpty() ? word : cur + " " + word;
			if (cur.isEmpty() || Gfx.tw(t) <= maxW) {
				cur = t;
			} else {
				out.add(cur);
				cur = word;
				if (out.size() >= maxLines) {
					return out;
				}
			}
		}
		if (!cur.isEmpty() && out.size() < maxLines) {
			out.add(cur);
		}
		return out;
	}

	private static float step(float a, boolean target) {
		float g = target ? 1F : 0F;
		a += (g - a) * ease(14F);
		return Math.abs(g - a) < 0.01F ? g : a;
	}

	/** Seconds since the last frame (animations follow the clock, not the frame rate: a slow menu no longer feels slow). */
	private static float frameDt = 1F / 60F;
	private static long lastFrameNs;

	private static void frameTick() {
		long now = System.nanoTime();
		frameDt = lastFrameNs == 0 ? 1F / 60F : Math.max(0.001F, Math.min(0.1F, (now - lastFrameNs) / 1e9F));
		lastFrameNs = now;
	}

	/** How far to move towards the target this frame (k = speed: 25 settles in about 0.1 s). */
	private static float ease(float k) {
		return 1F - (float) Math.exp(-frameDt * k);
	}

	private static final java.util.Map<String, Float> SWITCH_POS = new java.util.HashMap<>();

	/** Position 0..1 of an on / off switch: it glides to its side in about a quarter of a second (always, whatever the hover-animation setting). */
	private static float swAnim(String key, boolean on) {
		float t = on ? 1F : 0F;
		float v = SWITCH_POS.getOrDefault(key, t);
		v += (t - v) * (1F - (float) Math.exp(-frameDt * 16F));
		if (Math.abs(t - v) < 0.01F) {
			v = t;
		}
		SWITCH_POS.put(key, v);
		return v;
	}

	// ------------------------------------------------------------- render
	private float anim(String key, boolean target) {
		if (SolClient.settings != null && !SolClient.settings.buttonAnimations.on()) {
			return target ? 1F : 0F; // hover animations off (setting)
		}
		float v = hoverAnim.getOrDefault(key, 0F);
		v += ((target ? 1F : 0F) - v) * ease(28F);
		if (Math.abs((target ? 1F : 0F) - v) < 0.01F) {
			v = target ? 1F : 0F;
		}
		hoverAnim.put(key, v);
		return v;
	}

	/**
	 * One item of a "pick one" list in the side-menu look: the selected one has the soft highlight, the others are
	 * just text (a faint light on hover). No borders, no button style, no animation. Returns the text colour to use.
	 */
	private int navItem(DrawContext c, int mx, int my, int x, int y, int w, int h, String id, boolean sel) {
		boolean hov = !sel && in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
		float a = anim(id, hov);
		if (sel) {
			Gfx.rrect(c, x, y, w, h, 4, SlideBg.colour());
			return 0xFFFFFFFF;
		}
		if (a > 0.01F) {
			Gfx.rrect(c, x, y, w, h, 4, Gfx.withAlpha(0xFFFFFF, Math.round(0x0C * a)));
		}
		return Gfx.blend(Theme.DIM, Theme.TEXT, 0.55F + 0.45F * a);
	}

	private void pill(DrawContext c, int mx, int my, int x, int y, int w, int h, int r, String id, boolean selected, int selBg, int bg, int bgHover) {
		boolean hov = in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
		float a = anim(id, hov);
		boolean neutral = bg == TILE || bg == Theme.OFF_BG || bg == SUB || bg == Theme.CARD;
		if (neutral && (!selected || selBg == Theme.accent() || selBg == 0)) {
			KuboButton.bg(c, x, y, w, h, a, selected); // same buttons as the pause / main menu
			return;
		}
		if (classic()) {
			if (selected) {
				Gfx.button(c, x, y, w, h, false, true);
			} else {
				Gfx.buttonBody(c, x, y, w, h, slate(bg, a > 0.5F), false);
			}
			return;
		}
		if (selected && selBg == Theme.accent()) {
			selBg = SlideBg.colour();
		}
		Gfx.rrect(c, x, y, w, h, r, selected ? selBg : Gfx.blend(bg, bgHover, a));
	}

	private static boolean classic() {
		return SolClient.settings == null || SolClient.settings.buttonStyle.idx() * 0 == 0;
	}

	/** Classic style: neutral dark tiles become the slate button colour; coloured ones (green, red, accent) stay. */
	private static int slate(int col, boolean hover) {
		if (col == TILE || col == Theme.OFF_BG || col == SUB || col == Theme.CARD) {
			return hover ? Gfx.BTN_HOVER : Gfx.BTN_BODY;
		}
		if (col == TILE_HOVER || col == Theme.OFF_BG_HOVER || col == Theme.CARD_HOVER) {
			return Gfx.BTN_HOVER;
		}
		return col | 0xFF000000;
	}

	/** Vanilla/Laby-like button body: flat colour, darker bottom edge, faint top highlight, almost square corners. */
	private static void classicFill(DrawContext c, int x, int y, int w, int h, int col) {
		Gfx.buttonBody(c, x, y, w, h, col, false);
	}

	/** Text field with an optional magnifier on the right (like the mock-up). */
	private void field(DrawContext c, int x, int y, int w, int h, TextBox box, String hint, boolean focused, int accent, boolean magnifier) {
		if (focused) {
			Gfx.rrect(c, x - 2, y - 2, w + 4, h + 4, 10, Gfx.withAlpha(accent, 0x30)); // soft focus ring
		}
		Gfx.rrectBorder(c, x, y, w, h, 8, Theme.FIELD, focused ? Gfx.withAlpha(accent, 0xFF) : Theme.CARD_BORDER);
		int tx = x + (magnifier ? 24 : 9);
		int maxW = w - 18 - (magnifier ? 16 : 0);
		if (box.isEmpty() && !focused) {
			Gfx.textMid(c, fit(hint, maxW), tx, y, h, Theme.DIM);
			box.draw(c, x, y, w, h, tx, maxW, false, accent, Theme.TEXT); // keeps the hit box up to date
		} else {
			box.draw(c, x, y, w, h, tx, maxW, focused, accent, Theme.TEXT);
		}
		if (magnifier) {
			Gfx.tex(c, Theme.icon2("search"), x + 8, y + (h - 11) / 2, 11, 11, 64, 64, focused || !box.isEmpty() ? Theme.TEXT : Theme.DIM);
		}
	}

	/** The vanilla background re-blurs the whole game every frame (costly); a flat dark backdrop costs nothing. */
	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		if (dev.solclient.mods.MenuBlurMod.fast() && client != null && client.world != null) {
			c.fill(0, 0, width, height, 0xB0000000);
			return;
		}
		super.renderBackground(c, mouseX, mouseY, delta);
	}

	@Override
	public void render(DrawContext c, int guiMouseX, int guiMouseY, float delta) {
		super.render(c, guiMouseX, guiMouseY, delta);
		frameTick();
		updateScale();
		Theme.refresh();
		SUB = Theme.SUB;
		TILE = Theme.TILE;
		TILE_HOVER = Theme.TILE_HOVER;
		LINE = Theme.LINE;
		int mouseX = Math.round(guiMouseX * f);
		int mouseY = Math.round(guiMouseY * f);
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(1F / f, 1F / f);
		Gfx.scaleOverride = k;
		Gfx.shapeAlpha = SolClient.settings == null ? 1F : SolClient.settings.menuOpacity.f() / 100F;
		try {
			renderMenu(c, mouseX, mouseY);
		} finally {
			Gfx.scaleOverride = 0F;
			Gfx.shapeAlpha = 1F;
			c.getMatrices().popMatrix();
		}
	}

	private void renderMenu(DrawContext c, int mouseX, int mouseY) {
		Crates.tick();
		regions.clear();
		tooltipText = null;
		clipY0 = 0;
		clipY1 = vh;
		scroll += (scrollTarget - scroll) * 0.35F;
		if (Math.abs(scrollTarget - scroll) < 0.5F) {
			scroll = scrollTarget;
		}

		float t = Math.min(1F, (System.currentTimeMillis() - openedAt) / 200F);
		float ease = 1F - (1F - t) * (1F - t) * (1F - t);
		int slide = Math.round((1F - ease) * 14F);

		int pw = Math.min(vw - 16, DESIGN_W);
		int ph = Math.min(vh - 16, DESIGN_H);
		int x0 = (vw - pw) / 2;
		int y0 = (vh - ph) / 2 + slide;
		int accent = Theme.accent();

		// "tela do PC" in the mock-up is just the screen itself: no background box, panels float over the game

		// ---- which optional panels fit, and their slide animation
		int left = x0 + PAD, right = x0 + pw - PAD;
		float room = (pw - 2 * PAD) - LW - GAP - 200;
		// the chat takes the friends panel's place (and is wider); closing the chat brings the friends list back
		boolean chatting = friendsOpen && (Social.friend(chatId) != null || Social.group(chatId) != null) && CW + GAP <= room;
		boolean fShow = friendsOpen && !chatting;
		boolean cShow = chatting;
		float used = (fShow ? FW + GAP : 0) + (cShow ? CW + GAP : 0);
		boolean eShow = editOpen && used + EW + GAP <= room;
		aE = step(aE, eShow);
		aF = step(aF, fShow);
		aC = step(aC, cShow);

		int row1 = y0 + 10;
		int row2 = row1 + ROW_H + GAP;
		int row3 = row2 + ROW_H + GAP;
		int bottom = y0 + ph - PAD;
		int h3 = bottom - row3;
		int hSide = bottom - row2;

		// positions from the right edge: chat, friends, edit, then the big panel takes the rest
		int edge = right;
		int chatRight = edge;
		int chatX = Math.round(edge - CW * aC);
		edge = Math.round(edge - (CW + GAP) * aC);
		int friendsRight = edge;
		int friendsX = Math.round(edge - FW * aF);
		edge = Math.round(edge - (FW + GAP) * aF);
		int barRight = edge;
		int editRight = edge;
		int editX = Math.round(edge - EW * aE);
		edge = Math.round(edge - (EW + GAP) * aE);
		int cx = left + LW + GAP;
		int cw = Math.max(40, edge - cx);

		fbw = 0;
		cbw = 0;
		drawTopBar(c, mouseX, mouseY, x0, pw, row1, left, right, accent);
		drawSecondRow(c, mouseX, mouseY, row2, left, cx, barRight, accent);
		drawLeftList(c, mouseX, mouseY, left, row3, h3, accent);
		drawCenter(c, mouseX, mouseY, cx, row3, cw, h3, accent);
		if (aE > 0.01F) {
			drawEdit(c, mouseX, mouseY, editX, editRight, row3, h3, accent);
		}
		if (aF > 0.01F) {
			drawFriends(c, mouseX, mouseY, friendsX, friendsRight, row2, hSide, accent);
		}
		if (aC > 0.01F) {
			drawChat(c, mouseX, mouseY, chatX, chatRight, row2, hSide, accent);
		}
		// (Social.say messages are notification cards now: gui.Notify)
		drawBuyWindow(c, mouseX, mouseY, accent);
		Crates.drawOverlay(c, mouseX, mouseY, vw, vh, crateHost);
		if (statusMenu) {
			drawStatusMenu(c, mouseX, mouseY);
		}
		if (tooltipText != null) {
			// long texts are wrapped in a box of at most 220 px (they used to run across the whole screen)
			List<String> tl = wrap(tooltipText, 220, 6);
			int widest = 0;
			for (String ln : tl) {
				widest = Math.max(widest, Gfx.tw(ln));
			}
			int tw = widest + 12, th = tl.size() * 11 + 5;
			int tx = Math.max(2, Math.min(vw - tw - 2, mouseX + 10)), ty = Math.max(2, Math.min(vh - th - 2, mouseY - 18));
			Gfx.rrectBorder(c, tx, ty, tw, th, 5, 0xF0101114, Theme.CARD_BORDER);
			for (int i = 0; i < tl.size(); i++) {
				Gfx.text(c, tl.get(i), tx + 6, ty + 4 + i * 11, Theme.TEXT);
			}
		}
	}

	// -------------------------------------------------------------- top bar
	private void drawTopBar(DrawContext c, int mx, int my, int x0, int pw, int y, int left, int right, int accent) {
		// edit button (top-left, as wide as the button list)
		pill(c, mx, my, left, y, LW, ROW_H, 9, "editbtn", editOpen, accent, TILE, TILE_HOVER);
		Gfx.tex(c, Theme.icon2("edit"), left + 10, y + ROW_H / 2 - 8, 16, 16, 64, 64, editOpen ? 0xFFFFFFFF : Theme.TEXT);
		Gfx.textMid(c, "Edit", left + 32, y, ROW_H, editOpen ? 0xFFFFFFFF : Theme.TEXT);
		region(left, y, LW, ROW_H, () -> editOpen = !editOpen);

		// (no centre logo: the Caos logo lives in the left pill below)
		int lx = left, lw = LW;

		// close button + nick pill (top-right)
		int closeX = right - ROW_H;
		boolean hClose = in(mx, my, closeX, y, ROW_H, ROW_H);
		float ca = anim("close", hClose);
		Gfx.button(c, closeX, y, ROW_H, ROW_H, ca > 0.5F, false);
		int cis = Math.round(ROW_H * 0.62F);
		Gfx.tex(c, Theme.icon2("close"), closeX + (ROW_H - cis) / 2, y + (ROW_H - cis) / 2 - 1, cis, cis, 64, 64, ca > 0.5F ? 0xFFFF6B6B : 0xFFE6E8EE);
		region(closeX, y, ROW_H, ROW_H, this::closeMenu);

		Social.User me = Social.me;
		String user = me != null ? me.display() : MinecraftClient.getInstance().getSession().getUsername();
		int upW = Gfx.tw(user) + 40;
		int upX = closeX - GAP - upW;
		Gfx.rrectBorder(c, upX, y, upW, ROW_H, 14, TILE, Theme.CARD_BORDER);
		if (me != null) {
			avatar(c, me, upX + 16, y + ROW_H / 2, 8, true);
		} else {
			Gfx.circle(c, upX + 16, y + ROW_H / 2F, 8, accent);
			Gfx.textC(c, user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase(Locale.ROOT), upX + 16, y + 10, 0xFFFFFFFF);
		}
		// my status (click: Online / Away / Busy / Invisible); red ring = not connected to Caos
		if (me != null) {
			StatusBadge.draw(c, Social.effectiveStatus(), upX + 26, y + ROW_H / 2 + 10, 7, TILE);
		} else {
			Gfx.circle(c, upX + 22, y + ROW_H / 2F + 6, 3, Social.loggingIn ? 0xFFFFC94D : Theme.RED);
		}
		Gfx.textMid(c, user, upX + 30, y, ROW_H, Theme.TEXT);
		if (me == null && !Social.loggingIn) {
			region(upX, y, upW, ROW_H, Social::retryLogin);
		} else if (me != null) {
			region(upX, y, upW, ROW_H, () -> showView(V_ME, -1));
		}

		// coins (opens the shop)
		String ct = coinsText(Math.max(0, Social.coins));
		int cw = Gfx.tw(ct) + 30;
		int cx = upX - GAP - cw;
		if (me != null && Social.coins >= 0 && cx > lx + lw + GAP) {
			pill(c, mx, my, cx, y, cw, ROW_H, 14, "coins", false, 0, TILE, TILE_HOVER);
			Gfx.coinSpinning(c, cx + 8, y + ROW_H / 2 - 6, 12);
			Gfx.textMid(c, ct, cx + 24, y, ROW_H, Theme.TEXT);
			region(cx, y, cw, ROW_H, () -> showView(V_SHOP, 5));
		}
	}

	// ----------------------------------------------------------- second row
	private void drawSecondRow(DrawContext c, int mx, int my, int y, int left, int cx, int barRight, int accent) {
		// "name" pill: what the big panel is showing
		Gfx.rrectBorder(c, left, y, LW, ROW_H, 9, TILE, Theme.CARD_BORDER);
		Gfx.tex(c, Theme.icon2("sol_client"), left + 6, y + 4, 20, 20, 64, 64, 0xFFFFFFFF);
		Gfx.textMid(c, "Caos", left + 32, y, ROW_H, Theme.TEXT);

		// long bar with the four icons on the right
		int bw = barRight - cx;
		if (bw < 60) {
			return;
		}
		Gfx.rrectBorder(c, cx, y, bw, ROW_H, 9, SUB, Theme.CARD_BORDER);
		int on = 0, total = 0;
		for (Mod m : SolClient.MODS) {
			if (!m.locked && !m.hiddenFromList) {
				total++;
				if (m.enabled) {
					on++;
				}
			}
		}
		String status = on + " of " + total + " mods enabled";
		int bs = 22, bgp = 4;
		int ix = barRight - 6 - 4 * bs - 3 * bgp;

		// "Resource Packs" button (icon + text), just left of the settings icon: opens the pack browser
		String rpText = dev.solclient.Lang.t("Resource Packs");
		int rpW = 10 + 14 + 6 + Gfx.tw(rpText) + 10, rpH = bs, rpX = ix - bgp - rpW, rpY = y + (ROW_H - rpH) / 2;
		boolean showRp = rpX > cx + 6;
		if (showRp) {
			pill(c, mx, my, rpX, rpY, rpW, rpH, 7, "barRp", view == V_PACKS && open == null, accent, SUB, TILE_HOVER);
			Gfx.tex(c, Theme.icon2("resource_packs"), rpX + 8, rpY + 4, 14, 14, 64, 64, Theme.TEXT);
			Gfx.textMid(c, rpText, rpX + 8 + 14 + 6, rpY, rpH, Theme.TEXT);
			region(rpX, rpY, rpW, rpH, () -> showView(V_PACKS, -1));
		}
		// Ranked PvP: installed with the client, always one click away (left of Resource Packs)
		dev.solclient.addons.Addon rk = null;
		for (dev.solclient.addons.Addon a : dev.solclient.addons.Addons.list()) {
			if ("ranked_pvp".equals(a.id())) {
				rk = a;
			}
		}
		String rkText = "PvP";
		int rkW = 10 + 14 + 6 + Gfx.tw(rkText) + 10, rkX = rpX - bgp - rkW;
		boolean showRk = rk != null && showRp && rkX > cx + 6;
		if (showRk) {
			final dev.solclient.addons.Addon fr = rk;
			boolean rkOn = view == V_ADDONS && embedded != null && embeddedAddon;
			pill(c, mx, my, rkX, rpY, rkW, rpH, 7, "barRk", rkOn, accent, SUB, TILE_HOVER);
			Gfx.tex(c, Theme.icon2("pvp_group"), rkX + 8, rpY + 4, 14, 14, 64, 64, Theme.TEXT);
			Gfx.textMid(c, rkText, rkX + 8 + 14 + 6, rpY, rpH, Theme.TEXT);
			region(rkX, rpY, rkW, rpH, () -> {
				showView(V_ADDONS, 9);
				openAddon(fr);
			});
		}
		int statusEnd = showRk ? rkX : showRp ? rpX : ix;
		if (Gfx.tw(status) + 20 < statusEnd - cx) {
			Gfx.textMid(c, status, cx + 12, y, ROW_H, Theme.DIM);
		}
		for (int i = 0; i < 4; i++) {
			int bx = ix + i * (bs + bgp), by = y + (ROW_H - bs) / 2;
			boolean sel = i == 0 ? open == SolClient.settings : i == 1 ? view == V_SHOP && open == null : i == 2 ? friendsOpen : view == V_NOTIF && open == null;
			pill(c, mx, my, bx, by, bs, bs, 7, "bar" + i, sel, accent, SUB, TILE_HOVER);
			int col = sel ? 0xFFFFFFFF : Theme.TEXT;
			int icx = bx + bs / 2, icy = by + bs / 2;
			if (i == 0) {
				Gfx.texSpin(c, Theme.icon2("settings_small"), bx + 3, by + 3, 16, 16, 64, 64, col, "gear:bar", in(mx, my, bx, by, bs, bs));
			} else if (i == 1) {
				cartIcon(c, icx, icy, col);
			} else if (i == 2) {
				Gfx.tex(c, Theme.icon2("players"), bx + 3, by + 3, 16, 16, 64, 64, col);
			} else {
				bellIcon(c, icx, icy, col);
			}
			if (i == 2) {
				badge(c, bx + bs - 2, by + 2, Social.incoming.size() + Social.unreadMessages);
			} else if (i == 3) {
				badge(c, bx + bs - 2, by + 2, Social.unreadNotifications);
			}
			final int idx = i;
			region(bx, by, bs, bs, () -> {
				if (idx == 0) {
					openSettings(SolClient.settings);
				} else if (idx == 1) {
					showView(V_SHOP, 5);
				} else if (idx == 2) {
					friendsOpen = !friendsOpen;
					if (!friendsOpen) {
						closeChat();
					}
				} else {
					showView(V_NOTIF, -1);
				}
			});
		}
	}

	private void showView(int v, int navIdx) {
		if (view == V_PACKS && v != V_PACKS && packs != null) {
			packs.dispose();
			packs = null;
		}
		view = v;
		nav = navIdx;
		openFolder = null;
		open = null;
		scroll = 0;
		scrollTarget = 0;
		colourOpen = null;
		listening = null;
		focus = F_NONE;
		if (v == V_PACKS) {
			if (packs == null) {
				packs = new ResourcePacksPanel();
			}
			focus = F_PACKS; // type to search right away
		} else if (v == V_MODS) {
			focus = F_SEARCH; // type to search right away
		} else if (v == V_SHOP) {
			editOpen = false; // the Locker needs the room: close the Edit panel
			Social.loadShop(Social.shopCategory);
		} else if (v == V_SKIN) {
			Social.loadCapes();
			Social.loadShop("All"); // the capes you own come from the shop list
		} else if (v == V_NOTIF) {
			Social.markNotificationsRead();
		}
	}

	// ------------------------------------------------------------ left list
	private void drawLeftList(DrawContext c, int mx, int my, int x, int y, int h, int accent) {
		Gfx.rrectBorder(c, x, y, LW, h, 12, SUB, Theme.PANEL_BORDER);
		field(c, x + 8, y + 8, LW - 16, 22, search, dev.solclient.Lang.tr("Search...", "Procurar..."), focus == F_SEARCH, accent, true);
		if (search.isEmpty() && focus != F_SEARCH) {
			// Commander shortcut hint
			String kb = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "\u2318K" : "Ctrl K";
			int kw = Gfx.twRaw(kb) + 8;
			Gfx.rrect(c, x + LW - 12 - kw, y + 13, kw, 12, 4, Theme.OFF_BG);
			Gfx.textRaw(c, kb, x + LW - 8 - kw, y + 15, Theme.DIM);
		}
		region(x + 8, y + 8, LW - 16, 22, () -> focus = F_SEARCH);
		if (!search.isEmpty()) {
			// clear (x) on the right of the search box
			int qx = x + LW - 26, qy = y + 13;
			boolean qh = in(mx, my, qx, qy, 12, 12);
			Gfx.rrect(c, qx, qy, 12, 12, 6, qh ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			drawX(c, qx + 3, qy + 3, 6, qh ? Theme.TEXT : Theme.DIM);
			region(qx, qy, 12, 12, () -> {
				search.clear();
				scroll = 0;
				scrollTarget = 0;
			});
		}

		int by = y + 38;
		int selNav = -1;
		for (int i = 0; i < NAV.length; i++) {
			if (nav == i && open == null && (view == V_MODS || i >= 5) && y + 38 + i * 30 + 26 <= y + h - 6) {
				selNav = i;
			}
		}
		// one soft highlight that glides to the selected item (no blue buttons)
		SlideBg.draw(c, "nav", x + 8, y + 38 + Math.max(0, selNav) * 30, LW - 16, 26, 9F, SlideBg.colour(), selNav >= 0);
		for (int i = 0; i < NAV.length; i++) {
			if (by + 26 > y + h - 6) {
				break;
			}
			boolean sel = i == selNav;
			boolean navHov = in(mx, my, x + 8, by, LW - 16, 26);
			float na = anim("nav" + i, navHov && !sel);
			if (na > 0.01F) {
				Gfx.rrect(c, x + 8, by, LW - 16, 26, 9, Gfx.withAlpha(0xFFFFFF, Math.round(0x0C * na)));
			}
			int navCol = sel ? 0xFFFFFFFF : Gfx.blend(Theme.DIM, Theme.TEXT, 0.55F + 0.45F * na);
			Gfx.tex(c, Theme.icon2(NAV_ICON[i]), x + 17, by + 5, 16, 16, 64, 64, navCol);
			Gfx.textMid(c, NAV[i], x + 39, by, 26, navCol);
			final int idx = i;
			region(x + 8, by, LW - 16, 26, () -> {
				if (idx == 5) {
					showView(V_SHOP, 5);
				} else if (idx == 6) {
					showView(V_SKIN, 6);
				} else if (idx == 7) {
					showView(V_PROFILES, 7);
				} else if (idx == 8) {
					Config.save();
					MinecraftClient.getInstance().setScreen(new MoveHudsScreen(true));
				} else if (idx == 9) {
					showView(V_ADDONS, 9);
				} else if (idx == 10) {
					Config.save();
					MinecraftClient.getInstance().setScreen(new MinigamesScreen(this));
				} else {
					showView(V_MODS, idx);
				}
			});
			by += 30;
		}
		// tip at the bottom: the TikTok Live relay website (click to open it). Short lines so it fits the narrow sidebar.
		if (by + 38 < y + h - 4) {
			int ty = y + h - 46;
			Gfx.fill(c, x + 12, ty - 4, x + LW - 12, ty - 3, Theme.PANEL_BORDER);
			Gfx.text(c, "You Can Use This", x + 12, ty, Theme.DIM);
			Gfx.text(c, "Website:", x + 12, ty + 10, Theme.DIM);
			boolean hov = mx >= x + 8 && mx < x + LW - 8 && my >= ty + 20 && my < ty + 42;
			int lc = hov ? accent : 0xFF7FB3F5;
			Gfx.text(c, "kubo-tiktok", x + 12, ty + 22, lc);
			Gfx.text(c, ".lovable.app", x + 12, ty + 32, lc);
			region(x + 8, ty + 20, LW - 16, 22, () -> net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create("https://kubo-tiktok.lovable.app")));
		}
	}

	/** The first option of this mod (or of the mods inside its card) whose name contains q, in the shown language. */
	private static String optionMatch(Mod m, String q) {
		if (q == null || q.isEmpty()) {
			return null;
		}
		for (Option<?> o : m.options) {
			if (o instanceof Option.Header) {
				continue;
			}
			String en = o.name == null ? "" : o.name, shown = dev.solclient.Lang.t(en);
			if (en.toLowerCase(Locale.ROOT).contains(q) || shown.toLowerCase(Locale.ROOT).contains(q)) {
				return shown;
			}
		}
		for (Mod c : SolClient.MODS) {
			if (c.parent == m) {
				String hit = c.name.toLowerCase(Locale.ROOT).contains(q) || dev.solclient.Lang.t(c.name).toLowerCase(Locale.ROOT).contains(q)
						? dev.solclient.Lang.t(c.name) : optionMatch(c, q);
				if (hit != null) {
					return hit;
				}
			}
		}
		return null;
	}

	private List<Mod> filtered() {
		List<Mod> out = new ArrayList<>();
		String q = search.text().toLowerCase(Locale.ROOT).trim();
		// inside an extension folder the category filter does not apply
		final int nav = openFolder != null ? 0 : this.nav;
		for (Mod m0 : SolClient.MODS) {
			if (m0.inSettings || m0.hiddenFromList) {
				continue;
			}
			// extension mods live in their folder (shown flat only when searching)
			String owner = dev.solclient.ext.Extensions.ownerOf(m0);
			if (openFolder != null ? !openFolder.equals(owner) : owner != null && q.isEmpty()) {
				continue;
			}
			Mod m = m0;
			if (m0.parent != null) {
				// a mod inside a card (Info HUD, Screen Tweaks...): only shows up as its card, and only when searched for
				boolean hit = !q.isEmpty() && (m0.name.toLowerCase(Locale.ROOT).contains(q) || m0.description.toLowerCase(Locale.ROOT).contains(q)
						|| dev.solclient.Lang.t(m0.name).toLowerCase(Locale.ROOT).contains(q) || optionMatch(m0, q) != null);
				// inside a group (Camera...): show the mod itself, so it can be switched off right there
				Mod shown = m0.parent instanceof dev.solclient.mods.ModGroup ? m0 : m0.parent;
				if (!hit || out.contains(shown)) {
					continue;
				}
				m = shown;
				if (nav == 4 && !m.favorite) {
					continue;
				}
				out.add(m);
				continue;
			}
			if (nav == 1 && m.category != Mod.Category.HUD) {
				continue;
			}
			if (nav == 2 && m.category != Mod.Category.UTILITY) {
				continue;
			}
			if (nav == 3 && m.category != Mod.Category.VISUAL) {
				continue;
			}
			if (nav == 4 && !m.favorite) {
				continue;
			}
			if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.description.toLowerCase(Locale.ROOT).contains(q)
					&& !dev.solclient.Lang.t(m.name).toLowerCase(Locale.ROOT).contains(q) && optionMatch(m, q) == null) {
				continue;
			}
			if (!out.contains(m)) {
				out.add(m);
			}
		}
		if (openFolder == null && nav == 0) {
			for (var fo : dev.solclient.ext.Extensions.folders()) {
				if (q.isEmpty() || fo.name.toLowerCase(Locale.ROOT).contains(q) || fo.description.toLowerCase(Locale.ROOT).contains(q)) {
					out.add(fo);
				}
			}
		}
		return out;
	}

	/** The extension folder that is open in the mod list (fabric mod id), or null. */
	private static String openFolder;

	// ---------------------------------------------------------- big panel
	// ------------------------------------------------------------ resource packs (a panel inside the menu, like the shop)
	private ResourcePacksPanel packs;
	private int packMx, packMy;
	private final ResourcePacksPanel.Host packHost = new ResourcePacksPanel.Host() {
		@Override
		public void region(int x, int y, int w, int h, Runnable click) {
			SolMenuScreen.this.region(x, y, w, h, click);
		}

		@Override
		public boolean hovered(int x, int y, int w, int h) {
			return in(packMx, packMy, x, y, w, h) && packMy >= clipY0 && packMy < clipY1;
		}

		@Override
		public float anim(String key, boolean target) {
			return SolMenuScreen.this.anim("rp" + key, target);
		}

		@Override
		public void button(DrawContext c, int x, int y, int w, int h, String label, String id, Runnable run) {
			SolMenuScreen.this.button(c, packMx, packMy, x, y, w, h, label, "rp" + id, TILE, TILE_HOVER, Theme.TEXT, run);
		}

		@Override
		public boolean searchFocused() {
			return focus == F_PACKS;
		}

		@Override
		public void focusSearch() {
			focus = F_PACKS;
		}

		@Override
		public void close() {
			showView(V_MODS, 0);
		}
	};

	private void drawPacks(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		if (packs == null) {
			packs = new ResourcePacksPanel();
		}
		packMx = mx;
		packMy = my;
		c.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		packs.draw(c, packHost, mx, my, x, y, w, h);
		c.disableScissor();
		if (packs.tooltip() != null) {
			tooltipText = packs.tooltip();
		}
	}

	private void drawCenter(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent) {
		Gfx.rrectBorder(c, x, y, w, h, 12, SUB, Theme.PANEL_BORDER);
		// corner marks: this area can be mods, shop, skin editor, anything
		c.fill(x + 6, y + 6, x + 14, y + 7, LINE);
		c.fill(x + 6, y + 6, x + 7, y + 14, LINE);
		c.fill(x + w - 14, y + h - 7, x + w - 6, y + h - 6, LINE);
		c.fill(x + w - 7, y + h - 14, x + w - 6, y + h - 6, LINE);

		int ix0 = x + 22, ix1 = x + w - 16;
		int iy0 = y + 36, iy1 = y + h - 16;
		int barX = x + 10;

		if (open != null) {
			drawSettingsHeader(c, mx, my, ix0, ix1, y + 9);
			boolean hasDesc = open.description != null && !open.description.isEmpty();
			// Watch Party has its own Language option: its whole page follows it, not the client language
			dev.solclient.Lang.override = open instanceof dev.solclient.mods.WatchPartyMod wp ? wp.langOverride() : -1;
			try {
				drawSettings(c, mx, my, ix0, ix1, iy0 + (hasDesc ? 16 : 0), iy1, barX);
			} finally {
				dev.solclient.Lang.override = -1;
			}
			return;
		}
		if (view == V_PACKS) {
			drawPacks(c, mx, my, x, y, w, h);
			return;
		}
		if (view == V_SHOP) {
			drawShop(c, mx, my, x, y, w, h, ix0, ix1, iy0, iy1, barX, accent);
			return;
		}
		if (view == V_NOTIF) {
			drawNotifications(c, mx, my, x, y, w, h, ix0, ix1, iy0, iy1, barX, accent);
			return;
		}
		if (view == V_PROFILES) {
			drawProfiles(c, mx, my, x, y, w, h, ix0, ix1, iy0, iy1, accent);
			return;
		}
		if (view == V_ADDONS) {
			drawAddons(c, mx, my, x, y, w, h, ix0, ix1, iy0, iy1, accent);
			return;
		}
		if (view == V_ME) {
			drawProfilePage(c, mx, my, x, y, w, h, ix0, ix1, iy0, iy1, accent);
			return;
		}
		if (view == V_SKIN) {
			drawCapes(c, mx, my, x, y, w, h, ix0, ix1, iy0, iy1, barX, accent);
			return;
		}

		List<Mod> list = filtered();
		// tabs: Plegumo's mods | your other mods (Sodium, Voice Chat...)
		if (openFolder == null) {
			String t1 = NAV[Math.max(0, Math.min(4, nav))] + "  (" + list.size() + ")";
			String t2 = "Mods  (" + dev.solclient.gamemods.GameMods.list().size() + ")";
			String t3 = dev.solclient.Lang.tr("Extensions", "Extensões");
			int w1 = Gfx.tw(t1) + 18, w2 = Gfx.tw(t2) + 18, w3 = Gfx.tw(t3) + 18, tby = y + 8;
			Gfx.rrect(c, ix0 - 2, tby - 2, w1 + w2 + w3 + 12, 24, 9, Theme.ROW);
			int selX = extTab ? ix0 + w1 + w2 + 8 : gameModsTab ? ix0 + w1 + 4 : ix0, selW = extTab ? w3 : gameModsTab ? w2 : w1;
			SlideBg.draw(c, "modsTab", selX, tby, selW, 20, 7F, SlideBg.colour(), true);
			Gfx.textFit(c, t1, ix0 + w1 / 2, w1 - 6, tby + 6, !gameModsTab && !extTab ? 0xFFFFFFFF : Theme.DIM);
			region(ix0, tby, w1, 20, () -> {
				gameModsTab = false;
				extTab = false;
				scrollTarget = 0;
			});
			Gfx.textFit(c, t2, ix0 + w1 + 4 + w2 / 2, w2 - 6, tby + 6, gameModsTab && !extTab ? 0xFFFFFFFF : Theme.DIM);
			region(ix0 + w1 + 4, tby, w2, 20, () -> {
				if (ModMenuLink.preferred() && ModMenuLink.open(this)) {
					return; // Settings > Menus > Mods Screen: the original Mod Menu
				}
				gameModsTab = true;
				extTab = false;
				scrollTarget = 0;
			});
			Gfx.textFit(c, t3, ix0 + w1 + w2 + 8 + w3 / 2, w3 - 6, tby + 6, extTab ? 0xFFFFFFFF : Theme.DIM);
			region(ix0 + w1 + w2 + 8, tby, w3, 20, () -> {
				extTab = true;
				scrollTarget = 0;
			});
		} else {
			extTab = false;
			gameModsTab = false;
			drawFolderHeader(c, mx, my, ix0, y + 8);
		}
		if (extTab) {
			drawExtensions(c, mx, my, ix0, ix1, iy0, iy1, barX, accent);
			return;
		}
		if (gameModsTab) {
			if (embedded != null && !embeddedAddon) {
				drawEmbedded(c, mx, my, ix0, ix1, iy0, iy1);
				return;
			}
			drawGameMods(c, mx, my, ix0, ix1, iy0, iy1, barX, accent);
			return;
		}

		// grid / list toggle (top-right of the panel)
		int tb = 20;
		int lx = ix1 - tb, gx = lx - tb - 4, ty = y + 8;
		Gfx.rrect(c, gx - 2, ty - 2, tb * 2 + 8, tb + 4, 9, Theme.ROW);
		SlideBg.draw(c, "viewToggle", gridView ? gx : lx, ty, tb, tb, 7F, SlideBg.colour(), true);
		gridIcon(c, gx + 4, ty + 4, gridView ? 0xFFFFFFFF : Theme.DIM);
		region(gx, ty, tb, tb, () -> {
			gridView = true;
			scrollTarget = 0;
		});
		listIcon(c, lx + 4, ty + 4, !gridView ? 0xFFFFFFFF : Theme.DIM);
		region(lx, ty, tb, tb, () -> {
			gridView = false;
			scrollTarget = 0;
		});

		if (list.isEmpty()) {
			Gfx.textC(c, nav == 4 ? "No favourites yet - tap the heart on a mod to like it" : "No mods found", (ix0 + ix1) / 2, (iy0 + iy1) / 2 - 4, Theme.DIM);
			if (nav != 4 && !search.text().isBlank()) {
				// maybe it's in an extension (Hypixel, stats, particles...)
				String hint = dev.solclient.Lang.tr("Look in Extensions", "Procurar nas Extensões");
				int hw = Gfx.tw(hint) + 20, hx = (ix0 + ix1) / 2 - hw / 2, hy = (iy0 + iy1) / 2 + 10;
				pill(c, mx, my, hx, hy, hw, 20, 7, "toExt", false, 0, Theme.ROW, TILE_HOVER);
				Gfx.textFit(c, hint, hx + hw / 2, hw - 6, hy + 6, Theme.TEXT);
				region(hx, hy, hw, 20, () -> {
					extTab = true;
					scrollTarget = 0;
				});
			}
			maxScroll = 0;
			return;
		}

		int gap = 8, minW = 92, cardH = 106, rowH = 30;
		int inner = ix1 - ix0;
		int cols = gridView ? Math.max(1, (inner + gap) / (minW + gap)) : 1;
		int cardW = (inner - (cols - 1) * gap) / cols;
		int itemH = gridView ? cardH : rowH;
		int rowGap = gridView ? gap : 4;
		int rows = (list.size() + cols - 1) / cols;
		int contentH = Math.max(0, rows * (itemH + rowGap) - rowGap) + 10;
		maxScroll = Math.max(0, contentH - (iy1 - iy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		clipY0 = iy0;
		clipY1 = iy1;
		c.enableScissor(ix0 - 4, iy0, ix1 + 4, iy1);
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols, row = i / cols;
			int cxx = ix0 + col * (cardW + gap);
			int cyy = iy0 + row * (itemH + rowGap) - Math.round(scroll);
			if (cyy + itemH < iy0 || cyy > iy1) {
				continue;
			}
			if (gridView) {
				drawCard(c, mx, my, list.get(i), cxx, cyy, cardW, itemH, iy0, iy1, accent);
			} else {
				drawRow(c, mx, my, list.get(i), cxx, cyy, cardW, itemH, iy0, iy1, accent);
			}
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, barX, iy0, iy1, contentH);
	}

	private static boolean gameModsTab, extTab;

	/** Opens the menu on Mods > Extensions (the store). */
	public static SolMenuScreen openExtensions() {
		extTab = true;
		return open(VIEW_MODS);
	}

	private static final java.util.Map<String, String> EXT_ICONS = java.util.Map.ofEntries(java.util.Map.entry("hypixel", "hypixel_util"),
			java.util.Map.entry("stats", "session_stats"), java.util.Map.entry("particles", "ext_particles"), java.util.Map.entry("streamer", "streamer_mode"),
			java.util.Map.entry("better-f3", "better_f3"), java.util.Map.entry("fun", "ext_fun"), java.util.Map.entry("far-horizons", "far_horizons"),
			java.util.Map.entry("building", "kubo_builder"), java.util.Map.entry("friends", "party_hud"), java.util.Map.entry("water-effects", "water_effects"),
			java.util.Map.entry("pvp-extras", "ext_pvp_extras"), java.util.Map.entry("hud-extras", "ext_hud_extras"), java.util.Map.entry("combat", "damage_numbers"),
			java.util.Map.entry("utilities", "inventory_tweaks"), java.util.Map.entry("chat-translator", "chat_translator"),
			java.util.Map.entry("practice", "ext_practice"));

	/** One extension as a card, in the same look as the mod cards. Click the strip: install / uninstall / update. */
	private void drawExtCard(DrawContext c, int mx, int my, dev.solclient.ext.Extensions.Item it, int x, int y, int w, int h, int cy0, int cy1,
			int accent) {
		boolean inView = my >= cy0 && my < cy1;
		boolean hov = in(mx, my, x, y, w, h) && inView;
		float a = anim("ext" + it.id, hov);
		var st = dev.solclient.ext.Extensions.state(it);
		boolean have = st == dev.solclient.ext.Extensions.State.INSTALLED || st == dev.solclient.ext.Extensions.State.UPDATE;
		Gfx.rrectBorder(c, x, y, w, h, 10, Gfx.blend(TILE, TILE_HOVER, a), have ? Gfx.withAlpha(Theme.GREEN, 0x70)
				: Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		// the same white glyph icons as the mod cards (the store's own picture for extensions Caos doesn't know yet)
		int is = 34, icx = x + w / 2 - is / 2, icy = y + 19;
		String local = EXT_ICONS.get(it.id);
		RemoteTextures.Tex t = local != null || it.icon.isEmpty() ? null : RemoteTextures.get(it.icon);
		if (local != null) {
			Gfx.tex(c, Theme.icon2(local), icx, icy, is, is, 64, 64, have ? 0xFFFFFFFF : 0xFFB6BAC8);
		} else if (t != null) {
			Gfx.tex(c, t.id(), icx, icy, is, is, t.width(), t.height(), have ? 0xFFFFFFFF : 0xE0FFFFFF);
		} else {
			Gfx.rrect(c, icx, icy, is, is, 8, 0xFF2C2F38);
			Gfx.textFit(c, it.name.isEmpty() ? "?" : it.name.substring(0, 1).toUpperCase(Locale.ROOT), icx + is / 2, is - 6, icy + 13, Theme.TEXT);
		}
		Gfx.textFit(c, fit(it.name, w - 10), x + w / 2, w - 6, y + h - 50, have ? Theme.TEXT : 0xFFB6BAC8);
		String count = it.mods.isEmpty() ? it.version : it.mods.size() + (it.mods.size() == 1 ? " mod" : " mods");
		Gfx.textRaw(c, fit(count, w - 12), x + w / 2 - Math.min(Gfx.twRaw(count), w - 12) / 2, y + h - 39, 0xFF6B7080);
		if (hov) {
			tooltipText = it.text() + (it.mods.isEmpty() ? "" : "  -  " + String.join(", ", it.mods));
		}
		// status strip
		int fh = 22, by = y + h - fh - 4, bx = x + 4, bw = w - 8;
		boolean fhov = in(mx, my, bx, by, bw, fh) && inView;
		float fa = anim("extf" + it.id, fhov);
		String word;
		int bg, fg;
		Runnable act = null;
		switch (st) {
			case DOWNLOADING -> {
				word = Math.round(dev.solclient.ext.Extensions.progress * 100) + "%";
				bg = Theme.OFF_BG;
				fg = Theme.TEXT;
			}
			case INSTALLED -> {
				// installed: green; under the mouse it offers to uninstall
				boolean locked = dev.solclient.ext.Extensions.isBuiltin(it);
				word = locked ? dev.solclient.Lang.tr("Built in", "Incluída") : fhov ? dev.solclient.Lang.tr("Uninstall", "Desinstalar") : dev.solclient.Lang.tr("Installed", "Instalada");
				bg = fhov && !locked ? 0xFF3A1E22 : Theme.GREEN_BG;
				fg = fhov && !locked ? 0xFFE5767A : Theme.GREEN;
				act = locked ? null : () -> dev.solclient.ext.Extensions.remove(it);
			}
			case UPDATE -> {
				word = dev.solclient.Lang.tr("Update", "Atualizar");
				bg = Gfx.blend(accent, 0xFF000000, 0.35F - 0.15F * fa);
				fg = 0xFFFFFFFF;
				act = () -> dev.solclient.ext.Extensions.install(it);
			}
			case RESTART_TO_ADD, RESTART_TO_REMOVE -> {
				word = dev.solclient.Lang.tr("Restart", "Reiniciar");
				bg = Theme.OFF_BG;
				fg = 0xFFFFD27A;
			}
			default -> {
				word = dev.solclient.Lang.tr("Install", "Instalar");
				bg = Gfx.blend(accent, 0xFF000000, 0.35F - 0.15F * fa);
				fg = 0xFFFFFFFF;
				act = () -> dev.solclient.ext.Extensions.install(it);
			}
		}
		Gfx.rrect(c, bx, by, bw, fh, 6, bg);
		if (st == dev.solclient.ext.Extensions.State.DOWNLOADING) {
			Gfx.rrect(c, bx, by, Math.max(6, Math.round(bw * dev.solclient.ext.Extensions.progress)), fh, 6, Gfx.withAlpha(accent, 0xB0));
		}
		Gfx.textFit(c, word, x + w / 2, w - 16, by + (fh - 9) / 2, fg);
		if (act != null && inView) {
			region(bx, by, bw, fh, act);
		}
	}

	/**
	 * Extensions: the store. Search box filters by name, description, the mods inside and tags. Each card: icon, name,
	 * what's inside, and one button (Get / downloading % / Restart to add / Installed - Remove / Update).
	 */
	private void drawExtensions(DrawContext c, int mx, int my, int ix0, int ix1, int iy0, int iy1, int barX, int accent) {
		String q = search.text();
		List<dev.solclient.ext.Extensions.Item> list = new ArrayList<>();
		for (var it : dev.solclient.ext.Extensions.catalog()) {
			if (it.matches(q)) {
				list.add(it);
			}
		}
		// status line (top) and the restart notice
		String msg = dev.solclient.ext.Extensions.status;
		boolean restart = dev.solclient.ext.Extensions.restartNeeded();
		int top = iy0;
		if (restart || (msg != null && !msg.isEmpty())) {
			String line = restart ? dev.solclient.Lang.tr("Restart Minecraft to finish adding / removing extensions", "Reinicia o Minecraft para acabar de adicionar / remover extensões")
					: dev.solclient.Lang.t(msg);
			Gfx.rrect(c, ix0, iy0, ix1 - ix0, 18, 6, restart ? 0x30FFD27A : Theme.ROW);
			Gfx.textC(c, fit(line, ix1 - ix0 - 12), (ix0 + ix1) / 2, iy0 + 5, restart ? 0xFFFFD27A : Theme.DIM);
			top = iy0 + 24;
		}
		if (list.isEmpty()) {
			String empty = dev.solclient.ext.Extensions.fetching() ? dev.solclient.Lang.tr("Loading...", "A carregar...")
					: !q.isBlank() ? dev.solclient.Lang.tr("Nothing found", "Nada encontrado") : dev.solclient.Lang.tr("No extensions yet", "Ainda não há extensões");
			Gfx.textC(c, empty, (ix0 + ix1) / 2, (top + iy1) / 2 - 4, Theme.DIM);
			if (!dev.solclient.ext.Extensions.fetching()) {
				String r = dev.solclient.Lang.tr("Try again", "Tentar outra vez");
				int rw = Gfx.tw(r) + 20, rx = (ix0 + ix1) / 2 - rw / 2, ry = (top + iy1) / 2 + 10;
				pill(c, mx, my, rx, ry, rw, 20, 7, "extRetry", false, 0, Theme.ROW, TILE_HOVER);
				Gfx.textFit(c, r, rx + rw / 2, rw - 6, ry + 6, Theme.TEXT);
				region(rx, ry, rw, 20, dev.solclient.ext.Extensions::refresh);
			}
			maxScroll = 0;
			return;
		}
		// cards like the mod cards: icon, name, how many mods inside, and a status strip at the bottom
		int gap = 8, minW = 92, cardH = 106;
		int inner = ix1 - ix0;
		int cols = Math.max(1, (inner + gap) / (minW + gap));
		int cardW = (inner - (cols - 1) * gap) / cols;
		int rows = (list.size() + cols - 1) / cols;
		int contentH = Math.max(0, rows * (cardH + gap) - gap) + 10;
		maxScroll = Math.max(0, contentH - (iy1 - top));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		clipY0 = top;
		clipY1 = iy1;
		c.enableScissor(ix0 - 4, top, ix1 + 4, iy1);
		for (int i = 0; i < list.size(); i++) {
			var it = list.get(i);
			int x = ix0 + (i % cols) * (cardW + gap);
			int yy = top + (i / cols) * (cardH + gap) - Math.round(scroll);
			if (yy + cardH < top || yy > iy1) {
				continue;
			}
			drawExtCard(c, mx, my, it, x, yy, cardW, cardH, top, iy1, accent);
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, barX, top, iy1, contentH);
	}

	/** Your other mods (Mod Menu style): icon, name, version and a Settings button when the mod has a settings screen. */
	// ------------------------------------------------------------ another mod's settings screen, shown inside this window
	private net.minecraft.client.gui.screen.Screen embedded;
	private String embeddedName = "";
	private int eX, eY, eW, eH, eInitW, eInitH;

	/** True when the embedded screen is an add-on (shown in Add-ons) and not a mod's settings (shown in Mods). */
	private boolean embeddedAddon;

	private boolean embedOn() {
		return embedded != null && (embeddedAddon ? view == V_ADDONS && open == null : gameModsTab);
	}

	private void openAddon(dev.solclient.addons.Addon a) {
		net.minecraft.client.gui.screen.Screen s = null;
		try {
			s = a.embed(backProxy());
		} catch (Throwable t) {
			System.err.println("[Caos] add-on " + a.id() + ": " + t);
		}
		if (s == null) {
			a.open(this);
			return;
		}
		closeEmbedded();
		embedded = s;
		embeddedAddon = true;
		embeddedName = a.name();
		eInitW = -1;
		scroll = 0;
		scrollTarget = 0;
	}

	/** A screen that, when something switches to it ("Done" / "Back"), closes the embedded screen instead. */
	private net.minecraft.client.gui.screen.Screen backProxy() {
		return new net.minecraft.client.gui.screen.Screen(Text.empty()) {
			@Override
			protected void init() {
				// the settings screen asked to go back (Done / Cancel): close it inside our window
				MinecraftClient mc = MinecraftClient.getInstance();
				mc.execute(() -> {
					closeEmbedded();
					if (mc.currentScreen != SolMenuScreen.this) {
						mc.setScreen(SolMenuScreen.this);
					}
				});
			}
		};
	}

	private boolean openEmbedded(String modId, String name) {
		var s = dev.solclient.gamemods.GameMods.createConfig(modId, backProxy());
		if (s == null) {
			return false;
		}
		closeEmbedded();
		embeddedAddon = false;
		embedded = s;
		embeddedName = name;
		eInitW = -1;
		scroll = 0;
		scrollTarget = 0;
		return true;
	}

	private void closeEmbedded() {
		var s = embedded;
		embedded = null;
		if (s != null) {
			try {
				s.removed();
			} catch (Throwable ignored) {
				// the other screen is gone anyway
			}
		}
	}

	private void drawEmbedded(DrawContext c, int mx, int my, int ix0, int ix1, int iy0, int iy1) {
		int top = iy0 + 26;
		pill(c, mx, my, ix0, iy0, 70, 20, 7, "emBack", false, 0, TILE_HOVER, Theme.CARD_HOVER);
		Gfx.text(c, dev.solclient.Lang.tr("< Back", "< Voltar"), ix0 + 14, iy0 + 6, Theme.TEXT);
		region(ix0, iy0, 70, 20, this::closeEmbedded);
		Gfx.text(c, embeddedName, ix0 + 82, iy0 + 6, Theme.DIM);
		eX = ix0;
		eY = top;
		eW = ix1 - ix0;
		eH = iy1 - top;
		// another mod's settings: drawn at the game's GUI scale in its own dark box; a Caos add-on: drawn in the menu's
		// own units (same size as everything around it), straight on the panel
		float es = embScale();
		if (!embeddedAddon) {
			Gfx.rrectBorder(c, eX - 2, eY - 2, eW + 4, eH + 4, 8, 0xFF0C0D10, Theme.CARD_BORDER);
		}
		int gw = Math.max(200, Math.round(eW * es)), gh = Math.max(120, Math.round(eH * es));
		var mc = MinecraftClient.getInstance();
		if (eInitW != gw || eInitH != gh) {
			eInitW = gw;
			eInitH = gh;
			embedded.init(gw, gh);
		}
		float gmx = (mx - eX) * es, gmy = (my - eY) * es;
		float oldOverride = Gfx.scaleOverride;
		if (!embeddedAddon) {
			Gfx.scaleOverride = 0F;
		}
		c.enableScissor(eX, eY, eX + eW, eY + eH);
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(eX, eY);
		c.getMatrices().scale(1F / es, 1F / es);
		dev.solclient.gui.EmbedFlag.rendering = true;
		try {
			embedded.render(c, Math.round(gmx), Math.round(gmy), 0F);
		} catch (Throwable t) {
			System.err.println("[Caos] embedded settings: " + t);
		} finally {
			dev.solclient.gui.EmbedFlag.rendering = false;
			c.getMatrices().popMatrix();
			c.disableScissor();
			Gfx.scaleOverride = oldOverride;
		}
	}

	private boolean inEmbedded(double vx, double vy) {
		return embedOn() && vx >= eX && vx < eX + eW && vy >= eY && vy < eY + eH;
	}

	/** Embedded-screen units per menu unit: GUI units for other mods' settings, menu units for Caos add-ons. */
	private float embScale() {
		return embeddedAddon ? 1F : u;
	}

	private double embX(double vx) {
		return (vx - eX) * embScale();
	}

	private double embY(double vy) {
		return (vy - eY) * embScale();
	}

	@Override
	public void tick() {
		super.tick();
		if (embedded != null) {
			try {
				embedded.tick();
			} catch (Throwable ignored) {
				// ignore
			}
		}
	}

	@Override
	public void mouseMoved(double x, double y) {
		if (inEmbedded(x * f, y * f)) {
			embedded.mouseMoved(embX(x * f), embY(y * f));
		}
	}

	private void drawGameMods(DrawContext c, int mx, int my, int ix0, int ix1, int iy0, int iy1, int barX, int accent) {
		String q = search.text().toLowerCase(Locale.ROOT).trim();
		List<dev.solclient.gamemods.GameMods.Entry> list = new ArrayList<>();
		for (var e : dev.solclient.gamemods.GameMods.list()) {
			if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q) || e.id.contains(q)) {
				list.add(e);
			}
		}
		String msg = dev.solclient.gamemods.GameMods.message;
		int bottom = msg != null ? iy1 - 16 : iy1;
		if (msg != null) {
			Gfx.textC(c, fit(msg, ix1 - ix0), (ix0 + ix1) / 2, iy1 - 11, 0xFFFFD27A);
		}
		if (list.isEmpty()) {
			Gfx.textC(c, q.isEmpty() ? "No other mods installed" : "No mods found", (ix0 + ix1) / 2, (iy0 + bottom) / 2 - 4, Theme.DIM);
			maxScroll = 0;
			return;
		}
		int gap = 8, minW = 92, cardH = 106;
		int inner = ix1 - ix0;
		int cols = Math.max(1, (inner + gap) / (minW + gap));
		int cardW = (inner - (cols - 1) * gap) / cols;
		int rows = (list.size() + cols - 1) / cols;
		int contentH = Math.max(0, rows * (cardH + gap) - gap) + 10;
		maxScroll = Math.max(0, contentH - (bottom - iy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		clipY0 = iy0;
		clipY1 = bottom;
		c.enableScissor(ix0 - 4, iy0, ix1 + 4, bottom);
		for (int i = 0; i < list.size(); i++) {
			var e = list.get(i);
			int x = ix0 + (i % cols) * (cardW + gap);
			int yy = iy0 + (i / cols) * (cardH + gap) - Math.round(scroll);
			if (yy + cardH < iy0 || yy > bottom) {
				continue;
			}
			boolean hov = in(mx, my, x, yy, cardW, cardH) && my >= iy0 && my < bottom;
			Gfx.rrectBorder(c, x, yy, cardW, cardH, 10, hov ? TILE_HOVER : TILE, Theme.CARD_BORDER);
			RemoteTextures.Tex t = dev.solclient.social.RemoteTextures.local(e.id + "/" + e.jar.getFileName(), () -> dev.solclient.gamemods.GameMods.icon(e));
			int is = 32, icx = x + cardW / 2 - is / 2, icy = yy + 14;
			if (t != null) {
				Gfx.tex(c, t.id(), icx, icy, is, is, t.width(), t.height(), e.loaded ? 0xFFFFFFFF : 0x88FFFFFF);
			} else {
				Gfx.rrect(c, icx, icy, is, is, 6, 0xFF2C2F38);
				Gfx.textFit(c, e.name.substring(0, 1).toUpperCase(Locale.ROOT), icx + is / 2, is - 6, icy + 12, Theme.TEXT);
			}
			Gfx.textFit(c, fit(e.name, cardW - 10), x + cardW / 2, cardW - 6, yy + 54, e.loaded ? 0xFFFFFFFF : Theme.DIM);
			// like Mod Menu: no on / off. A mod that gives Mod Menu a settings screen gets a Settings button that opens it
			int bx = x + 6, by = yy + cardH - 28, bw = cardW - 12;
			if (!e.loaded) {
				// turned off by an older version of the client: one button to bring it back (loads on the next start)
				Gfx.textFit(c, fit(dev.solclient.Lang.tr("Restart to apply", "Reinicia para aplicar"), cardW - 10), x + cardW / 2, cardW - 6, yy + 65, 0xFFFFD27A);
				pill(c, mx, my, bx, by, bw, 20, 7, "gm" + e.id, false, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
				Gfx.textFit(c, dev.solclient.Lang.tr("Re-enable", "Reativar"), bx + bw / 2, bw - 6, by + 6, Theme.TEXT);
				region(bx, by, bw, 20, () -> dev.solclient.gamemods.GameMods.set(e, true));
			} else {
				Gfx.textFit(c, fit(e.version, cardW - 10), x + cardW / 2, cardW - 6, yy + 65, Theme.DIM);
				if (dev.solclient.gamemods.GameMods.hasConfig(e.id)) {
					pill(c, mx, my, bx, by, bw, 20, 7, "gm" + e.id, false, 0, TILE_HOVER, Theme.CARD_HOVER);
					int sw = Gfx.tw(dev.solclient.Lang.tr("Settings", "Definições")) + 18;
					int sx0 = bx + (bw - sw) / 2;
					Gfx.tex(c, Theme.icon2("settings_small"), sx0, by + 4, 12, 12, 64, 64, Theme.TEXT);
					Gfx.text(c, dev.solclient.Lang.tr("Settings", "Definições"), sx0 + 17, by + 6, Theme.TEXT);
					region(bx, by, bw, 20, () -> {
						if (!openEmbedded(e.id, e.name)) {
							dev.solclient.gamemods.GameMods.message = e.name + dev.solclient.Lang.tr(" has no settings screen", " não tem ecrã de definições");
						}
					});
				} else {
					Gfx.textFit(c, dev.solclient.Lang.tr("No settings", "Sem definições"), x + cardW / 2, cardW - 6, by + 6, 0xFF5A5E6E);
				}
			}
			if (hov) {
				String d = e.description == null || e.description.isBlank() ? e.name : e.description;
				tooltipText = e.name + " " + e.version + " - " + (d.length() > 90 ? d.substring(0, 90) + "..." : d);
			}
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, barX, iy0, bottom, contentH);
	}

	private void drawScrollbar(DrawContext c, int x, int y0, int y1, int contentH) {
		if (maxScroll <= 0) {
			return;
		}
		ScrollBar.draw(c, x - 2, y0, y1, contentH, scroll, maxScroll);
	}


	// ------------------------------------------------------------ extension folders
	private static List<Mod> folderMods(String modId) {
		List<Mod> out = new ArrayList<>();
		for (Mod m : dev.solclient.ext.Extensions.modsOf(modId)) {
			if (m.parent == null && !m.inSettings) {
				out.add(m);
			}
		}
		return out;
	}

	private static void openFolderNow(String modId) {
		openFolder = modId;
		extTab = false;
		gameModsTab = false;
	}

	/** Top row inside an open folder: a back button and the folder's name. */
	private void drawFolderHeader(DrawContext c, int mx, int my, int x, int y) {
		var it = dev.solclient.ext.Extensions.byModId(openFolder);
		String name = it == null ? "" : it.name;
		String back = "<  " + dev.solclient.Lang.tr("Back", "Voltar");
		int bw = Gfx.tw(back) + 22;
		pill(c, mx, my, x, y, bw, 22, 8, "folderBack", false, 0, Theme.ROW, TILE_HOVER);
		Gfx.textFit(c, back, x + bw / 2, bw - 6, y + 7, Theme.TEXT);
		region(x, y, bw, 22, () -> {
			openFolder = null;
			scrollTarget = 0;
		});
		int n = folderMods(openFolder).size();
		String t = name + "  -  " + n + (n == 1 ? " mod" : " mods");
		Gfx.textRaw(c, t, x + bw + 12, y + 7, Theme.TEXT);
	}

	private void drawFolderCard(DrawContext c, int mx, int my, dev.solclient.mods.ExtFolder f, int x, int y, int w, int h, int cy0, int cy1, int accent) {
		boolean hov = in(mx, my, x, y, w, h) && my >= cy0 && my < cy1;
		float a = anim("card" + f.id, hov);
		Gfx.rrectBorder(c, x, y, w, h, 10, Gfx.blend(TILE, TILE_HOVER, a), Gfx.blend(Gfx.withAlpha(accent, 0x90), accent, a));
		region(x, y, w, h, () -> openFolderNow(f.modId));
		// little folder glyph (top-left)
		Gfx.tex(c, Theme.icon2("folder"), x + 8, y + 7, 15, 15, 64, 64, hov ? 0xFFFFFFFF : Theme.DIM);
		String local = EXT_ICONS.get(f.extId);
		if (local != null) {
			Gfx.tex(c, Theme.icon2(local), Math.round(x + w / 2F - 17), y + 21, 34, 34, 64, 64, 0xFFFFFFFF);
		} else {
			Gfx.rrect(c, x + w / 2 - 17, y + 21, 34, 34, 8, 0xFF2C2F38);
			Gfx.textC(c, f.name.substring(0, 1).toUpperCase(Locale.ROOT), x + w / 2, y + 34, Theme.TEXT);
		}
		Gfx.textFit(c, fit(f.name, w - 10), x + w / 2, w - 6, y + h - 50, Theme.TEXT);
		List<Mod> in = folderMods(f.modId);
		int on = 0;
		for (Mod m : in) {
			if (m.enabled) {
				on++;
			}
		}
		String count = in.size() + (in.size() == 1 ? " mod" : " mods") + "  -  " + on + " " + dev.solclient.Lang.tr("on", "ligados");
		Gfx.textRaw(c, fit(count, w - 12), x + w / 2 - Math.min(Gfx.twRaw(count), w - 12) / 2, y + h - 39, 0xFF6B7080);
		int fh = 22, by = y + h - fh - 4, bx = x + 4, bw = w - 8;
		boolean fhov = in(mx, my, bx, by, bw, fh) && my >= cy0 && my < cy1;
		float fa = anim("ft" + f.id, fhov);
		Gfx.rrect(c, bx, by, bw, fh, 6, Gfx.blend(Gfx.blend(accent, 0xFF000000, 0.6F), Gfx.blend(accent, 0xFF000000, 0.4F), fa));
		Gfx.textFit(c, dev.solclient.Lang.tr("Open", "Abrir"), x + w / 2, w - 16, by + (fh - 9) / 2, 0xFFFFFFFF);
		if (hov) {
			tooltipText = f.description;
		}
	}

	private void drawFolderRow(DrawContext c, int mx, int my, dev.solclient.mods.ExtFolder f, int x, int y, int w, int h, int cy0, int cy1, int accent) {
		boolean hov = in(mx, my, x, y, w, h) && my >= cy0 && my < cy1;
		float a = anim("row" + f.id, hov);
		Gfx.rrectBorder(c, x, y, w, h, 9, Gfx.blend(TILE, TILE_HOVER, a), Gfx.blend(Gfx.withAlpha(accent, 0x90), accent, a));
		region(x, y, w, h, () -> openFolderNow(f.modId));
		String local = EXT_ICONS.get(f.extId);
		if (local != null) {
			Gfx.tex(c, Theme.icon2(local), x + 8, y + h / 2 - 10, 20, 20, 64, 64, 0xFFFFFFFF);
		}
		Gfx.textMid(c, f.name, x + 36, y, h, Theme.TEXT);
		int n = folderMods(f.modId).size();
		String t = n + (n == 1 ? " mod" : " mods");
		Gfx.textRaw(c, t, x + w - 8 - Gfx.twRaw(t), y + h / 2 - 4, 0xFF6B7080);
	}

	private void drawCard(DrawContext c, int mx, int my, Mod m, int x, int y, int w, int h, int cy0, int cy1, int accent) {
		if (m instanceof dev.solclient.mods.ExtFolder fo) {
			drawFolderCard(c, mx, my, fo, x, y, w, h, cy0, cy1, accent);
			return;
		}
		boolean hov = in(mx, my, x, y, w, h) && my >= cy0 && my < cy1;
		float a = anim("card" + m.id, hov);
		boolean sel = selected == m;
		Gfx.rrectBorder(c, x, y, w, h, 10, Gfx.blend(TILE, TILE_HOVER, a), sel ? accent : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));

		// body: click selects (shows it in the edit panel), right click opens the settings
		region(x, y, w, h, b -> {
			if (b == 1) {
				openSettings(m);
			} else {
				selected = m;
			}
		}, null);

		// settings gear (top-right): opens this mod's settings
		int sx = x + w - 15, sy = y + 13;
		boolean hs = in(mx, my, sx - 8, sy - 8, 16, 16) && my >= cy0 && my < cy1;
		Gfx.texSpin(c, Theme.icon2("settings_small"), sx - 6, sy - 6, 12, 12, 64, 64, hs ? accent : (sel ? 0xFFFFFFFF : Theme.DIM), "gear:" + sx + ":" + sy, hs);
		region(sx - 9, sy - 9, 18, 18, () -> openSettings(m));

		// like / favourite heart (top-left): click to add it to Favourites
		if (!m.locked) {
			heartButton(c, mx, my, m, x + 15, y + 13, cy0, cy1, accent);
		}

		// searching: which setting inside matched (e.g. "Fire Height"), shown as a small chip under the name
		String sq = search.text().toLowerCase(Locale.ROOT).trim();
		String inside = null;
		if (!sq.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(sq) && !dev.solclient.Lang.t(m.name).toLowerCase(Locale.ROOT).contains(sq)) {
			inside = optionMatch(m, sq);
		}
		if (inside == null) {
			modIcon(c, m, x + w / 2F, y + 37, 40, m.enabled || m.locked);
			Gfx.textFit(c, fit(m.name, w - 10), x + w / 2, w - 6, y + h - 44, m.enabled || m.locked ? Theme.TEXT : 0xFFB6BAC8);
		} else {
			modIcon(c, m, x + w / 2F, y + 32, 28, m.enabled || m.locked);
			Gfx.textFit(c, fit(m.name, w - 10), x + w / 2, w - 6, y + h - 54, m.enabled || m.locked ? Theme.TEXT : 0xFFB6BAC8);
			String t = fit(inside, w - 26);
			int tw = Gfx.tw(t) + 14, tx = x + (w - tw) / 2, ty = y + h - 42;
			Gfx.rrect(c, tx, ty, tw, 12, 5, Gfx.withAlpha(accent, 0x2E));
			Gfx.textMid(c, t, tx + 7, ty, 12, Gfx.blend(accent | 0xFF000000, 0xFFFFFFFF, 0.6F));
		}

		// footer: a status strip across the bottom of the card (click = on / off)
		int fh = 22, by = y + h - fh - 4, bx = x + 4, bw = w - 8;
		boolean fhov = in(mx, my, bx, by, bw, fh) && my >= cy0 && my < cy1;
		float fa = anim("ft" + m.id, fhov);
		if (m.locked) {
			Gfx.rrect(c, bx, by, bw, fh, 6, Gfx.blend(Theme.OFF_BG, Theme.OFF_BG_HOVER, fa));
			String lbl = m instanceof dev.solclient.mods.ModGroup g ? g.onCount() + " / " + g.children().size() + " " + dev.solclient.Lang.tr("on", "ligados")
					: dev.solclient.Lang.t("Settings");
			Gfx.textFit(c, lbl, x + w / 2, w - 16, by + (fh - 9) / 2, Theme.TEXT); // same font as the rest of the client
			region(bx, by, bw, fh, () -> openSettings(m));
			return;
		}
		boolean on = m.enabled;
		int bg = on ? Gfx.blend(Theme.GREEN_BG, Theme.GREEN_BG_HOVER, fa) : Gfx.blend(0xFF2A1A1D, 0xFF3A1E22, fa);
		Gfx.rrect(c, bx, by, bw, fh, 6, bg);
		int fg = on ? Theme.GREEN : 0xFFE5767A;
		String word = on ? dev.solclient.Lang.t("Enabled") : dev.solclient.Lang.t("Disabled");
		Gfx.textFit(c, word, x + w / 2, w - 16, by + (fh - 9) / 2, fg); // same font as the rest of the client
		region(bx, by, bw, fh, m::toggle);
	}

	/** Small text with extra space between the letters (section titles, card footers). */
	private static void spaced(DrawContext c, String s, int x, int y, float scale, float gap, int col) {
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(x, y);
		c.getMatrices().scale(scale, scale);
		float cx = 0;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			Gfx.text(c, ch, Math.round(cx), 0, col);
			cx += Gfx.tw(ch) + gap / scale;
		}
		c.getMatrices().popMatrix();
	}

	private static float spacedW(String s, float scale, float gap) {
		float w = 0;
		for (int i = 0; i < s.length(); i++) {
			w += Gfx.tw(String.valueOf(s.charAt(i))) * scale + gap;
		}
		return Math.max(0, w - gap);
	}

	private static void spacedC(DrawContext c, String s, int cx, int y, float scale, float gap, int col) {
		spaced(c, s, Math.round(cx - spacedW(s, scale, gap) / 2F), y, scale, gap, col);
	}

	/** Same, but it never gets wider than maxW: first less space between the letters, then smaller letters. */
	private static void spacedC(DrawContext c, String s, int cx, int y, float scale, float gap, int col, int maxW) {
		float w = spacedW(s, scale, gap);
		if (w > maxW) {
			gap = 0.4F;
			w = spacedW(s, scale, gap);
		}
		if (w > maxW) {
			float k = Math.max(0.6F, maxW / w);
			scale *= k;
			gap *= k;
			w = spacedW(s, scale, gap);
		}
		int yy = y + Math.round((0.7F - scale) * 4.5F / 0.7F);
		spaced(c, s, Math.round(cx - w / 2F), yy, scale, gap, col);
	}

	private void drawRow(DrawContext c, int mx, int my, Mod m, int x, int y, int w, int h, int cy0, int cy1, int accent) {
		if (m instanceof dev.solclient.mods.ExtFolder fo) {
			drawFolderRow(c, mx, my, fo, x, y, w, h, cy0, cy1, accent);
			return;
		}
		boolean hov = in(mx, my, x, y, w, h) && my >= cy0 && my < cy1;
		float a = anim("row" + m.id, hov);
		boolean sel = selected == m;
		Gfx.rrectBorder(c, x, y, w, h, 9, Gfx.blend(TILE, TILE_HOVER, a), sel ? accent : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		region(x, y, w, h, b -> {
			if (b == 1) {
				openSettings(m);
			} else {
				selected = m;
			}
		}, null);
		modIcon(c, m, x + 18, y + h / 2F, 20, m.enabled || m.locked);
		Gfx.textMid(c, m.name, x + 36, y, h, m.enabled || m.locked ? Theme.TEXT : 0xFFB6BAC8);

		int pwid = 48, px = x + w - 8 - pwid, py = y + 5;
		if (m.locked) {
			pwid = 70;
			px = x + w - 8 - pwid;
			pill(c, mx, my, px, py, pwid, 20, 7, "lk" + m.id, false, 0, accent, Theme.accentHover());
			Gfx.textFit(c, "SETTINGS", px + pwid / 2, pwid - 6, py + 5, 0xFFFFFFFF);
			final int fpx = px, fpw = pwid, fpy = py;
			region(fpx, fpy, fpw, 20, () -> openSettings(m));
		} else {
			if (m.enabled) {
				pill(c, mx, my, px, py, pwid, 20, 7, "t" + m.id, false, 0, Theme.GREEN_BG, Theme.GREEN_BG_HOVER);
				Gfx.textFit(c, "ON", px + pwid / 2, pwid - 6, py + 5, Theme.GREEN);
			} else {
				pill(c, mx, my, px, py, pwid, 20, 7, "t" + m.id, false, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
				Gfx.textFit(c, "OFF", px + pwid / 2, pwid - 6, py + 5, Theme.DIM);
			}
			region(px, py, pwid, 20, m::toggle);
		}
		int sx = px - 16, sy = y + h / 2;
		boolean hs = in(mx, my, sx - 8, sy - 8, 16, 16) && my >= cy0 && my < cy1;
		Gfx.texSpin(c, Theme.icon2("settings_small"), sx - 6, sy - 6, 12, 12, 64, 64, hs ? accent : Theme.DIM, "gear:" + sx + ":" + sy, hs);
		region(sx - 9, sy - 9, 18, 18, () -> openSettings(m));
		if (!m.locked) {
			heartButton(c, mx, my, m, sx - 20, sy, cy0, cy1, accent);
		}
	}

	/** Heart that likes a mod (adds it to Favourites) with a little pop, saved straight away. */
	private void heartButton(DrawContext c, int mx, int my, Mod m, int hx, int hy, int cy0, int cy1, int accent) {
		boolean hh = in(mx, my, hx - 8, hy - 8, 16, 16) && my >= cy0 && my < cy1;
		float pop = anim("fav" + m.id, m.favorite);
		int size = Math.round(12 + (m.favorite ? (float) Math.sin(Math.PI * pop) * 4 : 0));
		int col = m.favorite ? 0xFFFF4D6D : hh ? 0xFFFFFFFF : 0x66FFFFFF;
		Gfx.tex(c, Theme.icon2("favourite"), hx - size / 2, hy - size / 2, size, size, 64, 64, col);
		if (hh) {
			tooltipText = m.favorite ? "Remove from favourites" : "Like (add to favourites)";
		}
		region(hx - 9, hy - 9, 18, 18, () -> toggleFavourite(m));
	}

	private static void toggleFavourite(Mod m) {
		m.favorite = !m.favorite;
		Config.save();
	}

	/** Opens straight on one mod's settings (gear button in the HUD editor). */
	public void openSettingsFor(Mod m) {
		openSettings(m);
	}

	private void openSettings(Mod m) {
		open = m;
		scroll = 0;
		scrollTarget = 0;
		colourOpen = null;
		listening = null;
		focus = F_NONE;
	}

	// ------------------------------------------------------------- settings
	private void drawSettingsHeader(DrawContext c, int mx, int my, int cx0, int cx1, int y) {
		String backLbl = dev.solclient.Lang.tr("Back", "Voltar");
		int bw = Gfx.tw(backLbl) + 30, bh = 18;
		y += 2; // the button is smaller than the 22 px title row: keep it centred
		boolean bhov = in(mx, my, cx0, y, bw, bh) && my >= clipY0 && my < clipY1;
		float ba = anim("backBtn", bhov);
		Gfx.lightButton(c, cx0, y, bw, bh, ba, false);
		Gfx.tex(c, Theme.icon2("back"), cx0 + 6, y + 3, 12, 12, 64, 64, Gfx.BTN_LIGHT_TEXT);
		Gfx.textMid(c, backLbl, cx0 + 21, y, bh, Gfx.BTN_LIGHT_TEXT);
		region(cx0, y, bw, bh, () -> {
			open = null;
			scroll = 0;
			scrollTarget = 0;
			colourOpen = null;
			listening = null;
		});
		y -= 2;
		Identifier icon = Theme.icon(open.id);
		int tx = cx0 + bw + 12;
		if (icon != null) {
			Gfx.tex(c, icon, tx, y + 2, 18, 18, 64, 64, 0xFFFFFFFF);
			tx += 24;
		}
		Gfx.textMid(c, open.name, tx, y, 22, Theme.TEXT);
		int enW = 0;
		if (!open.locked) {
			String en = dev.solclient.Lang.t("Enabled");
			enW = Gfx.tw(en) + 86; // label + switch: the label used to be cut to "E..." by the switch
			toggleRow(c, mx, my, "Enabled", open.enabled, cx1 - enW, y, enW, () -> open.toggle());
		}
		// like Lunar: one small line under the title that says what the mod does, then a thin divider
		if (open.description != null && !open.description.isEmpty()) {
			Gfx.textRaw(c, fit(dev.solclient.Lang.t(open.description), cx1 - cx0 - 4), cx0 + 2, y + 28, Theme.DIM);
			c.fill(cx0, y + 40, cx1, y + 41, 0x14FFFFFF);
		}
	}

	/** A page of a mod's settings: shown when its button (tab) at the top is picked. */
	private static final class Page {
		final String title;
		/** The mod inside this card (Zoom, Freelook...) whose settings this page shows, or null for the card's own options. */
		final Mod child;
		/** Range of the card's own options (index in {@code mod.options}) this page shows. */
		final int from, to;

		Page(String title, Mod child, int from, int to) {
			this.title = title;
			this.child = child;
			this.from = from;
			this.to = to;
		}
	}

	/** The page picked in each mod's settings (kept while the game runs). */
	private static final java.util.Map<String, Integer> PAGE_SEL = new java.util.HashMap<>();

	private static boolean hasContent(List<Option<?>> all, int from, int to) {
		for (int i = from; i < to; i++) {
			Option<?> o = all.get(i);
			if (!(o instanceof Option.Header) && o.visible()) {
				return true; // an option that is hidden right now (showIf) does not make a page
			}
		}
		return false;
	}

	/**
	 * Splits a mod's settings into pages. The options are cut at every header, and every mod inside the card (Zoom,
	 * Freelook...) gets a page of its own. Fewer than two pages = no buttons, everything is shown as before.
	 */
	private List<Page> pagesFor(Mod m) {
		List<Page> out = new ArrayList<>();
		List<Option<?>> all = m.options;
		int headers = 0;
		for (Option<?> o : all) {
			if (o instanceof Option.Header) {
				headers++;
			}
		}
		boolean byHeader = headers >= 2 || (headers >= 1 && m == SolClient.settings);
		if (!byHeader) {
			if (!all.isEmpty()) {
				out.add(new Page(dev.solclient.Lang.tr("General", "Geral"), null, 0, all.size()));
			}
		} else {
			int first = 0;
			while (first < all.size() && !(all.get(first) instanceof Option.Header)) {
				first++;
			}
			if (first > 0 && hasContent(all, 0, first)) {
				out.add(new Page(dev.solclient.Lang.tr("General", "Geral"), null, 0, first));
			}
			for (int i = first; i < all.size(); i++) {
				if (!(all.get(i) instanceof Option.Header)) {
					continue;
				}
				int j = i + 1;
				while (j < all.size() && !(all.get(j) instanceof Option.Header)) {
					j++;
				}
				if (all.get(i).visible() && hasContent(all, i + 1, j)) {
					out.add(new Page(all.get(i).name, null, i + 1, j));
				}
				i = j - 1;
			}
		}
		for (Mod sm : SolClient.MODS) {
			if (sm.parent == m) {
				out.add(new Page(sm.name, sm, 0, 0));
			}
		}
		if (m == SolClient.settings) {
			for (Mod sm : SolClient.MODS) {
				if (sm.inSettings) {
					out.add(new Page(sm.name, sm, 0, 0));
				}
			}
		}
		return out;
	}

	private static boolean pageHasMatch(Mod open, Page pg, String sq) {
		if (sq.isEmpty()) {
			return false;
		}
		if (pg.child != null) {
			if (pg.child.name.toLowerCase(Locale.ROOT).contains(sq)) {
				return true;
			}
			for (Option<?> o : pg.child.options) {
				if (matchesSearch(o, sq)) {
					return true;
				}
			}
			return false;
		}
		for (int i = pg.from; i < pg.to && i < open.options.size(); i++) {
			if (matchesSearch(open.options.get(i), sq)) {
				return true;
			}
		}
		return false;
	}

	/** Light blue of the Caos logo. */
	private static final int LOGO_BLUE = 0xFFA3C3FC;

	private static final int RAIL_W = 128;
	private static final java.util.Set<String> SECTION_ICONS = java.util.Set.of("language", "font", "theme", "custom_colours", "menus",
			"hud_notifications", "players", "keys", "performance", "loading_screen", "setup_reset", "testing", "general");

	/** Icon of a settings section: the mod's own icon for a mod page, else a section icon (or the default one). */
	private static Identifier sectionIcon(Page pg) {
		if (pg.child != null) {
			Identifier ic = Theme.icon(pg.child.id);
			if (ic != null) {
				return ic;
			}
		}
		String slug = pg.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		return Theme.icon2("sec_" + (SECTION_ICONS.contains(slug) ? slug : "default"));
	}

	/**
	 * The pages of a mod's settings as a flat vertical list on the left (no box around it), like the user's SidebarLight:
	 * icon + name, the open one on a soft "muted" background, the others muted and lighter under the mouse. Pages that are
	 * mods inside the card sit under a "Mods" heading with a thin line down their left side.
	 */
	private void railBar(DrawContext c, int mx, int my, Mod m, List<Page> pages, int sel, String sq, int x, int y0, int y1) {
		int ih = 24, gap = 2, acc = Theme.accent() | 0xFF000000;
		clipY0 = 0;
		clipY1 = vh;
		c.enableScissor(x, y0, x + RAIL_W, y1);
		int py = y0 + 2;
		boolean subOpen = false;
		int subTop = 0;
		for (int i = 0; i < pages.size(); i++) {
			Page pg = pages.get(i);
			boolean child = pg.child != null;
			if (child && !subOpen) {
				subOpen = true;
				py += 4;
				Gfx.textMid(c, dev.solclient.Lang.tr("Mods", "Mods"), x + 10, py, 18, Theme.TEXT);
				py += 20;
				subTop = py;
			}
			int ix = child ? x + 14 : x;
			int iw = RAIL_W - (child ? 14 : 0);
			String title = dev.solclient.Lang.t(pg.title);
			boolean selected = i == sel;
			boolean hov = in(mx, my, ix, py, iw, ih) && my >= y0 && my < y1;
			float a = anim("rail" + m.id + i, hov);
			boolean off = child && !pg.child.enabled;
			if (selected) {
				Gfx.rrect(c, ix, py, iw, ih, 6, Gfx.withAlpha(0xFFFFFF, 26)); // bg-muted
				Gfx.rrect(c, ix, py + 5, 3, ih - 10, 1, acc); // a thin accent bar on the open page
			} else if (a > 0.01F) {
				Gfx.rrect(c, ix, py, iw, ih, 6, Gfx.withAlpha(0xFFFFFF, Math.round(13 * a))); // hover:bg-muted/50
			}
			int muted = Gfx.blend(Theme.DIM, Theme.TEXT, 0.15F);
			int col = selected ? Theme.TEXT : pageHasMatch(m, pg, sq) ? acc : off ? Gfx.blend(Theme.DIM, Theme.PANEL | 0xFF000000, 0.45F) : Gfx.blend(muted, Theme.TEXT, a);
			Gfx.tex(c, sectionIcon(pg), ix + 9, py + 5, 14, 14, 64, 64, col);
			Gfx.textMid(c, fit(title, iw - 38), ix + 30, py, ih, col);
			String info = SECTION_INFO.get(pg.title);
			if (info != null && hov) {
				tooltipText = dev.solclient.Lang.t(info);
			} // (a mod that is off is already shown dimmed: its tooltip used to cover the description next to the list)
			final int idx = i;
			region(ix, py, iw, ih, () -> {
				PAGE_SEL.put(m.id, idx);
				scroll = 0;
				scrollTarget = 0;
				colourOpen = null;
				listening = null;
			});
			py += ih + gap;
			if (subOpen && (i + 1 >= pages.size() || pages.get(i + 1).child == null)) {
				c.fill(x + 7, subTop, x + 8, py - gap, 0x26FFFFFF); // border-l of the group
			}
		}
		c.disableScissor();
	}

	/** The pages of a mod's settings as a row of small blue buttons (like Lunar). Returns its height. */
	private int tabBar(DrawContext c, int mx, int my, Mod m, List<Page> pages, int sel, String sq, int x, int y, int rw) {
		// chips that wrap onto more rows when they do not fit (they used to be squeezed until the names overlapped)
		int h = 26, gapX = 6, gapY = 6, n = pages.size();
		int px = x, py = y;
		for (int i = 0; i < n; i++) {
			Page pg = pages.get(i);
			String title = dev.solclient.Lang.t(pg.title);
			int w = Math.min(rw, Gfx.tw(title) + 30);
			if (px > x && px + w > x + rw) {
				px = x;
				py += h + gapY;
			}
			boolean selected = i == sel;
			boolean hov = in(mx, my, px, py, w, h) && my >= clipY0 && my < clipY1;
			float a = anim("pgh" + m.id + i, hov);
			int acc = Theme.accent() | 0xFF000000;
			boolean off = pg.child != null && !pg.child.enabled;
			// soft chips: filled with a tint of the accent when open, a faint card otherwise (no lines under or beside anything)
			int fill = selected ? Gfx.blend(0xFF1B1E29, acc, 0.42F) : Gfx.blend(Theme.ROW | 0xFF000000, 0xFF2A2E3F, a);
			Gfx.rrect(c, px, py, w, h, 8, fill);
			int col = selected ? 0xFFFFFFFF : pageHasMatch(m, pg, sq) ? acc : off ? Gfx.blend(Theme.DIM, Theme.PANEL | 0xFF000000, 0.45F) : Gfx.blend(Theme.DIM, Theme.TEXT, 0.45F + 0.55F * a);
			Gfx.textFit(c, title, px + w / 2, w - 10, py + 9, col);
			String info = SECTION_INFO.get(pg.title);
			if (info != null && hov) {
				tooltipText = dev.solclient.Lang.t(info);
			} else if (off && hov) {
				tooltipText = dev.solclient.Lang.tr("Off", "Desligado");
			}
			final int idx = i;
			region(px, py, w, h, () -> {
				PAGE_SEL.put(m.id, idx);
				scroll = 0;
				scrollTarget = 0;
				colourOpen = null;
				listening = null;
			});
			px += w + gapX;
		}
		return py + h - y;
	}

	/** A mod with only a few options keeps everything on one page: tabs would just be in the way. */
	private static boolean worthTabs(Mod m, List<Page> pages) {
		if (m instanceof dev.solclient.mods.CustomSkyMod) {
			return false; // the sky has its own Sky / Night / Effects... tabs: no second row of tabs
		}
		for (Option<?> o : m.options) {
			if (o instanceof Option.Choice ch && ch.tabs) {
				// the mod already has its own tab buttons (Section: Clips / Voice / Fights...) that show and hide the options:
				// a second row of buttons made most of the pages empty
				return false;
			}
		}
		int n = 0;
		for (Page pg : pages) {
			if (pg.child != null) {
				return true;
			}
		}
		for (Option<?> o : m.options) {
			if (!(o instanceof Option.Header)) {
				n++;
			}
		}
		return n > 9;
	}

	private void drawSettings(DrawContext c, int mx, int my, int cx0, int cx1, int cy0, int cy1, int barX) {
		int rw = cx1 - cx0 - 8;
		List<Page> pages = pagesFor(open);
		boolean tabs = pages.size() > 1 && worthTabs(open, pages);
		int sel = 0;
		if (tabs) {
			String sq = search.text().toLowerCase(Locale.ROOT).trim();
			sel = Math.max(0, Math.min(PAGE_SEL.getOrDefault(open.id, 0), pages.size() - 1));
			if (!sq.isEmpty() && !pageHasMatch(open, pages.get(sel), sq)) {
				for (int i = 0; i < pages.size(); i++) {
					if (pageHasMatch(open, pages.get(i), sq)) {
						sel = i; // searching: jump to the first page that has what you typed
						PAGE_SEL.put(open.id, i);
						break;
					}
				}
			}
			clipY0 = 0;
			clipY1 = vh;
			// sections as a vertical list on the left (always in view), the options of the picked one on the right
			railBar(c, mx, my, open, pages, sel, sq, cx0, cy0, cy1);
			cx0 += RAIL_W + 14;
			rw = cx1 - cx0 - 8;
		}
		clipY0 = cy0;
		clipY1 = cy1;
		c.enableScissor(cx0 - 2, cy0, cx1 + 2, cy1);
		int y = cy0 - Math.round(scroll);
		int startY = y;
		if (!tabs || sel == 0) {
			if (open instanceof dev.solclient.mods.CrosshairMod ch) {
				y += crosshairPreview(c, ch, cx0, y, rw) + 6;
			}
			if (open instanceof dev.solclient.mods.CustomSkyMod skyMod) {
				y += skyMod.preview(c, cx0, y, rw) + 6;
			}
			if (open instanceof dev.solclient.mods.SpotifyMod spMod) {
				y += spMod.preview(c, cx0, y, rw) + 6;
			}
		}
		if (!tabs) {
			y = drawOptionList(c, mx, my, open, cx0, y, rw);
			if (!pages.isEmpty() && pages.get(0).child != null) {
				y = drawChildPage(c, mx, my, pages.get(0).child, cx0, y, rw);
			}
		} else {
			Page pg = pages.get(sel);
			if (pg.child != null) {
				y = drawChildPage(c, mx, my, pg.child, cx0, y, rw);
			} else {
				List<Option<?>> opts = new ArrayList<>();
				for (int i = pg.from; i < pg.to && i < open.options.size(); i++) {
					Option<?> o = open.options.get(i);
					if (o.visible()) {
						opts.add(o);
					}
				}
				y = drawOptions(c, mx, my, opts, cx0, y, rw);
			}
		}
		int contentH = Math.max(0, y - startY - 4);
		maxScroll = Math.max(0, contentH - (cy1 - cy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, barX, cy0, cy1, contentH);
	}

	/** The settings of a mod that lives inside another card: its own checkbox, then its options. */
	private int drawChildPage(DrawContext c, int mx, int my, Mod sm, int cx0, int y, int rw) {
		if (sm.inSettings) {
			Gfx.text(c, dev.solclient.Lang.t(sm.name).toUpperCase(Locale.ROOT), cx0 + 2, y + 4, Theme.DIM);
			y += 18;
			y += toggleRow(c, mx, my, "Enabled", sm.enabled, cx0, y, rw, sm::toggle) + 4;
			return drawOptionList(c, mx, my, sm, cx0, y, rw);
		}
		y += toggleRow(c, mx, my, dev.solclient.Lang.t(sm.name), sm.enabled, cx0, y, rw, sm::toggle) + 2;
		if (sm.enabled) {
			y = drawOptionList(c, mx, my, sm, cx0 + 10, y, rw - 10);
		} else if (sm.description != null && !sm.description.isEmpty()) {
			Gfx.textRaw(c, fit(dev.solclient.Lang.t(sm.description), rw - 30), cx0 + 24, y, 0xFF6B7080);
			y += 12;
		}
		return y;
	}

	/** One line under each tab of the Caos settings: what is inside (shown when the mouse is over the button). */
	private static final java.util.Map<String, String> SECTION_INFO = java.util.Map.ofEntries(
			java.util.Map.entry("Language", "Menu language and automatic translation"),
			java.util.Map.entry("Font", "Pick a font, or import your own .ttf file"),
			java.util.Map.entry("Theme", "Ready-made looks, accent colour and transparency"),
			java.util.Map.entry("Custom Colours", "Change every colour yourself (advanced)"),
			java.util.Map.entry("Menus", "Home screen, pause menu, buttons and menu size"),
			java.util.Map.entry("HUD & Notifications", "The HUD in F3 and the notification sound"),
			java.util.Map.entry("Players", "Emotes, capes, skins, 3D cosmetics and friends"),
			java.util.Map.entry("Keys", "Shortcuts and the keyboard editor"),
			java.util.Map.entry("Performance", "See what is slowing the game down"),
			java.util.Map.entry("Loading Screen", "The logo shown while the game loads"),
			java.util.Map.entry("Setup & Reset", "First-time setup, presets and reset everything"),
			java.util.Map.entry("Testing", "Temporary test buttons"));

	private static boolean matchesSearch(Option<?> o, String sq) {
		return !sq.isEmpty() && !(o instanceof Option.Header) && o.name != null
				&& (o.name.toLowerCase(Locale.ROOT).contains(sq) || dev.solclient.Lang.t(o.name).toLowerCase(Locale.ROOT).contains(sq));
	}

	/** Shows the option's one-line explanation as a tooltip while the mouse is over its row. */
	private void tipFor(Option<?> o, int mx, int my, int x, int y, int w, int h) {
		if (o.tip != null && !o.tip.isEmpty() && in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1) {
			tooltipText = o.tip;
		}
	}

	/** Draws every visible option of a mod from y down; returns the new y. */
	private int drawOptionList(DrawContext c, int mx, int my, Mod m, int cx0, int y, int rw) {
		List<Option<?>> opts = new ArrayList<>();
		for (Option<?> o : m.options) {
			if (o.visible()) {
				opts.add(o);
			}
		}
		return drawOptions(c, mx, my, opts, cx0, y, rw);
	}

	/** Draws these options one under the other; returns the new y. */
	private int drawOptions(DrawContext c, int mx, int my, List<Option<?>> opts, int cx0, int y, int rw) {
		String sq = search.text().toLowerCase(Locale.ROOT).trim();
		for (int i = 0; i < opts.size(); i++) {
			Option<?> o = opts.get(i);
			// two on/off options next to each other (like Lunar)
			if (o instanceof Option.Bool b1 && i + 1 < opts.size() && opts.get(i + 1) instanceof Option.Bool b2
					&& (b1.tip == null || b1.tip.isEmpty()) && (b2.tip == null || b2.tip.isEmpty())) {
				int half = (rw - 6) / 2;
				tipFor(b1, mx, my, cx0, y, half, CHECK_ROW);
				tipFor(b2, mx, my, cx0 + half + 6, y, half, CHECK_ROW);
				toggleRow(c, mx, my, b1.name, b1.on(), cx0, y, half, () -> b1.set(!b1.on()));
				toggleRow(c, mx, my, b2.name, b2.on(), cx0 + half + 6, y, half, () -> b2.set(!b2.on()));
				if (matchesSearch(b1, sq)) {
					searchMark(c, cx0, y, half, CHECK_ROW);
				}
				if (matchesSearch(b2, sq)) {
					searchMark(c, cx0 + half + 6, y, half, CHECK_ROW);
				}
				y += CHECK_ROW + 4;
				i++;
				continue;
			}
			int oh = drawOption(c, mx, my, o, cx0, y, rw);
			if (matchesSearch(o, sq)) {
				searchMark(c, cx0, y, rw, oh);
			}
			y += oh + 4;
		}
		return y;
	}

	/** Marks the option you searched for: a thin outline exactly around its row. Everything stays clickable. */
	private void searchMark(DrawContext c, int x, int y, int w, int h) {
		int acc = Theme.accent() & 0xFFFFFF;
		Gfx.rrectBorder(c, x - 1, y - 1, w + 2, h + 2, 8, Gfx.withAlpha(acc, 0x1E), Gfx.withAlpha(acc, 0xC0));
	}

	/** Height of an on / off row (checkbox + label). */
	private static final int CHECK_ROW = 28;

	/** An on / off option: a card with its name and a pill switch on the right. The whole row is clickable. */
	private int toggleRow(DrawContext c, int mx, int my, String label, boolean on, int x, int y, int rw, Runnable toggle) {
		boolean hov = in(mx, my, x, y, rw, CHECK_ROW) && my >= clipY0 && my < clipY1;
		String key = "cb" + (open == null ? "" : open.id) + label;
		float a = anim(key, hov);
		float sa = swAnim(key + "sw", on);
		int acc = Theme.accent() | 0xFF000000;
		Gfx.rrect(c, x, y, rw, CHECK_ROW, 8, hov ? Theme.CARD_HOVER : Theme.ROW); // no outline: just the soft card
		Gfx.textMid(c, fit(label, rw - 66), x + 14, y, CHECK_ROW, on ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, 0.5F + 0.5F * a));
		// same switch as the launcher: square, dark outline, a block that slides, a darker lip underneath
		int sw = 38, sh = 20, sx = x + rw - sw - 12, sy = y + (CHECK_ROW - sh) / 2;
		Gfx.rrect(c, sx, sy, sw, sh, 4, 0xFF07080F);
		int top = Gfx.blend(hov ? 0xFF2D3147 : 0xFF252839, acc, sa);
		int lip = Gfx.blend(0xFF141622, Gfx.blend(acc, 0xFF000000, 0.38F), sa);
		Gfx.rrect(c, sx + 2, sy + 2, sw - 4, sh - 4, 2, lip);
		Gfx.rrect(c, sx + 2, sy + 2, sw - 4, sh - 7, 2, top);
		int kw = 14, kh = sh - 8;
		float kx = sx + 4 + sa * (sw - 8 - kw);
		Gfx.rrectF(c, kx, sy + 4, kw, kh, 2, Gfx.blend(0xFF8A8EA3, 0xFFFFFFFF, sa));
		c.fill((int) kx + 1, sy + 4 + kh - 3, (int) kx + kw - 1, sy + 4 + kh, Gfx.blend(0xFF5E6277, 0xFFB9BFE0, sa));
		region(x, y, rw, CHECK_ROW, toggle);
		return CHECK_ROW;
	}

	/** On / off switch with almost square corners: a small block slides inside a flat track (no ON / OFF text). */
	private void squareSwitch(DrawContext c, String key, int x, int y, boolean on, boolean hov) {
		float a = swAnim(key + "sw", on);
		int w = 30, h = 14;
		int track = Gfx.blend(Theme.OFF_BG_HOVER | 0xFF000000, Theme.GREEN_BG | 0xFF000000, a);
		Gfx.rrect(c, x, y, w, h, 3, hov ? Gfx.blend(track, 0xFFFFFFFF, 0.08F) : track);
		float kx = x + 2 + a * (w - 4 - 10);
		Gfx.rrectF(c, kx, y + 2, 10, h - 4, 2, Gfx.blend(0xFFB4B9C9, 0xFFFFFFFF, a));
	}

	/** easeOutBack: the little bounce of toggle #17 (cubic-bezier(0.18, 0.89, 0.35, 1.15)). */
	private static float backOut(float t) {
		float c1 = 1.70158F, c3 = c1 + 1F;
		return 1F + c3 * (float) Math.pow(t - 1F, 3) + c1 * (float) Math.pow(t - 1F, 2);
	}

	/**
	 * Toggle #17: a rectangular switch whose coloured block slides from left (OFF, red) to right (ON, green)
	 * with a small bounce, carrying its label. The track turns a light tint of the block colour.
	 */
	private String editFor = "";
	private static final int[] TINTS = {0, 0xFF5A5A, 0xFFA64D, 0xFFE066, 0x6BDB7A, 0x5AC8FF, 0x6B8CFF, 0xB57BFF, 0xFF7AC8};

	/**
	 * "Edit" above Buy / Equip: nudge the accessory up/down, left/right, forward/back and turn it a little (like Roblox, so it fits any
	 * skin), plus a colour tint. Limited so it never drifts off the body. Works before you buy it (try-on).
	 */
	private void cosmeticEditor(DrawContext c, int mx, int my, int w, int x, int bx, int bw, int by, Social.Item it) {
		var pd = dev.solclient.cosmetic.Variants.resolve(dev.solclient.cosmetic.Cosmetics.modelFor(it));
		if (pd == null) {
			return;
		}
		boolean ed = it.id.equals(editFor);
		int ey = by - 26;
		button(c, mx, my, bx, ey, bw, 20, ed ? "Done" : "Edit", "ledit", Theme.OFF_BG, Theme.OFF_BG_HOVER, 0xFFFFFFFF, () -> editFor = ed ? "" : it.id);
		if (!ed) {
			return;
		}
		int rowH = 19, rows = 4, top = ey - 8 - rows * rowH - 26;
		Gfx.rrect(c, bx - 4, top - 18, bw + 8, ey - top + 12, 6, 0x66000000);
		Gfx.text(c, "Position", bx + 2, top - 13, Theme.DIM);
		int rw = Gfx.tw("Reset") + 14;
		button(c, mx, my, bx + bw - rw, top - 16, rw, 14, "Reset", "lpreset", Theme.OFF_BG, Theme.OFF_BG_HOVER, 0xFFFFFFFF, () -> dev.solclient.cosmetic.Offsets.reset(pd));
		int[] v = dev.solclient.cosmetic.Offsets.get(pd);
		String[] lab = {"Up / Down", "Left / Right", "Forward / Back", "Turn"};
		int[] idx = {dev.solclient.cosmetic.Offsets.UP, dev.solclient.cosmetic.Offsets.SIDE, dev.solclient.cosmetic.Offsets.FWD, dev.solclient.cosmetic.Offsets.ROT};
		int[] step = {1, 1, 1, 5};
		for (int r = 0; r < rows; r++) {
			final int ix = idx[r], st = step[r];
			int y0 = top + r * rowH;
			Gfx.text(c, lab[r], bx + 2, y0 + 4, Theme.TEXT);
			button(c, mx, my, bx + bw - 70, y0, 20, 16, "-", "lpm" + r, Theme.OFF_BG, Theme.OFF_BG_HOVER, 0xFFFFFFFF, () -> dev.solclient.cosmetic.Offsets.nudge(pd, ix, -st));
			Gfx.textFit(c, String.valueOf(v[ix]), bx + bw - 35, 22, y0 + 4, Theme.TEXT);
			button(c, mx, my, bx + bw - 20, y0, 20, 16, "+", "lpp" + r, Theme.OFF_BG, Theme.OFF_BG_HOVER, 0xFFFFFFFF, () -> dev.solclient.cosmetic.Offsets.nudge(pd, ix, st));
		}
		int cy = top + rows * rowH + 2;
		Gfx.text(c, "Colour", bx + 2, cy + 3, Theme.TEXT);
		int sw = 13, gap = 4, n = TINTS.length, sx0 = bx + bw - n * (sw + gap) + gap;
		for (int k = 0; k < n; k++) {
			final int col = TINTS[k];
			int sx = sx0 + k * (sw + gap);
			boolean cur = (v[dev.solclient.cosmetic.Offsets.TINT] & 0xFFFFFF) == col;
			Gfx.rrect(c, sx - 1, cy - 1, sw + 2, sw + 2, 3, cur ? 0xFFFFFFFF : 0x55FFFFFF);
			if (col == 0) {
				Gfx.rrect(c, sx, cy, sw, sw, 2, 0xFF3A3F4F);
				Gfx.text(c, "x", sx + 4, cy + 3, 0xFFFFFFFF);
			} else {
				Gfx.rrect(c, sx, cy, sw, sw, 2, col | 0xFF000000);
			}
			region(sx - 1, cy - 1, sw + 2, sw + 2, () -> dev.solclient.cosmetic.Offsets.setTint(pd, col));
		}
	}

	private void toggle17(DrawContext c, String key, int x, int y, int w, int h, boolean on) {
		float[] st = toggleAnim.computeIfAbsent(key, k -> new float[]{on ? 1F : 0F, on ? 1F : 0F});
		float target = on ? 1F : 0F;
		if (st[1] != target) {
			st[1] = target;
		}
		float speed = Math.min(1F, frameDt * 6F); // about 0.17 s, whatever the frame rate
		st[0] = on ? Math.min(1F, st[0] + speed) : Math.max(0F, st[0] - speed);
		float pos = on ? backOut(st[0]) : 1F - backOut(1F - st[0]);
		int onCol = 0xFF2FB36A, offCol = 0xFFE5484D;
		int blockCol = Gfx.blend(offCol, onCol, Math.max(0F, Math.min(1F, st[0])));
		Gfx.rrect(c, x, y, w, h, 3, Gfx.blend(0xFF2A1A1D, 0xFF17301F, Math.max(0F, Math.min(1F, st[0]))));
		int bw = w / 2 - 1, bh = h - 4;
		float bx = x + 2 + pos * (w - 4 - bw);
		Gfx.rrectF(c, bx, y + 2, bw, bh, 2, blockCol);
		String t = st[0] > 0.5F ? "ON" : "OFF";
		Gfx.textFit(c, t, Math.round(bx + bw / 2F), bw - 4, y + (h - 8) / 2, 0xFFFFFFFF);
	}

	// ------------------------------------------------------------- crosshair editor
	/**
	 * Preview of the crosshair over the game's own panorama: open view and "on a player" (so the dynamic colour can be judged).
	 */
	private int crosshairPreview(DrawContext c, dev.solclient.mods.CrosshairMod ch, int x, int y, int rw) {
		int h = 120, gap = 8;
		int pw = (rw - gap) / 2;
		Gfx.text(c, dev.solclient.Lang.tr("PREVIEW", "PRÉ-VISUALIZAÇÃO"), x + 4, y + 2, Theme.DIM);
		int py = y + 14;
		for (int k = 0; k < 2; k++) {
			int px = x + k * (pw + gap);
			previewScene(c, px, py, pw, h, k == 1);
			float cx = px + pw / 2F, cy = py + (k == 1 ? 50 : h / 2F - 8);
			ch.drawAt(c, cx, cy, 3.5F, k == 1, true);
			String label = k == 0 ? dev.solclient.Lang.tr("Normal", "Normal") : dev.solclient.Lang.tr("On a player", "Num jogador");
			int lw = Gfx.tw(label) + 12;
			Gfx.rrect(c, Math.round(px + pw / 2F - lw / 2F), py + h - 18, lw, 13, 5, 0xB0000000);
			Gfx.text(c, label, Math.round(px + pw / 2F - Gfx.tw(label) / 2F), py + h - 15, 0xFFFFFFFF);
		}
		return 14 + h;
	}

	/** One sample picture: the game's own title-screen panorama (whatever the current resources have), optionally with your real skin in 3D. */
	private void previewScene(DrawContext c, int x, int y, int w, int h, boolean player) {
		Gfx.rrectBorder(c, x - 1, y - 1, w + 2, h + 2, 4, Theme.CARD_BORDER, Theme.CARD_BORDER);
		c.enableScissor(x, y, x + w, y + h);
		// the middle band of one panorama face (1024 x 1024), cut to the shape of the box
		int rh = Math.max(1, Math.round(1024F * h / w));
		Identifier face = Identifier.of("minecraft", "textures/gui/title/background/panorama_" + (player ? 1 : 0) + ".png");
		Gfx.texUv(c, face, x, y, w, h, 0, Math.max(0, 512 - rh / 2), 1024, rh, 1024, 1024, 0xFFFFFFFF);
		if (player) {
			RemoteTextures.Tex skin = dev.solclient.skin.SkinChanger.myPreview();
			if (skin != null) {
				SkinModel3D.draw(c, skin, x + w / 2F, y + 16, 3F, 0, 0, 6, true, java.util.List.of());
			}
		}
		c.disableScissor(); // the scissor of the settings list stays (scissors stack)
	}

	/** Preset picker: every preset drawn as a small icon. Picking one also copies it into Custom. */
	private int presetPicker(DrawContext c, int mx, int my, dev.solclient.mods.CrosshairMod ch, int x, int y, int rw) {
		int cell = 30, gap = 6;
		int cols = Math.max(1, (rw + gap) / (cell + gap));
		int n = dev.solclient.mods.CrosshairMod.PRESETS.length;
		for (int i = 0; i < n; i++) {
			int cx = x + (i % cols) * (cell + gap), cy = y + (i / cols) * (cell + gap);
			boolean sel = ch.preset.idx() == i;
			boolean hov = in(mx, my, cx, cy, cell, cell) && my >= clipY0 && my < clipY1;
			Gfx.rrectBorder(c, cx, cy, cell, cell, 6, hov ? TILE_HOVER : TILE, sel ? Theme.accent() : Theme.CARD_BORDER);
			boolean[][] m = dev.solclient.mods.CrosshairMod.fromString(dev.solclient.mods.CrosshairMod.PRESETS[i]);
			int px = cx + (cell - 30) / 2;
			for (int yy = 0; yy < m.length; yy++) {
				for (int xx = 0; xx < m.length; xx++) {
					if (m[yy][xx]) {
						c.fill(px + xx * 2, cy + yy * 2, px + xx * 2 + 2, cy + yy * 2 + 2, 0xFFFFFFFF);
					}
				}
			}
			final int idx = i;
			region(cx, cy, cell, cell, () -> ch.pickPreset(idx));
		}
		int rows = (n + cols - 1) / cols;
		Gfx.text(c, "Tip: open Custom to edit the picked preset", x, y + rows * (cell + gap) + 2, Theme.DIM);
		return rows * (cell + gap) + 12;
	}

	/** Pixel editor: left click / drag draws, right click / drag erases. */
	private int gridEditor(DrawContext c, int mx, int my, Option.Grid g, int x, int y, int rw) {
		int size = g.size;
		int cell = Math.max(6, Math.min(11, (rw - 150) / size));
		int gw = cell * size;
		int gx = x, gy = y;
		Gfx.rrect(c, gx - 3, gy - 3, gw + 6, gw + 6, 6, 0xFF0A0B0E);
		for (int yy = 0; yy < size; yy++) {
			for (int xx = 0; xx < size; xx++) {
				int px = gx + xx * cell, py = gy + yy * cell;
				boolean on = g.get(xx, yy);
				boolean centre = xx == size / 2 && yy == size / 2;
				c.fill(px, py, px + cell - 1, py + cell - 1, on ? 0xFFE8EAF0 : centre ? 0xFF2A2D36 : 0xFF181A20);
			}
		}
		region(gx, gy, gw, gw, b -> {
		}, (dx, dy) -> {
			int cxp = (int) Math.floor((dx - gx) / cell), cyp = (int) Math.floor((dy - gy) / cell);
			g.put(cxp, cyp, dragButton == 0);
		});
		// help box on the right
		int hx = gx + gw + 14, hw = Math.max(80, rw - gw - 14);
		Gfx.text(c, "HELP", hx, gy, Theme.TEXT);
		String[][] help = {{"LEFT CLICK", "Draw pixels"}, {"RIGHT CLICK", "Erase pixels"}, {"CLEAR", "Empty the grid"}};
		int hy = gy + 14;
		for (String[] row : help) {
			int kw = Gfx.tw(row[0]) + 8;
			Gfx.rrect(c, hx, hy, kw, 13, 3, Theme.OFF_BG_HOVER);
			Gfx.text(c, row[0], hx + 4, hy + 3, Theme.TEXT);
			Gfx.text(c, fit(row[1], hw - kw - 6), hx + kw + 6, hy + 3, Theme.DIM);
			hy += 18;
		}
		int bw = Gfx.tw("Clear") + 24;
		pill(c, mx, my, hx, hy + 4, bw, 20, 6, "gclear", false, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
		Gfx.textFit(c, "Clear", hx + bw / 2, bw - 6, hy + 10, Theme.TEXT);
		region(hx, hy + 4, bw, 20, g::clear);
		return gw + 6;
	}

	/** Settings > Language: a dropdown with flags (Discord-style), like LabyMod's. */
	private static final String[] LANG_FLAGS = dev.solclient.Lang.FLAGS;
	private boolean langOpen;

	private int languagePicker(DrawContext c, int mx, int my, Option.Choice ch, int x, int y, int rw) {
		Gfx.rrect(c, x, y, rw, 28, 9, Theme.ROW);
		Gfx.tex(c, Theme.icon2("language"), x + 10, y + 7, 14, 14, 64, 64, Theme.DIM);
		Gfx.textMid(c, ch.name, x + 30, y, 28, Theme.TEXT);
		int bw = 150, bx = x + rw - 12 - bw, by = y + 4;
		boolean hov = in(mx, my, bx, by, bw, 20);
		Gfx.rrectBorder(c, bx, by, bw, 20, 7, hov || langOpen ? Theme.OFF_BG_HOVER : Theme.OFF_BG, langOpen ? Theme.accent() : Theme.CARD_BORDER);
		int cur = Math.floorMod(ch.idx(), ch.names.length);
		Gfx.tex(c, Gfx.id("textures/gui/flag/" + LANG_FLAGS[Math.min(cur, LANG_FLAGS.length - 1)] + ".png"), bx + 7, by + 4, 16, 12, 64, 48, 0xFFFFFFFF);
		Gfx.textRaw(c, ch.names[cur], bx + 28, by + 6, Theme.TEXT);
		chevron(c, bx + bw - 12, by + 10, !langOpen, Theme.DIM);
		region(bx, by, bw, 20, () -> langOpen = !langOpen);
		if (!langOpen) {
			return 28;
		}
		int ly = y + 30, rh = 24;
		int lh = ch.names.length * rh + 8;
		Gfx.rrectBorder(c, bx, ly, bw, lh, 8, Theme.FIELD, Theme.CARD_BORDER);
		for (int i = 0; i < ch.names.length; i++) {
			int ry = ly + 4 + i * rh;
			boolean sel = cur == i, h = in(mx, my, bx + 4, ry, bw - 8, rh);
			if (sel || h) {
				Gfx.rrect(c, bx + 4, ry, bw - 8, rh, 6, sel ? Gfx.blend(Theme.accent(), Theme.FIELD, 0.75F) : Theme.OFF_BG);
			}
			Gfx.tex(c, Gfx.id("textures/gui/flag/" + LANG_FLAGS[Math.min(i, LANG_FLAGS.length - 1)] + ".png"), bx + 10, ry + 6, 16, 12, 64, 48, 0xFFFFFFFF);
			Gfx.textRaw(c, ch.names[i], bx + 32, ry + 8, sel ? 0xFFFFFFFF : Theme.TEXT);
			if (sel) {
				// check mark (a smooth icon: hand-drawn pixels broke apart at some GUI scales)
				Gfx.tex(c, Theme.icon2("check"), bx + bw - 24, ry + 5, 14, 14, 64, 64, 0xFFFFFFFF);
			}
			final int idx = i;
			region(bx + 4, ry, bw - 8, rh, () -> {
				ch.set(idx);
				langOpen = false;
				Config.save();
			});
		}
		return 30 + lh + 4;
	}

	/** Settings > Font: a grid of tiles, each one written in its own font. */
	private int fontPicker(DrawContext c, int mx, int my, Option.Choice ch, int x, int y, int w) {
		var tr = MinecraftClient.getInstance().textRenderer;
		int n = Gfx.FONT_NAMES.length, gap = 6;
		int cols = Math.max(2, Math.min(5, w / 104));
		int tw = (w - gap * (cols - 1)) / cols, th = 38;
		for (int i = 0; i < n; i++) {
			int tx = x + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
			boolean sel = ch.idx() == i;
			boolean hov = in(mx, my, tx, ty, tw, th);
			Gfx.rrectBorder(c, tx, ty, tw, th, 8, sel ? Gfx.blend(Theme.accent(), TILE, 0.78F) : hov ? TILE_HOVER : TILE,
					sel ? Theme.accent() : hov ? 0xFF3A3D49 : Theme.CARD_BORDER);
			if (i == Gfx.CUSTOM_FONT && !dev.solclient.CustomFont.installed()) {
				// no imported font yet (it does not exist, so it can't be drawn in it): explain how to add one
				Gfx.textC(c, "+ Your own font", tx + tw / 2, ty + 9, Theme.TEXT);
				Gfx.textC(c, fit("Drop a .ttf file here", tw - 8), tx + tw / 2, ty + 23, Theme.DIM);
				region(tx, ty, tw, th, dev.solclient.CustomFont::openFolder);
				continue;
			}
			net.minecraft.text.Text big = Gfx.inFont("Aa Caos 123", i);
			int bw = tr.getWidth(big);
			float sc = Math.min(1.25F, (tw - 12) / (float) Math.max(1, bw));
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(tx + tw / 2F - bw * sc / 2F, ty + 7);
			m.scale(sc, sc);
			c.drawText(tr, big, 0, 0, 0xFFFFFFFF, false);
			m.popMatrix();
			net.minecraft.text.Text nm = Gfx.inFont(Gfx.FONT_NAMES[i], i);
			int nw = tr.getWidth(nm);
			c.drawText(tr, nm, tx + tw / 2 - nw / 2, ty + 24, sel ? Theme.accent() : Theme.DIM, false);
			if (sel) {
				Gfx.circle(c, tx + tw - 8, ty + 8, 3.5F, Theme.accent());
			}
			final int idx = i;
			region(tx, ty, tw, th, () -> {
				ch.set(idx);
				Gfx.setFont(idx);
			});
		}
		int rows = (n + cols - 1) / cols;
		return rows * (th + gap) + 4;
	}

	private static final int[] PRESETS = {0xFFFF4D4D, 0xFFFF9F43, 0xFFFFD93D, 0xFF6BCB77, 0xFF4D96FF, 0xFFB57BFF,
			0xFFFF6BB5, 0xFF2DD4BF, 0xFF7FB3F5, 0xFFFFFFFF, 0xFF9AA0B4, 0xFF000000};

	private int drawOption(DrawContext c, int mx, int my, Option<?> o, int x, int y, int rw) {
		int h = drawOptionRaw(c, mx, my, o, x, y, rw);
		// one dim line under the option that says what it does (the same text as the tooltip)
		if (o.tip != null && !o.tip.isEmpty() && h <= 30 && !(o instanceof Option.Header) && !(o instanceof Option.Grid)
				&& !(o instanceof Option.Choice tb && tb.tabs)) {
			Gfx.textRaw(c, fit(dev.solclient.Lang.t(o.tip), rw - 24), x + 14, y + h + 2, 0xFF6B7080);
			return h + 13;
		}
		return h;
	}

	@SuppressWarnings("unchecked")
	private int drawOptionRaw(DrawContext c, int mx, int my, Option<?> o, int x, int y, int rw) {
		if (o instanceof Option.Header) {
			spaced(c, dev.solclient.Lang.t(o.name).toUpperCase(Locale.ROOT), x + 4, y + 10, 0.75F, 1.6F, Gfx.blend(Theme.DIM, Theme.TEXT, 0.4F));
			return 24;
		}
		if (o instanceof Option.Bool b) {
			tipFor(b, mx, my, x, y, rw, CHECK_ROW);
			return toggleRow(c, mx, my, b.name, b.on(), x, y, rw, () -> b.set(!b.on()));
		}
		boolean rowHov = in(mx, my, x, y, rw, 28) && my >= clipY0 && my < clipY1;
		Gfx.rrect(c, x, y, rw, 28, 8, rowHov ? Theme.CARD_HOVER : Theme.ROW);
		Gfx.textMid(c, o.name, x + 14, y, 28, Theme.TEXT);
		tipFor(o, mx, my, x, y, rw, 28);
		int rx = x + rw - 12;
		int accent = Theme.accent();

		if (o instanceof Option.Num n) {
			int tw = 130, sx = rx - tw, sy = y + 14;
			float pct = (n.f() - n.min) / (n.max - n.min);
			boolean drag = draggingTag(n);
			boolean hov = in(mx, my, sx - 8, y, tw + 16, 28) && my >= clipY0 && my < clipY1;
			if (n.showValue) {
				String v = n.display();
				Gfx.text(c, v, sx - 14 - Gfx.tw(v), y + 10, drag ? Theme.TEXT : Theme.DIM);
			}
			SmoothSlider.draw(c, n, sx, sy, tw, pct, drag, hov, n.display());
			region(sx - 8, y, tw + 16, 28, b -> {
			}, (mxx, myy) -> {
				float p = (float) Math.max(0, Math.min(1, (mxx - sx) / tw));
				SmoothSlider.dragTo(n, p);
				n.set(n.min + p * (n.max - n.min));
			});
			tagLast(n);
			return 28;
		}

		if (o instanceof Option.Custom cu) {
			return cu.drawer.draw(c, mx, my, x + 4, y + 2, rw - 8, (cqx, cqy, cqw, cqh, cqc) -> region(cqx, cqy, cqw, cqh, cqc)) + 6;
		}
		if (o instanceof Option.Grid g) {
			return gridEditor(c, mx, my, g, x + 4, y + 4, rw - 8) + 8;
		}
		if (open instanceof dev.solclient.mods.CrosshairMod cm && o == cm.preset) {
			return presetPicker(c, mx, my, cm, x + 4, y + 2, rw - 8);
		}
		if (dev.solclient.SolClient.settings != null && o == dev.solclient.SolClient.settings.language) {
			return languagePicker(c, mx, my, (Option.Choice) o, x, y, rw);
		}
		if (dev.solclient.SolClient.settings != null && o == dev.solclient.SolClient.settings.font) {
			return fontPicker(c, mx, my, (Option.Choice) o, x + 4, y + 2, rw - 8);
		}
		if (o instanceof Option.Choice tb && tb.tabs) {
			int n = tb.names.length, gap = 2;
			Gfx.rrect(c, x, y + 1, rw, 24, 8, Theme.ROW);
			float tw = (rw - 6 - gap * (n - 1)) / (float) n;
			int sel = Math.floorMod(tb.idx(), n);
			SlideBg.draw(c, "tabs" + System.identityHashCode(o), x + 3 + sel * (tw + gap), y + 4, tw, 18, 6F, SlideBg.colour(), true, Math.round(scroll));
			for (int i = 0; i < n; i++) {
				int tx = Math.round(x + 3 + i * (tw + gap));
				boolean hov = in(mx, my, tx, y + 4, Math.round(tw), 18) && my >= clipY0 && my < clipY1;
				String label = tb.names[i].toUpperCase(Locale.ROOT);
				Gfx.textC(c, fit(label, Math.round(tw) - 8), Math.round(tx + tw / 2F), y + 9, i == sel ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
				final int idx = i;
				region(tx, y + 4, Math.round(tw), 18, () -> tb.set(idx));
			}
			return 26;
		}
		if (o instanceof Option.Choice ch) {
			// every value is visible: a row of buttons when they fit, else a list that opens under the row
			int n = ch.names.length;
			int[] ws = new int[n];
			int segW = 6 + 2 * Math.max(0, n - 1);
			for (int i = 0; i < n; i++) {
				ws[i] = Gfx.tw(ch.names[i]) + 18;
				segW += ws[i];
			}
			int avail = rw - 28 - Gfx.tw(o.name) - 16;
			if (n <= 6 && segW <= avail) {
				int bx = rx - segW;
				// same look as the on / off switch: a dark square track, the chosen value lifted with a thin accent lip under it
				Gfx.rrect(c, bx, y + 2, segW, 24, 4, 0xFF07080F);
				int acc = accent | 0xFF000000, cur = Math.floorMod(ch.idx(), n), px = bx + 3;
				for (int i = 0; i < n; i++) {
					boolean hov = in(mx, my, px, y + 5, ws[i], 18) && my >= clipY0 && my < clipY1;
					if (i == cur) {
						Gfx.rrect(c, px, y + 5, ws[i], 18, 3, 0xFF2D3147);
					} else if (hov) {
						Gfx.rrect(c, px, y + 5, ws[i], 18, 3, 0xFF1A1D2B);
					}
					Gfx.textFit(c, ch.names[i], px + ws[i] / 2, ws[i] - 4, y + 9, i == cur ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
					final int idx = i;
					region(px, y + 5, ws[i], 18, () -> ch.set(idx));
					px += ws[i] + 2;
				}
				return 28;
			}
			boolean openList = choiceOpen == ch;
			int bw = Math.min(170, Math.max(120, rw / 3)), bx = rx - bw;
			pill(c, mx, my, bx, y + 4, bw, 20, 7, "ch" + o.key + x, openList, accent, Theme.OFF_BG, Theme.OFF_BG_HOVER);
			Gfx.textFit(c, ch.label(), bx + bw / 2, bw - 30, y + 10, Theme.TEXT);
			Gfx.text(c, openList ? "^" : "v", bx + bw - 9 - Gfx.tw("v"), y + 10, Theme.DIM);
			region(bx, y + 4, bw, 20, () -> choiceOpen = openList ? null : ch);
			if (!openList) {
				return 28;
			}
			int top = y + 30, rh = 22;
			Gfx.rrect(c, x, top, rw, n * rh + 6, 9, Theme.ROW);
			int cur = Math.floorMod(ch.idx(), n);
			for (int i = 0; i < n; i++) {
				int iy = top + 3 + i * rh;
				boolean hov = in(mx, my, x + 3, iy, rw - 6, rh - 1) && my >= clipY0 && my < clipY1;
				if (i == cur) {
					Gfx.rrect(c, x + 3, iy, rw - 6, rh - 1, 6, Gfx.blend(Theme.ROW | 0xFF000000, accent | 0xFF000000, 0.22F));
				} else if (hov) {
					Gfx.rrect(c, x + 3, iy, rw - 6, rh - 1, 6, Gfx.withAlpha(0xFFFFFF, 16));
				}
				CheckBox.draw(c, "chl" + o.key + i, x + 10, iy + 5.5F, 10, i == cur, hov);
				Gfx.textMid(c, fit(ch.names[i], rw - 50), x + 28, iy, rh - 1, i == cur ? Theme.TEXT : Gfx.blend(Theme.DIM, Theme.TEXT, 0.5F));
				final int idx = i;
				region(x + 3, iy, rw - 6, rh - 1, () -> {
					ch.set(idx);
					choiceOpen = null;
				});
			}
			return 30 + n * rh + 6;
		}

		if (o instanceof Option.Text tx) {
			TextBox box = optBoxes.computeIfAbsent(tx, k -> new TextBox(Math.max(1, k.max)));
			boolean foc = focus == F_OPT && optFocus == tx;
			if (!foc && !box.text().equals(tx.get())) {
				box.set(tx.get());
			}
			int bw = Math.max(90, Math.min(240, rw - 150)), bx = rx - bw;
			if (tx.secret && !foc && !tx.get().isEmpty()) {
				Gfx.rrectBorder(c, bx, y + 3, bw, 22, 8, Theme.FIELD, Theme.CARD_BORDER);
				Gfx.textMid(c, "*".repeat(Math.min(24, tx.get().length())), bx + 9, y + 3, 22, Theme.TEXT);
			} else {
				field(c, bx, y + 3, bw, 22, box, tx.hint, foc, accent, false);
			}
			region(bx, y + 3, bw, 22, () -> {
				focus = F_OPT;
				optFocus = tx;
			});
			return 28;
		}

		if (o instanceof Option.Action act) {
			String st = act.status.get();
			if (st != null && st.length() > 34) {
				if (in(mx, my, x, y, rw, 28) && my >= clipY0 && my < clipY1) {
					tooltipText = st; // the whole message (e.g. what to allow in System Settings)
				}
				st = st.substring(0, 33) + "...";
			}
			int bw = Gfx.tw(act.button) + 28, bx = rx - bw;
			pill(c, mx, my, bx, y + 4, bw, 20, 7, "ac" + o.key + x, false, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
			Gfx.textFit(c, act.button, bx + bw / 2, bw - 6, y + 10, Theme.TEXT);
			if (st != null && !st.isEmpty()) {
				Gfx.text(c, st, bx - 10 - Gfx.tw(st), y + 10, Theme.DIM);
			}
			region(bx, y + 4, bw, 20, () -> act.run.run());
			return 28;
		}

		if (o instanceof Option.Key k) {
			int bw = 120, bx = rx - bw;
			boolean isListening = listening == k;
			pill(c, mx, my, bx, y + 4, bw, 20, 7, "k" + o.key + x, isListening, accent, Theme.OFF_BG, Theme.OFF_BG_HOVER);
			Gfx.textFit(c, isListening ? "Press a key..." : Keys.name(k.code()), bx + bw / 2, bw - 6, y + 10,
					isListening ? 0xFFFFFFFF : Theme.TEXT);
			region(bx, y + 4, bw, 20, () -> listening = k);
			return 28;
		}

		if (o instanceof Option.Col col) {
			int sx = rx - 36, sy = y + 6;
			Gfx.rrectBorder(c, sx, sy, 36, 16, 5, 0xFF0E1013, 0xFF3A3D49);
			Gfx.rrect(c, sx + 1, sy + 1, 34, 14, 4, col.c());
			String hex = String.format("#%06X", col.c() & 0xFFFFFF);
			Gfx.text(c, hex, sx - 10 - Gfx.tw(hex), y + 10, Theme.DIM);
			region(x, y, rw, 28, () -> colourOpen = (colourOpen == col ? null : col));
			if (colourOpen != col) {
				return 28;
			}

			// the colour can change from outside the picker (presets, reset button): resync the picker state
			float[] st0 = hsv.get(col);
			if (st0 == null || hsvToArgb(st0) != col.c()) {
				st0 = toHsv(col.c());
				hsv.put(col, st0);
			}
			final float[] st = st0;
			int pickerH = 118;
			int top = y + 30;
			Gfx.rrect(c, x, top, rw, pickerH, 9, Theme.ROW);

			// saturation / brightness square
			int sqX = x + 12, sqY = top + 11, sqW = 156, sqH = 96;
			for (int i = 0; i < sqW; i++) {
				float sat = i / (float) (sqW - 1);
				Gfx.gradientV(c, sqX + i, sqY, 1, sqH, hsvToRgb(st[0], sat, 1F), 0xFF000000);
			}
			Gfx.outline(c, sqX - 1, sqY - 1, sqW + 2, sqH + 2, 0xFF2C2F38);
			int kx = sqX + Math.round(st[1] * (sqW - 1));
			int ky = sqY + Math.round((1F - st[2]) * (sqH - 1));
			Gfx.circle(c, kx, ky, 4.5F, 0xFFFFFFFF);
			Gfx.circle(c, kx, ky, 3.2F, hsvToRgb(st[0], st[1], st[2]));
			region(sqX - 4, sqY - 4, sqW + 8, sqH + 8, b -> {
			}, (mxx, myy) -> {
				st[1] = (float) Math.max(0, Math.min(1, (mxx - sqX) / (sqW - 1)));
				st[2] = 1F - (float) Math.max(0, Math.min(1, (myy - sqY) / (sqH - 1)));
				col.set(hsvToArgb(st));
			});

			// hue bar
			int huX = sqX + sqW + 14, barW = 12;
			int[] hueCols = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
			for (int i = 0; i < 6; i++) {
				int y1 = sqY + i * sqH / 6, y2 = sqY + (i + 1) * sqH / 6;
				c.fillGradient(huX, y1, huX + barW, y2, hueCols[i], hueCols[i + 1]);
			}
			Gfx.outline(c, huX - 1, sqY - 1, barW + 2, sqH + 2, 0xFF2C2F38);
			int hky = sqY + Math.round(st[0] * (sqH - 1));
			Gfx.rrectF(c, huX - 3, hky - 2, barW + 6, 4, 2, 0xFFFFFFFF);
			region(huX - 4, sqY - 4, barW + 8, sqH + 8, b -> {
			}, (mxx, myy) -> {
				st[0] = (float) Math.max(0, Math.min(0.999, (myy - sqY) / (sqH - 1)));
				col.set(hsvToArgb(st));
			});

			// opacity bar
			int alX = huX + barW + 12;
			Gfx.rrect(c, alX, sqY, barW, sqH, 0, 0xFF3A3D49);
			for (int i = 0; i < sqH; i += 8) {
				for (int j = 0; j < barW; j += 6) {
					if (((i / 8) + (j / 6)) % 2 == 0) {
						c.fill(alX + j, sqY + i, alX + Math.min(barW, j + 6), sqY + Math.min(sqH, i + 8), 0xFF1C1E24);
					}
				}
			}
			int rgbOnly = hsvToRgb(st[0], st[1], st[2]);
			c.fillGradient(alX, sqY, alX + barW, sqY + sqH, rgbOnly, Gfx.withAlpha(rgbOnly, 0));
			Gfx.outline(c, alX - 1, sqY - 1, barW + 2, sqH + 2, 0xFF2C2F38);
			int aky = sqY + Math.round((1F - st[3]) * (sqH - 1));
			Gfx.rrectF(c, alX - 3, aky - 2, barW + 6, 4, 2, 0xFFFFFFFF);
			region(alX - 4, sqY - 4, barW + 8, sqH + 8, b -> {
			}, (mxx, myy) -> {
				st[3] = 1F - (float) Math.max(0, Math.min(1, (myy - sqY) / (sqH - 1)));
				col.set(hsvToArgb(st));
			});

			// presets
			int prX = alX + barW + 16;
			for (int i = 0; i < PRESETS.length; i++) {
				int px = prX + (i % 2) * 16, pyy = sqY + (i / 2) * 16;
				final int pc = PRESETS[i];
				Gfx.rrectBorder(c, px, pyy, 13, 13, 4, pc, 0xFF3A3D49);
				region(px, pyy, 13, 13, () -> {
					float[] n2 = toHsv(pc);
					st[0] = n2[0];
					st[1] = n2[1];
					st[2] = n2[2];
					col.set(hsvToArgb(st));
				});
			}

			// readout
			int rdX = prX + 40;
			Gfx.text(c, "HEX", rdX, sqY + 2, Theme.DIM);
			Gfx.text(c, hex, rdX, sqY + 14, Theme.TEXT);
			Gfx.text(c, "OPACITY", rdX, sqY + 38, Theme.DIM);
			Gfx.text(c, Math.round(st[3] * 100) + "%", rdX, sqY + 50, Theme.TEXT);
			Gfx.rrectBorder(c, rdX, sqY + 72, 44, 22, 6, 0xFF0E1013, 0xFF3A3D49);
			Gfx.rrect(c, rdX + 1, sqY + 73, 42, 20, 5, col.c());
			return 28 + 2 + pickerH;
		}
		return 28;
	}

	// ------------------------------------------------------------ edit panel
	private void drawEdit(DrawContext c, int mx, int my, int x, int right, int y, int h, int accent) {
		Gfx.rrectBorder(c, x, y, Math.max(2, right - x), h, 12, SUB, Theme.PANEL_BORDER);
		boolean settled = aE >= 0.99F;
		int px = right - EW; // content stays at its final position and is revealed while the panel slides in
		c.enableScissor(x, y, right, y + h);
		int ix = px + 10, iw = EW - 20;

		Gfx.text(c, "EDIT", ix, y + 11, Theme.DIM);
		{
			int gx = px + EW - 24, gy = y + 7;
			boolean hg = in(mx, my, gx, gy, 16, 16);
			Gfx.texSpin(c, Theme.icon2("settings_small"), gx + 2, gy + 2, 12, 12, 64, 64, hg ? 0xFFFFFFFF : Theme.DIM, "gear:" + gx + ":" + gy, hg);
			if (hg) {
				tooltipText = selected == null ? "Caos settings" : "Settings of " + selected.name;
			}
			region(gx, gy, 16, 16, () -> {
				if (selected != null) {
					openSettings(selected);
				} else {
					for (Mod mm : SolClient.MODS) {
						if (mm instanceof dev.solclient.mods.SolClientMod) {
							openSettings(mm);
						}
					}
				}
			});
		}

		// preview box
		int bh = 90;
		Gfx.rrectBorder(c, ix, y + 28, iw, bh, 10, Theme.FIELD, Theme.CARD_BORDER);

		if (selected == null) {
			Gfx.textFit(c, "Nothing", px + EW / 2, EW - 6, y + 28 + bh + (h - 28 - bh) / 2 - 8, Theme.DIM);
			c.disableScissor();
			return;
		}
		Mod m = selected;
		modIcon(c, m, px + EW / 2F, y + 28 + bh / 2F, 48, m.enabled || m.locked);

		int ty = y + 28 + bh + 10;
		Gfx.text(c, fit(m.name, iw), ix, ty, Theme.TEXT);
		ty += 14;
		for (String line : wrap(m.description, iw, 3)) {
			Gfx.text(c, line, ix, ty, Theme.DIM);
			ty += 11;
		}
		ty = Math.max(ty + 8, y + 28 + bh + 10 + 14 + 33 + 8);

		if (!m.locked) {
			if (m.enabled) {
				pill(c, mx, my, ix, ty, iw, 22, 8, "etog", false, 0, Theme.GREEN_BG, Theme.GREEN_BG_HOVER);
				Gfx.textFit(c, "Enabled", ix + iw / 2, iw - 6, ty + 6, Theme.GREEN);
			} else {
				pill(c, mx, my, ix, ty, iw, 22, 8, "etog", false, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
				Gfx.textFit(c, "Disabled", ix + iw / 2, iw - 6, ty + 6, Theme.DIM);
			}
			if (settled) {
				region(ix, ty, iw, 22, m::toggle);
			}
			ty += 28;
		}

		pill(c, mx, my, ix, ty, iw, 22, 8, "eset", false, 0, accent, Theme.accentHover());
		Gfx.textFit(c, "Settings", ix + iw / 2, iw - 6, ty + 6, 0xFFFFFFFF);
		if (settled) {
			region(ix, ty, iw, 22, () -> openSettings(m));
		}
		ty += 28;

		if (!m.locked) {
			pill(c, mx, my, ix, ty, iw, 22, 8, "efav", m.favorite, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
			Gfx.tex(c, Theme.icon2("favourite"), ix + 8, ty + 4, 14, 14, 64, 64, m.favorite ? 0xFFFF6B7A : 0xFFE6E8EE);
			Gfx.textMid(c, m.favorite ? "Favourite" : "Add to favourites", ix + 30, ty, 22, Theme.TEXT);
			if (settled) {
				region(ix, ty, iw, 22, () -> toggleFavourite(m));
			}
			ty += 32;
		}

		// quick toggles (first few on/off options of the mod)
		int shown = 0;
		for (Option<?> o : m.options) {
			if (!(o instanceof Option.Bool b) || !o.visible() || shown >= 4 || ty + 24 > y + h - 8) {
				continue;
			}
			if (shown == 0) {
				Gfx.text(c, "QUICK OPTIONS", ix, ty, Theme.DIM);
				ty += 14;
			}
			boolean qh = in(mx, my, ix, ty, iw, 22);
			Gfx.rrect(c, ix, ty, iw, 22, 4, qh ? TILE_HOVER : TILE);
			CheckBox.draw(c, "qs" + m.id + b.name, ix + 6, ty + 5.5F, 11, b.on(), qh);
			Gfx.textMid(c, fit(b.name, iw - 30), ix + 24, ty, 22, b.on() ? Theme.TEXT : Theme.DIM);
			if (settled) {
				region(ix, ty, iw, 22, () -> b.set(!b.on()));
			}
			ty += 26;
			shown++;
		}
		c.disableScissor();
	}

	// --------------------------------------------------------- social helpers
	/** Two clicks within 3 s on the same thing = confirmed (buy, delete, remove friend). */
	private boolean confirm(String key, String ask) {
		long now = System.currentTimeMillis();
		if (key.equals(confirmKey) && now - confirmAt < 3000L) {
			confirmKey = null;
			return true;
		}
		confirmKey = key;
		confirmAt = now;
		Social.say(ask, false);
		return false;
	}

	private boolean confirming(String key) {
		return key.equals(confirmKey) && System.currentTimeMillis() - confirmAt < 3000L;
	}

	private static void badge(DrawContext c, int cx, int cy, int n) {
		if (n <= 0) {
			return;
		}
		String t = n > 99 ? "99+" : String.valueOf(n);
		int w = Math.max(11, Gfx.tw(t) + 6);
		Gfx.rrect(c, cx - w / 2, cy - 6, w, 12, 6, Theme.RED);
		Gfx.textC(c, t, cx, cy - 4, 0xFFFFFFFF);
	}

	private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, String id, int bg, int bgHover, int textCol, Runnable run) {
		boolean neutralBg = bg == TILE || bg == Theme.OFF_BG || bg == SUB || bg == Theme.CARD;
		if (neutralBg) {
			boolean hv = in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
			Gfx.lightButton(c, x, y, w, h, anim(id, hv), false); // the launcher's white-grey button
			textCol = Gfx.BTN_LIGHT_TEXT;
		} else if (classic() && (bg == Theme.accent() || bg == (Theme.accent() | 0xFF000000))) {
			boolean hv = in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
			Gfx.blueButton(c, x, y, w, h, Theme.accent(), anim(id, hv), false); // the launcher's blue button
			textCol = 0xFFFFFFFF;
		} else if (classic()) {
			boolean hov = in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
			classicFill(c, x, y, w, h, hov ? slate(bgHover, true) : slate(bg, false));
			if (textCol == Theme.DIM && (bg == TILE || bg == Theme.OFF_BG)) {
				textCol = Theme.TEXT;
			}
		} else {
			pill(c, mx, my, x, y, w, h, 7, id, false, 0, bg, bgHover);
		}
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 9) / 2, textCol);
		if (run != null) {
			region(x, y, w, h, run);
		}
	}

	/** Player head from the user's "image" URL, or a circle with the first letter. */
	private void avatar(DrawContext c, Social.User u, int cx, int cy, int r, boolean on) {
		RemoteTextures.Tex t = u == null ? null : RemoteTextures.get(u.image);
		if (t != null) {
			Gfx.tex(c, t.id(), cx - r, cy - r, r * 2, r * 2, t.width(), t.height(), 0xFFFFFFFF);
		} else {
			Gfx.circle(c, cx, cy, r, on ? Theme.GREEN_BG_HOVER : Theme.OFF_BG_HOVER);
			String n = u == null ? "?" : u.display();
			Gfx.textC(c, n.substring(0, 1).toUpperCase(Locale.ROOT), cx, cy - 4, 0xFFFFFFFF);
		}
	}

	/** Shown in the social views while not logged in. */
	private boolean drawOffline(DrawContext c, int mx, int my, int cx, int cy, int maxW) {
		if (Social.me != null) {
			return false;
		}
		List<String> lines = wrap(Social.status, maxW, 3);
		int ty = cy - lines.size() * 6 - 12;
		for (String l : lines) {
			Gfx.textC(c, l, cx, ty, Theme.DIM);
			ty += 12;
		}
		if (!Social.loggingIn) {
			int bw = Gfx.tw("Retry") + 26;
			button(c, mx, my, cx - bw / 2, ty + 6, bw, 20, "Retry", "retry" + cx, Theme.accent(), Theme.accentHover(), 0xFFFFFFFF, Social::retryLogin);
		}
		return true;
	}

	private void drawFlash(DrawContext c, int cx, int bottom) {
		String f = Social.flash;
		long age = System.currentTimeMillis() - Social.flashAt;
		if (f == null || age > 3500L) {
			return;
		}
		int a = (int) Math.max(0, Math.min(255, (3500L - age) * 255 / 300L));
		int w = Gfx.tw(f) + 24;
		int x = cx - w / 2, y = bottom - 30;
		Gfx.rrectBorder(c, x, y, w, 22, 9, Gfx.withAlpha(0x15161B, Math.min(a, 245)), Gfx.withAlpha(Social.flashError ? Theme.RED : Theme.accent(), a));
		Gfx.textC(c, f, cx, y + 7, Gfx.withAlpha(Social.flashError ? 0xFF9A9C : 0xF1F3F8, Math.max(30, a)));
	}

	/**
	 * A server invite in the chat, like a server in the multiplayer list (v100): the server's icon, name, MOTD,
	 * players and ping bars (asked to the server itself), then Join and Copy IP.
	 */
	private int inviteCard(DrawContext c, int mx, int my, Social.Msg m, int x, int y, int w, int h, int my0, int my1, boolean settled, int subCol) {
		final String addr = m.address;
		boolean hosted = dev.solclient.social.KuboRelay.isRelayAddress(addr);
		dev.solclient.social.ServerPing.Info info = dev.solclient.social.ServerPing.get(addr);
		Gfx.rrectBorder(c, x, y, w, h, 3, 0xFF111318, 0xFF2A2D36);
		Gfx.textRaw(c, dev.solclient.Lang.t(hosted ? "Hosted world" : "Server invite").toUpperCase(Locale.ROOT), x + 6, y + 5, 0xFF6B7080);
		// icon
		int is = 28, ix = x + 6, iy = y + 15;
		Gfx.rrect(c, ix - 1, iy - 1, is + 2, is + 2, 3, 0xFF0A0B0E);
		if (info.icon != null) {
			Gfx.tex(c, info.icon, ix, iy, is, is, 64, 64, 0xFFFFFFFF);
		} else if (hosted) {
			Gfx.tex(c, Theme.icon2("host_world"), ix + 4, iy + 4, is - 8, is - 8, 64, 64, 0xFFFFFFFF);
		} else {
			Gfx.tex(c, Identifier.of("minecraft", "textures/misc/unknown_server.png"), ix, iy, is, is, 64, 64, 0xFFFFFFFF);
		}
		// ping bars + players on the right
		int bxr = x + w - 6;
		int bars = !info.done && info.ping < 0 ? -1 : !info.online ? 0 : info.ping < 150 ? 5 : info.ping < 300 ? 4 : info.ping < 600 ? 3 : info.ping < 1000 ? 2 : 1;
		int barCol = bars >= 4 ? 0xFF4ADE80 : bars >= 2 ? 0xFFFBBF24 : 0xFFF87171;
		for (int b = 0; b < 5; b++) {
			int bh = 2 + b, bx2 = bxr - 10 + b * 2;
			boolean lit = bars < 0 ? (System.currentTimeMillis() / 150 + b) % 5 == 0 : b < bars;
			c.fill(bx2, iy + 8 - bh, bx2 + 1, iy + 8, lit ? (bars < 0 ? 0xFF8C90A0 : barCol) : 0xFF3A3D49);
		}
		String pl = info.players >= 0 ? info.players + (info.max >= 0 ? "/" + info.max : "") : "";
		if (!pl.isEmpty()) {
			Gfx.textRaw(c, pl, bxr - Gfx.twRaw(pl), iy + 12, 0xFFB6BAC8);
		}
		int tx = ix + is + 7, tw = bxr - 14 - tx - (pl.isEmpty() ? 0 : 0);
		String name = m.serverName == null || m.serverName.isBlank() ? addr : m.serverName;
		Gfx.textRaw(c, fitRaw(name, tw), tx, iy, 0xFFFFFFFF);
		String motd = info.done && !info.online ? dev.solclient.Lang.t(hosted ? "Not hosting right now" : "Can't reach the server")
				: info.motd == null || info.motd.isBlank() ? (info.done ? "" : dev.solclient.Lang.t("Loading...")) : info.motd.split("\n")[0];
		Gfx.textRaw(c, fitRaw(motd, w - (tx - x) - 6 - (pl.isEmpty() ? 0 : Gfx.twRaw(pl) + 6)), tx, iy + 11, info.done && !info.online ? 0xFFF87171 : 0xFF9AA0B4);
		Gfx.textRaw(c, fitRaw(hosted ? addr.replaceFirst("(?i)^kubo:", "") : addr, w - (tx - x) - 6), tx, iy + 21, 0xFF6B7080);
		// buttons
		int by = y + h - 18, bw = (w - 16) / 2;
		if (!m.mine) {
			boolean hj = in(mx, my, x + 6, by, bw, 14) && my >= my0 && my < my1;
			KuboButton.draw(c, x + 6, by, bw, 14, "Join", hj, Theme.GREEN_BG, Theme.GREEN_BG_HOVER, 0xFFFFFFFF);
			if (settled) {
				region(x + 6, by, bw, 14, () -> dev.solclient.social.ServerShare.join(addr));
			}
		}
		int cx2 = m.mine ? x + 6 : x + 10 + bw, cw2 = m.mine ? w - 12 : w - 16 - bw;
		boolean hc = in(mx, my, cx2, by, cw2, 14) && my >= my0 && my < my1;
		KuboButton.neutral(c, cx2, by, cw2, 14, "Copy IP", hc);
		if (settled) {
			region(cx2, by, cw2, 14, () -> {
				MinecraftClient.getInstance().keyboard.setClipboard(addr);
				Social.say("Copied " + addr, false);
			});
		}
		return y + h + 2;
	}

	private boolean embeddedFocus;
	private boolean statusMenu, statusBadges;
	private int statusMenuX, statusMenuY, badgeMenuX, badgeMenuY, statusRegionsFrom = Integer.MAX_VALUE;

	private boolean isStatusRegion(int i) {
		return i >= statusRegionsFrom;
	}

	/** The little menu under your name: pick what friends see. */
	private void drawStatusMenu(DrawContext c, int mx, int my) {
		statusRegionsFrom = regions.size();
		if (statusBadges) {
			drawBadgeMenu(c, mx, my);
			return;
		}
		StatusBadge.Status[] opts = {StatusBadge.Status.ONLINE, StatusBadge.Status.AWAY, StatusBadge.Status.BUSY, StatusBadge.Status.INVISIBLE};
		String[] notes = {"", "", "No notifications", "You look offline"};
		int w = 150, rh = 22, h = opts.length * rh + 8, x = Math.min(statusMenuX, vw - w - 8), y = statusMenuY;
		Gfx.rrectBorder(c, x, y, w, h, 4, 0xF8101114, Theme.CARD_BORDER);
		for (int i = 0; i < opts.length; i++) {
			int ry = y + 4 + i * rh;
			boolean sel = Social.myStatus == opts[i], hov = in(mx, my, x + 4, ry, w - 8, rh);
			if (sel || hov) {
				Gfx.rrect(c, x + 4, ry, w - 8, rh, 3, sel ? SlideBg.colour() : 0x0CFFFFFF);
			}
			StatusBadge.draw(c, opts[i], x + 18, ry + rh / 2 + 4, 8, sel ? SlideBg.colour() : 0xF8101114);
			Gfx.text(c, opts[i].label, x + 26, ry + (notes[i].isEmpty() ? 7 : 3), Theme.TEXT);
			if (!notes[i].isEmpty()) {
				Gfx.textClip(c, notes[i], x + 26, ry + 12, w - 34, Theme.DIM);
			}
			final StatusBadge.Status s = opts[i];
			region(x + 4, ry, w - 8, rh, () -> {
				Social.setMyStatus(s);
				statusMenu = false;
			});
		}
	}

	/** Profile badges: the ones this account may wear (crown = owner, Minecraft = original, stone = pirate) + "none". */
	private void drawBadgeMenu(DrawContext c, int mx, int my) {
		var list = dev.solclient.social.ProfileBadges.available();
		var worn = dev.solclient.social.ProfileBadges.equipped();
		int w = 190, rh = 24, rows = list.length + 1, h = rows * rh + 8, x = Math.min(badgeMenuX, vw - w - 8), y = badgeMenuY;
		Gfx.rrectBorder(c, x, y, w, h, 4, 0xF8101114, Theme.CARD_BORDER);
		for (int i = 0; i < rows; i++) {
			int ry = y + 4 + i * rh;
			final dev.solclient.social.ProfileBadges.Badge b = i < list.length ? list[i] : null;
			boolean sel = b == worn, hov = in(mx, my, x + 4, ry, w - 8, rh);
			if (sel || hov) {
				Gfx.rrect(c, x + 4, ry, w - 8, rh, 3, sel ? SlideBg.colour() : 0x0CFFFFFF);
			}
			if (b != null) {
				Gfx.tex(c, b.texture(), x + 10, ry + (rh - 16) / 2, 16, 16, 32, 32, 0xFFFFFFFF);
				Gfx.textClip(c, dev.solclient.Lang.tr(b.en, b.pt), x + 32, ry + 8, w - 44, Theme.TEXT);
			} else {
				Gfx.textClip(c, dev.solclient.Lang.tr("No badge", "Sem badge"), x + 32, ry + 8, w - 44, Theme.DIM);
			}
			region(x + 4, ry, w - 8, rh, () -> {
				dev.solclient.social.ProfileBadges.equip(b);
				statusMenu = false;
				statusBadges = false;
			});
		}
	}


	// ------------------------------------------------------------ profile page
	private dev.solclient.social.ProfileBadges.Badge badgeSel;

	private static String badgeInfo(dev.solclient.social.ProfileBadges.Badge b) {
		return switch (b) {
			case CROWN -> dev.solclient.Lang.tr("The owner's crown. It shows to the right of your name in the nametag and the tab list.",
					"A coroa do dono. Aparece à direita do teu nome na nametag e na lista de jogadores.");
			case MINECRAFT -> dev.solclient.Lang.tr("Shows everyone you play with a real, paid Minecraft account.",
					"Mostra a toda a gente que jogas com uma conta Minecraft original.");
			case PIRATE -> dev.solclient.Lang.tr("A little pirate stone for offline (cracked) accounts.",
					"Uma pedrinha pirata para contas offline (crackeadas).");
			case HANDS -> dev.solclient.Lang.tr("For the people who helped build Caos.", "Para quem ajudou a construir o Caos.");
			case BUG_FIXER -> dev.solclient.Lang.tr("For the person who finds and fixes the bugs of Caos.", "Para quem encontra e corrige os bugs do Caos.");
			case BETA_TESTER -> dev.solclient.Lang.tr("For the people who test Caos before everybody else.", "Para quem testa o Caos antes de toda a gente.");
			case VIP -> dev.solclient.Lang.tr("For VIPs: a golden name that every Caos player sees, and your own cape (drop a 64x32 PNG in Capes & skins).",
					"Para VIPs: nome dourado que todos os jogadores com Caos veem, e a tua própria capa (larga um PNG 64x32 em Capas e skins).");
			case YOUTUBE -> dev.solclient.Lang.tr("For YouTubers: a red name that every Caos player sees.", "Para YouTubers: nome vermelho que todos os jogadores com Caos veem.");
			case TWITCH -> dev.solclient.Lang.tr("For streamers: a purple name that every Caos player sees.", "Para streamers: nome roxo que todos os jogadores com Caos veem.");
			case TIKTOK -> dev.solclient.Lang.tr("For TikTokers: a light blue, white and pink name that every Caos player sees.", "Para TikTokers: nome azul claro, branco e rosa que todos os jogadores com Caos veem.");
			case SON -> dev.solclient.Lang.tr("The meme badge. Other Caos players on your server see it next to your name.", "A badge do meme. Os outros jogadores com Caos no teu servidor veem-na ao lado do teu nome.");
			default -> dev.solclient.Lang.tr("Discord boost badge: it will show that you boosted the Caos Discord server. It does nothing yet.",
					"Badge de boost do Discord: vai mostrar que deste boost ao servidor do Caos. Ainda não faz nada.");
		};
	}

	private static String badgeNeed(dev.solclient.social.ProfileBadges.Badge b) {
		return switch (b) {
			case CROWN -> dev.solclient.Lang.tr("Needs: a Microsoft account with the nick " + dev.solclient.social.ProfileBadges.OWNER_NICK + ".",
					"Precisas de: conta Microsoft com o nick " + dev.solclient.social.ProfileBadges.OWNER_NICK + ".");
			case MINECRAFT -> dev.solclient.Lang.tr("Needs: a Microsoft (original) account.", "Precisas de: conta Microsoft (original).");
			case PIRATE -> dev.solclient.Lang.tr("Needs: an offline / non-Microsoft account.", "Precisas de: conta offline / não Microsoft.");
			case BUG_FIXER -> dev.solclient.Lang.tr("Needs: the Microsoft account Itz_Vxiper.", "Precisas de: a conta Microsoft Itz_Vxiper.");
			case BETA_TESTER -> dev.solclient.Lang.tr("Needs: the Microsoft account NoOneAboveMe.", "Precisas de: a conta Microsoft NoOneAboveMe.");
			case VIP, YOUTUBE, TWITCH, TIKTOK -> dev.solclient.Lang.tr("Needs: the site has to give it to your account.", "Precisas de: o site dar-ta à tua conta.");
			case SON -> dev.solclient.Lang.tr("Needs: drown to death. Only then it is yours.", "Precisas de: morrer afogado. Só assim é tua.");
			case HANDS -> dev.solclient.Lang.tr("Needs: a Microsoft account of one of the team (Itz_Vxiper, Marzox_x, NoOneAboveMe).",
					"Precisas de: conta Microsoft de alguém da equipa (Itz_Vxiper, Marzox_x, NoOneAboveMe).");
			default -> dev.solclient.Lang.tr("Needs: boost the Caos Discord server (coming soon).", "Precisas de: dar boost ao servidor do Discord do Caos (em breve).");
		};
	}

	private void drawProfilePage(DrawContext c, int mx, int my, int x, int y, int w, int h, int ix0, int ix1, int iy0, int iy1, int accent) {
		var all = dev.solclient.social.ProfileBadges.Badge.values();
		var worn = dev.solclient.social.ProfileBadges.equipped();
		if (badgeSel == null) {
			badgeSel = worn != null ? worn : all[0];
		}
		Social.User me = Social.me;
		String nick = me != null ? me.display() : MinecraftClient.getInstance().getSession().getUsername();
		int top = y + 12, bottom = y + h - 12, left = x + 14, right = x + w - 14;
		int gap = 10;
		int lw = Math.max(150, Math.min(250, (right - left) * 36 / 100));

		// ---- left: User card with the full 3D skin
		Gfx.rrectBorder(c, left, top, lw, bottom - top, 8, SUB, Theme.CARD_BORDER);
		Gfx.text(c, dev.solclient.Lang.tr("User", "Utilizador"), left + 10, top + 8, Theme.DIM);
		int statH = 100;
		drawPosedModel(c, left, top + 18, lw, bottom - top - 18 - statH, tryOnNone(), true, dev.solclient.skin.SkinChanger.myPreview());
		// status chips
		StatusBadge.Status[] opts = {StatusBadge.Status.ONLINE, StatusBadge.Status.AWAY, StatusBadge.Status.BUSY, StatusBadge.Status.INVISIBLE};
		int cwid = (lw - 20 - 6) / 2, cy0 = bottom - statH + 6;
		for (int i = 0; i < opts.length; i++) {
			int cx0 = left + 10 + (i % 2) * (cwid + 6), cy1 = cy0 + (i / 2) * 26;
			final StatusBadge.Status st = opts[i];
			boolean sel = Social.myStatus == st, hov = in(mx, my, cx0, cy1, cwid, 22);
			Gfx.rrectBorder(c, cx0, cy1, cwid, 22, 6, sel ? SlideBg.colour() : hov ? TILE_HOVER : TILE, Theme.CARD_BORDER);
			StatusBadge.draw(c, st, cx0 + 11, cy1 + 15, 7, sel ? SlideBg.colour() : TILE);
			Gfx.textClip(c, st.label, cx0 + 20, cy1 + 7, cwid - 24, Theme.TEXT);
			if (me != null) {
				region(cx0, cy1, cwid, 22, () -> Social.setMyStatus(st));
			}
		}
		// country flag next to your name (off by default)
		String cc = dev.solclient.social.ProfileBadges.myCountry();
		if (!cc.isEmpty()) {
			int fy = cy0 + 52, fw = lw - 20;
			boolean fOn = dev.solclient.social.ProfileBadges.myFlagOn();
			toggleRow(c, mx, my, dev.solclient.Lang.tr("Flag on my name", "Bandeira no meu nome"), fOn, left + 10, fy - 3, fw,
					() -> dev.solclient.social.ProfileBadges.setMyFlag(!fOn));
			Gfx.tex(c, Gfx.id("textures/gui/flag/" + cc + ".png"), left + 10 + fw - 78, fy + 5, 16, 12, 64, 48, fOn ? 0xFFFFFFFF : 0x80FFFFFF);
		}

		// ---- right: Badges
		int rx = left + lw + gap, rw = right - rx;
		if (rw < 120) {
			return;
		}
		Gfx.text(c, "Badges", rx, top + 2, Theme.DIM);
		// compact tiles (icon only, the name shows when you point at it): yours first, then the locked ones
		int cell = 40, gp = 6;
		int cols = Math.max(3, (rw + gp) / (cell + gp));
		java.util.List<dev.solclient.social.ProfileBadges.Badge> mine = new java.util.ArrayList<>(), locked = new java.util.ArrayList<>();
		for (var bb : all) {
			(dev.solclient.social.ProfileBadges.allowed(bb) ? mine : locked).add(bb);
		}
		int gy = top + 18, yy = gy, dh = 92;
		for (int part = 0; part < 2; part++) {
			var list = part == 0 ? mine : locked;
			if (list.isEmpty()) {
				continue;
			}
			Gfx.textRaw(c, (part == 0 ? dev.solclient.Lang.t("Yours") : dev.solclient.Lang.t("Locked")).toUpperCase(Locale.ROOT) + "  " + list.size(), rx, yy, 0xFF6B7080);
			yy += 12;
			for (int i = 0; i < list.size(); i++) {
				var b = list.get(i);
				int bx = rx + (i % cols) * (cell + gp), by = yy + (i / cols) * (cell + gp);
				boolean can = part == 0, isWorn = b == worn, sel = b == badgeSel;
				boolean hov = in(mx, my, bx, by, cell, cell);
				Gfx.rrectBorder(c, bx, by, cell, cell, 9, sel ? SlideBg.colour() : hov ? TILE_HOVER : TILE, isWorn ? accent : sel ? 0xFFB6BAC8 : Theme.CARD_BORDER);
				Gfx.tex(c, b.texture(), bx + (cell - 26) / 2, by + (cell - 26) / 2, 26, 26, 32, 32, can ? 0xFFFFFFFF : 0x55FFFFFF);
				if (!isWorn && !can) { // a small padlock
					Gfx.rrect(c, bx + cell - 12, by + cell - 11, 8, 6, 2, 0xFFB6BAC8);
					Gfx.rrectBorder(c, bx + cell - 11, by + cell - 15, 6, 6, 3, 0, 0xFFB6BAC8);
				}
				if (hov) {
					tooltipText = dev.solclient.Lang.tr(b.en, b.pt);
				}
				region(bx, by, cell, cell, () -> badgeSel = b);
			}
			yy += ((list.size() + cols - 1) / cols) * (cell + gp) + 2;
		}
		int dy = yy + 2;
		int cardH = cell;

		// detail panel of the selected badge
		Gfx.rrectBorder(c, rx, dy, rw, dh, 8, SUB, Theme.CARD_BORDER);
		var b = badgeSel;
		boolean can = dev.solclient.social.ProfileBadges.allowed(b);
		Gfx.tex(c, b.texture(), rx + 12, dy + 12, 32, 32, 32, 32, can ? 0xFFFFFFFF : 0x55FFFFFF);
		Gfx.text(c, dev.solclient.Lang.tr(b.en, b.pt), rx + 54, dy + 14, Theme.TEXT);
		Gfx.textClip(c, can ? dev.solclient.Lang.tr("You can wear this badge", "Podes usar esta badge") : dev.solclient.Lang.tr("You cannot wear this badge", "Não podes usar esta badge"),
				rx + 54, dy + 27, rw - (b.creator() ? 290 : 140), can ? 0xFF6BE08A : Theme.RED);
		int ty = dy + (dh >= 92 ? 52 : 46);
		for (String ln : wrap(badgeInfo(b), rw - 24)) {
			Gfx.text(c, ln, rx + 12, ty, Theme.DIM);
			ty += 11;
		}
		for (String ln : wrap(badgeNeed(b), rw - 24)) {
			Gfx.text(c, ln, rx + 12, ty, Theme.TEXT);
			ty += 11;
		}
		boolean isWorn = b == worn;
		String lbl = isWorn ? dev.solclient.Lang.tr("Unequip", "Tirar") : dev.solclient.Lang.tr("Equip", "Equipar");
		int bw = 84;
		if (can && b.creator()) {
			boolean col = dev.solclient.social.ProfileBadges.myColourOn();
			String cl = dev.solclient.Lang.tr("Name colour: ", "Cor do nome: ") + (col ? dev.solclient.Lang.tr("On", "Sim") : dev.solclient.Lang.tr("Off", "Não"));
			button(c, mx, my, rx + rw - bw - 10 - 128 - 6, dy + 12, 128, 22, cl, "pfColour", Theme.OFF_BG, Theme.OFF_BG_HOVER, 0xFFFFFFFF,
					() -> dev.solclient.social.ProfileBadges.setMyColour(!col));
		}
		if (can) {
			button(c, mx, my, rx + rw - bw - 10, dy + 12, bw, 22, lbl, "pfEquip", isWorn ? Theme.OFF_BG : accent, isWorn ? Theme.OFF_BG_HOVER : Theme.accentHover(), 0xFFFFFFFF,
					() -> dev.solclient.social.ProfileBadges.equip(isWorn ? null : b));
		} else {
			Gfx.rrect(c, rx + rw - bw - 10, dy + 12, bw, 22, 6, Theme.OFF_BG);
			Gfx.textFit(c, dev.solclient.Lang.tr("Locked", "Bloqueada"), rx + rw - bw / 2 - 10, bw - 6, dy + 19, Theme.DIM);
		}

		// lower row: locator bar colour + commands / API
		int tyy = dy + dh + 8, th = bottom - tyy;
		if (th >= 56) {
			int cw2 = (rw - 8) / 2;
			drawLocatorCard(c, mx, my, rx, tyy, cw2, th, accent);
			drawCommandsCard(c, mx, my, rx + cw2 + 8, tyy, rw - cw2 - 8, th, accent, nick);
		}
	}

	// ---- locator bar colour (the colour of your dot on other players' locator bar)
	private static float locatorHue = -1F;
	private String copiedWhat;
	private long copiedAt;

	private static java.nio.file.Path locatorFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo-locator.txt");
	}

	private static float locatorHue() {
		if (locatorHue < 0F) {
			locatorHue = 353F / 360F;
			try {
				locatorHue = Float.parseFloat(java.nio.file.Files.readString(locatorFile()).trim());
			} catch (Exception ignored) {
				// no file yet
			}
		}
		return locatorHue;
	}

	private static int locatorRgb() {
		return dev.solclient.fx.Rainbow.hsv(locatorHue(), 0.62F, 0.90F) & 0xFFFFFF;
	}

	private void copy(String what, String text) {
		MinecraftClient.getInstance().keyboard.setClipboard(text);
		copiedWhat = what;
		copiedAt = System.currentTimeMillis();
	}

	private boolean justCopied(String what) {
		return what.equals(copiedWhat) && System.currentTimeMillis() - copiedAt < 1400;
	}

	private void copyButton(DrawContext c, int mx, int my, int x, int y, int w, int h, String id, String text, int accent) {
		boolean done = justCopied(id);
		String lbl = done ? dev.solclient.Lang.tr("Copied!", "Copiado!") : dev.solclient.Lang.tr("Copy", "Copiar");
		button(c, mx, my, x, y, w, h, lbl, "cp_" + id, done ? 0xFF2E8B57 : accent, done ? 0xFF2E8B57 : Theme.accentHover(), 0xFFFFFFFF, () -> copy(id, text));
	}

	private void drawLocatorCard(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent) {
		Gfx.rrectBorder(c, x, y, w, h, 8, SUB, Theme.CARD_BORDER);
		Gfx.text(c, dev.solclient.Lang.tr("LOCATOR BAR COLOR", "COR DA LOCATOR BAR"), x + 10, y + 9, Theme.DIM);
		int tx = x + 12, tw = w - 24, ty = y + 30;
		// the rainbow track
		int segs = 24;
		for (int i = 0; i < segs; i++) {
			int x0 = tx + tw * i / segs, x1 = tx + tw * (i + 1) / segs;
			Gfx.gradientH(c, x0, ty, x1 - x0, 6, 0xFF000000 | dev.solclient.fx.Rainbow.hsv(i / (float) segs, 0.62F, 0.90F),
					0xFF000000 | dev.solclient.fx.Rainbow.hsv((i + 1) / (float) segs, 0.62F, 0.90F));
		}
		int rgb = locatorRgb();
		float tpx = tx + locatorHue() * tw;
		Gfx.circle(c, tpx, ty + 3, 7, 0xFFFFFFFF);
		Gfx.circle(c, tpx, ty + 3, 5, 0xFF000000 | rgb);
		region(tx - 8, ty - 8, tw + 16, 22, b -> {
		}, (mxx, myy) -> {
			locatorHue = (float) Math.max(0, Math.min(0.999, (mxx - tx) / tw));
			try {
				java.nio.file.Files.writeString(locatorFile(), String.valueOf(locatorHue));
			} catch (Exception ignored) {
				// cannot save
			}
		});
		String hex = String.format(Locale.ROOT, "#%06X", rgb);
		int cy = ty + 22;
		int hw = Gfx.tw(hex) + 20;
		Gfx.rrect(c, x + 12, cy, hw, 20, 6, 0xFF000000 | rgb);
		Gfx.text(c, hex, x + 22, cy + 6, 0xFFFFFFFF);
		copyButton(c, mx, my, x + 12 + hw + 8, cy, Math.max(60, w - hw - 44), 20, "locator", "/waypoint modify @s color hex " + hex.substring(1), accent);
		if (h >= 96) {
			Gfx.textClip(c, dev.solclient.Lang.tr("Copies the command. Needs permission on the server.", "Copia o comando. Precisa de permissão no servidor."), x + 12, cy + 28, w - 24, Theme.DIM);
		}
	}

	private void drawCommandsCard(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent, String nick) {
		Gfx.rrectBorder(c, x, y, w, h, 8, SUB, Theme.CARD_BORDER);
		Gfx.text(c, dev.solclient.Lang.tr("COMMANDS & API", "COMANDOS E API"), x + 10, y + 9, Theme.DIM);
		String name = MinecraftClient.getInstance().getSession().getUsername();
		String cmd = "/give @p minecraft:player_head[profile=\"" + name + "\"]";
		String url = "https://mc-heads.net/avatar/" + name + "/256";
		int bw = 52, ix = x + 12, iw = w - 24 - bw - 6;
		Gfx.text(c, dev.solclient.Lang.tr("PLAYER HEAD (1.21+)", "CABEÇA DE JOGADOR (1.21+)"), ix, y + 24, Theme.DIM);
		Gfx.rrectBorder(c, ix, y + 35, iw, 20, 6, TILE, Theme.CARD_BORDER);
		Gfx.textClip(c, cmd, ix + 8, y + 41, iw - 14, Theme.TEXT);
		copyButton(c, mx, my, ix + iw + 6, y + 35, bw, 20, "head", cmd, accent);
		if (h >= 90) {
			Gfx.text(c, dev.solclient.Lang.tr("AVATAR URL", "URL DO AVATAR"), ix, y + 64, Theme.DIM);
			Gfx.rrectBorder(c, ix, y + 75, iw, 20, 6, TILE, Theme.CARD_BORDER);
			Gfx.textClip(c, url, ix + 8, y + 81, iw - 14, Theme.TEXT);
			copyButton(c, mx, my, ix + iw + 6, y + 75, bw, 20, "avatar", url, accent);
		}
	}

		private static List<String> wrap(String t, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : t.split(" ")) {
			String test = cur.length() == 0 ? word : cur + " " + word;
			if (Gfx.tw(test) > width && cur.length() > 0) {
				out.add(cur.toString());
				cur = new StringBuilder(word);
			} else {
				cur = new StringBuilder(test);
			}
		}
		if (cur.length() > 0) {
			out.add(cur.toString());
		}
		return out;
	}

	private static String coinsText(long n) {
		return String.format(Locale.ROOT, "%,d", n).replace(',', ' ') + " coins";
	}

	private static String until(String iso) {
		if (iso == null) {
			return "";
		}
		try {
			long s = java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(iso)).getSeconds();
			if (s <= 0) {
				return "now";
			}
			return s >= 3600 ? (s / 3600) + "h " + (s % 3600) / 60 + "m" : Math.max(1, s / 60) + "m";
		} catch (RuntimeException e) {
			return "";
		}
	}

	// --------------------------------------------------------- profiles view
	private void saveProfile() {
		String n = profileName.text().trim();
		if (dev.solclient.Profiles.save(n)) {
			Social.say("Saved profile \"" + n + "\"", false);
			profileName.clear();
			focus = F_NONE;
		} else {
			Social.say("Use letters, numbers, spaces, _ or - (max 24)", true);
		}
	}

	private void drawProfiles(DrawContext c, int mx, int my, int x, int y, int w, int h, int ix0, int ix1, int iy0, int iy1, int accent) {
		Gfx.text(c, "Profiles", ix0, y + 13, Theme.TEXT);
		Gfx.text(c, "Each profile keeps all mod settings and HUD positions.", ix0 + Gfx.tw("Profiles") + 14, y + 13, Theme.DIM);
		String active = dev.solclient.Profiles.active();
		List<String> list = dev.solclient.Profiles.list();
		int rowH = 30, gap = 6, ry = iy0;
		if (list.isEmpty()) {
			Gfx.text(c, "No profiles yet: save the current settings below.", ix0, ry + 8, Theme.DIM);
			ry += 30;
		}
		for (String p : list) {
			if (ry + rowH > iy1 - 40) {
				break;
			}
			boolean act = p.equals(active);
			int rw = ix1 - ix0;
			boolean hov = in(mx, my, ix0, ry, rw, rowH);
			float a = anim("prof" + p, hov);
			Gfx.rrectBorder(c, ix0, ry, rw, rowH, 9, Gfx.blend(TILE, TILE_HOVER, a), act ? accent : Theme.CARD_BORDER);
			Gfx.textMid(c, p, ix0 + 12, ry, rowH, Theme.TEXT);
			if (act) {
				Gfx.text(c, "ACTIVE", ix0 + 20 + Gfx.tw(p), ry + 11, accent);
			}
			int bw = 60, bx = ix0 + rw - 30 - bw;
			if (!act) {
				pill(c, mx, my, bx, ry + 5, bw, 20, 6, "pl" + p, false, 0, accent, Theme.accentHover());
				Gfx.textFit(c, "Load", bx + bw / 2, bw - 6, ry + 11, 0xFFFFFFFF);
				region(bx, ry + 5, bw, 20, () -> {
					dev.solclient.Profiles.load(p);
					Social.say("Loaded profile \"" + p + "\"", false);
				});
			} else {
				pill(c, mx, my, bx, ry + 5, bw, 20, 6, "ps" + p, false, 0, Theme.OFF_BG, Theme.OFF_BG_HOVER);
				Gfx.textFit(c, "Update", bx + bw / 2, bw - 6, ry + 11, Theme.TEXT);
				region(bx, ry + 5, bw, 20, () -> {
					dev.solclient.Profiles.save(p);
					Social.say("Updated \"" + p + "\"", false);
				});
			}
			int dx = ix0 + rw - 24, dy = ry + 7;
			boolean ask = confirming("pdel" + p);
			boolean hx = in(mx, my, dx, dy, 16, 16);
			Gfx.rrect(c, dx, dy, 16, 16, 5, ask ? Theme.RED : hx ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			drawX(c, dx + 4, dy + 4, 7, ask ? 0xFFFFFFFF : hx ? Theme.RED : Theme.DIM);
			region(dx, dy, 16, 16, () -> {
				if (confirm("pdel" + p, "Click X again to delete " + p)) {
					dev.solclient.Profiles.delete(p);
				}
			});
			ry += rowH + gap;
		}
		// save as new profile
		int fy = iy1 - 30, bw = Gfx.tw("Save As New Profile") + 24;
		int fw = ix1 - ix0 - bw - 8;
		field(c, ix0, fy, fw, 24, profileName, "Profile name (e.g. UHC, Arena PvP)", focus == F_PROFILE, accent, false);
		region(ix0, fy, fw, 24, () -> focus = F_PROFILE);
		pill(c, mx, my, ix0 + fw + 8, fy, bw, 24, 7, "psave", false, 0, accent, Theme.accentHover());
		Gfx.textFit(c, "Save As New Profile", ix0 + fw + 8 + bw / 2, bw - 6, fy + 8, 0xFFFFFFFF);
		region(ix0 + fw + 8, fy, bw, 24, this::saveProfile);
		maxScroll = 0;
	}


	// ------------------------------------------------------------- add-ons (extra features that are not mods)
	private void drawAddons(DrawContext c, int mx, int my, int x, int y, int w, int h, int ix0, int ix1, int iy0, int iy1, int accent) {
		Gfx.text(c, "Add-ons", ix0, y + 13, Theme.TEXT);
		Gfx.text(c, "Extra features of the client. More coming.", ix0 + Gfx.tw("Add-ons") + 14, y + 13, Theme.DIM);
		if (embedded != null && embeddedAddon) {
			drawEmbedded(c, mx, my, ix0, ix1, iy0, iy1);
			maxScroll = 0;
			return;
		}
		List<dev.solclient.addons.Addon> list = dev.solclient.addons.Addons.list();
		if (list.isEmpty()) {
			Gfx.text(c, "PvP Practice, the Kit Editor and Bridge Practice are in the Practice extension.", ix0, iy0 + 4, Theme.DIM);
			int bw = 150;
			KuboButton.primary(c, ix0, iy0 + 20, bw, 22, "Open Extensions", in(mx, my, ix0, iy0 + 20, bw, 22));
			region(ix0, iy0 + 20, bw, 22, () -> Gfx.mc().setScreen(openExtensions()));
		}
		// a grid of cards: a coloured banner with the add-on's icon, its name, a short text and one button
		int rw = ix1 - ix0, gap = 10, cols = rw >= 760 ? 3 : 2;
		int cw = (rw - (cols - 1) * gap) / cols, ch = 142;
		for (int i = 0; i < list.size(); i++) {
			dev.solclient.addons.Addon a2 = list.get(i);
			int cx2 = ix0 + (i % cols) * (cw + gap), cy2 = iy0 + (i / cols) * (ch + gap);
			if (cy2 + ch > iy1) {
				break;
			}
			boolean hov = in(mx, my, cx2, cy2, cw, ch);
			float an = anim("addon" + a2.id(), hov);
			Gfx.rrectBorder(c, cx2, cy2, cw, ch, 8, Gfx.blend(TILE, TILE_HOVER, an), hov ? accent : Theme.CARD_BORDER);
			int tagCol = switch (a2.tag()) {
				case "PvP" -> 0xFFD6525F;
				case "Training" -> 0xFF4F8CFF;
				case "TikTok" -> 0xFFEE5FA7;
				default -> accent;
			};
			// banner
			Gfx.rrect(c, cx2 + 6, cy2 + 6, cw - 12, 50, 6, Gfx.blend(0xFF15161B, tagCol, 0.28F + 0.10F * an));
			Gfx.rrect(c, cx2 + 14, cy2 + 13, 36, 36, 8, Gfx.blend(0xFF0D0E12, tagCol, 0.35F));
			if (!a2.icon().isEmpty()) {
				Gfx.tex(c, Theme.icon2(a2.icon()), cx2 + 20, cy2 + 19, 24, 24, 64, 64, 0xFFFFFFFF);
			}
			Gfx.text(c, a2.name(), cx2 + 58, cy2 + 15, Theme.TEXT);
			int tw = Gfx.tw(a2.tag()) + 10;
			Gfx.rrect(c, cx2 + 58, cy2 + 30, tw, 14, 7, Gfx.blend(0xFF0D0E12, tagCol, 0.5F));
			Gfx.text(c, a2.tag(), cx2 + 63, cy2 + 33, 0xFFFFFFFF);
			if (a2.id().equals("ranked_pvp")) {
				String bi = dev.solclient.Lang.tr("Built in", "Incluído");
				int bw2 = Gfx.tw(bi) + 10;
				Gfx.rrect(c, cx2 + cw - 6 - 8 - bw2, cy2 + 14, bw2, 14, 7, Theme.GREEN_BG);
				Gfx.text(c, bi, cx2 + cw - 6 - 8 - bw2 + 5, cy2 + 17, Theme.GREEN);
			}
			int ty2 = cy2 + 64;
			for (String l : wrap(a2.description(), cw - 24, 2)) {
				Gfx.text(c, l, cx2 + 12, ty2, Theme.DIM);
				ty2 += 11;
			}
			int by2 = cy2 + ch - 30;
			pill(c, mx, my, cx2 + 10, by2, cw - 20, 22, 7, "addonopen" + a2.id(), false, 0, accent, Theme.accentHover());
			Gfx.textFit(c, dev.solclient.Lang.tr("Open", "Abrir"), cx2 + cw / 2, cw - 30, by2 + 7, 0xFFFFFFFF);
			region(cx2, cy2, cw, ch, () -> openAddon(a2));
		}
		maxScroll = 0;
	}

	// ------------------------------------------------------------- locker (shop + inventory)
	// Like Lunar's Locker: categories on the left, a searchable / sortable grid in the middle and a preview on the right
	// with your player and the Equip / Buy button. "Inventory" = what you have (every emote of the client + cosmetics you
	// own), "Shop" = the Solar site's shop (cosmetics and emotes, bought with coins).
	private static boolean lockerShop;
	/** Locker third tab: the Store (crates and coin chests). */
	private static boolean lockerStore;
	private final Crates.Host crateHost = (x, y, w, h, r) -> region(x, y, w, h, r);
	private static String invCat = "Emotes";
	private static int lockerSort; // 0 default, 1 A-Z, 2 Z-A, 3 price
	private static String lockerSel;
	private static int lockerSlot = -1; // wheel slot picked in the preview (-1 = first free)
	private final TextBox lockerSearch = new TextBox(32);
	private String lastClickId;
	private long lastClickAt;
	private static final String[] SORTS = {"Default", "A-Z", "Z-A", "Price"};
	private static final String[] INV_CATS = {"Emotes", "Skins", "Cosmetics"};
	private final TextBox skinInput = new TextBox(300);

	/** Opens the Locker on the emotes of the inventory with one wheel slot picked (empty slot clicked on the wheel). */
	public static SolMenuScreen openEmoteSlot(int slot) {
		SolMenuScreen m = open(VIEW_SHOP);
		lockerShop = false;
		invCat = "Emotes";
		lockerSlot = slot;
		lockerSel = null;
		return m;
	}

	private boolean doubleClick(String id) {
		long now = System.currentTimeMillis();
		boolean d = id.equals(lastClickId) && now - lastClickAt < 350L;
		lastClickId = id;
		lastClickAt = now;
		return d;
	}

	private void drawStoreTab(DrawContext c, int mx, int my, int ix0, int ix1, int iy0, int iy1) {
		int top = iy0 - 4;
		int contentH = Crates.storeHeight();
		maxScroll = Math.max(0, contentH - (iy1 - top));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int listX1 = maxScroll > 0 ? ix1 - 8 : ix1;
		clipY0 = top;
		clipY1 = iy1;
		c.enableScissor(ix0 - 4, top, ix1 + 4, iy1);
		Crates.drawStore(c, mx, my, ix0, top - Math.round(scroll), listX1 - ix0, top, iy1, crateHost);
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, ix1 + 3, top, iy1, contentH);
		Crates.drawInfo(c, mx, my, ix0 - 4, top, ix1 - ix0 + 8, iy1 - top, crateHost);
	}

	private void drawShop(DrawContext c, int mx, int my, int x, int y, int w, int h, int ix0, int ix1, int iy0, int iy1, int barX, int accent) {
		// ---- header: title, Inventory / Shop tabs, coins
		Gfx.text(c, "Locker", ix0, y + 13, Theme.TEXT);
		int tx = ix0 + Gfx.tw("Locker") + 14;
		String[] tabs = {"Inventory", "Shop", "Chests"};
		for (int i = 0; i < 3; i++) {
			int tw = Gfx.tw(tabs[i]) + 20;
			boolean sel = i == 2 ? lockerStore : !lockerStore && lockerShop == (i == 1);
			int col = navItem(c, mx, my, tx, y + 8, tw, 20, "ltab" + i, sel);
			Gfx.textFit(c, tabs[i], tx + tw / 2, tw - 6, y + 14, col);
			final boolean shop = i == 1;
			if (i == 2) {
				region(tx, y + 8, tw, 20, () -> {
					lockerStore = true;
					lockerShop = true;
					lockerSel = null;
					scroll = 0;
					scrollTarget = 0;
				});
			} else {
				region(tx, y + 8, tw, 20, () -> switchLocker(shop));
			}
			tx += tw + 4;
		}
		// what you are looking at: Cosmetics / Emotes / Skin, centred on top (like LabyMod)
		if (!lockerStore) {
			String[] kinds = {"Cosmetics", "Emotes", "Kill Effects", "Skin"};
			boolean emotesNow = lockerShop ? "Emotes".equalsIgnoreCase(Social.shopCategory) : invCat.equals("Emotes");
			boolean killNow = lockerShop ? "Kill Effects".equalsIgnoreCase(Social.shopCategory) : invCat.equals("Kill Effects");
			boolean skinNow = !lockerShop && invCat.equals("Skins");
			int cur = skinNow ? 3 : killNow ? 2 : emotesNow ? 1 : 0;
			int segW = 0;
			int[] ws = new int[4];
			for (int i = 0; i < 4; i++) {
				ws[i] = Math.max(64, Gfx.tw(kinds[i]) + 24);
				segW += ws[i];
			}
			int sxx = Math.max(tx + 8, (ix0 + ix1) / 2 - (segW + 6) / 2), syy = y + 7; // never on top of Inventory / Shop
			Gfx.rrectBorder(c, sxx, syy, segW + 6, 22, 4, Theme.FIELD, Theme.CARD_BORDER);
			int kx = sxx + 3;
			for (int i = 0; i < 4; i++) {
				boolean sel = i == cur, hov = in(mx, my, kx, syy + 3, ws[i], 16);
				if (sel) {
					Gfx.rrect(c, kx, syy + 3, ws[i], 16, 3, SlideBg.colour());
				} else if (hov) {
					Gfx.rrect(c, kx, syy + 3, ws[i], 16, 3, 0x0CFFFFFF);
				}
				Gfx.textFit(c, kinds[i], kx + ws[i] / 2, ws[i] - 6, syy + 7, sel ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
				final int idx = i;
				region(kx, syy + 3, ws[i], 16, () -> lockerKind(idx));
				kx += ws[i];
			}
		}
		int rx = ix1;
		if (Social.me != null) {
			// (the daily coin reward was removed in v99)
			if (Social.coins >= 0) {
				String ct = coinsText(Social.coins);
				int cw = Gfx.tw(ct) + 26;
				rx -= cw;
				Gfx.rrectBorder(c, rx, y + 8, cw, 20, 7, TILE, Theme.CARD_BORDER);
				Gfx.coin(c, rx + 5, y + 13, 10);
				Gfx.textMid(c, ct, rx + 18, y + 8, 20, Theme.TEXT);
			}
		}

		// ---- the Store: crates and coin chests instead of the item grid
		if (lockerStore) {
			drawStoreTab(c, mx, my, ix0, ix1, iy0, iy1);
			return;
		}

		// ---- layout
		int sideW = 104, prevW = Math.min(170, (ix1 - ix0) / 3);
		int sx = ix0, gx0 = ix0 + sideW + 10, px = ix1 - prevW, gx1 = px - 10;

		// ---- sidebar
		Gfx.rrectBorder(c, sx, iy0, sideW, iy1 - iy0, 10, TILE, Theme.CARD_BORDER);
		List<String> cats = lockerShop ? shopCats() : List.of();
		String curCat = lockerShop ? Social.shopCategory : invCat;
		if (!lockerShop) {
			lockerSideList(c, mx, my, sx, iy0, sideW, iy1 - iy0 - 34);
		} else {
			Gfx.textRaw(c, dev.solclient.Lang.t("Sections").toUpperCase(Locale.ROOT), sx + 10, iy0 + 9, 0xFF6B7080);
		}
		int cy = iy0 + 22;
		int lastGroup = -1;
		for (String cat : cats) {
			// two groups with a small heading: browsing, then the cosmetic types (each with how many items it has)
			int group = cat.equalsIgnoreCase("All") || cat.equalsIgnoreCase("Featured") || cat.equalsIgnoreCase("Sale") || cat.equalsIgnoreCase("Emotes") ? 0 : 1;
			if (lockerShop && group != lastGroup) {
				if (group == 1 && cy + 40 > iy1 - 34) {
					break;
				}
				if (group == 1) {
					cy += 4;
					Gfx.textRaw(c, dev.solclient.Lang.t("Cosmetics").toUpperCase(Locale.ROOT), sx + 10, cy, 0xFF6B7080);
					cy += 14;
				}
				lastGroup = group;
			}
			if (cy + 22 > iy1 - 34) {
				break;
			}
			boolean sel = cat.equals(curCat);
			int col = navItem(c, mx, my, sx + 5, cy, sideW - 10, 22, "lcat" + cat, sel);
			String label = lockerShop && cat.equals("Emotes") ? "All Emotes" : cat;
			int cnt = !lockerShop ? -1 : cat.equals("Emotes") ? EmoteLibrary.all().size() : cat.equalsIgnoreCase("Featured") || cat.equalsIgnoreCase("Sale") ? -1 : Social.countIn(cat);
			String cs = cnt >= 0 ? String.valueOf(cnt) : "";
			int cw2 = cs.isEmpty() ? 0 : Gfx.tw(cs) + 6;
			Gfx.textMid(c, fit(label, sideW - 26 - cw2), sx + 14, cy, 22, col);
			if (!cs.isEmpty()) {
				Gfx.textMid(c, cs, sx + sideW - 14 - Gfx.tw(cs), cy, 22, Theme.DIM);
			}
			region(sx + 5, cy, sideW - 10, 22, () -> {
				deathSel = null;
				if (lockerShop) {
					Social.loadShop(cat);
				} else {
					invCat = cat;
					if (cat.equals("Cosmetics")) {
						Social.loadShop("All");
					}
				}
				lockerSel = null;
				scroll = 0;
				scrollTarget = 0;
			});
			cy += 25;
		}
		// bottom: jump between inventory and shop
		String jump = lockerShop ? "My Inventory" : "Shop Cosmetics";
		{
			int jx = sx + 5, jy = iy1 - 29, jw = sideW - 10, jh = 24;
			boolean jh2 = in(mx, my, jx, jy, jw, jh);
			float ja = anim("ljump", jh2);
			Gfx.rrect(c, jx, jy, jw, jh, 4, Gfx.blend(Theme.ROW, TILE_HOVER, ja));
			Gfx.textFit(c, jump, jx + jw / 2, jw - 6, jy + 8, Gfx.blend(Theme.DIM, Theme.TEXT, 0.6F + 0.4F * ja));
			region(jx, jy, jw, jh, () -> switchLocker(!lockerShop));
		}

		// ---- search + sort (skins: add by nick / URL / file instead)
		int fy = iy0;
		int sortW = 74;
		boolean skins = !lockerShop && invCat.equals("Skins");
		if (skins) {
			drawSkins(c, mx, my, gx0, gx1, fy, iy1, px, iy0, prevW, accent);
			return;
		}
		int daW = 0;
		field(c, gx0, fy, gx1 - gx0 - sortW - 6 - daW, 22, lockerSearch, "Search " + (lockerShop ? "the shop" : invCat.toLowerCase(Locale.ROOT)) + "...", focus == F_LOCKER, accent, true);
		region(gx0, fy, gx1 - gx0 - sortW - 6 - daW, 22, () -> focus = F_LOCKER);
		int nSorts = lockerShop ? 4 : 3;
		lockerSort %= nSorts;
		button(c, mx, my, gx1 - sortW, fy, sortW, 22, "Sort: " + SORTS[lockerSort], "lsort", TILE, TILE_HOVER, Theme.TEXT, () -> lockerSort = (lockerSort + 1) % nSorts);
		int gy0 = fy + 30;
		boolean chipsRow = true;
		if (chipsRow) {
			gy0 += 20; // filter chips go here (drawn below, once the items are known)
		}

		// ---- items
		boolean shopEmotes = lockerShop && "Emotes".equalsIgnoreCase(Social.shopCategory);
		boolean emotes = shopEmotes || !lockerShop && invCat.equals("Emotes");
		List<EmoteLibrary.Info> emoteList = new ArrayList<>();
		List<Social.Item> items = new ArrayList<>();
		String q = lockerSearch.text().toLowerCase(Locale.ROOT).trim();
		if (emotes) {
			for (EmoteLibrary.Info e : shopEmotes ? EmoteLibrary.all() : EmoteLibrary.mine()) {
				if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)) {
					emoteList.add(e);
				}
			}
			if (lockerSort == 1) {
				emoteList.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
			} else if (lockerSort == 2) {
				emoteList.sort((a, b) -> b.name().compareToIgnoreCase(a.name()));
			} else if (lockerSort == 3) {
				emoteList.sort((a, b) -> Long.compare(emotePrice(a), emotePrice(b)));
			}
		} else {
			boolean killTab = !lockerShop && invCat.equals("Kill Effects");
			// the free built-in cosmetics (auras, minimes) are there even without the site: only an empty list shows "offline"
			if (!(killTab && !Social.sellsKillEffects()) && Social.shop.isEmpty() && drawOffline(c, mx, my, (gx0 + ix1) / 2, (gy0 + iy1) / 2, gx1 - gx0 - 20)) {
				maxScroll = 0;
				return;
			}
			for (Social.Item it : killTab ? Social.killEffectItems() : Social.shop) {
				if (!lockerShop && !(it.owned && it.wearable())) {
					continue;
				}
				// kill effects have their own tab in the inventory
				if (!lockerShop && killTab != Social.isKillEffect(it)) {
					continue;
				}
				// emotes have their own "All Emotes" section: the other sections show only accessories
				if (lockerShop && it.category != null && (it.category.equalsIgnoreCase("Emotes") || it.category.equalsIgnoreCase("Emojis"))) {
					continue;
				}
				if (Social.isDeathItem(it)) {
					continue; // drawn by the death animation cards below
				}
				if (q.isEmpty() || it.name.toLowerCase(Locale.ROOT).contains(q)) {
					items.add(it);
				}
			}
			// chips: one per type of accessory with how many there are (All 37, Hats 3, Wings 5...)
			java.util.LinkedHashMap<String, Integer> kindsCount = new java.util.LinkedHashMap<>();
			for (Social.Item it : items) {
				String k = it.category == null || it.category.isBlank() ? "Other" : it.category;
				kindsCount.merge(k, 1, Integer::sum);
			}
			int chx = gx0, chy = gy0 - 20;
			int allN = items.size();
			String[] chipNames = new String[kindsCount.size() + 1];
			int[] chipN = new int[chipNames.length];
			chipNames[0] = null;
			chipN[0] = allN;
			int ci = 1;
			for (var e : kindsCount.entrySet()) {
				chipNames[ci] = e.getKey();
				chipN[ci++] = e.getValue();
			}
			if (lockerChip != null && !kindsCount.containsKey(lockerChip)) {
				lockerChip = null;
			}
			for (int k = 0; k < chipNames.length; k++) {
				String label = k == 0 ? dev.solclient.Lang.t("All") : dev.solclient.Lang.t(chipNames[k]);
				String n = String.valueOf(chipN[k]);
				int cwid = Gfx.tw(label) + Gfx.twRaw(n) + 18;
				if (chx + cwid > gx1 - 90) {
					break;
				}
				boolean sel = java.util.Objects.equals(lockerChip, chipNames[k]);
				boolean hov = in(mx, my, chx, chy, cwid, 15);
				Gfx.rrectBorder(c, chx, chy, cwid, 15, 3, sel ? SlideBg.colour() : hov ? TILE_HOVER : TILE, sel ? 0xFFB6BAC8 : Theme.CARD_BORDER);
				Gfx.text(c, label, chx + 6, chy + 4, sel ? 0xFFFFFFFF : Theme.TEXT);
				Gfx.textRaw(c, n, chx + 6 + Gfx.tw(label) + 5, chy + 4, Theme.DIM);
				final String chip = chipNames[k];
				region(chx, chy, cwid, 15, () -> {
					lockerChip = chip;
					scroll = 0;
					scrollTarget = 0;
				});
				chx += cwid + 4;
			}
			if (!lockerShop) {
				// "Only on" (equipped only)
				String on = "Only on";
				int ow = Gfx.tw(on) + 22, ox = gx1 - ow;
				boolean hov = in(mx, my, ox, chy, ow, 15);
				Gfx.rrectBorder(c, ox, chy, ow, 15, 3, hov ? TILE_HOVER : TILE, Theme.CARD_BORDER);
				CheckBox.draw(c, "lonlyon", ox + 4, chy + 3, 9, lockerOnlyOn, hov);
				Gfx.text(c, on, ox + 16, chy + 4, Theme.TEXT);
				region(ox, chy, ow, 15, () -> lockerOnlyOn = !lockerOnlyOn);
			}
			if (lockerChip != null) {
				items.removeIf(it -> !lockerChip.equals(it.category == null || it.category.isBlank() ? "Other" : it.category));
			}
			if (lockerOnlyOn && !lockerShop) {
				items.removeIf(it -> !it.equipped);
			}
			if (lockerSort == 1) {
				items.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
			} else if (lockerSort == 2) {
				items.sort((a, b) -> b.name.compareToIgnoreCase(a.name));
			} else if (lockerSort == 3) {
				items.sort((a, b) -> Long.compare(a.finalPrice, b.finalPrice));
			}
		}
		// the death animations live in the Kill Effects section too (same grid, equip like any kill effect)
		boolean killSec = !emotes && (lockerShop ? "Kill Effects".equalsIgnoreCase(Social.shopCategory) : invCat.equals("Kill Effects"));
		List<dev.solclient.fx.DeathAnimBridge.Entry> deaths = new ArrayList<>();
		if (killSec && dev.solclient.fx.DeathAnimBridge.available()) {
			dev.solclient.fx.DeathAnimBridge.enforce();
			for (var e : dev.solclient.fx.DeathAnimBridge.all()) {
				boolean own = dev.solclient.fx.DeathAnimBridge.owned(e);
				if (!lockerShop && !own) {
					continue; // the inventory shows what you have
				}
				if (!q.isEmpty() && !e.title.toLowerCase(Locale.ROOT).contains(q)) {
					continue;
				}
				if (lockerOnlyOn && !lockerShop && !dev.solclient.fx.DeathAnimBridge.isEquipped(e)) {
					continue;
				}
				deaths.add(e);
			}
			if (lockerSort == 1) {
				deaths.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
			} else if (lockerSort == 2) {
				deaths.sort((a, b) -> b.title.compareToIgnoreCase(a.title));
			} else {
				deaths.sort((a, b) -> Boolean.compare(dev.solclient.fx.DeathAnimBridge.owned(b), dev.solclient.fx.DeathAnimBridge.owned(a)));
			}
		} else {
			deathSel = null;
			dev.solclient.fx.DeathAnimBridge.hover(null);
		}
		int nItems = items.size();
		int count = emotes ? emoteList.size() : nItems + deaths.size();

		// ---- preview (right)
		dev.solclient.fx.DeathAnimBridge.Entry deathCur = deathHover;
		if (deathCur == null && deathSel != null) {
			for (var e : deaths) {
				if (e.id.equals(deathSel)) {
					deathCur = e;
				}
			}
		}
		if (killSec && deathCur != null && (deathHover != null || lockerSel == null)) {
			drawDeathPanel(c, mx, my, px, iy0, prevW, iy1 - iy0 + iy0, accent, deathCur);
		} else {
			drawLockerPreview(c, mx, my, px, iy0, prevW, iy1 - iy0, accent, emotes, emoteList, items);
		}
		deathHover = null;

		if (count == 0) {
			String msg = emotes ? "No emotes found"
					: !Social.shopState.isEmpty() ? Social.shopState
					: lockerShop ? "Nothing here" : "You don't own any cosmetics yet";
			if (!emotes && "Loading...".equals(Social.shopState)) {
				c.enableScissor(gx0 - 4, gy0, gx1 + 4, iy1);
				Gfx.skeletonCards(c, gx0, gy0, gx1, iy1, 6, 80);
				c.disableScissor();
			} else {
				Gfx.textC(c, msg, (gx0 + gx1) / 2, (gy0 + iy1) / 2 - 4, Theme.DIM);
			}
			if (!lockerShop && !emotes && Social.shopState.isEmpty()) {
				int bw = Gfx.tw("Shop Cosmetics") + 26;
				button(c, mx, my, (gx0 + gx1) / 2 - bw / 2, (gy0 + iy1) / 2 + 10, bw, 22, "Shop Cosmetics", "lempty", accent, Theme.accentHover(), 0xFFFFFFFF, () -> switchLocker(true));
			}
			maxScroll = 0;
			return;
		}

		// ---- grid
		int gap = 6, minW = 80;
		int inner = gx1 - gx0;
		int cols = Math.max(1, (inner + gap) / (minW + gap));
		int cardW = (inner - (cols - 1) * gap) / cols;
		int cardH = cardW + 6;
		count = emotes ? emoteList.size() : nItems + deaths.size();
		int rows = (count + cols - 1) / cols;
		int contentH = Math.max(0, rows * (cardH + gap) - gap) + 10;
		maxScroll = Math.max(0, contentH - (iy1 - gy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		clipY0 = gy0;
		clipY1 = iy1;
		c.enableScissor(gx0 - 4, gy0, gx1 + 4, iy1);
		for (int i = 0; i < count; i++) {
			int cx = gx0 + (i % cols) * (cardW + gap);
			int cyy = gy0 + (i / cols) * (cardH + gap) - Math.round(scroll);
			if (cyy + cardH < gy0 || cyy > iy1) {
				continue;
			}
			if (emotes) {
				drawEmoteCard(c, mx, my, emoteList.get(i), cx, cyy, cardW, cardH, accent);
			} else if (i < nItems) {
				drawItemCard(c, mx, my, items.get(i), cx, cyy, cardW, cardH, accent);
			} else {
				drawDeathCard(c, mx, my, deaths.get(i - nItems), cx, cyy, cardW, cardH, accent);
			}
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, gx1 + 3, gy0, iy1, contentH);
	}

	// ------------------------------------------------------------- locker: skin changer
	private String skinSel;

	private void pickSkinFile() {
		pickSkinFileThen(false);
	}

	/** equip = true: the PNG is also put on at once (Mojang for Microsoft accounts, the Caos site for the others). */
	private void pickSkinFileThen(boolean equip) {
		Thread t = new Thread(() -> {
			String path;
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.png"));
				filters.flip();
				path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Choose a skin (64x64 PNG)", "", filters, "PNG images", false);
			} catch (Throwable e) {
				Social.say("No file picker: drag the PNG onto the window", true);
				return;
			}
			if (path != null && !path.isBlank()) {
				dev.solclient.skin.SkinChanger.addFile(java.nio.file.Path.of(path), equip);
			}
		}, "plegumo-skin-picker");
		t.setDaemon(true);
		t.start();
	}

	private void drawSkins(DrawContext c, int mx, int my, int gx0, int gx1, int fy, int iy1, int px, int py, int prevW, int accent) {
		// add bar: "Add a skin by username or URL..." [Add] [File]
		int addW = 46, fileW = 46;
		int fw = gx1 - gx0 - addW - fileW - 12;
		field(c, gx0, fy, fw, 22, skinInput, "Add a skin by username or URL...", focus == F_SKIN, accent, false);
		region(gx0, fy, fw, 22, () -> focus = F_SKIN);
		button(c, mx, my, gx0 + fw + 6, fy, addW, 22, dev.solclient.skin.SkinChanger.busy ? "..." : "+ Add", "skadd", accent, Theme.accentHover(), 0xFFFFFFFF, () -> {
			dev.solclient.skin.SkinChanger.add(skinInput.text());
			skinInput.clear();
		});
		button(c, mx, my, gx1 - fileW, fy, fileW, 22, "File", "skfile", TILE, TILE_HOVER, Theme.TEXT, this::pickSkinFile);
		int gy0 = fy + 30;

		List<dev.solclient.skin.SkinChanger.Skin> list = dev.solclient.skin.SkinChanger.all();
		drawSkinPreview(c, mx, my, px, py, prevW, iy1 - py, accent, list);
		if (list.isEmpty()) {
			Gfx.textC(c, "No skins yet", (gx0 + gx1) / 2, (gy0 + iy1) / 2 - 16, Theme.TEXT);
			Gfx.textC(c, "Type a nick or paste a link above,", (gx0 + gx1) / 2, (gy0 + iy1) / 2, Theme.DIM);
			Gfx.textC(c, "or drop a PNG onto the window", (gx0 + gx1) / 2, (gy0 + iy1) / 2 + 11, Theme.DIM);
			maxScroll = 0;
			return;
		}
		int gap = 7, minW = 74;
		int inner = gx1 - gx0;
		int cols = Math.max(1, (inner + gap) / (minW + gap));
		int cardW = (inner - (cols - 1) * gap) / cols;
		int cardH = Math.round(cardW * 1.3F) + 14;
		int rows = (list.size() + cols - 1) / cols;
		int contentH = Math.max(0, rows * (cardH + gap) - gap) + 10;
		maxScroll = Math.max(0, contentH - (iy1 - gy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		clipY0 = gy0;
		clipY1 = iy1;
		c.enableScissor(gx0 - 4, gy0, gx1 + 4, iy1);
		dev.solclient.skin.SkinChanger.Skin active = dev.solclient.skin.SkinChanger.active();
		for (int i = 0; i < list.size(); i++) {
			int cx = gx0 + (i % cols) * (cardW + gap);
			int cy = gy0 + (i / cols) * (cardH + gap) - Math.round(scroll);
			if (cy + cardH < gy0 || cy > iy1) {
				continue;
			}
			dev.solclient.skin.SkinChanger.Skin sk = list.get(i);
			boolean hov = in(mx, my, cx, cy, cardW, cardH) && my >= clipY0 && my < clipY1;
			float a = anim("sk" + sk.id, hov);
			boolean eq = sk == active;
			boolean sel = sk.id.equals(skinSel);
			Gfx.rrectBorder(c, cx, cy, cardW, cardH, 9, eq ? Gfx.blend(TILE, 0xFF1B5A2A, 0.55F) : Gfx.blend(TILE, TILE_HOVER, a),
					sel ? accent : eq ? 0xFF3FA35A : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
			RemoteTextures.Tex t = sk.tex();
			if (t != null) {
				float pxs = (cardH - 34) / 20F;
				SkinModel3D.draw(c, t, cx + cardW / 2F, cy + 6, pxs, 25 + a * 20, 30 + a * 20, 8);
			}
			Gfx.textFit(c, fit(sk.name, cardW - 8), cx + cardW / 2, cardW - 6, cy + cardH - (eq ? 24 : 14), Theme.TEXT);
			if (eq) {
				Gfx.textFit(c, "Equipped", cx + cardW / 2, cardW - 6, cy + cardH - 13, Theme.GREEN);
			}
			region(cx, cy, cardW, cardH, b -> {
				if (b == 1) {
					if (confirm("delskin" + sk.id, "Right click again to delete " + sk.name)) {
						dev.solclient.skin.SkinChanger.remove(sk);
					}
					return;
				}
				skinSel = sk.id;
				if (doubleClick("sk" + sk.id)) {
					dev.solclient.skin.SkinChanger.equip(sk);
				}
			}, null);
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, gx1 + 3, gy0, iy1, contentH);
	}

	private void drawSkinPreview(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent, List<dev.solclient.skin.SkinChanger.Skin> list) {
		Gfx.rrectBorder(c, x, y, w, h, 10, Gfx.blend(TILE, 0xFF000000, 0.25F), Theme.CARD_BORDER);
		dev.solclient.skin.SkinChanger.Skin sel = null;
		for (dev.solclient.skin.SkinChanger.Skin sk : list) {
			if (sk.id.equals(skinSel)) {
				sel = sk;
			}
		}
		dev.solclient.skin.SkinChanger.Skin active = dev.solclient.skin.SkinChanger.active();
		RemoteTextures.Tex t = sel != null ? sel.tex() : dev.solclient.skin.SkinChanger.myPreview();
		// the whole player in real 3D (Minecraft's own player model, hat layer and slim arms included), drag to turn
		float px = Math.max(1.6F, Math.min(5F, Math.min((h - 150) / 36F, (w - 16) / 20F)));
		float feet = y + 10 + 34 * px;
		if (t != null) {
			stepPreview();
			float uu = 1F / f;
			boolean slimNow = sel != null ? sel.slim : dev.solclient.skin.SkinChanger.selfSlim();
			c.enableScissor(x + 2, y + 2, x + w - 2, Math.round(feet) + 6);
			if (!dev.solclient.cosmetic.SkinPreview.draw(c, t, slimNow, (x + w / 2F) * uu, feet * uu, px * uu, previewYaw, 6F, null)) {
				SkinModel3D.draw(c, t, x + w / 2F, feet - 32 * px, px, previewYaw, previewYaw + 6, 10);
			}
			c.disableScissor();
			region(x, y, w, Math.round(feet) + 6 - y, b -> previewDragX = null, (mx2, my2) -> {
				long now = System.currentTimeMillis();
				if (previewDragX != null) {
					float d = (float) (mx2 - previewDragX) * 1.1F;
					previewTarget += d;
					float dt = Math.max(0.008F, (now - previewLastDrag) / 1000F);
					previewVel = previewVel * 0.5F + (d / dt) * 0.5F;
				}
				previewLastDrag = now;
				previewDragX = mx2;
			});
		} else {
			Gfx.skeleton(c, x + w / 4, y + 14, w / 2, Math.round(30 * px), 8);
		}
		int ty = Math.round(feet) + 10;
		String msg = dev.solclient.skin.SkinChanger.message;
		if (sel == null) {
			Gfx.textFit(c, active != null ? "Wearing: " + fit(active.name, w - 70) : "Wearing your account's skin", x + w / 2, w - 6, ty, Theme.TEXT);
			Gfx.textFit(c, "Select a skin to preview it", x + w / 2, w - 6, ty + 12, Theme.DIM);
			if (active != null) {
				button(c, mx, my, x + 8, y + h - 30, w - 16, 22, "Use Account Skin", "skreset", TILE, TILE_HOVER, Theme.TEXT,
						dev.solclient.skin.SkinChanger::unequip);
			}
		} else {
			final dev.solclient.skin.SkinChanger.Skin fs = sel;
			Gfx.textFit(c, fit(sel.name, w - 16), x + w / 2, w - 6, ty, Theme.TEXT);
			// arm model
			int half = (w - 20) / 2, my2 = ty + 14;
			pill(c, mx, my, x + 8, my2, half, 18, 6, "skc", !sel.slim, accent, TILE, TILE_HOVER);
			Gfx.textFit(c, "Classic", x + 8 + half / 2, half - 6, my2 + 5, !sel.slim ? 0xFFFFFFFF : Theme.TEXT);
			region(x + 8, my2, half, 18, () -> dev.solclient.skin.SkinChanger.setSlim(fs, false));
			pill(c, mx, my, x + 12 + half, my2, half, 18, 6, "sks", sel.slim, accent, TILE, TILE_HOVER);
			Gfx.textFit(c, "Slim", x + 12 + half + half / 2, half - 6, my2 + 5, sel.slim ? 0xFFFFFFFF : Theme.TEXT);
			region(x + 12 + half, my2, half, 18, () -> dev.solclient.skin.SkinChanger.setSlim(fs, true));
			boolean eq = sel == active;
			button(c, mx, my, x + 8, y + h - 56, w - 16, 22, eq ? "Unequip" : "Equip", "skeq", eq ? Theme.OFF_BG : accent,
					eq ? Theme.OFF_BG_HOVER : Theme.accentHover(), 0xFFFFFFFF, () -> {
						if (eq) {
							dev.solclient.skin.SkinChanger.unequip();
						} else {
							dev.solclient.skin.SkinChanger.equip(fs);
						}
					});
			boolean ask = confirming("delskin" + sel.id);
			button(c, mx, my, x + 8, y + h - 30, w - 16, 22, ask ? "Click again to delete" : "Delete", "skdel", ask ? 0xFF5A1B1F : TILE,
					ask ? 0xFF74232A : TILE_HOVER, ask ? 0xFFFF9A9C : Theme.DIM, () -> {
						if (confirm("delskin" + fs.id, "Click again to delete " + fs.name)) {
							dev.solclient.skin.SkinChanger.remove(fs);
							skinSel = null;
						}
					});
		}
		if (msg != null) {
			int my3 = sel == null ? ty + 30 : ty + 40;
			for (String l : wrap(msg, w - 16, 3)) {
				Gfx.textFit(c, l, x + w / 2, w - 6, my3, dev.solclient.skin.SkinChanger.messageError ? 0xFFFF9A9C : 0xFF9BD5A8);
				my3 += 11;
			}
		}
		String acc = dev.solclient.account.Accounts.currentType();
		Gfx.textFit(c, acc.equals("Microsoft") ? "Everyone sees it after you rejoin" : "Offline: Caos players see it", x + w / 2, w - 6,
				y + h - (sel == null && active == null ? 16 : sel == null ? 42 : 68), 0x66FFFFFF);
	}

	// ------------------------------------------------------------- locker: try-on preview + buy window
	/** Shown yaw follows the target smoothly; after you let go it keeps spinning a little and slows down. */
	private static float previewYaw = 28F, previewTarget = 28F, previewVel;
	private static long previewLastFrame, previewLastDrag;
	private Double previewDragX;

	private static void stepPreview() {
		long now = System.currentTimeMillis();
		float dt = previewLastFrame == 0 ? 0F : Math.min(0.05F, (now - previewLastFrame) / 1000F);
		previewLastFrame = now;
		boolean dragging = now - previewLastDrag < 70L;
		if (!dragging) {
			previewTarget += previewVel * dt;
			previewVel *= (float) Math.exp(-5.0 * dt);
			if (Math.abs(previewVel) < 2F) {
				previewVel = 0F;
			}
		}
		previewVel = Math.max(-900F, Math.min(900F, previewVel));
		previewYaw += (previewTarget - previewYaw) * (1F - (float) Math.exp(-16.0 * dt));
	}
	private Social.Item buyItem;
	private boolean wearAfterBuy = true;
	/** Cape tried on in the preview (null = the one you wear) and the cape item the preview was last turned for. */
	private static dev.solclient.social.CapeMediaLogic.Desc previewCape;
	private static String lastCapeSel;

	/** What the preview wears: your equipped 3D cosmetics, with the selected item in place of the one of its category. */
	private static List<dev.solclient.cosmetic.CosmeticModels.Def> tryOn(Social.Item sel) {
		List<dev.solclient.cosmetic.CosmeticModels.Def> out = new ArrayList<>();
		java.util.UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
		if (me != null) {
			out.addAll(dev.solclient.cosmetic.Cosmetics.worn(me));
		}
		dev.solclient.cosmetic.CosmeticModels.Def d = dev.solclient.cosmetic.Variants.resolve(dev.solclient.cosmetic.Cosmetics.modelFor(sel));
		if (d != null) {
			out.removeIf(o -> o.category().equalsIgnoreCase(d.category()) || o.id().equals(d.id()));
			out.add(d);
		}
		return out;
	}

	/** Right panel for the shop / cosmetics: your whole skin in 3D wearing the selected item (drag to turn it). */
	private void drawWearPreview(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent, List<Social.Item> items, RemoteTextures.Tex skin) {
		Social.Item it = null;
		for (Social.Item i : items) {
			if (i.id != null && i.id.equals(lockerSel)) {
				it = i;
			}
		}
		int infoH = it == null ? 34 : 92;
		int areaY = y + 8, areaH = h - infoH - 14;
		List<dev.solclient.cosmetic.CosmeticModels.Def> wear = it == null ? new ArrayList<>(tryOnNone()) : tryOn(it);
		// shop capes have no 3D model: they are a texture put on the real cape of the preview player
		boolean isCape = it != null && it.category != null && it.category.equalsIgnoreCase("Capes");
		String capeId = isCape ? it.id : null;
		if (!java.util.Objects.equals(capeId, lastCapeSel)) {
			// picking a cape turns the player to show his back (that is where the cape is); leaving it turns him round again
			float base = isCape ? 208F : 28F;
			previewTarget = base + 360F * Math.round((previewTarget - base) / 360F);
			previewVel = 0F;
			lastCapeSel = capeId;
		}
		previewCape = isCape ? dev.solclient.social.CapeMedia.forItem(it) : null;
		if (it != null && Social.isKillEffect(it)) {
			itemImage(c, it, x + 8, areaY + 4, w - 16, areaH - 8); // a kill effect: its picture, big
		} else {
			drawPosedModel(c, x, areaY, w, areaH, wear, true, skin);
		}
		previewCape = null;
		// item without a 3D model (cape, emote...): its picture in the corner
		if (it != null && !Social.isKillEffect(it) && dev.solclient.cosmetic.Cosmetics.modelFor(it) == null) {
			itemImage(c, it, x + 8, areaY + 4, 40, 40);
		}
		Gfx.fill(c, x + 10, areaY + areaH + 2, x + w - 10, areaY + areaH + 3, LINE);

		int ty = areaY + areaH + 9;
		if (it == null) {
			Gfx.textFit(c, lockerShop ? "Click an item to try it on" : "Select a cosmetic", x + w / 2, w - 6, ty + 4, Theme.DIM);
			return;
		}
		Gfx.textFit(c, fit(it.name, w - 16), x + w / 2, w - 6, ty, Theme.TEXT);
		String sub = it.category == null ? "" : it.category;
		Gfx.textFit(c, fit(sub, w - 16), x + w / 2, w - 6, ty + 11, Theme.DIM);
		dev.solclient.cosmetic.CosmeticModels.Def def = dev.solclient.cosmetic.Cosmetics.modelFor(it);
		String credit = def != null && !def.credit().isEmpty() ? def.credit().replaceAll(" \\(.*$", "") : null;
		if (credit != null) {
			Gfx.textFit(c, fit(credit, w - 16), x + w / 2, w - 6, ty + 22, 0x66FFFFFF);
		}
		int by = y + h - 30, bx = x + 8, bw = w - 16;
		final Social.Item fi = it;
		if (Social.isKillEffect(it) && Gfx.mc().player != null) {
			button(c, mx, my, bx, by - 28, bw, 22, "View in world", "lviewfx", TILE, TILE_HOVER, Theme.TEXT, () -> viewKillEffect(fi));
		}
		if (it.wearable() && !("verity".equalsIgnoreCase(it.id) || "verity".equalsIgnoreCase(it.modelId))) { // Verity is not an accessory
			cosmeticEditor(c, mx, my, w, x, bx, bw, by, it);
		}
		if (!it.owned) {
			String price = String.valueOf(it.finalPrice);
			boolean afford = Social.coins < 0 || Social.coins >= it.finalPrice;
			button(c, mx, my, bx, by, bw, 22, "Buy  " + price, "lbuy", afford ? accent : Theme.OFF_BG, afford ? Theme.accentHover() : Theme.OFF_BG_HOVER,
					afford ? 0xFFFFFFFF : Theme.DIM, () -> {
						buyItem = fi;
						wearAfterBuy = fi.wearable();
					});
			if (it.discountPct > 0 && it.finalPrice != it.price) {
				String was = String.valueOf(it.price);
				int ww = Gfx.tw(was);
				Gfx.text(c, was, x + w / 2 - ww / 2, by - 12, Theme.DIM);
				c.fill(x + w / 2 - ww / 2 - 1, by - 8, x + w / 2 + ww / 2 + 1, by - 7, Theme.DIM);
			}
		} else if (!it.wearable()) {
			dev.solclient.emote.EmoteLibrary.Info local = localEmote(it);
			if (local != null) {
				button(c, mx, my, bx, by, bw, 22, "Equip on Wheel", "lgoinv", accent, Theme.accentHover(), 0xFFFFFFFF, () -> {
					switchLocker(false);
					invCat = "Emotes";
					lockerSel = local.id();
				});
			} else {
				Gfx.rrect(c, bx, by, bw, 22, 4, Theme.OFF_BG);
				Gfx.textFit(c, "Owned", x + w / 2, w - 6, by + 7, Theme.DIM);
			}
		} else if (it.equipped) {
			button(c, mx, my, bx, by, bw, 22, "Unequip", "luneq", Theme.OFF_BG, Theme.OFF_BG_HOVER, Theme.TEXT, () -> Social.equip(fi, false));
		} else {
			button(c, mx, my, bx, by, bw, 22, "Equip", "leq", accent, Theme.accentHover(), 0xFFFFFFFF, () -> Social.equip(fi, true));
		}
	}

	/** Your player in 3D (real renderer in game, skin model outside), drag to turn; plays the requested emote preview. */
	private void drawPosedModel(DrawContext c, int x, int areaY, int w, int areaH, List<dev.solclient.cosmetic.CosmeticModels.Def> wear, boolean showName,
			RemoteTextures.Tex skin) {
		stepPreview();
		float yawNow = previewYaw;
		// name pill on top, model in the middle, soft floor shadow, "Drag to rotate" at the bottom
		String nick = MinecraftClient.getInstance().getSession().getUsername();
		int ny = areaY + 4;
		if (showName) {
			int nw = Gfx.tw(nick) + 16;
			Gfx.rrect(c, x + w / 2 - nw / 2, ny, nw, 15, 4, 0xFF1C1E25);
			Gfx.textFit(c, nick, x + w / 2, w - 6, ny + 4, 0xFFC9CCD6);
		}
		int mY0 = showName ? ny + 19 : ny, mY1 = areaY + areaH - 16, mH = mY1 - mY0;
		float px = Math.max(2F, Math.min(mH / 37F, (w - 20) / 34F));
		float feet = mY0 + 4 * px + 32 * px;
		// floor shadow (drawn first, under the feet)
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(x + w / 2F, feet);
		c.getMatrices().scale(1F, 0.16F);
		for (int i = 0; i < 6; i++) {
			Gfx.circle(c, 0, 0, px * (13 - i * 1.6F), Gfx.withAlpha(0xFFFFFF, 6 + i * 3));
		}
		c.getMatrices().popMatrix();
		boolean real = false;
		if (MinecraftClient.getInstance().player != null) {
			// real 3D player (same renderer as in game): coordinates of addEntity are screen units, not menu units
			float uu = 1F / f;
			int top3 = Math.round(feet - 40 * px);
			c.enableScissor(x + 2, mY0, x + w - 2, mY1 + 8);
			real = dev.solclient.cosmetic.PlayerPreview.draw(c, Math.round((x + 2) * uu), Math.round(top3 * uu), Math.round((x + w - 2) * uu),
					Math.round(feet * uu), -yawNow, wear, previewCape);
			c.disableScissor();
		}
		if (real) {
			// drawn
		} else if (skin != null) {
			c.enableScissor(x + 2, mY0, x + w - 2, mY1 + 8);
			float uu = 1F / f;
			if (!dev.solclient.cosmetic.SkinPreview.draw(c, skin, dev.solclient.skin.SkinChanger.selfSlim(), (x + w / 2F) * uu, feet * uu, px * uu,
					yawNow, 6F, wear)) {
				SkinModel3D.draw(c, skin, x + w / 2F, feet - 32 * px, px, yawNow, yawNow + 6, 10, true, wear);
			}
			c.disableScissor();
		} else {
			Gfx.skeleton(c, x + w / 4, areaY + areaH / 8, w / 2, areaH * 3 / 4, 8);
		}
		// "<-|-> Drag to rotate"
		String dr = "Drag to rotate";
		int dw = Gfx.tw(dr) + 14, dx0 = x + w / 2 - dw / 2, dy0 = areaY + areaH - 12;
		int ac = 0x99C9CCD6;
		c.fill(dx0, dy0 + 4, dx0 + 9, dy0 + 5, ac);
		c.fill(dx0 + 4, dy0, dx0 + 5, dy0 + 9, ac);
		c.fill(dx0 + 1, dy0 + 3, dx0 + 2, dy0 + 6, ac);
		c.fill(dx0 + 7, dy0 + 3, dx0 + 8, dy0 + 6, ac);
		Gfx.text(c, dr, dx0 + 14, dy0 + 1, ac);
		region(x, areaY, w, areaH, b -> previewDragX = null, (mx2, my2) -> {
			long now = System.currentTimeMillis();
			if (previewDragX != null) {
				float d = (float) (mx2 - previewDragX) * 1.1F;
				previewTarget += d;
				float dt = Math.max(0.008F, (now - previewLastDrag) / 1000F);
				previewVel = previewVel * 0.5F + (d / dt) * 0.5F;
			}
			previewLastDrag = now;
			previewDragX = mx2;
		});
	}

	private static List<dev.solclient.cosmetic.CosmeticModels.Def> tryOnNone() {
		java.util.UUID me = MinecraftClient.getInstance().getSession().getUuidOrNull();
		return me == null ? List.of() : dev.solclient.cosmetic.Cosmetics.worn(me);
	}

	/** Roblox-like "Buy item" window: picture, price, "Wear item after purchase", Buy. */
	private void drawBuyWindow(DrawContext c, int mx, int my, int accent) {
		Social.Item it = buyItem;
		if (it == null) {
			return;
		}
		c.fill(0, 0, vw, vh, 0x90000000);
		region(0, 0, vw, vh, () -> { }); // blocks clicks behind the window
		int w = 300, h = it.wearable() ? 176 : 150;
		int x = vw / 2 - w / 2, y = vh / 2 - h / 2;
		Gfx.rrectBorder(c, x, y, w, h, 8, 0xFF15161B, Theme.CARD_BORDER);
		Gfx.text(c, "Buy item", x + 16, y + 14, 0xFFFFFFFF);
		int cxb = x + w - 28, cyb = y + 9;
		boolean hx = in(mx, my, cxb, cyb, 18, 18);
		Gfx.rrect(c, cxb, cyb, 18, 18, 4, hx ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
		drawX(c, cxb + 5, cyb + 5, 7, hx ? Theme.RED : Theme.TEXT);
		region(cxb, cyb, 18, 18, () -> buyItem = null);

		int iy = y + 36;
		itemImage(c, it, x + 16, iy, 56, 56);
		Gfx.text(c, fit(it.name, w - 104), x + 84, iy + 4, Theme.TEXT);
		Gfx.text(c, "Permanent", x + 84, iy + 17, Theme.DIM);
		Gfx.coin(c, x + 83, iy + 30, 10);
		String price = String.valueOf(it.finalPrice);
		Gfx.text(c, price, x + 96, iy + 31, 0xFFFFD27A);
		if (it.discountPct > 0 && it.finalPrice != it.price) {
			int px0 = x + 102 + Gfx.tw(price);
			String was = String.valueOf(it.price);
			Gfx.text(c, was, px0, iy + 31, Theme.DIM);
			c.fill(px0 - 1, iy + 35, px0 + Gfx.tw(was) + 1, iy + 36, Theme.DIM);
		}
		long after = Social.coins - it.finalPrice;
		boolean afford = Social.coins < 0 || after >= 0;
		if (Social.coins >= 0) {
			Gfx.text(c, afford ? "Balance after: " + coinsText(after) : "You need " + coinsText(-after) + " more coins", x + 84, iy + 46,
					afford ? Theme.DIM : 0xFFFF9A9C);
		}
		int ry = iy + 66;
		if (it.wearable()) {
			boolean hc = in(mx, my, x + 16, ry, w - 32, 18);
			Gfx.rrect(c, x + 16, ry + 2, 14, 14, 3, wearAfterBuy ? accent : (hc ? Theme.OFF_BG_HOVER : Theme.OFF_BG));
			if (wearAfterBuy) {
				c.fill(x + 19, ry + 9, x + 22, ry + 11, 0xFFFFFFFF);
				c.fill(x + 21, ry + 10, x + 23, ry + 12, 0xFFFFFFFF);
				for (int i = 0; i < 5; i++) {
					c.fill(x + 22 + i, ry + 10 - i, x + 24 + i, ry + 11 - i, 0xFFFFFFFF);
				}
			}
			Gfx.text(c, "Wear item after purchase", x + 38, ry + 5, Theme.TEXT);
			region(x + 16, ry, w - 32, 18, () -> wearAfterBuy = !wearAfterBuy);
			ry += 26;
		}
		final Social.Item fi = it;
		button(c, mx, my, x + 16, y + h - 34, w - 32, 24, afford ? "Buy" : "Not enough coins", "bwbuy",
				afford ? accent : Theme.OFF_BG, afford ? Theme.accentHover() : Theme.OFF_BG_HOVER, afford ? 0xFFFFFFFF : Theme.DIM, () -> {
					if (!afford) {
						return;
					}
					boolean wear = wearAfterBuy && fi.wearable();
					buyItem = null;
					lockerSel = fi.id;
					Social.buy(fi, () -> {
						if (wear) {
							Social.equip(fi, true);
						} else {
							Social.loadShop(Social.shopCategory);
						}
					});
				});
	}

	/**
	 * Inventory side list, like LabyMod's "My cosmetics": what you have on, each with an x to take it off.
	 * On the Emotes tab it is your emote wheel (slot, emote, x).
	 */
	private void lockerSideList(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		boolean emotesTab = invCat.equals("Emotes");
		boolean killTab = invCat.equals("Kill Effects");
		List<Social.Item> src = killTab ? Social.killEffectItems() : Social.shop;
		List<String[]> rows = new ArrayList<>(); // id, name, slot
		if (emotesTab) {
			for (int i = 0; i < 8; i++) {
				EmoteLibrary.Info e = EmoteSlots.get(i);
				rows.add(new String[] {e == null ? null : e.id(), e == null ? null : e.name(), String.valueOf(i)});
			}
		} else {
			for (Social.Item it : src) {
				if (it.owned && it.equipped && killTab == Social.isKillEffect(it)) {
					rows.add(new String[] {it.id, it.name, null});
				}
			}
			var deq = killTab ? dev.solclient.fx.DeathAnimBridge.equipped() : null;
			if (deq != null) {
				rows.add(new String[] {"death:" + deq.id, deq.title.replaceFirst("^Originals: ", ""), null});
			}
		}
		String title = dev.solclient.Lang.t(emotesTab ? "Emote wheel" : killTab ? "Kill effect on" : "My cosmetics").toUpperCase(Locale.ROOT);
		Gfx.textRaw(c, fitRaw(title, w - 30), x + 8, y + 9, 0xFF6B7080);
		if (!emotesTab) {
			String n = String.valueOf(rows.size());
			Gfx.textRaw(c, n, x + w - 8 - Gfx.twRaw(n), y + 9, 0xFF6B7080);
		}
		int ry = y + 22, rh = 20;
		for (String[] r : rows) {
			if (ry + rh > y + h) {
				break;
			}
			boolean empty = r[0] == null;
			boolean hov = in(mx, my, x + 4, ry, w - 8, rh);
			Gfx.rrectBorder(c, x + 4, ry, w - 8, rh, 3, hov && !empty ? TILE_HOVER : Theme.ROW, 0xFF2A2C33);
			int tx = x + 9;
			if (r[2] != null) {
				Gfx.textRaw(c, String.valueOf(Integer.parseInt(r[2]) + 1), tx, ry + 6, 0xFF6B7080);
				tx += 10;
			} else {
				Social.Item it = null;
				for (Social.Item s2 : src) {
					if (r[0].equals(s2.id)) {
						it = s2;
					}
				}
				if (it != null) {
					itemImage(c, it, tx - 2, ry + 3, 14, 14);
					tx += 16;
				} else if (r[0].startsWith("death:")) {
					Gfx.rrect(c, tx - 1, ry + 6, 8, 8, 4, 0xFFB8BCC8);
					tx += 12;
				}
			}
			Gfx.textClip(c, empty ? dev.solclient.Lang.t("Empty") : r[1], tx, ry + 6, x + w - 22 - tx, empty ? 0xFF5E6272 : Theme.TEXT);
			if (!empty) {
				int bx = x + w - 16, by = ry + 5;
				boolean xh = in(mx, my, bx - 2, by - 2, 12, 12);
				int col = xh ? 0xFFFF8A8A : 0xFF6B7080;
				for (int i = 0; i < 5; i++) {
					c.fill(bx + i, by + i, bx + 1 + i, by + 1 + i, col);
					c.fill(bx + 4 - i, by + i, bx + 5 - i, by + 1 + i, col);
				}
				final String[] row = r;
				region(bx - 2, by - 2, 12, 12, () -> {
					if (row[2] != null) {
						EmoteSlots.clear(Integer.parseInt(row[2]));
					} else if (row[0].startsWith("death:")) {
						dev.solclient.fx.DeathAnimBridge.unequip();
					} else {
						for (Social.Item s2 : src) {
							if (row[0].equals(s2.id)) {
								Social.equip(s2, false);
							}
						}
					}
				});
				region(x + 4, ry, w - 24, rh, () -> {
					if (row[0].startsWith("death:")) {
						deathSel = row[0].substring(6);
						lockerSel = null;
					} else {
						lockerSel = row[0];
					}
				});
			} else if (r[2] != null) {
				final int slotIdx = Integer.parseInt(r[2]);
				region(x + 4, ry, w - 8, rh, () -> lockerSlot = slotIdx); // pick the slot, then switch an emote on
			}
			ry += rh + 3;
		}
		if (rows.isEmpty()) {
			Gfx.textClip(c, dev.solclient.Lang.t("Nothing on yet"), x + 8, ry + 2, w - 16, 0xFF5E6272);
		}
	}

	/** Top tabs: 0 cosmetics, 1 emotes, 2 kill effects, 3 skin (skins only exist in the inventory). */
	private void lockerKind(int k) {
		lockerSel = null;
		lockerChip = null;
		scroll = 0;
		scrollTarget = 0;
		deathSel = null;
		dev.solclient.fx.DeathAnimBridge.hover(null);
		if (k == 3) {
			lockerShop = false;
			invCat = "Skins";
		} else if (lockerShop) {
			Social.loadShop(k == 1 ? "Emotes" : k == 2 ? "Kill Effects" : "All");
		} else {
			invCat = k == 1 ? "Emotes" : k == 2 ? "Kill Effects" : "Cosmetics";
			if (k != 1) {
				Social.loadShop("All");
			}
		}
	}

	/** The death animation picked in the Kill Effects section (null = a normal kill effect / nothing). */
	private static String deathSel;
	private dev.solclient.fx.DeathAnimBridge.Entry deathHover;

	private static String lockerChip;
	private static boolean lockerOnlyOn;

	/** Small on / off switch (cards): green when on. */
	private static void miniSwitch(DrawContext c, String key, int x, int y, boolean on, boolean hov) {
		int w = 18, h = 10;
		float a = swAnim("mini" + key, on);
		int off = hov ? 0xFF3A3D49 : 0xFF2A2D36, onC = hov ? 0xFF2FA866 : 0xFF248A52;
		Gfx.rrect(c, x, y, w, h, 3, Gfx.blend(off, onC, a));
		Gfx.rrectF(c, x + 2 + a * (w - 10), y + 2, 6, 6, 2, Gfx.blend(0xFFB6BAC8, 0xFFFFFFFF, a));
	}

	private void switchLocker(boolean shop) {
		deathSel = null;
		lockerStore = false;
		lockerShop = shop;
		lockerSel = null;
		scroll = 0;
		scrollTarget = 0;
		if (shop) {
			Social.loadShop(Social.shopCategory.equals("All") && !Social.categories.isEmpty() ? Social.categories.get(0) : Social.shopCategory);
		} else if (invCat.equals("Cosmetics") || invCat.equals("Kill Effects")) {
			Social.loadShop("All");
		}
	}

	/** An emote card in the same look as the cosmetics: picture, name, and a switch = on the emote wheel. */
	private void drawEmoteCard(DrawContext c, int mx, int my, EmoteLibrary.Info e, int x, int y, int w, int h, int accent) {
		boolean hov = in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
		float a = anim("em" + e.id(), hov);
		boolean sel = e.id().equals(lockerSel);
		int slot = EmoteSlots.slotOf(e.id());
		Gfx.rrectBorder(c, x, y, w, h, 3, Gfx.blend(TILE, TILE_HOVER, a), sel ? 0xFFB6BAC8 : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		int areaH = h - 24;
		Gfx.rrect(c, x + 4, y + 4, w - 8, areaH, 3, 0xFF1B1D24);
		int is = Math.min(w - 20, areaH - 8);
		// the 3D player is placed in SCREEN units, not menu units (same as the player preview): that is why it was not showing
		float uu = 1F / f;
		if (dev.solclient.cosmetic.SkinPreview.drawEmoteCard(c, e, hov, Math.round((x + 4) * uu), Math.round((y + 4) * uu), Math.round((w - 8) * uu),
				Math.round(areaH * uu))) {
			// a grey mannequin doing the emote (no image needed)
		} else if (e.icon()) {
			Gfx.tex(c, e.iconId(), x + (w - is) / 2, y + 4 + (areaH - is) / 2, is, is, 256, 256, 0xFFFFFFFF);
		} else {
			Gfx.circle(c, x + w / 2F, y + 4 + areaH / 2F, is / 3F, 0xFF3A3D49);
			Gfx.textFit(c, e.name().substring(0, 1).toUpperCase(Locale.ROOT), x + w / 2, w - 6, y + areaH / 2, 0xFFFFFFFF);
		}
		if (slot >= 0) {
			String sn = String.valueOf(slot + 1);
			Gfx.rrect(c, x + w - 18, y + 7, 12, 12, 3, 0xFF248A52);
			Gfx.textRaw(c, sn, x + w - 12 - Gfx.twRaw(sn) / 2, y + 9, 0xFFFFFFFF);
		}
		int ny = y + h - 15;
		Social.Item si = lockerShop ? Social.emoteItem(e.id(), e.name()) : null;
		boolean canWear = !lockerShop || EmoteLibrary.owned(e.id());
		if (lockerShop && si != null && !si.owned) {
			String p = String.valueOf(si.finalPrice);
			int pw = Gfx.tw(p);
			Gfx.text(c, p, x + w - 6 - pw, ny + 1, 0xFFFFD27A);
			Gfx.coin(c, x + w - 18 - pw, ny, 9);
			Gfx.textClip(c, e.name(), x + 6, ny + 1, w - 30 - pw, Theme.TEXT);
		} else if (canWear) {
			int sxx = x + w - 24;
			boolean sh = in(mx, my, sxx - 2, ny - 2, 22, 14) && my >= clipY0 && my < clipY1;
			miniSwitch(c, "e" + e.id(), sxx, ny, slot >= 0, sh);
			Gfx.textClip(c, e.name(), x + 6, ny + 1, w - 36, Theme.TEXT);
			region(sxx - 2, ny - 2, 22, 14, () -> equipEmote(e));
		} else {
			Gfx.textClip(c, e.name(), x + 6, ny + 1, w - 12, Theme.TEXT);
		}
		region(x, y, w, h - 18, () -> {
			lockerSel = e.id();
			if (doubleClick("em" + e.id()) && canWear) {
				equipEmote(e);
			}
		});
		region(x, y + h - 18, w - 28, 18, () -> lockerSel = e.id());
	}

	/** Equip into the picked slot (or the first free one, or slot 1); equipping an equipped emote unequips it. */
	private void equipEmote(EmoteLibrary.Info e) {
		int cur = EmoteSlots.slotOf(e.id());
		if (cur >= 0 && (lockerSlot < 0 || lockerSlot == cur)) {
			EmoteSlots.clear(cur);
			Social.say("Removed " + e.name() + " from the wheel", false);
			return;
		}
		int slot = lockerSlot >= 0 ? lockerSlot : EmoteSlots.firstFree();
		if (slot < 0) {
			Social.say("Wheel is full: pick a slot on the right first", true);
			return;
		}
		EmoteSlots.set(slot, e.id());
		lockerSlot = -1;
		Social.say(e.name() + " equipped in slot " + (slot + 1), false);
	}

	/** A cosmetic like LabyMod's cards: the picture, the name bottom-left and an on / off switch (or the price) bottom-right. */
	private void drawItemCard(DrawContext c, int mx, int my, Social.Item it, int x, int y, int w, int h, int accent) {
		boolean hov = in(mx, my, x, y, w, h) && my >= clipY0 && my < clipY1;
		float a = anim("it" + it.id, hov);
		boolean sel = it.id != null && it.id.equals(lockerSel);
		Gfx.rrectBorder(c, x, y, w, h, 3, Gfx.blend(TILE, TILE_HOVER, a), sel ? 0xFFB6BAC8 : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		if (!liveItemImage(c, it, hov && my >= clipY0 && my < clipY1, x + 4, y + 4, w - 8, h - 24)) {
			itemImage(c, it, x + 4, y + 4, w - 8, h - 24);
		}
		String tag = it.badge != null ? it.badge : it.discountPct > 0 ? "-" + it.discountPct + "%" : null;
		if ("New".equalsIgnoreCase(tag)) {
			Gfx.tex(c, Theme.icon2("new_tag"), x + 6, y + 6, 26, 13, 128, 64, accent);
		} else if (tag != null) {
			int bw = Gfx.tw(tag) + 8;
			Gfx.rrect(c, x + 6, y + 6, bw, 12, 3, it.discountPct > 0 && it.badge == null ? Theme.RED : 0xFF3A3D49);
			Gfx.text(c, tag, x + 10, y + 8, 0xFFFFFFFF);
		}
		int ny = y + h - 15;
		boolean wearable = it.owned && it.wearable();
		if (lockerShop && !it.owned) {
			String p = String.valueOf(it.finalPrice);
			int pw = Gfx.tw(p);
			Gfx.text(c, p, x + w - 6 - pw, ny + 1, 0xFFFFD27A);
			Gfx.coin(c, x + w - 18 - pw, ny, 9);
			Gfx.textClip(c, it.name, x + 6, ny + 1, w - 30 - pw, Theme.TEXT);
		} else if (wearable) {
			int sxx = x + w - 24;
			boolean sh = in(mx, my, sxx - 2, ny - 2, 22, 14) && my >= clipY0 && my < clipY1;
			miniSwitch(c, "i" + it.id, sxx, ny, it.equipped, sh);
			Gfx.textClip(c, it.name, x + 6, ny + 1, w - 36, Theme.TEXT);
			region(sxx - 2, ny - 2, 22, 14, () -> Social.equip(it, !it.equipped));
		} else {
			Gfx.textClip(c, it.name, x + 6, ny + 1, w - 12, Theme.TEXT);
		}
		region(x, y, w, h - 18, () -> {
			lockerSel = it.id;
			deathSel = null;
			if (doubleClick("it" + it.id) && wearable) {
				Social.equip(it, !it.equipped);
			}
		});
		region(x, y + h - 18, w - 28, 18, () -> {
			lockerSel = it.id;
			deathSel = null;
		});
		if (hov && Social.isKillEffect(it) && Gfx.mc().player != null) {
			int vbw = Gfx.tw("View") + 16, vbx = x + (w - vbw) / 2, vby = y + 4 + (h - 24) - 20;
			boolean vh2 = in(mx, my, vbx, vby, vbw, 16);
			Gfx.rrect(c, vbx, vby, vbw, 16, 5, vh2 ? Theme.accentHover() : Theme.accent());
			Gfx.textC(c, "View", vbx + vbw / 2, vby + 4, 0xFFFFFFFF);
			region(vbx, vby, vbw, 16, () -> viewKillEffect(it));
		}
	}

	private static final java.util.Map<String, Identifier> THUMBS = new java.util.HashMap<>();

	/** Your own skin in real 3D inside a card (screen box x..x+w, y..y+h in menu units), wearing 'wear'. False when there is no world to draw it. */
	private boolean livePlayer(DrawContext c, String key, List<dev.solclient.cosmetic.CosmeticModels.Def> wear, int x, int y, int w, int h) {
		var p = MinecraftClient.getInstance().player;
		if (p == null) {
			return false;
		}
		Identifier skin = p.getSkin().body().texturePath();
		c.enableScissor(x, y, x + w, y + h);
		boolean ok = dev.solclient.cosmetic.SkinPreview.drawWearCard(c, key, skin, dev.solclient.skin.SkinChanger.selfSlim(), wear, x, y, w, h);
		c.disableScissor();
		return ok;
	}

	/**
	 * Cards that show something live instead of a picture: auras are worn on your own skin, and a kill effect plays (real model, real
	 * animation) while the mouse is over its card. False = use the normal picture.
	 */
	private boolean liveItemImage(DrawContext c, Social.Item it, boolean hov, int px, int py, int pw, int ph) {
		try {
			var d = dev.solclient.cosmetic.Variants.resolve(dev.solclient.cosmetic.Cosmetics.modelFor(it));
			if (d != null && dev.solclient.cosmetic.Auras.is(d)) {
				Gfx.rrect(c, px, py, pw, ph, 3, 0xFF262935);
				return livePlayer(c, "aura:" + d.id(), List.of(d), px, py, pw, ph);
			}
			if (false && Social.isKillEffect(it) && Gfx.mc().player != null) { // the live card drew only black: the bundled picture is used (itemImage): a still frame, playing while the mouse is over it
				Identifier skin = Gfx.mc().player.getSkin().body().texturePath();
				Gfx.rrect(c, px, py, pw, ph, 3, 0xFF262935);
				c.enableScissor(px, py, px + pw, py + ph);
				boolean ok = dev.solclient.cosmetic.SkinPreview.drawKillCard(c, Social.killEffectKey(it), skin, hov, px, py, pw, ph);
				c.disableScissor();
				return ok;
			}
		} catch (RuntimeException ignored) {
		}
		return false;
	}

	/** The generated picture of a bundled cosmetic (textures/cosmetics/thumb/<id>.png), or null. */
	private static final java.util.Map<String, java.util.Optional<Identifier>> KILL_THUMBS = new java.util.HashMap<>();

	/** Picture of a kill effect (textures/killfx/thumb/<id>.png), or null. */
	private static Identifier killThumb(Social.Item it) {
		String key = Social.killEffectKey(it);
		return KILL_THUMBS.computeIfAbsent(key, k -> {
			Identifier id = Gfx.id("textures/killfx/thumb/" + k + ".png");
			return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent() ? java.util.Optional.of(id) : java.util.Optional.empty();
		}).orElse(null);
	}

	private static Identifier thumb(Social.Item it) {
		dev.solclient.cosmetic.CosmeticModels.Def d = dev.solclient.cosmetic.Cosmetics.modelFor(it);
		if (d == null) {
			return null;
		}
		d = dev.solclient.cosmetic.Variants.resolve(d);
		return THUMBS.computeIfAbsent(d.id(), k -> {
			Identifier id = Gfx.id("textures/cosmetics/thumb/" + k + ".png");
			return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent() ? id : null;
		});
	}

	/**
	 * A cape drawn as a little 3D slab: the outside of the cape (turned a bit and swaying slowly), its thickness on the side and the
	 * top edge. Atlas capes (2:1, 64x32 layout) use the real cape area; a flat picture is used whole.
	 */
	/**
	 * A cape as a real little box (10 x 16 x 1) seen from above and turning: the top edge, the front, the sides and the inside
	 * all show as it goes round. Every face is the cape texture moved by a 2D matrix (orthographic projection), so the turn is smooth.
	 */
	private void capeCard(DrawContext c, RemoteTextures.Tex t, int px, int py, int pw, int ph) {
		boolean atlas = Math.abs(t.width() / (float) t.height() - 2F) < 0.05F && t.width() >= 64;
		float k = atlas ? t.width() / 64F : 1F;
		double tilt = Math.toRadians(17); // like Lunar: a little from above
		float cosP = (float) Math.cos(tilt), sinP = (float) Math.sin(tilt);
		double yaw = System.currentTimeMillis() / 1000.0 * 0.9 + (t.id().hashCode() & 7) * 0.8;
		float cosT = (float) Math.cos(yaw), sinT = (float) Math.sin(yaw);
		float s = Math.min(ph * 0.92F / (16F * cosP + 10F * sinP * 0.6F), pw * 0.92F / 11.5F);
		float cx = px + pw / 2F, cy = py + ph / 2F + 1;
		Gfx.circle(c, cx, py + ph - 6, Math.max(6F, 7F * s), 0x33000000); // soft shadow
		// faces: origin (x,y,z), width axis, height axis, size, texture rectangle (atlas) and shade
		float[][] face = {
				{-5, 8, 0.75F, 1, 0, 0, 0, -1, 0, 10, 16, 0, 0, 1, 1.00F},    // front: outside of the cape
				{5, 8, -0.75F, -1, 0, 0, 0, -1, 0, 10, 16, 0, 0, -1, 0.78F},  // back: the inside
				{5, 8, 0.75F, 0, 0, -0.75F, 0, -1, 0, 2, 16, 1, 0, 0, 0.80F}, // right edge (2 px of 0.75 = 1.5 thick)
				{-5, 8, -0.75F, 0, 0, 0.75F, 0, -1, 0, 2, 16, -1, 0, 0, 0.80F}, // left edge
				{-5, 8, -0.75F, 1, 0, 0, 0, 0, 0.75F, 10, 2, 0, 1, 0, 0.94F}, // top edge
		};
		var m = c.getMatrices();
		for (int fi = 0; fi < face.length; fi++) {
			float[] f2 = face[fi];
			float nx = f2[11], ny = f2[12], nz = f2[13];
			float nzr = -nx * sinT + nz * cosT;
			if (ny * sinP + nzr * cosP <= 0.001F) {
				continue; // turned away
			}
			float[] o = proj(f2[0], f2[1], f2[2], cosT, sinT, cosP, sinP), a = proj(f2[0] + f2[3], f2[1] + f2[4], f2[2] + f2[5], cosT, sinT, cosP, sinP),
					b2 = proj(f2[0] + f2[6], f2[1] + f2[7], f2[2] + f2[8], cosT, sinT, cosP, sinP);
			int fw = (int) f2[9], fh = (int) f2[10];
			float shade = f2[14];
			int g = Math.round(255F * shade);
			int col = 0xFF000000 | g << 16 | g << 8 | g;
			m.pushMatrix();
			m.mul(new org.joml.Matrix3x2f((a[0] - o[0]) * s, (a[1] - o[1]) * s, (b2[0] - o[0]) * s, (b2[1] - o[1]) * s, cx + o[0] * s, cy + o[1] * s));
			if (atlas) {
				int u, v, uw, vh;
				switch (fi) {
					case 0 -> { u = Math.round(k); v = Math.round(k); uw = Math.round(10 * k); vh = Math.round(16 * k); }
					case 1 -> { u = Math.round(12 * k); v = Math.round(k); uw = Math.round(10 * k); vh = Math.round(16 * k); }
					case 2 -> { u = Math.round(11 * k); v = Math.round(k); uw = Math.max(1, Math.round(k)); vh = Math.round(16 * k); }
					case 3 -> { u = 0; v = Math.round(k); uw = Math.max(1, Math.round(k)); vh = Math.round(16 * k); }
					default -> { u = Math.round(k); v = 0; uw = Math.round(10 * k); vh = Math.max(1, Math.round(k)); }
				}
				Gfx.texUv(c, t.id(), 0, 0, fw, fh, u, v, uw, vh, t.width(), t.height(), col);
			} else if (fi == 0) {
				Gfx.texUv(c, t.id(), 0, 0, fw, fh, 0, 0, t.width(), t.height(), t.width(), t.height(), col);
			} else {
				c.fill(0, 0, fw, fh, 0xFF000000 | Math.round(0x3A * shade) << 16 | Math.round(0x3D * shade) << 8 | Math.round(0x49 * shade));
			}
			m.popMatrix();
		}
	}

	/** Orthographic projection of a point of the cape box (y up, z toward you): turned by yaw, then seen from above. */
	private static float[] proj(float x, float y, float z, float cosT, float sinT, float cosP, float sinP) {
		float xr = x * cosT + z * sinT, zr = -x * sinT + z * cosT;
		return new float[]{xr, -y * cosP + zr * sinP};
	}

	private void itemImage(DrawContext c, Social.Item it, int px, int py, int pw, int ph) {
		int c1 = it.colors.isEmpty() ? 0xFF2C2F38 : parseColour(it.colors.get(0), 0xFF2C2F38);
		int c2 = it.colors.size() > 1 ? parseColour(it.colors.get(1), c1) : Gfx.blend(c1, 0xFF000000, 0.35F);
		Gfx.rrect(c, px, py, pw, ph, 3, Gfx.blend(0xFF30333F, Gfx.blend(c1, c2, 0.5F), 0.22F)); // a mid grey-blue (not black), with a hint of the item's colour
		RemoteTextures.Tex t = RemoteTextures.get(it.imageUrl);
		if (Social.isKillEffect(it) && killThumb(it) != null) {
			// the kill effect's own picture, bundled in the client (always there, even before the site sends one)
			int ts = Math.min(pw, ph) - 4;
			Gfx.tex(c, killThumb(it), px + (pw - ts) / 2, py + (ph - ts) / 2, ts, ts, 128, 128, 0xFFFFFFFF);
			return;
		}
		if (t != null && it.category != null && it.category.equalsIgnoreCase("Capes")) {
			capeCard(c, t, px, py, pw, ph);
		} else if (t != null) {
			float sc = Math.min((pw - 8) / (float) t.width(), (ph - 8) / (float) t.height());
			int dw = Math.max(1, Math.round(t.width() * sc)), dh = Math.max(1, Math.round(t.height() * sc));
			Gfx.tex(c, t.id(), px + (pw - dw) / 2, py + (ph - dh) / 2, dw, dh, t.width(), t.height(), 0xFFFFFFFF);
		} else if (thumb(it) != null) {
			// a bundled cosmetic without a shop picture: its own little 3D picture (made from the model)
			int ts = Math.min(pw, ph) - 8;
			Gfx.tex(c, thumb(it), px + (pw - ts) / 2, py + (ph - ts) / 2, ts, ts, 96, 96, 0xFFFFFFFF);
		} else {
			float r = Math.min(pw, ph) / 4F;
			Gfx.circle(c, px + pw / 2F, py + ph / 2F, r, c1);
			Gfx.circle(c, px + pw / 2F, py + ph / 2F, r * 0.72F, c2);
			String ini = it.name.isEmpty() ? "?" : it.name.substring(0, 1).toUpperCase(Locale.ROOT);
			Gfx.textFit(c, ini, px + pw / 2, pw - 6, py + ph / 2 - 4, 0xFFFFFFFF);
		}
	}

	/** Local emote with the same id or name as a shop emote (so a bought emote can go on the wheel). */
	private static EmoteLibrary.Info localEmote(Social.Item it) {
		for (EmoteLibrary.Info e : EmoteLibrary.all()) {
			if (e.id().equalsIgnoreCase(String.valueOf(it.modelId)) || e.id().equalsIgnoreCase(String.valueOf(it.id)) || e.name().equalsIgnoreCase(it.name)) {
				return e;
			}
		}
		return null;
	}

	/** Shop sections from the site, always with an "Emotes" section (all the client's emotes, played in the preview). */
	private static List<String> shopCats() {
		List<String> base = Social.categories.isEmpty() ? List.of("Featured", "Sale", "All", "Auras", "Minimes") : Social.categories;
		// "All Emotes" right under "All" (internally the "Emotes" section)
		List<String> out = new ArrayList<>();
		for (String c : base) {
			if (!c.equalsIgnoreCase("Emotes") && !c.equalsIgnoreCase("Emojis")) {
				out.add(c);
			}
		}
		int all = -1;
		for (int i = 0; i < out.size(); i++) {
			if (out.get(i).equalsIgnoreCase("All")) all = i;
		}
		out.add(all >= 0 ? all + 1 : 0, "Emotes");
		return out;
	}

	private static long emotePrice(EmoteLibrary.Info e) {
		Social.Item it = Social.emoteItem(e.id(), e.name());
		return it == null ? Long.MAX_VALUE : it.owned ? -1 : it.finalPrice;
	}

	/** Shop > Emotes: your player doing the emote, its name and Buy / Equip. */
	private void drawEmoteShopPreview(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent, EmoteLibrary.Info e,
			RemoteTextures.Tex skin) {
		int infoH = e == null ? 34 : 70;
		int areaY = y + 8, areaH = h - infoH - 14;
		drawPosedModel(c, x, areaY, w, areaH, tryOnNone(), true, skin);
		Gfx.fill(c, x + 10, areaY + areaH + 2, x + w - 10, areaY + areaH + 3, LINE);
		int ty = areaY + areaH + 9;
		if (e == null) {
			Gfx.textFit(c, "Click an emote to see it", x + w / 2, w - 6, ty + 4, Theme.DIM);
			return;
		}
		Gfx.textFit(c, fit(e.name(), w - 16), x + w / 2, w - 6, ty, Theme.TEXT);
		String sub = (e.author().isEmpty() ? "" : "by " + e.author()) + (e.loop() ? (e.author().isEmpty() ? "" : " - ") + "Loops" : "");
		if (!sub.isEmpty()) {
			Gfx.textFit(c, fit(sub, w - 16), x + w / 2, w - 6, ty + 11, Theme.DIM);
		}
		int by = y + h - 30, bx = x + 8, bw = w - 16;
		final EmoteLibrary.Info fe = e;
		Social.Item it = Social.emoteItem(e.id(), e.name());
		if (it != null && !it.owned) {
			final Social.Item fi = it;
			boolean afford = Social.coins < 0 || Social.coins >= it.finalPrice;
			button(c, mx, my, bx, by, bw, 22, "Buy  " + it.finalPrice, "lbuy", afford ? accent : Theme.OFF_BG, afford ? Theme.accentHover() : Theme.OFF_BG_HOVER,
					afford ? 0xFFFFFFFF : Theme.DIM, () -> {
						if (Social.me == null) {
							Social.say("Log in first (Friends tab)", true);
							return;
						}
						buyItem = fi;
						wearAfterBuy = false;
					});
		} else if (it != null || !Social.sellsEmotes()) {
			button(c, mx, my, bx, by, bw, 22, "Equip on Wheel", "lgoinv", accent, Theme.accentHover(), 0xFFFFFFFF, () -> {
				switchLocker(false);
				invCat = "Emotes";
				lockerSel = fe.id();
			});
		} else {
			Gfx.rrect(c, bx, by, bw, 22, 7, Theme.OFF_BG);
			Gfx.textFit(c, "Not for sale yet", x + w / 2, w - 6, by + 7, Theme.DIM);
		}
	}

	private void drawLockerPreview(DrawContext c, int mx, int my, int x, int y, int w, int h, int accent, boolean emotes,
			List<EmoteLibrary.Info> emoteList, List<Social.Item> items) {
		Gfx.rrectBorder(c, x, y, w, h, 10, Gfx.blend(TILE, 0xFF000000, 0.25F), Theme.CARD_BORDER);
		MinecraftClient mc = MinecraftClient.getInstance();
		RemoteTextures.Tex skin = dev.solclient.skin.SkinChanger.myPreview();
		if (!emotes) {
			drawWearPreview(c, mx, my, x, y, w, h, accent, items, skin);
			return;
		}
		EmoteLibrary.Info selE = null;
		for (EmoteLibrary.Info i : emoteList) {
			if (i.id().equals(lockerSel)) {
				selE = i;
			}
		}
		// your player plays the selected emote (so you know what it is before buying / equipping)
		dev.solclient.emote.EmoteManager.requestPreview(selE);
		if (lockerShop) {
			drawEmoteShopPreview(c, mx, my, x, y, w, h, accent, selE, skin);
			return;
		}
		float px = Math.max(2.5F, Math.min(5F, (h * 0.36F) / 20F));
		int modelTop = y + 14;
		int iy = modelTop + Math.round(px * 20) + 12;
		drawPosedModel(c, x, y + 2, w, iy - y - 8, tryOnNone(), false, skin);
		Gfx.fill(c, x + 10, iy - 4, x + w - 10, iy - 3, LINE);

		if (emotes) {
			EmoteLibrary.Info e = null;
			for (EmoteLibrary.Info i : EmoteLibrary.all()) {
				if (i.id().equals(lockerSel)) {
					e = i;
				}
			}
			// the wheel: 8 slots, click one to pick where the next emote goes (right click empties it)
			Gfx.text(c, "Emote Wheel", x + 10, iy + 2, Theme.DIM);
			int ss = Math.min(28, (w - 20 - 3 * 5) / 4);
			int sx0 = x + (w - (4 * ss + 3 * 5)) / 2, sy0 = iy + 14;
			for (int s = 0; s < EmoteSlots.SLOTS; s++) {
				int bx = sx0 + (s % 4) * (ss + 5), by = sy0 + (s / 4) * (ss + 5);
				EmoteLibrary.Info si = EmoteSlots.get(s);
				boolean picked = s == lockerSlot;
				boolean hov = in(mx, my, bx, by, ss, ss);
				Gfx.rrectBorder(c, bx, by, ss, ss, 6, hov ? TILE_HOVER : 0xFF101116, picked ? accent : Theme.CARD_BORDER);
				if (si != null && dev.solclient.cosmetic.SkinPreview.drawEmoteCard(c, si, hov, Math.round((bx + 2) / f), Math.round((by + 2) / f),
						Math.round((ss - 4) / f), Math.round((ss - 4) / f))) {
					// the grey mannequin doing the emote
				} else if (si != null && si.icon()) {
					Gfx.tex(c, si.iconId(), bx + 3, by + 3, ss - 6, ss - 6, 256, 256, 0xFFFFFFFF);
				} else if (si != null) {
					Gfx.textFit(c, si.name().substring(0, 1), bx + ss / 2, ss - 6, by + ss / 2 - 4, Theme.TEXT);
				}
				Gfx.text(c, String.valueOf(s + 1), bx + 2, by + 2, 0x66FFFFFF);
				if (hov) {
					tooltipText = si == null ? "Slot " + (s + 1) + ": empty" : "Slot " + (s + 1) + ": " + si.name() + " (right click: remove)";
				}
				final int slot = s;
				region(bx, by, ss, ss, btn -> {
					if (btn == 1) {
						EmoteSlots.clear(slot);
					} else {
						lockerSlot = lockerSlot == slot ? -1 : slot;
						EmoteLibrary.Info cur = EmoteSlots.get(slot);
						if (cur != null && lockerSel == null) {
							lockerSel = cur.id();
						}
					}
				}, null);
			}
			int ty = sy0 + 2 * (ss + 5) + 6;
			if (e == null) {
				String hint = lockerSlot >= 0 ? "Pick an emote for slot " + (lockerSlot + 1) : "Select an emote";
				for (String l : wrap(hint, w - 20, 2)) {
					Gfx.textFit(c, l, x + w / 2, w - 6, ty, Theme.DIM);
					ty += 11;
				}
				Gfx.textFit(c, "Double click = equip", x + w / 2, w - 6, y + h - 18, 0x66FFFFFF);
				return;
			}
			Gfx.textFit(c, fit(e.name(), w - 16), x + w / 2, w - 6, ty, Theme.TEXT);
			String sub = (e.author().isEmpty() ? "" : "by " + e.author()) + (e.loop() ? (e.author().isEmpty() ? "" : " - ") + "Loops" : "");
			if (!sub.isEmpty()) {
				Gfx.textFit(c, fit(sub, w - 16), x + w / 2, w - 6, ty + 11, Theme.DIM);
			}
			int slot = EmoteSlots.slotOf(e.id());
			boolean moving = slot >= 0 && lockerSlot >= 0 && lockerSlot != slot;
			String label = slot >= 0 && !moving ? "Unequip (slot " + (slot + 1) + ")"
					: "Equip" + (lockerSlot >= 0 ? " in slot " + (lockerSlot + 1) : "");
			boolean equipped = slot >= 0 && !moving;
			final EmoteLibrary.Info fe = e;
			int by = y + h - 30;
			if (mc.player != null) {
				int half = (w - 24) / 2;
				button(c, mx, my, x + 8, by, half, 22, "Play", "lplay", TILE, TILE_HOVER, Theme.TEXT, () -> {
					closeMenu();
					dev.solclient.emote.EmoteManager.play(fe);
				});
				button(c, mx, my, x + 16 + half, by, w - 24 - half, 22, equipped ? "Unequip" : "Equip",
						"lequip", equipped ? Theme.OFF_BG : accent, equipped ? Theme.OFF_BG_HOVER : Theme.accentHover(), 0xFFFFFFFF, () -> equipEmote(fe));
			} else {
				button(c, mx, my, x + 8, by, w - 16, 22, label, "lequip", equipped ? Theme.OFF_BG : accent,
						equipped ? Theme.OFF_BG_HOVER : Theme.accentHover(), 0xFFFFFFFF, () -> equipEmote(fe));
			}
			return;
		}

		// shop / owned cosmetics
		Social.Item it = null;
		for (Social.Item i : items) {
			if (i.id != null && i.id.equals(lockerSel)) {
				it = i;
			}
		}
		if (it == null) {
			Gfx.textFit(c, lockerShop ? "Select an item" : "Select a cosmetic", x + w / 2, w - 6, iy + 10, Theme.DIM);
			return;
		}
		int ih = Math.min(56, y + h - iy - 90);
		if (ih > 20) {
			itemImage(c, it, x + 10, iy + 2, w - 20, ih);
		}
		int ty = iy + Math.max(0, ih) + 8;
		Gfx.textFit(c, fit(it.name, w - 16), x + w / 2, w - 6, ty, Theme.TEXT);
		if (it.category != null) {
			Gfx.textFit(c, fit(it.category, w - 16), x + w / 2, w - 6, ty + 11, Theme.DIM);
		}
		int swatchRows = 0;
		if (it.variantGroup != null && !"Pets".equalsIgnoreCase(it.category)) {
			// colour picker: one dot per colour (click = wear that colour), like the colour choice in the Laby shop
			var vs = dev.solclient.cosmetic.CosmeticModels.variantsOf(it.variantGroup);
			var cur = dev.solclient.cosmetic.Variants.chosen(it.variantGroup);
			int r = 6, gap = 5, per = Math.max(1, (w - 24) / (2 * r + gap));
			swatchRows = (vs.size() + per - 1) / per;
			Gfx.textFit(c, "Colour" + (cur != null && cur.variant() != null ? ": " + cur.variant() : ""), x + w / 2, w - 6, ty + 24, Theme.DIM);
			for (int i = 0; i < vs.size(); i++) {
				int row = i / per, inRow = Math.min(per, vs.size() - row * per);
				int sx = x + w / 2 - (inRow * (2 * r + gap) - gap) / 2 + (i % per) * (2 * r + gap) + r, sy = ty + 42 + row * (2 * r + gap);
				var d = vs.get(i);
				boolean sel = d == cur, hv = in(mx, my, sx - r - 1, sy - r - 1, 2 * r + 2, 2 * r + 2);
				Gfx.circle(c, sx, sy, r + (sel ? 2.4F : hv ? 1.4F : 0F), sel ? 0xFFFFFFFF : 0x55FFFFFF);
				Gfx.circle(c, sx, sy, r - 0.5F, parseColour(d.swatch(), 0xFF888888));
				if (hv) {
					tooltipText = d.variant();
				}
				final String gid = it.variantGroup, did = d.id();
				region(sx - r - 1, sy - r - 1, 2 * r + 2, 2 * r + 2, () -> dev.solclient.cosmetic.Variants.choose(gid, did));
			}
		}
		if (it.description != null && !it.description.isEmpty() && !it.id.equals(editFor)) {
			int dy = ty + 25 + (swatchRows > 0 ? 18 + swatchRows * 17 : 0);
			for (String l : wrap(it.description, w - 20, 3)) {
				if (dy > y + h - 82) {
					break;
				}
				Gfx.textFit(c, l, x + w / 2, w - 6, dy, 0xFFB8BCC8);
				dy += 11;
			}
		}
		int by = y + h - 30, bx = x + 8, bw = w - 16;
		final Social.Item fi = it;
		if (it.wearable() && !("verity".equalsIgnoreCase(it.id) || "verity".equalsIgnoreCase(it.modelId))) { // Verity is not an accessory
			cosmeticEditor(c, mx, my, w, x, bx, bw, by, it);
		}
		if (!it.owned) {
			String price = it.discountPct > 0 && it.finalPrice != it.price ? it.price + " > " + it.finalPrice : String.valueOf(it.finalPrice);
			boolean ask = confirming("buy" + it.id);
			boolean afford = Social.coins < 0 || Social.coins >= it.finalPrice;
			button(c, mx, my, bx, by, bw, 22, ask ? "Confirm " + it.finalPrice + "?" : "Buy  " + price, "lbuy",
					ask ? Theme.GREEN_BG : afford ? accent : Theme.OFF_BG, ask ? Theme.GREEN_BG_HOVER : afford ? Theme.accentHover() : Theme.OFF_BG_HOVER,
					ask ? Theme.GREEN : afford ? 0xFFFFFFFF : Theme.DIM, () -> {
						if (!afford) {
							Social.say("Not enough coins", true);
						} else if (confirm("buy" + fi.id, "Click again to buy " + fi.name)) {
							Social.buy(fi);
						}
					});
		} else if (!it.wearable()) {
			EmoteLibrary.Info local = localEmote(it);
			if (local != null) {
				button(c, mx, my, bx, by, bw, 22, "Equip on Wheel", "lgoinv", accent, Theme.accentHover(), 0xFFFFFFFF, () -> {
					switchLocker(false);
					invCat = "Emotes";
					lockerSel = local.id();
				});
			} else {
				Gfx.rrect(c, bx, by, bw, 22, 7, Theme.OFF_BG);
				Gfx.textFit(c, "Owned", x + w / 2, w - 6, by + 7, Theme.DIM);
			}
		} else if (it.equipped) {
			button(c, mx, my, bx, by, bw, 22, "Unequip", "luneq", Theme.OFF_BG, Theme.OFF_BG_HOVER, Theme.TEXT, () -> Social.equip(fi, false));
		} else {
			button(c, mx, my, bx, by, bw, 22, "Equip", "leq", accent, Theme.accentHover(), 0xFFFFFFFF, () -> Social.equip(fi, true));
		}
	}

	/** Equipping a death animation: it becomes THE kill effect (the equipped normal one and the other animations switch off). */
	private void equipDeath(dev.solclient.fx.DeathAnimBridge.Entry e) {
		if (!dev.solclient.fx.DeathAnimBridge.owned(e)) {
			return;
		}
		if (dev.solclient.fx.DeathAnimBridge.isEquipped(e)) {
			dev.solclient.fx.DeathAnimBridge.unequip();
			return;
		}
		for (Social.Item k : new ArrayList<>(Social.killEffectItems())) {
			if (k.equipped) {
				Social.equip(k, false);
			}
		}
		dev.solclient.fx.DeathAnimBridge.equip(e);
	}

	/** One death animation as a card of the Kill Effects grid: same size and look as the other items. */
	private void drawDeathCard(DrawContext c, int mx, int my, dev.solclient.fx.DeathAnimBridge.Entry e, int x, int y, int cw, int ch, int accent) {
		boolean hov = in(mx, my, x, y, cw, ch) && my >= clipY0 && my < clipY1;
		if (hov) {
			deathHover = e;
		}
		boolean sel = e.id.equals(deathSel), own = dev.solclient.fx.DeathAnimBridge.owned(e);
		boolean on = own && dev.solclient.fx.DeathAnimBridge.isEquipped(e);
		var shopItem = dev.solclient.social.Social.deathItem(e.id, e.title);
		float an = anim("da" + e.id, hov);
		Gfx.rrectBorder(c, x, y, cw, ch, 3, Gfx.blend(TILE, TILE_HOVER, an), sel ? 0xFFB6BAC8 : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, an));
		region(x, y, cw, ch, () -> {
			deathSel = e.id;
			lockerSel = null;
		}); // first: later regions (buttons) win
		int stx = x + 4, sty = y + 4, stw = cw - 8, sth = ch - 24;
		Gfx.rrect(c, stx, sty, stw, sth, 3, Gfx.blend(0xFF1B1D24, e.accent, 0.20F + 0.10F * an));
		float uu = 1F / f;
		if (hov) {
			dev.solclient.fx.DeathAnimBridge.hover(e);
			c.enableScissor(stx, sty, stx + stw, sty + sth);
			dev.solclient.fx.DeathAnimBridge.drawPreview(c, 1F, Math.round(stx * uu), Math.round(sty * uu), Math.round((stx + stw) * uu), Math.round((sty + sth) * uu));
			c.disableScissor();
		} else if (livePlayer(c, "death:" + e.id, List.of(), stx, sty, stw, sth)) {
			// your skin standing, and the animation's own little icon in the corner so the cards are told apart
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(stx + stw - 17, sty + 2);
			m.scale(0.9F, 0.9F);
			c.drawItem(new net.minecraft.item.ItemStack(e.icon), 0, 0);
			m.popMatrix();
		} else { // your skin standing still, like in the preview panel
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(stx + stw / 2F - 12, sty + sth / 2F - 12);
			m.scale(1.5F, 1.5F);
			c.drawItem(new net.minecraft.item.ItemStack(e.icon), 0, 0);
			m.popMatrix();
		}
		String name = e.title.replaceFirst("^Originals: ", "");
		int ny = y + ch - 15;
		if (!own) {
			if (shopItem != null) {
				String pr = String.valueOf(shopItem.finalPrice);
				int pw = Gfx.tw(pr);
				Gfx.text(c, pr, x + cw - 6 - pw, ny + 1, 0xFFFFD27A);
				Gfx.coin(c, x + cw - 18 - pw, ny, 9);
				Gfx.textClip(c, name, x + 6, ny + 1, cw - 30 - pw, Theme.TEXT);
			} else {
				Gfx.textClip(c, name, x + 6, ny + 1, cw - 12, Theme.DIM);
			}
		} else {
			int sxx = x + cw - 24;
			boolean sh = in(mx, my, sxx - 2, ny - 2, 22, 14) && my >= clipY0 && my < clipY1;
			miniSwitch(c, "d" + e.id, sxx, ny, on, sh);
			Gfx.textClip(c, name, x + 6, ny + 1, cw - 36, Theme.TEXT);
			region(sxx - 2, ny - 2, 22, 14, () -> equipDeath(e));
		}
		if (hov && Gfx.mc().player != null) {
			int vbw = Gfx.tw("View") + 16, vbx = x + (cw - vbw) / 2, vby = sty + sth - 20;
			boolean vh2 = in(mx, my, vbx, vby, vbw, 16);
			Gfx.rrect(c, vbx, vby, vbw, 16, 5, vh2 ? Theme.accentHover() : Theme.accent());
			Gfx.textC(c, "View", vbx + vbw / 2, vby + 4, 0xFFFFFFFF);
			region(vbx, vby, vbw, 16, () -> viewDeath(e));
		}
	}

	/** Right panel for a death animation: live stage, text, Buy / Equip, View. */
	private void drawDeathPanel(DrawContext c, int mx, int my, int px, int iy0, int prevW, int iy1, int accent, dev.solclient.fx.DeathAnimBridge.Entry cur) {
		Gfx.rrectBorder(c, px, iy0, prevW, iy1 - iy0, 10, Gfx.blend(TILE, 0xFF000000, 0.25F), Theme.CARD_BORDER);
		float uu = 1F / f;
		int stageH = Math.min(190, (iy1 - iy0) - 150);
		if (stageH > 60) {
			Gfx.rrect(c, px + 8, iy0 + 8, prevW - 16, stageH, 8, 0xFF15161B);
			c.enableScissor(px + 8, iy0 + 8, px + prevW - 8, iy0 + 8 + stageH);
			dev.solclient.fx.DeathAnimBridge.drawPreview(c, 1F, Math.round((px + 8) * uu), Math.round((iy0 + 8) * uu), Math.round((px + prevW - 8) * uu), Math.round((iy0 + 8 + stageH) * uu));
			c.disableScissor();
		}
		int ty = iy0 + 8 + Math.max(0, stageH) + 10;
		Gfx.textFit(c, fit(cur.title, prevW - 16), px + prevW / 2, prevW - 6, ty, Theme.TEXT);
		Gfx.textFit(c, "Kill effect", px + prevW / 2, prevW - 6, ty + 11, Theme.DIM);
		int dy = ty + 25;
		for (String l : wrap(cur.description, prevW - 20, 3)) {
			Gfx.textFit(c, l, px + prevW / 2, prevW - 6, dy, 0xFFB8BCC8);
			dy += 11;
		}
		int bx = px + 8, bw = prevW - 16, by = iy1 - 30;
		final var fc = cur;
		boolean own = dev.solclient.fx.DeathAnimBridge.owned(cur);
		var si = dev.solclient.social.Social.deathItem(cur.id, cur.title);
		if (!own && si != null) {
			boolean ask = confirming("buyda" + cur.id);
			boolean afford = dev.solclient.social.Social.coins < 0 || dev.solclient.social.Social.coins >= si.finalPrice;
			button(c, mx, my, bx, by, bw, 22, ask ? "Confirm " + si.finalPrice + "?" : "Buy  " + si.finalPrice, "lbuyda",
					ask ? Theme.GREEN_BG : afford ? accent : Theme.OFF_BG, ask ? Theme.GREEN_BG_HOVER : afford ? Theme.accentHover() : Theme.OFF_BG_HOVER,
					ask ? Theme.GREEN : afford ? 0xFFFFFFFF : Theme.DIM, () -> {
						if (!afford) {
							Social.say("Not enough coins", true);
						} else if (confirm("buyda" + fc.id, "Click again to buy " + fc.title)) {
							Social.buy(si);
						}
					});
		} else if (!own) {
			Gfx.rrect(c, bx, by, bw, 22, 7, Theme.OFF_BG);
			Gfx.textFit(c, "Not for sale yet", px + prevW / 2, prevW - 24, by + 7, Theme.DIM);
		} else {
			boolean on = dev.solclient.fx.DeathAnimBridge.isEquipped(cur);
			button(c, mx, my, bx, by, bw, 22, on ? "Unequip" : "Equip", "lsw" + cur.id, on ? Theme.OFF_BG : accent, on ? Theme.OFF_BG_HOVER : Theme.accentHover(), 0xFFFFFFFF, () -> equipDeath(fc));
		}
		button(c, mx, my, bx, by - 26, bw, 20, "View in world", "lviewda", TILE, TILE_HOVER, Theme.TEXT, () -> viewDeath(fc));
	}

	/** Plays an animation in front of you (the menu closes so you can watch). */
	private void viewDeath(dev.solclient.fx.DeathAnimBridge.Entry e) {
		if (e == null || Gfx.mc().player == null) {
			return;
		}
		closeMenu();
		dev.solclient.fx.DeathAnimBridge.play(e);
	}

	/** Plays a kill effect a few steps in front of you (the menu closes so you can watch it). */
	private void viewKillEffect(Social.Item it) {
		var p = Gfx.mc().player;
		if (p == null) {
			return;
		}
		closeMenu();
		double yaw = Math.toRadians(p.getYaw());
		Identifier skin = null;
		try {
			skin = p.getSkin().body().texturePath();
		} catch (RuntimeException ignored) {
		}
		dev.solclient.fx.killfx.KillFx.play(Social.killEffectKey(it), p.getX() - Math.sin(yaw) * 4, p.getY(), p.getZ() + Math.cos(yaw) * 4, skin, -1);
	}

	private static int parseColour(String s, int def) {
		try {
			String h = s.startsWith("#") ? s.substring(1) : s;
			if (h.length() == 3) {
				h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
			}
			return 0xFF000000 | Integer.parseInt(h.substring(0, 6), 16);
		} catch (RuntimeException e) {
			return def;
		}
	}

	// ---------------------------------------------------- notifications view
	private void drawNotifications(DrawContext c, int mx, int my, int x, int y, int w, int h, int ix0, int ix1, int iy0, int iy1, int barX, int accent) {
		Gfx.text(c, "Notifications", ix0, y + 13, Theme.TEXT);
		if (drawOffline(c, mx, my, (ix0 + ix1) / 2, (iy0 + iy1) / 2, ix1 - ix0 - 20)) {
			maxScroll = 0;
			return;
		}
		List<Social.Notif> list = Social.notifications;
		if (list.isEmpty()) {
			Gfx.textC(c, "No notifications", (ix0 + ix1) / 2, (iy0 + iy1) / 2 - 4, Theme.DIM);
			maxScroll = 0;
			return;
		}
		int rowH = 36, gap = 6;
		int contentH = list.size() * (rowH + gap) - gap;
		maxScroll = Math.max(0, contentH - (iy1 - iy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		clipY0 = iy0;
		clipY1 = iy1;
		c.enableScissor(ix0 - 4, iy0, ix1 + 4, iy1);
		for (int i = 0; i < list.size(); i++) {
			Social.Notif n = list.get(i);
			int ry = iy0 + i * (rowH + gap) - Math.round(scroll);
			if (ry + rowH < iy0 || ry > iy1) {
				continue;
			}
			int rw = ix1 - ix0;
			boolean hov = in(mx, my, ix0, ry, rw, rowH) && my >= iy0 && my < iy1;
			float a = anim("nt" + n.id, hov);
			Gfx.rrectBorder(c, ix0, ry, rw, rowH, 9, Gfx.blend(TILE, TILE_HOVER, a), Theme.CARD_BORDER);
			Gfx.circle(c, ix0 + 13, ry + rowH / 2F, 3.5F, n.read ? 0xFF4A4D5A : accent);
			Gfx.text(c, fit(n.title, rw - 60), ix0 + 24, ry + (n.detail == null ? 14 : 7), Theme.TEXT);
			if (n.detail != null) {
				Gfx.text(c, fit(n.detail, rw - 60), ix0 + 24, ry + 20, Theme.DIM);
			}
			region(ix0, ry, rw - 30, rowH, () -> {
				if ("friend_request".equals(n.kind) || "message".equals(n.kind) || "invite".equals(n.kind)) {
					friendsOpen = true;
				}
			});
			int dx = ix0 + rw - 26, dy = ry + (rowH - 20) / 2;
			boolean hx = in(mx, my, dx, dy, 20, 20);
			Gfx.rrect(c, dx, dy, 20, 20, 7, hx ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			drawX(c, dx + 6, dy + 6, 7, hx ? Theme.RED : Theme.DIM);
			region(dx, dy, 20, 20, () -> Social.deleteNotification(n));
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, barX, iy0, iy1, contentH);
	}

	// ---------------------------------------------------- capes & skins view
	private void pickUpload() {
		Thread t = new Thread(() -> {
			String path;
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.png"));
				filters.flip();
				path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Choose a skin (64x64 PNG)", "", filters, "PNG images", false);
			} catch (Throwable e) {
				Social.say("No file picker: drag the PNG onto the window", true);
				return;
			}
			if (path != null && !path.isBlank()) {
				Social.upload(java.nio.file.Path.of(path));
			}
		}, "plegumo-upload-picker");
		t.setDaemon(true);
		t.start();
	}

	private void drawCapes(DrawContext c, int mx, int my, int x, int y, int w, int h, int ix0, int ix1, int iy0, int iy1, int barX, int accent) {
		Gfx.text(c, "Capes & Skins", ix0, y + 13, Theme.TEXT);
		// two tabs: Skins | Capes (the capes you bought in the shop)
		int tabX = ix0 + Gfx.tw("Capes & Skins") + 14;
		String[] ctabs = {"Skins", "Capes"};
		for (int i = 0; i < 2; i++) {
			int tw = Gfx.tw(ctabs[i]) + 20;
			boolean sel = capesTab == i;
			pill(c, mx, my, tabX, y + 8, tw, 20, 7, "ctab" + i, sel, accent, TILE, TILE_HOVER);
			Gfx.textFit(c, ctabs[i], tabX + tw / 2, tw - 6, y + 14, sel ? 0xFFFFFFFF : Theme.TEXT);
			final int idx = i;
			region(tabX, y + 8, tw, 20, () -> {
				if (capesTab != idx) {
					capesTab = idx;
					lockerSel = null;
					scroll = 0;
					scrollTarget = 0;
					if (idx == 1) {
						Social.loadShop("All");
					}
				}
			});
			tabX += tw + 4;
		}
		if (capesTab == 1) {
			if (drawOffline(c, mx, my, (ix0 + ix1) / 2, (iy0 + iy1) / 2, ix1 - ix0 - 20)) {
				maxScroll = 0;
				return;
			}
			drawOwnedCapes(c, mx, my, ix0, ix1, iy0, iy1, barX, accent);
			return;
		}
		int bw = Gfx.tw("Upload skin") + 24;
		// Microsoft account: goes to Mojang (nothing kept on our servers). Any other account: shared with every Caos player.
		button(c, mx, my, ix1 - bw, y + 8, bw, 20, "Upload skin", "upl", accent, Theme.accentHover(), 0xFFFFFFFF, () -> pickSkinFileThen(true));
		String hint = "or drag a PNG here - skin 64x64";
		if (tabX + Gfx.tw(hint) + 20 < ix1 - bw - 90) {
			Gfx.text(c, hint, tabX + 10, y + 13, Theme.DIM);
		}
		if (!Social.skins.isEmpty()) {
			// skins uploaded to the site before: still there, behind this switch
			String cl = showCloudSkins ? "My skins" : "Old cloud skins";
			int cw = Gfx.tw(cl) + 16;
			button(c, mx, my, ix1 - bw - cw - 6, y + 8, cw, 20, cl, "cloud", TILE, TILE_HOVER, Theme.TEXT, () -> {
				showCloudSkins = !showCloudSkins;
				scroll = 0;
				scrollTarget = 0;
			});
		}
		if (!showCloudSkins || Social.skins.isEmpty()) {
			showCloudSkins = false;
			int prevW = Math.min(170, (ix1 - ix0) / 3);
			int px = ix1 - prevW;
			drawSkins(c, mx, my, ix0, px - 10, iy0, iy1, px, iy0, prevW, accent);
			return;
		}
		if (!Social.capesState.isEmpty() && Social.skins.isEmpty()) {
			Gfx.textC(c, Social.capesState, (ix0 + ix1) / 2, (iy0 + iy1) / 2 - 4, Theme.DIM);
			maxScroll = 0;
			return;
		}

		int gap = 8, cardW = 92, cardH = 116;
		int inner = ix1 - ix0;
		int cols = Math.max(1, (inner + gap) / (cardW + gap));
		int skinRows = Math.max(1, (Social.skins.size() + cols - 1) / cols);
		int contentH = 20 + skinRows * (cardH + gap);
		maxScroll = Math.max(0, contentH - (iy1 - iy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		clipY0 = iy0;
		clipY1 = iy1;
		c.enableScissor(ix0 - 4, iy0, ix1 + 4, iy1);
		int yy = iy0 - Math.round(scroll);
		// capes are no longer uploaded by players (they took a lot of space): they come from the shop
		drawUploadSection(c, mx, my, "SKINS", Social.skins, false, ix0, ix1, yy, cols, cardW, cardH, gap, iy0, iy1, accent);
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, barX, iy0, iy1, contentH);
	}

	private static int capesTab; // 0 skins, 1 capes
	private static boolean showCloudSkins; // skins tab: the old list of skins kept on the Caos site

	/** Capes tab: the capes you own, with your player in 3D wearing the selected one (back view) and Equip / Unequip. */
	private void drawOwnedCapes(DrawContext c, int mx, int my, int ix0, int ix1, int iy0, int iy1, int barX, int accent) {
		int prevW = Math.min(170, (ix1 - ix0) / 3);
		int px = ix1 - prevW, gx0 = ix0, gx1 = px - 10;
		List<Social.Item> capes = Social.ownedCapes();
		// the cape picked before stays picked only while you still own it
		boolean found = false;
		for (Social.Item i : capes) {
			if (i.id != null && i.id.equals(lockerSel)) {
				found = true;
			}
		}
		if (!found) {
			lockerSel = null;
		}
		if (lockerSel == null) {
			for (Social.Item i : capes) {
				if (i.equipped) {
					lockerSel = i.id; // starts on the one you wear
				}
			}
		}
		Gfx.rrectBorder(c, px, iy0, prevW, iy1 - iy0, 10, Gfx.blend(TILE, 0xFF000000, 0.25F), Theme.CARD_BORDER);
		drawWearPreview(c, mx, my, px, iy0, prevW, iy1 - iy0, accent, capes, dev.solclient.skin.SkinChanger.myPreview());
		if (capes.isEmpty()) {
			boolean loading = Social.shopState.equals("Loading...");
			if (loading) {
				c.enableScissor(gx0 - 4, iy0, gx1 + 4, iy1);
				Gfx.skeletonCards(c, gx0, iy0, gx1, iy1, 6, 80);
				c.disableScissor();
			} else {
				Gfx.textC(c, "You don't own any capes yet", (gx0 + gx1) / 2, (iy0 + iy1) / 2 - 12, Theme.TEXT);
			}
			if (!loading) {
				Gfx.textC(c, "Buy capes in the Locker shop", (gx0 + gx1) / 2, (iy0 + iy1) / 2 + 2, Theme.DIM);
				int bw = Gfx.tw("Shop Capes") + 26;
				button(c, mx, my, (gx0 + gx1) / 2 - bw / 2, (iy0 + iy1) / 2 + 16, bw, 22, "Shop Capes", "lcapes", accent, Theme.accentHover(), 0xFFFFFFFF, () -> {
					showView(V_SHOP, 5);
					switchLocker(true);
					Social.loadShop("Capes");
				});
			}
			maxScroll = 0;
			return;
		}
		int gap = 7, minW = 74;
		int inner = gx1 - gx0;
		int cols = Math.max(1, (inner + gap) / (minW + gap));
		int cardW = (inner - (cols - 1) * gap) / cols;
		int cardH = cardW + 16;
		int rows = (capes.size() + cols - 1) / cols;
		int contentH = Math.max(0, rows * (cardH + gap) - gap) + 10;
		maxScroll = Math.max(0, contentH - (iy1 - iy0));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		clipY0 = iy0;
		clipY1 = iy1;
		c.enableScissor(gx0 - 4, iy0, gx1 + 4, iy1);
		for (int i = 0; i < capes.size(); i++) {
			int cx = gx0 + (i % cols) * (cardW + gap);
			int cy = iy0 + (i / cols) * (cardH + gap) - Math.round(scroll);
			if (cy + cardH < iy0 || cy > iy1) {
				continue;
			}
			drawItemCard(c, mx, my, capes.get(i), cx, cy, cardW, cardH, accent);
		}
		c.disableScissor();
		clipY0 = 0;
		clipY1 = vh;
		drawScrollbar(c, gx1 + 3, iy0, iy1, contentH);
	}

	private int drawUploadSection(DrawContext c, int mx, int my, String title, List<Social.Upload> list, boolean cape,
			int ix0, int ix1, int yy, int cols, int cardW, int cardH, int gap, int cy0, int cy1, int accent) {
		Gfx.text(c, title + "  (" + list.size() + ")", ix0, yy + 4, Theme.DIM);
		Long active = cape ? Social.activeCapeId : Social.activeSkinId;
		if (active != null) {
			String rm = cape ? "Remove cape" : "Remove skin";
			int rw = Gfx.tw(rm) + 16;
			button(c, mx, my, ix1 - rw, yy, rw, 16, rm, "rm" + title, Theme.OFF_BG, Theme.OFF_BG_HOVER, Theme.TEXT,
					() -> Social.setActive(cape ? null : Social.activeCapeId, cape ? Social.activeSkinId : null));
		}
		yy += 20;
		if (list.isEmpty()) {
			Gfx.text(c, cape ? "No capes yet" : "No skins yet", ix0, yy + 6, Theme.DIM);
			return yy + cardH + gap;
		}
		for (int i = 0; i < list.size(); i++) {
			Social.Upload u = list.get(i);
			int x = ix0 + (i % cols) * (cardW + gap);
			int y = yy + (i / cols) * (cardH + gap);
			if (y + cardH < cy0 || y > cy1) {
				continue;
			}
			boolean isActive = active != null && active == u.id;
			boolean hov = in(mx, my, x, y, cardW, cardH) && my >= cy0 && my < cy1;
			float a = anim("up" + u.id, hov);
			Gfx.rrectBorder(c, x, y, cardW, cardH, 10, Gfx.blend(TILE, TILE_HOVER, a), isActive ? accent : Theme.CARD_BORDER);
			Gfx.rrect(c, x + 6, y + 6, cardW - 12, 60, 8, Theme.FIELD);
			RemoteTextures.Tex t = RemoteTextures.get(u.imageUrl);
			if (t != null) {
				int sx = t.width() / 64;
				if (cape) {
					// front of the cape: 10x16 at (1,1) on a 64x32 sheet
					c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, t.id(), x + cardW / 2 - 16, y + 11, 1F * sx, 1F * sx, 32, 50, 10 * sx, 16 * sx, t.width(), t.height(), 0xFFFFFFFF);
				} else {
					// face + hat layer: 8x8 at (8,8) and (40,8) on a 64x64 sheet
					c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, t.id(), x + cardW / 2 - 22, y + 14, 8F * sx, 8F * sx, 44, 44, 8 * sx, 8 * sx, t.width(), t.height(), 0xFFFFFFFF);
					c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, t.id(), x + cardW / 2 - 22, y + 14, 40F * sx, 8F * sx, 44, 44, 8 * sx, 8 * sx, t.width(), t.height(), 0xFFFFFFFF);
				}
			}
			Gfx.textFit(c, fit(u.name == null ? "Untitled" : u.name, cardW - 10), x + cardW / 2, cardW - 6, y + 72, Theme.TEXT);

			int by = y + cardH - 26, bx = x + 5, bw = cardW - 10;
			if (isActive) {
				Gfx.rrect(c, bx, by, bw, 20, 7, Theme.GREEN_BG);
				Gfx.textFit(c, "Active", x + cardW / 2, cardW - 6, by + 6, Theme.GREEN);
			} else {
				button(c, mx, my, bx, by, bw, 20, "Use", "use" + u.id, accent, Theme.accentHover(), 0xFFFFFFFF,
						() -> Social.setActive(cape ? Long.valueOf(u.id) : Social.activeCapeId, cape ? Social.activeSkinId : Long.valueOf(u.id)));
			}
			// delete (top-right corner of the preview)
			int dx = x + cardW - 22, dy = y + 9;
			boolean hx = in(mx, my, dx, dy, 14, 14);
			boolean ask = confirming("del" + u.id);
			Gfx.rrect(c, dx, dy, 14, 14, 5, ask ? Theme.RED : hx ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			drawX(c, dx + 4, dy + 4, 5, ask ? 0xFFFFFFFF : hx ? Theme.RED : Theme.DIM);
			region(dx, dy, 14, 14, () -> {
				if (confirm("del" + u.id, "Click X again to delete")) {
					Social.deleteUpload(u);
				}
			});
		}
		int rows = (list.size() + cols - 1) / cols;
		return yy + rows * (cardH + gap);
	}

	// --------------------------------------------------------- friends panel
	// "Social" panel (like Essential): three buttons on top - Add Friend, Requests (with a counter), Search.
	// List: ONLINE / OFFLINE sections with the Minecraft head, nick and what they are doing.
	// Add Friend: type a nick (Enter) and, below, the people on your server who use Plegumo with a one-click "+".
	/** One line of the friends list: a section title, a request, a friend or a suggestion. */
	private record FRow(int type, Social.Request req, Social.User user, String title, String nick) {
	}

	private static final int R_TITLE = 0, R_IN = 1, R_OUT = 2, R_FRIEND = 3, R_SUGGEST = 4, R_EMPTY = 5, R_GROUP = 6, R_CREATE = 7;
	private boolean creatingGroup;
	private final TextBox groupName = new TextBox(24);
	private final java.util.Set<String> groupPick = new java.util.LinkedHashSet<>();
	private static int friendTab; // 0 friends, 1 add, 2 requests
	private final java.util.Set<String> requested = new java.util.HashSet<>();

	private List<FRow> friendRows() {
		List<FRow> out = new ArrayList<>();
		if (friendTab == 2) {
			List<Social.Request> in = Social.incoming, outReq = Social.outgoing;
			out.add(new FRow(R_TITLE, null, null, "RECEIVED (" + in.size() + ")", null));
			for (Social.Request r : in) {
				out.add(new FRow(R_IN, r, r.user(), null, null));
			}
			if (in.isEmpty()) {
				out.add(new FRow(R_EMPTY, null, null, "No requests right now", null));
			}
			out.add(new FRow(R_TITLE, null, null, "SENT (" + outReq.size() + ")", null));
			for (Social.Request r : outReq) {
				out.add(new FRow(R_OUT, r, r.user(), null, null));
			}
			if (outReq.isEmpty()) {
				out.add(new FRow(R_EMPTY, null, null, "Nothing pending", null));
			}
			return out;
		}
		if (friendTab == 1) {
			// suggestions: Plegumo players around you who aren't your friends yet
			MinecraftClient mc = MinecraftClient.getInstance();
			List<FRow> sug = new ArrayList<>();
			if (mc.world != null) {
				java.util.Set<String> known = new java.util.HashSet<>();
				for (Social.User f : Social.friends) {
					known.add(f.display().toLowerCase(Locale.ROOT));
				}
				for (Social.Request r : Social.outgoing) {
					known.add(r.user().display().toLowerCase(Locale.ROOT));
				}
				for (net.minecraft.client.network.AbstractClientPlayerEntity p : mc.world.getPlayers()) {
					String n = p.getName().getString();
					if (p == mc.player || known.contains(n.toLowerCase(Locale.ROOT)) || !dev.solclient.social.SolarBadge.uses(p.getUuid())) {
						continue;
					}
					sug.add(new FRow(R_SUGGEST, null, null, null, n));
				}
			}
			out.add(new FRow(R_TITLE, null, null, "CAOS PLAYERS NEAR YOU", null));
			if (sug.isEmpty()) {
				out.add(new FRow(R_EMPTY, null, null, mc.world == null ? "Join a server to see them" : "Nobody around right now", null));
			}
			out.addAll(sug);
			if (!Social.outgoing.isEmpty()) {
				out.add(new FRow(R_TITLE, null, null, "SENT (" + Social.outgoing.size() + ")", null));
				for (Social.Request r : Social.outgoing) {
					out.add(new FRow(R_OUT, r, r.user(), null, null));
				}
			}
			return out;
		}
		String q = friendSearch.text().toLowerCase(Locale.ROOT).trim();
		List<FRow> onl = new ArrayList<>(), off = new ArrayList<>();
		for (Social.User u : Social.friends) {
			if (q.isEmpty() || u.display().toLowerCase(Locale.ROOT).contains(q)) {
				(u.online() ? onl : off).add(new FRow(R_FRIEND, null, u, null, null));
			}
		}
		java.util.Comparator<FRow> byName = (a, b) -> {
			int s1 = Boolean.compare(b.user().onMyServer(), a.user().onMyServer());
			return s1 != 0 ? s1 : a.user().display().compareToIgnoreCase(b.user().display());
		};
		onl.sort(byName);
		off.sort(byName);
		if (!Social.incoming.isEmpty() && q.isEmpty()) {
			out.add(new FRow(R_TITLE, null, null, "REQUESTS (" + Social.incoming.size() + ")", null));
			for (Social.Request r : Social.incoming) {
				out.add(new FRow(R_IN, r, r.user(), null, null));
			}
		}
		if (q.isEmpty() && Social.groupsSupported) {
			List<Social.Group> gs = Social.groups;
			out.add(new FRow(R_TITLE, null, null, "GROUPS - " + gs.size(), null));
			for (Social.Group g : gs) {
				out.add(new FRow(R_GROUP, null, null, g.key(), null));
			}
			if (gs.size() < Social.GROUPS_PER_USER) {
				out.add(new FRow(R_CREATE, null, null, null, null));
			}
		}
		if (!onl.isEmpty()) {
			out.add(new FRow(R_TITLE, null, null, "ONLINE - " + onl.size(), null));
			out.addAll(onl);
		}
		if (!off.isEmpty()) {
			out.add(new FRow(R_TITLE, null, null, "OFFLINE - " + off.size(), null));
			out.addAll(off);
		}
		return out;
	}

	/** Small square header button with an icon, a tooltip and an optional red counter. */
	private void socialButton(DrawContext c, int mx, int my, int x, int y, String icon, String tip, boolean active, int count, int accent, boolean settled, Runnable run) {
		boolean h = in(mx, my, x, y, 20, 20);
		float a = anim("sb" + tip, h);
		Gfx.rrect(c, x, y, 20, 20, 6, active ? accent : Gfx.blend(Theme.OFF_BG, Theme.OFF_BG_HOVER, a));
		Gfx.tex(c, Theme.icon2(icon), x + 4, y + 4, 12, 12, 64, 64, active ? 0xFFFFFFFF : Theme.TEXT);
		if (count > 0) {
			badge(c, x + 18, y + 2, count);
		}
		if (h) {
			tooltipText = tip;
		}
		if (settled) {
			region(x, y, 20, 20, run);
		}
	}

	private void drawFriends(DrawContext c, int mx, int my, int x, int right, int y, int h, int accent) {
		Gfx.rrectBorder(c, x, y, Math.max(2, right - x), h, 12, SUB, Theme.PANEL_BORDER);
		fbx = x;
		fby = y;
		fbw = Math.max(0, right - x);
		fbh = h;
		boolean settled = aF >= 0.99F;
		int px = right - FW;
		c.enableScissor(x, y, right, y + h);
		int ix = px + 8, iw = FW - 16;
		addMode = friendTab == 1;

		Gfx.text(c, "Social", ix + 2, y + 12, Theme.TEXT);
		if (Social.me != null) {
			int bx = px + FW - 8 - 20;
			socialButton(c, mx, my, bx, y + 7, "search", "Search friends", friendTab == 0 && focus == F_FRIEND_SEARCH, 0, accent, settled, () -> {
				friendTab = 0;
				friendScroll = 0;
				focus = F_FRIEND_SEARCH;
			});
			socialButton(c, mx, my, bx - 23, y + 7, "bell", "Friend requests", friendTab == 2, Social.incoming.size(), accent, settled, () -> {
				friendTab = friendTab == 2 ? 0 : 2;
				friendScroll = 0;
				focus = F_NONE;
			});
			socialButton(c, mx, my, bx - 46, y + 7, "friends_hud", "Add friend", friendTab == 1, 0, accent, settled, () -> {
				friendTab = friendTab == 1 ? 0 : 1;
				friendSearch.clear();
				friendScroll = 0;
				focus = friendTab == 1 ? F_FRIEND_SEARCH : F_NONE;
			});
		}
		Gfx.rrectF(c, ix, y + 33, iw, 1, 0.5F, Theme.PANEL_BORDER);

		if (Social.me == null) {
			drawOffline(c, mx, my, px + FW / 2, y + h / 2, iw - 4);
			c.disableScissor();
			return;
		}

		if (creatingGroup) {
			drawCreateGroup(c, mx, my, ix, iw, y + 40, y + h - 10, settled, accent);
			c.disableScissor();
			return;
		}
		int ly0;
		if (friendTab == 2) {
			Gfx.text(c, "Friend requests", ix + 2, y + 42, Theme.DIM);
			ly0 = y + 56;
		} else {
			int fy = y + 40;
			if (friendTab == 1) {
				int sendW = 34;
				field(c, ix, fy, iw - sendW - 4, 22, friendSearch, "Nick or username", focus == F_FRIEND_SEARCH, accent, false);
				if (settled) {
					region(ix, fy, iw - sendW - 4, 22, () -> focus = F_FRIEND_SEARCH);
				}
				boolean can = !friendSearch.text().isBlank();
				button(c, mx, my, ix + iw - sendW, fy, sendW, 22, "Add", "fadd", can ? accent : Theme.OFF_BG, can ? Theme.accentHover() : Theme.OFF_BG_HOVER,
						can ? 0xFFFFFFFF : Theme.DIM, () -> {
							if (!friendSearch.text().isBlank()) {
								Social.addFriend(friendSearch.text());
								friendSearch.clear();
							}
						});
			} else {
				field(c, ix, fy, iw, 22, friendSearch, "Search friends...", focus == F_FRIEND_SEARCH, accent, true);
				if (settled) {
					region(ix, fy, iw, 22, () -> focus = F_FRIEND_SEARCH);
				}
			}
			ly0 = fy + 30;
		}

		List<FRow> rows = friendRows();
		int ly1 = y + h - 10;
		int rowH = 32, rg = 4, titleH = 16;
		int contentH = 0;
		for (FRow r : rows) {
			contentH += (r.type() == R_TITLE || r.type() == R_EMPTY ? titleH : rowH) + rg;
		}
		contentH = Math.max(0, contentH - rg);
		int maxFs = Math.max(0, contentH - (ly1 - ly0));
		friendMax = maxFs;
		friendScroll = Math.max(0, Math.min(maxFs, friendScroll));

		int oc0 = clipY0, oc1 = clipY1;
		clipY0 = ly0;
		clipY1 = ly1;
		c.enableScissor(ix - 2, ly0, right, ly1);
		int ry = ly0 - Math.round(friendScroll);
		for (FRow r : rows) {
			int rh = r.type() == R_TITLE || r.type() == R_EMPTY ? titleH : rowH;
			if (ry + rh >= ly0 && ry <= ly1) {
				drawFriendRow(c, mx, my, r, ix, ry, iw - 6, rh, ly0, ly1, settled, accent);
			}
			ry += rh + rg;
		}
		String pp = dev.solclient.social.Presence.problem;
		if (pp != null && friendTab == 0) {
			int py = ry + 2;
			for (String l : wrap(pp, iw - 8, 3)) {
				Gfx.text(c, l, ix + 2, py, 0xFF8A6A50);
				py += 10;
			}
			ry = py + 4;
		}
		if (friendTab == 0 && Social.friends.isEmpty() && Social.incoming.isEmpty() && Social.friendsLoaded) {
			Gfx.textFit(c, "No friends yet", px + FW / 2, FW - 6, ry + 8, Theme.DIM);
			int bw = Gfx.tw("Add a friend") + 20;
			button(c, mx, my, px + FW / 2 - bw / 2, ry + 22, bw, 20, "Add a friend", "faddempty", accent, Theme.accentHover(), 0xFFFFFFFF, () -> {
				friendTab = 1;
				focus = F_FRIEND_SEARCH;
			});
		}
		c.disableScissor();
		clipY0 = oc0;
		clipY1 = oc1;
		if (maxFs > 0) {
			int trackH = ly1 - ly0;
			int barH = Math.max(14, trackH * trackH / Math.max(trackH, contentH));
			int barY = ly0 + Math.round((trackH - barH) * (friendScroll / maxFs));
			Gfx.rrectF(c, right - 7, ly0, 3, trackH, 1.5F, 0x14FFFFFF);
			Gfx.rrectF(c, right - 7, barY, 3, barH, 1.5F, 0x66FFFFFF);
		}
		c.disableScissor();
	}

	/** Two stacked heads of a group (the first members that are not you). */
	/** A small speech bubble with three dots (friend list "chat" hint). */
	private static void chatGlyph(DrawContext c, int x, int y, int col) {
		Gfx.rrect(c, x, y, 12, 9, 3, col);
		c.fill(x + 2, y + 8, x + 5, y + 11, col);
		c.fill(x + 3, y + 10, x + 4, y + 11, col);
		for (int i = 0; i < 3; i++) {
			c.fill(x + 3 + i * 3, y + 4, x + 4 + i * 3, y + 5, 0xFF16181F);
		}
	}

	private void groupHeads(DrawContext c, Social.Group g, int x, int y, int s) {
		dev.solclient.chat.GroupIcons.Preset logo = dev.solclient.chat.GroupIcons.of(g.icon);
		if (logo != null) {
			dev.solclient.chat.GroupIcons.draw(c, logo, x, y, s);
			return;
		}
		List<Social.User> others = new ArrayList<>();
		for (Social.User u : g.members) {
			if (Social.me == null || u.id == null || !u.id.equals(Social.me.id)) {
				others.add(u);
			}
		}
		int hs = Math.round(s * 0.72F);
		if (others.size() >= 2) {
			head(c, others.get(1).mcUsername, others.get(1), x + s - hs, y, hs, 0);
			head(c, others.get(0).mcUsername, others.get(0), x, y + s - hs, hs, 0);
		} else if (others.size() == 1) {
			head(c, others.get(0).mcUsername, others.get(0), x, y, s, 0);
		} else {
			Gfx.rrect(c, x, y, s, s, 5, 0xFF2C2F38);
		}
	}

	private static String fitRich(String s, int w) {
		if (s == null) {
			return "";
		}
		s = s.replace('\n', ' ');
		if (dev.solclient.chat.Emoji.width(s) <= w) {
			return s;
		}
		while (s.length() > 1 && dev.solclient.chat.Emoji.width(s + "..") > w) {
			s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
		}
		return s + "..";
	}

	/** New group: name + pick up to 9 friends. */
	private void drawCreateGroup(DrawContext c, int mx, int my, int ix, int iw, int y, int bottom, boolean settled, int accent) {
		Gfx.text(c, "New group", ix + 2, y + 2, Theme.TEXT);
		field(c, ix, y + 14, iw, 22, groupName, "Group name", focus == F_GROUP, accent, false);
		if (settled) {
			region(ix, y + 14, iw, 22, () -> focus = F_GROUP);
		}
		int max = Social.GROUP_MAX_MEMBERS - 1;
		Gfx.text(c, "FRIENDS  " + groupPick.size() + "/" + max, ix + 2, y + 44, Theme.DIM);
		int ly = y + 56, rowH = 24, ly1 = bottom - 30;
		c.enableScissor(ix - 2, ly, ix + iw + 2, ly1);
		for (Social.User u : Social.friends) {
			if (ly + rowH > ly1) {
				break;
			}
			boolean on = groupPick.contains(u.id);
			boolean hov = in(mx, my, ix, ly, iw, rowH - 2);
			Gfx.rrect(c, ix, ly, iw, rowH - 2, 7, hov ? TILE_HOVER : TILE);
			head(c, u.mcUsername, u, ix + 4, ly + 3, 16, 0);
			Gfx.text(c, fit(u.display(), iw - 50), ix + 25, ly + 7, Theme.TEXT);
			int bx = ix + iw - 18, by = ly + 5;
			Gfx.rrect(c, bx, by, 12, 12, 3, on ? accent : 0xFF0A0B10);
			if (!on) {
				Gfx.rrect(c, bx + 1, by + 1, 10, 10, 2, 0xFF1E2129);
			} else {
				Gfx.textC(c, "\u2713", bx + 6, by + 2, 0xFFFFFFFF);
			}
			final String id = u.id;
			if (settled) {
				region(ix, ly, iw, rowH - 2, () -> {
					if (!groupPick.remove(id) && groupPick.size() < max) {
						groupPick.add(id);
					}
				});
			}
			ly += rowH;
		}
		if (Social.friends.isEmpty()) {
			Gfx.textFit(c, "Add friends first", ix + iw / 2, iw - 6, ly + 6, Theme.DIM);
		}
		c.disableScissor();
		int bw = (iw - 4) / 2, by = bottom - 24;
		button(c, mx, my, ix, by, bw, 22, "Cancel", "gcancel", TILE, TILE_HOVER, Theme.TEXT, () -> creatingGroup = false);
		boolean can = !groupName.text().isBlank() && !groupPick.isEmpty();
		button(c, mx, my, ix + bw + 4, by, bw, 22, "Create", "gcreate", can ? accent : Theme.OFF_BG, can ? Theme.accentHover() : Theme.OFF_BG_HOVER,
				can ? 0xFFFFFFFF : Theme.DIM, () -> {
					if (!groupName.text().isBlank() && !groupPick.isEmpty()) {
						Social.createGroup(groupName.text().trim(), new ArrayList<>(groupPick), this::openChatWith);
						creatingGroup = false;
					}
				});
	}

	/** Group chat: members (owner can kick and add friends), leave / delete. */
	private void drawGroupMembers(DrawContext c, int mx, int my, Social.Group g, int ix, int iw, int y0, int y1, boolean settled, int accent) {
		int y = y0;
		Gfx.text(c, "MEMBERS  " + g.members.size() + "/" + Social.GROUP_MAX_MEMBERS, ix + 2, y, Theme.DIM);
		y += 12;
		boolean owner = g.owner();
		int bottom = y1 - (owner ? 52 : 28);
		c.enableScissor(ix - 2, y, ix + iw + 2, bottom);
		for (Social.User u : g.members) {
			if (y + 22 > bottom) {
				break;
			}
			Gfx.rrect(c, ix, y, iw, 20, 6, TILE);
			head(c, u.mcUsername, u, ix + 3, y + 2, 16, 0);
			boolean isOwner = u.id != null && u.id.equals(g.ownerId);
			boolean isMe = Social.me != null && u.id != null && u.id.equals(Social.me.id);
			Gfx.text(c, fit(u.display() + (isMe ? " (you)" : ""), iw - 70), ix + 24, y + 6, Theme.TEXT);
			if (isOwner) {
				Gfx.text(c, "Owner", ix + iw - 8 - Gfx.tw("Owner") - (owner && !isMe ? 18 : 0), y + 6, 0xFFFFD27A);
			}
			if (owner && !isMe) {
				int kx = ix + iw - 17, ky = y + 3;
				boolean hk = in(mx, my, kx, ky, 14, 14);
				drawX(c, kx + 3, ky + 3, 8, hk ? Theme.RED : Theme.DIM);
				if (hk) {
					tooltipText = "Remove " + u.display();
				}
				if (settled) {
					region(kx, ky, 14, 14, () -> Social.removeFromGroup(g, u.id));
				}
			}
			y += 22;
		}
		if (owner && g.members.size() < Social.GROUP_MAX_MEMBERS) {
			y += 4;
			Gfx.text(c, "ADD FRIENDS", ix + 2, y, Theme.DIM);
			y += 12;
			for (Social.User u : Social.friends) {
				if (g.member(u.id) != null) {
					continue;
				}
				if (y + 22 > bottom) {
					break;
				}
				Gfx.rrect(c, ix, y, iw, 20, 6, TILE);
				head(c, u.mcUsername, u, ix + 3, y + 2, 16, 0);
				Gfx.text(c, fit(u.display(), iw - 50), ix + 24, y + 6, Theme.TEXT);
				int ax = ix + iw - 20, ay = y + 2;
				boolean ha = in(mx, my, ax, ay, 16, 16);
				Gfx.rrect(c, ax, ay, 16, 16, 5, ha ? Theme.accentHover() : accent);
				c.fill(ax + 4, ay + 7, ax + 12, ay + 9, 0xFFFFFFFF);
				c.fill(ax + 7, ay + 4, ax + 9, ay + 12, 0xFFFFFFFF);
				if (settled) {
					region(ax, ay, 16, 16, () -> Social.addToGroup(g, u.id));
				}
				y += 22;
			}
		}
		c.disableScissor();
		int by = y1 - 24;
		button(c, mx, my, ix, by, iw, 22, "Leave group", "gleave", 0xFF4A1C1F, 0xFF6A2328, 0xFFFFB4B4, () -> {
			if (Social.me != null) {
				Social.removeFromGroup(g, Social.me.id);
				membersOpen = false;
				closeChat();
			}
		});
		if (owner) {
			button(c, mx, my, ix, by - 26, iw, 22, confirming("gdel" + g.id) ? "Click again to delete" : "Delete group", "gdel", TILE, TILE_HOVER, Theme.RED, () -> {
				if (confirm("gdel" + g.id, "Click again to delete " + g.name)) {
					Social.deleteGroup(g);
					membersOpen = false;
					closeChat();
				}
			});
		}
	}

	/** Second line under a friend's nick. */
	private static String friendLine(Social.User u) {
		if (u.onMyServer()) {
			return "On your server";
		}
		StatusBadge.Status st = u.state();
		boolean playing = u.activity != null && !u.activity.isBlank();
		return switch (st) {
			case AWAY -> playing ? dev.solclient.Lang.t("Away") + " · " + u.activity : "Away";
			case BUSY -> playing ? dev.solclient.Lang.t("Busy") + " · " + u.activity : "Busy";
			case MOBILE -> "On the phone";
			case ONLINE -> playing ? u.activity : "Online";
			default -> "Offline";
		};
	}

	private void drawFriendRow(DrawContext c, int mx, int my, FRow r, int ix, int ry, int rw, int rh, int ly0, int ly1, boolean settled, int accent) {
		if (r.type() == R_CREATE) {
			boolean hov = in(mx, my, ix, ry + 4, rw, rh - 8) && my >= ly0 && my < ly1;
			Gfx.buttonBody(c, ix, ry + 4, rw, rh - 8, hov ? Gfx.BTN_HOVER : Gfx.BTN_BODY, false);
			Gfx.textFit(c, "+  Create group", ix + rw / 2, rw - 6, ry + rh / 2 - 5, Theme.TEXT);
			if (settled) {
				region(ix, ry + 4, rw, rh - 8, () -> {
					creatingGroup = true;
					groupName.clear();
					groupPick.clear();
					focus = F_GROUP;
				});
			}
			return;
		}
		if (r.type() == R_GROUP) {
			Social.Group g = Social.group(r.title());
			if (g == null) {
				return;
			}
			boolean hov = in(mx, my, ix, ry, rw, rh) && my >= ly0 && my < ly1;
			boolean sel = g.key().equals(chatId);
			Gfx.rrectBorder(c, ix, ry, rw, rh, 9, sel ? Gfx.blend(TILE, accent, 0.18F) : hov ? TILE_HOVER : TILE, sel ? accent : Theme.CARD_BORDER);
			groupHeads(c, g, ix + 4, ry + 5, 22);
			String gn = g.name == null ? "Group" : g.name;
			int cnt = g.members.size();
			Gfx.text(c, fit(gn, rw - 60), ix + 31, ry + 7, Theme.TEXT);
			int bx = ix + 31 + Math.min(rw - 60, Gfx.tw(gn)) + 4;
			String cs = String.valueOf(cnt);
			Gfx.rrect(c, bx, ry + 6, Gfx.tw(cs) + 6, 10, 4, 0xFF2C2F3A);
			Gfx.text(c, cs, bx + 3, ry + 7, Theme.DIM);
			String last = g.lastBody == null ? cnt + " members" : dev.solclient.chat.Gifs.isGif(g.lastBody) ? "GIF" : g.lastBody;
			dev.solclient.chat.Emoji.draw(c, fitRich(last, rw - 36), ix + 31, ry + 18, Theme.DIM);
			if (settled) {
				region(ix, ry, rw, rh, () -> openChatWith(g.key()));
			}
			return;
		}
		if (r.type() == R_TITLE) {
			Gfx.text(c, r.title(), ix + 2, ry + 5, Theme.DIM);
			return;
		}
		if (r.type() == R_EMPTY) {
			Gfx.text(c, r.title(), ix + 4, ry + 4, 0xFF5A5E6E);
			return;
		}
		boolean hov = in(mx, my, ix, ry, rw, rh) && my >= ly0 && my < ly1;
		if (r.type() == R_SUGGEST) {
			String n = r.nick();
			float a = anim("sg" + n, hov);
			Gfx.rrectBorder(c, ix, ry, rw, rh, 9, Gfx.blend(TILE, TILE_HOVER, a), Theme.CARD_BORDER);
			head(c, n, null, ix + 5, ry + 6, 20, 0);
			Gfx.text(c, fit(n, rw - 62), ix + 31, ry + 7, Theme.TEXT);
			Gfx.text(c, "Uses Caos", ix + 31, ry + 18, Theme.DIM);
			boolean sent = requested.contains(n.toLowerCase(Locale.ROOT));
			int bx = ix + rw - 26, by = ry + 7;
			boolean hb = in(mx, my, bx, by, 20, 18);
			Gfx.rrect(c, bx, by, 20, 18, 6, sent ? Theme.OFF_BG : hb ? Theme.accentHover() : accent);
			if (sent) {
				Gfx.textC(c, "...", bx + 10, by + 5, Theme.DIM);
			} else {
				c.fill(bx + 6, by + 8, bx + 14, by + 10, 0xFFFFFFFF);
				c.fill(bx + 9, by + 5, bx + 11, by + 13, 0xFFFFFFFF);
			}
			if (hb) {
				tooltipText = sent ? "Request sent" : "Send friend request";
			}
			if (settled && !sent) {
				region(bx, by, 20, 18, () -> {
					requested.add(n.toLowerCase(Locale.ROOT));
					Social.addFriend(n);
				});
			}
			return;
		}
		Social.User u = r.user();
		String key = (r.type() == R_FRIEND ? "f" : "q") + (r.req() != null ? r.req().id() : u.id);
		boolean sel = r.type() == R_FRIEND && u.id != null && u.id.equals(chatId);
		float a = anim("fr" + key, hov);
		boolean removing = r.type() == R_FRIEND && confirming("unf" + u.id);
		Gfx.rrectBorder(c, ix, ry, rw, rh, 9, removing ? 0xFF3A1A1D : sel ? Gfx.blend(TILE, accent, 0.18F) : Gfx.blend(TILE, TILE_HOVER, a),
				sel ? accent : removing ? Theme.RED : Theme.CARD_BORDER);
		int dot = r.type() != R_FRIEND ? 0 : u.onMyServer() ? accent : u.online() ? 0xFF4ADE80 : 0xFF5A5E6E;
		head(c, u.mcUsername, u, ix + 5, ry + 6, 20, dot);

		if (r.type() == R_IN) {
			Gfx.text(c, fit(u.display(), rw - 74), ix + 31, ry + 7, Theme.TEXT);
			Gfx.text(c, fit("wants to be friends", rw - 74), ix + 31, ry + 18, Theme.DIM);
			int bx = ix + rw - 44, by = ry + (rh - 18) / 2;
			boolean h1 = in(mx, my, bx, by, 18, 18), h2 = in(mx, my, bx + 21, by, 18, 18);
			Gfx.rrect(c, bx, by, 18, 18, 6, h1 ? Theme.GREEN_BG_HOVER : Theme.GREEN_BG);
			c.fill(bx + 4, by + 9, bx + 7, by + 11, Theme.GREEN);
			c.fill(bx + 6, by + 10, bx + 8, by + 12, Theme.GREEN);
			for (int i = 0; i < 6; i++) {
				c.fill(bx + 8 + i, by + 10 - i, bx + 10 + i, by + 11 - i, Theme.GREEN);
			}
			Gfx.rrect(c, bx + 21, by, 18, 18, 6, h2 ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			drawX(c, bx + 26, by + 5, 7, h2 ? Theme.RED : Theme.DIM);
			if (h1) {
				tooltipText = "Accept";
			} else if (h2) {
				tooltipText = "Decline";
			}
			if (settled) {
				region(bx, by, 18, 18, () -> Social.answerRequest(r.req(), true));
				region(bx + 21, by, 18, 18, () -> Social.answerRequest(r.req(), false));
			}
			return;
		}
		if (r.type() == R_OUT) {
			Gfx.text(c, fit(u.display(), rw - 56), ix + 31, ry + 7, Theme.TEXT);
			Gfx.text(c, "Request sent", ix + 31, ry + 18, Theme.DIM);
			int bx = ix + rw - 24, by = ry + (rh - 18) / 2;
			boolean h2 = in(mx, my, bx, by, 18, 18);
			Gfx.rrect(c, bx, by, 18, 18, 6, h2 ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			drawX(c, bx + 5, by + 5, 7, h2 ? Theme.RED : Theme.DIM);
			if (h2) {
				tooltipText = "Cancel request";
			}
			if (settled) {
				region(bx, by, 18, 18, () -> Social.cancelRequest(r.req()));
			}
			return;
		}
		// friend
		Gfx.text(c, fit(removing ? "Remove " + u.display() + "?" : u.display(), rw - 48), ix + 31, ry + 7,
				removing ? Theme.RED : u.online() ? Theme.TEXT : 0xFFB0B4C0);
		boolean typingNow = !removing && dev.solclient.social.KuboLive.isTyping(u.display());
		String line = removing ? "right click again" : typingNow ? dev.solclient.Lang.tr("typing...", "a escrever...") : friendLine(u);
		Gfx.text(c, fit(line, rw - 48), ix + 31, ry + 18, removing ? 0xFFB06A6E : typingNow ? accent : u.onMyServer() ? accent : u.online() ? 0xFF7FD69A : Theme.DIM);
		Integer unread = Social.unreadByUser.get(u.id);
		if (unread != null && unread > 0 && !sel) {
			badge(c, ix + rw - 12, ry + rh / 2, unread);
		} else if (hov && !removing) {
			chatGlyph(c, ix + rw - 18, ry + rh / 2 - 5, Theme.DIM);
		}
		if (hov) {
			tooltipText = removing ? null : "Click: chat - right click: remove";
		}
		if (settled) {
			region(ix, ry, rw, rh, b -> {
				if (b == 1) {
					if (confirm("unf" + u.id, "Right-click again to remove " + u.display())) {
						Social.removeFriend(u);
					}
				} else {
					openChatWith(u.id);
				}
			}, null);
		}
		// mute (only on this client): always visible when muted, on hover otherwise
		boolean muted = dev.solclient.social.Mutes.isMuted(u);
		if (!removing && (muted || hov)) {
			int mxp = ix + rw - 34, myp = ry + rh / 2 - 6;
			boolean hm2 = in(mx, my, mxp - 2, myp - 2, 16, 16) && my >= ly0 && my < ly1;
			Gfx.tex(c, Theme.icon2("volume_off"), mxp, myp, 12, 12, 64, 64, muted ? Theme.RED : hm2 ? Theme.TEXT : Theme.DIM);
			if (hm2) {
				tooltipText = muted ? "Muted on this client - click to unmute" : "Mute (no pop-ups, only on this client)";
			}
			if (settled) {
				region(mxp - 2, myp - 2, 16, 16, () -> {
					dev.solclient.social.Mutes.toggle(u);
					boolean nowMuted = dev.solclient.social.Mutes.isMuted(u);
					Social.say(nowMuted ? dev.solclient.Lang.tr("Muted ", "Silenciaste ") + u.display() + dev.solclient.Lang.tr(" (click the icon again to unmute)", " (clica outra vez no ícone para tirar)")
							: dev.solclient.Lang.tr("Unmuted ", "Tiraste o silêncio a ") + u.display(), false);
				});
			}
		}
		// "Join" when your friend is playing on a server you are not on
		String srv = removing ? null : dev.solclient.social.ServerShare.serverOf(u);
		if (srv != null && !u.onMyServer()) {
			int jw = Gfx.tw("Join") + 12, jx = ix + rw - jw - 22, jy = ry + (rh - 14) / 2;
			boolean hj = in(mx, my, jx, jy, jw, 14) && my >= ly0 && my < ly1;
			Gfx.rrect(c, jx, jy, jw, 14, 7, hj ? Theme.accentHover() : accent);
			Gfx.textFit(c, "Join", jx + jw / 2, jw - 6, jy + 3, 0xFFFFFFFF);
			if (hj) {
				tooltipText = "Join " + srv;
			}
			if (settled) {
				region(jx, jy, jw, 14, () -> dev.solclient.social.ServerShare.join(srv));
			}
		}
	}

	/** Minecraft head of a nick (falls back to the site picture / initial), with an optional status dot. */
	private void head(DrawContext c, String mcName, Social.User u, int x, int y, int s, int dot) {
		// 1) their Minecraft skin by nick (works offline too), 2) the skin they uploaded to Plegumo, 3) by UUID
		RemoteTextures.Tex t = null;
		String nick = mcName != null && !mcName.isBlank() ? mcName : u != null ? u.display() : null;
		// 0) the exact skin they have on right now, when they are on the same server as you (or it is you)
		if (liveHead(c, nick, x, y, s)) {
			if (dot != 0) {
				Gfx.circle(c, x + s - 1, y + s - 1, 4, SUB);
				Gfx.circle(c, x + s - 1, y + s - 1, 2.6F, dot);
			}
			return;
		}
		// the skin they use in Caos first (cracked / offline names have no Mojang skin: mc-heads would give Steve)
		String own = Social.friendSkin(u);
		if (own != null) {
			t = RemoteTextures.get(own);
		}
		if (t == null && u != null && u.image != null && u.image.startsWith("http") && u.image.contains("skin")) {
			t = RemoteTextures.get(u.image);
		}
		if (t == null && nick != null) {
			t = dev.solclient.social.FaceSkins.of(nick, u == null ? null : u.mcUuid); // several mirrors + a copy on disk
		}
		if (t != null) {
			Gfx.rrect(c, x - 1, y - 1, s + 2, s + 2, 4, 0xFF0A0B0E);
			AccountsScreen.face(c, t, x, y, s);
		} else if (u != null) {
			avatar(c, u, x + s / 2, y + s / 2, s / 2, u.online());
		} else {
			Gfx.rrect(c, x, y, s, s, 4, Theme.OFF_BG_HOVER);
		}
		if (dot != 0) {
			// LabyMod-like status mark: online / away / busy / phone / offline
			StatusBadge.draw(c, u != null ? u.state() : StatusBadge.Status.ONLINE, x + s + 2, y + s + 2, Math.max(6, s / 3), SUB);
		}
	}

	private static boolean liveHead(DrawContext c, String nick, int x, int y, int s) {
		if (nick == null || nick.isBlank()) {
			return false;
		}
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player != null && nick.equalsIgnoreCase(mc.getSession().getUsername())) {
				Gfx.rrect(c, x - 1, y - 1, s + 2, s + 2, 4, 0xFF0A0B0E);
				net.minecraft.client.gui.PlayerSkinDrawer.draw(c, mc.player.getSkin(), x, y, s);
				return true;
			}
			var nh = mc.getNetworkHandler();
			var e = nh == null ? null : nh.getPlayerListEntry(nick);
			if (e != null) {
				Gfx.rrect(c, x - 1, y - 1, s + 2, s + 2, 4, 0xFF0A0B0E);
				net.minecraft.client.gui.PlayerSkinDrawer.draw(c, e.getSkinTextures(), x, y, s);
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	private void openChatWith(String id) {
		chatId = id;
		chatScroll = 0;
		chatInput.clear();
		focus = F_CHAT;
		Social.openChat(id);
	}

	private void closeChat() {
		chatId = null;
		membersOpen = false;
		bubblePicker = false;
		logoPicker = false;
		hoverMsg = null;
		menuMsg = null;
		editing = null;
		picker = 0;
		Social.closeChat();
	}

	// ------------------------------------------------------------ chat panel
	// Bubbles grouped by sender (head on the left for your friend), times under each group, day separators
	// ("Today", "Yesterday"...), "Sending..." / "Not sent - click to retry", right click copies a message,
	// a "New messages" pill when you are scrolled up, and a send button.
	private int lastChatCount;
	private String lastChatId;
	private boolean newBelow;

	private void sendChat() {
		String msg = chatInput.text().trim();
		if (msg.isEmpty() || chatId == null) {
			return;
		}
		if (editing != null) {
			Social.editMessage(chatId, editing, msg);
			editing = null;
			chatInput.clear();
			return;
		}
		if (dev.solclient.chat.Gifs.isGifPage(msg)) {
			// a GIPHY / Tenor page link: send the GIF itself
			String target = chatId;
			chatInput.clear();
			chatScroll = 0;
			dev.solclient.chat.Gifs.resolvePage(msg, direct -> Social.sendMessage(target, direct != null ? direct : msg));
			return;
		}
		Social.sendMessage(chatId, msg);
		chatInput.clear();
		chatScroll = 0;
	}

	private void sendLocation() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || chatId == null) {
			return;
		}
		String server = mc.isInSingleplayer() ? "singleplayer" : mc.getCurrentServerEntry() == null ? "" : mc.getCurrentServerEntry().address;
		String name = mc.getSession().getUsername() + (server.isEmpty() ? "" : " @ " + server);
		Social.sendMessage(chatId, "[kubo-wp] " + name.replace("|", "") + "|" + mc.player.getBlockX() + "|" + mc.player.getBlockY() + "|" + mc.player.getBlockZ() + "|"
				+ mc.world.getRegistryKey().getValue() + "|" + server);
	}

	private static java.time.ZonedDateTime msgTime(Social.Msg m) {
		try {
			if (m.createdAt != null) {
				return java.time.Instant.parse(m.createdAt).atZone(java.time.ZoneId.systemDefault());
			}
		} catch (RuntimeException ignored) {
		}
		return null;
	}

	private static String dayLabel(java.time.LocalDate d) {
		java.time.LocalDate today = java.time.LocalDate.now();
		if (d.equals(today)) {
			return "Today";
		}
		if (d.equals(today.minusDays(1))) {
			return "Yesterday";
		}
		return d.format(java.time.format.DateTimeFormatter.ofPattern(d.getYear() == today.getYear() ? "d MMM" : "d MMM yyyy", Locale.ENGLISH));
	}

	// chat extras: message menu (react / edit / delete / copy), emoji + GIF pickers, editing, group members
	private Social.Msg menuMsg;
	/** Discord-like quick reaction bar over the message under the mouse. */
	private Social.Msg hoverMsg;
	private int hoverBarX, hoverBarY;
	private long hoverMsgAt;
	private static final String[] QUICK_REACTIONS = {"\u2705", "\u2764\uFE0F", "\uD83D\uDD25"};
	private static final int HOVER_BAR_W = 4 * 16 + 6;
	/** When each message was first drawn: new ones slide in. */
	private final java.util.Map<String, Long> msgSeen = new java.util.HashMap<>();
	/** When your last "sending..." message appeared: its real copy from the site keeps the same animation. */
	private long lastPendingSeen;

	private long seenAt(Social.Msg m, boolean opening) {
		String key = m.id > 0 && m.id != Long.MAX_VALUE ? "id:" + m.id : "p:" + System.identityHashCode(m);
		Long v = msgSeen.get(key);
		if (v == null) {
			long now = System.currentTimeMillis();
			v = opening ? 0L : m.mine && m.id > 0 && now - lastPendingSeen < 3000L ? lastPendingSeen : now;
			if (m.pending) {
				lastPendingSeen = v == 0L ? now : v;
			}
			if (msgSeen.size() > 600) {
				msgSeen.clear();
			}
			msgSeen.put(key, v);
		}
		return v;
	}
	/** True during the first frame of an open chat: the messages that are already there do not slide in. */
	private boolean chatOpenedAnim = true;
	private String animChatId;
	private int menuX, menuY;
	private Social.Msg editing;
	private int picker; // 0 none, 1 emoji, 2 GIF, 3 emoji for a reaction
	private Social.Msg reactTarget;
	private float pickerScroll;
	private int pickerMax, pkx, pky, pkw, pkh;
	private final TextBox gifSearch = new TextBox(60);
	private String gifAsked;
	private long gifTyped;
	private boolean membersOpen;
	/** The chat's bubble style picker (button in the chat header). */
	private boolean bubblePicker;
	/** The group logo picker (the owner clicks the logo in the chat header). */
	private boolean logoPicker;
	private long pickerOpenedAt;

	private static final int BUBBLE_OTHER = 0xFF262833;

	/** Every friend gets their own soft name colour in chats (always the same one for the same name). */
	private static final int[] NAME_COLOURS = {0xFF7DD3FC, 0xFFA78BFA, 0xFFF9A8D4, 0xFF86EFAC, 0xFFFCD34D, 0xFFFDBA74, 0xFF5EEAD4, 0xFFC4B5FD,
			0xFFF0ABFC, 0xFF93C5FD};

	private static int nameColour(String name) {
		return NAME_COLOURS[Math.floorMod(name == null ? 0 : name.toLowerCase(Locale.ROOT).hashCode(), NAME_COLOURS.length)];
	}

	/** Cuts a name (never translated) to fit a width. */
	private static String fitRaw(String s, int w) {
		if (s == null) {
			return "";
		}
		if (Gfx.twRaw(s) <= w) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "...") > w) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	private static List<String> wrapRich(String s, int maxW, int maxLines) {
		List<String> out = new ArrayList<>();
		for (String para : s.split("\n")) {
			String cur = "";
			for (String word : para.split(" ")) {
				String t = cur.isEmpty() ? word : cur + " " + word;
				if (cur.isEmpty() || dev.solclient.chat.Emoji.width(t) <= maxW) {
					cur = t;
				} else {
					out.add(cur);
					cur = word;
				}
				// very long words: cut
				while (dev.solclient.chat.Emoji.width(cur) > maxW && cur.length() > 1) {
					int cut = cur.length() - 1;
					while (cut > 1 && dev.solclient.chat.Emoji.width(cur.substring(0, cut)) > maxW) {
						cut--;
					}
					out.add(cur.substring(0, cut));
					cur = cur.substring(cut);
				}
				if (out.size() >= maxLines) {
					return out;
				}
			}
			out.add(cur);
		}
		while (out.size() > maxLines) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	/** Grid of group logos (gradient + symbol); the first one is the members' heads. Only the owner opens it. */
	private void drawLogoPicker(DrawContext c, int mx, int my, Social.Group g, int ix, int iw, int y0, int y1, boolean settled, int accent) {
		Gfx.text(c, dev.solclient.Lang.tr("Group logo", "Logótipo do grupo"), ix + 2, y0, 0xFFFFFFFF);
		Gfx.text(c, fit(dev.solclient.Lang.tr("Everyone in the group sees it.", "Toda a gente no grupo o vê."), iw - 4), ix + 2, y0 + 11, Theme.DIM);
		var all = dev.solclient.chat.GroupIcons.ALL;
		int cols = 4, gap = 6, cw = (iw - gap * (cols - 1)) / cols, ch = 44, top = y0 + 30;
		for (int i = 0; i < all.size(); i++) {
			var p = all.get(i);
			int cx0 = ix + (i % cols) * (cw + gap), cy0 = top + (i / cols) * (ch + gap);
			if (cy0 + ch > y1) {
				break;
			}
			float pop = Math.min(1F, Math.max(0F, (System.currentTimeMillis() - pickerOpenedAt - i * 20L) / 180F));
			if (pop <= 0F) {
				continue;
			}
			cy0 += Math.round((1F - pop) * 8F);
			boolean sel = p.id().equals(g.icon == null ? "" : g.icon);
			boolean hov = in(mx, my, cx0, cy0, cw, ch);
			Gfx.rrect(c, cx0, cy0, cw, ch, 6, sel ? Gfx.withAlpha(accent & 0xFFFFFF, 0x40) : hov ? 0x22FFFFFF : 0x10FFFFFF);
			if (sel) {
				Gfx.rrectBorder(c, cx0, cy0, cw, ch, 6, 0, accent);
			}
			int ls = hov ? 24 : 22, lx = cx0 + cw / 2 - ls / 2, ly = cy0 + 5 - (ls - 22) / 2;
			if (p.id().isEmpty()) {
				String keep = g.icon;
				g.icon = "";
				groupHeads(c, g, lx, ly, ls);
				g.icon = keep;
			} else {
				dev.solclient.chat.GroupIcons.draw(c, p, lx, ly, ls);
			}
			Gfx.textFit(c, p.name(), cx0 + cw / 2, cw - 4, cy0 + ch - 11, sel ? 0xFFFFFFFF : Theme.DIM);
			if (settled) {
				final String id = p.id();
				region(cx0, cy0, cw, ch, () -> {
					Social.setGroupIcon(g, id);
					logoPicker = false;
				});
			}
		}
	}

	/** Grid of bubble styles with a preview each (like phone chat apps): click one to use it for the messages you send next. */
	private void drawBubblePicker(DrawContext c, int mx, int my, int ix, int iw, int y0, int y1, boolean settled, int accent) {
		Gfx.text(c, dev.solclient.Lang.tr("Bubble style", "Estilo de balão"), ix + 2, y0, 0xFFFFFFFF);
		Gfx.text(c, fit(dev.solclient.Lang.tr("For every chat. Only the messages you send after choosing.", "Para todas as conversas. Só as mensagens que enviares depois."), iw - 4),
				ix + 2, y0 + 11, Theme.DIM);
		String mine = dev.solclient.chat.Bubbles.mine();
		int cols = 3, gap = 6, cw = (iw - gap * (cols - 1)) / cols, ch = 76;
		int top = y0 + 30;
		var all = dev.solclient.chat.Bubbles.ALL;
		for (int i = 0; i < all.size(); i++) {
			var st = all.get(i);
			int cx0 = ix + (i % cols) * (cw + gap), cy0 = top + (i / cols) * (ch + gap);
			if (cy0 + ch > y1) {
				break;
			}
			boolean sel = st.id().equals(mine);
			boolean hov = in(mx, my, cx0, cy0, cw, ch);
			float pop = Math.min(1F, Math.max(0F, (System.currentTimeMillis() - pickerOpenedAt - i * 25L) / 180F));
			if (pop <= 0F) {
				continue;
			}
			cy0 += Math.round((1F - pop) * 8F);
			Gfx.rrect(c, cx0, cy0, cw, ch, 6, sel ? Gfx.withAlpha(accent & 0xFFFFFF, 0x40) : hov ? 0x22FFFFFF : 0x10FFFFFF);
			if (sel) {
				Gfx.rrectBorder(c, cx0, cy0, cw, ch, 6, 0, accent);
			}
			// preview bubble
			int[] ex = dev.solclient.chat.Bubbles.extents(st, 26);
			int pbh = 26, pbx = cx0 + 6 + ex[0], pbw = cw - 12 - ex[0] - ex[2], pby = cy0 + 4 + Math.max(4, ex[1]);
			dev.solclient.chat.Bubbles.draw(c, st, pbx, pby, pbw, pbh, accent, false);
			boolean styled = !st.plain();
			Gfx.text(c, fit("Hey!", pbw - 8), pbx + 6, pby + 9, styled ? st.text() : 0xFFFFFFFF);
			Gfx.textFit(c, st.name(), cx0 + cw / 2, cw - 6, cy0 + ch - 11, sel ? 0xFFFFFFFF : Theme.DIM);
			if (settled) {
				final String id = st.id();
				region(cx0, cy0, cw, ch, () -> {
					dev.solclient.chat.Bubbles.setMine(id);
					bubblePicker = false;
				});
			}
		}
	}

	private void drawChat(DrawContext c, int mx, int my, int x, int right, int y, int h, int accent) {
		Gfx.rrectBorder(c, x, y, Math.max(2, right - x), h, 12, SUB, Theme.PANEL_BORDER);
		cbx = x;
		cby = y;
		cbw = Math.max(0, right - x);
		cbh = h;
		Social.User u = Social.friend(chatId);
		Social.Group g = Social.group(chatId);
		if (u == null && g == null) {
			return;
		}
		boolean settled = aC >= 0.99F;
		chatOpenedAnim = !java.util.Objects.equals(animChatId, chatId);
		animChatId = chatId;
		int px = right - CW;
		c.enableScissor(x, y, right, y + h);
		int ix = px + 8, iw = CW - 16;
		String name = g != null ? (g.name == null ? "Group" : g.name) : u.display();

		// ---------------- header: back, head(s) + name + status, members / invite, close
		int hbx = ix, hby = y + 9;
		boolean hb = in(mx, my, hbx, hby, 18, 18);
		Gfx.button(c, hbx, hby, 18, 18, hb, false);
		chevronLeft(c, hbx + 9, hby + 8, hb ? 0xFFFFFFFF : Theme.TEXT);
		if (hb) {
			tooltipText = membersOpen ? "Back to chat" : "Back to friends";
		}
		if (settled) {
			region(hbx, hby, 18, 18, () -> {
				if (membersOpen) {
					membersOpen = false;
				} else {
					closeChat();
				}
			});
		}
		if (g != null) {
			groupHeads(c, g, ix + 24, y + 7, 22);
			if (g.owner()) {
				boolean hl = in(mx, my, ix + 24, y + 7, 22, 22);
				if (hl) {
					Gfx.rrectBorder(c, ix + 23, y + 6, 24, 24, 6, 0, accent);
					tooltipText = dev.solclient.Lang.tr("Change the group logo", "Mudar o logótipo do grupo");
				}
				if (settled) {
					region(ix + 24, y + 7, 22, 22, () -> {
						logoPicker = !logoPicker;
						bubblePicker = false;
						membersOpen = false;
						pickerOpenedAt = System.currentTimeMillis();
					});
				}
			}
		} else {
			int dot = u.onMyServer() ? accent : u.online() ? 0xFF4ADE80 : 0xFF5A5E6E;
			head(c, u.mcUsername, u, ix + 24, y + 8, 20, dot);
		}
		int cxb = px + CW - 26, cyb = y + 8;
		boolean canInvite = g == null && (MinecraftClient.getInstance().getCurrentServerEntry() != null || dev.solclient.social.HostWorld.hosting());
		int nameW = iw - 80 - (canInvite || g != null ? 24 : 0) - 24;
		{
			int bbx = cxb - 24 - (canInvite || g != null ? 24 : 0);
			boolean hbb = in(mx, my, bbx, cyb, 20, 20);
			Gfx.button(c, bbx, cyb, 20, 20, hbb, bubblePicker);
			// a tiny bubble in your style as the icon
			dev.solclient.chat.Bubbles.Style ms = dev.solclient.chat.Bubbles.of(dev.solclient.chat.Bubbles.mine());
			int ib = ms.plain() ? accent : ms.bg();
			Gfx.rrect(c, bbx + 4, cyb + 6, 12, 8, 3, ib);
			c.fill(bbx + 5, cyb + 13, bbx + 8, cyb + 15, ib);
			if (hbb) {
				tooltipText = dev.solclient.Lang.tr("Bubble style", "Estilo de balão");
			}
			if (settled) {
				region(bbx, cyb, 20, 20, () -> {
					bubblePicker = !bubblePicker;
					logoPicker = false;
					membersOpen = false;
					pickerOpenedAt = System.currentTimeMillis();
				});
			}
		}
		Gfx.textRaw(c, fitRaw(name, nameW), ix + 50, y + 9, 0xFFFFFFFF);
		if (g != null) {
			Gfx.text(c, g.members.size() + " members", ix + 50, y + 20, Theme.DIM);
		} else {
			Gfx.text(c, fit(friendLine(u), nameW), ix + 50, y + 20, u.onMyServer() ? accent : u.online() ? 0xFF7FD69A : Theme.DIM);
		}
		if (g != null) {
			int mbx = cxb - 24;
			boolean hmb = in(mx, my, mbx, cyb, 20, 20);
			Gfx.button(c, mbx, cyb, 20, 20, hmb, membersOpen);
			Gfx.tex(c, Theme.icon2("players"), mbx + 3, cyb + 3, 14, 14, 64, 64, membersOpen ? Gfx.BTN_TEXT_ON : Theme.TEXT);
			if (hmb) {
				tooltipText = "Members";
			}
			if (settled) {
				region(mbx, cyb, 20, 20, () -> membersOpen = !membersOpen);
			}
		} else if (canInvite) {
			int ivx = cxb - 24;
			boolean hi = in(mx, my, ivx, cyb, 20, 20);
			Gfx.button(c, ivx, cyb, 20, 20, hi, false);
			Gfx.tex(c, Theme.icon2("players"), ivx + 3, cyb + 3, 14, 14, 64, 64, Theme.TEXT);
			if (hi) {
				tooltipText = "Invite to your server";
			}
			final Social.User fu = u;
			if (settled) {
				region(ivx, cyb, 20, 20, () -> {
					if (Social.sendInvite(fu.id)) {
						Social.say("Invite sent to " + fu.display(), false);
					}
				});
			}
		}
		boolean hx = in(mx, my, cxb, cyb, 20, 20);
		Gfx.button(c, cxb, cyb, 20, 20, hx, false);
		drawX(c, cxb + 6, cyb + 6, 8, hx ? Theme.RED : Theme.TEXT);
		if (hx) {
			tooltipText = "Close chat";
		}
		if (settled) {
			region(cxb, cyb, 20, 20, this::closeChat);
		}
		Gfx.rrectF(c, ix, y + 36, iw, 1, 0.5F, Theme.PANEL_BORDER);

		if (g != null && membersOpen) {
			drawGroupMembers(c, mx, my, g, ix, iw, y + 44, y + h - 8, settled, accent);
			c.disableScissor();
			return;
		}
		if (bubblePicker) {
			drawBubblePicker(c, mx, my, ix, iw, y + 44, y + h - 8, settled, accent);
			c.disableScissor();
			return;
		}
		if (logoPicker && g != null && g.owner()) {
			drawLogoPicker(c, mx, my, g, ix, iw, y + 44, y + h - 8, settled, accent);
			c.disableScissor();
			return;
		}

		// ---------------- input bar (bottom): GIF, emoji, text, send
		int iy = y + h - 32, ib = 22;
		int fieldX = ix + 3 * (ib + 3), sendX = ix + iw - ib, fieldW = sendX - 3 - fieldX;
		int pickerH = picker == 2 ? 178 : picker != 0 ? 128 : 0;
		int editH = editing != null ? 14 : 0;
		int my0 = y + 42, my1 = iy - 6 - pickerH - editH;

		// ---------------- messages (newest at the bottom)
		List<Social.Msg> msgs = Social.messages(chatId);
		if (!chatId.equals(lastChatId)) {
			lastChatId = chatId;
			lastChatCount = msgs.size();
			newBelow = false;
			menuMsg = null;
			editing = null;
			picker = 0;
		} else if (msgs.size() > lastChatCount) {
			if (chatScroll > 4) {
				newBelow = true;
			}
			lastChatCount = msgs.size();
		}
		if (chatScroll <= 4) {
			newBelow = false;
		}
		java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
		if (msgs.isEmpty()) {
			if (g != null) {
				groupHeads(c, g, px + CW / 2 - 16, my0 + 18, 32);
			} else {
				head(c, u.mcUsername, u, px + CW / 2 - 16, my0 + 18, 32, 0);
			}
			Gfx.textFit(c, "This is the start of your chat", px + CW / 2, CW - 6, my0 + 58, Theme.TEXT);
			Gfx.textFit(c, g != null ? "in " + fit(name, iw - 40) : "with " + fit(name, iw - 40), px + CW / 2, CW - 6, my0 + 70, Theme.DIM);
		} else {
			int oc0 = clipY0, oc1 = clipY1;
			clipY0 = my0;
			clipY1 = my1;
			c.enableScissor(px, my0, right, my1);
			// a little room under the newest message so its bottom edge / time is never cut off
			int by = my1 - 6 + Math.round(chatScroll);
			int avatar = 18;
			// heads on both sides (yours on the right), so bubbles leave room for two
			int maxBw = iw - 2 * (avatar + 6) - 4;
			String myNick = MinecraftClient.getInstance().getSession().getUsername();
			// the reaction bar stays while the mouse is on it (moving from the message up to the bar)
			Social.Msg barMsg = hoverMsg != null && menuMsg == null && in(mx, my, hoverBarX, hoverBarY, HOVER_BAR_W, 16) ? hoverMsg : null;
			int rbarX = hoverBarX, rbarY = hoverBarY;
			for (int n = msgs.size() - 1; n >= 0; n--) {
				Social.Msg m = msgs.get(n);
				Social.Msg next = n + 1 < msgs.size() ? msgs.get(n + 1) : null;
				Social.Msg prev = n > 0 ? msgs.get(n - 1) : null;
				java.time.ZonedDateTime t = msgTime(m);
				java.time.ZonedDateTime tn = next == null ? null : msgTime(next);
				java.time.ZonedDateTime tp = prev == null ? null : msgTime(prev);
				boolean sameAsNext = next != null && next.mine == m.mine && java.util.Objects.equals(next.senderId, m.senderId) && t != null && tn != null
						&& java.time.Duration.between(t, tn).toMinutes() < 5;
				boolean sameAsPrev = prev != null && prev.mine == m.mine && java.util.Objects.equals(prev.senderId, m.senderId) && t != null && tp != null
						&& java.time.Duration.between(tp, t).toMinutes() < 5;
				boolean groupEnd = !sameAsNext, groupStart = !sameAsPrev;
				Social.User sender = g != null ? g.member(m.senderId) : u;

				boolean invite = "invite".equals(m.kind) && m.address != null;
				String[] wpData = !invite && m.body != null && m.body.startsWith("[kubo-wp] ") ? m.body.substring(10).split("\\|") : null;
				boolean wp = wpData != null && wpData.length >= 5;
				boolean gif = !invite && !wp && dev.solclient.chat.Gifs.isGif(m.body);
				boolean big = !invite && !wp && !gif && dev.solclient.chat.Emoji.onlyEmoji(m.body);
				// the name + time now sit above each group, next to the head (like LabyMod / Discord)
				String senderName = null;
				String headerName = m.mine ? (Social.me != null ? Social.me.display() : myNick) : sender != null ? sender.display() : "?";
				int headerH = groupStart ? 13 : 0;
				String time = (m.editedAt != null ? "edited  " : "") + (t == null ? "" : t.format(hm));
				int timeW = Gfx.tw(time);

				// ---- size of the bubble
				List<String> lines = List.of();
				int contentW, contentH;
				dev.solclient.chat.Gifs.Anim anim = null;
				if (gif) {
					anim = dev.solclient.chat.Gifs.get(m.body.trim());
					contentW = Math.min(maxBw - 8, 120);
					contentH = anim == null ? 70 : Math.max(20, Math.min(160, Math.round(contentW * anim.h() / (float) anim.w())));
				} else if (wp) {
					contentW = Math.min(maxBw - 12, Math.max(120, Gfx.twRaw(wpData[0]) + 16));
					contentH = 52;
				} else if (invite) {
					contentW = Math.min(maxBw - 12, 210);
					contentH = 70;
				} else if (big) {
					contentW = dev.solclient.chat.Emoji.width(m.body.strip()) * 2;
					contentH = 20;
				} else {
					lines = wrapRich(m.body == null ? "" : m.body, maxBw - 14, 14);
					contentW = 0;
					for (String l : lines) {
						contentW = Math.max(contentW, dev.solclient.chat.Emoji.width(l));
					}
					contentH = lines.size() * 11 - 2;
				}
				boolean timeInline = !gif && !invite && !big && !lines.isEmpty()
						&& dev.solclient.chat.Emoji.width(lines.get(lines.size() - 1)) + timeW + 8 <= maxBw - 14;
				int bw, bhh;
				if (big) {
					bw = Math.max(contentW, timeW) + 4;
					bhh = contentH + 10;
				} else {
					bw = Math.max(contentW + 14, (senderName != null ? Gfx.tw(senderName) : 0) + 14);
					if (timeInline) {
						int lastW = dev.solclient.chat.Emoji.width(lines.get(lines.size() - 1));
						bw = Math.max(bw, Math.min(maxBw, lastW + timeW + 22));
					} else {
						bw = Math.max(bw, timeW + 14);
					}
					bw = Math.min(maxBw, bw);
					bhh = 6 + (senderName != null ? 11 : 0) + contentH + (timeInline ? 6 : 13) + (gif || invite || wp ? 2 : 0);
				}
				// reactions row below the bubble
				int reactH = m.reactions.isEmpty() ? 0 : 15;
				String status = m.failed ? "Not sent - click to retry" : m.pending ? "Sending..." : null;
				int statusH = status != null ? 11 : 0;

				by -= statusH + reactH + bhh;
				boolean visible = !(by - headerH > my1 || by + bhh + reactH + statusH < my0);
				int bx = m.mine ? ix + iw - avatar - 6 - bw : ix + avatar + 6;
				if (!big && !m.failed) {
					// the picture of a styled bubble goes a bit past the text on the head's side: keep it off the head
					int side = dev.solclient.chat.Bubbles.extents(dev.solclient.chat.Bubbles.of(m.bubble), bhh)[2];
					bx += m.mine ? -Math.max(0, side - 4) : Math.max(0, side - 4);
				}
				if (visible) {
					// new messages slide in from their side and settle (first 260 ms after they appear)
					long seenAt = seenAt(m, chatOpenedAnim);
					float ak = seenAt == 0L ? 1F : Math.min(1F, (System.currentTimeMillis() - seenAt) / 260F);
					float ae = 1F - (1F - ak) * (1F - ak) * (1F - ak);
					boolean animating = ae < 1F;
					if (animating) {
						c.getMatrices().pushMatrix();
						c.getMatrices().translate((m.mine ? 14F : -14F) * (1F - ae), 6F * (1F - ae));
					}
					try {
					int bg = m.failed ? 0xFF4A1C1F : m.mine ? (m.pending ? Gfx.blend(accent, SUB, 0.45F) : accent) : BUBBLE_OTHER;
					dev.solclient.chat.Bubbles.Style bst = dev.solclient.chat.Bubbles.of(m.failed ? "" : m.bubble);
					boolean styled = !bst.plain();
					int textCol = styled ? bst.text() : m.mine ? 0xFFFFFFFF : 0xFFE6E8EE;
					int subCol = styled ? bst.sub() : m.mine ? 0xCCFFFFFF : 0xFF7D8190;
					if (!big && styled) {
						dev.solclient.chat.Bubbles.draw(c, bst, bx, by, bw, bhh, bg, m.mine);
					} else if (!big) {
						Gfx.rrect(c, bx, by, bw, bhh, 6, bg);
						// soft top highlight + a square "tail" corner on the side of the head (first bubble of a group)
						c.fill(bx + 5, by, bx + bw - 5, by + 1, m.mine ? 0x33FFFFFF : 0x14FFFFFF);
						if (groupStart) {
							c.fill(m.mine ? bx + bw - 6 : bx, by, m.mine ? bx + bw : bx + 6, by + 6, bg);
						}
					}
					if (groupStart) {
						// head of whoever sent it (yours too) with a status ring, and their name + time above the bubbles
						int hx2 = m.mine ? ix + iw - avatar : ix, hy2 = by - headerH;
						Social.User hu = m.mine ? Social.me : sender;
						// status like Discord: a small dot on the corner of the head (no ring around it)
						int dot = m.mine ? 0xFF4ADE80 : hu != null && hu.onMyServer() ? accent : hu != null && hu.online() ? 0xFF4ADE80 : 0xFF5A5E6E;
						head(c, m.mine ? myNick : sender != null ? sender.mcUsername : null, hu, hx2, hy2, avatar, dot);
						int nameCol = m.mine ? Gfx.blend(accent, 0xFFFFFFFF, 0.35F) : nameColour(headerName);
						String hn = fitRaw(headerName, maxBw - 40);
							int hnw = Gfx.twRaw(hn);
						if (m.mine) {
							int nx = ix + iw - avatar - 6 - hnw;
							Gfx.textRaw(c, hn, nx, hy2 + 1, nameCol);
							} else {
							int nx = ix + avatar + 7;
							Gfx.textRaw(c, hn, nx, hy2 + 1, nameCol);
							}
					}
					int cy = by + 5;
					if (senderName != null) {
						Gfx.text(c, fit(senderName, bw - 12), bx + 7, cy, 0xFF9AA0B4);
						cy += 11;
					}
					if (gif) {
						int gx = bx + 4, gy = cy - 1;
						if (anim != null) {
							dev.solclient.chat.Gifs.draw(c, anim, gx, gy, contentW, contentH);
						} else {
							Gfx.rrect(c, gx, gy, contentW, contentH, 4, 0x33000000);
							boolean bad = dev.solclient.chat.Gifs.failed(m.body);
							Gfx.textFit(c, bad ? "GIF unavailable" : "Loading GIF...", gx + contentW / 2, contentW - 6, gy + contentH / 2 - 4, subCol);
						}
						cy = gy + contentH + 2;
					} else if (wp) {
						// shared waypoint: name, coordinates, "Add waypoint"
						Gfx.rrect(c, bx + 5, cy, bw - 10, contentH, 5, m.mine ? 0x33000000 : 0xFF1C1E26);
						Gfx.text(c, "WAYPOINT", bx + 11, cy + 5, subCol);
						Gfx.textRaw(c, fitRaw(wpData[0], bw - 24), bx + 11, cy + 16, textCol);
						String dimName = wpData[4].contains("nether") ? " · Nether" : wpData[4].contains("end") ? " · End" : "";
						Gfx.textRaw(c, wpData[1] + " " + wpData[2] + " " + wpData[3] + dimName, bx + 11, cy + 26, subCol);
						int cbw2 = bw - 22, cby2 = cy + contentH - 15;
						boolean hc = in(mx, my, bx + 11, cby2, cbw2, 12) && my >= my0 && my < my1;
						Gfx.rrect(c, bx + 11, cby2, cbw2, 12, 4, hc ? 0x55FFFFFF : 0x33FFFFFF);
						Gfx.textFit(c, "Add waypoint", bx + 11 + cbw2 / 2, cbw2 - 6, cby2 + 2, 0xFFFFFFFF);
						final String[] fw = wpData;
						if (settled) {
							region(bx + 11, cby2, cbw2, 12, () -> {
								if (dev.solclient.mods.WaypointsMod.instance != null && dev.solclient.mods.WaypointsMod.instance.addShared(fw)) {
									Social.say("Waypoint added: " + fw[0], false);
								} else {
									Social.say("Join that world to add it", true);
								}
							});
						}
						cy += contentH + 2;
					} else if (invite) {
						cy = inviteCard(c, mx, my, m, bx + 5, cy, bw - 10, contentH, my0, my1, settled, subCol);
					} else if (big) {
						c.getMatrices().pushMatrix();
						c.getMatrices().translate(m.mine ? bx + bw - contentW : bx, by);
						c.getMatrices().scale(2F, 2F);
						dev.solclient.chat.Emoji.draw(c, m.body.strip(), 0, 0, 0xFFFFFFFF);
						c.getMatrices().popMatrix();
						cy = by + contentH;
					} else {
						for (int li = 0; li < lines.size(); li++) {
							dev.solclient.chat.Emoji.draw(c, lines.get(li), bx + 7, cy + li * 11, textCol);
						}
						cy += contentH + (timeInline ? -9 : 2);
					}
					if (!time.isEmpty()) {
						int tx = big ? (m.mine ? bx + bw - timeW : bx) : bx + bw - timeW - 6;
						int tyy = big ? by + bhh - 9 : by + bhh - 10;
						if (gif && anim != null) {
							Gfx.rrect(c, tx - 3, tyy - 2, timeW + 6, 11, 4, 0x88000000); // readable over the picture
						}
						Gfx.text(c, time, tx, tyy, gif && anim != null ? 0xEEFFFFFF : subCol);
					}
					// reactions
					if (reactH > 0) {
						int rx = m.mine ? bx + bw : bx, ry2 = by + bhh + 2;
						List<int[]> spots = new ArrayList<>();
						int totalW = 0;
						for (Social.Reaction r : m.reactions) {
							int w = dev.solclient.chat.Emoji.width(r.emoji) + Gfx.tw(String.valueOf(r.count)) + 12;
							spots.add(new int[]{w});
							totalW += w + 3;
						}
						int cx0 = m.mine ? rx - totalW + 3 : rx;
						int k = 0;
						for (Social.Reaction r : m.reactions) {
							int w = spots.get(k++)[0];
							boolean hr = in(mx, my, cx0, ry2, w, 12) && my >= my0 && my < my1;
							Gfx.rrect(c, cx0, ry2, w, 12, 6, r.mine ? Gfx.withAlpha(accent, 0x66) : hr ? 0xFF2E3140 : 0xFF22242E);
							if (r.mine) {
								Gfx.rrect(c, cx0, ry2, w, 12, 6, Gfx.withAlpha(accent, 0x66));
							}
							dev.solclient.chat.Emoji.draw(c, r.emoji, cx0 + 4, ry2 + 2, 0xFFFFFFFF);
							Gfx.text(c, String.valueOf(r.count), cx0 + 6 + dev.solclient.chat.Emoji.width(r.emoji), ry2 + 2, 0xFFE6E8EE);
							final Social.Msg fm = m;
							final String em = r.emoji;
							if (settled) {
								region(cx0, ry2, w, 12, () -> Social.react(chatId, fm, em));
							}
							cx0 += w + 3;
						}
					}
					if (status != null) {
						int sx2 = m.mine ? ix + iw - Gfx.tw(status) - 4 : bx;
						Gfx.text(c, status, sx2, by + bhh + reactH + 1, m.failed ? 0xFFFF8A8A : 0xFF5A5E6E);
					}
					boolean hm2 = in(mx, my, bx, by, bw, bhh) && my >= my0 && my < my1;
					if (hm2 && menuMsg == null) {
						tooltipText = m.failed ? "Click to send again" : "Click for options";
					}
					if (settled) {
						final Social.Msg fm = m;
						if (barMsg == null && hm2 && !fm.pending && !fm.failed && fm.id > 0) {
							barMsg = fm;
							rbarX = m.mine ? Math.max(ix, bx - 2) : Math.min(ix + iw - HOVER_BAR_W, bx + bw - HOVER_BAR_W + 2);
							rbarY = by - 11;
						}
						region(bx, by, bw, bhh, b -> {
							if (b == 1) {
								MinecraftClient.getInstance().keyboard.setClipboard(fm.body == null ? "" : fm.body);
								Social.say("Message copied", false);
							} else if (fm.failed) {
								Social.retryMessage(chatId, fm);
							} else {
								menuMsg = fm;
								menuX = mx;
								menuY = my;
							}
						}, null);
					}
									} finally {
						if (animating) {
							c.getMatrices().popMatrix();
						}
					}
				}
				boolean mascotRoom = !big && !m.failed && !dev.solclient.chat.Bubbles.of(m.bubble).plain();
				int picTop = mascotRoom ? dev.solclient.chat.Bubbles.extents(dev.solclient.chat.Bubbles.of(m.bubble), bhh)[1] : 0;
				by -= headerH + (groupStart ? 8 : 2) + (mascotRoom ? Math.max(0, picTop - (groupStart ? 4 : 0)) : 0);
				// day separator above the first message of a day
				if (t != null && (tp == null || !tp.toLocalDate().equals(t.toLocalDate()))) {
					by -= 18;
					if (by < my1 && by + 18 > my0) {
						String d = dayLabel(t.toLocalDate());
						int dw = Gfx.tw(d) + 14;
						Gfx.rrect(c, px + CW / 2 - dw / 2, by + 3, dw, 13, 4, 0xFF1C1E26);
						Gfx.textFit(c, d, px + CW / 2, CW - 6, by + 6, Theme.DIM);
					}
				}
			}
			if (barMsg != hoverMsg) {
				hoverMsgAt = System.currentTimeMillis();
			}
			hoverMsg = barMsg;
			hoverBarX = rbarX;
			hoverBarY = rbarY;
			if (barMsg != null && settled) {
				float k = Math.min(1F, (System.currentTimeMillis() - hoverMsgAt) / 120F);
				int yy = rbarY + Math.round((1F - k) * 4F);
				Gfx.rrect(c, rbarX - 1, yy - 1, HOVER_BAR_W + 2, 18, 6, 0xFF0E1015);
				Gfx.rrect(c, rbarX, yy, HOVER_BAR_W, 16, 5, 0xFF262935);
				final Social.Msg fm = barMsg;
				for (int qi = 0; qi < QUICK_REACTIONS.length + 1; qi++) {
					int qx = rbarX + 3 + qi * 16;
					boolean hq = in(mx, my, qx, yy + 1, 15, 14);
					if (hq) {
						Gfx.rrect(c, qx, yy + 1, 15, 14, 4, 0xFF353949);
					}
					if (qi < QUICK_REACTIONS.length) {
						String em = QUICK_REACTIONS[qi];
						c.getMatrices().pushMatrix();
						float sc = hq ? 1.25F : 1.1F;
						c.getMatrices().translate(qx + 7.5F, yy + 8F);
						c.getMatrices().scale(sc, sc);
						dev.solclient.chat.Emoji.draw(c, em, -4, -4, 0xFFFFFFFF);
						c.getMatrices().popMatrix();
						if (hq) {
							tooltipText = dev.solclient.Lang.tr("React", "Reagir");
						}
						region(qx, yy + 1, 15, 14, () -> Social.react(chatId, fm, em));
					} else {
						// "+": the full menu (all reactions, reply, copy, edit...)
						Gfx.textC(c, "+", qx + 8, yy + 4, hq ? 0xFFFFFFFF : Theme.DIM);
						if (hq) {
							tooltipText = dev.solclient.Lang.tr("Add reaction", "Adicionar reação");
						}
						final int ox = qx, oy = yy + 16;
						region(qx, yy + 1, 15, 14, () -> {
							menuMsg = fm;
							menuX = ox;
							menuY = oy;
						});
					}
				}
			}
			int total = my1 + Math.round(chatScroll) - by + 8;
			chatMax = Math.max(0, total - (my1 - my0));
			chatScroll = Math.max(0, Math.min(chatMax, chatScroll));
			c.disableScissor();
			clipY0 = oc0;
			clipY1 = oc1;
			if (chatMax > 0) {
				int trackH = my1 - my0;
				int barH = Math.max(14, trackH * trackH / Math.max(trackH, total));
				int barY = my0 + Math.round((trackH - barH) * (1F - chatScroll / chatMax));
				Gfx.rrectF(c, right - 5, my0, 3, trackH, 1.5F, 0x14FFFFFF);
				Gfx.rrectF(c, right - 5, barY, 3, barH, 1.5F, 0x66FFFFFF);
			}
			if (newBelow || chatScroll > 60) {
				String label = newBelow ? "New messages" : "Latest";
				int lw = Gfx.tw(label) + 22, lx = px + CW / 2 - lw / 2, lyy = my1 - 22;
				boolean hl = in(mx, my, lx, lyy, lw, 16);
				Gfx.rrect(c, lx, lyy, lw, 16, 8, newBelow ? (hl ? Theme.accentHover() : accent) : (hl ? Theme.OFF_BG_HOVER : Theme.OFF_BG));
				Gfx.text(c, label, lx + 8, lyy + 4, 0xFFFFFFFF);
				chevron(c, lx + lw - 9, lyy + 8, true, 0xFFFFFFFF);
				if (settled) {
					region(lx, lyy, lw, 16, () -> {
						chatScroll = 0;
						newBelow = false;
					});
				}
			}
		}

		// ---------------- editing banner
		if (editing != null) {
			int ey = iy - 4 - editH - pickerH;
			Gfx.text(c, "Editing message", ix + 2, ey + 3, accent);
			int ex = ix + iw - 12;
			boolean he = in(mx, my, ex - 2, ey, 14, 12);
			drawX(c, ex, ey + 2, 7, he ? Theme.RED : Theme.DIM);
			if (settled) {
				region(ex - 2, ey, 14, 12, () -> {
					editing = null;
					chatInput.clear();
				});
			}
		}

		// ---------------- picker (emoji / GIF) above the input
		if (picker != 0) {
			drawPicker(c, mx, my, ix, iy - 4 - pickerH, iw, pickerH, settled, accent);
		}

		// ---------------- input bar
		boolean hg = in(mx, my, ix, iy, ib, ib);
		Gfx.button(c, ix, iy, ib, ib, hg, picker == 2);
		Gfx.tex(c, Theme.icon2("gif"), ix + 4, iy + 4, 14, 14, 64, 64, picker == 2 ? Gfx.BTN_TEXT_ON : Theme.TEXT);
		if (hg) {
			tooltipText = "GIFs";
		}
		int ex2 = ix + ib + 3;
		boolean hem = in(mx, my, ex2, iy, ib, ib);
		Gfx.button(c, ex2, iy, ib, ib, hem, picker == 1 || picker == 3);
		dev.solclient.chat.Emoji.draw(c, "😀", ex2 + 6, iy + 6, 0xFFFFFFFF);
		if (hem) {
			tooltipText = "Emojis";
		}
		// share my location (a waypoint card your friend can add with one click)
		int lx2 = ix + 2 * (ib + 3);
		boolean canLoc = MinecraftClient.getInstance().player != null;
		boolean hl2 = in(mx, my, lx2, iy, ib, ib);
		Gfx.button(c, lx2, iy, ib, ib, hl2 && canLoc, false);
		int pc = canLoc ? Theme.TEXT : Theme.DIM;
		Gfx.circle(c, lx2 + ib / 2F, iy + 8.5F, 4.5F, pc);
		Gfx.circle(c, lx2 + ib / 2F, iy + 8.5F, 1.8F, Gfx.BTN_BODY);
		for (int i = 0; i < 5; i++) {
			c.fill(lx2 + ib / 2 - 4 + i, iy + 11 + i, lx2 + ib / 2 + 5 - i, iy + 12 + i, pc);
		}
		if (hl2) {
			tooltipText = canLoc ? "Send my location" : "Join a world to send your location";
		}
		if (settled && canLoc) {
			region(lx2, iy, ib, ib, this::sendLocation);
		}
		if (settled) {
			region(ix, iy, ib, ib, () -> {
				picker = picker == 2 ? 0 : 2;
				pickerScroll = 0;
				if (picker == 2) {
					focus = F_GIF;
					gifAsked = null;
				}
			});
			region(ex2, iy, ib, ib, () -> {
				picker = picker == 1 ? 0 : 1;
				pickerScroll = 0;
				focus = F_CHAT;
			});
		}
		field(c, fieldX, iy, fieldW, ib, chatInput, editing != null ? "Edit your message..." : "Message " + fit(name, fieldW - 60) + "...", focus == F_CHAT, accent, false);
		if (settled) {
			region(fieldX, iy, fieldW, ib, () -> focus = F_CHAT);
		}
		boolean can = !chatInput.text().isBlank();
		boolean hs = in(mx, my, sendX, iy, ib, ib);
		Gfx.button(c, sendX, iy, ib, ib, hs && can, false);
		int ax = sendX + 8, ay = iy + 10;
		for (int i = 0; i < 6; i++) {
			c.fill(ax + i, ay - 5 + i, ax + i + 1, ay + 5 - i, can ? 0xFFFFFFFF : Theme.DIM);
		}
		if (hs) {
			tooltipText = editing != null ? "Save (Enter)" : "Send (Enter)";
		}
		if (settled) {
			region(sendX, iy, ib, ib, this::sendChat);
		}
		// "<name> is typing..." with three bouncing dots, like Discord (only friend chats: KuboLive)
		Social.User typingFriend = Social.friend(chatId);
		Social.Group typingGroup = Social.group(chatId);
		List<String> gTypers = typingGroup == null ? List.of() : dev.solclient.social.KuboLive.groupTypers(typingGroup.id);
		if (!gTypers.isEmpty()) {
			long t = System.currentTimeMillis();
			int dy0 = iy - 9;
			for (int i = 0; i < 3; i++) {
				double ph = ((t + i * 160) % 1200) / 1200.0;
				float lift = ph < 0.35 ? (float) Math.sin(ph / 0.35 * Math.PI) * 2.2F : 0F;
				Gfx.circle(c, fieldX + 4 + i * 5, dy0 + 3 - lift, 1.6F, ph < 0.35 ? Theme.TEXT : Theme.DIM);
			}
			String who = gTypers.size() > 2 ? gTypers.size() + dev.solclient.Lang.tr(" people", " pessoas") : String.join(", ", gTypers);
			String rest = gTypers.size() == 1 ? dev.solclient.Lang.tr(" is typing...", " está a escrever...") : dev.solclient.Lang.tr(" are typing...", " estão a escrever...");
			Gfx.textRaw(c, fitRaw(who, 120), fieldX + 20, dy0, Theme.TEXT);
			Gfx.textRaw(c, dev.solclient.Lang.t(rest), fieldX + 20 + Gfx.twRaw(fitRaw(who, 120)), dy0, Theme.DIM);
		} else if (typingFriend != null && dev.solclient.social.KuboLive.isTyping(typingFriend.display())) {
			long t = System.currentTimeMillis();
			int dy0 = iy - 9;
			for (int i = 0; i < 3; i++) {
				double ph = ((t + i * 160) % 1200) / 1200.0;
				float lift = ph < 0.35 ? (float) Math.sin(ph / 0.35 * Math.PI) * 2.2F : 0F;
				Gfx.circle(c, fieldX + 4 + i * 5, dy0 + 3 - lift, 1.6F, ph < 0.35 ? Theme.TEXT : Theme.DIM);
			}
			String who = typingFriend.display();
			String rest = dev.solclient.Lang.tr(" is typing...", " está a escrever...");
			Gfx.textRaw(c, who, fieldX + 20, dy0, Theme.TEXT);
			Gfx.textRaw(c, dev.solclient.Lang.t(rest), fieldX + 20 + Gfx.twRaw(who), dy0, Theme.DIM);
		}
		int len = chatInput.text().length();
		if (len > chatInput.max - 40) {
			String cnt = len + "/" + chatInput.max;
			Gfx.text(c, cnt, fieldX + fieldW - 4 - Gfx.tw(cnt), iy - 10, len >= chatInput.max ? 0xFFFF8A8A : Theme.DIM);
		}

		// ---------------- message menu (on top of everything)
		if (menuMsg != null) {
			drawMessageMenu(c, mx, my, px, right, y, h, settled, accent);
		}
		c.disableScissor();
	}

	/** React / Edit / Delete / Copy for the clicked message. */
	private void drawMessageMenu(DrawContext c, int mx, int my, int px, int right, int y, int h, boolean settled, int accent) {
		Social.Msg m = menuMsg;
		// click anywhere else closes it
		if (settled) {
			region(px, y, right - px, h, () -> menuMsg = null);
		}
		List<String[]> items = new ArrayList<>();
		boolean text = m.body != null && !"invite".equals(m.kind) && !dev.solclient.chat.Gifs.isGif(m.body);
		if (m.mine && text && !m.pending) {
			items.add(new String[]{"edit", "Edit"});
		}
		if (m.body != null) {
			items.add(new String[]{"copy", dev.solclient.chat.Gifs.isGif(m.body) ? "Copy GIF link" : "Copy text"});
		}
		if (m.body != null && dev.solclient.chat.Gifs.isGif(m.body)) {
			items.add(new String[]{"fav", dev.solclient.chat.Gifs.isFavourite(m.body.trim()) ? "Remove from favourite GIFs" : "Add to favourite GIFs"});
		}
		if (m.mine) {
			items.add(new String[]{"delete", "Delete"});
		}
		int qs = 16, w = dev.solclient.chat.Emoji.QUICK.length * (qs + 2) + qs + 10;
		int hgt = 8 + qs + 4 + items.size() * 15 + 2;
		int x0 = Math.max(px + 4, Math.min(right - w - 4, menuX - w / 2));
		int y0 = Math.max(y + 40, Math.min(y + h - hgt - 40, menuY - hgt - 4));
		Gfx.rrect(c, x0 - 1, y0 - 1, w + 2, hgt + 2, 7, 0xFF0A0B10);
		Gfx.rrect(c, x0, y0, w, hgt, 6, 0xFF1A1C24);
		if (settled) {
			region(x0, y0, w, hgt, () -> {
			}); // clicks on the menu background do nothing
		}
		int qx = x0 + 5, qy = y0 + 5;
		boolean canReact = !m.pending && !m.failed;
		for (String e : dev.solclient.chat.Emoji.QUICK) {
			boolean hq = in(mx, my, qx, qy, qs, qs);
			if (hq) {
				Gfx.rrect(c, qx, qy, qs, qs, 4, 0xFF2E3140);
			}
			dev.solclient.chat.Emoji.draw(c, e, qx + 3, qy + 4, canReact ? 0xFFFFFFFF : 0x66FFFFFF);
			if (settled && canReact) {
				region(qx, qy, qs, qs, () -> {
					Social.react(chatId, m, e);
					menuMsg = null;
				});
			}
			qx += qs + 2;
		}
		boolean hp = in(mx, my, qx, qy, qs, qs);
		Gfx.rrect(c, qx, qy, qs, qs, 4, hp ? 0xFF2E3140 : 0xFF22242E);
		Gfx.textFit(c, "+", qx + qs / 2, qs - 6, qy + 4, Theme.TEXT);
		if (settled && canReact) {
			region(qx, qy, qs, qs, () -> {
				reactTarget = m;
				picker = 3;
				pickerScroll = 0;
				menuMsg = null;
			});
		}
		int iy0 = y0 + 8 + qs + 2;
		for (String[] it : items) {
			boolean hi = in(mx, my, x0 + 3, iy0, w - 6, 14);
			if (hi) {
				Gfx.rrect(c, x0 + 3, iy0, w - 6, 14, 4, it[0].equals("delete") ? 0xFF4A1C1F : 0xFF2E3140);
			}
			Gfx.text(c, it[1], x0 + 8, iy0 + 3, it[0].equals("delete") ? 0xFFFF8A8A : Theme.TEXT);
			if (settled) {
				region(x0 + 3, iy0, w - 6, 14, () -> {
					switch (it[0]) {
						case "edit" -> {
							editing = m;
							chatInput.set(m.body);
							focus = F_CHAT;
							picker = 0;
						}
						case "copy" -> {
							MinecraftClient.getInstance().keyboard.setClipboard(m.body);
							Social.say("Copied", false);
						}
						case "delete" -> Social.deleteMessage(chatId, m);
						case "fav" -> Social.say(dev.solclient.chat.Gifs.toggleFavourite(m.body) ? "Added to favourites" : "Removed from favourites", false);
						default -> {
						}
					}
					menuMsg = null;
				});
			}
			iy0 += 15;
		}
	}

	private float gifChipScroll;
	private int gifChipMax, gifChipY = -100;
	private String gifClipUsed, gifClipText;
	private long gifClipAt;

	/** A GIF link (or GIPHY / Tenor page) on the clipboard that was not sent yet, else null. Checked twice a second. */
	private String gifClip(long now) {
		if (now - gifClipAt > 500) {
			gifClipAt = now;
			try {
				String t = MinecraftClient.getInstance().keyboard.getClipboard();
				gifClipText = t == null || t.length() > 600 ? null : t.trim();
			} catch (Exception e) {
				gifClipText = null;
			}
		}
		String t = gifClipText;
		if (t == null || t.isEmpty() || t.equals(gifClipUsed)) {
			return null;
		}
		return dev.solclient.chat.Gifs.isGif(t) || dev.solclient.chat.Gifs.isGifPage(t) ? t : null;
	}

	/** Sends a GIF link or page (pages are turned into the direct link first) and remembers it in Recent. */
	private void sendGif(String link) {
		String target = chatId;
		if (target == null) {
			return;
		}
		if (dev.solclient.chat.Gifs.isGifPage(link)) {
			dev.solclient.chat.Gifs.resolvePage(link, direct -> {
				String u = direct != null ? direct : link;
				if (direct != null) {
					dev.solclient.chat.Gifs.sent(u);
				}
				Social.sendMessage(target, u);
			});
		} else {
			dev.solclient.chat.Gifs.sent(link);
			Social.sendMessage(target, link.trim());
		}
		picker = 0;
		chatScroll = 0;
	}

	/** Emoji grid (insert / react) or GIF search grid. */
	private void drawPicker(DrawContext c, int mx, int my, int x, int y, int w, int h, boolean settled, int accent) {
		Gfx.rrect(c, x - 1, y - 1, w + 2, h + 2, 7, 0xFF0A0B10);
		Gfx.rrect(c, x, y, w, h, 6, 0xFF16181F);
		pkx = x;
		pky = y;
		pkw = w;
		pkh = h;
		if (settled) {
			region(x, y, w, h, () -> {
			});
		}
		if (picker == 2) {
			int tab = dev.solclient.chat.Gifs.tab;
			// tabs: GIFs / Stickers / Recent / Favourites
			int tx = x + 4, tw = (w - 8 - 6) / 4;
			for (int i = 0; i < 4; i++) {
				int bx = tx + i * (tw + 2);
				boolean on = tab == i, ht = in(mx, my, bx, y + 4, tw, 14);
				Gfx.rrect(c, bx, y + 4, tw, 14, 5, on ? accent : ht ? 0xFF2E3140 : 0xFF22242E);
				Gfx.textFit(c, dev.solclient.chat.Gifs.TABS[i], bx + tw / 2, tw - 4, y + 7, on ? 0xFFFFFFFF : Theme.TEXT);
				if (settled) {
					final int ti = i;
					region(bx, y + 4, tw, 14, () -> {
						dev.solclient.chat.Gifs.setTab(ti);
						gifAsked = null;
						pickerScroll = 0;
					});
				}
			}
			// search box
			int sy = y + 21;
			field(c, x + 4, sy, w - 8, 16, gifSearch, tab == 1 ? "Search stickers..." : tab >= 2 ? "Filter..." : "Search GIPHY...", focus == F_GIF, accent, true);
			if (settled) {
				region(x + 4, sy, w - 8, 16, () -> focus = F_GIF);
			}
			String q = gifSearch.text().trim();
			long now = System.currentTimeMillis();
			if (!q.equals(gifAsked) && now - gifTyped > 450 || gifAsked == null) {
				gifAsked = q;
				dev.solclient.chat.Gifs.search(q);
			}
			int gy0 = sy + 19;
			// a GIF link copied somewhere (browser, Discord...): one click sends it
			String clip = gifClip(now);
			if (clip != null) {
				boolean hc = in(mx, my, x + 4, gy0, w - 8, 13);
				Gfx.rrect(c, x + 4, gy0, w - 8, 13, 4, hc ? Gfx.blend(0xFF22242E, accent, 0.45F) : Gfx.blend(0xFF22242E, accent, 0.25F));
				Gfx.textFit(c, "Send the GIF link you copied", x + w / 2, w - 14, gy0 + 3, 0xFFFFFFFF);
				if (settled) {
					region(x + 4, gy0, w - 8, 13, () -> {
						sendGif(clip);
						gifClipUsed = clip;
					});
				}
				gy0 += 16;
			}
			// quick searches
			if (tab < 2) {
				int cx0 = x + 4 - Math.round(gifChipScroll), cyq = gy0;
				c.enableScissor(x + 4, cyq, x + w - 4, cyq + 13);
				for (String k : dev.solclient.chat.Gifs.QUICK) {
					String label = k.isEmpty() ? "Trending" : k;
					int cw0 = Gfx.tw(label) + 10;
					boolean on = q.equalsIgnoreCase(k), hq = in(mx, my, cx0, cyq, cw0, 12) && mx >= x + 4 && mx < x + w - 4;
					Gfx.rrect(c, cx0, cyq, cw0, 12, 6, on ? Gfx.blend(0xFF22242E, accent, 0.6F) : hq ? 0xFF2E3140 : 0xFF1E2029);
					Gfx.text(c, label, cx0 + 5, cyq + 2, on ? 0xFFFFFFFF : Theme.DIM);
					if (settled && cx0 + cw0 > x + 4 && cx0 < x + w - 4) {
						region(Math.max(x + 4, cx0), cyq, Math.min(cx0 + cw0, x + w - 4) - Math.max(x + 4, cx0), 12, () -> {
							gifSearch.set(k);
							gifAsked = null;
							pickerScroll = 0;
						});
					}
					cx0 += cw0 + 3;
				}
				c.disableScissor();
				gifChipMax = Math.max(0, cx0 + Math.round(gifChipScroll) - (x + w - 4));
				gifChipY = cyq;
				gy0 += 15;
			} else {
				gifChipMax = 0;
			}
			int gy1 = y + h - 4;
			List<dev.solclient.chat.Gifs.Result> res = dev.solclient.chat.Gifs.results;
			String st = dev.solclient.chat.Gifs.state;
			if (res.isEmpty()) {
				int ty = gy0 + 10;
				for (String l : wrap(st == null || st.isEmpty() ? "Type to search" : st, w - 16, 3)) {
					Gfx.textFit(c, l, x + w / 2, w - 6, ty, Theme.DIM);
					ty += 10;
				}
				pickerMax = 0;
				return;
			}
			int cols = 3, gap = 3, cw = (w - 8 - (cols - 1) * gap) / cols, ch = Math.round(cw * 0.75F);
			int rows = (res.size() + cols - 1) / cols;
			pickerMax = Math.max(0, rows * (ch + gap) - (gy1 - gy0) + (tab < 2 ? 12 : 0));
			pickerScroll = Math.max(0, Math.min(pickerMax, pickerScroll));
			if (tab < 2 && pickerScroll >= pickerMax - ch) {
				dev.solclient.chat.Gifs.more(); // near the bottom: load the next page
			}
			c.enableScissor(x, gy0, x + w, gy1);
			for (int i = 0; i < res.size(); i++) {
				var r = res.get(i);
				int cx = x + 4 + (i % cols) * (cw + gap), cy = gy0 + (i / cols) * (ch + gap) - Math.round(pickerScroll);
				if (cy + ch < gy0 || cy > gy1) {
					continue;
				}
				boolean hv = in(mx, my, cx, cy, cw, ch) && my >= gy0 && my < gy1;
				// animate the one under the mouse, still pictures for the rest
				dev.solclient.chat.Gifs.Anim a = dev.solclient.chat.Gifs.get(hv ? r.url() : r.preview());
				if (a == null && hv) {
					a = dev.solclient.chat.Gifs.get(r.preview());
				}
				if (tab == 1) {
					Gfx.rrect(c, cx, cy, cw, ch, 3, 0xFF1E2029); // stickers are see-through
				}
				if (a != null) {
					dev.solclient.chat.Gifs.draw(c, a, cx, cy, cw, ch);
				} else {
					Gfx.rrect(c, cx, cy, cw, ch, 3, 0xFF22242E);
				}
				boolean fav = dev.solclient.chat.Gifs.isFavourite(r.url());
				if (fav || hv) {
					Gfx.circle(c, cx + cw - 6, cy + 6, 4.5F, 0xC0000000);
					Gfx.tex(c, Theme.icon2("favourite"), cx + cw - 10, cy + 2, 8, 8, 64, 64, fav ? 0xFFFFC94A : 0xFFFFFFFF);
				}
				if (hv) {
					Gfx.rrect(c, cx, cy + ch - 2, cw, 2, 0, accent);
					tooltipText = fav ? "Click: send - right click: remove from favourites" : "Click: send - right click: add to favourites";
				}
				if (settled) {
					region(cx, cy, cw, ch, b -> {
						if (b == 1) {
							boolean nowFav = dev.solclient.chat.Gifs.toggleFavourite(r);
							Social.say(nowFav ? "Added to favourites" : "Removed from favourites", false);
						} else {
							dev.solclient.chat.Gifs.sent(r);
							Social.sendMessage(chatId, r.url());
							picker = 0;
							chatScroll = 0;
						}
					}, null);
				}
			}
			if (dev.solclient.chat.Gifs.loadingMore()) {
				int ly = gy0 + rows * (ch + gap) - Math.round(pickerScroll);
				Gfx.textFit(c, "Loading more...", x + w / 2, w - 8, ly + 1, Theme.DIM);
			}
			c.disableScissor();
			if (tab < 2) {
				Gfx.text(c, "via GIPHY", x + w - 4 - Gfx.tw("via GIPHY"), y + h - 11, 0x55FFFFFF);
			}
			return;
		}
		// emojis
		List<String> all = dev.solclient.chat.Emoji.all();
		int cell = 16, cols = Math.max(1, (w - 8) / cell);
		int gy0 = y + 4, gy1 = y + h - 4;
		if (picker == 3) {
			Gfx.text(c, "React with...", x + 6, y + 5, Theme.DIM);
			gy0 += 12;
		}
		int rows = (all.size() + cols - 1) / cols;
		pickerMax = Math.max(0, rows * cell - (gy1 - gy0));
		pickerScroll = Math.max(0, Math.min(pickerMax, pickerScroll));
		c.enableScissor(x, gy0, x + w, gy1);
		int ox = x + 4 + ((w - 8) - cols * cell) / 2;
		for (int i = 0; i < all.size(); i++) {
			int cx = ox + (i % cols) * cell, cy = gy0 + (i / cols) * cell - Math.round(pickerScroll);
			if (cy + cell < gy0 || cy > gy1) {
				continue;
			}
			String e = all.get(i);
			boolean hv = in(mx, my, cx, cy, cell, cell) && my >= gy0 && my < gy1;
			if (hv) {
				Gfx.rrect(c, cx, cy, cell, cell, 4, 0xFF2E3140);
			}
			dev.solclient.chat.Emoji.draw(c, e, cx + 3, cy + 4, 0xFFFFFFFF);
			if (settled) {
				region(cx, cy, cell, cell, () -> {
					if (picker == 3) {
						Social.react(chatId, reactTarget, e);
						picker = 0;
					} else {
						chatInput.insert(e);
						focus = F_CHAT;
					}
				});
			}
		}
		c.disableScissor();
	}

	private static void chevron(DrawContext c, int cx, int cy, boolean down, int col) {
		for (int i = 0; i < 4; i++) {
			int yy = down ? cy - 2 + i : cy + 1 - i;
			c.fill(cx - 4 + i, yy, cx - 3 + i, yy + 1, col);
			c.fill(cx + 3 - i, yy, cx + 4 - i, yy + 1, col);
		}
	}

	private static void chevronLeft(DrawContext c, int cx, int cy, int col) {
		Gfx.tex(c, Theme.icon2("back"), cx - 5, cy - 5, 10, 10, 64, 64, col);
	}

	// -------------------------------------------------------------- input
	/** Drag a photo onto the window while the Custom Sky settings are open. (No @Override on purpose: never breaks the build.) */
	public void onFilesDropped(java.util.List<java.nio.file.Path> paths) {
		if (open instanceof dev.solclient.mods.CustomSkyMod sky && paths != null && !paths.isEmpty()) {
			sky.choose(paths.get(0));
		} else if (open != null && paths != null && !paths.isEmpty() && open.onFileDropped(paths.get(0))) {
			// the open mod took the file (TikTok Live sounds, ...)
		} else if (open == null && view == V_SHOP && !lockerShop && invCat.equals("Skins") && paths != null && !paths.isEmpty()) {
			for (java.nio.file.Path p : paths) {
				dev.solclient.skin.SkinChanger.addFile(p);
			}
		} else if (open == null && view == V_SKIN && paths != null && !paths.isEmpty()) {
			if (capesTab == 0 && showCloudSkins) {
				Social.upload(paths.get(0)); // the old cloud list is open: keep its behaviour
			} else {
				dev.solclient.skin.SkinChanger.addFile(paths.get(0), true);
			}
		}
	}

	/** Copies what was typed into the Option.Text being edited. */
	private void syncOpt(TextBox b) {
		if (focus == F_OPT && optFocus != null && b != null) {
			optFocus.set(b.text());
		}
	}

	private TextBox focusedBox() {
		return switch (focus) {
			case F_SEARCH -> search;
			case F_FRIEND_SEARCH -> friendSearch;
			case F_CHAT -> chatInput;
			case F_PROFILE -> profileName;
			case F_LOCKER -> lockerSearch;
			case F_SKIN -> skinInput;
			case F_GIF -> gifSearch;
			case F_GROUP -> groupName;
			case F_PACKS -> packs == null ? null : packs.searchBox();
			case F_OPT -> optFocus == null ? null : optBoxes.get(optFocus);
			default -> null;
		};
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x() * f, my = click.y() * f;
		int button = click.button();
		if (inEmbedded(mx, my)) {
			embeddedFocus = true;
			return embedded.mouseClicked(new Click(embX(mx), embY(my), click.buttonInfo()), doubled) || true;
		}
		embeddedFocus = false;
		listening = null;
		focus = F_NONE; // a region below may set it again
		if (button == 0 && Notify.click(click.x(), click.y())) {
			return true;
		}
		boolean menuWasOpen = statusMenu;
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region r = regions.get(i);
			if (r.contains(mx, my)) {
				if (r.drag != null) {
					activeDrag = r;
					dragButton = button;
					r.drag.accept(mx, my);
				}
				if (r.click != null) {
					r.click.accept(button);
				}
				if (menuWasOpen && statusMenu && i < regions.size() && !isStatusRegion(i)) {
					statusMenu = false;
				}
				// clicking a text field: place the caret / start a selection
				TextBox box = focusedBox();
				if (box != null && button == 0 && box.inside(mx, my)) {
					box.click(mx);
					draggingBox = box;
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(Click click, double dx, double dy) {
		if (embedded != null && embeddedFocus) {
			embedded.mouseDragged(new Click(embX(click.x() * f), embY(click.y() * f), click.buttonInfo()), dx * f * embScale(), dy * f * embScale());
			return true;
		}
		if (draggingBox != null) {
			draggingBox.drag(click.x() * f);
			return true;
		}
		if (activeDrag != null && activeDrag.drag != null) {
			activeDrag.drag.accept(click.x() * f, click.y() * f);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (embedded != null && embeddedFocus) {
			embedded.mouseReleased(new Click(embX(click.x() * f), embY(click.y() * f), click.buttonInfo()));
			embeddedFocus = false;
			return true;
		}
		if (draggingBox != null) {
			draggingBox.release();
			draggingBox = null;
		}
		activeDrag = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		double gx = mx * f, gy = my * f;
		if (inEmbedded(gx, gy)) {
			return embedded.mouseScrolled(embX(gx), embY(gy), hAmount, vAmount) || true;
		}
		if (picker != 0 && chatId != null && gx >= pkx && gx < pkx + pkw && gy >= pky && gy < pky + pkh) {
			pickerScroll = Math.max(0, Math.min(pickerMax, pickerScroll - (float) vAmount * 20F));
			return true;
		}
		if (cbw > 0 && chatId != null && gx >= cbx && gx < cbx + cbw && gy >= cby && gy < cby + cbh) {
			chatScroll = Math.max(0, Math.min(chatMax, chatScroll + (float) vAmount * 24F));
			return true;
		}
		if (fbw > 0 && gx >= fbx && gx < fbx + fbw && gy >= fby && gy < fby + fbh) {
			friendScroll = Math.max(0, Math.min(friendMax, friendScroll - (float) vAmount * 28F));
			return true;
		}
		if (view == V_PACKS && open == null && packs != null) {
			packs.scrolled(gx, vAmount);
			return true;
		}
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) vAmount * 34F));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();
		if (embedOn()) {
			if (key == 256) {
				closeEmbedded();
				return true;
			}
			embedded.keyPressed(input);
			return true;
		}
		if (key == 75 && input.hasCtrlOrCmd() && listening == null) {
			CommanderScreen.open(); // Ctrl / Cmd + K
			return true;
		}
		if (Crates.overlayOpen() || Crates.infoOpen()) {
			if (key == 256) {
				Crates.escape();
			}
			return true;
		}
		if (buyItem != null && key == 256) {
			buyItem = null;
			return true;
		}
		if (listening != null) {
			if (key == 256) {
				listening = null;
			} else if (key == 259 || key == 261) {
				listening.set(-1);
				listening = null;
			} else {
				// hold Ctrl / Shift / Alt while pressing the key for a combo (more shortcuts than keys)
				listening.set(Keys.withMods(key, input.hasCtrlOrCmd(), input.hasShift(), input.hasAlt()));
				listening = null;
			}
			return true;
		}
		if (focus != F_NONE) {
			TextBox box = focusedBox();
			if (focus == F_GIF) {
				gifTyped = System.currentTimeMillis();
			}
			if (box != null && key != 256 && key != 257 && key != 335 && box.key(key)) {
				syncOpt(box);
				if (focus == F_SEARCH || focus == F_LOCKER) {
					scrollTarget = 0;
				} else if (focus == F_FRIEND_SEARCH) {
					friendScroll = 0;
				}
			} else if (key == 256) {
				if (focus == F_PACKS && packs != null && packs.back()) {
					// closed a pack's detail page
				} else if (menuMsg != null || picker != 0 || editing != null) {
					menuMsg = null;
					picker = 0;
					if (editing != null) {
						editing = null;
						chatInput.clear();
					}
				} else {
					focus = F_NONE;
				}
			} else if (key == 257 || key == 335) {
				if (focus == F_GIF) {
					gifAsked = gifSearch.text().trim();
					dev.solclient.chat.Gifs.search(gifAsked);
				} else if (focus == F_GROUP) {
					focus = F_NONE;
				} else if (focus == F_PACKS) {
					if (packs != null) {
						packs.submit();
					}
				} else if (focus == F_CHAT) {
					sendChat();
				} else if (focus == F_FRIEND_SEARCH && addMode) {
					Social.addFriend(friendSearch.text());
					friendSearch.clear();
				} else if (focus == F_PROFILE) {
					saveProfile();
				} else if (focus == F_SKIN) {
					dev.solclient.skin.SkinChanger.add(skinInput.text());
					skinInput.clear();
				} else {
					focus = F_NONE;
				}
			}
			return true;
		}
		if (open instanceof dev.solclient.mods.PersonasMod && dev.solclient.voice.persona.PersonaPanel.INSTANCE.key(key)) {
			return true; // backspace / escape in the voice search
		}
		if (key == SolClient.settings.modsKey.code()) {
			closeMenu();
			return true;
		}
		if (key == 256) {
			if (open != null) {
				open = null;
				scrollTarget = 0;
			} else if (view == V_PACKS && packs != null && packs.back()) {
				// closed a pack's detail page
			} else if (view == V_PACKS) {
				showView(V_MODS, 0);
			} else if (chatId != null) {
				closeChat();
			} else {
				closeMenu();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (embedOn()) {
			embedded.charTyped(input);
			return true;
		}
		if (focus == F_NONE && open == null && !friendsOpen && !editOpen && (view == V_MODS || view == V_SHOP || view == V_SKIN)) {
			focus = view == V_MODS ? F_SEARCH : F_LOCKER; // just start typing: the search box takes it
		}
		if (focus == F_NONE && open instanceof dev.solclient.mods.PersonasMod) {
			return dev.solclient.voice.persona.PersonaPanel.INSTANCE.typed(input.codepoint()); // typing searches the voices
		}
		if (focus == F_NONE) {
			return false;
		}
		int cp = input.codepoint();
		if (cp < 32 || cp == 127) {
			return true;
		}
		String ch = new String(Character.toChars(cp));
		TextBox box = focusedBox();
		if (focus == F_CHAT) {
			Social.User tf = Social.friend(chatId);
			if (tf != null) {
				dev.solclient.social.KuboLive.typing(tf.display(), true);
			} else if (Social.group(chatId) != null) {
				dev.solclient.social.KuboLive.groupTyping(Social.group(chatId));
			}
		}
		if (box != null) {
			box.typed(cp);
			syncOpt(box);
			if (focus == F_SEARCH || focus == F_LOCKER) {
				scrollTarget = 0;
			} else if (focus == F_FRIEND_SEARCH) {
				friendScroll = 0;
			}
		}
		return true;
	}

	@Override
	public void removed() {
		closeEmbedded();
		Config.save();
		savedEdit = editOpen;
		savedFriends = friendsOpen;
		savedChat = friendsOpen ? chatId : null;
		savedView = open == null ? view : V_MODS;
		savedNav = nav;
		if (packs != null) {
			packs.dispose();
		}
		Crates.shutdown();
		Social.onMenuClosed();
	}
}
