package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

/**
 * Fully customisable sidebar scoreboard (the vanilla one is hidden by InGameHudMixin): size, position, rounded corners,
 * gradient, border, shadow, title bar, separator, line backgrounds, colours... plus a few ready-made presets.
 * The panel is drawn in real screen pixels, so the rounded corners are smooth at any scale.
 */
public class ScoreboardMod extends Mod {

	public static ScoreboardMod instance;

	private final Option.Choice preset = choice("Preset", 0, "Custom", "Vanilla", "Rounded Dark", "Glass", "Neon", "Minimal")
			.onPick(this::applyPreset);

	// ---- layout
	private final Option.Num scale = num("Scale", 1F, 0.4F, 2F, 0.05F, "x");
	private final Option.Choice anchor = choice("Position", 0, "Right Middle", "Right Top", "Right Bottom", "Left Middle",
			"Left Top", "Left Bottom");
	private final Option.Num offX = num("Offset X", 0F, -300F, 300F, 1F, " px");
	private final Option.Num offY = num("Offset Y", 0F, -300F, 300F, 1F, " px");
	private final Option.Num padding = num("Padding", 4F, 0F, 12F, 1F, " px");
	private final Option.Num lineSpacing = num("Line Spacing", 0F, 0F, 8F, 1F, " px");
	private final Option.Num minWidth = num("Min Width", 0F, 0F, 200F, 1F, " px");
	private final Option.Num maxLines = num("Max Lines", 15F, 1F, 15F, 1F, "");
	private final Option.Bool hideLast = bool("Hide Last Line (Server IP)", false);

	// ---- title
	private final Option.Bool showTitle = bool("Show Title", true);
	private final Option.Choice titleAlign = choice("Title Align", 1, "Left", "Centre", "Right");
	private final Option.Col titleColour = colour("Title Background Colour", 0x60000000);
	private final Option.Col titleTextColour = colour("Title Text Colour", 0xFFFFFFFF);
	private final Option.Bool separator = bool("Title Separator", false);
	private final Option.Col separatorColour = colour("Separator Colour", 0x40FFFFFF);
	private final Option.Num separatorSize = num("Separator Thickness", 1F, 1F, 3F, 1F, " px");

	// ---- background
	private final Option.Bool background = bool("Background", true);
	private final Option.Col backgroundColour = colour("Background Colour", 0x50000000);
	private final Option.Bool gradient = bool("Gradient", false);
	private final Option.Col backgroundColourBottom = colour("Background Colour Bottom", 0x50000000);
	private final Option.Num radius = num("Corner Radius", 4F, 0F, 16F, 1F, " px");
	private final Option.Num opacity = num("Opacity", 100F, 0F, 100F, 5F, "%");
	private final Option.Choice lineBg = choice("Line Backgrounds", 0, "Off", "All Lines", "Striped");
	private final Option.Col lineBgColour = colour("Line Background Colour", 0x18FFFFFF);
	private final Option.Bool panelShadow = bool("Panel Shadow", false);
	private final Option.Col panelShadowColour = colour("Panel Shadow Colour", 0x70000000);

	// ---- border
	private final Option.Bool border = bool("Border", false);
	private final Option.Col borderColour = colour("Border Colour", 0xFF000000);
	private final Option.Num borderWidth = num("Border Width", 1F, 1F, 4F, 1F, " px");

	// ---- text
	private final Option.Col textColour = colour("Default Text Colour", 0xFFFFFFFF);
	private final Option.Bool forceColour = bool("Force Text Colour", false);
	private final Option.Bool shadow = bool("Text Shadow", true);
	private final Option.Choice textAlign = choice("Text Align", 0, "Left", "Centre", "Right");
	private final Option.Bool numbers = bool("Numbers", true);
	private final Option.Col numbersColour = colour("Numbers Colour", 0xFFFF5555);

	/** Set when our own drawing threw: from then on the vanilla sidebar is used again, so it never shows nothing. */
	private static boolean broken;
	private static boolean warned;

	public ScoreboardMod() {
		super("Scoreboard", "scoreboard", "Customise the scoreboard: size, position, rounded corners, colours...", Category.HUD);
		instance = this;
		titleAlign.showIf(showTitle::on);
		titleColour.showIf(() -> showTitle.on() && background.on());
		titleTextColour.showIf(showTitle::on);
		separator.showIf(showTitle::on);
		separatorColour.showIf(() -> showTitle.on() && separator.on());
		separatorSize.showIf(() -> showTitle.on() && separator.on());
		backgroundColour.showIf(background::on);
		gradient.showIf(background::on);
		backgroundColourBottom.showIf(() -> background.on() && gradient.on());
		radius.showIf(background::on);
		lineBgColour.showIf(() -> lineBg.idx() != 0);
		panelShadowColour.showIf(panelShadow::on);
		borderColour.showIf(border::on);
		borderWidth.showIf(border::on);
		numbersColour.showIf(numbers::on);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean replacesVanilla() {
		return instance != null && instance.enabled && !broken;
	}

	// ------------------------------------------------------------------------------------------ presets
	private void applyPreset(int p) {
		switch (p) {
			case 1 -> { // vanilla
				set(radius, 0F); set(padding, 2F); set(lineSpacing, 0F);
				set(background, true); set(backgroundColour, 0x50000000); set(gradient, false);
				set(titleColour, 0x60000000); set(showTitle, true); set(separator, false);
				set(border, false); set(panelShadow, false); set(lineBg, 0); set(numbers, true);
				set(titleAlign, 1); set(textAlign, 0); set(opacity, 100F);
			}
			case 2 -> { // rounded dark
				set(radius, 6F); set(padding, 5F); set(lineSpacing, 1F);
				set(background, true); set(backgroundColour, 0xD0101218); set(gradient, false);
				set(titleColour, 0xE0181B24); set(showTitle, true); set(separator, true);
				set(separatorColour, 0x30FFFFFF); set(separatorSize, 1F);
				set(border, true); set(borderColour, 0xFF2A2F3D); set(borderWidth, 1F);
				set(panelShadow, true); set(panelShadowColour, 0x70000000); set(lineBg, 0);
				set(numbers, false); set(titleAlign, 1); set(textAlign, 0); set(opacity, 100F);
			}
			case 3 -> { // glass
				set(radius, 8F); set(padding, 5F); set(lineSpacing, 1F);
				set(background, true); set(backgroundColour, 0x50203050); set(gradient, true);
				set(backgroundColourBottom, 0x30101828); set(titleColour, 0x40FFFFFF);
				set(showTitle, true); set(separator, false);
				set(border, true); set(borderColour, 0x66FFFFFF); set(borderWidth, 1F);
				set(panelShadow, true); set(panelShadowColour, 0x40000000); set(lineBg, 0);
				set(numbers, false); set(titleAlign, 1); set(textAlign, 0); set(opacity, 100F);
			}
			case 4 -> { // neon
				set(radius, 4F); set(padding, 5F); set(lineSpacing, 1F);
				set(background, true); set(backgroundColour, 0xD0080810); set(gradient, false);
				set(titleColour, 0xD0120A24); set(showTitle, true); set(separator, true);
				set(separatorColour, 0xFFB44BFF); set(separatorSize, 1F);
				set(border, true); set(borderColour, 0xFFB44BFF); set(borderWidth, 2F);
				set(panelShadow, true); set(panelShadowColour, 0x80B44BFF); set(lineBg, 0);
				set(numbers, false); set(titleAlign, 1); set(textAlign, 0); set(opacity, 100F);
			}
			case 5 -> { // minimal
				set(radius, 3F); set(padding, 3F); set(lineSpacing, 0F);
				set(background, true); set(backgroundColour, 0x30000000); set(gradient, false);
				set(titleColour, 0x00000000); set(showTitle, true); set(separator, false);
				set(border, false); set(panelShadow, false); set(lineBg, 0);
				set(numbers, false); set(titleAlign, 1); set(textAlign, 0); set(opacity, 100F);
			}
			default -> {
			}
		}
	}

	private static void set(Option.Num o, float v) {
		o.set(v);
	}

	private static void set(Option.Bool o, boolean v) {
		o.set(v);
	}

	private static void set(Option.Col o, int v) {
		o.set(v);
	}

	private static void set(Option.Choice o, int v) {
		o.set(v);
	}

	// ------------------------------------------------------------------------------------------ shapes (real pixels)
	/** How far (in pixels) row j of a W x H rounded rectangle is cut away on each side by the corner. */
	private static double edge(int j, int h, int r) {
		if (r <= 0) {
			return 0;
		}
		int jj = j < r ? j : (j >= h - r ? h - 1 - j : -1);
		if (jj < 0) {
			return 0;
		}
		double dy = r - jj - 0.5;
		return r - Math.sqrt(Math.max(0, r * (double) r - dy * dy));
	}

	private static int alphaOf(int col) {
		return (col >>> 24) & 255;
	}

	private static void rows(DrawContext c, int ox, int w, int oy, int from, int to, int col) {
		if (to > from && alphaOf(col) > 0) {
			c.fill(ox, oy + from, ox + w, oy + to, col);
		}
	}

	/** A smooth rounded rectangle whose colour can change per row (gradient / title band). */
	private static void shape(DrawContext c, int ox, int oy, int w, int h, int r, IntUnaryOperator rowCol) {
		int runStart = -1, runCol = 0;
		for (int j = 0; j < h; j++) {
			double e = edge(j, h, r);
			int col = rowCol.applyAsInt(j);
			if (e == 0) {
				if (runStart >= 0 && col == runCol) {
					continue;
				}
				if (runStart >= 0) {
					rows(c, ox, w, oy, runStart, j, runCol);
				}
				runStart = j;
				runCol = col;
				continue;
			}
			if (runStart >= 0) {
				rows(c, ox, w, oy, runStart, j, runCol);
				runStart = -1;
			}
			if (alphaOf(col) == 0) {
				continue;
			}
			int k = (int) Math.floor(e);
			float cov = (float) (k + 1 - e);
			int y = oy + j;
			if (ox + k + 1 < ox + w - k - 1) {
				c.fill(ox + k + 1, y, ox + w - k - 1, y + 1, col);
			}
			if (cov > 0.04F) {
				int ec = Gfx.withAlpha(col, Math.round(alphaOf(col) * Math.min(1F, cov)));
				c.fill(ox + k, y, ox + k + 1, y + 1, ec);
				c.fill(ox + w - k - 1, y, ox + w - k, y + 1, ec);
			}
		}
		if (runStart >= 0) {
			rows(c, ox, w, oy, runStart, h, runCol);
		}
	}

	/** Outline of a rounded rectangle, t pixels thick. */
	private static void ring(DrawContext c, int ox, int oy, int w, int h, int r, int t, int col) {
		if (alphaOf(col) == 0 || w < 2 * t || h < 2 * t) {
			return;
		}
		int m = Math.min(Math.max(r, t), h / 2);
		for (int j = 0; j < m; j++) {
			int xo = (int) Math.round(edge(j, h, r));
			int xi;
			if (j < t) {
				xi = w; // whole row
			} else {
				xi = (int) Math.round(t + edge(j - t, h - 2 * t, Math.max(0, r - t)));
			}
			for (int side = 0; side < 2; side++) {
				int y = side == 0 ? oy + j : oy + h - 1 - j;
				if (xi >= w - xi) {
					c.fill(ox + xo, y, ox + w - xo, y + 1, col);
				} else {
					c.fill(ox + xo, y, ox + xi, y + 1, col);
					c.fill(ox + w - xi, y, ox + w - xo, y + 1, col);
				}
			}
		}
		if (h - 2 * m > 0) {
			c.fill(ox, oy + m, ox + t, oy + h - m, col);
			c.fill(ox + w - t, oy + m, ox + w, oy + h - m, col);
		}
	}

	// ------------------------------------------------------------------------------------------ drawing
	public void draw(DrawContext c) {
		try {
			drawPanel(c);
		} catch (Throwable t) {
			broken = true;
			if (!warned) {
				warned = true;
				System.err.println("[Sleep] Scoreboard failed, using the vanilla scoreboard instead: " + t);
			}
		}
	}

	private int fade(int col, float op) {
		return op >= 0.999F ? col : Gfx.withAlpha(col, Math.round(alphaOf(col) * op));
	}

	private void drawPanel(DrawContext c) {
		if (mc.world == null || mc.options.hudHidden) {
			return;
		}
		Scoreboard sb = mc.world.getScoreboard();
		ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		if (obj == null) {
			return;
		}
		List<ScoreboardEntry> list = new ArrayList<>();
		for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
			if (!e.hidden()) {
				list.add(e);
			}
		}
		list.sort((a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
				: a.owner().compareToIgnoreCase(b.owner()));
		if (hideLast.on() && list.size() > 1) {
			list.remove(list.size() - 1); // the lowest score is the bottom line (usually the server address)
		}
		int cap = Math.min(15, maxLines.i());
		if (list.size() > cap) {
			list = new ArrayList<>(list.subList(0, cap));
		}
		TextRenderer tr = mc.textRenderer;
		int n = list.size();
		int pad = padding.i();
		int spacing = lineSpacing.i();
		int lh = 9 + spacing;
		boolean title = showTitle.on();
		Text titleText = obj.getDisplayName();

		int contentW = title ? tr.getWidth(titleText) : 0;
		List<Text> names = new ArrayList<>();
		List<String> nums = new ArrayList<>();
		int numGap = numbers.on() ? 6 : 0;
		int numColW = 0;
		for (ScoreboardEntry e : list) {
			Text nm = Team.decorateName(sb.getScoreHolderTeam(e.owner()), e.name());
			if (forceColour.on()) {
				nm = Text.literal(nm.getString());
			}
			names.add(nm);
			String s = Integer.toString(e.value());
			nums.add(s);
			if (numbers.on()) {
				numColW = Math.max(numColW, tr.getWidth(s));
			}
		}
		int nameColW = 0;
		for (Text nm : names) {
			nameColW = Math.max(nameColW, tr.getWidth(nm));
		}
		contentW = Math.max(contentW, nameColW + (numbers.on() ? numGap + numColW : 0));
		contentW = Math.max(contentW, minWidth.i());
		int pw = contentW + pad * 2;
		int titleH = title ? 9 + Math.max(2, pad * 2 - 2) : 0;
		int topPad = title ? Math.max(1, pad / 2) : pad;
		int linesH = n == 0 ? 0 : n * lh - spacing;
		int ph = titleH + topPad + linesH + (n == 0 ? 0 : pad);

		float k = scale.f();
		float guiS = (float) mc.getWindow().getScaleFactor();
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		int screenW = Math.round(pw * k), screenH = Math.round(ph * k);
		int margin = 3;
		boolean right = anchor.idx() < 3;
		int v = anchor.idx() % 3;
		int x = (right ? sw - margin - screenW : margin) + offX.i();
		int y = (v == 0 ? (sh - screenH) / 2 : v == 1 ? margin : sh - margin - screenH) + offY.i();
		x = Math.max(0, Math.min(x, sw - screenW));
		y = Math.max(0, Math.min(y, sh - screenH));
		float op = opacity.f() / 100F;

		c.getMatrices().pushMatrix();
		try {
			c.getMatrices().translate((float) x, (float) y);

			// ---- shapes, in real screen pixels
			c.getMatrices().pushMatrix();
			try {
				c.getMatrices().scale(1F / guiS, 1F / guiS);
				float ps = k * guiS;
				int pxW = Math.max(2, Math.round(pw * ps)), pxH = Math.max(2, Math.round(ph * ps));
				int pr = background.on() || border.on() ? Math.min(Math.round(radius.f() * ps), Math.min(pxW, pxH) / 2) : 0;
				if (panelShadow.on()) {
					int sp = Math.max(1, Math.round(2 * ps)), dy = Math.max(1, Math.round(ps));
					int sc = fade(panelShadowColour.c(), op);
					shape(c, -sp, -sp + dy, pxW + 2 * sp, pxH + 2 * sp, pr + sp, j -> sc);
				}
				if (background.on()) {
					int titlePx = Math.round(titleH * ps);
					int top = fade(backgroundColour.c(), op);
					int bot = fade(gradient.on() ? backgroundColourBottom.c() : backgroundColour.c(), op);
					int tcol = fade(titleColour.c(), op);
					boolean grad = gradient.on();
					int bodyH = Math.max(1, pxH - titlePx);
					int step = Math.max(2, pxH / 48);
					shape(c, 0, 0, pxW, pxH, pr, j -> {
						if (title && j < titlePx) {
							return tcol;
						}
						if (!grad) {
							return top;
						}
						int q = ((j - titlePx) / step) * step;
						return Gfx.blend(top, bot, q / (float) bodyH);
					});
				}
				if (border.on()) {
					int t = Math.max(1, Math.round(borderWidth.f() * Math.max(1F, ps / 2F)));
					ring(c, 0, 0, pxW, pxH, pr, t, fade(borderColour.c(), op));
				}
				if (title && separator.on()) {
					int th = Math.max(1, Math.round(separatorSize.f() * Math.max(1F, ps / 2F)));
					int sy = Math.round(titleH * ps) - th;
					int inset = Math.max(pr / 2, Math.round(pad * ps / 2F));
					rows(c, inset, Math.max(1, pxW - 2 * inset), 0, sy, sy + th, fade(separatorColour.c(), op));
				}
			} finally {
				c.getMatrices().popMatrix();
			}

			// ---- text and line backgrounds, in GUI units scaled by "Scale"
			c.getMatrices().pushMatrix();
			try {
				c.getMatrices().scale(k, k);
				boolean sh2 = shadow.on();
				if (title) {
					int tw = tr.getWidth(titleText);
					int tx = titleAlign.idx() == 0 ? pad : titleAlign.idx() == 2 ? pw - pad - tw : pw / 2 - tw / 2;
					c.drawText(tr, titleText, tx, (titleH - (separator.on() ? separatorSize.i() : 0) - 9) / 2 + 1,
							titleTextColour.c(), sh2);
				}
				int y0 = titleH + topPad;
				for (int i = 0; i < n; i++) {
					int ly = y0 + i * lh;
					if (lineBg.idx() == 1 || (lineBg.idx() == 2 && i % 2 == 1)) {
						c.fill(Math.max(0, pad - 2), ly - spacing / 2, Math.min(pw, pw - pad + 2), ly + 9 + (spacing + 1) / 2,
								fade(lineBgColour.c(), op));
					}
					Text nm = names.get(i);
					int nw = tr.getWidth(nm);
					int area = contentW - (numbers.on() ? numGap + numColW : 0);
					int nx = textAlign.idx() == 0 ? pad : textAlign.idx() == 2 ? pad + area - nw : pad + (area - nw) / 2;
					c.drawText(tr, nm, nx, ly, textColour.c(), sh2);
					if (numbers.on()) {
						String s = nums.get(i);
						c.drawText(tr, s, pw - pad - tr.getWidth(s), ly, numbersColour.c(), sh2);
					}
				}
			} finally {
				c.getMatrices().popMatrix();
			}
		} finally {
			c.getMatrices().popMatrix();
		}
	}
}
