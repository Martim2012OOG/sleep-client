package dev.solclient.video;

import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * One embedded Chromium page that plays ONE animated cape (video / GIF / WebP) muted and looping. The page draws the
 * whole cape atlas, and its picture is exposed as a texture the cape renderer samples. It is shared by every player
 * wearing that cape (the caller keeps one CapeVideo per cape, never one per player), runs at a low frame rate, and is
 * closed by the caller when nobody is looking at it. Decoding happens inside Chromium, never on the render thread.
 */
public final class CapeVideo {

	private static final int FPS = 24;

	private final Mcef.Browser browser;
	private final BrowserTexture tex = new BrowserTexture();
	private final Identifier id;
	private boolean registered;
	private boolean ready;

	private CapeVideo(Mcef.Browser browser, Identifier id) {
		this.browser = browser;
		this.id = id;
	}

	/** Opens the page; null when the video engine (MCEF) is not ready or the browser could not be created. */
	public static CapeVideo open(String html, int w, int h, Identifier id) {
		String url = "data:text/html;charset=utf-8;base64," + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
		Mcef.Browser b = Mcef.create(url, w, h);
		if (b == null) {
			return null;
		}
		b.frameRate(FPS);
		return new CapeVideo(b, id);
	}

	public Identifier id() {
		return id;
	}

	/**
	 * Call on the render thread before the texture is used (cheap): takes the browser's newest frame view.
	 * Returns true once the first frame exists; from then on the texture stays valid.
	 */
	public boolean refresh() {
		GpuTextureView v = browser.texture();
		if (v != null && !v.isClosed()) {
			tex.set(v);
			if (!registered) {
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
				registered = true;
			}
			ready = true;
		}
		return ready;
	}

	public void close() {
		try {
			browser.close();
		} catch (RuntimeException ignored) {
			// already gone
		}
		if (registered) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
			registered = false;
		}
		ready = false;
	}
}
