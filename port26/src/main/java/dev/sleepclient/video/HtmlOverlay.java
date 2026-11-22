package dev.sleepclient.video;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * A web page drawn over the whole game (the duel intro / victory screen of Ranked PvP), through the optional MCEF mod.
 * {@link #available()} is false when MCEF is not installed or not ready: callers then draw their own simple version.
 */
public final class HtmlOverlay {

	private static int counter;
	private final Mcef.Browser browser;
	private final BrowserTexture tex = new BrowserTexture();
	private final Identifier id = Identifier.fromNamespaceAndPath("sleep_client", "html_overlay_" + (counter++));
	private boolean registered;
	private int w, h;
	private boolean scaled;

	private HtmlOverlay(Mcef.Browser b, int w, int h) {
		this.browser = b;
		this.w = w;
		this.h = h;
	}

	/** MCEF is installed and its browser files are downloaded. */
	public static boolean available() {
		if (!Mcef.installed()) {
			return false;
		}
		Mcef.start();
		return Mcef.ready();
	}

	/** Opens a page (http(s):// or file://) at the window's pixel size; null when MCEF is not ready. */
	public static HtmlOverlay open(String url) {
		return open(url, false);
	}

	/** Page size in pixels: the window size, or (scaled) a smaller one so a heavy 3D page runs smoothly and its text is bigger. */
	static int[] sizeFor(boolean scaled, int fw, int fh) {
		if (!scaled || fw <= 960) {
			return new int[] {Math.max(320, fw), Math.max(180, fh)};
		}
		int tw = Math.max(960, Math.min(1280, Math.round(fw * 0.6F)));
		int th = Math.max(180, Math.round(tw * (float) fh / fw));
		return new int[] {tw, th};
	}

	/** Like {@link #open(String)}; scaled = rendered at a reduced size (the 3D party lobby). */
	public static HtmlOverlay open(String url, boolean scaled) {
		if (!available()) {
			return null;
		}
		var win = Minecraft.getInstance().getWindow();
		int[] sz = sizeFor(scaled, win.getWidth(), win.getHeight());
		int w = sz[0], h = sz[1];
		try {
			Mcef.Browser b = Mcef.create(url, w, h);
			if (b == null) {
				return null;
			}
			b.frameRate(60);
			HtmlOverlay o = new HtmlOverlay(b, w, h);
			o.scaled = scaled;
			return o;
		} catch (Throwable t) {
			System.err.println("[Sleep] html overlay: " + t);
			return null;
		}
	}

	/** The browser itself (the party lobby forwards the mouse and keys to it and reads its address). */
	public Mcef.Browser browser() {
		return browser;
	}

	/** The page size in pixels (mouse positions must be given in it). */
	public int pageW() {
		return w;
	}

	public int pageH() {
		return h;
	}

	/** Runs JavaScript in the page. */
	public void js(String code) {
		try {
			browser.js(code);
		} catch (Throwable ignored) {
			// page not loaded yet
		}
	}

	/** True once the page drew its first frame. */
	public boolean ready() {
		var v = browser.texture();
		if (v != null && !v.isClosed()) {
			tex.set(v);
			if (!registered) {
				Minecraft.getInstance().getTextureManager().register(id, tex);
				registered = true;
			}
			return true;
		}
		return false;
	}

	/** Draws the page over the whole screen (GUI coordinates). */
	public void draw(GuiGraphicsExtractor c) {
		var win = Minecraft.getInstance().getWindow();
		int fw = win.getWidth(), fh = win.getHeight();
		int[] sz = sizeFor(scaled, fw, fh);
		if (sz[0] != w || sz[1] != h) {
			w = sz[0];
			h = sz[1];
			browser.resize(w, h);
		}
		if (!ready()) {
			return;
		}
		int sw = win.getGuiScaledWidth(), sh = win.getGuiScaledHeight();
		c.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, sw, sh, sw, sh);
	}

	public void close() {
		try {
			browser.close();
		} catch (Throwable ignored) {
			// already gone
		}
		if (registered) {
			Minecraft.getInstance().getTextureManager().release(id);
			registered = false;
		}
	}
}
