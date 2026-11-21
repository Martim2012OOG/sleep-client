package dev.sleepclient.video;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * One floating browser panel in the world (client side only). Sizes are in blocks. "Local" coordinates have their origin
 * at the centre of the panel: x to the viewer's right, y up. The title bar (buttons + address) sits on top of the page.
 */
public final class PanelBox {

	public static final double TITLE_H = 0.16, BTN = 0.21, MIN_W = 1.3, MIN_H = 0.9, MAX_W = 16, MAX_H = 9;
	public static final int BTN_BACK = 0, BTN_FWD = 1, BTN_RELOAD = 2, BTN_CLOSE = 3, BTN_COPY = 4, BTN_NEW = 5;

	private static int next = 1;

	public final int id = next++;
	public final String startUrl;
	public Vec3d center;
	/** Horizontal unit vector the panel "looks" along, i.e. away from whoever is looking at it. */
	public Vec3d facing;
	public double width, height;

	// aim state (refreshed every frame by PanelInput)
	public boolean hovered;
	public double hitX, hitY, hitDist;
	public int cornerX, cornerY;

	// browser state (owned by Panels)
	Mcef.Browser browser;
	BrowserTexture tex;
	Identifier texId;
	boolean texRegistered, closed, canBack, canFwd;
	// the Safari-style bar picture
	net.minecraft.client.texture.NativeImageBackedTexture chromeTex;
	Identifier chromeId;
	String chromeKey = "";
	/** Set when this panel shows an app of the computer instead of a web page. */
	AppMirror.Source mirrorSrc;
	LiveView mirror;
	/** Opening an installed app: waiting for its window. */
	volatile boolean launching;
	/** The "videos don't play here" hint was shown for this panel. */
	boolean videoHint;
	int pxW = 640, pxH = 360, fps = 30;
	long resizeAt, retryAt, urlAt, jsAt, fpsAt;
	String shownUrl = "";

	public PanelBox(Vec3d center, Vec3d facing, double width, double height, String startUrl) {
		this.center = center;
		this.facing = facing.normalize();
		this.width = width;
		this.height = height;
		this.startUrl = startUrl;
	}

	/** Tilt in degrees: positive leans the top of the panel away from you (to look down at it), -75..75. */
	public double pitch;

	/** Direction the panel faces, with its tilt. */
	public Vec3d facing3() {
		double r = Math.toRadians(pitch);
		Vec3d flat = new Vec3d(facing.x, 0, facing.z);
		if (flat.lengthSquared() < 1.0E-6) {
			flat = new Vec3d(0, 0, 1);
		}
		flat = flat.normalize();
		return new Vec3d(flat.x * Math.cos(r), -Math.sin(r), flat.z * Math.cos(r)).normalize();
	}

	public Vec3d right() {
		return facing.crossProduct(new Vec3d(0, 1, 0)).normalize();
	}

	/** The panel's "up" (tilts with the pitch). */
	public Vec3d up() {
		return right().crossProduct(facing3()).normalize();
	}

	/** Turns the panel: yaw (left / right) and pitch (up / down), in degrees. */
	public void rotate(double yawDeg, double pitchDeg) {
		if (yawDeg != 0) {
			double a = Math.toRadians(yawDeg), cs = Math.cos(a), sn = Math.sin(a);
			facing = new Vec3d(facing.x * cs - facing.z * sn, 0, facing.x * sn + facing.z * cs).normalize();
		}
		pitch = Math.max(-75, Math.min(75, pitch + pitchDeg));
	}

	/** Normal of the front face (points at the viewer). */
	public Vec3d normal() {
		return facing3().negate();
	}

	/** A point of the panel plane, in world coordinates ('off' blocks in front of it). */
	public Vec3d point(double lx, double ly, double off) {
		return center.add(right().multiply(lx)).add(up().multiply(ly)).add(normal().multiply(off));
	}

	public double contentH() {
		return height - TITLE_H;
	}

	/** Size of the corner handles (blocks). */
	public double cornerSize() {
		return Math.min(0.32, 0.25 * Math.min(width, height));
	}

	public boolean inContent(double x, double y) {
		return Math.abs(x) <= width / 2 && y >= -height / 2 && y <= height / 2 - TITLE_H;
	}

	public boolean inTitle(double x, double y) {
		return Math.abs(x) <= width / 2 && y > height / 2 - TITLE_H && y <= height / 2;
	}

	/** Blocks per unit of the Safari-style bar (the bar is 52 units high). */
	public double unit() {
		return TITLE_H / SafariChrome.BAR;
	}

	private SafariChrome.Layout layout;
	private double layoutW = -1;

	/** Where the bar's parts are for the current width. */
	public SafariChrome.Layout layout() {
		if (layout == null || layoutW != width) {
			layoutW = width;
			layout = SafariChrome.layout(width / unit());
		}
		return layout;
	}

	/** Local x of a bar position in units. */
	public double barX(double units) {
		return -width / 2 + units * unit();
	}

	/** Which part of the bar is at this local point (SafariChrome.CLOSE / BACK / ... or NONE). */
	public int barPart(double x, double y) {
		if (!inTitle(x, y)) {
			return SafariChrome.NONE;
		}
		return layout().at((x + width / 2) / unit(), (height / 2 - y) / unit());
	}

	public int buttonAt(double x, double y) {
		return switch (barPart(x, y)) {
			case SafariChrome.CLOSE -> BTN_CLOSE;
			case SafariChrome.BACK -> BTN_BACK;
			case SafariChrome.FWD -> BTN_FWD;
			case SafariChrome.RELOAD -> BTN_RELOAD;
			case SafariChrome.SHARE -> BTN_COPY;
			case SafariChrome.NEW -> BTN_NEW;
			default -> -1;
		};
	}

	public boolean inUrl(double x, double y) {
		return barPart(x, y) == SafariChrome.URL;
	}

	/** Page pixel under a local point (clamped to the page). */
	public int pixelX(double x) {
		return (int) Math.max(0, Math.min(pxW - 1, (x + width / 2) / width * pxW));
	}

	public int pixelY(double y) {
		double v = 1.0 - (y + height / 2) / contentH();
		return (int) Math.max(0, Math.min(pxH - 1, v * pxH));
	}
}
