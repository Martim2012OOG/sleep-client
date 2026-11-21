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
		var win = Minecraft.getInstance().getWindow();
		int w = Math.max(320, win.getWidth()), h = Math.max(180, win.getHeight());
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
		if (fw != w || fh != h) {
			w = fw;
			h = fh;
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
