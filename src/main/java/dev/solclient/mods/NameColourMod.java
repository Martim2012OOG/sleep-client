package dev.solclient.mods;

import dev.solclient.option.Option;
import dev.solclient.social.ProfileBadges;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * Name Colour: only for the Owner and Team badges. Your name as one colour, a gradient, an animated gradient or a moving
 * rainbow (bold if you like). Every Caos player sees it: the style is sent to them with your badge.
 */
public class NameColourMod extends Mod {

	public static NameColourMod instance;

	/** One name style (what is sent to the other players). */
	public static final class Look {
		public int look, a, b;
		public float speed = 1F;
		public boolean bold;
		/** The colours of the custom gradient (Owner only). */
		public int[] stops;
	}

	private final Option.Choice look = choice("Look", 3, "One Colour", "Gradient", "Rainbow", "Animated Gradient", "Custom Gradient (Owner)");
	private final Option.Col colourA = colour("Colour", 0xFFFF9F43);
	private final Option.Col colourB = colour("Gradient End", 0xFFFF4D8D);
	private final Option.Num stopCount = num("Custom Colours", 3, 2, 5, 1, "");
	private final Option.Col colour3 = colour("Custom Colour 3", 0xFF7B5CFF);
	private final Option.Col colour4 = colour("Custom Colour 4", 0xFF25F4EE);
	private final Option.Col colour5 = colour("Custom Colour 5", 0xFFFFFFFF);
	private final Option.Num speed = num("Speed", 1F, 0.2F, 4F, 0.1F, "x");
	private final Option.Bool bold = bool("Bold", false);
	private final Option.Bool chat = bool("In Chat", true);
	private final Option.Bool share = bool("Show To Other Caos Players", true);

	public NameColourMod() {
		super("Name Colour", "name_colour", "Owner and Team badge only: your name as a colour, gradient, animated gradient or rainbow. Every Caos player sees it.", Category.VISUAL);
		instance = this;
		colourA.showIf(() -> look.idx() != 2);
		colourB.showIf(() -> look.idx() == 1 || look.idx() >= 3);
		stopCount.showIf(() -> look.idx() == 4);
		colour3.showIf(() -> look.idx() == 4 && stopCount.i() >= 3);
		colour4.showIf(() -> look.idx() == 4 && stopCount.i() >= 4);
		colour5.showIf(() -> look.idx() == 4 && stopCount.i() >= 5);
		speed.showIf(() -> look.idx() >= 2);
	}

	@Override
	public void setEnabled(boolean e) {
		if (e && !ProfileBadges.canStyleName()) {
			return; // only the Owner and Team badges
		}
		super.setEnabled(e);
	}

	public static boolean on() {
		return instance != null && instance.enabled && ProfileBadges.canStyleName();
	}

	private static Look mine() {
		NameColourMod m = instance;
		Look l = new Look();
		l.look = m.look.idx();
		l.a = m.colourA.c() & 0xFFFFFF;
		l.b = m.colourB.c() & 0xFFFFFF;
		l.speed = m.speed.get();
		l.bold = m.bold.on();
		if (l.look == 4 && !ProfileBadges.allowed(ProfileBadges.Badge.CROWN)) {
			l.look = 3; // the custom gradient is for the Owner badge
		}
		if (l.look == 4) {
			int n = Math.max(2, Math.min(5, m.stopCount.i()));
			int[] all = {l.a, l.b, m.colour3.c() & 0xFFFFFF, m.colour4.c() & 0xFFFFFF, m.colour5.c() & 0xFFFFFF};
			l.stops = java.util.Arrays.copyOf(all, n);
		}
		return l;
	}

	/** What the other Caos players are told about your name ("" = nothing). */
	public static String encodeMine() {
		if (!on() || !instance.share.on()) {
			return "";
		}
		Look l = mine();
		return l.look + "," + Integer.toHexString(l.a) + "," + Integer.toHexString(l.b) + "," + Math.round(l.speed * 100F) + "," + (l.bold ? 1 : 0)
				+ (l.stops == null ? "" : "," + stopsText(l.stops));
	}

	private static String stopsText(int[] st) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < st.length; i++) {
			b.append(i == 0 ? "" : ",").append(Integer.toHexString(st[i]));
		}
		return b.toString();
	}

	public static Look decode(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			String[] p = s.split(",");
			Look l = new Look();
			l.look = Math.max(0, Math.min(4, Integer.parseInt(p[0])));
			l.a = Integer.parseInt(p[1], 16) & 0xFFFFFF;
			l.b = Integer.parseInt(p[2], 16) & 0xFFFFFF;
			l.speed = Math.max(0.2F, Math.min(4F, Integer.parseInt(p[3]) / 100F));
			l.bold = p.length > 4 && p[4].equals("1");
			if (l.look == 4) {
				int n = Math.max(0, Math.min(5, p.length - 5));
				if (n < 2) {
					l.look = 3;
				} else {
					l.stops = new int[n];
					for (int i = 0; i < n; i++) {
						l.stops[i] = Integer.parseInt(p[5 + i], 16) & 0xFFFFFF;
					}
				}
			}
			return l;
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static Text applyChat(Text t) {
		return on() && instance.chat.on() ? apply(t) : t;
	}

	/** Recolours every place where your name appears in this text. */
	public static Text apply(Text t) {
		if (!on() || t == null) {
			return t;
		}
		// with Nick Hider on, the name shown in your place gets the colour (the real one is not on screen any more)
		String self = NickHiderMod.hidingSelf() ? NickHiderMod.selfName() : instance.mc.getSession().getUsername();
		return applyTo(t, self, mine());
	}

	/** Recolours every place where {@code nick} appears in this text, with this look. */
	public static Text applyTo(Text t, String nick, Look l) {
		if (t == null || l == null || nick == null || nick.isEmpty() || !t.getString().contains(nick)) {
			return t;
		}
		MutableText out = Text.empty();
		t.visit((style, str) -> {
			int from = 0, i;
			while ((i = str.indexOf(nick, from)) >= 0) {
				if (i > from) {
					out.append(Text.literal(str.substring(from, i)).setStyle(style));
				}
				paint(out, nick, style, l);
				from = i + nick.length();
			}
			if (from < str.length()) {
				out.append(Text.literal(str.substring(from)).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static void paint(MutableText out, String name, Style base, Look l) {
		Style st = l.bold ? base.withBold(true) : base;
		if (l.look == 0) {
			out.append(Text.literal(name).setStyle(st.withColor(l.a)));
			return;
		}
		int[] cps = name.codePoints().toArray();
		float period = 4000F / Math.max(0.1F, l.speed);
		float t0 = (System.currentTimeMillis() % (long) period) / period;
		for (int i = 0; i < cps.length; i++) {
			float k = cps.length <= 1 ? 0F : i / (float) (cps.length - 1);
			int rgb;
			if (l.look == 1) {
				rgb = mix(l.a, l.b, k);
			} else if (l.look == 2) {
				rgb = java.awt.Color.HSBtoRGB((t0 + k * 0.6F) % 1F, 0.75F, 1F) & 0xFFFFFF;
			} else if (l.look == 4 && l.stops != null) {
				// your own colours, one after the other, sliding along the name in a loop
				float u = ((k * 0.8F - t0) % 1F + 1F) % 1F * l.stops.length;
				int i0 = Math.min(l.stops.length - 1, (int) u);
				rgb = mix(l.stops[i0], l.stops[(i0 + 1) % l.stops.length], u - i0);
			} else {
				// the gradient slides along the name and back
				float m = 0.5F + 0.5F * (float) Math.sin((t0 - k * 0.7F) * Math.PI * 2.0);
				rgb = mix(l.a, l.b, m);
			}
			out.append(Text.literal(new String(Character.toChars(cps[i]))).setStyle(st.withColor(rgb)));
		}
	}

	private static int mix(int a, int b, float k) {
		int r = Math.round(((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
		int g = Math.round(((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
		int bl = Math.round((a & 255) * (1 - k) + (b & 255) * k);
		return (r << 16) | (g << 8) | bl;
	}
}
