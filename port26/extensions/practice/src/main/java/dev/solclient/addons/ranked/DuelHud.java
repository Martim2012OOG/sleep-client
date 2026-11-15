package dev.solclient.addons.ranked;

import static dev.solclient.addons.ranked.RankedCinema.*;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.solclient.Gfx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/**
 * The broadcast graphics of the duel intro and of the victory screen, drawn like the intro page: cinema bars, the
 * "DUEL 1V1" opening, a lower-third banner for every player, the VS card, the tilted 3-2-1 band and FIGHT!, and the
 * VICTORY / DEFEAT band. Sizes follow the page (its clamp(px, vw, px) rules) and every animation uses its timings.
 */
final class DuelHud {

	static final int BLUE = 0xFF3B8CFF, RED = 0xFFFF3B4E, GOLD = 0xFFFFCF4A, INK = 0xFF05060C, PAPER = 0xFFF6F2FF;
	private static final String NS = "kubo_ext_practice";
	static final String BIG = NS + ":duel_big", UI = NS + ":duel_ui", PIX = NS + ":duel_pix";

	private DuelHud() {
	}

	// ------------------------------------------------------------------ page units
	private static float w, h, gs;

	static void frame(int width, int height) {
		w = width;
		h = height;
		gs = (float) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** clamp(minPx, frac * size, maxPx) like the page's CSS, in GUI pixels. */
	private static float cl(float minPx, float val, float maxPx) {
		return Math.max(minPx / gs, Math.min(maxPx / gs, val));
	}

	private static float vw(float v) {
		return w * v / 100F;
	}

	private static float vh(float v) {
		return h * v / 100F;
	}

	private static float px(float v) {
		return v / gs;
	}

	static int teamColour(String team) {
		return "A".equals(team) ? BLUE : RED;
	}

	static String teamName(String team) {
		return "A".equals(team) ? "BLUE TEAM" : "RED TEAM";
	}

	static int tierColour(String tier) {
		int n = 5;
		for (char ch : (tier == null ? "" : tier).toCharArray()) {
			if (Character.isDigit(ch)) {
				n = ch - '0';
			}
		}
		return n == 1 ? 0xFFFFCF3F : n == 2 ? 0xFFE3E8F2 : n == 3 ? 0xFFEE9A5E : 0xFF9AA2B6;
	}

	private static int alpha(int col, double a) {
		int al = (int) Math.round(((col >>> 24) & 255) * Math.max(0, Math.min(1, a)));
		return (al << 24) | (col & 0xFFFFFF);
	}

	// ------------------------------------------------------------------ text
	private static final Map<String, Component> TEXTS = new HashMap<>();

	static Component t(String s, String font) {
		return TEXTS.computeIfAbsent(font + "|" + s, k -> {
			JsonObject o = new JsonObject();
			o.addProperty("text", s);
			o.addProperty("font", font);
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Component.literal(s));
		});
	}

	private static Font tr() {
		return Minecraft.getInstance().font;
	}

	/** Width of s at letter height size (+ letter spacing in em). */
	static float width(String s, String font, float size, float spacing) {
		float sc = size / 9F;
		return tr().width(t(s, font)) * sc + Math.max(0, s.length() - 1) * spacing * size;
	}

	/**
	 * Draws text: (x, y) = top-left of the box after alignment (align 0 left, 1 centre, 2 right); skew in degrees like
	 * CSS skewX; outline = dark stroke like -webkit-text-stroke.
	 */
	static void text(GuiGraphicsExtractor c, String s, String font, float x, float y, float size, int col, int align, float skewDeg, float spacing, boolean outline) {
		if (((col >>> 24) & 255) < 4 || s.isEmpty()) {
			return;
		}
		float sc = size / 9F, tw = width(s, font, size, spacing);
		float x0 = align == 1 ? x - tw / 2 : align == 2 ? x - tw : x;
		var m = c.pose();
		m.pushMatrix();
		m.translate(x0, y + size);
		if (skewDeg != 0) {
			m.mul(new Matrix3x2f(1, 0, (float) Math.tan(Math.toRadians(skewDeg)), 1, 0, 0));
		}
		m.scale(sc, sc);
		m.translate(0, -9);
		if (spacing == 0) {
			if (outline) {
				int o = alpha(INK, ((col >>> 24) & 255) / 255.0);
				float d = Math.max(.6F, 1.2F / sc);
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4;
					m.pushMatrix();
					m.translate((float) Math.cos(a) * d, (float) Math.sin(a) * d);
					c.text(tr(), t(s, font), 0, 0, o, false);
					m.popMatrix();
				}
			}
			c.text(tr(), t(s, font), 0, 0, col, false);
		} else {
			float cx = 0, gap = spacing * 9F;
			for (int i = 0; i < s.length(); i++) {
				String ch = String.valueOf(s.charAt(i));
				c.text(tr(), t(ch, font), Math.round(cx), 0, col, false);
				cx += tr().width(t(ch, font)) + gap;
			}
		}
		m.popMatrix();
	}

	/** Each letter rises into place one after the other (the page's revealChars), fading in. */
	static void reveal(GuiGraphicsExtractor c, String s, String font, float x, float y, float size, int col, int align, float skewDeg, double t, double t0,
			double stag, double dur) {
		float sc = size / 9F, tw = width(s, font, size, 0);
		float cx = align == 1 ? x - tw / 2 : align == 2 ? x - tw : x;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			double k = out5(seg(t, t0 + i * stag, t0 + i * stag + dur));
			if (k > 0.01) {
				text(c, ch, font, cx, y + (float) ((1 - k) * 1.1 * size), size, alpha(col, k), 0, skewDeg, 0, false);
			}
			cx += tr().width(t(ch, font)) * sc;
		}
	}

	// ------------------------------------------------------------------ shapes
	/** A parallelogram (the page's clip-path shapes): the top edge is moved by lean (GUI px, + = right). */
	static void para(GuiGraphicsExtractor c, float x, float y, float pw, float ph, float lean, int col) {
		if (pw <= 0 || ph <= 0) {
			return;
		}
		var m = c.pose();
		m.pushMatrix();
		m.translate(x, y + ph);
		m.mul(new Matrix3x2f(1, 0, -lean / ph, 1, 0, 0));
		m.scale(1F / 8F, 1F / 8F);
		c.fill(0, Math.round(-ph * 8), Math.round(pw * 8), 0, col);
		m.popMatrix();
	}

	static void rect(GuiGraphicsExtractor c, float x, float y, float rw, float rh, int col) {
		var m = c.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(1F / 8F, 1F / 8F);
		c.fill(0, 0, Math.round(rw * 8), Math.round(rh * 8), col);
		m.popMatrix();
	}

	/** Horizontal gradient strip with up to 4 stops (positions 0..1). */
	static void hgrad(GuiGraphicsExtractor c, float x, float y, float rw, float rh, float[] at, int[] cols) {
		for (int i = 0; i < at.length - 1; i++) {
			float a = x + rw * at[i], b = x + rw * at[i + 1];
			if (b - a > 0.05F) {
				var m = c.pose();
				m.pushMatrix();
				m.translate(a, y);
				m.scale(1F / 8F, 1F / 8F);
				Gfx.gradientH(c, 0, 0, Math.round((b - a) * 8), Math.round(rh * 8), cols[i], cols[i + 1]);
				m.popMatrix();
			}
		}
	}

	// ------------------------------------------------------------------ cinema bars, flash, fade
	static float barsAt(double t) {
		double bar = inOut(seg(t, .1, .9)) * 10;
		if (t >= WIDE + .8) {
			bar = lerp(10, 6, inOut(seg(t, WIDE + .8, POV)));
		}
		if (t >= GO + .15) {
			bar = 6 * (1 - inOut(seg(t, GO + .15, GO + .7)));
		}
		return (float) bar;
	}

	static void bars(GuiGraphicsExtractor c, float pct) {
		float bh = vh(pct);
		if (bh > 0.2F) {
			rect(c, 0, 0, w, bh, 0xFF000000);
			rect(c, 0, h - bh, w, bh, 0xFF000000);
		}
	}

	static void flashFade(GuiGraphicsExtractor c, double flash, double fade) {
		if (fade > 0.01) {
			rect(c, 0, 0, w, h, alpha(0xFF000000, fade));
		}
		if (flash > 0.01) {
			rect(c, 0, 0, w, h, alpha(0xFFFFFFFF, flash * .55));
		}
	}

	// ------------------------------------------------------------------ the intro
	/** What the intro shows about the match (the page's CONFIG). */
	record Info(String queue, String arena, String mode, String kit, String local, String other, List<Ranked.Player> first, List<Ranked.Player> second,
			int firstTo) {
	}

	static void intro(GuiGraphicsExtractor c, Info in, double t) {
		bars(c, barsAt(t));
		opening(c, in, t);
		boolean two = "2v2".equals(in.mode);
		if (two) {
			banner(c, in, in.first.get(0), in.local, 'R', t, A1 + .06, A2, 0);
			banner(c, in, in.first.size() > 1 ? in.first.get(1) : in.first.get(0), in.local, 'R', t, A2 + .04, B1, 0);
			banner(c, in, in.second.get(0), in.other, 'L', t, B1 + .06, B2, 0);
			banner(c, in, in.second.size() > 1 ? in.second.get(1) : in.second.get(0), in.other, 'L', t, B2 + .04, VS, 0);
		} else {
			banner(c, in, in.first.get(0), in.local, 'R', t, A1 + .06, B1, 0);
			banner(c, in, in.second.get(0), in.other, 'L', t, B1 + .06, VS, 0);
		}
		vs(c, in, t);
		countdown(c, in, t);
		fight(c, in, t);
	}

	private static void opening(GuiGraphicsExtractor c, Info in, double t) {
		if (t <= .2 || t >= A1) {
			return;
		}
		double a = 1 - in2(seg(t, A1 - .22, A1 - .02));
		float zoom = (float) (1 + t * .018);
		var m = c.pose();
		m.pushMatrix();
		m.translate(w / 2, h / 2);
		m.scale(zoom, zoom);
		float eb = cl(10, vw(1.15F), 15), big = cl(70, vw(13), 220), ar = cl(10, vw(1.05F), 14), gap = vh(1.6F);
		float total = eb + gap + big * .86F + gap + ar;
		float y = -total / 2;
		// eyebrow
		String q = in.queue.toUpperCase();
		float ebw = width(q, UI, eb, .5F);
		for (int i = 0; i < q.length(); i++) {
			double k = out5(seg(t, .25 + i * .025, .25 + i * .025 + .5));
			float cx = -ebw / 2 + width(q.substring(0, i), UI, eb, .5F) + (i > 0 ? .5F * eb : 0);
			text(c, String.valueOf(q.charAt(i)), UI, cx, y + (float) ((1 - k) * eb), eb, alpha(0xFFFFD7C7, k * a), 0, 0, 0, false);
		}
		y += eb + gap;
		// DUEL + mode chip
		float dw = width("DUEL", BIG, big, 0), chipH = big * .36F * 1.26F, chipW = width(in.mode.toUpperCase(), BIG, big * .36F, 0) + big * .36F * .68F;
		float sp = cl(10, vw(1.6F), 26), rowW = dw + sp + chipW, x0 = -rowW / 2;
		reveal(c, "DUEL", BIG, x0, y, big, alpha(PAPER, a), 0, -8, t, .4, .06, .55);
		double mk = outBack(seg(t, .72, 1.0));
		if (mk > 0) {
			float cw = (float) (chipW * Math.max(0, mk));
			float cy = y + big * .86F / 2 - chipH / 2;
			para(c, x0 + dw + sp, cy, cw, chipH, chipH * .2F, alpha(PAPER, Math.min(1, mk * 3) * a));
			if (mk > .6) {
				text(c, in.mode.toUpperCase(), BIG, x0 + dw + sp + cw / 2, cy + chipH * .14F, big * .36F, alpha(INK, a), 1, 0, 0, false);
			}
		}
		y += big * .86F + gap;
		// — ARENA n —
		String an = in.arena.toUpperCase();
		float aw = width(an, UI, ar, .32F), lw = (float) (cl(28, vw(6), 96) * out3(seg(t, .8, 1.3)));
		for (int i = 0; i < an.length(); i++) {
			double k = out5(seg(t, .75 + i * .02, .75 + i * .02 + .5));
			float cx = -aw / 2 + width(an.substring(0, i), UI, ar, .32F) + (i > 0 ? .32F * ar : 0);
			text(c, String.valueOf(an.charAt(i)), UI, cx, y + (float) ((1 - k) * ar), ar, alpha(0xFFD8CFF0, k * a), 0, 0, 0, false);
		}
		float ly = y + ar / 2;
		rect(c, -aw / 2 - 14 / gs * 1.4F - lw, ly, lw, Math.max(.5F, 1 / gs), alpha(0xB3D8CFF0, a));
		rect(c, aw / 2 + 14 / gs * 1.4F, ly, lw, Math.max(.5F, 1 / gs), alpha(0xB3D8CFF0, a));
		m.popMatrix();
	}

	/**
	 * A lower-third banner (the page's makeBanner / animBanner): side 'R' = your team (right), 'L' = the other one.
	 * delta != 0 shows the ELO change (victory screen).
	 */
	static void banner(GuiGraphicsExtractor c, Info in, Ranked.Player p, String team, char side, double t, double t0, double t1, int delta) {
		if (!(t >= t0 - .02 && t < t1 + .3)) {
			return;
		}
		int dir = side == 'L' ? -1 : 1;
		double out = in3(seg(t, t1 - .14, t1 + .06));
		double fade = 1 - out;
		float shiftX = (float) (out * px(90) * dir);
		float bw = cl(120, vw(11.5F), 184), bh = bw / .84F;
		float bottom = h - vh(barsAt(t) > 0 ? barsAt(t) : 0) - vh(4);
		float edge = vw(4);
		int tc = teamColour(team);
		// soft dark shade behind (the page's radial gradient)
		float shW = vw(46), shH = bh * 1.9F;
		float shx = side == 'L' ? 0 : w - shW;
		c.fillGradient(Math.round(shx), Math.round(bottom - shH), Math.round(shx + shW), Math.round(h), alpha(0x00030309, 0), alpha(0xBB030309, fade));
		// the block with the player's head
		double bq = out5(seg(t, t0, t0 + .4));
		float bx = side == 'L' ? edge : w - edge - bw;
		bx += shiftX + (float) ((1 - bq) * px(60) * dir);
		float by = bottom - bh;
		float lean = bw * .16F;
		float shownW = (float) (bw * bq);
		float sx = side == 'L' ? bx : bx + bw - shownW;
		para(c, sx, by, shownW, bh, lean, alpha(tc, fade));
		para(c, sx, by, shownW, bh * .45F, lean * .45F, alpha(0x42FFFFFF, fade * bq)); // light from the top-left
		c.fillGradient(Math.round(sx), Math.round(by + bh * .3F), Math.round(sx + shownW), Math.round(by + bh), 0, alpha(0x80000000, fade));
		double cq = seg(t, t0 + .18, t0 + .52);
		if (cq > 0) {
			var entity = playerEntity(p.mcUsername());
			if (entity != null && !entity.isInvisible() && !dev.solclient.mods.HideInventoryModelMod.hides()) {
				// the player's real 3D model from the chest up, rising into the block and turned towards the middle of the screen
				if (fade > .5) {
					int rx0 = Math.round(sx + lean * .5F), rx1 = Math.round(sx + shownW - lean * .5F), ry0 = Math.round(by), ry1 = Math.round(by + bh);
					if (rx1 - rx0 > 4) {
						float mid = (rx0 + rx1) / 2F, turn = 26F * (side == 'L' ? 1 : -1);
						float rise = (float) ((1 - outBack(cq)) * .85);
						net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(c, rx0, ry0, rx1, ry1, Math.round(bh / 1.25F), .5F + rise, mid + turn, (ry0 + ry1) / 2F,
								entity);
					}
				}
			} else {
				int hs = Math.round(bw * .62F);
				float hy = by + bh - hs - bh * .08F + (float) ((1 - outBack(cq)) * hs * .8F);
				int ha = (int) Math.round(255 * fade * Math.min(1, cq * 3));
				c.enableScissor(Math.round(sx), Math.round(by), Math.round(sx + shownW + lean), Math.round(by + bh));
				dev.solclient.gui.Faces.draw(c, p.mcUsername(), Math.round(bx + bw / 2 - hs / 2F), Math.round(hy), hs, ha);
				c.disableScissor();
			}
		}
		// info column
		float gapX = cl(14, vw(1.6F), 26);
		float ix = side == 'L' ? bx + bw + gapX : bx - gapX;
		int al = side == 'L' ? 0 : 2;
		float nm = cl(46, vw(6), 108), chip = cl(10, vw(.95F), 13), ti = cl(11, vw(1.05F), 15), mo = cl(10, vw(.9F), 13), tier = cl(24, vw(2.4F), 40);
		float gy = cl(6, vw(.7F), 10);
		// from the bottom up: mode + tier row, title row, name, chip row
		float rowH = tier + px(9);
		float yTier = bottom - rowH, yTi = yTier - gy - ti, yName = yTi - gy - nm * .9F, yChip = yName - gy - chip - px(11);
		// chip (team name)
		double tq = out3(seg(t, t0 + .08, t0 + .4));
		String tn = teamName(team);
		float cw = width(tn, UI, chip, .24F) + px(26);
		float cx = side == 'L' ? ix : ix - cw;
		cx += shiftX + (float) ((1 - tq) * px(30) * dir);
		para(c, cx, yChip, cw, chip + px(11), (chip + px(11)) * .08F * 2, alpha(tc, tq * fade));
		text(c, tn, UI, cx + px(12), yChip + px(6), chip, alpha(INK, tq * fade), 0, 0, .24F, false);
		// name
		reveal(c, p.mcUsername(), BIG, ix + shiftX, yName, nm, alpha(0xFFFFFFFF, fade), al, -7, t, t0 + .14, .032, .42);
		// title + points
		double iq = out3(seg(t, t0 + .42, t0 + .72));
		String title = p.tier() == null ? "UNRANKED" : "TIER " + p.tier();
		int elo = p.elo() + (int) Math.round(delta * out3(seg(t, t0 + .45, t0 + 1.6)));
		String pts = elo + " ELO" + (delta != 0 ? "  " + (delta > 0 ? "+" : "") + delta : "");
		float tiw = width(title, UI, ti, .2F), ptw = width(pts, UI, ti, .14F);
		float tiY = yTi + (float) ((1 - iq) * px(10));
		if (side == 'L') {
			text(c, title, UI, ix + shiftX, tiY, ti, alpha(0xFFECE6FF, iq * fade), 0, 0, .2F, false);
			text(c, pts, UI, ix + shiftX + tiw + px(12), tiY, ti, alpha(GOLD, iq * fade), 0, 0, .14F, false);
		} else {
			text(c, pts, UI, ix + shiftX - ptw, tiY, ti, alpha(GOLD, iq * fade), 0, 0, .14F, false);
			text(c, title, UI, ix + shiftX - ptw - px(12) - tiw, tiY, ti, alpha(0xFFECE6FF, iq * fade), 0, 0, .2F, false);
		}
		// mode chip (kit icon + name) and the tier box
		double mq = out5(seg(t, t0 + .5, t0 + .8));
		String kitName = Ranked.kit(in.kit).name().toUpperCase();
		float mw = width(kitName, UI, mo, .26F) + px(14 + 9 + 22) + 16;
		float tbw = width(p.tier() == null ? "LT5" : p.tier(), BIG, tier, 0) + px(36);
		float rowW = mw + tbw - px(11);
		float rx = side == 'L' ? ix : ix - rowW;
		rx += shiftX;
		if (mq > 0) {
			float sw = (float) (mw * mq);
			para(c, rx, yTier, sw, rowH, -px(11), alpha(0xDB080811, fade));
			if (mq > .7) {
				c.pose().pushMatrix();
				c.pose().translate(rx + px(14), yTier + rowH / 2 - 8 * (mo / 9F) * 1.4F);
				c.pose().scale(mo / 9F * 1.4F, mo / 9F * 1.4F);
				c.item(icon(Ranked.kit(in.kit).icon()), 0, 0);
				c.pose().popMatrix();
				text(c, kitName, UI, rx + px(14) + 16 * mo / 9F * 1.4F + px(9), yTier + rowH / 2 - mo / 2, mo, alpha(0xFFDDD6F4, fade), 0, 0, .26F, false);
			}
		}
		double sq = seg(t, t0 + .66, t0 + .92);
		if (sq > 0) {
			String tt = p.tier() == null ? "LT5" : p.tier();
			int col = tierColour(tt);
			float s = (float) lerp(1.9, 1, outBack(sq));
			float tx = rx + mw - px(11), cxm = tx + tbw / 2, cym = yTier + rowH / 2;
			var m = c.pose();
			m.pushMatrix();
			m.translate(cxm, cym);
			m.scale(s, s);
			double glow = Math.exp(-Math.max(0, t - t0 - .92) * 3.5) * (sq >= 1 ? 1 : 0);
			if (glow > .02) {
				para(c, -tbw / 2 - px(6), -rowH / 2 - px(6), tbw + px(12), rowH + px(12), px(11), alpha(col, glow * .35 * fade));
			}
			para(c, -tbw / 2, -rowH / 2, tbw, rowH, px(11), alpha(col, fade));
			text(c, tt, BIG, 0, -tier / 2 + px(1), tier, alpha(INK, fade), 1, 0, 0, false);
			m.popMatrix();
		}
	}

	/** The player in the loaded world, or null. */
	private static net.minecraft.world.entity.player.Player playerEntity(String name) {
		var world = Minecraft.getInstance().level;
		if (world != null) {
			for (var e : world.players()) {
				if (e.getName().getString().equalsIgnoreCase(name)) {
					return e;
				}
			}
		}
		return null;
	}

	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	private static ItemStack icon(String id) {
		return ICONS.computeIfAbsent(id, i -> {
			try {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(i)));
			} catch (Exception e) {
				return ItemStack.EMPTY;
			}
		});
	}

	private static int avgElo(List<Ranked.Player> ps) {
		int s = 0;
		for (Ranked.Player p : ps) {
			s += p.elo();
		}
		return ps.isEmpty() ? 0 : s / ps.size();
	}

	private static void vs(GuiGraphicsExtractor c, Info in, double t) {
		if (!(t >= VS && t < WIDE + .1)) {
			return;
		}
		double a = 1 - in2(seg(t, WIDE - .12, WIDE));
		double k = out5(seg(t, VS + .06, VS + .5));
		float bottom = h - vh(barsAt(t)) - vh(5);
		float tn = cl(10, vw(1), 13), nm = cl(34, vw(5.2F), 92), nm2 = cl(22, vw(3), 52), rk = cl(11, vw(1.05F), 15);
		for (int s = 0; s < 2; s++) {
			boolean left = s == 0;
			List<Ranked.Player> ps = left ? in.second : in.first;
			String team = left ? in.other : in.local;
			float x = left ? vw(4) - (float) ((1 - k) * px(80)) : w - vw(4) + (float) ((1 - k) * px(80));
			int al = left ? 0 : 2;
			float y = bottom - rk;
			String eloS = avgElo(ps) + " ELO";
			String tierS = ps.get(0).tier() == null ? "LT5" : ps.get(0).tier();
			float ew = width(eloS, UI, rk, .14F), tw = width(tierS, UI, rk, .14F);
			if (left) {
				text(c, tierS, UI, x, y, rk, alpha(tierColour(tierS), a), 0, 0, .14F, false);
				text(c, eloS, UI, x + tw + px(10), y, rk, alpha(GOLD, a), 0, 0, .14F, false);
			} else {
				text(c, eloS, UI, x - ew, y, rk, alpha(GOLD, a), 0, 0, .14F, false);
				text(c, tierS, UI, x - ew - px(10) - tw, y, rk, alpha(tierColour(tierS), a), 0, 0, .14F, false);
			}
			y -= px(8);
			for (int i = ps.size() - 1; i >= 0; i--) {
				float size = i == 0 ? nm : nm2;
				y -= size * .92F;
				reveal(c, ps.get(i).mcUsername(), BIG, x, y, size, alpha(0xFFFFFFFF, a * (i == 0 ? 1 : .75)), al, -7, t, VS + (left ? .1 : .16), .03, .4);
				y -= px(8);
			}
			y -= tn;
			text(c, teamName(team), UI, x, y, tn, alpha(teamColour(team), a), al, 0, .32F, false);
		}
		// the big VS
		double s = seg(t, VS + .18, VS + .32);
		if (s > 0) {
			float size = cl(120, Math.min(w, h) * .24F, 280);
			float sl = (float) (1 + (1 - out3(s)) * 2.2 + (t - VS) * .03 + Math.sin(t * 3) * .004);
			float off = (float) (6 + 18 * (1 - out3(seg(t, VS + .25, VS + .7))));
			var m = c.pose();
			int[] cols = {alpha(BLUE, .85 * a), alpha(RED, .85 * a), alpha(0xFFFFFFFF, a)};
			float[] dx = {-px(off), px(off), 0};
			for (int i = 0; i < 3; i++) {
				m.pushMatrix();
				m.translate(w / 2 + dx[i], h / 2);
				m.scale(sl, sl);
				text(c, "VS", BIG, 0, -size / 2, size, cols[i], 1, -10, 0, false);
				m.popMatrix();
			}
		}
		// ROUND 1 · 1V1 at the top
		double ra = seg((t - VS - .4) * 4, 0, 1);
		String rd = "FIRST TO " + in.firstTo + " · " + in.mode.toUpperCase();
		text(c, rd, PIX, w / 2, vh(barsAt(t)) + vh(3.5F), cl(10, vw(.95F), 13), alpha(0xFFF3ECFF, ra * a), 1, 0, .32F, false);
	}

	/** The tilted band (count / FIGHT! / victory), centred at cy, rotated -2.5 deg. */
	private static void bandBg(GuiGraphicsExtractor c, float cy, float bh, double reveal, int lineL, int lineR, int lineL2, int lineR2, double a) {
		var m = c.pose();
		m.pushMatrix();
		m.translate(w / 2, cy);
		m.rotate((float) Math.toRadians(-2.5));
		float x0 = -w / 2 - vw(6), bw = w + vw(12);
		float shown = (float) (bw * reveal);
		int dark = alpha(0xD606060E, a);
		hgrad(c, x0, -bh / 2, shown, bh, new float[] {0, .16F, .84F, 1}, new int[] {0, dark, dark, 0});
		float lh = Math.max(1, px(3));
		hgrad(c, x0, -bh / 2, shown, lh, new float[] {.04F, .22F, .46F, .54F, .78F, .96F},
				new int[] {0, alpha(lineL, a), alpha(lineL, a), alpha(lineR, a), alpha(lineR, a), 0});
		hgrad(c, x0, bh / 2 - lh, shown, lh, new float[] {.04F, .22F, .46F, .54F, .78F, .96F},
				new int[] {0, alpha(lineL2, a), alpha(lineL2, a), alpha(lineR2, a), alpha(lineR2, a), 0});
		m.popMatrix();
	}

	/** Three copies of a word (other team colour, your colour, white with a dark stroke), like the page's .ca/.cb/.cw. */
	private static void triple(GuiGraphicsExtractor c, String s, float cx, float cy, float size, float scale, float off, float dy, float skew, int ca, int cb, double aw,
			double ac) {
		var m = c.pose();
		int[] cols = {alpha(ca, ac), alpha(cb, ac), alpha(0xFFFFFFFF, aw)};
		float[] dx = {-off, off, 0};
		for (int i = 0; i < 3; i++) {
			m.pushMatrix();
			m.translate(cx + dx[i], cy + (i == 2 ? 0 : dx[i] * dy));
			m.scale(scale, scale);
			text(c, s, BIG, 0, -size / 2, size, cols[i], 1, skew, 0, i == 2);
			m.popMatrix();
		}
	}

	private static void countdown(GuiGraphicsExtractor c, Info in, double t) {
		if (!(t >= C3 - .4 && t < GO + .02)) {
			return;
		}
		float cy = h * .41F, bh = cl(64, vh(12), 128);
		int ca = teamColour(in.other), cb = teamColour(in.local);
		double bk = out5(seg(t, C3 - .4, C3 - .08));
		bandBg(c, cy, bh, bk, ca, cb, ca, cb, 1);
		var m = c.pose();
		m.pushMatrix();
		m.translate(w / 2, cy);
		m.rotate((float) Math.toRadians(-2.5));
		// flash inside the band on every number
		if (t >= C3) {
			double fl = Math.exp(-((t - C3) % 1) * 11) * .32;
			rect(c, -w / 2 - vw(6), -bh / 2, (float) ((w + vw(12)) * bk), bh, alpha(0xFFFFFFFF, fl));
		}
		float ls = cl(10, vw(1.05F), 14);
		for (int k = 0; k < 2; k++) {
			double q = out3(seg(t, C3 - .2 + k * .06, C3 + .15 + k * .06));
			float dx = (float) ((1 - q) * px(k == 1 ? 30 : -30));
			if (k == 0) {
				text(c, "ROUND 1", UI, -w / 2 + vw(16) + dx, -ls / 2, ls, alpha(0xC7FFFFFF, q), 0, 0, .42F, false);
			} else {
				String rdy = "GET READY";
				float rw = width(rdy, UI, ls, .42F);
				float rx = w / 2 - vw(16) - rw + dx;
				text(c, rdy, UI, rx, -ls / 2, ls, alpha(0xC7FFFFFF, q), 0, 0, .42F, false);
				float pw = px(24), ph = px(10), px0 = rx - px(16) - 3 * pw - 2 * px(6);
				for (int p = 0; p < 3; p++) {
					boolean lit = t >= C3 + p;
					int col = lit ? (p == 2 ? 0xFFFF3B4E : 0xFFFFD34D) : 0x24FFFFFF;
					float sy = lit ? (float) lerp(1.9, 1, out3(seg(t, C3 + p, C3 + p + .18))) : 1;
					para(c, px0 + p * (pw + px(6)), -ph * sy / 2, pw, ph * sy, pw * .22F, alpha(col, q));
				}
			}
		}
		m.popMatrix();
		// the numbers
		if (t >= C3) {
			int i = (int) Math.max(0, Math.min(2, Math.floor(t - C3)));
			double u = t - C3 - i;
			String n = String.valueOf(3 - i);
			double ein = out5(seg(u, 0, .16)), ex = in3(seg(u, .78, 1));
			float off = (float) px((float) (lerp(52, 7, out3(seg(u, 0, .4))) + ex * 40));
			float sc = (float) (lerp(2.4, 1, ein) * (1 + u * .05) * (1 - ex * .2));
			float size = cl(150, vh(30), 330);
			triple(c, n, w / 2 - px((float) (ex * 90)), cy, size, sc, off, 0, (float) (-8 - ex * 16), ca, cb, 1 - ex, .85 * (1 - ex));
		}
	}

	private static void fight(GuiGraphicsExtractor c, Info in, double t) {
		if (!(t >= GO && t < GO + 1.5)) {
			return;
		}
		double u = t - GO, sp = out3(seg(u, .08, .42));
		float cy = h * .41F, bh = cl(64, vh(12), 128);
		var m = c.pose();
		m.pushMatrix();
		m.translate(w / 2, cy);
		m.rotate((float) Math.toRadians(-2.5));
		float x0 = -w / 2 - vw(6), bw = w + vw(12);
		int white = alpha(0xFFFFFFFF, 1 - sp);
		rect(c, x0, -bh / 2 - (float) (sp * 1.4 * bh / 2), bw, bh / 2, white);
		rect(c, x0, (float) (sp * 1.4 * bh / 2), bw, bh / 2, white);
		m.popMatrix();
		double k = out5(seg(u, 0, .2)), ex = in3(seg(u, 1.05, 1.45));
		float off = px((float) lerp(70, 9, out3(seg(u, .05, .5))));
		float sc = (float) (lerp(2.5, 1, k) + u * .06 + ex * .3);
		triple(c, "FIGHT!", w / 2, cy, cl(110, vh(24), 290), sc, off, 0, -10, teamColour(in.other), teamColour(in.local), 1 - ex, .85 * (1 - ex));
	}

	// ------------------------------------------------------------------ victory
	/** The page's victory screen; won = your team won, none = no result. Returns the LEAVE button box {x, y, w, h}. */
	static float[] victory(GuiGraphicsExtractor c, Info in, String winner, boolean won, boolean none, int delta, double vt, int mx, int my) {
		final double HIT = .74;
		double dt = vt - HIT;
		int tc = none ? 0xFF9AA2B6 : teamColour(winner), ac = won ? GOLD : 0xFFB9C0D2;
		float bandH = Math.min(cl(92, vh(21), 232), vw(16));
		float cy = vh(19) + bandH / 2;
		double sh = vt >= HIT ? Math.exp(-dt * 9) : 0;
		var m = c.pose();
		m.pushMatrix();
		m.translate((float) (Math.sin(vt * 91) * px(8) * sh), (float) (Math.cos(vt * 77) * px(6) * sh));
		double bk = out5(seg(vt, .1, .42));
		bandBg(c, cy, bandH, bk, tc, tc, ac, ac, 1);
		if (vt >= HIT) {
			m.pushMatrix();
			m.translate(w / 2, cy);
			m.rotate((float) Math.toRadians(-2.5));
			rect(c, -w / 2 - vw(6), -bandH / 2, w + vw(12), bandH, alpha(0xFFFFFFFF, .6 * Math.exp(-dt * 7)));
			m.popMatrix();
		}
		// side line above
		double q = out3(seg(vt, 1.0, 1.35));
		String sd = (in.queue + " · first to " + in.firstTo + " · " + in.mode).toUpperCase();
		float sdS = cl(10, vw(1), 13);
		text(c, sd, UI, w / 2, cy - bandH / 2 - px(6) - sdS - (float) ((1 - q) * px(10)), sdS, alpha(0xD1FFFFFF, q), 1, 0, .42F, false);
		// the word, falling and hitting
		String word = none ? "NO CONTEST" : won ? "VICTORY" : "DEFEAT";
		double sq = seg(vt, HIT - .17, HIT);
		float sc = (float) (vt < HIT ? lerp(2.3, 1, in3(sq)) : 1 - .045 * Math.exp(-dt * 9) * Math.cos(dt * 30) + Math.min(dt, 8) * .004);
		float off = (float) (vt < HIT ? 0 : px((float) (5 + 22 * Math.exp(-dt * 5) + Math.sin(vt * 2.2) * 1.6)));
		float size = bandH;
		float ww = width(word, BIG, size, 0);
		float rkS = cl(40, vh(8.6F), 94);
		float rkW = width(won ? "#1" : "#2", BIG, rkS, 0) + rkS * .64F;
		float gap = cl(12, vw(1.5F), 24);
		float rowW = (none ? 0 : rkW + gap) + ww;
		m.pushMatrix();
		m.translate(w / 2, cy);
		m.rotate((float) Math.toRadians(-2.5));
		float wx = -rowW / 2 + (none ? 0 : rkW + gap) + ww / 2;
		triple(c, word, wx, 0, size, sc, off, .3F, -10, RED, BLUE, sq > 0 ? Math.min(1, sq * 4) : 0, vt >= HIT ? (won ? .85 : .5) : 0);
		if (!none) {
			double rq = seg(vt, .86, 1.14);
			if (rq > 0) {
				m.pushMatrix();
				m.translate(-rowW / 2 + rkW / 2, (float) ((1 - outBack(rq)) * -.7 * rkS));
				m.rotate((float) Math.toRadians((1 - out3(rq)) * -16));
				float s2 = (float) lerp(1.7, 1, out3(rq));
				m.scale(s2, s2);
				float rh = rkS * 1.12F;
				para(c, -rkW / 2, -rh / 2, rkW, rh, rkW * .14F * 2, ac);
				text(c, won ? "#1" : "#2", BIG, 0, -rkS / 2 + px(2), rkS, INK, 1, 0, 0, false);
				m.popMatrix();
			}
		}
		m.popMatrix();
		// sub line: chip + "wins · names"
		if (!none) {
			double uq = out3(seg(vt, 1.05, 1.4));
			String cn = teamName(winner);
			float cs = cl(10, vw(.95F), 13), chW = width(cn, UI, cs, .24F) + px(26);
			List<Ranked.Player> ws = winner.equals(in.local) ? in.first : in.second;
			StringBuilder names = new StringBuilder("WINS · ");
			for (int i = 0; i < ws.size(); i++) {
				names.append(i > 0 ? " & " : "").append(ws.get(i).mcUsername().toUpperCase());
			}
			float nw = width(names.toString(), UI, cs, .3F);
			float sy = cy + bandH / 2 + px(4) + (float) ((1 - uq) * px(12));
			float sx = w / 2 - (chW + px(12) + nw) / 2;
			para(c, sx, sy, chW, cs + px(11), (cs + px(11)) * .16F, alpha(tc, uq));
			text(c, cn, UI, sx + px(12), sy + px(6), cs, alpha(INK, uq), 0, 0, .24F, false);
			text(c, names.toString(), UI, sx + chW + px(12), sy + px(6), cs, alpha(0xFFF3ECFF, uq), 0, 0, .3F, false);
		}
		m.popMatrix();
		// the winner's banner (with your ELO change if it's you)
		if (!none) {
			List<Ranked.Player> ws = winner.equals(in.local) ? in.first : in.second;
			banner(c, in, ws.get(0), winner, won ? 'R' : 'L', vt, 1.35, 1e9, won ? delta : 0);
		}
		// LEAVE button on the other side
		double k = seg(vt, 2.0, 2.35);
		float ls = cl(22, vw(2.2F), 30);
		float bw = width("LEAVE", BIG, ls, .1F) + px(34 + 40) + width("ESC", UI, px(10), .16F) + px(14 + 14);
		float bh = ls + px(28);
		float bx = won ? vw(4) : w - vw(4) - bw, by = h - vh(4) - bh;
		if (k > 0) {
			float s = (float) outBack(k);
			boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
			m.pushMatrix();
			m.translate(bx + bw / 2, by + bh);
			m.scale(s, s);
			para(c, -bw / 2, -bh, bw, bh, bw * .07F * 2, hov ? GOLD : PAPER);
			text(c, "LEAVE", BIG, -bw / 2 + px(40), -bh + px(15), ls, INK, 0, 0, .1F, false);
			float kx = -bw / 2 + px(40) + width("LEAVE", BIG, ls, .1F) + px(14);
			float ks = px(10);
			rect(c, kx, -bh / 2 - ks / 2 - px(5), width("ESC", UI, ks, .16F) + px(14), ks + px(9), INK);
			text(c, "ESC", UI, kx + px(7), -bh / 2 - ks / 2 - px(1), ks, PAPER, 0, 0, .16F, false);
			m.popMatrix();
		}
		return new float[] {bx, by, bw, bh};
	}
}
