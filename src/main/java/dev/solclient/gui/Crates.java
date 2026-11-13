package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.SoundLib;
import dev.solclient.music.MusicHub;
import dev.solclient.social.RemoteTextures;
import dev.solclient.social.Social;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/**
 * The Store tab (Locker: Inventory | Shop | Store): crates and coin chests in a list, cheapest on top and the best at the
 * bottom, each with an (i) that shows the drop chances. Opening one is a full-screen scene: the box shakes while the sound
 * plays, opens (frames rendered from the 3D models, see assets/sol_client/textures/gui/crates) and the prize rises out of it.
 *
 * While the opening sound plays everything else is turned down (the game's sound categories, and Spotify / Music when the
 * client can reach them, see {@link MusicHub#duck}).
 *
 * The site rolls the prize ({@link Social#crateOpen}); when it has no crates yet the opening is a preview that charges and
 * gives nothing. Coin chests never give anything for now: they only play the animation (the website is not live yet).
 */
final class Crates {

	/** Sink for click areas (the menu screen registers them). */
	interface Host {
		void region(int x, int y, int w, int h, Runnable click);
	}

	/** Rarity colours / names, common to legendary. */
	static final int[] RARITY_COL = {0xFF9AA3B2, 0xFF4D8DFF, 0xFFB05CFF, 0xFFFFC53D};
	static final String[] RARITY = {"Common", "Rare", "Epic", "Legendary"};

	/** Buying coins with real money opens this page. Empty = the website is not ready, so a coin chest only plays the animation. */
	static final String WEBSITE_BUY_URL = "";

	private static final int SHEET = 192, COLS = 8;
	/** Sound of the opening of the random crates (10.8 s, the prize shows when the fanfare starts at 8.25 s). */
	private static final long OPEN_SOUND_MS = 10_900L;
	private static final long FRAME_MS = 55L;

	record Def(String id, String name, String tag, String contains, String idleSheet, int idleRows, int idleN, String openSheet, int openRows, int openOff, int openN,
			long price, int[] odds, int glowA, int glowB, int season, boolean coinPack, long revealMs) {
	}

	record Pack(String name, long coins, String price) {
	}

	static final Pack[] PACKS = {new Pack("Small Coin Chest", 500, "1.99 €"), new Pack("Medium Coin Chest", 1200, "4.49 €"), new Pack("Large Coin Chest", 3000, "9.99 €")};

	private static final Def TREASURE = new Def("treasure", "Treasure Chest", "A chest full of loot", "Emotes, auras and cosmetics", "treasure", 11, 16, "treasure", 11, 16, 40, 250,
			new int[]{55, 28, 13, 4}, 0xFFFFC53D, 0xFFFF8A3D, 0, false, 8250);
	private static final Def MIMIC = new Def("mimic", "Mimic Chest", "Halloween only. It bites back.", "Emotes, auras and cosmetics", "mimic", 10, 16, "mimic", 10, 16, 32, 400,
			new int[]{40, 32, 20, 8}, 0xFFFF8A1F, 0xFF8B3DFF, Season.HALLOWEEN, false, 8250);
	private static final Def PRESENT = new Def("present", "Christmas Present", "Limited: Christmas", "Emotes, auras and cosmetics", "present", 10, 16, "present", 10, 16, 32, 300,
			new int[]{45, 30, 18, 7}, 0xFFFF4D4D, 0xFF4DD964, Season.CHRISTMAS, false, 8250);
	/** Coin chests: the animation is the treasure chest opening (its sheet), the sprite in the list is the chest full of coins. */
	private static final Def COINS = new Def("coins", "Coin Chest", "Buy coins on the website", "Coins", "coins", 3, 24, "treasure", 11, 16, 40, 0,
			new int[]{0, 0, 0, 0}, 0xFFFFC53D, 0xFFFFFFFF, 0, true, 5900);

	// --------------------------------------------------------------------------------------------- state
	private static final int BROWSE = 0, OPENING = 1, REVEAL = 2;
	private static int stage = BROWSE;
	private static Def cur;
	private static int curPack;
	private static long stageAt;
	private static boolean started;
	private static volatile Social.CrateResult result;

	private static Def infoDef;
	private static int infoPack = -1;

	static boolean overlayOpen() {
		return stage != BROWSE;
	}

	static boolean infoOpen() {
		return infoDef != null;
	}

	/** Crates in season, cheapest first. */
	static List<Def> crates() {
		List<Def> l = new ArrayList<>();
		l.add(TREASURE);
		int s = Season.current();
		if (s == Season.HALLOWEEN) {
			l.add(MIMIC);
		}
		if (s == Season.CHRISTMAS) {
			l.add(PRESENT);
		}
		l.sort(Comparator.comparingLong(Def::price));
		return l;
	}

	/** Esc / click on the dim area. Only the finished prize screen closes. */
	static void escape() {
		if (infoDef != null) {
			infoDef = null;
		} else if (stage == REVEAL) {
			stage = BROWSE;
		}
	}

	// --------------------------------------------------------------------------------------------- sound: everything else goes quiet
	private static final Map<SoundCategory, Double> SAVED = new EnumMap<>(SoundCategory.class);
	private static long duckUntil;
	private static boolean hook;

	private static void duck(long ms) {
		duckUntil = Math.max(duckUntil, System.currentTimeMillis() + ms);
		if (!SAVED.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		for (SoundCategory cat : SoundCategory.values()) {
			if (cat == SoundCategory.MASTER) {
				continue; // the crate sound itself is a master (UI) sound
			}
			try {
				SimpleOption<Double> o = mc.options.getSoundVolumeOption(cat);
				double v = o.getValue();
				SAVED.put(cat, v);
				o.setValue(v * 0.08);
			} catch (RuntimeException e) {
				System.err.println("[Sleep] crates duck: " + e);
			}
		}
		MusicHub.duck(true);
		if (!hook) {
			hook = true;
			Runtime.getRuntime().addShutdownHook(new Thread(Crates::restore, "kubo-crates-restore"));
		}
	}

	private static void restore() {
		if (SAVED.isEmpty()) {
			return;
		}
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			for (Map.Entry<SoundCategory, Double> e : SAVED.entrySet()) {
				mc.options.getSoundVolumeOption(e.getKey()).setValue(e.getValue());
			}
		} catch (RuntimeException e) {
			System.err.println("[Sleep] crates restore: " + e);
		}
		SAVED.clear();
		MusicHub.duck(false);
	}

	/** Called every frame by the menu: gives the volumes back once the sound is over. */
	static void tick() {
		if (!SAVED.isEmpty() && System.currentTimeMillis() > duckUntil) {
			restore();
		}
	}

	/** The menu closed: volumes back at once. */
	static void shutdown() {
		restore();
		infoDef = null;
		if (stage == REVEAL || !started) {
			stage = BROWSE;
		}
	}

	// --------------------------------------------------------------------------------------------- store: layout of the web design
	private static final int GAP = 12, SCENE_H = 120;
	private static final int HERO_H = 190, COIN_ART = 104, COIN_FT = 44, CRATE_ART = 150, CRATE_FT = 46, SEC_H = 34;

	private static boolean scene() {
		return Season.current() == Season.HALLOWEEN;
	}

	/** The crate of the current season (the big banner on top), or null. */
	private static Def hero() {
		for (Def d : crates()) {
			if (d.season != 0) {
				return d;
			}
		}
		return null;
	}

	/** "11 days left" until the season ends (Halloween: 2 Nov, Christmas: 31 Dec), or null out of season. */
	private static String seasonLeft() {
		int s = Season.current();
		java.time.LocalDate t = java.time.LocalDate.now();
		java.time.LocalDate end = s == Season.HALLOWEEN ? java.time.LocalDate.of(t.getMonthValue() >= 10 ? t.getYear() : t.getYear(), 11, 3)
				: s == Season.CHRISTMAS ? java.time.LocalDate.of(t.getYear() + 1, 1, 1) : null;
		if (end == null) {
			return null;
		}
		long n = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(t, end));
		return Lang.tr(n + (n == 1 ? " day left" : " days left"), n + (n == 1 ? " dia restante" : " dias restantes"));
	}

	static int storeHeight() {
		int h = 0;
		if (hero() != null) {
			h += HERO_H + 24;
		}
		h += SEC_H + COIN_ART + COIN_FT + 26;
		h += SEC_H + CRATE_ART + CRATE_FT + 8;
		if (scene()) {
			h += 18 + SCENE_H;
		}
		return h + 8;
	}

	/** The store: season banner, coin chests, crates, Halloween strip. (x, y) is the top-left of the content (already moved by the scroll). */
	static void drawStore(DrawContext c, int mx, int my, int x, int y, int w, int vy0, int vy1, Host host) {
		long now = System.currentTimeMillis();
		int yy = y;
		Def hd = hero();
		if (hd != null) {
			heroBanner(c, mx, my, x, yy, w, HERO_H, hd, vy0, vy1, now, host);
			yy += HERO_H + 24;
		}
		yy = header(c, x, yy, Lang.tr("Coin Chests", "Baús de Moedas"), Lang.tr("Buy on the website", "Compra no site"));
		int cw = (w - GAP * 2) / 3;
		for (int i = 0; i < PACKS.length; i++) {
			coinCard(c, mx, my, x + i * (cw + GAP), yy, i == 2 ? x + w - (x + i * (cw + GAP)) : cw, i, vy0, vy1, now, host);
		}
		yy += COIN_ART + COIN_FT + 26;
		String left = seasonLeft();
		yy = header(c, x, yy, Lang.tr("Crates", "Caixas"), left);
		List<Def> cr = crates();
		int bw = (w - GAP) / 2;
		for (int i = 0; i < cr.size(); i++) {
			crateCard(c, mx, my, x + i * (bw + GAP), yy, i == 1 ? x + w - (x + i * (bw + GAP)) : bw, cr.get(i), vy0, vy1, now, host);
		}
		yy += CRATE_ART + CRATE_FT + 8;
		if (scene()) {
			halloweenScene(c, x, yy + 18, w, SCENE_H, now, vy0, vy1);
		}
	}

	/** Turns the scissor on only when the area is not empty (an empty one would be invalid). */
	private static boolean clip(DrawContext c, int x0, int y0, int x1, int y1) {
		if (x1 <= x0 || y1 <= y0) {
			return false;
		}
		c.enableScissor(x0, y0, x1, y1);
		return true;
	}

	private static int header(DrawContext c, int x, int y, String title, String note) {
		bigText(c, title, x + 2, y + 4, Theme.TEXT, 1.3F, 400);
		if (note != null) {
			// a quiet note on the same line (no coloured box)
			Gfx.text(c, note, x + 2 + Math.round(Gfx.tw(title) * 1.3F) + 12, y + 9, Theme.DIM);
		}
		return y + SEC_H;
	}

	/** The little coloured labels on the cards. kind 0 = green (new), 1 = grey, 2 = gold (season). Returns the width. */
	private static int tag(DrawContext c, int x, int y, String text, int kind) {
		int bg = kind == 0 ? 0xFF0F3B26 : kind == 1 ? 0xFF262C3D : 0xFF3A2A14;
		int fg = kind == 0 ? 0xFF4EE38E : kind == 1 ? 0xFFE7E9F1 : 0xFFE0A63A;
		int bd = kind == 0 ? 0xFF1D6A43 : kind == 1 ? 0xFF495169 : 0xFF5C4318;
		int w = Gfx.tw(text) + 14;
		Gfx.rrect(c, x, y, w, 16, 3, bd);
		Gfx.rrect(c, x + 1, y + 1, w - 2, 14, 2, bg);
		Gfx.text(c, text, x + 7, y + 4, fg);
		return w;
	}

	private static void cobweb(DrawContext c, int x, int y, int size, boolean flip, int alpha) {
		Identifier id = Gfx.id("textures/gui/season/cobweb.png");
		var m = c.getMatrices();
		m.pushMatrix();
		if (flip) {
			m.translate(x + size, y);
			m.scale(-1F, 1F);
			Gfx.tex(c, id, 0, 0, size, size, 128, 128, (alpha << 24) | 0xFFFFFF);
		} else {
			Gfx.tex(c, id, x, y, size, size, 128, 128, (alpha << 24) | 0xFFFFFF);
		}
		m.popMatrix();
	}

	/** The same flat card as the rest of Sleep: one fill, one thin border, a little lighter under the mouse. */
	private static void cardFrame(DrawContext c, int x, int y, int w, int h, boolean hov, int border) {
		Gfx.rrectBorder(c, x, y, w, h, 8, hov ? Theme.CARD_HOVER : Theme.CARD, hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
	}

	/** One of our buttons (accent colour, gloss and the running shine). */
	private static void storeButton(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, boolean coin, int vy0, int vy1, long now, long off, Host host, Runnable click) {
		boolean hv = in(mx, my, x, y, w, h) && my >= vy0 && my < vy1;
		Gfx.rrect(c, x, y, w, h, 6, hv ? Theme.accentHover() : Theme.accent());
		int lw = Gfx.tw(label);
		int lx = x + (w - lw - (coin ? 16 : 0)) / 2;
		Gfx.text(c, label, lx, y + (h - 8) / 2, 0xFFFFFFFF);
		if (coin) {
			Gfx.coin(c, lx + lw + 4, y + (h - 12) / 2, 12);
		}
		host.region(x, y, w, h, click);
	}

	private static void infoDot(DrawContext c, int mx, int my, int cx, int cy, Def d, int pack, int vy0, int vy1, Host host) {
		boolean ih = in(mx, my, cx - 9, cy - 9, 18, 18) && my >= vy0 && my < vy1;
		Gfx.circle(c, cx, cy, 8F, ih ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
		Gfx.circle(c, cx, cy, 7F, Theme.CARD);
		Gfx.textC(c, "i", cx, cy - 4, ih ? Theme.TEXT : Theme.DIM);
		host.region(cx - 9, cy - 9, 18, 18, () -> {
			infoDef = d;
			infoPack = pack;
		});
	}

	private static List<String> wrap(String s, int maxW, int maxLines) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : s.split(" ")) {
			String t = cur.length() == 0 ? word : cur + " " + word;
			if (Gfx.tw(t) > maxW && cur.length() > 0) {
				out.add(cur.toString());
				cur = new StringBuilder(word);
			} else {
				cur = new StringBuilder(t);
			}
		}
		if (cur.length() > 0) {
			out.add(cur.toString());
		}
		return out.size() > maxLines ? out.subList(0, maxLines) : out;
	}

	/** The mini rarity bar (one segment per rarity, as wide as its chance). */
	private static void rarityBar(DrawContext c, int x, int y, int w, Def d) {
		int total = 0;
		for (int o : d.odds) {
			total += o;
		}
		int bx = x;
		for (int i = 0; i < 4; i++) {
			int sw = i == 3 ? x + w - bx : Math.max(4, Math.round(w * d.odds[i] / (float) total));
			c.fill(bx, y, bx + sw - (i < 3 ? 2 : 0), y + 4, RARITY_COL[i]);
			bx += sw;
		}
	}

	private static void coinCard(DrawContext c, int mx, int my, int x, int y, int w, int i, int vy0, int vy1, long now, Host host) {
		int h = COIN_ART + COIN_FT;
		if (y + h + 6 < vy0 || y > vy1) {
			return;
		}
		boolean hov = in(mx, my, x, y, w, h) && my >= vy0 && my < vy1;
		cardFrame(c, x, y, w, h, hov, 0);
		int ax = x + 2, ay = y + 2, aw = w - 4, ah = COIN_ART - 2;
		if (clip(c, ax, Math.max(ay, vy0), ax + aw, Math.min(ay + ah, vy1))) {
			// just the chest: no glow, no glitter
			int size = Math.round(120 * (1F + 0.2F * i));
			int sx = x + w / 2 - size / 2, sy = ay + ah / 2 - size / 2 + 8 + (hov ? -3 : 0);
			int frame = (int) ((now / 83L + (i + 1) * 5L) % COINS.idleN);
			sprite(c, COINS.idleSheet, COINS.idleRows, frame, sx, sy, size, 1F);
			c.disableScissor();
		}
		if (i > 0) {
			Gfx.text(c, i == 1 ? Lang.tr("Popular", "Popular") : Lang.tr("Best value", "Melhor valor"), x + 14, y + 14, Theme.accent());
		}
		int fy = y + COIN_ART;
		c.fill(x + 14, fy, x + w - 14, fy + 1, Theme.CARD_BORDER);
		String title = String.format(Locale.ROOT, "%,d", PACKS[i].coins()).replace(',', ' ') + " " + Lang.tr("coins", "moedas");
		bigText(c, title, x + 14, fy + 10, Theme.TEXT, 1.15F, w - 120);
		Gfx.text(c, Lang.t(PACKS[i].name()), x + 14, fy + 26, Theme.DIM);
		String price = PACKS[i].price();
		Gfx.text(c, price, x + w - 14 - Gfx.tw(price), fy + 18, Theme.TEXT);
		final int fi = i;
		host.region(x, y, w, h, () -> start(COINS, fi));
	}

	private static void crateCard(DrawContext c, int mx, int my, int x, int y, int w, Def d, int vy0, int vy1, long now, Host host) {
		int h = CRATE_ART + CRATE_FT;
		if (y + h + 6 < vy0 || y > vy1) {
			return;
		}
		boolean hov = in(mx, my, x, y, w, h) && my >= vy0 && my < vy1;
		cardFrame(c, x, y, w, h, hov, 0);
		int ax = x + 2, ay = y + 2, aw = w - 4, ah = CRATE_ART - 2;
		if (clip(c, ax, Math.max(ay, vy0), ax + aw, Math.min(ay + ah, vy1))) {
			int size = d == MIMIC ? 290 : 190;
			int cx = x + w / 2, cy = ay + ah / 2 + (d == MIMIC ? -10 : 2) + (hov ? -3 : Math.round((float) Math.sin(now / 520.0) * 2F));
			int frame = (int) ((now / 83L + 5L) % d.idleN);
			sprite(c, d.idleSheet, d.idleRows, frame, cx - size / 2, cy - size / 2, size + (hov ? 8 : 0), 1F);
			c.disableScissor();
		}
		int tx = x + 14;
		String kind = d.season == 0 ? Lang.tr("Classic", "Clássico")
				: Lang.tr("Limited", "Limitado") + "  ·  " + (d.season == Season.HALLOWEEN ? "Halloween" : Lang.tr("Christmas", "Natal"));
		Gfx.text(c, kind, tx, y + 14, d.season == 0 ? Theme.DIM : Theme.accent());
		int fy = y + CRATE_ART;
		c.fill(x + 14, fy, x + w - 14, fy + 1, Theme.CARD_BORDER);
		bigText(c, Lang.t(d.name), tx, fy + 9, Theme.TEXT, 1.15F, w - 200);
		String leg = Lang.t(RARITY[3]) + " " + d.odds[3] + "%";
		Gfx.text(c, leg, tx, fy + 26, Theme.DIM);
		rarityBar(c, tx + Gfx.tw(leg) + 8, fy + 28, 52, d);
		int bw = 104, bh = 26, bx = x + w - 14 - bw, by = fy + (CRATE_FT - bh) / 2;
		host.region(x, y, w, h, () -> start(d, -1));
		storeButton(c, mx, my, bx, by, bw, bh, Lang.tr("Open", "Abrir") + "  " + d.price, true, vy0, vy1, now, d.price * 3L, host, () -> start(d, -1));
		infoDot(c, mx, my, bx - 16, fy + CRATE_FT / 2, d, -1, vy0, vy1, host);
	}

	private static void heroBanner(DrawContext c, int mx, int my, int x, int y, int w, int h, Def d, int vy0, int vy1, long now, Host host) {
		if (y + h + 6 < vy0 || y > vy1) {
			return;
		}
		boolean hov = in(mx, my, x, y, w, h) && my >= vy0 && my < vy1;
		boolean xmas = d == PRESENT;
		int stripe = xmas ? 0xFFD64557 : 0xFF9B6BFF;
		// a flat card like every other one; the season colour lives in a soft glow behind the chest, not in a side bar
		cardFrame(c, x, y, w, h, hov, 0);
		if (clip(c, x + 2, Math.max(y + 2, vy0), x + w - 2, Math.min(y + h - 2, vy1))) {
			int gx = x + w * 76 / 100, gy = y + h / 2;
			for (int i = 0; i < 6; i++) {
				int r = 150 - i * 22;
				Gfx.rrect(c, gx - r, gy - r * 2 / 3, r * 2, r * 4 / 3, r * 2 / 3, (0x0C + i * 3 << 24) | (stripe & 0xFFFFFF));
			}
			c.disableScissor();
		}
		if (clip(c, x + 2, Math.max(y + 2, vy0), x + w - 2, Math.min(y + h - 2, vy1))) {
			int size = d == MIMIC ? 300 : 230;
			int cx = x + w * 76 / 100, cy = y + h / 2 + (d == MIMIC ? -16 : 6) + Math.round((float) Math.sin(now / 520.0) * 3F);
			int frame = (int) ((now / 83L) % d.idleN);
			sprite(c, d.idleSheet, d.idleRows, frame, cx - size / 2, cy - size / 2, size, 1F);
			c.disableScissor();
		}
		int tx = x + 30;
		String ev = xmas ? Lang.tr("Christmas event", "Evento de Natal") : Lang.tr("Halloween event", "Evento de Halloween");
		String left = seasonLeft();
		Gfx.text(c, ev, tx, y + 20, stripe);
		Gfx.text(c, "·  " + Lang.tr("Limited time", "Tempo limitado") + (left == null ? "" : "  ·  " + left), tx + Gfx.tw(ev) + 6, y + 20, Theme.DIM);
		bigText(c, Lang.t(d.name), tx, y + 38, Theme.TEXT, 1.9F, w / 2);
		String para = xmas ? Lang.tr("Unwrap the Christmas present for emotes, auras and cosmetics you won't find anywhere else.",
				"Abre o presente de Natal com emotes, auras e cosméticos que não encontras em mais lado nenhum.")
				: Lang.tr("It looks like loot. It isn't. Open the Halloween-only chest for emotes, auras and cosmetics you won't find anywhere else.",
						"Parece tesouro. Não é. Abre o baú só de Halloween com emotes, auras e cosméticos que não encontras em mais lado nenhum.");
		int ty = y + 68;
		for (String ln : wrap(para, w * 46 / 100, 3)) {
			Gfx.text(c, ln, tx, ty, Theme.DIM);
			ty += 12;
		}
		host.region(x, y, w, h, () -> start(d, -1));
		String cta = Lang.tr("Open for ", "Abrir por ") + d.price + " " + Lang.tr("coins", "moedas");
		int cw = Gfx.tw(cta) + 56;
		storeButton(c, mx, my, tx, y + 112, cw, 30, cta, true, vy0, vy1, now, 300L, host, () -> start(d, -1));
		// the chances as one quiet line
		int oy = y + h - 30, ox = tx;
		for (int r = 3; r >= 2; r--) {
			String t = Lang.t(RARITY[r]) + " " + d.odds[r] + "%";
			c.fill(ox, oy + 2, ox + 6, oy + 8, RARITY_COL[r]);
			Gfx.text(c, t, ox + 11, oy, Theme.DIM);
			ox += Gfx.tw(t) + 28;
		}
		infoDot(c, mx, my, x + w - 20, y + 20, d, -1, vy0, vy1, host);
	}

	private static final String[] PUMPKIN = {"....gg......", "..oooggooo..", ".oooooooooo.", "ooyooooooyoo", "oyyyooooyyyo", "oooooyyooooo", "oyoyoyoyoyoo", "ooyyyyyyyyoo", ".oooooooooo.", "..oooooooo.."};
	private static final String[] TOMB = {".cccccccc.", "cccccddccc", "cccdddddcc", "cccccddccc", "cccccddccc", "cccccccccc", "cccccccccc", "cccccccccc", "gggggggggg"};
	private static final String[] CROSS = {"...cc...", "...cc...", "cccccccc", "cccccccc", "...cc...", "...cc...", "...cc...", "...cc...", "gggggggg"};
	private static final String[] BAT0 = {"bb........bb", "bbbb.bb.bbbb", ".bbbbbbbbbb.", "...brbbrb..."};
	private static final String[] BAT1 = {"..bb....bb..", "..bbb.bbbb..", "...bbbbbbb..", "...brbbrbb.."};

	private static int pix(char ch) {
		return switch (ch) {
			case 'o' -> 0xFFE8731A;
			case 'y' -> 0xFFFFD34D;
			case 'g' -> 0xFF3E7A22;
			case 'c' -> 0xFF6E6A86;
			case 'd' -> 0xFF8E8AA8;
			case 'b' -> 0xFF14101C;
			case 'r' -> 0xFFFF4D4D;
			default -> 0;
		};
	}

	private static void pixels(DrawContext c, String[] rows, int x, int y, int k, int vy0, int vy1) {
		for (int r = 0; r < rows.length; r++) {
			int yy = y + r * k;
			if (yy + k < vy0 || yy > vy1) {
				continue;
			}
			for (int q = 0; q < rows[r].length(); q++) {
				int col = pix(rows[r].charAt(q));
				if (col != 0) {
					c.fill(x + q * k, yy, x + q * k + k, yy + k, col);
				}
			}
		}
	}

	/** A Halloween strip under the crates, like the Laby shop: dark sky fading to orange, a sloped graveyard, pumpkins, bats. */
	private static void halloweenScene(DrawContext c, int x, int y, int w, int h, long now, int vy0, int vy1) {
		if (y + h < vy0 || y > vy1) {
			return;
		}
		Gfx.rrect(c, x, y, w, h, 6, 0xFF120E1A);
		Gfx.gradientV(c, x + 1, y + h / 3, w - 2, h - h / 3 - 1, 0x00FF5A1A, 0x88FF5A1A);
		for (int i = 0; i < 6; i++) {
			c.fill(x + 8 + (i * 137) % Math.max(10, w - 16), y + 8 + (i * 41) % 40, x + 10 + (i * 137) % Math.max(10, w - 16), y + 10 + (i * 41) % 40, 0x55FFFFFF);
		}
		int top0 = y + h - 52, top1 = y + h - 20;
		for (int px = 0; px < w - 2; px += 2) {
			int gy = top0 + (top1 - top0) * px / Math.max(1, w);
			c.fill(x + 1 + px, gy, x + 3 + px, y + h - 1, 0xFF2A2140);
			c.fill(x + 1 + px, gy, x + 3 + px, gy + 3, 0xFF4A3A70);
		}
		int[] at = {6, 21, 36, 62, 80};
		String[][] kind = {PUMPKIN, CROSS, PUMPKIN, TOMB, PUMPKIN};
		int[] sc = {3, 3, 2, 3, 3};
		for (int i = 0; i < at.length; i++) {
			int ox = x + w * at[i] / 100;
			int gy = top0 + (top1 - top0) * (ox - x) / Math.max(1, w) + 2;
			int sh = kind[i].length * sc[i];
			if (kind[i] == PUMPKIN) {
				Gfx.circle(c, ox + 6 * sc[i], gy - sh / 2, sh * 0.9F, 0x22FFA030);
			}
			pixels(c, kind[i], ox, gy - sh, sc[i], vy0, vy1);
		}
		for (int i = 0; i < 3; i++) {
			int bx = x + (int) ((now / 28L + i * 211L) % (w + 60)) - 30;
			int by = y + 20 + i * 14 + (int) (Math.sin(now / 400.0 + i * 2) * 6);
			if (bx >= x && bx + 26 <= x + w) {
				pixels(c, ((now / 170L + i) & 1) == 0 ? BAT0 : BAT1, bx, by, 2, vy0, vy1);
			}
		}
	}

	private static void bigText(DrawContext c, String s, int x, int y, int color, float k, int maxW) {
		float kk = Math.min(k, maxW / (float) Math.max(1, Gfx.tw(s)));
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(kk, kk);
		Gfx.text(c, s, 0, 0, color);
		m.popMatrix();
	}

	/** A slanted band of light that runs across a rectangle now and then (the "shine" on buttons). */
	private static void shine(DrawContext c, int x, int y, int w, int h, long now, long period, int band, int alpha, long offset) {
		float t = ((now + offset) % period) / (float) period;
		float run = 0.42F;
		if (t > run) {
			return;
		}
		float travel = w + h * 0.6F + band * 2F;
		float p = t / run * travel - band;
		for (int r = 0; r < h; r += 2) {
			float cx = x + p - r * 0.6F;
			int x1 = Math.max(x, Math.round(cx)), x2 = Math.min(x + w, Math.round(cx + band));
			if (x2 > x1) {
				c.fill(x1, y + r, x2, Math.min(y + h, y + r + 2), (alpha / 3 << 24) | 0xFFFFFF);
			}
			int k1 = Math.max(x, Math.round(cx + band * 0.3F)), k2 = Math.min(x + w, Math.round(cx + band * 0.7F));
			if (k2 > k1) {
				c.fill(k1, y + r, k2, Math.min(y + h, y + r + 2), (alpha << 24) | 0xFFFFFF);
			}
		}
	}

	/** The "starshine" glitter: 8-point stars at fixed random spots that grow, spin and fade in and out (6 s cycle, random delays). */
	private static void starshine(DrawContext c, int x, int y, int w, int h, int count, int seed, long now) {
		Identifier star = Gfx.id("textures/gui/crates/sparkle.png");
		int x0 = x + w * 35 / 100, rw = Math.max(10, w - w * 35 / 100 - 10), ry = Math.max(10, h - 20);
		for (int k = 0; k < count; k++) {
			long hsh = (k * 2654435761L + seed * 40503L + 12345L) & 0x7FFFFFFFL;
			float fx = (hsh % 1000L) / 1000F, fy = ((hsh / 1000L) % 1000L) / 1000F, fd = ((hsh / 1000000L) % 1000L) / 1000F;
			int base = k % 2 == 0 ? 14 : k % 3 == 0 ? 20 : 30;
			float ph = (((now + (long) (fd * 20000L)) % 6000L)) / 6000F;
			float sc, rot, op;
			if (ph < 0.25F) {
				float u = ph / 0.25F;
				sc = 0.3F + 0.7F * u;
				rot = 360F * u;
				op = u;
			} else if (ph < 0.5F) {
				float u = (ph - 0.25F) / 0.25F;
				sc = 1F - 0.7F * u;
				rot = 360F + 360F * u;
				op = 1F - u;
			} else {
				continue;
			}
			int size = Math.max(2, Math.round(base * sc));
			int sx = x0 + Math.round(fx * rw), sy = y + 10 + Math.round(fy * ry);
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(sx, sy);
			m.rotate((float) Math.toRadians(rot));
			Gfx.tex(c, star, -size / 2, -size / 2, size, size, 128, 128, ((int) (0xFF * op) << 24) | 0xFFFFFF);
			m.popMatrix();
		}
	}

	// --------------------------------------------------------------------------------------------- info: the chances
	static void drawInfo(DrawContext c, int mx, int my, int x, int y, int w, int h, Host host) {
		if (infoDef == null) {
			return;
		}
		Def d = infoDef;
		c.fill(x, y, x + w, y + h, 0xB0000000);
		host.region(x, y, w, h, () -> infoDef = null);
		int pw = 290, ph = d.coinPack ? 118 : 178;
		int px = x + w / 2 - pw / 2, py = y + h / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 9, 0xFF15161B, Theme.CARD_BORDER);
		host.region(px, py, pw, ph, () -> { });
		String title = d.coinPack && infoPack >= 0 ? PACKS[infoPack].name() : d.name;
		Gfx.text(c, Lang.t(title), px + 14, py + 12, 0xFFFFFFFF);
		int cx = px + pw - 26, cy = py + 8;
		boolean hx = in(mx, my, cx, cy, 18, 18);
		Gfx.rrect(c, cx, cy, 18, 18, 4, hx ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
		Gfx.textC(c, "x", cx + 9, cy + 5, hx ? Theme.RED : Theme.TEXT);
		host.region(cx, cy, 18, 18, () -> infoDef = null);
		if (d.coinPack) {
			long n = infoPack >= 0 ? PACKS[infoPack].coins() : 0;
			Gfx.text(c, Lang.tr("You get", "Recebes") + " " + String.format(Locale.ROOT, "%,d", n).replace(',', ' ') + " " + Lang.tr("coins", "moedas"), px + 14, py + 40, 0xFFFFC53D);
			Gfx.textClip(c, Lang.tr("Bought on the website. It never gives items.", "Compra-se no site. Nunca dá itens."), px + 14, py + 58, pw - 28, Theme.DIM);
			Gfx.textClip(c, Lang.tr("Coins can't be turned back into money.", "As moedas não se trocam por dinheiro."), px + 14, py + 72, pw - 28, Theme.DIM);
			return;
		}
		Gfx.text(c, Lang.tr("Drop chances", "Probabilidades"), px + 14, py + 32, Theme.DIM);
		int by = py + 50;
		for (int i = 0; i < 4; i++) {
			Gfx.circle(c, px + 18, by + 4, 3.5F, RARITY_COL[i]);
			Gfx.text(c, Lang.t(RARITY[i]), px + 28, by, Theme.TEXT);
			int barX = px + 98, barW = pw - 98 - 52;
			Gfx.rrect(c, barX, by + 1, barW, 6, 2, 0x22FFFFFF);
			Gfx.rrect(c, barX, by + 1, Math.max(3, Math.round(barW * d.odds[i] / 60F)), 6, 2, RARITY_COL[i]);
			String pct = d.odds[i] + "%";
			Gfx.text(c, pct, px + pw - 14 - Gfx.tw(pct), by, Theme.TEXT);
			by += 18;
		}
		Gfx.textClip(c, Lang.tr("Cosmetics only. A duplicate gives coins back.", "Só cosméticos. Um repetido devolve moedas."), px + 14, py + ph - 22, pw - 28, 0xFF565B6B);
	}

	// --------------------------------------------------------------------------------------------- light (rays + glow)
	private static final float[] RAY_H = {170, 100, 170, 120, 140, 160, 180, 120, 80, 190};
	private static final float[] RAY_W = {30, 8, 50, 14, 30, 40, 10, 30, 10, 23};
	private static final float[] RAY_A = {180, 220, 250, 305, -15, 30, 70, 100, 120, 150};

	/** Soft glow plus slowly swinging rays centred on (cx, cy). {@code power} 0..1 scales how strong it is. */
	private static void light(DrawContext c, int cx, int cy, int size, int colA, int colB, long now, float spin, float power) {
		float k = 0.3F + power * 0.7F;
		Identifier glow = Gfx.id("textures/gui/season/glow.png");
		int r1 = Math.round(size * 1.25F), r2 = Math.round(size * 0.95F), r3 = Math.round(size * 0.6F);
		Gfx.tex(c, glow, cx - r1, cy - r1, r1 * 2, r1 * 2, 64, 64, ((int) (0x48 * k) << 24) | (colB & 0xFFFFFF));
		Gfx.tex(c, glow, cx - r2, cy - r2, r2 * 2, r2 * 2, 64, 64, ((int) (0x55 * k) << 24) | (colA & 0xFFFFFF));
		Gfx.tex(c, glow, cx - r3, cy - r3, r3 * 2, r3 * 2, 64, 64, ((int) (0x60 * k) << 24) | 0xFFFFFF);
		float rs = size / 240F, t = now / 1000F;
		float sway = (float) Math.sin(t * 0.6F) * (float) Math.toRadians(3) + t * spin * 0.15F;
		var m = c.getMatrices();
		for (int i = 0; i < RAY_A.length; i++) {
			float ang = (float) Math.toRadians(RAY_A[i]) + sway;
			float rw = Math.max(3F, RAY_W[i] * rs), rh = RAY_H[i] * rs, dist = size * 0.5F;
			int a = (int) (0x58 * k);
			m.pushMatrix();
			m.translate(cx, cy);
			m.rotate(ang);
			m.translate(0, dist);
			int x1 = Math.round(-rw / 2), x2 = Math.round(rw / 2);
			int y1 = Math.round(-rh / 2), y2 = Math.round(rh / 2);
			Gfx.gradientV(c, x1, y1, Math.max(1, x2 - x1), -y1, 0x00FFFFFF, (a << 24) | 0xFFFFFF);
			Gfx.gradientV(c, x1, 0, Math.max(1, x2 - x1), y2, (a << 24) | 0xFFFFFF, 0x00FFFFFF);
			m.popMatrix();
		}
	}

	/** Strong rotating burst used behind the prize. */
	private static void burst(DrawContext c, int cx, int cy, int radius, int col, float alpha, long now) {
		var m = c.getMatrices();
		float t = now / 1000F;
		int n = 14;
		for (int i = 0; i < n; i++) {
			float ang = (float) (i * Math.PI * 2 / n) + t * 0.35F;
			float len = radius * (i % 2 == 0 ? 1F : 0.72F);
			int wd = Math.max(6, radius / 9);
			int a = (int) (0x70 * alpha);
			m.pushMatrix();
			m.translate(cx, cy);
			m.rotate(ang);
			Gfx.gradientV(c, -wd / 2, 0, wd, Math.round(len), (a << 24) | (col & 0xFFFFFF), (col & 0xFFFFFF));
			m.popMatrix();
		}
	}

	// --------------------------------------------------------------------------------------------- sprites
	private static final int SPIN_N = 32;

	/** Draws the frame at float position {@code fpos} of the sheet, blending it with the next one so the motion is smooth. */
	private static void smoothSprite(DrawContext c, Def d, float fpos, int spinStart, int x, int y, int size) {
		int fa = (int) Math.floor(fpos);
		float frac = fpos - fa;
		int fb;
		if (fa >= spinStart) {
			fb = spinStart + ((fa - spinStart + 1) % SPIN_N);
		} else {
			fb = Math.min(fa + 1, d.openOff + d.openN - 1);
		}
		sprite(c, d.openSheet, d.openRows, fa, x, y, size, 1F);
		if (frac > 0.02F && fb != fa) {
			sprite(c, d.openSheet, d.openRows, fb, x, y, size, frac);
		}
	}

	private static void sprite(DrawContext c, String sheet, int rows, int frame, int x, int y, int size, float alpha) {
		if (alpha <= 0.01F) {
			return;
		}
		int col = frame % COLS, row = frame / COLS;
		Gfx.texUv(c, Gfx.id("textures/gui/crates/" + sheet + ".png"), x, y, size, size, col * (float) SHEET, row * (float) SHEET, SHEET, SHEET, SHEET * COLS, SHEET * rows,
				((int) (255 * Math.min(1F, alpha)) << 24) | 0xFFFFFF);
	}

	// --------------------------------------------------------------------------------------------- opening
	private static void start(Def d, int pack) {
		if (stage != BROWSE) {
			return;
		}
		if (d.coinPack) {
			if (!WEBSITE_BUY_URL.isEmpty()) {
				net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(WEBSITE_BUY_URL));
				return;
			}
			curPack = Math.max(0, pack);
		} else if (Social.me == null) {
			Social.say("Log in to Sleep to open crates", true);
			return;
		}
		cur = d;
		stage = OPENING;
		started = false;
		stageAt = System.currentTimeMillis();
		result = null;
		if (d.coinPack) {
			Social.CrateResult r = new Social.CrateResult();
			r.ok = true;
			r.demo = true;
			r.coinsWon = PACKS[curPack].coins();
			r.rarity = curPack == 2 ? "legendary" : curPack == 1 ? "epic" : "rare";
			result = r;
		} else {
			final Def dd = d;
			Social.crateOpen(d.id, r -> {
				if (r.ok && r.demo) {
					rollLocal(dd, r);
				}
				result = r;
			});
		}
	}

	/** Preview roll when the site has no crates: odds like the crate's, prize from the shop items the client knows. */
	private static void rollLocal(Def d, Social.CrateResult r) {
		Random rnd = new Random();
		int roll = rnd.nextInt(100), acc = 0, tier = 0;
		for (int i = 0; i < 4; i++) {
			acc += d.odds[i];
			if (roll < acc) {
				tier = i;
				break;
			}
		}
		r.rarity = RARITY[tier].toLowerCase(Locale.ROOT);
		List<Social.Item> all = new ArrayList<>();
		List<Social.Item> inTier = new ArrayList<>();
		for (Social.Item it : Social.catalogue()) {
			if (it.name == null || it.id == null || it.category == null || it.category.equalsIgnoreCase("Bundles")) {
				continue;
			}
			all.add(it);
			long p = it.finalPrice;
			int t = p < 150 ? 0 : p < 400 ? 1 : p < 800 ? 2 : 3;
			if (t == tier) {
				inTier.add(it);
			}
		}
		List<Social.Item> from = !inTier.isEmpty() ? inTier : all;
		if (from.isEmpty()) {
			r.coinsWon = new long[]{50, 150, 400, 1000}[tier];
		} else {
			r.item = from.get(rnd.nextInt(from.size()));
			r.duplicate = r.item.owned;
		}
	}

	private static int rarityIndex(String s) {
		if (s != null) {
			for (int i = 0; i < 4; i++) {
				if (RARITY[i].equalsIgnoreCase(s)) {
					return i;
				}
			}
		}
		return 0;
	}

	/** Full-screen opening / reveal, drawn on top of the menu. Does nothing while browsing. */
	static void drawOverlay(DrawContext c, int mx, int my, int vw, int vh, Host host) {
		if (stage == BROWSE || cur == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Def d = cur;
		c.fill(0, 0, vw, vh, 0xC8000000);
		host.region(0, 0, vw, vh, Crates::escape); // blocks everything behind; only the prize screen closes on click

		Social.CrateResult res = result;
		if (res != null && !res.ok) {
			Social.say(res.error == null ? "Could not open the crate" : res.error, true);
			stage = BROWSE;
			return;
		}
		if (res == null) {
			Gfx.textC(c, Lang.tr("Opening...", "A abrir..."), vw / 2, vh / 2 - 4, Theme.DIM);
			return;
		}
		if (!started) {
			started = true;
			stageAt = now; // the scene (and the sound) start together, when the site has answered
			if (d.coinPack) {
				SoundLib.play("sol_client:crate_coins", 1F, 1F);
				duck(8200L);
			} else {
				SoundLib.play("sol_client:crate_open", 1F, 1F);
				duck(OPEN_SOUND_MS);
			}
		}

		int size = Math.max(110, Math.min(Math.min(Math.min(vh - 60, vw / 2), 300), Math.round(vh * 0.62F)));
		int cx = vw / 2, cy = vh / 2 - 6;
		long el = now - stageAt;
		long openMs = d.openN * FRAME_MS, openStart = d.revealMs - openMs;
		// choreography: the chest spins smoothly (slow, fast, slow), crouches, jumps and the lid pops open when it lands
		long crouchAt = openStart - 180L, crouchMs = 160L, airMs = 620L;
		long spinEnd = crouchAt - 80L, landAt = crouchAt + crouchMs + airMs;
		int spinStart = d.openOff + d.openN;
		float fpos;
		float prog;
		float jump = 0F, squash = 1F;
		int rarity = rarityIndex(res.rarity);
		if (stage == OPENING) {
			if (el < spinEnd) {
				float t = Math.max(0F, el / (float) spinEnd);
				float e = t * t * t * (t * (t * 6F - 15F) + 10F);
				fpos = spinStart + (e * 4F * SPIN_N) % SPIN_N;
				prog = 0.15F + 0.55F * t;
			} else {
				prog = 0.8F;
				if (el < openStart) {
					fpos = spinStart;
				} else {
					fpos = d.openOff + Math.min(d.openN - 1F, (el - openStart) / (float) FRAME_MS);
					prog = 1F;
				}
				if (el < crouchAt + crouchMs) {
					float k = Math.max(0F, (el - crouchAt) / (float) crouchMs);
					squash = 1F - 0.13F * (float) Math.sin(k * Math.PI / 2);
				} else if (el < landAt) {
					float u = (el - crouchAt - crouchMs) / (float) airMs;
					jump = 4F * u * (1F - u);
					squash = 1F + 0.07F * (float) Math.sin(u * Math.PI);
				} else if (el < landAt + 170L) {
					float k = (el - landAt) / 170F;
					squash = 0.88F + 0.12F * k;
				}
			}
			if (el >= d.revealMs) {
				stage = REVEAL;
				stageAt = now;
				if (d.coinPack) {
					SoundLib.play("sol_client:crate_ching", 1F, 1F);
					duck(2400L);
				}
			}
		} else {
			fpos = d.openOff + d.openN - 1;
			prog = 1F;
		}
		if (stage == REVEAL) {
			fpos = d.openOff + d.openN - 1;
		}
		int lightA = stage == REVEAL ? RARITY_COL[rarity] : d.glowA;
		int lightB = stage == REVEAL ? Gfx.blend(RARITY_COL[rarity], 0xFFFFFFFF, 0.35F) : d.glowB;
		int spr = Math.round((stage == REVEAL ? size * 0.72F : size) * (d == MIMIC ? 1.45F : 1F)); // the mimic's frames have more empty space
		int sy = cy + (stage == REVEAL ? Math.round(size * 0.22F) : 0);
		light(c, cx, cy, size, lightA, lightB, now, 1.2F, 0.2F + prog * 0.8F);
		if (stage == REVEAL) {
			float rp = Math.min(1F, (now - stageAt) / 500F);
			burst(c, cx, cy - size / 8, Math.round(size * (0.55F + 0.35F * rp)), lightA, rp, now);
		}
		// the shadow under the chest shrinks while it is in the air
		int baseY = sy + Math.round(spr * 0.30F);
		int shw = Math.round(spr * 0.62F * (1F - 0.35F * jump));
		Gfx.tex(c, Gfx.id("textures/gui/season/glow.png"), cx - shw / 2, baseY - 6, shw, 14, 64, 64, ((int) (0x88 * (1F - 0.4F * jump)) << 24));
		var mt = c.getMatrices();
		mt.pushMatrix();
		mt.translate(cx, baseY - jump * size * 0.30F);
		mt.scale(1F + (1F - squash) * 0.6F, squash);
		int topRel = -(int) (spr * 0.5F) - Math.round(spr * 0.30F);
		smoothSprite(c, d, fpos, spinStart, -spr / 2, topRel, spr);
		mt.popMatrix();

		if (stage == OPENING) {
			Gfx.textC(c, Lang.t(d.coinPack ? PACKS[curPack].name() : d.name), cx, cy - size / 2 - 16, 0xFFFFFFFF);
			return;
		}
		drawReveal(c, mx, my, vw, vh, cx, cy, size, d, res, rarity, now, host);
	}

	private static void drawReveal(DrawContext c, int mx, int my, int vw, int vh, int cx, int cy, int size, Def d, Social.CrateResult res, int rarity, long now, Host host) {
		float p = Math.min(1F, (now - stageAt) / 750F);
		float e = 1F - (1F - p) * (1F - p) * (1F - p);
		int col = RARITY_COL[rarity];
		int cw = 210, ch = 150;
		float cardCy = cy - size * 0.36F;
		float fromCy = cy + size * 0.2F;
		float ccy = fromCy + (cardCy - fromCy) * e;
		float sc = 0.25F + 0.75F * e;
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(cx, ccy);
		m.scale(sc, sc);
		int x = -cw / 2, y = -ch / 2;
		Gfx.rrectBorder(c, x, y, cw, ch, 10, 0xFF15161B, col);
		Gfx.rrect(c, x + 1, y + 1, cw - 2, 20, 9, Gfx.blend(0xFF15161B, col, 0.35F));
		Gfx.textC(c, Lang.t(RARITY[rarity]).toUpperCase(Locale.ROOT), 0, y + 7, 0xFFFFFFFF);
		Social.Item it = res.item;
		if (it != null) {
			itemImage(c, it, -36, y + 28, 72, 72, col);
			Gfx.textFit(c, it.name, 0, cw - 16, y + 106, Theme.TEXT);
			String sub = res.duplicate ? Lang.tr("Duplicate: +" + res.refund + " coins back", "Repetido: +" + res.refund + " moedas de volta")
					: Lang.tr("New in your locker", "Novo no teu cacifo") + (it.category == null ? "" : "  ·  " + Lang.t(it.category));
			Gfx.textFit(c, sub, 0, cw - 16, y + 120, res.duplicate ? 0xFFFFC53D : Theme.DIM);
		} else {
			Gfx.coin(c, -28, y + 34, 56);
			Gfx.textC(c, "+" + String.format(Locale.ROOT, "%,d", res.coinsWon).replace(',', ' ') + " " + Lang.tr("coins", "moedas"), 0, y + 104, 0xFFFFC53D);
			Gfx.textC(c, d.coinPack ? Lang.t(PACKS[curPack].name()) : Lang.tr("Coins", "Moedas"), 0, y + 118, Theme.DIM);
		}
		m.popMatrix();

		// sparkles
		Random rnd = new Random(stageAt);
		for (int i = 0; i < 26; i++) {
			float ang = rnd.nextFloat() * 6.2832F, sp = 0.5F + rnd.nextFloat();
			float dist = size * 0.9F * sp * e * (0.6F + 0.4F * (float) Math.sin(now / 400.0 + i));
			int px = Math.round(cx + (float) Math.cos(ang) * dist), py = Math.round(cardCy + (float) Math.sin(ang) * dist * 0.7F);
			int sz = 2 + (i % 3);
			int a = (int) (200 * (1F - 0.5F * e) * (0.5F + 0.5F * (float) Math.sin(now / 150.0 + i * 1.7)));
			c.fill(px, py, px + sz, py + sz, (Math.max(0, a) << 24) | (i % 2 == 0 ? col & 0xFFFFFF : 0xFFFFFF));
		}

		if (p < 1F) {
			return;
		}
		int by = vh - 34 - (res.demo ? 16 : 0);
		if (res.demo) {
			String note = d.coinPack
					? Lang.tr("Preview: coin chests open on the website soon. Nothing was added to your account.", "Pré-visualização: os baús de moedas abrem no site em breve. Nada foi adicionado à tua conta.")
					: Lang.tr("Preview: the site has no crates yet. Nothing was charged or given.", "Pré-visualização: o site ainda não tem caixas. Nada foi cobrado nem dado.");
			Gfx.textC(c, note, cx, by, 0xFFFFC53D);
			by += 14;
		}
		boolean canEquip = it != null && it.wearable() && !res.demo;
		int bw = 130, gap = 8;
		int n = 2 + (canEquip ? 1 : 0);
		int bx = cx - (n * bw + (n - 1) * gap) / 2;
		if (canEquip) {
			final Social.Item fi = it;
			smallButton(c, mx, my, bx, by, bw, 22, Lang.tr("Equip", "Equipar"), true, host, () -> Social.equip(fi, true));
			bx += bw + gap;
		}
		String again = d.coinPack ? Lang.tr("Back", "Voltar") : Lang.tr("Open another", "Abrir outra") + "  ·  " + d.price;
		final Def dd = d;
		final int pk = curPack;
		smallButton(c, mx, my, bx, by, bw, 22, again, false, host, () -> {
			stage = BROWSE;
			if (!dd.coinPack) {
				start(dd, pk);
			}
		});
		bx += bw + gap;
		smallButton(c, mx, my, bx, by, bw, 22, Lang.tr("Close", "Fechar"), false, host, () -> stage = BROWSE);
	}

	private static void smallButton(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Host host, Runnable click) {
		boolean hov = in(mx, my, x, y, w, h);
		if (primary) {
			Gfx.rrect(c, x, y, w, h, 5, hov ? Theme.accentHover() : Theme.accent());
		} else {
			Gfx.button(c, x, y, w, h, hov, false);
		}
		Gfx.textFit(c, label, x + w / 2, w - 8, y + (h - 8) / 2, 0xFFFFFFFF);
		host.region(x, y, w, h, click);
	}

	private static void itemImage(DrawContext c, Social.Item it, int px, int py, int pw, int ph, int rarityCol) {
		Gfx.rrect(c, px, py, pw, ph, 5, Gfx.blend(0xFF1B1D24, rarityCol, 0.16F));
		RemoteTextures.Tex t = RemoteTextures.get(it.imageUrl);
		if (t != null) {
			float sc = Math.min((pw - 8) / (float) t.width(), (ph - 8) / (float) t.height());
			int dw = Math.max(1, Math.round(t.width() * sc)), dh = Math.max(1, Math.round(t.height() * sc));
			Gfx.tex(c, t.id(), px + (pw - dw) / 2, py + (ph - dh) / 2, dw, dh, t.width(), t.height(), 0xFFFFFFFF);
		} else {
			Gfx.circle(c, px + pw / 2F, py + ph / 2F, Math.min(pw, ph) / 4F, rarityCol);
			String ini = it.name == null || it.name.isEmpty() ? "?" : it.name.substring(0, 1).toUpperCase(Locale.ROOT);
			Gfx.textC(c, ini, px + pw / 2, py + ph / 2 - 4, 0xFFFFFFFF);
		}
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
