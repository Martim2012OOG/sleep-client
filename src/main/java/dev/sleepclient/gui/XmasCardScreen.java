package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.social.Social;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;

/**
 * Christmas card: the first time you start the game between 1 November and 31 December, a red knitted postcard drops in.
 * Click it and it opens like a real card: a message from Santa on the right, a free Christmas hat on the left.
 * The hat is added to your Locker (Headwear) when the card opens; the button wears it.
 */
final class XmasCardScreen extends UiScreen {

	private static final String HAT = "christmas_hat";
	private static final int W = 170, H = 236;
	private static final int RED = 0xFFC72320, KNIT = 0xFFFFF4F0, PAPER = 0xFFF3E9D8, PAPER2 = 0xFFE8DCC6, INK = 0xFF3A2A22, GREEN = 0xFF1F7A3E;
	private static final int COLS = 34, ROWS = 47;
	private static boolean[][] knit;

	private final Screen parent;
	private long openAt;
	private boolean clicked, opened, given, threeD = true;
	private long clickAt, drumMs, unlockAt;
	private static final long DRUMS_FALLBACK = 4700L, AWARD_FALLBACK = 4200L;
	private long closeAt;

	private XmasCardScreen(Screen parent) {
		super("Christmas card");
		this.parent = parent;
		SeasonMusic.preloadCue("drums"); // decoded now, so the drum roll starts the moment you click
		SeasonMusic.preloadCue("award");
	}

	// ------------------------------------------------------------------ when to show it
	private static Path flag() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("xmas-card.txt");
	}

	/** The card once per Christmas season (1 Nov - 31 Dec), the first time the game starts in it. Otherwise {@code home}. */
	static Screen maybeShow(Screen home) {
		try {
			LocalDate d = LocalDate.now();
			if (d.getMonthValue() < 11) {
				return home;
			}
			String key = String.valueOf(d.getYear());
			Path f = flag();
			if (Files.exists(f) && Files.readString(f).strip().equals(key)) {
				return home;
			}
			Files.createDirectories(f.getParent());
			Files.writeString(f, key); // marked at once: it never comes back, even if you close it right away (the hat is given when it opens)
			return new XmasCardScreen(home);
		} catch (Exception e) {
			return home;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void close() {
		mc.setScreen(parent);
	}

	@Override
	public void removed() {
		if (clicked && !opened) {
			SeasonMusic.release(); // closed during the drum roll: the music comes back at once
		}
	}

	@Override
	public boolean keyPressed(KeyInput k) {
		if (k.key() == 256) {
			close();
			return true;
		}
		return super.keyPressed(k);
	}

	// ------------------------------------------------------------------ drawing
	private static float ease(float t) {
		t = Math.max(0F, Math.min(1F, t));
		return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	@Override
	protected void draw(DrawContext c, float delta) {
		long now = System.currentTimeMillis();
		Season.background(c, width, height);
		c.fill(0, 0, width, height, 0xB0070A12);
		Season.snow(c, width, height);

		dev.sleepclient.skin.SkinChanger.myPreview(); // start loading your skin early
		if (clicked && !opened && now >= unlockAt) {
			unlockCard(now); // the drum roll is over: the card opens and The Award plays
		}
		float drop = ease((now - openedAt) / 900F);
		float t = opened ? ease((now - openAt) / 1300F) : 0F;
		int cx = width / 2, cy = height / 2 + 6;
		float tilt = (float) Math.toRadians(-10) * (1F - t);
		if (clicked && !opened) { // drum roll: the card trembles more and more
			float k = Math.min(1F, (now - clickAt) / (float) Math.max(1L, unlockAt - clickAt));
			tilt += (float) Math.sin(now / 38.0) * (0.012F + 0.05F * k);
		}
		float x0 = cx - W / 2F + t * W / 2F;
		float y0 = cy - H / 2F - (1F - drop) * height * 0.7F;
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(cx, cy);
		m.rotate(tilt);
		m.translate(-cx, -cy);

		// soft shadow on the "table"
		Gfx.rrectFree(c, x0 - 4 - (t * W / 2F), y0 + 8, W + 8 + t * W, H + 4, 8, 0x55000000);

		// inside right page (message), always under the front
		page(c, x0, y0, 1F, false, t);
		float a = (float) (t * Math.PI);
		float sx = (float) Math.cos(a);
		if (Math.abs(sx) > 0.02F) {
			if (sx > 0) {
				page(c, x0, y0, sx, true, t); // the front (red, knitted)
			} else {
				page(c, x0 - W * -sx, y0, -sx, false, t, true); // its inside (the hat), swung to the left
			}
		}
		m.popMatrix();

		if (t >= 1F && threeD) {
			hat3d(c, x0 - W / 2F, y0);
		}
		if (!opened) {
			float bob = (float) Math.sin(now / 300.0) * 3F;
			if (clicked) {
				String dots = ".".repeat(1 + (int) ((now / 350) % 3));
				Gfx.textC(c, "Drumroll" + dots, cx, Math.round(y0 + H + 16), 0xFFFFD23B);
				return;
			}
			if (drop >= 1F) {
				String s = "Click the card to open it";
				Gfx.textC(c, s, cx, Math.round(y0 + H + 16 + bob), 0xFFFFFFFF);
				Gfx.rrectFree(c, cx - 3, y0 + H + 7 + bob, 6, 6, 3, 0xFFFFD23B);
			}
			int fx = Math.round(cx - W / 2F), fy = Math.round(y0);
			region(fx, fy, W, H, this::open);
			return;
		}
		if (t >= 1F) {
			int bw = 120, bh = 22, bx = Math.round(x0 + W / 2F - bw / 2F), by = Math.round(y0 + H - 34);
			if (!given) {
				button(c, "wear", bx, by, bw, bh, "Wear the hat", this::wear);
			} else {
				button(c, "done", bx, by, bw, bh, "Close", this::close);
			}
			if (closeAt != 0 && now > closeAt) {
				close();
			}
		}
	}

	/**
	 * The hat in real 3D, on your own skin (like the cosmetic in the Locker / home screen). Drawn in screen units, so only once the card
	 * is fully open and still. Falls back to the flat picture when your skin is not loaded yet.
	 */
	private void hat3d(DrawContext c, float pageCx, float y0) {
		var tex = dev.sleepclient.skin.SkinChanger.myPreview();
		var def = dev.sleepclient.cosmetic.CosmeticModels.find(HAT);
		if (tex == null || def == null) {
			return; // the flat hat stays
		}
		float u = 3.4F, top = y0 + 56F;
		float yaw = -22F + (float) Math.sin(System.currentTimeMillis() / 1100.0) * 28F;
		c.enableScissor(Math.round(pageCx - W / 2F + 4), Math.round(top - 14), Math.round(pageCx + W / 2F - 4), Math.round(top + 112));
		boolean ok = dev.sleepclient.cosmetic.SkinPreview.draw(c, tex, dev.sleepclient.skin.SkinChanger.selfSlim(), pageCx * ui, (top + 32 * u) * ui, u * ui, yaw, 8F,
				java.util.List.of(def));
		c.disableScissor();
		if (!ok) {
			threeD = false;
		}
		have3d = ok;
	}

	private boolean have3d;

	/** Click: everything goes quiet and the drum roll plays; when it ends the card opens (see {@link #unlockCard}). */
	private void open() {
		if (clicked) {
			return;
		}
		clicked = true;
		clickAt = System.currentTimeMillis();
		SeasonMusic.hold(DRUMS_FALLBACK + AWARD_FALLBACK + 900L);
		long ms = SeasonMusic.playCue("drums");
		drumMs = ms > 0 ? ms : DRUMS_FALLBACK;
		unlockAt = clickAt + drumMs;
	}

	private void unlockCard(long now) {
		opened = true;
		openAt = now;
		Social.gift(HAT); // the hat is yours from this moment
		long ms = SeasonMusic.playCue("award");
		SeasonMusic.hold((ms > 0 ? ms : AWARD_FALLBACK) + 300L); // when it ends the menu music comes back
	}

	private void wear() {
		var it = Social.gift(HAT);
		if (it != null) {
			Social.equip(it, true);
		}
		given = true;
		closeAt = System.currentTimeMillis() + 1400L;
	}

	/** One page of the card, scaled in width (the swing of the cover). */
	private void page(DrawContext c, float x, float y, float sx, boolean front, float t) {
		page(c, x, y, sx, front, t, false);
	}

	private void page(DrawContext c, float x, float y, float sx, boolean front, float t, boolean left) {
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(sx, 1F);
		if (front) {
			knitted(c);
		} else if (left) {
			hatPage(c);
		} else {
			messagePage(c);
		}
		m.popMatrix();
	}

	private static void ensureKnit() {
		if (knit != null) {
			return;
		}
		knit = new boolean[ROWS][COLS];
		for (int j = 0; j < ROWS; j++) {
			for (int i = 0; i < COLS; i++) {
				boolean on = false;
				int band = j / 12;
				if (j % 12 == 0 && j > 0 || j == ROWS - 1) {
					on = i % 2 == 0; // little dotted line between bands
				} else if (band == 0 || band == 3) {
					int d = Math.abs(i % 8 - 4) + Math.abs(j % 12 - 6);
					on = d == 2 || d == 0;
				} else {
					int dx = i - COLS / 2, dy = j - ROWS / 2;
					int adx = Math.abs(dx), ady = Math.abs(dy);
					on = (adx == 0 && ady <= 9) || (ady == 0 && adx <= 9) || (adx == ady && adx <= 7) || (adx == 3 && ady == 0) || (adx <= 1 && ady == 9)
							|| (ady <= 1 && adx == 9) || (adx + ady == 12 && adx % 2 == 0);
				}
				knit[j][i] = on;
			}
		}
	}

	/** The red knitted cover. */
	private void knitted(DrawContext c) {
		ensureKnit();
		Gfx.rrectFree(c, 0, 0, W, H, 3, 0xFFFBFBFB); // white border like a real card
		int b = 7;
		Gfx.fill(c, b, b, W - b, H - b, RED);
		float cw = (W - 2F * b) / COLS, ch = (H - 2F * b) / ROWS;
		for (int j = 0; j < ROWS; j++) {
			int i = 0;
			while (i < COLS) {
				if (!knit[j][i]) {
					i++;
					continue;
				}
				int k = i;
				while (k < COLS && knit[j][k]) {
					k++;
				}
				c.fill(Math.round(b + i * cw), Math.round(b + j * ch), Math.round(b + k * cw), Math.round(b + (j + 1) * ch), KNIT);
				i = k;
			}
		}
		Gfx.gradientH(c, W - 14, 0, 14, H, 0x00000000, 0x30000000); // the fold
	}

	/** Inside of the cover, on the left once the card is open: the free hat. */
	private void hatPage(DrawContext c) {
		Gfx.rrectFree(c, 0, 0, W, H, 3, PAPER2);
		Gfx.gradientH(c, W - 16, 0, 16, H, 0x00000000, 0x28000000);
		long now = System.currentTimeMillis();
		float k = 0.6F + 0.4F * (float) Math.sin(now / 420.0);
		Gfx.tex(c, Gfx.id("textures/gui/season/glow.png"), 10, 40, W - 20, W - 20, 64, 64, ((int) (110 * k) << 24) | 0xFFD23B);
		int hw = 116, hh = Math.round(hw * 109F / 152F);
		float bob = (float) Math.sin(now / 520.0) * 3F;
		if (threeD && dev.sleepclient.skin.SkinChanger.myPreview() == null && opened) {
			Gfx.skeleton(c, W / 2 - 30, 62, 60, 100, 8); // your skin is still loading: the hat goes on it, never on a stand-in
		} else if (!(have3d && threeD)) { // once open, the real 3D hat on your skin is drawn over this page instead
			Gfx.tex(c, Gfx.id("textures/gui/season/santa_hat2.png"), (W - hw) / 2, Math.round(70 + bob), hw, hh, 152, 109, 0xFFFFFFFF);
		}
		Gfx.textC(c, "Christmas Hat", W / 2, 22, RED);
		Gfx.textC(c, "A gift from Santa", W / 2, 34, INK);
		Gfx.textC(c, "Free. Yours to keep.", W / 2, H - 22, GREEN);
	}

	/** Right page: the letter. */
	private void messagePage(DrawContext c) {
		Gfx.rrectFree(c, 0, 0, W, H, 3, PAPER);
		Gfx.textC(c, "Ho ho ho!", W / 2, 16, RED);
		String msg = "Merry Christmas! You were on the nice list all year, so I left you a little present: a Christmas hat to wear all season. It is already in your Locker, under Headwear.";
		int y = 36;
		for (String line : wrap(msg, W - 28)) {
			Gfx.text(c, line, 14, y, INK);
			y += 11;
		}
		Gfx.textC(c, "Santa", W / 2 + 28, H - 62, RED);
		if (given) {
			Gfx.textC(c, "Hat on!", W / 2, H - 50, GREEN);
		}
	}

	private static List<String> wrap(String s, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String w : s.split(" ")) {
			String t = cur.length() == 0 ? w : cur + " " + w;
			if (Gfx.tw(t) > maxW && cur.length() > 0) {
				out.add(cur.toString());
				cur = new StringBuilder(w);
			} else {
				cur = new StringBuilder(t);
			}
		}
		if (cur.length() > 0) {
			out.add(cur.toString());
		}
		return out;
	}
}
