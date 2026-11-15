package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import dev.solclient.tiktok.TikStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The viewer counter of your TikTok live, like the spectator badge of mobile games: a blue TV, the number of people
 * watching (counts up smoothly and pops when it grows) and a red LIVE tag under it. Optional total likes. A normal HUD
 * element: move it and resize it in the HUD editor.
 */
public class TikTokViewersMod extends HudMod {

	public static TikTokViewersMod instance;

	private final Option.Choice show = choice("Show", 0, "Only while live", "Always");
	private final Option.Bool liveTag = bool("LIVE Tag", true);
	private final Option.Bool likes = bool("Show Likes", false);
	private final Option.Bool animate = bool("Count Up Animation", true);

	// your picture, cut in pieces (textures/gui/tiktok): pill.png = left part with the TV | 4 px middle that stretches | right end
	private static final Identifier PILL = Gfx.id("textures/gui/tiktok/pill.png"), LIVE = Gfx.id("textures/gui/tiktok/live.png"),
			DIGITS = Gfx.id("textures/gui/tiktok/digits.png");
	private static final int PW = 310, PH = 246, LEFT = 224, MID = 4, RIGHT = 82, LW = 356, LH = 148, LIVE_Y = 252;
	/** Digit atlas (Lilita One, white with a dark outline and shadow): characters, x, width, white width; glyph top at y 62. */
	private static final String CH = "0123456789.KM";
	private static final int[] GX = {0, 99, 169, 254, 339, 433, 518, 609, 691, 782, 872, 919, 1016};
	private static final int[] GW = {97, 68, 83, 83, 92, 83, 89, 80, 89, 88, 45, 95, 132};
	private static final int[] WW = {82, 52, 67, 68, 77, 67, 74, 64, 73, 72, 29, 79, 116};
	private static final int DW = 1150, DH = 135, STX = 8, GAP = 8, DIG_X = 238, DIG_Y = 62;
	/** Badge height on screen at 100% (GUI pixels). */
	private static final float H = 24F, F = H / PH;

	private float shown = -1;
	private int lastTarget = -1;
	private long lastFrame, bumpAt;

	public TikTokViewersMod() {
		super("TikTok Viewers", "tiktok_viewers", "How many people are watching your TikTok live, as a badge on screen (needs TikTok Live connected).", 0.42F,
				0.02F);
		instance = this;
	}

	private boolean connected() {
		return TikTokLiveMod.instance != null && TikTokLiveMod.instance.live();
	}

	@Override
	public boolean visible() {
		return show.idx() == 1 || connected() || TikStats.viewers >= 0 && System.currentTimeMillis() - TikStats.viewersAt < 15_000L;
	}

	/** 439, 9 999, 12.3K, 1.2M */
	static String fmt(long n) {
		if (n < 0) {
			return "-";
		}
		if (n < 10_000) {
			return Long.toString(n);
		}
		if (n < 1_000_000) {
			double k = n / 1000.0;
			return (k < 100 ? String.format(java.util.Locale.ROOT, "%.1f", k) : Long.toString(Math.round(k))) + "K";
		}
		double m = n / 1_000_000.0;
		return (m < 100 ? String.format(java.util.Locale.ROOT, "%.1f", m) : Long.toString(Math.round(m))) + "M";
	}

	private int target(boolean edit) {
		int v = TikStats.viewers;
		return v < 0 && edit ? 439 : Math.max(0, v);
	}

	private static int numW(String num) {
		int w = 0;
		for (int i = 0; i < num.length(); i++) {
			int k = CH.indexOf(num.charAt(i));
			if (k >= 0) {
				w += WW[k] + (w > 0 ? GAP : 0);
			}
		}
		return w;
	}

	/** Pill width in texture pixels for this number. */
	private static int pillW(String num) {
		return DIG_X + numW(num) + 16 + RIGHT;
	}

	private int likesChipW() {
		return likes.on() ? numW(fmt(TikStats.likes < 0 ? 12_345 : TikStats.likes)) / 2 + 90 : 0;
	}

	@Override
	public int width() {
		int pw = pillW(fmt(target(true)));
		int row = liveTag.on() ? pw / 2 + LW / 2 + 6 : 0;
		if (likes.on()) {
			row = Math.max(row, (liveTag.on() ? pw / 2 + LW / 2 + 20 : 0) + likesChipW());
		}
		return (int) Math.ceil(Math.max(pw, row) * F);
	}

	@Override
	public int height() {
		return (int) Math.ceil((liveTag.on() || likes.on() ? LIVE_Y + LH : PH) * F);
	}

	/** Draws a number with the digit atlas; x, y in texture pixels, scale k. */
	private static void digits(GuiGraphicsExtractor c, String num, float x, float y, float k) {
		var m = c.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(k, k);
		int cx = 0;
		for (int i = 0; i < num.length(); i++) {
			int g = CH.indexOf(num.charAt(i));
			if (g < 0) {
				continue;
			}
			Gfx.texUv(c, DIGITS, cx - STX, 0, GW[g], DH, GX[g], 0, GW[g], DH, DW, DH, 0xFFFFFFFF);
			cx += WW[g] + GAP;
		}
		m.popMatrix();
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
		lastFrame = now;
		int target = target(edit);
		if (shown < 0 || !animate.on()) {
			shown = target;
		} else {
			shown += (target - shown) * Math.min(1F, dt * 5F);
			if (Math.abs(target - shown) < 0.5F) {
				shown = target;
			}
		}
		if (lastTarget >= 0 && target > lastTarget) {
			bumpAt = now; // more people: the number pops
		}
		lastTarget = target;
		String num = fmt(Math.round(shown));
		int pw = pillW(num);

		var m = c.pose();
		m.pushMatrix();
		m.scale(F, F); // from here on: texture pixels of your picture
		// the pill: left part with the TV, stretched middle, right end
		Gfx.texUv(c, PILL, 0, 0, LEFT, PH, 0, 0, LEFT, PH, PW, PH, 0xFFFFFFFF);
		Gfx.texUv(c, PILL, LEFT, 0, pw - RIGHT - LEFT, PH, LEFT, 0, MID, PH, PW, PH, 0xFFFFFFFF);
		Gfx.texUv(c, PILL, pw - RIGHT, 0, RIGHT, PH, LEFT + MID, 0, RIGHT, PH, PW, PH, 0xFFFFFFFF);
		// the number, popping a little when it grows
		float bump = 1F + 0.16F * Math.max(0F, 1F - (now - bumpAt) / 260F);
		int nw = numW(num);
		float cx = DIG_X + nw / 2F, cy = DIG_Y + DH / 2F;
		digits(c, num, cx - nw / 2F * bump, cy - DH / 2F * bump, bump);
		// LIVE under it
		int lx = pw / 2 - LW / 2 + 6;
		if (liveTag.on()) {
			Gfx.texUv(c, LIVE, lx, LIVE_Y, LW, LH, 0, 0, LW, LH, LW, LH, 0xFFFFFFFF);
		}
		m.popMatrix();
		if (likes.on()) {
			// a small dark chip with a heart and the total likes, next to LIVE
			long lk = TikStats.likes < 0 && edit ? 12_345 : Math.max(0, TikStats.likes);
			String t = fmt(lk);
			float x = (liveTag.on() ? lx + LW + 14 : 0) * F, y = (LIVE_Y + 14) * F, w = likesChipW() * F, h = (LH - 28) * F;
			Gfx.rrectF(c, x, y, w, h, h / 2F, 0xE0101422);
			Gfx.heart(c, x + 40 * F, y + h / 2F, 8, 0xFFFF3B6B);
			m.pushMatrix();
			m.scale(F, F);
			digits(c, t, x / F + 74, y / F + 8, 0.5F);
			m.popMatrix();
		}
	}
}

