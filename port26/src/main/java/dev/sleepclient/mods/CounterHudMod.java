package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD mod showing an integer that eases towards its real value (FPS, ping), with its own label and colours by value. */
public abstract class CounterHudMod extends SimpleHudMod {

	private int counter;

	private final Option.Header counterHeader = header("Number");
	protected final Option.Text label = text("Label", "", 12, "default");
	protected final Option.Choice labelSide = tabs("Label Position", 0, "After", "Before", "Hidden");
	protected final Option.Col labelColour = colour("Label Colour", 0xFFFFFFFF);
	protected final Option.Bool smooth = bool("Smooth Number", true);
	protected final Option.Bool byValue = bool("Colour By Value", false);
	protected final Option.Col goodColour = colour("Good Colour", 0xFF55FF7F).showIf(byValue::on);
	protected final Option.Col midColour = colour("Medium Colour", 0xFFFFD34D).showIf(byValue::on);
	protected final Option.Col badColour = colour("Bad Colour", 0xFFFF5A5A).showIf(byValue::on);
	protected final Option.Num goodAt;
	protected final Option.Num badAt;

	protected CounterHudMod(String name, String id, String description, float dx, float dy) {
		super(name, id, description, dx, dy);
		boolean high = higherIsBetter();
		goodAt = num("Good At", high ? 120 : 60, 0, 1000, 5, "").showIf(byValue::on);
		badAt = num("Bad At", high ? 60 : 150, 0, 1000, 5, "").showIf(byValue::on);
	}

	public abstract int getIntValue();

	public abstract String getSuffix();

	/** FPS: more is better. Ping: less is better. */
	protected boolean higherIsBetter() {
		return true;
	}

	@Override
	public void tick() {
		int actual = getIntValue();
		if (!smooth.on()) {
			counter = actual;
		} else if (actual > counter) {
			counter += Math.max((actual - counter) / 2, 1);
		} else if (actual < counter) {
			counter -= Math.max((counter - actual) / 2, 1);
		}
	}

	private String labelText() {
		String l = label.get();
		return l.isEmpty() ? getSuffix() : l;
	}

	@Override
	public String getText(boolean edit) {
		String v = String.valueOf(edit ? 0 : counter);
		return switch (labelSide.idx()) {
			case 1 -> labelText() + " " + v;
			case 2 -> v;
			default -> v + " " + labelText();
		};
	}

	private int numberColour() {
		if (!byValue.on()) {
			return textColourNow();
		}
		boolean high = higherIsBetter();
		boolean good = high ? counter >= goodAt.get() : counter <= goodAt.get();
		boolean bad = high ? counter <= badAt.get() : counter >= badAt.get();
		return good ? goodColour.c() : bad ? badColour.c() : midColour.c();
	}

	@Override
	protected void drawLine(GuiGraphicsExtractor c, String text, int x, int y) {
		// colours each digit run as the number and the rest as the label
		int cx = x, i = 0;
		while (i < text.length()) {
			boolean digit = Character.isDigit(text.charAt(i));
			int j = i;
			while (j < text.length() && Character.isDigit(text.charAt(j)) == digit) {
				j++;
			}
			String part = text.substring(i, j);
			c.text(mc.font, styled(part), cx, y, digit ? numberColour() : labelColour.c(), shadow.on());
			cx += vw(part);
			i = j;
		}
	}
}
