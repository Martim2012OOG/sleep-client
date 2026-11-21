package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * HUD mod that shows one line of text in a box. Every part of the look can be changed: text (font, colours, rainbow,
 * capitals, brackets, text before / after) and box (background, rounded corners, border, side stripe, margins, width).
 */
public abstract class SimpleHudMod extends HudMod {

	private final Option.Header textHeader = header("Text");
	protected final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF);
	protected final Option.Bool shadow = bool("Text Shadow", true);
	protected final Option.Choice font = choice("Font", 0, fontNames());
	protected final Option.Bool bold = bool("Bold", false);
	protected final Option.Bool upperCase = bool("Capital Letters", false);
	protected final Option.Bool chroma = bool("Rainbow Text", false);
	protected final Option.Num chromaSpeed = num("Rainbow Speed", 3, 1, 10, 1, "").showIf(chroma::on);
	protected final Option.Choice brackets = choice("Brackets", 0, "None", "[ ]", "( )", "< >", "{ }");
	protected final Option.Text before = text("Text Before", "", 24, "e.g. >>");
	protected final Option.Text after = text("Text After", "", 24, "");
	protected final Option.Choice align = tabs("Text Align", 1, "Left", "Centre", "Right");

	private final Option.Header boxHeader = header("Box");
	protected final Option.Bool background = bool("Background", true);
	protected final Option.Col backgroundColour = colour("Background Colour", 0x64000000).showIf(background::on);
	protected final Option.Num radius = num("Corner Radius", 0, 0, 12, 0.5F, "");
	protected final Option.Bool border = bool("Border", false);
	protected final Option.Col borderColour = colour("Border Colour", 0xFF000000).showIf(border::on);
	protected final Option.Num borderSize = num("Border Thickness", 1, 0.5F, 4, 0.5F, "").showIf(border::on);
	protected final Option.Bool stripe = bool("Side Stripe", false);
	protected final Option.Col stripeColour = colour("Stripe Colour", 0xFF4F86E8).showIf(stripe::on);
	protected final Option.Num padX = num("Side Margin", 6, 0, 20, 0.5F, "");
	protected final Option.Num padY = num("Top / Bottom Margin", 4, 0, 14, 0.5F, "");
	protected final Option.Num minWidth = num("Minimum Width", 53, 0, 200, 1, "");

	protected SimpleHudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, dx, dy);
	}

	private static String[] fontNames() {
		String[] out = new String[Gfx.FONT_NAMES.length + 1];
		out[0] = "Minecraft";
		System.arraycopy(Gfx.FONT_NAMES, 0, out, 1, Gfx.FONT_NAMES.length);
		return out;
	}

	/** Text in the chosen font. */
	protected Text styled(String s) {
		Text t = font.idx() == 0 ? Text.literal(s) : Gfx.inFont(s, font.idx() - 1);
		return bold.on() ? t.copy().styled(st -> st.withBold(true)) : t;
	}

	private String shown(boolean edit) {
		String t = getText(edit);
		if (t == null) {
			return null;
		}
		String b = before.get(), a = after.get();
		t = (b.isEmpty() ? "" : b + " ") + t + (a.isEmpty() ? "" : " " + a);
		if (brackets.idx() > 0) {
			String pair = new String[] {"", "[]", "()", "<>", "{}"}[brackets.idx()];
			t = pair.charAt(0) + t + pair.charAt(1);
		}
		return upperCase.on() ? t.toUpperCase(java.util.Locale.ROOT) : t;
	}

	private int lastTextW;

	@Override
	public int width() {
		return Math.max(Math.round(minWidth.get()), lastTextW + Math.round(padX.get() * 2));
	}

	@Override
	public int height() {
		return 8 + Math.round(padY.get() * 2);
	}

	public abstract String getText(boolean edit);

	/** Colour of the text right now (rainbow or the chosen one). */
	protected int textColourNow() {
		if (chroma.on()) {
			float h = (System.currentTimeMillis() % (long) (11000 - chromaSpeed.get() * 1000)) / (11000F - chromaSpeed.get() * 1000F);
			return 0xFF000000 | java.awt.Color.HSBtoRGB(h, .75F, 1F);
		}
		return textColour.c();
	}

	protected void box(DrawContext c) {
		int w = width(), h = height();
		float r = radius.get();
		if (border.on()) {
			float t = borderSize.get();
			Gfx.rrectFree(c, 0, 0, w, h, r, borderColour.c());
			if (background.on()) {
				Gfx.rrectFree(c, t, t, w - 2 * t, h - 2 * t, Math.max(0, r - t), backgroundColour.c());
			}
		} else if (background.on()) {
			Gfx.rrectFree(c, 0, 0, w, h, r, backgroundColour.c());
		}
		if (stripe.on()) {
			Gfx.rrectFree(c, 0, 0, 2, h, Math.min(r, 1), stripeColour.c());
		}
	}

	protected int vw(String s) {
		return mc.textRenderer.getWidth(styled(s));
	}

	/** Draws the line starting at x; counters draw their value and label in different colours. */
	protected void drawLine(DrawContext c, String text, int x, int y) {
		c.drawText(mc.textRenderer, styled(text), x, y, textColourNow(), shadow.on());
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		String text = shown(edit);
		if (text == null) {
			return;
		}
		lastTextW = vw(text);
		box(c);
		int w = width(), px = Math.round(padX.get());
		int x = align.idx() == 0 ? px : align.idx() == 2 ? w - px - lastTextW : w / 2 - lastTextW / 2;
		drawLine(c, text, x, Math.round(padY.get()));
	}
}
