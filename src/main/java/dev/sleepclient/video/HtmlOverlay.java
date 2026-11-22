package dev.sleepclient.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * A web page drawn over the whole game (the duel intro / victory screen of Ranked PvP), through the optional MCEF mod.
 * {@link #available()} is false when MCEF is not installed or not ready: callers then draw their own simple version.
 */
public final class HtmlOverlay {

	private static int counter;
	private final Mcef.Browser browser;
	private final BrowserTexture tex = new BrowserTexture();
	private final Identifier id = Identifier.of("sleep_client", "html_overlay_" + (counter++));
	private boolean registered;
	private int w, h;

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
		if (!available()) {
			return null;
		}
		var win = MinecraftClient.getInstance().getWindow();
		int w = Math.max(320, win.getFramebufferWidth()), h = Math.max(180, win.getFramebufferHeight());
		try {
			Mcef.Browser b = Mcef.create(url, w, h);
			if (b == null) {
				return null;
			}
			b.frameRate(60);
			return new HtmlOverlay(b, w, h);
		} catch (Throwable t) {
			System.err.println("[Sleep] html overlay: " + t);
			return null;
		}
	}

	/** The browser itself (the party lobby forwards the mouse and keys to it and reads its address). */
	public Mcef.Browser browser() {
		return browser;
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
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
				registered = true;
			}
			return true;
		}
		return false;
	}

	/** Draws the page over the whole screen (GUI coordinates). */
	public void draw(DrawContext c) {
		var win = MinecraftClient.getInstance().getWindow();
		int fw = win.getFramebufferWidth(), fh = win.getFramebufferHeight();
		if (fw != w || fh != h) {
			w = fw;
			h = fh;
			browser.resize(w, h);
		}
		if (!ready()) {
			return;
		}
		int sw = win.getScaledWidth(), sh = win.getScaledHeight();
		c.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, sw, sh, sw, sh);
	}

	public void close() {
		try {
			browser.close();
		} catch (Throwable ignored) {
			// already gone
		}
		if (registered) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
			registered = false;
		}
	}
}
