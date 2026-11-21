package dev.sleepclient.video;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The title bar of the floating browser panels, drawn like the user's "Safari" frame (dark version): a 52-unit high
 * bar (#262626, 1-unit #404040 edge, rounded top corners), three window dots, sidebar / back / forward / shield icons on
 * the left, the rounded address pill (lock + address, reload on its right) in the middle and share / new / tabs on the
 * right. Everything is measured in the SVG's own units (the bar is 52 units high); the icons are the SVG's own paths.
 * <p>
 * The bar is painted into a picture (crisp, rounded, anti-aliased) and drawn on the panel as one textured quad. The
 * address text itself is drawn live on top, so typing does not repaint the picture.
 */
public final class SafariChrome {

	/** Height of the bar in SVG units. */
	public static final double BAR = 52;

	// clickable parts
	public static final int NONE = -1, CLOSE = 0, BACK = 1, FWD = 2, RELOAD = 3, URL = 4, SHARE = 5, NEW = 6;

	private static final Color BG = new Color(0x262626), EDGE = new Color(0x404040), ICON = new Color(0xA3A3A3),
			ICON_HOVER = new Color(0xF2F2F2), ICON_OFF = new Color(0x5C5C5C), PILL = new Color(0x404040), PILL_HOVER = new Color(0x4D4D4D);

	// ---- the SVG's icon paths (absolute coordinates of the 1202-wide original)
	private static final String SIDEBAR = "M99.5703 33.6016H112.938C114.633 33.6016 115.516 32.7266 115.516 31.0547V21.5469C115.516 19.875 114.633 19 112.938 19H99.5703C97.8828 19 97 19.8672 97 21.5469V31.0547C97 32.7266 97.8828 33.6016 99.5703 33.6016ZM99.6719 32.0469C98.9531 32.0469 98.5547 31.6719 98.5547 30.9141V21.6875C98.5547 20.9297 98.9531 20.5547 99.6719 20.5547H103.234V32.0469H99.6719ZM112.836 20.5547C113.555 20.5547 113.953 20.9297 113.953 21.6875V30.9141C113.953 31.6719 113.555 32.0469 112.836 32.0469H104.711V20.5547H112.836ZM101.703 23.4141C101.984 23.4141 102.219 23.1719 102.219 22.9062C102.219 22.6406 101.984 22.4062 101.703 22.4062H100.102C99.8203 22.4062 99.5859 22.6406 99.5859 22.9062C99.5859 23.1719 99.8203 23.4141 100.102 23.4141H101.703ZM101.703 25.5156C101.984 25.5156 102.219 25.2812 102.219 25.0078C102.219 24.7422 101.984 24.5078 101.703 24.5078H100.102C99.8203 24.5078 99.5859 24.7422 99.5859 25.0078C99.5859 25.2812 99.8203 25.5156 100.102 25.5156H101.703ZM101.703 27.6094C101.984 27.6094 102.219 27.3828 102.219 27.1094C102.219 26.8438 101.984 26.6172 101.703 26.6172H100.102C99.8203 26.6172 99.5859 26.8438 99.5859 27.1094C99.5859 27.3828 99.8203 27.6094 100.102 27.6094H101.703Z";
	private static final String BACK_P = "M143.914 32.5938C144.094 32.7656 144.312 32.8594 144.562 32.8594C145.086 32.8594 145.492 32.4531 145.492 31.9375C145.492 31.6797 145.391 31.4453 145.211 31.2656L139.742 25.9219L145.211 20.5938C145.391 20.4141 145.492 20.1719 145.492 19.9219C145.492 19.4062 145.086 19 144.562 19C144.312 19 144.094 19.0938 143.922 19.2656L137.844 25.2031C137.625 25.4062 137.516 25.6562 137.516 25.9297C137.516 26.2031 137.625 26.4375 137.836 26.6484L143.914 32.5938Z";
	private static final String FWD_P = "M168.422 32.8594C168.68 32.8594 168.891 32.7656 169.07 32.5938L175.148 26.6562C175.359 26.4375 175.469 26.2109 175.469 25.9297C175.469 25.6562 175.367 25.4141 175.148 25.2109L169.07 19.2656C168.891 19.0938 168.68 19 168.422 19C167.898 19 167.492 19.4062 167.492 19.9219C167.492 20.1719 167.602 20.4141 167.773 20.5938L173.25 25.9375L167.773 31.2656C167.594 31.4531 167.492 31.6797 167.492 31.9375C167.492 32.4531 167.898 32.8594 168.422 32.8594Z";
	private static final String SHIELD = "M265.5 33.8984C265.641 33.8984 265.852 33.8516 266.047 33.7422C270.547 31.2969 272.109 30.1641 272.109 27.3203V21.4219C272.109 20.4844 271.742 20.1484 270.961 19.8125C270.094 19.4453 267.18 18.4297 266.328 18.1406C266.07 18.0547 265.766 18 265.5 18C265.234 18 264.93 18.0703 264.672 18.1406C263.82 18.3828 260.906 19.4531 260.039 19.8125C259.258 20.1406 258.891 20.4844 258.891 21.4219V27.3203C258.891 30.1641 260.461 31.2812 264.945 33.7422C265.148 33.8516 265.359 33.8984 265.5 33.8984ZM265.922 19.5781C266.945 19.9766 269.172 20.7656 270.344 21.1875C270.562 21.2656 270.617 21.3828 270.617 21.6641V27.0234C270.617 29.3125 269.469 29.9375 265.945 32.0625C265.727 32.1875 265.617 32.2344 265.508 32.2344V19.4844C265.617 19.4844 265.734 19.5156 265.922 19.5781Z";
	private static final String LOCK = "M566.269 32.0852H572.426C573.277 32.0852 573.696 31.6663 573.696 30.7395V25.9851C573.696 25.1472 573.353 24.7219 572.642 24.6521V23.0842C572.642 20.6721 571.036 19.5105 569.348 19.5105C567.659 19.5105 566.053 20.6721 566.053 23.0842V24.6711C565.393 24.7727 565 25.1917 565 25.9851V30.7395C565 31.6663 565.418 32.0852 566.269 32.0852ZM567.272 22.97C567.272 21.491 568.211 20.6785 569.348 20.6785C570.478 20.6785 571.423 21.491 571.423 22.97V24.6394L567.272 24.6458V22.97Z";
	private static final String RELOAD_P = "M936.273 24.9766C936.5 24.9766 936.68 24.9062 936.82 24.7578L940.023 21.5312C940.195 21.3594 940.273 21.1719 940.273 20.9531C940.273 20.7422 940.188 20.5391 940.023 20.3828L936.82 17.125C936.68 16.9688 936.5 16.8906 936.273 16.8906C935.852 16.8906 935.516 17.2422 935.516 17.6719C935.516 17.8828 935.594 18.0547 935.727 18.2031L937.594 20.0312C937.227 19.9766 936.852 19.9453 936.477 19.9453C932.609 19.9453 929.516 23.0391 929.516 26.9141C929.516 30.7891 932.633 33.9062 936.5 33.9062C940.375 33.9062 943.484 30.7891 943.484 26.9141C943.484 26.4453 943.156 26.1094 942.688 26.1094C942.234 26.1094 941.93 26.4453 941.93 26.9141C941.93 29.9297 939.516 32.3516 936.5 32.3516C933.492 32.3516 931.07 29.9297 931.07 26.9141C931.07 23.875 933.469 21.4688 936.477 21.4688C936.984 21.4688 937.453 21.5078 937.867 21.5781L935.734 23.6875C935.594 23.8281 935.516 24 935.516 24.2109C935.516 24.6406 935.852 24.9766 936.273 24.9766Z";
	private static final String SHARE_P = "M1099.51 28.4141C1099.91 28.4141 1100.24 28.0859 1100.24 27.6953V19.8359L1100.18 18.6797L1100.66 19.25L1101.75 20.4141C1101.88 20.5547 1102.06 20.625 1102.24 20.625C1102.6 20.625 1102.9 20.3672 1102.9 20C1102.9 19.8047 1102.82 19.6641 1102.69 19.5312L1100.06 17.0078C1099.88 16.8203 1099.7 16.7578 1099.51 16.7578C1099.32 16.7578 1099.14 16.8203 1098.95 17.0078L1096.33 19.5312C1096.2 19.6641 1096.12 19.8047 1096.12 20C1096.12 20.3672 1096.41 20.625 1096.77 20.625C1096.95 20.625 1097.14 20.5547 1097.27 20.4141L1098.35 19.25L1098.84 18.6719L1098.78 19.8359V27.6953C1098.78 28.0859 1099.11 28.4141 1099.51 28.4141ZM1095 34.6562H1104C1105.7 34.6562 1106.57 33.7812 1106.57 32.1094V24.4297C1106.57 22.7578 1105.7 21.8828 1104 21.8828H1101.89V23.4375H1103.9C1104.61 23.4375 1105.02 23.8125 1105.02 24.5625V31.9688C1105.02 32.7188 1104.61 33.0938 1103.9 33.0938H1095.1C1094.38 33.0938 1093.98 32.7188 1093.98 31.9688V24.5625C1093.98 23.8125 1094.38 23.4375 1095.1 23.4375H1097.13V21.8828H1095C1093.31 21.8828 1092.43 22.75 1092.43 24.4297V32.1094C1092.43 33.7812 1093.31 34.6562 1095 34.6562Z";
	private static final String PLUS = "M1134 33.0156C1134.49 33.0156 1134.89 32.6094 1134.89 32.1484V27.2578H1139.66C1140.13 27.2578 1140.54 26.8594 1140.54 26.3672C1140.54 25.8828 1140.13 25.4766 1139.66 25.4766H1134.89V20.5859C1134.89 20.1172 1134.49 19.7188 1134 19.7188C1133.52 19.7188 1133.11 20.1172 1133.11 20.5859V25.4766H1128.34C1127.88 25.4766 1127.46 25.8828 1127.46 26.3672C1127.46 26.8594 1127.88 27.2578 1128.34 27.2578H1133.11V32.1484C1133.11 32.6094 1133.52 33.0156 1134 33.0156Z";
	private static final String TABS = "M1161.8 31.0703H1163.23V32.375C1163.23 34.0547 1164.12 34.9219 1165.81 34.9219H1174.2C1175.89 34.9219 1176.77 34.0547 1176.77 32.3828V24.0469C1176.77 22.375 1175.89 21.5 1174.2 21.5H1172.77V20.2578C1172.77 18.5859 1171.88 17.7109 1170.19 17.7109H1161.8C1160.1 17.7109 1159.23 18.5781 1159.23 20.2578V28.5234C1159.23 30.1953 1160.1 31.0703 1161.8 31.0703ZM1161.9 29.5078C1161.18 29.5078 1160.78 29.1328 1160.78 28.3828V20.3984C1160.78 19.6406 1161.18 19.2656 1161.9 19.2656H1170.09C1170.8 19.2656 1171.2 19.6406 1171.2 20.3984V21.5H1165.81C1164.12 21.5 1163.23 22.375 1163.23 24.0469V29.5078H1161.9ZM1165.91 33.3672C1165.19 33.3672 1164.8 32.9922 1164.8 32.2422V24.1875C1164.8 23.4297 1165.19 23.0625 1165.91 23.0625H1174.1C1174.81 23.0625 1175.21 23.4297 1175.21 24.1875V32.2422C1175.21 32.9922 1174.81 33.3672 1174.1 33.3672H1165.91Z";

	private static final Path2D P_SIDEBAR = parse(SIDEBAR), P_BACK = parse(BACK_P), P_FWD = parse(FWD_P), P_SHIELD = parse(SHIELD),
			P_LOCK = parse(LOCK), P_RELOAD = parse(RELOAD_P), P_SHARE = parse(SHARE_P), P_PLUS = parse(PLUS), P_TABS = parse(TABS);

	private SafariChrome() {
	}

	/** Where everything is, in SVG units, for a bar 'w' units wide. Boxes are {x0, y0, x1, y1}; null = hidden (narrow bar). */
	public static final class Layout {
		public final double w;
		public final boolean full;
		public final double[] close, dot2, dot3, sidebar, back, fwd, shield, pill, reload, share, plus, tabs;
		/** How far the right-hand icons moved compared to the 1202-wide original. */
		final double rightShift;
		/** Where the original pill items (lock at 565, reload at 929) are moved to. */
		final double lockShift, reloadShift;

		Layout(double w) {
			this.w = w;
			full = w >= 760;
			close = box(27, 25, 9);
			dot2 = box(47, 25, 9);
			dot3 = box(67, 25, 9);
			sidebar = full ? new double[] {92, 12, 120, 40} : null;
			back = new double[] {full ? 130 : 84, 12, full ? 153 : 107, 40};
			double bShift = full ? 0 : -54;
			fwd = new double[] {full ? 160 : 112, 12, full ? 183 : 135, 40};
			shield = full ? new double[] {253, 12, 278, 40} : null;
			rightShift = w - 1202;
			share = full ? new double[] {1086 + rightShift, 12, 1113 + rightShift, 40} : null;
			plus = new double[] {(full ? 1120 : 1150) + rightShift, 12, (full ? 1147 : 1177) + rightShift, 40};
			tabs = full ? new double[] {1153 + rightShift, 12, 1183 + rightShift, 40} : null;
			double left = full ? 286 : 148, right = full ? share[0] - 140 : plus[0] - 12;
			// the pill keeps the original's proportions when there is room (centred), and fills the gap otherwise
			double maxW = 666, gap = right - left;
			double pw = Math.min(maxW, Math.max(80, gap));
			double px0 = full ? Math.max(left, w / 2 - pw / 2 + 25) : left;
			if (px0 + pw > right) {
				px0 = Math.max(left, right - pw);
			}
			pill = new double[] {px0, 11, px0 + Math.min(pw, Math.max(80, right - px0)), 41};
			reload = new double[] {pill[2] - 26, 12, pill[2] - 4, 40};
			lockShift = pill[0] + 12 - 565;
			reloadShift = pill[2] - 952;
			if (bShift != 0) {
				// narrow: back / forward sit closer to the dots
			}
		}

		private static double[] box(double cx, double cy, double r) {
			return new double[] {cx - r, cy - r, cx + r, cy + r};
		}

		public int at(double x, double y) {
			if (in(close, x, y)) {
				return CLOSE;
			}
			if (in(back, x, y)) {
				return BACK;
			}
			if (in(fwd, x, y)) {
				return FWD;
			}
			if (in(reload, x, y)) {
				return RELOAD;
			}
			if (in(pill, x, y)) {
				return URL;
			}
			if (in(share, x, y)) {
				return SHARE;
			}
			if (in(plus, x, y) || in(tabs, x, y)) {
				return NEW;
			}
			return NONE;
		}

		private static boolean in(double[] b, double x, double y) {
			return b != null && x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3];
		}

		/** Where the address text starts (units): right after the lock. */
		public double textX0() {
			return pill[0] + 26;
		}

		public double textX1() {
			return reload[0] - 4;
		}
	}

	public static Layout layout(double wUnits) {
		return new Layout(Math.max(300, wUnits));
	}

	/**
	 * Paints the bar. scale = pixels per unit. hover = the part under the crosshair; barHover = the crosshair is on the
	 * bar (the window dots get their colours, like on a Mac).
	 */
	public static BufferedImage paint(Layout l, double scale, int hover, boolean barHover, boolean canBack, boolean canFwd, boolean editing,
			int accent) {
		int pw = Math.max(1, (int) Math.round(l.w * scale)), ph = Math.max(1, (int) Math.round(BAR * scale));
		BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.scale(scale, scale);
			// bar: #404040 edge, #262626 inside, rounded top corners (the bottom edge meets the page)
			Path2D outer = topRounded(0, 0, l.w, BAR, 12);
			g.setColor(EDGE);
			g.fill(outer);
			g.setColor(BG);
			g.fill(topRounded(1.07, 1, l.w - 2.14, BAR - 2, 11));
			// window dots: grey like the original, red / yellow / green while you aim at the bar
			Color[] dots = barHover ? new Color[] {new Color(0xFF5F57), new Color(0xFEBC2E), new Color(0x28C840)} : new Color[] {EDGE, EDGE, EDGE};
			if (hover == CLOSE) {
				dots[0] = new Color(0xFF5F57);
			}
			double[][] dc = {{27, 25}, {47, 25}, {67, 25}};
			for (int i = 0; i < 3; i++) {
				g.setColor(dots[i]);
				g.fill(new Ellipse2D.Double(dc[i][0] - 6, dc[i][1] - 6, 12, 12));
			}
			if (hover == CLOSE) {
				g.setColor(new Color(0x4D0000));
				g.setStroke(new BasicStroke(1.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new java.awt.geom.Line2D.Double(24.6, 22.6, 29.4, 27.4));
				g.draw(new java.awt.geom.Line2D.Double(29.4, 22.6, 24.6, 27.4));
			}
			// left icons
			double backShift = l.full ? 0 : l.back[0] - 130;
			if (l.sidebar != null) {
				icon(g, P_SIDEBAR, 0, ICON);
			}
			icon(g, P_BACK, backShift, !canBack ? ICON_OFF : hover == BACK ? ICON_HOVER : ICON);
			icon(g, P_FWD, l.full ? 0 : l.fwd[0] - 160, !canFwd ? ICON_OFF : hover == FWD ? ICON_HOVER : ICON);
			if (l.shield != null) {
				icon(g, P_SHIELD, 0, ICON);
			}
			// address pill
			double[] p = l.pill;
			g.setColor(hover == URL && !editing ? PILL_HOVER : PILL);
			RoundRectangle2D pillShape = new RoundRectangle2D.Double(p[0], p[1] + 0, p[2] - p[0], p[3] - p[1], 12, 12);
			g.fill(pillShape);
			if (editing) {
				g.setColor(new Color(accent & 0xFFFFFF));
				g.setStroke(new BasicStroke(1.5F));
				g.draw(pillShape);
			}
			icon(g, P_LOCK, l.lockShift, ICON);
			icon(g, P_RELOAD, l.reloadShift, hover == RELOAD ? ICON_HOVER : ICON);
			// right icons
			if (l.share != null) {
				icon(g, P_SHARE, l.rightShift, hover == SHARE ? ICON_HOVER : ICON);
			}
			icon(g, P_PLUS, l.plus[0] - 1120, hover == NEW ? ICON_HOVER : ICON);
			if (l.tabs != null) {
				icon(g, P_TABS, l.rightShift, ICON);
			}
		} finally {
			g.dispose();
		}
		return img;
	}

	private static void icon(Graphics2D g, Path2D path, double dx, Color col) {
		AffineTransform old = g.getTransform();
		g.translate(dx, 0);
		g.setColor(col);
		g.fill(path);
		g.setTransform(old);
	}

	private static Path2D topRounded(double x, double y, double w, double h, double r) {
		Path2D p = new Path2D.Double();
		p.moveTo(x, y + h);
		p.lineTo(x, y + r);
		p.quadTo(x, y, x + r, y);
		p.lineTo(x + w - r, y);
		p.quadTo(x + w, y, x + w, y + r);
		p.lineTo(x + w, y + h);
		p.closePath();
		return p;
	}

	// ------------------------------------------------------------------ tiny SVG path reader (M L H V C Z, absolute)
	static Path2D parse(String d) {
		Path2D p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("[MLHVCZmlhvcz]|-?[0-9]*\\.?[0-9]+(?:e-?[0-9]+)?").matcher(d);
		java.util.List<String> t = new java.util.ArrayList<>();
		while (m.find()) {
			t.add(m.group());
		}
		double cx = 0, cy = 0;
		char cmd = 'M';
		int i = 0;
		while (i < t.size()) {
			String s = t.get(i);
			if (Character.isLetter(s.charAt(0))) {
				cmd = s.charAt(0);
				i++;
				if (cmd == 'Z' || cmd == 'z') {
					p.closePath();
					continue;
				}
			}
			switch (cmd) {
				case 'M' -> {
					cx = num(t, i++);
					cy = num(t, i++);
					p.moveTo(cx, cy);
					cmd = 'L';
				}
				case 'L' -> {
					cx = num(t, i++);
					cy = num(t, i++);
					p.lineTo(cx, cy);
				}
				case 'H' -> {
					cx = num(t, i++);
					p.lineTo(cx, cy);
				}
				case 'V' -> {
					cy = num(t, i++);
					p.lineTo(cx, cy);
				}
				case 'C' -> {
					double x1 = num(t, i++), y1 = num(t, i++), x2 = num(t, i++), y2 = num(t, i++);
					cx = num(t, i++);
					cy = num(t, i++);
					p.curveTo(x1, y1, x2, y2, cx, cy);
				}
				default -> i++; // not used by these icons
			}
		}
		return p;
	}

	private static double num(java.util.List<String> t, int i) {
		return i < t.size() ? Double.parseDouble(t.get(i)) : 0;
	}
}
