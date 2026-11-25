package dev.solclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Gfx;
import dev.solclient.SolClient;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The animated Plegumo logo (the user's GIF, baked into a sprite sheet: textures/gui/splash_logo.png,
 * timings in splash_logo.json) drawn above the Mojang logo on the loading screen.
 */
public final class SplashLogo {

	/** Registered by hand from the mod jar (not via resource packs, which are still loading / reloading). */
	private static final Identifier SHEET = Gfx.id("plegumo_splash_sheet");
	private static boolean uploaded, failed;
	private static net.minecraft.client.renderer.texture.DynamicTexture sheetTex;
	private static int tries;
	private static int frame = 160, cols = 10, count = 1;
	private static int[] ends = {70}; // cumulative frame end times (ms)
	private static boolean loaded;
	private static final long START = System.currentTimeMillis();

	private SplashLogo() {
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		// read from the mod jar directly (not through resource packs: they may be reloading right now)
		try (InputStream in = SplashLogo.class.getResourceAsStream("/assets/sol_client/splash_logo.json")) {
			if (in == null) {
				return;
			}
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			frame = o.get("frame").getAsInt();
			cols = o.get("cols").getAsInt();
			count = o.get("count").getAsInt();
			JsonArray d = o.getAsJsonArray("durations");
			ends = new int[d.size()];
			int t = 0;
			for (int i = 0; i < d.size(); i++) {
				t += Math.max(20, d.get(i).getAsInt());
				ends[i] = t;
			}
		} catch (Exception e) {
			System.err.println("[Caos] splash logo: " + e);
		}
	}

	/** True while a resource pack reload runs with its loading screen hidden (the Caos HUD keeps showing). */
	public static volatile boolean quietReload;

	/** Should the logo be drawn on this loading screen? reloading = resource pack reload (not the game start). */
	public static boolean enabled(boolean reloading) {
		if (SolClient.settings == null) {
			return !reloading;
		}
		if (reloading && SolClient.settings.hideReloadScreen.on()) {
			return false;
		}
		if (!SolClient.settings.splashLogo.on()) {
			return false;
		}
		return !reloading || SolClient.settings.splashOnReload.on();
	}

	private static final class Sheet extends net.minecraft.client.renderer.texture.DynamicTexture {
		Sheet(com.mojang.blaze3d.platform.NativeImage img) {
			super(() -> "plegumo_splash", img);
			this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
		}
	}

	private static boolean upload() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (uploaded && sheetTex != null && mc.getTextureManager().getTexture(SHEET) != sheetTex) {
			uploaded = false; // a resource reload / window change dropped our texture: upload it again
		}
		if (uploaded || failed) {
			return uploaded;
		}
		if (++tries > 6) {
			failed = true; // never loop on a broken GPU state
			return false;
		}
		try (InputStream in = SplashLogo.class.getResourceAsStream("/assets/sol_client/textures/gui/splash_logo.png")) {
			if (in == null) {
				failed = true;
				return false;
			}
			com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in);
			Sheet tex = new Sheet(img);
			tex.upload();
			mc.getTextureManager().register(SHEET, tex);
			sheetTex = tex;
			uploaded = true;
		} catch (Throwable e) {
			failed = true;
			System.err.println("[Caos] splash logo texture: " + e);
		}
		return uploaded;
	}

	/** Draws the current frame above the Mojang logo; alpha 0..1 follows the loading screen fade. */
	public static void draw(GuiGraphicsExtractor c, int sw, int sh, float alpha) {
		if (alpha <= 0.01F) {
			return;
		}
		var win = net.minecraft.client.Minecraft.getInstance().getWindow();
		if (win == null || win.getWidth() <= 0 || win.getHeight() <= 0 || win.getGuiScaledWidth() <= 0) {
			return; // minimised or in the middle of a fullscreen switch
		}
		load();
		if (!upload()) {
			return;
		}
		int total = ends[ends.length - 1];
		long t = (System.currentTimeMillis() - START) % Math.max(1, total);
		int f = 0;
		while (f < ends.length - 1 && t >= ends[f]) {
			f++;
		}
		f = Math.min(f, count - 1);

		// same maths as the vanilla Mojang logo, so we sit right above it
		double d = Math.min(sw * 0.75, sh) * 0.25;
		int logoTop = (int) (sh * 0.5) - (int) (d * 0.5);
		int size = (int) Math.min(d * 1.5, logoTop - 8);
		if (size < 16) {
			return;
		}
		int x = sw / 2 - size / 2, y = logoTop - size - 4;
		int u = (f % cols) * frame, v = (f / cols) * frame;
		int a = Math.round(Math.max(0F, Math.min(1F, alpha)) * 255F);
		c.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, (float) u, (float) v, size, size, frame, frame,
				cols * frame, ((count + cols - 1) / cols) * frame, (a << 24) | 0xFFFFFF);
	}
}
